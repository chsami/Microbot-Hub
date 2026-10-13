package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.Client;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AutoChinHopResultTest {

    private static final int SOURCE_WORLD = 330;
    private static final int TARGET_WORLD = 480;

    @Test
    void refusedHopIsNotCountedOrBlacklistedAndIsRetriedForTheSameWorld() throws Exception {
        AutoChinScript script = new AutoChinScript();
        Client client = mock(Client.class);
        when(client.getWorld()).thenReturn(SOURCE_WORLD);
        WorldService worldService = mock(WorldService.class);
        WorldResult worldResult = mock(WorldResult.class);
        World target = mock(World.class);
        when(target.getId()).thenReturn(TARGET_WORLD);
        when(target.getRegion()).thenReturn(WorldRegion.AUSTRALIA);
        when(target.getTypes()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(target.getPlayers()).thenReturn(200);
        when(worldResult.getWorlds()).thenReturn(List.of(target));
        when(worldService.getWorlds()).thenReturn(worldResult);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getWorldService).thenReturn(worldService);
            player.when(Rs2Player::isMember).thenReturn(true);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(false);

            invoke(script, "requestAustralianWorldHop", "occupied");

            assertEquals(0, field(script, "worldHopAttempts"));
            assertTrue(((Map<?, ?>) field(script, "rejectedWorlds")).isEmpty());
            assertEquals(TARGET_WORLD, field(script, "hopTargetWorld"));
            assertEquals("HOPPING_WORLD", field(script, "currentState").toString());

            invoke(script, "monitorWorldHop");
            microbot.verify(() -> Microbot.hopToWorld(TARGET_WORLD), times(1));

            setField(script, "hopStartedAt", System.currentTimeMillis() - 3_000);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(true);
            invoke(script, "monitorWorldHop");

            microbot.verify(() -> Microbot.hopToWorld(TARGET_WORLD), times(2));
            assertEquals(1, field(script, "worldHopAttempts"));
            assertTrue(((Map<?, ?>) field(script, "rejectedWorlds")).isEmpty());
        }
    }

    @Test
    void persistentRefusalBlacklistsTheTargetAndMovesToAnotherWorld() throws Exception {
        AutoChinScript script = new AutoChinScript();
        Client client = mock(Client.class);
        when(client.getWorld()).thenReturn(SOURCE_WORLD);
        WorldService worldService = australianWorlds(TARGET_WORLD, TARGET_WORLD + 1);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getWorldService).thenReturn(worldService);
            player.when(Rs2Player::isMember).thenReturn(true);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(false);

            invoke(script, "requestAustralianWorldHop", "occupied");
            int firstTarget = (int) field(script, "hopTargetWorld");

            for (int i = 0; i < 3; i++) {
                setField(script, "hopStartedAt", System.currentTimeMillis() - 3_000);
                invoke(script, "monitorWorldHop");
            }

            microbot.verify(() -> Microbot.hopToWorld(firstTarget), times(3));
            assertTrue(((Map<?, ?>) field(script, "rejectedWorlds")).containsKey(firstTarget));
            assertEquals(1, field(script, "worldHopAttempts"));
            int secondTarget = (int) field(script, "hopTargetWorld");
            assertNotEquals(firstTarget, secondTarget);
            microbot.verify(() -> Microbot.hopToWorld(secondTarget), times(1));
            assertEquals("HOPPING_WORLD", field(script, "currentState").toString());
        }
    }

    @Test
    void persistentRefusalOnEveryWorldStopsAtTheAttemptCap() throws Exception {
        AutoChinScript script = new AutoChinScript();
        Client client = mock(Client.class);
        when(client.getWorld()).thenReturn(SOURCE_WORLD);
        WorldService worldService = australianWorlds(480, 481, 482, 483, 484, 485, 486, 487);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getWorldService).thenReturn(worldService);
            player.when(Rs2Player::isMember).thenReturn(true);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(false);

            invoke(script, "requestAustralianWorldHop", "occupied");
            for (int i = 0; i < 100 && !"STOPPED".equals(field(script, "currentState").toString()); i++) {
                setField(script, "hopStartedAt", System.currentTimeMillis() - 3_000);
                invoke(script, "monitorWorldHop");
            }

            assertEquals("STOPPED", field(script, "currentState").toString());
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(15));
            assertEquals(5, ((Map<?, ?>) field(script, "rejectedWorlds")).size());
        }
    }

    private static WorldService australianWorlds(int... ids) {
        List<World> worlds = new ArrayList<>();
        for (int id : ids) {
            World world = mock(World.class);
            when(world.getId()).thenReturn(id);
            when(world.getRegion()).thenReturn(WorldRegion.AUSTRALIA);
            when(world.getTypes()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(world.getPlayers()).thenReturn(200);
            worlds.add(world);
        }
        WorldResult worldResult = mock(WorldResult.class);
        when(worldResult.getWorlds()).thenReturn(worlds);
        WorldService worldService = mock(WorldService.class);
        when(worldService.getWorlds()).thenReturn(worldResult);
        return worldService;
    }

    private static void invoke(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
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
