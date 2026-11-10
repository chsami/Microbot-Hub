package net.runelite.client.plugins.microbot.droslayer;

import java.awt.Rectangle;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/** Cannon reclaim must not borrow the generic NPC clicker's native-walker recovery. */
final class DroSlayerCannonNpcApproach {
    private DroSlayerCannonNpcApproach() { }

    static boolean talk(DroSlayerConfig config, Rs2NpcModel model) {
        if (model == null) return false;
        Approach approach = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var player = Microbot.getClient().getLocalPlayer();
            WorldPoint destination = model.getWorldLocation();
            if (player == null || destination == null) return null;
            WorldPoint here = player.getWorldLocation();
            boolean ready = here != null && here.getPlane() == destination.getPlane()
                && here.distanceTo(destination) <= 2 && model.hasLineOfSight();
            return new Approach(destination, ready);
        }).orElse(null);
        if (approach == null) return false;
        if (!approach.ready) {
            DroSlayerRuntime.walkAdaptive(config, approach.destination, 1, "Nulodion cannon reclaim NPC approach");
            return false;
        }
        DroSlayerRuntime.cancelWalk();
        // Turning is separate from input dispatch; the next tick obtains a fresh clickbox.
        boolean visible = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Rectangle bounds = Rs2UiHelper.getActorClickbox(model.getNpc());
            return bounds != null && bounds.width > 1 && bounds.height > 1;
        }).orElse(false);
        if (!visible) {
            Rs2Camera.turnTo(model.getNpc());
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPC npc = model.getNpc();
            if (npc == null || !model.hasLineOfSight() || Microbot.getClient().isWidgetSelected()) return false;
            NPCComposition composition = npc.getTransformedComposition();
            if (composition == null) return false;
            String[] actions = composition.getActions();
            int index = -1;
            if (actions != null) for (int i = 0; i < actions.length && i < 5; i++)
                if ("Talk-to".equalsIgnoreCase(actions[i])) { index = i; break; }
            if (index < 0) return false;
            Rectangle bounds = Rs2UiHelper.getActorClickbox(npc);
            if (bounds == null || bounds.width <= 1 || bounds.height <= 1) return false;
            MenuAction[] options = {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
                MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION};
            Microbot.doInvoke(new NewMenuEntry().param0(0).param1(0).opcode(options[index].getId())
                .identifier(npc.getIndex()).itemId(-1).target(npc.getName()).actor(npc).option("Talk-to"), bounds);
            return true;
        }).orElse(false);
    }

    private static final class Approach {
        final WorldPoint destination;
        final boolean ready;
        Approach(WorldPoint destination, boolean ready) { this.destination = destination; this.ready = ready; }
    }
}
