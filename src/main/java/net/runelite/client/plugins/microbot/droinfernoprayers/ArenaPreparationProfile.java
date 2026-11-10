package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.Rectangle;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.DroSetupMouse;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope;
import net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionVariation;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;

/** Local form of the Lite coordinator's SETUP-only profile; no breaks, cameras or login owner. */
public final class ArenaPreparationProfile implements AutoCloseable, MouseMotionScope.Profile {
    private static final AtomicInteger PREPARING = new AtomicInteger();
    private final BooleanSupplier allowed;
    private final Random random = new Random();
    private final double speed = 0.84 + random.nextDouble() * 0.34;
    private final double hesitation = (random.nextDouble() + random.nextDouble()) / 2;
    private final MouseMotionVariation.Session variation = new MouseMotionVariation.Session();
    private volatile boolean ended;
    private AutoCloseable lease;
    private long nextActionAt;
    private MouseMotionFactory factory;

    public ArenaPreparationProfile() { this(() -> true); }
    ArenaPreparationProfile(BooleanSupplier allowed) { this.allowed = allowed; }
    static boolean preparing() { return PREPARING.get() > 0; }

    public void activate() {
        // Permission can ask the client thread; never hold our lifecycle monitor while reading it.
        if(!isValid())return;
        synchronized(this){
            if (ended || lease != null) return;
            lease = MouseMotionScope.background(this);
            PREPARING.incrementAndGet();
            DroSetupMouse.register(this, this::before, this::after, this::point, () -> { }, this::isValid);
        }
    }

    public boolean perform(BooleanSupplier action, Runnable permission) {
        if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
            throw new IllegalStateException("Preparation input must run off the client thread");
        if (ended) { permission.run(); return action.getAsBoolean(); }
        activate();
        if (!Global.sleepUntil(() -> !isValid() || monotonic() >= nextActionAt, 1500) || !isValid()) return false;
        try (MouseMotionVariation.Scope ignored = variation.open()) {
            before();
            permission.run();
            if (!isValid()) return false;
            try { return action.getAsBoolean(); }
            finally { nextActionAt = monotonic() + 600; after(); }
        }
    }

    private void before() {
        int base=70+random.nextInt(161);
        pause(gaussian(base,Math.max(10,base/4),30,360));
        int chance = Math.max(3, Math.min(12, (int) Math.round(7 * (0.65 + hesitation * 0.7))));
        if (random.nextInt(100) < chance) pause(gaussian(170, 80, 70, 430));
    }
    private void after() { pause(gaussian(75, 25, 20, 220)); }
    private int gaussian(int mean, int spread, int low, int high) {
        return Math.max(low, Math.min(high, (int) Math.round(mean + random.nextGaussian() * spread)));
    }
    private void pause(long millis) {
        long until = monotonic() + Math.max(0, Math.round(millis * speed * 0.35));
        Global.sleepUntil(() -> !isValid() || monotonic() >= until, 1000);
    }
    private static long monotonic() { return System.nanoTime() / 1_000_000; }
    private net.runelite.api.Point point(Rectangle bounds) {
        if (bounds == null || bounds.isEmpty()) return null;
        int inset = Math.max(0, Math.min(3, Math.min(bounds.width, bounds.height) / 3));
        int lowX = bounds.x + inset, highX = bounds.x + bounds.width - 1 - inset;
        int lowY = bounds.y + inset, highY = bounds.y + bounds.height - 1 - inset;
        return new net.runelite.api.Point(
            Math.max(lowX, Math.min(highX, (int) Math.round((lowX + highX) / 2.0 + random.nextGaussian() * Math.max(1, bounds.width / 5.0)))),
            Math.max(lowY, Math.min(highY, (int) Math.round((lowY + highY) / 2.0 + random.nextGaussian() * Math.max(1, bounds.height / 5.0)))));
    }
    public long clickPause() { return 0; }
    public boolean isValid() {
        return !ended && allowed.getAsBoolean() && !Thread.currentThread().isInterrupted()
            && !Microbot.pauseAllScripts.get() && !InputArbiter.isHuman();
    }
    public synchronized MouseMotionFactory motion(MouseMotionFactory source) {
        if (source == null) return null;
        if (factory != null) return factory;
        // Lite's LOW/normal-gamer trajectory and mistakes, on a private nature only.
        var original = source.getNature();
        var copy = new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature();
        copy.setSystemCalls(original.getSystemCalls());copy.setMouseInfo(original.getMouseInfo());
        copy.setTimeToStepsDivider(original.getTimeToStepsDivider());copy.setMinSteps(original.getMinSteps());
        copy.setEffectFadeSteps(original.getEffectFadeSteps());copy.setReactionTimeBaseMs(original.getReactionTimeBaseMs());
        copy.setReactionTimeVariationMs(100);
        copy.setDeviationProvider(new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider(10));
        copy.setNoiseProvider(new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider(2));
        var overshoots = new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager(new Random());
        overshoots.setOvershoots(4);overshoots.setMinDistanceForOvershoots(3);overshoots.setMinOvershootMovementMs(250);
        copy.setOvershootManager(overshoots);
        var speedManager = new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager(java.util.List.of(
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.variatingFlow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.slowStartupFlow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.slowStartup2Flow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.adjustingFlow()),
            new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates.jaggedFlow())));
        speedManager.setMouseMovementBaseTimeMs(150);copy.setSpeedManager(speedManager);
        return factory = new MouseMotionFactory(copy);
    }
    @Override public synchronized void close() {
        if (ended) return;
        ended = true;
        DroSetupMouse.unregister(this);
        variation.close();
        if (lease != null) {
            PREPARING.decrementAndGet();
            try { lease.close(); } catch (Exception error) { throw new IllegalStateException(error); }
            lease = null;
        }
    }
}
