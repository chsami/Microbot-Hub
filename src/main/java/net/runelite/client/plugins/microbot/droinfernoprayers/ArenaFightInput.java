package net.runelite.client.plugins.microbot.droinfernoprayers;

/** The two fight input choices share command logic and cancellation. */
public enum ArenaFightInput
{
    MICROBOT_MOUSE("Natural mouse"),
    INVOKES("Direct invokes");

    private final String label;
    ArenaFightInput(String label) { this.label = label; }
    @Override public String toString() { return label; }

    /** Preserve the user's old private-mouse selection as the remaining mouse option. */
    public static void migrate(net.runelite.client.config.ConfigManager configs, String group) {
        if ("ARENA_MOUSE".equals(configs.getConfiguration(group, "fightInput")))
            configs.setConfiguration(group, "fightInput", MICROBOT_MOUSE);
    }
}
