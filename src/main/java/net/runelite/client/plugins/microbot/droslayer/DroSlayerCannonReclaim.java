package net.runelite.client.plugins.microbot.droslayer;

/** Separate from usable deployment state: a failed pickup must survive task changes. */
final class DroSlayerCannonReclaim {
    // Wiki: 25 minutes to break, then 10 unrepaired minutes to disappear.
    // Start at failed retrieval (latest possible repair), with a one-minute margin.
    static final long DECAY_WAIT_MS = 36 * 60_000L;
    enum Phase { NONE, WAITING, BANK, DWARF }
    private Phase phase = Phase.NONE;
    private long dueAt;
    private long startedAt;
    private int attempts;
    private int lastWorld;
    private net.runelite.api.coords.WorldPoint lastTile;
    int lastWorld() { return lastWorld; }
    net.runelite.api.coords.WorldPoint lastTile() { return lastTile; }
    void remember(int world, net.runelite.api.coords.WorldPoint tile) {
        if (world > 0 && tile != null) { lastWorld = world; lastTile = tile; }
    }

    String snapshot() {
        return phase.name() + "," + dueAt + "," + attempts + "," + lastWorld + ","
                + (lastTile == null ? "-" : lastTile.getX() + "," + lastTile.getY() + "," + lastTile.getPlane());
    }
    void restore(String saved) {
        String[] parts = saved.split(",");
        Phase restored = Phase.valueOf(parts[0]);
        long deadline = Long.parseLong(parts[1]);
        int tries = Integer.parseInt(parts[2]);
        int world = Integer.parseInt(parts[3]);
        net.runelite.api.coords.WorldPoint tile = parts[4].equals("-") ? null
                : new net.runelite.api.coords.WorldPoint(Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
        // A restart repeats the bank verification, never resumes mid-dialogue.
        phase = restored == Phase.BANK || restored == Phase.DWARF ? Phase.WAITING : restored;
        dueAt = deadline;
        attempts = Math.max(0, tries - (restored == Phase.BANK || restored == Phase.DWARF ? 1 : 0));
        lastWorld = world;
        lastTile = tile;
        startedAt = 0;
    }

    Phase phase() { return phase; }
    void missing(long now) {
        if (phase != Phase.NONE) return;
        phase = Phase.WAITING;
        dueAt = now + DECAY_WAIT_MS;
        attempts = 0;
    }
    boolean startIfDue(long now, boolean bankOpen) {
        if (!bankOpen || phase != Phase.WAITING || now < dueAt) return false;
        phase = Phase.BANK;
        startedAt = now;
        attempts++;
        return true;
    }
    boolean active() { return phase == Phase.BANK || phase == Phase.DWARF; }
    boolean timedOut(long now) { return active() && now - startedAt >= 5 * 60_000L; }
    void bankChecked() { phase = Phase.DWARF; }
    void retryLater(long now) {
        phase = attempts >= 3 ? Phase.NONE : Phase.WAITING;
        dueAt = now + 10 * 60_000L;
    }
    void found() { phase = Phase.NONE; attempts = 0; lastWorld = 0; lastTile = null; }
}
