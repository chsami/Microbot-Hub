package net.runelite.client.plugins.microbot.aiofighter.loot;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class LootScriptCombatGateTest {

    @Test
    void lootsBetweenKillsWhenNoTargetIsBeingFought() {
        assertFalse(LootScript.blockedByCombat(false, () -> false));
    }

    @Test
    void holdsLootWhileATargetIsBeingFought() {
        assertTrue(LootScript.blockedByCombat(false, () -> true));
    }

    @Test
    void forceLootIgnoresTheFight() {
        assertFalse(LootScript.blockedByCombat(true, () -> true));
        assertFalse(LootScript.blockedByCombat(true, () -> false));
    }

    @Test
    void forceLootSkipsTheLiveCombatLookup() {
        AtomicInteger lookups = new AtomicInteger();

        LootScript.blockedByCombat(true, () -> {
            lookups.incrementAndGet();
            return true;
        });

        assertEquals(0, lookups.get());
    }
}
