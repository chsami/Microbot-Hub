package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.coords.WorldPoint;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

class TormentedDemonBombDodge {

    static final int BOMB_TICKS = 7;
    static final int TICK_MILLIS = 600;
    static final int MAX_DODGE_DELAY_MILLIS = 1800;
    static final int SAFE_TILE_RADIUS = 2;

    enum Outcome {IDLE, WAITING, STEP, DODGED, HIT}

    private final Map<WorldPoint, Integer> bombTiles = new HashMap<>();
    private int ticksSinceFirstBomb;
    private boolean exposed;
    private boolean noSafeTile;

    synchronized void addBomb(WorldPoint tile) {
        if (tile == null) {
            return;
        }
        if (bombTiles.isEmpty()) {
            ticksSinceFirstBomb = 0;
            noSafeTile = false;
        }
        bombTiles.merge(tile, BOMB_TICKS, Math::max);
    }

    synchronized Outcome onTick(WorldPoint playerLocation, int dodgeDelayMillis) {
        if (bombTiles.isEmpty()) {
            return Outcome.IDLE;
        }

        Integer remaining = playerLocation == null ? null : bombTiles.get(playerLocation);
        boolean landedOnPlayer = remaining != null && remaining <= 0;
        bombTiles.values().removeIf(ticksLeft -> ticksLeft <= 0);
        bombTiles.replaceAll((tile, ticksLeft) -> ticksLeft - 1);
        int elapsedTicks = ticksSinceFirstBomb++;
        int delayTicks = Math.max(0, Math.min(dodgeDelayMillis, MAX_DODGE_DELAY_MILLIS)) / TICK_MILLIS;

        if (landedOnPlayer) {
            exposed = false;
            return Outcome.HIT;
        }
        if (remaining != null) {
            exposed = true;
            return elapsedTicks >= delayTicks ? Outcome.STEP : Outcome.WAITING;
        }
        if (exposed) {
            exposed = false;
            return Outcome.DODGED;
        }
        return Outcome.IDLE;
    }

    synchronized boolean hasPendingBombs() {
        return !bombTiles.isEmpty();
    }

    synchronized boolean hasPendingBombsNear(WorldPoint playerLocation) {
        if (playerLocation == null) {
            return !bombTiles.isEmpty();
        }
        return bombTiles.keySet().stream().anyMatch(tile -> tile.distanceTo(playerLocation) <= SAFE_TILE_RADIUS);
    }

    synchronized boolean reportNoSafeTile() {
        boolean firstReport = !noSafeTile;
        noSafeTile = true;
        return firstReport;
    }

    synchronized boolean hadNoSafeTile() {
        return noSafeTile;
    }

    synchronized void reset() {
        bombTiles.clear();
        ticksSinceFirstBomb = 0;
        exposed = false;
        noSafeTile = false;
    }

    synchronized WorldPoint pickSafeTile(WorldPoint playerLocation, Map<WorldPoint, Integer> reachableTiles) {
        if (playerLocation == null || reachableTiles == null) {
            return null;
        }
        return reachableTiles.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .filter(entry -> !entry.getKey().equals(playerLocation))
                .filter(entry -> !bombTiles.containsKey(entry.getKey()))
                .min(Comparator.<Map.Entry<WorldPoint, Integer>>comparingInt(entry -> isNextToBomb(entry.getKey()) ? 1 : 0)
                        .thenComparingInt(Map.Entry::getValue)
                        .thenComparingInt(entry -> entry.getKey().getX())
                        .thenComparingInt(entry -> entry.getKey().getY()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean isNextToBomb(WorldPoint tile) {
        return bombTiles.keySet().stream().anyMatch(bombTile -> bombTile.distanceTo(tile) <= 1);
    }
}
