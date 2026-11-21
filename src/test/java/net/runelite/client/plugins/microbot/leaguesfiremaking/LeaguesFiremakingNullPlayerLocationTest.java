package net.runelite.client.plugins.microbot.leaguesfiremaking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class LeaguesFiremakingNullPlayerLocationTest {

    private static final WorldPoint START_TILE = new WorldPoint(3165, 3487, 0);
    private static final WorldPoint FAR_TILE = new WorldPoint(3200, 3487, 0);

    @Test
    void walkingBackSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        LeaguesFiremakingScript script = new LeaguesFiremakingScript();
        LeaguesFiremakingConfig config = mock(LeaguesFiremakingConfig.class);
        when(config.scanRadius()).thenReturn(10);
        setField(script, "startPosition", START_TILE);
        setField(script, "state", State.WALKING_BACK);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = LeaguesFiremakingScript.class.getDeclaredMethod("handleWalkingBack", LeaguesFiremakingConfig.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script, config));

            assertEquals(State.WALKING_BACK, script.getState());
            player.verify(Rs2Player::isMoving, never());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        }
    }

    @Test
    void walkingBackStartsScanningOnceThePlayerIsInsideTheScanRadius() throws Exception {
        LeaguesFiremakingScript script = new LeaguesFiremakingScript();
        LeaguesFiremakingConfig config = mock(LeaguesFiremakingConfig.class);
        when(config.scanRadius()).thenReturn(10);
        setField(script, "startPosition", START_TILE);
        setField(script, "state", State.WALKING_BACK);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(START_TILE);

            Method method = LeaguesFiremakingScript.class.getDeclaredMethod("handleWalkingBack", LeaguesFiremakingConfig.class);
            method.setAccessible(true);
            method.invoke(script, config);

            assertEquals(State.SCANNING, script.getState());
        }
    }

    @Test
    void walkingToLineSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        LeaguesFiremakingScript script = new LeaguesFiremakingScript();
        setField(script, "currentLine", new FireLine(START_TILE, FAR_TILE, 36));
        setField(script, "state", State.WALKING_TO_LINE);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = LeaguesFiremakingScript.class.getDeclaredMethod("handleWalkingToLine");
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script));

            assertEquals(State.WALKING_TO_LINE, script.getState());
            player.verify(Rs2Player::isMoving, never());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
