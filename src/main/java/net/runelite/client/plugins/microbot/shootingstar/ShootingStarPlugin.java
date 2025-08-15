package net.runelite.client.plugins.microbot.shootingstar;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = ShootingStarPlugin.PLUGIN_PREFIX + "ShootingStar",
	description = "Finds & Travels to shooting stars",
	author = "g-mason0",
	version = ShootingStarPlugin.version,
	minClientVersion = "1.9.8",
	tags = {"mining",  "skilling", "star", "shooting"},
	enabledByDefault = false,
	isExternal = true
)
@Slf4j
public class ShootingStarPlugin extends Plugin
{

	static final String PLUGIN_PREFIX = "<html>[<font color=#0077B6>✨</font>] ";
	static final String version = "1.4.0";

	@Getter
	private final List<Star> starList = new ArrayList<>();

	@Inject
	private ShootingStarScript shootingStarScript;

	@Inject
	private ShootingStarApiClient shootingStarApiClient;

	@Inject
	private ShootingStarConfig config;

	@Provides
	ShootingStarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShootingStarConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ShootingStarOverlay shootingStarOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navButton;
	private ShootingStarPanel panel;

	@Getter
	@Setter
	public int totalStarsMined = 0;
	private final AtomicInteger apiTickCounter = new AtomicInteger(0);
	private int lastWorld = -1;
	private final int UPDATE_INTERVAL = 3;
	private final ZoneId utcZoneId = ZoneId.of("UTC");
	@Getter
	private boolean displayAsMinutes;
	@Getter
	private boolean hideMembersWorlds;
	@Getter
	private boolean hideF2PWorlds;

	private Set<String> blacklistedLocations = new HashSet<>();

	@Override
	protected void startUp() throws AWTException
	{
		displayAsMinutes = config.isDisplayAsMinutes();
		hideMembersWorlds = !Rs2Player.isInMemberWorld();
		hideF2PWorlds = Rs2Player.isInMemberWorld();

		loadBlacklistedLocations();
		fetchStars();
		createPanel();
		updatePanelList(true);

		toggleOverlay(config.isHideOverlay());
		shootingStarScript.run();
	}

	protected void shutDown()
	{
		shootingStarScript.shutdown();
		removePanel();
		starList.clear();
		lastWorld = -1;
		apiTickCounter.set(0);
		overlayManager.remove(shootingStarOverlay);
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event)
	{
		if (!event.getGroup().equals(ShootingStarConfig.configGroup))
		{
			return;
		}

		if (event.getKey().equals(ShootingStarConfig.blacklistedLocations))
		{
			loadBlacklistedLocations();
			updateHiddenStars();
			updatePanelList(true);
		}

		if (event.getKey().equals(ShootingStarConfig.displayAsMinutes))
		{
			displayAsMinutes = config.isDisplayAsMinutes();
			updatePanelList(false);
		}

		if (event.getKey().equals(ShootingStarConfig.hideWildernessLocations))
		{
			updateHiddenStars();
			updatePanelList(true);
		}

		if (event.getKey().equals(ShootingStarConfig.providerName)) {
			log.info("Provider changed to: {}", config.getProvider());
			final int beforeSize = starList.size();
			starList.clear();
			fetchStars();
			log.info("Stars fetched: {} (before: {})", starList.size(), beforeSize);
		}

		if (event.getKey().equals(ShootingStarConfig.hideOverlay))
		{
			toggleOverlay(config.isHideOverlay());
		}
	}

