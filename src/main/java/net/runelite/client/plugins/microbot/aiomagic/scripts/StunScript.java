package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class StunScript extends Script {
    private final MagicState state = MagicState.CASTING;
    private final AIOMagicPlugin plugin;

    @Inject
    public StunScript(AIOMagicPlugin plugin) {
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

                switch (state) {
                    case CASTING:
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

                        if (!Rs2Magic.hasRequiredRunes(plugin.getStunSpell().getRs2Spell())) {
                            Microbot.showMessage("Out of runes for " + plugin.getStunSpell().name());
                            shutdown();
                            return;
                        }

                        Rs2NpcModel interactingNpc = null;
                        Object interacting = Rs2Player.getInteracting();
                        if (interacting instanceof Rs2NpcModel) {
                            interactingNpc = (Rs2NpcModel) interacting;
                        }
                        if (interactingNpc != null) {
                            Rs2Magic.castOn(plugin.getStunSpell().getRs2Spell().getMagicAction(), interactingNpc);
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

                        sleep(200, 300);
                        break;
                    case BANKING:
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Stun loop failed: " + ex.getMessage());
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
