package net.runelite.client.plugins.microbot.aiofighter.safety;

import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class SafetyScriptTest {

    private static AIOFighterConfig config(boolean bank, boolean useInventorySetup, boolean hasCurrentSetup) {
        AIOFighterConfig config = mock(AIOFighterConfig.class);
        when(config.bank()).thenReturn(bank);
        when(config.useInventorySetup()).thenReturn(useInventorySetup);
        when(config.currentInventorySetup()).thenReturn(hasCurrentSetup ? mock(InventorySetup.class) : null);
        return config;
    }

    @Test
    void bankWithoutSelectedSetupDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, false, false), ""));
        assertFalse(SafetyScript.bankRestocksFood(config(true, true, true), ""));
        assertFalse(SafetyScript.bankRestocksFood(config(true, true, true), null));
    }

    @Test
    void selectedSetupWithInventorySetupOffDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, false, true), "Melee"));
    }

    @Test
    void missingCurrentSetupDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, true, false), "Melee"));
    }

    @Test
    void bankingOffDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(false, true, true), "Melee"));
    }

    @Test
    void bankingWithUsableSetupRestocksFood() {
        assertTrue(SafetyScript.bankRestocksFood(config(true, true, true), "Melee"));
    }
}
