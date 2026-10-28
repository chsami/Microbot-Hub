package net.runelite.client.plugins.microbot.aiofighter.bank;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class BankerScriptBankingReleaseTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2Inventory> inventory;
    private AIOFighterConfig config;
    private BankerScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        bank = mockStatic(Rs2Bank.class);
        inventory = mockStatic(Rs2Inventory.class);
        bank.when(Rs2Bank::isOpen).thenReturn(true);
        inventory.when(() -> Rs2Inventory.waitForInventoryChanges(anyInt())).thenReturn(true);
        config = mock(AIOFighterConfig.class);
        when(config.bank()).thenReturn(true);
        script = new BankerScript();
        script.config = config;
        inventory.when(Rs2Inventory::isFull).thenReturn(true);
        assertTrue(script.needBanking());
        inventory.when(Rs2Inventory::isFull).thenReturn(false);
        inventory.when(Rs2Inventory::emptySlotCount).thenReturn(20);
    }

    @AfterEach
    void tearDown() {
        inventory.close();
        bank.close();
        microbot.close();
        BankerScript.inventorySetupChanged = false;
    }

    @Test
    void bankingWithoutInventorySetupDepositsAndReleasesLatch() {
        script.withdrawUpkeepItems(config);

        bank.verify(Rs2Bank::depositAll);
        assertFalse(script.needBanking());
    }

    @Test
    void slayerModeWithoutCurrentSetupDepositsAndReleasesLatch() {
        when(config.slayerMode()).thenReturn(true);
        when(config.slayerHasTaskWeakness()).thenReturn(false);
        script.withdrawUpkeepItems(config);

        bank.verify(Rs2Bank::depositAll);
        when(config.slayerMode()).thenReturn(false);
        assertFalse(script.needBanking());
    }

    @Test
    void inventorySetupSelectedButSetupNameNullDepositsAndReleasesLatch() {
        InventorySetup unnamed = mock(InventorySetup.class);
        when(config.useInventorySetup()).thenReturn(true);
        when(config.inventorySetup()).thenReturn(unnamed);
        script.withdrawUpkeepItems(config);

        bank.verify(Rs2Bank::depositAll);
        assertFalse(script.needBanking());
    }
}
