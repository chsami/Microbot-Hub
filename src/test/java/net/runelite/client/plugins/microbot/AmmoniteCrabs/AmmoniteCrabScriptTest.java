package net.runelite.client.plugins.microbot.AmmoniteCrabs;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AmmoniteCrabScriptTest {

    private final WorldPoint fightLocation = new WorldPoint(3733, 3846, 0);

    @Test
    void nullPlayerLocationIsNotWithinRange() {
        assertFalse(AmmoniteCrabScript.isWithin(null, fightLocation, 3));
    }

    @Test
    void playerLocationWithinRangeIsDetected() {
        assertTrue(AmmoniteCrabScript.isWithin(new WorldPoint(3735, 3846, 0), fightLocation, 3));
        assertFalse(AmmoniteCrabScript.isWithin(new WorldPoint(3740, 3846, 0), fightLocation, 3));
    }

    @Test
    void nullLocationDetectsNoOtherPlayerWithoutQueryingPlayers() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            assertFalse(AmmoniteCrabScript.otherPlayerDetected(null));
            player.verify(() -> Rs2Player.getPlayers(any()), never());
        }
    }

    @Test
    void keyIsPressedWhenIdleLogoutIsNear() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            stubClient(microbot, "", null);
            player.when(() -> Rs2Player.checkIdleLogout(8000L)).thenReturn(true);

            assertTrue(AmmoniteCrabScript.preventIdleLogout(8000L));
            keyboard.verify(() -> Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE));
        }
    }

    @Test
    void noKeyIsPressedWhileInputIsRecent() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            stubClient(microbot, "", "");
            player.when(() -> Rs2Player.checkIdleLogout(8000L)).thenReturn(false);

            assertFalse(AmmoniteCrabScript.preventIdleLogout(8000L));
            keyboard.verifyNoInteractions();
        }
    }

    @Test
    void noKeyIsPressedWhileAChatMessageIsHalfTyped() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            stubClient(microbot, "brb", "");
            player.when(() -> Rs2Player.checkIdleLogout(8000L)).thenReturn(true);

            assertFalse(AmmoniteCrabScript.preventIdleLogout(8000L));
            keyboard.verifyNoInteractions();
        }
    }

    @Test
    void noKeyIsPressedWhileAnInputDialogHasText() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            stubClient(microbot, "", "25");
            player.when(() -> Rs2Player.checkIdleLogout(8000L)).thenReturn(true);

            assertFalse(AmmoniteCrabScript.preventIdleLogout(8000L));
            keyboard.verifyNoInteractions();
        }
    }

    @Test
    void resetStartsOnceTheCountdownIsDone() {
        assertFalse(AmmoniteCrabScript.afkCountdownDone(0));
        assertFalse(AmmoniteCrabScript.afkCountdownDone(AmmoniteCrabScript.AFK_LOOPS_BEFORE_RESET - 1));
        assertTrue(AmmoniteCrabScript.afkCountdownDone(AmmoniteCrabScript.AFK_LOOPS_BEFORE_RESET));
    }

    @SuppressWarnings("unchecked")
    private static void stubClient(MockedStatic<Microbot> microbot, String chatInput, String dialogInput) {
        Client client = mock(Client.class);
        when(client.getVarcStrValue(VarClientID.CHATINPUT)).thenReturn(chatInput);
        when(client.getVarcStrValue(VarClientID.MESLAYERINPUT)).thenReturn(dialogInput);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any(Callable.class)))
                .thenAnswer(invocation -> Optional.ofNullable(((Callable<Object>) invocation.getArgument(0)).call()));
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }
}
