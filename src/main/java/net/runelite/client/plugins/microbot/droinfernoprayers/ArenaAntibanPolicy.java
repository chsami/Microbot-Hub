package net.runelite.client.plugins.microbot.droinfernoprayers;

/**
 * Arena lifecycle compatibility. Fight commands own their fast, no-mistake motion factory through
 * ArenaInputScope; preparation owns its separate profile. Neither changes Main's shared settings.
 */
public final class ArenaAntibanPolicy implements AutoCloseable
{
    public void start() { }
    public void apply() { }
    @Override public void close() { }
}
