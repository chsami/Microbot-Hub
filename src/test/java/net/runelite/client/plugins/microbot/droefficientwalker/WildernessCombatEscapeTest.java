package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

public class WildernessCombatEscapeTest
{
    private static final WorldPoint LEVEL30 = new WorldPoint(3200, 3759, 0);
    private static final WorldPoint LEVEL31 = new WorldPoint(3200, 3760, 0);
    private static final WorldPoint SAFE = new WorldPoint(3200, 3400, 0);

    private static final class Observation implements WildernessCombatEscape.Environment
    {
        GameState game = GameState.LOGGED_IN;
        WorldPoint here = LEVEL30;
        boolean dead, combat, interaction, teleblocked;
        long deathAt;
        int tick;
        public GameState gameState() { return game; }
        public WorldPoint location() { return here; }
        public boolean dead() { return dead; }
        public long lastDeathAt() { return deathAt; }
        public boolean combatGrace() { return combat; }
        public boolean actorInteraction() { return interaction; }
        public boolean teleBlocked() { return teleblocked; }
        public boolean paused() { return false; }
        public int gameTick() { return tick; }
    }

    private static final class Fixture implements AutoCloseable
    {
        final Observation observation = new Observation();
        final WildernessCombatEscape controller;
        int teleports, logouts, cancellations;
        Fixture() { this(Runnable::run); }
        Fixture(Executor executor)
        {
            controller = new WildernessCombatEscape(observation, () -> cancellations++, executor,
                () -> teleports++, () -> logouts++, status -> {});
        }
        void tick(long now, boolean threat) { tick(now, threat, false, 0); }
        void tick(long now, boolean threat, boolean attacker, int player)
        {
            tickSnapshot(now, threat ? Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(
                observation.here, 90, true, attacker, attacker ? 422 : -1, player)) : Collections.emptyList());
        }
        void tickSnapshot(long now, List<WildernessThreatPolicy.NearbyPlayer> others)
        {
            observation.tick = (int) (now / 600L);
            var snapshot = WildernessThreatPolicy.evaluate(observation.here, 90, false,
                false, false, others, 32, 5);
            controller.tick(snapshot, now);
        }
        public void close() { controller.close(); }
    }

    @Test public void logoutPreferenceBelongsOnlyToTheCurrentCombatLease() {
        var ownership = new WildernessThreatOwnership();
        var lease = ownership.acquire("DroSlayer-Krystilia");
        lease.setCombatActive(true);
        assertFalse(ownership.prefersLogoutWhenOutOfCombat());
        lease.setLogoutWhenOutOfCombat(true);
        assertTrue(ownership.prefersLogoutWhenOutOfCombat());
        lease.setCombatActive(false);
        assertFalse(ownership.prefersLogoutWhenOutOfCombat());
        lease.close();
        lease.setLogoutWhenOutOfCombat(true);
        var next = ownership.acquire("DroSlayer-Krystilia");
        next.setCombatActive(true);
        assertFalse(ownership.prefersLogoutWhenOutOfCombat());
        next.close();
    }

    @Test public void revenantPreferenceLogsOutOnceWhenClearAndObservesLogout() {
        try (Fixture test = new Fixture()) {
            var threat = WildernessThreatPolicy.evaluate(LEVEL30, 90, false, false, false,
                Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(LEVEL30, 90, true, false, -1, 0)), 32, 5);
            test.observation.teleblocked = true;
            test.controller.tick(threat, 1000, true, true);
            test.controller.tick(threat, 1200, true, true);
            assertEquals(1, test.logouts);
            assertEquals(0, test.teleports);
            test.observation.game = GameState.LOGIN_SCREEN;
            test.controller.tick(threat, 1800, true, true);
            assertEquals(WildernessCombatEscape.State.LOGGED_OUT, test.controller.getState());
        }
    }

    @Test public void revenantPreferenceRetainsTeleportDuringCombatAndGrace() {
        try (Fixture test = new Fixture()) {
            var threat = WildernessThreatPolicy.evaluate(LEVEL30, 90, false, false, false,
                Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(LEVEL30, 90, true, false, -1, 0)), 32, 5);
            test.observation.combat = true;
            test.controller.tick(threat, 1000, true, true);
            assertEquals(0, test.logouts);
            assertEquals(1, test.teleports);
        }
        try (Fixture test = new Fixture()) {
            var threat = WildernessThreatPolicy.evaluate(LEVEL30, 90, false, false, false,
                Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(LEVEL30, 90, true, true, 422, 0)), 32, 5);
            test.controller.tick(threat, 1000, true, true);
            assertEquals(0, test.logouts);
            assertEquals(1, test.teleports);
        }
    }

    @Test public void slayerAttackRunsSouthThenTeleportsOnlyAtLevelThirty()
    {
        try (Fixture test = new Fixture())
        {
            int[] clicks = {0};
            test.controller.setSouthAction(() -> clicks[0]++);
            test.observation.here = LEVEL31;
            test.observation.combat = true;
            for (long now : new long[] {1000, 1200, 4600})
            {
                var threat = WildernessThreatPolicy.evaluate(test.observation.here, 90, false,
                    false, false, Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(
                    test.observation.here, 90, true, true, 422, 0)), 32, 5);
                test.controller.tick(threat, now, true);
            }
            assertEquals(2, clicks[0]);
            assertEquals(0, test.teleports);
            assertEquals(0, test.logouts);
            assertEquals(WildernessCombatEscape.State.ESCAPING, test.controller.getState());
            test.observation.here = LEVEL30;
            test.observation.teleblocked = true;
            var threat = WildernessThreatPolicy.evaluate(LEVEL30, 90, false, false, false,
                Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(LEVEL30, 90, true, true, 422, 0)), 32, 5);
            test.controller.tick(threat, 8200, true);
            assertEquals(3, clicks[0]);
            assertEquals(0, test.teleports);
            test.observation.teleblocked = false;
            test.controller.tick(threat, 8300, true);
            assertEquals(1, test.teleports);
        }
    }

    @Test public void southClickChoosesFurthestVisiblePointWithRandomLateralDrift()
    {
        WorldPoint here = new WorldPoint(3143, 3813, 0);
        WorldPoint target = WildernessSouthEscape.furthestSouth(here, 4,
            point -> here.getY() - point.getY() <= 38);
        assertEquals(new WorldPoint(3152, 3775, 0), target);
        assertNull(WildernessSouthEscape.furthestSouth(here, -4, point -> false));
    }

    @Test public void combatTeleportIsIssuedOnceWhileLandingIsPending()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.combat = true;
            test.observation.interaction = true;
            test.tick(1000, true);
            for (long now = 1100; now < 4600; now += 100) test.tick(now, true);
            assertEquals(1, test.teleports);
            assertEquals(0, test.logouts);
            assertEquals(1, test.cancellations);
            assertEquals(WildernessCombatEscape.State.ESCAPING, test.controller.getState());
            test.observation.here = SAFE;
            test.tick(4600, false);
            assertEquals(WildernessCombatEscape.State.ESCAPED, test.controller.getState());
            test.tick(9000, false);
            assertEquals(1, test.teleports);
        }
    }

    @Test public void attackingPlayerKeepsSpacedTeleportRetriesUntilEscape()
    {
        try (Fixture test = new Fixture())
        {
            for (long now = 1000; now <= 40000; now += 100) test.tick(now, true, true, 0);
            assertEquals(9, test.teleports);
            assertEquals(WildernessCombatEscape.State.ESCAPING, test.controller.getState());
        }
    }

    @Test public void teleblockHoldsPlayerAttackUntilItEndsThenResumesTeleporting()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.here = new WorldPoint(3237, 3622, 0);
            test.observation.teleblocked = true;
            test.tick(1000, true, true, 0);
            test.tick(5000, true, true, 0);
            assertEquals(0, test.teleports);
            assertEquals(1, test.cancellations);
            assertEquals(WildernessCombatEscape.State.BLOCKED, test.controller.getState());
            test.observation.teleblocked = false;
            test.tick(6000, true, true, 0);
            assertEquals(1, test.teleports);
            test.tick(9500, true, true, 0);
            assertEquals(1, test.teleports);
            test.tick(9600, true, true, 0);
            assertEquals(2, test.teleports);
        }
    }

    @Test public void continuedTeleportRetriesReleaseWhenAttackerLeavesForTenTicks()
    {
        try (Fixture test = new Fixture())
        {
            test.tick(1200, true, true, 0);
            test.tick(4800, true, true, 0);
            test.tick(10800, false);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            test.tick(11400, false);
            assertEquals(2, test.teleports);
            test.tick(12000, true, true, 1);
            assertEquals(3, test.teleports);
        }
    }

    @Test public void blockedEncounterResetsAfterTenQuietGameTicksThenAcceptsNewThreat()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.teleblocked = true;
            test.tick(1000, true);
            assertEquals(WildernessCombatEscape.State.BLOCKED, test.controller.getState());
            test.tick(2000, false);
            test.tick(6599, false);
            assertEquals(WildernessCombatEscape.State.BLOCKED, test.controller.getState());
            test.tick(6600, false);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            test.observation.teleblocked = false;
            test.tick(13000, true);
            assertEquals(1, test.teleports);
        }
    }

    @Test public void deepWildernessDoesNotTryLogoutDuringCombatOrItsGraceWindow()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.here = LEVEL31;
            test.observation.combat = true;
            test.tick(1000, true, true, 0);
            test.tick(16000, true, true, 0);
            assertEquals(WildernessCombatEscape.State.LOGOUT_WAIT_COMBAT, test.controller.getState());
            assertEquals(0, test.logouts);
            assertEquals(0, test.teleports);
            test.observation.combat = false;
            test.observation.interaction = true;
            test.tick(17000, true);
            assertEquals(0, test.logouts);
            test.observation.interaction = false;
            test.tick(18000, true);
            assertEquals(1, test.logouts);
            test.tick(18100, true);
            test.tick(19000, true);
            assertEquals(1, test.logouts);
            test.observation.game = GameState.LOGIN_SCREEN;
            test.tick(19100, false);
            assertEquals(WildernessCombatEscape.State.LOGGED_OUT, test.controller.getState());
        }
    }

    @Test public void failedDeepLogoutIsNeverClickedRepeatedly()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.here = LEVEL31;
            for (long now = 1000; now <= 30000; now += 100) test.tick(now, true);
            assertEquals(1, test.logouts);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
        }
    }

    @Test public void respawnNeverCountsAsObservedTeleportArrival()
    {
        try (Fixture test = new Fixture())
        {
            test.tick(1000, true);
            test.observation.here = SAFE;
            test.observation.deathAt = 1500;
            test.tick(1600, false);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
        }
    }

    @Test public void stalledMouseWorkerTimesOutWithoutIssuingAnotherAction()
    {
        List<Runnable> queued = new ArrayList<>();
        try (Fixture test = new Fixture(queued::add))
        {
            test.tick(1000, true);
            test.tick(11001, true);
            assertEquals(WildernessCombatEscape.State.BLOCKED, test.controller.getState());
            assertEquals(1, queued.size());
            queued.get(0).run();
            assertEquals(0, test.teleports);
            assertEquals(WildernessCombatEscape.State.BLOCKED, test.controller.getState());
        }
    }

    @Test public void cancelledQueuedActionCannotFireForTheNextEncounter()
    {
        List<Runnable> queued = new ArrayList<>();
        try (Fixture test = new Fixture(queued::add))
        {
            test.tick(1000, true);
            test.controller.reset();
            test.tick(2000, true);
            queued.get(0).run();
            assertEquals(0, test.teleports);
            assertEquals(WildernessCombatEscape.State.ESCAPING, test.controller.getState());
            queued.get(1).run();
            assertEquals(1, test.teleports);
        }
    }

    @Test public void stoppedAttackerReleasesDeepWaitEvenWhileNpcCombatContinues()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.here = LEVEL31;
            test.observation.combat = true;
            test.observation.interaction = true;
            test.tick(1200, true, true, 7);
            test.tick(2400, true, true, 7);
            test.tick(8399, true, false, 7);
            assertEquals(WildernessCombatEscape.State.LOGOUT_WAIT_COMBAT, test.controller.getState());
            test.tick(8400, true, false, 7);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            for (long now = 8500; now < 15000; now += 100) test.tick(now, true, false, 7);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            assertEquals(1, test.cancellations);
            assertEquals(0, test.teleports);
            assertEquals(0, test.logouts);
            test.tick(15000, true, true, 7);
            assertEquals(WildernessCombatEscape.State.LOGOUT_WAIT_COMBAT, test.controller.getState());
            assertEquals(2, test.cancellations);
        }
    }

    @Test public void blockedEscapeSuppressesSameQuietPlayerButNotNewIncomingPlayer()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.teleblocked = true;
            test.tick(1200, true, true, 7);
            test.tick(7200, true, false, 7);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            test.tick(7800, true, false, 7);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            assertEquals(1, test.cancellations);
            test.observation.teleblocked = false;
            test.tickSnapshot(8400, java.util.Arrays.asList(
                new WildernessThreatPolicy.NearbyPlayer(LEVEL30, 90, true, false, -1, 7),
                new WildernessThreatPolicy.NearbyPlayer(LEVEL30, 90, true, false, -1, 8)));
            assertEquals(WildernessCombatEscape.State.ESCAPING, test.controller.getState());
            assertEquals(1, test.teleports);
            assertEquals(2, test.cancellations);
        }
    }

    @Test public void departingAndReturningPlayerStartsFreshThreatEpisode()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.here = LEVEL31;
            test.observation.combat = true;
            test.tick(1200, true, false, 7);
            test.tick(7200, true, false, 7);
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            test.tick(7800, false);
            test.tick(8400, true, false, 7);
            assertEquals(WildernessCombatEscape.State.LOGOUT_WAIT_COMBAT, test.controller.getState());
            assertEquals(2, test.cancellations);
        }
    }

    @Test public void followingOrTradingDoesNotRenewQuietPlayerActivity()
    {
        try (Fixture test = new Fixture())
        {
            test.observation.here = LEVEL31;
            test.observation.combat = true;
            test.tick(1200, true, true, 7);
            for (long now = 1800; now <= 7200; now += 600)
                test.tickSnapshot(now, Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(
                    LEVEL31, 90, true, true, -1, 7)));
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            test.tickSnapshot(7800, Collections.singletonList(new WildernessThreatPolicy.NearbyPlayer(
                LEVEL31, 90, true, true, -1, 7)));
            assertEquals(WildernessCombatEscape.State.IDLE, test.controller.getState());
            assertEquals(1, test.cancellations);
        }
    }
}
