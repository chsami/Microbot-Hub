package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

/** The locked hideout door uses the carried lockpick via its second object action. */
final class PiratesHideoutDoorAction {
    static final int DOOR_ID = 11727;
    static final int LOCKPICK_ID = 1523;

    private PiratesHideoutDoorAction() { }

    static String accessProblem(boolean lockpick, int thieving) {
        if (!lockpick) return "Pirates' Hideout door requires a carried lockpick";
        if (thieving < 39) return "Pirates' Hideout door requires 39 Thieving";
        return null;
    }

    static NewMenuEntry pickLock(NewMenuEntry original) {
        return new NewMenuEntry(original.getParam0(), original.getParam1(),
                MenuAction.GAME_OBJECT_SECOND_OPTION.getId(), original.getIdentifier(),
                original.getItemId(), "Pick-lock", original.getTarget(),
                original.getGameObject(), original.getWorldViewId());
    }
}
