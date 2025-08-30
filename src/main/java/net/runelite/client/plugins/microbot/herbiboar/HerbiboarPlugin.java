package net.runelite.client.plugins.microbot.herbiboar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

import javax.inject.Inject;
import java.awt.AWTException;
import java.time.Instant;
import java.util.*;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Herbiboar",
        description = "Automatically hunts herbiboars with trail tracking and banking support",
        tags = {"skilling", "hunter", "herbiboar"},
        authors = {"AI Agent"},
        version = HerbiboarPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoHerbiboarPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoHerbiboarPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)

public class HerbiboarPlugin extends Plugin {
    static final String version = "1.2.0";

    @Getter
    @Setter
    private Instant startTime;

    @Getter
    @Setter
    private long runningTime;

    @Getter
    @Setter
    private Integer startXp = 0;

    @Getter
    @Setter
    private Integer xpGained = 0;

    @Getter
    @Setter
    private Integer currentLevel = 0;

    @Getter
    @Setter
    private Integer xpPerHour = 0;

    private static final List<WorldPoint> END_LOCATIONS = ImmutableList.of(
            new WorldPoint(3693, 3798, 0),
            new WorldPoint(3702, 3808, 0),
            new WorldPoint(3703, 3826, 0),
            new WorldPoint(3710, 3881, 0),
            new WorldPoint(3700, 3877, 0),
            new WorldPoint(3715, 3840, 0),
            new WorldPoint(3751, 3849, 0),
            new WorldPoint(3685, 3869, 0),
            new WorldPoint(3681, 3863, 0)
    );

    private static final Set<Integer> START_OBJECT_IDS = ImmutableSet.of(
            ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL1,
            ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL2,
            ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL3,
            ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL4,
            ObjectID.HUNTING_TRAIL_SPAWN_FOSSIL5
    );

    private static final List<Integer> HERBIBOAR_REGIONS = ImmutableList.of(
            14652,
            14651,
            14908,
            14907
    );

    @Inject
    private Client client;

