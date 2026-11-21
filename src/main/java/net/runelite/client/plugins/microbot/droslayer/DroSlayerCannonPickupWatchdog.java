package net.runelite.client.plugins.microbot.droslayer;

/** Walking time cannot count as a stationary missing-object observation. */
final class DroSlayerCannonPickupWatchdog {
    private long missingSince = -1L;
    boolean missing(long now, boolean stationaryAtSite) {
        if (!stationaryAtSite) { reset(); return false; }
        if (missingSince < 0) missingSince = now;
        return now - missingSince >= 10_000L;
    }
    void reset() { missingSince = -1L; }
}
