package net.runelite.client.plugins.microbot.natewinemaker;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class WineScript extends Script {

    public boolean run(WineConfig config) {
        // Respect the user's antiban micro break setting: the reset + cooking template
        // below would otherwise wipe it, so capture it first and restore it after.
        boolean microBreaksEnabled = Rs2AntibanSettings.takeMicroBreaks;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2AntibanSettings.takeMicroBreaks = microBreaksEnabled;
        Rs2AntibanSettings.moveMouseRandomly = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
				if (!super.run()) return;
				if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (Rs2AntibanSettings.microBreakActive) return;
                if (Rs2Inventory.count("grapes") > 0 && (Rs2Inventory.count("jug of water") > 0)) {
                    Rs2Inventory.combine("jug of water", "grapes");
                    sleepUntil(() -> Rs2Widget.getWidget(17694734) != null);
                    Rs2Keyboard.keyPress('1');
                    sleepUntil(() -> !Rs2Inventory.hasItem("jug of water"),25000);
                    Rs2Antiban.actionCooldown();
                    if (Rs2AntibanSettings.takeMicroBreaks) {
                        Rs2Antiban.takeMicroBreakByChance();
                    }
                } else {
                    bank();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bank(){
        Rs2Bank.openBank();
        if(Rs2Bank.isOpen()){
            Rs2Bank.depositAll();
            int jugsInBank = Rs2Bank.count("jug of water");
            int grapesInBank = Rs2Bank.count("grapes");
            if(jugsInBank > 0 && grapesInBank > 0) {
                // Withdraw up to 14 of each, or whatever is left for a final partial batch
                int amount = Math.min(14, Math.min(jugsInBank, grapesInBank));
                Rs2Bank.withdrawDeficit("jug of water", amount);
                sleepUntil(() -> Rs2Inventory.hasItem("jug of water"));
                Rs2Bank.withdrawDeficit("grapes", amount);
                sleepUntil(() -> Rs2Inventory.hasItem("grapes"));
            } else {
                // Out of grapes or jugs of water: log out and stop the plugin
                Microbot.getNotifier().notify("Run out of Materials");
                Microbot.status = "[Shutting down] - Reason: out of grapes or jugs of water.";
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen());
                Rs2Player.logout();
                sleepUntil(() -> !Microbot.isLoggedIn(), 10000);
                Plugin wineMakerPlugin = Microbot.getPluginManager().getPlugins().stream()
                        .filter(x -> x.getClass().getName().equals(WinePlugin.class.getName()))
                        .findFirst()
                        .orElse(null);
                Microbot.stopPlugin(wineMakerPlugin);
                shutdown();
                return;
            }
        }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
