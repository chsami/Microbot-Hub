package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.MouseMotionScope;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise the real helper -> client thread -> mouse worker -> natural motion boundary. */
@Disabled("Tests the droplugins client's walker-local mouse speed profiles; the Hub client uses its stock mouse")
class WalkerMouseScopeIntegrationTest
{
    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = WalkerTestReflection.declaredField(owner, name); f.setAccessible(true); return f;
    }

    private static Map<Field, Object> settingsSnapshot() throws Exception {
        Map<Field, Object> snapshot = new LinkedHashMap<>();
        for (Field f : Rs2AntibanSettings.class.getFields())
            if (Modifier.isStatic(f.getModifiers())) snapshot.put(f, f.get(null));
        return snapshot;
    }

    private static void assertSettings(Map<Field, Object> before) throws Exception {
        for (var entry : before.entrySet())
            assertEquals(entry.getValue(), entry.getKey().get(null), entry.getKey().getName());
    }

    private static EfficientWalkerConfig enabledConfig() {
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class);
        when(config.useMicrobotMouse()).thenReturn(true);
        WalkerMouseControl.configure(config);
        return config;
    }

    private static MouseMotionFactory cacheFastFactory(NaturalMouse natural) throws Exception {
        natural.nature.setSpeedManager(distance -> new Pair<>(new Flow(new double[]{1}), 10L));
        MouseMotionFactory factory = new MouseMotionFactory(natural.nature);
        field(NaturalMouse.class, "cachedFactory").set(natural, factory);
        field(NaturalMouse.class, "cachedIntensity").set(natural, Rs2Antiban.getActivityIntensity());
        return factory;
    }

    private static AutoCloseable stableSessionTiming() throws Exception {
        Object profile = field(WalkerMouseControl.class, "profile").get(null);
        Field speed = field(WalkerMouseProfile.class, "speed");
        double previous = speed.getDouble(profile);
        // Pin session variation in integration fixtures; the real random motion flows still run.
        // At the minimum session speed, whole-step rounding can yield 119ms for a 120ms base.
        speed.setDouble(profile, 1.18);
        return () -> speed.setDouble(profile, previous);
    }

    @Test void moderateTimingIsLocalAndDoesNotTouchSettingsOrTheOtherScriptsFactory() throws Exception {
        enabledConfig();
        NaturalMouse natural = new NaturalMouse();
        MouseMotionFactory original = cacheFastFactory(natural);
        var originalSpeed = original.getSpeedManager();
        var originalOvershoot = original.getOvershootManager();
        boolean oldBreak = Rs2AntibanSettings.microBreakActive;
        boolean oldCooldown = Rs2AntibanSettings.actionCooldownActive;
        boolean oldFatigue = Rs2AntibanSettings.simulateFatigue;
        var oldFatigueModel = Rs2Antiban.mouseFatigue;
        var fatigue = mock(net.runelite.client.plugins.microbot.util.antiban.MouseFatigue.class);
        Rs2AntibanSettings.microBreakActive = true;
        Rs2AntibanSettings.actionCooldownActive = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2Antiban.mouseFatigue = fatigue;
        Map<Field, Object> settings = settingsSnapshot();
        try {
            try (var ignored = WalkerMouseControl.openProfile(); var antiban = mockStatic(Rs2Antiban.class)) {
                MouseMotionFactory local = natural.getFactory();
                assertNotSame(original.getNature(), local.getNature());
                for (int i = 0; i < 100; i++)
                    assertTrue(local.getSpeedManager().getFlowWithTime(300).y >= 120,
                        "Walker must not inherit the other script's 10ms motion");
                MouseMotionFactory other = CompletableFuture.supplyAsync(natural::getFactory).get(2, TimeUnit.SECONDS);
                assertSame(original, other, "Another script thread must keep its cached factory");
                antiban.verifyNoInteractions();
            }
            assertSame(original, natural.getFactory());
            assertSame(originalSpeed, original.getSpeedManager());
            assertSame(originalOvershoot, original.getOvershootManager());
            assertSettings(settings);
            verifyNoInteractions(fatigue);
            WalkerMouseProfile profile = new WalkerMouseProfile();
            var precise = profile.moderateMotion(original, true);
            assertEquals(0, precise.getOvershootManager().getOvershoots(new Flow(new double[]{1}), 500, 300));
        } finally {
            Rs2AntibanSettings.microBreakActive = oldBreak;
            Rs2AntibanSettings.actionCooldownActive = oldCooldown;
            Rs2AntibanSettings.simulateFatigue = oldFatigue;
            Rs2Antiban.mouseFatigue = oldFatigueModel;
            WalkerMouseControl.shutdown();
        }
    }

    @Test void clientThreadRetriesAndTickEndCallbacksCarryOnlyTheirOwnProfile() throws Exception {
        enabledConfig();
        Client client = mock(Client.class);
        AtomicBoolean onClient = new AtomicBoolean(false);
        when(client.isClientThread()).thenAnswer(i -> onClient.get());
        ClientThread thread = new ClientThread();
        field(ClientThread.class, "client").set(thread, client);
        var pump = WalkerTestReflection.declaredMethod(ClientThread.class, "invoke"); pump.setAccessible(true);
        var tickEnd = WalkerTestReflection.declaredMethod(ClientThread.class, "invokeTickEnd"); tickEnd.setAccessible(true);
        List<Boolean> observed = new ArrayList<>();
        AtomicBoolean first = new AtomicBoolean(true);
        try {
            try (var ignored = WalkerMouseControl.openProfile()) {
                thread.invoke((java.util.function.BooleanSupplier) () -> {
                    observed.add(MouseMotionScope.isPresent());
                    return !first.getAndSet(false);
                });
                thread.invokeAtTickEnd(() -> observed.add(MouseMotionScope.isPresent()));
            }
            thread.invokeLater(() -> observed.add(MouseMotionScope.isPresent()));
            onClient.set(true);
            pump.invoke(thread);
            assertFalse(MouseMotionScope.isPresent(), "Profile must leave the shared client thread after a callback");
            pump.invoke(thread); tickEnd.invoke(thread);
            assertEquals(Arrays.asList(true, false, true, true), observed);
            assertFalse(MouseMotionScope.isPresent());
        } finally { WalkerMouseControl.shutdown(); }
    }

    @Test void stockWidgetAndMenuClicksRetainModerateMotionAndTrailsAfterBothThreadHandoffs() throws Exception {
        EfficientWalkerConfig config = enabledConfig();
        Client client = mock(Client.class);
        AtomicBoolean onClient = new AtomicBoolean();
        when(client.isClientThread()).thenAnswer(i -> onClient.get());
        when(client.getCanvasWidth()).thenReturn(400); when(client.getCanvasHeight()).thenReturn(250);
        ClientThread thread = new ClientThread(); field(ClientThread.class, "client").set(thread, client);
        var pump = WalkerTestReflection.declaredMethod(ClientThread.class, "invoke"); pump.setAccessible(true);
        VirtualMouse mouse = new VirtualMouse();
        var oldMouse = field(Microbot.class, "mouse").get(null);
        var oldNatural = Microbot.naturalMouse;
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        List<Runnable> deferred = new ArrayList<>();
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(i -> {
            deferred.add(i.getArgument(0)); return mock(ScheduledFuture.class);
        });
        // Keep the actual worker body but run it deterministically off the simulated client thread.
        field(VirtualMouse.class, "scheduledExecutorService").set(mouse, scheduler);
        NaturalMouse natural = new NaturalMouse();
        field(NaturalMouse.class, "client").set(natural, client);
        AtomicLong clock = new AtomicLong();
        natural.nature.setMouseInfo(mouse::getMousePosition);
        natural.nature.setSystemCalls(new SystemCalls() {
            public long currentTimeMillis() { return clock.get(); }
            public void sleep(long ms) { clock.addAndGet(ms); }
            public Dimension getScreenSize() { return new Dimension(400, 250); }
            public void setMousePosition(int x, int y) { mouse.move(x, y); }
        });
        MouseMotionFactory original = cacheFastFactory(natural);
        Widget widget = mock(Widget.class);
        Rectangle bounds = new Rectangle(280, 160, 20, 20);
        when(widget.getBounds()).thenReturn(bounds);
        NewMenuEntry entry = new NewMenuEntry(0, 0, MenuAction.CC_OP.getId(), 1, 8007, "Break");
        List<net.runelite.api.MenuEntry> dispatched = new ArrayList<>();
        Map<Field, Object> settings = settingsSnapshot();
        try (var timing = stableSessionTiming(); var micro = mockStatic(Microbot.class, CALLS_REAL_METHODS);
             var arbiter = mockStatic(InputArbiter.class); var awt = mockStatic(AwtEmitter.class)) {
            field(Microbot.class, "mouse").set(null, mouse); Microbot.naturalMouse = natural;
            micro.when(Microbot::getClient).thenReturn(client);
            micro.when(Microbot::getClientThread).thenReturn(thread);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            awt.when(() -> AwtEmitter.moved(anyInt(), anyInt())).thenAnswer(i -> {
                PointerState.setFromBot(i.getArgument(0), i.getArgument(1)); return null;
            });
            awt.when(() -> AwtEmitter.clicked(anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
                dispatched.add(Microbot.targetMenu); return null;
            });
            PointerState.setFromBot(20, 30);
            try (var ignored = WalkerMouseControl.openProfile()) {
                thread.invokeLater(() -> Rs2Widget.clickWidget(widget));
                thread.invokeLater(() -> Microbot.doInvoke(entry, bounds));
            }
            onClient.set(true); pump.invoke(thread);
            assertFalse(MouseMotionScope.isPresent()); assertEquals(2, deferred.size());
            assertTrue(dispatched.isEmpty(), "Client thread must defer the natural gestures");
            onClient.set(false);
            deferred.remove(0).run();
            assertTrue(clock.get() >= 120, "Natural-motion clock: " + clock.get()); assertTrue(mouse.getPoints().size() > 10, "Natural-motion trail points: " + mouse.getPoints().size());
            assertTrue(bounds.contains(mouse.getLastClick().getX(), mouse.getLastClick().getY()));
            deferred.remove(0).run();
            assertEquals(Arrays.asList(null, entry), dispatched, "Preserve exactly one stock click and its menu action");
            assertSame(original, natural.getFactory()); assertSettings(settings);

            // Turning the checkbox off before a queued click runs must not issue a stale click.
            try (var ignored = WalkerMouseControl.openProfile()) {
                onClient.set(true); mouse.click(new Point(120, 100));
            }
            onClient.set(false); when(config.useMicrobotMouse()).thenReturn(false);
            deferred.remove(0).run(); assertEquals(2, dispatched.size());
            assertSame(entry, Microbot.targetMenu, "Cancelling queued motion must not clear another pending menu entry");
            Runnable raw = () -> {}; assertSame(raw, WalkerMouseControl.profiledTask(raw));
        } finally {
            field(Microbot.class, "mouse").set(null, oldMouse); Microbot.naturalMouse = oldNatural;
            Microbot.targetMenu = null; WalkerMouseControl.shutdown(); PointerState.reset(); mouse.shutdown();
        }
    }

    @Test void actionWorkerRestoresContextAfterFailureAndKeepsFutureCancellation() throws Exception {
        enabledConfig();
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorService worker = WalkerMouseControl.profiledExecutor(delegate);
        try {
            assertTrue(worker.submit(MouseMotionScope::isPresent).get(2, TimeUnit.SECONDS));
            var failed = worker.submit(() -> { throw new IllegalStateException("test"); });
            assertThrows(ExecutionException.class, () -> failed.get(2, TimeUnit.SECONDS));
            assertFalse(delegate.submit(MouseMotionScope::isPresent).get(2, TimeUnit.SECONDS));
            CountDownLatch hold = new CountDownLatch(1);
            Future<?> busy = delegate.submit(() -> { try { hold.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } });
            Future<?> cancelled = worker.submit(() -> fail("Cancelled future must not execute"));
            assertTrue(cancelled.cancel(false)); hold.countDown(); busy.get(2, TimeUnit.SECONDS);
            assertTrue(cancelled.isCancelled());
        } finally { worker.shutdownNow(); WalkerMouseControl.shutdown(); }
    }

    @Test void sailingUsesTheSameLocalSpeedAndStillCancelsBeforeDispatch() throws Exception {
        enabledConfig();
        NaturalMouse natural = new NaturalMouse();
        VirtualMouse mouse = new VirtualMouse();
        AtomicLong clock = new AtomicLong();
        AtomicBoolean permission = new AtomicBoolean(true);
        AtomicBoolean cancelDuringMotion = new AtomicBoolean();
        natural.nature.setMouseInfo(mouse::getMousePosition);
        natural.nature.setSystemCalls(new SystemCalls() {
            public long currentTimeMillis() { return clock.get(); }
            public void sleep(long ms) {
                clock.addAndGet(ms);
                if (cancelDuringMotion.get()) permission.set(false);
            }
            public Dimension getScreenSize() { return new Dimension(400, 250); }
            public void setMousePosition(int x, int y) { fail("Sailing must retain its permission-guarded emitter"); }
        });
        MouseMotionFactory original = cacheFastFactory(natural);
        try (var timing = stableSessionTiming(); var micro = mockStatic(Microbot.class); var arbiter = mockStatic(InputArbiter.class);
             var awt = mockStatic(AwtEmitter.class)) {
            micro.when(Microbot::getMouse).thenReturn(mouse);
            micro.when(Microbot::getNaturalMouse).thenReturn(natural);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            awt.when(() -> AwtEmitter.moved(anyInt(), anyInt())).thenAnswer(i -> {
                PointerState.setFromBot(i.getArgument(0), i.getArgument(1)); return null;
            });
            PointerState.setFromBot(20, 30);
            assertTrue(SailingInput.click(new Point(280, 160), null, permission::get));
            assertTrue(clock.get() >= 120, "Natural-motion clock: " + clock.get()); assertTrue(mouse.getPoints().size() > 10, "Natural-motion trail points: " + mouse.getPoints().size());
            awt.verify(() -> AwtEmitter.clicked(280, 160, java.awt.event.MouseEvent.BUTTON1), times(1));
            cancelDuringMotion.set(true);
            assertFalse(SailingInput.click(new Point(50, 50), null, permission::get));
            awt.verify(() -> AwtEmitter.clicked(50, 50, java.awt.event.MouseEvent.BUTTON1), never());
            assertSame(original, natural.getFactory());
        } finally { WalkerMouseControl.shutdown(); PointerState.reset(); mouse.shutdown(); }
    }

    @Test void engineCannotJumpToTheTargetBeforeItsNaturalClick() throws Exception {
        EfficientWalkerConfig config = enabledConfig();
        var ctor = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader")
            .getDeclaredConstructor(ClassLoader.class);
        ctor.setAccessible(true);
        ClassLoader loader = (ClassLoader) ctor.newInstance(getClass().getClassLoader());
        Class<?> type = Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.engine.WalkerEngine", true, loader);
        Object engine = mock(type, CALLS_REAL_METHODS);
        var click = WalkerTestReflection.declaredMethod(type, "clickSceneWalkTarget", net.runelite.api.coords.LocalPoint.class,
            Point.class, net.runelite.api.WorldView.class, boolean.class);
        click.setAccessible(true);
        var workerMethod = WalkerTestReflection.declaredMethod(type, "getPlanningExecutor"); workerMethod.setAccessible(true);
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(400); when(client.getCanvasHeight()).thenReturn(250);
        net.runelite.api.WorldView view = mock(net.runelite.api.WorldView.class);
        when(view.getId()).thenReturn(net.runelite.api.WorldView.TOPLEVEL);
        VirtualMouse mouse = new VirtualMouse();
        NaturalMouse natural = new NaturalMouse();
        field(NaturalMouse.class, "client").set(natural, client);
        AtomicLong clock = new AtomicLong();
        natural.nature.setMouseInfo(mouse::getMousePosition);
        natural.nature.setSystemCalls(new SystemCalls() {
            public long currentTimeMillis() { return clock.get(); }
            public void sleep(long ms) { clock.addAndGet(ms); }
            public Dimension getScreenSize() { return new Dimension(400, 250); }
            public void setMousePosition(int x, int y) { mouse.move(x, y); }
        });
        cacheFastFactory(natural);
        var oldNatural = Microbot.naturalMouse;
        try (var timing = stableSessionTiming(); var micro = mockStatic(Microbot.class); var arbiter = mockStatic(InputArbiter.class);
             var awt = mockStatic(AwtEmitter.class)) {
            Microbot.naturalMouse = natural;
            micro.when(Microbot::getMouse).thenReturn(mouse);
            micro.when(Microbot::getClient).thenReturn(client);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            awt.when(() -> AwtEmitter.moved(anyInt(), anyInt())).thenAnswer(i -> {
                PointerState.setFromBot(i.getArgument(0), i.getArgument(1)); return null;
            });
            PointerState.setFromBot(20, 30);
            try (var ignored = WalkerMouseControl.openProfile()) {
                click.invoke(engine, new net.runelite.api.coords.LocalPoint(128, 128), new Point(280, 160), view, false);
            }
            assertTrue(clock.get() >= 120, "Prepositioning must not skip the Moderate trajectory");
            assertTrue(mouse.getPoints().size() > 10);
            awt.verify(() -> AwtEmitter.clicked(280, 160, java.awt.event.MouseEvent.BUTTON1), times(1));
            ExecutorService worker = (ExecutorService) workerMethod.invoke(engine);
            try { assertTrue(worker.submit(MouseMotionScope::isPresent).get(2, TimeUnit.SECONDS)); }
            finally { worker.shutdownNow(); }
            when(config.useMicrobotMouse()).thenReturn(false);
            mouse.getPoints().clear(); clock.set(0); PointerState.setFromBot(20, 30);
            click.invoke(engine, new net.runelite.api.coords.LocalPoint(128, 128), new Point(280, 160), view, false);
            assertEquals(0, clock.get(), "Checkbox off retains the old preposition-and-click path");
            awt.verify(() -> AwtEmitter.clicked(280, 160, java.awt.event.MouseEvent.BUTTON1), times(2));
        } finally {
            Microbot.naturalMouse = oldNatural; Microbot.targetMenu = null;
            WalkerMouseControl.shutdown(); PointerState.reset(); mouse.shutdown();
        }
    }
}
