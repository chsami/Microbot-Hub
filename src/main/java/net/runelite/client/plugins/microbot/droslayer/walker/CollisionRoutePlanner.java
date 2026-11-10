package net.runelite.client.plugins.microbot.droslayer.walker;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Compact collision-map route planner from Efficient Walker v1.11.2. */
final class CollisionRoutePlanner
{
    private static final Logger log = LoggerFactory.getLogger(CollisionRoutePlanner.class);
    private static final int[] DELTA_X = {-1, 1, 0, 0, -1, 1, -1, 1};
    private static final int[] DELTA_Y = {0, 0, -1, 1, -1, -1, 1, 1};

    private final Int2ObjectOpenHashMap<long[]> collisionRegions;
    private final Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> doorEdgesByOrigin = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> transportsByOrigin = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> transportsByDestination = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet transportEndpoints = new IntOpenHashSet();
    private final Predicate<TransportCatalog.TransportEdge> transportPredicate;

    private CollisionRoutePlanner(Int2ObjectOpenHashMap<long[]> collisionMap, List<TransportCatalog.TransportEdge> transports, Predicate<TransportCatalog.TransportEdge> transportPredicate)
    {
        this.collisionRegions = collisionMap;
        this.transportPredicate = transportPredicate;
        for (TransportCatalog.TransportEdge transport : transports)
        {
            if (transport.isUnrestrictedDoor())
            {
                this.doorEdgesByOrigin.computeIfAbsent(packWorldPoint(transport.origin), CollisionRoutePlanner::createDoorEdgeList).add(transport);
            }
            else
            {
                this.transportsByOrigin.computeIfAbsent(packWorldPoint(transport.origin), CollisionRoutePlanner::createOutgoingTransportList).add(transport);
                this.transportsByDestination.computeIfAbsent(packWorldPoint(transport.destination), CollisionRoutePlanner::createIncomingTransportList).add(transport);
            }
            registerTransportEndpoints(transport);
        }
    }

    private void registerTransportEndpoints(TransportCatalog.TransportEdge transport)
    {
        this.transportEndpoints.add(packWorldPoint(transport.origin));
        this.transportEndpoints.add(packWorldPoint(transport.destination));
    }

    static CollisionRoutePlanner create(List<TransportCatalog.TransportEdge> transports, Predicate<TransportCatalog.TransportEdge> transportPredicate)
    {
        try (InputStream input = openCollisionMap())
        {
            return loadCollisionMap(input, transports, transportPredicate);
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Failed to load Efficient Walker collision map", ex);
            return new CollisionRoutePlanner(new Int2ObjectOpenHashMap<>(), transports, transportPredicate);
        }
    }

