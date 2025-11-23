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

import java.util.Comparator;
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
                            // Exit through portal - find nearest portal
                            Microbot.status = "Looking for nearest portal (attempt " + (portalSearchAttempts + 1) + ")";

                            // Find the NEAREST portal (there are multiple in the mine)
                            // Search with large distance to find all portals, returns nearest
                            GameObject portal = Rs2GameObject.getGameObject("Portal", 50);

                            if (portal == null) {
                                log.info("No portals found, adjusting camera and rotating...");

                                // Zoom out and look down from above for better visibility
                                Rs2Camera.setZoom(450);
                                Rs2Camera.setPitch(383); // Max pitch - looking straight down
                                sleep(600, 800);

                                // Rotate through 3 cardinal directions (270 degrees total, not full 360)
                                int[] cameraAngles = {0, 512, 1024}; // North, East, South
                                for (int angle : cameraAngles) {
                                    Rs2Camera.setYaw(angle);
                                    sleep(400, 600);

                                    // Search for nearest portal after EACH rotation
                                    portal = Rs2GameObject.getGameObject("Portal", 50);
                                    if (portal != null) {
                                        log.info("Found portal at distance " + portal.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) + " after rotating to angle: " + angle);
                                        break;
                                    }
                                }
                            } else {
                                log.info("Found portal at distance: " + portal.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()));
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

                                // Re-search for portal to ensure fresh object reference
                                GameObject freshPortal = Rs2GameObject.getGameObject("Portal", 50);
                                if (freshPortal == null) {
                                    log.warn("Portal was found but disappeared, retrying next cycle");
                                    return;
                                }

                                // Turn camera to portal if it's not on screen
                                if (!Rs2Camera.isTileOnScreen(freshPortal)) {
                                    Microbot.status = "Turning camera to portal";
                                    Rs2Camera.turnTo(freshPortal);
                                    sleep(600, 900);
                                }

                                Microbot.status = "Using portal to exit";
                                log.info("Attempting to interact with portal at: " + freshPortal.getWorldLocation());

                                boolean interacted = Rs2GameObject.interact(freshPortal);
                                if (interacted) {
                                    log.info("Successfully clicked portal, waiting for teleport");
                                    sleep(3000, 4000); // Wait for teleport
                                } else {
                                    log.warn("Failed to interact with portal, will retry");
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
