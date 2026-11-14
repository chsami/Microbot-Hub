package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerBankStartupTest {
    @Test public void incompleteGearCannotBeDepositedBySupplyLoadingOnRetry() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn("test setup");
        when(setup.getEquipment()).thenReturn(List.of());
        when(setup.getInventory()).thenReturn(List.of());
        when(config.defaultInventorySetup()).thenReturn(setup);
        when(config.maxRecoveryAttempts()).thenReturn(6);
        Field field = DroSlayerScript.class.getDeclaredField("config");
        field.setAccessible(true); field.set(script, config);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        Method banking = DroSlayerScript.class.getDeclaredMethod("handleBankingState");
        banking.setAccessible(true);
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var equipment = mockStatic(Rs2Equipment.class); var player = mockStatic(Rs2Player.class);
             var dialogue = mockStatic(Rs2Dialogue.class); var dro = mockStatic(Microbot.class);
             var setups = mockStatic(MInventorySetupsPlugin.class); var runtime = mockStatic(DroSlayerRuntime.class);
             var loaders = mockConstruction(DroInventorySetup.class, (loader, context) ->
                     when(loader.loadEquipment()).thenReturn(context.getCount() == 3))) {
            dro.when(Microbot::getClientThread).thenReturn(clientThread);
            setups.when(MInventorySetupsPlugin::getInventorySetups).thenReturn(List.of(setup));
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(i -> ((BooleanSupplier) i.getArgument(2)).getAsBoolean());
            banking.invoke(script);
            assertEquals(1, loaders.constructed().size());
            DroInventorySetup first = loaders.constructed().get(0);
            verify(first).loadEquipment();
            verify(first, never()).loadInventory();
            banking.invoke(script);
            assertEquals(2, loaders.constructed().size());
            verify(loaders.constructed().get(1)).loadEquipment();
            verify(loaders.constructed().get(1), never()).loadInventory();
            Field complete = DroSlayerScript.class.getDeclaredField("baseBankLoadComplete");
            complete.setAccessible(true); assertFalse(complete.getBoolean(script));
            banking.invoke(script); // Gear is now confirmed; supplies may finally load.
            DroInventorySetup third = loaders.constructed().get(2);
            verify(third).loadEquipment();
            verify(third).loadInventory();
            assertFalse(complete.getBoolean(script)); // Inventory is still unconfirmed.

        }
    }
}