    private static CollisionRoutePlanner loadCollisionMap(InputStream input, List<TransportCatalog.TransportEdge> transports, Predicate<TransportCatalog.TransportEdge> transportPredicate) throws IOException
    {
        Int2ObjectOpenHashMap<long[]> map = new Int2ObjectOpenHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                String[] parts = entry.getName().split("_");
                if (parts.length != 2)
                {
                    continue;
                }
                int regionX = Integer.parseInt(parts[0]);
                int regionY = Integer.parseInt(parts[1]);
                long[] words = BitSet.valueOf(zip.readAllBytes()).toLongArray();
                map.put(packRegionKey(regionX, regionY), words);
            }
        }
        return new CollisionRoutePlanner(map, transports, transportPredicate);
    }

    RouteSearchResult planRoute(WorldPoint start, Collection<WorldPoint> targets, Set<TransportCatalog.TransportEdge> excluded,
                 Set<Long> edgeOverrides, Set<TransportCatalog.TransportEdge> allowed)
    {
        return planRoute(Collections.singleton(start), targets, excluded, edgeOverrides, allowed);
    }

    RouteSearchResult planRoute(Collection<WorldPoint> starts, Collection<WorldPoint> targets, Set<TransportCatalog.TransportEdge> excluded,
                 Set<Long> edgeOverrides, Set<TransportCatalog.TransportEdge> allowed)
    {
        if (this.collisionRegions.isEmpty() || targets == null)
        {
            return new RouteSearchResult(Collections.emptyList(), EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED, Integer.MAX_VALUE);
        }
        List<WorldPoint> validTargets = targets.stream().filter(this::isUsableRouteTarget).collect(Collectors.toList());
        if (validTargets.isEmpty())
        {
            return new RouteSearchResult(Collections.emptyList(), EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED, Integer.MAX_VALUE);
        }
        Objects.requireNonNull(allowed);
        return searchRoute(starts, validTargets, excluded, edgeOverrides, allowed::contains);
    }

    private RouteSearchResult searchRoute(Collection<WorldPoint> starts, Collection<WorldPoint> targets, Set<TransportCatalog.TransportEdge> excluded,
                       Set<Long> edgeOverrides, Predicate<TransportCatalog.TransportEdge> allowed)
    {
        return searchBounded(starts, targets, excluded, edgeOverrides, allowed, 750_000);
    }

    RouteSearchResult searchBounded(Collection<WorldPoint> starts, Collection<WorldPoint> targets, Set<TransportCatalog.TransportEdge> excluded,
                    Set<Long> edgeOverrides, Predicate<TransportCatalog.TransportEdge> allowed, int nodeLimit)
    {
        SearchFrontier forward = new SearchFrontier(starts);
        SearchFrontier backward = new SearchFrontier(targets);
        final long deadline = System.nanoTime() + 2_000_000_000L;
        int best = Integer.MAX_VALUE;
        int meeting = -1;

        while (forward.peekDistance() != Integer.MAX_VALUE && backward.peekDistance() != Integer.MAX_VALUE)
        {
            if (forward.parents.size() + backward.parents.size() > nodeLimit)
            {
                break;
            }
            if (System.nanoTime() > deadline)
            {
                break;
            }
            if (Thread.currentThread().isInterrupted())
            {
                break;
            }
            if ((long) forward.peekDistance() + (long) backward.peekDistance() >= (long) best)
            {
                break;
            }

            boolean reverse = backward.peekDistance() < forward.peekDistance();
            SearchFrontier active = reverse ? backward : forward;
            SearchFrontier other = reverse ? forward : backward;

            int current = active.removeNext();
            int currentDistance = active.distances.get(current);
            int otherDistance = other.distances.get(current);
            if (otherDistance != Integer.MAX_VALUE && currentDistance + otherDistance < best)
            {
                best = currentDistance + otherDistance;
                meeting = current;
            }

            int x = unpackX(current);
            int yy = unpackY(current);
            int plane = unpackPlane(current);

            for (int direction = 0; direction < DELTA_X.length; direction++)
            {
                int nx = x + DELTA_X[direction];
                int ny = yy + DELTA_Y[direction];
                int next = packWorldPoint(nx, ny, plane);
                if (isWildernessBlocked(new WorldPoint(nx, ny, plane)))
                {
                    continue;
                }

                int fromX = reverse ? nx : x;
                int fromY = reverse ? ny : yy;
                int toX = reverse ? x : nx;
                int toY = reverse ? yy : ny;

                if (!canStep(fromX, fromY, plane, toX, toY, edgeOverrides, allowed))
                {
                    if (direction >= 4 || !isTileIsolated(toX, toY, plane)
                        || !hasUsableOutgoingTransport(packWorldPoint(toX, toY, plane), excluded, allowed))
                    {
                        continue;
                    }
                }

                active.enqueueIfShorter(next, current, currentDistance + 1);
                int opposite = other.distances.get(next);
                long candidate = (long) active.distances.get(next) + opposite;
                if (opposite != Integer.MAX_VALUE && candidate < best)
                {
                    best = (int) candidate;
                    meeting = next;
                }
            }

            Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> transportMap = reverse ? this.transportsByDestination : this.transportsByOrigin;
            List<TransportCatalog.TransportEdge> transports = transportMap.get(current);
            if (transports != null)
            {
                for (TransportCatalog.TransportEdge transport : transports)
                {
                    WorldPoint endpoint = reverse ? transport.origin : transport.destination;
                    if (excluded.contains(transport) || !allowed.test(transport) || isWildernessBlocked(endpoint))
                    {
                        continue;
                    }
                    active.enqueueIfShorter(packWorldPoint(endpoint), current, currentDistance + 4);
                    int next = packWorldPoint(endpoint);
                    int opposite = other.distances.get(next);
                    long candidate = (long) active.distances.get(next) + opposite;
                    if (opposite != Integer.MAX_VALUE && candidate < best)
                    {
                        best = (int) candidate;
                        meeting = next;
                    }
                }
            }
        }

        boolean exhausted = forward.peekDistance() == Integer.MAX_VALUE || backward.peekDistance() == Integer.MAX_VALUE;
        // A joined path is executable even if the budget expires before proving optimality.
        // Cancellation must still discard it so an obsolete request cannot resume walking.
        if (meeting != -1 && !Thread.currentThread().isInterrupted())
        {
            ArrayList<WorldPoint> route = new ArrayList<>(reconstructPath(meeting, forward.parents));
            List<WorldPoint> tail = reconstructPath(meeting, backward.parents);
            for (int index = tail.size() - 2; index >= 0; index--)
            {
                route.add(tail.get(index));
            }
            return new RouteSearchResult(Collections.unmodifiableList(route), EfficientWalker.PlanningFailure.NONE, best);
        }

        return new RouteSearchResult(Collections.emptyList(), exhausted
            ? EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED
            : EfficientWalker.PlanningFailure.SEARCH_LIMIT, Integer.MAX_VALUE);
    }

    static boolean isWildernessBlocked(WorldPoint point)
    {
        return WildernessTravelPolicy.isBlocked(point, false);
    }

    private static boolean relaxSearchNode(int node, int parent, int distance,
                                    Int2IntOpenHashMap parents, Int2IntOpenHashMap distances,
                                    IntArrayFIFOQueue[] queues)
    {
        if (distance >= distances.get(node))
        {
            return false;
        }
        parents.put(node, parent);
        distances.put(node, distance);
        queues[distance % 5].enqueue(node);
        return true;
    }

    boolean isStandable(WorldPoint point)
    {
        int packed = packWorldPoint(point);
        return this.transportEndpoints.contains(packed) || !isTileIsolated(point.getX(), point.getY(), point.getPlane());
    }

    boolean canStep(int x, int y, int plane, int toX, int toY)
    {
        return canStep(x, y, plane, toX, toY, Collections.emptySet(), this.transportPredicate);
    }

    private boolean canStep(int x, int y, int plane, int toX, int toY,
                             Set<Long> edgeOverrides, Predicate<TransportCatalog.TransportEdge> transportPredicate)
    {
        int dx = toX - x;
        int dy = toY - y;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0))
        {
            return false;
        }

        int from = packWorldPoint(x, y, plane);
        int to = packWorldPoint(toX, toY, plane);
        if (dx == 0 || dy == 0)
        {
            if (hasTraversableEdgeTransport(from, to, transportPredicate) || edgeOverrides.contains(packUndirectedEdge(from, to)))
            {
                return true;
            }
            if (isTileIsolated(x, y, plane))
            {
                return !isTileIsolated(toX, toY, plane);
            }
            if (dx < 0) return isWestEdgeOpen(x, y, plane);
            if (dx > 0) return isEastEdgeOpen(x, y, plane);
            if (dy < 0) return isSouthEdgeOpen(x, y, plane);
            return isNorthEdgeOpen(x, y, plane);
        }

        int horizontal = packWorldPoint(x + dx, y, plane);
        int vertical = packWorldPoint(x, y + dy, plane);
        if (hasTraversableEdgeTransport(from, horizontal, transportPredicate)
            || hasTraversableEdgeTransport(from, vertical, transportPredicate)
            || edgeOverrides.contains(packUndirectedEdge(from, horizontal))
            || edgeOverrides.contains(packUndirectedEdge(from, vertical)))
        {
            return false;
        }

        if (isTileIsolated(x, y, plane))
        {
            return !isTileIsolated(toX, toY, plane)
                && !isTileIsolated(x + dx, y, plane)
                && !isTileIsolated(x, y + dy, plane);
        }

        if (dx < 0)
        {
            if (dy < 0)
            {
                return isSouthEdgeOpen(x, y, plane) && isWestEdgeOpen(x, y - 1, plane) && isWestEdgeOpen(x, y, plane) && isSouthEdgeOpen(x - 1, y, plane);
            }
            return isNorthEdgeOpen(x, y, plane) && isWestEdgeOpen(x, y + 1, plane) && isWestEdgeOpen(x, y, plane) && isNorthEdgeOpen(x - 1, y, plane);
        }
        if (dy < 0)
        {
            return isSouthEdgeOpen(x, y, plane) && isEastEdgeOpen(x, y - 1, plane) && isEastEdgeOpen(x, y, plane) && isSouthEdgeOpen(x + 1, y, plane);
        }
        return isNorthEdgeOpen(x, y, plane) && isEastEdgeOpen(x, y + 1, plane) && isEastEdgeOpen(x, y, plane) && isNorthEdgeOpen(x + 1, y, plane);
    }

    private boolean hasTraversableEdgeTransport(int from, int to, Predicate<TransportCatalog.TransportEdge> predicate)
    {
        List<TransportCatalog.TransportEdge> transports = this.doorEdgesByOrigin.get(from);
        if (transports == null)
        {
            return false;
        }
        for (TransportCatalog.TransportEdge transport : transports)
        {
            if (matchesTraversableTransportDestination(to, predicate, transport))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsableOutgoingTransport(int packed, Set<TransportCatalog.TransportEdge> excluded, Predicate<TransportCatalog.TransportEdge> predicate)
    {
        List<TransportCatalog.TransportEdge> transports = this.transportsByOrigin.get(packed);
        if (transports == null)
        {
            return false;
        }
        for (TransportCatalog.TransportEdge transport : transports)
        {
            if (isUsableOutgoingTransport(excluded, predicate, transport))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isTileIsolated(int x, int y, int plane)
    {
        return !isNorthEdgeOpen(x, y, plane) && !isSouthEdgeOpen(x, y, plane) && !isEastEdgeOpen(x, y, plane) && !isWestEdgeOpen(x, y, plane);
    }

    private boolean isNorthEdgeOpen(int x, int y, int plane)
    {
        return isCollisionEdgeOpen(x, y, plane, 0);
    }

    private boolean isSouthEdgeOpen(int x, int y, int plane)
    {
        return isNorthEdgeOpen(x, y - 1, plane);
    }

    private boolean isEastEdgeOpen(int x, int y, int plane)
    {
        return isCollisionEdgeOpen(x, y, plane, 1);
    }

    private boolean isWestEdgeOpen(int x, int y, int plane)
    {
        return isEastEdgeOpen(x - 1, y, plane);
    }

    private boolean isCollisionEdgeOpen(int x, int y, int plane, int flag)
    {
        long[] words = this.collisionRegions.get(packRegionKey(x / 64, y / 64));
        if (words == null || plane < 0 || plane >= 4)
        {
            return false;
        }
        int bitIndex = (((plane * 64 + (y & 63)) * 64 + (x & 63)) * 2 + flag);
        int wordIndex = bitIndex >>> 6;
        if (wordIndex >= words.length)
        {
            return false;
        }
        return ((words[wordIndex] >>> (bitIndex & 63)) & 1L) != 0L;
    }

    private static List<WorldPoint> reconstructPath(int packed, Int2IntOpenHashMap parents)
    {
        List<WorldPoint> path = new ArrayList<>();
        int current = packed;
        while (current != -1)
        {
            path.add(new WorldPoint(unpackX(current), unpackY(current), unpackPlane(current)));
            current = parents.get(current);
        }
        Collections.reverse(path);
        return Collections.unmodifiableList(path);
    }

    private static int packWorldPoint(WorldPoint point)
    {
        return packWorldPoint(point.getX(), point.getY(), point.getPlane());
    }

    static long packUndirectedEdge(int x1, int y1, int x2, int y2, int plane)
    {
        return packUndirectedEdge(packWorldPoint(x1, y1, plane), packWorldPoint(x2, y2, plane));
    }

    private static long packUndirectedEdge(int a, int b)
    {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    private static int packWorldPoint(int x, int y, int plane)
    {
        return (x & 0x7fff) | ((y & 0x7fff) << 15) | ((plane & 3) << 30);
    }

    private static int unpackX(int packed)
    {
        return packed & 0x7fff;
    }

    private static int unpackY(int packed)
    {
        return (packed >>> 15) & 0x7fff;
    }

    private static int unpackPlane(int packed)
    {
        return (packed >>> 30) & 3;
    }

    private static int packRegionKey(int regionX, int regionY)
    {
        return (regionX & 0xffff) | ((regionY & 0xffff) << 16);
    }

    private static InputStream openCollisionMap() throws IOException
    {
        InputStream resource = WalkerEngineResources.open(
            "net/runelite/client/plugins/microbot/droslayer/walker/collision-map.zip");
        if (resource == null)
        {
            resource = CollisionRoutePlanner.class.getResourceAsStream("collision-map.zip");
        }
        if (resource != null)
        {
            return resource;
        }

        Path first = Path.of("src/main/resources/net/runelite/client/plugins/microbot/droslayer/walker/collision-map.zip");
        if (!Files.isRegularFile(first))
        {
            first = Path.of("microbot-client/src/main/resources/net/runelite/client/plugins/microbot/droslayer/walker/collision-map.zip");
        }
        return Files.newInputStream(first);
    }

    private static boolean isUsableOutgoingTransport(Set<TransportCatalog.TransportEdge> excluded, Predicate<TransportCatalog.TransportEdge> predicate, TransportCatalog.TransportEdge transport)
    {
        return !transport.isRestrictedDoor() && !excluded.contains(transport) && predicate.test(transport);
    }

    private static boolean matchesTraversableTransportDestination(int destination, Predicate<TransportCatalog.TransportEdge> predicate, TransportCatalog.TransportEdge transport)
    {
        return packWorldPoint(transport.destination) == destination && predicate.test(transport);
    }

    private boolean isUsableRouteTarget(WorldPoint point)
    {
        return point != null && !isWildernessBlocked(point) && isStandable(point);
    }

    private static List<TransportCatalog.TransportEdge> createDoorEdgeList(int ignored)
    {
        return new ArrayList<>();
    }

    private static List<TransportCatalog.TransportEdge> createOutgoingTransportList(int ignored)
    {
        return new ArrayList<>();
    }

    private static List<TransportCatalog.TransportEdge> createIncomingTransportList(int ignored)
    {
        return new ArrayList<>();
    }

    private static final class SearchFrontier
    {
        private final Int2IntOpenHashMap parents = new Int2IntOpenHashMap();
        private final Int2IntOpenHashMap distances = new Int2IntOpenHashMap();
        private final IntArrayFIFOQueue[] distanceBuckets = new IntArrayFIFOQueue[5];
        private int queuedCount;
        private int currentDistance;

        private SearchFrontier(Collection<WorldPoint> points)
        {
            this.distances.defaultReturnValue(Integer.MAX_VALUE);
            for (int i = 0; i < this.distanceBuckets.length; i++)
            {
                this.distanceBuckets[i] = new IntArrayFIFOQueue();
            }
            for (WorldPoint point : points)
            {
                if (point != null && !CollisionRoutePlanner.isWildernessBlocked(point))
                {
                    enqueueIfShorter(CollisionRoutePlanner.packWorldPoint(point), -1, 0);
                }
            }
        }

        private void enqueueIfShorter(int node, int parent, int distance)
        {
            if (CollisionRoutePlanner.relaxSearchNode(node, parent, distance, this.parents, this.distances, this.distanceBuckets))
            {
                this.queuedCount++;
            }
        }

        private int peekDistance()
        {
            while (this.queuedCount > 0)
            {
                IntArrayFIFOQueue queue = this.distanceBuckets[this.currentDistance % 5];
                if (queue.isEmpty())
                {
                    this.currentDistance++;
                    continue;
                }
                if (this.distances.get(queue.firstInt()) != this.currentDistance)
                {
                    queue.dequeueInt();
                    this.queuedCount--;
                    continue;
                }
                return this.currentDistance;
            }
            return Integer.MAX_VALUE;
        }

        private int removeNext()
        {
            this.queuedCount--;
            return this.distanceBuckets[this.currentDistance % 5].dequeueInt();
        }
    }

    static final class RouteSearchResult
    {
        final List<WorldPoint> path;
        final EfficientWalker.PlanningFailure failure;
        final int distance;

        private RouteSearchResult(List<WorldPoint> route, EfficientWalker.PlanningFailure failure, int distance)
        {
            this.path = route;
            this.failure = failure;
            this.distance = distance;
        }
    }
}
