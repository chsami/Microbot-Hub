package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerCannonWorldReturnTest {
    @Test public void startupRetrievalClearsDisabledTripAndPreparesSuppliesBeforeResuming() throws Exception {
        var script = new DroSlayerScript();
        set(script, "cannonStartupPickup", true);
        set(script, "cannonAssemblyRecovery", true);
        var policy = (DroSlayerCannonPolicy) get(script, "cannonPolicy");
        policy.selectTask("Black demons");
        policy.disable();
        var finish = DroSlayerScript.class.getDeclaredMethod("finishCannonAssemblyRecovery");
        finish.setAccessible(true);
        try (var inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(true);
            finish.invoke(script);
            assertFalse(policy.isDisabled());
            assertEquals(false, get(script, "cannonAssemblyRecovery"));
            assertEquals(true, get(script, "cannonRecoveredForBank"));
            assertEquals(DroSlayerState.DETECTING_TASK, DroSlayerPlugin.getState());
        }
    }

    @Test public void incompleteStartupRetrievalCannotEnableCannon() throws Exception {
        var script = new DroSlayerScript();
        var config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script, "config", config);
        set(script, "cannonStartupPickup", true);
        set(script, "cannonAssemblyRecovery", true);
        var finish = DroSlayerScript.class.getDeclaredMethod("finishCannonAssemblyRecovery");
        finish.setAccessible(true);
        try (var inventory = mockStatic(Rs2Inventory.class); var runtime = mockStatic(DroSlayerRuntime.class);
             var reader = mockStatic(DroSlayerWildernessTaskReader.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Black demons");
            finish.invoke(script);
            assertTrue(((DroSlayerCannonPolicy) get(script, "cannonPolicy")).isDisabled());
            assertEquals(false, get(script, "cannonRecoveredForBank"));
        }
    }

    @Test public void wildernessFeroxUsesCarriedRingAfterEdgevilleLanding() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script, "config", config);
        var ring = mock(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
        when(ring.getName()).thenReturn("Ring of dueling(3)");
        try (var player = mockStatic(Rs2Player.class); var inventory = mockStatic(Rs2Inventory.class);
             var equipment = mockStatic(net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.class);
             var pvp = mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Pvp.class);
             var bank = mockStatic(Rs2Bank.class); var runtime = mockStatic(DroSlayerRuntime.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new net.runelite.api.coords.WorldPoint(3087,3496,0));
            inventory.when(Rs2Inventory::items).thenAnswer(call -> java.util.stream.Stream.empty());
            equipment.when(() -> net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.RING)).thenReturn(ring);
            equipment.when(() -> net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing("Ring of dueling", false)).thenReturn(true);
            Method recovery = DroSlayerScript.class.getDeclaredMethod("handleFeroxRecovery");
            recovery.setAccessible(true);
            recovery.invoke(script);
            runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            runtime.verify(() -> DroSlayerRuntime.performAction(any(), eq(true), any(BooleanSupplier.class)));

            var cave = new net.runelite.api.coords.WorldPoint(3339, 10056, 0);
            player.when(Rs2Player::getWorldLocation).thenReturn(cave);
            pvp.when(net.runelite.client.plugins.microbot.util.player.Rs2Pvp::isInWilderness).thenReturn(true);
            pvp.when(() -> net.runelite.client.plugins.microbot.util.player.Rs2Pvp.getWildernessLevelFrom(cave)).thenReturn(17);
            set(script, "lastEmergencyEscapeAttemptAt", 0L);
            recovery.invoke(script);
            runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            runtime.verify(() -> DroSlayerRuntime.performAction(any(), eq(true), any(BooleanSupplier.class)), times(2));
            Method allowed = DroSlayerScript.class.getDeclaredMethod("canUseFeroxRing");
            allowed.setAccessible(true);
            player.when(Rs2Player::isTeleBlocked).thenReturn(true);
            assertFalse((boolean) allowed.invoke(script));
            player.when(Rs2Player::isTeleBlocked).thenReturn(false);
            pvp.when(() -> net.runelite.client.plugins.microbot.util.player.Rs2Pvp.getWildernessLevelFrom(cave)).thenReturn(21);
            assertFalse((boolean) allowed.invoke(script));
        }
    }

    @Test public void bankedCannonCancelsRestoredDeploymentBeforeTravel() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        set(script, "cannonStartupCheck", true);
        set(script, "cannonAssemblyRecovery", true);
        set(script, "cannonPlaced", true);
        set(script, "cannonDeploymentWorld", 624);
        Method check = DroSlayerScript.class.getDeclaredMethod("checkStartupCannonSupplies");
        check.setAccessible(true);
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem(anyString())).thenReturn(true);
            assertEquals(false, check.invoke(script));
            assertEquals(false, get(script, "cannonAssemblyRecovery"));
            assertEquals(0, get(script, "cannonDeploymentWorld"));
            runtime.verify(DroSlayerRuntime::cancelWalk);
            runtime.verifyNoMoreInteractions();
        }
    }

    @Test public void incompleteCannonKeepsSavedRecoveryAfterBankCheck() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        set(script, "cannonStartupCheck", true);
        set(script, "cannonAssemblyRecovery", true);
        set(script, "cannonDeploymentWorld", 624);
        Method check = DroSlayerScript.class.getDeclaredMethod("checkStartupCannonSupplies");
        check.setAccessible(true);
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem("Cannon base")).thenReturn(true);
            assertEquals(false, check.invoke(script));
            assertEquals(true, get(script, "cannonAssemblyRecovery"));
            assertEquals(624, get(script, "cannonDeploymentWorld"));
            runtime.verifyNoInteractions();
        }
    }

    @Test public void savedDeploymentStartsRetrievalAndIsNotReloadedEveryTick() throws Exception {
        var manager = mock(net.runelite.client.config.ConfigManager.class);
        when(manager.getRSProfileKey()).thenReturn("accountA");
        when(manager.getConfiguration("droslayer", "accountA", "cannonRecoveryV1"))
                .thenReturn("624,3287,3944,0;NONE,0,0,0,-");
        DroSlayerScript script = new DroSlayerScript();
        set(script, "cannonConfigManager", manager);
        Method restore = DroSlayerScript.class.getDeclaredMethod("restoreCannonRecord");
        restore.setAccessible(true);
        restore.invoke(script);
        assertEquals(624, get(script, "cannonDeploymentWorld"));
        assertEquals(true, get(script, "cannonAssemblyRecovery"));
        assertEquals("3287, 3944, 0", script.getCannonTileDisplay());
        set(script, "cannonAssemblyRecovery", false);
        restore.invoke(script);
        assertEquals(false, get(script, "cannonAssemblyRecovery"));
        verify(manager, times(1)).getConfiguration("droslayer", "accountA", "cannonRecoveryV1");
    }

    @Test public void restartRestoresMissingCannonDeadlineAndSeparatesAccounts() throws Exception {
        var manager = mock(net.runelite.client.config.ConfigManager.class);
        when(manager.getRSProfileKey()).thenReturn("accountA");
        DroSlayerScript first = new DroSlayerScript();
        set(first, "cannonConfigManager", manager);
        Method restore = DroSlayerScript.class.getDeclaredMethod("restoreCannonRecord");
        Method save = DroSlayerScript.class.getDeclaredMethod("saveCannonRecord");
        restore.setAccessible(true); save.setAccessible(true);
        restore.invoke(first);
        DroSlayerCannonReclaim pending = (DroSlayerCannonReclaim) get(first, "cannonReclaim");
        pending.remember(624, new net.runelite.api.coords.WorldPoint(3287,3944,0));
        pending.missing(1000L);
        save.invoke(first);
        var saved = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(manager).setConfiguration(eq("droslayer"), eq("accountA"), eq("cannonRecoveryV1"), saved.capture());
        when(manager.getConfiguration("droslayer", "accountA", "cannonRecoveryV1")).thenReturn(saved.getValue());
        DroSlayerScript restarted = new DroSlayerScript();
        set(restarted, "cannonConfigManager", manager);
        restore.invoke(restarted);
        DroSlayerCannonReclaim restored = (DroSlayerCannonReclaim) get(restarted, "cannonReclaim");
        assertEquals(624, restored.lastWorld());
        assertFalse(restored.startIfDue(1000L + DroSlayerCannonReclaim.DECAY_WAIT_MS - 1, true));
        assertTrue(restored.startIfDue(1000L + DroSlayerCannonReclaim.DECAY_WAIT_MS, true));
        when(manager.getRSProfileKey()).thenReturn("accountB");
        restore.invoke(restarted);
        assertEquals(0, restored.lastWorld());
        assertEquals(DroSlayerCannonReclaim.Phase.NONE, restored.phase());
    }

    @Test public void interruptedDwarfTripRepeatsBankCheckWithoutRestartingDeadline() {
        DroSlayerCannonReclaim original = new DroSlayerCannonReclaim();
        original.missing(1000);
        long due = 1000 + DroSlayerCannonReclaim.DECAY_WAIT_MS;
        assertTrue(original.startIfDue(due, true));
        original.bankChecked();
        DroSlayerCannonReclaim restored = new DroSlayerCannonReclaim();
        restored.restore(original.snapshot());
        assertEquals(DroSlayerCannonReclaim.Phase.WAITING, restored.phase());
        assertFalse(restored.startIfDue(due, false));
        assertTrue(restored.startIfDue(due, true));
    }

    @Test public void reclaimBanksBlightedFoodAtFeroxThenAdvancesToDwarf() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerCannonReclaim reclaim = (DroSlayerCannonReclaim) get(script, "cannonReclaim");
        reclaim.missing(System.currentTimeMillis() - DroSlayerCannonReclaim.DECAY_WAIT_MS - 1);
        var food = mock(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
        when(food.getId()).thenReturn(24592);
        when(food.getName()).thenReturn("Blighted manta ray");
        when(food.getQuantity()).thenReturn(1);
        when(food.isFood()).thenReturn(true);
        Method handler = DroSlayerScript.class.getDeclaredMethod("handleCannonReclaim");
        handler.setAccessible(true);
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(4);
            inventory.when(() -> Rs2Inventory.getList(any(java.util.function.Predicate.class)))
                    .thenAnswer(call -> ((java.util.function.Predicate) call.getArgument(0)).test(food)
                            ? java.util.List.of(food) : java.util.List.of());
            runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            assertEquals(true, handler.invoke(script));
            bank.verify(() -> Rs2Bank.depositOne(24592));
            assertEquals(DroSlayerCannonReclaim.Phase.BANK, reclaim.phase());
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(5);
            assertEquals(true, handler.invoke(script));
            assertEquals(DroSlayerCannonReclaim.Phase.DWARF, reclaim.phase());
        }
    }

    @Test public void pickupSuppressesRefillBeforeAnyCannonInteraction() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        set(script, "cannonPickupStarted", 1L);
        set(script, "cannonPlaced", true);
        set(script, "isUsingCannon", true);
        Method maintenance = DroSlayerScript.class.getDeclaredMethod("handleCannonMaintenance");
        maintenance.setAccessible(true);
        assertEquals(false, maintenance.invoke(script));
    }

    @Test public void failedRetrievalCannotBecomeUsableCannonForNextTask() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script,"config",config);
        set(script,"cannonPlaced",true);
        set(script,"cannonAssemblyStage",4);
        set(script,"cannonDeploymentWorld",508);
        set(script,"lastCannonPlacementTile",new net.runelite.api.coords.WorldPoint(3363,10120,0));
        set(script,"isUsingCannon",true);
        try (var reader = mockStatic(DroSlayerWildernessTaskReader.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Black demons");
            Method failure = DroSlayerScript.class.getDeclaredMethod("abandonCannonPickup",String.class);
            failure.setAccessible(true); failure.invoke(script,"Pickup not confirmed");
            assertEquals(false,get(script,"cannonPlaced"));
            assertEquals(0,get(script,"cannonDeploymentWorld"));
            assertNull(get(script,"lastCannonPlacementTile"));
            assertEquals("508 (unconfirmed)", script.getCannonWorldDisplay());
            assertEquals("3363, 10120, 0", script.getCannonTileDisplay());
            assertEquals(false,get(script,"isUsingCannon"));
            DroSlayerCannonPolicy policy=(DroSlayerCannonPolicy)get(script,"cannonPolicy");
            assertTrue(policy.isDisabled());
            assertEquals(DroSlayerCannonReclaim.Phase.WAITING,
                    ((DroSlayerCannonReclaim)get(script,"cannonReclaim")).phase());
            policy.selectTask("Greater demons");
            Method deployed=DroSlayerScript.class.getDeclaredMethod("hasDeployedWildernessCannon");
            deployed.setAccessible(true);
            assertEquals(false,deployed.invoke(script));
        }
    }

    @Test public void mismatchWaitsForCombatThenHopsOnceAndKeepsDeploymentUntilArrival() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.maxRecoveryAttempts()).thenReturn(3);
        set(script, "config", config);
        set(script, "cannonPlaced", true);
        set(script, "cannonDeploymentWorld", 441);
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        when(client.getWorld()).thenReturn(337);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        WorldService service = mock(WorldService.class);
        WorldResult worlds = mock(WorldResult.class);
        World target = mock(World.class);
        when(service.getWorlds()).thenReturn(worlds);
        when(target.getId()).thenReturn(441);
        when(target.getPlayers()).thenReturn(100);
        when(target.getTypes()).thenReturn(EnumSet.of(net.runelite.http.api.worlds.WorldType.MEMBERS));
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        try (var bot = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class);
             var pvp = mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Pvp.class);
             var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bot.when(Microbot::getClient).thenReturn(client);
            bot.when(Microbot::getClientThread).thenReturn(thread);
            bot.when(Microbot::getWorldService).thenReturn(service);
            bot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            bot.when(() -> Microbot.hopToWorld(441)).thenReturn(true);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            pvp.when(net.runelite.client.plugins.microbot.util.player.Rs2Pvp::isInWilderness).thenReturn(true);
            DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
            runtime.when(DroSlayerRuntime::isWildernessPlayerAttackActive).thenReturn(true);
            assertTrue(hold(script));
            assertEquals(DroSlayerState.TRAVELING, DroSlayerPlugin.getState());
            bot.verify(() -> Microbot.hopToWorld(441), never());
            runtime.verify(DroSlayerRuntime::cancelWalk, never());
            runtime.when(DroSlayerRuntime::isWildernessPlayerAttackActive).thenReturn(false);
            runtime.when(DroSlayerRuntime::walkerStatus).thenReturn("PAUSED_SAFETY_LOGOUT");
            assertTrue(hold(script));
            bot.verify(() -> Microbot.hopToWorld(441), never());
            runtime.verify(DroSlayerRuntime::cancelWalk, never());
            runtime.when(DroSlayerRuntime::walkerStatus).thenReturn("IDLE");
            player.when(Rs2Player::isInCombat).thenReturn(true);
            assertTrue(hold(script));
            bot.verify(() -> Microbot.hopToWorld(441), never());
            player.when(Rs2Player::isInCombat).thenReturn(false);
            assertTrue(hold(script)); // Original world unavailable; retain it.
            assertTrue(Microbot.status.contains("become available"));
            assertEquals(441, get(script, "cannonDeploymentWorld"));
            set(script, "cannonWorldReturnAttemptAt", 0L);
            when(worlds.findWorld(441)).thenReturn(target);
            // A lingering banker interaction is not combat and must not prevent hopping.
            when(client.getLocalPlayer().getInteracting()).thenReturn(mock(net.runelite.api.NPC.class));
            assertTrue(hold(script));
            assertTrue(hold(script));
            bot.verify(() -> Microbot.hopToWorld(441), times(1));
            assertEquals(DroSlayerState.TRAVELING, DroSlayerPlugin.getState()); // No Ferox detour, still in Wilderness.
            assertEquals(true, get(script, "cannonPlaced"));
            // A world ID can change before login finishes; it is not an arrival yet.
            when(client.getWorld()).thenReturn(441);
            when(client.getGameState()).thenReturn(net.runelite.api.GameState.HOPPING);
            assertTrue(hold(script));
            assertEquals(2, get(script, "cannonWorldReturnAttempts"));
            when(client.getWorld()).thenReturn(337);
            when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
            set(script, "cannonWorldReturnAttempts", 3);
            set(script, "cannonWorldReturnAttemptAt", 0L);
            assertFalse(hold(script));
            assertEquals(true, get(script, "cannonWorldReturnSuppressed"));
            assertEquals(441, get(script, "cannonDeploymentWorld"));
            assertEquals(true, get(script, "cannonPlaced"));
            when(client.getWorld()).thenReturn(441);
            assertFalse(hold(script));
            assertEquals(441, get(script, "cannonDeploymentWorld"));
            inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(true);
            when(client.getWorld()).thenReturn(337);
            assertFalse(hold(script)); // Collected cannon does not force another hop.
            assertEquals(0, get(script, "cannonDeploymentWorld"));
            assertEquals(0, get(script, "cannonAssemblyStage"));
            assertEquals(false, get(script, "cannonPlaced"));
            set(script, "cannonDeploymentWorld", 441); // Retained record after optional recovery fallback.
            assertFalse(hold(script));
            assertEquals(0, get(script, "cannonDeploymentWorld"));
        }
    }
    @Test public void livingLogoutReturnPreservesGearAndDoesNotStartDeathRecovery() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        set(script, "abandonedWildernessCannon", new net.runelite.api.coords.WorldPoint(3307, 3669, 0));
        set(script, "cannonDeploymentWorld", 304);
        set(script, "cannonPlaced", true);
        set(script, "wildernessHopRequired", true);
        set(script, "baseBankLoadComplete", true);
        try (var runtime = mockStatic(DroSlayerRuntime.class); var bank = mockStatic(Rs2Bank.class)) {
            Method method = DroSlayerScript.class.getDeclaredMethod("handleWildernessResumeHop");
            method.setAccessible(true);
            assertEquals(true, method.invoke(script));
            assertEquals(DroSlayerState.TRAVELING, DroSlayerPlugin.getState());
            assertEquals(true, get(script, "baseBankLoadComplete"));
            assertEquals(true, get(script, "cannonPlaced"));
            assertEquals(304, get(script, "cannonDeploymentWorld"));
            assertFalse(((DroSlayerWildernessRecovery) get(script, "wildernessRecovery")).isActive());
            bank.verifyNoInteractions();
            // Manual pickup can clear the deployment before the living-login handoff.
            set(script, "livingLogoutReturn", true);
            set(script, "abandonedWildernessCannon", null);
            assertEquals(true, method.invoke(script));
            assertEquals(DroSlayerState.TRAVELING, DroSlayerPlugin.getState());
            assertEquals(true, get(script, "baseBankLoadComplete"));
        }
    }

    @Test public void deathResetDoesNotLeaveAnActiveAssemblyForLaterWorldHops() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        set(script, "cannonAssemblyStage", 4);
        set(script, "cannonPlaced", true);
        set(script, "cannonDeploymentWorld", 304);
        Method method = DroSlayerScript.class.getDeclaredMethod("resetCannonState");
        method.setAccessible(true);
        method.invoke(script);
        assertEquals(0, get(script, "cannonAssemblyStage"));
        assertEquals(false, get(script, "cannonPlaced"));
        assertEquals(304, get(script, "cannonDeploymentWorld")); // Recovery retains the recorded world.
    }

    @Test public void restartOnWrongWorldCannotAdoptAnUnseenCannonFromAssemblyVarp() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        set(script, "config", config);
        set(script, "isUsingCannon", true);
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        when(client.getWorld()).thenReturn(337);
        when(client.getVarpValue(net.runelite.api.VarPlayer.CANNON_COORD)).thenReturn((3307 << 14) | 3669);
        var cache = mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache.class, RETURNS_DEEP_STUBS);
        try (var bot = mockStatic(Microbot.class); var player = mockStatic(Rs2Player.class)) {
            bot.when(Microbot::getClient).thenReturn(client);
            bot.when(Microbot::getClientThread).thenReturn(thread);
            bot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            bot.when(() -> Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.DROPCANNON)).thenReturn(4);
            player.when(Rs2Player::getWorldLocation).thenReturn(new net.runelite.api.coords.WorldPoint(3307, 3669, 0));
            // Explicitly return no local object instead of Mockito's deep-stub placeholder.
            when(cache.query().where(any()).within(any(net.runelite.api.coords.WorldPoint.class), anyInt()).nearestOnClientThread()).thenReturn(null);
            Method method = DroSlayerScript.class.getDeclaredMethod("advanceCannonAssembly");
            method.setAccessible(true);
            assertEquals(true, method.invoke(script));
            assertEquals(0, get(script, "cannonDeploymentWorld"));
            assertEquals(false, get(script, "cannonPlaced"));
            assertEquals(false, get(script, "isUsingCannon"));
        }
    }

    private static boolean hold(DroSlayerScript script) throws Exception {
        Method method = DroSlayerScript.class.getDeclaredMethod("holdForCannonWorldReturn");
        method.setAccessible(true); return (Boolean) method.invoke(script);
    }
    private static Object get(Object script, String name) throws Exception {
        Field field = script.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(script);
    }
    private static void set(Object script, String name, Object value) throws Exception {
        Field field = script.getClass().getDeclaredField(name); field.setAccessible(true); field.set(script, value);
    }
}
