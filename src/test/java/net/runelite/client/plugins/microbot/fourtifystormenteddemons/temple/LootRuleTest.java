package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LootRuleTest
{
    @Test
    public void namesAreTrimmedLowerCasedAndBlankEntriesDropped()
    {
        assertEquals(Set.of("burning claw", "tormented synapse"), LootRule.names(" Burning Claw ,, TORMENTED synapse ,  "));
        assertTrue(LootRule.names(null).isEmpty());
        assertTrue(LootRule.names("").isEmpty());
    }

    @Test
    public void theAlwaysListMatchesNamesIgnoringCaseAndSpaces()
    {
        LootRule rule = new LootRule("Tormented synapse, Burning claw", 1_000_000);
        assertTrue(rule.take("Tormented synapse", 0, 1));
        assertTrue(rule.take("  BURNING CLAW ", 1, 1));
        assertTrue(rule.take("burning claw", 5, 0));
        assertFalse(rule.take("Burning claws", 1, 1));
    }

    @Test
    public void aPriceOfZeroIsUnknownSoOnlyListedItemsAreTaken()
    {
        LootRule rule = new LootRule("Tormented synapse", 0);
        assertFalse(rule.take("Mystery item", 0, 1000));
        assertFalse(rule.take("Mystery item", -5, 1000));
        assertTrue(rule.take("Tormented synapse", 0, 1));
    }

    @Test
    public void unlistedItemsNeedTheMinimumValueForTheWholeStack()
    {
        LootRule rule = new LootRule("", 5000);
        assertFalse(rule.take("Coins", 1, 4999));
        assertTrue(rule.take("Coins", 1, 5000));
        assertTrue(rule.take("Rune bar", 5000, 1));
        assertFalse(rule.take("Rune bar", 4999, 1));
        assertFalse(rule.take("Rune bar", 5000, 0));
    }

    @Test
    public void largeStacksDoNotOverflow()
    {
        LootRule rule = new LootRule("", Long.MAX_VALUE);
        assertFalse(rule.take("Coins", Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(new LootRule("", 4_000_000_000L).take("Coins", Integer.MAX_VALUE, 2));
    }

    @Test
    public void aMissingNameFallsBackToTheValueTest()
    {
        LootRule rule = new LootRule("Tormented synapse", 100);
        assertTrue(rule.take(null, 100, 1));
        assertFalse(rule.take(null, 0, 1));
    }
}
