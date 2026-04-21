package net.runelite.client.plugins.microbot.actionreplay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.actionreplay.model.RecordedAction;
import net.runelite.client.plugins.microbot.actionreplay.model.Recording;
import net.runelite.client.plugins.microbot.actionreplay.model.TargetType;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@PluginDescriptor(
	name = PluginConstants.RED_BRACKET + "AIO AIO</html>",
	description = "Record and replay sequences of in-game actions. 80/20 automation for one-off tasks.",
	tags = {"microbot", "aio", "record", "replay", "macro", "automation"},
	authors = { "Red Bracket" },
	version = ActionReplayPlugin.version,
	minClientVersion = "2.1.32",
	iconUrl = "https://chsami.github.io/Microbot-Hub/ActionReplayPlugin/assets/icon.png",
	cardUrl = "https://chsami.github.io/Microbot-Hub/ActionReplayPlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class ActionReplayPlugin extends Plugin
{
	public static final String version = "1.1.0";

	static final Path RECORDINGS_DIR = RuneLite.RUNELITE_DIR.toPath().resolve("microbot-recordings");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Inject
	private ActionReplayConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ActionReplayOverlay overlay;

	@Inject
	private ActionReplayScript script;

	@Getter
	private volatile boolean recording = false;
	@Getter
	private volatile boolean playing = false;

	@Getter
	private Recording currentRecording;
	@Getter
	private boolean appendingToExisting;
	private int gameTickCounter;
	private int lastActionTickCounter;

	private ActionReplayPanel panel;
	private NavigationButton navButton;

	@Provides
	ActionReplayConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(ActionReplayConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		try
		{
			Files.createDirectories(RECORDINGS_DIR);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("ActionReplay: could not create recordings dir " + RECORDINGS_DIR, e);
		}

		panel = injector.getInstance(ActionReplayPanel.class);
		panel.setPlugin(this);
		panel.refresh();

		navButton = NavigationButton.builder()
			.tooltip("AIO AIO")
			.icon(buildIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		stopRecording(false);
		stopPlayback();
		overlayManager.remove(overlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (recording)
		{
			gameTickCounter++;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		Recording rec = currentRecording;
		if (!recording || rec == null || e.isConsumed())
		{
			return;
		}

		int tickDelta = rec.size() == 0 ? 0 : Math.max(0, gameTickCounter - lastActionTickCounter);
		lastActionTickCounter = gameTickCounter;

		RecordedAction a = new RecordedAction();
		a.setDelayTicksBefore(tickDelta);
		a.setMenuOption(stripColorTags(e.getMenuOption()));
		a.setTargetName(cleanTarget(e.getMenuTarget()));
		a.setMenuAction(e.getMenuAction() == null ? null : e.getMenuAction().name());
		a.setTargetType(TargetType.fromMenuAction(e.getMenuAction()));
		a.setIdentifier(e.getId());
		a.setParam0(e.getParam0());
		a.setParam1(e.getParam1());
		a.setItemId(e.getItemId());

		Point mouse = Microbot.getClient().getMouseCanvasPosition();
		if (mouse != null)
		{
			a.setCanvasX(mouse.getX());
			a.setCanvasY(mouse.getY());
		}

		rec.getActions().add(a);
		if (panel != null)
		{
			panel.onActionRecorded(a);
		}
	}

	public void startRecording(Recording existing)
	{
		if (recording)
		{
			return;
		}
		if (existing != null)
		{
			currentRecording = existing;
			appendingToExisting = true;
			log.info("ActionReplay: recording started (appending to '{}')", existing.getName());
		}
		else
		{
			currentRecording = new Recording();
			currentRecording.setLastUsedAtEpochMs(System.currentTimeMillis());
			currentRecording.setName("Recording");
			appendingToExisting = false;
			log.info("ActionReplay: recording started (new)");
		}
		gameTickCounter = 0;
		lastActionTickCounter = 0;
		recording = true;
	}

	public Recording stopRecording(boolean save)
	{
		if (!recording)
		{
			return null;
		}
		recording = false;
		Recording r = currentRecording;
		currentRecording = null;
		if (r == null || r.size() == 0)
		{
			log.info("ActionReplay: recording stopped (empty, not saved)");
			if (panel != null)
			{
				panel.refresh();
			}
			return null;
		}
		if (save)
		{
			if (!appendingToExisting)
			{
				enrichName(r);
				r.setName(uniqueName(r.getName()));
			}
			try
			{
				save(r);
			}
			catch (IOException e)
			{
				throw new IllegalStateException("ActionReplay: failed to save recording " + r.getName(), e);
			}
		}
		log.info("ActionReplay: recording stopped ({} actions)", r.size());
		if (panel != null)
		{
			panel.refresh();
		}
		return r;
	}

	public int getCurrentRecordingSize()
	{
		return currentRecording == null ? 0 : currentRecording.size();
	}

	public void play(Recording r)
	{
		if (playing)
		{
			return;
		}
		if (r == null || r.size() == 0)
		{
			log.warn("ActionReplay: recording is empty, nothing to play");
			return;
		}
		r.setLastUsedAtEpochMs(System.currentTimeMillis());
		Path savedFile = RECORDINGS_DIR.resolve(r.getName() + ".json");
		if (Files.exists(savedFile))
		{
			try
			{
				save(r);
			}
			catch (IOException e)
			{
				log.warn("ActionReplay: failed to persist lastUsedAt for {}: {}", r.getName(), e.getMessage());
			}
		}
		playing = true;
		if (panel != null)
		{
			panel.refresh();
		}
		boolean started = script.play(r, () ->
		{
			playing = false;
			if (panel != null)
			{
				panel.refresh();
			}
			log.info("ActionReplay: playback finished");
		});
		if (!started)
		{
			playing = false;
			if (panel != null)
			{
				panel.refresh();
			}
		}
	}

	public void stopPlayback()
	{
		if (!playing)
		{
			return;
		}
		script.shutdown();
		playing = false;
		if (panel != null)
		{
			panel.refresh();
		}
	}

	public List<Recording> listRecordings()
	{
		List<Recording> out = new ArrayList<>();
		if (!Files.isDirectory(RECORDINGS_DIR))
		{
			return out;
		}
		try (Stream<Path> files = Files.list(RECORDINGS_DIR))
		{
			files.filter(p -> p.getFileName().toString().endsWith(".json"))
				.forEach(p ->
				{
					try
					{
						String json = new String(Files.readAllBytes(p));
						Recording r = GSON.fromJson(json, Recording.class);
						if (r != null)
						{
							if (r.getName() == null)
							{
								String fn = p.getFileName().toString();
								r.setName(fn.substring(0, fn.length() - 5));
							}
							if (r.getLastUsedAtEpochMs() == 0)
							{
								r.setLastUsedAtEpochMs(Files.getLastModifiedTime(p).toMillis());
							}
							out.add(r);
						}
					}
					catch (IOException ex)
					{
						log.warn("ActionReplay: failed to read {}: {}", p, ex.getMessage());
					}
				});
		}
		catch (IOException e)
		{
			throw new IllegalStateException("ActionReplay: failed to list recordings in " + RECORDINGS_DIR, e);
		}
		out.sort(Comparator.comparingLong(Recording::getLastUsedAtEpochMs).reversed());
		return out;
	}

	public void save(Recording r) throws IOException
	{
		Files.createDirectories(RECORDINGS_DIR);
		Path file = RECORDINGS_DIR.resolve(r.getName() + ".json");
		Files.write(file, GSON.toJson(r).getBytes());
		log.info("ActionReplay: saved {}", file);
	}

	public void rename(Recording r, String newName) throws IOException
	{
		String clean = sanitize(newName);
		if (clean.isEmpty())
		{
			throw new IllegalArgumentException("ActionReplay: recording name cannot be empty");
		}
		Path oldFile = RECORDINGS_DIR.resolve(r.getName() + ".json");
		Path newFile = RECORDINGS_DIR.resolve(clean + ".json");
		r.setName(clean);
		Files.write(newFile, GSON.toJson(r).getBytes());
		if (!oldFile.equals(newFile) && Files.exists(oldFile))
		{
			Files.delete(oldFile);
		}
	}

	public void delete(Recording r) throws IOException
	{
		Path file = RECORDINGS_DIR.resolve(r.getName() + ".json");
		Files.deleteIfExists(file);
	}

	private static String sanitize(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replaceAll("[^a-zA-Z0-9_.-]", "_").trim();
	}

	private void enrichName(Recording r)
	{
		if (r == null || r.size() == 0)
		{
			return;
		}
		RecordedAction first = r.getActions().get(0);
		String verb = sanitize(first.getMenuOption());
		String target = sanitize(first.getTargetName());
		StringBuilder name = new StringBuilder();
		if (!verb.isEmpty())
		{
			name.append(verb);
		}
		if (!target.isEmpty())
		{
			if (name.length() > 0)
			{
				name.append("-");
			}
			name.append(target);
		}
		if (name.length() > 0)
		{
			r.setName(name.toString());
		}
	}

	private String uniqueName(String base)
	{
		if (base == null || base.isEmpty())
		{
			base = "Recording";
		}
		String candidate = base;
		int n = 2;
		while (Files.exists(RECORDINGS_DIR.resolve(candidate + ".json")))
		{
			candidate = base + "-" + n;
			n++;
		}
		return candidate;
	}

	private static String stripColorTags(String s)
	{
		if (s == null)
		{
			return null;
		}
		return s.replaceAll("<[^>]+>", "");
	}

	private static String cleanTarget(String menuTarget)
	{
		String s = stripColorTags(menuTarget);
		if (s == null)
		{
			return null;
		}
		return s.replaceAll("\\s*\\(level-\\d+\\)\\s*$", "").trim();
	}

	private static BufferedImage buildIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		try
		{
			g.setColor(new java.awt.Color(220, 40, 40));
			g.fillOval(2, 2, 12, 12);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}
}
