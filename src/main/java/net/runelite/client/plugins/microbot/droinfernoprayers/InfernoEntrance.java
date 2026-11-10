package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;

/** Fixed local entrance with a loaded-scene receipt before the off-thread input submission. */
final class InfernoEntrance {
    private InfernoEntrance() { }
    static boolean jump(BooleanSupplier allowed) {
        if (!allowed.getAsBoolean() || Thread.currentThread().isInterrupted()
            || Microbot.getClientThread().isClientThread()) return false;
        var entrance=Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client=Microbot.getClient();
            if (!allowed.getAsBoolean() || client==null || client.getGameState()!=GameState.LOGGED_IN
                || client.getLocalPlayer()==null || client.getTopLevelWorldView()==null
                || client.getTopLevelWorldView().isInstance() || client.isWidgetSelected()
                || client.getBoostedSkillLevel(Skill.HITPOINTS)<=0) return null;
            var position=client.getLocalPlayer().getWorldLocation();
            if (position==null || position.distanceTo(InfernoApproach.KET_KEH)>12) return null;
            var candidate=Microbot.getRs2TileObjectCache().query().where(o -> {
                if (o.getId()!=ObjectID.INFERNO_ENTRANCE_NOOP && o.getId()!=ObjectID.INFERNO_ENTRANCE_OP && o.getId()!=30352) return false;
                var composition=o.getObjectComposition();
                return o.getWorldView()==client.getTopLevelWorldView() && composition!=null && composition.getId()==ObjectID.INFERNO_ENTRANCE_OP
                    && "The Inferno".equals(composition.getName())
                    && composition.getActions()!=null && Arrays.asList(composition.getActions()).contains("Jump-in")
                    && o.getWorldLocation()!=null && o.getWorldLocation().distanceTo(position)<=24;
            }).nearestOnClientThread();
            if (candidate==null || candidate.getLocalLocation()==null
                || !Rs2Camera.isTileOnScreen(candidate.getLocalLocation()) || !allowed.getAsBoolean()) return null;
            return candidate;
        }).orElse(null);
        // Object models may turn the camera or dispatch the mouse; never perform that on the client thread.
        return entrance!=null && allowed.getAsBoolean() && entrance.click("Jump-in");
    }
}
