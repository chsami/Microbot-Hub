package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class WildernessAgilityEmergencyEscapeTest {

    private static final WorldPoint GATE_TILE = new WorldPoint(2998, 3931, 0);

    @Test
    void nullPlayerLocationSkipsRockStep() throws Exception {
        WildernessAgilityScript script = escapingScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertDoesNotThrow(script::handleEmergencyEscape);

            walker.verifyNoInteractions();
        }

        assertFalse((boolean) get(script, "hasClimbedRocks"));
        assertEquals(0L, (long) get(script, "escapeStep2StartTime"));
    }

    @Test
    void playerAtGateCompletesRockStep() throws Exception {
        WildernessAgilityScript script = escapingScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(GATE_TILE);

            assertDoesNotThrow(script::handleEmergencyEscape);

            walker.verifyNoInteractions();
        }

        assertTrue((boolean) get(script, "hasClimbedRocks"));
    }

    private static WildernessAgilityScript escapingScript() throws Exception {
        WildernessAgilityScript script = new WildernessAgilityScript();
        set(script, "emergencyEscapeTriggered", true);
        set(script, "emergencyEscapeStartTime", System.currentTimeMillis());
        set(script, "hasEquippedPhoenixNecklace", true);
        return script;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
