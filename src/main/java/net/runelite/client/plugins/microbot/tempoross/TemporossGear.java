package net.runelite.client.plugins.microbot.tempoross;

/**
 * Recommended Tempoross equipment, best first, from the wiki's Tempoross/Strategies table.
 *
 * <p>The outfit is what actually matters for this plugin's objective: Spirit Angler and Angler pieces
 * raise points, and points are permits. Pieces mix freely — the wiki notes that without the full
 * Spirit Angler set a Spirit piece "is interchangeable with a piece of the Angler outfit" — so each
 * slot is resolved independently rather than as a set.
 *
 * <p>Deliberately not covered:
 * <ul>
 *   <li><b>Weapon</b> — the harpoon is driven by the plugin's own config and its crate fallback, so
 *       it is handled in the script rather than here. Note the wiki splits the top tier by goal:
 *       Crystal harpoon "if maximum fishing experience is desired", Infernal harpoon "if maximum
 *       reward permits are desired". For this plugin's objective, Infernal is the right one.</li>
 *   <li><b>Rings</b> (Lightbearer, Elven/Celestial signet) and Ghommal's lucky penny — all
 *       conditional on harpoon special-attack usage or on being under the catch-rate level, and
 *       worth little here. Left out rather than guessed at.</li>
 * </ul>
 */
public enum TemporossGear
{
    HEAD("head", 25592, 13258),      // Spirit angler headband -> Angler hat
    BODY("body", 25594, 13259),      // Spirit angler top      -> Angler top
    LEGS("legs", 25596, 13260),      // Spirit angler waders   -> Angler waders
    BOOTS("boots", 25598, 13261),    // Spirit angler boots    -> Angler boots
    /**
     * Imcando hammer (off-hand). Worth equipping for a second reason beyond the wiki's: it repairs
     * without occupying an inventory slot, freeing that slot for fish.
     */
    OFFHAND("off-hand", 29775);

    private final String label;
    /** Item ids, most effective first. */
    private final int[] tiers;

    TemporossGear(String label, int... tiers)
    {
        this.label = label;
        this.tiers = tiers;
    }

    public String getLabel()
    {
        return label;
    }

    public int[] getTiers()
    {
        return tiers;
    }
}
