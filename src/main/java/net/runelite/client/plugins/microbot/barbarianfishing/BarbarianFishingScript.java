package net.runelite.client.plugins.microbot.barbarianfishing;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.barbarianfishing.BarbarianFishingConfig;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;

public class BarbarianFishingScript extends Script {
    private long specReadyTime = 0;
    private boolean specActivated = false;
    public static String version = "1.1.4";
    public static int timeout = 0;
    private BarbarianFishingConfig config;

    private long actionCooldownStartTime = 0;
    private int lastFishingXp = -1;
    private long interactingSince = 0;
    private int lastInventoryCount = -1;
    private static final long ACTION_COOLDOWN_MAX_MS = 30_000;
    private static final long INTERACTING_STALE_MS = 15_000;

    public String status = "";

    public boolean run(BarbarianFishingConfig config) {
        this.config = config;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }

            if (!Rs2Inventory.hasItem("feather")) {
                status = "No feathers left";
                Microbot.log("Barbarian Fisher: No feathers left in inventory.");
                return;
            }
            if (!Rs2Inventory.hasItem("rod")) {
                status = "No barbarian rod";
                Microbot.log("Barbarian Fisher: No barbarian rod in inventory.");
                return;
            }

            if (Rs2Equipment.isWearing(ItemID.DRAGON_HARPOON)) {
                if (Rs2Combat.getSpecEnergy() == 1000) {
                    if (specReadyTime == 0) {
                        double delay = Rs2Random.gaussRand(45000, 30000);
                        specReadyTime = System.currentTimeMillis() + (long) delay;
                    } else if (!specActivated && System.currentTimeMillis() >= specReadyTime) {
                        Rs2Combat.setSpecState(true);
                        specActivated = true;
                    }
                } else {
                    specReadyTime = 0;
                    specActivated = false;
                }
            }

            if (Rs2AntibanSettings.actionCooldownActive) {
                if (actionCooldownStartTime == 0) {
                    actionCooldownStartTime = System.currentTimeMillis();
                    lastFishingXp = Microbot.getClientThread().invoke(() ->
                            Microbot.getClient().getSkillExperience(Skill.FISHING));
                } else {
                    int currentFishingXp = Microbot.getClientThread().invoke(() ->
                            Microbot.getClient().getSkillExperience(Skill.FISHING));
                    if (currentFishingXp > lastFishingXp) {
                        lastFishingXp = currentFishingXp;
                        actionCooldownStartTime = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - actionCooldownStartTime > ACTION_COOLDOWN_MAX_MS) {
                        Microbot.log("Barbarian Fisher: Action cooldown stuck for >30s with no XP drop, resetting.");
                        Rs2AntibanSettings.actionCooldownActive = false;
                        Rs2Antiban.TIMEOUT = 0;
                        actionCooldownStartTime = 0;
                        lastFishingXp = -1;
                    }
                }
                status = "Antiban cooldown";
                return;
            } else {
                actionCooldownStartTime = 0;
                lastFishingXp = -1;
            }

            if (Rs2Player.isInteracting()) {
                if (interactingSince == 0) {
                    interactingSince = System.currentTimeMillis();
                    lastInventoryCount = Rs2Inventory.size();
                } else {
                    long interactDuration = System.currentTimeMillis() - interactingSince;
                    int currentInventoryCount = Rs2Inventory.size();
                    boolean inventoryChanged = currentInventoryCount != lastInventoryCount;
                    if (inventoryChanged) {
                        interactingSince = System.currentTimeMillis();
                        lastInventoryCount = currentInventoryCount;
                    } else if (interactDuration > INTERACTING_STALE_MS) {
                        Microbot.log("Barbarian Fisher: Interacting state stuck for >15s with no inventory change, resetting.");
                        interactingSince = 0;
                    }
                }
                status = "Fishing";
                return;
            } else {
                interactingSince = 0;
            }

            if (Rs2Inventory.isFull()) {
                status = "Dropping fish";
                dropInventoryItems(config);
                return;
            }

            Rs2NpcModel fishingspot = findFishingSpot();
            if (fishingspot == null) {
                status = "No fishing spot found";
                return;
            }

            if (!Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())) {
                validateInteractable(fishingspot.getNpc());
            }

            if (fishingspot.click("Use-rod")) {
                status = "Clicked fishing spot";
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }

        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void onGameTick() {

    }

    private Rs2NpcModel findFishingSpot() {
        return Microbot.getRs2NpcCache().query()
                .withIds(FishingSpot.BARB_FISH.getIds())
                .nearest();
    }

    private void dropInventoryItems(BarbarianFishingConfig config) {
        InteractOrder dropOrder = config.dropOrder() == InteractOrder.RANDOM ? InteractOrder.random() : config.dropOrder();
        Rs2Inventory.dropAll(x -> x.getName().toLowerCase().contains("leaping"), dropOrder);
    }

    public void shutdown() {
        actionCooldownStartTime = 0;
        lastFishingXp = -1;
        interactingSince = 0;
        lastInventoryCount = -1;
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
