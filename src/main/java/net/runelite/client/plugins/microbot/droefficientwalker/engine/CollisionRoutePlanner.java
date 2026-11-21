package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.WalkerEngineResources;
import net.runelite.client.plugins.microbot.droefficientwalker.WildernessTravelPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bidirectional search over collision edges and eligible transports. */
final class CollisionRoutePlanner {
   private static final Logger log;
   private static final int[] DELTA_X;
   private static final int[] DELTA_Y;
   private final Int2ObjectOpenHashMap<long[]> collisionRegions;
   private final Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> doorEdgesByOrigin;
   private final Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> transportsByOrigin;
   private final Int2ObjectOpenHashMap<List<TransportCatalog.TransportEdge>> transportsByDestination;
   private final IntOpenHashSet transportEndpoints;
   private final Predicate<TransportCatalog.TransportEdge> transportPredicate;

   private static final LegacyLiteral ENTRY_SEPARATOR = new LegacyLiteral("_", 0, 1437776481, -2059239631);
   private static final LegacyLiteral MODULE_COLLISION_PATH = new LegacyLiteral(
      "src/main/resources/net/runelite/client/plugins/microbot/efficientwalker/collision-map.zip", 4, 621246927, -1374820917);
   private static final LegacyLiteral REPOSITORY_COLLISION_PATH = new LegacyLiteral(
      "runelite-client/src/main/resources/net/runelite/client/plugins/microbot/efficientwalker/collision-map.zip", 244, 1951865224, 540130166);
   private static final LegacyLiteral COLLISION_RESOURCE = new LegacyLiteral(
      "/net/runelite/client/plugins/microbot/efficientwalker/collision-map.zip", 524, 1487854133, -1512410851);
   private static String legacyDescriptorTable = createLegacyDescriptorTable();

   // The maintained wilderness adapter reads these fields across the facade boundary.
   public static volatile boolean allowWildernessTravel;
   public static volatile WorldPoint aioTravelOrigin;
   /** Wall-clock planning budget; tests widen it so route reachability does not depend on machine speed. */
   static volatile long searchBudgetNanos = 2000000000L;
   private static final int SEARCH_NODE_LIMIT = 750000;
   private static final int SEARCH_RETRY_NODE_LIMIT = 2500000;
   private static final long SEARCH_RETRY_BUDGET_FACTOR = 3L;
   public static volatile WorldPoint aioTravelTarget;

   private CollisionRoutePlanner(Int2ObjectOpenHashMap<long[]> collisionRegions,
      List<TransportCatalog.TransportEdge> transports, Predicate<TransportCatalog.TransportEdge> transportPredicate) {
      this.doorEdgesByOrigin = new Int2ObjectOpenHashMap<>();
      this.transportsByOrigin = new Int2ObjectOpenHashMap<>();
      this.transportsByDestination = new Int2ObjectOpenHashMap<>();
      this.transportEndpoints = new IntOpenHashSet();
      this.collisionRegions = collisionRegions;
      this.transportPredicate = transportPredicate;
      for (TransportCatalog.TransportEdge transport : transports) {
         if (transport.isUnrestrictedDoor()) {
            this.doorEdgesByOrigin.computeIfAbsent(packWorldPoint(transport.origin),
               (Int2ObjectFunction<List<TransportCatalog.TransportEdge>>) ignored -> new ArrayList<>()).add(transport);
            this.registerTransportEndpoints(transport);
         } else {
            this.transportsByOrigin.computeIfAbsent(packWorldPoint(transport.origin),
               (Int2ObjectFunction<List<TransportCatalog.TransportEdge>>) ignored -> new ArrayList<>()).add(transport);
            this.transportsByDestination.computeIfAbsent(packWorldPoint(transport.destination),
               (Int2ObjectFunction<List<TransportCatalog.TransportEdge>>) ignored -> new ArrayList<>()).add(transport);
            this.registerTransportEndpoints(transport);
         }
      }
   }

   private void registerTransportEndpoints(TransportCatalog.TransportEdge transport) {
      this.transportEndpoints.add(packWorldPoint(transport.origin));
      this.transportEndpoints.add(packWorldPoint(transport.destination));
   }

