package net.runelite.client.plugins.microbot.drozulrah.helper;

import java.awt.image.BufferedImage;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drozulrah.*;
import net.runelite.client.plugins.microbot.drozulrah.helper.constants.StandLocation;
import net.runelite.client.plugins.microbot.drozulrah.helper.overlays.*;
import net.runelite.client.plugins.microbot.drozulrah.helper.rotationutils.*;

/** Adapter for Microbot-Hub's Zulrah overlays (originally Owain van Brakel / Syntax).
 * Uses the combat worker's immutable phase snapshot; performs no combat inputs. */
@Singleton
public final class ZulrahRotationHelper
{
    public static final BufferedImage[] ZULRAH_IMAGES = {
        ZulrahImages.load("zulrah_range.png"),
        ZulrahImages.load("zulrah_melee.png"),
        ZulrahImages.load("zulrah_magic.png")
    };
    public static final ZulrahConfig DEFAULTS = new ZulrahConfig() {
        @Override public boolean phaseRotationName() { return true; }
        @Override public boolean phaseTickCounter() { return true; }
        @Override public boolean displayToxicClouds() { return true; }
    };
    @Inject private Client client;
    @Inject private DroZulrahScript script;
    @Inject private DroZulrahConfig config;
    private InstanceTimerOverlay timer;
    private NPC npc;
    private int attackTicks = -1;
    private final Map<LocalPoint, Integer> projectiles = new HashMap<>();
    private final Map<GameObject, Integer> clouds = new HashMap<>();

    public void start(EventBus events, InstanceTimerOverlay timer) {
        this.timer = timer;
        events.register(this);
    }
    public void stop(EventBus events) {
        events.unregister(this);
        reset();
        timer = null;
    }
    public boolean showOverlay() { return !config.hideOverlay() && config.showRotationHelperOverlay(); }
    private void reset() { npc = null; attackTicks = -1; clouds.clear(); projectiles.clear(); if (timer != null) timer.resetTimer(); }

    @Subscribe public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC) || !"Zulrah".equalsIgnoreCase(event.getActor().getName())) return;
        npc = (NPC) event.getActor();
        switch (npc.getAnimation()) {
            case 5071: if (timer != null) timer.setTimer(); break;
            case 5069: attackTicks = 4; break;
            case 5806: case 5807: attackTicks = 8; break;
            case 5804: reset(); break;
        }
    }
    @Subscribe public void onGameTick(GameTick event) {
        if (npc == null && script.getHelperSnapshot().index >= 0) {
            net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel cached =
                    Microbot.getRs2NpcCache().query().withName("Zulrah").first();
            npc = cached == null ? null : cached.getNpc();
        }
        if (attackTicks > 0) attackTicks--;
        clouds.values().removeIf(t -> t <= 1); clouds.replaceAll((p, t) -> t - 1);
        projectiles.values().removeIf(t -> t <= 1); projectiles.replaceAll((p, t) -> t - 1);
    }
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event.getGameObject().getId() == 11700) clouds.put(event.getGameObject(), 30);
    }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { clouds.remove(event.getGameObject()); }
    @Subscribe public void onProjectileMoved(ProjectileMoved event) {
        if (npc != null && (event.getProjectile().getId() == 1045 || event.getProjectile().getId() == 1047))
            projectiles.put(event.getPosition(), event.getProjectile().getRemainingCycles() / 30);
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.HOPPING
                || event.getGameState() == GameState.CONNECTION_LOST) reset();
    }
    public NPC getZulrahNpc() { return script.getHelperSnapshot().index < 0 ? null : npc; }
    public int getPhaseTicks() {
        ZulrahPhaseSnapshot snapshot = script.getHelperSnapshot();
        if (snapshot.index < 0) return -1;
        return Math.max(0, snapshot.duration - (client.getTickCount() - snapshot.startTick));
    }
    public int getAttackTicks() { return attackTicks; }
    public RotationType getCurrentRotation() {
        ZulrahRotation rotation = script.getHelperSnapshot().rotation;
        return rotation == null ? null : RotationType.values()[rotation.ordinal()];
    }
    public Map<LocalPoint, Integer> getProjectilesMap() { return projectiles; }
    public Map<GameObject, Integer> getToxicCloudsMap() { return clouds; }
    public Set<ZulrahData> getZulrahData() {
        ZulrahPhaseSnapshot snapshot = script.getHelperSnapshot();
        Set<ZulrahData> data = new LinkedHashSet<>();
        if (snapshot.index < 0) return data;
        for (ZulrahRotation candidate : snapshot.candidates()) {
            List<ZulrahPhase> phases = RotationType.values()[candidate.ordinal()].getZulrahPhases();
            if (snapshot.index >= phases.size()) continue;
            ZulrahPhase current = phases.get(snapshot.index);
            ZulrahPhase next = snapshot.index + 1 < phases.size() ? phases.get(snapshot.index + 1) : null;
            boolean flipStand = false;
            if (client.getLocalPlayer() != null) {
                LocalPoint me = client.getLocalPlayer().getLocalLocation();
                StandLocation home = current.getAttributes().getStandLocation();
                StandLocation alternate = home == StandLocation.NORTHEAST_TOP ? StandLocation.NORTHEAST_BOTTOM
                        : home == StandLocation.WEST ? StandLocation.NORTHWEST_BOTTOM : home;
                flipStand = distance(me, alternate.toLocalPoint()) < distance(me, home.toLocalPoint());
            }
            data.add(new ZulrahData(current, next, flipStand, script.getHelperPrayer()));
        }
        return data;
    }
    private static int distance(LocalPoint a, LocalPoint b) { return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())); }
}
