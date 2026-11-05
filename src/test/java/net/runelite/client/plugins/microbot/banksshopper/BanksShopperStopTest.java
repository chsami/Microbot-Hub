package net.runelite.client.plugins.microbot.banksshopper;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanksShopperStopTest {

    @Test
    void notEnoughCoinsStopsPluginWithoutBlockingDialog() throws Exception {
        BanksShopperPlugin plugin = new BanksShopperPlugin();
        BanksShopperScript script = mock(BanksShopperScript.class);
        Field field = BanksShopperPlugin.class.getDeclaredField("banksShopperScript");
        field.setAccessible(true);
        field.set(plugin, script);

        ChatMessage event = mock(ChatMessage.class);
        when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
        when(event.getMessage()).thenReturn("You don't have enough coins.");

        Microbot.pauseAllScripts.set(false);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            plugin.onChatMessage(event);

            microbot.verify(() -> Microbot.stopPlugin(plugin), times(1));
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
            verify(script, never()).shutdown();
        }
    }

    @Test
    void notEnoughCoinsMarksStopAsSupplyStop() throws Exception {
        BanksShopperPlugin plugin = new BanksShopperPlugin();
        BanksShopperScript script = mock(BanksShopperScript.class);
        Field field = BanksShopperPlugin.class.getDeclaredField("banksShopperScript");
        field.setAccessible(true);
        field.set(plugin, script);

        ChatMessage event = mock(ChatMessage.class);
        when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
        when(event.getMessage()).thenReturn("You don't have enough coins.");

        Microbot.pauseAllScripts.set(false);
        try (MockedStatic<Microbot> ignored = mockStatic(Microbot.class)) {
            plugin.onChatMessage(event);
            verify(script).setStopForSupply(true);
        }
    }

    @Test
    void manualStopDoesNotLogOut() {
        BanksShopperPlugin plugin = mock(BanksShopperPlugin.class);
        when(plugin.isUseLogout()).thenReturn(true);
        BanksShopperScript script = new BanksShopperScript(plugin);

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Shop> shop = mockStatic(Rs2Shop.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            script.shutdown();

            player.verify(Rs2Player::logout, never());
        }
    }

    @Test
    void supplyStopLogsOutOnceThenLaterManualStopDoesNot() {
        BanksShopperPlugin plugin = mock(BanksShopperPlugin.class);
        when(plugin.isUseLogout()).thenReturn(true);
        BanksShopperScript script = new BanksShopperScript(plugin);

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Shop> shop = mockStatic(Rs2Shop.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            script.setStopForSupply(true);
            script.shutdown();
            script.shutdown();

            player.verify(Rs2Player::logout, times(1));
        }
    }

    @Test
    void supplyStopRespectsLogoutSetting() {
        BanksShopperPlugin plugin = mock(BanksShopperPlugin.class);
        when(plugin.isUseLogout()).thenReturn(false);
        BanksShopperScript script = new BanksShopperScript(plugin);

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Shop> shop = mockStatic(Rs2Shop.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            script.setStopForSupply(true);
            script.shutdown();

            player.verify(Rs2Player::logout, never());
        }
    }
}