	@Schedule(
		period = 1,
		unit = ChronoUnit.MINUTES,
		asynchronous = true
	)
	public void tick() {
		checkDepletedStars();

		if (apiTickCounter.get() >= UPDATE_INTERVAL)
		{
			fetchStars();
			apiTickCounter.set(0);
		}

		apiTickCounter.incrementAndGet();

		if (config.useBreakAtBank() && !isBreakHandlerEnabled())
		{
			log.warn("Break Handler is not enabled to utilize the useBreakAtBank feature, enabling now..");
			enableBreakHandler();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			if (lastWorld == -1)
			{
				lastWorld = Microbot.getClient().getWorld();
				fetchStars();
			}
			else
			{
				int currentWorld = Microbot.getClient().getWorld();

				if (currentWorld != lastWorld)
				{
					lastWorld = currentWorld;
					updatePanelList(true);
				}
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

		if (event.getMessage().equalsIgnoreCase("oh dear, you are dead!") && config.shutdownOnDeath()) {
			Rs2Walker.setTarget(null);
			shutDown();
		}
	}

	public void fetchStars()
	{
		List<Star> latestStars = shootingStarApiClient.getStarData(config.getProvider());
		boolean fullUpdate = false;

		for (Star star : latestStars) {
			// Find oldStar inside starList
			Star oldStar = starList.stream()
				.filter(star::equals)
				.findFirst()
				.orElse(null);

			// If there is an oldStar in the same world & location
			if (oldStar != null)
			{
				oldStar.setEndsAt(star.getEndsAt());
				oldStar.setTier(star.getTier());
				continue;
			}

			// If oldStar not found, add new star into the list
			starList.add(star);
			fullUpdate = true;
		}

		if (fullUpdate) {
			updateHiddenStars();
		}

		updatePanelList(fullUpdate);
	}

	private void checkDepletedStars()
	{
		ZonedDateTime now = ZonedDateTime.now(utcZoneId);
		long threshold = now.minusMinutes(UPDATE_INTERVAL).toInstant().toEpochMilli();

		List<Star> depletedStars = starList.stream()
			.filter(star -> star.getEndsAt() < threshold)
			.collect(Collectors.toList());

		depletedStars.forEach(this::removeStar);

		boolean fullUpdate = !depletedStars.isEmpty();
		updatePanelList(fullUpdate);
	}

	public void removeStar(Star star)
	{
		if (star.equals(getSelectedStar()))
		{
			star.setSelected(false);
		}
		starList.remove(star);
	}

	public void updateSelectedStar(Star star)
	{
		Star oldStar = getSelectedStar();
		if (oldStar == null)
		{
			star.setSelected(!star.isSelected());
		}
		else if (!oldStar.equals(star))
		{
			oldStar.setSelected(false);
			star.setSelected(!star.isSelected());
		}
	}

	public void updateHiddenStars()
	{
		starList.forEach(star -> {
			boolean hide = hideMembersWorlds && star.isMemberWorld()
				|| (hideF2PWorlds && !star.isMemberWorld())
				|| (config.isHideWildernessLocations() && star.isInWilderness())
				|| blacklistedLocations.contains(star.getShootingStarLocation().getLocationName());
			star.setHidden(hide);
		});
	}

	public Star getSelectedStar()
	{
		return starList.stream().filter(Star::isSelected).findFirst().orElse(null);
	}

	private void createPanel()
	{
		if (panel == null)
		{
			panel = new ShootingStarPanel(this);
			final BufferedImage icon = ImageUtil.loadImageResource(ShootingStarPlugin.class, "icon.png");

			navButton = NavigationButton.builder()
				.tooltip("Shooting Stars")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);
		}
	}

	private void removePanel()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	public void updatePanelList(boolean fullUpdate)
	{
		List<Star> stars = new ArrayList<>(starList);

		if (fullUpdate)
		{
			SwingUtilities.invokeLater(() -> panel.updateList(stars));
		}
		else
		{
			SwingUtilities.invokeLater(() -> panel.refreshList(stars));
		}
	}

	private void toggleOverlay(boolean hideOverlay)
	{
		if (overlayManager != null)
		{
			boolean hasOverlay = overlayManager.anyMatch(ov -> ov.getName().equalsIgnoreCase(ShootingStarOverlay.class.getSimpleName()));

			if (hideOverlay)
			{
				if (!hasOverlay)
				{
					return;
				}

				overlayManager.remove(shootingStarOverlay);
			}
			else
			{
				if (hasOverlay)
				{
					return;
				}

				overlayManager.add(shootingStarOverlay);
			}
		}
	}

	public Star getClosestHighestTierStar()
	{
		// Get the highest tier available
		int highestTier = starList.stream()
			.filter(s -> !s.isHidden() && s.hasRequirements())
			.mapToInt(Star::getTier)
			.max()
			.orElse(-1);  // Return -1 if no star meets the requirements

		// If no star meets the requirements, return null
		if (highestTier == -1)
		{
			return null;
		}

		int minTier = Math.max(1, highestTier - 2); // The lowest tier to consider (at least 1)
		int maxTier = Math.min(9, highestTier + 1); // The highest tier to consider (up to 9)

		List<Star> accessibleStars = starList.stream()
			.filter(s -> !s.isHidden() && s.hasRequirements())
			.filter(s -> s.getTier() >= minTier && s.getTier() <= maxTier)
			.sorted(Comparator.comparingInt(Star::getTier).reversed())
			.distinct()
			.collect(Collectors.toList());

		Set<WorldPoint> accessibleStarPoints = accessibleStars.stream()
			.map(s -> s.getShootingStarLocation().getWorldPoint())
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		if (accessibleStarPoints.isEmpty())
		{
			return null; // No accessible stars found
		}

		if (ShortestPathPlugin.getPathfinderConfig().getTransports().isEmpty())
		{
			ShortestPathPlugin.getPathfinderConfig().refresh();
		}

		Pathfinder pathfinder = new Pathfinder(ShortestPathPlugin.getPathfinderConfig(), Microbot.getClient().getLocalPlayer().getWorldLocation(), accessibleStarPoints);
		pathfinder.run();
		List<WorldPoint> path = pathfinder.getPath();

		if (path.isEmpty())
		{
			return null; // No path found to any accessible star
		}

		WorldPoint finalTile = path.get(path.size() - 1);
		WorldArea finalTileArea = new WorldArea(finalTile, 3, 3);

		Map<Star, WorldArea> starAreas = accessibleStars.stream()
			.collect(Collectors.toMap(
				star -> star,
				star -> {
					WorldPoint wp = star.getShootingStarLocation().getWorldPoint();
					return new WorldArea(wp, 3, 3);
				}
			));

		Optional<Star> intersectingStar = starAreas.entrySet().stream()
			.filter(entry -> entry.getValue().intersectsWith2D(finalTileArea))
			.map(Map.Entry::getKey)
			.findFirst();

		if (intersectingStar.isEmpty())
		{
			return null;
		}
		else
		{
			Star closestStar = intersectingStar.get();
			closestStar.setSelected(true);
			return closestStar;
		}
	}

	private void enableBreakHandler()
	{
		Plugin breakHandlerPlugin = Microbot.getPlugin(BreakHandlerPlugin.class);

		Microbot.startPlugin(breakHandlerPlugin);
	}

	public boolean isBreakHandlerEnabled()
	{
		return Microbot.isPluginEnabled(BreakHandlerPlugin.class);
	}

	public Duration getScriptRuntime()
	{
		return shootingStarScript.getRunTime();
	}

	private void loadBlacklistedLocations()
	{
		String raw = Microbot.getConfigManager().getConfiguration(ShootingStarConfig.configGroup, ShootingStarConfig.blacklistedLocations);
		if (raw == null || raw.isEmpty())
		{
			blacklistedLocations.clear();
			return;
		}
		blacklistedLocations = Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());
	}

