package net.runelite.client.plugins.microbot.irkedmlm;

import java.util.ArrayList;
import java.util.List;

/** Past sessions, newest last, stored as JSON in the plugin's config. The running session is saved as it goes. */
public class MlmHistory {
    static final int KEEP = 30;
    /** Sessions shorter than this are too short for a fair best rate. */
    static final long BEST_MIN_MS = 15 * 60_000L;

    public List<Entry> sessions = new ArrayList<>();

    public static class Entry {
        public long startMs;
        public long activeMs;
        public long xp;
        public long ores;
        public long nuggets;
        public long sacks;
        public long gp;
        /** Why it ended; null while running, so a session left null ended with the client. */
        public String reason;

        long xpPerHour() {
            return activeMs > 0 ? xp * 3_600_000L / activeMs : 0;
        }
    }

    /** Replaces the entry with the same start, or adds it; keeps the newest {@link #KEEP}. */
    void upsert(Entry e) {
        sessions.removeIf(s -> s.startMs == e.startMs);
        sessions.add(e);
        while (sessions.size() > KEEP) sessions.remove(0);
    }

    /** Sessions before the one that started at {@code currentStartMs}. */
    List<Entry> before(long currentStartMs) {
        List<Entry> out = new ArrayList<>();
        for (Entry s : sessions) if (s.startMs != currentStartMs) out.add(s);
        return out;
    }

    /** XP an hour: "Last 1h 12m · 48.2K/hr · best 52.1K", or null with no earlier session. */
    static String line(List<Entry> earlier) {
        if (earlier == null || earlier.isEmpty()) return null;
        Entry last = earlier.get(earlier.size() - 1);
        long best = 0;
        for (Entry s : earlier) if (s.activeMs >= BEST_MIN_MS) best = Math.max(best, s.xpPerHour());
        String out = "Last " + IrkedMLMOverlay.hoursMinutes(last.activeMs);
        if (last.activeMs >= 60_000) out += " · " + IrkedMLMOverlay.compact(last.xpPerHour()) + "/hr";
        if (best > 0) out += " · best " + IrkedMLMOverlay.compact(best);
        return out;
    }
}
