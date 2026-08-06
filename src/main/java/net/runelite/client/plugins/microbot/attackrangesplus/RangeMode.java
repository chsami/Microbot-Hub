package net.runelite.client.plugins.microbot.attackrangesplus;

/**
 * How the overlay decides your attack radius.
 *
 * <ul>
 *     <li>{@link #AUTO} - read your equipped weapon and selected attack style (via the maintained
 *     Rs2Combat helper) and use the real range: ranged with long-range, the spell range while
 *     autocasting, halberds 2, melee 1. This is the recommended setting.</li>
 *     <li>{@link #MELEE} - fixed 1 tile.</li>
 *     <li>{@link #RANGED} - fixed 7 tiles (a representative preview; AUTO gives the exact value).</li>
 *     <li>{@link #MAGIC} - fixed 10 tiles (use this if you click-cast without an autocast set).</li>
 * </ul>
 */
public enum RangeMode
{
    AUTO("Auto (detect)"),
    MELEE("Melee (1)"),
    RANGED("Ranged (7)"),
    MAGIC("Magic (10)");

    private final String label;

    RangeMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
