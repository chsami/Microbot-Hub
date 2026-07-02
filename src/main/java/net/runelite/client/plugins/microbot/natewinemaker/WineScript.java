package net.runelite.client.plugins.microbot.natewinemaker;

import net.runelite.api.Skill;
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

    private static final int MAX_XP = 13_034_431; // 99 Cooking
    private static final int WINE_XP = 200; // xp per jug of wine
    private static final int BATCH_SIZE = 14; // wines per inventory batch

    // Cooking xp / wall clock at session start; -1 until the first logged-in tick.
    // Static fields leak across plugin restarts, so run() resets them.
    private static int startXp = -1;
    private static long startTimeMillis = -1;

    private WineConfig config;

    /**
     * Wines made this session, derived from cooking xp gained (200 xp each).
     * Safe to call from the overlay (client thread).
     */
    public static int getWinesMade() {
        if (startXp < 0 || !Microbot.isLoggedIn()) return 0;
        return Math.max(0, (Microbot.getClient().getSkillExperience(Skill.COOKING) - startXp) / WINE_XP);
    }

    /** Cooking xp remaining to 99, or 0 when maxed. */
    public static int getXpToMax() {
        if (!Microbot.isLoggedIn()) return 0;
        return Math.max(0, MAX_XP - Microbot.getClient().getSkillExperience(Skill.COOKING));
    }

    /** Wines remaining to 99 (rounded up), or 0 when maxed. */
    public static int getWinesToMax() {
        return (getXpToMax() + WINE_XP - 1) / WINE_XP;
    }

    /**
     * Estimated time to 99 based on this session's xp rate (includes breaks and
     * cooldowns, which is what makes the projection honest). "-" until there is
     * enough data to project from.
     */
    public static String getTimeToMax() {
        if (startXp < 0 || !Microbot.isLoggedIn()) return "-";
        int xpToMax = getXpToMax();
        if (xpToMax == 0) return "Maxed!";
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        int xpGained = Microbot.getClient().getSkillExperience(Skill.COOKING) - startXp;
        if (xpGained <= 0 || elapsed < 60_000) return "-"; // need a minute of data
        long msLeft = (long) (xpToMax / ((double) xpGained / elapsed));
        long totalMinutes = msLeft / 60_000;
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    public boolean run(WineConfig config) {
        this.config = config;
        startXp = -1;
        startTimeMillis = -1;
        // Apply the cooking template as a baseline, then overlay the user's saved
        // antiban panel settings so anything toggled there wins over the template.
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.loadFromProfile();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
				if (!super.run()) return;
				if (!Microbot.isLoggedIn()) return;
                if (startXp < 0) {
                    startXp = Microbot.getClient().getSkillExperience(Skill.COOKING);
                    startTimeMillis = System.currentTimeMillis();
                }
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (Rs2AntibanSettings.microBreakActive) return;
                if (config.stopBeforeMax() && isMaxWithinOneBatch()) {
                    logoutAndStop("99 Cooking is within one batch - the final wines are yours");
                    return;
                }
                if (Rs2Inventory.count("grapes") > 0 && (Rs2Inventory.count("jug of water") > 0)) {
                    Rs2Inventory.combine("jug of water", "grapes");
                    sleepUntil(() -> Rs2Widget.getWidget(17694734) != null);
                    Rs2Keyboard.keyPress('1');
                    // Trigger the cooldown (incl. mouse off screen / random moves) as soon as
                    // the batch starts crafting, like a human looking away mid-batch, rather
                    // than after the last wine finishes.
                    Rs2Inventory.waitForInventoryChanges(3000);
                    Rs2Antiban.actionCooldown();
                    if (Rs2AntibanSettings.takeMicroBreaks) {
                        Rs2Antiban.takeMicroBreakByChance();
                    }
                    sleepUntil(() -> !Rs2Inventory.hasItem("jug of water"),25000);
                } else {
                    bank();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * True once 99 Cooking is reachable within a single 14-wine batch, i.e. the
     * script should stop and leave the final batch for the user to craft.
     */
    private boolean isMaxWithinOneBatch() {
        int xp = Microbot.getClient().getSkillExperience(Skill.COOKING);
        return xp >= MAX_XP - BATCH_SIZE * WINE_XP;
    }

    private void bank(){
        Rs2Bank.openBank();
        if(Rs2Bank.isOpen()){
            Rs2Bank.depositAll();
            int jugsInBank = Rs2Bank.count("jug of water");
            int grapesInBank = Rs2Bank.count("grapes");
            if (jugsInBank > 0 && grapesInBank > 0) {
                // Withdraw up to 14 of each, or whatever is left for a final partial batch
                int amount = Math.min(BATCH_SIZE, Math.min(jugsInBank, grapesInBank));
                Rs2Bank.withdrawDeficit("jug of water", amount);
                sleepUntil(() -> Rs2Inventory.hasItem("jug of water"));
                Rs2Bank.withdrawDeficit("grapes", amount);
                sleepUntil(() -> Rs2Inventory.hasItem("grapes"));
            } else {
                // Out of grapes or jugs of water: log out and stop the plugin
                Microbot.getNotifier().notify("Run out of Materials");
                logoutAndStop("out of grapes or jugs of water");
                return;
            }
        }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    private void logoutAndStop(String reason) {
        Microbot.status = "[Shutting down] - Reason: " + reason + ".";
        Microbot.getNotifier().notify("Wine Maker stopping: " + reason);
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }
        Rs2Player.logout();
        sleepUntil(() -> !Microbot.isLoggedIn(), 10000);
        Plugin wineMakerPlugin = Microbot.getPluginManager().getPlugins().stream()
                .filter(x -> x.getClass().getName().equals(WinePlugin.class.getName()))
                .findFirst()
                .orElse(null);
        Microbot.stopPlugin(wineMakerPlugin);
        shutdown();
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
