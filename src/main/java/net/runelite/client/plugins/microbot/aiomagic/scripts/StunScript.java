package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class StunScript extends Script {
    private MagicState state = MagicState.CASTING;
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
                        if (!Rs2Magic.hasRequiredRunes(plugin.getStunSpell().getRs2Spell())) {
                            Microbot.showMessage("Out of runes for " + plugin.getStunSpell().name());
                            shutdown();
                            return;
                        }

                        Rs2NpcModel interactingNpc = (Rs2NpcModel) Rs2Player.getInteracting();
                        if (interactingNpc != null) {
                            Rs2Magic.castOn(plugin.getStunSpell().getRs2Spell().getMagicAction(), interactingNpc);
                        } else {
                            Rs2NpcModel configuredNpc = Rs2Npc.getNpc(plugin.getStunNpcName());
                            if (configuredNpc == null) {
                                Microbot.log("Unable to find NPC: " + plugin.getStunNpcName());
                                return;
                            }
                            Rs2Magic.castOn(plugin.getStunSpell().getRs2Spell().getMagicAction(), configuredNpc);
                        }

                        sleep(200, 300);
                        break;
                    case BANKING:
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
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
