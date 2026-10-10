package net.runelite.client.plugins.microbot.actions;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.actions.fixtures.FixtureState;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class ActionScriptShutdownTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> rs2Player;
    private DeferredExecutor executor;
    private List<String> interactions;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        rs2Player = mockStatic(Rs2Player.class);
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(false);
        executor = new DeferredExecutor();
        interactions = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        rs2Player.close();
        microbot.close();
    }

    @Test
    void queuedTickIsCancelledOnStopAndNeverInteracts() {
        ProbeScript script = new ProbeScript(executor, Arrays.asList(
                new RecordingAction(1, "bank", interactions, s -> { }),
                new RecordingAction(2, "travel", interactions, s -> { })));
        script.initialize();

        script.gameTick();
        Future<?> queued = executor.futures.get(0);
        script.shutdown();
        executor.runAll();

        assertTrue(queued.isCancelled(), "stopping must cancel the submitted tick");
        assertEquals(List.of(), interactions, "a cancelled tick must not interact");
    }

    @Test
    void stoppingMidActionInterruptsItAndSkipsLaterActions() {
        boolean[] interruptedDuringAction = new boolean[1];
        ProbeScript[] holder = new ProbeScript[1];
        ProbeScript script = new ProbeScript(executor, Arrays.asList(
                new RecordingAction(1, "bank", interactions, s -> {
                    holder[0].shutdown();
                    interruptedDuringAction[0] = Thread.currentThread().isInterrupted();
                }),
                new RecordingAction(2, "travel", interactions, s -> { })));
        holder[0] = script;
        script.initialize();

        script.gameTick();
        executor.runAll();
        Thread.interrupted();

        assertTrue(interruptedDuringAction[0], "stopping must interrupt the in-flight action");
        assertTrue(executor.futures.get(0).isCancelled(), "the in-flight tick must be cancelled");
        assertEquals(List.of("bank"), interactions, "no action may start after the plugin stopped");
    }

    @Test
    void staleTickFromBeforeRestartCannotRunOrReleaseTheNewGuard() {
        ProbeScript script = new ProbeScript(executor, Arrays.asList(
                new RecordingAction(1, "bank", interactions, s -> { })));
        script.initialize();

        script.gameTick();
        Runnable staleBody = executor.bodies.get(0);
        script.shutdown();

        script.initialize();
        script.gameTick();
        assertEquals(2, executor.bodies.size(), "a restarted script must accept a fresh tick");

        staleBody.run();
        assertEquals(List.of(), interactions, "a tick submitted before stop must not interact after restart");

        script.gameTick();
        assertEquals(2, executor.bodies.size(), "a stale tick must not release the restarted script's guard");

        executor.bodies.get(1).run();
        assertEquals(List.of("bank"), interactions, "the restarted script's own tick runs normally");
    }

    private static final class ProbeScript extends ActionScript<FixtureState> {
        private final List<Action<FixtureState>> actions;

        ProbeScript(ScheduledExecutorService executor, List<Action<FixtureState>> actions) {
            this.scheduledExecutorService = executor;
            this.actions = actions;
        }

        @Override
        protected Class<? extends Action<FixtureState>> actionType() {
            return null;
        }

        @Override
        protected List<? extends Action<FixtureState>> discoverActions() {
            return actions;
        }

        @Override
        protected FixtureState createState() {
            return new FixtureState();
        }
    }

    private static final class RecordingAction implements Action<FixtureState> {
        private final int order;
        private final String key;
        private final List<String> interactions;
        private final Consumer<FixtureState> during;

        RecordingAction(int order, String key, List<String> interactions, Consumer<FixtureState> during) {
            this.order = order;
            this.key = key;
            this.interactions = interactions;
            this.during = during;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public boolean needsExecution(FixtureState state) {
            return true;
        }

        @Override
        public Object execute(FixtureState state) {
            interactions.add(key);
            during.accept(state);
            return null;
        }
    }

    private static final class DeferredExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        final List<Runnable> bodies = new ArrayList<>();
        final List<Future<?>> futures = new ArrayList<>();
        private final List<Runnable> queued = new ArrayList<>();
        private boolean stopped;

        @Override
        public Future<?> submit(Runnable task) {
            bodies.add(task);
            Future<?> future = super.submit(task);
            futures.add(future);
            return future;
        }

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            List<Runnable> copy = new ArrayList<>(queued);
            queued.clear();
            copy.forEach(Runnable::run);
        }

        @Override
        public void shutdown() {
            stopped = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            stopped = true;
            List<Runnable> pending = new ArrayList<>(queued);
            queued.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return stopped;
        }

        @Override
        public boolean isTerminated() {
            return stopped && queued.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
