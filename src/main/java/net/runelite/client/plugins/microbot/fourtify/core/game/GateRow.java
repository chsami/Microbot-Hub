package net.runelite.client.plugins.microbot.fourtify.core.game;

/**
 * One requirement of one spot, as a plugin keeps it in its hand-written gate enum. {@code typeName}, {@code x} and
 * {@code y} name the spot as in the plugin's generated spot enum (its kind or tree, and the x and y of its centre), and
 * {@link Gates#load} groups the requirements by that key. A spot with several requirements has several rows and needs
 * all of them. The core takes the rows as this interface so that it never imports a plugin package.
 */
public interface GateRow {
    /** The constant name of the spot's kind or tree type. */
    String typeName();

    /** The x of the spot's centre. */
    int x();

    /** The y of the spot's centre. */
    int y();

    /** What the spot asks for. */
    Gate gate();
}