    @Provides
    HerbiboarConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(HerbiboarConfig.class); }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private HerbiboarOverlay overlay;

    @Inject
    private HerbiboarTileOverlay tileOverlay;

    @Inject
    private HerbiboarMinimapOverlay minimapOverlay;

    @Inject
    private HerbiboarScript script;

    /**
     * Objects which appear at the beginning of Herbiboar hunting trails
     */
    @Getter
    private final Map<WorldPoint, TileObject> starts = new HashMap<>();
    /**
     * Herbiboar hunting "footstep" trail objects
     */
    @Getter
    private final Map<WorldPoint, TileObject> trails = new HashMap<>();
    /**
     * Objects which trigger next trail (mushrooms, mud, seaweed, etc.)
     */
    @Getter
    private final Map<WorldPoint, TileObject> trailObjects = new HashMap<>();
    /**
     * Tunnel where the Herbiboar is hiding at the end of a trail
     */
    @Getter
    private final Map<WorldPoint, TileObject> tunnels = new HashMap<>();
    /**
     * Trail object IDs which should be highlighted
     */
    @Getter
    private final Set<Integer> shownTrails = new HashSet<>();
    /**
     * Sequence of herbiboar spots searched along the current trail
     */
    @Getter
    private final List<HerbiboarSearchSpot> currentPath = Lists.newArrayList();

    @Getter
    private boolean inHerbiboarArea;

    @Getter
    private TrailToSpot nextTrail;

    @Getter
    private HerbiboarSearchSpot.Group currentGroup;

    @Getter
    private int finishId;

    @Getter
    private boolean started;

    @Getter
    private WorldPoint startPoint;

    @Getter
    private HerbiboarStart startSpot;

    @Override
    protected void startUp() throws AWTException {
        this.client = Microbot.getClient();

        ClientThread clientThread = Microbot.getClientThread();
        HerbiboarConfig config = provideConfig(Microbot.getConfigManager());

        setStartTime(Instant.now());
        setStartXp(Microbot.getClient().getSkillExperience(Skill.HUNTER));

        this.overlay = new HerbiboarOverlay(this);
        overlayManager.add(overlay);
        overlayManager.add(tileOverlay);
        overlayManager.add(minimapOverlay);

        script.run(config, this);

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() ->
            {
                inHerbiboarArea = checkArea();
                updateTrailData();
            });
        }
    }
    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(tileOverlay);
        resetTrailData();
        clearCache();
        setRunningTime(0);
        setStartXp(0);
        setStartTime(null);
        setXpPerHour(0);
        inHerbiboarArea = false;
        script.shutdown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            String message = chatMessage.getMessage();
            if (message.equals("The creature has successfully confused you with its tracks, leading you round in circles.") ||
                message.equals("You'll need to start again.")) {
                script.handleConfusionMessage();
            }
        }
    }
    private void updateTrailData()
    {
        if (!isInHerbiboarArea())
        {
            return;
        }

        boolean pathActive = false;
        boolean wasStarted = started;

        // Get trail data
        for (HerbiboarSearchSpot spot : HerbiboarSearchSpot.values())
        {
            for (TrailToSpot trail : spot.getTrails())
            {
                int value = client.getVarbitValue(trail.getVarbitId());

                if (value == trail.getValue())
                {
                    // The trail after you have searched the spot
                    currentGroup = spot.getGroup();
                    nextTrail = trail;

                    // You never visit the same spot twice
                    if (!currentPath.contains(spot))
                    {
                        currentPath.add(spot);
                    }
                }
                else if (value > 0)
                {
                    // The current trail
                    shownTrails.addAll(trail.getFootprintIds());
                    pathActive = true;
                }
            }
        }

        finishId = client.getVarbitValue(VarbitID.HUNTING_TRAIL_ENDS_FOSSIL);

        // The started varbit doesn't get set until the first spot of the rotation has been searched
        // so we need to use the current group as an indicator of the rotation being started
        started = client.getVarbitValue(VarbitID.HUNTING_TRAILS_USED_FOSSIL) > 0 || currentGroup != null;
        boolean finished = !pathActive && started;

        if (!wasStarted && started)
        {
            startSpot = HerbiboarStart.from(startPoint);
        }

        if (finished)
        {
            resetTrailData();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOpt)
    {
        if (!inHerbiboarArea || started || MenuAction.GAME_OBJECT_FIRST_OPTION != menuOpt.getMenuAction())
        {
            return;
        }

        switch (Text.removeTags(menuOpt.getMenuTarget()))
        {
            case "Rock":
            case "Mushroom":
            case "Driftwood":
                startPoint = WorldPoint.fromScene(client.getTopLevelWorldView(), menuOpt.getParam0(), menuOpt.getParam1(), client.getTopLevelWorldView().getPlane());
        }
    }

    private void resetTrailData()
    {
        Microbot.log("Reset trail data");
        shownTrails.clear();
        currentPath.clear();
        nextTrail = null;
        currentGroup = null;
        finishId = 0;
        started = false;
        startPoint = null;
        startSpot = null;
    }

    private void clearCache()
    {
        starts.clear();
        trails.clear();
        trailObjects.clear();
        tunnels.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (event.getGameState())
        {
            case HOPPING:
            case LOGGING_IN:
                resetTrailData();
                break;
            case LOADING:
                clearCache();
                inHerbiboarArea = checkArea();
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        updateTrailData();
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        onTileObject(null, event.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        onTileObject(event.getGameObject(), null);
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        onTileObject(null, event.getGroundObject());
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event)
    {
        onTileObject(event.getGroundObject(), null);
    }

    // Store relevant GameObjects (starts, tracks on trails, objects used to trigger next trails, and tunnels)
    private void onTileObject(TileObject oldObject, TileObject newObject)
    {
        if (oldObject != null)
        {
            WorldPoint oldLocation = oldObject.getWorldLocation();
            starts.remove(oldLocation);
            trails.remove(oldLocation);
            trailObjects.remove(oldLocation);
            tunnels.remove(oldLocation);
        }

        if (newObject == null)
        {
            return;
        }

        // Starts
        if (START_OBJECT_IDS.contains(newObject.getId()))
        {
            starts.put(newObject.getWorldLocation(), newObject);
            return;
        }

        // Trails
        if (HerbiboarSearchSpot.isTrail(newObject.getId()))
        {
            trails.put(newObject.getWorldLocation(), newObject);
            return;
        }

        // GameObject to trigger next trail (mushrooms, mud, seaweed, etc.)
        if (HerbiboarSearchSpot.isSearchSpot(newObject.getWorldLocation()))
        {
            trailObjects.put(newObject.getWorldLocation(), newObject);
            return;
        }

        // Herbiboar tunnel
        if (END_LOCATIONS.contains(newObject.getWorldLocation()))
        {
            tunnels.put(newObject.getWorldLocation(), newObject);
        }
    }

    public int calculateXpPerHour() {
        if (getRunningTime() <= 1 || getStartXp() <= 1) {
            return 0;
        }

        int currentXP = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        int xpGained = currentXP - getStartXp();
        setXpGained(xpGained);
        return (int) (xpGained * 3600 / getRunningTime());
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        setRunningTime(Instant.now().getEpochSecond() - getStartTime().getEpochSecond());
        setXpPerHour(calculateXpPerHour());
    }

    private boolean checkArea()
    {
        final int[] mapRegions = client.getMapRegions();
        for (int region : HERBIBOAR_REGIONS)
        {
            if (ArrayUtils.contains(mapRegions, region))
            {
                return true;
            }
        }
        return false;
    }

    public List<WorldPoint> getEndLocations()
    {
        return END_LOCATIONS;
    }
}
