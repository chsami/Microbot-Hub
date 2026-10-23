package net.runelite.client.plugins.microbot.sandcrabs;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.sandcrabs.enums.State;
import net.runelite.client.plugins.microbot.sandcrabs.models.ScanLocation;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SandCrabScript extends Script {

    public int afkTimer = 0;
    public int hijackTimer = 0;
    private boolean DisableInventorySetup;

    public State state = State.FIGHT;

    public ScanLocation currentScanLocation;

    public int timesHopped = 0;

    List<ScanLocation> initSandCrabLocations = Arrays.asList(new ScanLocation(new WorldPoint(1843, 3462, 0)),
            new ScanLocation(new WorldPoint(1833, 3458, 0)),
            new ScanLocation(new WorldPoint(1790, 3468, 0)),
            new ScanLocation(new WorldPoint(1776, 3468, 0), true),
            new ScanLocation(new WorldPoint(1773, 3461, 0), true),
            new ScanLocation(new WorldPoint(1765, 3468, 0), true),
            new ScanLocation(new WorldPoint(1749, 3469, 0)),
            new ScanLocation(new WorldPoint(1738, 3468, 0)));

    List<ScanLocation> sandCrabLocations = new ArrayList<>();

    static boolean hasInventorySetup(InventorySetup setup) {
        return setup != null && setup.getName() != null && !setup.getName().isEmpty();
    }

    public boolean run(SandCrabConfig config, SandCrabPlugin plugin) {
        initialPlayerLocation = null;
        if (config.threeNpcs()) {
            sandCrabLocations = initSandCrabLocations.stream().filter(x -> x.hasThreeNpcs).collect(Collectors.toList());
        } else {
            sandCrabLocations = initSandCrabLocations.stream().collect(Collectors.toList());
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation == null) return;
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = playerLocation;
                }
                long startTime = System.currentTimeMillis();
                Rs2InventorySetup inventorySetup = null;

                if (!config.DisableInventorySetup()) {
                    if (!hasInventorySetup(config.inventorySetup())) {
                        Microbot.showMessage("Please setup your Inventory Setup for sand crabs! Stopping...");
                        shutdown();
                        return;
                    }
                    inventorySetup = new Rs2InventorySetup(config.inventorySetup().getName(), mainScheduledFuture);
                }

                Rs2Combat.enableAutoRetialiate();

                if (!config.DisableInventorySetup() && !inventorySetup.doesEquipmentMatch()) {
                    if (Rs2Bank.walkToBankAndUseBank()) {
                        if (inventorySetup.loadEquipment()) {
                            Microbot.log("We're setup for sand crabs!");
                            return;
                        }
                    }
                }

                if (otherPlayerDetected(playerLocation) &&!Rs2Combat.inCombat())
                    hijackTimer++;
                else
                    hijackTimer = 0;

                if (hijackTimer > 10) {
                    if (sandCrabLocations.stream().anyMatch(x -> !x.isScanned())) {
                        state = State.SCAN_LOCATIONS;
                    } else {
                        state = State.HOP_WORLD;
                        resetScanLocations();
                    }
                }

                if (currentScanLocation != null && playerLocation.distanceTo(currentScanLocation.getWorldPoint()) > 10 && (state != State.RESET_AGGRO && state != State.WALK_BACK && state != State.BANK)) {
                    state = State.WALK_BACK;
                    resetAggro(plugin);
                    resetAfkTimer();
                }
                currentScanLocation = sandCrabLocations.stream()
                        .filter(x -> !x.isScanned())
                        .min(Comparator.comparingInt(x -> x.getWorldPoint().distanceTo(playerLocation)))
                        .orElse(null);

                if (sandCrabLocations.stream()
                        .noneMatch(x -> x.getWorldPoint()
                                .equals(playerLocation))
                        && currentScanLocation != null
                        && state != State.RESET_AGGRO
                        && state != State.WALK_BACK) {
                    state = State.SCAN_LOCATIONS;
                }

                if (config.useFood()) {
                    Rs2Player.eatAt(50);

                    if (Rs2Inventory.getInventoryFood().isEmpty()) {
                        state = State.BANK;
                        Rs2Walker.walkTo(new WorldPoint(1720, 3465, 0));
                        if (Rs2Bank.openBank()) {
                            Rs2Bank.withdrawAll(config.food().getName(), true);
                            state = State.WALK_BACK;
                        }
                        return;
                    }
                }


                switch (state) {
                    case FIGHT:
                        if (!Rs2Player.isInteracting() &&Rs2Combat.inCombat()) {
                            Rs2Tab.switchToCombatOptionsTab();
                            Rs2Combat.enableAutoRetialiate();
                        }
                        if (!isNpcAggressive() || afkTimer > 10) {
                            state = State.AFK;
                        }
                        break;
                    case AFK:
                        afkTimer++;
                        if (Rs2Combat.inCombat()) {
                            resetAfkTimer();
                        }
                        if (afkTimer > 10) {
                            state = State.RESET_AGGRO;
                        }
                        break;
                    case RESET_AGGRO:
                        resetAggro(plugin);
                        break;
                    case WALK_BACK:
                        walkBack(plugin);
                        break;
                    case HOP_WORLD:
                        int world = Login.getRandomWorld(true, null);
                        if (!Microbot.hopToWorld(world)) {
                            Microbot.log("World hop to " + world + " refused, retrying next loop");
                            break;
                        }
                        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);

                        if (timesHopped > 10) {
                            timesHopped = 0;
                            state = State.SCAN_LOCATIONS;
                        } else {
                            timesHopped++;
                            hijackTimer = 0;
                            resetAfkTimer();
                        }
                        resetScanLocations();
                        break;
                    case SCAN_LOCATIONS:
                        scanSandCrabLocations(config, playerLocation);
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println(totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Checks if there are sandy rocks spawned next to the player
     * This is used to know if the aggro timer has ran out
     *
     * @return true if npc is aggressive
     */
    private boolean isNpcAggressive()
    {
        if (Microbot.getClient() == null || Microbot.getClientThread() == null)
        {
            return false;
        }

        java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

        Microbot.getClientThread().invoke(() ->
        {
            try
            {

        List<Rs2NpcModel> npcs = Microbot.getRs2NpcCache().query().withName("Sandy rocks").toListOnClientThread();
        if (npcs.isEmpty()) {
            result.set(false); return;
        }
        for (Rs2NpcModel sandyRock : npcs) {
            if (!sandyRock.getNpc().getWorldArea().isInMeleeDistance(Rs2Player.getWorldLocation()))
                continue;

            result.set(false); return;
        }
        result.set(true); return;
    
            }
            catch (Exception ignored)
            {
                result.set(false);
            }
            finally
            {
                done.countDown();
            }
        });

        try
        {
            done.await(750, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }

        return result.get();
    }

    /**
     * Reset aggro will walk 40 tiles north
     */
    private void resetAggro(SandCrabPlugin plugin) {

        boolean walkedFarEnough = false;
        if (Rs2Player.getWorldLocation().getX() > 1805) {
            walkedFarEnough = Rs2Walker.walkTo(new WorldPoint(Rs2Random.between(1844, 1849), 3496, 0));
        } else {
            walkedFarEnough = Rs2Walker.walkTo(new WorldPoint(initialPlayerLocation.getX(), initialPlayerLocation.getY() + 40, initialPlayerLocation.getPlane()), 4);
        }

        if (!walkedFarEnough) return;

        state = State.WALK_BACK;
    }

    /**
     * Walks back to the initial player location when the script started
     */
    private void walkBack(SandCrabPlugin plugin) {

        boolean backToInitialLocation = Rs2Walker.walkTo(initialPlayerLocation, 0);
        if (!backToInitialLocation) return;

        resetAfkTimer();
    }

    /**
     * Reset afk timer and sets state back to fight
     */
    private void resetAfkTimer() {
        afkTimer = 0;
        state = State.FIGHT;
    }

    private boolean otherPlayerDetected(WorldPoint worldPoint) {
        for (Rs2PlayerModel player : Rs2Player.getPlayers(player -> true).collect(Collectors.toList())) {
            if (player.getWorldLocation().distanceTo(worldPoint) > 2)
                continue;
            return true;
        }
        return false;
    }

    private void scanSandCrabLocations(SandCrabConfig config, WorldPoint playerLocation) {
        currentScanLocation = sandCrabLocations.stream()
                .filter(x -> !x.isScanned())
                .min(Comparator.comparingInt(x -> x.getWorldPoint().distanceTo(playerLocation)))
                .orElse(null);

        if (currentScanLocation == null) {

            state = State.HOP_WORLD;
            return;
        }
        if (currentScanLocation.getWorldPoint().distanceTo(playerLocation) > 10) {
            boolean reachedLocation = Rs2Walker.walkTo(currentScanLocation.getWorldPoint());
            if (!reachedLocation) {
                if (currentScanLocation.triedWalking > 20) { //something went wrong, just skip this location
                    currentScanLocation.scanned = true;
                } else {
                    currentScanLocation.triedWalking++;
                }
            }
        }
        //check if there are other players on our spot
        if (otherPlayerDetected(currentScanLocation.getWorldPoint())) {
            currentScanLocation.scanned = true;
        } else {
            Rs2Walker.walkTo(currentScanLocation.getWorldPoint(), 0);
            initialPlayerLocation = currentScanLocation.getWorldPoint();
            state = State.FIGHT;
        }
    }

    private void resetScanLocations() {
        for (ScanLocation scanLocation : sandCrabLocations) {
            scanLocation.reset();
        }
    }
}