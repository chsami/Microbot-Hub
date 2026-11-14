package net.runelite.client.plugins.microbot.droslayer;

import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerMasterInteractionTest {
    @Test void movingApproachCancelsWalkerAndSendsNoNpcInput() {
        try(var runtime=mockStatic(DroSlayerRuntime.class);var player=mockStatic(Rs2Player.class);
            var micro=mockStatic(Microbot.class)) {
            player.when(Rs2Player::isMoving).thenReturn(true);
            assertFalse(DroSlayerMasterInteraction.request(DroSlayerMaster.KRYSTILIA,"Assignment"));
            runtime.verify(DroSlayerRuntime::cancelWalk);
            runtime.verify(()->DroSlayerRuntime.performAction(any(),anyBoolean(),any(BooleanSupplier.class)),never());
            micro.verifyNoInteractions();
        }
    }
    @Test void profilePacingFinishesBeforeTheMasterIsResolvedAndClicked() {
        var cache=mock(Rs2NpcCache.class,RETURNS_DEEP_STUBS);
        var master=mock(Rs2NpcModel.class);when(master.getName()).thenReturn("Krystilia");
        when(master.click("Assignment")).thenReturn(true);
        when(cache.query().withName("Krystilia").nearestOnClientThread()).thenReturn(master);
        clearInvocations(cache);
        try(var runtime=mockStatic(DroSlayerRuntime.class);var player=mockStatic(Rs2Player.class);
            var inventory=mockStatic(Rs2Inventory.class);var micro=mockStatic(Microbot.class)) {
            micro.when(Microbot::getRs2NpcCache).thenReturn(cache);
            runtime.when(()->DroSlayerRuntime.performAction(eq(BaseProfileDro.ActionPhase.SETUP),eq(false),any(BooleanSupplier.class)))
                .thenAnswer(call->{runtime.verify(DroSlayerRuntime::cancelWalk);verify(cache,never()).query();return ((BooleanSupplier)call.getArgument(2)).getAsBoolean();});
            assertTrue(DroSlayerMasterInteraction.request(DroSlayerMaster.KRYSTILIA,"Assignment"));
            verify(master).click("Assignment");verify(master,never()).click("Attack");
        }
    }
    @Test void aGuardOrAnItemSelectionCannotBecomeAnAssignmentClick() {
        var cache=mock(Rs2NpcCache.class,RETURNS_DEEP_STUBS);var guard=mock(Rs2NpcModel.class);
        when(guard.getName()).thenReturn("Guard");
        when(cache.query().withName("Krystilia").nearestOnClientThread()).thenReturn(guard);
        try(var runtime=mockStatic(DroSlayerRuntime.class);var player=mockStatic(Rs2Player.class);
            var inventory=mockStatic(Rs2Inventory.class);var micro=mockStatic(Microbot.class)) {
            micro.when(Microbot::getRs2NpcCache).thenReturn(cache);
            runtime.when(()->DroSlayerRuntime.performAction(any(),eq(false),any(BooleanSupplier.class)))
                .thenAnswer(call->((BooleanSupplier)call.getArgument(2)).getAsBoolean());
            assertFalse(DroSlayerMasterInteraction.request(DroSlayerMaster.KRYSTILIA,"Assignment"));
            verify(guard,never()).click(anyString());
            inventory.when(Rs2Inventory::isItemSelected).thenReturn(true);clearInvocations(cache);
            assertFalse(DroSlayerMasterInteraction.request(DroSlayerMaster.KRYSTILIA,"Assignment"));
            inventory.verify(Rs2Inventory::deselect);verify(cache,never()).query();
        }
    }
    @Test void movementBeginningDuringPacingDefersTheNpcClick() {
        try(var runtime=mockStatic(DroSlayerRuntime.class);var player=mockStatic(Rs2Player.class);
            var micro=mockStatic(Microbot.class)) {
            player.when(Rs2Player::isMoving).thenReturn(false,true);
            runtime.when(()->DroSlayerRuntime.performAction(any(),eq(false),any(BooleanSupplier.class)))
                .thenAnswer(call->((BooleanSupplier)call.getArgument(2)).getAsBoolean());
            assertFalse(DroSlayerMasterInteraction.request(DroSlayerMaster.KRYSTILIA,"Assignment"));
            micro.verifyNoInteractions();
        }
    }
}
