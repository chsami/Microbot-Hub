package net.runelite.client.plugins.microbot.autobankstander.processors;

public enum ProcessorType {
    ENCHANTING("Bolt enchanting"),
    LUNARS("Lunar spells"),
    HERBLORE("Herblore processing");

    private final String displayName;

    ProcessorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}