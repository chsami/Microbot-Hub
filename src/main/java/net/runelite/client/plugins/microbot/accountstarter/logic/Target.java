package net.runelite.client.plugins.microbot.accountstarter.logic;

public enum Target {
    OFF(0, "Off"),
    LEVEL_5(5, "Level 5"),
    LEVEL_10(10, "Level 10"),
    LEVEL_20(20, "Level 20");

    private final int level;
    private final String text;

    Target(int level, String text) {
        this.level = level;
        this.text = text;
    }

    public int level() { return level; }

    @Override
    public String toString() { return text; }
}
