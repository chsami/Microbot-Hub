package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class StunAlchScript extends Script {

    private final MagicState state = MagicState.CASTING;
    private final AIOMagicPlugin plugin;

    @Inject
    public StunAlchScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.TELEPORT_TRAINING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();


                if (state != MagicState.CASTING) {
                    return;
                }

                if (plugin.getStunSpell() == null) {
                    Microbot.showMessage("Set a stun spell in config");
                    shutdown();
                    return;
                }

                String targetNpcName = plugin.getStunNpcName() == null ? "" : plugin.getStunNpcName().trim();
                if (targetNpcName.isEmpty()) {
                    Microbot.showMessage("Set a stun NPC name in config");
                    shutdown();
                    return;
                }

                if (plugin.getAlchItemNames().isEmpty() || plugin.getAlchItemNames().stream().noneMatch(Rs2Inventory::hasItem)) {
                    Microbot.log("Missing alch items...");
                    return;
                }

                if (!Rs2Magic.hasRequiredRunes(plugin.getAlchSpell())) {
                    Microbot.showMessage("Out of runes for alchemy");
                    shutdown();
                    return;
                }

                if (!Rs2Magic.hasRequiredRunes(plugin.getStunSpell().getRs2Spell())) {
                    Microbot.showMessage("Out of runes for " + plugin.getStunSpell().name());
                    shutdown();
                    return;
                }

                Rs2ItemModel alchItem = plugin.getAlchItemNames().stream()
                        .filter(Rs2Inventory::hasItem)
                        .map(Rs2Inventory::get)
                        .findFirst()
                        .orElse(null);

                if (alchItem == null) {
                    Microbot.log("Missing alch items...");
                    return;
                }

                if (Rs2AntibanSettings.naturalMouse) {
                    int inventorySlot = Rs2Player.getSkillRequirement(Skill.MAGIC, 55) ? 12 : 4;
                    if (alchItem.getSlot() != inventorySlot) {
                        Rs2Inventory.moveItemToSlot(alchItem, inventorySlot);
                        return;
                    }
                }

                Rs2NpcModel npc = null;
                Object interacting = Rs2Player.getInteracting();
                if (interacting instanceof Rs2NpcModel) {
                    npc = (Rs2NpcModel) interacting;
                }

                if (npc != null) {
                    Rs2Magic.castOn(plugin.getStunSpell().getRs2Spell().getMagicAction(), npc);
                } else {
                    var configuredNpc = Microbot.getRs2NpcCache().query()
                            .withName(targetNpcName)
                            .nearest();
                    if (configuredNpc == null) {
                        Microbot.log("Unable to find NPC: " + targetNpcName);
                        return;
                    }
                    if (!Rs2Magic.cast(plugin.getStunSpell().getRs2Spell().getMagicAction())) {
                        Microbot.log("Unable to select stun spell: " + plugin.getStunSpell().name());
                        return;
                    }
                    if (!sleepUntil(() -> Microbot.getClient().isWidgetSelected(), 800)) {
                        Microbot.log("Stun spell was not selected in time");
                        return;
                    }
                    if (!configuredNpc.click()) {
                        Microbot.log("Unable to cast stun on NPC: " + targetNpcName);
                        return;
                    }
                }

                if (Rs2AntibanSettings.naturalMouse) {
                    Rs2Magic.alch(alchItem, 10, 50);
                } else {
                    Rs2Magic.alch(alchItem);
                    sleep(200, 300);
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.log("Stun-Alch loop failed: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
