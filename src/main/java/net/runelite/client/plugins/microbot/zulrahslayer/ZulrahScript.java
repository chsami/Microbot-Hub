package net.runelite.client.plugins.microbot.zulrahslayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.actions.Action;
import net.runelite.client.plugins.microbot.actions.ActionScript;
import net.runelite.client.plugins.microbot.zulrahslayer.actions.FightContext;
import net.runelite.client.plugins.microbot.zulrahslayer.actions.LootAction;
import net.runelite.client.plugins.microbot.zulrahslayer.actions.ZulrahAction;
import net.runelite.client.plugins.microbot.zulrahslayer.actions.ZulrahState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.StandLocation;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.VenomTiming;
import net.runelite.client.plugins.microbot.zulrahslayer.rotationutils.ZulrahPhase;
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
        log.debug("Initializing");
        context = new FightContext(itemManager);
        String magicName = zulrahConfig.mageInventorySetup().getName();
        String rangeName = zulrahConfig.rangeInventorySetup().getName();
        log.debug("Using inventory setups — magic: '{}', range: '{}'", magicName, rangeName);
        context.setMagicSetup(new Rs2InventorySetup(magicName, mainScheduledFuture));
        context.setRangeSetup(new Rs2InventorySetup(rangeName, mainScheduledFuture));
        context.reset();
        armStartPoint();
    }

    private void armStartPoint() {
        CycleStartPoint start = zulrahConfig.cycleStartPoint();
        log.debug("Start point: {}", start);
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
                break;
        }
    }

    public void reset() {
        if (context != null) {
            context.reset();
        }
    }

    public void setZulrahPhase(ZulrahPhase phase) {
        if (context != null && phase != null) {
            context.setPhase(phase);
            context.setPhaseChanged(true);
        }
    }

    public void setNextStandLocation(net.runelite.api.coords.WorldPoint next) {
        if (context != null) {
            context.setNextStandLocation(next);
        }
    }

    public void onVenomCloudSpawned() {
        if (context == null || context.isVenomPreMoved() || context.isMeleeDodgePhase()) {
            return;
        }
        ZulrahPhase phase = context.getPhase();
        if (onlyMoveAtEndOfPhase(phase)) {
            return;
        }
        WorldPoint next = context.getNextStandLocation();
        if (next == null) {
            return;
        }
        WorldPoint current = context.getStandLocation();
        WorldPoint pos = Rs2Player.getWorldLocation();
        if (!playerIsNotStandingOnRightLocationOfCurrentPhase(current, pos)) {
            return;
        }
        if (playerIsAlreadyStandingInRightLocationForNextPhase(pos, next, current)) {
            return;
        }
        log.debug("[venom] cloud spawned while settled; pre-moving to next stand tile {}", next);
        context.setVenomPreMoved(true);
        context.setStandLocation(next);
    }

    private static boolean playerIsAlreadyStandingInRightLocationForNextPhase(WorldPoint pos, WorldPoint next, WorldPoint current) {
        return pos.equals(next) || next.equals(current);
    }

    private static boolean playerIsNotStandingOnRightLocationOfCurrentPhase(WorldPoint current, WorldPoint pos) {
        return current != null && pos != null
                && (pos.equals(current) || (!Rs2Player.isMoving() && pos.distanceTo(current) <= 1));
    }

    private static boolean onlyMoveAtEndOfPhase(ZulrahPhase phase) {
        return phase == null || phase.getAttributes().getVenomTiming() != VenomTiming.END;
    }

    public void onFightStart() {
        if (context == null || context.getPhase() != null) {
            return;
        }
        log.debug("[timing] onFightStart: NPC spawned, opening hold until the first attack fires");
        context.setSurfaced(false);
        context.setWaitingForFirstAttackOnZulrah(true);
        context.setStandLocation(StandLocation.NORTHEAST_NORTH.toWorldPoint());
    }

    public void onZulrahSurfaced() {
        if (context != null) {
            log.debug("[timing] onZulrahSurfaced: attackable now (phase={})",
                    context.getPhase() == null ? "none" : context.getPhase().getZulrahNpc().getType().getName());
            context.setSurfaced(true);
            context.setVenomPreMoved(false);
        }
    }

    public void onZulrahSubmerged() {
        if (context != null) {
            context.setSurfaced(false);
            context.setWaitingForFirstAttackOnZulrah(false);
            context.setNeedsToGoToFirstLocation(false);
        }
    }

    public void onZulrahDeath() {
        log.debug("Zulrah died. Disabling all prayers; loot will be picked up when the corpse despawns.");
        Rs2Prayer.disableAllPrayers();
        if (context != null) {
            context.setDeathAwaitingDrop(true);
        }
    }

    public void onZulrahDespawned() {
        if (context == null || !context.isDeathAwaitingDrop()) {
            return;
        }
        log.debug("Zulrah corpse despawned; drop should be down — looting the kill.");
        context.setDeathAwaitingDrop(false);
        context.setLootPending(true);
        context.setLootDeadlineMs(System.currentTimeMillis() + LootAction.INITIAL_LOOT_WAIT_MS);
    }

    public void handleMeleeSwing() {
        if (context != null && context.isMeleeDodgePhase()) {
            context.setMeleeDodgeAtNorth(!context.isMeleeDodgeAtNorth());
        }
    }

    public void handleZulrahAttack() {
        if (context == null) {
            log.debug("Context is null, returning");
            return;
        }
        ZulrahPhase phase = context.getPhase();
        if (phase == null || !phase.getZulrahNpc().isJad()) {
            return;
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }
    }
}