   static CollisionRoutePlanner create(List<TransportCatalog.TransportEdge> transports,
      Predicate<TransportCatalog.TransportEdge> transportPredicate) {
      try (InputStream input = openCollisionMap()) {
         return loadCollisionMap(input, transports, transportPredicate);
      } catch (IOException | RuntimeException failure) {
         log.warn("Could not load the collision map; using local route fallback", failure);
         return new CollisionRoutePlanner(new Int2ObjectOpenHashMap<>(), transports, transportPredicate);
      }
   }

   private static CollisionRoutePlanner loadCollisionMap(InputStream input,
      List<TransportCatalog.TransportEdge> transports, Predicate<TransportCatalog.TransportEdge> transportPredicate) throws IOException {
      Int2ObjectOpenHashMap<long[]> regions = new Int2ObjectOpenHashMap<>();
      // The previous catch-state switch had identical arms. Standard resource
      // handling retains close timing and suppressed exceptions, including errors.
      try (ZipInputStream archive = new ZipInputStream(input)) {
         ZipEntry entry;
         while ((entry = archive.getNextEntry()) != null) {
            String name = entry.getName();
            String[] coordinates = name.split(ENTRY_SEPARATOR.value());
            if (coordinates.length == 2) {
               int regionX = Integer.parseInt(coordinates[0]);
               int regionY = Integer.parseInt(coordinates[1]);
               int regionKey = packRegionKey(regionX, regionY);
               long[] collisionWords = BitSet.valueOf(archive.readAllBytes()).toLongArray();
               regions.put(regionKey, collisionWords);
            }
         }
      }
      return new CollisionRoutePlanner(regions, transports, transportPredicate);
   }

   RouteSearchResult planRoute(WorldPoint start, Collection<WorldPoint> targets,
      Set<TransportCatalog.TransportEdge> excludedTransports, Set<Long> openEdges,
      Set<TransportCatalog.TransportEdge> allowedTransports) {
      return this.planRoute(Collections.singleton(start), targets, excludedTransports, openEdges, allowedTransports);
   }

   RouteSearchResult planRoute(Collection<WorldPoint> starts, Collection<WorldPoint> targets,
      Set<TransportCatalog.TransportEdge> excludedTransports, Set<Long> openEdges,
      Set<TransportCatalog.TransportEdge> allowedTransports) {
      Object previous = WildernessTravelPolicy.beginPlanning(starts, targets, allowWildernessTravel, aioTravelOrigin, aioTravelTarget);
      try {
         RouteSearchResult result = aio$wildernessPlan(WildernessTravelPolicy.planningStarts(starts), targets,
            excludedTransports, openEdges, allowedTransports);
         if (!WildernessTravelPolicy.plannedPathAllowed(result.path)) {
            result = new RouteSearchResult(Collections.emptyList(), WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED, Integer.MAX_VALUE);
         }
         return result;
      } finally {
         WildernessTravelPolicy.endPlanning(previous);
      }
   }

   /** Wilderness-scoped planning entry point. */
   private RouteSearchResult aio$wildernessPlan(Collection<WorldPoint> starts, Collection<WorldPoint> targets,
      Set<TransportCatalog.TransportEdge> excludedTransports, Set<Long> openEdges,
      Set<TransportCatalog.TransportEdge> allowedTransports) {
      if (!this.collisionRegions.isEmpty() && targets != null) {
         List<WorldPoint> usableTargets = targets.stream().filter(this::isUsableRouteTarget).collect(Collectors.toList());
         return usableTargets.isEmpty()
            ? new RouteSearchResult(Collections.emptyList(), WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED, Integer.MAX_VALUE)
            : this.searchRoute(starts, usableTargets, excludedTransports, openEdges, allowedTransports::contains);
      }
      return new RouteSearchResult(Collections.emptyList(), WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED, Integer.MAX_VALUE);
   }

   private boolean isUsableRouteTarget(WorldPoint target) {
      return target != null && !isWildernessBlocked(target) && this.isStandable(target);
   }

