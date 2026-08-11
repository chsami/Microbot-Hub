package net.runelite.client.plugins.custom.zulrah;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.custom.actions.Action;
import net.runelite.client.plugins.custom.actions.ActionScript;
import net.runelite.client.plugins.custom.zulrah.actions.FightContext;
import net.runelite.client.plugins.custom.zulrah.actions.LootAction;
import net.runelite.client.plugins.custom.zulrah.actions.ZulrahAction;
import net.runelite.client.plugins.custom.zulrah.actions.ZulrahState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.custom.zulrah.constants.StandLocation;
import net.runelite.client.plugins.custom.zulrah.constants.VenomTiming;
import net.runelite.client.plugins.custom.zulrah.rotationutils.ZulrahPhase;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;

/**
 * Zulrah's action-driven script. The generic pipeline wiring (discovery, runner, per-tick loop)
 * lives in {@link ActionScript}; here we only build the Zulrah {@link FightContext} and feed it the
 * plugin's animation events. Actions are auto-discovered from the {@code actions} package.
 */
@Slf4j
public class ZulrahScript extends ActionScript<ZulrahState> {

    @Inject
    private ZulrahConfig zulrahConfig;
    @Inject
    private ItemManager itemManager;

    private FightContext context;

    @Override
    protected Class<? extends Action<ZulrahState>> actionType() {
        return ZulrahAction.class;
    }

    @Override
    protected ZulrahState createState() {
        return context == null ? null : new ZulrahState(context);
    }

    @Override
    protected void onInitialize() {
        log.info("Initializing");
        context = new FightContext(itemManager);
        // Resolve the setups LIVE by name, not from the InventorySetup object the config returns: that
        // object is a snapshot serialized when the setup was picked in the dropdown, so editing the
        // setup afterwards (gear/potions/quantities) leaves it stale and the restock withdraws the old
        // layout. The String constructor looks the current setup up by name from the Inventory Setups
        // plugin. (Editing a setup while the plugin is running still needs a restart to re-resolve.)
        String magicName = zulrahConfig.mageInventorySetup().getName();
        String rangeName = zulrahConfig.rangeInventorySetup().getName();
        log.info("Using inventory setups — magic: '{}', range: '{}'", magicName, rangeName);
        context.setMagicSetup(new Rs2InventorySetup(magicName, mainScheduledFuture));
        context.setRangeSetup(new Rs2InventorySetup(rangeName, mainScheduledFuture));
        context.reset();
        armStartPoint();
    }

    /**
     * Seed the very first cycle based on the configured start point. "Beginning of fight" is a no-op:
     * we just wait for Zulrah to spawn (assumes we're already on the boat and have continued). The two
     * travel options arm ReturnToZulrahAction to run once on the first tick — "Banking" does the full
     * restock+travel trip, "Travel to Zulrah" skips banking and only travels back. Every later cycle is
     * armed by LootAction as usual (always a full trip).
     */
    private void armStartPoint() {
        CycleStartPoint start = zulrahConfig.cycleStartPoint();
        log.info("Start point: {}", start);
        switch (start) {
            case BANKING:
                context.setPrepSkipBank(false);
                context.setPrepPending(true);
                break;
            case TRAVEL_TO_ZULRAH:
                context.setPrepSkipBank(true);
                context.setPrepPending(true);
                break;
            case BEGINNING_OF_FIGHT:
            default:
                // No prep — the fight begins when Zulrah spawns (onFightStart / openingHold).
                break;
        }
    }

    @Override
    public void onShutdown() {
        // NOTE: onShutdown() is invoked *by* AbstractScript.shutdown(); do not call shutdown() here.
        reset();
    }

    // ---- Event delegates (called by ZulrahPlugin's @Subscribe handlers) ----

    /** Clears per-fight state on (re)start and on every plugin/fight reset. */
    public void reset() {
        if (context != null) {
            context.reset();
        }
    }

    public void setZulrahPhase(ZulrahPhase phase) {
        // null = rotation not yet identified / end of rotation: keep the current phase.
        if (context != null && phase != null) {
            context.setPhase(phase);
            context.setPhaseChanged(true);
        }
    }

    /** The next phase's stand tile (or null if unknown); used to pre-move out of end-of-phase venom. */
    public void setNextStandLocation(net.runelite.api.coords.WorldPoint next) {
        if (context != null) {
            context.setNextStandLocation(next);
        }
    }

