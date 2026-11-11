package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.droslayer.walker.support.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Standalone walking API backed by ordinary Java source in the engine package.
 * The facade owns request cancellation, bank/equipment handoffs and transport extensions.
 * A private class loader gives each walker its own engine statics and configuration proxy.
 */
public final class EfficientWalker
{
    private static final Logger LOG = LoggerFactory.getLogger(EfficientWalker.class);
    private static final String ENGINE_PACKAGE =
        "net.runelite.client.plugins.microbot.droslayer.walker.engine";
    private static final String ENGINE_WALKER = ENGINE_PACKAGE + ".WalkerEngine";
    private static final String ENGINE_CONFIG = ENGINE_PACKAGE + ".WalkerEngineConfig";
    // Fossil Island bank chests supplied from the Location overlay.  These are kept local
    // to Fossil Island so the generic Efficient Walker bank catalogue is unchanged elsewhere.
    private static final WorldPoint FOSSIL_NORTH_ISLAND_BANK =
        net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint();
    // Route to the bank approach, rather than the occupied chest tile (3742,3805).
    private static final WorldPoint FOSSIL_CAMP_BANK =
        net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.FOSSIL_ISLAND.getWorldPoint();
    private static final int SHORT_LOCAL_ROUTE_STEPS = 10;
    private static final int FOSSIL_BANK_AREA_RADIUS = 180;

    private static final int IMMEDIATE_BANK_RADIUS = 12;

    private static WorldPoint nearestFossilBank(WorldPoint player)
    {
        if (player == null)
        {
            return null;
        }
        int sailed = player.distanceTo2D(FOSSIL_NORTH_ISLAND_BANK);
        int camp = player.distanceTo2D(FOSSIL_CAMP_BANK);
        if (Math.min(sailed, camp) > FOSSIL_BANK_AREA_RADIUS)
        {
            return null;
        }
        return sailed <= camp ? FOSSIL_NORTH_ISLAND_BANK : FOSSIL_CAMP_BANK;
    }

    private final WildernessTravelResume wildernessTravelResume = new WildernessTravelResume();
    private final WalkerRequestCancellation requestCancellation = new WalkerRequestCancellation();
    private final WalkerRequestOwnership requestOwnership = new WalkerRequestOwnership();
    private volatile WalkerRouteSubmission routeSubmission;
    private volatile String routeSubmissionFailure;
    private final WalkerRouteSettings routeSettings = new WalkerRouteSettings();
    private boolean doorActionBlocked;
    private volatile String caveLightFailure;
    private final Client client;
    private final EfficientWalkerConfig config;
    private volatile boolean bankSuppliesAllowed = true;
    private final ClassLoader engineLoader;
    private final Class<?> engineClass;
    private final Object engine;
    private Object trackedTeleport;
    private WorldPoint trackedTeleportDeparture;
    private final CatalogTeleportSupport catalogTeleportSupport;
    private final SpiritTreeSupport spiritTreeSupport;
    private final NetworkTransportSupport networkTransportSupport;
    private final BankTeleportBootstrap bankTeleportBootstrap;
    private final WalkerSpellbookSupport spellbookSupport;
    private final FinalTileWatchdog finalTileWatchdog = new FinalTileWatchdog();
    private final NearbyDestinationRecovery nearbyDestinationRecovery = new NearbyDestinationRecovery();
    private final FairyRingEquipmentRestore fairyRingEquipmentRestore = new FairyRingEquipmentRestore();
    private final ApeAtollGreegree apeAtollGreegree = new ApeAtollGreegree();
    private Future<?> greegreeHoldAction;
    private volatile boolean apeAtollGreegreeVerified;
    private Future<?> apeAtollTabletAction;
    private WorldPoint apeAtollTabletTarget;
    private int apeAtollTabletStartedTick, apeAtollTabletWorld;
    private boolean apeAtollTabletLanded;
    private WorldPoint committedTirannwnTarget;
    private MotherlodeEntranceRockfalls motherlodeEntranceRockfalls = new MotherlodeEntranceRockfalls();
    private static final WorldPoint WYVERN_TUNNEL_ENTRANCE = new WorldPoint(3026, 9572, 0);

    EfficientWalker(Client client, EfficientWalkerConfig config)
    {
        this(client, config, null);
    }

    EfficientWalker(Client client, EfficientWalkerConfig config, WalkerRouteSubmission routeSubmission)
    {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.routeSubmission = routeSubmission == null ? newRouteSubmission() : routeSubmission;
        TravelCombatPolicy.configure(config);
        WalkerMouseControl.configure(config);
        try
        {
            this.engineLoader = new NavigationEngineClassLoader(EfficientWalker.class.getClassLoader());
            this.engineClass = Class.forName(ENGINE_WALKER, true, engineLoader);
            Class<?> engineConfigClass = Class.forName(ENGINE_CONFIG, true, engineLoader);
            Object shadowConfig = Proxy.newProxyInstance(
                engineLoader,
                new Class<?>[]{engineConfigClass},
                (proxy, method, args) -> invokeConfig(proxy, method, args));

            Constructor<?> constructor = engineClass.getDeclaredConstructor(Client.class, engineConfigClass);
            constructor.setAccessible(true);
            this.engine = constructor.newInstance(client, shadowConfig);
            this.catalogTeleportSupport = new CatalogTeleportSupport(
                client, config, engineLoader, engineClass, engine, ENGINE_PACKAGE);
            this.catalogTeleportSupport.install();
            this.spiritTreeSupport = new SpiritTreeSupport(
                client, config, engineLoader, engineClass, engine, ENGINE_PACKAGE);
            this.spiritTreeSupport.install();
            this.networkTransportSupport = new NetworkTransportSupport(
                client, config, engineLoader, engineClass, engine, ENGINE_PACKAGE, catalogTeleportSupport);
            this.networkTransportSupport.install();
            this.bankTeleportBootstrap = new BankTeleportBootstrap();
            this.spellbookSupport = new WalkerSpellbookSupport(client, config, new WalkerSpellbookSupport.Travel() {
                public void walk(WorldPoint point) {
                    if (!runNavigationRequest(point, () -> walkToWithCurrentTransportPolicy(point)))
                        throw new IllegalStateException("No route to the spellbook switch");
                }
                public void bank() {
                    if (!walkToNearestBankInternal()) throw new IllegalStateException("No reachable bank for spellbook preparation");
                }
                public void stop() { cancelNavigation(); }
                public Status status() {
                    return Microbot.getClientThread().runOnClientThreadOptional(EfficientWalker.this::getStatus).orElse(Status.IDLE);
                }
            });
        }
        catch (InvocationTargetException ex)
        {
            throw propagate(ex.getCause());
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException("Unable to initialize the source walking engine", ex);
        }
    }

    private WalkerRouteSubmission newRouteSubmission()
    {
        return new WalkerRouteSubmission(task -> Microbot.getClientThread().invokeLater(task));
    }

