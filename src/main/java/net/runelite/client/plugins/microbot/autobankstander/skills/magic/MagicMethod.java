package net.runelite.client.plugins.microbot.autobankstander.skills.magic;

public enum MagicMethod {
    ENCHANTING("Bolt enchanting"),
    LUNARS("Lunar spells"),
    ALCHING("High/Low alchemy"),
    SUPERHEATING("Superheating");

    private final String displayName;

    MagicMethod(String displayName) {
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