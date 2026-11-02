package net.runelite.client.plugins.microbot.mining;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AutoMiningNearbyPlayersTest {

    @Test
    void skipsTheTickWithoutTouchingTheClientThreadWhenThePlayerLocationIsNull() {
        AutoMiningConfig config = mock(AutoMiningConfig.class);
        when(config.maxPlayersInArea()).thenReturn(2);
        when(config.distanceToStray()).thenReturn(10);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertTrue(new AutoMiningScript().handleNearbyPlayers(config));

            microbot.verify(Microbot::getClientThread, never());
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
        }
    }

    @Test
    void doesNothingWhenTheMaxPlayersCheckIsOff() {
        AutoMiningConfig config = mock(AutoMiningConfig.class);
        when(config.maxPlayersInArea()).thenReturn(0);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            assertFalse(new AutoMiningScript().handleNearbyPlayers(config));

            player.verify(Rs2Player::getWorldLocation, never());
            microbot.verify(Microbot::getClientThread, never());
        }
    }

    @Test
    void keepsMiningWhenFewerPlayersThanTheLimitAreNearby() {
        AutoMiningConfig config = mock(AutoMiningConfig.class);
        when(config.maxPlayersInArea()).thenReturn(2);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any(Callable.class))).thenReturn(Optional.of(1L));
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3285, 3365, 0));
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);

            assertFalse(new AutoMiningScript().handleNearbyPlayers(config));

            microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
        }
    }

    @Test
    void hopsAndEndsTheTickWhenTheAreaIsCrowded() {
        AutoMiningConfig config = mock(AutoMiningConfig.class);
        when(config.maxPlayersInArea()).thenReturn(2);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any(Callable.class))).thenReturn(Optional.of(3L));
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Login> login = mockStatic(Login.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3285, 3365, 0));
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            login.when(() -> Login.getRandomWorld(anyBoolean())).thenReturn(420);
            microbot.when(() -> Microbot.hopToWorld(420)).thenReturn(true);

            assertTrue(new AutoMiningScript().handleNearbyPlayers(config));

            microbot.verify(() -> Microbot.hopToWorld(420), times(1));
        }
    }
}
