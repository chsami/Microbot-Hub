package net.runelite.client.plugins.microbot.mining;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.data.MiningRockLocations;
import net.runelite.client.plugins.microbot.mining.data.Rocks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

enum State {
    MINING,
    RESETTING,
}

@Slf4j
public class AutoMiningScript extends Script {

    private static final int GEM_MINE_UNDERGROUND = 11410;
    State state = State.MINING;
    private static final List<Rocks> PROGRESSIVE_ROCKS = buildProgressiveRocks();
    private Rocks activeRock;
    private LocationOption activeLocation;

    public boolean run(AutoMiningConfig config) {
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                updateActiveRock(config);

                if (config.progressiveMode() && ensureProgressiveLocation(config)) {
                    return;
                }

                if (activeRock == null || !activeRock.hasRequiredLevel()) {
                    Microbot.log("You do not have the required mining level to mine this ore.");
                    return;
                }

                if (Rs2Equipment.isWearing("Dragon pickaxe"))
                    Rs2Combat.setSpecState(true, 1000);

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                //code to change worlds if there are too many players in the distance to stray tiles
                int maxPlayers = config.maxPlayersInArea();
                if (maxPlayers > 0) {
                    WorldPoint localLocation = Rs2Player.getWorldLocation();

                    long nearbyPlayers = Microbot.getClient().getTopLevelWorldView().players().stream()
                            .filter(p -> p != null && p != Microbot.getClient().getLocalPlayer())
                            .filter(p -> {
                                if (config.distanceToStray() == 0) {
                                    // Only count players standing on the same exact tile
                                    return p.getWorldLocation().equals(localLocation);
                                }
                                // Count players within distanceToStray
                                return p.getWorldLocation().distanceTo(localLocation) <= config.distanceToStray();
                            })
                            //filter if players are using mining animation
                            .filter(p -> p.getAnimation() != -1)
                            .count();

                    if (nearbyPlayers >= maxPlayers) {
                        Microbot.status = "Too many players nearby. Hopping...";
                        Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking

                        int world = Login.getRandomWorld(Rs2Player.isMember());
                        boolean hopped = Microbot.hopToWorld(world);
                        if (hopped) {
                            Microbot.status = "Hopped to world: " + world;
                            return; // Exit current cycle after hop
                        }
                    }
                }


                switch (state) {
                    case MINING:
                        if (Rs2Inventory.isFull()) {
                            state = State.RESETTING;
                            return;
                        }

                        if (activeRock == null) {
                            return;
                        }

                        GameObject rock = Rs2GameObject.findReachableObject(activeRock.getName(), true, config.distanceToStray(), initialPlayerLocation);

                        if (rock != null) {
                            if (Rs2GameObject.interact(rock)) {
                                Rs2Player.waitForXpDrop(Skill.MINING, true);
                                Rs2Antiban.actionCooldown();
                                Rs2Antiban.takeMicroBreakByChance();
                            }
                        } else {
                            if (config.hopIfNoOreAvailable()) {
                                Microbot.status = "No ore available. Hopping...";
                                Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking

                                int world = Login.getRandomWorld(Rs2Player.isMember());
                                boolean hopped = Microbot.hopToWorld(world);
                                if (hopped) {
                                    Microbot.status = "Hopped to world: " + world;
                                    return; // Exit current cycle after hop
                                }
                            }
                        }
                        break;
                    case RESETTING:
                        List<String> itemNames = Arrays.stream(config.itemsToBank().split(",")).map(String::toLowerCase).collect(Collectors.toList());

                        if (config.useBank()) {
                            if (activeRock == Rocks.GEM && Rs2Player.getWorldLocation().getRegionID() == GEM_MINE_UNDERGROUND) {
                                if (Rs2DepositBox.openDepositBox()) {
                                    if (Rs2Inventory.contains("Open gem bag")) {
                                        Rs2Inventory.interact("Open gem bag", "Empty");
                                        Rs2DepositBox.depositAllExcept("Open gem bag");
                                    } else {
                                        Rs2DepositBox.depositAll();
                                    }
                                    Rs2DepositBox.closeDepositBox();
                                }
                            }
                            else if (Rocks.BASALT == activeRock) {
                                if (Rs2Walker.walkTo(2872,3935,0)){
                                    Rs2Inventory.useItemOnNpc(ItemID.BASALT, NpcID.MY2ARM_SNOWFLAKE);
                                    Rs2Walker.walkTo(2841,10339,0);
                                }
                            } else {
                                if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(itemNames, initialPlayerLocation, 0, config.distanceToStray()))
                                    return;
                            }

                        } else {
                            Rs2Inventory.dropAllExcept(false, config.interactOrder(), Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new));
                        }

                        state = State.MINING;
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown(){
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    private static List<Rocks> buildProgressiveRocks() {
        List<Rocks> rocks = new ArrayList<>(Arrays.asList(
                Rocks.TIN,
                Rocks.IRON,
                Rocks.COAL,
                Rocks.GOLD,
                Rocks.MITHRIL,
                Rocks.ADAMANTITE,
                Rocks.RUNITE
        ));
        return rocks;
    }

    private void updateActiveRock(AutoMiningConfig config) {
        Rocks previousRock = activeRock;
        LocationOption previousLocation = activeLocation;

        if (!config.progressiveMode()) {
            activeRock = config.ORE();
            activeLocation = MiningRockLocations.getBestAccessibleLocation(activeRock);
            updateStatus();
            return;
        }

        Rocks unlockedRock = PROGRESSIVE_ROCKS.stream()
                .filter(Rocks::hasRequiredLevel)
                .max(Comparator.comparingInt(Rocks::getMiningLevel))
                .orElse(PROGRESSIVE_ROCKS.get(0));

        activeRock = unlockedRock;
        activeLocation = MiningRockLocations.getBestAccessibleLocation(activeRock);

        updateStatus();
    }

    private boolean ensureProgressiveLocation(AutoMiningConfig config) {
        if (activeLocation == null || activeLocation.getWorldPoint() == null) {
            return false;
        }

        WorldPoint targetPoint = activeLocation.getWorldPoint();

        if (initialPlayerLocation == null || !initialPlayerLocation.equals(targetPoint)) {
            initialPlayerLocation = targetPoint;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return true;
        }

        int acceptableDistance = Math.min(Math.max(1, config.distanceToStray()), 5);
        int distanceToTarget = playerLocation.distanceTo(targetPoint);
        if (distanceToTarget > acceptableDistance) {
            if (Rs2Player.isMoving()) {
                return true;
            }

            Rs2Walker.walkTo(targetPoint, 3);
            return true;
        }

        return false;
    }

    private void updateStatus() {
        String oreName = activeRock != null ? activeRock.getName() : "Unknown";
        String locationName = (activeLocation != null && activeLocation.getName() != null)
                ? activeLocation.getName()
                : "current area";
        Microbot.status = "Mining " + oreName + " @ " + locationName;
    }
}
