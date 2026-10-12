package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mmcaves.enums.Mode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MmCavesDecisionsTest {

    @Test
    void holdsWhenEightMonkeysFitInChinAreaAndTwoAreOutside() {
        WorldPoint fightingTile = new WorldPoint(2449, 9172, 1);
        List<WorldPoint> monkeys = new ArrayList<>();
        for (int i = 0; i < 8; i++) monkeys.add(new WorldPoint(2450, 9172, 1));
        monkeys.add(new WorldPoint(2454, 9172, 1));
        monkeys.add(new WorldPoint(2455, 9172, 1));
        monkeys.add(new WorldPoint(2500, 9172, 1)); // Outside the local encounter.

        MmCavesDecisions.StackCounts counts = MmCavesDecisions.countStack(monkeys, fightingTile, 8);
        assertEquals(8, counts.stacked);
        assertEquals(2, counts.outside);
        assertTrue(counts.ready(8, 2));
        assertFalse(counts.ready(8, 1));
    }

    @Test
    void keepsGatheringWhenMonkeysAreSpreadOut() {
        WorldPoint fightingTile = new WorldPoint(2449, 9172, 1);
        List<WorldPoint> monkeys = new ArrayList<>();
        for (int i = 0; i < 6; i++) monkeys.add(new WorldPoint(2450, 9172, 1));
        for (int i = 0; i < 4; i++) monkeys.add(new WorldPoint(2454, 9172, 1));

        MmCavesDecisions.StackCounts counts = MmCavesDecisions.countStack(monkeys, fightingTile, 8);
        assertEquals(6, counts.stacked);
        assertEquals(4, counts.outside);
        assertFalse(counts.ready(8, 2));
    }

    @Test
    void rangedAttackClicksAreOptIn() {
        assertFalse(new MmCavesConfig() {}.clickRangedAttackTargets());
    }

    @Test
    void healsAtHalfHealthOrLower() {
        assertFalse(MmCavesDecisions.shouldHeal(50.1));
        assertTrue(MmCavesDecisions.shouldHeal(50));
        assertTrue(MmCavesDecisions.shouldHeal(40));
    }

    @Test
    void acceptsFiremakingCapeInInventory() {
        assertTrue(MmCavesDecisions.hasLightSource(
                "Firemaking cape"::equals, name -> false));
        assertTrue(MmCavesDecisions.hasLightSource(
                "Firemaking cape(t)"::equals, name -> false));
        assertTrue(MmCavesDecisions.hasLightSource(
                name -> false, "Firemaking cape(t)"::equals));
    }

    @Test
    void acceptsEquippedLightSource() {
        assertTrue(MmCavesDecisions.hasLightSource(
                name -> false, "Bullseye lantern"::equals));
    }

    @Test
    void rejectsMissingLightSource() {
        assertFalse(MmCavesDecisions.hasLightSource(
                name -> false, name -> false));
    }
    @Test
    void ancientMagicRequiresBothSpellbookAndRunes() {
        assertFalse(MmCavesDecisions.magicSuppliesMissing(true, true));
        assertTrue(MmCavesDecisions.magicSuppliesMissing(false, true));
        assertTrue(MmCavesDecisions.magicSuppliesMissing(true, false));
        assertTrue(MmCavesDecisions.magicSuppliesMissing(false, false));
    }

    @Test
    void rangedModeNeverUsesDirectMagicCast() {
        assertFalse(MmCavesDecisions.useDirectMagicCast(Mode.RANGE, false));
        assertFalse(MmCavesDecisions.useDirectMagicCast(Mode.RANGE, true));
        assertTrue(MmCavesDecisions.useDirectMagicCast(Mode.MAGIC, false));
        assertFalse(MmCavesDecisions.useDirectMagicCast(Mode.MAGIC, true));
    }

    @Test
    void worldSelectionStopsAfterBoundedCheckedResults() {
        AtomicInteger calls = new AtomicInteger();
        int world = MmCavesDecisions.selectUncheckedWorld(() -> {
            calls.incrementAndGet();
            return 330;
        }, Collections.singleton(330), 4);
        assertEquals(-1, world);
        assertEquals(4, calls.get());
    }

    @Test
    void worldSelectionSkipsCheckedAndInvalidWorlds() {
        int[] candidates = {0, 330, 331};
        AtomicInteger index = new AtomicInteger();
        int world = MmCavesDecisions.selectUncheckedWorld(
                () -> candidates[index.getAndIncrement()], new HashSet<>(Collections.singletonList(330)), 3);
        assertEquals(331, world);
        assertEquals(3, index.get());
    }
}
