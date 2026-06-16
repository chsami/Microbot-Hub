package net.runelite.client.plugins.microbot.attackrangesplus;

/**
 * When the overlay is shown.
 *
 * <ul>
 *     <li>{@link #ALWAYS} - always.</li>
 *     <li>{@link #IN_PVP_AREAS} - only where players can fight: the Wilderness, PvP/Deadman worlds,
 *     and PvP-flagged areas.</li>
 *     <li>{@link #WILDERNESS_ONLY} - only in the Wilderness.</li>
 * </ul>
 */
public enum DisplayMode
{
    ALWAYS("Always"),
    IN_PVP_AREAS("In PvP areas"),
    WILDERNESS_ONLY("Wilderness only");

    private final String label;

    DisplayMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
