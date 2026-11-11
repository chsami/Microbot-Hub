package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.*;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Explicit land -> recovery -> boat handoff. All mutable voyage state has one worker owner.
 * Public methods only queue requests, and are safe to call from another plugin/client thread.
 */
public final class EfficientSailingController implements AutoCloseable, SailingNavigationService {
    static final String VERSION = "0.2.9";
    public enum Status {
        IDLE, VALIDATING, WALKING_TO_BANK, BANKING, COMPARING_PORTS, WALKING_TO_PORT,
        AT_PORT, APPROACHING_GANGPLANK, BOARDING, NEEDS_RECOVERY_SETTINGS,
        APPROACHING_SHIPWRIGHT, RECOVERING_BOAT, SUMMONING_BOAT, TAKING_HELM, SAILING,
        STOPPING, ARRIVED, BLOCKED, CANCELLED, PAUSED, SUSPENDED
    }
    public static final class View {
        public final Status status;
        public final String detail, port, failure, walkerVersion;
        public final SeaPoint destination, position;
        public final long startedMillis, chartedTiles, etaSeconds;
        public final int recoveries;
        public final List<SeaPoint> route, localRoute;
        public final String navigationMode, routeSource;
        public final double speed;
        public final int replans, stops;
        View(Status status, String detail, String port, String failure, String walkerVersion,
             SeaPoint destination, SeaPoint position, long startedMillis, long chartedTiles,
             int recoveries, List<SeaPoint> route, long etaSeconds) {
            this(status,detail,port,failure,walkerVersion,destination,position,startedMillis,chartedTiles,
                    recoveries,route,etaSeconds,route,"Planning","Observed local chart",0,0,0);
        }
        View(Status status,String detail,String port,String failure,String walkerVersion,SeaPoint destination,
             SeaPoint position,long startedMillis,long chartedTiles,int recoveries,List<SeaPoint> route,long etaSeconds,
             List<SeaPoint> localRoute,String navigationMode,String routeSource,double speed,int replans,int stops) {
            this.localRoute=Collections.unmodifiableList(new ArrayList<>(localRoute));
            this.navigationMode=navigationMode;this.routeSource=routeSource;this.speed=speed;this.replans=replans;this.stops=stops;
            this.status = status; this.detail = detail; this.port = port; this.failure = failure;
            this.walkerVersion = walkerVersion; this.destination = destination; this.position = position;
            this.startedMillis = startedMillis; this.chartedTiles = chartedTiles; this.recoveries = recoveries; this.etaSeconds = etaSeconds;
            this.route = Collections.unmodifiableList(new ArrayList<>(route));
        }
    }
    private static final class Request {
        final long epoch; final WorldPoint destination; final NavigationSessions.Session session;
        Request(long epoch, WorldPoint destination, NavigationSessions.Session session) {
            this.epoch = epoch; this.destination = destination; this.session = session;
        }
    }
    private final EfficientSailingConfig config;
    private final PluginManager plugins;
    private final VoyageLog log = new VoyageLog();
    private final MicrobotSailingFacade io;
    private final SailingWalkerJourney walker;
    private final ScheduledExecutorService control = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "EfficientSailing-control"));
    private final ExecutorService planning = Executors.newSingleThreadExecutor(r -> daemon(r, "EfficientSailing-port-planner"));
    private final ExecutorService seaPlanning=Executors.newSingleThreadExecutor(r -> daemon(r,"EfficientSailing-voyage-planner"));
    private volatile Future<GlobalSeaPlanner.Result> voyageFuture;
    private volatile SeaReferenceMap referenceMap;
    private long voyageFutureEpoch,lastVoyagePlan;
    private SeaGrid seaSeed=SeaCollisionSeed.empty();
    private boolean voyageDirty=true;
    private static final class ReferenceHolder {static final SeaReferenceMap MAP=new SeaReferenceMap();}
    private final AtomicLong epoch = new AtomicLong();
    private final NavigationSessions sessions = new NavigationSessions();
    private final NavigationSessions.Caller mapCaller = sessions.registerCaller(this, () -> true);
    private volatile NavigationSessions.Session activeSession;
    private final AtomicReference<Request> pending = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(), dumpRequested = new AtomicBoolean(), walkerTakeover = new AtomicBoolean();
    private final AtomicBoolean userCancelled = new AtomicBoolean();
    private final AtomicReference<NavigationSessions.Session> cancelledSession = new AtomicReference<>();
    private final AtomicReference<String> gameMessage = new AtomicReference<>();
    private final BankActionAck bankAction = new BankActionAck();
    private final OceanAtlas atlas = new OceanAtlas();
    private volatile SeaGrid mapChart = atlas.snapshot(null, Collections.emptyList(),seaSeed);
    private final Path atlasPath = log.directory.resolve("ocean-atlas-v1.bin");
    private volatile View view = new View(Status.IDLE, "Right-click world map: Sail here", "-", "", "not connected", null, null, 0, 0, 0, Collections.emptyList(), -1);
    private volatile boolean closing;
    private volatile Status status = Status.IDLE;
    private volatile long activeEpoch;
    private volatile boolean stoppingPermission;
    private long started, phaseStarted, nextAction, lastAtlasSave, mergedTick = Long.MIN_VALUE, boatMissingSince;
    private int helmLosses, attempts, lastHp = -1, stopTicks, stopSnapshotTick = -1, bankWithdrawAttempts, portIndex;
    private SeaPoint goal;
    private SeaPose stoppedPosition;
    private SeaPilot pilot;
    private String detail = "Idle", failure = "", lastDecision = "", lastUi = "", hazardText = "";
    private List<HazardZone> manualHazards = Collections.emptyList();
    private List<PortCatalog.Candidate> candidates = Collections.emptyList();
    private PortCatalog.Candidate selected;
    private Future<List<PortCatalog.Candidate>> portFuture;
    private boolean boardingBeforeRecovery, recoveryStarted, boatSelected, paymentSubmitted, recoveryFinished;
    private Status walkCompletion, stopResult = Status.CANCELLED;
    private boolean localBoardAttempted, localBoarding, needRecovery, recoveryOnly, fundingForTravel;
    private boolean summonIssued, summonSelected, lostBoatRecovery;
    private int summonInitialCount, summonInitialTick, tabletWithdrawAttempts, lastSeaTick = -1;
    private long summonStarted, staleSince;
    private long walkerRetrySince;
    private String knownBoatLocation = "";
    private volatile boolean explicitlyPaused;
    private volatile long heldSince;
    private int heldTick = -1;
    private WorldPoint heldWalkTarget;
    private boolean heldBankWalk, heldWalkerReleased;

    EfficientSailingController(Client client, EfficientSailingConfig config, PluginManager plugins, EfficientWalker landWalker) {
        this.config = config; this.plugins = plugins;
        io = new MicrobotSailingFacade(client, config, this::note);
        walker = new SailingWalkerJourney(landWalker, this::note);
        io.permit(() -> false);
    }
    void start() {
        // Decompress advisory geography away from both the client and control loops.
        seaPlanning.execute(() -> {
            try {referenceMap=ReferenceHolder.MAP;}
            catch(RuntimeException ex) {note("reference-map unavailable: "+ex.getMessage());}
        });
        control.execute(() -> {
            try {seaSeed=SeaCollisionSeed.bundled();} catch(RuntimeException ex) {note("bundled-chart rejected: "+ex.getMessage());}
            // Read-only import; never alters another plugin's collision cache.
            if(config.importChartPlotter()) {
                Path text=log.directory.resolve("chart-plotter-collision.txt");
                Path binary=log.directory.getParent().resolve("chart-plotter/collision.bin");
                Path input=java.nio.file.Files.isRegularFile(text)?text:binary;
                if(java.nio.file.Files.isRegularFile(input))try {
                    SeaCollisionSeed imported=SeaCollisionSeed.read(input);
                    seaSeed=SeaCollisionSeed.fallback(imported,seaSeed);
                    note("chart-seed loaded "+imported.source+" chunks="+imported.chunkCount());
                } catch(Exception ex) {note("optional-chart rejected: "+ex.getMessage());}
            }
            if (config.persistAtlas()) try { atlas.load(atlasPath); } catch (Exception ex) { note("atlas-load rejected: " + ex.getMessage()); }
            mapChart = atlas.snapshot(null, Collections.emptyList(),seaSeed);
        });
        control.scheduleWithFixedDelay(this::guardedTick, 0, 150, TimeUnit.MILLISECONDS);
    }
    /** True means accepted for processing, NOT successfully sailed or arrived. */
    public synchronized boolean sailTo(WorldPoint target) {
        if (closing || target == null || target.getPlane() != 0 || target.getX() < 1 || target.getY() < 1
                || target.getX() > 16383 || target.getY() > 16383) return false;
        return request(mapCaller, new SeaPoint(target.getX(), target.getY())).accepted;
    }
    @Override public NavigationSessions.Caller registerCaller(Object pluginIdentity, java.util.function.BooleanSupplier inputSuspended) {
        return sessions.registerCaller(pluginIdentity, inputSuspended);
    }
    @Override public synchronized NavigationSessions.Result request(NavigationSessions.Caller caller, SeaPoint target) {
        NavigationSessions.Result result = sessions.request(caller, target);
        if (!result.accepted) return result;
        Request queued = pending.get();
        if (result.session == activeSession || queued != null && queued.session == result.session) return result;
        long token = epoch.incrementAndGet();
        explicitlyPaused = false;
        userCancelled.set(false);
        pending.set(new Request(token, new WorldPoint(target.x, target.y, 0), result.session));
        cancelled.set(false); cancelledSession.set(null);
        return result;
    }
    @Override public NavigationSessions.Snapshot status(NavigationSessions.Session session) { return sessions.status(session); }
    @Override public synchronized boolean cancel(NavigationSessions.Session session) {
        if (!sessions.cancel(session)) return false;
        epoch.incrementAndGet(); pending.set(null); cancelledSession.set(session); cancelled.set(true); return true;
    }
    @Override public synchronized boolean callerShutdown(NavigationSessions.Caller caller) {
        NavigationSessions.Session current = sessions.activeSession();
        boolean owned = sessions.owns(current, caller);
        if (!sessions.callerShutdown(caller)) return false;
        if (owned && current != null && sessions.status(current).state == NavigationSessions.State.STOPPING) {
            epoch.incrementAndGet(); pending.set(null); cancelledSession.set(current); cancelled.set(true);
        }
        return true;
    }
    /** User-facing cancel yields immediately. Session cancellation retains observed-stop cleanup. */
    public synchronized void cancel() {
        epoch.incrementAndGet(); pending.set(null);
        sessions.manualTakeover("Cancelled by user; Sailing yielded control without stopping the boat");
        cancelled.set(false); cancelledSession.set(null);
        explicitlyPaused = false; stoppingPermission = false;
        io.permit(() -> false); walker.permit(() -> false);
        userCancelled.set(true);
        View previous = view;
        view = new View(Status.CANCELLED, "Cancelled; you control the boat", previous.port, "", previous.walkerVersion,
                null, previous.position, previous.startedMillis, previous.chartedTiles, previous.recoveries,
                Collections.emptyList(), -1);
    }
    /** Read-only map classification over immutable chart snapshots; never scans the live scene. */
    public boolean isOceanDestination(WorldPoint target) {
        SailingSnapshot latest = io.latest();
        SceneChart live = latest != null && latest.loggedIn && latest.onBoat
                && MicrobotSailingFacade.now() - latest.capturedMillis <= 2200 ? latest.chart : null;
        return !closing && SailingMapDestinationPolicy.ocean(target, mapChart, live,config.useReferenceChart()?referenceMap:null);
    }

    public Status getStatus() { return view.status; }
    public String getFailure() { return view.failure; }
    public WorldPoint getDestination() { SeaPoint p = view.destination; return p == null ? null : new WorldPoint(p.x, p.y, 0); }
    public boolean isBusy() { return busyStatus(view.status); }
    public boolean isExplicitlyPaused() { return explicitlyPaused; }
    public synchronized void togglePause() {
        NavigationSessions.Session current = sessions.activeSession();
        if (current == null || sessions.status(current).state == NavigationSessions.State.STOPPING) return;
        explicitlyPaused = !explicitlyPaused;
        if (explicitlyPaused) sessions.hold(current, "Paused by start/pause control; press it again to resume");
    }
    public View getView() { return view; }
    public void exportDiagnostics() { dumpRequested.set(true); }
    private volatile String snapshotFailure = "";
    void onGameTick() {
        try { io.capture(); snapshotFailure = ""; }
        catch (RuntimeException ex) {
            io.invalidate();
            String reason = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            if (!reason.equals(snapshotFailure)) note("snapshot-error: " + reason);
            snapshotFailure = reason;
        }
    }
    void onSceneLoading() { io.invalidate(); snapshotFailure = ""; }
    void onWalkerUserCommand() { walkerTakeover.set(true); }
    void onGameMessage(String message) { gameMessage.set(message); }

    private void guardedTick() {
        if (net.runelite.client.plugins.microbot.droefficientwalker.compat.DroLoginReadiness.shouldWaitForLogin()) return;
        try { tick(); }
        catch (Throwable ex) {
            if (ex instanceof VirtualMachineError) throw (VirtualMachineError) ex;
            note("exception " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            if (status == Status.STOPPING) {
                failure = "Stop NOT confirmed after control error: " + ex.getMessage();
                stopResult = Status.BLOCKED; detail = failure; stoppingPermission = false;
                sessions.confirmRelease(activeSession, false, failure);
                phase(Status.BLOCKED, failure);
            } else stop(Status.BLOCKED, "Controller error: " + ex.getMessage());
        } finally { io.endStep(); publish(); }
    }
    private void tick() throws Exception {
        long now = MicrobotSailingFacade.now();
        if (closing) return;
        if (userCancelled.getAndSet(false)) {
            cancelPlanner();
            try { walker.cancelOwned(); } catch (RuntimeException ex) { note("cancelled land handoff: " + ex.getMessage()); }
            heldSince = 0; pilot = null; goal = null;
            phase(Status.CANCELLED, "Cancelled by user; Sailing yielded control without stopping the boat");
            return;
        }
        Request request = pending.getAndSet(null);
        if (request != null && request.epoch == epoch.get()) begin(request, now);
        if (walkerTakeover.getAndSet(false)) {
            walker.userTookOver();
            if (busyStatus(status)) { cancelPlanner(); yieldNavigation("Yielded to a user-issued Walker command"); }
        }
        if (cancelled.getAndSet(false)) {
            NavigationSessions.Session requested = cancelledSession.getAndSet(null);
            if (requested != null && requested == sessions.activeSession()) {
                activeSession = requested; activeEpoch = epoch.get();
                stop(Status.CANCELLED, "Navigation cancellation requested");
            }
        }
        bindDispatch();
        SailingSnapshot s = io.latest();
        if (dumpRequested.getAndSet(false)) dump(s);
        // Chart observation is read-only and also runs while the navigation module is idle.
        if (s != null && s.loggedIn && s.onBoat && s.chart != null
                && now - s.capturedMillis <= 2200 && s.tick != mergedTick) {
            long revision = atlas.revision();
            atlas.merge(s.chart); mergedTick = s.tick;
            if (atlas.revision() != revision) mapChart = atlas.snapshot(null, Collections.emptyList(),seaSeed);
            if (config.persistAtlas() && now - lastAtlasSave > 30000) { atlas.save(atlasPath); lastAtlasSave = now; }
        }
        if (!busyStatus(status)) return;
        if (activeSession != null && sessions.status(activeSession).released) {
            cancelPlanner();
            try { walker.cancelOwned(); } catch (RuntimeException ex) { note("released handoff cancellation: " + ex.getMessage()); }
            yieldNavigation(sessions.status(activeSession).detail); return;
        }
        if (activeEpoch != epoch.get() && status != Status.STOPPING) return;
        if (status != Status.STOPPING && sessions.activeSession() != null
                && !sessions.ownerSuspended(activeSession)) {
            cancelPlanner();
            try { walker.cancelOwned(); } catch (RuntimeException ex) { note("handoff cancellation: " + ex.getMessage()); }
            yieldNavigation("Calling plugin resumed input before navigation release; boat stop is unconfirmed");
            return;
        }
        if (status == Status.STOPPING) explicitlyPaused = false; // Cancellation wins over explicit pause.
        if (pauseOrResume(s, now)) return;
        if (s == null || !s.loggedIn || now - s.capturedMillis > 2200) {
            if (staleSince == 0) staleSince = now;
            detail = "Waiting for a fresh logged-in game snapshot";
            if (now - staleSince > (status == Status.STOPPING ? 10000 : 30000)) {
                failure = snapshotFailure.isEmpty()
                    ? "No fresh game snapshot. Boat stop NOT confirmed; inspect the client manually."
                    : "Sailing game snapshot failed: " + snapshotFailure + "; boat stop NOT confirmed.";
                cancelPlanner();
                try { walker.cancelOwned(); } catch (RuntimeException ignored) { }
                stoppingPermission = false;
                sessions.requestRelease(activeSession, NavigationSessions.State.FAILED, failure);
                sessions.confirmRelease(activeSession, false, failure);
                phase(Status.BLOCKED, failure); dump(s);
            }
            return;
        }
        staleSince = 0;
        if (status != Status.STOPPING && now - started > 45 * 60_000L) { stop(Status.BLOCKED, "Voyage duration limit reached"); return; }
        if (!config.hazardZones().equals(hazardText)) {
            manualHazards = HazardZone.parse(config.hazardZones()); hazardText = config.hazardZones(); voyageDirty=true;
        }
        String competitor = competitor();
        if (competitor != null) {
            cancelPlanner();
            try { walker.cancelOwned(); } catch (RuntimeException ignored) { }
            yieldNavigation("Another boat controller can issue input: " + competitor + ". Suspend it before requesting navigation.");
            return;
        }
        if (status == Status.STOPPING) { tickStop(s, now); return; }
        if (s.mapOpen) {
            if (act(now, 900)) io.closeMap();
            detail = "Closing world map before input";
            return;
        }
        switch (status) {
            case VALIDATING:
                if (s.boatType.startsWith("unsupported:")) { stop(Status.BLOCKED, "Unsupported boat type: " + s.boatType); break; }
                if (s.boatType.equals("pending-deck")) {
                    detail = "Waiting for boat world-view; land routing is disabled while on a deck";
                    if (now - phaseStarted > 12000) stop(Status.BLOCKED, "Boat cache not confirmed on this world-view");
                    break;
                }
                if (s.onBoat) { beginSea(s); break; }
                // Fast, non-destructive local boarding wins over bank/funding/remote-port planning.
                if (!localBoardAttempted) {
                    localBoardAttempted = true;
                    List<PortCatalog.Port> eligible = PortCatalog.shortlist(config, s.sailingLevel,
                            new SeaPoint(s.player.getX(), s.player.getY()), goal, recoveryOnly);
                    PortCatalog.Port nearby = eligible.isEmpty() ? null : io.nearbyBoardingPort(s, eligible.get(0).key);
                    if (nearby != null) {
                        WorldPoint plank = io.gangplankPoint(nearby.gangplankId);
                        selected = new PortCatalog.Candidate(nearby, plank, 0, nearby.sea.distance(goal), "local gangplank", true);
                        if (plank == null) { stop(Status.BLOCKED, "Nearby gangplank unloaded before boarding; retry Sail here"); break; }
                        localBoarding = true;
                        note("local-boarding-first port=" + nearby.key + " gangplank=" + plank);
                        if (plank != null && s.player.distanceTo2D(plank) <= 4) phase(Status.AT_PORT, "Boarding the boat beside you");
                        else if (walker.connect() && !walker.busy()) {
                            WorldPoint approach = walker.dockApproach(s.player, plank);
                            if (approach == null || !walker.walkTo(approach)) { stop(Status.BLOCKED, "No reachable standable local dock approach"); break; }
                            walkCompletion = Status.AT_PORT;
                            phase(Status.APPROACHING_GANGPLANK, "Walker: nearby gangplank");
                        } else stop(Status.BLOCKED, "Enable an idle Efficient Walker to approach the nearby gangplank");
                        break;
                    }
                }
                if (!walker.connect()) { stop(Status.BLOCKED, "Enable Efficient Walker for land travel"); break; }
                if (walker.busy() && !walker.ownsRoute()) { stop(Status.BLOCKED, "Efficient Walker is busy with another journey"); break; }
                note("integration walker=" + walker.version() + " " + s.diagnostic());
                fundingForTravel = true;
                if (needsSupplies(s)) requestBank();
                else phase(Status.COMPARING_PORTS, "Finding a reachable launch port near the sea destination");
                break;
            case WALKING_TO_BANK:
            case WALKING_TO_PORT:
            case APPROACHING_GANGPLANK:
            case APPROACHING_SHIPWRIGHT:
                tickWalk(s, now); break;
            case BANKING:
                tickBank(s, now); break;
            case COMPARING_PORTS:
                tickPortComparison(s, now); break;
            case AT_PORT:
                tickDock(s, now); break;
            case BOARDING:
                tickBoarding(s, now); break;
            case NEEDS_RECOVERY_SETTINGS:
                prepareRecovery(s, now); break;
            case SUMMONING_BOAT:
                tickSummon(s, now); break;
            case RECOVERING_BOAT:
                tickRecovery(s, now); break;
            case TAKING_HELM:
                if (!s.onBoat) { waitForBoat(s, now); break; }
                if (s.navigating) {
                    if (pilot == null) {
                        pilot = new SeaPilot(new SeaPilot.Settings(config.arrivalRadius(), config.lookAhead(), 0,
                            config.maxSearchNodes(), config.stallSeconds() * 1000L, config.noProgressSeconds() * 1000L,
                            config.commandMillis(), 75, config.anticipationTiles(),config.cruiseClearanceTiles(),config.steeringLatencyTicks()), goal, now);
                        pilot.seedCruiseSpeed(s.fullSpeed>0?s.fullSpeed:io.observedCruiseSpeed());
                        requestVoyagePlan(s,now);
                    }
                    lastHp = s.hitpoints;
                    note("helm-ready " + s.diagnostic());
                    phase(Status.SAILING, "Helm acquired; charting sea route");
                } else if (now - phaseStarted > 16000 || attempts >= 6) stop(Status.BLOCKED, "Navigate/helm acquisition was not confirmed");
                else if (act(now, 1800)) { attempts++; io.navigate(); }
                break;
            case SAILING:
                tickSea(s, now); break;
            default: break;
        }
    }
    private void begin(Request request, long now) {
        cancelPlanner();
        if (!MicrobotSailingFacade.humanOrPaused())
            try { walker.cancelOwned(); } catch (RuntimeException ex) { note("old journey release: " + ex.getMessage()); }
        activeEpoch = request.epoch; activeSession = request.session;
        goal = new SeaPoint(request.destination.getX(), request.destination.getY());
        started = now; failure = ""; voyageDirty=true;lastVoyagePlan=0; pilot = null; selected = null; candidates = Collections.emptyList(); portIndex = 0;
        stoppingPermission = false; boardingBeforeRecovery = false; recoveryStarted = false; boatSelected = false;
        paymentSubmitted = false; recoveryFinished = false; bankWithdrawAttempts = 0; boatMissingSince = 0;
        lastHp = -1; lastDecision = ""; lastUi = ""; helmLosses = 0; gameMessage.set(null);
        localBoardAttempted = false; localBoarding = false;
        needRecovery = true;
        fundingForTravel = false;
        recoveryOnly = !config.useSummonBoatTablet();
        summonReadyTick = -1; summonBoardingAt = 0;
        summonIssued = false; summonSelected = false; lostBoatRecovery = false; summonInitialCount = 0; tabletWithdrawAttempts = 0;
        summonStarted = 0; staleSince = 0; lastSeaTick = -1;
        walkerRetrySince = 0; bankAction.clear();
        heldSince = 0; heldTick = -1;
        heldWalkTarget = null; heldBankWalk = false; heldWalkerReleased = false;
        knownBoatLocation = "";
        manualHazards = HazardZone.parse(config.hazardZones()); hazardText = config.hazardZones(); voyageDirty=true;
        log.open(); note("request destination=" + goal + " version=" + VERSION);
        phase(Status.VALIDATING, "Validating journey");
    }
    private int reserve() {
        boolean npcRecovery = !useTabletRecall() && !recoveryFinished && (needRecovery || fundingForTravel);
        return config.charterReserve() + (npcRecovery ? config.maxRecoveryFee() : 0);
    }
    private boolean needsSupplies(SailingSnapshot s) {
        return s.coins < reserve() || (needRecovery || fundingForTravel) && useTabletRecall() && !recoveryFinished && s.summonTablets < 1;
    }
    private boolean useTabletRecall() { return config.useSummonBoatTablet() && !lostBoatRecovery; }
    private void requestBank() {
        SailingSnapshot current = io.latest();
        if (current != null && current.bankOpen && !current.onBoat) { phase(Status.BANKING, "Funding from open bank"); return; }
        if (!walker.connect() || !walker.walkToBank()) { stop(Status.BLOCKED, "Walker did not accept nearest-bank request"); return; }
        walkCompletion = Status.BANKING;
        phase(Status.WALKING_TO_BANK, "Efficient Walker: bank for coins" + (useTabletRecall() ? " and Summon boat tablet" : ""));
    }
    private void tickWalk(SailingSnapshot s, long now) {
        if (s.onBoat) { beginSea(s); return; }
        SailingWalkerJourney.JourneyState journey = walker.observe();
        detail = "Efficient Walker: " + journey.phase;
        if (walker.arrivedOwned(journey, s.player)) {
            // The maintained Walker's synthetic arrival watchdog stays latched until
            // cancellation/reset. Clear only this owned completion before another request.
            walker.cancelOwned();
            phase(walkCompletion, "Walker handoff complete");
            return;
        }
        if (!walker.ownershipIntact(journey)) {
            walker.userTookOver(); cancelPlanner(); yieldNavigation("Walker route replaced/cancelled; no competing input"); return;
        }
        String state = journey.status;
        if (status == Status.WALKING_TO_BANK && s.bankOpen) {
            walker.cancelOwned();
            phase(walkCompletion, "Walker handoff complete");
            return;
        }
        if (state.equals("ARRIVED")) {
            // Walker completion can precede the player's next copied GameTick snapshot.
            if (walkerRetrySince == 0) walkerRetrySince = now;
            if (now - walkerRetrySince < 3000) { detail = "Waiting for fresh player position after Walker arrival"; return; }
            walker.releaseCompleted(); stop(Status.BLOCKED, "Walker reported arrival away from the owned approach"); return;
        }
        if (WalkerJourneyPolicy.retryTransition(state, journey.failure)) {
            if (walkerRetrySince == 0) walkerRetrySince = now;
            if (now - walkerRetrySince < 3000) { detail = "Walker is updating its dock approach"; return; }
        } else walkerRetrySince = 0;
        if (state.equals("BLOCKED")) {
            String reason = journey.failure; walker.releaseCompleted();
            if (status == Status.WALKING_TO_PORT && nextPort("Walker blocked: " + reason)) return;
            stop(Status.BLOCKED, "Efficient Walker blocked: " + reason); return;
        }
        if (state.equals("IDLE")) { walker.releaseCompleted(); stop(Status.BLOCKED, "Walker stopped before confirmed arrival"); return; }
        if (now - phaseStarted > 300000) stop(Status.BLOCKED, "Walker handoff timed out (5 minutes)");
    }
    private void tickBank(SailingSnapshot s, long now) {
        if (now - phaseStarted > 45000) { stop(Status.BLOCKED, "Bank funding timeout"); return; }
        BankActionAck.Result acknowledgement = bankAction.observe(bankAction.itemId() == 995 ? s.coins : s.summonTablets, s.tick, now);
        if (acknowledgement == BankActionAck.Result.WAITING) { detail = "Waiting for inventory acknowledgement of withdrawal"; return; }
        if (acknowledgement == BankActionAck.Result.TIMED_OUT) {
            stop(Status.BLOCKED, "Withdrawal not confirmed; inspect inventory before retrying the journey"); return;
        }
        boolean missingTablet = (needRecovery || fundingForTravel) && useTabletRecall() && !recoveryFinished && s.summonTablets < 1;
        int missingCoins = Math.max(0, reserve() - s.coins);
        if (missingCoins == 0 && !missingTablet) {
            if (s.bankOpen) { if (act(now, 900)) io.closeBank(); }
            else if (selected != null) startPortWalk();
            else phase(Status.COMPARING_PORTS, "Supplies ready; comparing ports");
            return;
        }
        int slots = (missingCoins > 0 && s.coins == 0 ? 1 : 0) + (missingTablet ? 1 : 0);
        if (s.freeSlots < slots) { stop(Status.BLOCKED, slots + " free inventory slots needed for travel supplies; no items were deposited"); return; }
        if (!s.bankOpen) {
            if (attempts >= 5) { stop(Status.BLOCKED, "Nearby bank interaction not found after Walker arrival"); return; }
            if (act(now, 1800)) { attempts++; io.openNearbyBank(); }
            return;
        }
        if (s.bankCoins < 0 || s.bankSummonTablets < 0) { detail = "Waiting for bank inventory"; return; }
        // Check both supplies before withdrawing either; do not silently switch to a different recovery method.
        if (missingTablet && s.bankSummonTablets < 1) { stop(Status.BLOCKED, "No Summon boat tablet (31441) in bank. Teleport to boat is a different item"); return; }
        if (s.bankCoins < missingCoins) { stop(Status.BLOCKED, "Bank has insufficient coins for configured transport reserve"); return; }
        if (missingTablet) {
            if (tabletWithdrawAttempts >= 3) { stop(Status.BLOCKED, "Summon boat tablet withdrawal was not acknowledged"); return; }
            if (act(now, 1800)) {
                tabletWithdrawAttempts++;
                bankAction.begin(MicrobotSailingFacade.SUMMON_BOAT_TABLET, s.summonTablets, 1, s.tick, now, 10000);
                note("withdraw one Summon boat tablet; awaiting inventory"); io.withdrawSummonTablet();
            }
        } else if (missingCoins > 0) {
            if (bankWithdrawAttempts >= 3) { stop(Status.BLOCKED, "Coin withdrawal was not acknowledged"); return; }
            if (act(now, 1800)) {
                bankWithdrawAttempts++;
                bankAction.begin(995, s.coins, missingCoins, s.tick, now, 10000);
                note("withdraw missing coins=" + missingCoins + "; awaiting inventory"); io.withdrawCoins(missingCoins);
            }
        }
    }
    private void tickPortComparison(SailingSnapshot s, long now) throws Exception {
        if (portFuture == null) {
            if (walker.busy()) { stop(Status.BLOCKED, "Walker became busy before port comparison"); return; }
            final long token = activeEpoch;
            final WorldPoint startPoint = s.player;
            final SeaPoint destination = goal;
            final int level = s.sailingLevel;
            portFuture = planning.submit(() -> {
                List<PortCatalog.Candidate> list = new ArrayList<>();
                Map<String, WorldPoint> overrides = PortCatalog.overrides(config.portOverrides());
                for (PortCatalog.Port port : PortCatalog.shortlist(config, level,
                        new SeaPoint(startPoint.getX(), startPoint.getY()), destination, recoveryOnly, needRecovery)) {
                    if (epoch.get() != token || Thread.currentThread().isInterrupted()) throw new CancellationException();
                    WorldPoint override = overrides.get(port.key);
                    SailingWalkerJourney.Estimate e = override == null
                            ? walker.estimateLoadedDock(startPoint, io.gangplankPoint(port.gangplankId)) : null;
                    if (e == null) e = walker.estimateNearestDock(port.sea, startPoint,
                            override == null ? PortCatalog.landGateway(port.key) : override,
                            () -> epoch.get() != token || closing);
                    if (e == null) { note("port rejected: no standable land approach to " + port.key); continue; }
                    boolean knownBoat = port.key.equals(knownBoatLocation);
                    PortCatalog.Candidate candidate = new PortCatalog.Candidate(port, e.approach, e.cost, port.sea.distance(destination), e.chain, knownBoat,
                            PortCatalog.selectionMode(config), 3, 0);
                    list.add(candidate); note("port candidate " + candidate);
                    // Keep the nearest choices for automatic fallback if the actual
                    // Walker journey later reports a blocked route. No cost search here.
                    if (list.size() >= 3) break;
                }
                list.sort(Comparator.comparingDouble(c -> c.total));
                return list;
            });
            return;
        }
        if (!portFuture.isDone()) {
            if (now - phaseStarted > 150000) stop(Status.BLOCKED, "Port selection timed out while finding a standable land approach");
            return;
        }
        List<PortCatalog.Candidate> result = portFuture.get(); portFuture = null;
        if (activeEpoch != epoch.get()) return;
        candidates = result; portIndex = 0;
        if (candidates.isEmpty()) { stop(Status.BLOCKED, "No eligible port has a standable land approach"); return; }
        selected = candidates.get(portIndex);
        startPortWalk();
    }
    private void startPortWalk() {
        SailingSnapshot current = io.latest();
        WorldPoint plank = io.gangplankPoint(selected.port.gangplankId);
        boolean local = current != null && current.player != null && plank != null
                && current.player.getPlane() == plank.getPlane() && current.player.distanceTo2D(plank) <= 36;
        WorldPoint approach = local ? walker.dockApproach(current.player, plank) : selected.approach;
        if (approach == null) { stop(Status.BLOCKED, "No reachable standable dock approach from current position"); return; }
        note("port selected " + selected + " landApproach=" + approach);
        if (!(local ? walker.walkToPreparedLocal(approach) : walker.walkTo(approach))) { stop(Status.BLOCKED, "Walker rejected selected port"); return; }
        walkCompletion = Status.AT_PORT;
        phase(Status.WALKING_TO_PORT, "Efficient Walker to " + selected.port.name);
    }
    private boolean nextPort(String reason) {
        note("port failed " + (selected == null ? "none" : selected.port.key) + ": " + reason);
        // Never try another charge after a payment was submitted.
        if (paymentSubmitted || summonIssued && !lostBoatRecovery || ++portIndex >= Math.min(3, candidates.size())) return false;
        selected = candidates.get(portIndex); boardingBeforeRecovery = false; recoveryStarted = false; boatSelected = false;
        startPortWalk(); return true;
    }
    private void tickDock(SailingSnapshot s, long now) {
        if (s.onBoat) { beginSea(s); return; }
        if (s.bankOpen) { if (act(now, 900)) io.closeBank(); return; }
        if (selected == null) { stop(Status.BLOCKED, "No selected port"); return; }
        WorldPoint gangplank = io.gangplankPoint(selected.port.gangplankId);
        if (gangplank == null) {
            if (now - phaseStarted > 12000 && (recoveryFinished || !nextPort("Native sailing gangplank is not loaded"))) stop(Status.BLOCKED, "Sailing gangplank not found at selected port");
            return;
        }
        if (s.player.distanceTo2D(gangplank) > 4) {
            WorldPoint approach = walker.connect() ? walker.dockApproach(s.player, gangplank) : null;
            if (approach == null || !walker.walkTo(approach)) { stop(Status.BLOCKED, "No reachable standable sailing gangplank approach"); return; }
            walkCompletion = Status.AT_PORT; phase(Status.APPROACHING_GANGPLANK, "Walker: approach sailing gangplank"); return;
        }
        if (needRecovery && !recoveryFinished) {
            phase(Status.NEEDS_RECOVERY_SETTINGS, "Back at dock; prepare the already-required recovery"); return;
        }
        if (act(now, 1000)) {
            if (io.board(selected.port.gangplankId)) {
                boardingBeforeRecovery = !recoveryFinished;
                phase(Status.BOARDING, "Board requested; waiting for boat world-view");
            } else if (++attempts >= 3) {
                if (recoveryFinished) stop(Status.BLOCKED, "Board action unavailable after recovery");
                else boardingUnavailable("No boat can be boarded at this dock");
            }
        }
    }
    private void tickBoarding(SailingSnapshot s, long now) {
        if (s.onBoat) { beginSea(s); return; }
        MicrobotSailingFacade.UiState ui = io.ui();
        lastUi = ui.relevantText(false);
        String message = gameMessage.get();
        if (BoardingMessage.lostAtSea(message) || BoardingMessage.lostAtSea(lastUi)) {
            if (paymentSubmitted || lostBoatRecovery) {
                stop(Status.BLOCKED, "Boat still reported lost after shipwright recovery; no second payment or tablet will be used"); return;
            }
            lostBoatRecovery = true; recoveryOnly = true; needRecovery = true; recoveryFinished = false;
            localBoarding = false; recoveryStarted = false; boatSelected = false; knownBoatLocation = "";
            gameMessage.set(null);
            phase(Status.NEEDS_RECOVERY_SETTINGS, "Boat reported lost at sea; recover through a shipwright within the configured fee cap");
            return;
        }
        if (!ui.boatGroups(false).isEmpty() && config.boatName().trim().isEmpty()) {
            detail = "Set Exact boat name to choose your boat from this boarding list";
            if (now - phaseStarted > 60000) stop(Status.BLOCKED, detail);
            return;
        }
        if (act(now, 900)) {
            lastUi = ui.relevantText(false);
            boolean chosen = io.chooseBoat(ui, false);
            // Tablet boarding is requested once; ordinary boarding/recovery retries stay bounded.
            if ((!summonIssued || lostBoatRecovery) && !chosen && ui.boatGroups(false).isEmpty() && !ui.hasContinue && !ui.hasOptions
                && !s.moving && now >= boardingRetryAt && attempts < 2
                && io.atDock(selected.port.gangplankId)) {
                attempts++; boardingRetryAt = now + 3500;
                io.board(selected.port.gangplankId);
            }
        }
        Optional<String> dockedElsewhere = BoardingMessage.dockedElsewhere(message);
        if (dockedElsewhere.isPresent()) {
            knownBoatLocation = dockedElsewhere.get();
            note("Observed boat location=" + knownBoatLocation);
        }
        boolean absent = message != null && message.toLowerCase(Locale.ROOT).contains("boat")
                && (message.toLowerCase(Locale.ROOT).contains("no boat docked")
                    || message.toLowerCase(Locale.ROOT).contains("don't have a boat")
                    || message.toLowerCase(Locale.ROOT).contains("do not have a boat"));
        if (absent || dockedElsewhere.isPresent() || now - phaseStarted > 10000) {
            if (boardingBeforeRecovery && !paymentSubmitted && !summonIssued) boardingUnavailable("Boarding not confirmed; prepare recovery");
            else stop(Status.BLOCKED, "Boat boarding not confirmed after recovery; no second payment/tablet will be used");
        }
    }
    private void boardingUnavailable(String reason) {
        needRecovery = true; gameMessage.set(null);
        localBoarding = false;
        phase(Status.NEEDS_RECOVERY_SETTINGS, reason);
    }
    private void prepareRecovery(SailingSnapshot s, long now) {
        if (selected == null) { stop(Status.BLOCKED, "No selected recovery dock"); return; }
        if (config.boatName().trim().isEmpty()) {
            detail = "Set Exact boat name before summoning or recovery"; return;
        }
        if (lostBoatRecovery) {
            MicrobotSailingFacade.UiState ui = io.ui();
            if (BoardingMessage.lostAtSea(ui.relevantText(false))) {
                if (act(now, 900)) io.continueRecovery(ui);
                detail = "Closing lost-boat dialogue before shipwright travel"; return;
            }
        }
        if (useTabletRecall()) {
            if (needsSupplies(s)) { requestBank(); return; }
            phase(Status.SUMMONING_BOAT, "Summon boat at " + selected.port.name); return;
        }
        if (!PortCatalog.supportsNpcRecovery(selected.port.key)) {
            recoveryOnly = true; selected = null;
            if (needsSupplies(s)) requestBank();
            else phase(Status.COMPARING_PORTS, "Finding nearest registered NPC recovery port");
            return;
        }
        if (s.coins < config.maxRecoveryFee()) { requestBank(); return; }
        approachShipwright(s, now);
    }
    private void tickSummon(SailingSnapshot s, long now) {
        if (s.onBoat) { beginSea(s); return; }
        if (!useTabletRecall() || config.boatName().trim().isEmpty()) {
            stop(Status.BLOCKED, "Summon boat permission/settings changed"); return;
        }
        if (selected == null || !io.atDock(selected.port.gangplankId)) {
            stop(Status.BLOCKED, "Summon boat requires standing beside the selected port's loaded gangplank"); return;
        }
        if (s.bankOpen) { if (act(now, 900)) io.closeBank(); return; }
        if (!summonIssued) {
            if (s.summonTablets < 1) { stop(Status.BLOCKED, "Summon boat tablet missing at dock"); return; }
            if (attempts >= 3) { stop(Status.BLOCKED, "Could not interact with Summon boat tablet"); return; }
            if (act(now, 1200)) {
                attempts++;
                if (io.breakSummonTablet(selected.port.gangplankId)) {
                    summonIssued = true; summonStarted = now; summonInitialCount = s.summonTablets; summonInitialTick = s.tick;
                    gameMessage.set(null); note("summon-tablet requested ONCE at " + selected.port.key);
                }
            }
            return;
        }
        String message = gameMessage.get();
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean focusRejected = (lower.contains("teleport focus") || lower.contains("greater focus"))
                && (lower.contains("not") || lower.contains("need") || lower.contains("require") || lower.contains("no ") || lower.contains("can't"));
        if (focusRejected || lower.contains("mooring point") && lower.contains("must")) {
            stop(Status.BLOCKED, "Summon boat rejected: " + message + ". No second tablet will be used"); return;
        }
        if (now - summonStarted > 20000) {
            stop(Status.BLOCKED, "Summon boat was not confirmed. Check the boat name/focus and send the diagnostic UI; no repeat tablet"); return;
        }
        if (summonBoardingAt == 0 && !act(now, 700)) return;
        MicrobotSailingFacade.UiState ui = io.ui(); lastUi = ui.relevantText(true);
        if (summonBoardingAt == 0) {
            if (!summonSelected && io.chooseBoat(ui, true)) {
                summonSelected = true; detail = "Named boat selected; awaiting summon confirmation"; return;
            }
            if (summonSelected && io.confirmSummon(ui)) return;
            if (io.continueRecovery(ui)) return;
        }
        boolean closed = !ui.hasContinue && !ui.hasOptions && ui.boatGroups(true).isEmpty();
        boolean acknowledged = s.tick > summonInitialTick && s.summonTablets < summonInitialCount || lower.contains("boat is now docked")
                || lower.contains("boat has been summoned");
        if (closed && acknowledged && !s.moving && !io.summonCasting()) {
            // Tablet consumption can precede the server's completed recall/cast. Do not
            // overwrite it with Board while the spell or boat selection is still settling.
            if (summonReadyTick < 0) summonReadyTick = s.tick;
            if (s.tick - summonReadyTick < 2) {
                detail = "Summon consumed; waiting for cast and dock receipt to settle"; return;
            }
            if (summonBoardingAt == 0) summonBoardingAt = now + Rs2Random.betweenInclusive(350, 550);
            if (now < summonBoardingAt) {
                detail = "Summon settled; waiting 350-550 ms before boarding"; return;
            }
            recoveryFinished = true; localBoarding = false;
            phase(Status.AT_PORT, "Summon settled; boarding gangplank to verify boat");
        } else { summonReadyTick = -1; summonBoardingAt = 0; }
    }
    private boolean recoveryPermitted() {
        return !useTabletRecall() && !config.boatName().trim().isEmpty();
    }
    private void approachShipwright(SailingSnapshot s, long now) {
        if (selected == null || !PortCatalog.supportsNpcRecovery(selected.port.key) || s.shipwrightId < 0 || s.shipwrightPoint == null
                || new SeaPoint(s.shipwrightPoint.getX(), s.shipwrightPoint.getY()).distance(selected.port.sea) > 70) {
            if (now - phaseStarted > 15000 && !nextPort("No local shipwright at this dock")) stop(Status.BLOCKED, "No shipwright found at selected port");
            return;
        }
        if (s.player.distanceTo2D(s.shipwrightPoint) > 4) {
            if (!walker.walkTo(s.shipwrightPoint)) { stop(Status.BLOCKED, "Walker rejected shipwright approach"); return; }
            walkCompletion = Status.NEEDS_RECOVERY_SETTINGS;
            phase(Status.APPROACHING_SHIPWRIGHT, "Walker: approach shipwright"); return;
        }
        if (act(now, 1800)) {
            if (lostBoatRecovery ? io.beginRecovery(s.shipwrightId, true) : io.beginRecovery(s.shipwrightId)) {
                recoveryStarted = true; phase(Status.RECOVERING_BOAT, "Recover-boat requested; waiting for exact boat selection");
            } else if (++attempts >= 3) {
                if (!nextPort("Shipwright has no usable Recover-boat action")) stop(Status.BLOCKED, "Shipwright recovery action unavailable");
            }
        }
    }
    private void tickRecovery(SailingSnapshot s, long now) {
        if (!recoveryPermitted()) { phase(Status.NEEDS_RECOVERY_SETTINGS, "Recovery permission changed"); return; }
        if (!recoveryStarted) { stop(Status.BLOCKED, "Recovery dialogue has no owned initiating interaction"); return; }
        if (now - phaseStarted > 60000) { stop(Status.BLOCKED, "Unrecognized or stalled recovery UI; diagnostics recorded"); return; }
        if (!act(now, 1000)) return;
        MicrobotSailingFacade.UiState ui = io.ui(); lastUi = ui.relevantText(true);
        if (config.debug()) note("recovery-ui " + lastUi);
        if (!boatSelected && io.chooseBoat(ui, true)) { boatSelected = true; detail = "Selected exact configured boat"; return; }
        if (boatSelected && !paymentSubmitted && (lostBoatRecovery
                ? io.payRecovery(ui, config.maxRecoveryFee(), s.coins, true)
                : io.payRecovery(ui, config.maxRecoveryFee(), s.coins))) {
            paymentSubmitted = true; detail = "Recovery payment submitted once; waiting for completion"; return;
        }
        if (io.continueRecovery(ui)) return;
        String message = gameMessage.getAndSet(null);
        boolean dockedMessage = message != null && message.toLowerCase(Locale.ROOT).contains("boat is now docked");
        if (boatSelected && paymentSubmitted && (dockedMessage || (!ui.hasContinue && !ui.hasOptions && ui.boatGroups(true).isEmpty()))) {
            recoveryFinished = true; phase(Status.AT_PORT, "Recovery dialogue finished; verifying by boarding");
        }
    }
    private void beginSea(SailingSnapshot s) {
        if (walker.connect() && walker.busy() && !walker.ownsRoute()) {
            stop(Status.BLOCKED, "Walker is still active on another request; finish/cancel it before sailing"); return;
        }
        try { walker.cancelOwned(); } catch (RuntimeException ignored) { }
        boatMissingSince = 0;
        note("boat-handoff " + s.diagnostic());
        phase(Status.TAKING_HELM, "Acquire helm on the boarded vessel");
    }
    private void waitForBoat(SailingSnapshot s, long now) {
        if (boatMissingSince == 0) boatMissingSince = now;
        if (now - boatMissingSince > 3000) stop(Status.BLOCKED, "Boat world-view lost; no land/bank recovery was started automatically");
        else detail = "Waiting for boat cache/scene transition";
    }
    private void tickSea(SailingSnapshot s, long now) {
        if (s.tick == lastSeaTick) return;
        lastSeaTick = s.tick;
        if (!s.onBoat) { waitForBoat(s, now); return; }
        boatMissingSince = 0;
        if (!s.navigating) {
            if (++helmLosses > 3) { stop(Status.BLOCKED, "Repeated helm loss; another controller or interaction may be interfering"); return; }
            phase(Status.TAKING_HELM, "Helm lost; reacquire before sailing commands"); return;
        }
        if (config.stopOnDamage() && lastHp >= 0 && s.hitpoints < lastHp) pilot.fail("Player took damage at sea; manual inspection required");
        lastHp = s.hitpoints;
        if (s.chart == null || s.sea == null || s.pose==null || s.hull==null) {
            if (s.moving && act(now, 1000)) {
                if (s.backwards) io.stopReverse(); else io.sails(false);
            }
            detail = "No usable server pose, native hull bounds or top-level sailing collision scene";
            if (++attempts > 30) stop(Status.BLOCKED, detail);
            return;
        }
        List<HazardZone> zones = hazards(s, now);
        SeaGrid grid = atlas.snapshot(s.chart, zones,seaSeed);
        pollVoyagePlan();
        if(config.useReferenceChart()&&voyageFuture==null
                &&(voyageDirty||pilot.needsVoyageReplan()&&now-lastVoyagePlan>3000))requestVoyagePlan(s,now);
        if(voyageFuture!=null&&pilot.path().isEmpty()&&!s.moving) {
            detail="Planning full voyage before departure";return;
        }
        SeaPilot.Decision decision = pilot.step(grid,s.hull,s.pose,s.movementMode,s.fullSpeed,s.acceleration,
                s.headingAutoStarts,s.tick,atlas.knownTiles(),now,()->activeEpoch!=epoch.get()||closing);
        detail = decision.reason;
        String message = decision.action + " " + decision.reason + (decision.waypoint == null ? "" : " target=" + decision.waypoint);
        if (!message.equals(lastDecision)) {
            if (config.debug() || decision.action != SeaPilot.Action.WAIT)
                note(message + " sea=" + s.sea + " heading16=" + s.heading + " moving=" + s.moving);
            lastDecision = message;
        }
        if (activeEpoch != epoch.get()) return;
        boolean accepted = true;
        switch (decision.action) {
            case STEER: accepted = io.steer(decision.waypoint, decision.heading); break;
            case START: accepted = io.sails(true); break;
            case SLOW: accepted = io.slowForward(); break;
            case STOP: accepted = s.backwards ? io.stopReverse() : io.sails(false); break;
            case REVERSE:
                accepted = io.reverse();
                if (!accepted) pilot.fail("Reverse control unavailable; recovery was not started");
                break;
            case ARRIVED:
                if (!sessions.arrived(activeSession, decision.reason)) return;
                phase(Status.ARRIVED, decision.reason); note("arrival-confirmed " + s.diagnostic()); saveAtlas(); return;
            case BLOCKED: stop(Status.BLOCKED, decision.reason); return;
            default: return;
        }
        pilot.dispatched(decision,accepted);
        if (!accepted) {
            if (++attempts >= 5) stop(Status.BLOCKED, "Boat control action could not be issued/verified: " + decision.action+"; export sailing diagnostics for the observed side-panel actions");
        } else attempts = 0;
    }
    private void stop(Status result, String reason) {
        // A helper can finish after its session yielded and a new caller was accepted.
        // Its failure must not acquire or cancel that newer caller's queued voyage.
        if (activeSession != null && sessions.activeSession() != activeSession) return;
        if (status == Status.STOPPING) {
            if (result == Status.BLOCKED) { failure = reason; stopResult = result; }
            return; // Preserve the stop deadline; errors must not restart it forever.
        }
        cancelPlanner();
        sessions.requestRelease(activeSession, result == Status.BLOCKED ? NavigationSessions.State.FAILED : NavigationSessions.State.CANCELLED, reason);
        if (!MicrobotSailingFacade.humanOrPaused())
            try { walker.cancelOwned(); } catch (RuntimeException ex) { note("walker release: " + ex.getMessage()); }
        stopResult = result; failure = result == Status.BLOCKED ? reason : "";
        stoppingPermission = true; stopTicks = 0; stopSnapshotTick = -1; stoppedPosition = null;
        phase(Status.STOPPING, reason);
        bindDispatch();
    }
    private void tickStop(SailingSnapshot s, long now) {
        if (walker.ownsRoute()) walker.cancelOwned();
        if (!s.onBoat) {
            if ("none".equals(s.boatType)) { terminal(s); return; }
            if (now - phaseStarted > 10000) {
                failure = "Boat world-view unresolved. Stop NOT confirmed; take control manually.";
                detail = failure; stopResult = Status.BLOCKED; terminal(s);
            }
            return;
        }
        if (now - phaseStarted > 10000) {
            failure = "Stop NOT confirmed. Take control manually. " + detail;
            stopResult = Status.BLOCKED; detail = failure; terminal(s); return;
        }
        // Map cancellation reaches STOPPING before the normal map-close branch.
        // Expose the Sailing side panel before issuing its stop control; the next
        // snapshot must confirm that the map closed. Keep the same stop deadline.
        if (s.mapOpen && s.moving) {
            if (act(now, 900)) io.closeMap();
            detail = "Cancelling: closing world map before stopping the boat";
            return;
        }
        if (!s.moving) {
            if (s.tick != stopSnapshotTick) {
                stopTicks = s.pose != null && stoppedPosition!=null && s.pose.distance(stoppedPosition)<.05 ? stopTicks + 1 : 0;
                stoppedPosition = s.pose; stopSnapshotTick = s.tick;
            }
            if (stopTicks >= 2) terminal(s);
            return;
        }
        stopTicks = 0; stoppedPosition = null;
        if (s.navigating && act(now, 1200)) {
            if (s.backwards) io.stopReverse(); else io.sails(false);
        }
    }
    private void terminal(SailingSnapshot s) {
        stoppingPermission = false;
        boolean stopped = s != null && ("none".equals(s.boatType) || s.onBoat && !s.moving && stopTicks >= 2);
        sessions.confirmRelease(activeSession, stopped, detail);
        phase(stopResult, detail);
        saveAtlas();
        if (stopResult == Status.BLOCKED) dump(s);
    }
    private int summonReadyTick = -1;
    private long boardingRetryAt, summonBoardingAt;
    private boolean act(long now, long delay) {
        if (now < nextAction || activeEpoch != epoch.get() && !stoppingPermission) return false;
        nextAction = now + delay; return true;
    }
    private void phase(Status next, String message) {
        status = next; detail = message; phaseStarted = MicrobotSailingFacade.now(); attempts = 0; nextAction = 0;
        walkerRetrySince = 0;
        if (next == Status.BOARDING) boardingRetryAt = phaseStarted + 3500;
        note("phase=" + next + " detail=" + message);
        if (busyStatus(next) && next != Status.STOPPING) sessions.update(activeSession, message);
    }
    private void bindDispatch() {
        final NavigationSessions.Session session = activeSession;
        final NavigationSessions.DispatchTicket ticket = status == Status.STOPPING
                ? sessions.cleanupTicket(activeSession) : sessions.inputTicket(activeSession);
        final java.util.function.BooleanSupplier permission = () -> {
            if (MicrobotSailingFacade.humanOrPaused() || explicitlyPaused && status != Status.STOPPING) {
                sessions.hold(session, "Navigation input paused");
                return false;
            }
            return !closing && !walkerTakeover.get() && heldSince == 0 && busyStatus(status) && sessions.canDispatch(ticket) && competitor() == null;
        };
        io.permit(permission);
        walker.permit(permission);
        io.beginStep();
    }
    private boolean pauseOrResume(SailingSnapshot s, long now) {
        boolean automatic = MicrobotSailingFacade.humanOrPaused();
        boolean hold = automatic || explicitlyPaused;
        if (!hold && heldSince == 0 && !sessions.isHeld(activeSession)) return false;
        if (heldSince == 0) {
            heldSince = now;
            heldTick = s == null ? -1 : s.tick;
            sessions.hold(activeSession, "Navigation input paused; voyage retained");
        }
        if (hold) {
            if (s != null) heldTick = s.tick;
            detail = status == Status.STOPPING ? "Cancelling: waiting for Microbot/input pause before stopping the boat"
                    : explicitlyPaused ? "Paused; press Start / pause again to resume" : "Microbot input pause; voyage retained";
            // Walker has no local pause API. Release only our land request for an explicit
            // pause, then reissue that same request on resume. Global pauses already gate it.
            if (explicitlyPaused && !automatic && !heldWalkerReleased && walker.ownsRoute()) {
                SailingWalkerJourney.JourneyState journey = walker.observe();
                if (!walker.ownershipIntact(journey)) {
                    walker.userTookOver(); yieldNavigation("Walker route replaced while paused"); return true;
                }
                heldWalkTarget = journey.destination;
                heldBankWalk = status == Status.WALKING_TO_BANK;
                walker.cancelOwned(); heldWalkerReleased = true;
            }
            return true;
        }
        if (s == null || !s.loggedIn || now - s.capturedMillis > 2200 || s.tick <= heldTick) {
            detail = "Waiting for a fresh game tick before resuming"; return true;
        }
        long pausedMillis = Math.max(0, now - heldSince);
        started += pausedMillis;
        phaseStarted += Math.max(0, now - Math.max(heldSince, phaseStarted));
        if (summonStarted != 0) summonStarted += pausedMillis;
        summonReadyTick = -1; summonBoardingAt = 0;
        if (walkerRetrySince != 0) walkerRetrySince += pausedMillis;
        bankAction.defer(pausedMillis);
        heldSince = 0; heldTick = -1; staleSince = 0; boatMissingSince = 0;
        nextAction = 0; lastSeaTick = -1;
        stopTicks = 0; stopSnapshotTick = -1; stoppedPosition = null;
        if (pilot != null) pilot.resumeAfterPause(now);
        sessions.resume(activeSession);
        bindDispatch();
        if (heldWalkerReleased && status != Status.STOPPING && competitor() == null) {
            if (walker.busy() && !walker.ownsRoute()) {
                yieldNavigation("Another Walker request started while paused"); return true;
            }
            boolean accepted = heldBankWalk ? walker.walkToBank() : heldWalkTarget != null && walker.walkTo(heldWalkTarget);
            if (!accepted) { stop(Status.BLOCKED, "Walker could not resume the paused request"); return true; }
        }
        heldWalkTarget = null; heldBankWalk = false; heldWalkerReleased = false;
        detail = status == Status.STOPPING ? "Cancelling: stopping boat and releasing navigation" : "Resumed from fresh position and collision data";
        return false;
    }
    private void yieldNavigation(String reason) {
        if (!sessions.manualTakeover(activeSession, reason) && sessions.activeSession() != null) return;
        heldSince = 0; explicitlyPaused = false;
        stoppingPermission = false;
        io.permit(() -> false);
        phase(Status.PAUSED, reason);
    }
    private List<HazardZone> hazards(SailingSnapshot s, long now) {
        List<HazardZone> zones = new ArrayList<>(manualHazards);
        if (s != null) zones.addAll(s.hazards);
        if (pilot != null) zones.addAll(pilot.temporaryHazards(now));
        return zones;
    }
    private void publish() {
        if (userCancelled.get()) return; // A worker finishing an old step must not redraw a cancelled voyage.
        SailingSnapshot s = io.latest();
        if (busyStatus(status) && status != Status.STOPPING) sessions.update(activeSession, detail);
        view = new View(heldSince != 0 && busyStatus(status) ? Status.SUSPENDED : status,
                detail, selected == null ? "-" : selected.port.name, failure, walker.version(),
                goal, s == null ? null : s.sea, started, atlas.knownTiles(), pilot == null ? 0 : pilot.recoveries(),
                pilot == null ? Collections.emptyList() : pilot.path(),
                pilot == null || s == null ? -1 : pilot.etaSeconds(s.sea),
                pilot==null?Collections.emptyList():pilot.localPath(),pilot==null?"Planning":pilot.mode(),
                pilot==null?"Planning":pilot.routeSource(),pilot==null?0:pilot.speed(),pilot==null?0:pilot.replans(),pilot==null?0:pilot.stopCount());
    }
    private void note(String text) { log.note(text); }
    private void dump(SailingSnapshot s) {
        try {
            String relevant = lastUi;
            if (s != null && !s.onBoat) relevant = io.ui().relevantText(true);
            if (s != null && s.onBoat) {
                StringBuilder controls=new StringBuilder();
                for(MicrobotSailingFacade.UiElement element:io.ui().elements)
                    if(element.group()==937&&element.actions.length>0)controls.append(element.fingerprint()).append('\n');
                relevant=controls.toString();
            }
            Path path = log.dump(s, goal, status + " " + detail, pilot == null ? Collections.emptyList() : pilot.path(),
                    hazards(s, MicrobotSailingFacade.now()), relevant,
                    pilot==null?Collections.emptyList():pilot.localPath(),
                    pilot==null?"No pilot":pilot.routeSource()+"\nmode="+pilot.mode()+" speed="+pilot.speed()+" replans="+pilot.replans()+" stops="+pilot.stopCount());
            note("diagnostics saved " + path);
        } catch (Exception ex) { note("diagnostics export failed: " + ex.getMessage()); }
    }
    private void saveAtlas() {
        if (config.persistAtlas()) try { atlas.save(atlasPath); } catch (Exception ex) { note("atlas-save failed: " + ex.getMessage()); }
    }
    private void requestVoyagePlan(SailingSnapshot s,long now) {
        if(!config.useReferenceChart()||s==null||s.sea==null||s.chart==null||goal==null)return;
        if(voyageFuture!=null)voyageFuture.cancel(true);
        final long token=activeEpoch;
        final SeaGrid chart=atlas.snapshot(s.chart,hazards(s,now),seaSeed);
        final SeaPoint from=s.sea,to=goal;
        final int radius=config.arrivalRadius();
        voyageFutureEpoch=token;lastVoyagePlan=now;voyageDirty=false;
        voyageFuture=seaPlanning.submit(()->new GlobalSeaPlanner(ReferenceHolder.MAP).plan(chart,from,to,radius,1500,
                ()->closing||epoch.get()!=token));
    }
    private void pollVoyagePlan() {
        Future<GlobalSeaPlanner.Result> future=voyageFuture;
        if(future==null||!future.isDone())return;
        if(voyageFuture==future)voyageFuture=null;
        if(voyageFutureEpoch!=activeEpoch||activeEpoch!=epoch.get()||pilot==null)return;
        try {
            GlobalSeaPlanner.Result result=future.get();
            if(result.outcome==GlobalSeaPlanner.Outcome.CANCELLED)return;
            pilot.setVoyageRoute(result);
            note("voyage-plan outcome="+result.outcome+" points="+result.path.size()+" expanded="+result.expanded+" ms="+result.millis+" "+result.detail);
        }catch(CancellationException ignored){}catch(Exception ex){note("voyage-plan failed; retaining observed local route: "+ex.getMessage());}
    }
    private void cancelPlanner() {
        if(voyageFuture!=null){voyageFuture.cancel(true);voyageFuture=null;}
        if (portFuture != null) { portFuture.cancel(true); portFuture = null; }
    }
    private String competitor() {
        for (Plugin plugin : plugins.getPlugins()) {
            if (!plugins.isPluginEnabled(plugin)) continue;
            if (sessions.cooperating(plugin)) continue;
            String name = plugin.getClass().getName();
            if (name.equals("net.runelite.client.plugins.microbot.drosailingsalvager.DroSailingPlugin")
                    || name.equals("net.runelite.client.plugins.microbot.sailing.MSailingPlugin")) return plugin.getClass().getSimpleName();
        }
        return null;
    }
    private static boolean busyStatus(Status status) {
        return status != Status.IDLE && status != Status.ARRIVED && status != Status.BLOCKED && status != Status.CANCELLED && status != Status.PAUSED;
    }
    private static Thread daemon(Runnable runnable, String name) { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; }
    @Override public void close() {
        if (closing) return;
        closing = true; epoch.incrementAndGet(); pending.set(null); cancelPlanner(); planning.shutdownNow(); seaPlanning.shutdownNow();
        io.permit(() -> false);
        sessions.close("Navigation service disabled; boat stop was not confirmed");
        // No Script.shutdown(): that method resets shared walking/configuration state.
        control.execute(() -> {
            try {
                walker.cancelOwned();
                SailingSnapshot s = io.latest();
                if (busyStatus(status) && s != null && s.onBoat && s.moving)
                    note("disable: input released; boat stop is unconfirmed, take control manually");
                saveAtlas();
            } catch (Exception ex) { note("shutdown cleanup: " + ex.getMessage()); }
            finally { stoppingPermission = false; log.close(); }
        });
        control.shutdown();
    }
}
