package net.runelite.client.plugins.microbot.droslayer.walker.support;

import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single tag {@code [WebWalk]} for walker + ShortestPath + pathfinder observability:
 * INFO/WARN for outcomes and decisions; DEBUG for per-run volume (pf/cfg/leagues/yields).
 */
public final class WebWalkLog {
    private static final Logger LOG = LoggerFactory.getLogger(WebWalkLog.class);

    private WebWalkLog() {
    }

    /** Pathfinder {@link net.runelite.client.plugins.microbot.droslayer.walker.support.pathfinder.Pathfinder} - DEBUG volume. */
    public static void pf(String fmt, Object... args) {
        LOG.debug("[WebWalk] pf | " + fmt, args);
    }

    /** {@link net.runelite.client.plugins.microbot.droslayer.walker.support.pathfinder.PathfinderConfig} refresh - DEBUG volume. */
    public static void cfg(String fmt, Object... args) {
        LOG.debug("[WebWalk] cfg | " + fmt, args);
    }

    /**
     * Config work slow enough to be a user-visible stall (e.g. a pathfinder-config refresh that the
     * walker blocks on at route start). INFO so it appears without enabling debug logging.
     */
    public static void cfgSlow(String fmt, Object... args) {
        LOG.info("[WebWalk] cfg | " + fmt, args);
    }

}
