package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerBankSupplyWaitTest {
    @Test public void lastNineKillsSkipPrayerAndFoodRebankButTenKillsDoNot() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.foodThreshold()).thenReturn(1);
        set(script, "config", config);
        Rs2ItemModel ring = mock(Rs2ItemModel.class);
        when(ring.getName()).thenReturn("Ring of dueling(8)");
        when(ring.getQuantity()).thenReturn(1);
        try (var inventory = mockStatic(Rs2Inventory.class);
             var slayer = mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class);
             var reader = mockStatic(DroSlayerWildernessTaskReader.class)) {
            inventory.when(() -> Rs2Inventory.get(any(java.util.function.Predicate.class))).thenReturn(ring);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> java.util.stream.Stream.empty());
            inventory.when(() -> Rs2Inventory.getList(any(java.util.function.Predicate.class)))
                    .thenReturn(java.util.List.of(mock(Rs2ItemModel.class))); // Fish remain; prayer doses are empty.
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Rogues");
            for (int kills : new int[] {1, 4, 9}) {
                slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(kills);
                assertEquals(false, call(script, "needsBanking"));
            }
            slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(10);
            assertEquals(true, call(script, "needsBanking"));
            when(config.foodThreshold()).thenReturn(0); // Food is sufficient; prayer alone still triggers at ten.
            assertEquals(true, call(script, "needsBanking"));
            slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(4);
            assertEquals(false, call(script, "needsBanking"));
            inventory.when(() -> Rs2Inventory.getList(any(java.util.function.Predicate.class))).thenReturn(java.util.List.of());
            assertEquals(true, call(script, "needsBanking")); // Both supplies exhausted.
            Rs2ItemModel restore = mock(Rs2ItemModel.class);
            when(restore.getName()).thenReturn("Blighted super restore(4)");
            when(restore.getQuantity()).thenReturn(1);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> java.util.stream.Stream.of(restore));
            assertEquals(false, call(script, "needsBanking")); // No fish, but restore doses remain.
        }
    }

    @Test public void firingTimeoutRemainsRetryableAndManualRepairResumesWithoutRestart() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script, "config", config);
        set(script, "isUsingCannon", true);
        set(script, "cannonPlaced", true);
        set(script, "cannonFireAttemptStarted", System.currentTimeMillis() - 31_000L);
        try (var cannon = mockStatic(DroCannon.class); var bot = mockStatic(Microbot.class);
             var inventory = mockStatic(Rs2Inventory.class); var runtime = mockStatic(DroSlayerRuntime.class)) {
            net.runelite.client.callback.ClientThread clientThread = mock(net.runelite.client.callback.ClientThread.class);
            when(clientThread.runOnClientThreadOptional(any())).thenReturn(java.util.Optional.of(false));
            bot.when(Microbot::getClientThread).thenReturn(clientThread);
            bot.when(() -> Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.CANNON_AMMO)).thenReturn(25);
            cannon.when(DroCannon::isPresent).thenReturn(true);
            assertEquals(false, call(script, "handleCannonMaintenance"));
            assertEquals(true, get(script, "isUsingCannon"));
            assertEquals(0L, get(script, "cannonFireAttemptStarted"));
            assertTrue((Long) get(script, "cannonMaintenanceRetryAt") > System.currentTimeMillis());
            set(script, "cannonMaintenanceRetryAt", 0L);
            cannon.when(DroCannon::isFiring).thenReturn(true);
            assertEquals(false, call(script, "handleCannonMaintenance"));
            assertEquals(true, get(script, "cannonFired"));
            cannon.when(DroCannon::isBroken).thenReturn(true);
            assertEquals(true, call(script, "handleCannonMaintenance"));
            assertEquals(true, get(script, "isUsingCannon"));
            runtime.verify(() -> DroSlayerRuntime.performAction(any(), eq(false), any(java.util.function.BooleanSupplier.class)));
            set(script, "cannonFireAttemptStarted", System.currentTimeMillis() - 31_000L);
            assertEquals(false, call(script, "handleCannonMaintenance")); // Failed repair is bounded too.
            assertEquals(false, call(script, "handleCannonMaintenance")); // Combat window stays open.
            assertEquals(true, get(script, "isUsingCannon"));
            set(script, "cannonMaintenanceRetryAt", 0L);
            cannon.when(DroCannon::isBroken).thenReturn(false);
            assertEquals(false, call(script, "handleCannonMaintenance")); // Manual repair observed firing.
            assertEquals(true, get(script, "cannonFired"));
            cannon.when(DroCannon::isPresent).thenReturn(false);
            assertEquals(false, call(script, "handleCannonMaintenance"));
            assertEquals(false, call(script, "handleCannonMaintenance"));
            assertEquals(true, get(script, "isUsingCannon"));
            runtime.verify(() -> DroSlayerRuntime.performAction(any(), eq(false), any(java.util.function.BooleanSupplier.class)), times(1));
        }
    }

    @Test public void deployedCannonSurvivesOrdinarySupplyBankAndRestocksAmmoWithoutParts() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessPreferCannon()).thenReturn(true);
        set(script, "config", config);
        set(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Black demons"));
        set(script, "taskDestination", new net.runelite.api.coords.WorldPoint(3363,10120,0));
        set(script, "taskLocationName", "Wilderness Slayer Cave - Black demon");
        set(script, "lastCannonPlacementTile", new net.runelite.api.coords.WorldPoint(3363,10120,0));
        set(script, "cannonDeploymentWorld", 521);
        set(script, "cannonPlaced", true);
        set(script, "isUsingCannon", true);
        java.util.concurrent.atomic.AtomicInteger ammo = new java.util.concurrent.atomic.AtomicInteger(19);
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Black demons");
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count("Cannonball", false)).thenReturn(1000);
            inventory.when(() -> Rs2Inventory.itemQuantity("Cannonball", true)).thenAnswer(i -> ammo.get());
            inventory.when(() -> Rs2Inventory.itemQuantity("Cannonball", false)).thenAnswer(i -> ammo.get());
            bank.when(() -> Rs2Bank.withdrawX("Cannonball",331,false)).thenAnswer(i -> {ammo.set(350); return true;});
            runtime.when(() -> DroSlayerRuntime.performAction(any(),eq(false),any(java.util.function.BooleanSupplier.class)))
                    .thenAnswer(i -> ((java.util.function.BooleanSupplier)i.getArgument(2)).getAsBoolean());
            assertEquals(true,call(script,"hasDeployedWildernessCannon"));
            ammo.set(0);
            bank.when(() -> Rs2Bank.count("Cannonball", false)).thenReturn(0);
            assertEquals(false,call(script,"ensureCannonPolicyAtBank"));
            assertEquals(true,get(script,"cannonPlaced"));
            assertEquals(true,get(script,"isUsingCannon"));
            assertTrue(Microbot.status.contains("waiting for cannonballs"));
            ammo.set(19);
            bank.when(() -> Rs2Bank.count("Cannonball", false)).thenReturn(1000);
            assertEquals(true,call(script,"ensureCannonPolicyAtBank"));
            assertEquals(350,ammo.get());
            bank.verify(() -> Rs2Bank.withdrawX("Cannonball",331,false));
            for (String part : new String[]{"Cannon base","Cannon stand","Cannon barrels","Cannon furnace"})
                bank.verify(() -> Rs2Bank.withdrawX(eq(part),anyInt(),eq(false)),never());
            assertEquals(true,get(script,"cannonPlaced"));
            assertEquals(true,get(script,"isUsingCannon"));
            assertEquals(521,get(script,"cannonDeploymentWorld"));
            assertEquals(new net.runelite.api.coords.WorldPoint(3363,10120,0),get(script,"lastCannonPlacementTile"));
        }
    }

    @Test public void escapeAmmoWaitPreservesGearAndDeployedCannonThenReturnsWhenStockObserved() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        set(script, "config", mock(DroSlayerConfig.class));
        set(script, "escapeAmmoRestock", true);
        set(script, "cannonPlaced", true);
        set(script, "baseBankLoadComplete", true);
        DroSlayerCannonPolicy policy = (DroSlayerCannonPolicy) get(script, "cannonPolicy");
        policy.disable();
        DroSlayerPlugin.setState(DroSlayerState.BANKING);
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            call(script, "handleBankingState");
            assertEquals(DroSlayerState.BANKING, DroSlayerPlugin.getState());
            assertTrue(Microbot.status.contains("No cannonballs"));
            assertEquals(true, get(script, "cannonPlaced"));
            assertEquals(true, get(script, "baseBankLoadComplete"));
            set(script, "bankSupplyRetryAt", 0L);
            bank.when(() -> Rs2Bank.count("Cannonball", false)).thenReturn(1000);
            call(script, "handleBankingState");
            runtime.verify(() -> DroSlayerRuntime.performAction(any(), eq(false), any(java.util.function.BooleanSupplier.class)));
            assertEquals(true, get(script, "escapeAmmoRestock"));
            inventory.when(() -> Rs2Inventory.itemQuantity("Cannonball", true)).thenReturn(350);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false), any(java.util.function.BooleanSupplier.class)))
                    .thenAnswer(invocation -> { bank.when(Rs2Bank::isOpen).thenReturn(false); return true; });
            call(script, "handleBankingState");
            call(script, "handleBankingState"); // Observe bank closure on the following step.
            assertEquals(DroSlayerState.TRAVELING, DroSlayerPlugin.getState());
            assertEquals(false, get(script, "escapeAmmoRestock"));
            assertEquals(true, get(script, "isUsingCannon"));
            assertFalse(policy.isDisabled());
            assertEquals(true, get(script, "cannonPlaced"));
            assertEquals(true, get(script, "baseBankLoadComplete"));
        }
    }

    @Test public void missingRevenantCoinsRequestsLogoutAndCancelsOwner() throws Exception {
        DroSlayerScript script = spy(new DroSlayerScript());
        doNothing().when(script).shutdown();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script, "config", config);
        set(script, "wildernessAssignment", DroSlayerWildernessTasks.forAssignedTask("Revenants"));
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class); var dro = mockStatic(Microbot.class);
             var reader = mockStatic(DroSlayerWildernessTaskReader.class);
             var player = mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Player.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Revenants");
            Rs2ItemModel restore = mock(Rs2ItemModel.class);
            when(restore.getName()).thenReturn("Blighted super restore(4)");
            when(restore.getQuantity()).thenReturn(1);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.of(restore));
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(true), any(Runnable.class)))
                    .thenAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; });
            assertEquals(false, call(script, "ensureWildernessSuppliesAtBank"));
            dro.verify(() -> Microbot.supplyMessage("Coins (100,000 required for Revenant Cave entry)"));
            verify(script).shutdown();
            runtime.verify(DroSlayerRuntime::shutdown);
        }
    }

    @Test public void missingFoodStopsButInventorySpaceStillWaits() throws Exception {
        for (int freeSlots : new int[] {0, 28}) {
            DroSlayerScript script = spy(new DroSlayerScript());
            doNothing().when(script).shutdown();
            DroSlayerConfig config = mock(DroSlayerConfig.class);
            when(config.foodThreshold()).thenReturn(1);
            set(script, "config", config);
            DroSlayerPlugin.setState(DroSlayerState.BANKING);
            DroSlayerHold.clear();
            try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
                 var runtime = mockStatic(DroSlayerRuntime.class); var dro = mockStatic(Microbot.class)) {
                inventory.when(Rs2Inventory::emptySlotCount).thenReturn(freeSlots);
                runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(true), any(Runnable.class)))
                        .thenAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; });
                assertEquals(false, call(script, "fillRemainingSpaceWithFood"));
                if (freeSlots == 0) {
                    assertTrue(Microbot.status.contains("Not enough room"));
                    // Waiting never stops or logs out. Its only use of the client is the one chat line saying what is missing.
                    dro.verify(Microbot::getClientThread);
                    dro.verifyNoMoreInteractions();
                    assertTrue(DroSlayerHold.current(DroSlayerState.BANKING, System.currentTimeMillis()).contains("Not enough room"));
                    verify(script, never()).shutdown();
                } else {
                    dro.verify(() -> Microbot.supplyMessage("usable food"));
                    verify(script).shutdown();
                    runtime.verify(DroSlayerRuntime::shutdown);
                }
            }
        }
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
