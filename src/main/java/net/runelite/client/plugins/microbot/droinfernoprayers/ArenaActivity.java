package net.runelite.client.plugins.microbot.droinfernoprayers;

/** Standalone local fight lifecycle and immutable overlay progress. */
public interface ArenaActivity {
    boolean arenaRunning();
    String arenaStatus();
    String arenaFailure();
    default ArenaHud arenaHud(){return null;}
    default boolean arenaRunProtected(){return false;}
}
