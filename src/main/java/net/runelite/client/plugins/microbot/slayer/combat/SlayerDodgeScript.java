package net.runelite.client.plugins.microbot.slayer.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Script that handles dodging AOE projectile attacks.
 * Used for monsters like Adamant Dragons that have poison pool attacks.
 */
@Slf4j
public class SlayerDodgeScript extends Script {

    public final List<Projectile> projectiles = new ArrayList<>();
    private boolean enabled = false;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!enabled) return;
                if (!Microbot.isLoggedIn()) return;

                // Remove expired projectiles
                int cycle = Microbot.getClient().getGameCycle();
                projectiles.removeIf(projectile -> cycle >= projectile.getEndCycle());

                if (projectiles.isEmpty()) return;

                // Get all dangerous points from projectiles
                WorldPoint[] dangerousPoints = projectiles.stream()
                        .map(Projectile::getTargetPoint)
                        .toArray(WorldPoint[]::new);

                WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

                // Check if any projectile is targeting near the player
                boolean inDanger = projectiles.stream()
                        .anyMatch(p -> p.getTargetPoint().distanceTo(playerLocation) < 2);

                if (inDanger) {
                    WorldPoint safePoint = calculateSafePoint(playerLocation, dangerousPoints);
                    if (safePoint != null && !safePoint.equals(playerLocation)) {
                        log.info("Dodging projectile! Moving from {} to {}", playerLocation, safePoint);
                        Rs2Walker.walkFastCanvas(safePoint);
                    }
                }

            } catch (Exception e) {
                log.debug("Dodge script error: {}", e.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Calculates the nearest safe point away from all dangerous projectile targets.
     * A safe point is at least 2 tiles away from all dangerous points.
     */
    private WorldPoint calculateSafePoint(WorldPoint playerLocation, WorldPoint[] dangerousPoints) {
        int searchRadius = 5;
        int minDistance = Integer.MAX_VALUE;
        WorldPoint bestPoint = null;

        // Search in a square area around the player
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip current position

                WorldPoint candidate = playerLocation.dx(dx).dy(dy);

                // Check if this point is safe (at least 2 tiles away from all dangerous points)
                boolean isSafe = Arrays.stream(dangerousPoints)
                        .allMatch(p -> p.distanceTo(candidate) >= 2);

                if (isSafe) {
                    int distanceToPlayer = candidate.distanceTo(playerLocation);
                    // Prefer closer safe tiles that are reachable
                    if (distanceToPlayer < minDistance && Rs2Tile.isTileReachable(candidate)) {
                        minDistance = distanceToPlayer;
                        bestPoint = candidate;
                    }
                }
            }
        }

        return bestPoint != null ? bestPoint : playerLocation;
    }

    /**
     * Adds a projectile to track for dodging.
     * Only projectiles targeting a WorldPoint (not an actor) should be added.
     */
    public void addProjectile(Projectile projectile) {
        if (projectile != null && projectile.getTargetActor() == null) {
            projectiles.add(projectile);
        }
    }

    @Override
    public void shutdown() {
        projectiles.clear();
        super.shutdown();
    }
}
