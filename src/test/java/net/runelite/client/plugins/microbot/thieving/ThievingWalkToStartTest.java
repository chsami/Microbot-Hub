package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ThievingWalkToStartTest {

    @Test
    void walkToStartAdoptsThePlayerLocationWhenTheStartingLocationIsStillNull() throws Exception {
        ThievingScript script = script(ThievingNpc.MEN);
        setField(script, "startupGraceUntil", System.currentTimeMillis() + 60_000);
        WorldPoint here = new WorldPoint(3222, 3218, 0);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(null, here);

            Method method = ThievingScript.class.getDeclaredMethod("loop");
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script));

            assertEquals(here, getField(script, "startingLocation"));
            player.verify(Rs2Player::waitForWalking, never());
        }
    }

    private static ThievingScript script(ThievingNpc npc) {
        ThievingConfig config = mock(ThievingConfig.class);
        when(config.THIEVING_NPC()).thenReturn(npc);
        return new ThievingScript(config, mock(ThievingPlugin.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ThievingScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = ThievingScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
