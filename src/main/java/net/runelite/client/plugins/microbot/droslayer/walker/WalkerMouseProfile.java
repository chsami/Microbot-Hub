package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.OvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates;

import java.awt.*;
import java.util.Random;

/** Mouse routines copied from Dro Slayer's BaseProfileDro, kept local to walker gestures. */
public final class WalkerMouseProfile
{
    public enum Activity { AFK, BALANCED, ACTIVE }
    public enum ParkSide { NONE, LEFT, RIGHT, TOP, BOTTOM, RANDOM }
    private final Random random = new Random();
    private double speed, confidence;
    private ParkSide randomSide;
    private int activeChance;
    private long lastNudge;

    WalkerMouseProfile() { resetSession(); }

    synchronized void resetSession()
    {
        speed = .84 + random.nextDouble() * .34;
        confidence = triangular();
        randomSide = ParkSide.values()[1 + random.nextInt(4)];
        activeChance = 10 + random.nextInt(6);
        lastNudge = 0;
    }

    private double triangular() { return (random.nextDouble() + random.nextDouble()) / 2; }
    synchronized long clickPause()
    {
        return Math.round(clamp((int) Math.round(58 + random.nextGaussian() * 24), 20, 145) * speed);
    }
    synchronized int parkChance(Activity activity)
    {
        return activity == Activity.AFK ? 75 : activity == Activity.BALANCED ? 40 : activeChance;
    }
    synchronized ParkSide side(ParkSide configured) { return configured == ParkSide.RANDOM ? randomSide : configured; }
    synchronized boolean roll(int chance) { return random.nextInt(100) < chance; }
    synchronized Point parkPoint(ParkSide configured, Point current, int width, int height)
    {
        ParkSide edge = side(configured);
        int depth = 80 + random.nextInt(101);
        int x = along(width, current == null ? null : current.getX());
        int y = along(height, current == null ? null : current.getY());
        switch (edge) {
            case LEFT: x = -depth; break;
            case RIGHT: x = width + depth; break;
            case TOP: y = -depth; break;
            case BOTTOM: y = height + depth; break;
            default: return null;
        }
        return new Point(x, y);
    }
    private int along(int length, Integer current)
    {
        int low = Math.min(24, Math.max(0, length - 1));
        int high = Math.max(low, length - 1 - 24);
        return current == null ? low + random.nextInt(high - low + 1) : clamp(current, low, high);
    }

    /** NaturalMouse stays inside the canvas; trace to this edge before crossing once. */
    static Point canvasEdge(Point target, int width, int height)
    {
        return new Point(clamp(target.getX(), 0, Math.max(0, width - 1)),
            clamp(target.getY(), 0, Math.max(0, height - 1)));
    }
    synchronized Point nudge(Point start, int width, int height)
    {
        long now = System.currentTimeMillis();
        int chance = clamp((int) Math.round(6 * (.70 + (1 - confidence) * .70)), 4, 10);
        if (now - lastNudge < 2200 || !roll(chance) || start == null
            || start.getX() < 0 || start.getY() < 0 || start.getX() >= width || start.getY() >= height) return null;
        int dx = clamp((int) Math.round(random.nextGaussian() * 14), -42, 42);
        int dy = clamp((int) Math.round(random.nextGaussian() * 14), -42, 42);
        if (Math.abs(dx) + Math.abs(dy) < 5 || width < 4 || height < 4) return null;
        lastNudge = now;
        return new Point(clamp(start.getX() + dx, 2, width - 2), clamp(start.getY() + dy, 2, height - 2));
    }
    public Point randomPoint(Rectangle bounds) {
        return randomPoint(bounds, 3);
    }

    public Point randomPoint(Rectangle bounds, int inset) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
        int safeInset = Math.max(0, Math.min(inset, Math.min(bounds.width, bounds.height) / 3));
        int minX = bounds.x + safeInset;
        int maxX = bounds.x + bounds.width - 1 - safeInset;
        int minY = bounds.y + safeInset;
        int maxY = bounds.y + bounds.height - 1 - safeInset;
        if (maxX < minX) maxX = minX;
        if (maxY < minY) maxY = minY;

        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double sx = Math.max(1.0, (maxX - minX + 1) / 5.0);
        double sy = Math.max(1.0, (maxY - minY + 1) / 5.0);

