package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ThievingNullPlayerLocationTest {

    @Test
    void bankAndEquipWaitsForTheNextTickWhenThePlayerLocationIsNull() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(false);
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = ThievingScript.class.getDeclaredMethod("bankAndEquip");
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script));

            bank.verify(Rs2Bank::getNearestBank, never());
            bank.verify(Rs2Bank::openBank, never());
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any()), never());
        }
    }

    @Test
    void closeNearbyDoorStopsWhenThePlayerLocationIsLostAfterTheClick() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        WorldPoint here = new WorldPoint(3608, 3325, 0);
        Rs2TileObjectModel door = mock(Rs2TileObjectModel.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3610, 3325, 0));
        when(door.click("Close")).thenReturn(true);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.of(List.of(door)));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            player.when(Rs2Player::getWorldLocation).thenReturn(here, here, null);

            Method method = ThievingScript.class.getDeclaredMethod("closeNearbyDoor", int.class);
            method.setAccessible(true);
            assertFalse((boolean) method.invoke(script, 6));

            player.verify(Rs2Player::isMoving, never());
        }
    }

    private static ThievingScript script(ThievingNpc npc) {
        ThievingConfig config = mock(ThievingConfig.class);
        when(config.THIEVING_NPC()).thenReturn(npc);
        return new ThievingScript(config, mock(ThievingPlugin.class));
    }
}
