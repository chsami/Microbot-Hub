package net.runelite.client.plugins.microbot.firemakingplus;

/**
 * Script states. The CAMPFIRE method only uses BURNING/BANKING (driven by inventory); the LINE
 * method walks the full scan -&gt; walk-to -&gt; burn -&gt; bank -&gt; walk-back cycle.
 */
public enum State {
    SCANNING,
    WALKING_TO_LINE,
    BURNING,
    BANKING,
    WALKING_BACK
}
