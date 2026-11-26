package net.runelite.client.plugins.microbot.accountstarter.logic;

public enum Preset {
    EVERYTHING("Everything"),
    SKILLER("Skiller (gathering only)"),
    COMBAT_ONLY("Combat only"),
    CUSTOM("Choose skills");

    private final String text;

    Preset(String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
