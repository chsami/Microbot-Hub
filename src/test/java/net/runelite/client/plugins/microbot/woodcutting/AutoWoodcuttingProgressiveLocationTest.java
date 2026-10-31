package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingScriptState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoWoodcuttingProgressiveLocationTest {

    private static AutoWoodcuttingScript scriptIn(WoodcuttingScriptState state) {
        AutoWoodcuttingScript script = mock(AutoWoodcuttingScript.class, CALLS_REAL_METHODS);
        script.woodcuttingScriptState = state;
        return script;
    }

    private static AutoWoodcuttingConfig progressiveConfig(boolean progressive) {
        AutoWoodcuttingConfig config = mock(AutoWoodcuttingConfig.class);
        when(config.progressiveMode()).thenReturn(progressive);
        return config;
    }

    @Test
    void walksToProgressiveSpotWhileWoodcutting() {
        assertTrue(scriptIn(WoodcuttingScriptState.WOODCUTTING).shouldEnsureProgressiveLocation(progressiveConfig(true)));
    }

    @Test
    void staysOnBankTripWhileResetting() {
        assertFalse(scriptIn(WoodcuttingScriptState.RESETTING).shouldEnsureProgressiveLocation(progressiveConfig(true)));
    }

    @Test
    void staysPutWhileFiremaking() {
        assertFalse(scriptIn(WoodcuttingScriptState.FIREMAKING).shouldEnsureProgressiveLocation(progressiveConfig(true)));
    }

    @Test
    void ignoresProgressiveSpotWhenProgressiveModeIsOff() {
        assertFalse(scriptIn(WoodcuttingScriptState.WOODCUTTING).shouldEnsureProgressiveLocation(progressiveConfig(false)));
    }
}
