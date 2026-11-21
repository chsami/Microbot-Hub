package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

/** Identify the hut geographically, then use the live object's actual Pick-lock action. */
final class MagicAxesDoorAction {
    private MagicAxesDoorAction() { }

    static boolean isHutDoor(WorldPoint point) {
        return point != null && point.getPlane() == 0
            && point.getX() >= 3185 && point.getX() <= 3197
            && point.getY() >= 3956 && point.getY() <= 3970;
    }

    static String accessProblem(boolean lockpick, int thieving) {
        if (!lockpick) return "Magic axe hut requires a carried lockpick";
        if (thieving < 23) return "Magic axe hut requires 23 Thieving";
        return null;
    }

    static NewMenuEntry pickLock(NewMenuEntry original, String[] actions) {
        if (actions == null) return null;
        MenuAction[] options = {MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
            MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
            MenuAction.GAME_OBJECT_FIFTH_OPTION};
        for (int i = 0; i < actions.length && i < options.length; i++) {
            if (!"Pick-lock".equalsIgnoreCase(actions[i])) continue;
            return new NewMenuEntry(original.getParam0(), original.getParam1(), options[i].getId(),
                original.getIdentifier(), original.getItemId(), "Pick-lock", original.getTarget(),
                original.getGameObject(), original.getWorldViewId());
        }
        return null;
    }
}