	public void addLocationToBlacklist(String locationName)
	{
		blacklistedLocations.add(locationName);
		String joined = String.join(",", blacklistedLocations);
		Microbot.getConfigManager().setConfiguration(ShootingStarConfig.configGroup, ShootingStarConfig.blacklistedLocations, joined);
		updateHiddenStars();
		updatePanelList(true);
	}

	public void clearBlacklistedLocations()
	{
		blacklistedLocations.clear();
		Microbot.getConfigManager().setConfiguration(ShootingStarConfig.configGroup, ShootingStarConfig.blacklistedLocations, "");
		updateHiddenStars();
		updatePanelList(true);
	}

	public void exportBlacklistedLocations()
	{
		StringBuilder sb = new StringBuilder();
		blacklistedLocations.forEach(location -> sb.append(location).append(System.lineSeparator()));

		try {
			File file = chooseFileToSave("Export Blacklisted Locations", "txt");
			if (file != null) {
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
					writer.write(sb.toString());
					JOptionPane.showMessageDialog(null, "Blacklisted locations exported successfully: " + file.getAbsolutePath());
				}
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Error exporting blacklisted locations: " + e.getMessage());
			log.error("Error exporting blacklisted locations", e);
		}
	}

	public void importBlacklistedLocations()
	{
		try {
			File file = chooseFileToOpen("Import Blacklisted Locations", "txt");
			if (file != null) {
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					Set<String> importedLocations = new HashSet<>();
					String line;
					while ((line = reader.readLine()) != null) {
						String location = line.trim();
						if (!location.isEmpty()) {
							importedLocations.add(location);
						}
					}

					blacklistedLocations = importedLocations;
					String joined = String.join(",", blacklistedLocations);
					Microbot.getConfigManager().setConfiguration(ShootingStarConfig.configGroup, ShootingStarConfig.blacklistedLocations, joined);

					updateHiddenStars();
					updatePanelList(true);

					JOptionPane.showMessageDialog(null, "Blacklisted locations imported successfully: " + file.getAbsolutePath());
				}
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Error importing blacklisted locations: " + e.getMessage());
			log.error("Error importing blacklisted locations", e);
		}
	}

	private File chooseFileToSave(String dialogTitle, String fileExtension) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(dialogTitle);
		fileChooser.setFileFilter(new FileNameExtensionFilter("*." + fileExtension, fileExtension));

		int userSelection = fileChooser.showSaveDialog(null);
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();
			// Add file extension if not already present
			if (!fileToSave.getName().toLowerCase().endsWith("." + fileExtension)) {
				fileToSave = new File(fileToSave.getAbsolutePath() + "." + fileExtension);
			}
			return fileToSave;
		}
		return null;
	}

	private File chooseFileToOpen(String dialogTitle, String fileExtension) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle(dialogTitle);
		fileChooser.setFileFilter(new FileNameExtensionFilter("*." + fileExtension, fileExtension));

		int userSelection = fileChooser.showOpenDialog(null);
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			return fileChooser.getSelectedFile();
		}
		return null;
	}
}
