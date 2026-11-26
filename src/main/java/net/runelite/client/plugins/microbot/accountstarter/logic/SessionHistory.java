package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;

/** One account's past sessions, newest last, stored as JSON in the plugin's config. The running session is saved as it goes. */
public class SessionHistory {
    static final int KEEP = 30;
    /** A start and stop with nothing done is not a session. */
    static final long MIN_MS = 60_000L;

    public List<Entry> sessions = new ArrayList<>();

    public static class Entry {
        public long startMs;
        public long endMs;
        /** Tasks finished in this session. */
        public int done;
        /** Tasks whose reward was claimed from Jon in this session. */
        public int claimed;
        /** Why it ended; null while running, so a session left null ended with the client. */
        public String reason;

        public static Entry of(long startMs, long endMs, Map<ApTask, TaskState> before, Map<ApTask, TaskState> now, String reason) {
            Entry e = new Entry();
            e.startMs = startMs;
            e.endMs = endMs;
            e.reason = reason;
            for (ApTask t : ApTask.values()) {
                TaskState was = before.getOrDefault(t, TaskState.NOT_DONE);
                TaskState is = now.getOrDefault(t, TaskState.NOT_DONE);
                if (was == TaskState.NOT_DONE && is != TaskState.NOT_DONE) e.done++;
                if (was != TaskState.CLAIMED && is == TaskState.CLAIMED) e.claimed++;
            }
            return e;
        }

        public boolean worthKeeping() {
            return endMs - startMs >= MIN_MS || done + claimed > 0;
        }
    }

    /** Replaces the entry with the same start, or adds it; keeps the newest {@link #KEEP}. */
    public void upsert(Entry e) {
        sessions.removeIf(s -> s.startMs == e.startMs);
        sessions.add(e);
        while (sessions.size() > KEEP) sessions.remove(0);
    }

    /** Sessions before the one that started at {@code currentStartMs}. */
    public List<Entry> before(long currentStartMs) {
        List<Entry> out = new ArrayList<>();
        for (Entry s : sessions) if (s.startMs != currentStartMs) out.add(s);
        return out;
    }

    /** "Last 1h 12m · 3 done · 2 claimed · all chosen ... done", or null with no earlier session. */
    public static String line(List<Entry> earlier) {
        if (earlier == null || earlier.isEmpty()) return null;
        Entry last = earlier.get(earlier.size() - 1);
        return "Last " + PanelModel.hoursMinutes(last.endMs - last.startMs) + " · " + last.done + " done · " + last.claimed
                + " claimed · " + (last.reason == null ? "ended with the client" : last.reason);
    }
}
