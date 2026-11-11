package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/**
 * Sends one object option as a single menu click. The Microbot API object model's click first walks to the object
 * once the client has seen "I can't reach that!", which would run a blocking walk inside the action queue;
 * this click never walks.
 */
public final class ObjectClick
{
    private static final MenuAction[] OPTIONS = {MenuAction.GAME_OBJECT_FIRST_OPTION,
        MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION,
        MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION};

    private ObjectClick()
    {
    }

    /** Clicks the object's option by its listed name; false when the option, location or view is missing. */
    public static boolean click(TileObject object, String option)
    {
        ObjectComposition composition = Rs2TileObjectModel.compositionOf(object.getId());
        String[] actions = composition == null ? null : composition.getActions();
        int index = -1;
        for (int candidate = 0; actions != null && index < 0
            && candidate < Math.min(actions.length, OPTIONS.length); candidate++)
        {
            if (actions[candidate] != null && option.equalsIgnoreCase(Rs2UiHelper.stripColTags(actions[candidate])))
            {
                index = candidate;
            }
        }
        LocalPoint location = object.getLocalLocation();
        WorldView view = object.getWorldView();
        if (index < 0 || location == null || view == null)
        {
            return false;
        }
        int x = location.getSceneX();
        int y = location.getSceneY();
        if (object instanceof GameObject)
        {
            // A larger object's local point is its centre; the menu names its south-west scene tile.
            x -= ((GameObject) object).sizeX() / 2;
            y -= ((GameObject) object).sizeY() / 2;
        }
        if (!Rs2Camera.isTileOnScreen(location))
        {
            Rs2Camera.turnTo(object);
        }
        Microbot.doInvoke(new NewMenuEntry().param0(x).param1(y).opcode(OPTIONS[index].getId())
            .identifier(object.getId()).itemId(-1).option(option).target(composition.getName())
            .setWorldViewId(view.getId()).gameObject(object), Rs2UiHelper.getObjectClickbox(object));
        return true;
    }
}
