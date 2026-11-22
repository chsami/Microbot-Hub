package net.runelite.client.plugins.microbot.herbihunter;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * The only place Herbi Hunter logs from. Every line is {@code [HB-<TAG>] text}; the "Log detail" setting decides which
 * tiers reach client.log and nothing else. An instance, not static: static state survives a plugin restart.
 */
class HerbiboarLog
{
    static final int WARN = 2, INFO = 1, DEBUG = 0;
    /** Identical lines in a row written in full before the rest are only counted. */
    private static final int PLAIN_REPEATS = 3;

    interface Sink
    {
        void write(int level, String line);

        default boolean debugOn()
        {
            return true;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(HerbiboarLog.class);

    private final Sink sink;
    private final Supplier<String> runIds;
    private volatile HerbiboarConfig.LogDetail detail = HerbiboarConfig.LogDetail.DETAILED;

    // Guarded by this: the script thread and the client thread both log.
    private String run = "none";
    private boolean running;
    private int catchNo, steps, walks, unanswered, misses;
    private long slowestMs, lastCatchMs;
    private String slowest = "none";
    private String lastLine;
    private int lastLevel, sameInARow;

    HerbiboarLog()
    {
        this(new Sink()
        {
            @Override
            public void write(int level, String line)
            {
                // "{}" keeps slf4j from reading braces in our finished text as placeholders.
                if (level == WARN) LOG.warn("{}", line);
                else if (level == INFO) LOG.info("{}", line);
                else LOG.debug("{}", line);
            }

            @Override
            public boolean debugOn()
            {
                return LOG.isDebugEnabled();
            }
        }, () -> String.format("%04x", ThreadLocalRandom.current().nextInt(0x10000)));
    }

    HerbiboarLog(Sink sink, Supplier<String> runIds)
    {
        this.sink = sink;
        this.runIds = runIds;
    }

    long now()
    {
        return System.currentTimeMillis();
    }

    /** A change during a run is always written: a log that goes quiet must say why. */
    synchronized void setDetail(HerbiboarConfig.LogDetail detail)
    {
        if (detail == null || detail == this.detail) return;
        this.detail = detail;
        if (running) emit(INFO, "[HB-RUN] log=" + detail + " run=" + run);
    }

    /** Tier: 2 problem, 1 event, 0 detail. Pure. */
    static boolean writes(HerbiboarConfig.LogDetail detail, int tier)
    {
        if (tier >= 2) return true;
        if (tier == 1) return detail != HerbiboarConfig.LogDetail.QUIET;
        return detail == HerbiboarConfig.LogDetail.DETAILED;
    }

    synchronized void start(String version, String playStyle)
    {
        run = runIds.get();
        running = true;
        catchNo = 0;
        clearCatch();
        lastCatchMs = now();
        lastLine = null;
        sameInARow = 0;
        emit(INFO, "[HB-RUN] start v" + version + " run=" + run + " style=" + playStyle + " log=" + detail);
    }

    synchronized void stop(long minutes, int caught, String reason)
    {
        emit(INFO, "[HB-RUN] stop run=" + run + " after " + minutes + "m, " + caught + " caught, " + reason);
        running = false;
    }

    void problem(String tag, String format, Object... args)
    {
        emit(WARN, line(tag, format, args));
    }

    void event(String tag, String format, Object... args)
    {
        if (writes(detail, 1)) emit(INFO, line(tag, format, args));
    }

    void detail(String tag, String format, Object... args)
    {
        if (writes(detail, 0)) emit(INFO, line(tag, format, args));
        else if (sink.debugOn()) emit(DEBUG, line(tag, format, args));
    }

    /** For text that is costly to build: the supplier only runs when the line is written. */
    void detail(String tag, Supplier<String> text)
    {
        if (writes(detail, 0)) emit(INFO, line(tag, text.get()));
        else if (sink.debugOn()) emit(DEBUG, line(tag, text.get()));
    }

    synchronized void step(String what, String kind, long totalMs)
    {
        steps++;
        if ("walk".equals(kind)) walks++;
        if (totalMs > slowestMs)
        {
            slowestMs = totalMs;
            slowest = what + "->" + kind;
        }
    }

    /** Time on a break does not belong to the catch that follows it. */
    synchronized void paused(long ms)
    {
        lastCatchMs += ms;
    }

    synchronized void unanswered()
    {
        unanswered++;
    }

    synchronized void miss()
    {
        misses++;
    }

    /** One line per catch, then the counters start again. They count whatever the setting. */
    synchronized void caught(long nowMs, long harvestMs)
    {
        catchNo++;
        String line = catchLine(catchNo, run, Math.max(0, (nowMs - lastCatchMs) / 1000), steps, walks, slowestMs, slowest,
            harvestMs, unanswered, misses);
        lastCatchMs = nowMs;
        clearCatch();
        if (writes(detail, 1)) emit(INFO, line);
    }

    static String catchLine(int n, String run, long secs, int steps, int walks, long slowestMs, String slowest,
                            long harvestMs, int unanswered, int misses)
    {
        return "[HB-CATCH] #" + n + " run=" + run + " " + secs + "s steps=" + steps + " walks=" + walks
            + " slowest=" + slowestMs + "ms " + slowest + " harvest=" + harvestMs + "ms unanswered=" + unanswered
            + " misses=" + misses;
    }

    private void clearCatch()
    {
        steps = walks = unanswered = misses = 0;
        slowestMs = 0;
        slowest = "none";
    }

    private static String line(String tag, String format, Object... args)
    {
        String text = args == null || args.length == 0 ? format : MessageFormatter.arrayFormat(format, args).getMessage();
        return "[HB-" + tag + "] " + text;
    }

    /** A line repeated straight after itself is counted, not rewritten, so a stuck loop cannot flood the file. */
    private synchronized void emit(int level, String line)
    {
        if (line.equals(lastLine) && level == lastLevel)
        {
            if (++sameInARow > PLAIN_REPEATS) return;
        }
        else
        {
            if (sameInARow > PLAIN_REPEATS)
            {
                sink.write(lastLevel, lastLine + " (repeated " + (sameInARow - PLAIN_REPEATS) + " more times)");
            }
            lastLine = line;
            lastLevel = level;
            sameInARow = 1;
        }
        sink.write(level, line);
    }
}
