package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;

/** Evidence for the narrow Chaos Temple landing outside the native six-tile radius. */
final class BurningAmuletLanding
{
    static final WorldPoint CHAOS_TEMPLE = new WorldPoint(3234, 3634, 0);
    private Object request, provider, attempt;
    private long epoch;
    private int world, armedTick;
    private WorldPoint departure;
    private boolean jumped;

    static boolean charged(int id) {
        return id == 21166 || id == 21169 || id == 21171 || id == 21173 || id == 21175;
    }

    void reset() { request = provider = attempt = null; departure = null; jumped = false; }

    void arm(long epoch, Object request, Object provider, int world, int tick,
             WorldPoint departure, WorldPoint expected, boolean chargedItem) {
        if (request == null || provider == null || departure == null
            || !CHAOS_TEMPLE.equals(expected)) { reset(); return; }
        // Refresh pre-dispatch snapshots while UI preparation is pending, but never
        // overwrite observed flight evidence when the final charge disappears.
        if (this.attempt != null && this.request == request && this.provider == provider
            && this.epoch == epoch && this.world == world) return;
        if (!chargedItem) { reset(); return; }
        reset();
        this.epoch = epoch; this.request = request; this.provider = provider;
        this.world = world; this.armedTick = tick; this.departure = departure;
    }

    boolean observe(long epoch, Object request, Object provider, Object attempt, int world, int tick,
                    WorldPoint expected, WorldPoint actual, boolean loggedIn, boolean walking, boolean idle) {
        if (this.request == null || this.epoch != epoch || this.request != request || this.provider != provider
            || this.world != world || !loggedIn || !walking || !CHAOS_TEMPLE.equals(expected)
            || tick < armedTick || tick - armedTick > 16) { reset(); return false; }
        if (attempt == null || actual == null) return false;
        if (this.attempt != null && this.attempt != attempt) { reset(); return false; }
        this.attempt = attempt;
        int elapsed = tick - armedTick;
        if (departure.distanceTo2D(actual) > Math.max(16, 2 * elapsed)) jumped = true;
        int distance = actual.distanceTo2D(expected);
        return jumped && idle && actual.getPlane() == expected.getPlane() && distance >= 7 && distance <= 8;
    }
}
