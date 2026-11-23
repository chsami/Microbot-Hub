/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * Copyright (c) 2020, dekvall <https://github.com/dekvall>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.herbihunter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.ArrayUtils;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@PluginDescriptor(
        name = HerbiHunterPlugin.PREFIX + "Herbi Hunter",
        description = "Hunts herbiboars on Fossil Island with Relaxed and Focused play styles, banking, restocking and session history.",
        tags = {"herbiboar", "herbi hunter", "herbi", "hunter", "herblore", "fossil island", "skilling", "irked"},
        authors = {"Igor", "chsami", "Kavraka91", "irkedMATT"},
        version = HerbiHunterPlugin.version,
        minClientVersion = "2.6.60",
        iconUrl = "https://chsami.github.io/Microbot-Hub/HerbiHunterPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/HerbiHunterPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)

public class HerbiHunterPlugin extends Plugin {
    static final String version = "1.0.0";
    // Kept here, not in PluginConstants, so the PR stays inside this plugin's folder.
    static final String PREFIX = "<html>[<font color=#FF8FB1>irk</font>] ";

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

    static final List<WorldPoint> START_LOCATIONS = ImmutableList.of(
            new WorldPoint(3686, 3870, 0),  // MIDDLE
            new WorldPoint(3705, 3830, 0),  // LEPRECHAUN
            new WorldPoint(3704, 3810, 0),  // CAMP_ENTRANCE
            new WorldPoint(3695, 3800, 0),  // GHOST_MUSHROOM
            new WorldPoint(3751, 3850, 0)); // DRIFTWOOD

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

    @Inject
    private EventBus eventBus;

    /** Registered by hand: @Subscribe on a sideloaded jar logs a LambdaConversionException per handler. */
    private final List<EventBus.Subscriber> subscribers = new ArrayList<>();

    // Written on the client thread, read by the script thread: plain HashMap/ArrayList throw ConcurrentModificationException.
    @Getter
    private final Map<WorldPoint, TileObject> starts = new ConcurrentHashMap<>();
    @Getter
    private final Map<WorldPoint, TileObject> trailObjects = new ConcurrentHashMap<>();
    @Getter
    private final Map<WorldPoint, TileObject> tunnels = new ConcurrentHashMap<>();
    @Getter
    private final Set<Integer> shownTrails = ConcurrentHashMap.newKeySet();
    private final List<HerbiboarSearchSpot> currentPath = new CopyOnWriteArrayList<>();

    /** A snapshot: a size() then get() on the live list could straddle a clear() on the client thread. */
    public List<HerbiboarSearchSpot> getCurrentPath()
    {
        return new ArrayList<>(currentPath);
    }

    // Written by event handlers on the client thread, read by the script thread: volatile.
    @Getter
    private volatile boolean inHerbiboarArea;

    @Getter
    private volatile TrailToSpot nextTrail;


    @Getter
    @Setter
    private volatile List<WorldPoint> walkRoute = Collections.emptyList();

    @Getter
    private volatile HerbiboarSearchSpot.Group currentGroup;

    @Getter
    private volatile int finishId;
    // The finish varbit as last read: resetTrailData zeroes finishId while the varbit is still set, which would reopen TUNNEL over the BOAR step.
    private int finishVarbitSeen;

    private volatile boolean started;

    private static final String CORE_GROUP = "herbiboar";
    private static final String CORE_SHOW_TRAIL = "showTrail";
    private String coreShowTrail;

    @Inject
    private com.google.gson.Gson gson;

    @Getter
    private volatile HerbiboarSession session;
    final HerbiboarLog hbLog = new HerbiboarLog();
    @Getter
    private volatile List<HerbiboarSession> history = Collections.emptyList();
    @Setter
    private volatile String stopReason;
    private long lastCheckpointMs;
    private volatile int runId;
    private static final long CHECKPOINT_MS = 5 * 60_000L;

