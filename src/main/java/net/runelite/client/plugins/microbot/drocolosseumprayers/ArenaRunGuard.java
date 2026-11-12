package net.runelite.client.plugins.microbot.drocolosseumprayers;

final class ArenaRunGuard {
    private volatile boolean entered, finished, held;
    void observe(boolean reliable, boolean inside, boolean dead) {
        if (!reliable) return;
        if (dead) { finished = true; return; }
        if (inside) entered = true;
    }
    boolean protectedRun() { return entered && (!finished || held); }
    void completed() { if (entered) finished = true; }
}
