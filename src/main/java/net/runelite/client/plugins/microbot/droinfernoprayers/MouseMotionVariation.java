package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Random;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DoublePoint;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;

/**
 * Optional caller-owned variation within an existing mouse movement. Keeps its destination,
 * planned duration, noise, overshoots and reaction times. It never schedules input or waits.
 * The scope travels with MouseMotionScope callbacks; unrelated callers keep their original factory.
 */
public final class MouseMotionVariation
{
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private MouseMotionVariation() { }

    public static Scope open(Session session)
    {
        return new Scope(session);
    }

    public static final class Scope implements AutoCloseable
    {
        private final Session previous = CURRENT.get();

        private Scope(Session session)
        {
            if (session == null) CURRENT.remove();
            else CURRENT.set(session);
        }

        @Override public void close()
        {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static Runnable capture(Runnable task)
    {
        Session session = CURRENT.get();
        if (session == null) return task;
        return () -> {
            try (Scope ignored = open(session)) { task.run(); }
        };
    }

    static BooleanSupplier capture(BooleanSupplier task)
    {
        Session session = CURRENT.get();
        if (session == null) return task;
        return () -> {
            try (Scope ignored = open(session)) { return task.getAsBoolean(); }
        };
    }

    /** Called after normal intensity/mistake selection and any existing command motion profile. */
    public static MouseMotionFactory apply(MouseMotionFactory source)
    {
        Session session = CURRENT.get();
        if (session == null || !session.active || source == null) return source;
        return session.vary(source);
    }

    public static final class Session implements AutoCloseable
    {
        private final Random random;
        private final double handBias;
        private final double arcScale;
        private double boutBias;
        private double targetBias;
        private int remaining;
        private volatile boolean active = true;

        public Session() { this(new Random()); }

        Session(Random random)
        {
            this.random = random;
            handBias = (random.nextDouble() - 0.5) * 0.12;
            // Contract the existing arc slightly; never introduce a larger detour or overshoot.
            arcScale = 0.90 + random.nextDouble() * 0.10;
        }

        public Scope open() { return MouseMotionVariation.open(this); }

        @Override public void close() { active = false; }

        private synchronized MouseMotionFactory vary(MouseMotionFactory source)
        {
            if (!active) return source;
            if (remaining-- <= 0)
            {
                remaining = 18 + random.nextInt(37);
                targetBias = (random.nextDouble() - 0.5) * 0.12;
            }
            boutBias += (targetBias - boutBias) * 0.20;
            double skew = handBias + boutBias + (random.nextDouble() - 0.5) * 0.04;
            double curve = arcScale + (1.0 - arcScale) * random.nextDouble();
            MouseMotionNature original = source.getNature();
            MouseMotionNature copy = new MouseMotionNature();
            copy.setSystemCalls(original.getSystemCalls());
            copy.setMouseInfo(original.getMouseInfo());
            copy.setNoiseProvider(original.getNoiseProvider());
            copy.setOvershootManager(original.getOvershootManager());
            copy.setTimeToStepsDivider(original.getTimeToStepsDivider());
            copy.setMinSteps(original.getMinSteps());
            copy.setEffectFadeSteps(original.getEffectFadeSteps());
            copy.setReactionTimeBaseMs(original.getReactionTimeBaseMs());
            copy.setReactionTimeVariationMs(original.getReactionTimeVariationMs());
            copy.setDeviationProvider((distance, completion) -> {
                DoublePoint point = original.getDeviationProvider().getDeviation(distance, completion);
                return new DoublePoint(point.getX() * curve, point.getY() * curve);
            });
            copy.setSpeedManager(distance -> {
                Pair<Flow, Long> planned = original.getSpeedManager().getFlowWithTime(distance);
                double[] weights = planned.x.getFlowCharacteristics().clone();
                for (int i = 0; i < weights.length; i++)
                {
                    double progress = weights.length == 1 ? 0.5 : (double) i / (weights.length - 1);
                    // Redistribute acceleration inside the same duration. Existing zero buckets stay zero.
                    // Scale before Flow's integer-sum normalization to avoid rounding small weights away.
                    weights[i] = weights[i] == 0 ? 0 : Math.max(1,
                            Math.rint(weights[i] * 1_000 * (1.0 + skew * (2.0 * progress - 1.0))));
                }
                return new Pair<>(new Flow(weights), planned.y);
            });
            MouseMotionFactory varied = new MouseMotionFactory(copy);
            varied.setRandom(source.getRandom());
            return varied;
        }
    }
}
