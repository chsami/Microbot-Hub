package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.awt.Color;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.TravelCombatPolicy;
import net.runelite.client.plugins.microbot.droslayer.walker.WalkerMouseControl;
import net.runelite.client.plugins.microbot.droslayer.walker.WalkerTabState;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2MiniMap;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WalkerEngine {
   private static final Logger log;
   private static final int[] TELEPORT_PREP_ITEM_IDS;
   private static final int[] TELEPORT_PREP_TARGET_QUANTITIES;
   private static final WalkerEngine.RouteRequest CANCELLED_REQUEST;
   private final Client client;
   private final WalkerEngineConfig config;
   private final List<TransportCatalog.TransportEdge> transports;
   private final FairyRingNetwork fairyRings;
   private final QuetzalNetwork quetzals;
   private final List<TeleportProviders.TeleportProvider> teleportProviders;
   private final Set<Integer> transportObjectIds;
   private final CollisionRoutePlanner planner;
   private volatile WalkerEngine.RouteRequest activeRequest;
   private volatile WalkerEngine.LocalRoute currentLocalRoute;
   private volatile List<List<WorldPoint>> routeSegments;
   private volatile WalkerEngine.TransportMarker activeTransportMarker;
   private volatile WalkerEngine.Status status;
   private volatile WalkerEngine.PlanningFailure planningFailure;
   private String failureMessage;
   private BankSupplyController bankSupplyPlan;
   private List<WorldPoint> globalRoute;
   private int globalRouteIndex;
   private WalkerEngine.RouteRequest executionRequest;
   private List<WalkerEngine.LocalRoute> localRouteSegments;
   private List<TransportCatalog.TransportEdge> localRouteTransports;
   private TransportCatalog.TransportEdge activeTransport;
   private WalkerEngine.TransportAttempt transportAttempt;
   private TeleportProviders.TeleportProvider activeTeleportProvider;
   private WorldPoint expectedTeleportDestination;
   private WalkerEngine.TeleportAttempt teleportAttempt;
   private int teleportAttempts;
   private final Set<Integer> excludedWhistleSlots;
   private final Set<Integer> depletedWhistleSlots;
   private int inventoryRevision;
   private int[] whistleInventorySnapshot;
   private int lastTeleportActionTick;
   private int lastTransitionTick;
   private int localSegmentIndex;
   private int transportAttempts;
   private WalkerEngine.PendingDoorInteraction pendingDoor;
   private int lastWalkClickTick;
   private boolean lastClickUsedMinimap;
   private int clickOriginSceneX;
   private int clickOriginSceneY;
   private WorldPoint walkTarget;
   private boolean initialWalkClickPending;
   private boolean initialTransportClickPending;
   private boolean preflightComplete;
   private int lastPreflightActionTick;
   private int autoRetaliateAttempts;
   private int runEnableAttempts;
   private String preflightEquipmentName;
   private int equipmentAttempts;
   private String lastDecision;
   private final Map<WorldPoint, Integer> doorAttemptsByLocation;
   private final Set<WorldPoint> excludedDoorLocations;
   private final Set<TransportCatalog.TransportEdge> excludedTransports;
   private final Set<TeleportProviders.TeleportProvider> excludedTeleportProviders;
   private final Set<Integer> failedBankWithdrawalItems;
   private int pendingBankWithdrawalItem;
   private int pendingBankInventoryQuantity;
   private int pendingBankWithdrawalTick;
   private final Executor suppliedPlanningExecutor;
   private final Executor completionExecutor;
   private ExecutorService ownedPlanningExecutor;
   private FutureTask<WalkerEngine.PlanningResult> pendingPlanTask;
   private WalkerEngine.PlanningSnapshot pendingPlanSnapshot;
   private static byte[] LEGACY_OUTER_KEY = new byte[]{
         111, 62, 72, 92, 61, 115, 47, 2, 93, 80, 59, 72, 5, 64, 60, 92, 109, 123, 6, 70, 122, 43, 119, 94, 64, 118,
         112, 109, 62, 100, 35, 115, 109, 122, 126, 24, 88, 63, 17, 60, 118, 11, 110, 8, 123, 39, 38, 121, 93, 103,
         108, 120, 108, 121, 62, 121, 100, 12, 113, 28, 95, 125, 50, 126, 86, 81, 51, 81, 116, 28, 15, 90, 61, 117,
         41, 22, 23, 20, 124, 26, 79, 95, 45, 20, 36, 98, 27, 93, 126, 27, 95, 73, 49, 27, 47, 77
      };

   @Inject
   WalkerEngine(Client client, WalkerEngineConfig config) {
      this(client, config, TransportCatalog.loadAllTransports(), null, null,
         action -> Microbot.getClientThread().invokeLater(action));
   }

   private static String decodePayloadString(String encoded, int seed) {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      char[] result = new char[bytes.length >>> 1];
      for (int index = 0; index < result.length; index++) {
         seed = seed * 1664525 + 1013904223;
         int value = (bytes[index << 1] & 255) << 8 | bytes[(index << 1) + 1] & 255;
         result[index] = (char)(value ^ seed >>> 16);
      }
      return new String(result).intern();
   }

   /**
    * Keep the two original decoder evaluation points while showing their actual
    * text in the source. Legacy builds used the platform encoding for seed
    * digits. Ordinary decimal encodings can use the interned literal directly;
    * other encodings still reproduce the original result or decoding failure.
    */
   private static String prepareLegacyString(String text, int outerSeed, int innerSeed) {
      if (usesAsciiDecimalEncoding()) {
         return text;
      }
      byte[] bytes = encodePayloadString(text, innerSeed).getBytes(StandardCharsets.UTF_16);
      byte[] decimalSeed = Integer.toString(outerSeed).getBytes(StandardCharsets.US_ASCII);
      for (int index = 0; index < bytes.length; index++) {
         bytes[index] ^= decimalSeed[index % decimalSeed.length];
         bytes[index] ^= LEGACY_OUTER_KEY[index % LEGACY_OUTER_KEY.length];
      }
      return qhyyohwqbz(bytes, outerSeed);
   }

   private static String finishLegacyString(String prepared, int innerSeed) {
      return usesAsciiDecimalEncoding() ? prepared.intern() : decodePayloadString(prepared, innerSeed);
   }

   private static boolean usesAsciiDecimalEncoding() {
      return Arrays.equals("-0123456789".getBytes(), "-0123456789".getBytes(StandardCharsets.US_ASCII));
   }

   /** Inverse of the documented legacy LCG codec, used only for compatibility. */
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

   private boolean canUseTransport(TransportCatalog.TransportEdge transport) {
      if (!this.isTransportEnabledBySettings(transport) || !transport.meetsRequirements()) return false;
      if (FairyRingNetwork.isFairyRingTransport(transport) && !this.fairyRings.isTransportAvailable(this.client, transport)) return false;
      if (QuetzalNetwork.isQuetzalTransport(transport) && !this.quetzals.isTransportAvailable(this.client, transport)) return false;
      if (!StrongholdSecurity.isFloorPortal(transport.objectId)) return true;
      Player player = this.client.getLocalPlayer();
      if (player == null) return false;
      int objectId = transport.objectId;
      int combatLevel = player.getCombatLevel();
      Client currentClient = this.client;
      return StrongholdSecurity.canUsePortal(objectId, combatLevel, currentClient::getVarbitValue);
   }

   boolean isTransportEnabledBySettings(TransportCatalog.TransportEdge transport) {
      if (FairyRingNetwork.isFairyRingTransport(transport)) return this.config.useFairyRings();
      if (transport.isAgilityShortcut()) {
         if (!this.config.allowAgilityShortcuts()) return false;
         if (transport.requiresGrapple() && !this.config.allowGrappleShortcuts()) return false;
      }
      return true;
   }

   WalkerEngine(Client client, WalkerEngineConfig config, List<TransportCatalog.TransportEdge> transports,
      CollisionRoutePlanner planner, Executor planningExecutor, Executor completionExecutor) {
      this.fairyRings = TransportCatalog.loadFairyRings();
      this.quetzals = TransportCatalog.loadQuetzals();
      this.routeSegments = Collections.emptyList();
      this.status = WalkerEngine.Status.IDLE;
      this.planningFailure = WalkerEngine.PlanningFailure.NONE;
      this.globalRoute = Collections.emptyList();
      this.localRouteSegments = Collections.emptyList();
      this.localRouteTransports = Collections.emptyList();
      this.excludedWhistleSlots = new HashSet<>();
      this.depletedWhistleSlots = new HashSet<>();
      this.lastTeleportActionTick = -1;
      this.lastTransitionTick = -1;
      this.lastWalkClickTick = -1;
      this.lastPreflightActionTick = -1;
      this.lastDecision = "idle";
      this.doorAttemptsByLocation = new HashMap<>();
      this.excludedDoorLocations = new HashSet<>();
      this.excludedTransports = new HashSet<>();
      this.excludedTeleportProviders = new HashSet<>();
      this.failedBankWithdrawalItems = new HashSet<>();
      this.pendingBankWithdrawalItem = -1;
      this.pendingBankWithdrawalTick = -1;
      this.client = client;
      this.config = config;
      this.transports = transports;
      this.teleportProviders = planner == null ? new ArrayList<>(TeleportProviders.createProviders()) : Collections.emptyList();
      if (planner == null) this.teleportProviders.addAll(this.quetzals.createWhistleProviders(client, this.excludedWhistleSlots));
      this.transportObjectIds = collectTransportObjectIds(transports);
      this.planner = planner == null ? CollisionRoutePlanner.create(transports, this::canUseTransport) : planner;
      this.suppliedPlanningExecutor = planningExecutor;
      this.completionExecutor = completionExecutor;
   }

   private static Set<Integer> collectTransportObjectIds(List<TransportCatalog.TransportEdge> transports) {
      return transports.stream().filter(TransportCatalog.TransportEdge::isRestrictedDoor)
         .map(transport -> transport.objectId).collect(Collectors.toSet());
   }

   public boolean walkTo(WorldPoint destination) {
      this.resetBankWithdrawalTracking();
      return this.startRouteTo(destination);
   }

   public boolean walkToNearestBank() {
      this.resetBankWithdrawalTracking();
      this.activeRequest = new WalkerEngine.RouteRequest(null, true, true);
      this.status = WalkerEngine.Status.WALKING;
      this.planningFailure = WalkerEngine.PlanningFailure.NONE;
      return true;
   }

   boolean followSuppliedRoute(List<WorldPoint> suppliedRoute) {
      if (suppliedRoute.isEmpty()) {
         return false;
      }

      this.resetBankWithdrawalTracking();
      this.activeRequest = new WalkerEngine.RouteRequest((WorldPoint)suppliedRoute.get(0), true, false, Collections.unmodifiableList(new ArrayList<>(suppliedRoute)));
      this.status = WalkerEngine.Status.WALKING;
      this.planningFailure = WalkerEngine.PlanningFailure.NONE;
      return true;
   }

   public boolean prepareTeleportResourcesAtBank() {
      if (!Rs2Bank.isOpen()) {
         this.resetBankWithdrawalTracking();
         return false;
      }
      int currentTick = this.client.getTickCount();
      if (this.pendingBankWithdrawalItem >= 0) {
         if (Rs2Inventory.itemQuantity(this.pendingBankWithdrawalItem) != this.pendingBankInventoryQuantity) {
            this.pendingBankWithdrawalItem = -1;
         } else {
            if (currentTick - this.pendingBankWithdrawalTick < 6) return false;
            this.failedBankWithdrawalItems.add(this.pendingBankWithdrawalItem);
            this.pendingBankWithdrawalItem = -1;
         }
      }

      Map<Integer, Integer> bankQuantities = Rs2Bank.bankItems().stream()
         .collect(Collectors.toMap(item -> item.getId(), item -> item.getQuantity(), Integer::sum));
      int staffItem = Microbot.getClientThread().runOnClientThreadOptional(() -> {
         bankQuantities.keySet().removeIf(itemId -> !TeleportProviders.isItemAllowedOnWorld(this.client, itemId));
         int emptySlots = Rs2Inventory.emptySlotCount();
         boolean membersWorld = this.client.getWorldType().contains(WorldType.MEMBERS);
         int fairyTaleProgress = this.client.getVarbitValue(2329);
         int lumbridgeDiary = this.client.getVarbitValue(4498);
         boolean equippedStaff = FairyRingNetwork.hasEquippedStaff();
         return FairyRingNetwork.findBankedStaffItem(emptySlots, membersWorld, fairyTaleProgress, lumbridgeDiary,
            equippedStaff, Rs2Inventory::itemQuantity, itemId -> bankQuantities.getOrDefault(itemId, 0), this.failedBankWithdrawalItems);
      }).orElseThrow(() -> {
         String message = prepareLegacyString("Teleport bank prerequisites are unavailable", 1785658231, 1781940813);
         return new IllegalStateException(finishLegacyString(message, 1781940813));
      });
      int nextItem = staffItem >= 0 ? staffItem : selectNextBankSupplyItem(Rs2Inventory.emptySlotCount(),
         Rs2Inventory::itemQuantity, itemId -> bankQuantities.getOrDefault(itemId, 0), this.failedBankWithdrawalItems);
      if (nextItem < 0) return true;

      int quantity = 1;
      if (staffItem < 0 && Arrays.stream(QuetzalNetwork.WHISTLE_ITEM_IDS).noneMatch(itemId -> itemId == nextItem)) {
         int itemIndex = 0;
         while (TELEPORT_PREP_ITEM_IDS[itemIndex] != nextItem) itemIndex++;
         quantity = Math.min(TELEPORT_PREP_TARGET_QUANTITIES[itemIndex] - Rs2Inventory.itemQuantity(nextItem), bankQuantities.get(nextItem));
      }
      this.pendingBankWithdrawalItem = nextItem;
      this.pendingBankInventoryQuantity = Rs2Inventory.itemQuantity(nextItem);
      this.pendingBankWithdrawalTick = currentTick;
      if (!Rs2Bank.withdrawX(nextItem, quantity)) {
         this.failedBankWithdrawalItems.add(nextItem);
         this.pendingBankWithdrawalItem = -1;
      }
      return false;
   }

   static int selectNextBankSupplyItem(int emptySlots, IntUnaryOperator inventoryQuantity,
      IntUnaryOperator bankQuantity, Set<Integer> failedItemIds) {
      if (emptySlots > 0 && Arrays.stream(QuetzalNetwork.WHISTLE_ITEM_IDS).noneMatch(itemId -> inventoryQuantity.applyAsInt(itemId) > 0)) {
         for (int itemId : QuetzalNetwork.WHISTLE_ITEM_IDS) {
            if (bankQuantity.applyAsInt(itemId) > 0 && !failedItemIds.contains(itemId)) return itemId;
         }
      }
      for (int index = 0; index < TELEPORT_PREP_ITEM_IDS.length; index++) {
         int itemId = TELEPORT_PREP_ITEM_IDS[index];
         int carried = inventoryQuantity.applyAsInt(itemId);
         int required = TELEPORT_PREP_TARGET_QUANTITIES[index];
         if (carried < required && (carried > 0 || emptySlots > 0)
            && bankQuantity.applyAsInt(itemId) > 0 && !failedItemIds.contains(itemId)) return itemId;
      }
      return -1;
   }

   private void resetBankWithdrawalTracking() {
      this.pendingBankWithdrawalItem = -1;
      this.pendingBankWithdrawalTick = -1;
      this.failedBankWithdrawalItems.clear();
   }

   boolean previewRouteTo(WorldPoint destination) {
      return this.requestRoute(destination, false);
   }

   boolean startRouteTo(WorldPoint destination) {
      return this.requestRoute(destination, true);
   }

   boolean commitPreviewOrStartRoute(WorldPoint destination) {
      if (this.bankSupplyPlan == null && this.status == WalkerEngine.Status.PREVIEW && destination.equals(this.getDestination()) && (this.currentLocalRoute != null || this.activeTeleportProvider != null)) {
         this.activeRequest = new WalkerEngine.RouteRequest(destination, true, false);
         this.executionRequest = this.activeRequest;
         this.status = WalkerEngine.Status.WALKING;
         this.initialWalkClickPending = true;
         this.initialTransportClickPending = true;
         this.resetPreflight();
         return true;
      } else {
         return this.startRouteTo(destination);
      }
   }

   private boolean requestRoute(WorldPoint destination, boolean execute) {
      if (destination == null) return false;
      String preparedMessage = prepareLegacyString("request mode={} target={}", 715921857, -1351160869);
      String message = finishLegacyString(preparedMessage, -1351160869);
      Object[] arguments = new Object[2];
      String mode;
      if (execute) {
         String preparedMode = prepareLegacyString("walk", 980969538, 714940848);
         mode = finishLegacyString(preparedMode, 714940848);
      } else {
         String preparedMode = prepareLegacyString("preview", 1357258553, -1532920051);
         mode = finishLegacyString(preparedMode, -1532920051);
      }
      arguments[0] = mode;
      arguments[1] = destination;
      this.debug(message, arguments);
      this.activeRequest = new WalkerEngine.RouteRequest(destination, execute, false);
      this.status = execute ? WalkerEngine.Status.WALKING : WalkerEngine.Status.PREVIEW;
      this.planningFailure = WalkerEngine.PlanningFailure.NONE;
      return true;
   }

   public void cancel() {
      if (this.client.isClientThread()) {
         this.resetNavigation();
      } else {
         this.activeRequest = CANCELLED_REQUEST;
         this.status = WalkerEngine.Status.IDLE;
         this.planningFailure = WalkerEngine.PlanningFailure.NONE;
      }
   }

   private void resetNavigation() {
      pohTiming.reset();
      String message = finishLegacyString(prepareLegacyString("cancel destination={} status={}", 1662066888, 444687075), 444687075);
      this.debug(message, this.getDestination(), this.status);
      this.activeRequest = null;
      this.executionRequest = null;
      this.bankSupplyPlan = null;
      this.currentLocalRoute = null;
      this.globalRoute = Collections.emptyList();
      this.globalRouteIndex = 0;
      this.routeSegments = Collections.emptyList();
      this.activeTransportMarker = null;
      this.localRouteSegments = Collections.emptyList();
      this.localRouteTransports = Collections.emptyList();
      this.activeTransport = null;
      this.transportAttempt = null;
      this.activeTeleportProvider = null;
      this.expectedTeleportDestination = null;
      this.teleportAttempt = null;
      this.lastTeleportActionTick = -1;
      this.resetBankWithdrawalTracking();
      this.localSegmentIndex = 0;
      this.transportAttempts = 0;
      this.pendingDoor = null;
      this.initialWalkClickPending = false;
      this.initialTransportClickPending = false;
      this.lastWalkClickTick = -1;
      this.walkTarget = null;
      this.doorAttemptsByLocation.clear();
      this.excludedDoorLocations.clear();
      this.excludedTransports.clear();
      this.excludedTeleportProviders.clear();
      this.excludedWhistleSlots.retainAll(this.depletedWhistleSlots);
      this.teleportAttempts = 0;
      this.cancelPendingPlan();
      this.status = WalkerEngine.Status.IDLE;
      this.planningFailure = WalkerEngine.PlanningFailure.NONE;
   }

   void shutdown() {
      this.refreshTeleportInventory();
      this.cancelPendingPlan();
      if (this.ownedPlanningExecutor != null) {
         this.ownedPlanningExecutor.shutdownNow();
         this.ownedPlanningExecutor = null;
      }
   }

   public WorldPoint getDestination() {
      WalkerEngine.RouteRequest request = this.activeRequest;
      return request == null ? null : request.destination;
   }

   List<List<WorldPoint>> getRouteSegments() {
      return this.routeSegments;
   }

   List<WorldPoint> getRemainingRoute() {
      if (this.routeSegments.isEmpty()) {
         return Collections.emptyList();
      }

      if (!this.globalRoute.isEmpty()) {
         return this.globalRoute.subList(this.globalRouteIndex, this.globalRoute.size());
      }

      List<WorldPoint> remaining = new ArrayList<>();

      for (int segmentIndex = this.localSegmentIndex; segmentIndex < this.routeSegments.size(); segmentIndex++) {
         List<WorldPoint> segment = this.routeSegments.get(segmentIndex);
         int startIndex = segmentIndex == this.localSegmentIndex && this.currentLocalRoute != null ? this.currentLocalRoute.routeIndex : 0;
         remaining.addAll(segment.subList(startIndex, segment.size()));
      }

      return remaining;
   }

   Set<WorldPoint> getTransportEndpoints() {
      Set<WorldPoint> endpoints = new HashSet<>();
      if (this.activeTransportMarker != null) {
         endpoints.add(this.activeTransportMarker.point);
      }

      if (this.activeTransport != null) {
         endpoints.add(this.activeTransport.origin);
         endpoints.add(this.activeTransport.destination);
      }

      for (TransportCatalog.TransportEdge transport : this.localRouteTransports) {
         endpoints.add(transport.origin);
         endpoints.add(transport.destination);
      }

      for (int index = Math.max(1, this.globalRouteIndex); index < this.globalRoute.size(); index++) {
         WorldPoint origin = this.globalRoute.get(index - 1);
         WorldPoint destination = this.globalRoute.get(index);
         if (origin.distanceTo(destination) > 1) {
            endpoints.add(origin);
            endpoints.add(destination);
         }
      }

      return endpoints;
   }

   WalkerEngine.TransportMarker getActiveTransportMarker() {
      return this.activeTransportMarker;
   }

   public WalkerEngine.Status getStatus() {
      return this.status;
   }

   public WalkerEngine.PlanningFailure getPlanningFailure() {
      return this.planningFailure;
   }

   boolean hasActiveExecutionRequest() {
      WalkerEngine.RouteRequest request = this.activeRequest;
      return request != null
         && request != CANCELLED_REQUEST
         && request.execute
         && this.status != WalkerEngine.Status.IDLE
         && this.status != WalkerEngine.Status.ARRIVED
         && this.status != WalkerEngine.Status.BLOCKED;
   }

   private final net.runelite.client.plugins.microbot.droslayer.walker.PohTickTiming pohTiming =
      new net.runelite.client.plugins.microbot.droslayer.walker.PohTickTiming();

   void tick() {
      WalkerEngine.RouteRequest request = this.activeRequest;
      if (request == CANCELLED_REQUEST) {
         this.resetNavigation();
      } else if (request != null && this.client.getGameState() == GameState.LOGGED_IN) {
         if (request != this.executionRequest) {
            pohTiming.reset();
            Player planningPlayer = this.client.getLocalPlayer();
            this.cancelPendingPlan();
            this.executionRequest = request;
            this.bankSupplyPlan = null;
            this.failureMessage = null;
            this.doorAttemptsByLocation.clear();
            this.excludedDoorLocations.clear();
            this.excludedTransports.clear();
            this.excludedTeleportProviders.clear();
            this.excludedWhistleSlots.retainAll(this.depletedWhistleSlots);
            this.teleportAttempts = 0;
            this.currentLocalRoute = null;
            this.routeSegments = Collections.emptyList();
            this.activeTransportMarker = null;
            this.localRouteSegments = Collections.emptyList();
            this.localRouteTransports = Collections.emptyList();
            this.activeTransport = null;
            this.transportAttempt = null;
            this.activeTeleportProvider = null;
            this.expectedTeleportDestination = null;
            this.teleportAttempt = null;
            this.lastTeleportActionTick = -1;
            this.globalRoute = Collections.emptyList();
            this.globalRouteIndex = 0;
            this.localSegmentIndex = 0;
            this.transportAttempts = 0;
            this.pendingDoor = null;
            this.initialWalkClickPending = request.execute;
            this.initialTransportClickPending = request.execute;
            this.lastWalkClickTick = -1;
            this.resetPreflight();
            if (!this.captureAndStartPlanning(request, planningPlayer, true)) {
               this.routeSegments = Collections.emptyList();
               this.failNavigation("plan-unavailable");
               return;
            }
         }

         if (pohTiming.hold(net.runelite.client.plugins.microbot.droslayer.walker.MaxPohSupport.insideHouse(),
            System.currentTimeMillis())) return;

         if (this.pendingPlanTask != null) {
            if (!this.completePendingPlan(request)) {
               return;
            }

            if (this.status == WalkerEngine.Status.BLOCKED) {
               return;
            }
         }

         if (request.execute && this.status != WalkerEngine.Status.BLOCKED) {
            Player player = this.client.getLocalPlayer();
            if (player == null || player.getLocalLocation() == null || player.getWorldView() == null) {
               this.failNavigation("player-state-unavailable");
            } else if (this.preflightComplete || this.runPreflight()) {
               if (this.bankSupplyPlan == null || this.config.useItemsFromBank() && this.config.useTeleportations()) {
                  if (this.bankSupplyPlan != null && this.bankSupplyPlan.arrivedAtBank) {
                     try {
                        if (this.bankSupplyPlan.tickBankSupplyPreparation(this.client, this)) {
                           this.bankSupplyPlan = null;
                           if (!this.captureAndStartPlanning(request, player, true)) {
                              this.failNavigation("plan-unavailable");
                           }
                        }
                     } catch (IllegalStateException failure) {
                        this.rejectCurrentAction(failure.getMessage());
                     }
                  } else if (this.activeTeleportProvider != null) {
                     this.tickTeleport(request, player);
                  } else if (this.currentLocalRoute == null) {
                     this.failNavigation("route-unavailable-after-planning");
                  } else {
                     WorldView worldView = player.getWorldView();
                     WorldPoint playerLocation = player.getWorldLocation();
                     this.debug(
                        "tick={} status={} player={} moving={} destination={} global={}/{} route={}/{} transition={} pendingTransition={} pendingDoor={} decision={} walkTarget={}",
                        this.client.getTickCount(),
                        this.status,
                        playerLocation,
                        player.getPoseAnimation() != player.getIdlePoseAnimation(),
                        request.destination,
                        this.globalRouteIndex,
                        this.globalRoute.size(),
                        this.currentLocalRoute == null ? -1 : this.currentLocalRoute.routeIndex,
                        this.currentLocalRoute == null ? 0 : this.currentLocalRoute.points.size(),
                        describeTransport(this.activeTransport),
                        this.transportAttempt != null,
                        this.pendingDoor != null,
                        this.lastDecision,
                        this.walkTarget
                     );
                     if (this.transportAttempt != null) {
                        boolean transitionActive = player.getPoseAnimation() != player.getIdlePoseAnimation() || player.getAnimation() != -1;
                        // Hits and blocks while standing still (e.g. wolves east of Prifddinas) toggle the
                        // animation every tick; that is not transport progress and must not defer retries.
                        boolean progressActive = transitionActive
                           && !isStationaryCombatAnimation(player.getHealthRatio(), player.getPoseAnimation() == player.getIdlePoseAnimation(),
                              playerLocation.equals(this.transportAttempt.lastPosition));
                        boolean madeProgress = this.transportAttempt.observeProgress(this.client.getTickCount(), playerLocation, progressActive);
                        boolean transportArrived = hasTransportArrived(this.activeTransport.origin, this.activeTransport.destination, playerLocation, this.transportAttempt.observedProgress);
                        if (QuetzalNetwork.isQuetzalTransport(this.activeTransport)) {
                           transportArrived = this.transportAttempt
                              .confirmSettledLanding(
                                 this.client.getTickCount(),
                                 transportArrived
                                    && !transitionActive
                                    && !QuetzalNetwork.isMapOpen(this.client)
                                    && !QuetzalNetwork.isFadeOverlayVisible(this.client),
                                 madeProgress
                              );
                        }

                        if (transportArrived) {
                           this.debug(
                              "transition-landed expected={} actual={} distance={}",
                              this.activeTransport.destination,
                              playerLocation,
                              playerLocation.distanceTo2D(this.activeTransport.destination)
                           );
                           if (!transitionActive) {
                              this.lastTransitionTick = this.client.getTickCount();
                              this.markInitialClicksPending();
                              if (!this.globalRoute.isEmpty()) {
                                 this.transportAttempt = null;
                                 this.transportAttempts = 0;
                                 this.lastWalkClickTick = -1;
                                 this.pendingDoor = null;
                                 int landingRouteIndex = findRouteIndexAtOrAfter(this.globalRoute, playerLocation, this.globalRouteIndex);
                                 if (landingRouteIndex < 0) {
                                    this.lastDecision = "replan-landing";
                                    this.replanFromCurrentPosition(worldView, playerLocation, this.currentLocalRoute);
                                 } else {
                                    this.globalRouteIndex = landingRouteIndex;
                                    this.currentLocalRoute = this.buildNextGlobalRouteSegment(worldView, playerLocation);
                                    this.localRouteSegments = this.currentLocalRoute == null ? Collections.emptyList() : Collections.singletonList(this.currentLocalRoute);
                                    if (this.currentLocalRoute == null) {
                                       this.failNavigation(
                                          "transition-landing-route-unavailable"
                                       );
                                    } else {
                                       this.status = WalkerEngine.Status.WALKING;
                                    }
                                 }
                              } else {
                                 this.localSegmentIndex++;
                                 this.currentLocalRoute = this.localRouteSegments.get(this.localSegmentIndex);
                                 this.activeTransport = this.localSegmentIndex < this.localRouteTransports.size() ? this.localRouteTransports.get(this.localSegmentIndex) : null;
                                 this.transportAttempt = null;
                                 this.transportAttempts = 0;
                                 this.lastWalkClickTick = -1;
                                 this.pendingDoor = null;
                                 if (!playerLocation.equals(this.currentLocalRoute.points.get(0))) {
                                    this.lastDecision = "replan-landing";
                                    this.replanFromCurrentPosition(worldView, playerLocation, this.currentLocalRoute);
                                 }
                              }
                           }
                        } else if (!QuetzalNetwork.isQuetzalTransport(this.activeTransport) || !this.tickQuetzalSelection()) {
                           if (FairyRingNetwork.isFairyRingTransport(this.activeTransport)) {
                              if (!this.isTransportEnabledBySettings(this.activeTransport)
                                 || !this.fairyRings.isTransportAvailable(this.client, this.activeTransport)
                                 || FairyRingNetwork.getRequiredStaffName(this.client) != null) {
                                 this.failNavigation("fairy-ring-access-lost");
                                 return;
                              }

                              Widget fairyRingWidget = this.client.getWidget(398, 26);
                              if (fairyRingWidget != null && !fairyRingWidget.isHidden()) {
                                 int fairyRingAction = this.fairyRings.nextDialAction(this.client, this.activeTransport);
                                 if (fairyRingAction < 0) {
                                    this.failNavigation("fairy-ring-selection-failed");
                                    return;
                                 }

                                 if (fairyRingAction > 0) {
                                    Widget fairyRingOption = this.client.getWidget(398, fairyRingAction);
                                    this.dispatchMenuInteraction(
                                       new NewMenuEntry()
                                          .option(fairyRingOption.getActions()[0])
                                          .target("")
                                          .identifier(1)
                                          .type(MenuAction.CC_OP)
                                          .param0(-1)
                                          .param1(fairyRingOption.getId())
                                          .itemId(-1),
                                       fairyRingOption.getBounds(),
                                       true
                                    );
                                    this.transportAttempt.lastProgressTick = this.client.getTickCount();
                                 }

                                 this.lastDecision = "fairy-ring-select";
                                 return;
                              }
                           }

                           if (this.tickNpcTransportDialogue()) {
                              return;
                           }

                           if (playerLocation.getPlane() != this.activeTransport.origin.getPlane()) {
                              if (isUnexpectedPlaneStalled(this.client.getTickCount(), this.transportAttempt.lastProgressTick, madeProgress)) {
                                 this.failNavigation("transition-unexpected-plane");
                              }
                           } else if (!this.openDoorDuringTransport(worldView, playerLocation)) {
                              if (!madeProgress) {
                                 if (isTransportRetryDue(this.client.getTickCount(), this.transportAttempt.lastProgressTick)) {
                                    this.debug(
                                       "transition-retry step={} attempts={} elapsedTicks={}",
                                       describeTransport(this.activeTransport),
                                       this.transportAttempts,
                                       this.client.getTickCount() - this.transportAttempt.lastProgressTick
                                    );
                                    if (this.isTransportEntityLoaded(worldView, this.activeTransport) || !this.replanTransportApproach(worldView, playerLocation) && this.client.getTickCount() - this.transportAttempt.startedTick >= 9) {
                                       if (this.transportAttempts < 3 && this.activateTransport(worldView, this.activeTransport, true)) {
                                          this.transportAttempts++;
                                          this.transportAttempt = new WalkerEngine.TransportAttempt(this.client.getTickCount(), playerLocation, transitionActive);
                                       } else {
                                          this.excludeTransportAndReplan(player);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     } else if (!this.globalRoute.isEmpty() && !this.refreshGlobalRouteSegment(worldView, playerLocation)) {
                        this.failNavigation("global-route-extension-failed");
                     } else {
                        WalkerEngine.LocalRoute route = this.currentLocalRoute;
                        if (route != null && worldView.getId() == route.worldView.getId() && worldView.getPlane() == route.plane) {
                           LocalPoint playerLocalPoint = player.getLocalLocation();
                           int sceneX = playerLocalPoint.getSceneX();
                           int sceneY = playerLocalPoint.getSceneY();
                           int[][] flags = this.getCollisionFlags(worldView, route.plane);
                           if (flags != null && flags.length == route.gridWidth) {
                              WalkerEngine.DoorCollisionOverrides doorOverrides = this.buildDoorCollisionOverrides(worldView, route.plane, flags.length, flags[0].length);
                              int routeIndex = locatePlayerOnLocalRoute(route, playerLocation, worldView, flags, doorOverrides.overrides, sceneX, sceneY);
                              if (routeIndex < 0) {
                                 this.lastDecision = "replan-deviation";
                                 this.replanFromCurrentPosition(worldView, playerLocation, route);
                              } else {
                                 route.routeIndex = routeIndex;
                                 this.updateNextDoorMarker(route, doorOverrides, routeIndex);
                                 this.lastDecision = "evaluating";
                                 if (this.activeTransport != null && routeIndex == route.points.size() - 1 && playerLocation.equals(route.points.get(routeIndex))) {
                                    if (this.activateTransport(worldView, this.activeTransport, this.consumeInitialTransportClick())) {
                                       this.transportAttempts = 1;
                                       this.transportAttempt = new WalkerEngine.TransportAttempt(
                                          this.client.getTickCount(),
                                          playerLocation,
                                          player.getPoseAnimation() != player.getIdlePoseAnimation() || player.getAnimation() != -1
                                       );
                                       this.status = WalkerEngine.Status.WALKING;
                                    } else if (isWithinPostTransitionGracePeriod(this.client.getTickCount(), this.lastTransitionTick)) {
                                       this.lastDecision = "wait-transition-scene";
                                    } else {
                                       this.failNavigation(
                                          "transition-object-unavailable-at-approach"
                                       );
                                    }
                                 } else if (this.activeTransport == null && routeIndex == route.points.size() - 1 && playerLocation.equals(route.points.get(routeIndex))) {
                                    if (!this.globalRoute.isEmpty() && !playerLocation.equals(this.globalRoute.get(this.globalRoute.size() - 1))) {
                                       this.currentLocalRoute = this.buildNextGlobalRouteSegment(worldView, playerLocation);
                                       if (this.currentLocalRoute == null) {
                                          this.failNavigation(
                                             "next-global-segment-unavailable"
                                          );
                                       } else {
                                          this.status = WalkerEngine.Status.WALKING;
                                       }
                                    } else {
                                       this.completeCurrentRoute();
                                    }
                                 } else {
                                    int[] remainingTiles = new int[route.points.size() - routeIndex + 1];
                                    int tileCount = 1;
                                    remainingTiles[0] = LocalCollisionGrid.tileIndex(sceneX, sceneY, flags.length);

                                    for (int pathIndex = routeIndex; pathIndex < route.points.size(); pathIndex++) {
                                       if (!route.points.get(pathIndex).equals(playerLocation)) {
                                          LocalPoint routeLocalPoint = LocalPoint.fromWorld(worldView, route.points.get(pathIndex));
                                          if (routeLocalPoint == null) {
                                             break;
                                          }

                                          remainingTiles[tileCount++] = LocalCollisionGrid.tileIndex(
                                             routeLocalPoint.getSceneX(), routeLocalPoint.getSceneY(), flags.length
                                          );
                                       }
                                    }

                                    if (tileCount < 2) {
                                       this.failNavigation(
                                          "local-path-exhausted-before-arrival"
                                       );
                                    } else {
                                       remainingTiles = Arrays.copyOf(remainingTiles, tileCount);
                                       int nextSceneX = LocalCollisionGrid.unpackX(remainingTiles[1], flags.length);
                                       int nextSceneY = LocalCollisionGrid.unpackY(remainingTiles[1], flags.length);
                                       if (!LocalCollisionGrid.canStep(flags, doorOverrides.overrides, sceneX, sceneY, nextSceneX, nextSceneY)
                                          && player.getPoseAnimation() == player.getIdlePoseAnimation()) {
                                          if (this.lastTransitionTick >= 0 && this.client.getTickCount() - this.lastTransitionTick <= 3) {
                                             this.lastDecision = "wait-transition-settle";
                                          } else {
                                             this.lastDecision = "replan-blocked-step";
                                             this.replanFromCurrentPosition(worldView, playerLocation, route);
                                          }
                                       } else {
                                          if (this.pendingDoor != null) {
                                             if (this.handleStrongholdDialogue()) {
                                                return;
                                             }

                                             if (doorOverrides.overrides.get(this.pendingDoor.edgeIndex)
                                                && !LocalCollisionGrid.canStep(
                                                   flags, new BitSet(), this.pendingDoor.fromSceneX, this.pendingDoor.fromSceneY, this.pendingDoor.toSceneX, this.pendingDoor.toSceneY
                                                )) {
                                                if (player.getPoseAnimation() != player.getIdlePoseAnimation()) {
                                                   this.lastDecision = "pending-door-moving";
                                                   return;
                                                }

                                                if (this.client.getTickCount() - this.pendingDoor.lastInteractionTick < 3) {
                                                   return;
                                                }

                                                if (hasExhaustedDoorRetries(this.doorAttemptsByLocation.getOrDefault(this.pendingDoor.location, 0))) {
                                                   this.excludedDoorLocations.add(this.pendingDoor.location);
                                                   this.replanFromCurrentPosition(worldView, playerLocation, route);
                                                   return;
                                                }

                                                this.pendingDoor = null;
                                             } else {
                                                this.doorAttemptsByLocation.remove(this.pendingDoor.location);
                                                this.pendingDoor = null;
                                                this.markInitialClicksPending();
                                             }
                                          }

                                          WalkerEngine.RouteDoor nextDoor = doorOverrides.findNextDoorOnRoute(remainingTiles, flags.length, 0);
                                          if (nextDoor != null && nextDoor.routeIndex <= 4) {
                                             this.lastDecision = "click-door";
                                             this.clickDoorAndTrackPending(nextDoor, remainingTiles, flags.length, this.consumeInitialTransportClick());
                                          } else if (shouldActivateUpcomingTransport(this.activeTransport, route.points.size() - 1 - routeIndex)) {
                                             if (this.activateTransport(worldView, this.activeTransport, this.consumeInitialTransportClick())) {
                                                this.transportAttempts = 1;
                                                this.transportAttempt = new WalkerEngine.TransportAttempt(
                                                   this.client.getTickCount(),
                                                   playerLocation,
                                                   player.getPoseAnimation() != player.getIdlePoseAnimation() || player.getAnimation() != -1
                                                );
                                                this.status = WalkerEngine.Status.WALKING;
                                             } else if (isWithinPostTransitionGracePeriod(this.client.getTickCount(), this.lastTransitionTick)) {
                                                this.lastDecision = "wait-transition-scene";
                                             } else {
                                                this.failNavigation(
                                                   "queued-transition-object-unavailable"
                                                );
                                             }
                                          } else {
                                             boolean moving = player.getPoseAnimation() != player.getIdlePoseAnimation();
                                             if (this.lastWalkClickTick >= 0) {
                                                int clickedTargetIndex = this.walkTarget == null ? -1 : route.points.indexOf(this.walkTarget);
                                                if (moving && clickedTargetIndex > routeIndex + 4) {
                                                   this.lastDecision = "wait-distance";
                                                   return;
                                                }

                                                if (!moving && sceneX == this.clickOriginSceneX && sceneY == this.clickOriginSceneY && this.client.getTickCount() - this.lastWalkClickTick < 2) {
                                                   this.lastDecision = "wait-acceptance";
                                                   return;
                                                }
                                             }

                                             int furthestClickIndex = nextDoor == null ? remainingTiles.length - 1 : nextDoor.routeIndex;

                                             for (int clickIndex = furthestClickIndex; clickIndex > 0; clickIndex--) {
                                                LocalPoint localTarget = LocalPoint.fromScene(
                                                   LocalCollisionGrid.unpackX(remainingTiles[clickIndex], flags.length),
                                                   LocalCollisionGrid.unpackY(remainingTiles[clickIndex], flags.length),
                                                   worldView
                                                );
                                                LocalPoint minimapTarget = localTarget;
                                                if (clickIndex < remainingTiles.length - 1) {
                                                   int[] safe = MinimapClickSafety.safeTarget(flags, doorOverrides.overrides, localTarget.getSceneX(), localTarget.getSceneY());
                                                   if (safe[0] != localTarget.getSceneX() || safe[1] != localTarget.getSceneY()) {
                                                      minimapTarget = LocalPoint.fromScene(safe[0], safe[1], worldView);
                                                   }
                                                }

                                                Point clickPoint = shouldUseCanvasForStep(clickIndex) ? null : this.projectToMinimap(minimapTarget);
                                                boolean useMinimap = clickPoint != null;
                                                if (!useMinimap && shouldUseCanvasForStep(clickIndex)) {
                                                   clickPoint = Perspective.localToCanvas(this.client, localTarget, worldView.getPlane());
                                                   if (!isPointInsideViewport(
                                                      clickPoint,
                                                      this.client.getViewportXOffset(),
                                                      this.client.getViewportYOffset(),
                                                      this.client.getViewportWidth(),
                                                      this.client.getViewportHeight()
                                                   )) {
                                                      clickPoint = null;
                                                   }
                                                }

                                                if (clickPoint != null) {
                                                   WorldPoint worldTarget = WorldPoint.fromLocal(worldView, localTarget.getX(), localTarget.getY(), route.plane);
                                                   boolean sameTarget = this.lastWalkClickTick >= 0 && worldTarget.equals(this.walkTarget);
                                                   boolean switchFromMinimap = sameTarget && this.lastClickUsedMinimap && !useMinimap;
                                                   if (moving && sameTarget && !switchFromMinimap) {
                                                      this.lastDecision = "same-target";
                                                      this.status = WalkerEngine.Status.WALKING;
                                                      return;
                                                   }

                                                   boolean directClick = this.consumeInitialWalkClick() || switchFromMinimap;
                                                   if (useMinimap) {
                                                      this.lastDecision = "click-minimap";
                                                      Microbot.targetMenu = null;
                                                      if (directClick) {
                                                         clickThroughWalkerMouse(clickPoint);
                                                      } else {
                                                         WalkerMouseControl.preClickMove(Microbot.getMouse(), clickPoint).click(clickPoint);
                                                      }
                                                   } else {
                                                      this.lastDecision = "click-canvas";
                                                      this.clickSceneWalkTarget(localTarget, clickPoint, worldView, directClick);
                                                   }

                                                   this.recordWalkClick(sceneX, sceneY, worldTarget, useMinimap);
                                                   this.status = WalkerEngine.Status.WALKING;
                                                   return;
                                                }
                                             }

                                             this.failNavigation("no-clickable-walk-target");
                                          }
                                       }
                                    }
                                 }
                              }
                           } else {
                              this.failNavigation("collision-unavailable");
                           }
                        } else {
                           this.failNavigation("route-world-view-mismatch");
                        }
                     }
                  }
               } else {
                  this.rejectCurrentAction(
                     "Bank teleport option was disabled. Retry the walk with your current settings."
                  );
               }
            }
         }
      }
   }

   static boolean shouldActivateUpcomingTransport(TransportCatalog.TransportEdge transport, int remainingSteps) {
      return transport != null && transport.origin.distanceTo2D(transport.destination) > 0 && remainingSteps <= 4;
   }

   private void replanFromCurrentPosition(WorldView worldView, WorldPoint playerLocation, WalkerEngine.LocalRoute previousRoute) {
      String message = finishLegacyString(prepareLegacyString("replan reason={} player={} destination={} failedDoors={}", 1098992024, -75542461), -75542461);
      this.debug(message, this.lastDecision, playerLocation, this.getDestination(), this.excludedDoorLocations.size());
      if (!this.globalRoute.isEmpty()) {
         Player player = this.client.getLocalPlayer();
         this.globalRoute = Collections.emptyList();
         this.globalRouteIndex = 0;
         this.routeSegments = Collections.emptyList();
         this.currentLocalRoute = null;
         this.localRouteSegments = Collections.emptyList();
         this.pendingDoor = null;
         this.lastWalkClickTick = -1;
         this.walkTarget = null;
         if (this.executionRequest != null && this.captureAndStartPlanning(this.executionRequest, player, false)) return;
         this.failNavigation(finishLegacyString(prepareLegacyString("global-replan-unavailable", 1298098373, 2123668008), 2123668008));
      } else {
         WorldPoint destination = previousRoute.points.get(previousRoute.points.size() - 1);
         this.currentLocalRoute = this.planLocalRoute(worldView, playerLocation, destination, previousRoute.plane);
         this.updateDisplayedRouteSegment(this.localSegmentIndex, this.currentLocalRoute);
         this.pendingDoor = null;
         this.initialWalkClickPending = false;
         this.initialTransportClickPending = false;
         this.lastWalkClickTick = -1;
         this.walkTarget = null;
         if (this.currentLocalRoute == null) {
            this.failNavigation(finishLegacyString(prepareLegacyString("local-replan-unavailable", 1066209431, -257375083), -257375083));
         } else {
            this.status = WalkerEngine.Status.WALKING;
            String readyMessage = finishLegacyString(prepareLegacyString("replan ready routeLength={}", 660473702, 1790709506), 1790709506);
            this.debug(readyMessage, this.currentLocalRoute.points.size());
         }
      }
   }

   private void clickSceneWalkTarget(LocalPoint localTarget, Point clickPoint, WorldView worldView, boolean directClick) {
      NewMenuEntry entry = new NewMenuEntry().param0(localTarget.getSceneX()).param1(localTarget.getSceneY())
         .type(MenuAction.WALK).identifier(0).itemId(-1)
         .option(finishLegacyString(prepareLegacyString("Walk here", 1271533951, 59624252), 59624252))
         .setWorldViewId(worldView.getId());
      if (directClick) {
         Microbot.targetMenu = entry;
         clickThroughWalkerMouse(clickPoint);
      } else {
         WalkerMouseControl.preClickMove(Microbot.getMouse(), clickPoint).click(clickPoint, entry);
      }
   }

   private static void clickThroughWalkerMouse(Point clickPoint) {
      WalkerMouseControl.click(Microbot.getClient(), clickPoint);
   }

   private void resetPreflight() {
      this.preflightComplete = false;
      this.lastPreflightActionTick = -1;
      this.autoRetaliateAttempts = 0;
      this.runEnableAttempts = 0;
      this.preflightEquipmentName = null;
      this.equipmentAttempts = 0;
   }

   private boolean runPreflight() {
      if (shouldDisableAutoRetaliate(TravelCombatPolicy.preflightVarp(this.client, 172))) {
         if (!this.canRetryPreflightAction()) return false;
         if (this.autoRetaliateAttempts >= 3) {
            this.failNavigation(finishLegacyString(prepareLegacyString("preflight-auto-retaliate", 1494091379, -1803159460), -1803159460));
            return false;
         }
         Widget autoRetaliate = this.client.getWidget(38862880);
         if (autoRetaliate != null && !autoRetaliate.isHidden()) {
            this.clickWidgetFirstAction(autoRetaliate);
            this.lastDecision = finishLegacyString(prepareLegacyString("preflight-disable-auto-retaliate", 1213086695, -1621988214), -1621988214);
         } else {
            this.client.runScript(915, 0);
            this.lastDecision = finishLegacyString(prepareLegacyString("preflight-open-combat", 1029316695, 292434487), 292434487);
         }
         this.autoRetaliateAttempts++;
         this.lastPreflightActionTick = this.client.getTickCount();
         return false;
      }
      if (shouldEnableRun(this.client.getEnergy(), TravelCombatPolicy.preflightVarp(this.client, 173))) {
         if (!this.canRetryPreflightAction()) return false;
         if (this.runEnableAttempts >= 3) {
            this.failNavigation(finishLegacyString(prepareLegacyString("preflight-run", 1561508275, 88516381), 88516381));
            return false;
         }
         Widget runToggle = this.client.getWidget(10485788);
         if (runToggle != null && !runToggle.isHidden()) {
            this.clickWidgetFirstAction(runToggle);
            this.lastDecision = finishLegacyString(prepareLegacyString("preflight-enable-run", 1298535473, -2094345744), -2094345744);
         } else {
            this.lastDecision = finishLegacyString(prepareLegacyString("preflight-wait-run-widget", 642074778, 153029707), 153029707);
         }
         this.runEnableAttempts++;
         this.lastPreflightActionTick = this.client.getTickCount();
         return false;
      }

      String equipmentName = this.findEquipmentToEquip();
      if (equipmentName == null) {
         this.preflightComplete = true;
         this.lastDecision = finishLegacyString(prepareLegacyString("preflight-complete", 1772905522, 567272863), 567272863);
         return true;
      }
      if (!equipmentName.equals(this.preflightEquipmentName)) {
         this.preflightEquipmentName = equipmentName;
         this.equipmentAttempts = 0;
         this.lastPreflightActionTick = -1;
      }
      if (!this.canRetryPreflightAction()) return false;
      if (this.equipmentAttempts >= 3) {
         this.replanAfterPreflightFailure(equipmentName);
         return false;
      }
      String dramenStaff = finishLegacyString(prepareLegacyString("Dramen staff", 1094249155, -1224522018), -1224522018);
      if (dramenStaff.equals(equipmentName)
         || finishLegacyString(prepareLegacyString("Lunar staff", 1049150, 1039626417), 1039626417).equals(equipmentName)) {
         Rs2Inventory.wield(equipmentName);
      } else {
         TransportCatalog.equipRequirement(equipmentName);
      }
      this.equipmentAttempts++;
      this.lastPreflightActionTick = this.client.getTickCount();
      String normalizedName = equipmentName.toLowerCase();
      String prefix = finishLegacyString(prepareLegacyString("preflight-equip-", 273307619, -1143231740), -1143231740);
      this.lastDecision = prefix + normalizedName;
      return false;
   }

   private boolean canRetryPreflightAction() {
      return this.lastPreflightActionTick < 0 || this.client.getTickCount() - this.lastPreflightActionTick >= 3;
   }

   private String findEquipmentToEquip() {
      for (TransportCatalog.TransportEdge transport : this.collectPlannedTransports()) {
         if (!this.isTransportEnabledBySettings(transport)) continue;
         String equipment = FairyRingNetwork.isFairyRingTransport(transport)
            ? FairyRingNetwork.getRequiredStaffName(this.client) : transport.getMissingEquipmentRequirement();
         if (equipment != null) return equipment;
      }
      return null;
   }

   private List<TransportCatalog.TransportEdge> collectPlannedTransports() {
      if (this.globalRoute.isEmpty()) return this.localRouteTransports;
      List<TransportCatalog.TransportEdge> planned = new ArrayList<>();
      for (int index = 1; index < this.globalRoute.size(); index++) {
         TransportCatalog.TransportEdge transport = this.findTransportEdge(this.globalRoute.get(index - 1), this.globalRoute.get(index));
         if (transport != null && !planned.contains(transport)) planned.add(transport);
      }
      return planned;
   }

   private void replanAfterPreflightFailure(String equipmentName) {
      for (TransportCatalog.TransportEdge transport : this.collectPlannedTransports()) {
         if (!transport.requiresItem(equipmentName)) {
            if (!FairyRingNetwork.isFairyRingTransport(transport)) {
               continue;
            }

            // Keep the original decode timing and default-charset behavior.
            String dramenStaff = finishLegacyString(prepareLegacyString("Dramen staff", 21729009, -1336783401), -1336783401);
            if (!dramenStaff.equals(equipmentName)) {
               String lunarStaff = finishLegacyString(prepareLegacyString("Lunar staff", 256744489, 901351356), 901351356);
               if (!lunarStaff.equals(equipmentName)) {
                  continue;
               }
            }
         }
         this.excludedTransports.add(transport);
      }

      // Observe the player before discarding the old execution state.
      Player player = this.client.getLocalPlayer();
      this.currentLocalRoute = null;
      this.globalRoute = Collections.emptyList();
      this.routeSegments = Collections.emptyList();
      this.localRouteSegments = Collections.emptyList();
      this.localRouteTransports = Collections.emptyList();
      this.activeTransport = null;
      this.resetPreflight();

      if (this.executionRequest != null && this.captureAndStartPlanning(this.executionRequest, player, true)) {
         // Decodes to "preflight-replan-equipment".
         this.lastDecision = finishLegacyString(prepareLegacyString("preflight-replan-equipment", 1383049042, 563155606), 563155606);
         return;
      }

      // Preserve the original default-locale conversion and its exception order.
      String normalizedEquipment = equipmentName.toLowerCase();
      String failurePrefix = finishLegacyString(prepareLegacyString("preflight-equipment-", 1939054130, -1148856063), -1148856063);
      this.failNavigation(failurePrefix + normalizedEquipment);
   }

   private void clickWidgetFirstAction(Widget widget) {
      NewMenuEntry entry = new NewMenuEntry()
         .option(finishLegacyString(prepareLegacyString("Toggle", 1745606158, -935183254), -935183254))
         .target(finishLegacyString(prepareLegacyString("", 1745606158, 1302984193), 1302984193))
         .identifier(1).type(MenuAction.CC_OP).param0(-1).param1(widget.getId()).itemId(-1);
      this.dispatchMenuInteraction(entry, widget.getBounds(), false);
   }

   static boolean shouldDisableAutoRetaliate(int autoRetaliateVarp) {
      return autoRetaliateVarp != 1;
   }

   static boolean shouldEnableRun(int energy, int runEnabledVarp) {
      return energy >= 2000 && runEnabledVarp != 1;
   }

   private void dispatchMenuInteraction(NewMenuEntry entry, Rectangle bounds, boolean directClick) {
      pohTiming.action(System.currentTimeMillis());
      WalkerMouseControl.interact(Microbot.getClient(), entry, bounds, directClick);
   }

   private void recordWalkClick(int originSceneX, int originSceneY, WorldPoint target, boolean usedMinimap) {
      pohTiming.action(System.currentTimeMillis());
      this.lastWalkClickTick = this.client.getTickCount();
      this.clickOriginSceneX = originSceneX;
      this.clickOriginSceneY = originSceneY;
      this.walkTarget = target;
      this.lastClickUsedMinimap = usedMinimap;
      String message = finishLegacyString(prepareLegacyString("walk-click type={} fromScene=({}, {}) target={}", 1076647432, 684604096), 684604096);
      this.debug(message, this.lastDecision, originSceneX, originSceneY, target);
   }

   private Point projectToMinimap(LocalPoint localPoint) {
      Point point = Perspective.localToMinimap(this.client, localPoint);
      return point != null && Rs2MiniMap.isPointInsideMinimap(point) ? point : null;
   }

   private boolean captureAndStartPlanning(final WalkerEngine.RouteRequest request, Player player, boolean allowTeleports) {
      long startedNanos = System.nanoTime();
      boolean preparingBankSupplies = this.bankSupplyPlan != null;
      WorldPoint target = preparingBankSupplies ? this.bankSupplyPlan.bankLocation : request.destination;
      this.planningFailure = WalkerEngine.PlanningFailure.NONE;

      if (player == null || player.getWorldView() == null || player.getLocalLocation() == null) {
         this.planningFailure = WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED;
         this.debug(
            finishLegacyString(prepareLegacyString("plan failed reason=player-state-unavailable target={}", 700638593, -889036224), -889036224),
            target
         );
         return false;
      }
      // The second world-view read is intentional: the original validates first, then captures.
      WorldView worldView = player.getWorldView();
      WorldPoint origin = player.getWorldLocation();
      if (CollisionRoutePlanner.isWildernessBlocked(origin)
         || !request.nearestBank && CollisionRoutePlanner.isWildernessBlocked(target)) {
         this.planningFailure = WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED;
         this.debug(
            finishLegacyString(prepareLegacyString("plan failed reason=wilderness-route-rejected start={} target={}", 961664622, 1323935787), 1323935787),
            origin, target
         );
         return false;
      }

      WalkerEngine.LocalRoute localRoute = null;
      WalkerEngine.PlanningFailure initialFailure = null;
      if (!request.nearestBank && origin.getPlane() == target.getPlane()) {
         localRoute = this.planLocalRoute(worldView, origin, target, origin.getPlane());
         if (localRoute == null) {
            LocalPoint localTarget = LocalPoint.fromWorld(worldView, withWorldViewPlane(target, worldView));
            if (localTarget != null) {
               int[][] collisionFlags = this.getCollisionFlags(worldView, origin.getPlane());
               initialFailure = classifyTargetCollisionFailure(collisionFlags, localTarget.getSceneX(), localTarget.getSceneY());
               this.debug(
                  finishLegacyString(prepareLegacyString("plan local-miss reason=loaded-target-unreachable start={} target={} failure={}", 1799261567, -1059719530), -1059719530),
                  origin, target, initialFailure
               );
            }
         }
      }
      if (!preparingBankSupplies && request.suppliedRoute != null) {
         localRoute = null;
         for (WorldPoint suppliedPoint : request.suppliedRoute) {
            if (suppliedPoint.getPlane() == origin.getPlane()) {
               WalkerEngine.LocalRoute candidate = this.planLocalRoute(worldView, origin, suppliedPoint, origin.getPlane());
               if (candidate != null && (localRoute == null || candidate.points.size() < localRoute.points.size())) {
                  localRoute = candidate;
               }
            }
         }
      }

      Set<Long> edgeOverrides = Collections.emptySet();
      Set<TransportCatalog.TransportEdge> allowedTransports = Collections.emptySet();
      List<WalkerEngine.TeleportCandidate> teleportCandidates = Collections.emptyList();
      if (!worldView.isInstance()) {
         edgeOverrides = this.collectGlobalEdgeOverridesFromScene(worldView, origin.getPlane());
         if (!edgeOverrides.isEmpty()) {
            this.debug(
               finishLegacyString(prepareLegacyString("plan live-walk-edge-overrides={}", 363505026, 1522613505), 1522613505),
               edgeOverrides.size()
            );
         }
         allowedTransports = this.transports.stream().filter(this::canUseTransport).collect(Collectors.toSet());
         if (allowTeleports && this.config.useTeleportations()) {
            List<WalkerEngine.TeleportCandidate> availableTeleports = new ArrayList<>();
            teleportCandidates = availableTeleports;
            for (TeleportProviders.TeleportProvider provider : this.teleportProviders) {
               WorldPoint destination = provider.getDestination();
               if (!this.excludedTeleportProviders.contains(provider) && provider.isAvailableForRoute(origin, destination)) {
                  availableTeleports.add(new WalkerEngine.TeleportCandidate(provider, destination));
               }
            }
         }
      }

      List<WorldPoint> destinations;
      if (preparingBankSupplies) {
         destinations = Collections.singletonList(this.bankSupplyPlan.bankLocation);
      } else if (request.nearestBank) {
         destinations = Arrays.stream(BankLocation.values())
            .filter(BankLocation::hasRequirements)
            .map(BankLocation::getWorldPoint)
            .distinct()
            .collect(Collectors.toList());
      } else if (request.suppliedRoute != null) {
         destinations = request.suppliedRoute;
      } else {
         destinations = Collections.singletonList(target);
      }
      if (destinations.isEmpty()) {
         this.planningFailure = WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED;
         return false;
      }

      List<BankSupplyController.BankSupplyRequirement> bankSupplyCandidates = new ArrayList<>();
      List<WorldPoint> bankLocations = Collections.emptyList();
      if (!preparingBankSupplies && !request.bankDetourAttempted && !request.nearestBank
         && !worldView.isInstance() && allowTeleports && this.config.useTeleportations() && this.config.useItemsFromBank()) {
         Map<Integer, Integer> bankItems = Rs2Bank.bankItems().stream()
            .collect(Collectors.toMap(Rs2ItemModel::getId, Rs2ItemModel::getQuantity, Integer::sum));
         for (TeleportProviders.TeleportProvider provider : this.teleportProviders) {
            WorldPoint destination = provider.getDestination();
            if (destination != null && !this.excludedTeleportProviders.contains(provider) && !provider.isAvailableForRoute(origin, destination)) {
               List<BankSupplyController.BankSupplyRequirement> candidates = provider.getBankSupplyCandidates(this.client, bankItems, destination)
                  .stream()
                  .filter(requirement -> requirement.canPrepareSupplies(this.client))
                  .collect(Collectors.toList());
               bankSupplyCandidates.addAll(candidates);
            }
         }
         if (!bankSupplyCandidates.isEmpty()) {
            bankLocations = Arrays.stream(BankLocation.values())
               .filter(BankLocation::hasRequirements)
               .map(BankLocation::getWorldPoint)
               .distinct()
               .collect(Collectors.toList());
         }
      }

      int worldViewId = worldView.getId();
      int baseX = worldView.getBaseX();
      int baseY = worldView.getBaseY();
      boolean instanced = worldView.isInstance();
      Set<TransportCatalog.TransportEdge> exclusions = new HashSet<>(this.excludedTransports);
      final WalkerEngine.PlanningSnapshot snapshot = new WalkerEngine.PlanningSnapshot(
         request, origin, worldViewId, baseX, baseY, instanced, localRoute, initialFailure, edgeOverrides, allowedTransports,
         teleportCandidates, exclusions, destinations, allowTeleports, startedNanos
      );
      snapshot.bankSupplyCandidates = bankSupplyCandidates;
      snapshot.bankLocations = bankLocations;
      snapshot.hasRunEnergy = this.client.getEnergy() >= 2000;
      if (!snapshot.instanced) {
         int localDistance = localRoute == null ? Integer.MAX_VALUE : Math.max(0, localRoute.points.size() - 1);
         if (!isShortLocalRoute(localDistance)) {
            this.pendingPlanSnapshot = snapshot;
            this.status = WalkerEngine.Status.PLANNING;
            Callable<WalkerEngine.PlanningResult> planning = () -> this.solveRouteSnapshot(snapshot);
            FutureTask<WalkerEngine.PlanningResult> task = new FutureTask<WalkerEngine.PlanningResult>(planning) {
               final WalkerEngine.RouteRequest routeRequest = request;
               final WalkerEngine.PlanningSnapshot planningSnapshot = snapshot;
               final WalkerEngine engine = WalkerEngine.this;

               @Override
               protected void done() {
                  if (this.engine.completionExecutor != null) {
                     this.engine.completionExecutor.execute(() -> this.complete(this.routeRequest, this.planningSnapshot));
                  }
               }

               private void complete(WalkerEngine.RouteRequest request, WalkerEngine.PlanningSnapshot snapshot) {
                  this.engine.completePendingPlan(request, this, snapshot);
               }
            };
            this.pendingPlanTask = task;
            this.getPlanningExecutor().execute(WalkerMouseControl.profiledTask(task));
            return true;
         }
      }
      WalkerEngine.PlanningResult result = new WalkerEngine.PlanningResult(null, null);
      this.applyPlannedRoute(snapshot, result, player);
      return true;
   }

   private WalkerEngine.PlanningResult solveRouteSnapshot(WalkerEngine.PlanningSnapshot snapshot) {
      if (snapshot.instanced) {
         return new WalkerEngine.PlanningResult(null, null);
      }
      int localDistance = snapshot.localRoute == null
         ? Integer.MAX_VALUE
         : Math.max(0, snapshot.localRoute.points.size() - 1);
      if (isShortLocalRoute(localDistance)) {
         return new WalkerEngine.PlanningResult(null, null);
      }

      CollisionRoutePlanner.RouteSearchResult directRoute = this.planner.planRoute(
         snapshot.origin, snapshot.destinations, snapshot.excludedTransports, snapshot.edgeOverrides, snapshot.allowedTransports
      );
      WalkerEngine.TeleportRoute directTeleport = snapshot.allowTeleports
         ? this.findBeneficialTeleportRoute(snapshot, Math.min(directRoute.distance, localDistance))
         : null;
      WalkerEngine.PlanningResult result = new WalkerEngine.PlanningResult(directRoute, directTeleport);
      if (snapshot.bankSupplyCandidates.isEmpty()) {
         return result;
      }

      int globalWalkingTicks = BankSupplyController.estimateRouteTicks(directRoute.path, snapshot.hasRunEnergy);
      int localWalkingTicks = snapshot.localRoute == null
         ? Integer.MAX_VALUE
         : BankSupplyController.estimateRouteTicks(snapshot.localRoute.points, snapshot.hasRunEnergy);
      int directCost = Math.min(globalWalkingTicks, localWalkingTicks);
      if (directTeleport != null) {
         int teleportTicks = BankSupplyController.estimateRouteTicks(directTeleport.route.path, snapshot.hasRunEnergy) + 4;
         directCost = Math.min(directCost, teleportTicks);
      }

      CollisionRoutePlanner.RouteSearchResult walkingRouteToBank = this.planner.planRoute(
         snapshot.origin, snapshot.bankLocations, snapshot.excludedTransports, snapshot.edgeOverrides, snapshot.allowedTransports
      );
      WalkerEngine.TeleportRoute teleportToBank = this.findBeneficialTeleportRoute(snapshot, snapshot.bankLocations, walkingRouteToBank.distance);
      List<WorldPoint> pathToBank = teleportToBank == null ? walkingRouteToBank.path : teleportToBank.route.path;
      Map<Integer, List<BankSupplyController.BankSupplyRequirement>> requirementsByCost = snapshot.bankSupplyCandidates.stream()
         .collect(Collectors.groupingBy(requirement -> requirement.getWithdrawalCost()));

      for (Entry<Integer, List<BankSupplyController.BankSupplyRequirement>> group : requirementsByCost.entrySet()) {
         CollisionRoutePlanner routePlanner = this.planner;
         List<WorldPoint> suppliedTeleportLandings = group.getValue().stream()
            .map(requirement -> requirement.destination)
            .collect(Collectors.toList());
         CollisionRoutePlanner.RouteSearchResult routeAfterBank = routePlanner.planRoute(
            suppliedTeleportLandings, snapshot.destinations, snapshot.excludedTransports, Collections.emptySet(), snapshot.allowedTransports
         );
         if (!pathToBank.isEmpty() && !routeAfterBank.path.isEmpty()) {
            int bankTravelTicks = BankSupplyController.estimateRouteTicks(pathToBank, snapshot.hasRunEnergy);
            int bankTeleportTicks = teleportToBank == null ? 0 : 4;
            int detourCost = bankTravelTicks + bankTeleportTicks + group.getKey() + 4
               + BankSupplyController.estimateRouteTicks(routeAfterBank.path, snapshot.hasRunEnergy);
            if (BankSupplyController.isBankDetourPreferred(directCost, detourCost)
               && (result.bankSupplyPlan == null || detourCost < result.bankSupplyPlan.bankDetourCost)) {
               BankSupplyController.BankSupplyRequirement requirement = group.getValue().stream()
                  .filter(candidate -> candidate.destination.equals(routeAfterBank.path.get(0)))
                  .findFirst()
                  .orElseThrow();
               WorldPoint bank = pathToBank.get(pathToBank.size() - 1);
               result.bankSupplyPlan = new BankSupplyController(bank, requirement, directCost, detourCost);
            }
         }
      }
      return result;
   }

   private boolean completePendingPlan(WalkerEngine.RouteRequest request) {
      if (!this.pendingPlanTask.isDone()) {
         return false;
      }

      WalkerEngine.PlanningSnapshot snapshot = this.pendingPlanSnapshot;

      WalkerEngine.PlanningResult result;
      try {
         result = this.pendingPlanTask.get();
      } catch (InterruptedException interrupted) {
         Thread.currentThread().interrupt();
         this.cancelPendingPlan();
         this.failNavigation("planning-interrupted");
         return true;
      } catch (ExecutionException failure) {
         this.cancelPendingPlan();
         log.warn("Route planning worker failed", failure);
         this.failNavigation("planning-worker-error");
         return true;
      }

      this.pendingPlanTask = null;
      this.pendingPlanSnapshot = null;
      Player player = this.client.getLocalPlayer();
      WorldView worldView = player == null ? null : player.getWorldView();
      if (this.activeRequest == request && this.executionRequest == request) {
         if (player != null
            && worldView != null
            && snapshot.origin.equals(player.getWorldLocation())
            && snapshot.worldViewId == worldView.getId()
            && snapshot.baseX == worldView.getBaseX()
            && snapshot.baseY == worldView.getBaseY()) {
            this.applyPlannedRoute(snapshot, result, player);
            return true;
         }

         if (!this.captureAndStartPlanning(request, player, snapshot.allowTeleports)) {
            this.failNavigation("plan-unavailable");
         }

         return false;
      } else {
         return false;
      }
   }

   private void completePendingPlan(WalkerEngine.RouteRequest request, FutureTask<WalkerEngine.PlanningResult> task, WalkerEngine.PlanningSnapshot snapshot) {
      if (this.pendingPlanTask == task && this.pendingPlanSnapshot == snapshot) {
         this.completePendingPlan(request);
      }
   }

   private void applyPlannedRoute(WalkerEngine.PlanningSnapshot snapshot, WalkerEngine.PlanningResult result, Player player) {
      WalkerEngine.RouteRequest request = snapshot.request;
      WorldView worldView = player.getWorldView();
      if (result.bankSupplyPlan != null) {
         this.bankSupplyPlan = result.bankSupplyPlan;
         request.bankDetourAttempted = true;
         String providerName = this.bankSupplyPlan.requirement.provider.name;
         int directCost = this.bankSupplyPlan.directRouteCost;
         int detourCost = this.bankSupplyPlan.bankDetourCost;
         long savedSeconds = Math.round((double) (directCost - detourCost) * 0.6);
         StringBuilder message = new StringBuilder();
         message.append(finishLegacyString(prepareLegacyString("Bank detour for ", 2039538839, -206621710), -206621710));
         message.append(providerName);
         message.append(finishLegacyString(prepareLegacyString("; estimated saving ", 2039538839, 1984231833), 1984231833));
         message.append(savedSeconds);
         message.append(finishLegacyString(prepareLegacyString(" seconds", 2039538839, -126310620), -126310620));
         Microbot.status = message.toString();
         if (!this.captureAndStartPlanning(request, player, true)) {
            this.failNavigation(finishLegacyString(prepareLegacyString("plan-unavailable", 1758075664, 1651272883), 1651272883));
         }
         return;
      }

      CollisionRoutePlanner.RouteSearchResult globalResult = result.globalRoute;
      if (this.bankSupplyPlan == null && (request.nearestBank || request.suppliedRoute != null)) {
         CollisionRoutePlanner.RouteSearchResult selectedRoute = result.teleportRoute == null ? globalResult : result.teleportRoute.route;
         WorldPoint selectedDestination;
         if (selectedRoute != null && !selectedRoute.path.isEmpty()) {
            selectedDestination = selectedRoute.path.get(selectedRoute.path.size() - 1);
         } else {
            selectedDestination = snapshot.localRoute == null
               ? null
               : snapshot.localRoute.points.get(snapshot.localRoute.points.size() - 1);
         }
         request.destination = selectedDestination;
         if (request.destination == null) {
            this.planningFailure = globalResult == null ? WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED : globalResult.failure;
            String failure = request.nearestBank
               ? finishLegacyString(prepareLegacyString("nearest-bank-unavailable", 1981436982, -469755298), -469755298)
               : finishLegacyString(prepareLegacyString("quest-approach-unavailable", 488034407, 1849018341), 1849018341);
            this.failNavigation(failure);
            return;
         }
      }

      WalkerEngine.PlanningFailure globalFailure = globalResult == null ? WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED : globalResult.failure;
      int localDistance = snapshot.localRoute == null
         ? Integer.MAX_VALUE
         : Math.max(0, snapshot.localRoute.points.size() - 1);
      WalkerEngine.TeleportRoute teleport = result.teleportRoute;
      if (teleport != null) {
         this.activeTeleportProvider = teleport.provider;
         this.expectedTeleportDestination = teleport.destination;
         this.globalRoute = teleport.route.path;
         this.globalRouteIndex = 0;
         this.currentLocalRoute = null;
         this.localRouteSegments = Collections.emptyList();
         this.localRouteTransports = Collections.emptyList();
         this.routeSegments = splitRouteByPlane(this.globalRoute);
         WorldPoint markerOrigin = snapshot.origin;
         String markerAction = finishLegacyString(prepareLegacyString("Teleport", 1656211671, -802583184), -802583184);
         this.activeTransportMarker = new WalkerEngine.TransportMarker(markerOrigin, markerAction, this.activeTeleportProvider.name);
         this.debug(
            finishLegacyString(prepareLegacyString("plan ready kind=teleport start={} target={} teleport={} landing={} cost={} baselineCost={} millis={}", 1656211671, 1524480799), 1524480799),
            snapshot.origin, request.destination, this.activeTeleportProvider.name, this.expectedTeleportDestination, teleport.cost,
            globalResult == null ? localDistance : Math.min(globalResult.distance, localDistance), elapsedMillis(snapshot.startedNanos)
         );
         this.status = request.execute ? WalkerEngine.Status.WALKING : WalkerEngine.Status.PREVIEW;
         return;
      }

      this.globalRoute = globalResult == null ? Collections.emptyList() : globalResult.path;
      if (!this.globalRoute.isEmpty()) {
         this.globalRouteIndex = 0;
         this.currentLocalRoute = this.buildNextGlobalRouteSegment(worldView, snapshot.origin);
         if (this.currentLocalRoute != null) {
            this.localRouteSegments = Collections.singletonList(this.currentLocalRoute);
            this.localRouteTransports = Collections.emptyList();
            this.routeSegments = splitRouteByPlane(this.globalRoute);
            this.debug(
               finishLegacyString(prepareLegacyString("plan ready kind=global start={} target={} pathLength={} planeSegments={} firstTransition={} millis={}", 1278726729, -588160854), -588160854),
               snapshot.origin, request.destination, this.globalRoute.size(), this.routeSegments.size(),
               describeTransport(this.activeTransport), elapsedMillis(snapshot.startedNanos)
            );
            this.updateNextDoorMarker(worldView, this.currentLocalRoute, 0);
            this.status = request.execute ? WalkerEngine.Status.WALKING : WalkerEngine.Status.PREVIEW;
            return;
         }
         this.globalRoute = Collections.emptyList();
      }

      if (snapshot.localRoute != null) {
         this.currentLocalRoute = snapshot.localRoute;
         if (this.bankSupplyPlan == null && request.suppliedRoute != null) {
            request.destination = this.currentLocalRoute.points.get(this.currentLocalRoute.points.size() - 1);
         }
         this.localRouteSegments = Collections.singletonList(this.currentLocalRoute);
         this.localRouteTransports = Collections.emptyList();
         this.activeTransport = null;
         this.routeSegments = collectSegmentPaths(this.localRouteSegments);
         this.debug(
            finishLegacyString(prepareLegacyString("plan ready kind=local-fallback start={} target={} pathLength={} millis={}", 1867905074, 1189424689), 1189424689),
            snapshot.origin, request.destination, this.currentLocalRoute.points.size(), elapsedMillis(snapshot.startedNanos)
         );
         this.updateNextDoorMarker(worldView, this.currentLocalRoute, 0);
         this.status = request.execute ? WalkerEngine.Status.WALKING : WalkerEngine.Status.PREVIEW;
         return;
      }

      WorldPoint target = this.bankSupplyPlan == null ? request.destination : this.bankSupplyPlan.bankLocation;
      if (snapshot.origin.getPlane() != target.getPlane()) {
         WalkerEngine.LocalTransportRoute localTransportRoute = this.planLocalTransportRoute(worldView, snapshot.origin, target);
         if (localTransportRoute == null) {
            this.planningFailure = globalFailure;
            this.debug(
               finishLegacyString(prepareLegacyString("plan failed reason=no-supported-route start={} target={} millis={}", 284524691, 848200043), 848200043),
               snapshot.origin, request.destination, elapsedMillis(snapshot.startedNanos)
            );
            this.failNavigation(finishLegacyString(prepareLegacyString("plan-unavailable", 284524691, -1264431370), -1264431370));
         } else {
            this.localRouteSegments = localTransportRoute.segments;
            this.localRouteTransports = localTransportRoute.transports;
            this.currentLocalRoute = this.localRouteSegments.get(0);
            this.activeTransport = this.localRouteTransports.get(0);
            this.routeSegments = collectSegmentPaths(this.localRouteSegments);
            this.debug(
               finishLegacyString(prepareLegacyString("plan ready kind=loaded-transports start={} target={} pathSegments={} transitions={} firstTransition={} millis={}", 122195838, 1062630557), 1062630557),
               snapshot.origin, request.destination, this.localRouteSegments.size(), this.localRouteTransports.size(),
               describeTransport(this.activeTransport), elapsedMillis(snapshot.startedNanos)
            );
            this.updateNextDoorMarker(worldView, this.currentLocalRoute, 0);
            this.status = request.execute ? WalkerEngine.Status.WALKING : WalkerEngine.Status.PREVIEW;
         }
      } else {
         this.planningFailure = globalFailure == WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED && snapshot.initialFailure != null
            ? snapshot.initialFailure : globalFailure;
         this.failNavigation(finishLegacyString(prepareLegacyString("plan-unavailable", 71342454, -929375268), -929375268));
      }
   }

   private WalkerEngine.TeleportRoute findBeneficialTeleportRoute(WalkerEngine.PlanningSnapshot snapshot, int baselineCost) {
      return this.findBeneficialTeleportRoute(snapshot, snapshot.destinations, baselineCost);
   }

   private WalkerEngine.TeleportRoute findBeneficialTeleportRoute(WalkerEngine.PlanningSnapshot snapshot, List<WorldPoint> destinations, int baselineCost) {
      if (snapshot.teleportCandidates.isEmpty()) {
         return null;
      }
      CollisionRoutePlanner routePlanner = this.planner;
      List<WorldPoint> landings = snapshot.teleportCandidates.stream()
         .map(candidate -> candidate.destination)
         .collect(Collectors.toList());
      CollisionRoutePlanner.RouteSearchResult route = routePlanner.planRoute(
         landings, destinations, snapshot.excludedTransports, Collections.emptySet(), snapshot.allowedTransports
      );
      if (route.path.isEmpty()) {
         return null;
      }
      WorldPoint selectedLanding = route.path.get(0);
      int teleportCost = route.distance + 4;
      if (!isTeleportRouteWorthwhile(baselineCost, teleportCost)) {
         return null;
      }
      return snapshot.teleportCandidates.stream()
         .filter(candidate -> candidate.destination.equals(selectedLanding))
         .findFirst()
         .map(candidate -> new WalkerEngine.TeleportRoute(candidate.provider, candidate.destination, route, teleportCost))
         .orElse(null);
   }

   private Executor getPlanningExecutor() {
      if (this.suppliedPlanningExecutor != null) {
         return this.suppliedPlanningExecutor;
      }
      if (this.ownedPlanningExecutor != null && !this.ownedPlanningExecutor.isShutdown()) {
         return this.ownedPlanningExecutor;
      }
      ThreadFactory threadFactory = task -> {
         String name = finishLegacyString(prepareLegacyString("EfficientWalker-Planning", 1576910645, -1081574078), -1081574078);
         Thread thread = new Thread(task, name);
         thread.setDaemon(true);
         return thread;
      };
      this.ownedPlanningExecutor = WalkerMouseControl.profiledExecutor(Executors.newSingleThreadExecutor(threadFactory));
      return this.ownedPlanningExecutor;
   }

   private void cancelPendingPlan() {
      if (this.pendingPlanTask != null) {
         this.pendingPlanTask.cancel(true);
         this.pendingPlanTask = null;
         this.pendingPlanSnapshot = null;
      }
   }

   static boolean isTeleportRouteWorthwhile(int directRouteTicks, int teleportRouteTicks) {
      return teleportRouteTicks <= Integer.MAX_VALUE - 10
         && (directRouteTicks == Integer.MAX_VALUE || teleportRouteTicks + 10 < directRouteTicks);
   }

   static boolean isShortLocalRoute(int tileCount) {
      return tileCount <= 10;
   }

   private WalkerEngine.LocalTransportRoute planLocalTransportRoute(WorldView worldView, WorldPoint origin, WorldPoint destination) {
      if (worldView.isInstance()) {
         return null;
      }

      List<TransportCatalog.TransportEdge> loadedPlaneChanges = new ArrayList<>();
      for (TransportCatalog.TransportEdge transport : this.transports) {
         if (this.excludedTransports.contains(transport)
            || CollisionRoutePlanner.isWildernessBlocked(transport.origin)
            || CollisionRoutePlanner.isWildernessBlocked(transport.destination)
            || !this.canUseTransport(transport)
            || transport.origin.getPlane() == transport.destination.getPlane()) {
            continue;
         }
         LocalPoint localOrigin = LocalPoint.fromWorld(worldView, withWorldViewPlane(transport.origin, worldView));
         if (localOrigin == null) {
            continue;
         }
         LocalPoint localDestination = LocalPoint.fromWorld(worldView, withWorldViewPlane(transport.destination, worldView));
         if (localDestination != null && this.isTransportEntityLoaded(worldView, transport)) {
            loadedPlaneChanges.add(transport);
         }
      }

      PriorityQueue<WalkerEngine.LocalTransportSearchNode> frontier = new PriorityQueue<>(
         Comparator.comparingInt(node -> node.cost));
      Map<WorldPoint, Integer> bestCosts = new HashMap<>();
      frontier.add(new WalkerEngine.LocalTransportSearchNode(origin, Collections.emptyList(), Collections.emptyList(), 0));
      bestCosts.put(origin, 0);
      WalkerEngine.LocalTransportRoute bestRoute = null;
      int bestRouteCost = Integer.MAX_VALUE;

      while (!frontier.isEmpty()) {
         WalkerEngine.LocalTransportSearchNode node = frontier.remove();
         if (node.cost >= bestRouteCost || node.cost > bestCosts.getOrDefault(node.location, Integer.MAX_VALUE)) {
            continue;
         }
         if (node.location.getPlane() == destination.getPlane()) {
            WalkerEngine.LocalRoute finalSegment = this.planLocalRoute(worldView, node.location, destination, destination.getPlane());
            if (finalSegment != null && node.cost + finalSegment.packedTiles.length < bestRouteCost) {
               List<WalkerEngine.LocalRoute> segments = new ArrayList<>(node.segments);
               segments.add(finalSegment);
               bestRouteCost = node.cost + finalSegment.packedTiles.length;
               bestRoute = new WalkerEngine.LocalTransportRoute(segments, node.transports);
            }
            continue;
         }

         for (TransportCatalog.TransportEdge transport : loadedPlaneChanges) {
            if (transport.origin.getPlane() != node.location.getPlane()) {
               continue;
            }
            WalkerEngine.LocalRoute segment = this.planLocalRoute(worldView, node.location, transport.origin, node.location.getPlane());
            int cost = segment == null ? Integer.MAX_VALUE : node.cost + segment.packedTiles.length;
            if (cost < bestRouteCost && cost < bestCosts.getOrDefault(transport.destination, Integer.MAX_VALUE)) {
               List<WalkerEngine.LocalRoute> segments = new ArrayList<>(node.segments);
               segments.add(segment);
               List<TransportCatalog.TransportEdge> transports = new ArrayList<>(node.transports);
               transports.add(transport);
               bestCosts.put(transport.destination, cost);
               frontier.add(new WalkerEngine.LocalTransportSearchNode(transport.destination, segments, transports, cost));
            }
         }
      }
      return bestRoute;
   }

   private static List<List<WorldPoint>> collectSegmentPaths(List<WalkerEngine.LocalRoute> segments) {
      List<List<WorldPoint>> paths = new ArrayList<>(segments.size());
      for (WalkerEngine.LocalRoute segment : segments) {
         paths.add(segment.points);
      }
      return Collections.unmodifiableList(paths);
   }

   private static List<List<WorldPoint>> splitRouteByPlane(List<WorldPoint> path) {
      List<List<WorldPoint>> segments = new ArrayList<>();
      int segmentStart = 0;
      for (int index = 1; index < path.size(); index++) {
         if (path.get(index).getPlane() != path.get(index - 1).getPlane()) {
            segments.add(Collections.unmodifiableList(new ArrayList<>(path.subList(segmentStart, index))));
            segmentStart = index;
         }
      }
      segments.add(Collections.unmodifiableList(new ArrayList<>(path.subList(segmentStart, path.size()))));
      return Collections.unmodifiableList(segments);
   }

   private boolean refreshGlobalRouteSegment(WorldView worldView, WorldPoint playerLocation) {
      int playerIndex = findRouteIndexAtOrAfter(this.globalRoute, playerLocation, this.globalRouteIndex);
      if (playerIndex < 0) {
         return true;
      }
      this.globalRouteIndex = playerIndex;
      if (this.currentLocalRoute == null) {
         return false;
      }

      List<WorldPoint> globalPath = this.globalRoute;
      List<WorldPoint> segmentPoints = this.currentLocalRoute.points;
      int segmentEnd = this.currentLocalRoute.points.size() - 1;
      WorldPoint segmentDestination = segmentPoints.get(segmentEnd);
      int existingEnd = findRouteIndexAtOrAfter(globalPath, segmentDestination, this.globalRouteIndex);
      int loadedEnd = this.findLoadedGlobalRouteEnd(worldView, this.globalRouteIndex);
      if (loadedEnd <= existingEnd) {
         return true;
      }

      WalkerEngine.LocalRoute nextSegment = this.buildNextGlobalRouteSegment(worldView, playerLocation);
      if (nextSegment == null) {
         return false;
      }
      this.currentLocalRoute = nextSegment;
      this.localRouteSegments = Collections.singletonList(nextSegment);
      return true;
   }

   private WalkerEngine.LocalRoute buildNextGlobalRouteSegment(WorldView worldView, WorldPoint playerLocation) {
      int playerIndex = findRouteIndexAtOrAfter(this.globalRoute, playerLocation, this.globalRouteIndex);
      if (playerIndex < 0) {
         return null;
      }
      this.globalRouteIndex = playerIndex;
      int loadedEnd = this.findLoadedGlobalRouteEnd(worldView, playerIndex);
      TransportCatalog.TransportEdge boundaryTransport = loadedEnd < this.globalRoute.size() - 1
         ? this.findTransportEdge(this.globalRoute.get(loadedEnd), this.globalRoute.get(loadedEnd + 1)) : null;
      TransportCatalog.TransportEdge nextTransport = boundaryTransport;
      if (loadedEnd <= playerIndex && playerIndex < this.globalRoute.size() - 1 && boundaryTransport == null) {
         return null;
      }

      int reachableEnd = loadedEnd;
      WalkerEngine.LocalRoute segment = null;
      while (reachableEnd >= playerIndex && segment == null) {
         segment = this.planLocalRoute(worldView, playerLocation, this.globalRoute.get(reachableEnd), playerLocation.getPlane());
         if (segment == null) {
            reachableEnd--;
         }
      }
      if (segment == null) {
         return null;
      }

      boolean canUseAdjacentBoundary = reachableEnd == loadedEnd - 1
         && isAdjacentTransportOrigin(boundaryTransport, this.globalRoute.get(reachableEnd))
         && this.isTransportEntityLoaded(worldView, boundaryTransport);
      if (!canUseAdjacentBoundary) {
         nextTransport = reachableEnd >= this.globalRoute.size() - 1 ? null
            : this.findTransportEdge(this.globalRoute.get(reachableEnd), this.globalRoute.get(reachableEnd + 1));
      }
      if (reachableEnd <= playerIndex && playerIndex < this.globalRoute.size() - 1 && nextTransport == null) {
         return null;
      }
      if (reachableEnd < loadedEnd) {
         String message = prepareLegacyString("global live-route shortened loadedEnd={} reachableEnd={}", 7168223, 157972797);
         message = finishLegacyString(message, 157972797);
         this.debug(message, this.globalRoute.get(loadedEnd), this.globalRoute.get(reachableEnd));
      }

      this.globalRoute = spliceGlobalRoute(this.globalRoute, reachableEnd, segment.points);
      this.globalRouteIndex = 0;
      this.routeSegments = splitRouteByPlane(this.globalRoute);
      this.activeTransport = nextTransport;
      return segment;
   }

   static boolean isAdjacentTransportOrigin(TransportCatalog.TransportEdge transport, WorldPoint playerLocation) {
      return transport != null && !transport.isUnrestrictedDoor()
         && transport.origin.getPlane() == playerLocation.getPlane()
         && transport.origin.distanceTo2D(playerLocation) == 1;
   }

   static List<WorldPoint> spliceGlobalRoute(List<WorldPoint> globalPath, int joinIndex, List<WorldPoint> replacementPrefix) {
      int capacity = replacementPrefix.size() + globalPath.size() - joinIndex - 1;
      List<WorldPoint> spliced = new ArrayList<>(capacity);
      spliced.addAll(replacementPrefix);
      spliced.addAll(globalPath.subList(joinIndex + 1, globalPath.size()));
      return Collections.unmodifiableList(spliced);
   }

   private int findLoadedGlobalRouteEnd(WorldView worldView, int startIndex) {
      int loadedEnd = startIndex;
      for (int index = startIndex + 1; index < this.globalRoute.size(); index++) {
         if (this.findTransportEdge(this.globalRoute.get(index - 1), this.globalRoute.get(index)) != null) {
            break;
         }
         if (this.globalRoute.get(index).getPlane() != this.globalRoute.get(startIndex).getPlane()) {
            break;
         }
         if (LocalPoint.fromWorld(worldView, this.globalRoute.get(index)) == null) {
            return loadedEnd;
         }
         loadedEnd = index;
      }
      return loadedEnd;
   }

   private TransportCatalog.TransportEdge findTransportEdge(WorldPoint origin, WorldPoint destination) {
      for (TransportCatalog.TransportEdge transport : this.transports) {
         if (!this.excludedTransports.contains(transport) && !transport.isUnrestrictedDoor()
            && transport.origin.equals(origin) && transport.destination.equals(destination)) {
            return transport;
         }
      }
      return null;
   }

   private static int findRouteIndexAtOrAfter(List<WorldPoint> path, WorldPoint location, int startIndex) {
      for (int index = Math.max(0, startIndex); index < path.size(); index++) {
         if (path.get(index).equals(location)) {
            return index;
         }
      }
      return -1;
   }

   private WalkerEngine.LocalRoute planLocalRoute(WorldView worldView, WorldPoint origin, WorldPoint destination, int plane) {
      if (origin.getPlane() != plane || destination.getPlane() != plane) {
         return null;
      }
      if (CollisionRoutePlanner.isWildernessBlocked(origin) || CollisionRoutePlanner.isWildernessBlocked(destination)) {
         return null;
      }
      if (worldView.isInstance() && plane != worldView.getPlane()) {
         return null;
      }

      LocalPoint playerLocal = this.client.getLocalPlayer().getLocalLocation();
      LocalPoint localOrigin = this.resolveLocalPointInWorldView(worldView, withWorldViewPlane(origin, worldView), playerLocal);
      LocalPoint localDestination = this.resolveLocalPointInWorldView(worldView, withWorldViewPlane(destination, worldView), playerLocal);
      if (localOrigin == null || localDestination == null || localDestination.getWorldView() != localOrigin.getWorldView()) {
         return null;
      }
      int[][] flags = this.getCollisionFlags(worldView, plane);
      if (flags == null) {
         return null;
      }

      WalkerEngine.DoorCollisionOverrides doorOverrides = this.buildDoorCollisionOverrides(worldView, plane, flags.length, flags[0].length);
      int[] packedPath = findLocalPathWithDoorDetours(flags, doorOverrides,
         localOrigin.getSceneX(), localOrigin.getSceneY(), localDestination.getSceneX(), localDestination.getSceneY());
      if (packedPath.length == 0) {
         return null;
      }
      List<WorldPoint> points = new ArrayList<>(packedPath.length);
      for (int packedTile : packedPath) {
         int sceneX = LocalCollisionGrid.unpackX(packedTile, flags.length);
         int sceneY = LocalCollisionGrid.unpackY(packedTile, flags.length);
         LocalPoint localPoint = LocalPoint.fromScene(sceneX, sceneY, worldView);
         WorldPoint worldPoint = WorldPoint.fromLocal(worldView, localPoint.getX(), localPoint.getY(), plane);
         if (CollisionRoutePlanner.isWildernessBlocked(worldPoint)) {
            return null;
         }
         points.add(worldPoint);
      }
      return new WalkerEngine.LocalRoute(worldView, plane, packedPath, flags.length, Collections.unmodifiableList(points));
   }

   private static WorldPoint withWorldViewPlane(WorldPoint point, WorldView worldView) {
      return new WorldPoint(point.getX(), point.getY(), worldView.getPlane());
   }

   private static int[] findLocalPathWithDoorDetours(int[][] flags, WalkerEngine.DoorCollisionOverrides doorOverrides, int fromX, int fromY, int toX, int toY) {
      int width = flags.length;
      List<Integer> path = new ArrayList<>();
      BitSet openedEdges = new BitSet();
      int currentX = fromX;
      int currentY = fromY;
      int remainingDoors = doorOverrides.overrides.cardinality() + 1;
      for (; remainingDoors > 0; remainingDoors--) {
         int[] directPath = LocalCollisionGrid.findPath(flags, openedEdges, currentX, currentY, toX, toY);
         if (directPath.length > 0) {
            appendPathWithoutDuplicateJoin(path, directPath);
            return path.stream().mapToInt(Integer::intValue).toArray();
         }
         int[] pathWithDoors = LocalCollisionGrid.findPath(flags, doorOverrides.overrides, currentX, currentY, toX, toY);
         if (pathWithDoors.length == 0) {
            return new int[0];
         }
         WalkerEngine.RouteDoor nextDoor = doorOverrides.findNextDoorOnRoute(pathWithDoors, width, 0, openedEdges);
         if (nextDoor == null) {
            return new int[0];
         }

         int beforeDoor = pathWithDoors[nextDoor.routeIndex];
         int afterDoor = pathWithDoors[nextDoor.routeIndex + 1];
         int[] approach = LocalCollisionGrid.findPath(flags, openedEdges, currentX, currentY,
            LocalCollisionGrid.unpackX(beforeDoor, width), LocalCollisionGrid.unpackY(beforeDoor, width));
         if (approach.length == 0) {
            return new int[0];
         }
         appendPathWithoutDuplicateJoin(path, approach);
         path.add(afterDoor);
         openedEdges.set(nextDoor.edgeIndex);

         int beforeX = LocalCollisionGrid.unpackX(beforeDoor, width);
         int beforeY = LocalCollisionGrid.unpackY(beforeDoor, width);
         int afterX = LocalCollisionGrid.unpackX(afterDoor, width);
         int afterY = LocalCollisionGrid.unpackY(afterDoor, width);
         int beyondX = afterX + afterX - beforeX;
         int beyondY = afterY + afterY - beforeY;
         if ((afterX != toX || afterY != toY)
            && LocalCollisionGrid.canStep(flags, openedEdges, afterX, afterY, beyondX, beyondY)) {
            path.add(LocalCollisionGrid.tileIndex(beyondX, beyondY, width));
            currentX = beyondX;
            currentY = beyondY;
            continue;
         }
         currentX = afterX;
         currentY = afterY;
      }
      return new int[0];
   }

   private static void appendPathWithoutDuplicateJoin(List<Integer> path, int[] segment) {
      for (int index = path.isEmpty() ? 0 : 1; index < segment.length; index++) {
         path.add(segment[index]);
      }
   }

   private static int locatePlayerOnLocalRoute(WalkerEngine.LocalRoute route, WorldPoint playerLocation, WorldView worldView, int[][] flags, BitSet overrides, int playerSceneX, int playerSceneY) {
      for (int index = route.routeIndex; index < route.points.size(); index++) {
         if (route.points.get(index).equals(playerLocation)) {
            return index;
         }
      }
      for (int index = route.routeIndex; index < route.points.size(); index++) {
         WorldPoint point = route.points.get(index);
         if (point.distanceTo2D(playerLocation) <= 1) {
            LocalPoint localPoint = LocalPoint.fromWorld(worldView, point);
            if (localPoint != null && LocalCollisionGrid.canStep(flags, overrides, playerSceneX, playerSceneY,
               localPoint.getSceneX(), localPoint.getSceneY())) {
               return index;
            }
         }
      }
      return -1;
   }

   private LocalPoint resolveLocalPointInWorldView(WorldView worldView, WorldPoint worldPoint, LocalPoint referencePoint) {
      LocalPoint direct = LocalPoint.fromWorld(worldView, worldPoint);
      if (direct != null) {
         return direct;
      }
      if (!worldView.isInstance()) {
         return null;
      }

      LocalPoint nearest = null;
      int nearestDistance = Integer.MAX_VALUE;
      for (WorldPoint instancePoint : WorldPoint.toLocalInstance(worldView, worldPoint)) {
         LocalPoint candidate = LocalPoint.fromWorld(worldView, instancePoint);
         if (candidate != null) {
            int distance = candidate.distanceTo(referencePoint);
            if (distance < nearestDistance) {
               nearest = candidate;
               nearestDistance = distance;
            }
         }
      }
      return nearest;
   }

   private void updateDisplayedRouteSegment(int segmentIndex, WalkerEngine.LocalRoute route) {
      if (route == null) {
         this.routeSegments = Collections.emptyList();
         return;
      }
      List<List<WorldPoint>> segments = new ArrayList<>(this.routeSegments);
      segments.set(segmentIndex, route.points);
      this.routeSegments = Collections.unmodifiableList(segments);
   }

   private void completeCurrentRoute() {
      if (this.bankSupplyPlan != null && !this.bankSupplyPlan.arrivedAtBank) {
         WalkerEngine.RouteRequest request = this.activeRequest;
         BankSupplyController supplyPlan = this.bankSupplyPlan;
         this.resetNavigation();
         this.activeRequest = request;
         this.executionRequest = request;
         this.bankSupplyPlan = supplyPlan;
         this.bankSupplyPlan.arrivedAtBank = true;
         this.status = WalkerEngine.Status.WALKING;
      } else {
         this.debug(
            "arrived destination={} player={} pathCleared=true",
            this.getDestination(),
            this.client.getLocalPlayer() == null ? null : this.client.getLocalPlayer().getWorldLocation()
         );
         this.activeRequest = null;
         this.executionRequest = null;
         this.currentLocalRoute = null;
         this.globalRoute = Collections.emptyList();
         this.globalRouteIndex = 0;
         this.localRouteSegments = Collections.emptyList();
         this.localRouteTransports = Collections.emptyList();
         this.activeTransport = null;
         this.transportAttempt = null;
         this.activeTeleportProvider = null;
         this.expectedTeleportDestination = null;
         this.teleportAttempt = null;
         this.lastTeleportActionTick = -1;
         this.routeSegments = Collections.emptyList();
         this.activeTransportMarker = null;
         this.pendingDoor = null;
         this.lastWalkClickTick = -1;
         this.doorAttemptsByLocation.clear();
         this.excludedDoorLocations.clear();
         this.excludedTransports.clear();
         this.excludedTeleportProviders.clear();
         this.excludedWhistleSlots.retainAll(this.depletedWhistleSlots);
         this.teleportAttempts = 0;
         this.status = WalkerEngine.Status.ARRIVED;
      }
   }

   private void tickTeleport(WalkerEngine.RouteRequest request, Player player) {
      WorldPoint playerLocation = player.getWorldLocation();
      if (this.activeTeleportProvider.isWhistleProvider()) {
         this.tickItemTeleport(request, player);
         return;
      }
      if (this.teleportAttempt == null) {
         if (this.activeTeleportProvider.requiresEquipmentTab() && !WalkerTabState.isCurrentTab(InterfaceTab.EQUIPMENT)) {
            if (this.lastTeleportActionTick < 0) {
               this.client.runScript(915, 4);
               this.lastTeleportActionTick = this.client.getTickCount();
               this.lastDecision = finishLegacyString(prepareLegacyString("teleport-open-equipment", 1601578258, 474206365), 474206365);
               return;
            }
            if (this.client.getTickCount() - this.lastTeleportActionTick < 3) return;
            this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("equipment-tab-unavailable", 892003507, -1717036298), -1717036298));
            return;
         }
         this.lastTeleportActionTick = -1;
         if (this.activeTeleportProvider.isAvailableForRoute(playerLocation, this.expectedTeleportDestination)
            && this.activeTeleportProvider.activate(playerLocation, this.expectedTeleportDestination)) {
            this.teleportAttempt = new WalkerEngine.TeleportAttempt(this.client.getTickCount());
            this.lastDecision = finishLegacyString(prepareLegacyString("teleport-dispatched", 822331522, -1920954404), -1920954404);
            String message = finishLegacyString(prepareLegacyString("teleport dispatched teleport={} start={} expected={}", 822331522, 200079665), 200079665);
            this.debug(message, this.activeTeleportProvider.name, playerLocation, this.expectedTeleportDestination);
            return;
         }
         this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("activation-unavailable", 95959986, 395592779), 395592779));
         return;
      }
      boolean movingOrAnimating = player.getPoseAnimation() != player.getIdlePoseAnimation() || player.getAnimation() != -1;
      if (playerLocation.getPlane() == this.expectedTeleportDestination.getPlane()
         && playerLocation.distanceTo2D(this.expectedTeleportDestination) <= 6) {
         if (!movingOrAnimating) this.finishTeleportAndReplan(request, player);
         return;
      }
      if (this.client.getTickCount() - this.teleportAttempt.startedTick >= 16) {
         this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("landing-timeout", 1961385918, -2116475766), -2116475766));
      }
   }

   void onInventoryChanged(ItemContainer inventory) {
      int[] snapshot = inventory == null ? null : new int[56];
      Set<Integer> seenItemIds = new HashSet<>();
      boolean duplicateWhistles = false;
      if (inventory != null) {
         for (int slot = 0; slot < 28; slot++) {
            Item item = inventory.getItem(slot);
            if (item != null) {
               boolean isWhistle = Arrays.stream(QuetzalNetwork.WHISTLE_ITEM_IDS).anyMatch(whistleId -> {
                  int itemId = item.getId();
                  return whistleId == itemId;
               });
               if (isWhistle) {
                  duplicateWhistles |= !seenItemIds.add(item.getId());
               }

               if (isWhistle) {
                  snapshot[slot * 2] = item.getId();
                  snapshot[slot * 2 + 1] = item.getQuantity();
               }
            }
         }
      }

      if (inventory == null || duplicateWhistles || !Arrays.equals(this.whistleInventorySnapshot, snapshot)) {
         this.refreshTeleportInventory();
      }

      this.whistleInventorySnapshot = snapshot;
   }

   void refreshTeleportInventory() {
      this.depletedWhistleSlots.clear();
      this.excludedWhistleSlots.clear();
      this.inventoryRevision++;
   }

   boolean handleActionFailureMessage(String message) {
      String explanation = TravelFailureMessages.explainActionFailure(message);
      if (explanation != null
         && this.activeRequest != null
         && this.activeRequest == this.executionRequest
         && this.activeRequest.execute
         && this.status != WalkerEngine.Status.BLOCKED
         && this.status != WalkerEngine.Status.ARRIVED
         && (this.pendingDoor != null || this.transportAttempt != null || this.teleportAttempt != null || this.lastWalkClickTick >= 0 || this.bankSupplyPlan != null && this.bankSupplyPlan.hasStartedBankInteraction())) {
         this.rejectCurrentAction(explanation);
         return true;
      } else {
         return false;
      }
   }

   void rejectCurrentAction(String message) {
      this.failureMessage = message;
      this.failNavigation("action-rejected");
   }

   void onTeleportChargeMessage(String message) {
      int charges = QuetzalNetwork.parseWhistleCharges(message);
      if (charges > 0) {
         this.depletedWhistleSlots.clear();
         this.excludedWhistleSlots.clear();
      }

      if (this.activeTeleportProvider != null
         && this.activeTeleportProvider.isWhistleProvider()
         && this.teleportAttempt != null
         && this.teleportAttempt.itemActionTick < 0
         && this.teleportAttempt.inventorySlot >= 0
         && this.teleportAttempt.inventoryRevision == this.inventoryRevision
         && this.client.getTickCount() - this.teleportAttempt.startedTick < 20) {
         if (charges >= 0) {
            this.teleportAttempt.whistleCharges = charges;
         }

         if (charges == 0) {
            this.depletedWhistleSlots.add(this.teleportAttempt.inventorySlot);
            this.excludedWhistleSlots.add(this.teleportAttempt.inventorySlot);
         }
      }
   }

   private void tickItemTeleport(WalkerEngine.RouteRequest request, Player player) {
      int currentTick = this.client.getTickCount();
      if (this.teleportAttempt == null
         && (QuetzalNetwork.isMapOpen(this.client, 949) || !WalkerTabState.isCurrentTab(InterfaceTab.INVENTORY))) {
         if (this.lastTeleportActionTick >= 0 && currentTick - this.lastTeleportActionTick < 3) return;
         if (++this.teleportAttempts > 3) {
            this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("whistle-inventory-unavailable", 734508036, 474206364), 474206364));
            return;
         }
         if (QuetzalNetwork.isMapOpen(this.client, 949)) {
            Widget closeButton = this.client.getWidget(949, 18);
            NewMenuEntry close = new NewMenuEntry()
               .option(finishLegacyString(prepareLegacyString("Close", 240603517, -1717036297), -1717036297))
               .target(finishLegacyString(prepareLegacyString("", 240603517, 395592778), 395592778))
               .identifier(1).type(MenuAction.CC_OP).param0(-1).param1(closeButton.getId()).itemId(-1);
            this.dispatchMenuInteraction(close, closeButton.getBounds(), true);
         } else {
            this.client.runScript(915, 3);
         }
         this.lastTeleportActionTick = currentTick;
         return;
      }

      if (this.teleportAttempt == null) {
         if (this.whistleInventorySnapshot == null) this.onInventoryChanged(this.client.getItemContainer(InventoryID.INVENTORY));
         if (!this.activeTeleportProvider.isAvailableForRoute(player.getWorldLocation(), this.expectedTeleportDestination)) {
            this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("whistle-unavailable", 1902178031, -1920954403), -1920954403));
            return;
         }
         int slot = QuetzalNetwork.findWhistleInventorySlot(this.client, this.excludedWhistleSlots);
         if (slot < 0) {
            this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("whistle-missing", 1631665062, 200079664), 200079664));
            return;
         }
         this.teleportAttempt = new WalkerEngine.TeleportAttempt(currentTick);
         this.teleportAttempt.inventorySlot = slot;
         this.teleportAttempt.inventoryRevision = this.inventoryRevision;
         this.teleportAttempt.itemId = this.client.getItemContainer(InventoryID.INVENTORY).getItem(slot).getId();
         if (this.teleportAttempt.itemId != 33120) {
            String check = finishLegacyString(prepareLegacyString("Check", 1822367950, -2116475765), -2116475765);
            this.clickTeleportItemAction(check, 3);
            this.lastDecision = finishLegacyString(prepareLegacyString("whistle-check-charges", 1822367950, 1069903390), 1069903390);
            return;
         }
         this.teleportAttempt.whistleCharges = Integer.MAX_VALUE;
      }

      WalkerEngine.TeleportAttempt attempt = this.teleportAttempt;
      if (attempt.itemActionTick < 0) {
         if (attempt.whistleCharges == 0 || currentTick - attempt.startedTick >= 20) {
            this.excludedWhistleSlots.add(attempt.inventorySlot);
            this.teleportAttempt = null;
            this.lastDecision = finishLegacyString(prepareLegacyString("whistle-skip-empty-or-unverified", 448152333, -1255057295), -1255057295);
            return;
         }
         if (attempt.whistleCharges < 0) return;
         if (this.activeTeleportProvider.isAvailableForRoute(player.getWorldLocation(), this.expectedTeleportDestination)) {
            String signal = finishLegacyString(prepareLegacyString("Signal", 1969685047, 857572292), 857572292);
            if (this.clickTeleportItemAction(signal, 2)) {
               attempt.itemActionTick = currentTick;
               this.lastDecision = finishLegacyString(prepareLegacyString("whistle-open-map", 364737399, 662059186), 662059186);
               return;
            }
         }
         this.excludeTeleportAndReplan(player, finishLegacyString(prepareLegacyString("whistle-changed", 133265423, -1458975393), -1458975393));
         return;
      }
      if (currentTick - attempt.itemActionTick >= 20) {
         this.failNavigation(finishLegacyString(prepareLegacyString("quetzal-flight-failed", 546701350, -580754939), -580754939));
         return;
      }
      if (QuetzalNetwork.isMapOpen(this.client, 949)) {
         if (!this.quetzals.isWhistleDestinationAvailable(this.client, this.expectedTeleportDestination)) {
            this.failNavigation(finishLegacyString(prepareLegacyString("quetzal-access-lost", 941395020, 1531882904), 1531882904));
            return;
         }
         if (attempt.destinationSelectionTick >= 0) return;
         Widget destination = this.quetzals.findDestinationWidget(this.client, this.expectedTeleportDestination, 949);
         if (destination != null) {
            NewMenuEntry entry = new NewMenuEntry().option(destination.getActions()[0])
               .target(finishLegacyString(prepareLegacyString("", 1324707247, -793077773), -793077773))
               .identifier(1).type(MenuAction.CC_OP).param0(destination.getIndex()).param1(destination.getId()).itemId(-1);
            this.dispatchMenuInteraction(entry, destination.getBounds(), true);
            attempt.destinationSelectionTick = currentTick;
            this.lastDecision = finishLegacyString(prepareLegacyString("whistle-select-destination", 1324707247, 1319551302), 1319551302);
         }
         return;
      }
      boolean settled = attempt.destinationSelectionTick >= 0
         && !QuetzalNetwork.isFadeOverlayVisible(this.client)
         && player.getAnimation() == -1
         && player.getPoseAnimation() == player.getIdlePoseAnimation()
         && player.getWorldLocation().distanceTo(this.expectedTeleportDestination) <= 6;
      if (!settled) {
         attempt.settledLandingTick = -1;
      } else if (attempt.settledLandingTick < 0) {
         attempt.settledLandingTick = currentTick;
      } else if (currentTick > attempt.settledLandingTick) {
         this.finishTeleportAndReplan(request, player);
      }
   }

   private boolean clickTeleportItemAction(String action, int actionIndex) {
      ItemContainer inventory = this.client.getItemContainer(InventoryID.INVENTORY);
      Item item = inventory == null ? null : inventory.getItem(this.teleportAttempt.inventorySlot);
      if (item == null || item.getId() != this.teleportAttempt.itemId) return false;
      Widget inventoryWidget = this.client.getWidget(9764864);
      if (inventoryWidget == null || inventoryWidget.isHidden()) return false;
      Widget slotWidget = inventoryWidget.getChild(this.teleportAttempt.inventorySlot);
      if (slotWidget == null || slotWidget.getItemId() != item.getId()) return false;
      NewMenuEntry entry = new NewMenuEntry().option(action).target(this.client.getItemDefinition(item.getId()).getName())
         .identifier(actionIndex).type(MenuAction.CC_OP).param0(this.teleportAttempt.inventorySlot)
         .param1(inventoryWidget.getId()).itemId(item.getId());
      this.dispatchMenuInteraction(entry, slotWidget.getBounds(), true);
      return true;
   }

   private boolean finishTeleportAndReplan(WalkerEngine.RouteRequest request, Player player) {
      TeleportProviders.TeleportProvider provider = this.activeTeleportProvider;
      WorldPoint expected = this.expectedTeleportDestination;
      WorldPoint actual = player.getWorldLocation();
      this.activeTeleportProvider = null;
      this.expectedTeleportDestination = null;
      this.teleportAttempt = null;
      this.lastTeleportActionTick = -1;
      this.currentLocalRoute = null;
      this.globalRoute = Collections.emptyList();
      this.globalRouteIndex = 0;
      this.localRouteSegments = Collections.emptyList();
      this.localRouteTransports = Collections.emptyList();
      this.routeSegments = Collections.emptyList();
      this.activeTransportMarker = null;
      this.markInitialClicksPending();
      this.lastDecision = finishLegacyString(prepareLegacyString("teleport-landed-planning", 105601167, 474206459), 474206459);
      String message = finishLegacyString(prepareLegacyString("teleport landed teleport={} expected={} actual={}", 105601167, -1717036400), -1717036400);
      this.debug(message, provider.name, expected, actual);
      if (!this.captureAndStartPlanning(request, player, false)) {
         this.failNavigation(finishLegacyString(prepareLegacyString("teleport-landing-route-unavailable", 424359332, 395592749), 395592749));
      }
      return true;
   }

   private void excludeTeleportAndReplan(Player player, String reason) {
      TeleportProviders.TeleportProvider rejected = this.activeTeleportProvider;
      if (rejected != null) this.excludedTeleportProviders.add(rejected);
      String message = finishLegacyString(prepareLegacyString("teleport rejected teleport={} reason={} rejectedCount={} destination={}", 2053962895, 20805570), 20805570);
      String providerName = rejected == null ? finishLegacyString(prepareLegacyString("none", 80453934, -2066301527), -2066301527) : rejected.name;
      this.debug(message, providerName, reason, this.excludedTeleportProviders.size(), this.getDestination());
      this.activeTeleportProvider = null;
      this.expectedTeleportDestination = null;
      this.teleportAttempt = null;
      this.lastTeleportActionTick = -1;
      this.currentLocalRoute = null;
      this.globalRoute = Collections.emptyList();
      this.globalRouteIndex = 0;
      this.localRouteSegments = Collections.emptyList();
      this.localRouteTransports = Collections.emptyList();
      this.routeSegments = Collections.emptyList();
      this.activeTransportMarker = null;
      this.lastDecision = finishLegacyString(prepareLegacyString("replan-rejected-teleport", 1355583216, 183165716), 183165716);
      if (this.executionRequest != null && this.captureAndStartPlanning(this.executionRequest, player, true)) return;
      this.failNavigation(finishLegacyString(prepareLegacyString("rejected-teleport-route-unavailable", 52059982, -1862391677), -1862391677));
   }

   private void excludeTransportAndReplan(Player player) {
      TransportCatalog.TransportEdge rejected = this.activeTransport;
      this.excludedTransports.add(rejected);
      String message = finishLegacyString(prepareLegacyString("transition-rejected step={} rejectedCount={} destination={}", 601738851, -1949329109), -1949329109);
      this.debug(message, describeTransport(rejected), this.excludedTransports.size(), this.getDestination());
      this.currentLocalRoute = null;
      this.globalRoute = Collections.emptyList();
      this.globalRouteIndex = 0;
      this.localRouteSegments = Collections.emptyList();
      this.localRouteTransports = Collections.emptyList();
      this.activeTransport = null;
      this.transportAttempt = null;
      this.transportAttempts = 0;
      this.pendingDoor = null;
      this.lastWalkClickTick = -1;
      this.walkTarget = null;
      this.routeSegments = Collections.emptyList();
      this.activeTransportMarker = null;
      this.lastDecision = finishLegacyString(prepareLegacyString("replan-rejected-transition", 601738851, 237261632), 237261632);
      if (this.executionRequest != null && this.captureAndStartPlanning(this.executionRequest, player, true)) return;
      this.failNavigation(finishLegacyString(prepareLegacyString("rejected-transport-route-unavailable", 631329796, -2145907203), -2145907203));
   }

   private boolean activateTransport(WorldView worldView, TransportCatalog.TransportEdge transport, boolean directClick) {
      if (QuetzalNetwork.isQuetzalTransport(transport) && !this.quetzals.isTransportAvailable(this.client, transport)) return false;
      if (FairyRingNetwork.isFairyRingTransport(transport)) {
         if (!this.isTransportEnabledBySettings(transport)
            || !this.fairyRings.isTransportAvailable(this.client, transport)
            || FairyRingNetwork.getRequiredStaffName(this.client) != null) return false;
         this.fairyRings.resetDialTracking();
      }
      if (transport.hasAlternativeIds()) return this.activateNpcTransport(worldView, transport, directClick);
      TileObject object = this.findTransportObject(worldView, transport);
      WalkerEngine.EntityAction action = object == null ? null : this.resolveTransportAction(object, transport);
      if (object != null && action != null) {
         Rectangle bounds = Rs2UiHelper.getObjectClickbox(object);
         this.interactTransportObject(object, worldView, action, bounds, directClick);
         return true;
      }
      String message = finishLegacyString(prepareLegacyString("transition unavailable step={} objectFound={} actionFound={}", 152270822, 1299621471), 1299621471);
      this.debug(message, describeTransport(transport), object != null, action != null);
      return false;
   }

   private void interactTransportObject(TileObject object, WorldView worldView, WalkerEngine.EntityAction action, Rectangle bounds, boolean directClick) {
      Point sceneLocation = object instanceof GameObject ? ((GameObject) object).getSceneMinLocation()
         : new Point(object.getLocalLocation().getSceneX(), object.getLocalLocation().getSceneY());
      NewMenuEntry entry = new NewMenuEntry().param0(sceneLocation.getX()).param1(sceneLocation.getY())
         .opcode(action.menuAction.getId()).identifier(object.getId()).itemId(-1)
         .option(action.option).target(action.target).setWorldViewId(worldView.getId()).gameObject(object);
      this.dispatchMenuInteraction(entry, bounds, directClick);
   }

   boolean hasBankSupplyPlan() {
      return this.bankSupplyPlan != null;
   }

   Object getRequestIdentity() {
      return this.activeRequest;
   }

   boolean selectInventoryItem(QuestItemResolver item) {
      Widget inventoryWidget = this.client.getWidget(9764864);
      Widget slotWidget = inventoryWidget == null ? null : inventoryWidget.getChild(item.inventorySlot);
      if (!this.client.isWidgetSelected()
         && slotWidget != null
         && !inventoryWidget.isHidden()
         && !slotWidget.isHidden()
         && slotWidget.getItemId() == item.itemId
         && Rs2UiHelper.isRectangleWithinCanvas(slotWidget.getBounds())) {
         this.dispatchMenuInteraction(
            new NewMenuEntry()
               .option("Use")
               .target(item.name)
               .identifier(0)
               .type(MenuAction.WIDGET_TARGET)
               .param0(item.inventorySlot)
               .param1(inventoryWidget.getId())
               .itemId(item.itemId),
            slotWidget.getBounds(),
            true
         );
         return true;
      } else {
         return false;
      }
   }

   boolean performInventoryItemAction(QuestItemResolver.InventoryItemAction interaction) {
      QuestItemResolver item = interaction.targetItem == null ? interaction.selectedItem : interaction.targetItem;
      ItemContainer inventory = this.client.getItemContainer(InventoryID.INVENTORY);
      Item carriedItem = inventory == null ? null : inventory.getItem(item.inventorySlot);
      Widget inventoryWidget = this.client.getWidget(9764864);
      Widget slotWidget = inventoryWidget == null ? null : inventoryWidget.getChild(item.inventorySlot);
      if (carriedItem != null
         && carriedItem.getId() == item.itemId
         && slotWidget != null
         && !inventoryWidget.isHidden()
         && !slotWidget.isHidden()
         && slotWidget.getItemId() == item.itemId
         && Rs2UiHelper.isRectangleWithinCanvas(slotWidget.getBounds())) {
         int actionId = 0;
         String option = interaction.action;
         MenuAction menuAction;
         if (interaction.targetItem != null) {
            if (!interaction.selectedItem.isSelected(this.client)) {
               return false;
            }

            menuAction = MenuAction.WIDGET_TARGET_ON_WIDGET;
         } else {
            if (this.client.isWidgetSelected()) {
               return false;
            }

            String[] actions = slotWidget.getActions();
            if (actions == null) {
               return false;
            }

            for (int index = 0; index < actions.length; index++) {
               if (actions[index] != null && Text.removeTags(actions[index]).equalsIgnoreCase(interaction.action)) {
                  if (actionId != 0) {
                     return false;
                  }

                  actionId = index + 1;
                  option = Text.removeTags(actions[index]);
               }
            }

            if (actionId == 0) {
               return false;
            }

            menuAction = actionId > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
         }

         this.dispatchMenuInteraction(
            new NewMenuEntry().option(option).target(item.name).identifier(actionId).type(menuAction).param0(item.inventorySlot).param1(inventoryWidget.getId()).itemId(item.itemId),
            slotWidget.getBounds(),
            true
         );
         return true;
      } else {
         return false;
      }
   }

   boolean useSelectedItemOnQuestTarget(QuestItemResolver item, QuestHelperBridge.QuestTarget questTarget) {
      if (!item.isSelected(this.client)) {
         return false;
      }

      if (questTarget.npc != null) {
         NPC npc = questTarget.npc;
         Rectangle npcBounds = npc.getConvexHull() == null ? null : npc.getConvexHull().getBounds();
         if (npc.getWorldView() == this.client.getTopLevelWorldView()
            && npc.getId() >= 0
            && npcBounds != null
            && !npcBounds.isEmpty()
            && Rs2UiHelper.isRectangleWithinCanvas(npcBounds)) {
            this.interactTransportNpc(
               npc, npc.getWorldView(), new WalkerEngine.EntityAction("Use", MenuAction.WIDGET_TARGET_ON_NPC, npc.getName()), npcBounds, true
            );
            return true;
         } else {
            return false;
         }
      } else {
         TileObject object = questTarget.object;
         Rectangle objectBounds = object == null ? null : Rs2UiHelper.getObjectClickbox(object);
         if (object != null
            && object.getWorldView() == this.client.getTopLevelWorldView()
            && objectBounds != null
            && !objectBounds.isEmpty()
            && Rs2UiHelper.isRectangleWithinCanvas(objectBounds)) {
            ObjectComposition definition = this.getTransformedObjectDefinition(object.getId());
            if (definition == null) {
               return false;
            }

            this.interactTransportObject(
               object,
               object.getWorldView(),
               new WalkerEngine.EntityAction("Use", MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, definition.getName()),
               objectBounds,
               true
            );
            return true;
         } else {
            return false;
         }
      }
   }

   String interactQuestObject(TileObject object, Object questStep) {
      if (object != null && object.getId() >= 0 && object.getWorldView() == this.client.getTopLevelWorldView()) {
         ObjectComposition definition = this.getTransformedObjectDefinition(object.getId());
         if (definition != null && definition.getActions() != null) {
            String[] actions = (String[])definition.getActions().clone();

            for (int index = 0; index < Math.min(5, actions.length); index++) {
               String override = object.getOpOverride(index);
               if (override != null) {
                  actions[index] = override;
               }
            }

            int actionIndex = QuestHelperBridge.resolveInteractionActionIndex(questStep, actions);
            if (actionIndex < 0) {
               return null;
            } else {
               Rectangle bounds = Rs2UiHelper.getObjectClickbox(object);
               if (bounds != null && !bounds.isEmpty() && Rs2UiHelper.isRectangleWithinCanvas(bounds)) {
                  this.interactTransportObject(object, object.getWorldView(), new WalkerEngine.EntityAction(actions[actionIndex], objectMenuActionForIndex(actionIndex), definition.getName()), bounds, true);
                  return actions[actionIndex];
               } else {
                  return null;
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean activateNpcTransport(WorldView worldView, TransportCatalog.TransportEdge transport, boolean directClick) {
      NPC npc = this.findTransportNpc(transport);
      WalkerEngine.EntityAction action = npc == null ? null : this.resolveTransportAction(npc, transport);
      if (npc != null && action != null) {
         String message = finishLegacyString(prepareLegacyString("transition-click step={} liveNpcId={} liveNpc={} action={} attempt={} direct={}", 523669420, -929207246), -929207246);
         this.debug(message, describeTransport(transport), npc.getId(), npc.getWorldLocation(), action.option, this.transportAttempts + 1, directClick);
         Rectangle bounds = Rs2UiHelper.getActorClickbox(npc);
         this.interactTransportNpc(npc, worldView, action, bounds, directClick);
         return true;
      }
      String message = finishLegacyString(prepareLegacyString("transition unavailable step={} npcFound={} actionFound={}", 1619558322, 1299621465), 1299621465);
      this.debug(message, describeTransport(transport), npc != null, action != null);
      return false;
   }

   boolean openNearbyBank() {
      TileObject bankObject = SceneObjects.findBank();
      if (bankObject == null) {
         bankObject = SceneObjects.findGrandExchangeBooth();
      }

      if (bankObject != null && bankObject.getWorldLocation().distanceTo(this.client.getLocalPlayer().getWorldLocation()) <= 8) {
         ObjectComposition objectDefinition = this.getTransformedObjectDefinition(bankObject.getId());
         String[] objectActions = objectDefinition == null ? null : objectDefinition.getActions();
         if (objectActions != null) {
            for (int objectActionIndex = 0; objectActionIndex < objectActions.length; objectActionIndex++) {
               if ("Bank".equalsIgnoreCase(objectActions[objectActionIndex])) {
                  Rectangle objectBounds = Rs2UiHelper.getObjectClickbox((TileObject)bankObject);
                  if (objectBounds != null && Rs2UiHelper.isRectangleWithinCanvas(objectBounds)) {
                     this.interactTransportObject(
                        (TileObject)bankObject,
                        bankObject.getWorldView(),
                        new WalkerEngine.EntityAction("Bank", objectMenuActionForIndex(objectActionIndex), objectDefinition.getName()),
                        objectBounds,
                        true
                     );
                     return true;
                  }
                  break;
               }
            }
         }
      }

      Rs2NpcModel banker = Rs2Npc.getBankerNPC();
      if (banker != null && banker.getWorldLocation().distanceTo(this.client.getLocalPlayer().getWorldLocation()) <= 8) {
         NPCComposition npcDefinition = banker.getTransformedComposition();
         String[] npcActions = npcDefinition == null ? null : npcDefinition.getActions();
         if (npcActions == null) {
            return false;
         }

         for (int npcActionIndex = 0; npcActionIndex < npcActions.length; npcActionIndex++) {
            if ("Bank".equalsIgnoreCase(npcActions[npcActionIndex])) {
               Rectangle npcBounds = banker.getConvexHull() == null ? null : banker.getConvexHull().getBounds();
               if (npcBounds != null && Rs2UiHelper.isRectangleWithinCanvas(npcBounds)) {
                  this.interactTransportNpc(
                     banker, banker.getWorldView(), new WalkerEngine.EntityAction("Bank", npcMenuActionForIndex(npcActionIndex), npcDefinition.getName()), npcBounds, true
                  );
                  return true;
               }

               return false;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   boolean clickWidgetAction(Widget widget, String action) {
      if (widget != null && !widget.isHidden() && widget.getActions() != null && Rs2UiHelper.isRectangleWithinCanvas(widget.getBounds())) {
         String[] actions = widget.getActions();

         for (int index = 0; index < actions.length; index++) {
            if (action.equalsIgnoreCase(actions[index])) {
               this.dispatchMenuInteraction(
                  new NewMenuEntry()
                     .option(action)
                     .target("")
                     .identifier(index + 1)
                     .type(index < 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY)
                     .param0(widget.getIndex())
                     .param1(widget.getId())
                     .itemId(widget.getItemId()),
                  widget.getBounds(),
                  true
               );
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   String interactQuestNpc(NPC npc, Object questStep) {
      if (npc != null && npc.getId() >= 0 && npc.getWorldView() == this.client.getTopLevelWorldView()) {
         NPCComposition definition = npc.getTransformedComposition();
         if (definition == null) {
            return null;
         } else {
            String[] actions = definition.getActions();
            int actionIndex = QuestHelperBridge.resolveInteractionActionIndex(questStep, actions);
            if (actionIndex < 0) {
               return null;
            } else {
               Rectangle bounds = npc.getConvexHull() == null ? null : npc.getConvexHull().getBounds();
               if (bounds != null && !bounds.isEmpty() && Rs2UiHelper.isRectangleWithinCanvas(bounds)) {
                  this.interactTransportNpc(npc, npc.getWorldView(), new WalkerEngine.EntityAction(actions[actionIndex], npcMenuActionForIndex(actionIndex), definition.getName()), bounds, true);
                  return actions[actionIndex];
               } else {
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private void interactTransportNpc(NPC npc, WorldView worldView, WalkerEngine.EntityAction action, Rectangle bounds, boolean directClick) {
      NewMenuEntry entry = new NewMenuEntry().param0(0).param1(0).opcode(action.menuAction.getId())
         .identifier(npc.getIndex()).itemId(-1).option(action.option).target(action.target)
         .setWorldViewId(worldView.getId()).actor(npc);
      this.dispatchMenuInteraction(entry, bounds, directClick);
   }

   private boolean tickNpcTransportDialogue() {
      if (this.activeTransport == null || !this.activeTransport.hasAlternativeIds()) {
         return false;
      }
      if (Rs2Dialogue.hasContinue()) {
         Rs2Dialogue.clickContinue();
         this.transportAttempt.lastProgressTick = this.client.getTickCount();
         this.lastDecision = "npc-transport-dialogue-continue";
         return true;
      }
      if (Rs2Dialogue.hasSelectAnOption()) {
         for (Widget option : Rs2Dialogue.getDialogueOptions()) {
            String raw = option == null ? null : option.getText();
            String clean = raw == null ? "" : Text.removeTags(raw).trim().toLowerCase();
            if (clean.contains("great kourend") || clean.contains("take me there")
               || clean.contains("port piscarilius") || clean.contains("port sarim")
               || clean.contains(this.activeTransport.action.toLowerCase())) {
               Rs2Dialogue.clickOption(raw, true);
               this.transportAttempt.lastProgressTick = this.client.getTickCount();
               this.lastDecision = "npc-transport-dialogue-option";
               return true;
            }
         }
      }
      return false;
   }

   private boolean tickQuetzalSelection() {
      if (!this.quetzals.isTransportAvailable(this.client, this.activeTransport)) {
         this.failNavigation("quetzal-access-lost");
         return true;
      }

      boolean mapOpen = QuetzalNetwork.isMapOpen(this.client);
      if (!mapOpen && this.transportAttempt.firstSelectionTick < 0) {
         return false;
      }

      int currentTick = this.client.getTickCount();
      int firstActionTick = this.transportAttempt.firstSelectionTick < 0 ? this.transportAttempt.startedTick : this.transportAttempt.firstSelectionTick;
      if (currentTick - firstActionTick >= 20) {
         this.failNavigation(
            mapOpen
               ? "quetzal-selection-failed"
               : "quetzal-flight-failed"
         );
         return true;
      }

      if (!mapOpen) {
         this.lastDecision = "quetzal-wait-landing";
         return true;
      }

      if (this.transportAttempt.selectionAttempts > 0 && !isTransportRetryDue(currentTick, this.transportAttempt.lastProgressTick)) {
         return true;
      }

      if (this.transportAttempt.selectionAttempts >= 3) {
         this.failNavigation("quetzal-selection-failed");
         return true;
      }

      Widget destinationWidget = this.quetzals.findDestinationWidget(this.client, this.activeTransport);
      if (destinationWidget != null) {
         this.dispatchMenuInteraction(
            new NewMenuEntry()
               .option(destinationWidget.getActions()[0])
               .target("")
               .identifier(1)
               .type(MenuAction.CC_OP)
               .param0(destinationWidget.getIndex())
               .param1(destinationWidget.getId())
               .itemId(-1),
            destinationWidget.getBounds(),
            true
         );
         if (this.transportAttempt.firstSelectionTick < 0) {
            this.transportAttempt.firstSelectionTick = currentTick;
         }

         this.transportAttempt.selectionAttempts++;
         this.transportAttempt.lastProgressTick = currentTick;
      }

      this.lastDecision = "quetzal-select";
      return true;
   }

   private boolean replanTransportApproach(WorldView worldView, WorldPoint playerLocation) {
      if (this.activeTransport.origin.getPlane() != this.activeTransport.destination.getPlane() || this.globalRoute.isEmpty()) return false;
      int landingIndex = findRouteIndexAtOrAfter(this.globalRoute, this.activeTransport.destination, this.globalRouteIndex);
      if (landingIndex < 0) return false;
      int loadedEnd = this.findLoadedGlobalRouteEnd(worldView, landingIndex);
      WalkerEngine.LocalRoute route = this.planLocalRoute(worldView, playerLocation, this.globalRoute.get(loadedEnd), playerLocation.getPlane());
      if (route == null) return false;
      TransportCatalog.TransportEdge preparedTransport = this.activeTransport;
      List<WorldPoint> resumedRoute = new ArrayList<>(route.points);
      resumedRoute.addAll(this.globalRoute.subList(loadedEnd + 1, this.globalRoute.size()));
      this.globalRoute = Collections.unmodifiableList(resumedRoute);
      this.globalRouteIndex = 0;
      this.currentLocalRoute = route;
      this.localRouteSegments = Collections.singletonList(route);
      this.localRouteTransports = Collections.emptyList();
      int routeLength = route.points.size();
      this.activeTransport = routeLength < this.globalRoute.size()
         ? this.findTransportEdge(this.globalRoute.get(routeLength - 1), this.globalRoute.get(routeLength)) : null;
      this.transportAttempt = null;
      this.transportAttempts = 0;
      this.pendingDoor = null;
      this.lastWalkClickTick = -1;
      this.walkTarget = null;
      this.routeSegments = splitRouteByPlane(this.globalRoute);
      this.markInitialClicksPending();
      this.updateNextDoorMarker(worldView, this.currentLocalRoute, 0);
      this.status = WalkerEngine.Status.WALKING;
      String message = finishLegacyString(prepareLegacyString("transition-prepared step={} player={} resumedPathLength={}", 552477149, 643112457), 643112457);
      this.debug(message, describeTransport(preparedTransport), playerLocation, this.globalRoute.size());
      return true;
   }

   private void updateNextDoorMarker(WorldView worldView, WalkerEngine.LocalRoute route, int routeIndex) {
      int[][] flags = route == null ? null : this.getCollisionFlags(worldView, route.plane);
      if (flags != null) {
         WalkerEngine.DoorCollisionOverrides doorOverrides = this.buildDoorCollisionOverrides(worldView, route.plane, flags.length, flags[0].length);
         this.updateNextDoorMarker(route, doorOverrides, routeIndex);
      } else {
         this.activeTransportMarker = this.activeTransport == null ? null
            : new WalkerEngine.TransportMarker(this.activeTransport.origin, this.activeTransport.action, this.activeTransport.target);
      }
   }

   private void updateNextDoorMarker(WalkerEngine.LocalRoute route, WalkerEngine.DoorCollisionOverrides doorOverrides, int routeIndex) {
      WalkerEngine.RouteDoor nextDoor = doorOverrides.findNextDoorOnRoute(route.packedTiles, route.gridWidth, routeIndex);
      if (nextDoor != null) {
         WorldPoint location = route.points.get(nextDoor.routeIndex);
         this.activeTransportMarker = new WalkerEngine.TransportMarker(location, nextDoor.door.option, nextDoor.door.target);
      } else {
         this.activeTransportMarker = this.activeTransport == null ? null
            : new WalkerEngine.TransportMarker(this.activeTransport.origin, this.activeTransport.action, this.activeTransport.target);
      }
   }

   private TileObject findTransportObject(WorldView worldView, TransportCatalog.TransportEdge transport) {
      Scene scene = worldView.getScene();
      Tile[][][] tiles = scene == null ? null : scene.getTiles();
      int plane = transport.origin.getPlane();
      if (tiles == null || plane < 0 || plane >= tiles.length) {
         return null;
      }
      LocalPoint localOrigin = LocalPoint.fromWorld(worldView, withWorldViewPlane(transport.origin, worldView));
      if (localOrigin == null) {
         return null;
      }

      int minX = Math.max(0, localOrigin.getSceneX() - 4);
      int maxX = Math.min(tiles[plane].length - 1, localOrigin.getSceneX() + 4);
      for (int exactIdPass = 1; exactIdPass >= 0; exactIdPass--) {
         TileObject closest = null;
         for (int sceneX = minX; sceneX <= maxX; sceneX++) {
            int minY = Math.max(0, localOrigin.getSceneY() - 4);
            int maxY = Math.min(tiles[plane][sceneX].length - 1, localOrigin.getSceneY() + 4);
            for (int sceneY = minY; sceneY <= maxY; sceneY++) {
               TileObject candidate = this.findTransportObjectOnTile(tiles[plane][sceneX][sceneY], transport, exactIdPass == 1);
               if (candidate != null) {
                  if (closest != null && !isCloserToOrigin(transport.origin, candidate.getWorldLocation(), closest.getWorldLocation())) {
                     continue;
                  }
                  closest = candidate;
               }
            }
         }
         if (closest != null) {
            return closest;
         }
      }
      return null;
   }

   private boolean isTransportEntityLoaded(WorldView worldView, TransportCatalog.TransportEdge transport) {
      return transport.hasAlternativeIds()
         ? this.findTransportNpc(transport) != null : this.findTransportObject(worldView, transport) != null;
   }

   private NPC findTransportNpc(TransportCatalog.TransportEdge transport) {
      NPC closest = null;
      for (NPC npc : this.client.getNpcs()) {
         WorldPoint location = npc.getWorldLocation();
         if (matchesTransportNpcId(transport, npc) && location != null
            && location.getPlane() == transport.origin.getPlane()
            && location.distanceTo2D(transport.origin) <= 4
            && this.resolveTransportAction(npc, transport) != null) {
            if (closest != null && !isCloserToOrigin(transport.origin, location, closest.getWorldLocation())) {
               continue;
            }
            closest = npc;
         }
      }
      return closest;
   }

   static boolean matchesTransportNpcId(TransportCatalog.TransportEdge transport, NPC npc) {
      if (transport.matchesAlternativeId(npc.getId())) {
         return true;
      }
      NPCComposition transformed = npc.getTransformedComposition();
      return transformed != null && transport.matchesAlternativeId(transformed.getId());
   }

   static boolean isCloserToOrigin(WorldPoint origin, WorldPoint candidate, WorldPoint previous) {
      return candidate.distanceTo2D(origin) < previous.distanceTo2D(origin);
   }

   /** A visible health bar with an idle pose on an unchanged tile means the animation is combat, not a transport. */
   static boolean isStationaryCombatAnimation(int healthRatio, boolean idlePose, boolean samePosition) {
      return healthRatio >= 0 && idlePose && samePosition;
   }

   static boolean isTransportRetryDue(int currentTick, int lastProgressTick) {
      return currentTick - lastProgressTick > 3;
   }

   static boolean hasTransitionProgress(WorldPoint previousLocation, WorldPoint currentLocation, boolean previousActive, boolean currentActive) {
      return !currentLocation.equals(previousLocation) || currentActive != previousActive;
   }

   static boolean isUnexpectedPlaneStalled(int currentTick, int lastProgressTick, boolean madeProgress) {
      return !madeProgress && isTransportRetryDue(currentTick, lastProgressTick);
   }

   static boolean isWithinPostTransitionGracePeriod(int currentTick, int lastTransitionTick) {
      return lastTransitionTick >= 0 && currentTick - lastTransitionTick <= 9;
   }

   private TileObject findTransportObjectOnTile(Tile tile, TransportCatalog.TransportEdge transport, boolean requireExactId) {
      if (tile == null) {
         return null;
      }
      for (GameObject object : tile.getGameObjects()) {
         if (this.matchesTransportObject(object, transport, requireExactId)) {
            return object;
         }
      }
      WallObject wall = tile.getWallObject();
      if (this.matchesTransportObject(wall, transport, requireExactId)) {
         return wall;
      }
      DecorativeObject decoration = tile.getDecorativeObject();
      if (this.matchesTransportObject(decoration, transport, requireExactId)) {
         return decoration;
      }
      GroundObject ground = tile.getGroundObject();
      return this.matchesTransportObject(ground, transport, requireExactId) ? ground : null;
   }

   private boolean matchesTransportObject(TileObject object, TransportCatalog.TransportEdge transport, boolean requireExactId) {
      if (object == null || this.resolveTransportAction(object, transport) == null) {
         return false;
      }
      if (object.getId() == transport.objectId) {
         return true;
      }
      ObjectComposition transformed = this.getTransformedObjectDefinition(object.getId());
      return transformed != null && (transformed.getId() == transport.objectId
         || !requireExactId && transport.target.equalsIgnoreCase(transformed.getName()));
   }

   private int[][] getCollisionFlags(WorldView worldView, int plane) {
      CollisionData[] collisionMaps = worldView.getCollisionMaps();
      if (collisionMaps == null || plane < 0 || plane >= collisionMaps.length || collisionMaps[plane] == null) {
         return null;
      }
      return collisionMaps[plane].getFlags();
   }

   private WalkerEngine.DoorCollisionOverrides buildDoorCollisionOverrides(WorldView worldView, int plane, int width, int height) {
      WalkerEngine.DoorCollisionOverrides overrides = new WalkerEngine.DoorCollisionOverrides(width, height);
      Scene scene = worldView.getScene();
      Tile[][][] tiles = scene == null ? null : scene.getTiles();
      if (tiles == null || plane < 0 || plane >= tiles.length) {
         return overrides;
      }

      for (int sceneX = 0; sceneX < Math.min(width, tiles[plane].length); sceneX++) {
         for (int sceneY = 0; sceneY < Math.min(height, tiles[plane][sceneX].length); sceneY++) {
            Tile tile = tiles[plane][sceneX][sceneY];
            WallObject wall = tile == null ? null : tile.getWallObject();
            if (wall != null && !this.excludedDoorLocations.contains(wall.getWorldLocation())
               && !this.transportObjectIds.contains(wall.getId())) {
               WalkerEngine.EntityAction action = this.resolveDoorAction(wall);
               if (action != null) {
                  WalkerEngine.DoorInteraction door = new WalkerEngine.DoorInteraction(
                     wall, action.option, action.menuAction, action.target, sceneX, sceneY);
                  LocalCollisionGrid.forEachDirectionalEdge(width, height, sceneX, sceneY, wall.getOrientationA(),
                     edgeIndex -> overrides.registerDoorEdge(edgeIndex, door));
                  LocalCollisionGrid.forEachDirectionalEdge(width, height, sceneX, sceneY, wall.getOrientationB(),
                     edgeIndex -> overrides.registerDoorEdge(edgeIndex, door));
               }
            }

            if (tile != null) {
               for (GameObject object : tile.getGameObjects()) {
                  int movementMask = object == null ? 0 : doorMovementMaskForObjectConfig(object.getConfig());
                  if (movementMask == 0 || this.excludedDoorLocations.contains(object.getWorldLocation())
                     || this.transportObjectIds.contains(object.getId())) {
                     continue;
                  }
                  WalkerEngine.EntityAction action = this.resolveDoorAction(object);
                  if (action != null) {
                     WalkerEngine.DoorInteraction door = new WalkerEngine.DoorInteraction(
                        object, action.option, action.menuAction, action.target, sceneX, sceneY);
                     overrides.registerTileOverride(sceneX, sceneY);
                     LocalCollisionGrid.forEachDirectionalEdge(width, height, sceneX, sceneY, movementMask,
                        edgeIndex -> overrides.registerDoorEdge(edgeIndex, door));
                  }
               }
            }
         }
      }
      return overrides;
   }

   static int doorMovementMaskForObjectConfig(int objectConfig) {
      return (objectConfig & 31) == 9 ? 15 : 0;
   }

   private Set<Long> collectGlobalEdgeOverridesFromScene(WorldView worldView, int plane) {
      if (worldView.isInstance()) {
         return Collections.emptySet();
      }
      int[][] flags = this.getCollisionFlags(worldView, plane);
      if (flags == null || flags.length == 0 || flags[0] == null) {
         return Collections.emptySet();
      }
      WalkerEngine.DoorCollisionOverrides doors = this.buildDoorCollisionOverrides(worldView, plane, flags.length, flags[0].length);
      Set<Long> openEdges = new HashSet<>();
      int baseX = worldView.getBaseX();
      int baseY = worldView.getBaseY();
      for (int sceneX = 0; sceneX < flags.length; sceneX++) {
         for (int sceneY = 0; sceneY < flags[sceneX].length; sceneY++) {
            this.addSceneOpenedGlobalEdge(flags, doors.overrides, openEdges, baseX, baseY, plane,
               sceneX, sceneY, sceneX + 1, sceneY);
            this.addSceneOpenedGlobalEdge(flags, doors.overrides, openEdges, baseX, baseY, plane,
               sceneX, sceneY, sceneX, sceneY + 1);
         }
      }
      return openEdges;
   }

   private void addSceneOpenedGlobalEdge(int[][] flags, BitSet overrides, Set<Long> openEdges, int baseX, int baseY, int plane, int fromSceneX, int fromSceneY, int toSceneX, int toSceneY) {
      if (LocalCollisionGrid.canStep(flags, overrides, fromSceneX, fromSceneY, toSceneX, toSceneY)) {
         int fromX = baseX + fromSceneX;
         int fromY = baseY + fromSceneY;
         int toX = baseX + toSceneX;
         int toY = baseY + toSceneY;
         if (!this.planner.canStep(fromX, fromY, plane, toX, toY)) {
            openEdges.add(CollisionRoutePlanner.packUndirectedEdge(fromX, fromY, plane, toX, toY));
         }
      }
   }

   static boolean hasExhaustedDoorRetries(int attempts) {
      return attempts >= 3;
   }

   static boolean isPointInsideViewport(Point point, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
      return point != null && point.getX() >= viewportX && point.getX() < viewportX + viewportWidth
         && point.getY() >= viewportY && point.getY() < viewportY + viewportHeight;
   }

   static boolean shouldUseCanvasForStep(int stepsAhead) {
      return stepsAhead <= 4;
   }

   static boolean hasTransportArrived(WorldPoint origin, WorldPoint destination, WorldPoint playerLocation, boolean observedProgress) {
      return observedProgress && playerLocation.getPlane() == destination.getPlane()
         && (origin.getPlane() != destination.getPlane() || playerLocation.distanceTo2D(destination) <= 1);
   }

   static WalkerEngine.PlanningFailure classifyTargetCollisionFailure(int[][] flags, int targetX, int targetY) {
      if (flags != null && targetX >= 0 && targetX < flags.length && targetY >= 0
         && targetY < flags[targetX].length && (flags[targetX][targetY] & 2359552) != 0) {
         return WalkerEngine.PlanningFailure.TARGET_NOT_STANDABLE;
      }
      return WalkerEngine.PlanningFailure.ROUTE_UNSUPPORTED;
   }

   private WalkerEngine.EntityAction resolveDoorAction(TileObject object)
   {
      int id = object.getId();
      if ((id == 2786 || id == 2787 || id == 2788 || id == 2789 || id == 44050 || id == 44051)
         && net.runelite.client.plugins.microbot.util.player.Rs2Player.getQuestState(net.runelite.api.Quest.PRINCE_ALI_RESCUE) != net.runelite.api.QuestState.FINISHED)
      {
         return null;
      }
      ObjectComposition definition = getTransformedObjectDefinition(id);
      if (definition == null)
      {
         return null;
      }

      String[] initialActions = definition.getActions();
      if (initialActions == null)
      {
         return null;
      }

      String[] actions = definition.getActions();
      for (int index = 0; index < actions.length; index++)
      {
         String action = actions[index];

         String encodedOpen = prepareLegacyString("Open", 117674975, -1154476754);
         String open = finishLegacyString(encodedOpen, -1154476754);
         if (open.equalsIgnoreCase(action))
         {
            MenuAction menuAction = objectMenuActionForIndex(index);
            String name = definition.getName();
            return new WalkerEngine.EntityAction(action, menuAction, name);
         }

         String encodedPass = prepareLegacyString("Pass", 496046291, 1054596933);
         String pass = finishLegacyString(encodedPass, 1054596933);
         if (pass.equalsIgnoreCase(action))
         {
            MenuAction menuAction = objectMenuActionForIndex(index);
            String name = definition.getName();
            return new WalkerEngine.EntityAction(action, menuAction, name);
         }
      }

      return null;
   }

   private WalkerEngine.EntityAction resolveTransportAction(TileObject object, TransportCatalog.TransportEdge transport) {
      ObjectComposition definition = this.getTransformedObjectDefinition(object.getId());
      if (definition == null || definition.getActions() == null) return null;
      String[] actions = definition.getActions().clone();
      for (int index = 0; index < actions.length; index++) {
         String override = object.getOpOverride(index);
         actions[index] = override != null ? override : actions[index];
      }
      int index = findTransportActionIndex(transport.action, transport.target, definition.getName(), actions);
      return index >= 0 ? new WalkerEngine.EntityAction(actions[index], objectMenuActionForIndex(index), definition.getName()) : null;
   }

   private WalkerEngine.EntityAction resolveTransportAction(NPC npc, TransportCatalog.TransportEdge transport) {
      NPCComposition definition = npc.getTransformedComposition();
      if (definition == null) return null;
      String[] actions = definition.getActions();
      int index = findTransportActionIndex(transport.action, transport.target, definition.getName(), actions);
      if (index < 0 && (transport.matchesAlternativeId(8630) || transport.matchesAlternativeId(10726))) {
         index = findTransportActionIndex("Talk-to", transport.target, definition.getName(), actions);
      }
      return index < 0 ? null : new WalkerEngine.EntityAction(actions[index], npcMenuActionForIndex(index), definition.getName());
   }

   static int findTransportActionIndex(String request, String target, String objectName, String[] actions)
   {
      if (actions == null)
      {
         return -1;
      }

      for (int index = 0; index < actions.length; index++)
      {
         if (matchesActionIgnoringHyphens(request, actions[index]))
         {
            return index;
         }

         String encodedEnterRequest = prepareLegacyString("Enter", 1815533915, -1410749514);
         String enterRequest = finishLegacyString(encodedEnterRequest, -1410749514);
         if (enterRequest.equalsIgnoreCase(request))
         {
            String encodedUseAction = prepareLegacyString("Use", 1883020947, 771908061);
            String useAction = finishLegacyString(encodedUseAction, 771908061);
            if (useAction.equalsIgnoreCase(actions[index]))
            {
               return index;
            }
         }

         String encodedUseRequest = prepareLegacyString("Use", 722396759, -1606533280);
         String useRequest = finishLegacyString(encodedUseRequest, -1606533280);
         if (useRequest.equalsIgnoreCase(request))
         {
            String encodedEnterAction = prepareLegacyString("Enter", 181817265, 975893751);
            String enterAction = finishLegacyString(encodedEnterAction, 975893751);
            if (enterAction.equalsIgnoreCase(actions[index]))
            {
               return index;
            }
         }
      }

      if (!target.equalsIgnoreCase(objectName))
      {
         return -1;
      }

      for (int index = 0; index < actions.length; index++)
      {
         String encodedOpen = prepareLegacyString("Open", 2053821200, -1136229862);
         String open = finishLegacyString(encodedOpen, -1136229862);
         if (open.equalsIgnoreCase(actions[index]))
         {
            return index;
         }

         String encodedPass = prepareLegacyString("Pass", 996067074, 913530785);
         String pass = finishLegacyString(encodedPass, 913530785);
         if (pass.equalsIgnoreCase(actions[index]))
         {
            return index;
         }
      }

      return -1;
   }

   private static boolean matchesActionIgnoringHyphens(String requested, String available) {
      return available != null && requested.replace('-', ' ').equalsIgnoreCase(available.replace('-', ' '));
   }

   private ObjectComposition getTransformedObjectDefinition(int objectId) {
      ObjectComposition definition = this.client.getObjectDefinition(objectId);
      return definition != null && definition.getImpostorIds() != null ? definition.getImpostor() : definition;
   }

   private void interactDoorObject(WalkerEngine.DoorInteraction door, boolean directClick) {
      NewMenuEntry entry = new NewMenuEntry().param0(door.sceneX).param1(door.sceneY).opcode(door.menuAction.getId())
         .identifier(door.object.getId()).itemId(-1).option(door.option).target(door.target)
         .setWorldViewId(door.object.getWorldView().getId()).gameObject(door.object);
      String message = finishLegacyString(prepareLegacyString("door-click location={} objectId={} action={}", 1627863765, -1062713335), -1062713335);
      this.debug(message, door.object.getWorldLocation(), door.object.getId(), door.option);
      this.dispatchMenuInteraction(entry, Rs2UiHelper.getObjectClickbox(door.object), directClick);
   }

   private boolean openDoorDuringTransport(WorldView worldView, WorldPoint playerLocation) {
      if (this.currentLocalRoute == null) return false;
      int[][] flags = this.getCollisionFlags(worldView, this.currentLocalRoute.plane);
      if (flags == null) return false;
      WalkerEngine.DoorCollisionOverrides overrides = this.buildDoorCollisionOverrides(worldView, this.currentLocalRoute.plane, flags.length, flags[0].length);
      WalkerEngine.RouteDoor door = overrides.findNextDoorOnRoute(this.currentLocalRoute.packedTiles, this.currentLocalRoute.gridWidth, this.currentLocalRoute.routeIndex);
      if (door == null || door.routeIndex - this.currentLocalRoute.routeIndex > 4) return false;
      String message = finishLegacyString(prepareLegacyString("transition-yield-door transition={} door={} player={}", 795268241, 643112484), 643112484);
      this.debug(message, describeTransport(this.activeTransport), door.door.object.getWorldLocation(), playerLocation);
      this.transportAttempt = null;
      this.transportAttempts = 0;
      this.lastWalkClickTick = -1;
      this.walkTarget = null;
      this.lastDecision = finishLegacyString(prepareLegacyString("transition-yield-door", 795268241, -1547774897), -1547774897);
      this.clickDoorAndTrackPending(door, this.currentLocalRoute.packedTiles, this.currentLocalRoute.gridWidth, true);
      return true;
   }

   private void clickDoorAndTrackPending(WalkerEngine.RouteDoor routeDoor, int[] packedTiles, int gridWidth, boolean directClick) {
      this.interactDoorObject(routeDoor.door, directClick);
      WorldPoint location = routeDoor.door.object.getWorldLocation();
      this.doorAttemptsByLocation.merge(location, 1, Integer::sum);
      this.pendingDoor = new WalkerEngine.PendingDoorInteraction(location, routeDoor.edgeIndex,
         StrongholdSecurity.isStrongholdDoor(routeDoor.door.object.getId()),
         LocalCollisionGrid.unpackX(packedTiles[routeDoor.routeIndex], gridWidth),
         LocalCollisionGrid.unpackY(packedTiles[routeDoor.routeIndex], gridWidth),
         LocalCollisionGrid.unpackX(packedTiles[routeDoor.routeIndex + 1], gridWidth),
         LocalCollisionGrid.unpackY(packedTiles[routeDoor.routeIndex + 1], gridWidth), this.client.getTickCount());
      this.status = WalkerEngine.Status.WALKING;
   }

   private boolean handleStrongholdDialogue() {
      if (!this.pendingDoor.strongholdDoor) {
         return false;
      } else if (Rs2Dialogue.hasContinue()) {
         Rs2Dialogue.clickContinue();
         this.pendingDoor.lastInteractionTick = this.client.getTickCount();
         this.lastDecision = "stronghold-dialogue-continue";
         return true;
      } else if (!Rs2Dialogue.hasSelectAnOption()) {
         return false;
      } else {
         List<Widget> options = Rs2Dialogue.getDialogueOptions();
         String answer = StrongholdSecurity.selectDialogueAnswer(options.stream().<String>map(Widget::getText).collect(Collectors.toList()));
         if (answer == null) {
            this.failNavigation("stronghold-answer-unavailable");
            return true;
         } else if (!Rs2Dialogue.clickOption(answer, true)) {
            this.failNavigation("stronghold-answer-click-failed");
            return true;
         } else {
            this.pendingDoor.lastInteractionTick = this.client.getTickCount();
            this.lastDecision = "stronghold-dialogue-answer";
            return true;
         }
      }
   }

   private void failNavigation(String reason) {
      this.initialWalkClickPending = false;
      this.initialTransportClickPending = false;
      String prefix = finishLegacyString(prepareLegacyString("blocked-", 422068069, -1336784880), -1336784880);
      this.lastDecision = prefix + reason;
      String message = finishLegacyString(prepareLegacyString("blocked reason={} player={} destination={} transition={} attempts={}", 422068069, 901350011), 901350011);
      WorldPoint playerLocation = this.client.getLocalPlayer() == null ? null : this.client.getLocalPlayer().getWorldLocation();
      this.debug(message, reason, playerLocation, this.getDestination(), describeTransport(this.activeTransport), this.transportAttempts);
      boolean reportFailure = this.status != WalkerEngine.Status.BLOCKED;
      String explanation = this.describeFailure(reason);
      WalkerEngine.PlanningFailure failure = this.planningFailure;
      this.resetNavigation();
      this.planningFailure = failure;
      this.status = WalkerEngine.Status.BLOCKED;
      if (reportFailure) {
         String errorPrefix = finishLegacyString(prepareLegacyString("Error: Efficient Walker: ", 527111972, -1148855098), -1148855098);
         String error = errorPrefix + explanation;
         Microbot.status = error;
         String sender = finishLegacyString(prepareLegacyString("", 527111972, 563156817), 563156817);
         this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, sender, ColorUtil.wrapWithColorTag(error, Color.RED), null);
      }
   }

   private String describeFailure(String reason) {
      if ("action-rejected".equals(reason)) {
         return this.failureMessage;
      }

      if ("plan-unavailable".equals(reason)) {
         WorldPoint playerLocation = this.client.getLocalPlayer() == null ? null : this.client.getLocalPlayer().getWorldLocation();
         WorldPoint destination = this.getDestination();
         if (playerLocation != null && destination != null) {
            return PlannerFailureMessages.describePlanningFailure(this.planningFailure, playerLocation, destination);
         }
      }

      return TravelFailureMessages.describeTravelFailure(reason, this.getDestination(), this.activeTransport == null ? null : this.activeTransport.target);
   }

   private void markInitialClicksPending() {
      this.initialWalkClickPending = true;
      this.initialTransportClickPending = true;
   }

   private boolean consumeInitialWalkClick() {
      boolean pending = this.initialWalkClickPending;
      this.initialWalkClickPending = false;
      return pending;
   }

   private boolean consumeInitialTransportClick() {
      boolean pending = this.initialTransportClickPending;
      this.initialTransportClickPending = false;
      return pending;
   }

   private void debug(String message, Object... arguments) {
      if (this.config.enableDebugging()) {
         String prefix = finishLegacyString(prepareLegacyString("[EfficientWalker] ", 1160293384, 1780923237), 1780923237);
         log.info(prefix + message, arguments);
      }
   }

   private static String describeTransport(TransportCatalog.TransportEdge transport) {
      if (transport == null) return finishLegacyString(prepareLegacyString("none", 9751822, -487857134), -487857134);
      String action = transport.action;
      String target = transport.target;
      String origin = String.valueOf(transport.origin);
      String destination = String.valueOf(transport.destination);
      int objectId = transport.objectId;
      return new StringBuilder().append(action)
         .append(finishLegacyString(prepareLegacyString(" ", 1329427489, 1728065145), 1728065145)).append(target)
         .append(finishLegacyString(prepareLegacyString(" ", 1329427489, -381914940), -381914940)).append(origin)
         .append(finishLegacyString(prepareLegacyString(" -> ", 1329427489, 1932049235), 1932049235)).append(destination)
         .append(finishLegacyString(prepareLegacyString(" id=", 1329427489, -180040258), -180040258)).append(objectId).toString();
   }

   private static long elapsedMillis(long startedNanos) {
      return (System.nanoTime() - startedNanos) / 1000000L;
   }

   private static MenuAction objectMenuActionForIndex(int actionIndex) {
      // The original integer hash also accepted the listed out-of-range aliases.
      // These are its exhaustively inverted preimages; ordinary indexes are 0..4.
      switch (actionIndex) {
         case 0:
         case -1795512416:
         case -1486283690:
         case -1138540525:
         case -971904934:
         case -380141386:
         case 587780100:
         case 1246668896:
         case 1840728956:
         case 1979013055:
            return MenuAction.GAME_OBJECT_FIRST_OPTION;
         case 1:
         case -2073998253:
         case -1138540522:
         case -971904933:
         case -550777342:
         case -380141383:
         case -241856650:
         case -103572557:
         case 278666002:
         case 726064198:
         case 1246668899:
            return MenuAction.GAME_OBJECT_SECOND_OPTION;
         case 2:
         case -2073998161:
         case -1933616282:
         case -1795512414:
         case -1657228679:
         case -1347982486:
         case -1209698395:
         case -1138540436:
         case -827328753:
         case -689061042:
         case -550777343:
         case -241856654:
         case -103572599:
         case 140381273:
         case 278666004:
         case 726064221:
         case 864299800:
         case 1002583895:
         case 1110662149:
         case 1384953661:
         case 1523221368:
         case 1702460587:
            return MenuAction.GAME_OBJECT_THIRD_OPTION;
         case 3:
         case -1795512415:
         case -1486283695:
         case -1138540528:
         case -971904939:
         case -380141385:
         case 587780103:
         case 1246668897:
         case 1840728957:
         case 1979013054:
            return MenuAction.GAME_OBJECT_FOURTH_OPTION;
         case 4:
         case -2073998250:
         case -1933616260:
         case -1138540521:
         case -971904930:
         case -380141382:
         case -241856655:
         case -103572560:
         case 278666093:
         case 587780096:
         case 726064195:
            return MenuAction.GAME_OBJECT_FIFTH_OPTION;
         default:
            StringBuilder message = new StringBuilder();
            String prefix = prepareLegacyString("Unsupported object action index: ", 1583083556, -1329856182);
            message.append(finishLegacyString(prefix, -1329856182));
            message.append(actionIndex);
            throw new IllegalArgumentException(message.toString());
      }
   }

   private static MenuAction npcMenuActionForIndex(int actionIndex) {
      // The original integer hash also accepted the listed out-of-range aliases.
      // These are its exhaustively inverted preimages; ordinary indexes are 0..4.
      switch (actionIndex) {
         case 0:
         case -2078192554:
         case -1931251361:
         case -1213909540:
         case -1134077739:
         case -892087504:
         case -762447687:
         case -451314342:
         case -174745675:
         case 146945289:
         case 734774276:
         case 944118919:
         case 1324391269:
         case 1454538978:
            return MenuAction.NPC_FIRST_OPTION;
         case 1:
         case -2078192553:
         case -1931251362:
         case -1213909539:
         case -892087497:
         case -762447686:
         case -451314341:
         case -174745674:
         case 146945288:
         case 734774331:
         case 944118918:
         case 1324391270:
         case 1454538977:
            return MenuAction.NPC_SECOND_OPTION;
         case 2:
         case -2078192560:
         case -1931251367:
         case -1343553451:
         case -1213909550:
         case -892087502:
         case -762447681:
         case -451314396:
         case 146945291:
         case 944118917:
         case 1324391271:
         case 1454538988:
            return MenuAction.NPC_THIRD_OPTION;
         case 3:
         case -2078192559:
         case -1931251368:
         case -1213909549:
         case -892087503:
         case -762447688:
         case 146945290:
         case 1324391264:
         case 1454538979:
            return MenuAction.NPC_FOURTH_OPTION;
         case 4:
         case -2078192565:
         case -1801623890:
         case -1490478257:
         case -1134077752:
         case -1039028692:
         case -892087509:
         case -762447737:
         case -451314400:
         case -304389585:
         case -174745686:
         case -94913887:
         case 293362265:
         case 423514020:
         case 734774272:
         case 864414083:
         case 944118969:
         case 1177974418:
         case 1324391321:
         case 1454538980:
         case 1600959996:
         case 1643655891:
         case 1773807198:
         case 2121548055:
            return MenuAction.NPC_FIFTH_OPTION;
         default:
            StringBuilder message = new StringBuilder();
            String prefix = prepareLegacyString("Unsupported NPC action index: ", 1592222065, -1329856169);
            message.append(finishLegacyString(prefix, -1329856169));
            message.append(actionIndex);
            throw new IllegalArgumentException(message.toString());
      }
   }

   /** Public decoder entry point. */
   public static String qhyyohwqbz(byte[] bytes, int seed) {
      return decodeLegacyOuter(bytes, seed);
   }

   /** Mutates the caller's array in the same two XOR steps as the original. */
   private static String decodeLegacyOuter(byte[] bytes, int seed) {
      byte[] decimalSeed = Integer.toString(seed).getBytes();
      for (int index = 0; index < bytes.length; index++) {
         bytes[index] = (byte)(bytes[index] ^ decimalSeed[index % decimalSeed.length]);
         bytes[index] = (byte)(bytes[index] ^ LEGACY_OUTER_KEY[index % LEGACY_OUTER_KEY.length]);
      }
      return new String(bytes, StandardCharsets.UTF_16);
   }

   static {
      log = LoggerFactory.getLogger(WalkerEngine.class);
      TELEPORT_PREP_ITEM_IDS = new int[]{8007, 8011, 8009, 28790, 563, 556, 555, 554, 557};
      TELEPORT_PREP_TARGET_QUANTITIES = new int[]{10, 10, 10, 10, 100, 250, 250, 100, 250};
      CANCELLED_REQUEST = new WalkerEngine.RouteRequest(null, false, false);
   }

   private static final class DoorCollisionOverrides {
      private final int width;
      private final int height;
      private final BitSet overrides;
      private final Map<Integer, WalkerEngine.DoorInteraction> doorsByEdge;
      private static byte[] LEGACY_OUTER_KEY = new byte[]{
         76, 24, 124, 93, 91, 91, 116, 103, 1, 98, 99, 67, 28, 30, 54, 20, 63, 75, 4, 57, 83, 113, 84, 26, 53, 126,
         74, 107, 56, 56, 92, 10, 125, 18, 126, 111, 8, 36
      };

      private DoorCollisionOverrides(int width, int height) {
         this.doorsByEdge = new HashMap<>();
         this.width = width;
         this.height = height;
         this.overrides = new BitSet(width * height * 2);
      }

      private void registerDoorEdge(int edgeIndex, WalkerEngine.DoorInteraction door) {
         this.overrides.set(edgeIndex);
         this.doorsByEdge.putIfAbsent(edgeIndex, door);
      }

      private void registerTileOverride(int x, int y) {
         this.overrides.set(LocalCollisionGrid.tileOverrideIndex(this.width, this.height, x, y));
      }

      private WalkerEngine.RouteDoor findNextDoorOnRoute(int[] path, int gridWidth, int startIndex) {
         return this.findNextDoorOnRoute(path, gridWidth, startIndex, new BitSet());
      }

      private WalkerEngine.RouteDoor findNextDoorOnRoute(int[] path, int gridWidth, int startIndex, BitSet excludedEdges) {
         for (int routeIndex = startIndex; routeIndex < path.length - 1; routeIndex++) {
            int fromX = LocalCollisionGrid.unpackX(path[routeIndex], gridWidth);
            int fromY = LocalCollisionGrid.unpackY(path[routeIndex], gridWidth);
            int toX = LocalCollisionGrid.unpackX(path[routeIndex + 1], gridWidth);
            int toY = LocalCollisionGrid.unpackY(path[routeIndex + 1], gridWidth);
            int stepX = toX - fromX;
            int stepY = toY - fromY;
            if (stepX != 0 && stepY != 0) {
               WalkerEngine.RouteDoor door = this.findDoorOnEdge(routeIndex, fromX, fromY, fromX + stepX, fromY, excludedEdges);
               if (door == null) {
                  door = this.findDoorOnEdge(routeIndex, fromX, fromY, fromX, fromY + stepY, excludedEdges);
               }
               if (door == null) {
                  door = this.findDoorOnEdge(routeIndex, fromX + stepX, fromY, toX, toY, excludedEdges);
               }
               if (door == null) {
                  door = this.findDoorOnEdge(routeIndex, fromX, fromY + stepY, toX, toY, excludedEdges);
               }
               if (door != null) {
                  return door;
               }
            } else {
               WalkerEngine.RouteDoor door = this.findDoorOnEdge(routeIndex, fromX, fromY, toX, toY, excludedEdges);
               if (door != null) {
                  return door;
               }
            }
         }
         return null;
      }

      private WalkerEngine.RouteDoor findDoorOnEdge(int routeIndex, int fromX, int fromY, int toX, int toY, BitSet excludedEdges) {
         int edgeIndex = LocalCollisionGrid.cardinalEdgeIndex(this.width, this.height, fromX, fromY, toX, toY);
         WalkerEngine.DoorInteraction door = this.doorsByEdge.get(edgeIndex);
         if (door != null && !excludedEdges.get(edgeIndex)) {
            return new WalkerEngine.RouteDoor(routeIndex, edgeIndex, door);
         }
         return null;
      }

   }

   private static final class DoorInteraction {
      private final TileObject object;
      private final String option;
      private final MenuAction menuAction;
      private final String target;
      private final int sceneX;
      private final int sceneY;

      private DoorInteraction(TileObject object, String option, MenuAction menuAction, String target, int sceneX, int sceneY) {
         this.object = object;
         this.option = option;
         this.menuAction = menuAction;
         this.target = target;
         this.sceneX = sceneX;
         this.sceneY = sceneY;
      }
   }

   private static final class EntityAction {
      private final String option;
      private final MenuAction menuAction;
      private final String target;

      private EntityAction(String option, MenuAction menuAction, String target) {
         this.option = option;
         this.menuAction = menuAction;
         this.target = target;
      }
   }

   private static final class LocalRoute {
      private final WorldView worldView;
      private final int plane;
      private final int[] packedTiles;
      private final int gridWidth;
      private final List<WorldPoint> points;
      private int routeIndex;

      private LocalRoute(WorldView worldView, int plane, int[] packedTiles, int gridWidth, List<WorldPoint> points) {
         this.worldView = worldView;
         this.plane = plane;
         this.packedTiles = packedTiles;
         this.gridWidth = gridWidth;
         this.points = points;
      }
   }

   private static final class LocalTransportRoute {
      private final List<WalkerEngine.LocalRoute> segments;
      private final List<TransportCatalog.TransportEdge> transports;

      private LocalTransportRoute(List<WalkerEngine.LocalRoute> segments, List<TransportCatalog.TransportEdge> transports) {
         this.segments = Collections.unmodifiableList(segments);
         this.transports = Collections.unmodifiableList(new ArrayList<>(transports));
      }
   }

   private static final class LocalTransportSearchNode {
      private final WorldPoint location;
      private final List<WalkerEngine.LocalRoute> segments;
      private final List<TransportCatalog.TransportEdge> transports;
      private final int cost;

      private LocalTransportSearchNode(WorldPoint point, List<WalkerEngine.LocalRoute> segments, List<TransportCatalog.TransportEdge> transports, int cost) {
         this.location = point;
         this.segments = segments;
         this.transports = transports;
         this.cost = cost;
      }
   }

   private static final class PendingDoorInteraction {
      private final WorldPoint location;
      private final int edgeIndex;
      private final boolean strongholdDoor;
      private final int fromSceneX;
      private final int fromSceneY;
      private final int toSceneX;
      private final int toSceneY;
      private int lastInteractionTick;

      private PendingDoorInteraction(WorldPoint location, int edgeIndex, boolean strongholdDoor, int fromSceneX, int fromSceneY, int toSceneX, int toSceneY, int lastInteractionTick) {
         this.location = location;
         this.edgeIndex = edgeIndex;
         this.strongholdDoor = strongholdDoor;
         this.fromSceneX = fromSceneX;
         this.fromSceneY = fromSceneY;
         this.toSceneX = toSceneX;
         this.toSceneY = toSceneY;
         this.lastInteractionTick = lastInteractionTick;
      }
   }

   /** Pure inverse codec; using a nested owner preserves outer-class initialization order. */
   private static final class LegacyEnumPayload {
      private static String encode(String text, int seed) {
         byte[] bytes = new byte[text.length() * 2];
         for (int index = 0; index < text.length(); index++) {
            seed = seed * 1664525 + 1013904223;
            int value = text.charAt(index) ^ seed >>> 16;
            bytes[index * 2] = (byte)(value >>> 8);
            bytes[index * 2 + 1] = (byte)value;
         }
         return Base64.getEncoder().encodeToString(bytes);
      }
   }

   public static enum PlanningFailure {
      NONE,
      TARGET_NOT_STANDABLE,
      ROUTE_UNSUPPORTED,
      SEARCH_LIMIT;

      // Only the public decoder uses this key.
      private static byte[] LEGACY_OUTER_KEY = new byte[]{
         20, 35, 12, 59, 16, 1, 17, 86, 14, 126, 26, 76, 94, 124, 125, 41, 10, 58, 63, 7, 121, 31, 68, 80, 70, 125,
         11, 59, 46, 80, 11, 61, 86, 38, 41, 125, 90, 32, 39, 83, 101, 91, 95, 116, 34, 125, 13, 55, 64, 64, 19, 9,
         25, 102
      };

      /** Public decoder entry point. */
      public static String unnktxgdjt(byte[] bytes, int seed) {
         byte[] decimalSeed = Integer.toString(seed).getBytes();
         for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte)(bytes[index] ^ decimalSeed[index % decimalSeed.length]);
            bytes[index] = (byte)(bytes[index] ^ LEGACY_OUTER_KEY[index % LEGACY_OUTER_KEY.length]);
         }
         return new String(bytes, StandardCharsets.UTF_16);
      }

      private static void verifyLegacyName(String name, int innerSeed) {
         byte[] bytes = LegacyEnumPayload.encode(name, innerSeed).getBytes(StandardCharsets.UTF_16);
         byte[] originalDigits = "237946721".getBytes(StandardCharsets.US_ASCII);
         for (int index = 0; index < bytes.length; index++) {
            bytes[index] ^= originalDigits[index % originalDigits.length];
            bytes[index] ^= LEGACY_OUTER_KEY[index % LEGACY_OUTER_KEY.length];
         }
         // This preserves the original initialization failure on unusual default charsets.
         String payload = unnktxgdjt(bytes, 237946721);
         WalkerEngine.decodePayloadString(payload, innerSeed);
      }

      static {
         verifyLegacyName("NONE", 964706303);
         verifyLegacyName("TARGET_NOT_STANDABLE", -1133775468);
         verifyLegacyName("ROUTE_UNSUPPORTED", 844606249);
         verifyLegacyName("SEARCH_LIMIT", -1471979330);
      }
   }

   private static final class PlanningResult {
      private final CollisionRoutePlanner.RouteSearchResult globalRoute;
      private final WalkerEngine.TeleportRoute teleportRoute;
      private BankSupplyController bankSupplyPlan;

      private PlanningResult(CollisionRoutePlanner.RouteSearchResult globalRoute, WalkerEngine.TeleportRoute teleportRoute) {
         this.globalRoute = globalRoute;
         this.teleportRoute = teleportRoute;
      }
   }

   private static final class PlanningSnapshot {
      private final WalkerEngine.RouteRequest request;
      private final WorldPoint origin;
      private final int worldViewId;
      private final int baseX;
      private final int baseY;
      private final boolean instanced;
      private final WalkerEngine.LocalRoute localRoute;
      private final WalkerEngine.PlanningFailure initialFailure;
      private final Set<Long> edgeOverrides;
      private final Set<TransportCatalog.TransportEdge> allowedTransports;
      private final List<WalkerEngine.TeleportCandidate> teleportCandidates;
      private final Set<TransportCatalog.TransportEdge> excludedTransports;
      private final List<WorldPoint> destinations;
      private final boolean allowTeleports;
      private final long startedNanos;
      private List<BankSupplyController.BankSupplyRequirement> bankSupplyCandidates;
      private List<WorldPoint> bankLocations;
      private boolean hasRunEnergy;

      private PlanningSnapshot(
         WalkerEngine.RouteRequest request,
         WorldPoint origin,
         int worldViewId,
         int baseX,
         int baseY,
         boolean instanced,
         WalkerEngine.LocalRoute localRoute,
         WalkerEngine.PlanningFailure initialFailure,
         Set<Long> edgeOverrides,
         Set<TransportCatalog.TransportEdge> allowedTransports,
         List<WalkerEngine.TeleportCandidate> teleportCandidates,
         Set<TransportCatalog.TransportEdge> excludedTransports,
         List<WorldPoint> destinations,
         boolean allowTeleports,
         long startedNanos
      ) {
         this.bankSupplyCandidates = Collections.emptyList();
         this.bankLocations = Collections.emptyList();
         this.request = request;
         this.origin = origin;
         this.worldViewId = worldViewId;
         this.baseX = baseX;
         this.baseY = baseY;
         this.instanced = instanced;
         this.localRoute = localRoute;
         this.initialFailure = initialFailure;
         this.edgeOverrides = Collections.unmodifiableSet(new HashSet<>(edgeOverrides));
         this.allowedTransports = Collections.unmodifiableSet(new HashSet<>(allowedTransports));
         this.teleportCandidates = Collections.unmodifiableList(new ArrayList<>(teleportCandidates));
         this.excludedTransports = Collections.unmodifiableSet(new HashSet<>(excludedTransports));
         this.destinations = Collections.unmodifiableList(new ArrayList<>(destinations));
         this.allowTeleports = allowTeleports;
         this.startedNanos = startedNanos;
      }
   }

   private static final class RouteDoor {
      private final int routeIndex;
      private final int edgeIndex;
      private final WalkerEngine.DoorInteraction door;

      private RouteDoor(int routeIndex, int edgeIndex, WalkerEngine.DoorInteraction door) {
         this.routeIndex = routeIndex;
         this.edgeIndex = edgeIndex;
         this.door = door;
      }
   }

   private static final class RouteRequest {
      private volatile WorldPoint destination;
      private final boolean execute;
      private final boolean nearestBank;
      private boolean bankDetourAttempted;
      private final List<WorldPoint> suppliedRoute;

      private RouteRequest(WorldPoint destination, boolean execute, boolean nearestBank) {
         this(destination, execute, nearestBank, null);
      }

      private RouteRequest(WorldPoint destination, boolean execute, boolean nearestBank, List<WorldPoint> suppliedRoute) {
         this.destination = destination;
         this.execute = execute;
         this.nearestBank = nearestBank;
         this.suppliedRoute = suppliedRoute;
      }
   }

   public static enum Status {
      IDLE,
      PLANNING,
      PREVIEW,
      WALKING,
      ARRIVED,
      BLOCKED;

      // Rebuilt from the visible names; arbitrary legacy descriptor offsets still work.
      private static String legacyDescriptorTable = createLegacyDescriptorTable();

      private static String createLegacyDescriptorTable() {
         StringBuilder table = new StringBuilder();
         appendLegacyName(table, "IDLE", -2062870208);
         appendLegacyName(table, "PLANNING", 14734123);
         appendLegacyName(table, "PREVIEW", -1898148458);
         appendLegacyName(table, "WALKING", 348669441);
         appendLegacyName(table, "ARRIVED", -1834733332);
         appendLegacyName(table, "BLOCKED", 412216663);
         return table.toString();
      }

      private static void appendLegacyName(StringBuilder table, String name, int seed) {
         byte[] bytes = LegacyEnumPayload.encode(name, seed).getBytes(StandardCharsets.UTF_16BE);
         byte[] decimalSeed = "967412394".getBytes(StandardCharsets.US_ASCII);
         for (int index = 0; index < bytes.length; index++) {
            bytes[index] ^= decimalSeed[index % decimalSeed.length];
         }
         table.append(new String(bytes, StandardCharsets.UTF_16BE));
      }

      /** Public descriptor decoder. */
      public static String rmolmmwwan(byte[] descriptor, int seed) {
         byte[] decimalSeed = Integer.toString(seed).getBytes();
         int length = (descriptor[0] & 255) << 24 | (descriptor[1] & 255) << 16
            | (descriptor[2] & 255) << 8 | descriptor[3] & 255;
         int offset = (descriptor[4] & 255) << 24 | (descriptor[5] & 255) << 16
            | (descriptor[6] & 255) << 8 | descriptor[7] & 255;
         byte[] bytes = legacyDescriptorTable.substring(offset, offset + length).getBytes(StandardCharsets.UTF_16BE);
         for (int index = 0; index < bytes.length; index++) {
            bytes[index] ^= decimalSeed[index % decimalSeed.length];
         }
         return new String(bytes, StandardCharsets.UTF_16BE);
      }

      private static void verifyLegacyName(int length, int offset, int innerSeed) {
         byte[] descriptor = new byte[]{0, 0, 0, (byte)length, 0, 0, 0, (byte)offset};
         String payload = rmolmmwwan(descriptor, 967412394);
         WalkerEngine.decodePayloadString(payload, innerSeed);
      }

      static {
         verifyLegacyName(12, 0, -2062870208);
         verifyLegacyName(24, 12, 14734123);
         verifyLegacyName(20, 36, -1898148458);
         verifyLegacyName(20, 56, 348669441);
         verifyLegacyName(20, 76, -1834733332);
         verifyLegacyName(20, 96, 412216663);
      }
   }

   private static final class TeleportAttempt {
      private final int startedTick;
      private int inventorySlot = -1;
      private int itemId = -1;
      private int whistleCharges = -1;
      private int inventoryRevision;
      private int itemActionTick = -1;
      private int destinationSelectionTick = -1;
      private int settledLandingTick = -1;

      private TeleportAttempt(int startedTick) {
         this.startedTick = startedTick;
      }
   }

   private static final class TeleportCandidate {
      private final TeleportProviders.TeleportProvider provider;
      private final WorldPoint destination;

      private TeleportCandidate(TeleportProviders.TeleportProvider provider, WorldPoint destination) {
         this.provider = provider;
         this.destination = destination;
      }
   }

   private static final class TeleportRoute {
      private final TeleportProviders.TeleportProvider provider;
      private final WorldPoint destination;
      private final CollisionRoutePlanner.RouteSearchResult route;
      private final int cost;

      private TeleportRoute(TeleportProviders.TeleportProvider provider, WorldPoint destination, CollisionRoutePlanner.RouteSearchResult route, int cost) {
         this.provider = provider;
         this.destination = destination;
         this.route = route;
         this.cost = cost;
      }
   }

   private static final class TransportAttempt {
      private final int startedTick;
      private int lastProgressTick;
      private WorldPoint lastPosition;
      private boolean lastActive;
      private boolean observedProgress;
      private int firstSelectionTick = -1;
      private int selectionAttempts;
      private int settledLandingTick = -1;

      private TransportAttempt(int startedTick, WorldPoint position, boolean active) {
         this.startedTick = startedTick;
         this.lastProgressTick = startedTick;
         this.lastPosition = position;
         this.lastActive = active;
      }

      private boolean observeProgress(int currentTick, WorldPoint position, boolean active) {
         if (WalkerEngine.hasTransitionProgress(this.lastPosition, position, this.lastActive, active)) {
            this.lastPosition = position;
            this.lastActive = active;
            this.lastProgressTick = currentTick;
            this.observedProgress = true;
            return true;
         } else {
            return false;
         }
      }

      private boolean confirmSettledLanding(int currentTick, boolean atDestination, boolean madeProgress) {
         if (!atDestination || madeProgress) {
            this.settledLandingTick = -1;
         }

         if (atDestination && this.firstSelectionTick >= 0) {
            if (this.settledLandingTick < 0) {
               this.settledLandingTick = currentTick;
            }

            return currentTick > this.settledLandingTick;
         } else {
            return false;
         }
      }
   }

   static final class TransportMarker {
      final WorldPoint point;
      final String action;
      final String target;

      private TransportMarker(WorldPoint point, String action, String target) {
         this.point = point;
         this.action = action;
         this.target = target;
      }
   }
}
