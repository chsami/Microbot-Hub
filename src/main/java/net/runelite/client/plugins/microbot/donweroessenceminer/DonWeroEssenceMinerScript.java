package net.runelite.client.plugins.microbot.donweroessenceminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

enum EssenceMiningState {
    MINING,
    TELEPORT_AND_BANK
}

@Slf4j
public class DonWeroEssenceMinerScript extends Script {

    private static final WorldPoint AUBURY_LOCATION = new WorldPoint(3253, 3399, 0);
    private static final WorldPoint BANK_LOCATION = new WorldPoint(3253, 3420, 0);
    private static final int ESSENCE_MINE_REGION = 11595;

    private EssenceMiningState state = EssenceMiningState.TELEPORT_AND_BANK;
    private int portalSearchAttempts = 0;

    public boolean run(DonWeroEssenceMinerConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                boolean inEssenceMine = Rs2Player.getWorldLocation().getRegionID() == ESSENCE_MINE_REGION;

                switch (state) {
                    case MINING:
                        if (!inEssenceMine) {
                            log.warn("Not in essence mine, switching to TELEPORT_AND_BANK");
                            state = EssenceMiningState.TELEPORT_AND_BANK;
                            return;
                        }

                        if (Rs2Inventory.isFull()) {
                            Microbot.status = "Inventory full, exiting mine";
                            state = EssenceMiningState.TELEPORT_AND_BANK;
                            return;
                        }

                        // Find and mine essence
                        GameObject essenceRock = Rs2GameObject.getGameObject("Rune Essence", false);

                        if (essenceRock != null) {
                            Microbot.status = "Mining essence";
                            if (Rs2GameObject.interact(essenceRock, "Mine")) {
                                Rs2Player.waitForXpDrop(Skill.MINING, true);
                                Rs2Antiban.actionCooldown();
                                Rs2Antiban.takeMicroBreakByChance();
                            }
                        } else {
                            log.warn("No essence rocks found");
                        }
                        break;

                    case TELEPORT_AND_BANK:
                        if (inEssenceMine) {
                            // Exit through portal - enhanced search logic
                            Microbot.status = "Looking for portal (attempt " + (portalSearchAttempts + 1) + ")";

                            GameObject portal = Rs2GameObject.getGameObject("Portal");

                            if (portal == null) {
                                log.info("Portal not immediately visible, trying camera rotations...");

                                // Try rotating camera in different directions to find portal
                                int[] cameraAngles = {0, 512, 1024, 1536}; // North, East, South, West
                                for (int angle : cameraAngles) {
                                    Rs2Camera.setYaw(angle);
                                    sleep(400, 600);
                                    portal = Rs2GameObject.getGameObject("Portal");
                                    if (portal != null) {
                                        log.info("Found portal after rotating camera to angle: " + angle);
                                        break;
                                    }
                                }
                            }

                            // If still not found, try moving around
                            if (portal == null && portalSearchAttempts < 3) {
                                log.warn("Portal not found after camera rotations, trying to walk around...");
                                Microbot.status = "Searching for portal - moving around";

                                // Walk to a random nearby tile to change perspective
                                WorldPoint currentPos = Rs2Player.getWorldLocation();
                                WorldPoint searchPos = new WorldPoint(
                                    currentPos.getX() + (portalSearchAttempts % 2 == 0 ? 3 : -3),
                                    currentPos.getY() + (portalSearchAttempts % 2 == 0 ? 2 : -2),
                                    currentPos.getPlane()
                                );

                                Rs2Walker.walkFastCanvas(searchPos);
                                sleep(1200, 1800);

                                portalSearchAttempts++;
                                return;
                            }

                            // Found the portal!
                            if (portal != null) {
                                portalSearchAttempts = 0; // Reset counter

                                // Turn camera to portal if it's not on screen
                                if (!Rs2Camera.isTileOnScreen(portal)) {
                                    Microbot.status = "Turning camera to portal";
                                    Rs2Camera.turnTo(portal);
                                    sleep(300, 600);
                                }

                                Microbot.status = "Using portal to exit";
                                log.info("Interacting with portal at: " + portal.getWorldLocation());
                                if (Rs2GameObject.interact(portal)) {
                                    sleep(3000, 4000); // Wait for teleport
                                }
                            } else {
                                log.error("Portal not found after " + portalSearchAttempts + " search attempts!");
                                portalSearchAttempts = 0; // Reset to try again
                            }
                            return;
                        }

                        // Bank if inventory has items
                        if (!Rs2Inventory.isEmpty()) {
                            Microbot.status = "Banking essence";
                            if (Rs2Bank.walkToBankAndUseBank()) {
                                sleep(600, 1200);
                                Rs2Bank.depositAll();
                                sleep(600, 1200);
                            }
                            return;
                        }

                        // Walk to Aubury if not nearby
                        int distanceToAubury = Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION);
                        if (distanceToAubury > 8) {
                            Microbot.status = "Walking to Aubury";
                            Rs2Walker.walkTo(AUBURY_LOCATION, 3);
                            return;
                        }

                        // Teleport with Aubury
                        if (Rs2Npc.interact("Aubury", "Teleport")) {
                            Microbot.status = "Teleporting to essence mine";
                            sleep(3000, 4000); // Wait for teleport
                            state = EssenceMiningState.MINING;
                        }
                        break;
                }
            } catch (Exception ex) {
                log.error("Error in Don Wero's Essence Miner", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