   private RouteSearchResult searchRoute(Collection<WorldPoint> starts, Collection<WorldPoint> targets,
      Set<TransportCatalog.TransportEdge> excludedTransports, Set<Long> openEdges,
      Predicate<TransportCatalog.TransportEdge> canUseTransport) {
      long budget = searchBudgetNanos;
      RouteSearchResult result = searchRoute(starts, targets, excludedTransports, openEdges, canUseTransport,
         budget, SEARCH_NODE_LIMIT);
      // Long cross-continent walks (e.g. Grand Exchange to Barbarian Outpost) can exhaust the
      // normal budget on slower machines although a path exists. Retry once, bounded, only
      // when the search was cut short rather than proven unreachable.
      if (result.failure == WalkerEngine.PlanningFailure.SEARCH_LIMIT && !Thread.currentThread().isInterrupted()) {
         result = searchRoute(starts, targets, excludedTransports, openEdges, canUseTransport,
            budget * SEARCH_RETRY_BUDGET_FACTOR, SEARCH_RETRY_NODE_LIMIT);
      }
      return result;
   }

   private RouteSearchResult searchRoute(Collection<WorldPoint> starts, Collection<WorldPoint> targets,
      Set<TransportCatalog.TransportEdge> excludedTransports, Set<Long> openEdges,
      Predicate<TransportCatalog.TransportEdge> canUseTransport, long budgetNanos, int nodeLimit) {
      SearchFrontier forward = new SearchFrontier(starts);
      SearchFrontier reverse = new SearchFrontier(targets);
      long deadline = System.nanoTime() + budgetNanos;
      int bestDistance = Integer.MAX_VALUE;
      int meetingTile = -1;

      // Keep the original ordered stopping checks and forward preference on ties.
      while (true) {
         if (forward.peekDistance() == Integer.MAX_VALUE) break;
         if (reverse.peekDistance() == Integer.MAX_VALUE) break;
         if (forward.parents.size() + reverse.parents.size() > nodeLimit) break;
         if (System.nanoTime() - deadline > 0) break;
         if (Thread.currentThread().isInterrupted()) break;
         if ((long)forward.peekDistance() + reverse.peekDistance() - bestDistance >= 0) break;

         boolean expandingReverse = reverse.peekDistance() < forward.peekDistance();
         SearchFrontier frontier = expandingReverse ? reverse : forward;
         SearchFrontier opposite = expandingReverse ? forward : reverse;
         int tile = frontier.removeNext();
         int distance = frontier.distances.get(tile);
         int oppositeDistance = opposite.distances.get(tile);
         if (oppositeDistance != Integer.MAX_VALUE && distance + oppositeDistance < bestDistance) {
            bestDistance = distance + oppositeDistance;
            meetingTile = tile;
         }

         int x = unpackX(tile);
         int y = unpackY(tile);
         int plane = unpackPlane(tile);
         for (int direction = 0; direction < DELTA_X.length; direction++) {
            int nextX = x + DELTA_X[direction];
            int nextY = y + DELTA_Y[direction];
            int nextTile = packWorldPoint(nextX, nextY, plane);
            int fromX = expandingReverse ? nextX : x;
            int fromY = expandingReverse ? nextY : y;
            int toX = expandingReverse ? x : nextX;
            int toY = expandingReverse ? y : nextY;
            WorldPoint nextPoint = new WorldPoint(nextX, nextY, plane);
            if (!isWildernessBlocked(nextPoint)) {
               boolean traversable = this.canStep(fromX, fromY, plane, toX, toY, openEdges, canUseTransport);
               if (!traversable && direction < 4 && this.isTileIsolated(toX, toY, plane)) {
                  int transportOrigin = expandingReverse ? tile : nextTile;
                  traversable = this.hasUsableOutgoingTransport(transportOrigin, excludedTransports, canUseTransport);
               }
               if (traversable) frontier.enqueueIfShorter(nextTile, tile, distance + 1);
            }
         }

         List<TransportCatalog.TransportEdge> transportEdges =
            (expandingReverse ? this.transportsByDestination : this.transportsByOrigin).get(tile);
         if (transportEdges != null) {
            for (TransportCatalog.TransportEdge transport : transportEdges) {
               WorldPoint destination = expandingReverse ? transport.origin : transport.destination;
               if (!excludedTransports.contains(transport) && canUseTransport.test(transport) && !isWildernessBlocked(destination)) {
                  int cost = "Max POH".equals(transport.action)
                     ? (transport.target != null && transport.target.endsWith(":INSIDE") ? 12 : 28) : 4;
                  frontier.enqueueIfShorter(packWorldPoint(destination), tile, distance + cost);
               }
            }
         }
      }

      boolean exhausted = forward.peekDistance() == Integer.MAX_VALUE || reverse.peekDistance() == Integer.MAX_VALUE;
      // A found meeting tile is a valid route even if the 2-second budget elapsed
      // before Dijkstra could prove it was the globally shortest route.
      if (meetingTile != -1 && !Thread.currentThread().isInterrupted()) {
         List<WorldPoint> path = new ArrayList<>(reconstructPath(meetingTile, forward.parents));
         List<WorldPoint> reversePath = reconstructPath(meetingTile, reverse.parents);
         for (int index = reversePath.size() - 2; index >= 0; index--) path.add(reversePath.get(index));
         return new RouteSearchResult(Collections.unmodifiableList(path), WalkerEngine.PlanningFailure.NONE, bestDistance);
      }
      return new RouteSearchResult(Collections.emptyList(),
         exhausted ? WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED : WalkerEngine.PlanningFailure.SEARCH_LIMIT,
         Integer.MAX_VALUE);
   }

