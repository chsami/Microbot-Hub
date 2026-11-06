package net.runelite.client.plugins.microbot.GemCrabKiller;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemCrabKillerScriptTest {

    @Test
    void dharokModeAtTenHpIsNotLowEvenBelowQuarterHealth() {
        assertFalse(GemCrabKillerScript.isLowHp(true, 10, 10.1d));
    }

    @Test
    void dharokModeIsLowAtTwoHpOrLess() {
        assertTrue(GemCrabKillerScript.isLowHp(true, 2, 2.0d));
        assertTrue(GemCrabKillerScript.isLowHp(true, 1, 1.0d));
        assertFalse(GemCrabKillerScript.isLowHp(true, 3, 3.0d));
    }

    @Test
    void walkingStartWithInventorySetupChecksSetup() {
        assertTrue(GemCrabKillerScript.shouldCheckSetupAtStart(true, GemCrabKillerState.WALKING));
    }

    @Test
    void startWithoutInventorySetupSkipsCheck() {
        for (GemCrabKillerState state : GemCrabKillerState.values()) {
            assertFalse(GemCrabKillerScript.shouldCheckSetupAtStart(false, state));
        }
    }

    @Test
    void startAtCrabOrBankSkipsCheck() {
        assertFalse(GemCrabKillerScript.shouldCheckSetupAtStart(true, GemCrabKillerState.FIGHTING));
        assertFalse(GemCrabKillerScript.shouldCheckSetupAtStart(true, GemCrabKillerState.WAITING));
        assertFalse(GemCrabKillerScript.shouldCheckSetupAtStart(true, GemCrabKillerState.BANKING));
    }

    @Test
    void startWithoutOverrideAlwaysBeginsWalking() {
        for (GemCrabKillerState state : GemCrabKillerState.values()) {
            assertEquals(GemCrabKillerState.WALKING, GemCrabKillerScript.startState(false, state));
            assertEquals(state, GemCrabKillerScript.startState(true, state));
        }
    }

    @Test
    void mismatchedSetupAtWalkingStartGoesToBankOnce() {
        GemCrabKillerScript script = new GemCrabKillerScript();
        script.gemCrabKillerState = GemCrabKillerState.WALKING;
        AtomicInteger checks = new AtomicInteger();

        assertTrue(script.checkSetupOnceAtStart(true, () -> {
            checks.incrementAndGet();
            return false;
        }));
        assertEquals(GemCrabKillerState.BANKING, script.gemCrabKillerState);

        script.gemCrabKillerState = GemCrabKillerState.WALKING;
        assertFalse(script.checkSetupOnceAtStart(true, () -> {
            checks.incrementAndGet();
            return false;
        }));
        assertEquals(GemCrabKillerState.WALKING, script.gemCrabKillerState);
        assertEquals(1, checks.get());
    }

    @Test
    void matchingSetupAtWalkingStartKeepsWalking() {
        GemCrabKillerScript script = new GemCrabKillerScript();
        script.gemCrabKillerState = GemCrabKillerState.WALKING;

        assertFalse(script.checkSetupOnceAtStart(true, () -> true));
        assertEquals(GemCrabKillerState.WALKING, script.gemCrabKillerState);
    }

    @Test
    void overriddenFightingStartDoesNotCheckSetup() {
        GemCrabKillerScript script = new GemCrabKillerScript();
        script.gemCrabKillerState = GemCrabKillerState.FIGHTING;

        assertFalse(script.checkSetupOnceAtStart(true, () -> {
            throw new AssertionError("setup must not be checked");
        }));
        assertEquals(GemCrabKillerState.FIGHTING, script.gemCrabKillerState);
    }

    @Test
    void setupLoadFailureNamesSetupAndMissingItem() {
        String withItem = GemCrabKillerScript.setupLoadFailedMessage("Crab", "Rune pickaxe");
        assertTrue(withItem.contains("'Crab'"));
        assertTrue(withItem.contains("Rune pickaxe is missing from your bank"));
        String withoutItem = GemCrabKillerScript.setupLoadFailedMessage("Crab", null);
        assertTrue(withoutItem.contains("'Crab'"));
        assertTrue(withoutItem.contains("some items are missing from your bank"));
        assertTrue(withoutItem.endsWith("then start the script again."));
    }

    @Test
    void normalModeKeepsQuarterHealthThreshold() {
        assertTrue(GemCrabKillerScript.isLowHp(false, 20, 24.9d));
        assertFalse(GemCrabKillerScript.isLowHp(false, 25, 25.0d));
        assertFalse(GemCrabKillerScript.isLowHp(false, 2, 50.0d));
    }
}
