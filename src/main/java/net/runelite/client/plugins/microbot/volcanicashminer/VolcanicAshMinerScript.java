package net.runelite.client.plugins.microbot.volcanicashminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.mining.MiningAnimation;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

@Slf4j
public class VolcanicAshMinerScript extends Script {
    public static VolcanicAshMinerState BOT_STATUS = VolcanicAshMinerState.MINING;
    private final WorldPoint VOLCANIC_ASH_LOCATION = new WorldPoint(3790, 3770, 0);
    public Instant startTime;

    /**
     * Get the total runtime of the script
     *
     * @return the total runtime of the script
     */
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);
        return Duration.between(startTime, Instant.now());
    }
    public boolean run(VolcanicAshMinerConfig config) {
        startTime = Instant.now();
        BOT_STATUS = VolcanicAshMinerState.MINING;
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                handleMining(config);
            } catch (Exception ex) {
                log.error("Error during script execution", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleMining(VolcanicAshMinerConfig config) {
        if (Rs2Inventory.isFull()) {
            if (config.bankAsh()) {
                BOT_STATUS = VolcanicAshMinerState.WALKING;
                Rs2Bank.walkToBankAndUseBank(BankLocation.VOLCANO_BANK);
                if (!Rs2Bank.isOpen()) return;
                BOT_STATUS = VolcanicAshMinerState.BANKING;
                Rs2Bank.depositAll("soda ash");
                if (!sleepUntil(() -> !Rs2Inventory.contains("soda ash"))) {
                    log.warn("Failed to deposit soda ash");
                    return;
                }
                Rs2Bank.closeBank();
            } else {
                // Just for visual feedback in the overlay
                BOT_STATUS = VolcanicAshMinerState.DROPPING;
                Rs2Inventory.dropAll("soda ash");
            }
        }

        if (Rs2Player.distanceTo(VOLCANIC_ASH_LOCATION) > 15) {
            BOT_STATUS = VolcanicAshMinerState.WALKING;
            Rs2Walker.walkTo(VOLCANIC_ASH_LOCATION);
        }

        if (hopIfTooManyPlayersNearby(config)) return; // Exit current cycle after hop

        if (Rs2Equipment.isWearing("Dragon pickaxe") || Rs2Equipment.isWearing("Crystal pickaxe")) {
            if (Rs2Combat.setSpecState(true, 1000)) return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        GameObject rock = findReachableAshPile();
        if (rock != null) {
            BOT_STATUS = VolcanicAshMinerState.MINING;
            if (new Rs2TileObjectModel(rock).click()) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        } else {
            BOT_STATUS = VolcanicAshMinerState.WAITING;
            Microbot.log("No Ash pile found. Waiting...");
        }
    }

    boolean hopIfTooManyPlayersNearby(VolcanicAshMinerConfig config) {
        int maxPlayers = config.maxPlayersInArea();
        if (maxPlayers > 0) {
            WorldPoint localLocation = Rs2Player.getWorldLocation();
            if (localLocation == null) return false;

            Player localPlayer = Microbot.getClient().getLocalPlayer();
            int nearbyPlayers = Microbot.getRs2PlayerCache().query()
                    .where(p -> p.getPlayer() != localPlayer)
                    .where(p -> MiningAnimation.MINING_ANIMATIONS.contains(p.getAnimation()))
                    .within(localLocation, 15)
                    .count();

            if (nearbyPlayers > maxPlayers) {
                Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking
                int world = Login.getRandomWorld(Rs2Player.isMember());
                Microbot.log(nearbyPlayers + " other players are mining here (limit " + maxPlayers + "). Hopping to world " + world + "...");
                boolean hopped = Microbot.hopToWorld(world);
                if (!hopped) {
                    Microbot.log("Could not hop to world " + world + ". Will try again shortly.");
                    return false;
                }
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
                return true;
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public enum VolcanicAshMinerState {
        WALKING,
        BANKING,
        DROPPING,
        MINING,
        WAITING
    }

    private GameObject findReachableAshPile() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<GameObject> candidates = gameObjectsNear(VOLCANIC_ASH_LOCATION, 12,
                    o -> "Ash pile".equalsIgnoreCase(new Rs2TileObjectModel(o).getName()));
            if (candidates.isEmpty()) {
                return null;
            }
            WorldPoint playerLocation = Rs2Player.getRs2WorldPoint().getWorldPoint();
            return candidates.stream()
                    .sorted(Comparator.comparingInt(o -> Rs2WorldPoint.quickDistance(playerLocation, o.getWorldLocation())))
                    .filter(o -> new Rs2TileObjectModel(o).isReachable())
                    .findFirst()
                    .orElse(null);
        }).orElse(null);
    }

    private static List<GameObject> gameObjectsNear(WorldPoint anchor, int distance, Predicate<GameObject> predicate) {
        LocalPoint anchorLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), anchor);
        if (anchorLocal == null) {
            Player player = Microbot.getClient().getLocalPlayer();
            anchorLocal = player != null ? player.getLocalLocation() : null;
            if (anchorLocal == null) return Collections.emptyList();
        }
        LocalPoint from = anchorLocal;
        int maxDistance = Math.min(distance, Constants.SCENE_SIZE) * Perspective.LOCAL_TILE_SIZE;
        return Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .ofType(TileObjectType.GAME)
                .toList().stream()
                .map(o -> (GameObject) o.getTileObject())
                .filter(o -> isWithin(from, o.getLocalLocation(), maxDistance))
                .filter(predicate)
                .sorted(Comparator.comparingInt(o -> o.getLocalLocation().distanceTo(from)))
                .collect(Collectors.toList());
    }

    private static boolean isWithin(LocalPoint anchor, LocalPoint location, int maxDistance) {
        if (maxDistance == 0) {
            int dx = Math.abs(anchor.getX() - location.getX());
            int dy = Math.abs(anchor.getY() - location.getY());
            return (dx == Perspective.LOCAL_TILE_SIZE && dy == 0) || (dy == Perspective.LOCAL_TILE_SIZE && dx == 0);
        }
        return location.distanceTo(anchor) <= maxDistance;
    }
}