   private static boolean relaxSearchNode(int tile, int parent, int distance, Int2IntOpenHashMap parents,
      Int2IntOpenHashMap distances, IntArrayFIFOQueue[] distanceBuckets) {
      if (distance >= distances.get(tile)) return false;
      parents.put(tile, parent);
      distances.put(tile, distance);
      distanceBuckets[distance % distanceBuckets.length].enqueue(tile);
      return true;
   }

   boolean isStandable(WorldPoint point) {
      return this.transportEndpoints.contains(packWorldPoint(point)) || !this.isTileIsolated(point.getX(), point.getY(), point.getPlane());
   }

   boolean canStep(int fromX, int fromY, int plane, int toX, int toY) {
      return this.canStep(fromX, fromY, plane, toX, toY, Collections.emptySet(), this.transportPredicate);
   }

   private boolean canStep(int fromX, int fromY, int plane, int toX, int toY, Set<Long> openEdges,
      Predicate<TransportCatalog.TransportEdge> canUseTransport) {
      int deltaX = toX - fromX;
      int deltaY = toY - fromY;
      if (Math.abs(deltaX) <= 1 && Math.abs(deltaY) <= 1 && (deltaX != 0 || deltaY != 0)) {
         int packedFrom = packWorldPoint(fromX, fromY, plane);
         int packedTo = packWorldPoint(toX, toY, plane);
         if (deltaX != 0 && deltaY != 0) {
            if (this.hasTraversableEdgeTransport(packedFrom, packWorldPoint(fromX + deltaX, fromY, plane), canUseTransport)
               || this.hasTraversableEdgeTransport(packedFrom, packWorldPoint(fromX, fromY + deltaY, plane), canUseTransport)
               || openEdges.contains(packUndirectedEdge(packedFrom, packWorldPoint(fromX + deltaX, fromY, plane)))
               || openEdges.contains(packUndirectedEdge(packedFrom, packWorldPoint(fromX, fromY + deltaY, plane)))) {
               return false;
            } else if (this.isTileIsolated(fromX, fromY, plane)) {
               return !this.isTileIsolated(toX, toY, plane) && !this.isTileIsolated(fromX + deltaX, fromY, plane)
                  && !this.isTileIsolated(fromX, fromY + deltaY, plane);
            } else {
               return deltaX < 0
                  ? (deltaY < 0
                     ? this.isSouthEdgeOpen(fromX, fromY, plane) && this.isWestEdgeOpen(fromX, fromY - 1, plane)
                        && this.isWestEdgeOpen(fromX, fromY, plane) && this.isSouthEdgeOpen(fromX - 1, fromY, plane)
                     : this.isNorthEdgeOpen(fromX, fromY, plane) && this.isWestEdgeOpen(fromX, fromY + 1, plane)
                        && this.isWestEdgeOpen(fromX, fromY, plane) && this.isNorthEdgeOpen(fromX - 1, fromY, plane))
                  : (deltaY < 0
                     ? this.isSouthEdgeOpen(fromX, fromY, plane) && this.isEastEdgeOpen(fromX, fromY - 1, plane)
                        && this.isEastEdgeOpen(fromX, fromY, plane) && this.isSouthEdgeOpen(fromX + 1, fromY, plane)
                     : this.isNorthEdgeOpen(fromX, fromY, plane) && this.isEastEdgeOpen(fromX, fromY + 1, plane)
                        && this.isEastEdgeOpen(fromX, fromY, plane) && this.isNorthEdgeOpen(fromX + 1, fromY, plane));
            }
         } else if (this.hasTraversableEdgeTransport(packedFrom, packedTo, canUseTransport) || openEdges.contains(packUndirectedEdge(packedFrom, packedTo))) {
            return true;
         } else if (this.isTileIsolated(fromX, fromY, plane)) {
            return !this.isTileIsolated(toX, toY, plane);
         } else {
            return deltaX < 0 ? this.isWestEdgeOpen(fromX, fromY, plane)
               : (deltaX > 0 ? this.isEastEdgeOpen(fromX, fromY, plane)
                  : (deltaY < 0 ? this.isSouthEdgeOpen(fromX, fromY, plane) : this.isNorthEdgeOpen(fromX, fromY, plane)));
         }
      }
      return false;
   }

