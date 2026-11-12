package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.coords.WorldPoint;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

class TormentedDemonBombDodge {

    static final int BOMB_TICKS = 5;
    static final int TICK_MILLIS = 600;
    static final int MAX_DODGE_DELAY_MILLIS = 1800;
    static final int SAFE_TILE_RADIUS = 2;

    enum Outcome {IDLE, WAITING, STEP, DODGED, HIT}

    private final Map<WorldPoint, Integer> bombTiles = new HashMap<>();
    private int ticksSinceFirstBomb;
    private boolean exposed;

    synchronized void addBomb(WorldPoint tile) {
        if (tile == null) {
            return;
        }
        if (bombTiles.isEmpty()) {
            ticksSinceFirstBomb = 0;
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

    synchronized void reset() {
        bombTiles.clear();
        ticksSinceFirstBomb = 0;
        exposed = false;
    }

    synchronized WorldPoint pickSafeTile(WorldPoint playerLocation, Map<WorldPoint, Integer> reachableTiles) {
        if (playerLocation == null || reachableTiles == null) {
            return null;
        }
        return reachableTiles.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .filter(entry -> !entry.getKey().equals(playerLocation))
                .filter(entry -> !bombTiles.containsKey(entry.getKey()))
                .min(Comparator.<Map.Entry<WorldPoint, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingInt(entry -> entry.getKey().getX())
                        .thenComparingInt(entry -> entry.getKey().getY()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