    private Object invokeConfig(Object proxy, Method method, Object[] args) throws Throwable
    {
        if (method.getDeclaringClass() == Object.class)
        {
            switch (method.getName())
            {
                case "toString":
                    return "EfficientWalkerConfigBridge[" + config + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == (args == null ? null : args[0]);
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }

        // Away from an open bank the coordinator compares the full detour first.
        // The planner must not independently turn a direct preview into a bank walk.
        if ("useItemsFromBank".equals(method.getName()))
        {
            return bankSuppliesAllowed() && Rs2Bank.isOpen();
        }

        try
        {
            Method original = EfficientWalkerConfig.class.getMethod(method.getName(), method.getParameterTypes());
            return original.invoke(config, args);
        }
        catch (InvocationTargetException ex)
        {
            throw ex.getCause();
        }
    }

    private void refreshTransportExtensions(WorldPoint target)
    {
        long preparationStarted = System.nanoTime();
        requestOwnership.check();
        long request = requestCancellation.capture();
        requestCancellation.check(request);
        try (TravelEquipment.Scope ignored = TravelEquipment.planningScope()) {
            refreshTransportExtensionsSnapshot(target);
        }
        requestCancellation.check(request);
        requestOwnership.check();
        if (config.enableDebugging())
            LOG.info("[EfficientWalker] transport preparation ms={}", elapsedMillis(preparationStarted));
    }

    private void refreshTransportExtensionsSnapshot(WorldPoint target)
    {
        // Refresh the eligibility snapshot for the full item-teleport catalogue first.
        // Dro supplies metadata only; the Efficient Walker still scores and
        // selects these providers through its own planner.
        MaxPohSupport.refresh();
        catalogTeleportSupport.refreshForPlanning();

        // Spirit Trees own their additive layer first. The charter/minigame bridge then
        // adopts that current graph and adds its own target-specific edges on top.
        syncWildernessTravelPolicy(target);
        spiritTreeSupport.planningFor(target);
        spiritTreeSupport.refreshForConfigIfNeeded();
        networkTransportSupport.setCaveBankSuppliesAllowed(bankSuppliesAllowed());
        networkTransportSupport.refreshForPlanning(target);
    }

    private void syncWildernessTravelPolicy(WorldPoint target)
    {
        try
        {
            Class<?> planner = Class.forName(ENGINE_PACKAGE + ".CollisionRoutePlanner", true, engineLoader);
            Field preference = planner.getField("allowWildernessTravel");
            preference.setAccessible(true);
            preference.setBoolean(null, config.allowWildernessTravel());
            Field origin = planner.getField("aioTravelOrigin"), destination = planner.getField("aioTravelTarget");
            origin.setAccessible(true); destination.setAccessible(true);
            WalkerRequestOwnership.Request activeJourney = requestOwnership.current();
            WorldPoint current = Rs2Player.getWorldLocation();
            // A planner replan is a continuation, not a new Wilderness departure.
            // Once safe ground is observed, the current safe tile revokes the escape allowance.
            WorldPoint safetyOrigin = activeJourney != null && !activeJourney.reachedSafety
                && WildernessTravelPolicy.isBlocked(current, false)
                ? activeJourney.safetyOrigin : current;
            origin.set(null, safetyOrigin != null ? safetyOrigin : current); destination.set(null, target);
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException("Cannot apply Wilderness travel preference", ex);
        }
    }

    static boolean shouldInspectNearbyBank(int routeCost, boolean minigame, boolean wilderness)
    {
        return routeCost >= 30 || minigame || wilderness;
    }

    /** Effective destination after correcting a legacy map pin; exposes arrival semantics to callers. */
    private volatile WorldPoint lastLoggedDestinationPin;

    public WorldPoint resolveDestination(WorldPoint target)
    {
        WorldPoint resolved = DestinationCorrections.resolve(target);
        if (target != null && !target.equals(resolved)
            && !target.equals(lastLoggedDestinationPin))
        {
            lastLoggedDestinationPin = target;
            LOG.info("[EfficientWalker] known destination requested={} walkableApproach={}", target, resolved);
        }
        return resolved;
    }

    private boolean bankSuppliesAllowed() {
        return bankSuppliesAllowed && config.useItemsFromBank();
    }

    private void setBankSuppliesAllowed(boolean allowed) {
        if (bankSuppliesAllowed != allowed) cancelForPolicyChange();
        bankSuppliesAllowed = allowed;
    }

    public boolean walkTo(WorldPoint target)
    {
        return runNavigationRequest(target, () -> walkToInternal(target));
    }

    private boolean walkToInternal(WorldPoint target)
    {
        spellbookSupport.cancel();
        setBankSuppliesAllowed(true);
        if (!networkTransportSupport.magicMushtreesAllowed()) cancelForPolicyChange();
        networkTransportSupport.setMagicMushtreesAllowed(true);
        return walkToWithCurrentTransportPolicy(target);
    }

    private boolean runNavigationRequest(WorldPoint target, Supplier<Boolean> action)
    {
        requestOwnership.check();
        if (requestOwnership.context() != null)
        {
            return action.get();
        }
        return executeNavigationRequest(
            claimNavigationRequest(target, WalkerRequestOwnership.LEGACY_OWNER, false), action);
    }

    private WalkerRequestOwnership.Request claimNavigationRequest(WorldPoint target, String owner, boolean replace)
    {
        return claimNavigationRequest(target, owner, replace, () -> true);
    }

    private WalkerRequestOwnership.Request claimNavigationRequest(WorldPoint target, String owner, boolean replace,
                                                                  java.util.function.BooleanSupplier stillWanted)
    {
        WorldPoint resolved = resolveDestination(target);
        Runnable motionTick = net.runelite.client.plugins.microbot.droslayer.support.MouseMotionScope.capture(() -> {
            try (var ignored = WalkerMouseControl.openProfile()) { tickWithMouseProfile(); }
        });
        boolean hasContext = requestOwnership.hasContext();
        long expectedEpoch = requestOwnership.captureEpoch();
        java.util.concurrent.atomic.AtomicBoolean accepting = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<WalkerRequestOwnership.Request> claimed =
            new java.util.concurrent.atomic.AtomicReference<>();
        WalkerRequestOwnership.Request result = readOnClientThread(() -> {
            synchronized (requestOwnership)
            {
                if (!accepting.get() || hasContext && requestOwnership.epoch() != expectedEpoch
                    || !stillWanted.getAsBoolean()) return null;
                WalkerRequestOwnership.Request previous = requestOwnership.current();
                WalkerRequestOwnership.Request request = requestOwnership.begin(resolved, owner, replace);
                claimed.set(request);
                if (previous != request)
                {
                    request.safetyOrigin = client.getLocalPlayer() == null
                        ? Rs2Player.getWorldLocation() : client.getLocalPlayer().getWorldLocation();
                    request.reachedSafety = !WildernessTravelPolicy.isBlocked(request.safetyOrigin, false);
                    if (previous != null)
                    {
                        try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(request))
                        {
                            clearNavigationOnClientThread();
                        }
                    }
                    request.tickAction = motionTick;
                    routeSubmission.cancel();
                    routeSubmissionFailure = null;
                    finalTileWatchdog.reset();
                    catalogTeleportSupport.resetObservedLanding();
                }
                if (previous != null && previous != request)
                {
                    LOG.info("[EfficientWalker] request replaced reason=ROUTE_REPLACEMENT owner={} request={} nextOwner={} nextRequest={}",
                        previous.owner, previous.id, request.owner, request.id);
                }
                return request;
            }
        }, null);
        if (result != null) return result;
        WalkerRequestOwnership.Request abandoned;
        synchronized (requestOwnership)
        {
            accepting.set(false);
            abandoned = claimed.get();
        }
        if (abandoned != null)
        {
            requestOwnership.finished(abandoned);
            try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(abandoned))
            {
                cancelNavigation(Thread.currentThread().isInterrupted() ? "INTERRUPTED" : "START_REJECTED", true);
            }
        }
        throw new CancellationException("Walker request could not be accepted on the client thread");
    }

    private boolean executeNavigationRequest(WalkerRequestOwnership.Request request, Supplier<Boolean> action)
    {
        try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(request))
        {
            requestOwnership.check();
            return action.get() && requestOwnership.isCurrent(request);
        }
        catch (CancellationException cancelled)
        {
            return false;
        }
        finally
        {
            requestOwnership.finished(request);
        }
    }

    private <T> T readOnClientThread(Supplier<T> action, T unavailable)
    {
        return client.isClientThread() ? action.get()
            : Microbot.getClientThread().runOnClientThreadOptional(action::get).orElse(unavailable);
    }

    /** Carries the original request across a short client-thread state handoff. */
    private boolean updateOnClientThread(Supplier<Boolean> action)
    {
        WalkerRequestOwnership.Request request = requestOwnership.capture();
        long epoch = requestOwnership.captureEpoch();
        return readOnClientThread(() -> {
            synchronized (requestOwnership)
            {
                if (requestOwnership.epoch() != epoch
                    || request != null && !requestOwnership.isCurrent(request)) return false;
                try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(request))
                {
                    return action.get();
                }
            }
        }, false);
    }

    private static String cancellationReason(String reason)
    {
        return reason != null && reason.matches("[A-Z_]{1,48}") ? reason : "CANCEL_REQUESTED";
    }

    private void logCancellation(WalkerRequestOwnership.Request request, String reason, Status status)
    {
        LOG.info("[EfficientWalker] cancel reason={} owner={} request={} target={} status={}",
            reason, request == null ? WalkerRequestOwnership.LEGACY_OWNER : request.owner,
            request == null ? 0 : request.id, request == null ? null : request.target, status);
    }

    private boolean walkToWithCurrentTransportPolicy(WorldPoint target)
    {
        requestOwnership.check();
        caveLightFailure = null;
        if (motherlodeEntranceRockfalls.failure() != null || !Objects.equals(target, getLogicalDestination()))
            motherlodeEntranceRockfalls.reset();
        target = resolveDestination(target);
        if (apeAtollGreegree.failure() != null || !Objects.equals(target, getLogicalDestination())) resetApeAtollGreegree();
        if (wildernessTravelResume.acceptRequest(target)) return true;
        if (nearbyDestinationRecovery.acceptRequest(target, NearbyRequestMode.WALK))
        {
            return true;
        }
        if (nearbyDestinationRecovery.exhausted(target))
        {
            return false;
        }
        boolean started = walkToDirect(target);
        return started || (nearbyDestinationRecovery.canRetry() && nearbyDestinationRecovery.tryNext());
    }

    private boolean walkToDirect(WorldPoint target)
    {
        requestOwnership.check();
        doorActionBlocked = false;
        if (!Objects.equals(target, getDestination())) WalkerMouseControl.invalidate();
        if (committedTirannwnTarget != null)
        {
            Status status = getStatus();
            if (committedTirannwnTarget.equals(target)
                && (status == Status.PLANNING
                    || status == Status.PREVIEW
                    || status == Status.WALKING))
            {
                return true;
            }
            committedTirannwnTarget = null;
        }
        // Quest Helper can re-issue the same destination every script tick.  While the bank
        // coordinator is already opening/reading/withdrawing for that target, treat those
        // duplicate requests as the same in-flight walk instead of restarting the preview.
        if (bankTeleportBootstrap.acceptDuplicateTargetRequest(target))
        {
            return true;
        }
        if (Objects.equals(target, apeAtollTabletTarget) && apeAtollTabletTarget != null) return true;

        // Scripts commonly re-issue the same walk target every loop. Reusing the active route
        // avoids rebuilding the transport graph and rescoring teleports while already travelling.
        Status activeStatus = getStatus();
        WorldPoint activeDestination = getDestination();
        if (target != null && target.equals(activeDestination)
            && (activeStatus == Status.PLANNING
                || activeStatus == Status.PREVIEW
                || activeStatus == Status.WALKING))
        {
            return true;
        }

        if (activeStatus == Status.ARRIVED || activeStatus == Status.BLOCKED || activeStatus == Status.IDLE)
            networkTransportSupport.clearCompleteRoute();
        if (finalTileWatchdog.acceptRequest(target))
        {
            return true;
        }
        if (bankTeleportBootstrap.beginHousePortalRecovery(target, false)) return true;
        if (missingApeAtollSupplies(target)) {
            if (!bankSuppliesAllowed()) {
                stopApeAtollTravel("Ape Atoll requires a monkey greegree before MM2; enable bank supplies or carry one.");
                return false;
            }
            return bankTeleportBootstrap.prepareApeAtollGreegree(target);
        }
        if (useApeAtollTabletFlow(target) && !ApeAtollGreegree.onIsland(Rs2Player.getWorldLocation()))
            return beginApeAtollTabletTravel(target);
        refreshTransportExtensions(target);

        if (startShortLocalRoute(target)) return true;

        // An already-open bank can supply a live snapshot without a travel detour.
        if (bankTeleportBootstrap.shouldCoordinateBankedTeleports()
            && !Rs2Player.isTeleBlocked()
            && bankTeleportBootstrap.shouldCheckImmediateBankFirst(target))
        {
            return bankTeleportBootstrap.startBankDetour(target);
        }


        // When banked teleports are enabled, always preview first.  That lets the facade
        // intercept a preserved-engine bank plan and perform the bank interaction with
        // Dro's current bank APIs instead of the old v1.11.2 visible-widget helper.
        if (bankTeleportBootstrap.shouldCoordinateBankedTeleports())
        {
            boolean previewStarted = (Boolean) invoke("previewRouteTo", new Class<?>[]{WorldPoint.class}, target);
            if (!previewStarted)
            {
                // A carried-items-only preview can be impossible for island/fairy-ring targets even
                // though the bank contains the fare or staff that makes the route valid. Probe the
                // existing bank coordinator once before declaring the destination unsupported.
                if (bankTeleportBootstrap.shouldBootstrapUnsupportedTransportRoute(target))
                {
                    return bankTeleportBootstrap.startUnsupportedTransportDetour(target);
                }
                if (bankTeleportBootstrap.shouldCompareUnsupportedRoute(target))
                {
                    bankTeleportBootstrap.awaitDirectPreview(target);
                    return true;
                }
                return false;
            }

            bankTeleportBootstrap.awaitDirectPreview(target);
            return true;
        }

        return (Boolean) invoke("walkTo", new Class<?>[]{WorldPoint.class}, target);
    }

    /** Uses the engine's own collision result; distance alone never bypasses bank planning. */
    private boolean startShortLocalRoute(WorldPoint target)
    {
        WalkerRequestOwnership.Request request = requestOwnership.capture();
        long epoch = requestOwnership.captureEpoch();
        return readOnClientThread(() -> {
            try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(request, epoch))
            {
                requestOwnership.check();
                Player player = client.getLocalPlayer();
                if (target == null || player == null || bankTeleportBootstrap.isBusy()) return false;
                WorldView world = player.getWorldView();
                WorldPoint origin = player.getWorldLocation();
                if (world == null || world.isInstance() || origin == null
                    || origin.getPlane() != target.getPlane()
                    || origin.distanceTo2D(target) > SHORT_LOCAL_ROUTE_STEPS) return false;
                Object route = invokeEngine("planLocalRoute",
                    new Class<?>[]{WorldView.class, WorldPoint.class, WorldPoint.class, int.class},
                    world, origin, target, origin.getPlane());
                if (route == null) return false;
                @SuppressWarnings("unchecked") List<WorldPoint> points =
                    (List<WorldPoint>) field(route.getClass(), "points").get(route);
                if (!isShortLocalRoute(points, origin, target)) return false;
                synchronized (requestOwnership)
                {
                    requestOwnership.check();
                    if (player.getWorldView() != world || !origin.equals(player.getWorldLocation())) return false;
                    return (Boolean) invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, target);
                }
            }
            catch (ReflectiveOperationException unavailable)
            {
                return false;
            }
        }, false);
    }

    static boolean isShortLocalRoute(List<WorldPoint> points, WorldPoint origin, WorldPoint target)
    {
        if (origin == null || target == null || points == null || points.isEmpty()
            || points.size() - 1 > SHORT_LOCAL_ROUTE_STEPS
            || !origin.equals(points.get(0)) || !target.equals(points.get(points.size() - 1))) return false;
        WorldPoint previous = origin;
        for (WorldPoint point : points)
        {
            if (point == null || point.getPlane() != origin.getPlane() || previous.distanceTo2D(point) > 1) return false;
            previous = point;
        }
        return true;
    }

    private static long elapsedMillis(long started)
    {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    public boolean walkToNearestBank()
    {
        return runNavigationRequest(null, () -> {
            spellbookSupport.cancel();
            return walkToNearestBankInternal();
        });
    }

    private boolean walkToNearestBankInternal()
    {
        resetApeAtollGreegree();
        setBankSuppliesAllowed(true);
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint fossilBank = nearestFossilBank(player);
        // Recorded exit: Sticky Swamp -> House on the Hill -> Teeth-grip zip line
        // -> northern Bank Chest. Preserve the zip line's level requirement.
        if (StickySwampExit.nearby(player) && Rs2Player.getRealSkillLevel(Skill.AGILITY) >= 76)
            fossilBank = StickySwampExit.BANK_APPROACH;
        if (fossilBank != null)
        {
            refreshTransportExtensions(fossilBank);
            return (Boolean) invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, fossilBank);
        }
        refreshTransportExtensions(null);
        return (Boolean) invoke("walkToNearestBank", NO_TYPES);
    }

    private boolean previewDirect(WorldPoint point)
    {
        doorActionBlocked = false;
        if (bankTeleportBootstrap.beginHousePortalRecovery(point, true)) return true;
        refreshTransportExtensions(point);
        return (Boolean) invoke("previewRouteTo", new Class<?>[]{WorldPoint.class}, point);
    }

    public void cancel()
    {
        cancel("CANCEL_REQUESTED");
    }

    public void cancel(String reason)
    {
        cancelNavigation(cancellationReason(reason), true, true);
    }

    private void cancelNavigation()
    {
        cancelNavigation("INTERNAL_STOP", true);
    }

    private void cancelForPolicyChange()
    {
        cancelNavigation("ROUTE_POLICY_CHANGE", false);
    }

    private void cancelNavigation(String reason, boolean releaseOwner)
    {
        cancelNavigation(reason, releaseOwner, false);
    }

    private void cancelNavigation(String reason, boolean releaseOwner, boolean cancelSpellbook)
    {
        final long cancellationEpoch;
        // Invalidate the caller immediately, before the client-thread cancellation is queued.
        synchronized (requestOwnership)
        {
            if (!requestOwnership.isContextCurrent()) return;
            logCancellation(requestOwnership.current(), reason, engineStatus());
            cancellationEpoch = releaseOwner ? requestOwnership.cancel() : requestOwnership.epoch();
            synchronized (wildernessTravelResume)
            {
                requestCancellation.cancel();
                wildernessTravelResume.reset();
            }
        }
        Supplier<Boolean> cleanup = () -> {
            synchronized (requestOwnership)
            {
                // A new request may have started while this cancellation was queued.
                if (requestOwnership.epoch() != cancellationEpoch) return false;
                try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(null))
                {
                    if (cancelSpellbook) spellbookSupport.cancel();
                    clearNavigationOnClientThread();
                }
                return true;
            }
        };
        // Cancellation must not clear the bank target halfway through a client-tick
        // transition into WAITING_FINAL_PREVIEW. Script callers wait for completion.
        if (!client.isClientThread())
        {
            if (Thread.currentThread().isInterrupted())
                Microbot.getClientThread().invokeLater(() -> { cleanup.get(); });
            else
                readOnClientThread(cleanup, false);
            return;
        }
        cleanup.get();
    }

    private void cancelOnClientThread()
    {
        synchronized (requestOwnership)
        {
            if (!requestOwnership.isContextCurrent()) return;
            clearNavigationOnClientThread();
        }
    }

    private void clearNavigationOnClientThread()
    {
        routeSubmission.cancel();
        routeSubmissionFailure = null;
        catalogTeleportSupport.resetObservedLanding();
        routeSettings.clear();
        catalogTeleportSupport.resetActivationReadiness();
        resetApeAtollGreegree();
        doorActionBlocked = false;
        caveLightFailure = null;
        trackedTeleport = null;
        trackedTeleportDeparture = null;
        WalkerMouseControl.invalidate();
        networkTransportSupport.setMagicMushtreesAllowed(true);
        fairyRingEquipmentRestore.reset();
        committedTirannwnTarget = null;
        nearbyDestinationRecovery.reset();
        finalTileWatchdog.reset();
        bankTeleportBootstrap.cancel();
        networkTransportSupport.cancel();
        motherlodeEntranceRockfalls.reset();
        spiritTreeSupport.cancel();
        invoke("cancel", NO_TYPES);
    }

    void shutdown()
    {
        routeSubmission.close();
        // Guice retains this facade across plugin disable/enable cycles. The new
        // scheduler is lazy: no thread is started until a later route is submitted.
        routeSubmission = newRouteSubmission();
        spellbookSupport.shutdown();
        resetApeAtollGreegree();
        WalkerMouseControl.shutdown();
        fairyRingEquipmentRestore.reset();
        bankTeleportBootstrap.shutdown();
        networkTransportSupport.shutdown();
        motherlodeEntranceRockfalls.shutdown();
        motherlodeEntranceRockfalls = new MotherlodeEntranceRockfalls();
        invoke("shutdown", NO_TYPES);
    }

    public WorldPoint getDestination()
    {
        if (apeAtollTabletTarget != null) return apeAtollTabletTarget;
        WorldPoint paused = wildernessTravelResume.target();
        if (paused != null) return paused;
        WalkerRouteSubmission.Ticket pending = routeSubmission.pending();
        if (pending != null) return pending.target;
        WorldPoint synthetic = finalTileWatchdog.syntheticDestination();
        if (synthetic != null)
        {
            return synthetic;
        }
        WorldPoint instanceRecoveryTarget = bankTeleportBootstrap.instanceRecoveryTarget();
        return instanceRecoveryTarget != null
            ? instanceRecoveryTarget
            : (WorldPoint) invoke("getDestination", NO_TYPES);
    }

    public WorldPoint getLogicalDestination()
    {
        if (committedTirannwnTarget != null)
        {
            return committedTirannwnTarget;
        }
        WorldPoint nearbyTarget = nearbyDestinationRecovery.logicalTarget();
        if (nearbyTarget != null)
        {
            return nearbyTarget;
        }
        if (bankTeleportBootstrap.pendingTarget != null)
        {
            return bankTeleportBootstrap.pendingTarget;
        }
        return getDestination();
    }

    @SuppressWarnings("unchecked")
    List<WorldPoint> getRemainingRoute()
    {
        return (List<WorldPoint>) invoke("getRemainingRoute", NO_TYPES);
    }

    @SuppressWarnings("unchecked")
    Set<WorldPoint> getTransportEndpoints()
    {
        return (Set<WorldPoint>) invoke("getTransportEndpoints", NO_TYPES);
    }

    TransportMarker getActiveTransportMarker()
    {
        Object shadow = invoke("getActiveTransportMarker", NO_TYPES);
        if (shadow == null)
        {
            return null;
        }
        try
        {
            Field pointField = field(shadow.getClass(), "point");
            Field actionField = field(shadow.getClass(), "action");
            Field targetField = field(shadow.getClass(), "target");
            return new TransportMarker((WorldPoint) pointField.get(shadow), (String) actionField.get(shadow), (String) targetField.get(shadow));
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException("Unable to read active transport from v1.11.2 engine", ex);
        }
    }

    /** Coordinator work remains active even when the engine is idle or previewing. */
    public String getNavigationPhase()
    {
        if (spellbookSupport.active()) return "SPELLBOOK_PREPARATION";
        if (isWildernessTravelPaused()) return "PAUSED_SAFETY_LOGOUT";
        BankState phase = bankTeleportBootstrap.state;
        return phase == BankState.IDLE ? getStatus().name() : "BANK_" + phase.name();
    }

    public Status getStatus()
    {
        // Route publication from a worker uses this same short monitor. Recovery
        // ticks and engine completion run on the client thread, so no half-tick
        // BLOCKED/PREVIEW combination can escape to a polling script.
        boolean includePreparation = requestOwnership.context() == null;
        return readOnClientThread(() -> {
            synchronized (requestOwnership)
            {
                return includePreparation && requestOwnership.preparing()
                    ? Status.PLANNING : getStatusSnapshot();
            }
        }, Status.PLANNING);
    }

    private Status getStatusSnapshot()
    {
        if (routeSubmissionFailure != null) return Status.BLOCKED;
        if (routeSubmission.isPending()) return Status.PLANNING;
        if (caveLightFailure != null) return Status.BLOCKED;
        if (bankTeleportBootstrap.caveLightSupplyPass) return Status.WALKING;
        if (apeAtollGreegree.failure() != null) return Status.BLOCKED;
        if (apeAtollTabletTarget != null) return Status.WALKING;
        if (motherlodeEntranceRockfalls.failure() != null) return Status.BLOCKED;
        if (isWildernessTravelPaused()) return Status.WALKING;
        if(networkTransportSupport.tzhaarDoorFailure()!=null)return Status.BLOCKED;
        if (bankTeleportBootstrap.isHousePortalPreview()) return Status.PREVIEW;
        if (doorActionBlocked) return Status.BLOCKED;
        if (fairyRingEquipmentRestore.failed()) return Status.BLOCKED;
        Status status = engineStatus();
        if (fairyRingEquipmentRestore.isTracking() && status == Status.ARRIVED)
            return Status.WALKING;
        if (finalTileWatchdog.syntheticDestination() != null && !bankTeleportBootstrap.isBusy())
        {
            if (needsApeAtollGreegreeAtArrival()) return Status.WALKING;
            return Status.ARRIVED;
        }
        if (bankTeleportBootstrap.isEscapingInstance())
        {
            return Status.WALKING;
        }
        if (bankTeleportBootstrap.isRecoverableBlockedInstancePreview(status))
        {
            // Keep callers from treating an instanced carried-teleport preview failure as a
            // terminal ROUTE_UNSUPPORTED before the client-thread recovery tick can run.
            return Status.PREVIEW;
        }

        if (status == Status.ARRIVED && bankTeleportBootstrap.state == BankState.IDLE
            && needsApeAtollGreegreeAtArrival()) return Status.WALKING;
        if (bankTeleportBootstrap.isBusy()
            && (status == Status.ARRIVED || status == Status.BLOCKED || status == Status.IDLE))
        {
            return Status.PLANNING;
        }
        if (status == Status.BLOCKED && nearbyDestinationRecovery.canContinue(status))
        {
            return Status.PLANNING;
        }
        if (status == Status.BLOCKED && networkTransportSupport.hasRecoverableBlockedShipDeck())
            return Status.WALKING;
        if (status == Status.BLOCKED && networkTransportSupport.hasRecoverableBlockedCustomEdge())
        {
            // Elkoy is an executable NPC transport even when the engine has stopped
            // advancing the ground leg beside the maze shortcut. Keep the request alive long
            // enough for NetworkTransportSupport's guarded failsafe to run.
            return Status.WALKING;
        }
        return status;
    }

    private Status engineStatus()
    {
        Object value = invoke("getStatus", NO_TYPES);
        return Status.valueOf(((Enum<?>) value).name());
    }

    public PlanningFailure getPlanningFailure()
    {
        return readOnClientThread(() -> {
            synchronized (requestOwnership)
            {
                return requestOwnership.preparing() ? PlanningFailure.NONE : getPlanningFailureSnapshot();
            }
        }, PlanningFailure.NONE);
    }

    private PlanningFailure getPlanningFailureSnapshot()
    {
        if (routeSubmissionFailure != null) return PlanningFailure.ROUTE_UNSUPPORTED;
        if (routeSubmission.isPending()) return PlanningFailure.NONE;
        if (caveLightFailure != null) return PlanningFailure.ROUTE_UNSUPPORTED;
        if (apeAtollGreegree.failure() != null) return PlanningFailure.ROUTE_UNSUPPORTED;
        if (apeAtollTabletTarget != null) return PlanningFailure.NONE;
        if (isWildernessTravelPaused()) return PlanningFailure.NONE;
        if (doorActionBlocked) return PlanningFailure.ROUTE_UNSUPPORTED;
        Status status = engineStatus();
        if (bankTeleportBootstrap.isBusy()
            || bankTeleportBootstrap.isEscapingInstance()
            || bankTeleportBootstrap.isRecoverableBlockedInstancePreview(status)
            || networkTransportSupport.hasRecoverableBlockedCustomEdge()
            || (engineStatus() == Status.BLOCKED && networkTransportSupport.hasRecoverableBlockedShipDeck())
            || (engineStatus() == Status.BLOCKED && nearbyDestinationRecovery.canContinue(Status.BLOCKED)))
        {
            // The facade still owns a valid recovery. Do not let external bridges cancel
            // an instanced teleport escape or nearby Elkoy handoff as ROUTE_UNSUPPORTED
            // before the client-thread recovery tick can run.
            return PlanningFailure.NONE;
        }
        Object value = invoke("getPlanningFailure", NO_TYPES);
        return PlanningFailure.valueOf(((Enum<?>) value).name());
    }

    boolean hasActiveExecutionRequest()
    {
        WalkerRouteSubmission.Ticket pending = routeSubmission.pending();
        if (pending != null && !"previewRouteTo".equals(pending.command)) return true;
        return spellbookSupport.active() || apeAtollTabletTarget != null || fairyRingEquipmentRestore.isTracking() || bankTeleportBootstrap.isBusy()
            || (Boolean) invoke("hasActiveExecutionRequest", NO_TYPES);
    }

    void tick()
    {
        spellbookSupport.tick();
        WalkerRequestOwnership.Request request;
        long epoch;
        synchronized (requestOwnership)
        {
            if (requestOwnership.preparing() || routeSubmission.isPending()) return;
            request = requestOwnership.current();
            epoch = requestOwnership.epoch();
        }
        try (WalkerRequestOwnership.Scope scope = requestOwnership.bind(request, epoch);
             var ignored = WalkerMouseControl.openProfile())
        {
            requestOwnership.check();
            if (request != null && request.tickAction != null) request.tickAction.run();
            else tickWithMouseProfile();
        }
        catch (CancellationException replaced)
        {
            // A newer request owns the next tick; an old tick must not republish its route.
        }
    }

    void tickSpellbookPreparation() { spellbookSupport.tick(); }

    public enum SpellbookStatus { IDLE, DISABLED, PREPARING, READY, BLOCKED }

    private void tickWithMouseProfile()
    {
        boolean inputHeld = Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
        bankTeleportBootstrap.holdPreparationBudget(inputHeld);
        if (inputHeld) {
            catalogTeleportSupport.resetActivationReadiness();
            apeAtollGreegree.pause(client.getTickCount());
            if (apeAtollTabletTarget != null) apeAtollTabletStartedTick = client.getTickCount();
            return;
        }
        if (isWildernessTravelPaused()) return;
        if (refreshRouteSettingsBeforeTick()) return;
        syncWildernessTravelPolicy(getDestination());
        WalkerRequestOwnership.Request activeJourney = requestOwnership.current();
        WorldPoint safetyPosition = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
        if (activeJourney != null && safetyPosition != null
            && !WildernessTravelPolicy.isBlocked(safetyPosition, false)) activeJourney.reachedSafety = true;
        if (engineStatus() == Status.WALKING && !WildernessTravelPolicy.routeAllowed(
            getRemainingRoute(), getDestination(), config.allowWildernessTravel(),
            activeJourney == null ? null : activeJourney.safetyOrigin,
            activeJourney != null && activeJourney.reachedSafety)) {
            cancel("UNSAFE_WILDERNESS");
            Microbot.status = "Unsafe Wilderness shortcut blocked for a safe destination";
            LOG.warn("[EfficientWalker] blocked Wilderness transit to a safe destination");
            return;
        }
        if (tickApeAtollTabletTravel()) return;
        if (prepareSelectedCaveLight()) return;
        if (engineStatus() == Status.WALKING && motherlodeEntranceRockfalls.beforeTick(this::getRemainingRoute)) return;
        String doorFailure = WalkerMouseControl.takeDoorFailure();
        if (doorFailure != null) {
            cancel("DOOR_FAILURE");
            doorActionBlocked = true;
            Microbot.status = doorFailure;
            LOG.warn("[EfficientWalker] door action stopped: {}", doorFailure);
            return;
        }
        if (doorActionBlocked) return;
        if (travelEquipmentDirty) {
            travelEquipmentDirty = false;
            catalogTeleportSupport.refreshForPlanning();
        }
        WalkerMouseControl.tick(client, engineStatus() == Status.WALKING
            && bankTeleportBootstrap.state == BankState.IDLE && getActiveTransportMarker() == null);
        if (restoreFairyRingEquipmentBeforeTick()) return;
        if (holdGreegreeBeforeSurfaceMovement()) return;
        if (engineStatus() == Status.BLOCKED && networkTransportSupport.hasRecoverableBlockedShipDeck())
        {
            networkTransportSupport.tickBlockedShipDeckRecovery(getDestination());
            WorldPoint landedTarget = networkTransportSupport.consumeReplanTarget();
            if (landedTarget != null)
            {
                // Resume the original request rather than asking the caller to resubmit.
                routeSettings.consume();
                invoke("cancel", NO_TYPES);
                finalTileWatchdog.reset();
                networkTransportSupport.clearCompleteRoute();
                refreshTransportExtensions(landedTarget);
                invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, landedTarget);
            }
            return;
        }
        if (engineStatus() == Status.BLOCKED && nearbyDestinationRecovery.canRetry())
        {
            nearbyDestinationRecovery.tryNext();
            return;
        }
        if (finalTileWatchdog.syntheticDestination() != null && !bankTeleportBootstrap.isBusy())
        {
            return;
        }

        if (finalTileWatchdog.observeAndFinishIfStuck())
        {
            return;
        }

        boolean consumed = bankTeleportBootstrap.beforeEngineTick();
        // A failed bank-aware preview releases its coordinator here. Retry the nearby
        // destination on this same tick so script bridges cannot observe and cancel BLOCKED.
        if (nearbyDestinationRecovery.canRetry())
        {
            nearbyDestinationRecovery.tryNext();
            return;
        }
        if (!consumed)
        {
            // Planning/preview must stay side-effect free.  The previous network extension
            // could see the preview's selected transport and actually start a minigame
            // teleport before the world map had closed.  Only execute custom transports once
            // the engine has committed the route to WALKING.
            Status status = engineStatus();
            boolean blockedElkoyRecovery =
                status == Status.BLOCKED && networkTransportSupport.hasRecoverableBlockedCustomEdge();
            boolean executeTransitions = status == Status.WALKING || blockedElkoyRecovery;
            boolean networkConsumed = executeTransitions && networkTransportSupport.beforeEngineTick();

            WorldPoint replan = executeTransitions ? networkTransportSupport.consumeReplanTarget() : null;
            if (replan != null)
            {
                // A failed Nexus/Box hop can leave us at raw instance coordinates.
                // Retain the herb destination while exiting via the real house portal;
                // never send a dynamic POH location into the static overworld graph.
                if (MaxPohSupport.insideHouse()
                    && bankTeleportBootstrap.beginHousePortalRecovery(replan, false)) return;
                boolean randomLanding = networkTransportSupport.consumeObeliskReplan();
                boolean swampRetry = networkTransportSupport.consumeStickySwampBankRetry();
                if (swampRetry && bankTeleportBootstrap.state == BankState.IDLE)
                {
                    WorldPoint teleportBank = stickySwampRetryBank(replan);
                    if (teleportBank != null) replan = teleportBank;
                }
                // A transport completes one leg of the existing journey, not a new request.
                // Keep the final destination and bank withdrawals across the intermediate hop.
                routeSettings.consume();
                invoke("cancel", NO_TYPES);
                finalTileWatchdog.reset();
                if (bankTeleportBootstrap.state == BankState.WALKING_TO_BANK)
                {
                    bankTeleportBootstrap.startProvenBankWalk();
                }
                else
                {
                    if (randomLanding) networkTransportSupport.clearCompleteRoute();
                    refreshTransportExtensions(replan);
                    if (randomLanding && config.useTeleportations()
                        && !catalogTeleportSupport.availableLandings(Rs2Player.getWorldLocation(), replan).isEmpty())
                        bankTeleportBootstrap.recompareCarriedRoute(replan);
                    else invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, replan);
                }
                return;
            }

            boolean wyvernDialogueConsumed = executeTransitions && handleWyvernTunnelWarning();
            boolean spiritConsumed = executeTransitions && !wyvernDialogueConsumed
                && spiritTreeSupport.beforeEngineTick();
            if (spiritTreeSupport.consumeReplanRequest())
            {
                replanAfterSpiritTreeFailure();
                return;
            }
            boolean teleportConsumed = executeTransitions && !wyvernDialogueConsumed
                && (catalogTeleportSupport.handleDialogue()
                    || catalogTeleportSupport.prepareItemActivation(System.currentTimeMillis()));
            if (!networkConsumed && !wyvernDialogueConsumed && !spiritConsumed && !teleportConsumed)
            {
                Object departingTeleport = engineField("activeTeleportProvider");
                if (departingTeleport != trackedTeleport) {
                    trackedTeleport = departingTeleport;
                    trackedTeleportDeparture = Rs2Player.getWorldLocation();
                }
                WorldPoint departure = trackedTeleportDeparture;
                WorldPoint expected = (WorldPoint) engineField("expectedTeleportDestination");
                WorldPoint destination = getDestination();
                WorldPoint beforeTick = Rs2Player.getWorldLocation();
                if (!catalogTeleportSupport.confirmObservedLanding(requestOwnership.epoch()))
                {
                    catalogTeleportSupport.armObservedLanding(requestOwnership.epoch());
                    invoke("tick", NO_TYPES);
                    catalogTeleportSupport.recordObservedDispatch(requestOwnership.epoch());
                }
                Object pendingAfterTick = engineField("activeTeleportProvider");
                if (pendingAfterTick != null && pendingAfterTick != trackedTeleport) {
                    trackedTeleport = pendingAfterTick;
                    trackedTeleportDeparture = beforeTick;
                }
                // The engine plans only a walking leg on teleport arrival.
                // Re-evaluate carried teleports whose Wilderness restrictions just changed.
                if (departingTeleport != null && engineField("activeTeleportProvider") == null
                    && bankTeleportBootstrap.state == BankState.IDLE && config.useTeleportations()) {
                    trackedTeleport = null;
                    trackedTeleportDeparture = null;
                    WorldPoint actual = Rs2Player.getWorldLocation();
                    if (TeleportLandingReplan.shouldRecompare(departure, expected, actual, destination)) {
                        routeSettings.consume();
                        invoke("cancel", NO_TYPES);
                        finalTileWatchdog.reset();
                        networkTransportSupport.clearCompleteRoute();
                        refreshTransportExtensions(destination);
                        bankTeleportBootstrap.recompareCarriedRoute(destination);
                        return;
                    }
                }
            }
        }
        bankTeleportBootstrap.afterEngineTick();
        if (committedTirannwnTarget != null
            && (getStatus() == Status.ARRIVED || getDestination() == null))
        {
            committedTirannwnTarget = null;
        }
    }

    private void replanAfterSpiritTreeFailure()
    {
        // This recovery already rebuilds against current preferences; later edits stay queued.
        routeSettings.consume();
        WorldPoint target = getDestination();
        invoke("cancel", NO_TYPES);
        finalTileWatchdog.reset();
        // A complete route may still pin the rejected Spirit Tree edge.
        networkTransportSupport.clearCompleteRoute();
        if (target != null)
        {
            refreshTransportExtensions(target);
            if (bankTeleportBootstrap.state == BankState.WALKING_TO_BANK)
            {
                bankTeleportBootstrap.startProvenBankWalk();
            }
            else
            {
                invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, target);
            }
        }
    }

    /** A necklace landing never crosses this edge, so it never starts light preparation. */
    private boolean prepareSelectedCaveLight()
    {
        if (bankTeleportBootstrap.isBusy() || engineStatus() != Status.WALKING
            || !LumbridgeSwampCaveAccess.routeRequiresLight(getRemainingRoute()) || CaveLightSource.hasLight()) return false;
        CaveLightSource.Preparation preparation = CaveLightSource.prepare(bankSuppliesAllowed());
        if (preparation == null) {
            stopCaveTravel("The selected cave route needs a lit light source; no usable owned light was found.");
            return true;
        }
        return bankTeleportBootstrap.prepareCaveLight(getDestination(), preparation);
    }

    private void stopCaveTravel(String reason)
    {
        cancel("CAVE_LIGHT_FAILURE");
        caveLightFailure = reason;
        Microbot.status = reason;
    }

    void routeSettingsChanged() { routeSettings.changed(); }

    boolean hasPendingRouteSettings() { return routeSettings.pending(); }

    private boolean refreshRouteSettingsBeforeTick() {
        if (!routeSettings.pending()) return false;
        Status status = engineStatus();
        // Let existing transport/landing, bank and equipment restoration owners finish first.
        Object action = engineField("pendingPlanTask");
        if (status == Status.PLANNING || status == Status.PREVIEW || bankTeleportBootstrap.isBusy()
                || apeAtollTabletTarget != null || fairyRingEquipmentRestore.isTracking()
                || engineField("transportAttempt") != null || engineField("teleportAttempt") != null || engineField("pendingDoor") != null
                || networkTransportSupport.hasPendingAction() || spiritTreeSupport.hasPendingAction()
                || greegreeHoldAction != null && !greegreeHoldAction.isDone()
                || action instanceof Future && !((Future<?>) action).isDone()) return false;
        WorldPoint destination = getDestination();
        if (destination == null || status != Status.WALKING && status != Status.BLOCKED) {
            routeSettings.clear();
            return false;
        }
        if (!routeSettings.consume()) return false;
        // Cancel only the walking leg. Public cancel resets journey restrictions,
        // bank ownership and equipment restoration which still belong to the original caller.
        invoke("cancel", NO_TYPES);
        finalTileWatchdog.reset();
        trackedTeleport = null;
        trackedTeleportDeparture = null;
        catalogTeleportSupport.resetActivationReadiness();
        networkTransportSupport.clearCompleteRoute();
        refreshTransportExtensions(destination);
        invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, destination);
        LOG.info("[EfficientWalker] saved route settings refreshed target={}", destination);
        return true;
    }

    private Object engineField(String name) {
        try { return field(engineClass, name).get(engine); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("Cannot inspect teleport state", ex); }
    }

    private WorldPoint stickySwampRetryBank(WorldPoint target)
    {
        WorldPoint start = Rs2Player.getWorldLocation();
        if (!StickySwampExit.recoveryArea(start) || !StickySwampExit.bankTarget(target)
            || !config.useTeleportations() || Rs2Player.isTeleBlocked()) return null;
        List<WorldPoint> banks = new ArrayList<>();
        for (var bank : net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.values())
            if (bank.hasRequirements() && !WildernessTravelPolicy.isBlocked(bank.getWorldPoint(), false))
                banks.add(bank.getWorldPoint());
        WorldPoint bank = StickySwampExit.teleportBank(
            catalogTeleportSupport.availableLandings(start, target), banks);
        if (bank != null) LOG.debug("[EfficientWalker] Sticky Swamp exit failed; replanning with an available bank teleport target={}", bank);
        return bank;
    }

    /** Advance the warning, then choose an entry option on the following screen. */
    private boolean handleWyvernTunnelWarning()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint destination = getDestination();
        if (player == null || destination == null
            || player.distanceTo2D(WYVERN_TUNNEL_ENTRANCE) > 4
            || destination.getPlane() != 0 || destination.getY() >= 9570
            || destination.getX() < 3000 || destination.getX() > 3050)
        {
            return false;
        }
        if (Rs2Dialogue.hasSelectAnOption())
        {
            List<Widget> options = Rs2Dialogue.getDialogueOptions();
            int enter = -1;
            for (int index = 0; index < options.size(); index++)
            {
                String label = options.get(index).getText().replaceAll("<[^>]*>", "")
                    .trim().toLowerCase(Locale.ROOT);
                if (!label.contains("enter") || label.contains("do not enter")
                    || label.contains("don't enter") || label.contains("not enter"))
                {
                    continue;
                }
                if (label.contains("do not remind") || label.contains("don't remind"))
                {
                    return Rs2Dialogue.keyPressForDialogueOption(index + 1);
                }
                if (enter < 0)
                {
                    enter = index + 1;
                }
            }
            return enter > 0 && Rs2Dialogue.keyPressForDialogueOption(enter);
        }
        if (!Rs2Dialogue.hasContinue()) return false;
        for (int group : new int[]{229, 193, 217, 231})
        {
            for (int child = 0; child < 20; child++)
            {
                if (containsWyvernWarning(client.getWidget(group, child)))
                {
                    Rs2Dialogue.clickContinue();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsWyvernWarning(Widget widget)
    {
        if (widget == null || widget.isHidden()) return false;
        String text = widget.getText();
        if (text != null && text.toLowerCase(Locale.ROOT).contains("creatures in this cave are very dangerous"))
            return true;
        Widget[] children = widget.getChildren();
        if (children != null)
            for (Widget child : children)
                if (containsWyvernWarning(child)) return true;
        return false;
    }

    private boolean restoreFairyRingEquipmentBeforeTick()
    {
        if (fairyRingEquipmentRestore.failed()) return true;
        if (!fairyRingEquipmentRestore.isTracking() && engineStatus() != Status.WALKING) return false;
        Player player = client.getLocalPlayer();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (player == null || equipment == null || equipment.getItems() == null) return false;
        WorldPoint origin = null;
        WorldPoint destination = null;
        if (engineStatus() == Status.WALKING)
        {
            try
            {
                Object edge = field(engineClass, "activeTransport").get(engine);
                if (edge != null && ("Fairy ring".equalsIgnoreCase(
                    (String) field(edge.getClass(), "target").get(edge))
                    || ("Max POH".equals(field(edge.getClass(), "action").get(edge))
                        && String.valueOf(field(edge.getClass(), "target").get(edge)).startsWith("RING:"))))
                {
                    origin = (WorldPoint) field(edge.getClass(), "origin").get(edge);
                    destination = (WorldPoint) field(edge.getClass(), "destination").get(edge);
                }
            }
            catch (ReflectiveOperationException ex)
            {
                throw new IllegalStateException("Cannot inspect fairy-ring equipment transition", ex);
            }
            // A house hop starts from the global path before native O is materialized.
            // Capture the original equipment before its worker can equip the ring staff.
            Transport houseLeg = networkTransportSupport.pendingHouseLeg();
            if (origin == null && MaxPohSupport.isLeg(houseLeg) && houseLeg.getName().startsWith("RING:"))
            {
                origin = player.getWorldLocation();
                destination = houseLeg.getDestination();
            }
        }
        Item[] items = equipment.getItems();
        int weapon = equippedId(items, EquipmentInventorySlot.WEAPON.getSlotIdx());
        int shield = equippedId(items, EquipmentInventorySlot.SHIELD.getSlotIdx());
        boolean consumed = fairyRingEquipmentRestore.tick(origin, destination, player.getWorldLocation(),
            weapon, shield, client.getTickCount(), (offhand, itemId) -> {
                if (itemId < 0)
                    Rs2Equipment.unEquip(offhand ? EquipmentInventorySlot.SHIELD : EquipmentInventorySlot.WEAPON);
                else if (Rs2Inventory.hasItem(itemId))
                {
                    String[] actions = client.getItemDefinition(itemId).getInventoryActions();
                    if (actions != null)
                        Arrays.stream(actions).filter(action -> "Wield".equalsIgnoreCase(action)
                            || "Wear".equalsIgnoreCase(action)).findFirst()
                            .ifPresent(action -> Rs2Inventory.interact(itemId, action));
                }
            });
        if (consumed)
            Microbot.status = fairyRingEquipmentRestore.failed()
                ? "Could not restore equipment after fairy ring. Check the previous weapon/shield, then retry."
                : "Restoring equipment after fairy ring.";
        return consumed;
    }

    private static int equippedId(Item[] equipment, int slot)
    {
        return slot >= equipment.length || equipment[slot] == null ? -1 : equipment[slot].getId();
    }

    private int wornGreegree() {
        Map<Integer, Integer> worn = TravelEquipment.wornItems();
        return ApeAtollGreegree.first(worn::containsKey);
    }

    private boolean requiresApeAtollProtection(WorldPoint target) {
        return ApeAtollGreegree.onIsland(target)
            && ApeAtollGreegree.required(target, Rs2Player.getQuestState(Quest.MONKEY_MADNESS_II));
    }

    private boolean needsApeAtollGreegreeAtArrival() {
        if (apeAtollGreegreeVerified || !requiresApeAtollProtection(getLogicalDestination())
            || !ApeAtollGreegree.onIsland(Rs2Player.getWorldLocation())) return false;
        if (wornGreegree() == 0) return true;
        apeAtollGreegreeVerified = true;
        return false;
    }

    private boolean missingApeAtollSupplies(WorldPoint target) {
        return requiresApeAtollProtection(target) && (carriedGreegree() == 0
            || useApeAtollTabletFlow(target) && !ApeAtollGreegree.onIsland(Rs2Player.getWorldLocation()) && !Rs2Inventory.hasItem(ApeAtollGreegree.TABLET));
    }

    private boolean useApeAtollTabletFlow(WorldPoint target) { return config.useTeleportations() && requiresApeAtollProtection(target); }

    private boolean beginApeAtollTabletTravel(WorldPoint target) {
        return updateOnClientThread(() -> {
            if (Rs2Player.getQuestState(Quest.MONKEY_MADNESS_I) != QuestState.FINISHED) {
                stopApeAtollTravel("Ape Atoll tablet travel requires completion of Monkey Madness I."); return false;
            }
            if (Rs2Player.isTeleBlocked()
                || net.runelite.client.plugins.microbot.util.player.Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 20) {
                stopApeAtollTravel("Ape Atoll tablet cannot be used here; leave teleport-restricted Wilderness or Tele Block first."); return false;
            }
            if (missingApeAtollSupplies(target) || Rs2Bank.isOpen()) {
                stopApeAtollTravel("Ape Atoll travel supplies are not ready or the bank is still open."); return false;
            }
            cancelOnClientThread();
            apeAtollTabletTarget = target;
            apeAtollTabletStartedTick = client.getTickCount(); apeAtollTabletWorld = client.getWorld();
            apeAtollTabletAction = bankTeleportBootstrap.bankExecutor().submit(() -> Rs2Inventory.interact(ApeAtollGreegree.TABLET, "Break"));
            Microbot.status = "Teleporting to Ape Atoll before holding monkey greegree.";
            return true;
        });
    }

    private boolean tickApeAtollTabletTravel() {
        if (apeAtollTabletTarget == null) return false;
        if (client.getWorld() != apeAtollTabletWorld) {
            stopApeAtollTravel("World changed during Ape Atoll tablet travel; retry the route."); return true;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (!apeAtollTabletLanded) {
            Player livePlayer = client.getLocalPlayer();
            WorldPoint live = livePlayer == null ? null : livePlayer.getWorldLocation();
            if (player == null || live == null || !player.equals(live) || player.getPlane() != 0
                || player.distanceTo2D(ApeAtollGreegree.TABLET_LANDING) > 8) {
                if (client.getTickCount() - apeAtollTabletStartedTick >= 40)
                    stopApeAtollTravel("Ape Atoll tablet landing was not observed; no second tablet used.");
                return true;
            }
            apeAtollTabletLanded = true;
        }
        if (player == null || Rs2Player.isAnimating()) return true;
        if (!ApeAtollGreegree.onIsland(player)) {
            stopApeAtollTravel("Ape Atoll landing changed before greegree preparation finished; retry the route."); return true;
        }
        if (holdApeAtollGreegree(player)) return true;
        WorldPoint target = apeAtollTabletTarget;
        boolean protectedBeforeContinuing = apeAtollGreegreeVerified;
        resetApeAtollGreegree();
        apeAtollGreegreeVerified = protectedBeforeContinuing;
        walkToDirect(target);
        return true;
    }

    private int carriedGreegree() {
        int worn = wornGreegree();
        return worn > 0 ? worn : ApeAtollGreegree.first(Rs2Inventory::hasItem);
    }

    private boolean holdGreegreeBeforeSurfaceMovement() {
        WorldPoint player = Rs2Player.getWorldLocation();
        // The original AIO gate also intercepted departures from the island and bank legs.
        // Protect only the committed island journey; leaving Ape Atoll stays available.
        if (!ApeAtollGreegree.onIsland(player) || bankTeleportBootstrap.state != BankState.IDLE
            || !requiresApeAtollProtection(getLogicalDestination())) return false;
        Status status = engineStatus();
        if (apeAtollGreegreeVerified && (status == Status.ARRIVED || finalTileWatchdog.syntheticDestination() != null)) return false;
        if (status != Status.WALKING && status != Status.ARRIVED && finalTileWatchdog.syntheticDestination() == null) return false;
        return holdApeAtollGreegree(player);
    }

    private boolean holdApeAtollGreegree(WorldPoint player) {
        if (Microbot.pauseAllScripts.get() || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) {
            apeAtollGreegree.pause(client.getTickCount()); return true;
        }
        boolean consumed = apeAtollGreegree.tick(player, Rs2Player.getQuestState(Quest.MONKEY_MADNESS_II),
            wornGreegree(), carriedGreegree(), client.getTickCount(), id -> {
                if (greegreeHoldAction != null && !greegreeHoldAction.isDone()) return false;
                greegreeHoldAction = bankTeleportBootstrap.bankExecutor().submit(() -> Rs2Inventory.interact(id, "Hold"));
                return true;
            });
        if (!consumed && wornGreegree() > 0) apeAtollGreegreeVerified = true;
        if (apeAtollGreegree.failure() != null) stopApeAtollTravel(apeAtollGreegree.failure());
        else if (consumed) Microbot.status = "Holding monkey greegree before continuing Ape Atoll travel.";
        return consumed;
    }

    private void resetApeAtollGreegree() {
        if (greegreeHoldAction != null) greegreeHoldAction.cancel(true);
        if (apeAtollTabletAction != null) apeAtollTabletAction.cancel(true);
        greegreeHoldAction = null;
        apeAtollGreegreeVerified = false;
        apeAtollTabletAction = null; apeAtollTabletTarget = null; apeAtollTabletLanded = false;
        apeAtollGreegree.reset();
    }

    private void stopApeAtollTravel(String reason) {
        cancelOnClientThread();
        apeAtollGreegree.fail(reason);
        Microbot.status = reason;
    }

    void commitTirannwnWalking(WorldPoint target)
    {
        if (target != null)
        {
            committedTirannwnTarget = target;
        }
    }

    void releaseTirannwnWalkingCommit()
    {
        committedTirannwnTarget = null;
    }

    boolean resumeCommittedTirannwnWalk(WorldPoint target)
    {
        if (target == null)
        {
            return false;
        }
        bankTeleportBootstrap.cancel();
        invoke("cancel", NO_TYPES);
        refreshTransportExtensions(target);
        committedTirannwnTarget = target;
        return (Boolean) invoke("walkTo", new Class<?>[]{WorldPoint.class}, target);
    }

    /**
     * Runs a short EfficientWalker-owned waypoint while preserving the caller's real Tirannwn
     * destination.  Repeated external walkTo(finalTarget) calls are absorbed by the committed
     * target guard, so a script cannot replace this short egress leg before it is accepted.
     */
    boolean walkCommittedTirannwnWaypoint(WorldPoint waypoint, WorldPoint finalTarget)
    {
        if (waypoint == null || finalTarget == null)
        {
            return false;
        }
        bankTeleportBootstrap.cancel();
        invoke("cancel", NO_TYPES);
        refreshTransportExtensions(waypoint);
        committedTirannwnTarget = finalTarget;
        return (Boolean) invoke("walkTo", new Class<?>[]{WorldPoint.class}, waypoint);
    }

    private boolean hasCommittedTirannwnWalk()
    {
        return committedTirannwnTarget != null
            && committedTirannwnTarget.equals(getDestination());
    }

    private boolean travelEquipmentDirty;
    boolean isWildernessTravelPaused() { return wildernessTravelResume.target() != null; }

    void pauseForWildernessLogout(long now) {
        if (isWildernessTravelPaused()) return;
        long request = requestCancellation.capture();
        Status status = getStatus();
        WorldPoint target = status == Status.WALKING || status == Status.PLANNING
            || status == Status.PREVIEW ? getLogicalDestination() : null;
        synchronized (wildernessTravelResume) {
            // An explicit cancel during the destination lookup wins over this safety pause.
            if (requestCancellation.capture() != request || isWildernessTravelPaused()) return;
            requestCancellation.cancel();
            wildernessTravelResume.pause(target, now);
        }
        // Called by the client-tick threat watcher; stop the old route without clearing intent.
        cancelOnClientThread();
        if (target != null) LOG.info("[EfficientWalker] travel suspended for safety logout target={}", target);
    }

    void observeWildernessLogoutState(GameState state) { wildernessTravelResume.observe(state); }

    void resumeWildernessTravel(boolean threat, long now) {
        if (!wildernessTravelResume.canResume(client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null, threat, now)) return;
        WorldPoint target = wildernessTravelResume.take();
        LOG.info("[EfficientWalker] rebuilding travel after safety logout target={}", target);
        WalkerRequestOwnership.Request request = requestOwnership.current();
        if (request == null) walkTo(target);
        else if (requestOwnership.prepare(request)) executeNavigationRequest(request, () -> walkTo(target));
    }

    void travelEquipmentChanged() { travelEquipmentDirty = true; }

    void onInventoryChanged(ItemContainer itemContainer)
    {
        invoke("onInventoryChanged", new Class<?>[]{ItemContainer.class}, itemContainer);
    }

    void refreshTeleportInventory()
    {
        invoke("refreshTeleportInventory", NO_TYPES);
    }

    void resetNetworkTransportSession(boolean resetBankKnowledge)
    {
        networkTransportSupport.resetSession();
        fairyRingEquipmentRestore.reset();
        if (resetBankKnowledge) bankTeleportBootstrap.resetSessionKnowledge();
    }

    boolean handleActionFailureMessage(String message)
    {
        return (Boolean) invoke("handleActionFailureMessage", new Class<?>[]{String.class}, message);
    }

    void onTeleportChargeMessage(String message)
    {
        invoke("onTeleportChargeMessage", new Class<?>[]{String.class}, message);
    }

    boolean clickWidgetAction(Widget widget, String action)
    {
        return (Boolean) invoke("clickWidgetAction", new Class<?>[]{Widget.class, String.class}, widget, action);
    }

    /**
     * Very small anti-spam guard for an unstandable final tile. If the walker is
     * already one tile from the requested destination, the player is completely idle, and the
     * exact same position has made no progress for several game ticks, continuing to click the
     * final tile cannot improve the route. Treat the adjacent tile as successful arrival.
     *
     * <p>This deliberately does not run while farther away, moving, animating, interacting,
     * banking, or performing a custom transport, so it cannot short-circuit the working
     * minigame/charter transitions.</p>
     */
    static boolean isStillAtAcceptedArrival(WorldPoint player, WorldPoint target)
    {
        return player != null && target != null && player.getPlane() == target.getPlane()
            && player.distanceTo2D(target) <= 1;
    }

    private static final int[][] NEARBY_OFFSETS = {
        {0, -1}, {-1, 0}, {1, 0}, {0, 1},
        {-1, -1}, {1, -1}, {-1, 1}, {1, 1},
        {0, -2}, {-2, 0}, {2, 0}, {0, 2},
        {-1, -2}, {1, -2}, {-2, -1}, {2, -1},
        {-2, 1}, {2, 1}, {-1, 2}, {1, 2},
        {-2, -2}, {2, -2}, {-2, 2}, {2, 2}
    };

    private enum NearbyRequestMode
    {
        WALK,
        PREVIEW
    }

    /** Resolve an unreachable requested tile once, then keep the chosen route stable across calls. */
    private final class NearbyDestinationRecovery
    {
        // These fields share the short ownership monitor. Candidate construction,
        // graph refresh and client-thread waits always run outside that monitor.
        private WorldPoint requestedTarget;
        private WorldPoint nearbyTarget;
        private List<WorldPoint> candidates;
        private int nextCandidate;
        private NearbyRequestMode requestMode = NearbyRequestMode.WALK;
        private Object retryReservation;
        private WalkerRequestOwnership.Request recoveryRequest;

        boolean acceptRequest(WorldPoint target, NearbyRequestMode mode)
        {
            final WorldPoint accepted;
            synchronized (requestOwnership)
            {
                requestOwnership.check();
                if (target == null)
                {
                    reset();
                    return false;
                }
                WalkerRequestOwnership.Request request = requestOwnership.capture();
                if (!target.equals(requestedTarget) || recoveryRequest != request)
                {
                    requestedTarget = target;
                    nearbyTarget = null;
                    candidates = null;
                    nextCandidate = 0;
                    requestMode = mode;
                    retryReservation = null;
                    recoveryRequest = request;
                    return false;
                }
                // Repeated previews never downgrade an already committed walk.
                if (mode == NearbyRequestMode.WALK) requestMode = NearbyRequestMode.WALK;
                if (retryReservation != null || routeSubmission.isPending()) return true;
                accepted = nearbyTarget;
            }
            if (accepted == null) return false;
            Status status = engineStatus();
            return status == Status.PLANNING || status == Status.PREVIEW
                || status == Status.WALKING
                || (status == Status.ARRIVED && isStillAtAcceptedArrival(
                    Rs2Player.getWorldLocation(), accepted))
                || (status == Status.BLOCKED && canRetry(status));
        }

        WorldPoint logicalTarget()
        {
            synchronized (requestOwnership)
            {
                return nearbyTarget != null ? requestedTarget : null;
            }
        }

        private int candidateCount()
        {
            return candidates == null ? NEARBY_OFFSETS.length : candidates.size();
        }

        boolean exhausted(WorldPoint target)
        {
            synchronized (requestOwnership)
            {
                return retryReservation == null && !routeSubmission.isPending()
                    && target != null && target.equals(requestedTarget)
                    && nextCandidate >= candidateCount() && engineStatus() == Status.BLOCKED;
            }
        }

        boolean canContinue(Status observedStatus)
        {
            synchronized (requestOwnership)
            {
                if (retryReservation != null || routeSubmission.isPending()) return true;
            }
            return canRetry(observedStatus);
        }

        boolean canRetry()
        {
            return canRetry(engineStatus());
        }

        private boolean canRetry(Status observedStatus)
        {
            synchronized (requestOwnership)
            {
                if (requestedTarget == null || retryReservation != null
                    || routeSubmission.isPending() || nextCandidate >= candidateCount() || observedStatus != Status.BLOCKED)
                    return false;
            }
            // Use the status that belongs to the caller's snapshot. Rereading it
            // here used to turn a newly published PREVIEW into an old BLOCKED.
            if (bankTeleportBootstrap.isBusy() || networkTransportSupport.hasRecoverableBlockedCustomEdge())
                return false;
            PlanningFailure failure = rawPlanningFailure();
            return failure == PlanningFailure.TARGET_NOT_STANDABLE
                || failure == PlanningFailure.ROUTE_UNSUPPORTED;
        }

        boolean tryNext()
        {
            if (!canRetry()) return false;
            final Object reservation = new Object();
            final WorldPoint target;
            final WalkerRequestOwnership.Request request;
            final boolean prepare;
            synchronized (requestOwnership)
            {
                requestOwnership.check();
                if (retryReservation != null || requestedTarget == null
                    || nextCandidate >= candidateCount() || engineStatus() != Status.BLOCKED)
                    return false;
                retryReservation = reservation;
                target = requestedTarget;
                request = recoveryRequest;
                prepare = candidates == null;
            }
            boolean started = false;
            try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(request))
            {
                if (prepare)
                {
                    List<WorldPoint> prepared = prepareCandidates(target);
                    synchronized (requestOwnership)
                    {
                        if (!ownsReservation(reservation, request, target)) return false;
                        candidates = prepared;
                    }
                }
                // These helpers may obtain client-thread snapshots; never call them
                // while a worker holds the ownership monitor.
                boolean recoverable = !bankTeleportBootstrap.isBusy()
                    && !networkTransportSupport.hasRecoverableBlockedCustomEdge();
                final WorldPoint candidate;
                final NearbyRequestMode mode;
                synchronized (requestOwnership)
                {
                    if (!ownsReservation(reservation, request, target) || !recoverable
                        || nextCandidate >= candidateCount() || engineStatus() != Status.BLOCKED)
                        return false;
                    PlanningFailure failure = rawPlanningFailure();
                    if (failure != PlanningFailure.TARGET_NOT_STANDABLE
                        && failure != PlanningFailure.ROUTE_UNSUPPORTED) return false;
                    candidate = candidates.get(nextCandidate++);
                    nearbyTarget = candidate;
                    mode = requestMode;
                }
                finalTileWatchdog.reset();
                // The reservation remains active through graph preparation AND final
                // publication, including when this was the last available candidate.
                started = mode == NearbyRequestMode.PREVIEW
                    ? previewDirect(candidate) : walkToDirect(candidate);
                if (started && requestOwnership.isCurrent(request))
                {
                    LOG.info("[EfficientWalker] nearby destination requested={} route={} mode={} owner={} request={}",
                        target, candidate, mode, request.owner, request.id);
                }
            }
            catch (CancellationException cancelled)
            {
                return false;
            }
            finally
            {
                synchronized (requestOwnership)
                {
                    if (retryReservation == reservation) retryReservation = null;
                }
            }
            return started || canRetry();
        }

        private boolean ownsReservation(Object reservation, WalkerRequestOwnership.Request request, WorldPoint target)
        {
            return retryReservation == reservation && recoveryRequest == request
                && Objects.equals(requestedTarget, target) && requestOwnership.isCurrent(request);
        }

        private List<WorldPoint> prepareCandidates(WorldPoint target)
        {
            List<WorldPoint> prepared = new ArrayList<>(NEARBY_OFFSETS.length);
            for (int[] offset : NEARBY_OFFSETS)
            {
                prepared.add(new WorldPoint(target.getX() + offset[0],
                    target.getY() + offset[1], target.getPlane()));
            }
            // Query the installed graph, including extension endpoints. Do not search
            // the whole map repeatedly for tiles the collision data already rejects.
            try
            {
                Object planner = field(engineClass, "planner").get(engine);
                if (planner != null)
                {
                    Method standable = planner.getClass().getDeclaredMethod("isStandable", WorldPoint.class);
                    standable.setAccessible(true);
                    Method canStep = planner.getClass().getDeclaredMethod("canStep",
                        int.class, int.class, int.class, int.class, int.class);
                    canStep.setAccessible(true);
                    Map<WorldPoint, Integer> exits = new HashMap<>();
                    Iterator<WorldPoint> iterator = prepared.iterator();
                    while (iterator.hasNext())
                    {
                        WorldPoint point = iterator.next();
                        if (!Boolean.TRUE.equals(standable.invoke(planner, point)))
                        {
                            iterator.remove();
                            continue;
                        }
                        int count = 0;
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dy = -1; dy <= 1; dy++)
                                if ((dx != 0 || dy != 0) && Boolean.TRUE.equals(canStep.invoke(planner,
                                    point.getX(), point.getY(), point.getPlane(),
                                    point.getX() + dx, point.getY() + dy))) count++;
                        exits.put(point, count);
                    }
                    prepared.sort(Comparator.comparingInt((WorldPoint point) ->
                        exits.getOrDefault(point, 0)).reversed()
                        .thenComparingInt(point -> point.distanceTo2D(target)));
                }
            }
            catch (ReflectiveOperationException ex)
            {
                // Compatibility fallback retains the bounded candidate list.
            }
            WorldPoint player = Rs2Player.getWorldLocation();
            if (player != null && player.getPlane() == target.getPlane()
                && player.distanceTo2D(target) <= 24)
            {
                Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(player, 30);
                if (!reachable.isEmpty())
                {
                    prepared.sort(Comparator
                        .comparingInt((WorldPoint point) -> reachable.containsKey(point) ? 0 : 1)
                        .thenComparingInt(point -> point.distanceTo2D(target))
                        .thenComparingInt(point -> reachable.getOrDefault(point, Integer.MAX_VALUE)));
                }
            }
            return List.copyOf(prepared);
        }

        void reset()
        {
            synchronized (requestOwnership)
            {
                requestedTarget = null;
                nearbyTarget = null;
                candidates = null;
                nextCandidate = 0;
                requestMode = NearbyRequestMode.WALK;
                retryReservation = null;
                recoveryRequest = null;
            }
        }
    }

    private PlanningFailure rawPlanningFailure()
    {
        Object value = invoke("getPlanningFailure", NO_TYPES);
        return PlanningFailure.valueOf(((Enum<?>) value).name());
    }

    private final class FinalTileWatchdog
    {
        private static final int STUCK_TICKS = 8;

        private WorldPoint trackedDestination;
        private WorldPoint lastPlayer;
        private int stationaryTicks;
        private WorldPoint syntheticDestination;

        boolean acceptRequest(WorldPoint target)
        {
            if (target == null)
            {
                reset();
                return false;
            }
            if (syntheticDestination != null && syntheticDestination.equals(target))
            {
                WorldPoint location = Rs2Player.getWorldLocation();
                if (isStillAtAcceptedArrival(location, target)) return true;
                reset();
            }
            if (!target.equals(trackedDestination))
            {
                trackedDestination = target;
                lastPlayer = null;
                stationaryTicks = 0;
                syntheticDestination = null;
            }
            return false;
        }

        boolean observeAndFinishIfStuck()
        {
            if (engineStatus() != Status.WALKING || bankTeleportBootstrap.isBusy())
            {
                stationaryTicks = 0;
                lastPlayer = null;
                return false;
            }

            WorldPoint target = (WorldPoint) invoke("getDestination", NO_TYPES);
            Player player = client.getLocalPlayer();
            if (target == null || player == null)
            {
                stationaryTicks = 0;
                lastPlayer = null;
                return false;
            }

            WorldPoint location = player.getWorldLocation();
            if (location == null || location.getPlane() != target.getPlane()
                || location.distanceTo2D(target) > 1
                || Rs2Player.isMoving()
                || player.getAnimation() != -1
                || player.getInteracting() != null)
            {
                stationaryTicks = 0;
                lastPlayer = location;
                return false;
            }

            if (location.equals(lastPlayer))
            {
                stationaryTicks++;
            }
            else
            {
                lastPlayer = location;
                stationaryTicks = 1;
            }

            if (stationaryTicks < STUCK_TICKS)
            {
                return false;
            }

            syntheticDestination = target;
            trackedDestination = target;
            stationaryTicks = 0;
            // Stop the engine so it cannot continue issuing the same impossible click.
            invoke("cancel", NO_TYPES);
            Microbot.status = "Arrived beside an unstandable destination tile.";
            LOG.info("[EfficientWalker] final-tile watchdog accepted adjacent arrival player={} destination={}",
                location, target);
            return true;
        }

        WorldPoint syntheticDestination()
        {
            return syntheticDestination;
        }

        void reset()
        {
            trackedDestination = null;
            lastPlayer = null;
            stationaryTicks = 0;
            syntheticDestination = null;
        }
    }

    /**
     * Bridges the v1.11.2 bank-aware route planner to Dro's current
     * per-profile bank mirror.
     *
     * <p>The engine already knows how to compare a direct route against
     * "walk to bank + withdraw teleport supplies + teleport + finish route". Its
     * bank candidates come from {@link Rs2Bank#bankItems()}. On a fresh profile,
     * however, that mirror is empty until a bank has been opened at least once.
     * In that one case the old engine cannot know that useful tablets or jewellery
     * exist and therefore falls back to a very long walk.</p>
     *
     * <p>Direct previews exclude bank contents until a complete weighted comparison
     * proves a bank detour faster. Unknown contents never trigger speculative travel.
     * A bank already in reach may supply an otherwise unsupported transport.</p>
     */
    private final class BankTeleportBootstrap
    {
        // Minecarts are cheap and most useful for medium/long traversals across Kourend.
        // Keep this above the generic 30-cost bank probe to avoid banking
        // for tiny local walks just because 20 coins happen to be available.
        private static final int MIN_ROUTE_COST_FOR_MINECART_BANK_PROBE = 35;
        private static final int BANK_SNAPSHOT_TIMEOUT_TICKS = 14;
        private static final int BANK_ROUTE_TIMEOUT_TICKS = 80;
        private static final int ASYNC_BANK_ACTION_TIMEOUT_TICKS = 16;
        private static final int WITHDRAW_VERIFY_TIMEOUT_TICKS = 10;
        private static final int CLOSE_BANK_TIMEOUT_TICKS = 8;
        private static final int FINAL_PREVIEW_TIMEOUT_TICKS = 20;
        private static final int TRANSPORT_REQUIREMENT_ANALYSIS_TIMEOUT_TICKS = 5;
        private static final int FAIRY_CANDIDATE_ANALYSIS_TIMEOUT_TICKS = 24;
        private static final int WEIGHTED_ROUTE_ANALYSIS_TIMEOUT_TICKS = 20;
        // Avoid paying for a full multi-route comparison on trivial local walks.
        private static final int MIN_ROUTE_COST_FOR_WEIGHTED_ANALYSIS = 80;

        private ExecutorService bankExecutor = newBankExecutor();

        private ExecutorService newBankExecutor()
        {
            return WalkerMouseControl.profiledExecutor(Executors.newSingleThreadExecutor(runnable ->
            {
                Thread thread = new Thread(runnable, "EfficientWalker-bank");
                thread.setDaemon(true);
                return thread;
            }));
        }

        private synchronized ExecutorService bankExecutor()
        {
            if (bankExecutor == null || bankExecutor.isShutdown() || bankExecutor.isTerminated())
            {
                bankExecutor = newBankExecutor();
                debug("bank-coordinator executor restarted after plugin/profile lifecycle reset");
            }
            return bankExecutor;
        }

        private volatile BankState state = BankState.IDLE;
        private BankState timedPhase = BankState.IDLE;
        private long phaseStartedNanos = System.nanoTime();

        private void recordPhaseTiming()
        {
            BankState current = state;
            if (current == timedPhase) return;
            if (config.enableDebugging() && timedPhase != BankState.IDLE)
                LOG.info("[EfficientWalker] bank phase={} elapsedMs={} next={}",
                    timedPhase, elapsedMillis(phaseStartedNanos), current);
            timedPhase = current;
            phaseStartedNanos = System.nanoTime();
        }
        private WorldPoint pendingTarget;
        private int stateStartedTick;
        private int bankEpochBeforeOpen;
        private boolean bankWasOpenAtStart;
        private Future<Boolean> bankAction;
        private Future<Boolean> fairyRingCandidateAnalysis;
        private Future<BankTransportRequirements> transportRequirementAnalysis;
        private Future<WeightedRouteAdvisor.Advice> weightedRouteAnalysis;
        private long weightedInventorySignature;
        private int weightedWorld;
        private final WeightedRouteAdvisor weightedRouteAdvisor =
            new WeightedRouteAdvisor(catalogTeleportSupport,
                request -> WildernessTravelPolicy.plan(request, config.allowWildernessTravel()))
                .withDiagnostics(message -> debug("bank-diagnostic {}", message));
        private WorldPoint weightedAnalyzedTarget;
        private WeightedRouteAdvisor.Advice weightedAdvice;
        private Map<Integer, Integer> pendingWithdrawals = Map.of();
        private Map.Entry<Integer, Integer> activeWithdrawal;
        private int activeWithdrawalInventoryBefore;
        private int activeWithdrawalStartedTick;
        private int activeWithdrawalCompletedTick = -1;
        private int preparationHeldTick = -1;
        private boolean closeForDirect;
        private boolean bankCloseClicked;
        private boolean bankCloseFallback;
        private boolean transportSupplyPass;
        private boolean apeAtollSupplyPass;
        private volatile boolean caveLightSupplyPass;
        private int caveLightUnlit = -1, caveLightAttemptTick = -1, caveLightAttempts, caveLightWorld;
        private int caveLightBeforeUnlit, caveLightBeforeLit, caveLightWithdrawalEpoch;
        private boolean transportRequirementsChecked;
        private String selectedBankTeleportName;
        private java.util.concurrent.atomic.AtomicReference<BankTransportRequirements> supplyFallback =
            new java.util.concurrent.atomic.AtomicReference<>();
        private List<Integer> pendingEquipment = new ArrayList<>();
        private int equipmentAttemptTick = -1;
        private Integer spaceDepositItem;
        private int spaceDepositBefore;
        private int spaceDepositTick;
        private boolean probeFairyRingPrerequisites;
        private final LoginBankCache loginBankCache = new LoginBankCache();
        private int immediateBankOpenAttempts;

        // An instanced map can have no ordinary path edge to the overworld even though a
        // carried teleport/spell is perfectly usable. Preserve the original request while
        // one existing EfficientWalker teleport provider moves us out of the instance.
        private TeleportProviders.TeleportProvider instanceEscapeProvider;
        private WorldPoint instanceEscapeLanding;
        private String instanceEscapeName;
        private int instanceEscapeAttempts;
        private int instanceEscapeLastAttemptTick;
        // One supervised escape from a non-instanced tile that repeatedly exhausted the
        // route planner. Keep this budget across clear()/replans to avoid teleport loops.
        private WorldPoint lastIsolatedEscapeStart, lastIsolatedEscapeTarget;
        private int isolatedEscapeCount;
        private WorldPoint activeEscapeStart;
        private boolean isolatedTeleportEscape;
        private boolean housePortalEscape;
        private volatile boolean housePortalPreview;
        private int housePortalWorld;
        private int outsideHouseObservations;

        boolean isHousePortalPreview() {
            return state == BankState.ESCAPING_INSTANCE && housePortalPreview && pendingTarget != null;
        }

        boolean commitHousePortalPreview(WorldPoint target) {
            if (!isHousePortalPreview() || !Objects.equals(target, pendingTarget)) return false;
            if (!client.isClientThread())
                return updateOnClientThread(() -> commitHousePortalPreview(target));
            requestOwnership.check();
            housePortalPreview = false;
            stateStartedTick = client.getTickCount();
            return true;
        }

        /** Reuse the existing instance-escape handoff; no route is planned from a house tile. */
        boolean beginHousePortalRecovery(WorldPoint target, boolean preview) {
            if (target == null || !MaxPohSupport.enabled() || !MaxPohSupport.insideHouse()) return false;
            if (!client.isClientThread())
                return updateOnClientThread(() -> beginHousePortalRecovery(target, preview));
            requestOwnership.check();
            if (housePortalEscape && Objects.equals(target, pendingTarget)) {
                if (!preview) commitHousePortalPreview(target);
                return true;
            }
            var house = MaxPohSupport.currentHouse();
            if (house == null) return false;
            boolean mushtreesAllowed = networkTransportSupport.magicMushtreesAllowed();
            cancelOnClientThread();
            networkTransportSupport.setMagicMushtreesAllowed(mushtreesAllowed);
            pendingTarget = target;
            instanceEscapeLanding = house.getPortalLocation();
            instanceEscapeName = "current house portal";
            housePortalEscape = true;
            housePortalPreview = preview;
            housePortalWorld = client.getWorld();
            outsideHouseObservations = 0;
            instanceEscapeAttempts = 0;
            instanceEscapeLastAttemptTick = -1;
            stateStartedTick = client.getTickCount();
            state = BankState.ESCAPING_INSTANCE;
            Microbot.status = preview ? "Inside house: walking will exit before planning the route."
                : "Leaving the house to plan from outside.";
            debug("house portal recovery target={} preview={} outside={}", target, preview, instanceEscapeLanding);
            return true;
        }

        boolean shouldCoordinateBankedTeleports()
        {
            return state == BankState.IDLE
                && !hasCommittedTirannwnWalk()
                && config.useTeleportations()
                && !(config.useMaxPoh() && MaxPohSupport.inHouse())
                && bankSuppliesAllowed();
        }

        boolean isBusy()
        {
            return state != BankState.IDLE;
        }

        boolean isEscapingInstance()
        {
            return state == BankState.ESCAPING_INSTANCE && !housePortalPreview && pendingTarget != null;
        }

        WorldPoint instanceRecoveryTarget()
        {
            return state == BankState.ESCAPING_INSTANCE ? pendingTarget : null;
        }

        boolean isRecoverableBlockedInstancePreview(Status status)
        {
            // This accessor is called by script-worker bridges as well as the client thread, so
            // keep it free of client-scene reads. Mask any just-failed direct preview for one
            // recovery tick; finishInitialPreviewIfReady() performs the authoritative instance
            // check on the client thread and exposes genuine non-instance failures afterward.
            return state == BankState.WAITING_DIRECT_PREVIEW
                && pendingTarget != null
                && status == Status.BLOCKED;
        }

        boolean acceptDuplicateTargetRequest(WorldPoint target)
        {
            boolean duplicate = state != BankState.IDLE
                && target != null
                && pendingTarget != null
                && pendingTarget.equals(target);
            if (duplicate)
            {
                if (housePortalPreview) commitHousePortalPreview(target);
                debug("bank-coordinator duplicate target request accepted state={} target={}", state, target);
            }
            return duplicate;
        }

        boolean shouldCheckImmediateBankFirst(WorldPoint target)
        {
            if (!shouldCoordinateBankedTeleports() || target == null || Rs2Player.isTeleBlocked())
            {
                return false;
            }
            // House travel needs no inventory of teleport jewellery. A carried/worn
            // entry item plus an available exit can depart without inspecting the bank.
            if (!Rs2Bank.isOpen() && MaxPohSupport.canTravelWithCarriedSupplies()) return false;
            return Rs2Bank.isOpen() || (!bankMirrorKnown() && hasImmediateBankAccess());
        }

        private Future<CompleteRouteDiscovery.Choice> completeDiscovery;
        private CompleteRouteDiscovery.Choice completeChoice;
        private java.util.concurrent.atomic.AtomicReference<CompleteRouteDiscovery.Choice> completeProgress;
        private long completeSignature;
        private int completeWorld;

        private void beginCompleteDiscovery(WorldPoint target)
        {
            WorldPoint start = Rs2Player.getWorldLocation();
            if (start == null || target == null) { beginPreservedBankReplan(); return; }
            Map<Integer,Integer> bank = new LinkedHashMap<>(currentBankCounts());
            long signature = observationInventorySignature();
            int world = client.getWorld();
            completeSignature = signature;
            completeWorld = world;
            java.util.concurrent.atomic.AtomicReference<CompleteRouteDiscovery.Choice> progress = new java.util.concurrent.atomic.AtomicReference<>();
            completeProgress = progress;
            boolean wilderness = config.allowWildernessTravel();
            List<WeightedRouteAdvisor.BankedTeleportOption> options = snapshotBankedTeleportOptions(target);
            state = BankState.DISCOVERING_COMPLETE_ROUTE;
            stateStartedTick = client.getTickCount();
            completeDiscovery = bankExecutor().submit(() -> new CompleteRouteDiscovery(
                request -> WildernessTravelPolicy.plan(request, wilderness),
                (edges, prefix) -> Microbot.getClientThread().invoke(() -> {
                    if (world != client.getWorld() || signature != observationInventorySignature()
                        || !Objects.equals(target, pendingTarget) || !Rs2Bank.isOpen())
                        throw new java.util.concurrent.CancellationException("Travel state changed");
                    Rs2TransportLoadout loadout = TransportSupplyPlanner.supplies(edges,
                        Rs2WalkerBankingPlanner::getMissingTransportEdgeLoadout,
                        id -> bank.getOrDefault(id,0), Rs2Inventory::itemQuantity);
                    if (!loadout.isSatisfiable()) {
                        debug("complete-route loadout rejected reason=edge-supplies prefix={} transports={}",prefix,
                            edges.stream().map(Rs2TransportEdge::getDisplayInfo).collect(java.util.stream.Collectors.toList()));
                        return loadout;
                    }
                    Map<Integer,Integer> required = new LinkedHashMap<>(loadout.getWithdrawals());
                    for (var entry : prefix.entrySet()) {
                        ItemComposition item = client.getItemDefinition(entry.getKey());
                        boolean consumed = item != null && item.getInventoryActions() != null
                            && Arrays.stream(item.getInventoryActions()).anyMatch("Break"::equalsIgnoreCase);
                        required.merge(entry.getKey(),entry.getValue(), consumed ? Integer::sum : Math::max);
                    }
                    if (!containsRequirements(bank,required)) {
                        debug("complete-route loadout rejected reason=bank-stock required={}",required);
                        return Rs2TransportLoadout.unavailable();
                    }
                    String capacityRejection=BankCandidateCapacity.rejection(client,required);
                    if (!capacityRejection.isEmpty()) {
                        debug("complete-route loadout rejected reason={} required={}",capacityRejection,required);
                        return Rs2TransportLoadout.unavailable();
                    }
                    return new Rs2TransportLoadout(required,loadout.getEquipmentItemIds(),true);
                }), message -> debug("complete-route {}",message), System::nanoTime)
                .withProgress(progress::set)
                .compare(start,target,true,options,this::completeRouteSearchPriority));
            Microbot.status = "Comparing complete travel times.";
        }

        private boolean tickCompleteDiscovery()
        {
            if (!Rs2Bank.isOpen()) {
                cancelCompleteDiscovery();
                fallbackToDirect("bank closed during complete comparison");
                return true;
            }
            if (completeDiscovery == null) { beginPreservedBankReplan(); return true; }
            if (!completeDiscovery.isDone()) {
                if (client.getTickCount()-stateStartedTick < WEIGHTED_ROUTE_ANALYSIS_TIMEOUT_TICKS) return true;
                cancelCompleteDiscovery();
                completeChoice = completeProgress == null ? null : completeProgress.get();
                debug("complete-route budget reached; using best completed candidate, alternatives unverified");
            } else {
                try { completeChoice = completeDiscovery.get(); }
                catch (Exception ex) { debug("complete-route unavailable: {}",ex.toString()); completeChoice=null; }
                completeDiscovery=null;
            }
            if (client.getWorld() != completeWorld || observationInventorySignature() != completeSignature)
                completeChoice = null;
            if (completeChoice == null) { beginPreservedBankReplan(); return true; }
            if (!networkTransportSupport.canExecuteCompleteRoute(completeChoice))
            {
                debug("complete-route rejected before withdrawal: {}", networkTransportSupport.selectedTransitionRejection());
                completeChoice = null;
                beginPreservedBankReplan();
                return true;
            }
            pendingWithdrawals = new LinkedHashMap<>(completeChoice.loadout.getWithdrawals());
            pendingEquipment = new ArrayList<>(completeChoice.loadout.getEquipmentItemIds());
            activeWithdrawal=null;
            equipmentAttemptTick=-1;
            transportSupplyPass=false;
            selectedBankTeleportName=completeChoice.name;
            closeForDirect=false;
            debug("complete-route selected={} estimatedSeconds={} withdrawals={}", completeChoice.name,
                RouteTimeCost.seconds(completeChoice.cost),pendingWithdrawals);
            if (pendingWithdrawals.isEmpty()) beginFinalClose();
            else { state=BankState.WITHDRAWING; stateStartedTick=client.getTickCount(); }
            return true;
        }

        private void cancelCompleteDiscovery()
        {
            if (completeDiscovery != null) completeDiscovery.cancel(true);
            completeDiscovery=null;
        }

        private ExecutorService observationExecutor;
        private Future<?> observation;

        private int completeRouteSearchPriority(WorldPoint landing, WorldPoint target)
        {
            int best = WeightedRouteAdvisor.teleportSearchDistance(landing, target);
            // Ordering only: a tablet near an entry ring must not be buried simply
            // because its landing is far from the destination. No candidate is discarded.
            for (Set<Transport> edges : net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2PathApi.getTransports().values())
                for (Transport edge : edges)
                    if (edge.getType() == TransportType.FAIRY_RING)
                    {
                        int approach = WeightedRouteAdvisor.teleportSearchDistance(landing, edge.getOrigin());
                        int onward = WeightedRouteAdvisor.teleportSearchDistance(edge.getDestination(), target);
                        long hint = (long) approach + 10 + onward;
                        if (hint < best) best = (int) hint;
                    }
            return best;
        }

        private long observationInventorySignature()
        {
            long signature = 1;
            for (InventoryID id : new InventoryID[]{InventoryID.INVENTORY, InventoryID.EQUIPMENT})
            {
                ItemContainer container = client.getItemContainer(id);
                if (container == null) { signature *= 31; continue; }
                for (Item item : container.getItems())
                    signature = 31 * signature + (item == null ? 0 : 31L * item.getId() + item.getQuantity());
            }
            return signature;
        }

        private boolean hasImmediateBankAccess()
        {
            if (Rs2Bank.isOpen())
            {
                return true;
            }

            WorldPoint player = Rs2Player.getWorldLocation();
            if (player == null)
            {
                return false;
            }
            if (net.runelite.client.plugins.microbot.droslayer.walker.support.EniolaBankAccess.nearby(player)
                && !net.runelite.client.plugins.microbot.droslayer.walker.support.EniolaBankAccess.canPay(false))
            {
                return false;
            }

            try
            {
                GameObject bank = SceneObjects.findBank(IMMEDIATE_BANK_RADIUS);
                if (bank != null && bank.getWorldLocation() != null
                    && bank.getWorldLocation().getPlane() == player.getPlane()
                    && bank.getWorldLocation().distanceTo2D(player) <= IMMEDIATE_BANK_RADIUS
                    && bankObjectReachable(bank))
                {
                    return true;
                }

                WallObject exchange = SceneObjects.findGrandExchangeBooth(IMMEDIATE_BANK_RADIUS);
                if (exchange != null && exchange.getWorldLocation() != null
                    && exchange.getWorldLocation().getPlane() == player.getPlane()
                    && exchange.getWorldLocation().distanceTo2D(player) <= IMMEDIATE_BANK_RADIUS
                    && bankObjectReachable(exchange))
                {
                    return true;
                }

                TileObject chest = findImmediateBankChest(player);
                if (chest != null)
                {
                    return true;
                }

                var banker = Rs2Npc.getBankerNPC();
                return banker != null && banker.getWorldLocation() != null
                    && banker.getWorldLocation().getPlane() == player.getPlane()
                    && banker.getWorldLocation().distanceTo2D(player) <= IMMEDIATE_BANK_RADIUS
                    && bankPointReachable(banker.getWorldLocation());
            }
            catch (RuntimeException ex)
            {
                debug("bank-coordinator immediate bank detection failed: {}", ex.getMessage());
                return false;
            }
        }

        private boolean hasReachableLocalApproach(WorldPoint target)
        {
            if (target == null || nearbyDestinationRecovery.exhausted(target)
                || (rawPlanningFailure() != PlanningFailure.ROUTE_UNSUPPORTED
                    && rawPlanningFailure() != PlanningFailure.TARGET_NOT_STANDABLE)) return false;
            WorldPoint player = Rs2Player.getWorldLocation();
            if (player == null || player.distanceTo(target) > 32) return false;
            return Rs2Tile.getReachableTilesFromTile(player, 64).keySet().stream()
                .anyMatch(point -> !point.equals(target) && point.distanceTo(target) <= 2);
        }

        boolean shouldCompareUnsupportedRoute(WorldPoint target)
        {
            return target != null && bankSuppliesAllowed()
                && (bankMirrorKnown() || !hasImmediateBankAccess())
                && (rawPlanningFailure() == PlanningFailure.ROUTE_UNSUPPORTED
                    || rawPlanningFailure() == PlanningFailure.SEARCH_LIMIT);
        }

        boolean shouldBootstrapUnsupportedTransportRoute(WorldPoint target)
        {
            // A carried-items-only preview can fail even though a banked teleport item,
            // charter fare, or fairy-ring staff makes the target reachable. Probe the bank
            // once and let Efficient Walker's own bank-aware comparison decide what wins.
            // Also queried while awaiting an asynchronous direct preview, when state is
            // WAITING_DIRECT_PREVIEW rather than IDLE.
            return config.useTeleportations() && bankSuppliesAllowed()
                && !hasCommittedTirannwnWalk() && target != null && hasImmediateBankAccess()
                && (rawPlanningFailure() == PlanningFailure.ROUTE_UNSUPPORTED
                    || rawPlanningFailure() == PlanningFailure.SEARCH_LIMIT);
        }

        void awaitDirectPreview(WorldPoint target)
        {
            if (!updateOnClientThread(() -> {
                pendingTarget = target;
                state = BankState.WAITING_DIRECT_PREVIEW;
                stateStartedTick = client.getTickCount();
                debug("bank-coordinator waiting for preview target={}", target);
                return true;
            })) throw new CancellationException("A superseded request cannot take over a preview");
        }

        private boolean carriedLandingComparison;

        void recompareCarriedRoute(WorldPoint target)
        {
            clear();
            carriedLandingComparison = true;
            invoke("previewRouteTo", new Class<?>[]{WorldPoint.class}, target);
            awaitDirectPreview(target);
            debug("rechecking carried travel methods after landing target={}", target);
        }

        boolean prepareApeAtollGreegree(WorldPoint target)
        {
            boolean accepted = startBankDetour(target);
            return accepted || state != BankState.IDLE;
        }

        boolean prepareCaveLight(WorldPoint target, CaveLightSource.Preparation preparation)
        {
            if (target == null) return false;
            caveLightSupplyPass = true;
            caveLightWorld = client.getWorld();
            caveLightUnlit = preparation.unlit;
            if (preparation.withdrawals.isEmpty() && !Rs2Bank.isOpen()) {
                invoke("cancel", NO_TYPES);
                pendingTarget = target;
                state = BankState.LIGHTING_CAVE;
                stateStartedTick = client.getTickCount();
                return true;
            }
            if (!bankSuppliesAllowed()) {
                stopCaveTravel("Carry a lit light or an unlit light and tinderbox for this cave route, or enable bank supplies.");
                return true;
            }
            return startBankDetour(target);
        }

        boolean startBankDetour(WorldPoint target)
        {
            return startBankDetour(target, false);
        }

        boolean startUnsupportedTransportDetour(WorldPoint target)
        {
            return startBankDetour(target, true);
        }

        private boolean startBankDetour(WorldPoint target, boolean probeFairyRing)
        {
            if (!updateOnClientThread(() -> {
                pendingTarget = target;
                closeForDirect = false;
                bankCloseClicked = bankCloseFallback = false;
                probeFairyRingPrerequisites = probeFairyRing;
                stateStartedTick = client.getTickCount();
                bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
                bankWasOpenAtStart = Rs2Bank.isOpen();
                pendingWithdrawals = Map.of();
                spaceDepositItem = null;
                pendingEquipment = new ArrayList<>();
                equipmentAttemptTick = -1;
                activeWithdrawal = null;
                transportSupplyPass = false;
                apeAtollSupplyPass = false;
                immediateBankOpenAttempts = 0;
                transportRequirementsChecked = false;
                selectedBankTeleportName = null;
                // Only state publication is atomic; bank discovery and graph refresh
                // below remain outside the ownership monitor.
                invoke("cancel", NO_TYPES);
                return true;
            })) return false;

            if (Rs2Bank.isOpen())
            {
                return updateOnClientThread(() -> {
                    state = BankState.WAITING_BANK_SNAPSHOT;
                    Microbot.status = "Reading bank teleport items.";
                    debug("bank-coordinator bank already open target={} epoch={}", target, bankEpochBeforeOpen);
                    return true;
                });
            }

            if (!hasImmediateBankAccess())
            {
                startProvenBankWalk();
                return true;
            }

            // First try the current scene.  Dro's bank helper recognizes bank chests,
            // booths and banker NPCs that the 1.11.2 bank opener can miss.
            return updateOnClientThread(() -> {
                submitOpenBank();
                state = BankState.PROBING_VISIBLE_BANK;
                Microbot.status = "Checking for a nearby bank.";
                debug("bank-coordinator probing visible bank target={}", target);
                return true;
            });
        }

        void holdPreparationBudget(boolean held)
        {
            int tick = client.getTickCount();
            if (held) { if (preparationHeldTick < 0) preparationHeldTick = tick; return; }
            if (preparationHeldTick < 0) return;
            int duration = Math.max(0, tick - preparationHeldTick);
            preparationHeldTick = -1;
            if (state == BankState.IDLE) return;
            stateStartedTick += duration;
            if (activeWithdrawal != null) {
                activeWithdrawalStartedTick += duration;
                if (activeWithdrawalCompletedTick >= 0) activeWithdrawalCompletedTick += duration;
            }
            if (equipmentAttemptTick >= 0) equipmentAttemptTick += duration;
            if (caveLightAttemptTick >= 0) caveLightAttemptTick += duration;
            if (spaceDepositItem != null) spaceDepositTick += duration;
            if (instanceEscapeLastAttemptTick >= 0) instanceEscapeLastAttemptTick += duration;
        }

        boolean beforeEngineTick()
        {
            recordPhaseTiming();
            switch (state)
            {
                case ESCAPING_INSTANCE:
                    return tickInstanceEscape();
                case PROBING_VISIBLE_BANK:
                    return tickVisibleBankProbe();
                case OPENING_BANK:
                    return tickOpeningBank();
                case WAITING_BANK_SNAPSHOT:
                    return tickWaitingForSnapshot();
                case DISCOVERING_COMPLETE_ROUTE:
                    return tickCompleteDiscovery();
                case ANALYZING_TRANSPORT_REQUIREMENTS:
                    return tickAnalyzingTransportRequirements();
                case WITHDRAWING:
                    return tickWithdrawing();
                case CLOSING_BANK:
                    return tickClosingBank();
                case LIGHTING_CAVE:
                    return tickLightingCave();
                default:
                    return false;
            }
        }

        void afterEngineTick()
        {
            recordPhaseTiming();
            switch (state)
            {
                case WAITING_DIRECT_PREVIEW:
                    finishInitialPreviewIfReady();
                    break;
                case ANALYZING_WEIGHTED_ROUTE:
                    finishWeightedRouteAnalysisIfReady();
                    break;
                case ANALYZING_FAIRY_RING_CANDIDATE:
                    finishFairyRingCandidateAnalysisIfReady();
                    break;
                case WALKING_TO_BANK:
                    finishBankWalkIfReady();
                    break;
                case WAITING_BANK_REPLAN:
                    finishBankReplanIfReady();
                    break;
                case WAITING_FINAL_PREVIEW:
                    finishFinalPreviewIfReady();
                    break;
                default:
                    break;
            }
        }

        void cancel()
        {
            cancelBankAction();
            cancelFairyRingCandidateAnalysis();
            cancelTransportRequirementAnalysis();
            cancelWeightedRouteAnalysis();
            clear();
        }

        synchronized void shutdown()
        {
            if (observation != null) observation.cancel(true);
            if (observationExecutor != null) observationExecutor.shutdownNow();
            observationExecutor = null;
            cancelBankAction();
            cancelFairyRingCandidateAnalysis();
            cancelTransportRequirementAnalysis();
            cancelWeightedRouteAnalysis();
            if (bankExecutor != null)
            {
                bankExecutor.shutdownNow();
                bankExecutor = null;
            }
            clear();
        }

        private void finishInitialPreviewIfReady()
        {
            if (routeSubmission.isPending()) return;
            // Read the engine directly here. getStatus() intentionally masks a BLOCKED
            // instanced preview as PREVIEW long enough for this recovery hook to run.
            Status status = engineStatus();
            if (client.getTickCount() <= stateStartedTick) return;
            if (status == Status.PLANNING)
            {
                if (client.getTickCount() - stateStartedTick >= FINAL_PREVIEW_TIMEOUT_TICKS)
                    fallbackToDirect("initial preview timed out");
                return;
            }

            WorldPoint target = pendingTarget;
            if (target == null || !target.equals(invokeEngine("getDestination", NO_TYPES)))
            {
                // A plan-unavailable block clears the engine destination; nothing more will arrive,
                // so fall back now instead of idling out the full preview timeout.
                if (status == Status.BLOCKED
                        || client.getTickCount() - stateStartedTick >= FINAL_PREVIEW_TIMEOUT_TICKS)
                    fallbackToDirect("initial preview unavailable");
                return;
            }
            if (carriedLandingComparison && target != null)
            {
                if (status == Status.PREVIEW || status == Status.BLOCKED)
                    beginWeightedRouteAnalysis(target);
                else fallbackToDirect("landing preview unavailable");
                return;
            }
            if (status == Status.BLOCKED && target != null && isInInstancedScene())
            {
                if (beginInstanceEscape(target))
                {
                    return;
                }
                debug("bank-coordinator instanced preview blocked and no carried escape teleport was usable target={}", target);
                clear();
                return;
            }
            // SEARCH_LIMIT from an isolated ordinary port must not repeat the same
            // search forever. One available, verified teleport can relocate us to a
            // connected area, then the original destination is planned again.
            if (status == Status.BLOCKED && target != null
                && (rawPlanningFailure() == PlanningFailure.SEARCH_LIMIT
                    || rawPlanningFailure() == PlanningFailure.ROUTE_UNSUPPORTED)
                && beginIsolatedRouteEscape(target))
            {
                return;
            }

            // Nearby object tiles may be blocked while adjacent floor is reachable.
            // Release the coordinator so bounded nearby recovery runs before bank analysis.
            if (status == Status.BLOCKED && hasReachableLocalApproach(target))
            {
                debug("local approach available; trying nearby recovery before bank analysis target={}", target);
                clear();
                return;
            }

            // A known mirror describes supplies, not an executable carried-only route.
            // Prepare at the reachable bank before another comparison can exhaust its budget.
            if (status == Status.BLOCKED && shouldBootstrapUnsupportedTransportRoute(target))
            {
                startBankDetour(target, true);
                return;
            }

            if (status == Status.BLOCKED && shouldCompareUnsupportedRoute(target))
            {
                beginWeightedRouteAnalysis(target);
                return;
            }

            if (status != Status.PREVIEW || target == null || !target.equals(getDestination()))
            {
                clear();
                return;
            }

            int routeCost = currentPreviewCost();
            // Compare a bank-funded minecart trip before opening a known bank. Its approach
            // belongs in the cost comparison, not in an unconditional fare-withdrawal detour.
            if (!Objects.equals(weightedAnalyzedTarget, target) && bankMirrorKnown()
                && routeCost >= MIN_ROUTE_COST_FOR_MINECART_BANK_PROBE
                && (networkTransportSupport.hasBankBackedMinecartCandidate(target)
                    || networkTransportSupport.hasBankBackedAlKharidTollCandidate(target)))
            {
                beginWeightedRouteAnalysis(target);
                return;
            }
            // A visible bank is cheap to inspect. Do this before an optional global bank
            // comparison, but preserve short walks and immediately usable teleport routes.
            if (!Objects.equals(weightedAnalyzedTarget, target)
                && hasImmediateBankAccess() && shouldInspectNearbyBank(routeCost,
                networkTransportSupport.activePreviewUsesMinigame(),
                WildernessTravelPolicy.containsWilderness(getRemainingRoute())))
            {
                // Cached contents do not replace inspecting an immediately available bank.
                // Open it before comparing long routes so the bank-aware preview can actually
                // select and withdraw travel supplies, even if the optional advisor times out.
                startBankDetour(target);
                return;
            }

            // Compare the entire trip as one weighted chain before committing the preview.  The
            // v1.11.2 graph prices every transport at four tiles, while Dro's current
            // typed planner exposes real durations and exact bank prerequisites.  We use that only
            // as an advisor; Efficient Walker still performs every replan and every interaction.
            if (shouldAnalyzeWeightedRoute(target, routeCost))
            {
                beginWeightedRouteAnalysis(target);
                return;
            }

            continueInitialPreviewDecision(target, routeCost);
        }

        private boolean shouldAnalyzeWeightedRoute(WorldPoint target, int routeCost)
        {
            if (target == null || (routeCost < MIN_ROUTE_COST_FOR_WEIGHTED_ANALYSIS
                && !networkTransportSupport.activePreviewUsesMaxPoh()))
            {
                return false;
            }
            return weightedAnalyzedTarget == null || !weightedAnalyzedTarget.equals(target);
        }

        private void beginWeightedRouteAnalysis(WorldPoint target)
        {
            cancelWeightedRouteAnalysis();
            WorldPoint start = Rs2Player.getWorldLocation();
            if (start == null)
            {
                continueInitialPreviewDecision(target, currentPreviewCost());
                return;
            }
            boolean directUnavailable = engineStatus() == Status.BLOCKED
                && (rawPlanningFailure() == PlanningFailure.ROUTE_UNSUPPORTED
                    || rawPlanningFailure() == PlanningFailure.SEARCH_LIMIT);
            boolean allowBank = !carriedLandingComparison && bankSuppliesAllowed()
                && (bankMirrorKnown() || directUnavailable)
                && !(MaxPohSupport.enabled() && !directUnavailable
                    && networkTransportSupport.activePreviewUsesMaxPoh());
            List<WeightedRouteAdvisor.BankedTeleportOption> bankedTeleportOptions = allowBank
                ? snapshotBankedTeleportOptions(target)
                : List.of();

            WorldPoint nearbyBank = hasImmediateBankAccess() ? start : null;
            weightedInventorySignature = observationInventorySignature();
            weightedWorld = client.getWorld();
            state = BankState.ANALYZING_WEIGHTED_ROUTE;
            stateStartedTick = client.getTickCount();
            weightedRouteAnalysis = bankExecutor().submit(() ->
                weightedRouteAdvisor.analyze(start, target, allowBank, bankedTeleportOptions, directUnavailable, nearbyBank));
            Microbot.status = "Comparing complete travel routes.";
            debug("weighted-route analysis started start={} target={} bankedTeleportCandidates={} economics=ignored",
                start, target, bankedTeleportOptions.size());
        }

        private void resumeAfterWeightedAnalysis(WorldPoint target) {
            if (carriedLandingComparison) {
                state = BankState.WAITING_FINAL_PREVIEW;
                stateStartedTick = client.getTickCount();
                return;
            }
            if (Rs2Bank.isOpen()) {
                bankWasOpenAtStart = true;
                state = BankState.WAITING_BANK_SNAPSHOT;
                stateStartedTick = client.getTickCount();
            } else {
                continueInitialPreviewDecision(target, currentPreviewCost());
            }
        }

        private void finishWeightedRouteAnalysisIfReady()
        {
            Future<WeightedRouteAdvisor.Advice> future = weightedRouteAnalysis;
            WorldPoint target = pendingTarget;
            if (target == null)
            {
                cancelWeightedRouteAnalysis();
                clear();
                return;
            }
            if (future == null)
            {
                weightedAnalyzedTarget = target;
                resumeAfterWeightedAnalysis(target);
                return;
            }
            if (!future.isDone())
            {
                if (client.getTickCount() - stateStartedTick < WEIGHTED_ROUTE_ANALYSIS_TIMEOUT_TICKS)
                {
                    return;
                }
                cancelWeightedRouteAnalysis();
                weightedAnalyzedTarget = target;
                debug("weighted-route analysis timed out target={}", target);
                resumeAfterWeightedAnalysis(target);
                return;
            }

            WeightedRouteAdvisor.Advice advice = null;
            try
            {
                advice = future.get();
            }
            catch (Exception ex)
            {
                debug("weighted-route analysis failed target={} error={}", target, ex.getMessage());
            }
            weightedRouteAnalysis = null;
            weightedAnalyzedTarget = target;
            if (weightedWorld != client.getWorld() || weightedInventorySignature != observationInventorySignature()) {
                debug("complete-route discarded comparison after inventory/equipment/world changed");
                advice = null;
            }
            weightedAdvice = advice;
            if (Rs2Bank.isOpen()) {
                resumeAfterWeightedAnalysis(target);
                return;
            }

            if (advice != null && advice.available())
            {
                debug("weighted-route target={} direct={} bank={} preferred={} chain={} explicitBankTeleport={} explicitCost={} economics=ignored",
                    target,
                    advice.directCost,
                    advice.bankCost >= Integer.MAX_VALUE / 4 ? "n/a" : advice.bankCost,
                    advice.bankPreferred ? "bank" : "direct",
                    advice.bestChain,
                    advice.explicitBankedTeleport == null ? "none" : advice.explicitBankedTeleport.name,
                    advice.explicitBankedTeleportCost >= Integer.MAX_VALUE / 4
                        ? "n/a" : advice.explicitBankedTeleportCost);

                if (!advice.bankPreferred && advice.directChoice != null) {
                    networkTransportSupport.preferCompleteRoute(target, advice.directChoice);
                    refreshTransportExtensions(target);
                    if (networkTransportSupport.completeRouteReady()
                        && (Boolean) invoke("previewRouteTo", new Class<?>[]{WorldPoint.class}, target)) {
                        state = BankState.WAITING_FINAL_PREVIEW;
                        stateStartedTick = client.getTickCount();
                        debug("complete-route carried winner estimatedSeconds={} chain={}",
                            RouteTimeCost.seconds(advice.directChoice.cost), advice.directChain);
                        return;
                    }
                    networkTransportSupport.clearCompleteRoute();
                    refreshTransportExtensions(target);
                }

                // If the four-cost graph selected a Grouping teleport but the weighted
                // advisor proves that the best complete route does not use one, rebuild once with
                // Grouping edges suppressed for this target.  The replacement can be any chain in
                // the maintained transport graph: item/spell teleport, Spirit Tree, fairy ring,
                // Kourend minecart, charter/ship, mushtree, etc.
                boolean weightedWinnerUsesMinigame = advice.bankPreferred
                    ? advice.bankUsesMinigame : advice.directUsesMinigame;
                if (networkTransportSupport.activePreviewUsesMinigame()
                    && !weightedWinnerUsesMinigame)
                {
                    networkTransportSupport.suppressMinigamesForTarget(
                        target, "weighted complete route is faster");

                    // For a direct winner, immediately rebuild the same preview without Grouping.
                    // For a bank winner, the suppression is reinstated again after the bank snapshot
                    // because the intermediate bank walk temporarily refreshes a different target.
                    if (!advice.bankPreferred)
                    {
                        refreshTransportExtensions(target);
                        boolean previewStarted = (Boolean) invoke(
                            "previewRouteTo", new Class<?>[]{WorldPoint.class}, target);
                        if (previewStarted)
                        {
                            state = BankState.WAITING_DIRECT_PREVIEW;
                            stateStartedTick = client.getTickCount();
                            debug("weighted-route replanning without minigame target={} cost={} chain={}",
                                target, advice.directCost, advice.directChain);
                            return;
                        }
                    }
                }

                if (advice.bankPreferred && bankSuppliesAllowed())
                {
                    debug("weighted-route prefers bank target={} direct={} bank={} chain={}",
                        target, advice.directCost, advice.bankCost, advice.bankChain);
                    startBankDetour(target, advice.bankUsesFairyRing);
                    return;
                }
            }

            resumeAfterWeightedAnalysis(target);
        }

        private void cancelWeightedRouteAnalysis()
        {
            if (weightedRouteAnalysis != null)
            {
                weightedRouteAnalysis.cancel(true);
                weightedRouteAnalysis = null;
            }
        }

        private void continueInitialPreviewDecision(WorldPoint target, int routeCost)
        {
            if (engineStatus() != Status.PREVIEW)
            {
                clear();
                return;
            }
            // The weighted comparison includes the journey to the bank. A separate
            // fairy-ring-only probe cannot establish that the complete trip is faster.
            finishInitialPreviewDecision(target, routeCost);
        }

        private boolean beginInstanceEscape(WorldPoint target)
        {
            return isInInstancedScene() && beginTeleportEscape(target, false);
        }

        private boolean beginIsolatedRouteEscape(WorldPoint target)
        {
            if (isInInstancedScene() || !config.useTeleportations()
                || Rs2Player.isInCombat() || Rs2Player.isTeleBlocked()) return false;
            WorldPoint start = Rs2Player.getWorldLocation();
            if (start == null || net.runelite.client.plugins.microbot.util.player.Rs2Pvp.getWildernessLevelFrom(start) > 0
                || (Objects.equals(start,lastIsolatedEscapeStart)
                    && Objects.equals(target,lastIsolatedEscapeTarget))
                || (Objects.equals(target,lastIsolatedEscapeTarget) && isolatedEscapeCount >= 2)
                || networkTransportSupport.hasRecoverableBlockedShipDeck()) return false;
            if (!beginTeleportEscape(target, true)) return false;
            isolatedEscapeCount = Objects.equals(target,lastIsolatedEscapeTarget) ? isolatedEscapeCount+1 : 1;
            lastIsolatedEscapeStart = start;
            lastIsolatedEscapeTarget = target;
            return true;
        }

        private boolean beginTeleportEscape(WorldPoint target, boolean isolated)
        {
            if (target == null || !config.useTeleportations()) return false;
            WorldPoint start = Rs2Player.getWorldLocation();
            if (start == null || Rs2Player.isTeleBlocked())
            {
                return false;
            }

            TeleportProviders.TeleportProvider bestProvider = null;
            WorldPoint bestLanding = null;
            int bestScore = Integer.MAX_VALUE;

            // Reuse the exact provider objects EfficientWalker already trusts for spells,
            // tablets and jewellery. Their availability check already verifies runes, items,
            // equipment, quests and teleblock state; we only rank their landing tiles here.
            for (TeleportProviders.TeleportProvider provider : TeleportProviders.createProviders())
            {
                try
                {
                    WorldPoint landing = provider.getDestination();
                    if (landing == null || (isolated && landing.getPlane() == start.getPlane()
                        && landing.distanceTo2D(start) <= 16)
                        || !provider.isAvailableForRoute(start, target))
                    {
                        continue;
                    }

                    int score = instanceEscapeScore(landing, target);
                    if (score < bestScore)
                    {
                        bestProvider = provider;
                        bestLanding = landing;
                        bestScore = score;
                    }
                }
                catch (RuntimeException ex)
                {
                    debug("instance escape provider probe failed: {}", ex.getMessage());
                }
            }

            if (bestProvider == null)
            {
                return false;
            }

            // The preview is already terminal inside the engine. Cancel only that
            // dead plan; pendingTarget remains owned by this coordinator until the teleport
            // lands and the original request is replanned from the overworld.
            invoke("cancel", NO_TYPES);
            instanceEscapeProvider = bestProvider;
            instanceEscapeLanding = bestLanding;
            instanceEscapeName = bestProvider.name;
            activeEscapeStart = start;
            isolatedTeleportEscape = isolated;
            instanceEscapeAttempts = 0;
            instanceEscapeLastAttemptTick = -1;
            state = BankState.ESCAPING_INSTANCE;
            stateStartedTick = client.getTickCount();
            Microbot.status = isolated ? "Recovering stalled route with carried teleport: " + instanceEscapeName
                : "Teleporting out of the instance: " + instanceEscapeName;
            debug("bank-coordinator {} route fallback target={} player={} teleport={} landing={} score={}",
                isolated ? "isolated" : "instanced", target, start, instanceEscapeName, instanceEscapeLanding, bestScore);
            return true;
        }

        private boolean tickInstanceEscape()
        {
            if (housePortalEscape) return tickHousePortalRecovery();
            WorldPoint target = pendingTarget;
            if (target == null)
            {
                clear();
                return true;
            }

            // A world escape must observe a real teleport landing, not the fact that
            // it was outside an instance even before the first click.
            WorldPoint observed = Rs2Player.getWorldLocation();
            boolean landed = isolatedTeleportEscape
                ? observed != null && activeEscapeStart != null
                    && (observed.getPlane() != activeEscapeStart.getPlane()
                        || observed.distanceTo2D(activeEscapeStart) > 16)
                    && instanceEscapeLanding != null
                    && observed.getPlane() == instanceEscapeLanding.getPlane()
                    && observed.distanceTo2D(instanceEscapeLanding) <= 16
                : !isInInstancedScene();
            if (landed)
            {
                String used = instanceEscapeName;
                WorldPoint landing = observed;
                clear();
                debug("bank-coordinator instance escape landed via {} at {}; replanning original target={}",
                    used, landing, target);
                EfficientWalker.this.walkToWithCurrentTransportPolicy(target);
                return true;
            }

            int tick = client.getTickCount();
            if (tick - stateStartedTick > 24)
            {
                debug("bank-coordinator instance escape timed out teleport={} target={}",
                    instanceEscapeName, target);
                clear();
                return true;
            }

            // One retry is enough for an ignored menu action without turning a blocked instance
            // into another spam-click loop. Wait several game ticks before retrying.
            boolean shouldAttempt = instanceEscapeAttempts == 0
                || (instanceEscapeAttempts == 1 && tick - instanceEscapeLastAttemptTick >= 7);
            if (!shouldAttempt)
            {
                return true;
            }

            TeleportProviders.TeleportProvider provider = instanceEscapeProvider;
            if (provider == null)
            {
                clear();
                return true;
            }

            WorldPoint from = Rs2Player.getWorldLocation();
            boolean clicked = false;
            try
            {
                clicked = provider.activate(from, target);
            }
            catch (RuntimeException ex)
            {
                debug("bank-coordinator instance escape interaction failed teleport={} error={}",
                    instanceEscapeName, ex.getMessage());
            }

            instanceEscapeAttempts++;
            instanceEscapeLastAttemptTick = tick;
            Microbot.status = (isolatedTeleportEscape ? "Recovering stranded route: " : "Teleporting out of instance: ") + instanceEscapeName;
            debug("bank-coordinator teleport escape attempt={} clicked={} teleport={} landing={} target={}",
                instanceEscapeAttempts, clicked, instanceEscapeName, instanceEscapeLanding, target);
            return true;
        }

        private boolean tickHousePortalRecovery() {
            if (housePortalPreview) return true;
            WorldPoint target = pendingTarget;
            int tick = client.getTickCount();
            int elapsed = tick - stateStartedTick;
            if (!MaxPohSupport.enabled() || housePortalWorld != client.getWorld()) {
                debug("house portal recovery lost its configuration/world target={} world={} expectedWorld={}",
                    target, client.getWorld(), housePortalWorld);
                clear();
                doorActionBlocked = true;
                return true;
            }
            WorldPoint actual = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
            WorldPoint cached = Rs2Player.getWorldLocation();
            boolean outside = client.getGameState() == GameState.LOGGED_IN && !MaxPohSupport.insideHouse()
                && !isInInstancedScene();
            // A late exit may land after the original 50-tick deadline. Observe the
            // authoritative client scene BEFORE failing or cancelling the input worker.
            if (outside && actual != null) {
                boolean cacheAgree = cached != null && cached.getPlane() == actual.getPlane()
                    && cached.distanceTo2D(actual) <= 3;
                outsideHouseObservations++;
                if (cacheAgree || outsideHouseObservations >= 2) {
                    clear();
                    debug("house portal exit observed player={} expectedOutside={} cacheAgree={}; resuming original request={}",
                        actual, instanceEscapeLanding, cacheAgree, target);
                    EfficientWalker.this.walkToWithCurrentTransportPolicy(target);
                    return true;
                }
            } else outsideHouseObservations = 0;
            boolean inputPending = bankAction != null && !bankActionDone();
            if (HousePortalRecoveryPolicy.expired(elapsed, inputPending)) {
                debug("house portal recovery exhausted elapsedTicks={} attempts={} actionPending={} inside={} player={} target={}",
                    elapsed, instanceEscapeAttempts, inputPending, MaxPohSupport.insideHouse(), actual, target);
                clear();
                doorActionBlocked = true;
                Microbot.status = "Could not confirm leaving the house. Exit through your portal, then retry.";
                return true;
            }
            if (bankAction != null) {
                if (inputPending) return true;
                boolean clicked = consumeBankActionResult();
                debug("house portal exit action completed clicked={} attempt={} elapsedTicks={} inside={}",
                    clicked, instanceEscapeAttempts, elapsed, MaxPohSupport.insideHouse());
            }
            if (outside || client.getGameState() != GameState.LOGGED_IN) return true;
            if (HousePortalRecoveryPolicy.shouldAttempt(instanceEscapeAttempts,
                tick - instanceEscapeLastAttemptTick)) {
                instanceEscapeAttempts++;
                instanceEscapeLastAttemptTick = tick;
                debug("house portal exit attempt={} elapsedTicks={} target={}",
                    instanceEscapeAttempts, elapsed, target);
                bankAction = bankExecutor().submit(MaxPohSupport::leaveHouse);
            }
            return true;
        }

        private int instanceEscapeScore(WorldPoint landing, WorldPoint target)
        {
            if (landing == null || target == null)
            {
                return Integer.MAX_VALUE;
            }
            int distance = landing.distanceTo2D(target);
            // Prefer a landing on the target's plane. A different-plane teleport can still be
            // used as a last resort, but ordinary overworld landings should always beat it.
            return landing.getPlane() == target.getPlane()
                ? distance
                : 100_000 + distance;
        }

        private boolean isInInstancedScene()
        {
            try
            {
                return client.getTopLevelWorldView() != null
                    && client.getTopLevelWorldView().getScene() != null
                    && client.getTopLevelWorldView().getScene().isInstance();
            }
            catch (RuntimeException ex)
            {
                return false;
            }
        }

        private void finishInitialPreviewDecision(WorldPoint target, int routeCost)
        {
            if (bankPlanSelected())
            {
                debug("bank-coordinator preserved planner selected bank detour target={} summary={}",
                    target, bankPlanSummary());
                startBankDetour(target);
                return;
            }

            // A known bank winner was handled by the weighted comparison. Unknown
            // contents, a possible charter, or a staff alone do not justify a detour.
            clear();
            debug("bank-coordinator committing direct preview cost={} target={}", routeCost, target);
            // This is the same preview that was just calculated; do not rebuild the graph here.
            invoke("commitPreviewOrStartRoute", new Class<?>[]{WorldPoint.class}, target);
        }

        private void finishFairyRingCandidateAnalysisIfReady()
        {
            Future<Boolean> analysis = fairyRingCandidateAnalysis;
            WorldPoint target = pendingTarget;
            if (analysis == null || target == null)
            {
                cancelFairyRingCandidateAnalysis();
                if (target != null && engineStatus() == Status.PREVIEW && target.equals(getDestination()))
                {
                    finishInitialPreviewDecision(target, currentPreviewCost());
                }
                else
                {
                    clear();
                }
                return;
            }

            if (!analysis.isDone())
            {
                if (client.getTickCount() - stateStartedTick < FAIRY_CANDIDATE_ANALYSIS_TIMEOUT_TICKS)
                {
                    return;
                }
                debug("bank-coordinator fairy-ring candidate analysis timed out target={}", target);
                cancelFairyRingCandidateAnalysis();
            }

            boolean usesFairyRing = consumeFairyRingCandidateAnalysis();
            if (engineStatus() != Status.PREVIEW || !target.equals(getDestination()))
            {
                clear();
                return;
            }

            if (usesFairyRing)
            {
                debug("bank-coordinator banked fairy-ring candidate target={} -> probe bank for staff", target);
                startBankDetour(target, true);
                return;
            }

            finishInitialPreviewDecision(target, currentPreviewCost());
        }

        private boolean consumeFairyRingCandidateAnalysis()
        {
            Future<Boolean> analysis = fairyRingCandidateAnalysis;
            fairyRingCandidateAnalysis = null;
            if (analysis == null)
            {
                return false;
            }
            try
            {
                return Boolean.TRUE.equals(analysis.get());
            }
            catch (Exception ex)
            {
                debug("bank-coordinator fairy-ring candidate analysis failed: {}", ex.getMessage());
                return false;
            }
        }

        private void cancelFairyRingCandidateAnalysis()
        {
            if (fairyRingCandidateAnalysis != null)
            {
                fairyRingCandidateAnalysis.cancel(true);
                fairyRingCandidateAnalysis = null;
            }
        }

        private boolean tickVisibleBankProbe()
        {
            if (Rs2Bank.isOpen())
            {
                cancelBankAction();
                state = BankState.WAITING_BANK_SNAPSHOT;
                stateStartedTick = client.getTickCount();
                return tickWaitingForSnapshot();
            }
            if (!bankActionDone())
            {
                if (client.getTickCount() - stateStartedTick < ASYNC_BANK_ACTION_TIMEOUT_TICKS)
                {
                    return true;
                }
                cancelBankAction();
            }

            if (consumeBankActionResult())
            {
                bankEpochBeforeOpen = Math.max(0, Rs2Bank.getBankLiveEpoch() - 1);
                state = BankState.WAITING_BANK_SNAPSHOT;
                stateStartedTick = client.getTickCount();
                Microbot.status = "Reading bank teleport items.";
                debug("bank-coordinator nearby bank opened target={} epoch={}",
                    pendingTarget, Rs2Bank.getBankLiveEpoch());
                return true;
            }

            // One missed click must not make a nearby bank disappear from route selection.
            // Reacquire the immediate scene bank a couple of times before considering any
            // non-bank fallback. This applies uniformly to chests, booths, and bankers.
            if (hasImmediateBankAccess() && immediateBankOpenAttempts < 2)
            {
                immediateBankOpenAttempts++;
                bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
                submitOpenBank();
                stateStartedTick = client.getTickCount();
                Microbot.status = "Opening nearby bank.";
                debug("bank-coordinator retrying immediate bank attempt={} target={}",
                    immediateBankOpenAttempts, pendingTarget);
                return true;
            }

            return startProvenBankWalk();
        }

        private boolean startProvenBankWalk()
        {
            requestOwnership.check();
            // Execute the bank leg that was actually priced by the advisor. A failed
            // immediate-bank probe must never turn into an unpriced trip to another bank.
            WorldPoint bank = weightedAdvice != null && weightedAdvice.bankPreferred
                ? weightedAdvice.bankPoint : null;
            if (bank == null && caveLightSupplyPass) {
                if (EfficientWalker.this.walkToNearestBank()) {
                    return updateOnClientThread(() -> {
                        state = BankState.WALKING_TO_BANK;
                        stateStartedTick = client.getTickCount();
                        Microbot.status = "Walking to the bank for a light required by the selected cave route.";
                        return true;
                    });
                }
                stopCaveTravel("No reachable bank to prepare a light for the selected cave route.");
                return true;
            }
            if (bank == null && missingApeAtollSupplies(pendingTarget)) {
                if (EfficientWalker.this.walkToNearestBank()) {
                    return updateOnClientThread(() -> {
                        state = BankState.WALKING_TO_BANK;
                        stateStartedTick = client.getTickCount();
                        Microbot.status = "Walking to bank for an owned monkey greegree.";
                        return true;
                    });
                }
                stopApeAtollTravel("No reachable bank to prepare a monkey greegree for Ape Atoll.");
                return true;
            }
            if (bank == null)
            {
                fallbackToDirect("no proven faster bank route");
                return true;
            }
            refreshTransportExtensions(bank);
            boolean started = (Boolean) invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, bank);
            if (started)
            {
                if (!updateOnClientThread(() -> {
                    state = BankState.WALKING_TO_BANK;
                    stateStartedTick = client.getTickCount();
                    Microbot.status = "Walking to the bank selected for the faster route.";
                    return true;
                })) throw new CancellationException("Bank walk was replaced before its handoff");
                return false;
            }
            fallbackToDirect("selected bank route unavailable");
            return true;
        }

        private void finishBankWalkIfReady()
        {
            Status status = engineStatus();
            if (status == Status.WALKING || status == Status.PLANNING)
            {
                if (client.getTickCount() - stateStartedTick > (caveLightSupplyPass || missingApeAtollSupplies(pendingTarget) ? 300 : BANK_ROUTE_TIMEOUT_TICKS))
                {
                    fallbackToDirect("nearest-bank route timed out");
                }
                return;
            }

            if (status == Status.ARRIVED)
            {
                bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
                submitOpenBank();
                state = BankState.OPENING_BANK;
                stateStartedTick = client.getTickCount();
                Microbot.status = "Opening nearby bank.";
                debug("bank-coordinator arrived at bank target={} epoch={}",
                    pendingTarget, bankEpochBeforeOpen);
                return;
            }

            if (status == Status.BLOCKED)
            {
                // Even if the exact bank tile was awkward, a chest/booth may already be
                // in scene and interactable.  Try it once before abandoning the bank route.
                submitOpenBank();
                state = BankState.OPENING_BANK;
                stateStartedTick = client.getTickCount();
                Microbot.status = "Trying the nearby bank.";
                debug("bank-coordinator bank route blocked; probing nearby bank target={}", pendingTarget);
            }
        }

        private boolean tickOpeningBank()
        {
            if (Rs2Bank.isOpen())
            {
                cancelBankAction();
                state = BankState.WAITING_BANK_SNAPSHOT;
                stateStartedTick = client.getTickCount();
                return tickWaitingForSnapshot();
            }
            if (!bankActionDone())
            {
                if (client.getTickCount() - stateStartedTick < ASYNC_BANK_ACTION_TIMEOUT_TICKS)
                {
                    return true;
                }
                cancelBankAction();
            }

            if (consumeBankActionResult() || Rs2Bank.isOpen())
            {
                state = BankState.WAITING_BANK_SNAPSHOT;
                stateStartedTick = client.getTickCount();
                Microbot.status = "Reading bank teleport items.";
                debug("bank-coordinator bank opened target={} epoch={}",
                    pendingTarget, Rs2Bank.getBankLiveEpoch());
                return true;
            }

            fallbackToDirect("nearby bank could not be opened");
            return true;
        }

        private boolean tickWaitingForSnapshot()
        {
            if (!Rs2Bank.isOpen())
            {
                submitOpenBank();
                state = BankState.OPENING_BANK;
                stateStartedTick = client.getTickCount();
                return true;
            }

            int epochNow = Rs2Bank.getBankLiveEpoch();
            boolean liveSnapshot = epochNow > bankEpochBeforeOpen
                || (bankWasOpenAtStart && epochNow > 0);

            if (liveSnapshot)
            {
                currentBankCounts();
                WorldPoint target = pendingTarget;
                cancelBankAction();
                if (caveLightSupplyPass) {
                    CaveLightSource.Preparation preparation = CaveLightSource.prepare(true);
                    if (client.getWorld() != caveLightWorld || preparation == null) {
                        stopCaveTravel("The cave light is unavailable in the live bank; travel stopped.");
                        return true;
                    }
                    caveLightUnlit = preparation.unlit;
                    pendingWithdrawals = new LinkedHashMap<>(preparation.withdrawals);
                    activeWithdrawal = null;
                    state = BankState.WITHDRAWING;
                    stateStartedTick = client.getTickCount();
                    Microbot.status = "Preparing an owned light for the selected cave route.";
                    return true;
                }
                if (requiresApeAtollProtection(target)) {
                    LinkedHashMap<Integer, Integer> supplies = new LinkedHashMap<>();
                    if (carriedGreegree() == 0) {
                        int id = ApeAtollGreegree.first(item -> Rs2Bank.count(item) > 0);
                        if (id == 0) {
                            stopApeAtollTravel("No monkey greegree in inventory, equipment or bank; Ape Atoll travel before MM2 stopped.");
                            return true;
                        }
                        supplies.put(id, 1);
                    }
                    if (useApeAtollTabletFlow(target) && !ApeAtollGreegree.onIsland(Rs2Player.getWorldLocation()) && !Rs2Inventory.hasItem(ApeAtollGreegree.TABLET)) {
                        if (Rs2Bank.count(ApeAtollGreegree.TABLET) <= 0) {
                            stopApeAtollTravel("No Ape Atoll tablet in inventory or bank; pre-MM2 travel stopped."); return true;
                        }
                        supplies.put(ApeAtollGreegree.TABLET, 1);
                    }
                    pendingWithdrawals = supplies;
                    activeWithdrawal = null;
                    transportSupplyPass = false; apeAtollSupplyPass = true;
                    state = BankState.WITHDRAWING;
                    stateStartedTick = client.getTickCount();
                    Microbot.status = "Withdrawing an owned monkey greegree for Ape Atoll.";
                    return true;
                }
                debug("bank-coordinator snapshot ready epoch={} items={} target={}",
                    epochNow, Rs2Bank.bankItems().size(), target);

                // The executing planner owns banked teleport selection. The optional
                // weighted advisor may justify reaching a bank, but must not authorize a
                // tablet withdrawal that the executing planner then ignores.
                beginCompleteDiscovery(target);
                return true;
            }

            if (client.getTickCount() - stateStartedTick >= BANK_SNAPSHOT_TIMEOUT_TICKS)
            {
                beginCloseForDirect("bank snapshot timed out");
            }
            return true;
        }

        private void beginTransportRequirementAnalysis(WorldPoint target, boolean includeFairyRing)
        {
            transportRequirementsChecked = true;
            cancelTransportRequirementAnalysis();
            WorldPoint bankStart = Rs2Player.getWorldLocation();
            List<Rs2TransportEdge> selectedEdges = weightedAdvice == null
                ? List.of() : weightedAdvice.bankTransportEdges;
            java.util.concurrent.atomic.AtomicReference<BankTransportRequirements> fallback =
                new java.util.concurrent.atomic.AtomicReference<>();
            supplyFallback = fallback;
            state = BankState.ANALYZING_TRANSPORT_REQUIREMENTS;
            stateStartedTick = client.getTickCount();
            Microbot.status = "Comparing fastest banked travel supplies.";
            transportRequirementAnalysis = bankExecutor().submit(() ->
                bankBackedTransportRequirements(target, includeFairyRing, bankStart, selectedEdges, fallback));
            debug("bank-coordinator checking travel supplies at current bank target={}", target);
        }

        private boolean tickAnalyzingTransportRequirements()
        {
            Future<BankTransportRequirements> analysis = transportRequirementAnalysis;
            if (analysis == null)
            {
                beginPreservedBankReplan();
                return true;
            }

            BankTransportRequirements requirements;
            if (!analysis.isDone())
            {
                if (client.getTickCount() - stateStartedTick < TRANSPORT_REQUIREMENT_ANALYSIS_TIMEOUT_TICKS)
                {
                    return true;
                }
                cancelTransportRequirementAnalysis();
                debug("bank-coordinator bank-backed transport analysis timed out target={}", pendingTarget);
                requirements = supplyFallback.get();
            }
            else
            {
                requirements = consumeTransportRequirementAnalysis();
                if (requirements == null) requirements = supplyFallback.get();
            }
            if (requirements != null && (!requirements.withdrawals.isEmpty()
                || !requirements.equipment.isEmpty()))
            {
                pendingEquipment = new ArrayList<>(requirements.equipment);
                equipmentAttemptTick = -1;
                pendingWithdrawals = new LinkedHashMap<>(requirements.withdrawals);
                activeWithdrawal = null;
                // A weighted teleport has already won the route comparison. After withdrawal,
                // close the bank and let the carried provider enter the ordinary final preview.
                // Other supplies (coins/staff) still require the existing bank-aware replan.
                transportSupplyPass = !requirements.weightedTeleport;
                state = BankState.WITHDRAWING;
                stateStartedTick = client.getTickCount();
                Microbot.status = "Withdrawing travel supplies: " + requirements.summary;
                debug("bank-coordinator bank-backed transport supplies target={} summary={} withdrawals={} weightedTeleport={}",
                    pendingTarget, requirements.summary, pendingWithdrawals, requirements.weightedTeleport);
                return true;
            }

            beginPreservedBankReplan();
            return true;
        }

        private void beginPreservedBankReplan()
        {
            WorldPoint target = pendingTarget;
            if (target == null)
            {
                beginCloseForDirect("bank target was cleared");
                return;
            }

            refreshTransportExtensions(target);
            boolean previewStarted = (Boolean) invoke(
                "previewRouteTo", new Class<?>[]{WorldPoint.class}, target);
            if (!previewStarted)
            {
                if (!transportRequirementsChecked)
                {
                    beginTransportRequirementAnalysis(target, probeFairyRingPrerequisites);
                    return;
                }
                beginCloseForDirect("bank-aware replan could not start");
                return;
            }

            state = BankState.WAITING_BANK_REPLAN;
            stateStartedTick = client.getTickCount();
            Microbot.status = "Comparing banked teleports with the current route.";
        }

        private BankTransportRequirements bankBackedTransportRequirements(
            WorldPoint target, boolean includeFairyRing, WorldPoint bankStart,
            List<Rs2TransportEdge> selectedEdges,
            java.util.concurrent.atomic.AtomicReference<BankTransportRequirements> fallback)
        {
            // Publish no speculative reserves while a chosen route is being admitted. A timeout
            // or rejected transition must never turn that route into unrelated withdrawals.
            BankTransportRequirements noSupplies = new BankTransportRequirements(Map.of(), "none", false);
            fallback.set(noSupplies);
            List<Rs2TransportEdge> edges = selectedEdges.isEmpty()
                ? TransportSupplyPlanner.plan(bankStart, target,
                    request -> WildernessTravelPolicy.plan(request, config.allowWildernessTravel())) : selectedEdges;
            if (!Microbot.getClientThread().runOnClientThreadOptional(
                () -> networkTransportSupport.canExecuteSelectedEdges(edges)).orElse(false))
            {
                debug("bank-coordinator selected transport admission rejected target={} reason={}",
                    target, networkTransportSupport.selectedTransitionRejection());
                return noSupplies;
            }

            LinkedHashMap<Integer, Integer> withdrawals = new LinkedHashMap<>();
            StringBuilder summary = new StringBuilder();

            // Shared transport prerequisites remain additive. Balloon fuel is destination-specific
            // and Moonclan runes are relevant only for a Lunar Isle target; the weighted Efficient
            // Walker comparison below remains authoritative for direct banked teleport selection.
            Map<Integer, Integer> balloonLogs =
                networkTransportSupport.bankBackedBalloonLogRequirements(bankStart, target);
            if (!balloonLogs.isEmpty() && bankContainsRequirements(balloonLogs))
            {
                withdrawals.putAll(balloonLogs);
                summary.append("balloon log");
            }

            Map<Integer, Integer> moonclanRunes =
                networkTransportSupport.bankBackedMoonclanRuneRequirements(target);
            if (!moonclanRunes.isEmpty() && bankContainsRequirements(moonclanRunes))
            {
                for (Map.Entry<Integer, Integer> entry : moonclanRunes.entrySet())
                {
                    withdrawals.merge(entry.getKey(), entry.getValue(), Math::max);
                }
                if (summary.length() > 0) summary.append(" + ");
                summary.append("Moonclan runes");
            }

            // Already standing at an open bank: let the bank-aware planner
            // compare providers once after supplying fares. Resolving another bank here
            // could spend the entire deadline searching for a bank hundreds of tiles away.

            // Charter and Kourend minecart execution stay inside Efficient Walker. The bank
            // layer only supplies a conservative coin reserve; after withdrawal the normal
            // Efficient Walker graph is rebuilt and remains the authority on which route wins.
            int charterReserve = networkTransportSupport.bankBackedCharterFareRequirement(target);
            int minecartReserve = networkTransportSupport.bankBackedMinecartFareRequirement(target);
            int karamjaReserve = networkTransportSupport.bankBackedKaramjaFareRequirement(target);
            int tollReserve = networkTransportSupport.bankBackedAlKharidTollRequirement(target);
            int coinReserve = Math.max(Math.max(Math.max(charterReserve, minecartReserve), karamjaReserve), tollReserve);
            if (coinReserve > 0)
            {
                int missingCoins = Math.max(0, coinReserve - Rs2Inventory.itemQuantity(ItemID.COINS));
                if (missingCoins > 0 && Rs2Bank.count(ItemID.COINS) >= missingCoins)
                {
                    withdrawals.put(ItemID.COINS, missingCoins);
                    if (charterReserve > 0)
                    {
                        summary.append("charter fare");
                    }
                    if (minecartReserve > 0)
                    {
                        if (summary.length() > 0)
                        {
                            summary.append(" + ");
                        }
                        summary.append("Kourend minecart fare");
                    }
                    if (karamjaReserve > 0)
                    {
                        if (summary.length() > 0)
                        {
                            summary.append(" + ");
                        }
                        summary.append("Karamja/Shilo fare");
                    }
                    if (tollReserve > 0)
                    {
                        if (summary.length() > 0)
                        {
                            summary.append(" + ");
                        }
                        summary.append("Al Kharid toll");
                    }
                }
            }

            if (edges.isEmpty())
                fallback.set(new BankTransportRequirements(withdrawals, summary.toString(), false));

            // Search with bank contents visible, BEFORE requiring coins/staff in inventory.
            // The selected path carries its exact fares and item alternatives into withdrawal.
            // Execution still rebuilds the carried-only graph after supplies are verified.
            List<Rs2TransportEdge> supplyEdges = TransportSupplyPlanner.nonTeleportSupplies(edges);
            Rs2TransportLoadout loadout = Microbot.getClientThread().invoke(() ->
                TransportSupplyPlanner.supplies(supplyEdges,
                    Rs2WalkerBankingPlanner::getMissingTransportEdgeLoadout,
                    Rs2Bank::count, Rs2Inventory::itemQuantity));
            if (!loadout.isSatisfiable() && !edges.isEmpty())
            {
                debug("bank-coordinator selected transport loadout unavailable target={}", target);
                return noSupplies;
            }
            List<Integer> equipment = List.of();
            if (loadout.isSatisfiable() && !edges.isEmpty())
            {
                // Exact route evidence replaces broad fallback reserves. Do not withdraw
                // fares/logs for unrelated alternatives once an actual chain was found.
                withdrawals.clear();
                summary.setLength(0);
                for (Map.Entry<Integer, Integer> entry : loadout.getWithdrawals().entrySet())
                    withdrawals.merge(entry.getKey(), entry.getValue(), Math::max);
                equipment = loadout.getEquipmentItemIds();
                if (!loadout.isEmpty())
                {
                    if (summary.length() > 0) summary.append(" + ");
                    summary.append("planned transport supplies");
                }
            }
            return new BankTransportRequirements(withdrawals,
                summary.length() == 0 ? "none" : summary.toString(), false, equipment);
        }

        private BankTransportRequirements consumeTransportRequirementAnalysis()
        {
            Future<BankTransportRequirements> analysis = transportRequirementAnalysis;
            transportRequirementAnalysis = null;
            if (analysis == null)
            {
                return null;
            }
            try
            {
                return analysis.get();
            }
            catch (Exception ex)
            {
                debug("bank-coordinator bank-backed transport analysis failed: {}", ex.getMessage());
                return null;
            }
        }

        private void cancelTransportRequirementAnalysis()
        {
            if (transportRequirementAnalysis != null)
            {
                transportRequirementAnalysis.cancel(true);
                transportRequirementAnalysis = null;
            }
        }

        private void finishBankReplanIfReady()
        {
            if (routeSubmission.isPending()) return;
            // previewRouteTo starts an asynchronous plan. Never inspect the planning result in the same
            // client tick; that was the source of the old MAX_VALUE/false-direct result.
            if (client.getTickCount() <= stateStartedTick)
            {
                return;
            }

            Status status = engineStatus();
            if (status == Status.PLANNING)
            {
                if (client.getTickCount() - stateStartedTick >= FINAL_PREVIEW_TIMEOUT_TICKS)
                    beginCloseForDirect("bank-aware planning timed out");
                return;
            }

            WorldPoint engineTarget = (WorldPoint) invokeEngine("getDestination", NO_TYPES);
            boolean currentPreview = pendingTarget != null && pendingTarget.equals(engineTarget);
            if (status == Status.BLOCKED && currentPreview && !transportRequirementsChecked)
            {
                beginTransportRequirementAnalysis(pendingTarget, probeFairyRingPrerequisites);
                return;
            }

            if (status == Status.BLOCKED && currentPreview && transportRequirementsChecked)
            {
                beginCloseForDirect("bank-aware preview finished without an executable route");
                return;
            }

            if (status != Status.PREVIEW || !currentPreview)
            {
                if (client.getTickCount() - stateStartedTick < FINAL_PREVIEW_TIMEOUT_TICKS)
                {
                    return;
                }
                beginCloseForDirect("bank-aware preview unavailable");
                return;
            }

            if (!bankPlanSelected())
            {
                // PREVIEW already proves an executable carried-items route. A null bank
                // plan does not mean missing supplies: a carried necklace can be the winner.
                // Never withdraw for an independent alternative and only compare afterwards.
                // BLOCKED previews still request supply analysis above.
                debug("bank-coordinator legacy fallback found no bank plan; alternatives unverified target={} previewCost={}",
                    pendingTarget, currentPreviewCost());
                beginCloseForDirect("using available fallback route");
                return;
            }

            selectedBankTeleportName = bankPlanSummary();
            Map<Integer, Integer> requirements = bankPlanRequirements();
            if (requirements == null)
            {
                beginCloseForDirect("banked teleport supplies changed");
                return;
            }

            if (requirements.isEmpty())
            {
                debug("bank-coordinator bank plan requires no additional withdrawal target={} summary={}",
                    pendingTarget, bankPlanSummary());
                beginFinalClose();
                return;
            }

            pendingWithdrawals = new LinkedHashMap<>(requirements);
            activeWithdrawal = null;
            state = BankState.WITHDRAWING;
            stateStartedTick = client.getTickCount();
            Microbot.status = "Withdrawing teleport supplies: " + bankPlanSummary();
            debug("bank-coordinator bank plan selected {} target={} withdrawals={}",
                bankPlanSummary(), pendingTarget, pendingWithdrawals);
        }

        private boolean tickWithdrawing()
        {
            if (caveLightSupplyPass && (client.getGameState() != GameState.LOGGED_IN || client.getWorld() != caveLightWorld)) {
                stopCaveTravel("World changed while preparing the cave light; retry the route.");
                return true;
            }
            if (!Rs2Bank.isOpen())
            {
                fallbackToDirect("bank closed during teleport withdrawal");
                return true;
            }

            if (spaceDepositItem != null)
            {
                if (inventoryCount(spaceDepositItem) < spaceDepositBefore)
                {
                    spaceDepositItem = null;
                    cancelBankAction();
                }
                else if (client.getTickCount() - spaceDepositTick >= WITHDRAW_VERIFY_TIMEOUT_TICKS)
                {
                    cancel();
                    invoke("cancel", NO_TYPES);
                    Microbot.status = "Could not free an inventory slot; no travel item withdrawn.";
                    return true;
                }
                else return true;
            }

            if (activeWithdrawal != null)
            {
                int now = inventoryCount(activeWithdrawal.getKey());
                int requiredIncrease = Math.max(1, activeWithdrawal.getValue());
                if (now >= activeWithdrawalInventoryBefore + requiredIncrease
                    && (!caveLightSupplyPass || Rs2Bank.getBankLiveEpoch() > caveLightWithdrawalEpoch))
                {
                    pendingWithdrawals.remove(activeWithdrawal.getKey());
                    debug("bank-coordinator withdrawal verified item={} amount={} inventory={}",
                        activeWithdrawal.getKey(), activeWithdrawal.getValue(), now);
                    activeWithdrawal = null;
                    cancelBankAction();
                }
                else if (bankActionDone())
                {
                    consumeBankActionResult();
                    activeWithdrawalCompletedTick = client.getTickCount();
                }
                else if (activeWithdrawalCompletedTick >= 0
                    ? client.getTickCount() - activeWithdrawalCompletedTick >= WITHDRAW_VERIFY_TIMEOUT_TICKS
                    : client.getTickCount() - activeWithdrawalStartedTick >= ASYNC_BANK_ACTION_TIMEOUT_TICKS)
                {
                    cancelBankAction();
                    fallbackToDirect("teleport withdrawal was not verified");
                    return true;
                }

                if (activeWithdrawal != null)
                {
                    return true;
                }
            }

            if (pendingWithdrawals.isEmpty())
            {
                if (caveLightSupplyPass) { beginFinalClose(); return true; }
                if (apeAtollSupplyPass) { beginFinalClose(); return true; }
                if (transportSupplyPass)
                {
                    // Re-run the bank-aware preview while the bank is still open. This
                    // lets its existing teleport-item/rune comparison layer stack cleanly on top
                    // of the charter/fairy prerequisites we just supplied instead of competing
                    // with them after the bank has already closed.
                    transportSupplyPass = false;
                    beginPreservedBankReplan();
                    return true;
                }
                beginFinalClose();
                return true;
            }

            Map<Integer, Integer> carried = new HashMap<>();
            int occupied = 0;
            // A fully deposited inventory can have no container. Do not reuse the
            // convenience snapshot of the bag that was just deposited.
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            Item[] inventoryItems = inventory == null || inventory.getItems() == null
                ? new Item[0]
                : inventory.getItems();
            for (Item item : inventoryItems)
            {
                if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
                {
                    occupied++;
                    carried.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
            int slots = WithdrawalCapacity.additionalSlotsNeeded(pendingWithdrawals, carried,
                id -> client.getItemDefinition(id).isStackable());
            int free = Math.max(0, 28 - occupied);
            if (slots > free)
            {
                int deposit = WithdrawalCapacity.spareFood(carried, pendingWithdrawals, id -> {
                    ItemComposition item = client.getItemDefinition(id);
                    return !item.isStackable() && item.getInventoryActions() != null
                        && Arrays.stream(item.getInventoryActions()).anyMatch("Eat"::equalsIgnoreCase);
                });
                if (deposit > 0)
                {
                    spaceDepositItem = deposit;
                    spaceDepositBefore = carried.get(deposit);
                    spaceDepositTick = client.getTickCount();
                    bankAction = bankExecutor().submit(() -> Rs2Bank.depositOne(deposit));
                    Microbot.status = "Banking one spare food to make room for travel supplies.";
                    return true;
                }
                cancel();
                invoke("cancel", NO_TYPES);
                Microbot.status = "Free " + (slots - free) + " inventory slot(s) for travel supplies, then retry.";
                return true;
            }

            activeWithdrawal = pendingWithdrawals.entrySet().iterator().next();
            int itemId = activeWithdrawal.getKey();
            int amount = activeWithdrawal.getValue();
            activeWithdrawalInventoryBefore = inventoryCount(itemId);
            activeWithdrawalStartedTick = client.getTickCount();
            activeWithdrawalCompletedTick = -1;
            caveLightWithdrawalEpoch = Rs2Bank.getBankLiveEpoch();
            boolean caveWithdrawal = caveLightSupplyPass;
            int expectedCaveWorld = caveLightWorld;
            bankAction = bankExecutor().submit(() ->
            {
                try
                {
                    if (caveWithdrawal && !caveLightInputReady(expectedCaveWorld)) return false;
                    if (!Rs2Bank.setWithdrawAsItem())
                    {
                        return false;
                    }
                    if (caveWithdrawal && !caveLightInputReady(expectedCaveWorld)) return false;
                    return amount == 1
                        ? Rs2Bank.withdrawOne(itemId)
                        : Rs2Bank.withdrawX(itemId, amount);
                }
                catch (RuntimeException ex)
                {
                    debug("bank-coordinator withdrawal exception item={} amount={} error={}",
                        itemId, amount, ex.getMessage());
                    return false;
                }
            });
            debug("bank-coordinator withdrawing item={} amount={} inventoryBefore={}",
                itemId, amount, activeWithdrawalInventoryBefore);
            return true;
        }

        private void beginFinalClose()
        {
            cancelBankAction();
            state = BankState.CLOSING_BANK;
            stateStartedTick = client.getTickCount();
            Microbot.status = "Teleport supplies ready; closing bank.";
            clickBankClose();
        }

        private boolean tickClosingBank()
        {
            if (!Rs2Bank.isOpen())
            {
                if (!pendingEquipment.isEmpty())
                {
                    int itemId = pendingEquipment.get(0);
                    if (TravelEquipment.isWearing(itemId))
                    {
                        pendingEquipment.remove(0);
                        equipmentAttemptTick = -1;
                        cancelBankAction();
                    }
                    else if (equipmentAttemptTick < 0)
                    {
                        cancelBankAction();
                        equipmentAttemptTick = client.getTickCount();
                        if (FairyRingEquipmentRestore.isStaff(itemId))
                        {
                            ItemContainer equipped = client.getItemContainer(InventoryID.EQUIPMENT);
                            if (equipped != null && equipped.getItems() != null)
                            {
                                Item[] items = equipped.getItems();
                                fairyRingEquipmentRestore.beforeBankStaffEquip(
                                    equippedId(items, EquipmentInventorySlot.WEAPON.getSlotIdx()),
                                    equippedId(items, EquipmentInventorySlot.SHIELD.getSlotIdx()));
                            }
                        }
                        bankAction = bankExecutor().submit(() -> Rs2Inventory.equip(itemId));
                    }
                    else if (client.getTickCount() - equipmentAttemptTick >= WITHDRAW_VERIFY_TIMEOUT_TICKS)
                    {
                        cancel();
                        invoke("cancel", NO_TYPES);
                        Microbot.status = "Required travel equipment could not be equipped; retry the route.";
                    }
                    return true;
                }
                WorldPoint target = pendingTarget;
                if (caveLightSupplyPass) {
                    cancelBankAction();
                    state = BankState.LIGHTING_CAVE;
                    stateStartedTick = client.getTickCount();
                    return true;
                }
                if (apeAtollSupplyPass) {
                    cancelBankAction(); clear();
                    if (!useApeAtollTabletFlow(target) || ApeAtollGreegree.onIsland(Rs2Player.getWorldLocation())) walkToDirect(target);
                    else beginApeAtollTabletTravel(target);
                    return true;
                }
                if (closeForDirect)
                {
                    cancelBankAction();
                    boolean previewReady = engineStatus() == Status.PREVIEW
                        && target != null && target.equals(getDestination());
                    clear();
                    debug("bank-coordinator bank closed; starting direct route target={} previewReady={}", target, previewReady);
                    // A timed-out preview is not a route that can be committed.
                    invoke(previewReady ? "commitPreviewOrStartRoute" : "startRouteTo", new Class<?>[]{WorldPoint.class}, target);
                    return true;
                }

                cancelBankAction();
                if (completeChoice != null) networkTransportSupport.preferCompleteRoute(target, completeChoice);
                refreshTransportExtensions(target);
                if (completeChoice != null && !networkTransportSupport.completeRouteReady()) {
                    fallbackToDirect("selected complete route no longer executable");
                    return true;
                }
                boolean previewStarted = (Boolean) invoke(
                    "previewRouteTo", new Class<?>[]{WorldPoint.class}, target);
                if (!previewStarted)
                {
                    fallbackToDirect("final teleport preview could not start");
                    return true;
                }
                state = BankState.WAITING_FINAL_PREVIEW;
                stateStartedTick = client.getTickCount();
                Microbot.status = "Recalculating with withdrawn teleport supplies.";
                debug("bank-coordinator bank closed; final preview target={}", target);
                return true;
            }

            if (client.getTickCount() - stateStartedTick >= CLOSE_BANK_TIMEOUT_TICKS * 3)
            {
                debug("bank-coordinator stopped: bank would not close target={}", pendingTarget);
                cancel();
                invoke("cancel", NO_TYPES);
                Microbot.status = "Efficient Walker: bank would not close; retry the route.";
                return true;
            }
            if (client.getTickCount() - stateStartedTick >= CLOSE_BANK_TIMEOUT_TICKS)
            {
                // Use the current bank utility as a fallback close; run it off the client thread.
                if (!bankCloseFallback && (bankAction == null || bankAction.isDone()))
                {
                    bankCloseFallback = true;
                    bankAction = bankExecutor().submit(Rs2Bank::closeBank);
                }
            }
            else
            {
                clickBankClose();
            }
            return true;
        }

        private boolean tickLightingCave()
        {
            if (client.getGameState() != GameState.LOGGED_IN) return true;
            if (client.getWorld() != caveLightWorld || Rs2Bank.isOpen()) {
                stopCaveTravel("Cave light preparation changed before departure; retry the route.");
                return true;
            }
            if (CaveLightSource.hasLight()) {
                WorldPoint target = pendingTarget;
                clear();
                refreshTransportExtensions(target);
                invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, target);
                return true;
            }
            int unlit = caveLightUnlit, lit = CaveLightSource.litVariant(unlit);
            int tick = client.getTickCount();
            if (caveLightAttemptTick >= 0 && tick - caveLightAttemptTick < 6) return true;
            if (lit <= 0 || Rs2Inventory.itemQuantity(CaveLightSource.TINDERBOX) <= 0
                || Rs2Inventory.itemQuantity(unlit) <= 0
                || Rs2Player.getRealSkillLevel(Skill.FIREMAKING) < CaveLightSource.lightingLevel(unlit)) {
                stopCaveTravel("The cave light could not be lit with the carried supplies; travel stopped.");
                return true;
            }
            if (caveLightAttempts >= 3) {
                stopCaveTravel("Lighting the cave light was not confirmed after three attempts; travel stopped.");
                return true;
            }
            if (caveLightAttempts == 0) {
                caveLightBeforeUnlit = Rs2Inventory.itemQuantity(unlit);
                caveLightBeforeLit = Rs2Inventory.itemQuantity(lit);
            }
            // A partial container update must not cause another use-item input.
            if (Rs2Inventory.itemQuantity(unlit) < caveLightBeforeUnlit
                || Rs2Inventory.itemQuantity(lit) > caveLightBeforeLit) {
                caveLightAttempts++; caveLightAttemptTick = tick;
                return true;
            }
            cancelBankAction();
            caveLightAttempts++; caveLightAttemptTick = tick;
            bankAction = bankExecutor().submit(() -> {
                if (!LumbridgeSwampCaveAccess.inputAllowed()) return false;
                boolean ready = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    client.getGameState() == GameState.LOGGED_IN && client.getWorld() == caveLightWorld
                        && !Rs2Bank.isOpen() && Rs2Inventory.itemQuantity(unlit) > 0
                        && Rs2Inventory.itemQuantity(CaveLightSource.TINDERBOX) > 0
                        && Rs2Player.getRealSkillLevel(Skill.FIREMAKING) >= CaveLightSource.lightingLevel(unlit))
                    .orElse(false);
                return ready && LumbridgeSwampCaveAccess.inputAllowed()
                    && Rs2Inventory.combine(CaveLightSource.TINDERBOX, unlit);
            });
            Microbot.status = "Lighting a source for the selected cave route.";
            return true;
        }

        /** Worker-side authority check immediately before a cave-supply banking input. */
        private boolean caveLightInputReady(int expectedWorld)
        {
            return LumbridgeSwampCaveAccess.inputAllowed()
                && Microbot.getClientThread().runOnClientThreadOptional(() -> caveLightSupplyPass
                    && client.getGameState() == GameState.LOGGED_IN && client.getWorld() == expectedWorld
                    && Rs2Bank.isOpen()).orElse(false)
                && LumbridgeSwampCaveAccess.inputAllowed();
        }

        private void finishFinalPreviewIfReady()
        {
            if (routeSubmission.isPending()) return;
            if (pendingTarget == null) {
                clear();
                invoke("cancel", NO_TYPES);
                return;
            }
            if (client.getTickCount() <= stateStartedTick)
            {
                return;
            }

            Status status = engineStatus();
            if (status == Status.PLANNING)
            {
                if (client.getTickCount() - stateStartedTick >= FINAL_PREVIEW_TIMEOUT_TICKS)
                    fallbackToDirect("final planning timed out");
                return;
            }

            if (status == Status.PREVIEW && pendingTarget != null
                && pendingTarget.equals(invokeEngine("getDestination", NO_TYPES)))
            {
                WorldPoint target = pendingTarget;
                if (bankPlanSelected())
                {
                    debug("bank-coordinator final preview still requests bank supplies target={} summary={}",
                        target, bankPlanSummary());
                    fallbackToDirect("withdrawn supplies were not recognized");
                    return;
                }

                if (config.enableDebugging())
                {
                    Object selected = engineField("activeTeleportProvider");
                    String selectedName = "none (walk/transport)";
                    try
                    {
                        if (selected != null) selectedName = String.valueOf(field(selected.getClass(), "name").get(selected));
                    }
                    catch (ReflectiveOperationException ex) { selectedName = "unavailable: " + ex.getMessage(); }
                    debug("bank-diagnostic final target={} bankWinner={} finalTeleport={} previewRunningCost={}",
                        target, selectedBankTeleportName == null ? "none" : selectedBankTeleportName,
                        selectedName, currentPreviewCost());
                }
                clear();
                debug("bank-coordinator supplies ready; committing route target={}", target);
                invoke("commitPreviewOrStartRoute", new Class<?>[]{WorldPoint.class}, target);
                return;
            }

            if (client.getTickCount() - stateStartedTick >= FINAL_PREVIEW_TIMEOUT_TICKS)
            {
                fallbackToDirect("final teleport preview timed out");
            }
        }

        private void beginCloseForDirect(String reason)
        {
            requestOwnership.check();
            debug("bank-coordinator {} target={}", reason, pendingTarget);

            if (!Rs2Bank.isOpen())
            {
                WorldPoint target = pendingTarget;
                if (!updateOnClientThread(() -> { clear(); return true; }))
                    throw new CancellationException("Bank close was replaced before its handoff");
                refreshTransportExtensions(target);
                invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, target);
                return;
            }

            // No banked route won.  Close cleanly, then perform the direct route without
            // re-entering the public bank coordinator.
            if (!updateOnClientThread(() -> {
                WorldPoint target = pendingTarget;
                cancelBankAction();
                state = BankState.CLOSING_BANK;
                stateStartedTick = client.getTickCount();
                closeForDirect = true;
                pendingWithdrawals = Map.of();
                spaceDepositItem = null;
                pendingEquipment = new ArrayList<>();
                equipmentAttemptTick = -1;
                activeWithdrawal = null;
                Microbot.status = "No faster banked teleport found; closing bank.";
                debug("bank-coordinator closing bank before direct route target={}", target);
                clickBankClose();
                return true;
            })) throw new CancellationException("Bank close was replaced before its handoff");
        }

        private void fallbackToDirect(String reason)
        {
            requestOwnership.check();
            if (caveLightSupplyPass) {
                stopCaveTravel("Cave light preparation failed: " + reason);
                return;
            }
            if (missingApeAtollSupplies(pendingTarget)) {
                stopApeAtollTravel("Monkey greegree preparation failed: " + reason);
                return;
            }
            if (!updateOnClientThread(() -> {
                networkTransportSupport.clearCompleteRoute();
                return true;
            })) throw new CancellationException("Direct fallback was replaced before route cleanup");
            WorldPoint target = pendingTarget;
            debug("bank-coordinator fallback reason={} target={}", reason, target);
            if (Rs2Bank.isOpen()) {
                beginCloseForDirect(reason);
                return;
            }
            if (!updateOnClientThread(() -> {
                cancelBankAction();
                clear();
                invoke("cancel", NO_TYPES);
                return true;
            })) throw new CancellationException("Direct fallback was replaced before its handoff");
            if (target != null)
            {
                refreshTransportExtensions(target);
                invoke("startRouteTo", new Class<?>[]{WorldPoint.class}, target);
            }
        }

        private void submitOpenBank()
        {
            cancelBankAction();
            bankAction = bankExecutor().submit(() ->
            {
                try
                {
                    WorldPoint player = Rs2Player.getWorldLocation();
                    if (hasImmediateBankAccess())
                    {
                        // A bank already in interaction range owns this attempt. Do not spend
                        // several seconds falling through generic discovery or switch to another
                        // banking target; the probe state will reacquire this same nearby source.
                        return openImmediateSceneBank(player);
                    }
                    // Never let generic bank discovery click a different bank through a wall.
                    // The approach state must bring a verified local bank into reach first.
                    return false;
                }
                catch (RuntimeException ex)
                {
                    debug("bank-coordinator open-bank exception: {}", ex.getMessage());
                    return false;
                }
            });
        }

        private TileObject findImmediateBankChest(WorldPoint player)
        {
            if (player == null)
            {
                return null;
            }
            return Microbot.getClientThread().invoke(() -> SceneObjects.<TileObject>getAll(
                    object -> object != null && object.getWorldLocation() != null
                        && object.getWorldLocation().getPlane() == player.getPlane(),
                    player, IMMEDIATE_BANK_RADIUS).stream()
                .filter(object -> {
                    ObjectComposition composition = SceneObjects.convertToObjectComposition(object);
                    if (composition == null || composition.getName() == null)
                    {
                        return false;
                    }
                    String name = composition.getName();
                    return name.toLowerCase(Locale.ROOT).contains("bank chest")
                        && (SceneObjects.hasAction(composition, "Bank", true)
                            || SceneObjects.hasAction(composition, "Use", true));
                })
                .filter(this::bankObjectReachable)
                .min(Comparator.comparingInt(object ->
                    object.getWorldLocation().distanceTo2D(player)))
                .orElse(null));
        }

        private boolean bankObjectReachable(TileObject object)
        {
            return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
                WorldPoint player = Rs2Player.getWorldLocation();
                if (player == null || object == null) return false;
                net.runelite.api.coords.WorldArea area = object instanceof GameObject
                    ? SceneObjects.getWorldArea((GameObject) object)
                    : new net.runelite.api.coords.WorldArea(object.getWorldLocation(), 1, 1);
                return TransportApproach.reachable(area,
                    Rs2Tile.getReachableTilesFromTile(player, 24).keySet(), client.getTopLevelWorldView());
            });
        }

        private boolean bankPointReachable(WorldPoint point)
        {
            return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
                WorldPoint player = Rs2Player.getWorldLocation();
                return player != null && point != null && TransportApproach.reachable(
                    new net.runelite.api.coords.WorldArea(point, 1, 1),
                    Rs2Tile.getReachableTilesFromTile(player, 24).keySet(), client.getTopLevelWorldView());
            });
        }

        private boolean openImmediateSceneBank(WorldPoint player)
        {
            if (player == null)
            {
                return false;
            }
            if (Rs2Bank.isOpen())
            {
                return true;
            }
            if (net.runelite.client.plugins.microbot.droslayer.walker.support.EniolaBankAccess.nearby(player)
                && !net.runelite.client.plugins.microbot.droslayer.walker.support.EniolaBankAccess.canPay(false))
            {
                return false;
            }

            TileObject object = SceneObjects.findBank(IMMEDIATE_BANK_RADIUS);
            if (object != null && !bankObjectReachable(object)) object = null;
            if (object == null)
            {
                object = SceneObjects.findGrandExchangeBooth(IMMEDIATE_BANK_RADIUS);
                if (object != null && !bankObjectReachable(object)) object = null;
            }
            if (object == null)
            {
                object = findImmediateBankChest(player);
            }

            if (object != null)
            {
                ObjectComposition composition = SceneObjects.convertToObjectComposition(object);
                String action = composition != null && SceneObjects.hasAction(composition, "Bank", true)
                    ? "Bank"
                    : composition != null && SceneObjects.hasAction(composition, "Use", true)
                        ? "Use" : null;
                if (action != null && SceneObjects.interact(object, action))
                {
                    return waitForBankOpen(1_500L);
                }
            }

            var banker = Rs2Npc.getBankerNPC();
            if (banker != null && banker.getWorldLocation() != null
                && banker.getWorldLocation().getPlane() == player.getPlane()
                && banker.getWorldLocation().distanceTo2D(player) <= IMMEDIATE_BANK_RADIUS
                && bankPointReachable(banker.getWorldLocation())
                && Rs2Npc.interact(banker, "Bank"))
            {
                return waitForBankOpen(1_500L);
            }
            return false;
        }

        private boolean waitForBankOpen(long timeoutMillis)
        {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            do
            {
                if (Rs2Bank.isOpen())
                {
                    return true;
                }
                try
                {
                    Thread.sleep(50L);
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            while (System.currentTimeMillis() < deadline);
            return Rs2Bank.isOpen();
        }

        private boolean bankActionDone()
        {
            return bankAction != null && bankAction.isDone();
        }

        private boolean consumeBankActionResult()
        {
            Future<Boolean> action = bankAction;
            bankAction = null;
            if (action == null)
            {
                return false;
            }
            try
            {
                return Boolean.TRUE.equals(action.get());
            }
            catch (Exception ex)
            {
                debug("bank-coordinator async bank action failed: {}", ex.getMessage());
                return false;
            }
        }

        private void cancelBankAction()
        {
            if (bankAction != null)
            {
                bankAction.cancel(true);
                bankAction = null;
            }
        }

        @SuppressWarnings("unchecked")
        private List<WeightedRouteAdvisor.BankedTeleportOption> snapshotBankedTeleportOptions(WorldPoint target)
        {
            if (target == null || !bankMirrorKnown())
            {
                return List.of();
            }

            Map<Integer, Integer> bankCounts = currentBankCounts();
            if (bankCounts.isEmpty())
            {
                return List.of();
            }

            Object providersValue = engineField("teleportProviders");
            if (!(providersValue instanceof List))
            {
                return List.of();
            }

            List<WeightedRouteAdvisor.BankedTeleportOption> options = new ArrayList<>();
            debug("bank-diagnostic stock target={} BrimhavenTablet11745={} FortisTablet28824={}",
                target, bankCounts.getOrDefault(11745, 0), bankCounts.getOrDefault(28824, 0));
            for (Object provider : (List<Object>) providersValue)
            {
                if (provider == null)
                {
                    continue;
                }
                try
                {
                    Method landingMethod = provider.getClass().getDeclaredMethod("getDestination");
                    landingMethod.setAccessible(true);
                    WorldPoint landing = (WorldPoint) landingMethod.invoke(provider);
                    if (landing == null)
                    {
                        continue;
                    }

                    Method candidateMethod = provider.getClass().getDeclaredMethod(
                        "getBankSupplyCandidates", Client.class, Map.class, WorldPoint.class);
                    candidateMethod.setAccessible(true);
                    Object candidateValue = candidateMethod.invoke(provider, client, bankCounts, target);
                    if (!(candidateValue instanceof List))
                    {
                        continue;
                    }

                    Object nameValue = field(provider.getClass(), "name").get(provider);
                    String name = nameValue instanceof String ? (String) nameValue : "banked teleport";

                    if (config.enableDebugging())
                    {
                        int[] ids = (int[]) field(provider.getClass(), "itemIds").get(provider);
                        Map<Integer, Integer> owned = new LinkedHashMap<>();
                        for (int id : ids) if (bankCounts.getOrDefault(id, 0) > 0) owned.put(id, bankCounts.get(id));
                        debug("bank-diagnostic provider={} landing={} matchingBankItems={} offeredRequirements={} requirements={}",
                            name, landing, owned, ((List<?>) candidateValue).size(),
                            catalogTeleportSupport.requirementDiagnostic(provider));
                    }

                    for (Object requirement : (List<Object>) candidateValue)
                    {
                        if (requirement == null)
                        {
                            continue;
                        }
                        Object requirementsValue = field(requirement.getClass(), "requiredItems").get(requirement);
                        if (!(requirementsValue instanceof Map))
                        {
                            continue;
                        }
                        Map<Integer, Integer> requirements = new LinkedHashMap<>(
                            (Map<Integer, Integer>) requirementsValue);
                        requirements.entrySet().removeIf(entry -> entry.getKey() == null
                            || entry.getKey() <= 0 || entry.getValue() == null || entry.getValue() <= 0);
                        if (requirements.isEmpty() || !containsRequirements(bankCounts, requirements))
                        {
                            continue;
                        }

                        // Inventory space is handled by tickWithdrawing after a route wins.
                        // Rejecting a candidate here hides every new teleport when food fills the bag.

                        Method costMethod = requirement.getClass().getDeclaredMethod("getWithdrawalCost");
                        costMethod.setAccessible(true);
                        int withdrawalTimeCost = ((Number) costMethod.invoke(requirement)).intValue();
                        options.add(new WeightedRouteAdvisor.BankedTeleportOption(
                            name, landing, requirements, withdrawalTimeCost));
                        debug("bank-diagnostic admitted={} landing={} withdraw={} withdrawalCost={}",
                            name, landing, requirements, withdrawalTimeCost);
                    }
                }
                catch (InvocationTargetException ex)
                {
                    debug("bank-coordinator provider candidate skipped error={}",
                        ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
                catch (ReflectiveOperationException | RuntimeException ex)
                {
                    debug("bank-coordinator provider candidate unavailable error={}", ex.getMessage());
                }
            }
            return List.copyOf(options);
        }

        private Map<Integer, Integer> currentBankCounts()
        {
            return loginBankCache.counts(Rs2Bank.getBankLiveEpoch(), () -> {
                LinkedHashMap<Integer, Integer> bankCounts = new LinkedHashMap<>();
                for (Rs2ItemModel item : Rs2Bank.bankItems())
                    if (item != null && item.getId() > 0 && item.getQuantity() > 0)
                        bankCounts.merge(item.getId(), item.getQuantity(), Integer::sum);
                return bankCounts;
            });
        }

        private boolean bankContainsRequirements(Map<Integer, Integer> requirements)
        {
            if (requirements == null || requirements.isEmpty())
            {
                return false;
            }
            return containsRequirements(currentBankCounts(), requirements);
        }

        private boolean containsRequirements(Map<Integer, Integer> bankCounts,
                                             Map<Integer, Integer> requirements)
        {
            for (Map.Entry<Integer, Integer> entry : requirements.entrySet())
            {
                int required = Math.max(1, entry.getValue());
                if (bankCounts.getOrDefault(entry.getKey(), 0) < required)
                {
                    return false;
                }
            }
            return true;
        }

        private boolean bankMirrorKnown()
        {
            return loginBankCache.isKnown(Rs2Bank.getBankLiveEpoch());
        }

        void resetSessionKnowledge()
        {
            loginBankCache.reset(Rs2Bank.getBankLiveEpoch());
        }

        private boolean bankPlanSelected()
        {
            return engineField("bankSupplyPlan") != null;
        }

        @SuppressWarnings("unchecked")
        private Map<Integer, Integer> bankPlanRequirements()
        {
            Object plan = engineField("bankSupplyPlan");
            if (plan == null)
            {
                return Map.of();
            }

            try
            {
                Object requirement = field(plan.getClass(), "requirement").get(plan);
                Object provider = field(requirement.getClass(), "provider").get(requirement);
                int itemId = field(requirement.getClass(), "itemId").getInt(requirement);

                Map<Integer, Integer> bankCounts = new LinkedHashMap<>();
                for (var item : Rs2Bank.bankItems())
                {
                    if (item != null && item.getId() > 0 && item.getQuantity() > 0)
                    {
                        bankCounts.merge(item.getId(), item.getQuantity(), Integer::sum);
                    }
                }

                Method requirementsMethod = provider.getClass().getDeclaredMethod(
                    "getRequiredBankItems", Client.class, Map.class, int.class);
                requirementsMethod.setAccessible(true);
                Object value = requirementsMethod.invoke(provider, client, bankCounts, itemId);
                if (value == null)
                {
                    return null;
                }
                return new LinkedHashMap<>((Map<Integer, Integer>) value);
            }
            catch (InvocationTargetException ex)
            {
                throw propagate(ex.getCause());
            }
            catch (ReflectiveOperationException ex)
            {
                throw new IllegalStateException("Unable to inspect preserved bank teleport requirements", ex);
            }
        }

        @SuppressWarnings("unchecked")
        private int currentPreviewCost()
        {
            List<WorldPoint> route = (List<WorldPoint>) invoke("getRemainingRoute", NO_TYPES);
            if (route == null || route.isEmpty())
            {
                return Integer.MAX_VALUE;
            }
            return BankSupplyController.estimateRouteTicks(route, true);
        }

        private int inventoryCount(int itemId)
        {
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory == null || inventory.getItems() == null)
            {
                return 0;
            }

            int quantity = 0;
            for (Item item : inventory.getItems())
            {
                if (item != null && item.getId() == itemId)
                {
                    quantity += item.getQuantity();
                }
            }
            return quantity;
        }

        private Object engineField(String name)
        {
            try
            {
                return field(engineClass, name).get(engine);
            }
            catch (ReflectiveOperationException ex)
            {
                throw new IllegalStateException("Unable to inspect preserved Efficient Walker field " + name, ex);
            }
        }

        private String bankPlanSummary()
        {
            Object plan = engineField("bankSupplyPlan");
            if (plan == null)
            {
                return "";
            }

            try
            {
                Object requirement = field(plan.getClass(), "requirement").get(plan);
                if (requirement == null)
                {
                    return "";
                }

                Object provider = field(requirement.getClass(), "provider").get(requirement);
                if (provider == null)
                {
                    return "";
                }

                Object name = field(provider.getClass(), "name").get(provider);
                return name instanceof String ? (String) name : "";
            }
            catch (ReflectiveOperationException ex)
            {
                return "";
            }
        }

        private boolean clickBankClose()
        {
            if (bankCloseClicked) return true;
            Widget bankClose = client.getWidget(786434);
            if (bankClose == null)
            {
                return false;
            }

            Widget[] children = bankClose.getDynamicChildren();
            if (children == null)
            {
                return false;
            }

            for (Widget child : children)
            {
                if (child != null && EfficientWalker.this.clickWidgetAction(child, "Close"))
                {
                    bankCloseClicked = true;
                    return true;
                }
            }
            return false;
        }

        private final class BankTransportRequirements
        {
            private final Map<Integer, Integer> withdrawals;
            private final String summary;
            private final boolean weightedTeleport;
            private final List<Integer> equipment;

            private BankTransportRequirements(Map<Integer, Integer> withdrawals, String summary,
                                              boolean weightedTeleport)
            {
                this(withdrawals, summary, weightedTeleport, List.of());
            }

            private BankTransportRequirements(Map<Integer, Integer> withdrawals, String summary,
                                              boolean weightedTeleport, List<Integer> equipment)
            {
                this.equipment = List.copyOf(equipment);
                this.withdrawals = withdrawals == null
                    ? Map.of()
                    : new LinkedHashMap<>(withdrawals);
                this.summary = summary == null ? "" : summary;
                this.weightedTeleport = weightedTeleport;
            }
        }

        private void clear()
        {
            cancelCompleteDiscovery();
            completeChoice = null;
            completeProgress = null;
            // Never orphan queued work when a preview/fallback leaves the coordinator.
            cancelBankAction();
            cancelFairyRingCandidateAnalysis();
            cancelTransportRequirementAnalysis();
            cancelWeightedRouteAnalysis();
            state = BankState.IDLE;
            pendingTarget = null;
            stateStartedTick = 0;
            bankEpochBeforeOpen = 0;
            bankWasOpenAtStart = false;
            fairyRingCandidateAnalysis = null;
            transportRequirementAnalysis = null;
            weightedRouteAnalysis = null;
            weightedAnalyzedTarget = null;
            carriedLandingComparison = false;
            weightedAdvice = null;
            pendingWithdrawals = Map.of();
            spaceDepositItem = null;
            pendingEquipment = new ArrayList<>();
            equipmentAttemptTick = -1;
            activeWithdrawal = null;
            activeWithdrawalInventoryBefore = 0;
            activeWithdrawalStartedTick = 0;
            activeWithdrawalCompletedTick = -1;
            preparationHeldTick = -1;
            closeForDirect = false;
            bankCloseClicked = bankCloseFallback = false;
            transportSupplyPass = false;
            apeAtollSupplyPass = false;
            caveLightSupplyPass = false;
            caveLightUnlit = -1; caveLightAttemptTick = -1; caveLightAttempts = 0;
            caveLightWorld = 0; caveLightBeforeUnlit = 0; caveLightBeforeLit = 0;
            caveLightWithdrawalEpoch = 0;
            probeFairyRingPrerequisites = false;
            immediateBankOpenAttempts = 0;
            transportRequirementsChecked = false;
            selectedBankTeleportName = null;
            instanceEscapeProvider = null;
            activeEscapeStart = null;
            isolatedTeleportEscape = false;
            instanceEscapeLanding = null;
            instanceEscapeName = null;
            instanceEscapeAttempts = 0;
            instanceEscapeLastAttemptTick = -1;
            housePortalEscape = false;
            housePortalPreview = false;
            housePortalWorld = 0;
        }

        private void debug(String message, Object... args)
        {
            if (config.enableDebugging())
            {
                LOG.info("[EfficientWalker] " + message, args);
            }
        }
    }

    private enum BankState
    {
        IDLE,
        WAITING_DIRECT_PREVIEW,
        ESCAPING_INSTANCE,
        ANALYZING_WEIGHTED_ROUTE,
        ANALYZING_FAIRY_RING_CANDIDATE,
        PROBING_VISIBLE_BANK,
        WALKING_TO_BANK,
        OPENING_BANK,
        WAITING_BANK_SNAPSHOT,
        DISCOVERING_COMPLETE_ROUTE,
        ANALYZING_TRANSPORT_REQUIREMENTS,
        WAITING_BANK_REPLAN,
        WITHDRAWING,
        CLOSING_BANK,
        LIGHTING_CAVE,
        WAITING_FINAL_PREVIEW
    }

    private static final Class<?>[] NO_TYPES = new Class<?>[0];

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args)
    {
        if ("walkTo".equals(name) || "walkToNearestBank".equals(name)
            || "followSuppliedRoute".equals(name) || "previewRouteTo".equals(name)
            || "startRouteTo".equals(name) || "commitPreviewOrStartRoute".equals(name)
            || "cancel".equals(name) || "shutdown".equals(name))
        {
            // Only the engine's short request/status publication is serialized.
            // Collision loading and route preparation have already finished.
            synchronized (requestOwnership)
            {
                if (!requestOwnership.isContextCurrent())
                    throw new CancellationException("A superseded request cannot publish navigation state");
                if (!"cancel".equals(name) && !"shutdown".equals(name) && isWildernessTravelPaused())
                    return Boolean.TRUE;
                if (!"cancel".equals(name) && !"shutdown".equals(name))
                    return queueRouteSubmission(name, parameterTypes, args);
                routeSubmission.cancel();
                return invokeEngine(name, parameterTypes, args);
            }
        }
        return invokeEngine(name, parameterTypes, args);
    }

    /** Queue only fresh request publication; route calculation and movement remain unchanged. */
    private Object queueRouteSubmission(String name, Class<?>[] parameterTypes, Object[] arguments)
    {
        Object[] args = arguments.clone();
        WorldPoint target = args.length > 0 && args[0] instanceof WorldPoint ? (WorldPoint) args[0] : null;
        if ("followSuppliedRoute".equals(name))
        {
            @SuppressWarnings("unchecked") List<WorldPoint> route = (List<WorldPoint>) args[0];
            if (route == null || route.isEmpty()) return invokeEngine(name, parameterTypes, args);
            List<WorldPoint> snapshot = Collections.unmodifiableList(new ArrayList<>(route));
            args[0] = snapshot;
            target = snapshot.get(0); // Matches the preserved supplied-route entry point.
        }
        else if (!"walkToNearestBank".equals(name) && target == null)
        {
            return invokeEngine(name, parameterTypes, args);
        }
        WalkerRequestOwnership.Request request = requestOwnership.capture();
        long token = request == null ? 0 : request.id;
        long epoch = requestOwnership.captureEpoch();
        WalkerRouteSubmission submissions = routeSubmission;
        WalkerRouteSubmission.Ticket pending = submissions.pending();
        if (!"followSuppliedRoute".equals(name) && pending != null
            && pending.ownerToken == token && pending.command.equals(name)
            && Objects.equals(pending.target, target)) return Boolean.TRUE;
        // A supplied path can change while retaining the same endpoint. Its entire
        // captured payload belongs to this submission, so replace any earlier path.
        if ("followSuppliedRoute".equals(name) && pending != null) submissions.cancel();

        // Reset only after duplicate coalescing: a fresh preview or supplied path
        // must not inherit an adjacent-arrival receipt from an earlier request.
        finalTileWatchdog.reset();
        catalogTeleportSupport.resetObservedLanding();
        WalkerMouseControl.invalidate();
        routeSubmissionFailure = null;
        Class<?>[] types = parameterTypes.clone();
        java.util.concurrent.atomic.AtomicReference<WalkerRouteSubmission.Ticket> publishing =
            new java.util.concurrent.atomic.AtomicReference<>();
        // Capture at the caller boundary, before the timer and client queues. This
        // includes the caller's BaseProfileDro variation, speed and mouse scopes.
        Runnable publication = net.runelite.client.plugins.microbot.droslayer.support.MouseMotionScope.capture(() -> {
            synchronized (requestOwnership)
            {
                WalkerRouteSubmission.Ticket ticket = publishing.get();
                if (!submissions.isCurrent(ticket) || requestOwnership.epoch() != epoch
                    || request != null && !requestOwnership.isCurrent(request)) return;
                if (isWildernessTravelPaused()) return;
                try (WalkerRequestOwnership.Scope ignored = requestOwnership.bind(request, epoch))
                {
                    long submitStarted = System.nanoTime();
                    if (!Boolean.TRUE.equals(invokeEngine(name, types, args)))
                        routeSubmissionFailure = "The queued route request was rejected.";
                    if (config.enableDebugging())
                        LOG.info("[EfficientWalker] route timing command={} requestedDelayMs={} queueMs={} submitMs={}",
                            name, ticket.requestedDelayMillis, ticket.actualQueueMillis, elapsedMillis(submitStarted));
                }
                catch (RuntimeException failure)
                {
                    routeSubmissionFailure = "The queued route request could not be submitted.";
                    LOG.warn("[EfficientWalker] route submission failed owner={} request={} command={}",
                        request == null ? WalkerRequestOwnership.LEGACY_OWNER : request.owner, token, name, failure);
                }
            }
        });
        return submissions.submit(token, name, target, ticket -> {
            publishing.set(ticket);
            publication.run();
        }) != null;
    }

    private Object invokeEngine(String name, Class<?>[] parameterTypes, Object... args)
    {
        if ("cancel".equals(name) || "shutdown".equals(name)) WalkerMouseControl.invalidate();
        if (("previewRouteTo".equals(name) || "startRouteTo".equals(name))
            && args.length > 0 && !Objects.equals(args[0], getDestination())) WalkerMouseControl.invalidate();
        try (var ignored = WalkerMouseControl.openProfile())
        {
            Method method = engineClass.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(engine, args);
        }
        catch (InvocationTargetException ex)
        {
            throw propagate(ex.getCause());
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException("v1.11.2 engine method unavailable: " + name, ex);
        }
    }

    /** Engine classes isolated per walker. */
    private static final class NavigationEngineClassLoader extends ClassLoader
    {
        private NavigationEngineClassLoader(ClassLoader parent)
        {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
        {
            if (!name.startsWith(ENGINE_PACKAGE + "."))
            {
                return super.loadClass(name, resolve);
            }
            // Travel admission and request state belong to one facade, never the parent loader.
            synchronized (getClassLoadingLock(name))
            {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException
        {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resource))
            {
                if (input == null) throw new ClassNotFoundException(name + " (source engine class missing)");
                byte[] bytes = input.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            catch (IOException ex)
            {
                throw new ClassNotFoundException(name, ex);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name)
        {
            InputStream data = WalkerEngineResources.open(name);
            return data != null ? data : super.getResourceAsStream(name);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static RuntimeException propagate(Throwable throwable)
    {
        if (throwable instanceof RuntimeException)
        {
            return (RuntimeException) throwable;
        }
        if (throwable instanceof Error)
        {
            throw (Error) throwable;
        }
        return new IllegalStateException("v1.11.2 Efficient Walker engine failed", throwable);
    }

    public enum Status
    {
        IDLE,
        PLANNING,
        PREVIEW,
        WALKING,
        ARRIVED,
        BLOCKED
    }

    public enum PlanningFailure
    {
        NONE,
        TARGET_NOT_STANDABLE,
        ROUTE_UNSUPPORTED,
        SEARCH_LIMIT
    }

    static final class TransportMarker
    {
        final WorldPoint point;
        final String action;
        final String target;

        private TransportMarker(WorldPoint point, String action, String target)
        {
            this.point = point;
            this.action = action;
            this.target = target;
        }
    }
}
