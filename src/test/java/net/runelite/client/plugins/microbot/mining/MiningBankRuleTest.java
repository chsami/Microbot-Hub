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
        assertTrue(MiningBankRule.shouldBank("Tin ore", only(Rocks.TIN), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Iron ore", only(Rocks.IRON), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Copper ore", only(Rocks.TIN), DEFAULT_ITEMS));
    }

    @Test
    void defaultListBanksMinedClayAndCoal() {
        assertFalse(DEFAULT_ITEMS.stream().anyMatch("clay"::contains));
        assertTrue(MiningBankRule.shouldBank("Clay", only(Rocks.CLAY), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Coal", only(Rocks.COAL), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Te salt", only(Rocks.TE_SALT), DEFAULT_ITEMS));
    }

    @Test
    void doesNotBankOtherRocksResourceUnlessListed() {
        assertFalse(MiningBankRule.shouldBank("Coal", only(Rocks.CLAY), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Coal", only(Rocks.CLAY), MiningBankRule.parseItemNames("ore,coal")));
    }

    @Test
    void banksUncutGems() {
        assertTrue(MiningBankRule.shouldBank("Uncut sapphire", only(Rocks.CLAY), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Uncut red topaz", only(Rocks.GEM), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Uncut diamond", only(null), Collections.emptyList()));
    }

    @Test
    void keepsPickaxesAndUnrelatedItems() {
        assertFalse(MiningBankRule.shouldBank("Rune pickaxe", only(Rocks.COAL), DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Bronze pickaxe", only(Rocks.CLAY), DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Open gem bag", only(Rocks.GEM), DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Bracelet of clay", only(Rocks.CLAY), DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Soft clay", only(Rocks.CLAY), DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank(null, only(Rocks.CLAY), DEFAULT_ITEMS));
    }

    @Test
    void progressiveModeBanksResourcesOfEarlierRocks() {
        List<Rocks> progressive = Arrays.asList(Rocks.TIN, Rocks.IRON, Rocks.COAL, Rocks.GOLD, Rocks.MITHRIL, Rocks.ADAMANTITE, Rocks.RUNITE);
        assertFalse(MiningBankRule.shouldBank("Coal", only(Rocks.GOLD), DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Coal", progressive, DEFAULT_ITEMS));
        assertTrue(MiningBankRule.shouldBank("Gold ore", progressive, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Clay", progressive, DEFAULT_ITEMS));
        assertFalse(MiningBankRule.shouldBank("Rune pickaxe", progressive, DEFAULT_ITEMS));
    }

    @Test
    void gemAndNoneRocksHaveNoOreName() {
        assertFalse(MiningBankRule.shouldBank("None", only(Rocks.NONE), Collections.emptyList()));
        assertFalse(MiningBankRule.shouldBank("Gem rocks", only(Rocks.GEM), Collections.emptyList()));
    }

    private static List<Rocks> only(Rocks rock) {
        return Collections.singletonList(rock);
    }
}
