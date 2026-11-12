package net.runelite.client.plugins.microbot.drocolosseumprayers;

public enum ArenaFightInput
{
    MICROBOT_MOUSE("Natural mouse"),
    INVOKES("Direct invokes");

    private final String label;
    ArenaFightInput(String label) { this.label = label; }
    @Override public String toString() { return label; }

}
