package net.runelite.client.plugins.microbot.droinfernoprayers;

final class ArenaRunGuard {
    private volatile boolean entered, finished;
    void observe(boolean reliable, boolean inside, boolean dead) {
        if (!reliable) return;
        if (dead) { finished = true; return; }
        if (inside) entered = true;
        else if (entered) finished = true;
    }
    boolean protectedRun() { return entered && !finished; }
    void completed() { if (entered) finished = true; }
    void departed() { finished = true; }
}
