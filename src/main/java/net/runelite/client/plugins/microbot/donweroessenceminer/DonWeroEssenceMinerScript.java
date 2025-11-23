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
                            // Exit through portal
                            Microbot.status = "Looking for portal";

                            // Wait for portal to appear (sometimes takes a moment to load)
                            boolean portalFound = sleepUntil(() -> Rs2GameObject.getGameObject("Portal") != null, 5000);

                            if (!portalFound) {
                                log.warn("Portal not found after waiting");
                                return;
                            }

                            GameObject portal = Rs2GameObject.getGameObject("Portal");
                            if (portal != null) {
                                // Turn camera to portal if it's not on screen
                                if (!Rs2Camera.isTileOnScreen(portal)) {
                                    Microbot.status = "Turning camera to portal";
                                    Rs2Camera.turnTo(portal);
                                    sleep(300, 600); // Brief delay for camera to move
                                }

                                Microbot.status = "Using portal to exit";
                                if (Rs2GameObject.interact(portal)) {
                                    sleep(3000, 4000); // Wait for teleport
                                }
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
