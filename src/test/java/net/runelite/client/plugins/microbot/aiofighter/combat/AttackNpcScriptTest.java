package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AttackNpcScriptTest {

    @Test
    void holdsAttackWhileInCombatWhenAntibanCooldownNeverStarts() {
        assertTrue(AttackNpcScript.shouldHoldAttack(true, true, 0.1, false));
        assertTrue(AttackNpcScript.shouldHoldAttack(true, true, 1.0, false));
    }

    @Test
    void holdsAttackWhileInCombatWithAntibanDisabled() {
        assertTrue(AttackNpcScript.shouldHoldAttack(true, false, 0.0, false));
    }

    @Test
    void holdsAttackDuringActiveCooldown() {
        assertTrue(AttackNpcScript.shouldHoldAttack(false, true, 1.0, true));
    }

    @Test
    void ignoresStaleCooldownFlagWhenAntibanCannotClearIt() {
        assertFalse(AttackNpcScript.shouldHoldAttack(false, false, 1.0, true));
        assertFalse(AttackNpcScript.shouldHoldAttack(false, true, 0.0, true));
    }

    @Test
    void attacksWhenIdleAndNoCooldown() {
        assertFalse(AttackNpcScript.shouldHoldAttack(false, true, 1.0, false));
        assertFalse(AttackNpcScript.shouldHoldAttack(false, false, 0.0, false));
    }

    @Test
    void sortsByPrimaryThenSecondaryKey() {
        List<String> items = Arrays.asList("far-free", "near-free", "far-mine", "near-mine");
        Map<String, Integer> primary = Map.of("far-free", 1, "near-free", 1, "far-mine", 0, "near-mine", 0);
        Map<String, Integer> secondary = Map.of("far-free", 9, "near-free", 2, "far-mine", 7, "near-mine", 1);

        List<String> sorted = AttackNpcScript.sortBySnapshotKeys(items, primary::get, secondary::get);

        assertEquals(Arrays.asList("near-mine", "far-mine", "near-free", "far-free"), sorted);
    }

    @Test
    void readsEachKeyOncePerItemSoLiveKeysCannotBreakTheSort() {
        List<Integer> items = IntStream.range(0, 500).boxed().collect(Collectors.toList());
        Random random = new Random(42);
        Map<Integer, Integer> primaryReads = new HashMap<>();
        Map<Integer, Integer> secondaryReads = new HashMap<>();

        List<Integer> sorted = AttackNpcScript.sortBySnapshotKeys(items,
                item -> {
                    primaryReads.merge(item, 1, Integer::sum);
                    return random.nextInt(2);
                },
                item -> {
                    secondaryReads.merge(item, 1, Integer::sum);
                    return random.nextInt(50);
                });

        assertEquals(items.size(), sorted.size());
        assertEquals(new ArrayList<>(items), sorted.stream().sorted().collect(Collectors.toList()));
        assertTrue(primaryReads.values().stream().allMatch(count -> count == 1));
        assertTrue(secondaryReads.values().stream().allMatch(count -> count == 1));
        assertEquals(items.size(), primaryReads.size());
    }

    private static final IntUnaryOperator AUTOKILL_OFF = varbit -> 0;
    private static final IntUnaryOperator AUTOKILL_ON = varbit -> 1;

    @Test
    void skipsNpcWhoseNameIsGoneWhileDespawning() {
        assertEquals(-1, AttackNpcScript.finishingItemFor(null, 1, AUTOKILL_OFF));
        assertEquals(-1, AttackNpcScript.finishingItemFor(null, 0, AUTOKILL_ON));
    }

    @Test
    void picksFinishingItemForSlayerNpcsNearDeath() {
        assertEquals(ItemID.SLAYER_ICY_WATER, AttackNpcScript.finishingItemFor("Desert Lizard", 4, AUTOKILL_OFF));
        assertEquals(ItemID.SLAYER_BAG_OF_SALT, AttackNpcScript.finishingItemFor("Rockslug", 4, AUTOKILL_OFF));
        assertEquals(ItemID.SLAYER_ROCK_HAMMER, AttackNpcScript.finishingItemFor("Gargoyle", 2, AUTOKILL_OFF));
    }

    @Test
    void skipsFinishingItemWhenHealthyAutokillEnabledOrOtherNpc() {
        assertEquals(-1, AttackNpcScript.finishingItemFor("Rockslug", 5, AUTOKILL_OFF));
        assertEquals(-1, AttackNpcScript.finishingItemFor("Gargoyle", 3, AUTOKILL_OFF));
        assertEquals(-1, AttackNpcScript.finishingItemFor("Rockslug", 1, AUTOKILL_ON));
        assertEquals(-1, AttackNpcScript.finishingItemFor("Goblin", 0, AUTOKILL_OFF));
    }
}
