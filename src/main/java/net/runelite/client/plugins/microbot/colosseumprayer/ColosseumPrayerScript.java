package net.runelite.client.plugins.microbot.colosseumprayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Arbiter-driven threats on {@link GameTick}; manticore triple uses {@link ProjectileMoved} signals.
 */
@Slf4j
@Singleton
public class ColosseumPrayerScript {

    private static final int TILE_RANGE_MELEE_THREAT = 3;
    private static final int TILE_RANGE_RANGED_MAGING = 18;

    private final EventBus eventBus;
    private final ColosseumPrayerConfig config;
    private final Map<Integer, ManticoreCycleState> manticoreStates = new HashMap<>();
    private final Map<Long, Integer> seenManticoreLaunches = new HashMap<>();
    private boolean registered;
    /** Suppress duplicate toggles within the same game tick during manticore volleys (styles are one tick apart). */
    private int lastManticoreSwitchGameTick = -1;
    private int lastManticoreDebugTick = -1;
    private int lastAnyPrayerSwitchTick = -1;
    private Rs2PrayerEnum lastAnyPrayerSwitchPrayer;

    @Inject
    ColosseumPrayerScript(EventBus eventBus, ColosseumPrayerConfig config) {
        this.eventBus = eventBus;
        this.config = config;
    }

    void register() {
        if (registered) {
            return;
        }
        eventBus.register(this);
        registered = true;
    }

