package net.runelite.client.plugins.microbot.droslayer;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.death.Rs2Death;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerWildernessRecoveryTest {
    @Test public void staleDeathFlagAtSafeRespawnDoesNotBlockBanking() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 340, false);
            when(f.client.getLocalPlayer().isDead()).thenReturn(true);
            f.death.when(Rs2Death::isDead).thenReturn(true);
            f.player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3222,3218,0));
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.BANK);
            f.recovery.tick(f.config);
            f.runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(f.config, "Wilderness recovery supplies"));
        }
    }

    @Test public void deadActorAtDeathSiteCannotCountAsRespawn() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 340, false);
            when(f.client.getLocalPlayer().isDead()).thenReturn(true);
            f.death.when(Rs2Death::isDead).thenReturn(true);
            f.player.when(Rs2Player::getWorldLocation).thenReturn(f.deathPoint);
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.RESPAWN);
            f.runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString()), never());
        }
    }

    @Test public void logoutDoesNotExhaustRespawnTimeout() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 340, false);
            var started = DroSlayerWildernessRecovery.class.getDeclaredField("phaseStarted");
            started.setAccessible(true);
            started.setLong(f.recovery, System.nanoTime() - 9 * 60_000_000_000L);
            when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            f.phaseIs(DroSlayerWildernessRecovery.Phase.RESPAWN);
            when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.BANK);
        }
    }

    @Test public void externalHoldPausesRecoveryBudget() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 340, false);
            f.phase(DroSlayerWildernessRecovery.Phase.BANK);
            f.runtime.when(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString())).thenReturn(false);
            long beforeHold = System.nanoTime() - 9 * 60_000_000_000L;
            for (String name : new String[]{"phaseStarted", "suspendedAt"}) {
                var field = DroSlayerWildernessRecovery.class.getDeclaredField(name);
                field.setAccessible(true); field.setLong(f.recovery, beforeHold);
            }
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertNull(f.recovery.getFailureReason());
            f.runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(f.config, "Wilderness recovery supplies"));
        }
    }

    @Test public void nearbyPoolBehindBarrierKeepsWalkerOwnershipUntilReachable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3134,3641,0));
            f.player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(1);
            var cache=mock(net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache.class,RETURNS_DEEP_STUBS);
            var pool=mock(net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel.class);
            f.dro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            when(cache.query().withName("Pool of Refreshment").nearestOnClientThread()).thenReturn(pool);
            var refresh=DroSlayerWildernessRecovery.class.getDeclaredMethod("refreshPrayer",DroSlayerConfig.class);
            refresh.setAccessible(true);
            refresh.invoke(f.recovery,f.config);
            verify(pool,never()).click("Drink");
            f.runtime.verify(DroSlayerRuntime::cancelWalk,never());
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(f.config,new WorldPoint(3129,3636,0),1));
            when(pool.isReachable()).thenReturn(true);
            f.runtime.when(() -> DroSlayerRuntime.performAction(any(),anyBoolean(),any(java.util.function.BooleanSupplier.class)))
                .thenAnswer(c -> ((java.util.function.BooleanSupplier)c.getArgument(2)).getAsBoolean());
            refresh.invoke(f.recovery,f.config);
            f.runtime.verify(DroSlayerRuntime::cancelWalk);
            verify(pool).click("Drink");
        }
    }
    @Test public void lumbridgeRespawnUsesLiveStateDespiteStaleWildernessCache() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            f.player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3222, 3218, 0));
            f.runtime.verify(DroSlayerRuntime::resetRecoveryRoute);
            f.wilderness.when(Rs2Pvp::isInWilderness).thenReturn(true);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            f.phaseIs(DroSlayerWildernessRecovery.Phase.BANK);
        }
    }

    @Test public void respawnWaitsForLiveHpAndSafeLocationBeforeBanking() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            when(f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(0);
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.RESPAWN);
            when(f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(100);
            when(f.client.getVarbitValue(Varbits.IN_WILDERNESS)).thenReturn(1);
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.RESPAWN);
            f.runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString()), never());
            when(f.client.getVarbitValue(Varbits.IN_WILDERNESS)).thenReturn(0);
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.BANK);
            f.recovery.tick(f.config);
            f.runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(f.config, "Wilderness recovery supplies"));
        }
    }
    @Test public void secondDeathRetainsOriginalCannonSiteAndWorld() {
        try (MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class);
             MockedStatic<Rs2Pvp> wilderness = mockStatic(Rs2Pvp.class)) {
            DroSlayerWildernessRecovery recovery = new DroSlayerWildernessRecovery();
            WorldPoint firstCannon = new WorldPoint(3260, 10230, 0);
            recovery.begin(firstCannon, firstCannon, 420, true);
            recovery.begin(new WorldPoint(3290, 10235, 0), new WorldPoint(3300, 10240, 0), 422, false);
            assertEquals(firstCannon, recovery.getCannonTile());
            assertEquals(420, recovery.getCannonWorld());
            assertTrue(recovery.isActive());
            assertFalse(recovery.wasCannonUnavailable());
            recovery.cancel();
            assertFalse(recovery.isActive());
            assertEquals(0, recovery.getCannonWorld());
        }
    }

    @Test public void pvpDeathWithoutCannonOrGraveReturnsWithoutPackingOrRevisitingDeathSite() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, null, 420, false);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertEquals(DroSlayerWildernessRecovery.Result.COMPLETE, f.recovery.tick(f.config));
            assertNull(f.recovery.getFailureReason());
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            f.bank.verify(Rs2Bank::depositEquipment, never());
            f.bank.verify(Rs2Bank::depositAll, never());
            f.equipment.verify(Rs2Equipment::items, never());
        }
    }

    @Test public void graveDisappearingBeforeDepartureSkipsTheEmptyDeathSite() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, null, 420, false);
            f.phase(DroSlayerWildernessRecovery.Phase.TRAVEL);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertEquals(DroSlayerWildernessRecovery.Result.COMPLETE, f.recovery.tick(f.config));
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
        }
    }

    @Test public void optionalCannonFoodExhaustionCompletesOnlyAfterSafeBankArrival() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            f.phase(DroSlayerWildernessRecovery.Phase.TRAVEL);
            f.wilderness.when(Rs2Pvp::isInWilderness).thenReturn(true);
            f.player.when(Rs2Player::getHealthPercentage).thenReturn(20.0);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertTrue(f.recovery.wasCannonUnavailable());
            assertNull(f.recovery.getFailureReason());
            // Retreat has not completed while the character is still away from a safe bank.
            f.wilderness.when(Rs2Pvp::isInWilderness).thenReturn(false);
            f.player.when(Rs2Player::getHealthPercentage).thenReturn(100.0);
            f.runtime.when(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString())).thenReturn(false);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            f.runtime.when(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString())).thenReturn(true);
            assertEquals(DroSlayerWildernessRecovery.Result.COMPLETE, f.recovery.tick(f.config));
            assertNull(f.recovery.getFailureReason());
        }
    }

    @Test public void optionalRetrievalPrayerExhaustionReturnsForRegearRatherThanHoldingSlayer() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            f.phase(DroSlayerWildernessRecovery.Phase.TRAVEL);
            f.wilderness.when(Rs2Pvp::isInWilderness).thenReturn(true);
            f.player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertTrue(f.recovery.wasCannonUnavailable());
            f.wilderness.when(Rs2Pvp::isInWilderness).thenReturn(false);
            assertEquals(DroSlayerWildernessRecovery.Result.COMPLETE, f.recovery.tick(f.config));
            assertNull(f.recovery.getFailureReason());
        }
    }

    @Test public void missingMinimumDepartureFoodStillBlocksARealRecoveryTrip() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertEquals(DroSlayerWildernessRecovery.Result.BLOCKED, f.recovery.tick(f.config));
            assertEquals("At least five recovery food are required in the bank", f.recovery.getFailureReason());
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
        }
    }

    @Test public void originalWorldTravelRechecksSettingsAndDoesNotCompeteWithEmergencyEscape() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            f.phase(DroSlayerWildernessRecovery.Phase.TRAVEL);
            // Flags changed after the original world was rejoined; a click is not confirmation.
            f.settings.skull = false;
            f.runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("ESCAPING");
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertEquals(0, f.settings.skullClicks);
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            f.runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("IDLE");
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            assertEquals(1, f.settings.skullClicks);
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            f.settings.skull = true;
            assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(f.config, f.deathPoint, 4), times(1));
        }
    }

    @Test public void manuallyRecoveredPartsSkipRepackingAndDeathSiteVisit() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 304, false);
            var parts = java.util.Arrays.stream(new String[]{"Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace"})
                    .map(name -> {
                        var item = mock(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
                        when(item.getName()).thenReturn(name);
                        return item;
                    }).collect(java.util.stream.Collectors.toList());
            f.inventory.when(Rs2Inventory::items).thenAnswer(call -> parts.stream());
            f.recovery.tick(f.config);
            f.recovery.tick(f.config);
            assertEquals(DroSlayerWildernessRecovery.Result.COMPLETE, f.recovery.tick(f.config));
            f.bank.verify(Rs2Bank::depositAll, never());
            f.bank.verify(Rs2Bank::depositEquipment, never());
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
        }
    }

    @Test public void unknownDeploymentWorldUsesReclaimInsteadOfSearchingAnArbitraryWorld() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 0, false);
            f.phase(DroSlayerWildernessRecovery.Phase.TRAVEL);
            f.recovery.tick(f.config);
            f.phaseIs(DroSlayerWildernessRecovery.Phase.NULODION);
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
        }
    }

    @Test public void emergencyEscapeOwnsAllRecoveryInteractions() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 420, false);
            f.runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("ESCAPING");
            for (var phase : new DroSlayerWildernessRecovery.Phase[]{DroSlayerWildernessRecovery.Phase.BANK,
                    DroSlayerWildernessRecovery.Phase.REFRESH, DroSlayerWildernessRecovery.Phase.TRAVEL,
                    DroSlayerWildernessRecovery.Phase.CANNON, DroSlayerWildernessRecovery.Phase.GRAVE,
                    DroSlayerWildernessRecovery.Phase.NULODION, DroSlayerWildernessRecovery.Phase.RETURN_BANK}) {
                f.phase(phase);
                assertEquals(DroSlayerWildernessRecovery.Result.PROGRESS, f.recovery.tick(f.config));
                f.phaseIs(phase);
            }
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            f.runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString()), never());
            f.bank.verifyNoInteractions();
        }
    }

    @Test public void originalWorldHopWaitsForObservedArrivalAndCountsOnlyDispatchedAttempts() throws Exception {
        try (Fixture f = new Fixture()) {
            f.recovery.begin(f.deathPoint, f.deathPoint, 304, false);
            f.phase(DroSlayerWildernessRecovery.Phase.TRAVEL);
            var service = mock(net.runelite.client.game.WorldService.class);
            var worlds = mock(net.runelite.http.api.worlds.WorldResult.class);
            var world = mock(net.runelite.http.api.worlds.World.class);
            when(service.getWorlds()).thenReturn(worlds);
            when(worlds.findWorld(304)).thenReturn(world);
            when(world.getId()).thenReturn(304);
            when(world.getPlayers()).thenReturn(100);
            when(world.getTypes()).thenReturn(java.util.EnumSet.of(net.runelite.http.api.worlds.WorldType.MEMBERS));
            when(f.client.getWorldType()).thenReturn(java.util.EnumSet.of(net.runelite.api.WorldType.MEMBERS));
            f.dro.when(Microbot::getWorldService).thenReturn(service);
            // The profile declined the action: this must not spend a hop attempt.
            f.recovery.tick(f.config);
            var attempts = DroSlayerWildernessRecovery.class.getDeclaredField("returnWorldAttempts");
            attempts.setAccessible(true);
            assertEquals(0, attempts.get(f.recovery));
            var lastAction = DroSlayerWildernessRecovery.class.getDeclaredField("lastAction");
            lastAction.setAccessible(true);
            lastAction.set(f.recovery, 0L);
            f.runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(java.util.function.BooleanSupplier.class)))
                    .thenAnswer(call -> ((java.util.function.BooleanSupplier) call.getArgument(2)).getAsBoolean());
            f.dro.when(() -> Microbot.hopToWorld(304)).thenReturn(true);
            f.recovery.tick(f.config);
            assertEquals(1, attempts.get(f.recovery));
            f.recovery.tick(f.config);
            f.dro.verify(() -> Microbot.hopToWorld(304), times(1));
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            when(f.client.getWorld()).thenReturn(304);
            when(f.client.getGameState()).thenReturn(GameState.HOPPING);
            f.recovery.tick(f.config);
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            f.recovery.tick(f.config);
            f.runtime.verify(() -> DroSlayerRuntime.walkFull(f.config, f.deathPoint, 4));
        }
    }

    private static final class SafeSettings implements DroSlayerWildernessSettings.Environment {
        boolean skull = true;
        int skullClicks;
        public boolean loggedIn() { return true; }
        public boolean paused() { return false; }
        public boolean skullPreventionEnabled() { return skull; }
        public boolean acceptAidEnabled() { return false; }
        public boolean controlsVisible() { return true; }
        public void revealControls() { }
        public boolean enableSkullPrevention() { skullClicks++; return true; }
        public boolean disableAcceptAid() { return true; }
        public void restoreTab() { }
    }

    private static final class Fixture implements AutoCloseable {
        final WorldPoint deathPoint = new WorldPoint(3260, 10230, 0);
        final DroSlayerConfig config = mock(DroSlayerConfig.class);
        final Client client = mock(Client.class);
        final SafeSettings settings = new SafeSettings();
        final DroSlayerWildernessRecovery recovery = new DroSlayerWildernessRecovery(
                new DroSlayerWildernessSettings(settings, () -> 0L));
        final MockedStatic<DroSlayerRuntime> runtime;
        final MockedStatic<Microbot> dro;
        final MockedStatic<Rs2Pvp> wilderness;
        final MockedStatic<Rs2Player> player;
        final MockedStatic<Rs2Death> death;
        final MockedStatic<Rs2Inventory> inventory;
        final MockedStatic<Rs2Equipment> equipment;
        final MockedStatic<Rs2Bank> bank;
        Fixture() {
            try {
                runtime = mockStatic(DroSlayerRuntime.class);
                dro = mockStatic(Microbot.class);
                wilderness = mockStatic(Rs2Pvp.class);
                player = mockStatic(Rs2Player.class);
                death = mockStatic(Rs2Death.class);
                inventory = mockStatic(Rs2Inventory.class);
                equipment = mockStatic(Rs2Equipment.class);
                bank = mockStatic(Rs2Bank.class);
            ClientThread thread = mock(ClientThread.class);
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(100);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getWorld()).thenReturn(420);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                    Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            when(config.maxRecoveryAttempts()).thenReturn(3);
            when(config.wildernessRecoveryFood()).thenReturn(8);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3164, 3486, 0));
            player.when(Rs2Player::getHealthPercentage).thenReturn(100.0);
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(100);
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.PRAYER)).thenReturn(50);
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);
            inventory.when(Rs2Inventory::items).thenAnswer(call -> Stream.empty());
            equipment.when(Rs2Equipment::items).thenReturn(Collections.emptyList());
            bank.when(Rs2Bank::bankItems).thenReturn(Collections.emptyList());
            runtime.when(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString())).thenReturn(true);
            runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("IDLE");
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }
        void phase(DroSlayerWildernessRecovery.Phase phase) throws ReflectiveOperationException {
            var field = DroSlayerWildernessRecovery.class.getDeclaredField("phase");
            field.setAccessible(true);
            field.set(recovery, phase);
        }
        void phaseIs(DroSlayerWildernessRecovery.Phase phase) throws ReflectiveOperationException {
            var field = DroSlayerWildernessRecovery.class.getDeclaredField("phase");
            field.setAccessible(true);
            assertEquals(phase, field.get(recovery));
        }
        public void close() {
            for (MockedStatic<?> handle : new MockedStatic<?>[] { bank, equipment, inventory, death, player, wilderness, dro, runtime }) {
                if (handle != null && !handle.isClosed()) handle.close();
            }
        }
    }

    @Test public void recoveryPackStaysBetweenFiveAndTenFood() {
        assertEquals(5, DroSlayerWildernessRecovery.recoveryFoodTarget(0));
        assertEquals(8, DroSlayerWildernessRecovery.recoveryFoodTarget(8));
        assertEquals(10, DroSlayerWildernessRecovery.recoveryFoodTarget(28));
        assertFalse(DroSlayerWildernessRecovery.hasMinimumRecoveryFood(4));
        assertTrue(DroSlayerWildernessRecovery.hasMinimumRecoveryFood(5));
    }
    @Test public void cannonLookupRejectsUnrelatedCannonAndDifferentFloor() {
        WorldPoint saved = new WorldPoint(3260, 10230, 0);
        assertTrue(DroSlayerWildernessRecovery.matchesCannonSite(saved, saved, null));
        assertTrue(DroSlayerWildernessRecovery.matchesCannonSite(new WorldPoint(3261, 10231, 0), saved, saved));
        assertFalse(DroSlayerWildernessRecovery.matchesCannonSite(new WorldPoint(3265, 10230, 0), saved, null));
        assertFalse(DroSlayerWildernessRecovery.matchesCannonSite(new WorldPoint(3260, 10230, 1), saved, null));
        assertFalse(DroSlayerWildernessRecovery.matchesCannonSite(saved, saved, new WorldPoint(3290, 10230, 0)));
        assertFalse(DroSlayerWildernessRecovery.matchesCannonSite(saved, null, saved));
    }
    @Test public void recoveryEscapeMustWorkAboveLevelTwenty() {
        assertEquals("Commune", DroSlayerWildernessRecovery.escapeDestination("Royal seed pod"));
        assertEquals("Edgeville", DroSlayerWildernessRecovery.escapeDestination("Amulet of glory(1)"));
        assertEquals("Grand Exchange", DroSlayerWildernessRecovery.escapeDestination("Ring of wealth (3)"));
        assertNull(DroSlayerWildernessRecovery.escapeDestination("Ring of dueling(8)"));
        assertNull(DroSlayerWildernessRecovery.escapeDestination("Amulet of glory"));
        assertNull(DroSlayerWildernessRecovery.escapeDestination("Amulet of glory(0)"));
    }
}
