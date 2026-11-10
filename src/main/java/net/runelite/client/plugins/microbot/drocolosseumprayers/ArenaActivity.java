package net.runelite.client.plugins.microbot.drocolosseumprayers;

/** Explicit AIO lifecycle; the combat connection is not a Script and is never found by field traversal. */
public interface ArenaActivity {
    boolean arenaRunning();
    String arenaStatus();
    String arenaFailure();
    default ArenaHud arenaHud(){return null;}
    default boolean arenaRunProtected(){return false;}
}
