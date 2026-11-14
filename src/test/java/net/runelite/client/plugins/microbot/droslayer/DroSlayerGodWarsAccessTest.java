package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerGodWarsAccessTest {
    @Test public void progressIsObservedAcrossPlanesAndResumesAtEachEntranceStage() {
        assertEquals(DroSlayerGodWarsAccess.Step.CAVE, DroSlayerGodWarsAccess.step(new WorldPoint(3098,3494,0)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3056,10165,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3065,10159,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3058,10161,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3057,10165,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3057,10163,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3058,10163,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.BOULDER, DroSlayerGodWarsAccess.step(new WorldPoint(3059,10165,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.CREVICE, DroSlayerGodWarsAccess.step(new WorldPoint(3052,10165,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.CREVICE, DroSlayerGodWarsAccess.step(new WorldPoint(3050,10165,3)));
        assertEquals(DroSlayerGodWarsAccess.Step.INSIDE, DroSlayerGodWarsAccess.step(new WorldPoint(3030,10158,0)));
        assertEquals(DroSlayerGodWarsAccess.Step.INSIDE, DroSlayerGodWarsAccess.step(new WorldPoint(3043,10139,0)));
        assertEquals(DroSlayerGodWarsAccess.Step.CAVE, DroSlayerGodWarsAccess.step(new WorldPoint(3222,3218,0)));
    }

    @Test public void bankPlanningTargetsSurfaceWhileInteriorKeepsMonsterDestination() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.maxRecoveryAttempts()).thenReturn(3);
        set(script,"config",config);
        set(script,"taskLocationName","Wilderness God Wars Dungeon - Bloodveld");
        WorldPoint destination = new WorldPoint(3043,10139,0);
        set(script,"taskDestination",destination);
        Method handler = DroSlayerScript.class.getDeclaredMethod("handleGodWarsEntrance");
        handler.setAccessible(true);
        try (var player = mockStatic(Rs2Player.class); var runtime = mockStatic(DroSlayerRuntime.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3098,3494,0));
            assertEquals(true, handler.invoke(script));
            runtime.verify(() -> DroSlayerRuntime.walkFull(config, DroSlayerGodWarsAccess.CAVE, 3));
            runtime.verify(() -> DroSlayerRuntime.walkFull(config, destination, 3), never());
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3030,10158,0));
            assertEquals(false, handler.invoke(script));
            Field field = DroSlayerScript.class.getDeclaredField("taskDestination"); field.setAccessible(true);
            assertEquals(destination, field.get(script));
        }
    }
    @Test public void crossingAnimationHoldsBeforeAnyNewLocalMovement() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script,"config",config);
        set(script,"taskLocationName","Wilderness God Wars Dungeon - Bloodveld");
        var thread = mock(net.runelite.client.callback.ClientThread.class);
        when(thread.runOnClientThreadOptional(any(java.util.concurrent.Callable.class)))
            .thenReturn(java.util.Optional.of(true));
        Method handler = DroSlayerScript.class.getDeclaredMethod("handleGodWarsEntrance");
        handler.setAccessible(true);
        try (var player = mockStatic(Rs2Player.class);
             var dro = mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3058,10163,3));
            dro.when(net.runelite.client.plugins.microbot.Microbot::getClientThread).thenReturn(thread);
            assertEquals(true, handler.invoke(script));
            runtime.verify(() -> DroSlayerRuntime.cancelWalk());
            runtime.verifyNoMoreInteractions();
        }
    }

    @Test public void missingBoulderNeverStartsTeleportCapableTravelAndLoadedBoulderIsClicked() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.maxRecoveryAttempts()).thenReturn(3);
        set(script,"config",config);
        set(script,"taskLocationName","Wilderness God Wars Dungeon - Bloodveld");
        var thread = mock(net.runelite.client.callback.ClientThread.class);
        when(thread.runOnClientThreadOptional(any(java.util.concurrent.Callable.class)))
            .thenAnswer(invocation -> java.util.Optional.ofNullable(
                ((java.util.concurrent.Callable<?>) invocation.getArgument(0)).call()));
        var client = mock(net.runelite.api.Client.class);
        var local = mock(net.runelite.api.Player.class);
        when(client.getLocalPlayer()).thenReturn(local);
        when(local.getAnimation()).thenReturn(-1);
        var cache = mock(net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache.class);
        var query = mock(net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        var object = mock(net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel.class);
        when(object.getWorldLocation()).thenReturn(new WorldPoint(3053,10166,3));

        Method handler = DroSlayerScript.class.getDeclaredMethod("handleGodWarsEntrance");
        handler.setAccessible(true);
        try (var player = mockStatic(Rs2Player.class);
             var dro = mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            when(object.click("Move")).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3065,10159,3));
            dro.when(net.runelite.client.plugins.microbot.Microbot::getClientThread).thenReturn(thread);
            dro.when(net.runelite.client.plugins.microbot.Microbot::getClient).thenReturn(client);
            dro.when(net.runelite.client.plugins.microbot.Microbot::getRs2NpcCache).thenReturn(cache);
            assertEquals(true, handler.invoke(script));
            runtime.verify(() -> DroSlayerRuntime.cancelWalk(), atLeastOnce());
            runtime.verifyNoMoreInteractions();
            set(script,"godWarsAccessClickAt",0L);
            when(query.nearestOnClientThread(DroSlayerGodWarsAccess.BOULDER,16)).thenReturn(object);
            runtime.when(() -> DroSlayerRuntime.performAction(
                any(net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro.ActionPhase.class), eq(false),
                any(java.util.function.BooleanSupplier.class))).thenAnswer(invocation ->
                    ((java.util.function.BooleanSupplier) invocation.getArgument(2)).getAsBoolean());
            assertEquals(true, handler.invoke(script));
            verify(object).click("Move");
            verify(query, atLeastOnce()).withId(6621);
            dro.verify(net.runelite.client.plugins.microbot.Microbot::getRs2TileObjectCache, never());
            runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            runtime.verify(() -> DroSlayerRuntime.walkAdaptive(any(), any(), anyInt(), anyString()), never());
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
}
