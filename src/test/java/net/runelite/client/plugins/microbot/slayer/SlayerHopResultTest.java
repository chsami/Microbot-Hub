package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SlayerHopResultTest {

    private static final int CURRENT_WORLD = 330;
    private static final int TARGET_WORLD = 331;

    @Test
    void refusedHopStartsTheRefusalCooldownWithoutCountingAsAHop() throws Exception {
        SlayerScript script = scriptWithHopList();
        Client client = client();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class);
             MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            combat.when(Rs2Combat::inCombat).thenReturn(false);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(false);

            long before = System.currentTimeMillis();
            invoke(script, "hopToNewWorld");

            microbot.verify(() -> Microbot.hopToWorld(TARGET_WORLD), times(1));
            assertTrue((long) field(script, "lastHopRefusedAt") >= before);
            assertEquals(0L, field(script, "lastWorldHopTime"));
        }
    }

    @Test
    void inCombatSkipsTheHopAndLeavesPrayersAlone() throws Exception {
        SlayerScript script = scriptWithHopList();
        Client client = client();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class);
             MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            combat.when(Rs2Combat::inCombat).thenReturn(true);

            long before = System.currentTimeMillis();
            invoke(script, "hopToNewWorld");

            microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
            prayer.verifyNoInteractions();
            assertTrue((long) field(script, "lastHopRefusedAt") >= before);
        }
    }

    @Test
    void landedHopDoesNotWaitForSwitchWorldDialogOrPressSpace() throws Exception {
        SlayerScript script = scriptWithHopList();
        Client client = client();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class);
             MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            combat.when(Rs2Combat::inCombat).thenReturn(false);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(true);

            long before = System.currentTimeMillis();
            invoke(script, "hopToNewWorld");

            keyboard.verify(() -> Rs2Keyboard.keyPress(anyInt()), never());
            widget.verifyNoInteractions();
            assertTrue((long) field(script, "lastWorldHopTime") >= before);
            assertEquals(0L, field(script, "lastHopRefusedAt"));
        }
    }

    private static SlayerScript scriptWithHopList() throws Exception {
        SlayerScript script = new SlayerScript();
        SlayerConfig config = mock(SlayerConfig.class);
        when(config.hopWorldList()).thenReturn(String.valueOf(TARGET_WORLD));
        when(config.prayerFlickStyle()).thenReturn(PrayerFlickStyle.OFF);
        setField(script, "config", config);
        return script;
    }

    private static Client client() {
        Client client = mock(Client.class);
        when(client.getWorld()).thenReturn(CURRENT_WORLD);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        return client;
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
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
