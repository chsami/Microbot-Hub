package net.runelite.client.plugins.microbot.cannonballsmelter;

import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public final class CannonballSmelterScriptTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Keyboard> keyboard;
    private MockedStatic<Rs2Antiban> antiban;
    private CannonballSmelterScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        bank = mockStatic(Rs2Bank.class);
        inventory = mockStatic(Rs2Inventory.class);
        keyboard = mockStatic(Rs2Keyboard.class);
        antiban = mockStatic(Rs2Antiban.class);
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        microbot.when(Microbot::getClient).thenReturn(client);
        bank.when(Rs2Bank::isOpen).thenReturn(true);
        script = new CannonballSmelterScript();
    }

    @AfterEach
    void tearDown() {
        antiban.close();
        keyboard.close();
        inventory.close();
        bank.close();
        microbot.close();
    }

    @Test
    void bankWithMouldAndEmptyInventoryWithdrawsSteelBars() {
        inventory.when(() -> Rs2Inventory.hasItem(ItemID.STEEL_BAR)).thenReturn(false, true);
        inventory.when(() -> Rs2Inventory.hasItem(ItemID.MCANNONBALL)).thenReturn(false);
        bank.when(() -> Rs2Bank.hasItem(ItemID.STEEL_BAR)).thenReturn(true);

        script.bank();

        bank.verify(() -> Rs2Bank.withdrawAll(ItemID.STEEL_BAR));
        microbot.verify(() -> Microbot.showMessage(anyString()), never());
    }

    @Test
    void getMouldWithFullInventoryDepositsBeforeWithdrawingMould() {
        inventory.when(() -> Rs2Inventory.hasItem("ammo mould")).thenReturn(false);
        inventory.when(() -> Rs2Inventory.hasItem("double ammo mould")).thenReturn(false);
        inventory.when(Rs2Inventory::isFull).thenReturn(true, false);
        inventory.when(() -> Rs2Inventory.hasItem(ItemID.STEEL_BAR)).thenReturn(true);
        bank.when(() -> Rs2Bank.hasItem("ammo mould")).thenReturn(true);
        bank.when(() -> Rs2Bank.hasItem(ItemID.STEEL_BAR)).thenReturn(true);

        script.getMould();

        InOrder order = inOrder(Rs2Bank.class);
        order.verify(bank, () -> Rs2Bank.depositAll());
        order.verify(bank, () -> Rs2Bank.withdrawOne("ammo mould"));
    }

    @Test
    void getMouldWithNoMouldInBankDoesNotDeposit() {
        inventory.when(() -> Rs2Inventory.hasItem("ammo mould")).thenReturn(false);
        inventory.when(() -> Rs2Inventory.hasItem("double ammo mould")).thenReturn(false);
        inventory.when(Rs2Inventory::isFull).thenReturn(true);

        script.getMould();

        bank.verify(() -> Rs2Bank.depositAll(), never());
    }

    @Test
    void getMouldStopsAfterMissingMouldMessage() {
        inventory.when(() -> Rs2Inventory.hasItem("ammo mould")).thenReturn(false);
        inventory.when(() -> Rs2Inventory.hasItem("double ammo mould")).thenReturn(false);

        script.getMould();

        microbot.verify(() -> Microbot.showMessage(anyString()), times(1));
        bank.verify(() -> Rs2Bank.withdrawOne(anyString()), never());
        bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
    }

    @Test
    void getMouldStopsAfterMissingSteelBarsMessage() {
        inventory.when(() -> Rs2Inventory.hasItem("ammo mould")).thenReturn(true);

        script.getMould();

        microbot.verify(() -> Microbot.showMessage(anyString()), times(1));
        bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), never());
    }
}
