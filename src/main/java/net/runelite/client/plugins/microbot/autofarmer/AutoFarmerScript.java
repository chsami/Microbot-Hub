package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.PatchImplementation;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.plugins.itemstats.stats.Stats;
import net.runelite.client.plugins.microbot.Microbot;

import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class AutoFarmerScript extends Script {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AutoFarmerScript.class);
    @Inject
    private ConfigManager configManager;
    @Inject
    private FarmingWorld farmingWorld;
    private FarmingHandler farmingHandler;
    private final AutoFarmerPlugin plugin;
    private final AutoFarmerConfig config;
    private HerbPatch currentPatch;
    private final List<TreeStop> treeStops = new ArrayList<>();
    private TreeStop currentTree;
    private long treeActionStarted;
    private int treeWalkFailures;
    private volatile long strongholdTravelDeadline;
    private static final long STRONGHOLD_TRAVEL_LIMIT = TimeUnit.MINUTES.toMillis(5);
    private static final WorldPoint STRONGHOLD_INNER_PATH = new WorldPoint(2452, 3398, 0);
    private static final WorldPoint VARROCK_TREE = new WorldPoint(3229, 3457, 0);
    private static final WorldPoint VARROCK_SQUARE = new WorldPoint(3212, 3428, 0);
    private static final WorldPoint VARROCK_EAST_ROAD = new WorldPoint(3274, 3429, 0);
    private WorldPoint fortisDetourWaypoint;
    private long nextFortisTeleportAttempt;

    private static final class TreeStop {
        private final FarmingPatch patch;
        private final TreeSapling sapling;

        TreeStop(FarmingPatch patch, TreeSapling sapling) {
            this.patch = patch;
            this.sapling = sapling;
        }

        FarmingPatch patch() { return patch; }
        TreeSapling sapling() { return sapling; }

        String key() {
            return (sapling instanceof TreeSapling.Regular ? "Tree at " : "Fruit tree at ")
                    + patch.getRegion().getName();
        }
    }

    private static final int BANK_COIN_TARGET = 10_000;
    private static final int FORTIS_TABLET_TARGET = 10;
    private static final int BRIMHAVEN_FOOD_TARGET = 5;
    private static final int[] FARMING_ANTIPOISON = {
        ItemID._4DOSE2ANTIPOISON, ItemID._4DOSEANTIPOISON,
        ItemID._3DOSE2ANTIPOISON, ItemID._3DOSEANTIPOISON,
        ItemID._2DOSE2ANTIPOISON, ItemID._2DOSEANTIPOISON,
        ItemID._1DOSE2ANTIPOISON, ItemID._1DOSEANTIPOISON
    };
    private long nextRecoveryAction;
    private static final int GARDENER_CLEAR_COST = 200;
    private static final int MAX_TRAVEL_DEFERRALS = 3;
    private static final String PATCH_RETRY_DELAY = "PATCH_RETRY_DELAY";
    private final Map<String, Integer> travelDeferrals = new HashMap<>();
    private int unreachableWalks = 0;

    @Inject
    ClientThread clientThread;
    private volatile FailureLatch failureStop;
    private volatile String failureProfile;
    private SeedRestocker seedRestocker;
    private volatile boolean stopping;
    @Inject
    private ItemStatChanges itemStatChanges;
    private java.util.concurrent.ScheduledExecutorService safetyExecutor;
    private long eventSafetySince;
    private long manualPauseSince;
    private volatile long lastProgressAt = System.currentTimeMillis();
    private volatile boolean seenLoggedIn;
    private String progressSignature = "";
    private long offlineSince;
    private volatile long runStartedAt;
    private static final String DAILY_BREAK_KEY = "dailyBreakSchedule";
    private String dailyBreakProfile;
    private volatile DailyBreakSchedule dailyBreakSchedule;
    private volatile boolean takingDailyBreak;
    private long dailyBreakLoginAttemptAt;
    private final Map<String, Integer> patchFailures = new HashMap<>();
    private String exhaustedPatchAction;
    private boolean eventYielded;
    private String failureDetail = "Inventory setup could not be completed";

    private synchronized boolean prepareFailureProfile(String profile) {
        if (stopping) return false;
        if (profile.equals(failureProfile)) return true;

        FailureLatch latch = new FailureLatch(ProfileFiles.resolve(profile, "failure.properties"));
        seedRestocker = new SeedRestocker(ProfileFiles.resolve(profile, "seed-purchase.properties"));
        failureProfile = profile;
        if (config.clearFailureAndResume()) {
            try {
                latch.clear();
                configManager.setConfiguration("AutoFarmer", "clearFailureAndResume", false);
                log("Failure stop cleared; resuming with saved patch memory.");
            } catch (java.io.IOException e) {
                log.error("Could not clear the saved AutoFarmer failure", e);
                stopForSavedFailure("Could not clear the saved failure report");
                return false;
            }
        }
        if (latch.active()) {
            stopForSavedFailure(latch.reason());
            return false;
        }
        failureStop = latch;
        if (config.builtInRandomEvents()) plugin.randomEvents.start();
        return true;
    }

    private synchronized void failRun(String reason) {
        FailureLatch latch = failureStop;
        String profile = failureProfile;
        if (stopping || latch == null || profile == null || latch.active()) return;
        if (Microbot.isLoggedIn() && !profile.equals(configManager.getRSProfileKey())) return;
        stopping = true;
        String patch = currentTree != null ? currentTree.key()
                : currentPatch == null ? null : currentPatch.getRegionName();
        try {
            latch.trip(reason, patch, System.currentTimeMillis());
        } catch (java.io.IOException e) {
            log.error("Could not save AutoFarmer failure for the active profile", e);
        }
        try {
            cropBreak = false;
            preparingCropBreak = false;
            if (profile.equals(configManager.getRSProfileKey())) {
                configManager.unsetRSProfileConfiguration("AutoFarmer", "nextCropCheck");
                configManager.unsetRSProfileConfiguration("AutoFarmer", "nextCropRegion");
                configManager.unsetRSProfileConfiguration("AutoFarmer", "cropTravelPending");
            }
            plugin.randomEvents.stop();
            Rs2Walker.setTarget(null);
            if (Microbot.isLoggedIn()) {
                Rs2Bank.closeBank();
                Rs2Player.logout();
            }
        } finally {
            stopForSavedFailure(reason);
        }
    }

    private void stopForSavedFailure(String reason) {
        stopping = true;
        String message = "AutoFarmer stopped: " + reason
                + ". After fixing it, enable Clear failure and resume, then start AutoFarmer again.";
        log(message);
        try {
            if (Microbot.getNotifier() != null) Microbot.getNotifier().notify(message);
        } finally {
            Microbot.stopPlugin(plugin);
        }
    }

    private void safetyCheck() {
        try {
            long now=System.currentTimeMillis();
            if (stopping || failureStop == null || takingDailyBreak) return;
            checkNavigationOwnership();
            if(navigationFault!=null){failRun(navigationFault);return;}
            if (pauseSafetyForManualInput(now)) return;
            if(!Microbot.isLoggedIn()) {
                if(cropBreak) { if(now>nextRunAt+180000L)failRun("Scheduled login did not succeed within three minutes");return; }
                if(seenLoggedIn){if(offlineSince==0)offlineSince=now;if(now-offlineSince>90000L)failRun("Unexpected logout or disconnection during the farming run");}
                return;
            }
            seenLoggedIn=true;offlineSince=0;
            if(!preparingCropBreak && (cropBreak || now<nextRunAt)){lastProgressAt=now;return;}
            if(preparingCropBreak && now-prepositionStartedAt>600000L){failRun("Travel to next scheduled patch exceeded ten minutes: "+preferredNextRegion);return;}
            if (pauseSafetyForRandomEvent(now)) return;
            StringBuilder signature=new StringBuilder(String.valueOf(Rs2Player.getWorldLocation()));
            for(Rs2ItemModel item:Rs2Inventory.all())signature.append('|').append(item.getId()).append(':').append(item.getQuantity());
            String current=signature.toString();
            if(!current.equals(progressSignature)){progressSignature=current;lastProgressAt=now;}
            if(runStartedAt>0 && now-runStartedAt>1800000L) {failRun("Farming run exceeded 30 minutes without completing");return;}
            if(now-lastProgressAt>180000L && strongholdTravelDeadline == 0 && !(patchWorkStarted && currentPatch != null))failRun("No movement or inventory progress for three minutes; last status: " + AutoFarmerPlugin.status);
        } catch(Exception | LinkageError e) { failRun("Safety monitor error: "+e.getClass().getSimpleName()+": "+e.getMessage()); }
    }

    private synchronized boolean pauseSafetyForManualInput(long now) {
        if (Microbot.pauseAllScripts.get()
                || net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()) {
            if (manualPauseSince == 0) manualPauseSince = now;
            lastProgressAt = now;
            return true;
        }
        if (manualPauseSince != 0) {
            long elapsed = Math.max(0, now - manualPauseSince);
            if (runStartedAt > 0) runStartedAt += elapsed;
            if (patchWorkStarted) patchStartedAt += elapsed;
            if (treeActionStarted > 0) treeActionStarted += elapsed;
            if (strongholdTravelDeadline > 0) strongholdTravelDeadline += elapsed;
            if (preparingCropBreak) prepositionStartedAt += elapsed;
            lastProgressAt = now;
            manualPauseSince = 0;
        }
        return false;
    }

    private boolean pauseSafetyForRandomEvent(long now) {
        if(plugin!=null && plugin.randomEvents!=null && plugin.randomEvents.isHandling()) {
            if(eventSafetySince==0)eventSafetySince=now;
            lastProgressAt=now;
            return true;
        }
        if(eventSafetySince>0) {
            long elapsed=Math.max(0,now-eventSafetySince);
            if(runStartedAt>0)runStartedAt+=elapsed;
            if(patchWorkStarted)patchStartedAt+=elapsed;
            if(preparingCropBreak)prepositionStartedAt+=elapsed;
            lastProgressAt=now;
            eventSafetySince=0;
        }
        return false;
    }

    private final NavigationGuard navigationGuard=new NavigationGuard();
    private volatile String navigationFault;
    public void checkNavigationOwnership() {
        try {
            String conflict=navigationGuard.enforce();
            if(conflict!=null)navigationFault=conflict;
        }catch(Exception e){navigationFault="Navigation guard failed: "+e.getMessage();}
    }

    private boolean initialized = false;
    private boolean bankCleared = false;
    private final FarmingLamp farmingLamp = new FarmingLamp();
    private final Set<Integer> retainedAtBank = new HashSet<>();
    private boolean protectedReward(Rs2ItemModel item) {
        return item != null && (retainedAtBank.contains(item.getId()) || BankRetention.keep(item.getName()));
    }
    private boolean keepInInventory(Rs2ItemModel item) {
        if(item==null)return false;
        int id=item.getId();
        return id==ItemID.RAKE || id==ItemID.SPADE || id==ItemID.DIBBER
                || id==ItemID.LAWRUNE || id==ItemID.AIRRUNE || id==ItemID.EARTHRUNE
                || id==ItemID.FIRERUNE || id==ItemID.WATERRUNE || protectedReward(item);
    }
    private boolean depositBankableInventory() { return depositInventoryExcept(this::keepForRun); }
    private boolean finishInventoryForBreak() {
        if (!config.goToBank()) return true;
        // Keep the kit between timed patches. Harvest can travel with us until space is needed.
        if (config.repeatRuns() && Rs2Inventory.emptySlotCount() >= 8) {
            log("Keeping farming kit and harvest; enough inventory space for the next patch");
            return true;
        }
        if (Rs2Inventory.all().stream().allMatch(this::keepForRun)) return true;
        AutoFarmerPlugin.status="Banking harvest; keeping farming supplies";
        if (!openSupplyBank()) {
            failureDetail="Could not open bank to make room for harvested produce";
            return false;
        }
        if(!depositBankableInventory())return false;
        topUpTravelCoins();
        if (!topUpTeleportRunes()) return false;
        topUpFortisTablets();
        return true;
    }
    private boolean depositInventoryExcept(java.util.function.Predicate<Rs2ItemModel> retain) {
        if (!Rs2Bank.isOpen()) return false;
        Set<Integer> handled = new HashSet<>();
        for (Rs2ItemModel item : new ArrayList<>(Rs2Inventory.all())) {
            if (retain.test(item) || !handled.add(item.getId())) continue;
            if (yieldToRandomEvents()) return false;
            int id = item.getId();
            int before = Rs2Inventory.itemQuantity(id);
            pauseBeforeAction();
            Rs2Bank.depositAll(id);
            if (!sleepUntil(() -> Rs2Inventory.itemQuantity(id) < before, 3500)) {
                retainedAtBank.add(id);
                log("Keeping " + item.getName() + " in inventory: deposit not confirmed; will not keep retrying it");
            }
        }
        return Rs2Inventory.all().stream().allMatch(retain);
    }

    private final MouseFeel mouseFeel = new MouseFeel();
    private boolean yieldToRandomEvents() {
        if (stopping) return true;
        checkNavigationOwnership();
        if(navigationFault!=null){failRun(navigationFault);return true;}
        if (!Microbot.isLoggedIn() || Microbot.getBlockingEventManager() == null) return false;
        if (!Microbot.getBlockingEventManager().shouldBlockAndProcess()) return false;
        // Time spent in an event is not a failure of the farming patch.
        if (patchWorkStarted) patchStartedAt = System.currentTimeMillis();
        eventYielded = true;
        AutoFarmerPlugin.status = "Waiting for random event / blocking activity";
        return true;
    }

    private void pauseBeforeAction() {
        if (stopping) throw new IllegalStateException("Farmer is stopped after failure");
        if (!config.relaxedPacing()) return;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int delay = (int)Math.max(280, Math.min(1400, 650 + random.nextGaussian() * 220));
        if (random.nextInt(40) == 0) delay += random.nextInt(900, 1801);
        sleep(delay);
    }

    private final PatchLedger ledger = new PatchLedger();
    private final Set<String> enabledRegions = new HashSet<>();
    private String accountProfile;
    private volatile boolean cropBreak;
    private volatile boolean preparingCropBreak;
    private HerbPatch breakDestination;
    private volatile String preferredNextRegion;
    private long prepositionStartedAt;
    private int prepositionUnreachable;
    private boolean prepositionSuppliesChecked;
    private final Map<String,HerbPatch> allEnabledHerbs=new HashMap<>();
    private long loginAttemptAt;
    private long loginSettledAt;
    private net.runelite.client.plugins.Plugin heldAutoLogin;

    private void saveMemory() {
        configManager.setRSProfileConfiguration("AutoFarmer", "patchMemory", ledger.save());
    }
    private void remember(String outcome, long checkAt) {
        if (currentPatch == null) return;
        ledger.record(currentPatch.getRegionName(), outcome, checkAt);
        saveMemory();
        log("Patch memory: " + currentPatch.getRegionName() + " = " + outcome + ", check at " + java.time.Instant.ofEpochMilli(checkAt));
    }
    private void holdAutoLogin() {
        for (net.runelite.client.plugins.Plugin candidate : Microbot.getPluginManager().getPlugins()) {
            if (candidate.getClass().getSimpleName().equals("AutoLoginPlugin") && Microbot.isPluginEnabled(candidate)) {
                heldAutoLogin = candidate;
                Microbot.stopPlugin(candidate);
                log("Auto Farmer is holding Auto Login during crop timers");
            }
        }
    }
    private void releaseAutoLogin() {
        if (heldAutoLogin != null) {
            Microbot.startPlugin(heldAutoLogin);
            heldAutoLogin = null;
        }
    }

    private void scheduleCropBreak() {
        initialized=false;runStartedAt=0;loginSettledAt=0;
        breakDestination=CropBreakPlan.next(allEnabledHerbs.values(),ledger);
        TreeStop nextTree = nextTree();
        if (nextTree != null && (breakDestination == null
                || ledger.checkAt(nextTree.key()) < ledger.checkAt(breakDestination.getRegionName()))) {
            scheduleTreeCheck(nextTree);
            return;
        }
        if(breakDestination==null){failRun("No enabled and accessible patch for the next crop check");return;}
        preferredNextRegion=breakDestination.getRegionName();
        nextRunAt=Math.max(System.currentTimeMillis(),ledger.checkAt(preferredNextRegion));
        boolean waitBeforeTravel = PATCH_RETRY_DELAY.equals(ledger.outcome(preferredNextRegion))
                && !ledger.due(preferredNextRegion, System.currentTimeMillis());
        if (waitBeforeTravel) {
            scheduleCropWait(preferredNextRegion);
            return;
        }
        preparingCropBreak=true;cropBreak=false;
        prepositionStartedAt=System.currentTimeMillis();lastProgressAt=prepositionStartedAt;
        prepositionUnreachable=0;prepositionSuppliesChecked=false;
        waitReason="Next patch: "+preferredNextRegion;
        configManager.setRSProfileConfiguration("AutoFarmer","nextCropCheck",Long.toString(nextRunAt));
        configManager.setRSProfileConfiguration("AutoFarmer","nextCropRegion",preferredNextRegion);
        configManager.setRSProfileConfiguration("AutoFarmer","cropTravelPending","true");
        log("Preparing next patch "+preferredNextRegion+"; check due "+java.time.Instant.ofEpochMilli(nextRunAt));
    }
    private void scheduleTreeCheck(TreeStop tree) {
        scheduleCropWait(tree.key());
    }

    private void scheduleCropWait(String region) {
        preferredNextRegion = region;
        nextRunAt = Math.max(System.currentTimeMillis(), ledger.checkAt(region));
        preparingCropBreak = false;
        cropBreak = config.repeatRuns() && config.logoutBetweenRuns();
        waitReason = "Waiting for " + region;
        configManager.setRSProfileConfiguration("AutoFarmer", "nextCropCheck", Long.toString(nextRunAt));
        configManager.setRSProfileConfiguration("AutoFarmer", "nextCropRegion", preferredNextRegion);
        configManager.unsetRSProfileConfiguration("AutoFarmer", "cropTravelPending");
        log("Next crop check: " + region + " at " + java.time.Instant.ofEpochMilli(nextRunAt));
        if (cropBreak) holdAutoLogin();
        AutoFarmerPlugin.status = waitReason;
    }

    boolean prepareBreakSupplies() {return hasReadyHerbInventory() || setupAutoInventory();}
    private boolean handlePreposition() {
        if(!preparingCropBreak)return false;
        if(Microbot.pauseAllScripts.get() || net.runelite.client.plugins.microbot.ScriptPauseController.isPaused())return true;
        if(!Microbot.isLoggedIn())return true;
        if(accountProfile!=null && !accountProfile.equals(configManager.getRSProfileKey())) {
            preparingCropBreak=false;breakDestination=null;preferredNextRegion=null;nextRunAt=0;initialized=false;accountProfile=null;return false;
        }
        if(yieldToRandomEvents())return true;
        if(!prepositionSuppliesChecked) {
            if(!prepareBreakSupplies()){if(!eventYielded && !stopping)failRun(failureDetail);return true;}
            prepositionSuppliesChecked=true;
        }
        if(breakDestination==null){failRun("Next crop destination is unavailable");return true;}
        long now=System.currentTimeMillis();
        if(Rs2Player.getWorldLocation()==null || Rs2Player.getWorldLocation().distanceTo(breakDestination.getLocation())>6) {
            AutoFarmerPlugin.status="Travelling to next patch: "+preferredNextRegion+" ("+Math.max(0,(ledger.checkAt(preferredNextRegion)-now)/1000)+"s until check)";
            Rs2Bank.closeBank();
            if (walkToHerbPatch(breakDestination, 6) == WalkerState.UNREACHABLE
                    && ++prepositionUnreachable >= patchRetryLimit()) {
                if (deferUnreachablePatch(preferredNextRegion)) scheduleCropBreak();
            }
            return true;
        }
        travelDeferrals.remove(preferredNextRegion);
        preparingCropBreak=false;prepositionUnreachable=0;
        configManager.unsetRSProfileConfiguration("AutoFarmer","cropTravelPending");
        // Travel used some of the growth time. Keep the original planting deadline.
        nextRunAt=ledger.checkAt(preferredNextRegion);
        long remaining=nextRunAt-now;
        log("Arrived beside next patch "+preferredNextRegion+"; "+Math.max(0,remaining/1000)+" seconds until check");
        if(remaining<=0) {
            cropBreak=false;nextRunAt=0;
            configManager.unsetRSProfileConfiguration("AutoFarmer","nextCropCheck");
            configManager.unsetRSProfileConfiguration("AutoFarmer","nextCropRegion");
            AutoFarmerPlugin.status="Next patch ready: "+preferredNextRegion;
            return true;
        }
        configManager.setRSProfileConfiguration("AutoFarmer","nextCropCheck",Long.toString(nextRunAt));
        cropBreak=config.repeatRuns() && config.logoutBetweenRuns();
        waitReason="Waiting at "+preferredNextRegion;
        if(cropBreak)holdAutoLogin();
        if(CropBreakPlan.shouldLogout(now, nextRunAt, cropBreak, config.cropLogoutThresholdMinutes())) {
            AutoFarmerPlugin.status="Waiting at "+preferredNextRegion+"; logging out until crop check";
            Rs2Bank.closeBank();Rs2Player.logout();loginAttemptAt=now+15000;
        }else AutoFarmerPlugin.status=waitReason+"; "+(remaining/1000)+"s until check";
        return true;
    }
    private boolean handleCropBreak() {
        if (!cropBreak) return false;
        if (Microbot.isLoggedIn() && accountProfile != null && !accountProfile.equals(configManager.getRSProfileKey())) {
            cropBreak = false;
            nextRunAt = 0;
            initialized = false;
            accountProfile = null;
            return false;
        }
        if (Microbot.pauseAllScripts.get() || net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()) return true;
        long now = System.currentTimeMillis();
        if (now < nextRunAt) {
            AutoFarmerPlugin.status = "Crop timer ("+(preferredNextRegion==null?"next patch":preferredNextRegion)+"): " + ((nextRunAt-now)/60000) + "m " + (((nextRunAt-now)/1000)%60) + "s";
            if (Microbot.isLoggedIn() && now >= loginAttemptAt && CropBreakPlan.shouldLogout(now, nextRunAt, config.logoutBetweenRuns(), config.cropLogoutThresholdMinutes())) { loginAttemptAt = now + 15000; Rs2Bank.closeBank(); Rs2Player.logout(); }
            return true;
        }
        if (!Microbot.isLoggedIn()) {
            AutoFarmerPlugin.status = "Crop timer due; logging in";
            if (now >= loginAttemptAt && Microbot.getClient().getGameState() == net.runelite.api.GameState.LOGIN_SCREEN) {
                loginAttemptAt = now + 60000;
                net.runelite.client.plugins.microbot.util.security.LoginManager.login();
            }
            return true;
        }
        if (loginSettledAt == 0) loginSettledAt = now + 6000;
        if (now < loginSettledAt) return true;
        cropBreak = false;
        loginSettledAt = 0;
        configManager.unsetRSProfileConfiguration("AutoFarmer", "nextCropCheck");
        configManager.unsetRSProfileConfiguration("AutoFarmer", "nextCropRegion");
        configManager.unsetRSProfileConfiguration("AutoFarmer", "cropTravelPending");
        return false;
    }

    private volatile long nextRunAt = 0;
    private String waitReason = "Waiting for crops";
    private long patchStartedAt = 0;
    private boolean patchWorkStarted;

    private String lastPatchDiagnostic="not read";
    private final Set<WorldPoint> compostedPatches = new HashSet<>();
    private volatile boolean checkingCompost;
    private volatile boolean alreadyComposted;

    public void onGameMessage(String message) {
        if (!checkingCompost || message == null) return;
        String text = message.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("already") && (text.contains("compost") || text.contains("treated") || text.contains("fertili"))) alreadyComposted = true;
    }

    private enum LocationPhase { HERB, FLOWER, ALLOTMENT, DONE }
    private LocationPhase currentPhase = LocationPhase.HERB;

    private static final Set<String> ALLOTMENT_FLOWER_REGIONS = Set.of(
            "Ardougne", "Catherby", "Civitas illa Fortis", "Falador", "Kourend", "Morytania"
    );

    @Inject
    public AutoFarmerScript(AutoFarmerPlugin plugin, AutoFarmerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    private final List<HerbPatch> herbPatches = new ArrayList<>();
    private final Map<String, List<FarmingPatch>> allotmentsByRegion = new HashMap<>();
    private final Map<String, FarmingPatch> flowerByRegion = new HashMap<>();
    private final Set<Integer> handledAllotmentIds = new HashSet<>();
    private int currentAllotmentId = -1;

    boolean isTakingDailyBreak() {
        return takingDailyBreak;
    }

    boolean isWaitingForCrops() {
        return cropBreak;
    }

    String nextCropName() {
        String region = preferredNextRegion;
        return region == null ? "next patch" : region;
    }

    long nextCropCheckAt() {
        return nextRunAt;
    }

    long dailyBreakMillisRemaining() {
        if (!takingDailyBreak) return 0;
        DailyBreakSchedule schedule = dailyBreakSchedule;
        return schedule == null ? 0 : Math.max(0, schedule.endsAt() - System.currentTimeMillis());
    }

    private boolean handleDailyBreak() {
        if (!config.dailyBreak()) {
            if (takingDailyBreak) cancelDailyBreak();
            return false;
        }
        String profile = configManager.getRSProfileKey();
        if (profile == null && Microbot.isLoggedIn()) return false;
        if (profile == null) profile = dailyBreakProfile;
        if (profile == null) return false;
        if (!profile.equals(dailyBreakProfile)) {
            dailyBreakProfile = profile;
            dailyBreakSchedule = DailyBreakSchedule.parse(
                    configManager.getRSProfileConfiguration("AutoFarmer", DAILY_BREAK_KEY));
            takingDailyBreak = false;
            dailyBreakLoginAttemptAt = 0;
        }
        var localNow = java.time.ZonedDateTime.now();
        long now = localNow.toInstant().toEpochMilli();
        if (!takingDailyBreak && dailyBreakSchedule != null && dailyBreakSchedule.activeAt(now)) {
            takingDailyBreak = true;
        }
        if (!takingDailyBreak) {
            if (dailyBreakSchedule == null || !dailyBreakSchedule.day().equals(localNow.toLocalDate())) {
                dailyBreakSchedule = DailyBreakSchedule.plan(localNow);
                saveDailyBreakSchedule();
            }
            boolean betweenPatches = currentTree == null
                    && (currentPatch == null || !patchWorkStarted || currentPhase == LocationPhase.DONE);
            if (!betweenPatches || !dailyBreakSchedule.readyAt(now)) return false;
            dailyBreakSchedule = dailyBreakSchedule.start(now);
            saveDailyBreakSchedule();
            takingDailyBreak = true;
            log("Daily break started; ends at " + java.time.Instant.ofEpochMilli(dailyBreakSchedule.endsAt()));
        }

        holdAutoLogin();
        if (dailyBreakSchedule.activeAt(now)) {
            long minutesLeft = (dailyBreakSchedule.endsAt() - now + 59_999) / 60_000;
            AutoFarmerPlugin.status = "Daily break: " + minutesLeft / 60 + "h " + minutesLeft % 60 + "m left";
            if (Microbot.isLoggedIn() && now >= dailyBreakLoginAttemptAt) {
                dailyBreakLoginAttemptAt = now + 15_000;
                Rs2Walker.setTarget(null);
                Rs2Bank.closeBank();
                Rs2Player.logout();
            }
            return true;
        }

        if (!Microbot.isLoggedIn()) {
            AutoFarmerPlugin.status = "Daily break ended; logging in";
            if (now >= dailyBreakLoginAttemptAt && Microbot.getClient().getGameState() == net.runelite.api.GameState.LOGIN_SCREEN) {
                dailyBreakLoginAttemptAt = now + 60_000;
                net.runelite.client.plugins.microbot.util.security.LoginManager.login();
            }
            return true;
        }

        takingDailyBreak = false;
        dailyBreakLoginAttemptAt = 0;
        lastProgressAt = now;
        offlineSince = 0;
        runStartedAt = initialized ? now : 0;
        if (preparingCropBreak) prepositionStartedAt = now;
        log("Daily break complete; resuming saved crop schedule");
        return false;
    }

    private void cancelDailyBreak() {
        long now = System.currentTimeMillis();
        if (dailyBreakSchedule.activeAt(now)) {
            dailyBreakSchedule = dailyBreakSchedule.endNow(now);
            saveDailyBreakSchedule();
        }
        takingDailyBreak = false;
        dailyBreakLoginAttemptAt = 0;
        lastProgressAt = now;
        offlineSince = 0;
        runStartedAt = initialized ? now : 0;
        if (preparingCropBreak) prepositionStartedAt = now;
        if (!Microbot.isLoggedIn()) {
            preparingCropBreak = false;
            cropBreak = true;
            nextRunAt = Math.max(now, nextRunAt);
            loginAttemptAt = 0;
        }
        if (!cropBreak) releaseAutoLogin();
        log("Daily break turned off; resuming saved crop schedule");
    }

    private void saveDailyBreakSchedule() {
        configManager.setRSProfileConfiguration("AutoFarmer", DAILY_BREAK_KEY, dailyBreakSchedule.encode());
    }

    private int patchRetryLimit() {
        return Math.max(1, config.patchRetryLimit());
    }

    private void recordPatchFailure(String action) {
        if (eventYielded || stopping || pauseSafetyForManualInput(System.currentTimeMillis())) return;
        if (patchFailures.merge(action, 1, Integer::sum) >= patchRetryLimit()) exhaustedPatchAction = action;
    }

    private void resetPatchFailures() {
        patchFailures.clear();
        exhaustedPatchAction = null;
    }

    private void deferCurrentPatch(String reason) {
        deferPatch(currentPatch.getRegionName(), reason);
        advancePastDeferredPatch();
    }

    private boolean deferUnreachablePatch(String region) {
        if (travelDeferrals.merge(region, 1, Integer::sum) >= MAX_TRAVEL_DEFERRALS) {
            failRun("Could not reach the herb patch at " + region
                    + " after several tries. Check your teleport items and quest unlocks, or turn it off in Location toggles");
            return false;
        }
        deferPatch(region, "retry limit reached for travel");
        return true;
    }

    private void deferPatch(String region, String reason) {
        long retryAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
        ledger.record(region, PATCH_RETRY_DELAY, Math.max(retryAt, ledger.checkAt(region)));
        saveMemory();
        log("Deferring " + region + ": " + reason);
    }

    private void advancePastDeferredPatch() {
        currentPatch = null;
        handledAllotmentIds.clear();
        currentAllotmentId = -1;
        unreachableWalks = 0;
        resetPatchFailures();
        lastProgressAt = System.currentTimeMillis();
    }

    public boolean run() {
        stopping = false;
        resetPatchFailures();
        navigationFault=null;
        navigationGuard.acquire();
        log("Auto Farmer owns navigation; stale manual map destination cleared");
        lastProgressAt=System.currentTimeMillis();eventSafetySince=0;manualPauseSince=0;
        safetyExecutor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"AutoFarmerSafety");t.setDaemon(true);return t;});
        safetyExecutor.scheduleWithFixedDelay(this::safetyCheck,0,5,TimeUnit.SECONDS);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
            eventYielded=false;
            if (stopping) return;
            if (Microbot.isLoggedIn()) {
                String profile = configManager.getRSProfileKey();
                if (profile == null || !prepareFailureProfile(profile)) return;
            }
            if (pauseSafetyForManualInput(System.currentTimeMillis())) return;
            if (handleBrimhavenRecovery()) return;
            if (handleDailyBreak()) return;
            if (yieldToRandomEvents()) return;
            if (Microbot.isLoggedIn() && !Microbot.pauseAllScripts.get()
                    && !net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()
                    && config.dropEventFood() && plugin.randomEvents.dropFoodReward()) {
                if (patchWorkStarted) patchStartedAt = System.currentTimeMillis();
                return;
            }
            if (Microbot.isLoggedIn() && !Microbot.pauseAllScripts.get()
                    && !net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()
                    && config.useLampsOnFarming() && farmingLamp.handle()) {
                if (patchWorkStarted) patchStartedAt = System.currentTimeMillis();
                AutoFarmerPlugin.status = "Using event XP reward on Farming";
                return;
            }
            if (handlePreposition()) return;
            if (handleCropBreak()) return;
            if (!Microbot.isLoggedIn()) return;
            String activeProfile = configManager.getRSProfileKey();
            if (activeProfile == null) return;
            if (!activeProfile.equals(accountProfile)) {
                accountProfile = activeProfile;
                travelDeferrals.clear();
                fortisDetourWaypoint = null;
                ledger.load(configManager.getRSProfileConfiguration("AutoFarmer", "patchMemory"));
                initialized = false;
                currentPatch = null;
                currentTree = null;
                treeActionStarted = 0;
                String savedDeadline = configManager.getRSProfileConfiguration("AutoFarmer", "nextCropCheck");
                if (savedDeadline != null && config.logoutBetweenRuns() && config.repeatRuns()) {
                    long savedCheckAt = Long.parseLong(savedDeadline);
                    if (config.enableTrees() || config.enableFruitTrees()) {
                        populatePatches();
                        TreeStop nextTree = nextTree();
                        if (nextTree != null && ledger.checkAt(nextTree.key()) < savedCheckAt) {
                            scheduleCropBreak();
                            return;
                        }
                    }
                    nextRunAt = savedCheckAt;
                    preferredNextRegion=configManager.getRSProfileConfiguration("AutoFarmer","nextCropRegion");
                    if("true".equals(configManager.getRSProfileConfiguration("AutoFarmer","cropTravelPending"))) {
                        populatePatches();scheduleCropBreak();return;
                    }
                    cropBreak = true;
                    holdAutoLogin();
                    return;
                }
            }
            if (!super.run()) return;
            if (config.smoothMouse() && Microbot.naturalMouse != null) mouseFeel.enable(Microbot.naturalMouse.nature);
            else mouseFeel.restore();
            if (System.currentTimeMillis() < nextRunAt) {
                AutoFarmerPlugin.status = waitReason + "; next check in " + ((nextRunAt - System.currentTimeMillis()) / 1000) + "s";
                return;
            }
            if (currentTree != null) {
                handleTreeWork();
                return;
            }
            if (!initialized) {
                if(runStartedAt==0)runStartedAt=System.currentTimeMillis();
                AutoFarmerPlugin.status = "Gearing up";
                compostedPatches.clear();
                populatePatches();

                if (herbPatches.isEmpty()) {
                    if (handleTreeWork()) return;
                    if (enabledRegions.isEmpty() && treeStops.isEmpty()) {
                        failRun("No enabled and accessible farming patches");
                        return;
                    }
                    if (!config.repeatRuns() && !treeStops.isEmpty()) {
                        Microbot.stopPlugin(plugin);
                        return;
                    }
                    scheduleCropBreak();
                    waitReason = "No ready accessible patches";
                    AutoFarmerPlugin.status = waitReason;
                    return;
                }

                if (config.useInventorySetup()) {
                    var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                    if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                        if (Rs2Inventory.all().stream().anyMatch(this::protectedReward)) {
                            log("Inventory Setup loading paused to preserve unbankable rewards; use automatic banking or handle rewards manually");
                            failRun("Protected rewards prevent Inventory Setup loading; switch to Auto Banking");
                            return;
                        }
                        if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                            failRun("Custom inventory/equipment setup failed");
                            return;
                        }
                        if (Rs2Inventory.itemQuantity(ItemID.COINS) < BANK_COIN_TARGET
                                && openSupplyBank()) {
                            topUpTravelCoins();
                        }
                        Rs2Bank.closeBank();
                    }
                } else {
                    if (!hasReadyHerbInventory() && !setupAutoInventory()) {
                        if(eventYielded)return;
                        if (!stopping) failRun(failureDetail);
                        return;
                    }
                }

                initialized = true;
                int allotmentRegions = allotmentsByRegion.size();
                int flowerCount = flowerByRegion.size();
                log("Will visit " + herbPatches.size() + " herb patches, " + allotmentRegions + " allotment locations, " + flowerCount + " flower patches");
            }

            if (currentPatch == null) {
                getNextPatch();
                patchStartedAt = System.currentTimeMillis();
                patchWorkStarted = false;

                currentPhase = LocationPhase.HERB;
                resetPatchFailures();
                unreachableWalks = 0;
            }
            if (currentPatch == null) {
                if (yieldToRandomEvents()) return;
                if (handleTreeWork()) return;
                AutoFarmerPlugin.status = "Finishing up";
                log("Patch route complete; checking inventory space before the next crop timer");
                if (!finishInventoryForBreak()) {
                    if (!eventYielded) failRun(failureDetail);
                    return;
                }
                AutoFarmerPlugin.status = "Finished";
                if (config.repeatRuns()) {
                    initialized = false;
                    scheduleCropBreak();
                    handledAllotmentIds.clear();
                    currentAllotmentId = -1;
                } else {
                    Microbot.stopPlugin(plugin);
                }
                return;
            }

            if (!currentPatch.isEnabled()) {
                currentPatch = null;
                return;
            }

            if (!currentPatch.isInRange(40)) {
                walkToCurrentPatch();
                return;
            }
            unreachableWalks = 0;
            if (!patchWorkStarted) {
                travelDeferrals.remove(currentPatch.getRegionName());
                patchWorkStarted = true;
                patchStartedAt = System.currentTimeMillis();
                log("Arrived at " + currentPatch.getRegionName() + "; starting patch work timer");
            }
            if (System.currentTimeMillis() - patchStartedAt > 180_000L) {
                deferCurrentPatch("patch stalled for three minutes");
                return;
            }

            String region = currentPatch.getRegionName();

            switch (currentPhase) {
                case HERB:
                    AutoFarmerPlugin.status = "Farming herbs at " + region;
                    if (handleHerbPatch()) {
                        currentPhase = LocationPhase.FLOWER;
                    }
                    break;
                case FLOWER:
                    if (!config.enableFlowers() || !flowerByRegion.containsKey(region)) {
                        log("Skipping flowers at " + region + " (enabled=" + config.enableFlowers() + " hasRegion=" + flowerByRegion.containsKey(region) + " keys=" + flowerByRegion.keySet() + ")");
                        currentPhase = LocationPhase.ALLOTMENT;
                    } else {
                        AutoFarmerPlugin.status = "Farming flowers at " + region;
                        if (handleFlowerPatch(region)) {
                            currentPhase = LocationPhase.ALLOTMENT;
                        }
                    }
                    break;
                case ALLOTMENT:
                    if (!config.enableAllotments() || !allotmentsByRegion.containsKey(region)) {
                        log("Skipping allotments at " + region + " (enabled=" + config.enableAllotments() + " hasRegion=" + allotmentsByRegion.containsKey(region) + " keys=" + allotmentsByRegion.keySet() + ")");
                        currentPhase = LocationPhase.DONE;
                    } else {
                        AutoFarmerPlugin.status = "Farming allotments at " + region;
                        if (handleAllotmentPatches(region)) {
                            currentPhase = LocationPhase.DONE;
                        }
                    }
                    break;
                case DONE:
                    currentPatch = null;
                    currentPhase = LocationPhase.HERB;
                    handledAllotmentIds.clear();
                    currentAllotmentId = -1;
                    break;
            }
            if (currentPatch != null && exhaustedPatchAction != null) {
                deferCurrentPatch("retry limit reached for " + exhaustedPatchAction);
            }

            } catch (Exception | LinkageError failure) {
                waitReason = "Error: " + failure.getClass().getSimpleName() + "; see client log";
                AutoFarmerPlugin.status = waitReason;
                log.error("Auto Farmer loop failed", failure);
                failRun("Runtime error: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private WalkerState walkToHerbPatch(HerbPatch destination, int distance) {
        if (!"Civitas illa Fortis".equals(destination.getRegionName())) {
            fortisDetourWaypoint = null;
            return Rs2Walker.walkWithState(destination.getLocation(), distance);
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return WalkerState.MOVING;

        if (player.distanceTo(destination.getLocation()) > 180
                && tryFortisTeleport(destination.getLocation())) {
            fortisDetourWaypoint = null;
            return WalkerState.MOVING;
        }

        if (fortisDetourWaypoint == null && player.distanceTo(VARROCK_TREE) <= 8) {
            fortisDetourWaypoint = VARROCK_SQUARE;
            log("Routing from the Varrock tree to Fortis via Varrock Square and the east road.");
            boolean teleported = Rs2Magic.canCast(Rs2Spells.VARROCK_TELEPORT)
                    && Rs2Magic.cast(Rs2Spells.VARROCK_TELEPORT);
            if (!teleported && Rs2Inventory.hasItem(ItemID.POH_TABLET_VARROCKTELEPORT)) {
                teleported = Rs2Inventory.interact(ItemID.POH_TABLET_VARROCKTELEPORT, "Break");
            }
            if (teleported) {
                sleepUntil(() -> {
                    WorldPoint tile = Rs2Player.getWorldLocation();
                    return tile != null && tile.distanceTo(VARROCK_TREE) > 10;
                }, 8_000);
                return WalkerState.MOVING;
            }
        }
        if (VARROCK_SQUARE.equals(fortisDetourWaypoint) && player.distanceTo(VARROCK_SQUARE) <= 5) {
            fortisDetourWaypoint = VARROCK_EAST_ROAD;
        }
        if (VARROCK_EAST_ROAD.equals(fortisDetourWaypoint) && player.distanceTo(VARROCK_EAST_ROAD) <= 4) {
            fortisDetourWaypoint = null;
        }
        if (fortisDetourWaypoint != null) {
            return Rs2Walker.walkWithState(fortisDetourWaypoint, 4);
        }
        return Rs2Walker.walkWithState(destination.getLocation(), distance);
    }

    private boolean tryFortisTeleport(WorldPoint destination) {
        long now = System.currentTimeMillis();
        if (now < nextFortisTeleportAttempt
                || Rs2Player.getQuestState(Quest.TWILIGHTS_PROMISE) != QuestState.FINISHED) return false;
        nextFortisTeleportAttempt = now + 30_000L;

        if (canCastFortisTeleport() && Rs2Magic.cast(Rs2Spells.CIVITAS_ILLA_FORTIS_TELEPORT)
                && waitForFortisTeleport(destination)) {
            log("Used the Civitas illa Fortis teleport spell.");
            return true;
        }
        if (yieldToRandomEvents()) return true;
        int tablet = ItemID.POH_TABLET_FORTISTELEPORT;
        if (Rs2Inventory.hasItem(tablet) && Rs2Inventory.interact(tablet, "Break")
                && waitForFortisTeleport(destination)) {
            log("Used a Civitas illa Fortis teleport tablet.");
            return true;
        }
        return false;
    }

    private boolean waitForFortisTeleport(WorldPoint destination) {
        return sleepUntil(() -> {
            WorldPoint location = Rs2Player.getWorldLocation();
            return location != null && location.distanceTo(destination) <= 180;
        }, 8000);
    }

    private boolean canCastFortisTeleport() {
        return Rs2Magic.canCast(Rs2Spells.CIVITAS_ILLA_FORTIS_TELEPORT);
    }

    void walkToCurrentPatch() {
        if (yieldToRandomEvents()) return;
        AutoFarmerPlugin.status = "Walking to " + currentPatch.getRegionName();
        if (walkToHerbPatch(currentPatch, 20) != WalkerState.UNREACHABLE) {
            return;
        }
        if (++unreachableWalks < patchRetryLimit()) {
            return;
        }
        if (deferUnreachablePatch(currentPatch.getRegionName())) advancePastDeferredPatch();
    }

    private long nextGrowingCheck(String region, long now) {
        return now + ("UNKNOWN".equals(ledger.outcome(region)) ? config.growthMinutes() * 60000L : 300000L);
    }

    private void populatePatches() {
        this.farmingHandler = new FarmingHandler(Microbot.getClient(), configManager);
        herbPatches.clear();
        allotmentsByRegion.clear();
        flowerByRegion.clear();
        enabledRegions.clear();
        allEnabledHerbs.clear();
        treeStops.clear();

        clientThread.runOnClientThreadOptional(() -> {
            if (config.enableTrees()) {
                addTreeStops(Tab.TREE, PatchImplementation.TREE, config.regularSapling());
            }
            if (config.enableFruitTrees()) {
                addTreeStops(Tab.FRUIT_TREE, PatchImplementation.FRUIT_TREE, config.fruitSapling());
            }
            Map<String, HerbPatch> allHerbsByRegion = new HashMap<>();

            for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.HERB)) {
                HerbPatch _patch = new HerbPatch(patch, config, farmingHandler);
                if (!_patch.isEnabled()) continue;
                enabledRegions.add(_patch.getRegionName());
                allHerbsByRegion.put(_patch.getRegionName(), _patch);
                allEnabledHerbs.put(_patch.getRegionName(),_patch);
                long now = System.currentTimeMillis();
                String region = _patch.getRegionName();
                if (!ledger.due(region, now)) continue;
                // When we are already beside a due patch, verify its live state.
                if (_patch.getPrediction() == CropState.GROWING && !_patch.isInRange(6)) {
                    ledger.record(region, "OBSERVED_GROWING", nextGrowingCheck(region, now));
                    continue;
                }
                herbPatches.add(_patch);
            }

            if (config.enableAllotments()) {
                for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.ALLOTMENT)) {
                    String region = patch.getRegion().getName();
                    if (!ALLOTMENT_FLOWER_REGIONS.contains(region)) continue;
                    CropState prediction = farmingHandler.predictPatch(patch);
                    if (prediction != CropState.GROWING) {
                        allotmentsByRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(patch);
                    }
                }
            }

            if (config.enableFlowers()) {
                for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.FLOWER)) {
                    String region = patch.getRegion().getName();
                    if (!ALLOTMENT_FLOWER_REGIONS.contains(region)) continue;
                    CropState prediction = farmingHandler.predictPatch(patch);
                    if (prediction != CropState.GROWING) {
                        flowerByRegion.put(region, patch);
                    }
                }
            }

            for (String region : allHerbsByRegion.keySet()) {
                boolean alreadyInList = herbPatches.stream().anyMatch(p -> p.getRegionName().equals(region));
                if (alreadyInList || (PATCH_RETRY_DELAY.equals(ledger.outcome(region))
                        && !ledger.due(region, System.currentTimeMillis()))) continue;
                boolean hasAllotmentWork = allotmentsByRegion.containsKey(region);
                boolean hasFlowerWork = flowerByRegion.containsKey(region);
                if (hasAllotmentWork || hasFlowerWork) {
                    herbPatches.add(allHerbsByRegion.get(region));
                }
            }

            saveMemory();
            return true;
        });
    }

    private void addTreeStops(Tab tab, PatchImplementation type, TreeSapling sapling) {
        int level = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        if (level < sapling.level()) {
            log("Skipping " + tab.getName() + ": " + sapling + " requires Farming level " + sapling.level());
            return;
        }
        for (FarmingPatch patch : farmingWorld.getTabs().getOrDefault(tab, Set.of())) {
            if (patch.getImplementation() != type || patch.getLocation() == null || patch.getPatchArea() == null) continue;
            String region = patch.getRegion().getName();
            if ("Farming Guild".equals(region) && level < (tab == Tab.TREE ? 65 : 85)) continue;
            if ("Lletya".equals(region) && Rs2Player.getQuestState(Quest.MOURNINGS_END_PART_I) != QuestState.FINISHED) continue;
            treeStops.add(new TreeStop(patch, sapling));
        }
    }

    private TreeStop nextTree() {
        WorldPoint player = Rs2Player.getWorldLocation();
        return treeStops.stream()
                .min(Comparator.comparingLong((TreeStop stop) -> ledger.checkAt(stop.key()))
                        .thenComparingInt(stop -> player == null ? 0 : player.distanceTo(stop.patch().getLocation()))
                        .thenComparing(TreeStop::key))
                .orElse(null);
    }

    private boolean handleTreeWork() {
        long now = System.currentTimeMillis();
        if (currentTree == null) {
            TreeStop next = nextTree();
            if (next == null || !ledger.due(next.key(), now)) return false;
            currentTree = next;
            treeWalkFailures = 0;
            strongholdTravelDeadline = 0;
            resetPatchFailures();
            if (isBrimhavenTree(next) && !prepareBrimhavenTrip()) {
                if (eventYielded) currentTree = null;
                else if (!stopping) finishTree("BRIMHAVEN_SUPPLIES_MISSING", 30);
                return true;
            }
        }
        if (yieldToRandomEvents()) return true;
        TreeStop tree = currentTree;
        AutoFarmerPlugin.status = "Tending " + tree.key();

        if (!hasTreeSupplies(tree) || Rs2Inventory.isFull()) {
            treeActionStarted = 0;
            if (!prepareTreeSupplies(tree) && !eventYielded && !stopping) {
                deferTreesWithoutSupplies(tree);
            }
            return true;
        }
        WorldPoint location = tree.patch().getLocation();
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return true;
        if (player.distanceTo(location) > 4) {
            AutoFarmerPlugin.status = "Walking to " + tree.key();
            if ("Gnome Stronghold".equals(tree.patch().getRegion().getName())) {
                walkToStrongholdTree(location, player);
                return true;
            }
            if (Rs2Walker.walkWithState(location, 4) == WalkerState.UNREACHABLE
                    && ++treeWalkFailures >= patchRetryLimit()) {
                finishTree("TREE_ROUTE_UNREACHABLE", 15);
            }
            return true;
        }

        strongholdTravelDeadline = 0;
        if (treeActionStarted == 0) treeActionStarted = now;
        if (now - treeActionStarted >= 150_000L) {
            finishTree("TREE_INTERACTION_TIMEOUT", 15);
            return true;
        }
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return true;

        Rs2TileObjectModel object = findTreeObject(tree);
        if (object == null) {
            recordPatchFailure("finding tree object");
            if (exhaustedPatchAction != null) {
                log("No matching tree object at " + tree.patch().getLocation()
                        + "; patch varbit value " + Microbot.getVarbitValue(tree.patch().getVarbit()));
                finishTree("TREE_OBJECT_MISSING", 15);
            }
            return true;
        }
        patchFailures.remove("finding tree object");
        String pickAction = tree.sapling() instanceof TreeSapling.Fruit ? object.findAction("Pick") : null;
        int patchVarbit = tree.patch().getVarbit();
        CropState prediction = clientThread.runOnClientThreadOptional(
                () -> farmingHandler.predictPatch(tree.patch())).orElse(null);
        int objectId = object.getId();
        boolean deadYew = objectId >= ObjectID.YEW_TREE_DEAD_1 && objectId <= ObjectID.YEW_TREE_DEAD_FULLYGROWN;
        String objectName = object.getName();
        boolean deadTree = deadYew || prediction == CropState.DEAD
                || (objectName != null && objectName.toLowerCase(Locale.ROOT).startsWith("dead ")
                && object.hasAction("Clear"));
        if (!treeWasCleared(patchVarbit) && deadTree) {
            clearDeadTree(tree, object, patchVarbit);
        } else if (object.hasAction("Check-health")) {
            clickTreeAction(object, "Check-health");
        } else if (isFruitPickAction(pickAction)) {
            AutoFarmerPlugin.status = "Picking fruit at " + tree.patch().getRegion().getName();
            clickTreeAction(object, pickAction);
        } else if (object.hasAction("Rake")) {
            clickTreeAction(object, "Rake");
        } else if (config.payGardenerToClear() && isPickedPineapplePlant(tree, object)) {
            if (!payGardenerToClear(tree)) recordPatchFailure("gardener clearing");
        } else if (object.hasAction("Clear")) {
            clickTreeAction(object, "Clear");
        } else if (object.hasAction("Dig")) {
            clickTreeAction(object, "Dig");
        } else if (object.hasAction("Chop down") || object.hasAction("Chop-down") || object.hasAction("Chop")) {
            if (config.payGardenerToClear()) {
                if (!payGardenerToClear(tree)) recordPatchFailure("gardener clearing");
            } else {
                String action = object.hasAction("Chop down") ? "Chop down"
                        : object.hasAction("Chop-down") ? "Chop-down" : "Chop";
                clickTreeAction(object, action);
            }
        } else if (Microbot.getVarbitValue(tree.patch().getVarbit()) == 3) {
            if (Rs2Inventory.hasItem(ItemID.WEEDS)) {
                Rs2Inventory.dropAll("Weeds");
            }
            int saplingId = tree.sapling().itemId();
            int count = Rs2Inventory.itemQuantity(saplingId);
            if (selectItemForPatch(saplingId) && object.click()
                    && sleepUntil(() -> Rs2Inventory.itemQuantity(saplingId) < count, 8000)) {
                finishTree("PLANTED_" + tree.sapling(), tree.sapling().growthMinutes());
                if (Rs2Inventory.hasItem(ItemID.PLANTPOT_EMPTY)) {
                    Rs2Inventory.dropAll(ItemID.PLANTPOT_EMPTY);
                }
            } else {
                recordPatchFailure("planting sapling");
            }
        } else if (object.getName() != null && object.getName().toLowerCase(Locale.ROOT).contains("stump")) {
            if (selectItemForPatch(ItemID.SPADE)) object.click();
        } else {
            if (prediction == CropState.GROWING) finishTree("TREE_GROWING", 15);
            else recordPatchFailure("confirming tree state");
        }
        if (currentTree != null && exhaustedPatchAction != null) {
            log("Deferring " + tree.key() + ": retry limit reached for " + exhaustedPatchAction);
            finishTree("TREE_RETRY_DELAY", 15);
        }
        return true;
    }

    private void walkToStrongholdTree(WorldPoint location, WorldPoint player) {
        long now = System.currentTimeMillis();
        if (strongholdTravelDeadline == 0) strongholdTravelDeadline = now + STRONGHOLD_TRAVEL_LIMIT;

        boolean failed = false;
        if (now < strongholdTravelDeadline) {
            if (isInsideStrongholdGate(player)) {
                if (!Rs2Player.isMoving()) {
                    Rs2Walker.setTarget(null);
                    int distance = player.distanceTo(STRONGHOLD_INNER_PATH);
                    failed = !Rs2Walker.walkMiniMap(STRONGHOLD_INNER_PATH) || !sleepUntil(() -> {
                        WorldPoint position = Rs2Player.getWorldLocation();
                        return position != null && position.distanceTo(STRONGHOLD_INNER_PATH) < distance;
                    }, 5_000);
                }
            } else {
                // Preserve the transport pipeline until the gate crossing has landed.
                WalkerState walk = Rs2Walker.walkWithStateUntil(location, 4, () ->
                        stopping || System.currentTimeMillis() >= strongholdTravelDeadline
                                || isInsideStrongholdGate(Rs2Player.getWorldLocation()));
                failed = walk == WalkerState.UNREACHABLE;
            }
        }

        if (stopping || pauseSafetyForManualInput(System.currentTimeMillis())) return;
        WorldPoint position = Rs2Player.getWorldLocation();
        if (position != null && position.distanceTo(location) <= 4) {
            strongholdTravelDeadline = 0;
            return;
        }
        if (System.currentTimeMillis() >= strongholdTravelDeadline
                || (failed && ++treeWalkFailures >= patchRetryLimit())) {
            Rs2Walker.setTarget(null);
            finishTree("TREE_ROUTE_UNREACHABLE", 15);
        }
    }

    private static boolean isInsideStrongholdGate(WorldPoint point) {
        return point != null && point.getPlane() == 0 && point.getX() >= 2457 && point.getX() <= 2465
                && point.getY() >= 3385 && point.getY() <= 3393;
    }

    private boolean isPickedPineapplePlant(TreeStop tree, Rs2TileObjectModel object) {
        if (!(tree.sapling() instanceof TreeSapling.Fruit)
                || !"Pineapple plant".equalsIgnoreCase(object.getName())) return false;
        int state = Microbot.getVarbitValue(tree.patch().getVarbit());
        // Both values are mature pineapple states with no fruit available.
        return state == 142 || state == 161;
    }

    static boolean isFruitPickAction(String action) {
        if (action == null) return false;
        String name = action.trim().toLowerCase(Locale.ROOT);
        return name.equals("pick") || name.startsWith("pick-") || name.startsWith("pick ");
    }

    private boolean isBrimhavenTree(TreeStop tree) {
        return tree != null && "Brimhaven".equals(tree.patch().getRegion().getName());
    }

    private boolean handleBrimhavenRecovery() {
        if (!Microbot.isLoggedIn()) return false;
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location == null || location.getPlane() != 0 || location.getX() < 2690 || location.getX() > 2950
                || location.getY() < 3120 || location.getY() > 3225) return false;

        var client = Microbot.getClient();
        int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int maximum = client.getRealSkillLevel(Skill.HITPOINTS);
        boolean poisoned = client.getVarpValue(VarPlayerID.POISON) > 0;
        boolean needsFood = hp < maximum && (hp <= 12 || hp * 100 <= maximum * 65);
        if (!poisoned && !needsFood) return false;
        long now = System.currentTimeMillis();
        if (now < nextRecoveryAction) return true;

        boolean eatFirst = needsFood && (!poisoned || (hp <= 12 && carriedFarmingFood() > 0));
        boolean recovered = eatFirst ? eatFarmingFood() : drinkFarmingAntipoison();
        if (recovered) {
            nextRecoveryAction = now + 1_800L;
            log("Brimhaven recovery: " + (eatFirst ? "ate food" : "drank antipoison")
                    + " (HP " + hp + "/" + maximum + ", poisoned=" + poisoned + ")");
            return true;
        }
        failRun("Brimhaven recovery needs food or antipoison at " + hp + " HP");
        return true;
    }

    private boolean prepareBrimhavenTrip() {
        if (carriedFarmingFood() >= BRIMHAVEN_FOOD_TARGET && Rs2Inventory.hasItem(FARMING_ANTIPOISON)) return true;
        if (!openSupplyBank()) return false;
        try {
            if (yieldToRandomEvents()) return false;
            Rs2Bank.setWithdrawAsItem();
            if (Rs2Inventory.emptySlotCount() < BRIMHAVEN_FOOD_TARGET + 1 && !depositBankableInventory()) return false;
            if (!Rs2Inventory.hasItem(FARMING_ANTIPOISON)) {
                for (int id : FARMING_ANTIPOISON) {
                    if (Rs2Bank.count(id) > 0 && ensureBankItem(id, "antipoison")) break;
                }
            }
            for (Rs2ItemModel food : farmingFood(Rs2Bank.bankItems())) {
                int missing = BRIMHAVEN_FOOD_TARGET - carriedFarmingFood();
                if (missing <= 0 || eventYielded || stopping) break;
                int id = food.getId();
                topUpBankItem(id, Rs2Inventory.itemQuantity(id) + missing);
            }
            boolean ready = carriedFarmingFood() >= BRIMHAVEN_FOOD_TARGET && Rs2Inventory.hasItem(FARMING_ANTIPOISON);
            if (!ready) log("Skipping Brimhaven: need five healing food and an antipoison potion.");
            return ready;
        } finally {
            Rs2Bank.closeBank();
        }
    }

    private int carriedFarmingFood() {
        return Rs2Inventory.all().stream()
                .filter(item -> farmingFoodHealing(item) > 0)
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

    private List<Rs2ItemModel> farmingFood(Collection<Rs2ItemModel> items) {
        Map<Integer, Integer> healingById = new HashMap<>();
        return items.stream()
                .filter(item -> item != null && healingById.computeIfAbsent(item.getId(),
                        id -> farmingFoodHealing(item)) > 0)
                .sorted(Comparator.comparingInt((Rs2ItemModel item) -> healingById.get(item.getId())).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    private int farmingFoodHealing(Rs2ItemModel item) {
        if (item == null || item.isNoted()) return 0;
        var effect = itemStatChanges.get(item.getId());
        if (effect == null) return 0;
        String name = item.getName();
        if (name == null || name.toLowerCase(Locale.ROOT).startsWith("blighted ")
                || item.getAction("Eat") == null) return 0;
        return Arrays.stream(effect.calculate(Microbot.getClient()).getStatChanges())
                .filter(change -> change.getStat() == Stats.HITPOINTS)
                .mapToInt(change -> change.getTheoretical())
                .sum();
    }

    private boolean eatFarmingFood() {
        for (Rs2ItemModel item : farmingFood(Rs2Inventory.all())) {
            if (Rs2Inventory.interact(item, "Eat")) return true;
        }
        return false;
    }

    private boolean drinkFarmingAntipoison() {
        for (int id : FARMING_ANTIPOISON) {
            if (Rs2Inventory.hasItem(id) && Rs2Inventory.interact(id, "Drink")) return true;
        }
        return false;
    }

    private boolean payGardenerToClear(TreeStop tree) {
        int farmerId = tree.patch().getFarmer();
        if (farmerId <= 0 || Rs2Dialogue.isInDialogue()) return false;
        var gardener = Microbot.getRs2NpcCache().query().withIds(farmerId).nearestOnClientThread();
        if (gardener == null) return false;
        pauseBeforeAction();
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        int coins = Rs2Inventory.itemQuantity(ItemID.COINS);
        if (coins < GARDENER_CLEAR_COST) return false;
        String payAction = "Pay";
        if ("Farming Guild".equals(tree.patch().getRegion().getName())) {
            payAction = tree.sapling() instanceof TreeSapling.Regular ? "Pay (tree patch)" : "Pay (Fruit tree)";
        }
        if (!gardener.click(payAction)) {
            log(tree.key() + ": could not select " + payAction + " on gardener " + farmerId);
            return false;
        }

        AutoFarmerPlugin.status = "Paying gardener at " + tree.patch().getRegion().getName();
        int varbit = tree.patch().getVarbit();
        if (!sleepUntil(() -> Rs2Dialogue.isInDialogue() || treeWasCleared(varbit), 8_000)) return false;
        boolean quotedRemoval = false;
        for (int step = 0; step < 6 && !treeWasCleared(varbit); step++) {
            if (Microbot.pauseAllScripts.get()
                    || net.runelite.client.plugins.microbot.ScriptPauseController.isPaused()) return false;
            quotedRemoval |= isTreeRemovalQuote(Rs2Dialogue.getDialogueText());
            if (Rs2Dialogue.hasSelectAnOption()) {
                quotedRemoval |= isTreeRemovalQuote(Rs2Dialogue.getQuestion());
                for (var option : Rs2Dialogue.getDialogueOptions()) {
                    String text = option.getText();
                    if (!isTreeRemovalQuote(text) && !(quotedRemoval && isAffirmative(text))) continue;
                    Rs2Dialogue.clickOption(text, true);
                    boolean cleared = sleepUntil(() -> treeWasCleared(varbit), 6_000);
                    if (cleared && Rs2Dialogue.hasContinue()) Rs2Dialogue.clickContinue();
                    if (!cleared) log(tree.key() + ": gardener payment clicked, but clearing was not confirmed.");
                    return cleared;
                }
                log(tree.key() + ": no recognized 200 GP tree-removal confirmation.");
                return false;
            }
            if (Rs2Dialogue.hasContinue()) Rs2Dialogue.clickContinue();
            sleep(650);
        }
        return treeWasCleared(varbit);
    }

    private boolean treeWasCleared(int varbit) {
        int value = Microbot.getVarbitValue(varbit);
        return value >= 0 && value <= 3;
    }

    static boolean isTreeRemovalQuote(String text) {
        String quote = normalizeGardenerText(text);
        return !quote.matches(".*\\b(?:no|not|never|protect|watch)\\b.*")
                && quote.matches(".*\\b200\\s*(?:coins?|gp|gold)\\b.*")
                && quote.matches(".*\\b(?:chop(?:ped)?|cut|clear(?:ed)?|remov(?:e|ed))\\b.*");
    }

    static boolean isAffirmative(String text) {
        String option = normalizeGardenerText(text);
        return option.matches("^yes(?:[,.!]?|[, ]+.*)$")
                && !option.matches(".*\\b(?:protect|look after|watch)\\b.*");
    }

    private static String normalizeGardenerText(String text) {
        return text == null ? "" : text.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private void clearDeadTree(TreeStop tree, Rs2TileObjectModel object, int varbit) {
        AutoFarmerPlugin.status = "Clearing dead tree at " + tree.patch().getRegion().getName();
        pauseBeforeAction();
        if (treeWasCleared(varbit)) return;
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        boolean clicked;
        if (object.hasAction("Clear")) {
            clicked = object.click("Clear");
        } else if (object.hasAction("Dig")) {
            clicked = object.click("Dig");
        } else {
            clicked = selectItemForPatch(ItemID.SPADE) && object.click();
        }
        if (clicked && sleepUntil(() -> treeWasCleared(varbit), 8_000)) {
            patchFailures.remove("clearing dead tree");
            log("Cleared dead tree at " + tree.patch().getRegion().getName());
            return;
        }
        log("Dead tree clearing unconfirmed at " + tree.patch().getRegion().getName()
                + " (object " + object.getId() + ", varbit " + Microbot.getVarbitValue(varbit) + ")");
        recordPatchFailure("clearing dead tree");
    }

    private void clickTreeAction(Rs2TileObjectModel object, String action) {
        pauseBeforeAction();
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        if (object.click(action)) {
            patchFailures.remove(action);
            sleep(1500);
        } else {
            recordPatchFailure(action);
        }
    }

    private Rs2TileObjectModel findTreeObject(TreeStop tree) {
        if ("Farming Guild".equals(tree.patch().getRegion().getName())
                && tree.sapling() instanceof TreeSapling.Regular) {
            Rs2TileObjectModel sceneObject = clientThread.runOnClientThreadOptional(
                    () -> findGuildTreeInScene(tree)).orElse(null);
            if (sceneObject != null) return sceneObject;
        }
        int[] ids = tree.sapling() instanceof TreeSapling.Regular
                ? new int[]{ObjectID.FARMING_TREE_PATCH_1, ObjectID.FARMING_TREE_PATCH_2,
                    ObjectID.FARMING_TREE_PATCH_3, ObjectID.FARMING_TREE_PATCH_4, ObjectID.FARMING_TREE_PATCH_5}
                : new int[]{ObjectID.FARMING_FRUIT_TREE_PATCH_1, ObjectID.FARMING_FRUIT_TREE_PATCH_2,
                    ObjectID.FARMING_FRUIT_TREE_PATCH_3, ObjectID.FARMING_FRUIT_TREE_PATCH_4, ObjectID.FARMING_FRUIT_TREE_PATCH_5};
        var cache = Microbot.getRs2TileObjectCache();
        Rs2TileObjectModel known = cache.query().withIds(ids).toList().stream()
                .filter(object -> overlapsTreePatch(tree, object))
                .findFirst().orElse(null);
        if (known != null || !"Farming Guild".equals(tree.patch().getRegion().getName())) return known;

        return cache.query().toList().stream()
                .filter(object -> overlapsTreePatch(tree, object))
                .filter(object -> {
                    int id = object.getId();
                    if (tree.sapling() instanceof TreeSapling.Regular
                            && ((id >= ObjectID.YEW_TREE_SEEDLING && id <= ObjectID.YEW_TREE_DEAD_FULLYGROWN)
                            || id == ObjectID.YEW_TREE_STUMP_NEW
                            || (id >= ObjectID.TREE_PATCH_WEEDED && id <= ObjectID.TREE_PATCH_WEEDS_3))) {
                        return true;
                    }
                    String name = object.getName();
                    if (name == null) return false;
                    String normalized = name.toLowerCase(Locale.ROOT);
                    if (normalized.equals("tree patch") || normalized.equals("fruit tree patch")) {
                        return object.hasAction("Rake") || object.hasAction("Inspect");
                    }
                    return normalized.endsWith(" stump") ? object.hasAction("Dig") || object.hasAction("Clear")
                            : object.hasAction("Check-health") || (object.hasAction("Inspect")
                            && (normalized.endsWith(" tree") || normalized.endsWith(" plant")));
                })
                .findFirst().orElse(null);
    }

    private Rs2TileObjectModel findGuildTreeInScene(TreeStop tree) {
        var view = Microbot.getClient().getTopLevelWorldView();
        if (view == null || view.getScene() == null) return null;
        var tiles = view.getScene().getTiles();
        int plane = tree.patch().getLocation().getPlane();
        if (plane < 0 || plane >= tiles.length) return null;

        var bounds = tree.patch().getPatchArea().getBounds();
        for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
            for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
                var point = net.runelite.api.coords.LocalPoint.fromWorld(view, new WorldPoint(x, y, plane));
                if (point == null) continue;
                var tile = tiles[plane][point.getSceneX()][point.getSceneY()];
                if (tile == null) continue;
                for (var gameObject : tile.getGameObjects()) {
                    if (gameObject == null) continue;
                    var object = new Rs2TileObjectModel(gameObject);
                    var definition = object.getObjectComposition();
                    if (definition == null) continue;
                    String name = definition.getName();
                    if (name == null) continue;
                    boolean patch = "Tree patch".equalsIgnoreCase(name);
                    boolean farmingTree = object.hasAction("Inspect") && object.hasAction("Guide");
                    if (patch || farmingTree || ("Dead Yew".equalsIgnoreCase(name) && object.hasAction("Clear"))) {
                        return object;
                    }
                }
            }
        }
        return null;
    }

    private static boolean overlapsTreePatch(TreeStop tree, Rs2TileObjectModel object) {
        WorldPoint point = object.getWorldLocation();
        if (point == null || point.getPlane() != tree.patch().getLocation().getPlane()) return false;
        var area = tree.patch().getPatchArea();
        if (area.contains(point.getX() + 0.5, point.getY() + 0.5)) return true;
        if (!"Farming Guild".equals(tree.patch().getRegion().getName())) return false;
        var footprint = object.getWorldArea();
        return footprint != null && area.intersects(footprint.getX(), footprint.getY(),
                footprint.getWidth(), footprint.getHeight());
    }

    private static boolean isTreeAxe(Rs2ItemModel item) {
        return item != null && !item.isNoted() && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).endsWith(" axe");
    }

    private boolean hasTreeAxe() {
        return Rs2Inventory.get(AutoFarmerScript::isTreeAxe) != null
                || Rs2Equipment.isWearing(AutoFarmerScript::isTreeAxe);
    }

    private boolean hasTreeSupplies(TreeStop tree) {
        return Rs2Inventory.hasItem(tree.sapling().itemId())
                && Rs2Inventory.hasItem(ItemID.RAKE) && Rs2Inventory.hasItem(ItemID.SPADE)
                && (config.payGardenerToClear()
                    ? Rs2Inventory.itemQuantity(ItemID.COINS) >= GARDENER_CLEAR_COST : hasTreeAxe());
    }

    private boolean prepareTreeSupplies(TreeStop tree) {
        if (!openSupplyBank()) return false;
        if (eventYielded || yieldToRandomEvents()) return false;
        try {
            if (!Rs2Bank.setWithdrawAsItem()) return false;
            if (Rs2Inventory.emptySlotCount() < 4 && !depositBankableInventory()) return false;
            if (!ensureBankItem(ItemID.RAKE, "rake") || !ensureBankItem(ItemID.SPADE, "spade")) return false;
            if (!config.payGardenerToClear() && !hasTreeAxe() && !ensureBankItem(ItemID.BRONZE_AXE, "bronze axe")) return false;
            topUpTravelCoins();
            topUpFortisTablets();
            if (config.payGardenerToClear() && Rs2Inventory.itemQuantity(ItemID.COINS) < GARDENER_CLEAR_COST) return false;
            int saplingId = tree.sapling().itemId();
            long now = System.currentTimeMillis();
            int due = (int) treeStops.stream()
                    .filter(stop -> stop.sapling().equals(tree.sapling()) && ledger.due(stop.key(), now))
                    .count();
            int carried = Rs2Inventory.itemQuantity(saplingId);
            int target = Math.min(due, carried + Math.max(1, Rs2Inventory.emptySlotCount() - 4));
            topUpBankItem(saplingId, target);
            return Rs2Inventory.hasItem(saplingId) && !Rs2Inventory.isFull();
        } finally {
            Rs2Bank.closeBank();
        }
    }

    private void deferTreesWithoutSupplies(TreeStop tree) {
        long retryAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
        for (TreeStop stop : treeStops) {
            if (stop.sapling().equals(tree.sapling()) && ledger.due(stop.key(), System.currentTimeMillis())) {
                ledger.record(stop.key(), "TREE_SUPPLIES_MISSING", retryAt);
            }
        }
        saveMemory();
        String clearSupply = config.payGardenerToClear() ? "coins for the gardener" : "a usable axe";
        log("Tree route deferred: need a rake, spade, " + clearSupply + ", and " + tree.sapling() + " saplings.");
        currentTree = null;
        treeActionStarted = 0;
        strongholdTravelDeadline = 0;
    }

    private void finishTree(String outcome, int minutes) {
        TreeStop tree = currentTree;
        long checkAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
        ledger.record(tree.key(), outcome, checkAt);
        saveMemory();
        log(tree.key() + ": " + outcome + "; check at " + java.time.Instant.ofEpochMilli(checkAt));
        currentTree = null;
        treeActionStarted = 0;
        strongholdTravelDeadline = 0;
    }

    private void getNextPatch() {
        if (currentPatch == null) {
            if (herbPatches.isEmpty()) {
                return;
            }

            currentPatch=herbPatches.stream().filter(HerbPatch::isEnabled)
                .filter(p->Objects.equals(p.getRegionName(),preferredNextRegion)).findFirst()
                .orElseGet(()->CropBreakPlan.next(herbPatches,ledger));
            preferredNextRegion=null;

            if (currentPatch != null) {
                herbPatches.remove(currentPatch);
            }
        }
    }

    private static final int[] ALLOTMENT_PATCH_IDS = {
            ObjectID.FARMING_VEG_PATCH_1, ObjectID.FARMING_VEG_PATCH_2,
            ObjectID.FARMING_VEG_PATCH_3, ObjectID.FARMING_VEG_PATCH_4,
            ObjectID.FARMING_VEG_PATCH_5, ObjectID.FARMING_VEG_PATCH_6,
            ObjectID.FARMING_VEG_PATCH_7, ObjectID.FARMING_VEG_PATCH_8,
            ObjectID.FARMING_VEG_PATCH_9, ObjectID.FARMING_VEG_PATCH_10,
            ObjectID.FARMING_VEG_PATCH_11, ObjectID.FARMING_VEG_PATCH_12,
            ObjectID.FARMING_VEG_PATCH_13, ObjectID.FARMING_VEG_PATCH_14,
            ObjectID.FARMING_VEG_PATCH_15, ObjectID.FARMING_VEG_PATCH_16,
            ObjectID.FARMING_VEG_PATCH_17
    };

    private static final int[] HERB_PATCH_IDS = {
            ObjectID.MYARM_HERBPATCH,
            ObjectID.FARMING_HERB_PATCH_2,
            ObjectID.FARMING_HERB_PATCH_4,
            ObjectID.FARMING_HERB_PATCH_8,
            ObjectID.FARMING_HERB_PATCH_6,
            ObjectID.FARMING_HERB_PATCH_3,
            ObjectID.FARMING_HERB_PATCH_1,
            ObjectID.FARMING_HERB_PATCH_7,
            ObjectID.MY2ARM_HERBPATCH,
            ObjectID.FARMING_HERB_PATCH_5
    };

    // --- Herb patch handling (existing logic, refactored for shared compost/noting) ---

    private boolean handleHerbPatch() {
        if (yieldToRandomEvents()) return false;
        if (!ensureInventorySpace()) return false;

        final var obj = Microbot.getRs2TileObjectCache().query().withIds(HERB_PATCH_IDS).nearest();
        if (obj == null) {
            recordPatchFailure("finding herb patch");
            return false;
        }
        patchFailures.remove("finding herb patch");
        String state = getHerbPatchState(obj);
        if (state.equals("Unknown")) {
            log("Unconfirmed herb state: " + lastPatchDiagnostic);
            recordPatchFailure("reading herb patch state");
            AutoFarmerPlugin.status = "Waiting for patch state to refresh";
            sleep(1200, 1600);
            return false;
        }
        patchFailures.remove("reading herb patch state");
        if (state.equals("Growing")) {
            remember("OBSERVED_GROWING", nextGrowingCheck(currentPatch.getRegionName(), System.currentTimeMillis()));
            return true;
        }

        if (state.equals("Harvestable")) {
            pauseBeforeAction();
            if (!obj.hasAction("Pick") || !obj.click("Pick")) {
                recordPatchFailure("starting herb harvest");
                return false;
            }
            patchFailures.remove("starting herb harvest");
            Rs2Player.waitForWalking();
            sleepUntil(() -> getHerbPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
            return false;
        }

        if (state.equals("Weeds")) {
            pauseBeforeAction();
            if (!obj.hasAction("Rake") || !obj.click("Rake")) return giveUpClearingHerbPatch();
            Rs2Player.waitForWalking();
            sleepUntil(() -> !getHerbPatchState(obj).equals("Weeds"), 15000);
            state = getHerbPatchState(obj);
            if (state.equals("Weeds")) return giveUpClearingHerbPatch();
        }

        if (state.equals("Diseased")) {
            if (!clearDiseasedHerbPatch(obj)) return false;
            state = "Empty";
        }

        if (state.equals("Dead")) {
            pauseBeforeAction();
            if (!obj.hasAction("Clear") || !obj.click("Clear")) return giveUpClearingHerbPatch();
            Rs2Player.waitForWalking();
            sleepUntil(() -> getHerbPatchState(obj).equals("Empty"), 10000);
            state = getHerbPatchState(obj);
            if (state.equals("Dead")) return giveUpClearingHerbPatch();
        }

        if (state.equals("Empty")) {
            if (Rs2Inventory.hasItem("Weeds")) Rs2Inventory.dropAll("Weeds");
            HerbSeedType seedInInventory = getFirstHerbSeedInInventory();
            if (seedInInventory == null) {
                if(config.buySeedsWhenEmpty()) {
                    log("Carried herb seeds exhausted; checking bank and restocking only if its plantable stock is also zero");
                    if(!setupAutoInventory() && !eventYielded)failRun(failureDetail);
                    return false;
                }
                failRun("No plantable herb seeds remain in inventory at " + currentPatch.getRegionName());
                log("No herb seeds found in inventory, skipping patch");
                return true;
            }
            if (!applyCompost(obj)) return false;
            AutoFarmerPlugin.status = "Waiting to plant at " + currentPatch.getRegionName();
            sleep(1200, 1600);
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 8000);
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return false;
            }
            int seedsBefore = Rs2Inventory.itemQuantity(seedInInventory.getItemId());
            if (!selectItemForPatch(seedInInventory.getItemId()) || !obj.click()) {
                recordPatchFailure("starting herb planting");
                return false;
            }
            patchFailures.remove("starting herb planting");
            AutoFarmerPlugin.status = "Confirming planting at " + currentPatch.getRegionName();
            boolean planted = sleepUntil(() -> Rs2Inventory.itemQuantity(seedInInventory.getItemId()) < seedsBefore
                    || getHerbPatchState(obj).equals("Growing"), 15000);
            if (planted) {
                remember("PLANTED_" + seedInInventory.name(), System.currentTimeMillis() + config.growthMinutes() * 60000L);
                log("Planting confirmed at " + currentPatch.getRegionName());
                sleep(1200, 1600);
                return true;
            }
            recordPatchFailure("confirming herb planting");
            log("Planting not confirmed yet; keeping compost state for this patch");
            return false;
        }

        failRun("Cannot continue at " + currentPatch.getRegionName() + ": patch state is " + state);
        return true;
    }

    boolean clearDiseasedHerbPatch(Rs2TileObjectModel obj) {
        if (!getHerbPatchState(obj).equals("Diseased")) return false;
        if (!Rs2Inventory.hasItem(ItemID.SPADE)) {
            failRun("Cannot clear diseased herb at " + currentPatch.getRegionName() + ": spade is missing");
            return false;
        }
        AutoFarmerPlugin.status="Digging up diseased herb at " + currentPatch.getRegionName();
        if (!selectItemForPatch(ItemID.SPADE)) {
            if (!eventYielded) giveUpClearingHerbPatch();
            return false;
        }
        // Recheck after selecting the spade; never dig up a replacement planted manually.
        if (!getHerbPatchState(obj).equals("Diseased")) {
            Rs2Inventory.deselect();
            return false;
        }
        if (!obj.click() || !sleepUntil(() -> getHerbPatchState(obj).equals("Empty"), 10000)) {
            giveUpClearingHerbPatch();
            return false;
        }

        compostedPatches.remove(obj.getWorldLocation());
        log("Diseased herb removed at " + currentPatch.getRegionName() + "; patch confirmed empty, preparing to replant");
        return true;
    }

    boolean giveUpClearingHerbPatch() {
        recordPatchFailure("clearing herb patch");
        return false;
    }

    private String getHerbPatchState(TileObject rs2TileObject) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var base = Microbot.getClient().getObjectDefinition(rs2TileObject.getId());
            var current = base;
            if(base != null && base.getImpostorIds() != null) {
                var transformed=base.getImpostor();
                if(transformed != null)current=transformed;
            }
            if (current == null) { lastPatchDiagnostic="object="+rs2TileObject.getId()+", composition unavailable"; return "Unknown"; }
            // The farming patch metadata owns the per-location transmitted state, including
            // empty/growing crops whose transformed object no longer has a selector.
            int patchVarbit = currentPatch != null ? currentPatch.getPatch().getVarbit() : -1;
            int selector = patchVarbit >= 0 ? patchVarbit : base != null ? base.getVarbitId() : -1;
            int value = selector >= 0 ? Microbot.getClient().getVarbitValue(selector) : -1;
            lastPatchDiagnostic="region="+(currentPatch==null?"none":currentPatch.getRegionName())
                    +", object="+rs2TileObject.getId()+", name="+current.getName()
                    +", actions="+java.util.Arrays.toString(current.getActions())
                    +", patchVarbit="+patchVarbit+", selector="+selector+", value="+value;
            return HerbPatchState.classify(current.getActions(), current.getName(), value);
        }).orElse("Unknown");
    }

    // --- Flower patch handling ---

    private static final int[] FLOWER_PATCH_IDS = {
            ObjectID.FARMING_FLOWER_PATCH_1, ObjectID.FARMING_FLOWER_PATCH_2,
            ObjectID.FARMING_FLOWER_PATCH_3, ObjectID.FARMING_FLOWER_PATCH_4,
            ObjectID.FARMING_FLOWER_PATCH_5, ObjectID.FARMING_FLOWER_PATCH_6,
            ObjectID.FARMING_FLOWER_PATCH_7, ObjectID.FARMING_FLOWER_PATCH_8,
            ObjectID.FARMING_FLOWER_PATCH_9
    };

    private boolean handleFlowerPatch(String region) {
        if (!ensureInventorySpace()) return false;

        final var obj = Microbot.getRs2TileObjectCache().query().withIds(FLOWER_PATCH_IDS).nearest();
        if (obj == null) {
            log("[Flower] No flower patch object found at " + region);
            return true;
        }

        String state = getPatchState(obj);
        log("[Flower] Patch at " + obj.getWorldLocation() + " state=" + state);

        switch (state) {
            case "Harvestable":
                // A flower patch harvests in one action. Free as much space as possible first so a
                // bulk yield (e.g. limpwurt roots) fits rather than overflowing onto the ground.
                noteProduceViaLeprechaun();
                pauseBeforeAction();
            obj.click("Pick");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 10000);
                recoverOwnLimpwurtDrops();
                return false;
            case "Weeds":
                pauseBeforeAction();
            obj.click("Rake");
                Rs2Player.waitForWalking();
                sleepUntil(() -> !getPatchState(obj).equals("Weeds"), 15000);
                return false;
            case "Dead":
                pauseBeforeAction();
            obj.click("Clear");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty"), 10000);
                return false;
            case "Empty":
                if (Rs2Inventory.hasItem("Weeds")) Rs2Inventory.dropAll("Weeds");
                FlowerSeedType flowerSeed = config.flowerSeedType();
                if (!Rs2Inventory.hasItem(flowerSeed.getItemId())) {
                    log("[Flower] No " + flowerSeed.getSeedName() + " in inventory, skipping");
                    return true;
                }
                if (!applyCompost(obj)) return false;
                Rs2Inventory.use(flowerSeed.getItemId());
                obj.click();
                Rs2Player.waitForWalking();
                // Only advance once Growing is confirmed; otherwise retry next tick (weeds may have
                // regrown between clearing and planting, or the click missed).
                if (sleepUntil(() -> getPatchState(obj).equals("Growing"), 10000)) {
                    log("[Flower] Planted at " + obj.getWorldLocation());
                    return true;
                }
                recordPatchFailure("planting flowers");
                log("[Flower] Plant not confirmed (state=" + getPatchState(obj) + "), retrying");
                return false;
            default:
                log("[Flower] Patch is " + state + ", skipping");
                return true;
        }
    }

    // --- Allotment patch handling ---

    /**
     * A multi-tile allotment patch shares one ObjectID across ~12 tiles. Interior tiles cannot be
     * interacted with because the player has no adjacent tile to stand on â€” all four neighbours are
     * other (blocked) patch tiles. An edge tile is interactable: at least one orthogonal neighbour is
     * open ground (passes the BLOCK_MOVEMENT_FULL collision check). isReachable() can't distinguish
     * these (it returns true for any same-worldview object), so we test for a standable neighbour here.
     */
    private static boolean hasStandableNeighbor(WorldPoint tile) {
        if (tile == null) return false;
        return Rs2Tile.isWalkable(tile.dx(1))
                || Rs2Tile.isWalkable(tile.dx(-1))
                || Rs2Tile.isWalkable(tile.dy(1))
                || Rs2Tile.isWalkable(tile.dy(-1));
    }

    /** Squared Euclidean distance â€” finer than WorldPoint.distanceTo (Chebyshev), so ties between a
     *  diagonal and an orthogonal tile resolve toward the orthogonal (genuinely nearest) one. */
    private static int sqDist(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    private boolean handleAllotmentPatches(String region) {
        if (!ensureInventorySpace()) return false;

        var allObjects = Microbot.getRs2TileObjectCache().query().withIds(ALLOTMENT_PATCH_IDS).toList();
        if (allObjects == null || allObjects.isEmpty()) {
            log("[Allotment] No allotment objects found in scene");
            currentAllotmentId = -1;
            return true;
        }

        // distanceTo() is Chebyshev (max(|dx|,|dy|)), so many tiles tie and the tie is broken by list
        // order â€” picking a diagonal tile over the orthogonally-adjacent one. Compare by squared
        // Euclidean distance instead so we land on the genuinely-nearest tile at decision time.
        final WorldPoint playerLoc = Rs2Player.getWorldLocation();
        Rs2TileObjectModel pinned = null;
        if (currentAllotmentId >= 0) {
            pinned = allObjects.stream()
                    .filter(o -> o.getId() == currentAllotmentId && hasStandableNeighbor(o.getWorldLocation()))
                    .min(Comparator.comparingInt(o -> sqDist(o.getWorldLocation(), playerLoc)))
                    .orElse(null);
            if (pinned == null || handledAllotmentIds.contains(currentAllotmentId)) {
                log("[Allotment] Finished patch id=" + currentAllotmentId + ", looking for next");
                currentAllotmentId = -1;
                pinned = null;
            }
        }
        if (currentAllotmentId < 0) {
            pinned = allObjects.stream()
                    .filter(o -> !handledAllotmentIds.contains(o.getId()) && hasStandableNeighbor(o.getWorldLocation()))
                    .min(Comparator.comparingInt(o -> sqDist(o.getWorldLocation(), playerLoc)))
                    .orElse(null);
            if (pinned == null) {
                log("[Allotment] All patches handled at " + region);
                return true;
            }
            currentAllotmentId = pinned.getId();
            log("[Allotment] Starting patch id=" + currentAllotmentId + " near " + pinned.getWorldLocation());
        }
        final var obj = pinned;

        String state = getPatchState(obj);
        log("[Allotment] Patch id=" + currentAllotmentId + " state=" + state);

        switch (state) {
            case "Harvestable":
                pauseBeforeAction();
            obj.click("Pick");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
                return false;
            case "Weeds":
                pauseBeforeAction();
            obj.click("Rake");
                Rs2Player.waitForWalking();
                sleepUntil(() -> !getPatchState(obj).equals("Weeds"), 15000);
                return false;
            case "Dead":
                pauseBeforeAction();
            obj.click("Clear");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty"), 10000);
                return false;
            case "Empty":
                if (Rs2Inventory.hasItem("Weeds")) Rs2Inventory.dropAll("Weeds");
                AllotmentSeedType allotmentSeed = getFirstAllotmentSeedInInventory();
                if (allotmentSeed == null || Rs2Inventory.itemQuantity(allotmentSeed.getItemId()) < 3) {
                    log("[Allotment] Not enough seeds (need 3), skipping id=" + currentAllotmentId);
                    handledAllotmentIds.add(currentAllotmentId);
                    currentAllotmentId = -1;
                    return false;
                }
                if (!applyCompost(obj)) return false;
                Rs2Inventory.use(allotmentSeed.getItemId());
                obj.click();
                Rs2Player.waitForWalking();
                // Only mark done once the patch is confirmed Growing. If the plant didn't take
                // (e.g. weeds regrew between clearing and planting, or the click missed), leave the
                // patch pinned so the next tick re-rakes/re-plants instead of silently giving up.
                if (sleepUntil(() -> getPatchState(obj).equals("Growing"), 10000)) {
                    log("[Allotment] Planted id=" + currentAllotmentId);
                    handledAllotmentIds.add(currentAllotmentId);
                    currentAllotmentId = -1;
                } else {
                    recordPatchFailure("planting allotment " + currentAllotmentId);
                    log("[Allotment] Plant not confirmed (state=" + getPatchState(obj) + "), retrying id=" + currentAllotmentId);
                }
                return false;
            default:
                log("[Allotment] Patch id=" + currentAllotmentId + " is " + state + ", marking done");
                handledAllotmentIds.add(currentAllotmentId);
                currentAllotmentId = -1;
                return false;
        }
    }

    // --- Shared patch state detection via game object actions ---

    private static String getPatchState(TileObject tileObj) {
        Rs2TileObjectModel patch = new Rs2TileObjectModel(tileObj);
        if (patch.hasActionContaining("Rake")) return "Weeds";
        if (patch.hasActionContaining("Pick")) return "Harvestable";
        if (patch.hasActionContaining("Harvest")) return "Harvestable";
        if (patch.hasActionContaining("Clear")) return "Dead";

        var comp = Rs2TileObjectModel.compositionOf(tileObj.getId(), true);
        int varbit = Microbot.getVarbitValue(comp.getVarbitId());
        if (varbit <= 5) return "Empty";

        return "Growing";
    }

    // --- Shared compost application ---

    private boolean selectItemForPatch(int itemId) {
        if (yieldToRandomEvents()) return false;
        pauseBeforeAction();
        if (yieldToRandomEvents()) return false;
        if (Rs2Inventory.isItemSelected() && Rs2Inventory.getSelectedItemId() != itemId) {
            Rs2Inventory.deselect();
            if (!sleepUntil(() -> !Rs2Inventory.isItemSelected(), 1200)) return false;
        }
        if (!Rs2Inventory.isItemSelected() && !Rs2Inventory.use(itemId)) return false;
        return sleepUntil(() -> Rs2Inventory.isItemSelected() && Rs2Inventory.getSelectedItemId() == itemId, 1800);
    }

    private boolean applyCompost(Rs2TileObjectModel obj) {
        CompostType compost = config.compostType();
        if (compost == CompostType.NONE) return true;
        if (!compost.isBottomless() && config.refillLeprechaun() && Rs2Inventory.hasItem(FarmingSupplies.notedId(compost.getItemId()))) {
            if (yieldToRandomEvents()) return false;
            AutoFarmerPlugin.status = "Refilling leprechaun compost";
            pauseBeforeAction();
            if (!Rs2Leprechaun.depositNotedCompost(compost.getItemId())) {
                recordPatchFailure("refilling compost");
                log("Compost refill not confirmed; keeping notes and retrying before farming");
                return false;
            }
            patchFailures.remove("refilling compost");
            log("Leprechaun refill confirmed; stored compost = " + FarmingSupplies.stored(compost.getItemId()));
            return false;
        }
        if (compostedPatches.contains(obj.getWorldLocation())) return true;

        if (compost.isBottomless()) {
            if (!Rs2Inventory.hasItem(compost.getItemId())) {
                recordPatchFailure("finding bottomless compost bucket");
                log("Bottomless compost bucket not found in inventory");
                return false;
            }
        } else {
            if (!Rs2Inventory.hasItem(compost.getItemId())) {
                if (!Rs2Leprechaun.withdrawCompost(compost.getItemId())) {
                    log("Failed to withdraw " + compost.getCompostName() + " from leprechaun");
                    if (!Rs2Inventory.hasItem(compost.getItemId())) recordPatchFailure("withdrawing compost");
                    return false;
                }
                return false;
            }
            patchFailures.remove("withdrawing compost");
        }

        int xpBefore = Microbot.getClient().getSkillExperience(Skill.FARMING);
        int compostBefore = Rs2Inventory.itemQuantity(compost.getItemId());
        AutoFarmerPlugin.status = "Applying compost";
        alreadyComposted = false;
        checkingCompost = true;
        if (!selectItemForPatch(compost.getItemId()) || !obj.click()) { checkingCompost = false; return false; }
        Rs2Player.waitForWalking();
        boolean applied = sleepUntil(
                () -> alreadyComposted || Rs2Inventory.itemQuantity(compost.getItemId()) < compostBefore
                        || Microbot.getClient().getSkillExperience(Skill.FARMING) > xpBefore, 10000);

        checkingCompost = false;
        if (!applied) {
            recordPatchFailure("applying compost");
            log("Compost not confirmed; waiting before retry");
            sleep(1800, 2400);
            return false;
        } else if (config.dropEmptyBuckets() && !compost.isBottomless()) {
            Rs2Inventory.drop(ItemID.BUCKET_EMPTY);
        }
        patchFailures.remove("applying compost");
        compostedPatches.add(obj.getWorldLocation());
        log("Compost confirmed; will not reapply to this patch during this run");
        sleep(1200, 1600);
        return true;
    }

    // --- Shared inventory space management ---

    private boolean ensureInventorySpace() {
        if (!Rs2Inventory.isFull()) return true;
        return noteProduceViaLeprechaun();
    }

    private boolean noteProduceViaLeprechaun() {
        Rs2NpcModel leprechaun = Microbot.getRs2NpcCache().query().withName("Tool leprechaun").nearestOnClientThread();
        if (leprechaun == null) return false;

        // Note EVERY notable produce type we hold more than one of, not just the patch we're currently
        // harvesting. Otherwise produce from an earlier patch (e.g. grimy herbs) keeps occupying slots
        // and space never actually gets freed. Using an item on the leprechaun notes its whole stack.
        Set<Integer> seen = new HashSet<>();
        List<Rs2ItemModel> toNote = new ArrayList<>();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item == null || item.isNoted() || item.getName() == null) continue;
            if (!isNotableProduce(item.getName())) continue;
            if (Rs2Inventory.itemQuantity(item.getId()) <= 1) continue;
            if (seen.add(item.getId())) toNote.add(item);
        }

        boolean notedAny = false;
        for (Rs2ItemModel item : toNote) {
            Rs2Inventory.use(item);
            leprechaun.click("Talk-to");
            Rs2Inventory.waitForInventoryChanges(10000);
            notedAny = true;
        }
        if (notedAny) return true;

        if (Rs2Inventory.hasItem("Weeds")) {
            Rs2Inventory.dropAll("Weeds");
            return true;
        }
        if (Rs2Inventory.hasItem(ItemID.BUCKET_EMPTY)) {
            Rs2Inventory.drop(ItemID.BUCKET_EMPTY);
            return true;
        }
        return false;
    }

    /** A limpwurt patch harvests in bulk; if the inventory fills mid-harvest the surplus roots drop
     *  to the ground. Reclaim ONLY our own dropped roots (never another player's), noting between
     *  pickups to make room. Bounded loop so a contested/uncollectable drop can't spin forever. */
    private void recoverOwnLimpwurtDrops() {
        for (int i = 0; i < 10; i++) {
            Rs2TileItemModel root = Microbot.getRs2TileItemCache().query()
                    .withId(ItemID.LIMPWURT_ROOT)
                    .where(Rs2TileItemModel::isOwned)
                    .within(3)
                    .nearest();
            if (root == null) return;
            if (Rs2Inventory.isFull() && !noteProduceViaLeprechaun()) return; // can't free space, give up
            if (!root.pickup()) return;
            Rs2Inventory.waitForInventoryChanges(3000);
        }
    }

    private static final String[] ALLOTMENT_PRODUCE = {
            "Potato", "Onion", "Cabbage", "Tomato", "Sweetcorn", "Strawberry", "Watermelon", "Snape grass"
    };
    private static final String[] FLOWER_PRODUCE = {
            "Marigold", "Rosemary", "Nasturtium", "Woad leaf", "Limpwurt root", "White lily"
    };

    /** A farming product worth noting for space: any grimy herb, or allotment/flower produce. Produce
     *  names are matched exactly so seeds ("Potato seed", "Marigold seed") are never caught. */
    private static boolean isNotableProduce(String name) {
        if (name.startsWith("Grimy")) return true;
        for (String p : ALLOTMENT_PRODUCE) if (name.equals(p)) return true;
        for (String p : FLOWER_PRODUCE) if (name.equals(p)) return true;
        return false;
    }

    // --- Seed helpers ---

    HerbSeedType getFirstHerbSeedInInventory() {
        for (HerbSeedType herbType : HerbSeedType.getPlantableHerbs(Microbot.getClient().getRealSkillLevel(Skill.FARMING))) {
            if ((config.herbSeedType()==HerbSeedType.BEST || config.herbSeedType()==herbType) && Rs2Inventory.itemQuantity(herbType.getItemId())>0) return herbType;
        }
        return null;
    }

    private AllotmentSeedType getFirstAllotmentSeedInInventory() {
        if (config.allotmentSeedType() != AllotmentSeedType.BEST) {
            AllotmentSeedType selected = config.allotmentSeedType();
            if (Rs2Inventory.hasItem(selected.getItemId())) return selected;
            return null;
        }
        for (AllotmentSeedType seed : AllotmentSeedType.getPlantableSeeds(
                Microbot.getClient().getRealSkillLevel(Skill.FARMING))) {
            if (Rs2Inventory.hasItem(seed.getItemId())) return seed;
        }
        return null;
    }

    // --- Banking ---

    private String inventoryReadinessReason="Missing farming supplies";
    private static final int[] TELEPORT_RUNES={ItemID.LAWRUNE,ItemID.AIRRUNE,ItemID.EARTHRUNE,ItemID.FIRERUNE,ItemID.WATERRUNE};
    private final Set<Integer> unavailableRuneStock=new HashSet<>();
    private boolean staffChecked;
    private int checkedHerbTier;
    private static final int HERB_SEED_RESERVE=50;
    private boolean needBank(String reason) {
        inventoryReadinessReason=reason;
        AutoFarmerPlugin.status="Banking: " + reason;
        log("Inventory check: " + reason);
        return false;
    }
    private int carriedHerbSeeds() {
        int seeds=0;
        for(HerbSeedType seed:HerbSeedType.getPlantableHerbs(Microbot.getClient().getRealSkillLevel(Skill.FARMING)))
            if(config.herbSeedType()==HerbSeedType.BEST || config.herbSeedType()==seed)seeds+=Rs2Inventory.itemQuantity(seed.getItemId());
        return seeds;
    }
    private boolean keepForRun(Rs2ItemModel item) {
        if(keepInInventory(item))return true;
        int id=item.getId();
        if (id == ItemID.COINS) return true;
        if (id == ItemID.POH_TABLET_FORTISTELEPORT && config.enableVarlamore()) return true;
        if (config.enableFruitTrees() && ((isBrimhavenTree(currentTree) && farmingFoodHealing(item) > 0)
                || Arrays.stream(FARMING_ANTIPOISON).anyMatch(potion -> potion == id))) return true;
        if(id==ItemID.RAKE || id==ItemID.SPADE || id==ItemID.DIBBER || id==ItemID.FAIRY_ENCHANTED_SECATEURS
                || id==ItemID.ECTOPHIAL || id==ItemID.AIR_BATTLESTAFF)return true;
        if(id==ItemID.LAWRUNE || id==ItemID.AIRRUNE || id==ItemID.EARTHRUNE || id==ItemID.FIRERUNE || id==ItemID.WATERRUNE)return true;
        for(HerbSeedType seed:HerbSeedType.getPlantableHerbs(Microbot.getClient().getRealSkillLevel(Skill.FARMING)))if(id==seed.getItemId())return true;
        if(config.enableAllotments())for(AllotmentSeedType seed:AllotmentSeedType.values())if(id==seed.getItemId())return true;
        if(config.enableFlowers() && id==config.flowerSeedType().getItemId())return true;
        if ((config.enableTrees() || config.enableFruitTrees()) && isTreeAxe(item)) return true;
        if (config.enableTrees() && id == config.regularSapling().itemId()) return true;
        if (config.enableFruitTrees() && id == config.fruitSapling().itemId()) return true;
        CompostType compost=config.compostType();
        return compost!=CompostType.NONE && (id==compost.getItemId() || id==FarmingSupplies.notedId(compost.getItemId()));
    }
    private boolean hasReadyHerbInventory() {
        if(config.enableAllotments() || config.enableFlowers())return needBank("checking optional flower/allotment supplies");
        if(!Rs2Inventory.hasItem(ItemID.RAKE))return needBank("missing rake");
        if(!Rs2Inventory.hasItem(ItemID.SPADE))return needBank("missing spade");
        if(!Rs2Inventory.hasItem(ItemID.DIBBER))return needBank("missing seed dibber");
        int seeds=carriedHerbSeeds();
        if(seeds==0)return needBank("no plantable herb seeds carried");
        if(!config.allowPartialRuns() && seeds<herbPatches.size())
            return needBank("only "+seeds+" seeds for "+herbPatches.size()+" patches");
        if(config.herbSeedType()==HerbSeedType.BEST) {
            List<HerbSeedType> eligible=HerbSeedType.getPlantableHerbs(Microbot.getClient().getRealSkillLevel(Skill.FARMING));
            HerbSeedType carried=getFirstHerbSeedInInventory();
            if(!eligible.isEmpty() && carried!=null && eligible.get(0).getLevelRequired()>carried.getLevelRequired()
                    && checkedHerbTier<eligible.get(0).getLevelRequired())return needBank("checking newly unlocked herb seeds");
        }
        CompostType compost=config.compostType();
        if(compost.isBottomless() && !Rs2Inventory.hasItem(compost.getItemId()))return needBank("missing filled bottomless bucket");
        if(compost!=CompostType.NONE && !compost.isBottomless() && FarmingSupplies.stored(compost.getItemId())==0
                && !Rs2Inventory.hasItem(compost.getItemId()) && !Rs2Inventory.hasItem(FarmingSupplies.notedId(compost.getItemId())))return needBank("no carried or stored compost");
        if(config.useAirBattlestaff() && !staffChecked && !Rs2Equipment.isWearing(ItemID.AIR_BATTLESTAFF)) {
            if(!Rs2Inventory.hasItem(ItemID.AIR_BATTLESTAFF))return needBank("checking bank for air battlestaff");
            Rs2Inventory.equip(ItemID.AIR_BATTLESTAFF);
            if(!sleepUntil(()->Rs2Equipment.isWearing(ItemID.AIR_BATTLESTAFF),3000))return needBank("air battlestaff equip not confirmed");
        }
        staffChecked=true;
        int lowRune=lowTeleportRune();
        if(lowRune>=0)return needBank("low "+teleportRuneName(lowRune)+" ("+Rs2Inventory.itemQuantity(lowRune)+" carried)");
        Rs2Bank.closeBank();
        log("Reusing carried tools and "+seeds+" plantable seeds; no bank trip required");
        return true;
    }
    private static String teleportRuneName(int id) {
        if(id==ItemID.LAWRUNE)return "law runes";
        if(id==ItemID.AIRRUNE)return "air runes";
        if(id==ItemID.EARTHRUNE)return "earth runes";
        if(id==ItemID.FIRERUNE)return "fire runes";
        return "water runes";
    }
    private int lowTeleportRune() {
        for(int id:TELEPORT_RUNES) {
            if(id==ItemID.AIRRUNE && Rs2Equipment.isWearing(ItemID.AIR_BATTLESTAFF))continue;
            if(!unavailableRuneStock.contains(id) && Rs2Inventory.itemQuantity(id)<=(id==ItemID.LAWRUNE?5:10))return id;
        }
        return -1;
    }
    private boolean topUpTeleportRunes() {
        if(!Rs2Bank.isOpen())return false;
        Rs2Bank.setWithdrawAsItem();
        for(int id:TELEPORT_RUNES) {
            if(id==ItemID.AIRRUNE && Rs2Equipment.isWearing(ItemID.AIR_BATTLESTAFF))continue;
            if(Rs2Bank.count(id)<=0) {
                if(unavailableRuneStock.add(id))log("No banked "+teleportRuneName(id)+"; retaining carried stock and allowing walking. Rechecking at the next necessary bank visit.");
                continue;
            }
            unavailableRuneStock.remove(id);
            if(!topUpBankItem(id,id==ItemID.LAWRUNE?100:200))return false;
            if(Rs2Bank.count(id)<=0)unavailableRuneStock.add(id);
        }
        return true;
    }

    private void topUpTravelCoins() {
        int carried = Rs2Inventory.itemQuantity(ItemID.COINS);
        if (carried >= BANK_COIN_TARGET || !Rs2Bank.isOpen()) return;
        if (carried == 0 && Rs2Inventory.isFull()) {
            log("Coin top-up skipped: no free inventory slot for a coin stack.");
            return;
        }
        String priorFailure = failureDetail;
        try {
            Rs2Bank.setWithdrawAsItem();
            topUpBankItem(ItemID.COINS, BANK_COIN_TARGET);
            int balance = Rs2Inventory.itemQuantity(ItemID.COINS);
            if (balance < BANK_COIN_TARGET && !eventYielded) {
                log("Carrying " + balance + " GP after bank top-up; target is " + BANK_COIN_TARGET + " GP.");
            }
        } finally {
            failureDetail = priorFailure;
        }
    }

    private void topUpFortisTablets() {
        int id = ItemID.POH_TABLET_FORTISTELEPORT;
        if (!config.enableVarlamore() || !Rs2Bank.isOpen()
                || Rs2Inventory.itemQuantity(id) >= FORTIS_TABLET_TARGET || Rs2Bank.count(id) <= 0
                || Rs2Player.getQuestState(Quest.TWILIGHTS_PROMISE) != QuestState.FINISHED
                || canCastFortisTeleport()) return;
        if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(id)) return;

        String priorFailure = failureDetail;
        try {
            Rs2Bank.setWithdrawAsItem();
            topUpBankItem(id, FORTIS_TABLET_TARGET);
        } finally {
            failureDetail = priorFailure;
        }
    }

    private boolean topUpBankItem(int id,int target) {
        for(int attempt=1;attempt<=3;attempt++) {
            int before=Rs2Inventory.itemQuantity(id);
            int amount=Math.min(Math.max(0,target-before),Rs2Bank.count(id));
            if(amount<=0)return true;
            if(yieldToRandomEvents())return false;
            log("Supply top-up: item="+id+", carried="+before+", target="+target+", withdrawing="+amount+", attempt="+attempt);
            pauseBeforeAction();Rs2Bank.withdrawX(id,amount);
            if(sleepUntil(()->Rs2Inventory.itemQuantity(id)>=before+amount,5000))return true;
        }
        failureDetail="Supply top-up not confirmed: item="+id+", carried="+Rs2Inventory.itemQuantity(id)+", target="+target+", bank="+Rs2Bank.count(id);
        log(failureDetail);
        return false;
    }

    private boolean ensureBankItem(int itemId, String name) {
        if (Rs2Inventory.hasItem(itemId)) return true;
        for (int attempt = 0; attempt < 3; attempt++) {
            pauseBeforeAction();
            Rs2Bank.withdrawX(itemId, 1);
            if (sleepUntil(() -> Rs2Inventory.hasItem(itemId), 2500)) return true;
        }
        log("Could not verify withdrawal of " + name + "; staying at bank");
        return false;
    }

    private boolean openSupplyBank() {
        if (Rs2Bank.isOpen()) return true;
        int initialYaw = net.runelite.client.plugins.microbot.util.camera.Rs2Camera.getYaw();
        for (int attempt = 0; attempt < 4; attempt++) {
            if (stopping || !Microbot.isLoggedIn()) return false;
            AutoFarmerPlugin.status = "Banking: opening bank (" + (attempt + 1) + "/4)";
            if (attempt == 0) {
                var bank = Rs2Bank.getNearestBank();
                if (bank == null) return false;
                Rs2Walker.walkTo(bank.getWorldPoint(), 3);
            } else {
                log("Bank click failed; retrying with camera angle " + attempt);
                net.runelite.client.plugins.microbot.util.camera.Rs2Camera.setPitch(383);
                net.runelite.client.plugins.microbot.util.camera.Rs2Camera.resetZoom();
                net.runelite.client.plugins.microbot.util.camera.Rs2Camera.setYaw((initialYaw + attempt * 512) % 2048);
            }
            Rs2Bank.openBank();
            // The visible bank, rather than the click return value, confirms success.
            if (Rs2Bank.isOpen() || sleepUntil(Rs2Bank::isOpen, 2500)) return true;
        }
        log("Bank remained closed after four attempts with camera recovery");
        return false;
    }

    private boolean setupAutoInventory() {
        failureDetail="Could not reach or open the bank to prepare supplies";
        AutoFarmerPlugin.status="Banking: "+inventoryReadinessReason;
        if (!openSupplyBank()) return false;

        Rs2Bank.setWithdrawAsItem();
        if (!bankCleared) {
            pauseBeforeAction();
            if (!depositInventoryExcept(this::keepForRun)) {
                log("Bank deposit did not finish; leaving supplies untouched until retry");
                return false;
            }
            bankCleared = true;
        }

        int herbPatchCount = Math.max(1,(int) herbPatches.stream().filter(HerbPatch::isEnabled).count() + (currentPatch == null ? 0 : 1));
        int allotmentLocationCount = countEnabledAllotmentFlowerLocations();

        boolean toolsOk = true;
        toolsOk &= ensureBankItem(ItemID.RAKE, "Rake");
        toolsOk &= ensureBankItem(ItemID.SPADE, "Spade");
        toolsOk &= ensureBankItem(ItemID.DIBBER, "Seed dibber");
        if (!toolsOk) {
            failureDetail="Missing farming tools: rake, spade or seed dibber";
            log(failureDetail);
            return false;
        }

        if (!Rs2Inventory.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS) && Rs2Bank.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
            Rs2Bank.withdrawX(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
        }

        if (config.useAirBattlestaff() && !Rs2Equipment.isWearing(ItemID.AIR_BATTLESTAFF)) {
            if (Microbot.getClient().getRealSkillLevel(Skill.ATTACK) >= 30 && Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= 30
                    && Rs2Bank.hasItem(ItemID.AIR_BATTLESTAFF)) {
                pauseBeforeAction();
                Rs2Bank.withdrawAndEquip(ItemID.AIR_BATTLESTAFF);
                if (!sleepUntil(() -> Rs2Equipment.isWearing(ItemID.AIR_BATTLESTAFF), 5000)) {
                    log("Air battlestaff equip not confirmed; using available air runes instead");
                }
            } else log("Air battlestaff absent or level requirement unmet; using available air runes instead");
        }

        staffChecked=true;
        // Teleports are optional: the walker can walk to ordinary patches.
        if(!topUpTeleportRunes())return false;

        if(config.buySeedsWhenEmpty()) {
            AutoFarmerPlugin.status="Checking seed stock / GE restock";
            seedRestocker.restockWhenEmpty(config.herbSeedType(),Microbot.getClient().getRealSkillLevel(Skill.FARMING));
        }
        topUpTravelCoins();
        topUpFortisTablets();
        if(!preferBestBankedHerb())return false;
        // Withdraw herb seeds
        HerbSeedType seedType = config.herbSeedType();
        if (seedType == HerbSeedType.BEST) {
            if (!withdrawBestAvailableSeeds(herbPatchCount)) {
                failureDetail="Insufficient plantable herb seeds in bank for " + herbPatchCount + " patches at Farming " + Microbot.getClient().getRealSkillLevel(Skill.FARMING);
                log(failureDetail);
                return false;
            }
        } else {
            failureDetail="Cannot withdraw enough " + seedType.getSeedName() + " for " + herbPatchCount + " patches; check bank stock and Farming requirement";
            if (!withdrawSpecificSeeds(seedType.getItemId(), seedType.getSeedName(), herbPatchCount,
                    seedType.getLevelRequired(), config.allowPartialRuns())) {
                return false;
            }
        }

        if(!topUpHerbReserve())return false;
        if(config.herbSeedType()==HerbSeedType.BEST) {
            List<HerbSeedType> eligible=HerbSeedType.getPlantableHerbs(Microbot.getClient().getRealSkillLevel(Skill.FARMING));
            if(!eligible.isEmpty())checkedHerbTier=eligible.get(0).getLevelRequired();
        }

        // Withdraw allotment seeds
        if (config.enableAllotments() && allotmentLocationCount > 0) {
            int allotmentSeedsNeeded = 3 * 2 * allotmentLocationCount;
            AllotmentSeedType allotmentSeed = config.allotmentSeedType();
            if (allotmentSeed == AllotmentSeedType.BEST) {
                if (!withdrawBestAvailableAllotmentSeeds(allotmentSeedsNeeded)) {
                    log("Failed to withdraw allotment seeds");
                    if (!config.allowPartialRuns()) return false;
                }
            } else {
                if (!withdrawSpecificSeeds(allotmentSeed.getItemId(), allotmentSeed.getSeedName(),
                        allotmentSeedsNeeded, allotmentSeed.getLevelRequired(), config.allowPartialRuns())) {
                    if (!config.allowPartialRuns()) return false;
                }
            }
        }

        // Withdraw flower seeds
        if (config.enableFlowers() && allotmentLocationCount > 0) {
            FlowerSeedType flowerSeed = config.flowerSeedType();
            int flowerSeedsNeeded = allotmentLocationCount;
            if (!withdrawSpecificSeeds(flowerSeed.getItemId(), flowerSeed.getSeedName(),
                    flowerSeedsNeeded, flowerSeed.getLevelRequired(), config.allowPartialRuns())) {
                if (!config.allowPartialRuns()) return false;
            }
        }

        // Withdraw compost (bottomless only â€” non-bottomless comes from leprechaun)
        CompostType refill=config.compostType();
        if(config.refillLeprechaun() && refill!=CompostType.NONE && !refill.isBottomless()) {
            int id=refill.getItemId(),noted=FarmingSupplies.notedId(id);
            int amount=FarmingSupplies.refillQuantity(FarmingSupplies.stored(id),Rs2Bank.count(id),Rs2Inventory.itemQuantity(noted));
            if(amount>0) {
                int before=Rs2Inventory.itemQuantity(noted);
                try {
                    Rs2Bank.setWithdrawAsNote();Rs2Bank.withdrawX(id,amount);
                    if(!sleepUntil(()->Rs2Inventory.itemQuantity(noted)>=before+amount,5000)){failureDetail="Compost note top-up not confirmed";return false;}
                    log("Carrying "+amount+" compost notes for leprechaun refill");
                }finally{Rs2Bank.setWithdrawAsItem();}
            }
        }
        CompostType compostType = config.compostType();
        if (compostType != CompostType.NONE && compostType.isBottomless()) {
            if (!Rs2Inventory.hasItem(compostType.getItemId()) && !Rs2Bank.withdrawX(compostType.getItemId(), 1)) {
                failureDetail="Filled bottomless compost bucket missing from bank";
                log(failureDetail);
                return false;
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 5000);

        if (!Rs2Inventory.hasItem(ItemID.RAKE) || !Rs2Inventory.hasItem(ItemID.SPADE) || !Rs2Inventory.hasItem(ItemID.DIBBER)) {
            log("Tool verification failed; staying at bank");
            return false;
        }
        bankCleared = false;
        log("Inventory verified: rake, spade, seed dibber present; starting farm run");
        return true;
    }

    private boolean preferBestBankedHerb() {
        if(config.herbSeedType()!=HerbSeedType.BEST)return true;
        HerbSeedType carried=getFirstHerbSeedInInventory();
        for(HerbSeedType seed:HerbSeedType.getPlantableHerbs(Microbot.getClient().getRealSkillLevel(Skill.FARMING))) {
            if(carried!=null && seed.getLevelRequired()<=carried.getLevelRequired())return true;
            if(Rs2Bank.count(seed.getItemId())>0)return topUpBankItem(seed.getItemId(),HERB_SEED_RESERVE);
        }
        return true;
    }

    private boolean topUpHerbReserve() {
        HerbSeedType seed=getFirstHerbSeedInInventory();
        if(seed==null){failureDetail="Seed withdrawal did not leave any usable herb seeds in inventory";return false;}
        // Opportunistic reserve: never buy or demand 50 when only a smaller stock remains.
        if(!topUpBankItem(seed.getItemId(),HERB_SEED_RESERVE))return false;
        log("Keeping herb seed reserve: " + Rs2Inventory.itemQuantity(seed.getItemId()) + " " + seed.getSeedName());
        return true;
    }

    private boolean withdrawSpecificSeeds(int itemId, String seedName, int count, int levelRequired, boolean allowPartial) {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        if (farmingLevel < levelRequired) {
            log("Cannot plant " + seedName + " - requires Farming level " + levelRequired + " (you have " + farmingLevel + ")");
            return false;
        }

        count=Math.max(0,count-Rs2Inventory.itemQuantity(itemId));
        if(count==0)return true;
        if (!Rs2Bank.withdrawX(itemId, count)) {
            if (!allowPartial) {
                log("Failed to withdraw " + count + " " + seedName);
                return false;
            }
            int available = Rs2Bank.count(itemId);
            if (available > 0) {
                int toWithdraw = Math.min(available, count);
                Rs2Bank.withdrawX(itemId, toWithdraw);
                log("Partial run: withdrew " + toWithdraw + " " + seedName + " instead of " + count);
            } else {
                log("No " + seedName + " available in bank");
                return false;
            }
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        return true;
    }

    private boolean withdrawBestAvailableSeeds(int patchCount) {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        List<HerbSeedType> plantableHerbs = HerbSeedType.getPlantableHerbs(farmingLevel);

        if (plantableHerbs.isEmpty()) {
            log("No herbs can be planted at farming level " + farmingLevel);
            return false;
        }

        int seedsWithdrawn = carriedHerbSeeds();

        for (HerbSeedType herb : plantableHerbs) {
            if (seedsWithdrawn >= patchCount) break;
            int availableSeeds = Rs2Bank.count(herb.getItemId());
            if (availableSeeds > 0) {
                int toWithdraw = Math.min(availableSeeds, patchCount - seedsWithdrawn);
                if (Rs2Bank.withdrawX(herb.getItemId(), toWithdraw)) {
                    seedsWithdrawn += toWithdraw;
                    log("Withdrew " + toWithdraw + " " + herb.getSeedName());
                }
            }
        }

        if (seedsWithdrawn < patchCount && !config.allowPartialRuns()) {
            return false;
        }
        return seedsWithdrawn > 0;
    }

    private boolean withdrawBestAvailableAllotmentSeeds(int totalSeedsNeeded) {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        List<AllotmentSeedType> plantableSeeds = AllotmentSeedType.getPlantableSeeds(farmingLevel);

        if (plantableSeeds.isEmpty()) {
            log("No allotment seeds can be planted at farming level " + farmingLevel);
            return false;
        }

        int seedsWithdrawn = 0;

        for (AllotmentSeedType seed : plantableSeeds) {
            if (seedsWithdrawn >= totalSeedsNeeded) break;
            int available = Rs2Bank.count(seed.getItemId());
            if (available > 0) {
                int toWithdraw = Math.min(available, totalSeedsNeeded - seedsWithdrawn);
                if (Rs2Bank.withdrawX(seed.getItemId(), toWithdraw)) {
                    seedsWithdrawn += toWithdraw;
                    log("Withdrew " + toWithdraw + " " + seed.getSeedName());
                }
            }
        }

        if (seedsWithdrawn < totalSeedsNeeded && !config.allowPartialRuns()) {
            return false;
        }
        return seedsWithdrawn > 0;
    }

    private int countEnabledAllotmentFlowerLocations() {
        int count = 0;
        if (config.enableArdougne()) count++;
        if (config.enableCatherby()) count++;
        if (config.enableVarlamore()) count++;
        if (config.enableFalador()) count++;
        if (config.enableHosidius()) count++;
        if (config.enableMorytania()) count++;
        return count;
    }

    @Override
    public void shutdown() {
        if(safetyExecutor!=null){safetyExecutor.shutdownNow();safetyExecutor=null;}
        super.shutdown();
        navigationGuard.release();
        mouseFeel.restore();
        releaseAutoLogin();
        travelDeferrals.clear();
        failureStop = null;
        failureProfile = null;
        seedRestocker = null;
        seenLoggedIn = false;
        offlineSince = 0;
        runStartedAt = 0;
        progressSignature = "";
        cropBreak = false;preparingCropBreak=false;breakDestination=null;preferredNextRegion=null;allEnabledHerbs.clear();
        accountProfile = null;
        dailyBreakProfile = null;
        dailyBreakSchedule = null;
        takingDailyBreak = false;
        dailyBreakLoginAttemptAt = 0;
        loginAttemptAt = 0;
        loginSettledAt = 0;
        initialized = false;
        staffChecked=false;
        bankCleared = false;
        retainedAtBank.clear();unavailableRuneStock.clear();
        compostedPatches.clear();
        nextRunAt = 0;
        currentTree = null;
        treeActionStarted = 0;
        strongholdTravelDeadline = 0;
        treeStops.clear();
        fortisDetourWaypoint = null;
        nextFortisTeleportAttempt = 0;
        currentPatch = null;
        unreachableWalks = 0;

        allotmentsByRegion.clear();
        flowerByRegion.clear();
        currentPhase = LocationPhase.HERB;
        handledAllotmentIds.clear();
        currentAllotmentId = -1;
    }
}
