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
    void naturalMovementStillRunsWhenOptionalSpeedVariationIsOff() {
        FakeBackend backend = new FakeBackend();
        SpeedRandom random = new SpeedRandom();
        GeflipperMouseMotion helper = realHelper(backend, random);
        NewMenuEntry entry = new NewMenuEntry();

        assertTrue(helper.move(TARGET, false, () -> true));
        assertTrue(helper.click(TARGET, false, () -> true));
        assertTrue(helper.invoke(entry, TARGET, false, () -> true));
        backend.pendingMenu(null); // The client consumes a successful invoke before the next action.
        assertTrue(helper.offscreen(new Point(-1, 20), false, () -> true));

        assertTrue(backend.delegated.isEmpty());
        assertEquals(0, random.calls);
        assertEquals(4, backend.snapshots);
        assertEquals(4, backend.gestures);
        assertEquals(Arrays.asList("press", "release", "click", "press", "release", "click"), backend.events);
    }

    @Test
    void buyingSlotReceivesIntermediateNaturalMovesBeforePressInBothSpeedModes() {
        for (boolean varySpeed : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            SpeedRandom random = new SpeedRandom();
            NewMenuEntry entry = new NewMenuEntry();
            backend.afterPress = () -> {
                assertTrue(backend.moves.size() > 2);
                assertEquals(TARGET, backend.position);
                assertSame(entry, backend.menu);
            };
            assertTrue(realHelper(backend, random).invoke(entry, TARGET, varySpeed, () -> true));
            assertNotEquals(TARGET, backend.moves.get(0), "The first event must not jump to the buy slot");
            assertEquals(varySpeed ? 1 : 0, random.calls);
            assertTrue(backend.delegated.isEmpty());
        }
    }

    @Test
    void unsuccessfulNaturalMovementCannotFallBackToTeleportingAndClicking() {
        for (boolean varySpeed : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            GeflipperMouseMotion helper = helper(backend, new SpeedRandom(), (nature, path, x, y) -> {});
            assertFalse(helper.invoke(new NewMenuEntry(), TARGET, varySpeed, () -> true));
            assertTrue(backend.events.isEmpty());
            assertTrue(backend.delegated.isEmpty());
            assertNull(backend.menu);
        }
    }

    @Test
    void disabledSpeedVariationPreservesOriginalPaceAndNeverSamplesIt() {
        FakeBackend backend = new FakeBackend();
        SpeedRandom random = new SpeedRandom();
        Object originalSpeed = backend.nature.getSpeedManager();
        GeflipperMouseMotion helper = helper(backend, random, (copy, path, x, y) -> {
            assertSame(originalSpeed, copy.getSpeedManager());
            copy.getSystemCalls().setMousePosition(x, y);
        });
        assertTrue(helper.move(TARGET, false, () -> true));
        assertSame(originalSpeed, backend.nature.getSpeedManager());
        assertEquals(0, random.calls);
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
            copy.getSystemCalls().setMousePosition(x, y);
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
    void customPolicyClicksMoveFirstAndNeverDelegateADirectSdkClick() {
        FakeBackend backend = movementBackend(2);
        NewMenuEntry entry = new NewMenuEntry();
        assertTrue(realHelper(backend, new SpeedRandom()).invoke(entry, TARGET, false, () -> true));
        assertEquals(List.of(GeflipperMouseMotion.Action.MOVE), backend.delegated);
        assertEquals(Arrays.asList("press", "release", "click"), backend.events);
        assertSame(entry, backend.menu);
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

    @Test
    void validatedClicksMoveThenValidateOutsideThePressGestureInEveryMovementMode() {
        for (int mode = 0; mode < 3; mode++) {
            FakeBackend backend = movementBackend(mode);
            SpeedRandom random = new SpeedRandom();
            AtomicBoolean validated = new AtomicBoolean();
            backend.afterPress = () -> assertTrue(validated.get());

            assertTrue(realHelper(backend, random).clickValidated(TARGET, mode != 1, () -> true, () -> {
                assertFalse(backend.inGesture, "Fresh widget/menu reads cannot hold the input lock");
                assertEquals(TARGET, backend.position);
                assertTrue(backend.events.isEmpty());
                validated.set(true);
                return true;
            }));

            assertEquals(Arrays.asList("press", "release", "click"), backend.events);
            assertEquals(TARGET, backend.lastClick);
            assertEquals(mode == 2 ? 1 : 2, backend.gestures);
            assertEquals(mode == 0 ? 1 : 0, random.calls);
            assertEquals(mode == 2 ? List.of(GeflipperMouseMotion.Action.MOVE) : List.of(), backend.delegated);
        }
    }

    @Test
    void changedMenuOrSuggestionDuringMovementCancelsWithoutAnUnvalidatedFallbackClick() {
        for (int mode = 0; mode < 3; mode++) {
            for (boolean invoke : new boolean[]{false, true}) {
                FakeBackend backend = movementBackend(mode);
                AtomicBoolean currentSuggestionAndMenu = new AtomicBoolean(true);
                backend.afterMove = () -> currentSuggestionAndMenu.set(false);
                backend.afterDelegate = () -> currentSuggestionAndMenu.set(false);
                GeflipperMouseMotion helper = realHelper(backend, new SpeedRandom());
                NewMenuEntry entry = new NewMenuEntry();

                boolean clicked = invoke
                    ? helper.invokeValidated(entry, TARGET, mode != 1, () -> true, currentSuggestionAndMenu::get)
                    : helper.clickValidated(TARGET, mode != 1, () -> true, currentSuggestionAndMenu::get);

                assertFalse(clicked);
                assertTrue(backend.events.isEmpty());
                assertNull(backend.lastClick);
                assertNull(backend.menu);
                assertEquals(mode == 2 ? 0 : 1, backend.gestures);
                assertEquals(mode == 2 ? List.of(GeflipperMouseMotion.Action.MOVE) : List.of(), backend.delegated);
            }
        }
    }

    @Test
    void stationaryClickValidatesOutsideInputOwnershipAndNeverMovesAgain() {
        FakeBackend backend = new FakeBackend();
        backend.position = TARGET;
        GeflipperMouseMotion helper = helper(backend, new SpeedRandom(),
            (nature, path, x, y) -> fail("A validated hover must not start another movement"));

        assertTrue(helper.clickStationary(TARGET, () -> true, () -> {
            assertFalse(backend.inGesture);
            assertEquals(0, backend.gestures);
            return true;
        }));

        assertEquals(Arrays.asList("press", "release", "click"), backend.events);
        assertEquals(1, backend.gestures);
        assertEquals(0, backend.snapshots);
        assertTrue(backend.moves.isEmpty());
        assertTrue(backend.delegated.isEmpty());
    }

    @Test
    void pointerDriftAfterValidationOrWhileWaitingForInputOwnershipCancelsBeforePress() {
        for (boolean duringValidation : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            backend.position = TARGET;
            Runnable drift = () -> backend.position = new Point(61, 40);
            if (!duringValidation) backend.beforeGesture = drift;

            assertFalse(realHelper(backend, new SpeedRandom()).clickStationary(TARGET, () -> true, () -> {
                if (duringValidation) drift.run();
                return true;
            }));

            assertTrue(backend.events.isEmpty());
            assertNull(backend.lastClick);
            assertTrue(backend.delegated.isEmpty());
        }
    }

    @Test
    void validatedInvokePublishesAfterValidationForNormalDisabledAndCustomMovementPolicies() {
        for (int mode = 0; mode < 3; mode++) {
            FakeBackend backend = movementBackend(mode);
            NewMenuEntry entry = new NewMenuEntry();
            backend.afterMove = () -> assertNull(backend.menu);
            backend.afterDelegate = () -> assertNull(backend.menu);
            backend.afterPress = () -> assertSame(entry, backend.menu);

            assertTrue(realHelper(backend, new SpeedRandom()).invokeValidated(entry, TARGET, mode != 1, () -> true, () -> {
                assertFalse(backend.inGesture);
                assertNull(backend.menu);
                assertEquals(TARGET, backend.position);
                return true;
            }));

            assertSame(entry, backend.menu);
            assertEquals(Arrays.asList("press", "release", "click"), backend.events);
            assertEquals(TARGET, backend.lastClick);
            assertEquals(mode == 2 ? List.of(GeflipperMouseMotion.Action.MOVE) : List.of(), backend.delegated);
        }
    }

    @Test
    void cancelledValidatedInvokeClearsOnlyItsOwnPublishedEntry() {
        for (boolean replace : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            NewMenuEntry owned = new NewMenuEntry();
            NewMenuEntry foreign = new NewMenuEntry();
            backend.afterPress = () -> {
                if (replace) backend.menu = foreign;
                backend.human = true;
            };

            assertFalse(realHelper(backend, new SpeedRandom())
                .invokeValidated(owned, TARGET, true, () -> true, () -> true));

            if (replace) assertSame(foreign, backend.menu);
            else assertNull(backend.menu);
            assertEquals(List.of("press"), backend.events);
            assertEquals(1, backend.automaticReleases);
            assertNull(backend.lastClick);
        }
    }

    @Test
    void stationaryClickRejectsForeignMenuAndFailedValidationWithoutPressing() {
        for (boolean pending : new boolean[]{false, true}) {
            FakeBackend backend = new FakeBackend();
            backend.position = TARGET;
            NewMenuEntry foreign = new NewMenuEntry();
            if (pending) backend.menu = foreign;

            assertFalse(realHelper(backend, new SpeedRandom()).clickStationary(TARGET, () -> true, () -> false));

            if (pending) assertSame(foreign, backend.menu);
            assertEquals(0, backend.gestures);
            assertTrue(backend.events.isEmpty());
        }
    }

    private static FakeBackend movementBackend(int mode) {
        FakeBackend backend = new FakeBackend();
        if (mode == 2) backend.nature.setNoiseProvider((random, x, y) -> {
            throw new AssertionError("Unknown policies must retain the SDK movement path");
        });
        return backend;
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
        private Runnable afterMove = () -> {}, afterPress = () -> {}, afterSnapshot = () -> {},
            afterDelegate = () -> {}, beforeGesture = () -> {};

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
            beforeGesture.run();
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
            if (action == GeflipperMouseMotion.Action.MOVE || action == GeflipperMouseMotion.Action.OFFSCREEN)
                position = point;
            afterDelegate.run();
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
