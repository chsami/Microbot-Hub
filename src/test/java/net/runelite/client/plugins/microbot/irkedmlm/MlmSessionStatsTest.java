package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * The whole session accounting model, run headless.
 *
 * <p>This is the reason the counting was pulled out of {@code IrkedMLMScript}: while it lived among
 * eighty other fields none of it could be exercised without a client, and two bugs sat in it at once
 * — an XP baseline that was never taken when the plugin started at the login screen, and a price
 * cache that memoised a cold zero. Both are pinned below.
 */
class MlmSessionStatsTest {

    // ── XP ──────────────────────────────────────────────────────────────────

    @Test
    void xpBaselinesOnTheFirstReadingNotAtStartup() {
        Fixture f = new Fixture();
        // Pre-login: the client has no XP to give. Nothing may be treated as a baseline.
        assertTrue(!f.stats.recordXp(-1), "a failed read must not count as a gain");
        assertEquals(0, f.stats.getStartXp());
        assertEquals(0, f.stats.xpGained());

        // First real reading after login becomes the baseline, and is not itself a gain.
        assertTrue(!f.stats.recordXp(8_000_000), "the baseline reading is not a gain");
        assertEquals(8_000_000, f.stats.getStartXp());
        assertEquals(0, f.stats.xpGained());

        assertTrue(f.stats.recordXp(8_000_060), "a higher reading is an ore mined");
        assertEquals(60, f.stats.xpGained());
    }

    @Test
    void xpGainedIsNeverTheWholeAccount() {
        // The counting-down XP/hr bug: without a baseline, currentXp - 0 was the entire account.
        Fixture f = new Fixture();
        f.stats.recordXp(8_000_000);
        assertTrue(f.stats.xpGained() < 1_000,
                "xpGained() reported " + f.stats.xpGained() + " — the account total leaked in again");
    }

    // ── Ore and value ───────────────────────────────────────────────────────

    @Test
    void oreRisesAreCountedAtGePrices() {
        Fixture f = new Fixture();
        f.price(ItemID.RUNITE_ORE, 11_000);
        f.price(ItemID.COAL, 150);
        f.stats.rebaselineInventory();

        f.inventory(ItemID.RUNITE_ORE, 2);
        f.inventory(ItemID.COAL, 5);
        f.stats.update();

        assertEquals(2, f.stats.getRunite());
        assertEquals(5, f.stats.getCoal());
        assertEquals(2 * 11_000 + 5 * 150, f.stats.getTotalValueGained());
        assertEquals(7, f.stats.totalOres());
    }

    @Test
    void bankingDropsAreNotSubtracted() {
        Fixture f = new Fixture();
        f.price(ItemID.RUNITE_ORE, 11_000);
        f.stats.rebaselineInventory();

        f.inventory(ItemID.RUNITE_ORE, 3);
        f.stats.update();
        f.inventory(ItemID.RUNITE_ORE, 0); // deposited
        f.stats.update();
        f.inventory(ItemID.RUNITE_ORE, 1); // mined another
        f.stats.update();

        assertEquals(4, f.stats.getRunite());
        assertEquals(4 * 11_000, f.stats.getTotalValueGained());
    }

    @Test
    void payDirtIsCountedButNeverValued() {
        Fixture f = new Fixture();
        f.price(ItemID.PAYDIRT, 999); // even if the GE somehow priced it
        f.stats.rebaselineInventory();

        f.inventory(ItemID.PAYDIRT, 27);
        f.stats.update();

        assertEquals(0L, f.stats.getTotalValueGained());
        assertEquals(0, f.stats.totalOres()); // pay-dirt is not one of the five tracked ores
    }

    // ── Nuggets ─────────────────────────────────────────────────────────────

    @Test
    void nuggetsAlreadyCarriedAreNotCounted() {
        Fixture f = new Fixture();
        f.inventory(ItemID.MOTHERLODE_NUGGET, 40); // already in the bag at login
        f.stats.rebaselineInventory();
        f.stats.update(); // takes the nugget baseline
        assertEquals(0, f.stats.getGainedNuggets());

        f.inventory(ItemID.MOTHERLODE_NUGGET, 46);
        f.stats.update();
        assertEquals(6, f.stats.getGainedNuggets());

        f.inventory(ItemID.MOTHERLODE_NUGGET, 0); // banked by Deposit-All
        f.stats.update();
        assertEquals(6, f.stats.getGainedNuggets()); // a fall is never subtracted

        f.inventory(ItemID.MOTHERLODE_NUGGET, 6);
        f.stats.update();
        assertEquals(12, f.stats.getGainedNuggets());
    }

    // ── Deposit snapshot ────────────────────────────────────────────────────

    @Test
    void aFastDepositIsRecoveredFromTheSnapshot() {
        // Deposit-All banked the ore before any tick observed it in the inventory.
        Fixture f = new Fixture();
        f.price(ItemID.MITHRIL_ORE, 150);
        f.stats.rebaselineInventory();

        f.stats.applyDepositSnapshot(new int[] { 0, 0, 8, 0, 0 }); // index 2 == mithril
        assertEquals(8, f.stats.getMithril());
        assertEquals(8 * 150, f.stats.getTotalValueGained());
    }

    @Test
    void aVisibleDepositIsNotDoubleCounted() {
        // Ore still in the inventory: update() will count it, so the snapshot must not.
        Fixture f = new Fixture();
        f.price(ItemID.MITHRIL_ORE, 150);
        f.stats.rebaselineInventory();

        f.inventory(ItemID.MITHRIL_ORE, 8);
        f.stats.applyDepositSnapshot(new int[] { 0, 0, 8, 0, 0 });
        f.stats.update();

        assertEquals(8, f.stats.getMithril());
        assertEquals(8 * 150, f.stats.getTotalValueGained());
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Test
    void resetClearsEverySessionFigure() {
        // The script is a @Singleton that survives stop/start; a leaked total inflates the next run.
        Fixture f = new Fixture();
        f.price(ItemID.RUNITE_ORE, 11_000);
        f.stats.rebaselineInventory();
        f.inventory(ItemID.RUNITE_ORE, 3);
        f.inventory(ItemID.MOTHERLODE_NUGGET, 5);
        f.stats.update();
        f.stats.recordXp(500_000);
        f.stats.recordXp(500_100);
        assertTrue(f.stats.totalOres() > 0 && f.stats.xpGained() > 0, "fixture did not accumulate");

        f.stats.reset();

        assertEquals(0, f.stats.totalOres());
        assertEquals(0L, f.stats.getTotalValueGained());
        assertEquals(0, f.stats.getGainedNuggets());
        assertEquals(0, f.stats.getStartXp());
        assertEquals(0, f.stats.xpGained());
    }

    // ── Harness ─────────────────────────────────────────────────────────────

    /** A fake inventory and price list; no client anywhere. */
    private static final class Fixture {
        final Map<Integer, Integer> inv = new HashMap<>();
        final Map<Integer, Integer> prices = new HashMap<>();
        final MlmSessionStats stats = new MlmSessionStats(
                id -> inv.getOrDefault(id, 0),
                id -> prices.getOrDefault(id, 0));

        void inventory(int itemId, int quantity) {
            inv.put(itemId, quantity);
        }

        void price(int itemId, int gp) {
            prices.put(itemId, gp);
        }
    }
}