   private boolean hasTraversableEdgeTransport(int fromTile, int toTile, Predicate<TransportCatalog.TransportEdge> canUseTransport) {
      List<TransportCatalog.TransportEdge> doors = this.doorEdgesByOrigin.get(fromTile);
      return doors != null && doors.stream().anyMatch(door -> packWorldPoint(door.destination) == toTile && canUseTransport.test(door));
   }

   private boolean hasUsableOutgoingTransport(int origin, Set<TransportCatalog.TransportEdge> excludedTransports,
      Predicate<TransportCatalog.TransportEdge> canUseTransport) {
      List<TransportCatalog.TransportEdge> transports = this.transportsByOrigin.get(origin);
      return transports != null && transports.stream().anyMatch(transport ->
         !transport.isRestrictedDoor() && !excludedTransports.contains(transport) && canUseTransport.test(transport));
   }

   private boolean isTileIsolated(int x, int y, int plane) {
      return !this.isNorthEdgeOpen(x, y, plane) && !this.isSouthEdgeOpen(x, y, plane)
         && !this.isEastEdgeOpen(x, y, plane) && !this.isWestEdgeOpen(x, y, plane);
   }

   private boolean isNorthEdgeOpen(int x, int y, int plane) { return this.isCollisionEdgeOpen(x, y, plane, 0); }
   private boolean isSouthEdgeOpen(int x, int y, int plane) { return this.isNorthEdgeOpen(x, y - 1, plane); }
   private boolean isEastEdgeOpen(int x, int y, int plane) { return this.isCollisionEdgeOpen(x, y, plane, 1); }
   private boolean isWestEdgeOpen(int x, int y, int plane) { return this.isEastEdgeOpen(x - 1, y, plane); }

   private boolean isCollisionEdgeOpen(int x, int y, int plane, int edgeDirection) {
      long[] words = this.collisionRegions.get(packRegionKey(x / 64, y / 64));
      if (words == null || plane < 0 || plane > 3) return false;
      int bitIndex = (plane * 64 * 64 + (y & 63) * 64 + (x & 63)) * 2 + edgeDirection;
      int wordIndex = bitIndex >>> 6;
      return wordIndex < words.length && (words[wordIndex] >>> (bitIndex & 63) & 1L) != 0;
   }

