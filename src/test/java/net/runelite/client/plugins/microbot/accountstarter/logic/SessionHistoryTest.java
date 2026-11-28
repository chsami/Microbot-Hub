package net.runelite.client.plugins.microbot.accountstarter.logic;

import com.google.gson.Gson;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionHistoryTest {
    private static final long H = 3_600_000L;

    @Test
    void countsOnlyWhatChangedThisSession() {
        Map<ApTask, TaskState> before = new EnumMap<>(ApTask.class);
        before.put(ApTask.ATTACK_5, TaskState.CLAIMED);
        before.put(ApTask.ATTACK_10, TaskState.DONE_UNCLAIMED);
        Map<ApTask, TaskState> now = new EnumMap<>(before);
        now.put(ApTask.ATTACK_10, TaskState.CLAIMED);
        now.put(ApTask.STRENGTH_5, TaskState.CLAIMED);
        now.put(ApTask.FISHING_5, TaskState.DONE_UNCLAIMED);
        SessionHistory.Entry e = SessionHistory.Entry.of(0, H, before, now, null);
        assertEquals(2, e.done, "Strength 5 and Fishing 5");
        assertEquals(2, e.claimed, "Attack 10 and Strength 5");
    }

    @Test
    void aStartAndStopIsNotASession() {
        Map<ApTask, TaskState> none = new EnumMap<>(ApTask.class);
        assertFalse(SessionHistory.Entry.of(0, 30_000, none, none, "stopped").worthKeeping());
        assertTrue(SessionHistory.Entry.of(0, 2 * 60_000, none, none, "stopped").worthKeeping());
    }

    @Test
    void upsertReplacesTheRunningSessionAndKeepsTheNewest() {
        SessionHistory h = new SessionHistory();
        for (int i = 0; i < SessionHistory.KEEP + 5; i++) h.upsert(entry(i * H, i * H + H, null));
        h.upsert(entry((SessionHistory.KEEP + 4) * H, (SessionHistory.KEEP + 4) * H + 2 * H, "done"));
        assertEquals(SessionHistory.KEEP, h.sessions.size());
        assertEquals("done", h.sessions.get(h.sessions.size() - 1).reason);
        assertEquals(5 * H, h.sessions.get(0).startMs);
    }

    @Test
    void lineShowsTheLastEarlierSession() {
        SessionHistory h = new SessionHistory();
        assertNull(SessionHistory.line(h.before(0)));
        SessionHistory.Entry e = entry(0, H + 12 * 60_000, "all chosen Adventure Path tasks done and claimed");
        e.done = 3;
        e.claimed = 2;
        h.upsert(e);
        h.upsert(entry(5 * H, 5 * H + 60_000, null));
        assertEquals("Last 1h 12m · 3 done · 2 claimed · all chosen Adventure Path tasks done and claimed",
                SessionHistory.line(h.before(5 * H)));
        assertEquals("Last 1m · 0 done · 0 claimed · ended with the client", SessionHistory.line(h.before(-1)));
    }

    @Test
    void survivesAJsonRoundTrip() {
        SessionHistory h = new SessionHistory();
        h.upsert(entry(1, 2, "x"));
        SessionHistory back = new Gson().fromJson(new Gson().toJson(h), SessionHistory.class);
        assertEquals(1, back.sessions.size());
        assertEquals("x", back.sessions.get(0).reason);
    }

    private static SessionHistory.Entry entry(long start, long end, String reason) {
        SessionHistory.Entry e = new SessionHistory.Entry();
        e.startMs = start;
        e.endMs = end;
        e.reason = reason;
        return e;
    }
}
