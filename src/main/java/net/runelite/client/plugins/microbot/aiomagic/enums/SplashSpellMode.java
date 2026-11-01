package net.runelite.client.plugins.microbot.aiomagic.enums;

public enum SplashSpellMode {
    /** Pick the best standard-book combat spell for the current Magic level and runes, and upgrade on level-up. */
    AUTO("Auto"),
    /** Always use the spell chosen in the "Combat Spell" setting. */
    CONFIGURED("Combat Spell setting");

    private final String label;

    SplashSpellMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
