package net.runelite.client.plugins.microbot.droslayer;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerCannonNpcApproachTest {
    @Test void anObstructedNpcApproachUsesTheWalkerEvenWhenNativeRecoveryIsArmed() {
        try (var f = new Fixture()) {
            when(f.model.hasLineOfSight()).thenReturn(false);
            assertFalse(DroSlayerCannonNpcApproach.talk(f.config, f.model));
            f.runtime.verify(() -> DroSlayerRuntime.walkAdaptive(f.config, f.target, 1, "Nulodion cannon reclaim NPC approach"));
            verify(f.model, never()).click(anyString());
            f.nativeWalker.verifyNoInteractions();
            f.micro.verify(() -> Microbot.doInvoke(any(), any(Rectangle.class)), never());
        }
    }

    @Test void distantNpcDoesNotTriggerAnImplicitNativeApproach() {
        try (var f = new Fixture()) {
            when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3014, 3453, 0));
            assertFalse(DroSlayerCannonNpcApproach.talk(f.config, f.model));
            f.runtime.verify(() -> DroSlayerRuntime.walkAdaptive(f.config, f.target, 1, "Nulodion cannon reclaim NPC approach"));
            verify(f.model, never()).click(anyString());
            f.nativeWalker.verifyNoInteractions();
        }
    }

    @Test void adjacentVisibleNpcDispatchesTheLiveTalkActionWithoutGenericRecovery() {
        try (var f = new Fixture()) {
            assertTrue(DroSlayerCannonNpcApproach.talk(f.config, f.model));
            f.runtime.verify(DroSlayerRuntime::cancelWalk);
            f.micro.verify(() -> Microbot.doInvoke(argThat(e -> e.getType() == MenuAction.NPC_SECOND_OPTION
                && e.getOption().equals("Talk-to") && e.getIdentifier() == 42), any(Rectangle.class)));
            verify(f.model, never()).click(anyString());
            f.nativeWalker.verifyNoInteractions();
        }
    }

    @Test void unavailableTalkActionCannotDispatchTradeOrAnotherNpcAction() {
        try (var f = new Fixture()) {
            when(f.definition.getActions()).thenReturn(new String[]{"Trade", null});
            assertFalse(DroSlayerCannonNpcApproach.talk(f.config, f.model));
            f.micro.verify(() -> Microbot.doInvoke(any(), any(Rectangle.class)), never());
            f.nativeWalker.verifyNoInteractions();
        }
    }

    @Test void closedDoorIsOpenedBeforeAnyNpcInteraction() throws Exception {
        try (var f = new Fixture(); var bank = mockStatic(Rs2Bank.class);
             var inventory = mockStatic(Rs2Inventory.class); var dialogue = mockStatic(Rs2Dialogue.class)) {
            when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3015, 3454, 0));
            var cache = mock(Rs2TileObjectCache.class, RETURNS_DEEP_STUBS);
            var door = mock(Rs2TileObjectModel.class);when(door.click("Open")).thenReturn(true);
            f.micro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            when(cache.query().where(any()).nearestOnClientThread()).thenReturn(door);
            f.runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(BooleanSupplier.class)))
                .thenAnswer(i -> ((BooleanSupplier)i.getArgument(2)).getAsBoolean());
            invokeReclaim(f.config);
            verify(door).click("Open");
            f.micro.verify(Microbot::getRs2NpcCache, never());
            f.nativeWalker.verifyNoInteractions();
        }
    }

    @Test void anOpenDoorStillRequiresWalkerEntryBeforeTalking() throws Exception {
        try (var f = new Fixture(); var bank = mockStatic(Rs2Bank.class);
             var inventory = mockStatic(Rs2Inventory.class); var dialogue = mockStatic(Rs2Dialogue.class)) {
            when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3015, 3454, 0));
            var cache = mock(Rs2TileObjectCache.class, RETURNS_DEEP_STUBS);
            f.micro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            when(cache.query().where(any()).nearestOnClientThread()).thenReturn(null);
            invokeReclaim(f.config);
            f.runtime.verify(() -> DroSlayerRuntime.walkAdaptive(f.config, new WorldPoint(3014,3453,0), 0, "Nulodion doorway"));
            f.micro.verify(Microbot::getRs2NpcCache, never());
            f.nativeWalker.verifyNoInteractions();
        }
    }

    private static void invokeReclaim(DroSlayerConfig config) throws Exception {
        var script = new DroSlayerScript();
        var settings = DroSlayerScript.class.getDeclaredField("config");settings.setAccessible(true);settings.set(script, config);
        var recovery = DroSlayerScript.class.getDeclaredField("cannonReclaim");recovery.setAccessible(true);
        var reclaim = (DroSlayerCannonReclaim) recovery.get(script);
        long now = System.currentTimeMillis();reclaim.missing(now-DroSlayerCannonReclaim.DECAY_WAIT_MS-1);
        assertTrue(reclaim.startIfDue(now,true));reclaim.bankChecked();
        var method = DroSlayerScript.class.getDeclaredMethod("handleCannonReclaim");method.setAccessible(true);
        assertEquals(true, method.invoke(script));
    }

    private static final class Fixture implements AutoCloseable {
        final org.mockito.MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final org.mockito.MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class);
        final org.mockito.MockedStatic<Rs2Walker> nativeWalker = mockStatic(Rs2Walker.class);
        final org.mockito.MockedStatic<Rs2Player> players = mockStatic(Rs2Player.class);
        final org.mockito.MockedStatic<Rs2UiHelper> ui = mockStatic(Rs2UiHelper.class);
        final DroSlayerConfig config = mock(DroSlayerConfig.class);
        final Rs2NpcModel model = mock(Rs2NpcModel.class);
        final NPC npc = mock(NPC.class);
        final NPCComposition definition = mock(NPCComposition.class);
        final Player player = mock(Player.class);
        final WorldPoint target = new WorldPoint(3010, 3452, 0);
        final boolean priorCantReach = Microbot.cantReachTarget;
        final boolean priorDetection = Microbot.isCantReachTargetDetectionEnabled;
        Fixture() {
            Microbot.cantReachTarget = true;
            Microbot.isCantReachTargetDetectionEnabled = true;
            var client = mock(Client.class);var thread = mock(ClientThread.class);
            micro.when(Microbot::getClient).thenReturn(client);micro.when(Microbot::getClientThread).thenReturn(thread);
            when(client.getLocalPlayer()).thenReturn(player);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3011, 3452, 0));
            players.when(Rs2Player::getWorldLocation).thenAnswer(i -> player.getWorldLocation());
            when(model.getWorldLocation()).thenReturn(target);when(model.hasLineOfSight()).thenReturn(true);
            when(model.getNpc()).thenReturn(npc);when(npc.getTransformedComposition()).thenReturn(definition);
            when(npc.getIndex()).thenReturn(42);when(npc.getName()).thenReturn("Nulodion");
            when(definition.getActions()).thenReturn(new String[]{"Trade", "Talk-to"});
            ui.when(() -> Rs2UiHelper.getActorClickbox(npc)).thenReturn(new Rectangle(100,100,25,30));
        }
        public void close() { Microbot.cantReachTarget=priorCantReach;Microbot.isCantReachTargetDetectionEnabled=priorDetection;ui.close();players.close();nativeWalker.close();runtime.close();micro.close(); }
    }
}