   private static List<WorldPoint> reconstructPath(int tile, Int2IntOpenHashMap parents) {
      List<WorldPoint> path = new ArrayList<>();
      while (tile != -1) {
         path.add(new WorldPoint(unpackX(tile), unpackY(tile), unpackPlane(tile)));
         tile = parents.get(tile);
      }
      Collections.reverse(path);
      return Collections.unmodifiableList(path);
   }

   private static int packWorldPoint(WorldPoint point) { return packWorldPoint(point.getX(), point.getY(), point.getPlane()); }
   static long packUndirectedEdge(int fromX, int fromY, int plane, int toX, int toY) {
      return packUndirectedEdge(packWorldPoint(fromX, fromY, plane), packWorldPoint(toX, toY, plane));
   }
   private static long packUndirectedEdge(int first, int second) {
      int lower = Math.min(first, second);
      int upper = Math.max(first, second);
      return (long)lower << 32 | upper & 4294967295L;
   }
   private static int packWorldPoint(int x, int y, int plane) { return x & 32767 | (y & 32767) << 15 | (plane & 3) << 30; }
   private static int unpackX(int packed) { return packed & 32767; }
   private static int unpackY(int packed) { return packed >>> 15 & 32767; }
   private static int unpackPlane(int packed) { return packed >>> 30 & 3; }
   private static int packRegionKey(int x, int y) { return x & 0xFFFF | (y & 0xFFFF) << 16; }

   private static InputStream openCollisionMap() throws IOException {
      InputStream bundled = WalkerEngineResources.open(COLLISION_RESOURCE.value());
      if (bundled != null) return bundled;
      Path path = Path.of(REPOSITORY_COLLISION_PATH.value());
      if (!Files.isRegularFile(path)) path = Path.of(MODULE_COLLISION_PATH.value());
      return Files.newInputStream(path);
   }

   /** Public descriptor decoder. */
   public static String ifpkhxbonq(byte[] descriptor, int seed) {
      return decodeLegacyDescriptor(descriptor, seed);
   }

   private static String decodeLegacyDescriptor(byte[] descriptor, int seed) {
      byte[] decimalSeed = Integer.toString(seed).getBytes();
      int length = (descriptor[0] & 255) << 24 | (descriptor[1] & 255) << 16
         | (descriptor[2] & 255) << 8 | descriptor[3] & 255;
      int offset = (descriptor[4] & 255) << 24 | (descriptor[5] & 255) << 16
         | (descriptor[6] & 255) << 8 | descriptor[7] & 255;
      byte[] bytes = legacyDescriptorTable.substring(offset, offset + length).getBytes(StandardCharsets.UTF_16BE);
      for (int index = 0; index < bytes.length; index++) bytes[index] ^= decimalSeed[index % decimalSeed.length];
      return new String(bytes, StandardCharsets.UTF_16BE);
   }

   private static String createLegacyDescriptorTable() {
      StringBuilder table = new StringBuilder();
      for (LegacyLiteral literal : new LegacyLiteral[]{ENTRY_SEPARATOR, MODULE_COLLISION_PATH, REPOSITORY_COLLISION_PATH, COLLISION_RESOURCE}) {
         byte[] bytes = encodePayloadString(literal.text, literal.innerSeed).getBytes(StandardCharsets.UTF_16BE);
         byte[] decimalSeed = Integer.toString(literal.outerSeed).getBytes(StandardCharsets.US_ASCII);
         for (int index = 0; index < bytes.length; index++) bytes[index] ^= decimalSeed[index % decimalSeed.length];
         table.append(new String(bytes, StandardCharsets.UTF_16BE));
      }
      return table.toString();
   }

   private static String encodePayloadString(String text, int seed) {
      byte[] bytes = new byte[text.length() * 2];
      for (int index = 0; index < text.length(); index++) {
         seed = seed * 1664525 + 1013904223;
         int value = text.charAt(index) ^ seed >>> 16;
         bytes[index * 2] = (byte)(value >>> 8);
         bytes[index * 2 + 1] = (byte)value;
      }
      return Base64.getEncoder().encodeToString(bytes);
   }

