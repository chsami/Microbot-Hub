package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import com.google.common.collect.Sets;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.UsernameChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Mahogany Homes plugin for automating construction contracts.
 *
 * This plugin helps players complete Mahogany Homes contracts by:
 * - Tracking current contract state and progress
 * - Managing plank sack inventory
 * - Highlighting objects that need repair/building
 * - Providing hint arrows and map indicators
 * - Automatically handling contract dialogues
 *
 * The plugin is organized using service classes for better maintainability:
 * - ContractStateManager: Manages contract state and persistence
 * - PlankSackManager: Handles plank sack operations and inventory tracking
 * - ContractDialogueHandler: Parses contract-related dialogues and messages
 *
 * @author Donder
 * @version 1.0.0
 */
@Slf4j
@PluginDescriptor(
        name = "Donder's Mahogany Homes",
        description = "Automates Mahogany Homes contracts",
        tags = {"mahogany", "homes", "construction"},
		authors = {"Donder"},
        version = DonderMahoganyHomesPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class DonderMahoganyHomesPlugin extends Plugin {
    public static final String version = "1.0.0";

    private static final Map<Integer, Integer> MAHOGANY_HOMES_REPAIRS = HotspotObjects.getAllRepairObjectIds();

    private static final Set<Integer> HALLOWED_SEPULCHRE_FIXES = Sets.newHashSet(39527, 39528);
    public static final List<Integer> PLANKS = Arrays.asList(ItemID.PLANK, ItemID.OAK_PLANK, ItemID.TEAK_PLANK, ItemID.MAHOGANY_PLANK);

    // Construction interface constants
    private static final int CONSTRUCTION_WIDGET_GROUP = 458;
    private static final int CONSTRUCTION_WIDGET_BUILD_IDX_START = 4;
    private static final int CONSTRUCTION_SUBWIDGET_MATERIALS = 3;
    private static final int CONSTRUCTION_SUBWIDGET_CANT_BUILD = 5;
    private static final int SCRIPT_BUILD_CONSTRUCTION_MENU_ENTRY = 1404;

    /**
     * Simple data class for build menu items
     */
    private static class BuildMenuItem {
        private final Item[] planks;
        private final boolean canBuild;

        BuildMenuItem(Item[] planks, boolean canBuild) {
            this.planks = planks;
            this.canBuild = canBuild;
        }
    }

    @Getter
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Getter
    @Inject
    private MahoganyHomesConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MahoganyHomesOverlay textOverlay;

    @Inject
    private PlankSackOverlay plankSackOverlay;

    @Inject
    private MahoganyHomesHighlightOverlay highlightOverlay;

    @Inject
    private MahoganyHomesScript script;

    @Inject
    private WorldMapPointManager worldMapPointManager;

    @Inject
    private ContractStateManager contractStateManager;

    @Inject
    private PlankSackManager plankSackManager;

    @Inject
    private ContractDialogueHandler dialogueHandler;

    /**
     * Provides the plugin configuration.
     */
    @Provides
    MahoganyHomesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MahoganyHomesConfig.class);
    }

    // Animation tracking
    private boolean watchForAnimations = false;
    private int lastAnimation = -1;

    // Construction interface tracking
    private int menuItemsToCheck = 0;

    // Game object tracking
    @Getter
    private final List<GameObject> objectsToMark = new ArrayList<>();

    // Varbit tracking
    private final Map<Integer, Integer> varbMap = new HashMap<>();
    private boolean varbChange;
    private int lastCompletedCount = -1;

    // UI resources
    private BufferedImage mapIcon;
    private BufferedImage mapArrow;

    /**
     * API methods for accessing contract state (delegated to service classes)
     */

    /**
     * @return the currently active contract home, or null if none
     */
    public Home getCurrentHome() {
        return contractStateManager.getCurrentHome();
    }

    /**
     * @return the current contract tier (1-4)
     */
    public int getContractTier() {
        return contractStateManager.getContractTier();
    }

    /**
     * @return number of contracts completed this session
     */
    public int getSessionContracts() {
        return contractStateManager.getSessionContracts();
    }

    /**
     * @return total points earned this session
     */
    public int getSessionPoints() {
        return contractStateManager.getSessionPoints();
    }

    /**
     * @return current plank count in plank sack (-1 if unknown)
     */
    public int getPlankCount() {
        return plankSackManager.getPlankCount();
    }

    public void setPlankCount(int number) {
        plankSackManager.setPlankCount(number);
    }

    /**
     * Sets the current contract home and refreshes UI elements
     * @param home the home to set as current contract
     */
    public void setCurrentHome(Home home) {
        contractStateManager.setCurrentHome(home);
    }

    private boolean isRepairOrBuildAction(MenuOptionClicked event) {
        return (event.getMenuOption().equals("Repair") || event.getMenuOption().equals("Build")) &&
               MAHOGANY_HOMES_REPAIRS.containsKey(event.getId());
    }

    @Override
    public void startUp() {
        overlayManager.add(textOverlay);
        overlayManager.add(highlightOverlay);
        overlayManager.add(plankSackOverlay);

        if (client.getGameState() == GameState.LOGGED_IN) {
            contractStateManager.loadFromConfig();
            clientThread.invoke(this::updateVarbMap);
        }

        script.run(config);
    }

    @Override
    public void shutDown() {
        overlayManager.remove(textOverlay);
        overlayManager.remove(highlightOverlay);
        overlayManager.remove(plankSackOverlay);
        worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
        client.clearHintArrow();

        // Clear state
        varbMap.clear();
        objectsToMark.clear();
        contractStateManager.setCurrentHome(null);
        mapIcon = null;
        mapArrow = null;
        lastCompletedCount = -1;

        script.shutdown();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged c) {
        if (!c.getGroup().equals(MahoganyHomesConfig.GROUP_NAME)) {
            return;
        }

        switch (c.getKey()) {
            case MahoganyHomesConfig.WORLD_MAP_KEY:
                worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
                contractStateManager.getCurrentContract().ifPresent(home -> {
                    if (config.worldMapIcon()) {
                        worldMapPointManager.add(new MahoganyHomesWorldPoint(home.getLocation(), this));
                    }
                });
                break;
            case MahoganyHomesConfig.HINT_ARROW_KEY:
                client.clearHintArrow();
                if (client.getLocalPlayer() != null) {
                    refreshHintArrow(client.getLocalPlayer().getWorldLocation());
                }
                break;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        // Defer to game tick for better performance
        varbChange = true;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOADING) {
            objectsToMark.clear();
        }
    }

    @Subscribe
    public void onUsernameChanged(UsernameChanged e) {
        contractStateManager.loadFromConfig();
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        processGameObjects(event.getGameObject(), null);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        processGameObjects(null, event.getGameObject());
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked e) {
        if (!e.getOverlay().equals(textOverlay)) {
            return;
        }

        if (e.getEntry().getOption().equals(MahoganyHomesOverlay.CLEAR_OPTION)) {
            contractStateManager.setCurrentHome(null);
        }

        if (e.getEntry().getOption().equals(MahoganyHomesOverlay.RESET_SESSION_OPTION)) {
            contractStateManager.resetSessionStats();
        }
    }

    private void plankSackCheck() {
        if (menuItemsToCheck <= 0) {
            return;
        }

        for (int i = 0; i < menuItemsToCheck; i++) {
            int idx = CONSTRUCTION_WIDGET_BUILD_IDX_START + i;
            Widget widget = client.getWidget(CONSTRUCTION_WIDGET_GROUP, idx);
            if (widget == null) {
                continue;
            }

            Widget materialWidget = widget.getDynamicChildren()[CONSTRUCTION_SUBWIDGET_MATERIALS];
            if (materialWidget == null) {
                continue;
            }

            String[] materialLines = materialWidget.getText().split("<br>");
            List<Item> materials = new ArrayList<>();
            for (String line : materialLines) {
                String[] data = line.split(": ");
                if (data.length < 2) {
                    continue;
                }

                String name = data[0];
                int count = Integer.parseInt(data[1]);
                if (PlankSackManager.PLANK_NAMES.contains(name)) {
                    materials.add(new Item(PLANKS.get(PlankSackManager.PLANK_NAMES.indexOf(name)), count));
                }
            }
        }
        menuItemsToCheck = 0;
    }

    @Subscribe
    public void onGameTick(GameTick t) {
        plankSackCheck();

        // Handle contract dialogues
        if (contractStateManager.getContractTier() == 0 || !contractStateManager.hasActiveContract()) {
            dialogueHandler.processPlayerDialogue();
        }

        dialogueHandler.processNpcDialogue();

        if (!contractStateManager.hasActiveContract()) {
            return;
        }

        if (varbChange) {
            varbChange = false;
            updateVarbMap();

            // If we couldn't find their contract tier recalculate it when they get close
            if (contractStateManager.getContractTier() == 0) {
                calculateContractTier();
            }

            final int completed = getCompletedCount();
            if (completed != lastCompletedCount) {
                // Refresh UI elements
                contractStateManager.getCurrentContract().ifPresent(this::refreshUIForHome);
                updateVarbMap();

                lastCompletedCount = completed;
                contractStateManager.setLastChanged(Instant.now());
            }
        }

        refreshHintArrow(Rs2Player.getWorldLocation());
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        if (!e.getType().equals(ChatMessageType.GAMEMESSAGE)) {
            return;
        }

        final String message = e.getMessage();

        // Delegate to service classes
        plankSackManager.processChatMessage(message);
        dialogueHandler.processChatMessage(message);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (isRepairOrBuildAction(event) && !watchForAnimations) {
            watchForAnimations = true;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() != SCRIPT_BUILD_CONSTRUCTION_MENU_ENTRY) {
            return;
        }
        // Construction menu add object
        menuItemsToCheck += 1;
        // Cancel repair-based animation checking
        watchForAnimations = false;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!watchForAnimations || event.getActor() != client.getLocalPlayer()) {
            return;
        }

        int currentAnimation = client.getLocalPlayer().getAnimation();
        boolean isConstructionAnimation = (lastAnimation == AnimationID.CONSTRUCTION ||
                                         lastAnimation == AnimationID.CONSTRUCTION_IMCANDO);

        if (isConstructionAnimation && currentAnimation != lastAnimation) {
            resetAnimationTracking();
        } else {
            lastAnimation = currentAnimation;
        }
    }

    private void resetAnimationTracking() {
        watchForAnimations = false;
        lastAnimation = -1;
    }

    /**
     * Refreshes UI elements when a new home is set
     */
    private void refreshUIForHome(Home home) {
        client.clearHintArrow();

        if (config.worldMapIcon()) {
            worldMapPointManager.removeIf(MahoganyHomesWorldPoint.class::isInstance);
            worldMapPointManager.add(new MahoganyHomesWorldPoint(home.getLocation(), this));
        }

        if (config.displayHintArrows() && client.getLocalPlayer() != null) {
            refreshHintArrow(client.getLocalPlayer().getWorldLocation());
        }
    }

    // This method was moved to ContractStateManager, but we keep this for UI updates
    // that happen when the state changes


    private void processGameObjects(final GameObject cur, final GameObject prev) {
        objectsToMark.remove(prev);

        if (cur == null || (!Hotspot.isHotspotObject(cur.getId()) && !Home.isLadder(cur.getId()))) {
            return;
        }

        // Filter objects inside highlight overlay
        objectsToMark.add(cur);
    }

    private void updateVarbMap() {
        varbMap.clear();

        for (final Hotspot spot : Hotspot.values()) {
            varbMap.put(spot.getVarb(), client.getVarbitValue(spot.getVarb()));
        }
    }

    // Removed - now handled by ContractStateManager.loadFromConfig()
    // Configuration methods moved to ContractStateManager

    private void refreshHintArrow(final WorldPoint playerPos) {
        client.clearHintArrow();

        Home currentHome = getCurrentHome();
        if (currentHome == null || !config.displayHintArrows()) {
            return;
        }

        if (distanceBetween(currentHome.getArea(), playerPos) > 0) {
            client.setHintArrow(currentHome.getLocation());
        } else {
            // We are really close to house, only display a hint arrow if we are done.
            if (getCompletedCount() != 0) {
                return;
            }

            client.getNpcs().stream()
            .filter(n -> n.getId() == currentHome.getNpcId())
            .findFirst()
            .ifPresent(client::setHintArrow);

        if (client.getNpcs().stream().anyMatch(n -> n.getId() == currentHome.getNpcId())) {
            return;
        }

            // Couldn't find the NPC, find the closest ladder to player
            WorldPoint location = null;
            int distance = Integer.MAX_VALUE;
            for (final GameObject obj : objectsToMark) {
                if (Home.isLadder(obj.getId())) {
                    // Ensure ladder isn't in a nearby home.
                    if (distanceBetween(currentHome.getArea(), obj.getWorldLocation()) > 0) {
                        continue;
                    }

                    int diff = obj.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation());
                    if (diff < distance) {
                        distance = diff;
                        location = obj.getWorldLocation();
                    }
                }
            }

            if (location != null) {
                client.setHintArrow(location);
            }
        }
    }

    /**
     * Counts the number of completed hotspots in the current contract
     * @return number of completed hotspots, or -1 if no active contract
     */
    int getCompletedCount() {
        Home currentHome = getCurrentHome();
        if (currentHome == null) {
            return -1;
        }

        return (int) Arrays.stream(Hotspot.values())
            .filter(hotspot -> doesHotspotRequireAttention(hotspot.getVarb()))
            .count();
    }

    /**
     * Checks if a hotspot requires attention based on its varbit value
     * @param varb the varbit ID to check
     * @return true if the hotspot needs repair (1), removal (3), or building (4)
     */
    boolean doesHotspotRequireAttention(final int varb) {
        Integer varbValue = varbMap.get(varb);
        if (varbValue == null) {
            return false;
        }
        // 1=Needs repair, 3=Remove, 4=Build
        return varbValue == 1 || varbValue == 3 || varbValue == 4;
    }

    /**
     * Calculates distance between a world area and a point, ignoring plane differences
     * @param area the world area
     * @param point the world point
     * @return distance between the area and point
     */
    int distanceBetween(final WorldArea area, final WorldPoint point) {
        return area.distanceTo(new WorldPoint(point.getX(), point.getY(), area.getPlane()));
    }

    BufferedImage getMapIcon() {
        if (mapIcon == null) {
            try {
                mapIcon = ImageUtil.loadImageResource(getClass(), "map-icon.png");
            } catch (Exception e) {
                log.warn("Failed to load map icon", e);
            }
        }
        return mapIcon;
    }

    BufferedImage getMapArrow() {
        if (mapArrow == null) {
            try {
                mapArrow = ImageUtil.loadImageResource(getClass(), "map-arrow-icon.png");
            } catch (Exception e) {
                log.warn("Failed to load map arrow icon", e);
            }
        }
        return mapArrow;
    }

    boolean isPluginTimedOut() {
        return false;
    }

    private void calculateContractTier() {
        int maxVarbValue = varbMap.values().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

        // Normalize tier from varb values 5-8 to contract tiers 1-4
        int calculatedTier = Math.max(0, maxVarbValue - 4);
        contractStateManager.setContractTier(calculatedTier);
    }

    public Set<Integer> getRepairableVarbs() {
        return varbMap.keySet()
                .stream()
                .filter(this::doesHotspotRequireAttention)
                .collect(Collectors.toSet());
    }

    Color getColour() {
        int currentPlankCount = getPlankCount();
        if (currentPlankCount <= 0) {
            return Color.RED;
        } else if (currentPlankCount < 14) {
            return Color.YELLOW;
        } else {
            return Color.WHITE;
        }
    }

}
