package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SpecialAttackScript extends Script {

    public boolean run(QoLConfig config) {
        AtomicReference<Rs2NpcModel> npc = new AtomicReference<>();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useSpecWeapon()) return;
                if (Rs2Equipment.all("guthan's").count() == 4) return;
                if (Rs2Player.isInteracting()) {
                    var interacting = Rs2Player.getInteracting();
                    if (interacting instanceof net.runelite.api.NPC) {
                        var targetNpc = Microbot.getRs2NpcCache().query().where(n -> n.getNpc().equals(interacting)).nearest();
                        npc.set(targetNpc);
                    }
                    if (npc.get() != null && Microbot.getSpecialAttackConfigs().useSpecWeapon()) {
                        npc.get().click("Attack");
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }

}