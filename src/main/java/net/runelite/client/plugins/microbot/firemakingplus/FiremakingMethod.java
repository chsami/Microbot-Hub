package net.runelite.client.plugins.microbot.firemakingplus;

/**
 * Which firemaking method AutoFiremakingPlus performs.
 */
public enum FiremakingMethod {
    CAMPFIRE("Forester's Campfire"),
    LINE("Line firemaking");

    private final String label;

    FiremakingMethod(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
