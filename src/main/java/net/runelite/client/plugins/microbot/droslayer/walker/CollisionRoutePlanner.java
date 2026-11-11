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

    static boolean isWildernessBlocked(WorldPoint point)
    {
        return WildernessTravelPolicy.isBlocked(point, false);
    }

    private static int packWorldPoint(WorldPoint point)
    {
        return packWorldPoint(point.getX(), point.getY(), point.getPlane());
    }

    private static int packWorldPoint(int x, int y, int plane)
    {
        return (x & 0x7fff) | ((y & 0x7fff) << 15) | ((plane & 3) << 30);
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

}
