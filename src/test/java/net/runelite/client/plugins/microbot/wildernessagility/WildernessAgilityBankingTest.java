package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WildernessAgilityBankingTest {

    private static final int KNIFE_ID = 946;
    private static final int COINS_ID = 995;
    private static final int TELEPORT_ID = 24963;

    @Test
    void missingLootingBagDoesNotBlockBanking() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.of(item(KNIFE_ID), item(COINS_ID)));
            inventory.when(() -> Rs2Inventory.hasItem(KNIFE_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(COINS_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.itemQuantity(COINS_ID)).thenReturn(150000);

            script.handleBanking();

            bank.verify(Rs2Bank::closeBank);
            microbot.verify(() -> Microbot.stopPlugin(any(Plugin.class)), never());
        }

        assertEquals("POST_BANK_CONFIG", get(script, "currentState").toString());
    }

    @Test
    void retryWithOnlySuppliesHeldSkipsDepositAll() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.of(item(COINS_ID)));
            inventory.when(() -> Rs2Inventory.hasItem(COINS_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.itemQuantity(COINS_ID)).thenReturn(150000);

            script.handleBanking();

            bank.verify(Rs2Bank::depositAll, never());
            bank.verify(() -> Rs2Bank.withdrawOne(KNIFE_ID));
        }
    }

    @Test
    void lootInInventoryIsDeposited() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.of(item(KNIFE_ID), item(24592)));

            script.handleBanking();

            bank.verify(Rs2Bank::depositAll);
        }
    }

    @Test
    void missingSuppliesStopThePluginAfterRetryLimit() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.empty());

            script.handleBanking();
            script.handleBanking();
            microbot.verify(() -> Microbot.stopPlugin(any(Plugin.class)), never());

            script.handleBanking();

            microbot.verify(() -> Microbot.stopPlugin(any(Plugin.class)), times(1));
            bank.verify(Rs2Bank::closeBank, never());
            bank.verify(() -> Rs2Bank.withdrawX(anyInt(), anyInt()), times(3));
        }

        assertEquals("BANKING", get(script, "currentState").toString());
    }

    @Test
    void stopReasonNamesItemsAndSettingsAndLootingBagNoticeIsLoggedOnce() throws Exception {
        WildernessAgilityScript script = bankingScript();
        WildernessAgilityConfig config = (WildernessAgilityConfig) get(script, "config");
        when(config.withdrawVenomProtection()).thenReturn(WildernessAgilityConfig.VenomProtectionOption.AntiVenomPlus);

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.empty());

            script.handleBanking();
            script.handleBanking();
            script.handleBanking();

            String expected = "Stopped: could not withdraw Knife, Coins, Anti-venom+ (4 dose) from the bank after 3 tries. Put them in your bank, or turn off 'Withdraw Knife' / 'Withdraw Coins' / 'Withdraw Venom Protection' under Banking Settings.";
            assertEquals(expected, Microbot.status);
            microbot.verify(() -> Microbot.log("[WildernessAgility] " + expected));
            microbot.verify(() -> Microbot.log(startsWith("[WildernessAgility] No looting bag")), times(1));
        }
    }

    @Test
    void partialCoinStackIsToppedUpToTarget() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.of(item(KNIFE_ID), item(COINS_ID)));
            inventory.when(() -> Rs2Inventory.hasItem(KNIFE_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(COINS_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.itemQuantity(COINS_ID)).thenReturn(100000);

            script.handleBanking();

            bank.verify(Rs2Bank::depositAll, never());
            bank.verify(() -> Rs2Bank.withdrawX(COINS_ID, 50000));
        }
    }

    @Test
    void coinStackAboveTargetIsDeposited() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.of(item(KNIFE_ID), item(COINS_ID)));
            inventory.when(() -> Rs2Inventory.hasItem(KNIFE_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(COINS_ID)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.itemQuantity(COINS_ID)).thenReturn(1000000);

            script.handleBanking();

            bank.verify(Rs2Bank::depositAll);
        }
    }

    @Test
    void itemForDisabledSettingIsDeposited() throws Exception {
        WildernessAgilityScript script = bankingScript();

        try (MockedStatic<Microbot> microbot = mockMicrobot();
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.of(item(KNIFE_ID), item(TELEPORT_ID)));

            script.handleBanking();

            bank.verify(Rs2Bank::depositAll);
        }
    }

    private static MockedStatic<Microbot> mockMicrobot() {
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        return microbot;
    }

    private static Rs2ItemModel item(int id) {
        Rs2ItemModel item = mock(Rs2ItemModel.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static WildernessAgilityScript bankingScript() throws Exception {
        WildernessAgilityConfig config = mock(WildernessAgilityConfig.class);
        when(config.withdrawLootingBag()).thenReturn(true);
        when(config.withdrawKnife()).thenReturn(true);
        when(config.withdrawCoins()).thenReturn(true);
        when(config.withdrawVenomProtection()).thenReturn(WildernessAgilityConfig.VenomProtectionOption.None);

        WildernessAgilityScript script = new WildernessAgilityScript();
        script.setPlugin(mock(WildernessAgilityPlugin.class));
        set(script, "config", config);
        set(script, "currentState", state("BANKING"));
        return script;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object state(String name) throws Exception {
        Class type = WildernessAgilityScript.class.getDeclaredField("currentState").getType();
        return Enum.valueOf(type, name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
