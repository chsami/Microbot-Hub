package net.runelite.client.plugins.microbot.accountstarter.logic;

import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

public final class FocusPlan {
    private FocusPlan() {
    }

    /** pathFocus is varbit 9324 (0 Combat, 1 Gatherer, 2047 none); the task varbits hold the task's focusIndex. */
    public static boolean needed(ApTask t, int pathFocus, int combatFocus, int gatherFocus) {
        boolean combat = t.path() == ApPath.COMBAT;
        return pathFocus != (combat ? 0 : 1) || (combat ? combatFocus : gatherFocus) != t.focusIndex();
    }

    /** Dynamic child of 642:7 carrying "Show task: <name>": 11, 23, 35, ... one row per 12 children. */
    public static int rowIndex(ApTask t) {
        return 11 + 12 * (t.focusIndex() - 1);
    }

    public static String selectAction(ApPath p) {
        return p == ApPath.COMBAT ? "Select Combat path" : "Select Gatherer path";
    }
}
