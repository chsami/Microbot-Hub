package net.runelite.client.plugins.microbot.herbihunter;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class HerbiboarSession {

    long startMs;
    long endMs;
    int caught;
    /** Total time paused or on a break; 0 in older saved sessions. */
    long breakMs;
    /** Saved too: a checkpoint or a stop during a break must not count the break as hunting. */
    private volatile long pausedSince;
    long xpGained;
    int startLevel;
    int endLevel;
    int bankTrips;
    int restocks;
    int confusions;
    String stopReason;
    /** Null in older saved sessions. */
    String playStyle;
    // the overlay reads this while the script adds to it
    List<Long> catchTimes = new CopyOnWriteArrayList<>();

    HerbiboarSession(long startMs) {
        this.startMs = startMs;
    }

    void recordCatch(long atMs) {
        caught++;
        catchTimes.add(atMs);
    }

    /** Active time: breaks and pauses are left out, so the hourly rates are for time spent hunting. */
    synchronized long durationMs(long nowMs) {
        long until = endMs > 0 ? endMs : nowMs;
        long pausing = pausedSince > 0 ? Math.max(0, until - pausedSince) : 0;
        return Math.max(0, until - startMs - breakMs - pausing);
    }

    /** The first real reading after a start at the login screen: the session, and any pause open, begin here. */
    synchronized void beginAt(long ms) {
        startMs = ms;
        if (pausedSince > 0 && pausedSince < ms) pausedSince = ms;
    }

    synchronized void pause(long nowMs) {
        if (pausedSince == 0) pausedSince = nowMs;
    }

    /** Ends a pause and returns how long it lasted; 0 when none was running. */
    synchronized long resume(long nowMs) {
        if (pausedSince == 0) return 0;
        long ms = Math.max(0, nowMs - pausedSince);
        pausedSince = 0;
        breakMs += ms;
        return ms;
    }

    double catchesPerHour(long nowMs) {
        long ms = durationMs(nowMs);
        return ms < 60_000 ? -1 : caught * 3_600_000.0 / ms;
    }

    /** Windows before the session began are -1: drawn as nothing, not as a zero. Pure. */
    static int[] buckets(List<Long> times, long startMs, long nowMs, int n, long bucketMs) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            long to = nowMs - (long) (n - 1 - i) * bucketMs;
            long from = to - bucketMs;
            if (to <= startMs) {
                out[i] = -1;
                continue;
            }
            int c = 0;
            for (long t : times) if (t > from && t <= to) c++;
            out[i] = c;
        }
        return out;
    }

    static File dir() {
        return new File(new File(new File(System.getProperty("user.home"), ".runelite"), "microbot"), "herbiboar");
    }

    static File historyFile(File dir) {
        return new File(dir, "sessions.jsonl");
    }

    static File checkpointFile(File dir) {
        return new File(dir, "current-session.json");
    }

    static List<HerbiboarSession> loadHistory(File dir, Gson gson) {
        File f = historyFile(dir);
        if (!f.isFile()) return Collections.emptyList();
        List<HerbiboarSession> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                try {
                    HerbiboarSession s = gson.fromJson(line, HerbiboarSession.class);
                    if (s != null) out.add(s);
                } catch (RuntimeException bad) {
                    log.debug("Skipping a damaged Herbiboar session line: {}", bad.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not read Herbiboar session history {}: {}", f, e.getMessage());
        }
        return out;
    }

    static void saveFinished(File dir, Gson gson, HerbiboarSession s) {
        try {
            Files.createDirectories(dir.toPath());
            // A stop mid-append can leave a last line with no newline: this record must not join it.
            String start = endsMidLine(historyFile(dir)) ? System.lineSeparator() : "";
            Files.write(historyFile(dir).toPath(), (start + gson.toJson(s) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.deleteIfExists(checkpointFile(dir).toPath());
        } catch (IOException e) {
            log.warn("Could not save the Herbiboar session to {}: {}", dir, e.getMessage());
        }
    }

    private static boolean endsMidLine(File f) throws IOException {
        if (!f.isFile() || f.length() == 0) return false;
        try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r")) {
            r.seek(f.length() - 1);
            return r.read() != '\n';
        }
    }

    /** Atomic: a crash mid-write keeps the previous checkpoint. */
    static void checkpoint(File dir, Gson gson, HerbiboarSession s) {
        try {
            Files.createDirectories(dir.toPath());
            File tmp = new File(dir, "current-session.json.tmp");
            Files.write(tmp.toPath(), gson.toJson(s).getBytes(StandardCharsets.UTF_8));
            // ATOMIC_MOVE replaces the old file in one step, or fails and leaves it untouched.
            Files.move(tmp.toPath(), checkpointFile(dir).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.debug("Herbiboar checkpoint failed: {}", e.getMessage());
        }
    }

    /** A session is known by its start time. Pure. */
    static boolean filed(List<HerbiboarSession> history, long startMs) {
        for (HerbiboarSession h : history) if (h.startMs == startMs) return true;
        return false;
    }

    static void recoverCheckpoint(File dir, Gson gson) {
        recoverCheckpoint(dir, gson, 0);
    }

    /** @param freshMs a checkpoint written within this long is a live client's (checkpoints every 5 minutes): left alone */
    static void recoverCheckpoint(File dir, Gson gson, long freshMs) {
        File f = checkpointFile(dir);
        if (!f.isFile()) return;
        if (freshMs > 0 && System.currentTimeMillis() - f.lastModified() < freshMs) return;
        try {
            HerbiboarSession s = gson.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8),
                    HerbiboarSession.class);
            // A filed session whose checkpoint delete failed would otherwise be filed twice.
            if (s != null && s.caught > 0 && !filed(loadHistory(dir, gson), s.startMs)) {
                if (s.endMs <= 0) s.endMs = f.lastModified();
                s.resume(s.endMs);
                s.stopReason = "client closed or crashed";
                saveFinished(dir, gson, s);
                return;
            }
            Files.deleteIfExists(f.toPath());
        } catch (IOException | RuntimeException e) {
            log.debug("Herbiboar checkpoint unreadable, dropped: {}", e.getMessage());
        }
    }
}
