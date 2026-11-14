package net.runelite.client.plugins.microbot.droslayer;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerCannonRepairTest {
    @Test void repairWaitsForRetaliationAndMovementThenRequiresWorkingObjectReceipt() throws Exception {
        var script = new DroSlayerScript();
        var config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.DURADEL);
        set(script, "config", config);
        set(script, "cannonPlaced", true);
        set(script, "isUsingCannon", true);
        set(script, "cannonDiagnosticAt", Long.MAX_VALUE);
        var client = mock(Client.class);
        var thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        try (var dro = mockStatic(Microbot.class); var cannon = mockStatic(DroCannon.class);
             var player = mockStatic(Rs2Player.class); var combat = mockStatic(Rs2Combat.class);
             var inventory = mockStatic(Rs2Inventory.class); var runtime = mockStatic(DroSlayerRuntime.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            dro.when(() -> Microbot.getVarbitPlayerValue(VarPlayerID.DROPCANNON)).thenReturn(4);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            cannon.when(DroCannon::isPresent).thenReturn(true);
            cannon.when(DroCannon::isBroken).thenReturn(true);
            // Failed/held retaliation disable cannot authorize a repair click.
            assertEquals(true, call(script, "handleCannonMaintenance"));
            cannon.verify(DroCannon::repair, never());
            when(client.getVarpValue(VarPlayerID.OPTION_NODEF)).thenReturn(1);
            player.when(Rs2Player::isMoving).thenReturn(true);
            assertEquals(true, call(script, "handleCannonMaintenance"));
            cannon.verify(DroCannon::repair, never());
            player.when(Rs2Player::isMoving).thenReturn(false);
            cannon.when(DroCannon::repair).thenReturn(true);
            assertEquals(true, call(script, "handleCannonMaintenance"));
            assertEquals(true, get(script, "cannonRepairPending"));
            cannon.verify(DroCannon::repair, times(1));
            cannon.verify(() -> DroCannon.refill(anyInt()), never());
            // General fighting requests must leave retaliation off on subsequent ticks.
            assertEquals(true, call(script, "setAutoRetaliateHumanized", true));
            combat.verify(() -> Rs2Combat.setAutoRetaliate(true), never());
            cannon.when(DroCannon::isBroken).thenReturn(false);
            cannon.when(DroCannon::isFiring).thenReturn(true);
            assertEquals(false, call(script, "handleCannonMaintenance"));
            assertEquals(false, get(script, "cannonRepairPending"));
            assertEquals(0L, get(script, "cannonFireAttemptStarted"));
            combat.when(() -> Rs2Combat.setAutoRetaliate(true)).thenReturn(true);
            assertEquals(true, call(script, "setAutoRetaliateHumanized", true));
            combat.verify(() -> Rs2Combat.setAutoRetaliate(true), times(1));
        }
    }

    @Test void failedRepairYieldsWithBackoffAndClearsItsRetaliationGuard() throws Exception {
        var script = new DroSlayerScript();
        set(script, "cannonPlaced", true);
        set(script, "isUsingCannon", true);
        set(script, "cannonRepairPending", true);
        set(script, "cannonDiagnosticAt", Long.MAX_VALUE);
        set(script, "cannonFireAttemptStarted", System.currentTimeMillis() - 31_000L);
        try (var cannon = mockStatic(DroCannon.class)) {
            cannon.when(DroCannon::isPresent).thenReturn(true);
            cannon.when(DroCannon::isBroken).thenReturn(true);
            assertEquals(false, call(script, "handleCannonMaintenance"));
            assertEquals(false, get(script, "cannonRepairPending"));
            assertTrue((long) get(script, "cannonMaintenanceRetryAt") > System.currentTimeMillis());
            assertEquals(false, call(script, "handleCannonMaintenance"));
            cannon.verify(DroCannon::repair, never());
        }
    }

    @Test void missingObjectAndLifecycleResetsReleasePendingRepair() throws Exception {
        for (String method : new String[]{"handleCannonMaintenance", "clearCannonDeployment", "resetCannonState"}) {
            var script = new DroSlayerScript();
            set(script, "cannonPlaced", true);
            set(script, "isUsingCannon", true);
            set(script, "cannonRepairPending", true);
            set(script, "cannonDiagnosticAt", Long.MAX_VALUE);
            try (var cannon = mockStatic(DroCannon.class)) {
                call(script, method);
                assertEquals(false, get(script, "cannonRepairPending"), method);
                cannon.verify(DroCannon::repair, never());
            }
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(target);
    }
    private static Object call(Object target, String name, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, args.length == 0 ? new Class<?>[0] : new Class<?>[]{boolean.class});
        method.setAccessible(true); return method.invoke(target, args);
    }
}
