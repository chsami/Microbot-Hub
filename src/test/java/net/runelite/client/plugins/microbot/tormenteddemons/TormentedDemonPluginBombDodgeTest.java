package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TormentedDemonPluginBombDodgeTest {

    private static final WorldPoint BOMB = new WorldPoint(4070, 4430, 0);
    private static final WorldPoint SAFE = new WorldPoint(4071, 4430, 0);
    private static final String DODGED = "Successfully dodged Tormented Demon special attack.";

    private final TormentedDemonConfig config = mock(TormentedDemonConfig.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final TormentedDemonPlugin plugin = new TormentedDemonPlugin();
    private TormentedDemonScript script;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Tile> tile;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Prayer> prayer;

    @BeforeEach
    void setUp() throws Exception {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        tile = mockStatic(Rs2Tile.class);
        walker = mockStatic(Rs2Walker.class);
        prayer = mockStatic(Rs2Prayer.class);

        script = new TormentedDemonScript();
        when(config.dodgeDelay()).thenReturn(0);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        write("config", config);
        write("tormentedDemonScript", script);
        write("scheduledExecutorService", executor);

        HashMap<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(BOMB, 0);
        reachable.put(SAFE, 1);
        tile.when(() -> Rs2Tile.getReachableTilesFromTile(BOMB, TormentedDemonBombDodge.SAFE_TILE_RADIUS))
                .thenReturn(reachable);
    }

    @AfterEach
    void tearDown() {
        prayer.close();
        walker.close();
        tile.close();
        player.close();
        microbot.close();
    }

    @Test
    void clicksTheSafeTileEveryTickUntilThePlayerHasLeftTheBombTile() {
        script.bombDodge.addBomb(BOMB);

        tickAt(BOMB);
        tickAt(BOMB);
        tickAt(BOMB);

        walker.verify(() -> Rs2Walker.walkFastCanvas(SAFE), times(3));
        microbot.verify(() -> Microbot.log(anyString()), never());

        tickAt(SAFE);

        walker.verify(() -> Rs2Walker.walkFastCanvas(SAFE), times(3));
        microbot.verify(() -> Microbot.log(DODGED), times(1));
    }

    @Test
    void reportsAFailedDodgeWhenTheBombLandsOnThePlayer() {
        script.bombDodge.addBomb(BOMB);

        for (int tick = 0; tick <= TormentedDemonBombDodge.BOMB_TICKS; tick++) {
            tickAt(BOMB);
        }

        walker.verify(() -> Rs2Walker.walkFastCanvas(SAFE), times(TormentedDemonBombDodge.BOMB_TICKS));
        microbot.verify(() -> Microbot.log(startsWith("Failed to dodge Tormented Demon special attack")), times(1));
        microbot.verify(() -> Microbot.log(DODGED), never());
    }

    @Test
    void everyBombLogsItsOwnResult() {
        for (int bomb = 0; bomb < 2; bomb++) {
            script.bombDodge.addBomb(BOMB);
            tickAt(BOMB);
            for (int tick = 1; tick <= TormentedDemonBombDodge.BOMB_TICKS; tick++) {
                tickAt(SAFE);
            }
            assertFalse(script.bombDodge.hasPendingBombs());
        }

        microbot.verify(() -> Microbot.log(DODGED), times(2));
    }

    @Test
    void fightLoopDoesNothingWhileBombsArePendingEvenAfterTheDodge() {
        script.bombDodge.addBomb(BOMB);
        tickAt(BOMB);
        tickAt(SAFE);
        assertTrue(script.bombDodge.hasPendingBombs());

        script.handleFighting(config);

        prayer.verifyNoInteractions();
        player.verify(() -> Rs2Player.eatAt(anyInt()), never());
        player.verify(Rs2Player::getInteracting, never());
        walker.verify(() -> Rs2Walker.walkFastCanvas(any(WorldPoint.class), anyBoolean()), never());
    }

    private void tickAt(WorldPoint location) {
        player.when(Rs2Player::getWorldLocation).thenReturn(location);
        plugin.onGameTick(new GameTick());
    }

    private void write(String name, Object value) throws Exception {
        Field field = TormentedDemonPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
