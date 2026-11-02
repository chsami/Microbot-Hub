package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingPrimaryAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingScriptState;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static net.runelite.api.gameval.ItemID.TINDERBOX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoWoodcuttingBurnStateTest {

    private static final WoodcuttingTree TREE = WoodcuttingTree.OAK;

    private static AutoWoodcuttingScript script() {
        return mock(AutoWoodcuttingScript.class, CALLS_REAL_METHODS);
    }

    private static AutoWoodcuttingConfig firemakeOnlyConfig(boolean firemakeOnly) {
        AutoWoodcuttingConfig config = mock(AutoWoodcuttingConfig.class);
        when(config.firemakeOnly()).thenReturn(firemakeOnly);
        return config;
    }

    private static AutoWoodcuttingConfig burnConfig(WoodcuttingPrimaryAction action) {
        AutoWoodcuttingConfig config = firemakeOnlyConfig(false);
        when(config.primaryAction()).thenReturn(action);
        return config;
    }

    private static AutoWoodcuttingScript burningScript(AutoWoodcuttingConfig config, boolean usedCampfire) {
        AutoWoodcuttingScript script = script();
        doReturn(TREE).when(script).getActiveTree();
        doReturn(usedCampfire).when(script).burnLog(config);
        doNothing().when(script).walkBack(config);
        doNothing().when(script).shutdown();
        script.woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
        return script;
    }

    private static void resetWithInventory(AutoWoodcuttingScript script, AutoWoodcuttingConfig config, boolean logsLeft, boolean hasTinderbox) {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            inventory.when(() -> Rs2Inventory.contains(TREE.getLog())).thenReturn(logsLeft);
            inventory.when(() -> Rs2Inventory.hasItem(TINDERBOX)).thenReturn(hasTinderbox);
            script.resetInventory(config);
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
        }
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

    @Test
    void campfireBurnStoppedEarlyStaysInResettingInsteadOfFiremaking() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN_CAMPFIRE);
        AutoWoodcuttingScript script = burningScript(config, true);

        resetWithInventory(script, config, true, false);

        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
        verify(script, never()).withdrawTinderbox();
        verify(script, never()).walkBack(config);
    }

    @Test
    void tinderboxBurnWithLogsLeftStaysInResettingInsteadOfFiremaking() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);

        resetWithInventory(script, config, true, true);

        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
        verify(script, never()).withdrawTinderbox();
    }

    @Test
    void burnWithNoLogsLeftWalksBackAndReturnsToCutting() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);

        resetWithInventory(script, config, false, true);

        assertEquals(WoodcuttingScriptState.WOODCUTTING, script.woodcuttingScriptState);
        verify(script).walkBack(config);
    }

    @Test
    void missingTinderboxIsWithdrawnAndBurningContinues() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);
        doReturn(true).when(script).withdrawTinderbox();

        resetWithInventory(script, config, true, false);

        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
        verify(script, never()).shutdown();
    }

    @Test
    void missingTinderboxThatCannotBeWithdrawnStopsWithMessage() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);
        doReturn(false).when(script).withdrawTinderbox();

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            inventory.when(() -> Rs2Inventory.contains(TREE.getLog())).thenReturn(true);
            script.resetInventory(config);
            microbot.verify(() -> Microbot.showMessage(anyString()));
        }

        verify(script).shutdown();
        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
    }
}
