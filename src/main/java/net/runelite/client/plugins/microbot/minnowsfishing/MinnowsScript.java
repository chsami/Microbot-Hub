package net.runelite.client.plugins.microbot.minnowsfishing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GraphicID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

@Slf4j
public class MinnowsScript extends Script {
    public static final WorldArea MINNOWS_PLATFORM = new WorldArea(new WorldPoint(2607, 3440, 0), 2622 - 2607, 3446 - 3440);
    private static final int FLYING_FISH_GRAPHIC_ID = GraphicID.FLYING_FISH;

    static final int[] MINNOW_SPOT_IDS = {
            NpcID.MINNOW_FISHINGSPOT1,
            NpcID.MINNOW_FISHINGSPOT2,
            NpcID.MINNOW_FISHINGSPOT3,
            NpcID.MINNOW_FISHINGSPOT4
    };

    private Rs2NpcModel fishingspot;
    private int timeout;

    public boolean run() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        Rs2AntibanSettings.profileSwitching = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!MINNOWS_PLATFORM.contains(Rs2Player.getWorldLocation())) {
                    Microbot.showMessage("Not at minnows platform, please go to minnows platform to start the script.");
                    Microbot.status = "NOT AT MINNOWS PLATFORM";
                    sleep(15000);
                    return;
                }
                if (Rs2Player.isMoving()) {
                    Microbot.status = "MOVING";
                    return;
                }
                if (Rs2AntibanSettings.actionCooldownActive) {
                    Actor interacting = Rs2Player.getInteracting();
                    if (interacting != null && interacting.hasSpotAnim(FLYING_FISH_GRAPHIC_ID)) {
                        Microbot.status = "DODGING FLYING FISH";
                        fishingspot = findFishingSpot();
                        if (fishingspot != null) fishingspot.click("Small Net");
                        Rs2Antiban.actionCooldown();
                        return;
                    }
                    Microbot.status = "FISHING";
                    return;
                }

                Microbot.status = "INTERACTING";
                fishingspot = findFishingSpot();
                if (fishingspot != null) fishingspot.click("Small Net");
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();


            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private Rs2NpcModel findFishingSpot() {
        return Microbot.getRs2NpcCache().query()
                .withIds(MINNOW_SPOT_IDS)
                .where(spot -> !spot.hasSpotAnim(FLYING_FISH_GRAPHIC_ID))
                .nearest();
    }

    public void onGameTick() {
    }

    @Override
    public void shutdown() {

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
