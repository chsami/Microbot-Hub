package net.runelite.client.plugins.microbot.droefficientwalker.support;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/** Dispatches a transport action without handing recovery movement to the stock walker. */
public final class TransportObjectClick {
    private TransportObjectClick() {}

    public static boolean click(TileObject target, String action) {
        // On the Hub client a cached model's own click does not keep the multi-tile anchor, so the
        // raw object is dispatched here exactly as an uncached one.
        TileObject object = target instanceof net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel
                ? ((net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel) target).getTileObject()
                : target;
        if (object == null || Microbot.getClient() == null) return false;
        if (!Rs2Camera.isTileOnScreen(object.getLocalLocation())) Rs2Camera.turnTo(object);
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            LocalPoint local = object.getLocalLocation();
            if (player == null || local == null || !local.isInScene()) return false;
            LocalPoint from = player.getLocalLocation();
            if (Math.max(Math.abs(from.getSceneX() - local.getSceneX()),
                    Math.abs(from.getSceneY() - local.getSceneY())) > 51) return false;
            ObjectComposition composition = SceneObjects.convertToObjectComposition(target);
            if (composition == null) return false;
            String[] actions = composition.getActions();
            int index = -1;
            if (actions != null) for (int i = 0; i < actions.length && i < 5; i++) {
                if (actions[i] != null && action.equalsIgnoreCase(Rs2UiHelper.stripColTags(actions[i]))) {
                    index = i; break;
                }
            }
            if (index < 0) return false;
            MenuAction[] options = {MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
                    MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
                    MenuAction.GAME_OBJECT_FIFTH_OPTION};
            int x = local.getSceneX(), y = local.getSceneY();
            if (object instanceof GameObject) {
                GameObject gameObject = (GameObject) object;
                x -= gameObject.sizeX() > 1 ? gameObject.sizeX() / 2 : 0;
                y -= gameObject.sizeY() > 1 ? gameObject.sizeY() / 2 : 0;
            }
            Microbot.doInvoke(new NewMenuEntry().param0(x).param1(y).opcode(options[index].getId())
                    .identifier(object.getId()).itemId(-1).option(action).target(composition.getName())
                    .gameObject(object).worldViewId(object.getWorldView().isTopLevel()
                            ? WorldView.TOPLEVEL : object.getWorldView().getId()), Rs2UiHelper.getObjectClickbox(object));
            return true;
        }).orElse(false);
    }
}
