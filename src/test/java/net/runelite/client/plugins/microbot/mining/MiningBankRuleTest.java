package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.mining.data.Rocks;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MiningBankRuleTest {
    private static final List<String> DEFAULT_ITEMS = MiningBankRule.parseItemNames("ore");

    @Test
    void parsesCommaSeparatedNames() {
        assertEquals(Arrays.asList("ore", "coal", "uncut ruby"), MiningBankRule.parseItemNames(" Ore, coal ,, Uncut ruby ,"));
        assertTrue(MiningBankRule.parseItemNames(" , ").isEmpty());
    }

    @Test
    void defaultListStillBanksOres() {
        assertTrue(MiningBankRule.shouldBank("Tin ore", Rocks.TIN, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Iron ore", Rocks.IRON, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Copper ore", Rocks.TIN, DEFAULT_ITEMS));
    }

    @Test
    void defaultListBanksMinedClayAndCoal() {
        assertFalse(DEFAULT_ITEMS.stream().anyMatch("clay"::contains));
        assertTrue(MiningBankRule.shouldBank("Clay", Rocks.CLAY, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Coal", Rocks.COAL, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Te salt", Rocks.TE_SALT, DEFAULT_ITEMS));
    }

    @Test
    void doesNotBankOtherRocksResourceUnlessListed() {
        assertFalse(MiningBankRule.shouldBank("Coal", Rocks.CLAY, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Coal", Rocks.CLAY, MiningBankRule.parseItemNames("ore,coal")));
    }

    @Test
    void banksUncutGems() {
        assertTrue(MiningBankRule.shouldBank("Uncut sapphire", Rocks.CLAY, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Uncut red topaz", Rocks.GEM, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Uncut diamond", null, Collections.emptyList()));
    }

    @Test
    void keepsPickaxesAndUnrelatedItems() {
        assertFalse(MiningBankRule.shouldBank("Rune pickaxe", Rocks.COAL, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Bronze pickaxe", Rocks.CLAY, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Open gem bag", Rocks.GEM, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Bracelet of clay", Rocks.CLAY, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Soft clay", Rocks.CLAY, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank(null, Rocks.CLAY, DEFAULT_ITEMS));
    }

    @Test
    void gemAndNoneRocksHaveNoOreName() {
        assertFalse(MiningBankRule.shouldBank("None", Rocks.NONE, Collections.emptyList()));
        assertFalse(MiningBankRule.shouldBank("Gem rocks", Rocks.GEM, Collections.emptyList()));
    }
}
