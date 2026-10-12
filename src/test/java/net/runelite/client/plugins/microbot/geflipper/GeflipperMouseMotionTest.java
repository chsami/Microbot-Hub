package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotion;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeflipperMouseMotionTest {
    private static final Point TARGET = new Point(60, 40);

    @Test
    void disabledActionsDelegateWithoutReadingPolicyOrSamplingSpeed() {
        FakeBackend backend = new FakeBackend();
        SpeedRandom random = new SpeedRandom();
        GeflipperMouseMotion helper = helper(backend, random, (nature, path, x, y) -> fail("Disabled motion cannot run"));
        NewMenuEntry entry = new NewMenuEntry();

        assertTrue(helper.move(TARGET, false, () -> true));
        assertTrue(helper.click(TARGET, false, () -> true));
        assertTrue(helper.invoke(entry, TARGET, false, () -> true));
        assertTrue(helper.offscreen(new Point(-1, 20), false, () -> true));

        assertEquals(Arrays.asList(GeflipperMouseMotion.Action.MOVE, GeflipperMouseMotion.Action.CLICK,
            GeflipperMouseMotion.Action.INVOKE, GeflipperMouseMotion.Action.OFFSCREEN), backend.delegated);
        assertEquals(0, random.calls);
        assertEquals(0, backend.snapshots);
        assertEquals(0, backend.gestures);
        assertTrue(backend.events.isEmpty());
    }

    @Test
    void refusedWorkerHumanAndPauseGuardsPerformNoActions() {
        for (int condition = 0; condition < 3; condition++) {
            FakeBackend backend = new FakeBackend();
            backend.worker = condition != 0;
            backend.human = condition == 1;
            boolean running = condition != 2;
            SpeedRandom random = new SpeedRandom();

            assertFalse(realHelper(backend, random).click(TARGET, true, () -> running));
            assertEquals(0, random.calls);
            assertEquals(0, backend.snapshots);
            assertTrue(backend.events.isEmpty());
            assertTrue(backend.delegated.isEmpty());
        }
    }

    @Test
    void realMotionAndClickShareOneGestureAndUseSdkClickOrdering() {
        FakeBackend backend = new FakeBackend();
        SpeedRandom random = new SpeedRandom();

        assertTrue(realHelper(backend, random).click(TARGET, true, () -> true));

        assertEquals(1, backend.snapshots);
        assertEquals(1, backend.gestures);
        assertEquals(1, random.calls);
        assertEquals(TARGET, backend.position);
        assertEquals(TARGET, backend.lastClick);
        assertFalse(backend.moves.isEmpty());
        assertEquals(Arrays.asList("press", "release", "click"), backend.events);
        assertTrue(backend.delegated.isEmpty(), "Owned gestures cannot make nested SDK calls");
    }

    @Test
    void copiedPolicyPreservesScalarsAndStockProvidersWithoutMutatingTheSource() {
        FakeBackend backend = new FakeBackend();
        MouseMotionNature source = backend.nature;
        DefaultOvershootManager original = (DefaultOvershootManager) source.getOvershootManager();
        original.setOvershoots(2);
        original.setMinDistanceForOvershoots(7);
        original.setMinOvershootMovementMs(19);
        original.setOvershootRandomModifierDivider(13);
        original.setOvershootSpeedupDivider(1.7);
        AtomicReference<MouseMotionNature> captured = new AtomicReference<>();
        Object originalSpeed = source.getSpeedManager();
        SpeedRandom random = new SpeedRandom();
        GeflipperMouseMotion helper = helper(backend, random, (copy, path, x, y) -> {
            captured.set(copy);
            assertEquals(313L, copy.getSpeedManager().getFlowWithTime(20).y.longValue());
            assertEquals(313L, copy.getSpeedManager().getFlowWithTime(5).y.longValue());
        });

        assertTrue(helper.move(TARGET, true, () -> true));

        MouseMotionNature copy = captured.get();
        assertNotSame(source, copy);
        assertSame(source.getNoiseProvider(), copy.getNoiseProvider());
        assertSame(source.getDeviationProvider(), copy.getDeviationProvider());
        assertEquals(source.getTimeToStepsDivider(), copy.getTimeToStepsDivider());
        assertEquals(source.getMinSteps(), copy.getMinSteps());
        assertEquals(source.getEffectFadeSteps(), copy.getEffectFadeSteps());
        assertEquals(source.getReactionTimeBaseMs(), copy.getReactionTimeBaseMs());
        assertEquals(source.getReactionTimeVariationMs(), copy.getReactionTimeVariationMs());
        DefaultOvershootManager cloned = (DefaultOvershootManager) copy.getOvershootManager();
        assertNotSame(original, cloned);
        assertEquals(original.getOvershoots(), cloned.getOvershoots());
        assertEquals(original.getMinDistanceForOvershoots(), cloned.getMinDistanceForOvershoots());
        assertEquals(original.getMinOvershootMovementMs(), cloned.getMinOvershootMovementMs());
        assertEquals(original.getOvershootRandomModifierDivider(), cloned.getOvershootRandomModifierDivider());
        assertEquals(original.getOvershootSpeedupDivider(), cloned.getOvershootSpeedupDivider());
        assertSame(originalSpeed, source.getSpeedManager());
        assertNull(source.getMouseInfo());
        assertNull(source.getSystemCalls());
        assertEquals(1, random.calls, "All segments use the same sampled movement speed");
    }

    @Test
    void unknownProvidersKeepTheirUsualSdkMovementWithoutSamplingOrChangingThem() {
        FakeBackend backend = new FakeBackend();
        backend.nature.setNoiseProvider((random, x, y) -> { throw new AssertionError("Shared custom noise called"); });
        backend.nature.setDeviationProvider((distance, completion) -> { throw new AssertionError("Shared custom deviation called"); });
        Object noise = backend.nature.getNoiseProvider();
        Object deviation = backend.nature.getDeviationProvider();
        SpeedRandom random = new SpeedRandom();

        assertTrue(helper(backend, random, (copy, path, x, y) -> fail("Custom policies retain their SDK path"))
            .move(TARGET, true, () -> true));

        assertEquals(Arrays.asList(GeflipperMouseMotion.Action.MOVE), backend.delegated);
        assertEquals(0, backend.gestures);
        assertEquals(0, random.calls);
        assertSame(noise, backend.nature.getNoiseProvider());
        assertSame(deviation, backend.nature.getDeviationProvider());
    }

    @Test
    void unknownProviderFallbackStillRequiresFreshGuardAndHumanInputChecks() {
        for (boolean human : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            backend.nature.setNoiseProvider((random, x, y) -> { throw new AssertionError("Custom noise called"); });
            AtomicBoolean running = new AtomicBoolean(true);
            backend.afterSnapshot = () -> {
                if (human) backend.human = true;
                else running.set(false);
            };
            SpeedRandom random = new SpeedRandom();

            assertFalse(realHelper(backend, random).click(TARGET, true, running::get));

            assertTrue(backend.delegated.isEmpty());
            assertEquals(0, backend.gestures);
            assertEquals(0, random.calls);
        }
    }

    @Test
    void changedPauseOrHumanInputDuringMotionCancelsBeforeClickWithoutFallback() {
        for (boolean human : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            AtomicBoolean running = new AtomicBoolean(true);
            backend.afterMove = () -> {
                if (human) backend.human = true;
                else running.set(false);
            };

            assertFalse(realHelper(backend, new SpeedRandom()).click(TARGET, true, running::get));

            assertEquals(1, backend.moves.size());
            assertTrue(backend.events.isEmpty());
            assertNull(backend.lastClick);
            assertTrue(backend.delegated.isEmpty());
        }
    }

    @Test
    void interruptedSleepCancelsAndPreservesTheInterruptFlag() {
        FakeBackend backend = new FakeBackend();
        backend.interruptSleep = true;
        try {
            assertFalse(realHelper(backend, new SpeedRandom()).click(TARGET, true, () -> true));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(backend.events.isEmpty());
            assertTrue(backend.delegated.isEmpty());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void offscreenPaddingReachesTheRequestedOutsidePointEvenWhenPointerStateStopsUpdating() {
        FakeBackend backend = new FakeBackend();
        backend.position = new Point(90, 50);
        backend.freezeOutside = true;
        backend.nature.setDeviationProvider(new SinusoidalDeviationProvider(Double.POSITIVE_INFINITY));
        backend.nature.setNoiseProvider(new DefaultNoiseProvider(Double.POSITIVE_INFINITY));
        Point outside = new Point(101, 50);

        assertTrue(realHelper(backend, new SpeedRandom()).offscreen(outside, true, () -> true));

        assertEquals(outside, backend.moves.get(backend.moves.size() - 1));
        assertTrue(backend.moves.stream().anyMatch(point -> point.getX() == 100));
        assertEquals(100, backend.position.getX(), "The fake matches the SDK's first outside PointerState emission");
        assertTrue(backend.events.isEmpty());
        assertEquals(1, backend.gestures);
    }

    @Test
    void successfulInvokePublishesOnlyAfterMotionAndLeavesEntryForTheClientHook() {
        FakeBackend backend = new FakeBackend();
        NewMenuEntry entry = new NewMenuEntry();
        backend.afterMove = () -> assertNull(backend.menu, "Menu binding cannot be exposed during movement");
        backend.afterPress = () -> assertSame(entry, backend.menu);

        assertTrue(realHelper(backend, new SpeedRandom()).invoke(entry, TARGET, true, () -> true));

        assertSame(entry, backend.menu);
        assertEquals(Arrays.asList("press", "release", "click"), backend.events);
        assertEquals(TARGET, backend.lastClick);
    }

    @Test
    void abortedInvokeClearsItsOwnEntryButPreservesAReplacementEntry() {
        for (boolean replace : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            NewMenuEntry owned = new NewMenuEntry();
            NewMenuEntry foreign = new NewMenuEntry();
            backend.afterPress = () -> {
                if (replace) backend.menu = foreign;
                backend.human = true;
            };

            assertFalse(realHelper(backend, new SpeedRandom()).invoke(owned, TARGET, true, () -> true));

            if (replace) assertSame(foreign, backend.menu);
            else assertNull(backend.menu);
            assertEquals(Arrays.asList("press"), backend.events);
            assertEquals(1, backend.automaticReleases, "InputLoop owns release cleanup after cancellation");
            assertNull(backend.lastClick);
            assertTrue(backend.delegated.isEmpty());
        }
    }

    @Test
    void foreignPendingEntryBeforeOrDuringMotionIsNeverOverwritten() {
        for (boolean during : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            NewMenuEntry foreign = new NewMenuEntry();
            if (during) backend.afterMove = () -> backend.menu = foreign;
            else backend.menu = foreign;
            SpeedRandom random = new SpeedRandom();

            assertFalse(realHelper(backend, random).invoke(new NewMenuEntry(), TARGET, true, () -> true));

            assertSame(foreign, backend.menu);
            assertTrue(backend.events.isEmpty());
            assertTrue(backend.delegated.isEmpty());
            if (!during) assertEquals(0, random.calls);
        }
    }

    @Test
    void unavailablePolicyOrInvalidTargetsFailWithoutDelegatingOrSampling() {
        FakeBackend backend = new FakeBackend();
        SpeedRandom random = new SpeedRandom();
        GeflipperMouseMotion helper = realHelper(backend, random);
        assertFalse(helper.click(new Point(-1, 10), true, () -> true));
        assertFalse(helper.offscreen(TARGET, true, () -> true));
        assertFalse(helper.offscreen(new Point(10_000, 10), true, () -> true));
        backend.nature.setTimeToStepsDivider(0);
        assertFalse(helper.move(TARGET, true, () -> true));
        backend.nature.setTimeToStepsDivider(8);
        backend.nature.setSpeedManager(null);
        assertFalse(helper.move(TARGET, true, () -> true));
        assertEquals(0, random.calls);
        assertEquals(0, backend.gestures);
        assertTrue(backend.delegated.isEmpty());
    }

    private static GeflipperMouseMotion realHelper(FakeBackend backend, SpeedRandom random) {
        return helper(backend, random, (nature, path, x, y) -> new MouseMotion(nature, path, x, y).move());
    }

    private static GeflipperMouseMotion helper(FakeBackend backend, SpeedRandom random, GeflipperMouseMotion.Motion motion) {
        return new GeflipperMouseMotion(backend, random, new Random(17), motion);
    }

    private static final class SpeedRandom implements WaitingMouse.RandomInt {
        private int calls;
        public int inclusive(int minimum, int maximum) {
            assertEquals(80, minimum);
            assertEquals(120, maximum);
            calls++;
            return 80;
        }
    }

    private static final class FakeBackend implements GeflipperMouseMotion.Backend {
        private final MouseMotionNature nature = new MouseMotionNature();
        private final List<GeflipperMouseMotion.Action> delegated = new ArrayList<>();
        private final List<Point> moves = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private boolean worker = true, human, inGesture, held, interruptSleep, freezeOutside;
        private int snapshots, gestures, automaticReleases;
        private long now;
        private Point position = new Point(10, 10), lastClick;
        private MenuEntry menu;
        private Runnable afterMove = () -> {}, afterPress = () -> {}, afterSnapshot = () -> {};

        private FakeBackend() {
            nature.setTimeToStepsDivider(8);
            nature.setMinSteps(10);
            nature.setEffectFadeSteps(15);
            nature.setReactionTimeBaseMs(0);
            nature.setReactionTimeVariationMs(0);
            nature.setNoiseProvider(new DefaultNoiseProvider(2));
            nature.setDeviationProvider(new SinusoidalDeviationProvider(10));
            Flow flow = new Flow(new double[]{1, 1});
            nature.setSpeedManager(distance -> new Pair<>(flow, 250L));
            DefaultOvershootManager overshoot = new DefaultOvershootManager(new Random(9));
            overshoot.setOvershoots(0);
            nature.setOvershootManager(overshoot);
        }

        public boolean worker() { return worker; }
        public boolean human() { return human; }
        public GeflipperMouseMotion.Snapshot snapshot() {
            assertFalse(inGesture, "Client reads must happen before InputLoop ownership");
            snapshots++;
            afterSnapshot.run();
            return new GeflipperMouseMotion.Snapshot(100, 100, nature);
        }
        public Point position() { return position; }
        public boolean gesture(Consumer<GeflipperMouseMotion.Sink> action) {
            assertFalse(inGesture, "Nested InputLoop gestures are forbidden");
            gestures++;
            inGesture = true;
            try {
                action.accept(new GeflipperMouseMotion.Sink() {
                    public void checkpoint() { if (human) throw new IllegalStateException("Human input"); }
                    public void move(int x, int y) {
                        checkpoint();
                        moves.add(new Point(x, y));
                        boolean alreadyOutside = position.getX() < 0 || position.getY() < 0
                            || position.getX() >= 100 || position.getY() >= 100;
                        if (!freezeOutside || !alreadyOutside) position = new Point(x, y);
                        afterMove.run();
                    }
                    public void press(int x, int y) { checkpoint(); events.add("press"); held = true; afterPress.run(); }
                    public void release(int x, int y) { checkpoint(); events.add("release"); held = false; }
                    public void click(int x, int y) { checkpoint(); events.add("click"); }
                });
                return true;
            } finally {
                if (held) { automaticReleases++; held = false; }
                inGesture = false;
            }
        }
        public void delegate(GeflipperMouseMotion.Action action, Point point, NewMenuEntry entry) {
            assertFalse(inGesture);
            delegated.add(action);
        }
        public MenuEntry pendingMenu() { return menu; }
        public void pendingMenu(MenuEntry entry) { menu = entry; }
        public void clicked(Point point) { lastClick = point; }
        public long millis() { return now; }
        public void sleep(long millis) throws InterruptedException {
            if (interruptSleep) throw new InterruptedException();
            now += millis;
        }
    }
}
