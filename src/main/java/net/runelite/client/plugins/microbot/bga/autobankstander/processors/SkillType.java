package net.runelite.client.plugins.microbot.autobankstander.processors;

public enum SkillType {
    MAGIC("Magic"),
    HERBLORE("Herblore");

    private final String displayName;

    SkillType(String displayName) {
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