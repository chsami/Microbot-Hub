package net.runelite.client.plugins.microbot.mke_wintertodt.enums;

/**
 * Healing method options for Wintertodt bot
 */
public enum HealingMethod {
    POTIONS("🥄 Potions (Recommended)", "FREE - crafted automatically inside Wintertodt"),
    FOOD("🍖 Food", "Costs GP - withdrawn from bank automatically");

    private final String displayName;
    private final String description;

    HealingMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
} 