package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.zulrahslayer.ZulrahConfig;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.zulrahslayer.ZulrahScript;

import javax.inject.Inject;

/**
 * After Zulrah dies, pick up the kill's drops. Only runs between fights (no active phase) while the
 * loot flag armed by {@link ZulrahScript#onZulrahDeath()}
 * is set. Loots one ground item per tick via {@link Rs2GroundItem#lootAllItemBasedOnValue}; a
 * deadline (extended on each pickup) covers the delay before the drop spawns and stops us shortly
 * after the last item, so we don't idle forever if nothing is there.
 */
@Slf4j
public class LootAction implements ZulrahAction {

    /** How long to keep trying after death before giving up if we never find/loot anything. */
    public static final long INITIAL_LOOT_WAIT_MS = 3000L;
    /** Extra time granted after each successful pickup, so we keep going while loot remains. */
    private static final long LOOT_EXTEND_MS = 1500L;
    /** Loot everything (value >= 0) within this many tiles of the death spot. */
    private static final int LOOT_MIN_VALUE = 0;
    private static final int LOOT_RANGE = 20;

    private final ZulrahConfig config;

    @Inject
    public LootAction(ZulrahConfig config) {
        this.config = config;
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public String key() {
        return "loot";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        FightContext ctx = state.context();
        return ctx.isLootPending() && betweenFights(ctx);
    }

    @Override
    public Object execute(ZulrahState state) {
        FightContext ctx = state.context();
        long now = System.currentTimeMillis();

        boolean looted = Rs2GroundItem.lootAllItemBasedOnValue(LOOT_MIN_VALUE, LOOT_RANGE);
        if (looted) {
            ctx.setLootDeadlineMs(now + LOOT_EXTEND_MS);
            return "looting";
        }
        if (lootWindowExpired(ctx, now)) {
            ctx.setLootPending(false);
            boolean restock = config.restockBetweenKills();
            log.debug("Loot pickup complete. Restock between kills is {}.", restock ? "ON — arming resupply" : "OFF");
            if (restock) {
                armRestockTrip(ctx);
            }
            return "done";
        }
        return "waiting";
    }

    /** No phase active — once the next Zulrah surfaces (phase set) combat takes over. */
    private static boolean betweenFights(FightContext ctx) {
        return ctx.getPhase() == null;
    }

    /** The grace window has elapsed with nothing left to pick up (a full inventory also lands here). */
    private static boolean lootWindowExpired(FightContext ctx, long now) {
        return now >= ctx.getLootDeadlineMs();
    }

    /** Hand off to the full bank + travel restock routine (ReturnToZulrahAction). */
    private static void armRestockTrip(FightContext ctx) {
        ctx.setPrepSkipBank(false);
        ctx.setPrepPending(true);
    }
}
