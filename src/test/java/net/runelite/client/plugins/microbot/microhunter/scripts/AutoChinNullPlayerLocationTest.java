package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.microhunter.AutoHunterConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AutoChinNullPlayerLocationTest {

    private static final WorldPoint TARGET = new WorldPoint(2556, 2915, 0);

    @Test
    void handleMoveTargetSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoChinScript script = new AutoChinScript();
        setField(script, "moveTarget", TARGET);

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = AutoChinScript.class.getDeclaredMethod("handleMoveTarget");
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script));
        }

        assertEquals(TARGET, field(script, "moveTarget"));
        assertEquals(0, field(script, "moveTargetClickAttempts"));
    }

    @Test
    void updateSpawnRingSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoChinScript script = new AutoChinScript();
        Class<?> observationType = Class.forName(AutoChinScript.class.getName() + "$SpawnObservation");
        Constructor<?> constructor = observationType.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<WorldPoint, Object> observations = (Map<WorldPoint, Object>) field(script, "spawnObservations");
        observations.put(TARGET, constructor.newInstance(3, System.currentTimeMillis()));

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = AutoChinScript.class.getDeclaredMethod("updateSpawnRing", AutoHunterConfig.class);
            method.setAccessible(true);
            AutoHunterConfig config = mock(AutoHunterConfig.class);
            assertDoesNotThrow(() -> method.invoke(script, config));
        }

        assertNull(field(script, "bestSpawnTile"));
        assertEquals(0L, field(script, "nextRingEvaluationAt"));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
