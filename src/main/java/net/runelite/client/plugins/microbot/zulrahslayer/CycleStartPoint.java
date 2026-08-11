package net.runelite.client.plugins.custom.zulrah;

/**
 * Where the Zulrah cycle should begin the moment the plugin starts. Only affects the FIRST iteration;
 * every subsequent loop runs the full loot -> (restock) -> travel -> fight cycle as normal.
 */
public enum CycleStartPoint {
    /** Full trip: teleport to house, restore prayer, bank/restock at the GE, then travel to Zulrah. */
    BANKING("Banking"),
    /** Skip banking; just travel from the house back to Zulrah's shrine (fairy ring -> stone -> boat). */
    TRAVEL_TO_ZULRAH("Travel to Zulrah"),
    /** Do nothing on start — assume we're already on the boat and have continued, and just wait for
     *  Zulrah to spawn. */
    BEGINNING_OF_FIGHT("Beginning of fight");

    private final String label;

    CycleStartPoint(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
