package net.runelite.client.plugins.microbot.volcanicashminer;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerQueryable;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.mining.MiningAnimation;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VolcanicAshMinerScriptTest {
    private static final WorldPoint HERE = new WorldPoint(3790, 3770, 0);
    private static final int MINING = MiningAnimation.MINING_ANIMATIONS.iterator().next();

    private static VolcanicAshMinerConfig configWithMaxPlayers(int maxPlayers) {
        VolcanicAshMinerConfig config = mock(VolcanicAshMinerConfig.class);
        when(config.maxPlayersInArea()).thenReturn(maxPlayers);
        return config;
    }

    private static Rs2PlayerModel playerModel(Player player, int animation, WorldPoint location) {
        Rs2PlayerModel model = mock(Rs2PlayerModel.class);
        when(model.getPlayer()).thenReturn(player);
        when(model.getAnimation()).thenReturn(animation);
        when(model.getWorldLocation()).thenReturn(location);
        return model;
    }

    @Test
    void missingLocalLocationSkipsCheckWithoutTouchingPlayers() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse(new VolcanicAshMinerScript().hopIfTooManyPlayersNearby(configWithMaxPlayers(1)));

            microbot.verify(Microbot::getRs2PlayerCache, never());
            microbot.verify(Microbot::getClient, never());
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
        }
    }

    @Test
    void hopsOnlyWhenMoreNearbyMinersThanTheLimitWithoutRawPlayerLocation() {
        Player local = mock(Player.class);
        Player nearMiner = mock(Player.class);
        Player secondNearMiner = mock(Player.class);
        Player farMiner = mock(Player.class);
        Player idle = mock(Player.class);
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(local);
        when(client.getGameState()).thenReturn(GameState.HOPPING, GameState.LOGGED_IN);
        Rs2PlayerCache cache = mock(Rs2PlayerCache.class);
        when(cache.getStream()).thenAnswer(invocation -> Stream.of(
                playerModel(local, MINING, HERE),
                playerModel(nearMiner, MINING, HERE.dx(3)),
                playerModel(secondNearMiner, MINING, HERE.dy(-4)),
                playerModel(farMiner, MINING, HERE.dx(20)),
                playerModel(idle, -1, HERE.dy(2))));
        when(cache.query()).thenAnswer(invocation -> new Rs2PlayerQueryable());

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class);
             MockedStatic<Login> login = mockStatic(Login.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(HERE);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getRs2PlayerCache).thenReturn(cache);
            login.when(() -> Login.getRandomWorld(anyBoolean())).thenReturn(301);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(false, true);

            VolcanicAshMinerScript script = new VolcanicAshMinerScript();

            assertFalse(script.hopIfTooManyPlayersNearby(configWithMaxPlayers(2)));
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
            microbot.verify(() -> Microbot.log(anyString()), never());

            assertFalse(script.hopIfTooManyPlayersNearby(configWithMaxPlayers(1)));
            microbot.verify(() -> Microbot.log("2 other players are mining here (limit 1). Hopping to world 301..."));
            microbot.verify(() -> Microbot.log("Could not hop to world 301. Will try again shortly."));

            assertTrue(script.hopIfTooManyPlayersNearby(configWithMaxPlayers(1)));

            verify(local, never()).getWorldLocation();
            verifyNoInteractions(nearMiner, secondNearMiner, farMiner, idle);
        }
    }
}
