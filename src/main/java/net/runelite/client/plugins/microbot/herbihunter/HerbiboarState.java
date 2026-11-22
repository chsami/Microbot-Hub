package net.runelite.client.plugins.microbot.herbihunter;

public enum HerbiboarState {
    INITIALIZING("Starting up"),
    CHECK_AUTO_RETALIATE("Checking auto retaliate"),
    START("Finding a trail"),
    TRAIL("Following the trail"),
    TUNNEL("At the tunnel"),
    HARVEST("Harvesting"),
    BANK("Banking"),
    RETURN_FROM_ISLAND("Heading back to hunt"),
    RESTOCK("Restocking at the G.E."),
    TRAVEL("Walking to Fossil Island"),
    RESET("Recovering");

    private final String description;

    HerbiboarState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
