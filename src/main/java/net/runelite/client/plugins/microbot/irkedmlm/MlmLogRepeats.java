package net.runelite.client.plugins.microbot.irkedmlm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * Keeps a stuck loop from flooding client.log: a line repeated straight after itself is written three
 * times, then only counted, and the count is written when a different line comes. Only this plugin's
 * loggers are touched.
 */
final class MlmLogRepeats extends TurboFilter {
    static final int PLAIN_REPEATS = 3;
    private static final String PACKAGE = "net.runelite.client.plugins.microbot.irkedmlm";

    private String last;
    private int same;
    private Logger lastLogger;
    private Level lastLevel;
    private boolean writingNote;

    /** -1 drops the line; otherwise write it, after a note that the line before was dropped that many times. Pure. */
    synchronized int offer(String key) {
        if (key.equals(last)) return ++same > PLAIN_REPEATS ? -1 : 0;
        int dropped = Math.max(0, same - PLAIN_REPEATS);
        last = key;
        same = 1;
        return dropped;
    }

    /** Writes the count of a line still repeating, before the filter is removed. */
    synchronized void flush() {
        int dropped = Math.max(0, same - PLAIN_REPEATS);
        if (dropped > 0 && lastLogger != null) {
            writingNote = true;
            try {
                lastLogger.info("[MLM] (line above repeated {} more times)", dropped);
            } finally {
                writingNote = false;
            }
        }
        last = null;
        same = 0;
    }

    @Override
    public synchronized FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        // Level checks (isDebugEnabled and friends) arrive with no format; the level is not reached yet either.
        if (writingNote || format == null || logger == null || level == null
                || !logger.getName().startsWith(PACKAGE) || !level.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }
        String key = logger.getName() + '|' + level + '|' + MessageFormatter.arrayFormat(format, params).getMessage();
        Logger previous = lastLogger;
        Level previousLevel = lastLevel;
        int r = offer(key);
        if (r < 0) return FilterReply.DENY;
        lastLogger = logger;
        lastLevel = level;
        if (r > 0 && previous != null) {
            writingNote = true;
            try {
                if (previousLevel != null && previousLevel.isGreaterOrEqual(Level.WARN)) previous.warn("[MLM] (line above repeated {} more times)", r);
                else previous.info("[MLM] (line above repeated {} more times)", r);
            } finally {
                writingNote = false;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
