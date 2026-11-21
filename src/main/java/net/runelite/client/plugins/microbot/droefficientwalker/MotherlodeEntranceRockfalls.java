package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportObjectClick;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Clears only the two recorded entrance obstacles, with observed collision completion. */
final class MotherlodeEntranceRockfalls
{
    interface Ports
    {
        WorldPoint player();
        boolean present(WorldPoint rock);
        boolean reachable(WorldPoint rock);
        boolean canApproach(WorldPoint rock);
        boolean pickaxe();
        boolean mine(WorldPoint rock);
        void status(String text);
    }

    private final Ports ports;
    private final ExecutorService worker;
    private WorldPoint pendingRock;
    private Future<Boolean> input;
    private long deadline;
    private volatile String failure;

    MotherlodeEntranceRockfalls() { this(new GamePorts(), newWorker()); }
    MotherlodeEntranceRockfalls(Ports ports, ExecutorService worker) { this.ports = ports; this.worker = worker; }

    private static ExecutorService newWorker()
    {
        return WalkerMouseControl.profiledExecutor(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "EfficientWalker-mlm-entrance"); thread.setDaemon(true); return thread;
        }));
    }

    boolean beforeTick(Supplier<List<WorldPoint>> path) {
        return beforeTick(MotherlodeTransports.inMine(ports.player()) ? path.get() : List.of(), System.currentTimeMillis());
    }

    boolean beforeTick(List<WorldPoint> path, long now)
    {
        if (failure != null) { ports.status(failure); return true; }
        WorldPoint player = ports.player();
        if (!MotherlodeTransports.inMine(player)) { reset(); return false; }
        if (pendingRock != null) {
            // A missing object is insufficient during a scene change/cache update.
            if (!ports.present(pendingRock) && ports.reachable(pendingRock)) { reset(); return false; }
            if (now >= deadline) return fail("Motherlode Mine entrance rockfall did not clear; travel stopped.");
            if (input.isDone()) {
                try {
                    if (!input.get()) return fail("Could not mine the Motherlode Mine entrance rockfall.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); reset(); return true;
                } catch (ExecutionException | CancellationException ex) {
                    return fail("Motherlode Mine entrance rockfall interaction failed.");
                }
            }
            ports.status("Clearing Motherlode Mine entrance rockfall");
            return true;
        }
        WorldPoint rock = firstBlockingRock(player, path, ports);
        if (rock == null || !ports.canApproach(rock)) return false;
        if (!ports.pickaxe()) return fail("A carried or worn pickaxe is required for the Motherlode Mine entrance.");
        pendingRock = rock; deadline = now + 12_000L;
        input = worker.submit(() -> ports.mine(rock));
        ports.status("Clearing Motherlode Mine entrance rockfall");
        return true;
    }

    static WorldPoint firstBlockingRock(WorldPoint player, List<WorldPoint> path, Ports ports)
    {
        if (player == null || path == null) return null;
        for (WorldPoint step : path) {
            if (MotherlodeTransports.ENTRANCE_ROCKFALLS.contains(step)
                && player.distanceTo2D(step) <= 5 && ports.present(step)) return step;
        }
        return null;
    }

    private boolean fail(String message) { failure = message; ports.status(message); return true; }
    String failure() { return failure; }
    void reset() {
        if (input != null) input.cancel(true);
        input = null; pendingRock = null; deadline = 0; failure = null;
    }
    void shutdown() { reset(); worker.shutdownNow(); }

    private static final class GamePorts implements Ports
    {
        public WorldPoint player() { return Rs2Player.getWorldLocation(); }
        private Rs2TileObjectModel object(WorldPoint point) {
            return Microbot.getRs2TileObjectCache().query().where(o -> o.getWorldLocation() != null
                && o.getWorldLocation().equals(point) && (o.getId() == 26679 || o.getId() == 26680)).first();
        }
        public boolean present(WorldPoint point) { return object(point) != null; }
        public boolean reachable(WorldPoint point) {
            return Rs2Tile.getReachableTilesFromTile(player(), 12).containsKey(point);
        }
        public boolean canApproach(WorldPoint point) {
            var reachable = Rs2Tile.getReachableTilesFromTile(player(), 12);
            return reachable.containsKey(point.dx(1)) || reachable.containsKey(point.dx(-1))
                || reachable.containsKey(point.dy(1)) || reachable.containsKey(point.dy(-1));
        }
        public boolean pickaxe() { return Rs2Inventory.hasItem("pickaxe") || Rs2Equipment.isWearing("pickaxe"); }
        public boolean mine(WorldPoint point) {
            Rs2TileObjectModel rock = Microbot.getClientThread().runOnClientThreadOptional(() -> object(point)).orElse(null);
            return rock == null || TransportObjectClick.click(rock, "Mine");
        }
        public void status(String text) { Microbot.status = text; }
    }
}
