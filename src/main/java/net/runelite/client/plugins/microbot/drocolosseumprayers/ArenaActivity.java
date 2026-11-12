package net.runelite.client.plugins.microbot.drocolosseumprayers;

public interface ArenaActivity {
    boolean arenaRunning();
    String arenaStatus();
    String arenaFailure();
    default ArenaHud arenaHud(){return null;}
    default boolean arenaRunProtected(){return false;}
}
