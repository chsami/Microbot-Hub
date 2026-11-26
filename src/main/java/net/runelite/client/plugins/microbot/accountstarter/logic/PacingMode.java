package net.runelite.client.plugins.microbot.accountstarter.logic;

public enum PacingMode {
    SMART("Smart order"),
    WHOLE_SET("Whole set"),
    PATH_BY_PATH("Path by path"),
    MIX_IT_UP("Mix it up");

    private final String text;

    PacingMode(String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