    void unregister() {
        if (!registered) {
            return;
        }
        eventBus.unregister(this);
        registered = false;
        lastManticoreSwitchGameTick = -1;
        lastManticoreDebugTick = -1;
        lastAnyPrayerSwitchTick = -1;
        lastAnyPrayerSwitchPrayer = null;
        manticoreStates.clear();
        seenManticoreLaunches.clear();
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!config.helperEnabled()) {
            return;
        }
        if (!Microbot.isLoggedIn()) {
            return;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            return;
        }
        Projectile projectile = event.getProjectile();
        if (projectile == null) {
            return;
        }
        Rs2PrayerEnum incoming = ManticoreProjectilePrayers.prayerForProjectileId(projectile.getId());
        if (incoming == null) {
            return;
        }
        if (manticoreStates.isEmpty()) {
            return;
        }
        if (projectile.getRemainingCycles() <= 0) {
            return;
        }
        int tickNow = client.getTickCount();
        WorldPoint targetPoint = projectile.getTargetPoint();
        long launchKey = buildManticoreLaunchKey(projectile.getId(), projectile.getStartCycle(), targetPoint);
        Integer seenTick = seenManticoreLaunches.get(launchKey);
        if (seenTick != null) {
            return;
        }
        seenManticoreLaunches.put(launchKey, tickNow);
        ManticoreCycleState state = selectLaunchState(tickNow);
        if (state == null) {
            return;
        }
        state.markSeen(tickNow);
        state.onProjectileLaunch(incoming, tickNow);
        Rs2PrayerEnum nextPrayer = state.currentDesiredPrayer();
        if (nextPrayer == null) {
            nextPrayer = incoming;
        }
        switchManticoreProtection(client, nextPrayer);
        if (config.debugLogSignals()) {
            log.info("[ColosseumPrayer] Manticore idx={} projectile={} launched={} next={}",
                    state.npcIndex, projectile.getId(), incoming, nextPrayer);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!Microbot.isLoggedIn()) {
            return;
        }
        if (!config.helperEnabled()) {
            return;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            return;
        }
        refreshManticoreStates(client);
        Rs2PrayerEnum manticorePrayer = resolveManticorePrayer(client);
        if (manticorePrayer != null) {
            switchManticoreProtection(client, manticorePrayer);
            if (config.debugLogSignals()) {
                int tickNow = client.getTickCount();
                if (tickNow != lastManticoreDebugTick) {
                    log.info("[ColosseumPrayer] MantiTick tick={} choose={} states={}",
                            tickNow, manticorePrayer, formatManticoreStates());
                    lastManticoreDebugTick = tickNow;
                }
            }
            return;
        }
        if (!hasTrackedThreatNpcInScene()) {
            return;
        }
        List<ColosseumPrayerDemand> demands = gatherDemands();
        Player localPlayer = client.getLocalPlayer();
        if (shouldFlickJavelinWithSerpentShaman(demands, localPlayer)) {
            Client clientForFlick = Microbot.getClient();
            if (clientForFlick != null) {
                applyJavelinSerpentShamanFlickPrayer(clientForFlick);
            }
            if (config.debugLogSignals()) {
                log.info("[ColosseumPrayer] Javelin + Serpent Shaman flick tick={}",
                        Microbot.getClient() != null ? Microbot.getClient().getTickCount() : -1);
            }
            return;
        }
        if (shouldForceSerpentMagePrayer(demands, localPlayer)) {
            trySwitchPrayer(client, Rs2PrayerEnum.PROTECT_MAGIC);
            if (config.debugLogSignals()) {
                log.info("[ColosseumPrayer] LOS override: shaman visible, javelin blocked -> PROTECT_MAGIC");
            }
            return;
        }
        Rs2PrayerEnum resolved = ColosseumPrayerArbiter.resolve(demands);
        if (resolved == null) {
            if (config.debugLogSignals()) {
                log.info("[ColosseumPrayer] Tick: tracked threats nearby but no active demand (interact/range)");
            }
            return;
        }
        trySwitchPrayer(client, resolved);
        if (config.debugLogSignals()) {
            log.info("[ColosseumPrayer] demands={} resolved={}", demands.size(), resolved);
        }
    }

    private void refreshManticoreStates(Client client) {
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            manticoreStates.clear();
            seenManticoreLaunches.clear();
            return;
        }
        int tickNow = client.getTickCount();
        seenManticoreLaunches.entrySet().removeIf(e -> tickNow - e.getValue() > 8);
        Set<Integer> liveIndexes = new HashSet<>();
        for (NPC npc : worldView.npcs()) {
            if (npc == null) {
                continue;
            }
            if (npc.isDead()) {
                continue;
            }
            if (!isManticoreName(npc.getName())) {
                continue;
            }
            int npcIndex = npc.getIndex();
            liveIndexes.add(npcIndex);
            ManticoreCycleState state = manticoreStates.computeIfAbsent(npcIndex, ManticoreCycleState::new);
            state.markSeen(tickNow);
            List<Rs2PrayerEnum> visualOrder = extractManticoreOrbOrder(npc);
            if (visualOrder.size() == 3) {
                state.updateVisualOrder(visualOrder, tickNow);
            }
            Player local = client.getLocalPlayer();
            if (local != null) {
                WorldPoint npcWp = npc.getWorldLocation();
                WorldPoint playerWp = local.getWorldLocation();
                if (npcWp != null && playerWp != null) {
                    state.distanceToPlayer = playerWp.distanceTo(npcWp);
                }
                WorldArea npcArea = npc.getWorldArea();
                WorldArea playerArea = local.getWorldArea();
                if (npcArea != null && playerArea != null) {
                    state.hasLineOfSight = playerArea.hasLineOfSightTo(worldView, npcArea);
                } else {
                    state.hasLineOfSight = false;
                }
            } else {
                state.hasLineOfSight = false;
            }
        }
        manticoreStates.entrySet().removeIf(e -> {
            ManticoreCycleState state = e.getValue();
            if (!liveIndexes.contains(e.getKey())) {
                return true;
            }
            return state.isStale(tickNow);
        });
    }

    private Rs2PrayerEnum resolveManticorePrayer(Client client) {
        if (manticoreStates.isEmpty()) {
            return null;
        }
        int tickNow = client.getTickCount();
        ManticoreCycleState best = null;
        for (ManticoreCycleState state : manticoreStates.values()) {
            if (!state.hasLineOfSight) {
                continue;
            }
            Rs2PrayerEnum desired = state.currentDesiredPrayer();
            if (desired == null) {
                continue;
            }
            if (best == null) {
                best = state;
                continue;
            }
            if (state.beats(best, tickNow)) {
                best = state;
            }
        }
        if (best == null) {
            return null;
        }
        return best.currentDesiredPrayer();
    }

    private static List<Rs2PrayerEnum> extractManticoreOrbOrder(NPC npc) {
        List<ManticoreOrbVisual> visuals = new ArrayList<>();
        for (ActorSpotAnim spotAnim : npc.getSpotAnims()) {
            if (spotAnim == null) {
                continue;
            }
            Rs2PrayerEnum prayer = ManticoreProjectilePrayers.prayerForProjectileId(spotAnim.getId());
            if (prayer == null) {
                continue;
            }
            visuals.add(new ManticoreOrbVisual(prayer, spotAnim.getHeight(), spotAnim.getStartCycle()));
        }
        visuals.sort(Comparator
                .comparingInt((ManticoreOrbVisual v) -> v.height)
                .thenComparingInt(v -> v.startCycle));
        List<Rs2PrayerEnum> order = new ArrayList<>();
        for (ManticoreOrbVisual visual : visuals) {
            order.add(visual.prayer);
        }
        return order;
    }

    private static boolean isManticoreName(String name) {
        if (name == null) {
            return false;
        }
        return name.toLowerCase().contains("manticore");
    }

    private static long buildManticoreLaunchKey(int projectileId, int startCycle, WorldPoint targetPoint) {
        long key = 17;
        key = key * 31 + projectileId;
        key = key * 31 + startCycle;
        if (targetPoint != null) {
            key = key * 31 + targetPoint.getX();
            key = key * 31 + targetPoint.getY();
            key = key * 31 + targetPoint.getPlane();
        }
        return key;
    }

    private ManticoreCycleState selectLaunchState(int tickNow) {
        ManticoreCycleState best = null;
        for (ManticoreCycleState candidate : manticoreStates.values()) {
            if (!candidate.hasLineOfSight) {
                continue;
            }
            if (candidate.currentDesiredPrayer() == null) {
                continue;
            }
            if (best == null || candidate.beats(best, tickNow)) {
                best = candidate;
            }
        }
        return best;
    }

    private String formatManticoreStates() {
        if (manticoreStates.isEmpty()) {
            return "none";
        }
        List<ManticoreCycleState> sorted = new ArrayList<>(manticoreStates.values());
        sorted.sort(Comparator.comparingInt(s -> s.npcIndex));
        List<String> chunks = new ArrayList<>();
        for (ManticoreCycleState state : sorted) {
            chunks.add(state.describe());
        }
        return String.join(" | ", chunks);
    }

    private void switchManticoreProtection(Client client, Rs2PrayerEnum prayer) {
        int tickNow = client.getTickCount();
        if (Rs2Prayer.isPrayerActive(prayer)) {
            return;
        }
        if (tickNow == lastManticoreSwitchGameTick) {
            return;
        }
        if (!trySwitchPrayer(client, prayer)) {
            return;
        }
        lastManticoreSwitchGameTick = tickNow;
    }

    /**
     * Alternate Protect Missiles and Protect Magic when both ranged javelin autos and serpent shamans threaten.
     * Skipped while a jaguar warrior also demands melee (fallback to arbiter).
     */
    private boolean shouldFlickJavelinWithSerpentShaman(List<ColosseumPrayerDemand> demands, Player player) {
        if (!demandHasThreat(demands, ColosseumPrayerThreat.JAVELIN_COLOSSUS)) {
            return false;
        }
        if (!demandHasThreat(demands, ColosseumPrayerThreat.SERPENT_SHAMAN)) {
            return false;
        }
        if (demandHasThreat(demands, ColosseumPrayerThreat.JAGUAR_WARRIOR)) {
            return false;
        }
        if (!hasLineOfSightThreat(ThreatKind.JAVELIN_COLOSSUS, player)) {
            return false;
        }
        if (!hasLineOfSightThreat(ThreatKind.SERPENT_SHAMAN, player)) {
            return false;
        }
        return true;
    }

    private boolean shouldForceSerpentMagePrayer(List<ColosseumPrayerDemand> demands, Player player) {
        if (!demandHasThreat(demands, ColosseumPrayerThreat.JAVELIN_COLOSSUS)) {
            return false;
        }
        if (!demandHasThreat(demands, ColosseumPrayerThreat.SERPENT_SHAMAN)) {
            return false;
        }
        boolean shamanLos = hasLineOfSightThreat(ThreatKind.SERPENT_SHAMAN, player);
        boolean javelinLos = hasLineOfSightThreat(ThreatKind.JAVELIN_COLOSSUS, player);
        if (shamanLos && !javelinLos) {
            return true;
        }
        return false;
    }

    private static boolean demandHasThreat(List<ColosseumPrayerDemand> demands, ColosseumPrayerThreat threat) {
        for (ColosseumPrayerDemand d : demands) {
            if (d.threat() == threat) {
                return true;
            }
        }
        return false;
    }

    /**
     * Even game ticks: Protect Missiles (javelin autos). Odd ticks: Protect Magic (serpent shaman).
     */
    private void applyJavelinSerpentShamanFlickPrayer(Client client) {
        int tick = client.getTickCount();
        Rs2PrayerEnum want;
        if (tick % 2 == 0) {
            want = Rs2PrayerEnum.PROTECT_RANGE;
        } else {
            want = Rs2PrayerEnum.PROTECT_MAGIC;
        }
        if (Rs2Prayer.isPrayerActive(want)) {
            return;
        }
        trySwitchPrayer(client, want);
    }

    private boolean trySwitchPrayer(Client client, Rs2PrayerEnum prayer) {
        if (client == null || prayer == null) {
            return false;
        }
        int tick = client.getTickCount();
        if (Rs2Prayer.isPrayerActive(prayer)) {
            return false;
        }
        if (tick == lastAnyPrayerSwitchTick) {
            if (prayer == lastAnyPrayerSwitchPrayer) {
                return false;
            }
            return false;
        }
        boolean ok = Rs2Prayer.toggle(prayer, true, false);
        if (ok) {
            lastAnyPrayerSwitchTick = tick;
            lastAnyPrayerSwitchPrayer = prayer;
        }
        return ok;
    }

    private boolean hasLineOfSightThreat(ThreatKind kind, Player player) {
        if (player == null) {
            return false;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            return false;
        }
        for (Rs2NpcModel npc : Microbot.getRs2NpcCache().query().toList()) {
            if (npc == null || npc.isDead()) {
                continue;
            }
            if (classifyThreat(npc) != kind) {
                continue;
            }
            WorldArea npcArea = npc.getWorldArea();
            WorldArea playerArea = player.getWorldArea();
            if (npcArea == null || playerArea == null) {
                continue;
            }
            if (playerArea.hasLineOfSightTo(worldView, npcArea)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTrackedThreatNpcInScene() {
        if (Microbot.getClient() == null) {
            return false;
        }
        for (Rs2NpcModel npc : Microbot.getRs2NpcCache().query().toList()) {
            if (npc == null) {
                continue;
            }
            if (npc.isDead()) {
                continue;
            }
            ThreatKind kind = classifyThreat(npc);
            if (kind != null) {
                return true;
            }
        }
        return false;
    }

    List<ColosseumPrayerDemand> gatherDemands() {
        Player player = Microbot.getClient() != null ? Microbot.getClient().getLocalPlayer() : null;
        List<ColosseumPrayerDemand> demands = new ArrayList<>();
        if (player == null) {
            return demands;
        }
        WorldPoint playerWp = Rs2Player.getWorldLocation();
        boolean instanced = isInstanced();

        for (Rs2NpcModel npc : Microbot.getRs2NpcCache().query().toList()) {
            if (npc == null) {
                continue;
            }
            if (npc.isDead()) {
                continue;
            }
            ThreatKind kind = classifyThreat(npc);
            if (kind == null) {
                continue;
            }
            if (kind == ThreatKind.MANTICORE) {
                continue;
            }
            WorldPoint npcWp = npcWorldLocation(npc, instanced);
            if (npcWp == null) {
                continue;
            }
            int dist = playerWp.distanceTo(npcWp);

            if (kind == ThreatKind.JAVELIN_COLOSSUS) {
                if (threatEligible(player, npc, dist, TILE_RANGE_RANGED_MAGING)) {
                    demands.add(new ColosseumPrayerDemand(ColosseumPrayerThreat.JAVELIN_COLOSSUS,
                            Rs2PrayerEnum.PROTECT_RANGE));
                }
                continue;
            }
            if (kind == ThreatKind.SERPENT_SHAMAN) {
                if (threatEligible(player, npc, dist, TILE_RANGE_RANGED_MAGING)) {
                    demands.add(new ColosseumPrayerDemand(ColosseumPrayerThreat.SERPENT_SHAMAN,
                            Rs2PrayerEnum.PROTECT_MAGIC));
                }
                continue;
            }
            if (kind == ThreatKind.SHOCKWAVE_COLOSSUS) {
                if (threatEligible(player, npc, dist, TILE_RANGE_RANGED_MAGING)) {
                    demands.add(new ColosseumPrayerDemand(ColosseumPrayerThreat.SHOCKWAVE_COLOSSUS,
                            Rs2PrayerEnum.PROTECT_MAGIC));
                }
                continue;
            }
            if (kind == ThreatKind.JAGUAR_WARRIOR) {
                if (threatEligible(player, npc, dist, TILE_RANGE_MELEE_THREAT)) {
                    demands.add(new ColosseumPrayerDemand(ColosseumPrayerThreat.JAGUAR_WARRIOR,
                            Rs2PrayerEnum.PROTECT_MELEE));
                }
            }
        }
        return demands;
    }

    private enum ThreatKind {
        MANTICORE,
        JAVELIN_COLOSSUS,
        SERPENT_SHAMAN,
        SHOCKWAVE_COLOSSUS,
        JAGUAR_WARRIOR
    }

    private static ThreatKind classifyThreat(Rs2NpcModel npc) {
        if (npc == null) {
            return null;
        }
        String name = npc.getName();
        if (name == null) {
            return null;
        }
        String normalized = name.toLowerCase();
        if (normalized.contains("manticore")) {
            return ThreatKind.MANTICORE;
        }
        if (normalized.contains("serpent")) {
            if (normalized.contains("shaman")) {
                return ThreatKind.SERPENT_SHAMAN;
            }
        }
        if (normalized.contains("shockwave")) {
            return ThreatKind.SHOCKWAVE_COLOSSUS;
        }
        if (normalized.contains("colossus")) {
            return ThreatKind.JAVELIN_COLOSSUS;
        }
        if (normalized.contains("javelin")) {
            return ThreatKind.JAVELIN_COLOSSUS;
        }
        if (normalized.contains("jaguar")) {
            return ThreatKind.JAGUAR_WARRIOR;
        }
        return null;
    }

    private static boolean threatEligible(Player player, Rs2NpcModel npc, int chebyshevTiles, int maxRangeTiles) {
        if (npc.isInteractingWithPlayer()) {
            return true;
        }
        Actor interacting = npc.getInteracting();
        if (Objects.equals(interacting, player)) {
            return true;
        }
        return chebyshevTiles <= maxRangeTiles;
    }

    private static boolean isInstanced() {
        Client c = Microbot.getClient();
        if (c == null) {
            return false;
        }
        WorldView wv = c.getTopLevelWorldView();
        if (wv == null) {
            return false;
        }
        return wv.getScene().isInstance();
    }

    private static WorldPoint npcWorldLocation(Rs2NpcModel npc, boolean instanced) {
        if (npc == null) {
            return null;
        }
        if (instanced) {
            LocalPoint lp = npc.getLocalLocation();
            Client c = Microbot.getClient();
            if (lp == null) {
                return null;
            }
            if (c == null) {
                return null;
            }
            WorldPoint wp = WorldPoint.fromLocalInstance(c, lp);
            return wp;
        }
        return npc.getWorldLocation();
    }

    private static final class ManticoreOrbVisual {
        private final Rs2PrayerEnum prayer;
        private final int height;
        private final int startCycle;

        private ManticoreOrbVisual(Rs2PrayerEnum prayer, int height, int startCycle) {
            this.prayer = prayer;
            this.height = height;
            this.startCycle = startCycle;
        }
    }

    private static final class ManticoreCycleState {
        private final int npcIndex;
        private final List<Rs2PrayerEnum> orbOrder = new ArrayList<>(3);
        private int nextPrayerIndex;
        private int lastProjectileTick = -1000;
        private int lastSeenTick = -1000;
        private int distanceToPlayer = 99;
        private boolean hasLineOfSight;

        private ManticoreCycleState(int npcIndex) {
            this.npcIndex = npcIndex;
        }

        private void updateVisualOrder(List<Rs2PrayerEnum> order, int tickNow) {
            if (!orbOrder.equals(order)) {
                orbOrder.clear();
                orbOrder.addAll(order);
                nextPrayerIndex = 0;
            }
            lastSeenTick = tickNow;
        }

        private void onProjectileLaunch(Rs2PrayerEnum launchedPrayer, int tickNow) {
            lastProjectileTick = tickNow;
            lastSeenTick = tickNow;
            if (orbOrder.isEmpty()) {
                return;
            }
            int launchedIndex = orbOrder.indexOf(launchedPrayer);
            if (launchedIndex >= 0) {
                nextPrayerIndex = (launchedIndex + 1) % orbOrder.size();
                return;
            }
            nextPrayerIndex = (nextPrayerIndex + 1) % orbOrder.size();
        }

        private Rs2PrayerEnum currentDesiredPrayer() {
            if (orbOrder.isEmpty()) {
                return null;
            }
            if (nextPrayerIndex < 0 || nextPrayerIndex >= orbOrder.size()) {
                nextPrayerIndex = 0;
            }
            return orbOrder.get(nextPrayerIndex);
        }

        private void markSeen(int tickNow) {
            lastSeenTick = tickNow;
        }

        private boolean isStale(int tickNow) {
            return tickNow - lastSeenTick > 10;
        }

        private boolean beats(ManticoreCycleState other, int tickNow) {
            boolean thisRecentlyLaunched = tickNow - this.lastProjectileTick <= 2;
            boolean otherRecentlyLaunched = tickNow - other.lastProjectileTick <= 2;
            if (thisRecentlyLaunched != otherRecentlyLaunched) {
                return thisRecentlyLaunched;
            }
            if (this.lastProjectileTick != other.lastProjectileTick) {
                return this.lastProjectileTick > other.lastProjectileTick;
            }
            if (this.distanceToPlayer != other.distanceToPlayer) {
                return this.distanceToPlayer < other.distanceToPlayer;
            }
            return this.npcIndex < other.npcIndex;
        }

        private String describe() {
            return "idx=" + npcIndex +
                    ",next=" + currentDesiredPrayer() +
                    ",order=" + orbOrder +
                    ",dist=" + distanceToPlayer +
                    ",los=" + hasLineOfSight +
                    ",lastProjTick=" + lastProjectileTick;
        }
    }
}
