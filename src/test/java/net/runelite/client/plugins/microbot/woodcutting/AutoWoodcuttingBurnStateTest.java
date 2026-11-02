package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingScriptState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoWoodcuttingBurnStateTest {

    private static AutoWoodcuttingScript script() {
        return mock(AutoWoodcuttingScript.class, CALLS_REAL_METHODS);
    }

    private static AutoWoodcuttingConfig firemakeOnlyConfig(boolean firemakeOnly) {
        AutoWoodcuttingConfig config = mock(AutoWoodcuttingConfig.class);
        when(config.firemakeOnly()).thenReturn(firemakeOnly);
        return config;
    }

    @Test
    void keepsBurningWhileLogsAreLeftDuringNormalCutting() {
        assertEquals(WoodcuttingScriptState.RESETTING, script().stateAfterBurning(firemakeOnlyConfig(false), true));
    }

    @Test
    void returnsToCuttingOnceLogsAreBurned() {
        assertEquals(WoodcuttingScriptState.WOODCUTTING, script().stateAfterBurning(firemakeOnlyConfig(false), false));
    }

    @Test
    void keepsBurningWhileLogsAreLeftInFiremakeOnlyMode() {
        assertEquals(WoodcuttingScriptState.RESETTING, script().stateAfterBurning(firemakeOnlyConfig(true), true));
    }

    @Test
    void fetchesMoreLogsOnceBurnedInFiremakeOnlyMode() {
        assertEquals(WoodcuttingScriptState.FIREMAKING, script().stateAfterBurning(firemakeOnlyConfig(true), false));
    }

    @Test
    void doesNotBankForLogsWhenFiremakeOnlyIsOff() {
        assertFalse(script().shouldBankForLogs(firemakeOnlyConfig(false), false));
    }

    @Test
    void banksForLogsInFiremakeOnlyModeWhenOutOfLogs() {
        assertTrue(script().shouldBankForLogs(firemakeOnlyConfig(true), false));
    }

    @Test
    void doesNotBankForLogsWhileLogsAreLeft() {
        assertFalse(script().shouldBankForLogs(firemakeOnlyConfig(true), true));
    }
}
