package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Release travel input before resolving the named master at the actual interaction time. */
final class DroSlayerMasterInteraction {
    private DroSlayerMasterInteraction() { }

    static boolean request(DroSlayerMaster master, String action) {
        if (!"Assignment".equals(action) && !"Rewards".equals(action)) return false;
        DroSlayerRuntime.cancelWalk();
        if (Rs2Player.isMoving()) return false;
        return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> {
                    if (Rs2Player.isMoving()) return false;
                    if (Rs2Inventory.isItemSelected()) {
                        Rs2Inventory.deselect();
                        return false;
                    }
                    var npc = Microbot.getRs2NpcCache().query().withName(master.getName()).nearestOnClientThread();
                    return npc != null && master.getName().equalsIgnoreCase(npc.getName()) && npc.click(action);
                });
    }
}
