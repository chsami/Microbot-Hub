package net.runelite.client.plugins.microbot.irkedmlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.Pickaxe;
import org.junit.jupiter.api.Test;

/** Pickaxe variants rank and spec like their base pickaxe; Any only ever resolves to a real lower area. */
class PickaxeAndAreaTest {

    @Test
    void variantsAreRecognisedWithTheirLevels() {
        assertEquals(Pickaxe.DRAGON_PICKAXE, Pickaxe.fromItemId(ItemID.ZALCANO_PICKAXE));
        assertEquals(Pickaxe.GILDED_PICKAXE, Pickaxe.fromItemId(ItemID.TRAIL_GILDED_PICKAXE));
        assertEquals(65, Pickaxe.THIRD_AGE_PICKAXE.getAttackLevel());
        assertNull(Pickaxe.fromItemId(ItemID.COAL));
    }

    @Test
    void specialsFollowTheBasePickaxe() {
        assertEquals(Pickaxe.Spec.DRAGON, Pickaxe.fromItemId(ItemID.DRAGON_PICKAXE_PRETTY).getSpec());
        assertEquals(Pickaxe.Spec.DRAGON, Pickaxe.THIRD_AGE_PICKAXE.getSpec());
        assertEquals(Pickaxe.Spec.CRYSTAL, Pickaxe.CRYSTAL_PICKAXE.getSpec());
        assertEquals(Pickaxe.Spec.NONE, Pickaxe.CRYSTAL_PICKAXE_INACTIVE.getSpec());
    }

    @Test
    void theBestUsablePickaxeRespectsTheMiningLevel() {
        Stream<Integer> carried = Stream.of(ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE, ItemID.COAL);
        assertEquals(ItemID.RUNE_PICKAXE, Pickaxe.bestUsable(carried, i -> i, level -> level <= 41).orElse(-1));
        assertTrue(Pickaxe.isBetterPickaxe(ItemID.DRAGON_PICKAXE, ItemID.RUNE_PICKAXE));
        assertFalse(Pickaxe.isBetterPickaxe(ItemID.CRYSTAL_PICKAXE_INACTIVE, ItemID.DRAGON_PICKAXE));
        assertFalse(Pickaxe.hasUsableBetterTier(ItemID.RUNE_PICKAXE, level -> level <= 41));
    }

    @Test
    void anyOnlyPicksRealLowerAreas() {
        assertFalse(MLMMiningSpot.LOWER_AREAS.contains(MLMMiningSpot.ANY));
        assertTrue(MLMMiningSpot.LOWER_AREAS.stream().allMatch(MLMMiningSpot::isDownstairs));
        assertTrue(MLMMiningSpot.ANY.getWorldPoint().isEmpty());
    }
}
