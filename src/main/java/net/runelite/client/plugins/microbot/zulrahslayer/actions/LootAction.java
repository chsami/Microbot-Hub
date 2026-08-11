package net.runelite.client.plugins.custom.zulrah.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.custom.zulrah.ZulrahConfig;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;

import javax.inject.Inject;

/**
 * After Zulrah dies, pick up the kill's drops. Only runs between fights (no active phase) while the
 * loot flag armed by {@link net.runelite.client.plugins.custom.zulrah.ZulrahScript#onZulrahDeath()}
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
        // Only between fights: once the next Zulrah surfaces (phase set) combat takes over.
        return ctx.isLootPending() && ctx.getPhase() == null;
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
        // Nothing to loot this tick: wait out the grace window (drop may not have spawned yet), then
        // stop. A full inventory also lands here, so we don't spin once we can't pick anything up.
        if (now >= ctx.getLootDeadlineMs()) {
            ctx.setLootPending(false);
            // Read the toggle LIVE (not a start-up snapshot) so flipping it in the config panel takes
            // effect without restarting, and so a stale/false snapshot can never silently swallow the
            // hand-off. When enabled, arm the between-kills bank-and-travel routine (ReturnToZulrahAction).
            boolean restock = config.restockBetweenKills();
            log.info("Loot pickup complete. Restock between kills is {}.", restock ? "ON — arming resupply" : "OFF");
            if (restock) {
                ctx.setPrepSkipBank(false); // between-kills restock is always the full bank + travel trip
                ctx.setPrepPending(true);
            }
            return "done";
        }
        return "waiting";
    }
}
