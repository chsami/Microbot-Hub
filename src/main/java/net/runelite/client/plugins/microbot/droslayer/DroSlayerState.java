package net.runelite.client.plugins.microbot.droslayer;

public enum DroSlayerState {
    IDLE("Idle"),
    GETTING_TASK("Getting Task"),
    SKIPPING_TASK("Skipping Task"),
    BLOCKING_TASK("Blocking Task"),
    DETECTING_TASK("Detecting Task"),
    RESTORING_AT_FEROX("Restoring at Ferox"),
    RESTORING_AT_POH("Restoring at POH"),
    BANKING("Banking"),
    SWAPPING_SPELLBOOK("Swapping Spellbook"),
    TRAVELING("Traveling"),
    AT_LOCATION("At Location"),
    WILDERNESS_ESCAPE("Wilderness Escape"),
    WILDERNESS_RECOVERY("Wilderness Recovery"),
    FIGHTING("Fighting");

    private final String displayName;

    DroSlayerState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