   private static String decodePayloadString(String payload, int seed) {
      byte[] bytes = Base64.getDecoder().decode(payload);
      char[] text = new char[bytes.length >>> 1];
      for (int index = 0; index < text.length; index++) {
         seed = seed * 1664525 + 1013904223;
         int value = (bytes[index * 2] & 255) << 8 | bytes[index * 2 + 1] & 255;
         text[index] = (char)(value ^ seed >>> 16);
      }
      return new String(text).intern();
   }

   static boolean isWildernessBlocked(WorldPoint point) { return WildernessTravelPolicy.plannerBlocked(point, allowWildernessTravel); }

   static {
      log = LoggerFactory.getLogger(CollisionRoutePlanner.class);
      DELTA_X = new int[]{-1, 1, 0, 0, -1, 1, -1, 1};
      DELTA_Y = new int[]{0, 0, -1, 1, -1, -1, 1, 1};
   }

   private static final class LegacyLiteral {
      private final String text;
      private final int offset;
      private final int outerSeed;
      private final int innerSeed;

      private LegacyLiteral(String text, int offset, int outerSeed, int innerSeed) {
         this.text = text;
         this.offset = offset;
         this.outerSeed = outerSeed;
         this.innerSeed = innerSeed;
      }

      private String value() {
         if (Arrays.equals("-0123456789".getBytes(), "-0123456789".getBytes(StandardCharsets.US_ASCII))) return this.text.intern();
         int length = encodePayloadString(this.text, this.innerSeed).length();
         byte[] descriptor = new byte[]{(byte)(length >>> 24), (byte)(length >>> 16), (byte)(length >>> 8), (byte)length,
            (byte)(this.offset >>> 24), (byte)(this.offset >>> 16), (byte)(this.offset >>> 8), (byte)this.offset};
         return decodePayloadString(decodeLegacyDescriptor(descriptor, this.outerSeed), this.innerSeed);
      }
   }

   static final class RouteSearchResult {
      final List<WorldPoint> path;
      final WalkerEngine.PlanningFailure failure;
      final int distance;
      private RouteSearchResult(List<WorldPoint> path, WalkerEngine.PlanningFailure failure, int distance) {
         this.path = path;
         this.failure = failure;
         this.distance = distance;
      }
   }

   private static final class SearchFrontier {
      private final Int2IntOpenHashMap parents;
      private final Int2IntOpenHashMap distances;
      private final IntArrayFIFOQueue[] distanceBuckets;
      private int queuedCount;
      private int currentDistance;

      private SearchFrontier(Collection<WorldPoint> starts) {
         this.parents = new Int2IntOpenHashMap();
         this.distances = new Int2IntOpenHashMap();
         this.distanceBuckets = new IntArrayFIFOQueue[29];
         this.distances.defaultReturnValue(Integer.MAX_VALUE);
         for (int index = 0; index < this.distanceBuckets.length; index++) this.distanceBuckets[index] = new IntArrayFIFOQueue();
         for (WorldPoint start : starts) {
            if (start != null && !CollisionRoutePlanner.isWildernessBlocked(start)) this.enqueueIfShorter(CollisionRoutePlanner.packWorldPoint(start), -1, 0);
         }
      }

      private void enqueueIfShorter(int tile, int parent, int distance) {
         if (CollisionRoutePlanner.relaxSearchNode(tile, parent, distance, this.parents, this.distances, this.distanceBuckets)) this.queuedCount++;
      }

      private int peekDistance() {
         while (this.queuedCount > 0) {
            IntArrayFIFOQueue bucket = this.distanceBuckets[this.currentDistance % this.distanceBuckets.length];
            if (bucket.isEmpty()) {
               this.currentDistance++;
            } else {
               if (this.distances.get(bucket.firstInt()) == this.currentDistance) return this.currentDistance;
               bucket.dequeueInt();
               this.queuedCount--;
            }
         }
         return Integer.MAX_VALUE;
      }

      private int removeNext() {
         this.queuedCount--;
         return this.distanceBuckets[this.currentDistance % this.distanceBuckets.length].dequeueInt();
      }
   }
}
