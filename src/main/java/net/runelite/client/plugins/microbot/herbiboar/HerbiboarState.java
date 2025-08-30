package net.runelite.client.plugins.microbot.herbiboar;

public enum HerbiboarState {
    INITIALIZING("Initializing"),
    CHECK_AUTO_RETALIATE("Auto-retaliate?"),
    START("Starting"),
    TRAIL("Trailing"),
    TUNNEL("Checking tunnel"),
    HARVEST("Harvesting"),
    BANK("Banking"),
    RETURN_FROM_ISLAND("Returning from island");

    private final String description;

    HerbiboarState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
