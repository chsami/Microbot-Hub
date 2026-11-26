package net.runelite.client.plugins.microbot.irkedmlm.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.Test;

/** Deposit Inventory must never sweep a pickaxe, hammer or bag the run still needs. */
class DepositSweepTest {

    @Test
    void oreGemsNuggetsAndTheBagMayBeSwept() {
        assertTrue(SackSession.onlySweepableItems(IntStream.of(
                ItemID.COAL, ItemID.GOLD_ORE, ItemID.UNCUT_RUBY, ItemID.MOTHERLODE_NUGGET, ItemID.GEM_BAG_OPEN)));
    }

    @Test
    void aCarriedPickaxeOrHammerBlocksTheSweep() {
        assertFalse(SackSession.onlySweepableItems(IntStream.of(ItemID.COAL, ItemID.RUNE_PICKAXE)));
        assertFalse(SackSession.onlySweepableItems(IntStream.of(ItemID.COAL, ItemID.HAMMER)));
    }
}
