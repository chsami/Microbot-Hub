package net.runelite.client.plugins.microbot.looter.scripts;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.looter.AutoLooterConfig;
import net.runelite.client.plugins.microbot.looter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.looter.enums.LooterState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DefaultScript extends Script {

    LooterState state = LooterState.LOOTING;
    boolean lootExists;
    int failedLootAttempts = 0;

    public boolean run(AutoLooterConfig config) {
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn() || Rs2Combat.inCombat()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (config.priorityMode()) {
                    handlePriorityLoot(config);
                    return;
                }

                long startTime = System.currentTimeMillis();

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                switch (state) {
                    case LOOTING:
                        // Walker may still be carrying us back (post-bank or post-stray).
                        // Scanning from the wrong tile would trip the world-hop counter.
                        if (isAwayFromBase(config)) return;

                        if (config.worldHop()) {
                            lootExists = hasMatchingLoot(config);
                        } else {
                            lootExists = true;
                        }

                        if (lootExists) {
                            failedLootAttempts = 0;
                            lootItems(config);

                            Microbot.pauseAllScripts.set(false);
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();
                        } else {
                            failedLootAttempts++;

                            if (failedLootAttempts >= 5) {
                                Microbot.log("Failed to find loot 5 times, hopping worlds...");

                                if (Rs2Bank.isOpen()) {
                                    Microbot.log("Bank is open, closing before hopping...");
                                    Rs2Bank.closeBank();
                                    sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                                }

                                int worldNumber = config.useNextWorld()
                                        ? Login.getNextWorld(Rs2Player.isMember())
                                        : Login.getRandomWorld(Rs2Player.isMember());

                                Microbot.hopToWorld(worldNumber);
                                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);

                                failedLootAttempts = 0;
                                return;
                            }
                        }

                        if (Rs2Inventory.emptySlotCount() <= config.minFreeSlots()) {
                            state = LooterState.BANKING;
                            return;
                        }
                        break;

                    case BANKING:
                        // Stay in BANKING until the bank trip is fully complete: items deposited
                        // AND we're back at the loot spot. Flipping to LOOTING mid-walk-back
                        // would let the loot scan run from the bank tile.
                        if (Rs2Inventory.emptySlotCount() <= config.minFreeSlots()) return;
                        if (isAwayFromBase(config)) return;
                        failedLootAttempts = 0;
                        state = LooterState.LOOTING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.log("Error in DefaultScript: " + ex.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Microbot.pauseAllScripts.set(false);
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public boolean handleWalk(AutoLooterConfig config) {
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (Microbot.pauseAllScripts.get()) return;
                if (initialPlayerLocation == null) return;

                if (state == LooterState.LOOTING) {
                    if (Rs2Player.getWorldLocation().distanceTo(initialPlayerLocation) > config.distanceToStray()) {
                        Rs2Walker.walkTo(initialPlayerLocation);
                    }
                    return;
                }

                if (state == LooterState.BANKING) {
                    if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST) {
                        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                                Arrays.stream(config.listOfItemsToLoot().trim().split(",")).collect(Collectors.toList()),
                                initialPlayerLocation,
                                config.minFreeSlots());
                    } else {
                        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                                Rs2Inventory.all().stream().map(Rs2ItemModel::getName).collect(Collectors.toList()),
                                initialPlayerLocation,
                                config.minFreeSlots());
                    }
                }
            } catch (Exception ex) {
                Microbot.log("Error in handleWalk: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePriorityLoot(AutoLooterConfig config) {
        if (!hasMatchingLoot(config)) return;
        if (Rs2Inventory.emptySlotCount() <= config.minFreeSlots()) return;

        Microbot.pauseAllScripts.set(true);
        try {
            while (hasMatchingLoot(config)
                    && Rs2Inventory.emptySlotCount() > config.minFreeSlots()
                    && this.isRunning()) {
                lootItems(config);
            }
        } finally {
            Microbot.pauseAllScripts.set(false);
        }

        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    private boolean hasMatchingLoot(AutoLooterConfig config) {
        int distance = config.distanceToStray();
        switch (config.looterStyle()) {
            case ITEM_LIST:
                return Arrays.stream(config.listOfItemsToLoot().trim().split(","))
                        .anyMatch(name -> Rs2GroundItem.exists(name.trim(), distance));
            case GE_PRICE_RANGE:
                return Rs2GroundItem.isItemBasedOnValueOnGround(config.minPriceOfItem(), distance);
            case MIXED:
                return Arrays.stream(config.listOfItemsToLoot().trim().split(","))
                        .anyMatch(name -> Rs2GroundItem.exists(name.trim(), distance))
                        || Rs2GroundItem.isItemBasedOnValueOnGround(config.minPriceOfItem(), distance);
            default:
                return false;
        }
    }

    private void lootItems(AutoLooterConfig config) {
        if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST || config.looterStyle() == DefaultLooterStyle.MIXED) {
            LootingParameters itemLootParams = new LootingParameters(
                    config.distanceToStray(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleLootMyItemsOnly(),
                    config.listOfItemsToLoot().split(",")
            );
            Rs2GroundItem.lootItemsBasedOnNames(itemLootParams);
        }

        if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE || config.looterStyle() == DefaultLooterStyle.MIXED) {
            LootingParameters valueParams = new LootingParameters(
                    config.minPriceOfItem(),
                    config.maxPriceOfItem(),
                    config.distanceToStray(),
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleLootMyItemsOnly()
            );
            Rs2GroundItem.lootItemBasedOnValue(valueParams);
        }
    }

    private boolean isAwayFromBase(AutoLooterConfig config) {
        return initialPlayerLocation == null
                || Rs2Player.isMoving()
                || Rs2Player.getWorldLocation().distanceTo(initialPlayerLocation) > config.distanceToStray();
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 15;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }
}
