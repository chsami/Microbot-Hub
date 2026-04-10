package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class PVirewatchScript extends Script {

    public boolean run(PVirewatchKillerConfig config, PVirewatchKillerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Combat.enableAutoRetialiate();

                if(plugin.fightArea.contains(Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation()))) {
                    Microbot.status = "Figthing";
                }

                if(Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation()) != plugin.startingLocation) {
                    if(plugin.ticksOutOfArea > config.tickToReturn() || plugin.countedTicks > config.tickToReturnCombat()) {
                        Rs2Walker.walkTo(plugin.startingLocation, 0);
                    }
                }

                Rs2Player.eatAt(config.hitpoints());

                if(Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) <= config.prayAt()) {
                    plugin.rechargingPrayer = true;
                    var statue = Microbot.getRs2TileObjectCache().query().withId(39234).nearest();
                    if(statue != null) {
                        Rs2Walker.walkTo(statue.getWorldLocation(), 1);
                        sleepUntil(statue::isReachable);
                        if(statue.isReachable()) {
                            Microbot.status = "RECHARGING PRAYER";
                            statue.click();
                            sleep(100);
                            plugin.rechargingPrayer = false;
                            if(Rs2Player.isInteracting()) {
                                sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > config.prayAt());
                                Microbot.status = "WALKING TO STARTING POINT";
                                Rs2Walker.walkTo(plugin.startingLocation, 0);

                            }
                        }
                    }

                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
