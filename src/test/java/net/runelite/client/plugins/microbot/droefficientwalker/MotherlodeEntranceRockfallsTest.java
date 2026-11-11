package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.*;
import java.util.concurrent.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotherlodeEntranceRockfallsTest
{
    private static final WorldPoint ROCK = MotherlodeTransports.ENTRANCE_ROCKFALLS.get(0);

    @Test void sendsOneMineThenWaitsForObjectAndCollisionToClear() {
        Fake ports = new Fake(); ImmediateExecutor worker = new ImmediateExecutor();
        MotherlodeEntranceRockfalls handler = new MotherlodeEntranceRockfalls(ports, worker);
        assertTrue(handler.beforeTick(List.of(ROCK), 1));
        for (int i = 2; i < 20; i++) assertTrue(handler.beforeTick(List.of(ROCK), i * 100));
        assertEquals(1, ports.clicks);
        ports.present = false;
        assertTrue(handler.beforeTick(List.of(ROCK), 2100), "Cache disappearance alone is insufficient");
        ports.reachable = true;
        assertFalse(handler.beforeTick(List.of(ROCK), 2200));
        assertNull(handler.failure());
    }

    @Test void leavesAllOtherRockfallsAndNonCrossingPathsAlone() {
        Fake ports = new Fake(); MotherlodeEntranceRockfalls handler = new MotherlodeEntranceRockfalls(ports, new ImmediateExecutor());
        assertFalse(handler.beforeTick(List.of(new WorldPoint(3728, 5651, 0)), 1));
        assertFalse(handler.beforeTick(List.of(ROCK.dx(1)), 2));
        ports.player = MotherlodeTransports.BANK_APPROACH;
        assertFalse(handler.beforeTick(List.of(ROCK), 3));
        ports.player = ROCK.dy(1); ports.approach = false;
        assertFalse(handler.beforeTick(List.of(ROCK), 4));
        assertEquals(0, ports.clicks);
    }

    @Test void bothRecordedRockfallsAreRecognized() {
        for (WorldPoint rock : MotherlodeTransports.ENTRANCE_ROCKFALLS) {
            Fake ports = new Fake(); ports.player = rock.dy(1);
            MotherlodeEntranceRockfalls handler = new MotherlodeEntranceRockfalls(ports, new ImmediateExecutor());
            assertTrue(handler.beforeTick(List.of(rock), 1));
            assertEquals(1, ports.clicks);
        }
    }

    @Test void missingPickaxeAndTimeoutStopWithoutRepeatedClicks() {
        Fake ports = new Fake(); MotherlodeEntranceRockfalls handler = new MotherlodeEntranceRockfalls(ports, new ImmediateExecutor());
        ports.pickaxe = false;
        assertTrue(handler.beforeTick(List.of(ROCK), 1));
        assertTrue(handler.failure().contains("pickaxe"));
        assertEquals(0, ports.clicks);
        handler.reset(); ports.pickaxe = true;
        assertTrue(handler.beforeTick(List.of(ROCK), 10));
        assertTrue(handler.beforeTick(List.of(ROCK), 12010));
        assertTrue(handler.failure().contains("did not clear"));
        handler.beforeTick(List.of(ROCK), 13000);
        assertEquals(1, ports.clicks);
    }

    @Test void cancellationPreventsQueuedInputAndAllowsANewJourney() {
        Fake ports = new Fake(); QueuedExecutor worker = new QueuedExecutor();
        MotherlodeEntranceRockfalls handler = new MotherlodeEntranceRockfalls(ports, worker);
        handler.beforeTick(List.of(ROCK), 1); handler.reset(); worker.runQueued();
        assertEquals(0, ports.clicks);
        handler.beforeTick(List.of(ROCK), 2); worker.runQueued();
        assertEquals(1, ports.clicks);
    }

    static class Fake implements MotherlodeEntranceRockfalls.Ports {
        WorldPoint player = ROCK.dy(1); boolean present = true, reachable, approach = true, pickaxe = true; int clicks;
        public WorldPoint player() { return player; }
        public boolean present(WorldPoint p) { return present; }
        public boolean reachable(WorldPoint p) { return reachable; }
        public boolean canApproach(WorldPoint p) { return approach; }
        public boolean pickaxe() { return pickaxe; }
        public boolean mine(WorldPoint p) { clicks++; return true; }
        public void status(String text) { }
    }

    @Test void travelElsewhereDoesNotReadOrCopyTheRoute() {
        Fake ports = new Fake(); ports.player = new WorldPoint(3200,3200,0);
        MotherlodeEntranceRockfalls handler = new MotherlodeEntranceRockfalls(ports, new ImmediateExecutor());
        assertFalse(handler.beforeTick(() -> { throw new AssertionError("MLM must not inspect unrelated journeys"); }));
        assertEquals(0, ports.clicks);
    }

    static class ImmediateExecutor extends AbstractExecutorService {
        boolean stopped;
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { shutdown(); return List.of(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long t, TimeUnit unit) { return stopped; }
        public void execute(Runnable task) { task.run(); }
    }
    static class QueuedExecutor extends ImmediateExecutor {
        Runnable queued;
        public void execute(Runnable task) { queued = task; }
        void runQueued() { queued.run(); }
    }
}
