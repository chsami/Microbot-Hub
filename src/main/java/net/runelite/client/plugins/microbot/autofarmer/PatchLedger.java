package net.runelite.client.plugins.microbot.autofarmer;
import java.util.*;
import java.io.*;
/** Persistent account-scoped patch outcomes and approximate check deadlines. */
public final class PatchLedger {
    private final Properties entries = new Properties();
    public void load(String data) {
        entries.clear();
        if (data == null || data.isEmpty()) return;
        try { entries.load(new StringReader(data)); } catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Unreadable patch memory", e); }
    }
    public String save() {
        try { StringWriter w = new StringWriter(); entries.store(w, "Auto Farmer patch memory"); return w.toString(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    public void record(String region, String outcome, long checkAt) {
        entries.setProperty(region + ".outcome", outcome);
        entries.setProperty(region + ".checkAt", Long.toString(checkAt));
    }
    public String outcome(String region) { return entries.getProperty(region + ".outcome", "UNKNOWN"); }
    public long checkAt(String region) { return Long.parseLong(entries.getProperty(region + ".checkAt", "0")); }
    public boolean due(String region, long now) { return checkAt(region) <= now; }
    public long nextCheck(Collection<String> enabled, long now) {
        long next = Long.MAX_VALUE;
        for (String region : enabled) next = Math.min(next, checkAt(region));
        return next == Long.MAX_VALUE ? now + 300000L : Math.max(now + 60000L, next);
    }
}