        int x = clamp((int) Math.round(cx + random.nextGaussian() * sx), minX, maxX);
        int y = clamp((int) Math.round(cy + random.nextGaussian() * sy), minY, maxY);
        return new Point(x, y);
    }


    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    /** Native MODERATE flows, with Dro session variation, without native Antiban setup/fatigue. */
    synchronized MouseMotionFactory moderateMotion(MouseMotionFactory source, boolean minimap)
    {
        MouseMotionFactory local = motion(source, minimap);
        DefaultSpeedManager manager = new DefaultSpeedManager(java.util.List.of(
            new Flow(FlowTemplates.variatingFlow()), new Flow(FlowTemplates.slowStartupFlow()),
            new Flow(FlowTemplates.slowStartup2Flow()), new Flow(FlowTemplates.adjustingFlow()),
            new Flow(FlowTemplates.jaggedFlow())));
        // A session can be a little slower, but cannot fall back below Moderate's 120ms base.
        manager.setMouseMovementBaseTimeMs(Math.round(120 * (1 + speed - .84)));
        MouseMotionNature nature = local.getNature();
        nature.setSpeedManager(manager);
        nature.setTimeToStepsDivider(DefaultMouseMotionNature.TIME_TO_STEPS_DIVIDER);
        nature.setMinSteps(DefaultMouseMotionNature.MIN_STEPS);
        nature.setEffectFadeSteps(DefaultMouseMotionNature.EFFECT_FADE_STEPS);
        nature.setReactionTimeBaseMs(DefaultMouseMotionNature.REACTION_TIME_BASE_MS);
        nature.setReactionTimeVariationMs(100);
        nature.setNoiseProvider(new DefaultNoiseProvider(DefaultNoiseProvider.DEFAULT_NOISINESS_DIVIDER));
        nature.setDeviationProvider(new SinusoidalDeviationProvider(SinusoidalDeviationProvider.DEFAULT_SLOPE_DIVIDER));
        if (!minimap) {
            DefaultOvershootManager overshoots = new DefaultOvershootManager(new Random());
            // Respect the running script's preference, read-only. Never configure Antiban.
            overshoots.setOvershoots(net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.simulateMistakes ? 3 : 0);
            overshoots.setMinDistanceForOvershoots(3);
            overshoots.setMinOvershootMovementMs(130);
            nature.setOvershootManager(overshoots);
        }
        return local;
    }

    /** Copy the motion settings, never mutate the caller's cached motion nature. */
    static MouseMotionFactory motion(MouseMotionFactory source, boolean minimap)
    {
        MouseMotionNature original = source.getNature();
        MouseMotionNature copy = new MouseMotionNature();
        copy.setTimeToStepsDivider(original.getTimeToStepsDivider());
        copy.setMinSteps(original.getMinSteps());
        copy.setEffectFadeSteps(original.getEffectFadeSteps());
        copy.setReactionTimeBaseMs(original.getReactionTimeBaseMs());
        copy.setReactionTimeVariationMs(original.getReactionTimeVariationMs());
        copy.setSystemCalls(original.getSystemCalls());
        copy.setMouseInfo(original.getMouseInfo());
        copy.setSpeedManager(original.getSpeedManager());
        copy.setNoiseProvider(original.getNoiseProvider());
        copy.setDeviationProvider(original.getDeviationProvider());
        copy.setOvershootManager(minimap ? PRECISE : original.getOvershootManager());
        return new MouseMotionFactory(copy);
    }
    private static final OvershootManager PRECISE = new OvershootManager() {
        public int getOvershoots(Flow flow, long ms, double distance) { return 0; }
        public java.awt.Point getOvershootAmount(double x, double y, long ms, int remaining) { return new java.awt.Point(); }
        public long deriveNextMouseMovementTimeMs(long ms, int remaining) { return ms; }
    };
}
