package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import net.runelite.api.ChatMessageType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.microbot.moonsofperil.enums.Locations;
import net.runelite.client.plugins.microbot.moonsofperil.enums.Widgets;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RewardHandlerTest {
    private static final String SEALED_MESSAGE = "It's sealed tightly shut.";
    private static final int LUNAR_CHEST_ID = 51346;
    private static final int BANK_ALL_WIDGET_ID = 56885268;

    private static MoonsOfPerilConfig config() {
        MoonsOfPerilConfig config = mock(MoonsOfPerilConfig.class);
        when(config.enableEclipse()).thenReturn(true);
        when(config.enableBlue()).thenReturn(false);
        when(config.enableBlood()).thenReturn(false);
        when(config.debugLogging()).thenReturn(false);
        return config;
    }

    private static Rs2TileObjectCache chestCache() {
        Rs2TileObjectQueryable queryable = mock(Rs2TileObjectQueryable.class);
        when(queryable.interact(LUNAR_CHEST_ID, "Claim")).thenReturn(true);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(queryable);
        return cache;
    }

    @Test
    void sealedChestSuppressesRewardsUntilABossIsAliveAgain() {
        try (MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class)) {
            widget.when(() -> Rs2Widget.isHidden(anyInt())).thenReturn(false);
            RewardHandler handler = new RewardHandler(config());
            assertTrue(handler.validate());

            RewardHandler.onChatMessage(ChatMessageType.GAMEMESSAGE, SEALED_MESSAGE);
            assertFalse(handler.validate());
            assertFalse(handler.validate());

            widget.when(() -> Rs2Widget.isHidden(Widgets.ECLIPSE_MOON_ID.getID())).thenReturn(true);
            assertFalse(handler.validate());

            widget.when(() -> Rs2Widget.isHidden(Widgets.ECLIPSE_MOON_ID.getID())).thenReturn(false);
            assertTrue(handler.validate());
        }
    }

    @Test
    void unrelatedMessagesAndNewHandlerDoNotKeepRewardsSuppressed() {
        try (MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class)) {
            widget.when(() -> Rs2Widget.isHidden(anyInt())).thenReturn(false);
            RewardHandler handler = new RewardHandler(config());
            RewardHandler.onChatMessage(null, null);
            RewardHandler.onChatMessage(ChatMessageType.GAMEMESSAGE, null);
            RewardHandler.onChatMessage(ChatMessageType.GAMEMESSAGE, "You open the chest.");
            assertTrue(handler.validate());

            RewardHandler.onChatMessage(ChatMessageType.GAMEMESSAGE, SEALED_MESSAGE);
            assertFalse(handler.validate());
            assertTrue(new RewardHandler(config()).validate());
        }
    }

    @Test
    void playerTypedChatDoesNotSuppressRewards() {
        try (MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class)) {
            widget.when(() -> Rs2Widget.isHidden(anyInt())).thenReturn(false);
            RewardHandler handler = new RewardHandler(config());
            for (ChatMessageType type : new ChatMessageType[]{ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
                    ChatMessageType.PRIVATECHAT, ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT,
                    ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.TRADEREQ, ChatMessageType.AUTOTYPER}) {
                RewardHandler.onChatMessage(type, SEALED_MESSAGE);
                assertTrue(handler.validate(), type.name());
            }

            RewardHandler.onChatMessage(ChatMessageType.MESBOX, SEALED_MESSAGE);
            assertFalse(handler.validate());
        }
    }

    @Test
    void sealedReplyIsNotCountedAsAClaimedChest() {
        Rs2TileObjectCache cache = chestCache();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<BreakHandlerScript> breakHandler = mockStatic(BreakHandlerScript.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(Locations.REWARDS_CHEST_LOBBY.getWorldPoint());
            widget.when(() -> Rs2Widget.isHidden(Widgets.ECLIPSE_MOON_ID.getID())).thenReturn(false);
            RewardHandler handler = new RewardHandler(config());
            int before = RewardHandler.getRewardChestCount().get();
            Microbot.status = "IDLE";

            RewardHandler.onChatMessage(ChatMessageType.GAMEMESSAGE, SEALED_MESSAGE);
            handler.execute();

            assertEquals("Lunar Chest sealed - skipping rewards", Microbot.status);
            microbot.verify(() -> Microbot.log(contains("Lunar Chest is sealed: there are no rewards to claim.")));
            microbot.verify(() -> Microbot.log(contains("Eclipse: check mark, Blue: disabled, Blood: disabled")));

            assertEquals(before, RewardHandler.getRewardChestCount().get());
            widget.verify(() -> Rs2Widget.clickWidget(BANK_ALL_WIDGET_ID), never());
            breakHandler.verify(() -> BreakHandlerScript.setLockState(false));
            assertFalse(handler.validate());
        }
    }

    @Test
    void openedRewardInterfaceIsCountedAndBanked() {
        Rs2TileObjectCache cache = chestCache();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<BreakHandlerScript> breakHandler = mockStatic(BreakHandlerScript.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(Locations.REWARDS_CHEST_LOBBY.getWorldPoint());
            widget.when(() -> Rs2Widget.isWidgetVisible(BANK_ALL_WIDGET_ID)).thenReturn(true);
            RewardHandler handler = new RewardHandler(config());
            int before = RewardHandler.getRewardChestCount().get();

            handler.execute();

            assertEquals(before + 1, RewardHandler.getRewardChestCount().get());
            widget.verify(() -> Rs2Widget.clickWidget(BANK_ALL_WIDGET_ID));
            assertTrue(handler.validate());
        }
    }
}