    @Override
    protected void startUp() {
        ClientThread clientThread = Microbot.getClientThread();
        HerbiboarConfig config = provideConfig(Microbot.getConfigManager());
        lastTrailChangeTick = -1;
        nothingTick = -1;
        trailConfused = false;   // a confusion from the last run must not count in this one
        hbLog.setDetail(config.logDetail());
        hbLog.start(version, String.valueOf(config.playStyle()));
        script.hbLog = hbLog;

        java.io.File dir = HerbiboarSession.dir();
        // A second client still hunting keeps its checkpoint fresh: not filed as a crash.
        HerbiboarSession.recoverCheckpoint(dir, gson, CHECKPOINT_MS + 60_000L);
        history = HerbiboarSession.loadHistory(dir, gson);
        session = new HerbiboarSession(System.currentTimeMillis());
        session.playStyle = String.valueOf(config.playStyle());
        session.startLevel = client.getRealSkillLevel(Skill.HUNTER);
        stopReason = null;
        lastCheckpointMs = System.currentTimeMillis();

        setStartTime(Instant.now());
        setStartXp(Microbot.getClient().getSkillExperience(Skill.HUNTER));

        // Core RuneLite's Herbiboar draws every finished trail, never the current one: off while we run, restored after.
        coreShowTrail = Microbot.getConfigManager().getConfiguration(CORE_GROUP, CORE_SHOW_TRAIL);
        Microbot.getConfigManager().setConfiguration(CORE_GROUP, CORE_SHOW_TRAIL, false);

        overlayManager.add(overlay);
        overlayManager.add(tileOverlay);
        overlayManager.add(minimapOverlay);

        subscribe(HitsplatApplied.class, this::onHitsplatApplied);
        subscribe(ChatMessage.class, this::onChatMessage);
        subscribe(BeforeRender.class, this::onBeforeRender);
        subscribe(MenuOptionClicked.class, this::onMenuOptionClicked);
        subscribe(GameStateChanged.class, this::onGameStateChanged);
        subscribe(VarbitChanged.class, this::onVarbitChanged);
        subscribe(GameObjectSpawned.class, this::onGameObjectSpawned);
        subscribe(GameObjectDespawned.class, this::onGameObjectDespawned);
        subscribe(GroundObjectSpawned.class, this::onGroundObjectSpawned);
        subscribe(GroundObjectDespawned.class, this::onGroundObjectDespawned);
        subscribe(GameTick.class, this::onGameTick);
        subscribe(net.runelite.client.events.ConfigChanged.class, this::onConfigChanged);

        script.run(config, this);

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            // A stop before this runs unregisters the handlers but leaves it queued: it must not refill a stopped run.
            final int run = runId;
            clientThread.invokeLater(() ->
            {
                if (run != runId) return;
                inHerbiboarArea = checkArea();
                // A tunnel already found before we started is not a new step for the [HB-T] clock.
                finishVarbitSeen = client.getVarbitValue(VarbitID.HUNTING_TRAIL_ENDS_FOSSIL);
                updateTrailData();
                // What was already on the ground when we started is not a step we were timed on.
                script.stepTimer.reset();
                lastTrailChangeTick = -1;
                scanLoadedScene();
            });
        }
    }
    /** Spawn events for an already-loaded scene fired before we subscribed; without this the start rocks stay unknown until a loading screen. */
    private void scanLoadedScene()
    {
        Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
        for (Tile[][] plane : tiles)
        {
            for (Tile[] row : plane)
            {
                for (Tile tile : row)
                {
                    if (tile == null) continue;
                    for (GameObject object : tile.getGameObjects())
                    {
                        if (object != null) onTileObject(null, object);
                    }
                    if (tile.getGroundObject() != null) onTileObject(null, tile.getGroundObject());
                }
            }
        }
    }
    @Override
    protected void shutDown() {
        runId++;
        subscribers.forEach(eventBus::unregister);
        subscribers.clear();
        // The script first: a pass still running would act, or write to the session, after it is filed.
        script.shutdown();
        script.awaitPassEnd(2000);
        // A pass that was finishing a bank exit can lock breaks again after shutdown unlocked them.
        net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.setLockState(false);
        HerbiboarSession ending = session;
        String why = stopReason == null ? "stopped" : stopReason;
        finishSession();
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
        if (coreShowTrail == null) Microbot.getConfigManager().unsetConfiguration(CORE_GROUP, CORE_SHOW_TRAIL);
        else Microbot.getConfigManager().setConfiguration(CORE_GROUP, CORE_SHOW_TRAIL, coreShowTrail);
        // Last, so nothing of this run is written after its stop line.
        hbLog.stop(ending == null ? 0 : (System.currentTimeMillis() - ending.startMs) / 60_000,
            ending == null ? 0 : ending.caught, why);
    }

    /** The log setting takes effect at once, without a restart. */
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event) {
        if (HerbiboarConfig.GROUP.equals(event.getGroup()) && "logDetail".equals(event.getKey())) {
            hbLog.setDetail(provideConfig(Microbot.getConfigManager()).logDetail());
        }
    }

    private <T> void subscribe(Class<T> type, java.util.function.Consumer<T> handler) {
        subscribers.add(eventBus.register(type, handler, 0f));
    }

    private volatile boolean trailConfused;

    /** An Ammonite Crab's hit interrupts a search with no message. */
    @Getter
    private volatile int lastHitTick = -1;

    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() == client.getLocalPlayer()) lastHitTick = client.getTickCount();
    }

    private volatile int lastTrailChangeTick = -1;
    private volatile int nothingTick = -1;

    /** "Nothing seems to be out of place" only means a wasted search when the trail did not move with it. Pure. */
    static boolean nothingWasReal(int messageTick, int lastTrailChangeTick) {
        return lastTrailChangeTick < messageTick - 1;
    }

    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) return;
        String msg = chatMessage.getMessage();
        if (inHerbiboarArea) hbLog.detail("CHAT", String.format("%s", net.runelite.client.util.Text.removeTags(msg)));
        // Judged on the next tick: at one muddy patch the game sends this on the very tick the trail advances.
        if (inHerbiboarArea && msg.contains("Nothing seems to be out of place")) nothingTick = client.getTickCount();
        // contains: the real line is longer ("The creature has successfully confused you with its tracks...").
        if (msg.contains("confused you with its tracks") || msg.contains("need to start again")) {
            trailConfused = true;
        }
    }

    String trailDump()
    {
        StringBuilder sb = new StringBuilder();
        for (HerbiboarSearchSpot spot : HerbiboarSearchSpot.values())
        {
            for (TrailToSpot trail : spot.getTrails())
            {
                int v = client.getVarbitValue(trail.getVarbitId());
                if (v > 0) sb.append(spot.name()).append('=').append(v).append(v == trail.getValue() ? "* " : " ");
            }
        }
        sb.append("| finish=").append(client.getVarbitValue(VarbitID.HUNTING_TRAIL_ENDS_FOSSIL)).append(" | path=");
        for (HerbiboarSearchSpot spot : currentPath) sb.append(spot.name()).append(' ');
        return sb.toString().trim();
    }

    public boolean consumeTrailConfused()
    {
        if (!trailConfused) return false;
        trailConfused = false;
        return true;
    }

    private void updateTrailData()
    {
        if (!isInHerbiboarArea())
        {
            return;
        }

        boolean pathActive = false;
        TrailToSpot trailBefore = nextTrail;
        int finishBefore = finishVarbitSeen;
        for (HerbiboarSearchSpot spot : HerbiboarSearchSpot.values())
        {
            for (TrailToSpot trail : spot.getTrails())
            {
                int value = client.getVarbitValue(trail.getVarbitId());

                if (value == trail.getValue())
                {
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
                    shownTrails.addAll(trail.getFootprintIds());
                    pathActive = true;
                }
            }
        }

        finishId = finishVarbitSeen = client.getVarbitValue(VarbitID.HUNTING_TRAIL_ENDS_FOSSIL);
        // A cleared trail opens nothing: only a real new step starts the [HB-T] clock.
        if (finishId > 0 && finishBefore == 0) {
            script.stepTimer.landed(System.currentTimeMillis(), "TUNNEL");
            lastTrailChangeTick = client.getTickCount();
        } else if (nextTrail != null && nextTrail != trailBefore) {
            script.stepTimer.landed(System.currentTimeMillis(), "TRAIL");
            lastTrailChangeTick = client.getTickCount();
        }

        // The started varbit is only set after the first spot is searched, so a current group also counts as started.
        started = client.getVarbitValue(VarbitID.HUNTING_TRAILS_USED_FOSSIL) > 0 || currentGroup != null;
        if (!pathActive && started)
        {
            resetTrailData();
        }
    }

    @Getter
    private volatile String lastClickedOption = "";
    @Getter
    private volatile int lastClickedId = -1;
    @Getter
    private volatile int clickCount;

    /** As of the last finished frame: mid-rebuild reads show "Examine". */
    @Getter
    private volatile MenuEntry topEntry;

    public void onBeforeRender(BeforeRender event)
    {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        MenuEntry e = entries.length == 0 ? null : entries[entries.length - 1];
        // A copy: the client reuses entry objects.
        topEntry = e == null ? null : new NewMenuEntry(e.getOption(), e.getParam0(), e.getParam1(), e.getType().getId(),
                e.getIdentifier(), e.getItemId(), e.getTarget());
    }

    public void onMenuOptionClicked(MenuOptionClicked menuOpt)
    {
        lastClickedOption = menuOpt.getMenuOption();
        lastClickedId = menuOpt.getId();
        clickCount++;
    }

    static int routeAheadFrom(List<WorldPoint> route, WorldPoint me)
    {
        if (route == null || route.size() < 2 || me == null) return -1;
        if (me.distanceTo(route.get(route.size() - 1)) <= 1) return -1;
        int best = -1, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < route.size(); i++)
        {
            WorldPoint p = route.get(i);
            int d = p.getPlane() == me.getPlane() ? p.distanceTo(me) : Integer.MAX_VALUE;
            if (d < bestDist)
            {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private void resetTrailData()
    {
        hbLog.detail("TRAIL", "Reset trail data");
        shownTrails.clear();
        currentPath.clear();
        nextTrail = null;
        currentGroup = null;
        finishId = 0;
        started = false;
    }

    private void clearCache()
    {
        starts.clear();
        trailObjects.clear();
        tunnels.clear();
    }

    static boolean reenteredArea(boolean wasIn, boolean nowIn)
    {
        return !wasIn && nowIn;
    }

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
                boolean was = inHerbiboarArea;
                inHerbiboarArea = checkArea();
                // Back from a G.E. trip: the trail varbits changed while we were away, so read them again.
                if (reenteredArea(was, inHerbiboarArea))
                {
                    updateTrailData();
                    script.stepTimer.reset();
                    lastTrailChangeTick = -1;
                    hbLog.event("RECOVER", String.format("Back in the hunting area: trail %s, finish %d",
                        currentGroup == null ? "none" : currentGroup.name(), finishId));
                }
                break;
            default:
                break;
        }
    }

    public void onVarbitChanged(VarbitChanged event)
    {
        updateTrailData();
    }

    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        onTileObject(null, event.getGameObject());
    }

    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        onTileObject(event.getGameObject(), null);
    }

    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        onTileObject(null, event.getGroundObject());
    }

    public void onGroundObjectDespawned(GroundObjectDespawned event)
    {
        onTileObject(event.getGroundObject(), null);
    }

    private void onTileObject(TileObject oldObject, TileObject newObject)
    {
        if (oldObject != null)
        {
            WorldPoint oldLocation = oldObject.getWorldLocation();
            // Only the object that went: another one on the same tile (grass on a search spot) stays cached.
            starts.remove(oldLocation, oldObject);
            trailObjects.remove(oldLocation, oldObject);
            tunnels.remove(oldLocation, oldObject);
        }

        if (newObject == null)
        {
            return;
        }

        if (START_OBJECT_IDS.contains(newObject.getId()))
        {
            starts.put(newObject.getWorldLocation(), newObject);
            return;
        }

        if (!HerbiboarSearchSpot.cacheable(newObject.getId()))
        {
            return;
        }

        if (HerbiboarSearchSpot.isSearchSpot(newObject.getWorldLocation()))
        {
            trailObjects.put(newObject.getWorldLocation(), newObject);
            return;
        }

        if (END_LOCATIONS.contains(newObject.getWorldLocation()))
        {
            tunnels.put(newObject.getWorldLocation(), newObject);
        }
    }

    public int calculateXpPerHour() {
        int currentXP = client.getSkillExperience(Skill.HUNTER);
        if (getStartXp() <= 0) {
            // At the login screen Hunter reads 0: the session begins with the first real reading.
            if (currentXP <= 0) return 0;
            setStartXp(currentXP);
            setStartTime(Instant.now());
            HerbiboarSession s = session;
            if (s != null && s.caught == 0) s.beginAt(System.currentTimeMillis());
            // At the login screen the level is not the account's either.
            if (s != null) s.startLevel = client.getRealSkillLevel(Skill.HUNTER);
        }
        setXpGained(currentXP - getStartXp());
        if (getRunningTime() <= 1) return 0;
        return (int) (getXpGained() * 3600L / getRunningTime());
    }

    public void onGameTick(GameTick event) {
        if (nothingTick != -1 && client.getTickCount() > nothingTick) {
            if (nothingWasReal(nothingTick, lastTrailChangeTick)) hbLog.event("NOTHING", "trail: " + trailDump());
            nothingTick = -1;
        }
        setRunningTime(Instant.now().getEpochSecond() - getStartTime().getEpochSecond());
        setXpPerHour(calculateXpPerHour());
        HerbiboarSession s = session;
        long now = System.currentTimeMillis();
        if (s != null && now - lastCheckpointMs >= CHECKPOINT_MS) {
            lastCheckpointMs = now;
            fillTotals(s);
            HerbiboarSession.checkpoint(HerbiboarSession.dir(), gson, s);
        }
    }

    private void fillTotals(HerbiboarSession s) {
        Integer xp = getXpGained();
        s.xpGained = xp == null ? 0 : xp;
        s.endLevel = client.getRealSkillLevel(Skill.HUNTER);
    }

    private void finishSession() {
        HerbiboarSession s = session;
        session = null;
        if (s == null) return;
        s.endMs = System.currentTimeMillis();
        s.resume(s.endMs);
        fillTotals(s);
        s.stopReason = stopReason == null ? "stopped" : stopReason;
        if (s.caught == 0 && s.durationMs(s.endMs) < 60_000) return;
        java.io.File dir = HerbiboarSession.dir();
        HerbiboarSession.saveFinished(dir, gson, s);
        double rate = s.catchesPerHour(s.endMs);
        long ms = Math.max(1, s.durationMs(s.endMs));
        hbLog.event("SESSION", String.format("Herbiboar session saved: %s, %d caught (%s/hr), %,d Hunter XP (%,d/hr), %d bank trips; %s play style; %s -> %s",
                HerbiboarOverlay.hoursMinutes(s.durationMs(s.endMs)), s.caught, rate < 0 ? "-" : String.valueOf(Math.round(rate)),
                s.xpGained, s.xpGained * 3_600_000L / ms, s.bankTrips, s.playStyle, s.stopReason, HerbiboarSession.historyFile(dir).getName()));
    }

    private boolean checkArea()
    {
        final WorldView wv = client.getTopLevelWorldView();
        if (wv == null) {
            return false;
        }
        final int[] mapRegions = wv.getMapRegions();
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
