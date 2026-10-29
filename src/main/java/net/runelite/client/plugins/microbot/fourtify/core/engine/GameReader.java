package net.runelite.client.plugins.microbot.fourtify.core.engine;

import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

/** Reads the game into a {@link GameState}: from the live client or a recording. */
public interface GameReader {
    GameState read();
}
