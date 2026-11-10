package net.runelite.client.plugins.microbot.drocolosseumprayers;

/**
 * Once entered, only local death or the arena completion receipt releases AIO ownership. A held aftermath (the
 * Colosseum chest or grave) keeps it until that claim is done.
 */
final class ArenaRunGuard {
    private volatile boolean entered, finished, held;
    void observe(boolean reliable, boolean inside, boolean dead) {
        if (!reliable) return;
        if (dead) { finished = true; return; }
        if (inside) entered = true;
    }
    boolean protectedRun() { return entered && (!finished || held); }
    void completed() { if (entered) finished = true; }
    void hold(boolean held) { this.held = held; }
}