    /**
     * A venom cloud spawned (GameObjectSpawned, client thread). We pre-move toward the next phase's
     * tile so we don't linger in the cloud that drops on our spot — but only once we're already
     * settled on the CURRENT phase's tile, so this never fights the positioning logic that gets us
     * to the current phase (otherwise an early cloud would drag us to the next phase before we've
     * even fought this one). Guarded to fire at most once per phase; the guard is re-armed on
     * resurface. We only set the target; the pipeline's positioning action does the walking on its
     * own thread, so we never fire mouse input concurrently with it.
     */
    public void onVenomCloudSpawned() {
        if (context == null || context.isVenomPreMoved() || context.isMeleeDodgePhase()) {
            return;
        }
        ZulrahPhase phase = context.getPhase();
        // Only pre-move for phases whose venom lands at the END. START-venom phases (e.g. a green form
        // spewing barrages the instant it surfaces) must stay put — our tile is the safespot, and
        // leaving early walks us into the clouds while the phase's attacks keep coming.
        if (phase == null || phase.getAttributes().getVenomTiming() != VenomTiming.END) {
            return;
        }
        WorldPoint next = context.getNextStandLocation();
        if (next == null) {
            return; // next phase's tile not known yet
        }
        WorldPoint current = context.getStandLocation();
        WorldPoint pos = Rs2Player.getWorldLocation();
        // Only pre-move once we're standing in the right place for the CURRENT phase. Until then the
        // reposition logic owns standLocation and we must not override it.
        boolean settledOnCurrent = current != null && pos != null
                && (pos.equals(current) || (!Rs2Player.isMoving() && pos.distanceTo(current) <= 1));
        if (!settledOnCurrent) {
            return;
        }
        if (pos.equals(next) || next.equals(current)) {
            return; // already there / already targeting it
        }
        log.info("[venom] cloud spawned while settled; pre-moving to next stand tile {}", next);
        context.setVenomPreMoved(true);
        context.setStandLocation(next);
    }

    /**
     * The fight is starting (Zulrah's NPC appeared). For the FIRST phase of the rotation we hold at
     * the spawn spot and get the opening attack off, only heading to the first stand tile once that
     * attack has actually fired (see {@link net.runelite.client.plugins.custom.zulrah.actions.RepositionAttackAction}
     * and {@code openingHold}). We still record the first stand tile ({@link StandLocation#NORTHEAST_NORTH},
     * the same opener for every rotation) so movement can resume the instant the opening hit lands.
     * No-op once a phase is already active, so it never clobbers an in-progress fight.
     */
    public void onFightStart() {
        if (context == null || context.getPhase() != null) {
            return;
        }
        // TEMP (timing probe): confirm the NPC spawns before the surface.
        log.info("[timing] onFightStart: NPC spawned, opening hold until the first attack fires");
        context.setSurfaced(false);
        context.setOpeningHold(true);
        context.setStandLocation(StandLocation.NORTHEAST_NORTH.toWorldPoint());
    }

    /** Zulrah surfaced (spawn or resurface): it's attackable now, and re-arm the venom pre-move. */
    public void onZulrahSurfaced() {
        if (context != null) {
            // TEMP (timing probe): time gap from onFightStart tells us how long the pre-run window is.
            log.info("[timing] onZulrahSurfaced: attackable now (phase={})",
                    context.getPhase() == null ? "none" : context.getPhase().getZulrahNpc().getType().getName());
            context.setSurfaced(true);
            context.setVenomPreMoved(false);
        }
    }

    /** Zulrah dived under water: not attackable until it resurfaces, so hold fire and just walk. */
    public void onZulrahSubmerged() {
        if (context != null) {
            context.setSurfaced(false);
            // Once it dives, the opening phase is over — make sure the opening hold/walk never leak into
            // the next phase (e.g. if the opening hit didn't register before the dive).
            context.setOpeningHold(false);
            context.setOpeningWalk(false);
        }
    }

    /**
     * Zulrah died: turn off every prayer and mark that we're awaiting the drop. We do NOT start
     * looting here — the death animation is still playing and the drop only lands once the corpse
     * despawns. {@link #onZulrahDespawned()} arms the actual pickup at that point.
     */
    public void onZulrahDeath() {
        log.info("Zulrah died. Disabling all prayers; loot will be picked up when the corpse despawns.");
        Rs2Prayer.disableAllPrayers();
        if (context != null) {
            context.setDeathAwaitingDrop(true);
        }
    }

    /**
     * Zulrah's corpse despawned. If this follows a death (not a between-phase transform), the death
     * animation has finished and the drop is now on the ground, so arm the loot pickup.
     */
    public void onZulrahDespawned() {
        if (context == null || !context.isDeathAwaitingDrop()) {
            return;
        }
        log.info("Zulrah corpse despawned; drop should be down — looting the kill.");
        context.setDeathAwaitingDrop(false);
        context.setLootPending(true);
        // Small grace for the item(s) to register; LootAction extends this on each successful pickup.
        context.setLootDeadlineMs(System.currentTimeMillis() + LootAction.INITIAL_LOOT_WAIT_MS);
    }

    /** Melee tail swing (SNAKEBOSS_ATTACK_TAIL_LEFT/RIGHT): flip the dodge target tile. */
    public void handleMeleeSwing() {
        if (context != null && context.isMeleeDodgePhase()) {
            context.setMeleeDodgeAtNorth(!context.isMeleeDodgeAtNorth());
        }
    }

    /**
     * Jad-phase prayer flick, driven directly by the attack animation event (outside the tick
     * pipeline for latency). Blind toggle: fragile if it ever misses a swing, but the pipeline's
     * EnforcePrayersAction deliberately leaves the jad overhead alone so it doesn't fight this.
     */
    public void handleZulrahAttack() {
        if (context == null) {
            log.info("Context is null, returning");
            return;
        }
        ZulrahPhase phase = context.getPhase();
        if (phase == null || !phase.getZulrahNpc().isJad()) {
            return;
        }
        log.info("Jad phase. Toggling prayer.");
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
            log.info("Switching to protect magic");
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else {
            log.info("Switching to protect range");
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }
    }
}
