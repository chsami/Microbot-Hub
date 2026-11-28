package net.runelite.client.plugins.microbot.accountstarter.logic;

public enum GatherTarget {
    OFF(0, "Off"),
    LEVEL_5(5, "Level 5");

    private final int level;
    private final String text;

    GatherTarget(int level, String text) {
        this.level = level;
        this.text = text;
    }

    public int level() { return level; }

    @Override
    public String toString() { return text; }
}
