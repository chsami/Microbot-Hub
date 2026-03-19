package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;

import java.util.concurrent.TimeUnit;

public class SpecialAttackScript extends Script
{
    public boolean run(QoLConfig config)
    {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useSpecWeapon()) return;

                // Skip if full Guthan's
                if (Rs2Equipment.all("guthan's").count() == 4) return;

                if (Rs2Player.isInteracting())
                {
                    Actor interacting = Rs2Player.getInteracting();

                    // Only proceed if the target is an NPC
                    if (interacting instanceof NPC)
                    {
                        NPC target = (NPC) interacting;

                        if (target != null && Microbot.getSpecialAttackConfigs().useSpecWeapon())
                        {
                            Rs2Npc.attack(target);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }
}