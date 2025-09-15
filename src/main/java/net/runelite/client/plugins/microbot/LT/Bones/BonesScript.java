package net.runelite.client.plugins.microbot.LT.Bones;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class BonesScript extends Script {
    public static double version = 1.0;
    private static final int BIG_BONES = ItemID.BIG_BONES;

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // ✅ Si tenemos huesos → enterrar
                if (Rs2Inventory.hasItem(BIG_BONES)) {
                    buryBones();
                    return;
                }

                // ✅ Si no hay huesos → abrir banco
                if (!Rs2Bank.isOpen()) {
                    Rs2Bank.openBank();
                    sleepUntil(Rs2Bank::isOpen, 3000);
                }

                if (Rs2Bank.isOpen()) {
                    // Retirar Big Bones
                    if (!Rs2Bank.hasItem(BIG_BONES)) {
                        Microbot.log("❌ No quedan Big Bones en el banco.");
                        shutdown();
                        return;
                    }

                    Rs2Bank.withdrawAll(BIG_BONES);
                    sleepUntil(() -> Rs2Inventory.hasItem(BIG_BONES), 2000);

                    // ✅ Cerrar banco inmediatamente
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 1500);

                    // Caminar unos tiles fuera del banco
                    WorldPoint me = Rs2Player.getWorldLocation();
                    WorldPoint away = new WorldPoint(me.getX() + 3, me.getY(), me.getPlane());
                    Rs2Walker.walkTo(away);
                    sleep(Rs2Random.between(600, 900));

                    // Enterrar
                    buryBones();
                }

            } catch (Exception ex) {
                Microbot.log("Bones burier error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void buryBones() {
        while (Rs2Inventory.hasItem(BIG_BONES)) {
            Rs2Inventory.interact(BIG_BONES, "Bury");
            sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(1100, 1700));
            sleep(Rs2Random.between(80, 180));
        }
    }
}
