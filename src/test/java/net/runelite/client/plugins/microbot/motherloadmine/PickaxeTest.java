package net.runelite.client.plugins.microbot.motherloadmine;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.motherloadmine.enums.Pickaxe;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PickaxeTest {
    @Test
    void recognisesDragonPickaxeVariants() {
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.DRAGON_PICKAXE));
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.DRAGON_PICKAXE_PRETTY));
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.ZALCANO_PICKAXE));
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.ZALCANO_PICKAXE_SPECIAL_GLOW));
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL));
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL));
    }

    @Test
    void recognisesInfernalPickaxeVariants() {
        assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.INFERNAL_PICKAXE));
        assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.INFERNAL_PICKAXE_EMPTY));
        assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_PICKAXE));
        assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_PICKAXE_EMPTY));
        assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_RELOADED_PICKAXE));
        assertEquals(Pickaxe.INFERNAL_PICKAXE, Pickaxe.fromItemId(ItemID.TRAILBLAZER_RELOADED_PICKAXE_EMPTY));
    }

    @Test
    void recognisesThirdAgeAndCrystalPickaxes() {
        assertEquals(Pickaxe.THIRD_AGE_PICKAXE, Pickaxe.fromItemId(ItemID._3A_PICKAXE));
        assertEquals(Pickaxe.CRYSTAL_PICKAXE, Pickaxe.fromItemId(ItemID.CRYSTAL_PICKAXE));
        assertEquals(Pickaxe.CRYSTAL_PICKAXE_INACTIVE, Pickaxe.fromItemId(ItemID.CRYSTAL_PICKAXE_INACTIVE));
        assertEquals(Pickaxe.CRYSTAL_PICKAXE, Pickaxe.fromItemId(ItemID.CRYSTAL_PICKAXE_GLOW));
        assertEquals(Pickaxe.GILDED_PICKAXE, Pickaxe.fromItemId(ItemID.TRAIL_GILDED_PICKAXE));
    }

    @Test
    void inactiveCrystalPickaxeKeepsCrystalRequirementsButRanksAsDragon() {
        assertEquals(71, Pickaxe.CRYSTAL_PICKAXE_INACTIVE.getMiningLevel());
        assertEquals(70, Pickaxe.CRYSTAL_PICKAXE_INACTIVE.getAttackLevel());
        assertEquals(Pickaxe.DRAGON_PICKAXE.getTier(), Pickaxe.CRYSTAL_PICKAXE_INACTIVE.getTier());
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.CRYSTAL_PICKAXE_INACTIVE, ItemID.DRAGON_PICKAXE_PRETTY));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.CRYSTAL_PICKAXE_INACTIVE, ItemID.INFERNAL_PICKAXE));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.DRAGON_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE));
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.CRYSTAL_PICKAXE, ItemID.CRYSTAL_PICKAXE_INACTIVE));
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.CRYSTAL_PICKAXE_INACTIVE, ItemID.RUNE_PICKAXE));
    }

    @Test
    void skipsBankCheckWhenCarriedPickaxeIsBestUsableTier() {
        assertFalse(Pickaxe.hasUsableBetterTier(ItemID.CRYSTAL_PICKAXE, level -> level <= 99));
        assertFalse(Pickaxe.hasUsableBetterTier(ItemID.DRAGON_PICKAXE_PRETTY, level -> level <= 70));
        assertFalse(Pickaxe.hasUsableBetterTier(ItemID.RUNE_PICKAXE, level -> level <= 60));
        assertTrue(Pickaxe.hasUsableBetterTier(ItemID.DRAGON_PICKAXE_PRETTY, level -> level <= 71));
        assertTrue(Pickaxe.hasUsableBetterTier(ItemID.CRYSTAL_PICKAXE_INACTIVE, level -> level <= 71));
        assertTrue(Pickaxe.hasUsableBetterTier(ItemID.RUNE_PICKAXE, level -> level <= 61));
    }

    @Test
    void bankPickaxeIsBetterOnlyWhenItsTierIsHigher() {
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.DRAGON_PICKAXE, ItemID.RUNE_PICKAXE));
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.DRAGON_PICKAXE_PRETTY, ItemID.RUNE_PICKAXE));
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.INFERNAL_PICKAXE, ItemID.TRAIL_GILDED_PICKAXE));
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.CRYSTAL_PICKAXE, ItemID.ZALCANO_PICKAXE));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.DRAGON_PICKAXE, ItemID.INFERNAL_PICKAXE));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.TRAIL_GILDED_PICKAXE, ItemID.RUNE_PICKAXE));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.HAMMER, ItemID.BRONZE_PICKAXE));
    }

    @Test
    void carriedPickaxeIsHighestUsableTierAcrossWeaponAndInventory() {
        final int[] wieldedRune = {ItemID.RUNE_PICKAXE, 0};
        final int[] inventoryDragon = {ItemID.DRAGON_PICKAXE, 1};
        final int[] inventoryHammer = {ItemID.HAMMER, 2};

        assertSame(inventoryDragon, Pickaxe.bestUsable(Stream.of(wieldedRune, inventoryHammer, inventoryDragon), item -> item[0], level -> level <= 61).orElse(null));
        assertSame(inventoryDragon, Pickaxe.bestUsable(Stream.of(inventoryDragon, wieldedRune), item -> item[0], level -> level <= 61).orElse(null));
        assertSame(wieldedRune, Pickaxe.bestUsable(Stream.of(wieldedRune, inventoryDragon), item -> item[0], level -> level <= 60).orElse(null));
        assertFalse(Pickaxe.bestUsable(Stream.of(inventoryHammer), item -> item[0], level -> true).isPresent());
    }

    @Test
    void wieldedPickaxeWinsATierTie() {
        final int[] wieldedDragon = {ItemID.DRAGON_PICKAXE, 0};
        final int[] inventoryInfernal = {ItemID.INFERNAL_PICKAXE, 1};

        assertSame(wieldedDragon, Pickaxe.bestUsable(Stream.of(wieldedDragon, inventoryInfernal), item -> item[0], level -> true).orElse(null));
    }

    @Test
    void dragonTierVariantsShareDragonRequirements() {
        assertEquals(61, Pickaxe.INFERNAL_PICKAXE.getMiningLevel());
        assertEquals(60, Pickaxe.INFERNAL_PICKAXE.getAttackLevel());
        assertEquals(61, Pickaxe.THIRD_AGE_PICKAXE.getMiningLevel());
    }

    @Test
    void keepsBasicPickaxesAndRejectsOtherItems() {
        assertEquals(Pickaxe.RUNE_PICKAXE, Pickaxe.fromItemId(ItemID.RUNE_PICKAXE));
        assertEquals(Pickaxe.BRONZE_PICKAXE, Pickaxe.fromItemId(ItemID.BRONZE_PICKAXE));
        assertNull(Pickaxe.fromItemId(ItemID.DRAGON_PICKAXE_UPGRADE_KIT));
        assertNull(Pickaxe.fromItemId(ItemID.HAMMER));
    }
}
