package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Map;

public final class AttackerRules {
    /** Name to the highest combat level that hits 2 or less; the level 20 Dark wizard hits harder. */
    static final Map<String, Integer> WEAK = Map.of("Giant rat", 3, "Giant spider", 2, "Mugger", 6, "Dark wizard", 7,
            "Big frog", 10);

    private AttackerRules() {}

    public static boolean fightBack(String name, int combatLevel) {
        Integer max = name == null ? null : WEAK.get(name);
        return max != null && combatLevel > 0 && combatLevel <= max;
    }
}
