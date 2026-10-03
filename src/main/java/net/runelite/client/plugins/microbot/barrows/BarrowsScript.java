package net.runelite.client.plugins.microbot.barrows;

import com.google.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class BarrowsScript extends Script {

    /** Highest → lowest catalytic rune tiers for wind spells (Surge / Wave / Blast). */
    private static final List<String> RUNE_TIERS = Arrays.asList(
            "Wrath rune",
            "Blood rune",
            "Death rune"
    );

    /** Invalidates prior scheduled loops across plugin restarts / duplicate run() calls. */
    private static final AtomicInteger RUN_GENERATION = new AtomicInteger();
    private static volatile ScheduledFuture<?> activeMainFuture;
    private static volatile long lastErrorLogMs = 0;

    public static boolean inTunnels = false;
    public static boolean outOfPoweredStaffCharges = false;
    public static boolean usingPoweredStaffs = false;
    public static boolean firstRun = false;
    /** Cleared only after Inventory Setup equipment matches — blocks barrows until then. */
    private boolean startingEquipmentReady = false;

    private boolean loggedCachedInventorySetupWarning = false;
    private boolean shouldBank = false;
    private boolean shouldAttackSkeleton = false;
    private boolean varbitCheckEnabled = true;
    /**
     * Sticky POH travel gate: set when teleToPoh fails despite runes (canCast false / no tabs).
     * Cleared only when a house tablet is in inventory so suppliesCheck cannot flap shouldBank.
     */
    private boolean requireHouseTabsToTravel = false;
    /** Sticky: once the puzzle interface is seen, freeze pathing until it closes. */
    private boolean waitingOnPuzzle = false;
    /** Monsters killed since the last puzzle door (max 2 on the way to chest). */
    private int monstersKilledThisRoom = 0;
    private static final int MAX_MONSTERS_PER_ROOM = 2;
    /**
     * Pathfinder slack for "on the way": via(player→npc→chest) may be this many tiles
     * longer than direct(player→chest) and still count. Not a room-size constant.
     * Used as fallback when no chest polyline is available (brother targeting).
     */
    private static final int EN_ROUTE_PATH_SLACK_TILES = 2;
    /**
     * Max Chebyshev distance from the player→chest polyline to count as "in the hallway".
     * Side rooms sit farther off that line than this.
     */
    private static final int EN_ROUTE_PATH_PROXIMITY_TILES = 2;
    /**
     * Do not path across chambers for RP fodder — only fight what we are walking past.
     * Scene tiles (instance-correct via {@link #distancePlayerToNpc}).
     */
    private static final int EN_ROUTE_MAX_SCENE_DISTANCE = 8;
    /** Max polyline steps ahead of the player to consider an en-route fight. */
    private static final int EN_ROUTE_MAX_PATH_STEPS_AHEAD = 10;

    public static String WhoisTun = "Unknown";
    public String neededRune = "unknown";

    private int tunnelLoopCount = 0;
    int scriptDelay = Rs2Random.between(300,600);
    public static int ChestsOpened = 0;
    private int minRuneAmt;
    private int minForgottenBrews = 0;

    long walkerDelay = Rs2Random.between(1000,2000);

    private WorldPoint FirstLoopTile;
    private WorldPoint Chest = new WorldPoint(3552,9694,0);

    private Rs2PrayerEnum NeededPrayer;
    public static List<String> barrowsPieces = new ArrayList<>();
    private ScheduledFuture<?> WalkToTheChestFuture;

    @Inject
    Rs2NpcCache rs2NpcCache;
    @Inject Rs2TileItemCache rs2TileItemCache;
    @Inject Rs2PlayerCache rs2PlayerCache;
    @Inject Rs2TileObjectCache rs2TileObjectCache;



    public boolean run(BarrowsConfig config, BarrowsPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        // Intentional combat only — brother/skeleton attacks are re-issued after food/pots.
        try {
            Rs2Combat.setAutoRetaliate(false);
        } catch (Exception e) {
            Microbot.log("setAutoRetaliate failed: " + e.getClass().getSimpleName());
        }
        // Hard-stop any leftover main loop / chest walker so we never run BarrowsScript-N duplicates.
        stopAllScriptTasks();
        final int myGen = RUN_GENERATION.incrementAndGet();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            // Stale instance from a prior run()/plugin restart — exit immediately.
            if (myGen != RUN_GENERATION.get()) {
                return;
            }
            try {
                // Walker/sleepUntil leave the interrupt flag set on this executor thread.
                // That makes client-thread widget + NPC reads return empty until plugin toggle —
                // matching "skeletons skipped / puzzle fails until restart".
                Thread.interrupted();

                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                // If an Inventory Setup is selected, wait until its equipment matches.
                // Empty selection = use current gear and continue.
                if(!startingEquipmentReady){
                    try {
                        if(!ensureStartingEquipment(config)){
                            return;
                        }
                        startingEquipmentReady = true;
                        firstRun = false;
                    } catch (Exception equipEx) {
                        Microbot.log("Equipment setup check failed: " + equipEx.getClass().getSimpleName()
                                + (equipEx.getMessage() != null ? ": " + equipEx.getMessage() : "")
                                + " — retrying. Enable Inventory Setups or clear the setup to use current gear.");
                        return;
                    }
                }

                if(barrowsPieces == null){
                    barrowsPieces = new ArrayList<>();
                }
                if(barrowsPieces.isEmpty()) barrowsPieces.add("Nothing yet.");

                // Crypt mounds share the underground Y band (9600s) but are plane 3.
                // Actual tunnels are plane 0 in that same band — never treat a crypt as tunnels.
                if(isInTunnelCoords()) {
                    inTunnels = true;
                } else {
                    if(tunnelLoopCount != 0){
                        tunnelLoopCount = 0;
                    }
                    // Never keep a stale inTunnels=true while standing in a crypt mound / overworld.
                    inTunnels = false;
                }

                //powered staffs
                Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
                String weaponName = weapon != null ? weapon.getName() : null;
                if(weaponName != null && (
                        weaponName.contains("Trident of the") ||
                        weaponName.contains("Tumeken's") ||
                        weaponName.contains("sceptre") ||
                        weaponName.contains("Sanguinesti") ||
                        weaponName.contains("Crystal staff"))) {
                    usingPoweredStaffs = true;
                } else {
                    usingPoweredStaffs = false;
                    minRuneAmt = config.minRuneAmount();
                    gettheRune();
                    Rs2Spellbook spellbook = Rs2Magic.getSpellbook();
                    if(spellbook == null || !spellbook.equals(Rs2Spellbook.MODERN)){
                        swapTheSpellbook();
                        return;
                    }
                }

                minForgottenBrews = config.minForgottenBrew();
                int rewardPotential = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
                // ~86% = 870/1012. If tunnel brother still alive, leave room for his combat-level RP.
                shouldAttackSkeleton = config.shouldGainRP()
                        && rewardPotential < getRpTargetForEightySix();

                if(usingPoweredStaffs) {
                    if (outOfPoweredStaffCharges) {
                        Microbot.log("No charges left on our staff. Stopping...");
                        super.shutdown();
                    }
                }

                outOfSupplies(config);

                // Never leave for Barrows / POH until inventory meets configured mins.
                // (e.g. min prayer pots = 3 with only 2 → bank at Ferox first, not Burgh after.)
                if(shouldBank && !isNearBarrows() && !inTunnels
                        && Rs2Player.getWorldLocation().getPlane() != 3
                        && !isInPlayerOwnedHouse()){
                    // Fall through to the shouldBank restock block this tick.
                } else if(isPohTravelMode(config)) {
                    WorldPoint here = Rs2Player.getWorldLocation();
                    if (here != null && !inTunnels && !shouldBank && here.distanceTo(new WorldPoint(3573, 3296, 0)) > 60) {
                        if(Rs2Bank.isOpen()){
                            closeBank();
                            return;
                        }
                        //needed to intercept the walker
                        if(rs2TileObjectCache.query().withId(4525).nearest() == null){
                            if(!teleToPoh()){
                                shouldBank = true;
                                return;
                            }
                            sleepUntil(() -> Rs2Player.getAnimation() == 4069 || Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                            sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                            sleepUntil(() -> rs2TileObjectCache.query().withId(4525).nearest() != null, Rs2Random.between(6000, 10000));
                        }
                        handlePOH(config);
                        return;
                    }
                }

                if(!inTunnels && !shouldBank) {

                    if(BreakHandlerScript.lockState != null && !BreakHandlerScript.lockState.get()){
                        if(BreakHandlerScript.breakIn < 60 && BreakHandlerScript.breakIn != -1){
                            Microbot.log("Going on break soon, doing nothing.");
                            return;
                        }
                    }

                    if(BreakHandlerScript.lockState != null){
                        BreakHandlerScript.lockState.set(true);
                    }

                    brotherMounds:
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        Rs2WorldArea mound = brother.getHumpWP();
                        NeededPrayer = brother.whatToPray;
                        outOfSupplies(config);
                        if(shouldBank){
                            return;
                        }

                        stopFutureWalker();
                        closeBank();

                        if(!usingPoweredStaffs) setAutoCast();

                        Microbot.log("Checking mound for: " + brother.getName());

                        // Never dig the known empty tunnel coffin until the other five are dead.
                        if(brother.name.equals(WhoisTun) && !readyForTunnelEntry()){
                            Microbot.log("Skipping " + WhoisTun + " tunnel coffin until other brothers are dead ("
                                    + countKilledBrothers() + "/5).");
                            continue;
                        }

                        if(everyBrotherWasKilled()){
                            if(WhoisTun.equals("Unknown")){
                                Microbot.log("We're not sure who tunnel is, and every brother is dead. Checking all mounds manually");
                                varbitCheckEnabled = false;
                            }
                        } else {
                            if(!varbitCheckEnabled){
                                varbitCheckEnabled = true;
                            }
                        }

                        if(!WhoisTun.equals("Unknown")){
                            if(!varbitCheckEnabled){
                                varbitCheckEnabled = true;
                            }
                        }

                        //resume progress from varbits
                        if(varbitCheckEnabled) {
                            if (brother.name.contains("Dharok")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 1) {
                                    Microbot.log("We all ready killed Dharok.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Guthan")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 1) {
                                    Microbot.log("We all ready killed Guthan.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Karil")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 1) {
                                    Microbot.log("We all ready killed Karil.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Torag")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 1) {
                                    Microbot.log("We all ready killed Torag.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Verac")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 1) {
                                    Microbot.log("We all ready killed Verac.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Ahrim")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 1) {
                                    Microbot.log("We all ready killed Ahrim.");
                                    continue;
                                }
                            }
                        }

                        //Enter mound
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            Microbot.log("Entering the mound");
                            // Only use POH portal when actually in the house — never from Barrows surface.
                            if(isInPlayerOwnedHouse()){
                                handlePOH(config);
                            }
                            goToTheMound(mound);
                            digIntoTheMound(mound);
                        }

                        if (Rs2Player.getWorldLocation().getPlane() == 3) {
                            Microbot.log("We're in the mound");

                            // Empty-coffin tunnel brother: no combat prayer, keep searching until tunnels.
                            boolean alreadyTunnelBrother = brother.name.equals(WhoisTun);
                            if(!alreadyTunnelBrother){
                                if(config.shouldPrayAgainstWeakerBrothers()){
                                    activatePrayer(brother.getWhatToPray());
                                } else {
                                    if(!brother.getName().contains("Torag") && !brother.getName().contains("Guthan") && !brother.getName().contains("Verac")){
                                        activatePrayer(brother.getWhatToPray());
                                    }
                                }
                            } else {
                                // Last (or known) tunnel coffin — drop protect and re-enter via dialogue.
                                disableProtectPrayers();
                            }

                            // we're in the mound, prayer is active (unless tunnel coffin)
                            Rs2TileObjectModel sarc = rs2TileObjectCache.query().withIds(20770,20720,20722,20771,20721,20772).nearest();
                            Rs2NpcModel currentBrother = null;
                            Microbot.log("Found the Sarcophagus");
                            while(currentBrother == null) {
                                Microbot.log("Searching the Sarcophagus");
                                if (!super.isRunning()) break;
                                if(isInTunnelCoords()){
                                    inTunnels = true;
                                    disableProtectPrayers();
                                    return;
                                }
                                if(!isInCryptMound()){
                                    break;
                                }

                                if (sarc == null) {
                                    sarc = rs2TileObjectCache.query().withIds(20770,20720,20722,20771,20721,20772).nearest();
                                }
                                if (sarc != null && sarc.click("Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving() || Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                            Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat()
                                                    || Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                            Rs2Random.between(3000, 6000));
                                    // the brother could take a second to spawn in.
                                    sleepUntil(() -> hintNpcModel() != null || Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                            Rs2Random.between(750, 1500));
                                }

                                if(Rs2Dialogue.isInDialogue() && (Rs2Dialogue.hasDialogueText("You've found a hidden")
                                        || Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")
                                        || brother.name.equals(WhoisTun))){
                                    WhoisTun = brother.name;
                                    Microbot.log(brother.name+" is our tunnel");
                                    disableProtectPrayers();
                                    if(readyForTunnelEntry()){
                                        if(enterTunnelsFromDialogue()){
                                            return;
                                        }
                                        Microbot.log("Tunnel dialogue did not complete; re-clicking sarcophagus.");
                                        sarc = null;
                                        sleep(300, 600);
                                        continue;
                                    }
                                    Microbot.log(WhoisTun + " is tunnel — leaving to finish remaining brothers first ("
                                            + countKilledBrothers() + "/5 killed).");
                                    leaveTheMound();
                                    continue brotherMounds;
                                }

                                if(hintNpcModel() != null) {
                                    currentBrother = hintNpcModel();
                                } else if(brother.name.equals(WhoisTun)) {
                                    if(!readyForTunnelEntry()){
                                        Microbot.log("At tunnel coffin early — leaving to finish brothers ("
                                                + countKilledBrothers() + "/5).");
                                        leaveTheMound();
                                        continue brotherMounds;
                                    }
                                    // Known empty tunnel coffin with no dialogue yet — keep re-clicking.
                                    Microbot.log("Re-clicking tunnel sarcophagus.");
                                    sarc = null;
                                    sleep(300, 600);
                                    continue;
                                } else {
                                    break;
                                }

                                if (currentBrother != null) break;
                            }

                            // Fight until our hinted brother is dead (or gone). Do not leave mid-fight.
                            while(isInCryptMound()
                                    && findTunnelBrother() != null
                                    && !findTunnelBrother().isDead()){
                                if(!super.isRunning()){
                                    break;
                                }
                                checkForAndFightBrother(config);
                                outOfSupplies(config);
                                if(shouldBank){
                                    return;
                                }
                                // If fight helper returned without killing, brief pause then retry.
                                if(findTunnelBrother() != null && !findTunnelBrother().isDead()){
                                    sleep(300, 600);
                                }
                            }

                            // Tunnel brother — never climb out; keep trying coffin → tunnels.
                            if(brother.name.equals(WhoisTun)) {
                                if(!readyForTunnelEntry()){
                                    Microbot.log("Deferring tunnel entry — only " + countKilledBrothers() + "/5 brothers killed.");
                                    leaveTheMound();
                                    continue;
                                }
                                if(enterTunnelsFromDialogue()){
                                    return;
                                }
                                if(isInCryptMound()){
                                    Microbot.log("Re-searching tunnel sarcophagus.");
                                    disableProtectPrayers();
                                    Rs2TileObjectModel tunnelSarc = rs2TileObjectCache.query().withIds(20770,20720,20722,20771,20721,20772).nearest();
                                    if(tunnelSarc != null && tunnelSarc.click("Search")){
                                        sleepUntil(() -> Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                                Rs2Random.between(2000, 4000));
                                        if(enterTunnelsFromDialogue()){
                                            return;
                                        }
                                    }
                                }
                                // Stay in the mound this tick — do not leaveTheMound().
                                return;
                            }

                            // Only leave after the crypt brother is dead / gone.
                            if(findTunnelBrother() != null && !findTunnelBrother().isDead()){
                                Microbot.log("Brother still alive — staying in mound.");
                                return;
                            }
                            leaveTheMound();
                        }
                    }
                }

                if(!WhoisTun.equals("Unknown") && !shouldBank && !inTunnels){
                    int howManyBrothersWereKilled = countKilledBrothers();
                    if(!readyForTunnelEntry()){
                        Microbot.log("Tunnel known (" + WhoisTun + ") but only " + howManyBrothersWereKilled
                                + "/5 brothers killed — finishing mounds first.");
                        return;
                    } else {
                        Microbot.log("Going to the tunnels.");
                    }

                    stopFutureWalker();
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        if (brother.name.equals(WhoisTun)) {
                            // Tunnel entry only — do not prime combat prayer for the empty-coffin brother.
                            Rs2WorldArea tunnelMound = brother.getHumpWP();

                            handlePOH(config);

                            goToTheMound(tunnelMound);

                            digIntoTheMound(tunnelMound, false);

                            int tunnelSearchAttempts = 0;
                            final int maxTunnelSearchAttempts = 8;
                            while(!Rs2Dialogue.isInDialogue() && !isInTunnelCoords()) {
                                if (!super.isRunning()) break;
                                if (Rs2Player.getWorldLocation().getPlane() != 3) break;
                                if (tunnelSearchAttempts >= maxTunnelSearchAttempts) {
                                    Microbot.log("Tunnel sarcophagus never opened dialogue; leaving mound to retry.");
                                    this.leaveTheMound();
                                    return;
                                }

                                Rs2TileObjectModel sarc = rs2TileObjectCache.query().withIds(20770,20720,20722,20771,20721,20772).nearest();
                                if (sarc == null) {
                                    sleep(300, 600);
                                    tunnelSearchAttempts++;
                                    continue;
                                }

                                Microbot.log("Searching tunnel sarcophagus (" + (tunnelSearchAttempts + 1) + "/" + maxTunnelSearchAttempts + ")");
                                if (sarc.click("Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving() || Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                            Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat()
                                                    || Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                            Rs2Random.between(3000, 6000));
                                    sleepUntil(() -> Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                                            Rs2Random.between(2000, 4000));
                                }
                                tunnelSearchAttempts++;
                                sleep(200, 400);
                            }

                            if(enterTunnelsFromDialogue()){
                                return;
                            }
                            // Dialogue may have closed without entry — stay put; next tick re-searches.
                            break;
                        }
                    }
                }


                if(inTunnels && !shouldBank && isInTunnelCoords()) {
                    Thread.interrupted();
                    Microbot.log("In the tunnels");

                    if (Rs2Player.getQuestState(Quest.HIS_FAITHFUL_SERVANTS) != QuestState.FINISHED) {
                        Microbot.showMessage("Complete the 'His Faithful Servants' quest for the webwalker to function correctly");
                        shutdown();
                        return;
                    }

                    if(!varbitCheckEnabled) varbitCheckEnabled=true;

                    // Priority: brother → puzzle (if open) → same-room monsters (max 2) → chest walk
                    updateTunnelRoomTracking();

                    Rs2NpcModel ourBrother = findTunnelBrother();
                    if(ourBrother != null){
                        Microbot.log("Tunnel brother present: " + ourBrother.getName() + " — fighting.");
                        stopFutureWalker();
                        checkForAndFightBrother(config);
                        return;
                    }
                    disableProtectPrayers();

                    eatFood();
                    outOfSupplies(config);
                    if(shouldBank){
                        return;
                    }

                    if(isDoorPuzzleOpenRaw()){
                        waitingOnPuzzle = true;
                        if(!solvePuzzleUntilClosed()){
                            return;
                        }
                        resetTunnelRoomKills();
                    } else {
                        waitingOnPuzzle = false;
                    }

                    if(findTunnelTrashAggressor() != null){
                        stopFutureWalker();
                        if(!clearTunnelTrashAggressor(config)){
                            return;
                        }
                    }
                    if(shouldFightMonsterOnWayToChest()){
                        stopFutureWalker();
                        Microbot.log("Fighting same-room monster on the way to chest ("
                                + (monstersKilledThisRoom + 1) + "/" + MAX_MONSTERS_PER_ROOM + ").");
                        fightTunnelMonster(config);
                        return;
                    }

                    Rs2TileObjectModel barrowsChest = rs2TileObjectCache.query().withId(20973).nearest();
                    boolean atChest = barrowsChest != null
                            && barrowsChest.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 5;

                    if(atChest){
                        if(shouldFightMonsterOnWayToChest()){
                            stopFutureWalker();
                            fightTunnelMonster(config);
                            return;
                        }
                        if(isDoorPuzzleOpenRaw()){
                            solvePuzzleUntilClosed();
                            return;
                        }
                        stopFutureWalker();

                        if(barrowsChest.click("Open")){
                            sleepUntil(() -> findTunnelBrother() != null, Rs2Random.between(4000,6000));
                        } else {
                            return;
                        }

                        if(findTunnelBrother() != null){
                            checkForAndFightBrother(config);
                            return;
                        }

                        Map<String, Integer> piecesBeforeLoot = snapshotBarrowsPieceCounts();
                        int io = 0;
                        while (io < 2) {
                            if (!super.isRunning()) break;
                            if(barrowsChest.click("Search")){
                                sleep(500, 1500);
                            }
                            if (Rs2Widget.hasWidget("Barrows chest")) {
                                break;
                            }
                            io++;
                        }
                        // Loot can land a tick late — wait briefly, then record UI "Pieces found".
                        sleepUntil(() -> !snapshotBarrowsPieceCounts().equals(piecesBeforeLoot)
                                        || !Rs2Widget.hasWidget("Barrows chest"),
                                Rs2Random.between(800, 1500));
                        recordNewBarrowsPieces(piecesBeforeLoot);

                        suppliesCheck(config);
                        ChestsOpened++;
                        WhoisTun = "Unknown";
                        inTunnels = false;
                        resetTunnelRoomKills();
                        if(shouldBank){
                            Microbot.log("We should bank.");
                        } else if(!isPohTravelMode(config)){
                            Rs2Inventory.interact("Barrows teleport", "Break");
                            sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                        } else {
                            if(Rs2Bank.isOpen()){
                                closeBank();
                                return;
                            }
                            if(!teleToPoh()){
                                shouldBank = true;
                                return;
                            }
                            sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                            handlePOH(config);
                        }
                        return;
                    }

                    // Primary: keep walking to the chest (aborts mid-path for fights/puzzle).
                    ensureChestWalk();
                    Thread.interrupted();
                    updateTunnelRoomTracking();
                    if(isDoorPuzzleOpenRaw()){
                        waitingOnPuzzle = true;
                        if(!solvePuzzleUntilClosed()){
                            return;
                        }
                        resetTunnelRoomKills();
                    }
                    if(findTunnelBrother() != null){
                        stopFutureWalker();
                        checkForAndFightBrother(config);
                        return;
                    }
                    if(findTunnelTrashAggressor() != null){
                        stopFutureWalker();
                        clearTunnelTrashAggressor(config);
                        return;
                    }
                    if(shouldFightMonsterOnWayToChest()){
                        stopFutureWalker();
                        Microbot.log("Fighting same-room monster on the way to chest ("
                                + (monstersKilledThisRoom + 1) + "/" + MAX_MONSTERS_PER_ROOM + ").");
                        fightTunnelMonster(config);
                        return;
                    }

                    stuckInTunsCheck();
                    tunnelLoopCount++;
                }

                if(shouldBank){
                    // Re-check before committing to a bank walk — never leave Barrows for Burgh
                    // when inventory already has what we need (stale flag / false positive).
                    suppliesCheck(config);
                    if(!shouldBank){
                        return;
                    }
                    if(!Rs2Bank.isOpen()){
                        stopFutureWalker();
                        // From Barrows / crypt / tunnels / POH: RoD to Ferox — never walk Burgh.
                        if(isNearBarrows() || inTunnels || isInPlayerOwnedHouse()
                                || Rs2Player.getWorldLocation().getPlane() == 3){
                            outOfSupplies(config);
                            if(!isAtFeroxEnclave()){
                                if(tryFeroxTeleportViaRingOfDueling()){
                                    Microbot.log("Out of supplies at Barrows — teleporting to Ferox to bank.");
                                    sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                                    sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                                }
                                return;
                            }
                        }
                        goToNearestBankForRestock();
                        if(BreakHandlerScript.lockState != null){
                            BreakHandlerScript.lockState.set(false);
                        }
                    } else {
                        Rs2Food ourfood = config.food();
                        int ourFoodsID = ourfood.getId();
                        String ourfoodsname = ourfood.getName();

                        // Backup: catch any piece still in inv that chest-loot recording missed.
                        recordNewBarrowsPieces(new HashMap<>());
                        if(Rs2Inventory.isFull()
                                || Rs2Inventory.contains(it -> it != null && isBarrowsEquipmentName(it.getName()))
                                || Rs2Inventory.contains(it -> it != null && it.getName() != null
                                && it.getName().contains("Coins"))){
                            Rs2Bank.depositAllExcept(neededRune, "Wrath rune", "Blood rune", "Death rune", "Law rune", "Air rune", "Earth rune", "Dust rune",
                                    "Rune pouch", "Divine rune pouch", "Moonlight moth", "Moonlight moth mix (2)", "Teleport to house", "Spade",
                                    "Prayer potion(4)", "Prayer potion(3)", "Forgotten brew(4)", "Forgotten brew(3)", "Barrows teleport",
                                    "Ring of dueling", ourfoodsname);
                        }

                        // Required every trip — always withdraw before optional/random banking steps.
                        if(!Rs2Inventory.contains("Spade")){
                            if(Rs2Bank.getBankItem("Spade")!=null && Rs2Bank.getBankItem("Spade").getQuantity()>=1){
                                Rs2Bank.withdrawOne("Spade");
                                sleepUntil(()-> Rs2Inventory.contains("Spade"), Rs2Random.between(2000,4000));
                            } else {
                                Microbot.log("We're out of Spades. stopping...");
                                super.shutdown();
                            }
                        }

                        // Always stock Ring(s) of dueling for Ferox pool tele — never skip / never RoD(8)-only.
                        ensureRingOfDuelingFromBank();

                        // Catalytic runes before POH checks — POH must not abort banking before Blood/Death fallback.
                        if(!usingPoweredStaffs) {
                            if (Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= config.minRuneAmount()) {
                                if (bankHasEnoughRunes(neededRune, config.minRuneAmount())) {
                                    withdrawNeededRunes(config);
                                } else if (downgradeRuneTier(config)) {
                                    if (bankHasEnoughRunes(neededRune, config.minRuneAmount())) {
                                        withdrawNeededRunes(config);
                                    }
                                } else {
                                    Microbot.log("We're out of " + neededRune + "s and no lower-tier runes available. stopping...");
                                    super.shutdown();
                                }
                            }
                        } else if(outOfPoweredStaffCharges){
                            Microbot.log("We're out of staff charges. stopping...");
                            super.shutdown();
                        }

                        if(isPohTravelMode(config)){
                            ensurePohTravelSupplies(config);
                        }

                        int howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 60){
                            if(Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())){
                                if(Rs2Bank.getBankItem(config.prayerRestoreType().getPrayerRestoreTypeID())!=null){
                                    if(Rs2Bank.getBankItem(config.prayerRestoreType().getPrayerRestoreTypeID()).getQuantity()>=config.targetPrayerPots()){
                                        int amt = ((Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())) - (Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID())));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(config.prayerRestoreType().getPrayerRestoreTypeID(), amt)){
                                            sleepUntil(()-> Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) > Rs2Random.between(4,8), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+config.prayerRestoreType().getPrayerRestoreTypeID()+" need at least "+config.targetPrayerPots()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(config.minForgottenBrew() > 0) {
                                if (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < Rs2Random.between(config.minForgottenBrew(), config.targetForgottenBrew())) {
                                    if (Rs2Bank.getBankItem("Forgotten brew(4)") != null) {
                                        if (Rs2Bank.getBankItem("Forgotten brew(4)").getQuantity() >= config.targetForgottenBrew()) {
                                            int amt = ((Rs2Random.between(config.minForgottenBrew(), config.targetForgottenBrew())) - (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)")));
                                            if (amt <= 0) {
                                                amt = 1;
                                            }
                                            Microbot.log("Withdrawing " + amt);
                                            if (Rs2Bank.withdrawX("Forgotten brew(4)", amt)) {
                                                sleepUntil(() -> Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") > Rs2Random.between(1, 3), Rs2Random.between(2000, 4000));
                                            }
                                        } else {
                                            Microbot.log("We're out of " + " Forgotten brew " + " need at least " + config.targetForgottenBrew() + " stopping...");
                                            super.shutdown();
                                        }
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(!isPohTravelMode(config)){
                                if(Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID())==null || Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()).getQuantity() < Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports())){
                                if(Rs2Bank.getBankItem(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID())!=null){
                                    if(Rs2Bank.getBankItem(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()).getQuantity()>=config.targetBarrowsTeleports()){
                                        if(Rs2Bank.withdrawX(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID(), Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports()))){
                                            sleep(Rs2Random.between(300,750));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName()+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                        super.shutdown();
                                    }
                                } else {
                                    Microbot.log("We're out of "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName()+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                    super.shutdown();
                                }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){

                            if(Rs2Inventory.count(ourFoodsID) < config.targetFoodAmount()){
                                if(Rs2Bank.getBankItem(ourFoodsID)!=null){
                                    if(Rs2Bank.getBankItem(ourFoodsID).getQuantity()>=config.targetFoodAmount()){
                                        int amt = (Rs2Random.between(config.minFood(),config.targetFoodAmount()) - (Rs2Inventory.count(ourFoodsID)));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(ourFoodsID, amt)){
                                            sleepUntil(()-> Rs2Inventory.count(ourFoodsID) >= 10, Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+ourfoodsname+" need at least "+config.targetFoodAmount()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        // Re-assert RoD after random withdraws (inventory may have filled).
                        ensureRingOfDuelingFromBank();

                        suppliesCheck(config);

                        if(!shouldBank){
                            closeBank();
                            if(!Rs2Bank.isOpen()){
                                // Nearest-bank restock done — RoD to Ferox for the restoration pool, then continue.
                                restoreAtFeroxPool();
                                handlePOH(config);
                            }
                        } else {
                            if(Rs2Player.getRunEnergy() <= 5){
                                closeBank();
                                if(!Rs2Bank.isOpen()){
                                    restoreAtFeroxPool();
                                }
                            }
                        }

                    }
                }

                scriptDelay = Rs2Random.between(200,750);
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Thread.interrupted();
                // Rate-limit: NPEs from Inventory Setups / missing gear used to spam every tick.
                long now = System.currentTimeMillis();
                if (now - lastErrorLogMs > 5000) {
                    lastErrorLogMs = now;
                    String where = "";
                    for (StackTraceElement el : ex.getStackTrace()) {
                        if (el.getClassName().contains("barrows")) {
                            where = " at " + el.getFileName() + ":" + el.getLineNumber();
                            break;
                        }
                    }
                    Microbot.log("BarrowsScript error: " + ex.getClass().getSimpleName()
                            + (ex.getMessage() != null ? ": " + ex.getMessage() : "")
                            + where);
                    ex.printStackTrace();
                }
            }
        }, 0, scriptDelay, TimeUnit.MILLISECONDS);
        activeMainFuture = mainScheduledFuture;
        return true;
    }

    /**
     * @return true when ready to run: no Inventory Setup selected = use current gear;
     * otherwise bank until that setup's equipment matches.
     */
    private boolean ensureStartingEquipment(BarrowsConfig config){
        InventorySetup selected;
        try {
            selected = config.inventorySetup();
        } catch (Exception e) {
            Microbot.log("Inventory Setup unreadable (" + e.getClass().getSimpleName()
                    + ") — using current gear.");
            return true;
        }
        if(selected == null){
            Microbot.log("No Inventory Setup selected — using current gear.");
            return true;
        }

        Rs2InventorySetup inventorySetup = resolveInventorySetup(config);
        if(inventorySetup == null){
            Microbot.log("Selected Inventory Setup could not be loaded — using current gear.");
            return true;
        }
        if(inventorySetup.doesEquipmentMatch()){
            Microbot.log("Inventory Setup equipment matches — starting Barrows.");
            return true;
        }

        Microbot.log("Equipment does not match Inventory Setup — banking to load gear before Barrows.");

        // From Barrows / tunnels / crypt / POH: RoD to Ferox first (never walk Burgh).
        if(isNearBarrows() || inTunnels || isInPlayerOwnedHouse()
                || (Rs2Player.getWorldLocation() != null && Rs2Player.getWorldLocation().getPlane() == 3)){
            if(!isAtFeroxEnclave()){
                if(tryFeroxTeleportViaRingOfDueling()){
                    sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                    sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                } else {
                    Microbot.log("Need a Ring of dueling to bank for gear from Barrows.");
                }
                return false;
            }
        }

        return loadStartingEquipment(inventorySetup);
    }

    /** Bank and equip until {@link Rs2InventorySetup#doesEquipmentMatch()} or attempts exhausted. */
    private boolean loadStartingEquipment(Rs2InventorySetup inventorySetup){
        if (inventorySetup == null) {
            return false;
        }
        if (inventorySetup.doesEquipmentMatch()) {
            return true;
        }

        int equipmentLoadAttempts = 0;
        final int maxEquipmentLoadAttempts = 8;
        while(!inventorySetup.doesEquipmentMatch() && equipmentLoadAttempts < maxEquipmentLoadAttempts) {
            if(!super.isRunning()){ return false; }
            BankLocation nearestBank = Rs2Bank.getNearestBank();
            if (nearestBank == null
                    || nearestBank.getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) > 6) {
                goToNearestBankForRestock();
            }
            nearestBank = Rs2Bank.getNearestBank();
            if (nearestBank != null
                    && nearestBank.getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) <= 6) {
                if(!Rs2Bank.isOpen()){
                    Rs2Bank.openBank();
                    sleepUntil(Rs2Bank::isOpen, Rs2Random.between(3000, 5000));
                }
                boolean loaded = inventorySetup.loadEquipment();
                if (!loaded && !inventorySetup.doesEquipmentMatch()) {
                    tryDirectEquipMissingGear(inventorySetup);
                }
                equipmentLoadAttempts++;
                if (Rs2Bank.isOpen() && !inventorySetup.doesEquipmentMatch()) {
                    sleep(Rs2Random.between(400, 800));
                }
            } else {
                equipmentLoadAttempts++;
                sleep(Rs2Random.between(400, 800));
            }
        }
        if (!inventorySetup.doesEquipmentMatch()) {
            Microbot.log("Inventory Setup equipment still mismatched after " + maxEquipmentLoadAttempts
                    + " bank attempts — will retry. Check the setup has every required item in the bank.");
            return false;
        }
        return true;
    }

    private boolean isNearBarrows(){
        WorldPoint here = Rs2Player.getWorldLocation();
        if(here == null){
            return false;
        }
        if(isInTunnelCoords() || here.getPlane() == 3){
            return true;
        }
        return here.distanceTo(new WorldPoint(3573, 3296, 0)) <= 60;
    }

    /** Inventory trip requirements only — used to avoid unnecessary bank walks. */
    private boolean hasTripSupplies(BarrowsConfig config){
        boolean wasBanking = shouldBank;
        suppliesCheck(config);
        boolean ok = !shouldBank;
        shouldBank = wasBanking;
        return ok;
    }

    public void checkForWorldMap(){
        if(Rs2Widget.getWidget(38993938) != null){
            if(Rs2Widget.getWidget(38993938).getText().contains("Key")){
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }
        }
    }

    public void closeBank(){
        if(Rs2Bank.isOpen()){
            while(Rs2Bank.isOpen()) {
                if(!super.isRunning()){break;}
                if (Rs2Bank.closeBank()) sleepUntil(() -> !Rs2Bank.isOpen(), Rs2Random.between(2000, 4000));
            }
        }
    }

    private boolean isPohTravelMode(BarrowsConfig config){
        return config.selectedToBarrowsTPMethod() == BarrowsConfig.selectedToBarrowsTPMethod.POH;
    }

    private boolean hasHouseTeleportTablet(){
        if(Rs2Inventory.hasItem("Teleport to house")){
            return true;
        }
        return Rs2Inventory.get(net.runelite.api.gameval.ItemID.POH_TABLET_TELEPORTTOHOUSE) != null;
    }

    /**
     * Single "can leave for POH" predicate used by suppliesCheck and teleToPoh callers.
     * Does not call {@link Rs2Magic#canCast} (Magic-tab switch). After a failed cast attempt,
     * {@link #requireHouseTabsToTravel} forces banking until a house tablet is present.
     */
    private boolean canTravelToPoh(){
        if(hasHouseTeleportTablet()){
            requireHouseTabsToTravel = false;
            return true;
        }
        if(requireHouseTabsToTravel){
            return false;
        }
        return canCastHouseTeleport();
    }

    /**
     * Non-UI house-teleport readiness check (no Magic-tab switch / sleep).
     * Counts inventory + rune pouch, including Dust as Air+Earth.
     */
    private boolean canCastHouseTeleport(){
        Rs2Spellbook spellbook = Rs2Magic.getSpellbook();
        if(spellbook == null || !spellbook.equals(Rs2Spellbook.MODERN)){
            return false;
        }
        if(Rs2Inventory.hasRunePouch()){
            Rs2RunePouch.fullUpdate();
        }
        if(Rs2Magic.hasRequiredRunes(Rs2Spells.TELEPORT_TO_HOUSE)){
            return true;
        }
        // Explicit fallback: hasRequiredRunes can miss combo dust / stale pouch state.
        return hasHouseTeleportRuneSupplies();
    }

    private boolean hasHouseTeleportRuneSupplies(){
        boolean hasLaw = hasRuneInInventoryOrPouch("Law rune", Runes.LAW);
        boolean hasAirEarth = hasRuneInInventoryOrPouch("Dust rune", Runes.DUST)
                || (hasRuneInInventoryOrPouch("Air rune", Runes.AIR)
                    && hasRuneInInventoryOrPouch("Earth rune", Runes.EARTH));
        return hasLaw && hasAirEarth;
    }

    private boolean hasRuneInInventoryOrPouch(String inventoryName, Runes pouchRune){
        if(Rs2Inventory.contains(inventoryName)){
            return true;
        }
        return Rs2Inventory.hasRunePouch() && Rs2RunePouch.contains(pouchRune);
    }

    private boolean inventoryHasEnoughHouseTabs(BarrowsConfig config){
        Rs2ItemModel byName = Rs2Inventory.get("Teleport to house");
        if(byName != null && byName.getQuantity() >= config.minBarrowsTeleports()){
            return true;
        }
        Rs2ItemModel byId = Rs2Inventory.get(net.runelite.api.gameval.ItemID.POH_TABLET_TELEPORTTOHOUSE);
        return byId != null && byId.getQuantity() >= config.minBarrowsTeleports();
    }

    /**
     * POH banking: withdraw pouch / dust+law before requiring tabs.
     * Tabs remain the fallback when cast supplies still cannot be made available.
     */
    private void ensurePohTravelSupplies(BarrowsConfig config){
        if(!canCastHouseTeleport() && !Rs2Inventory.hasRunePouch()){
            Microbot.log("Withdrawing rune pouch for Teleport to House.");
            if(Rs2Bank.withdrawRunePouch()){
                sleepUntil(Rs2Inventory::hasRunePouch, Rs2Random.between(2000, 4000));
                if(Rs2Inventory.hasRunePouch()){
                    Rs2RunePouch.fullUpdate();
                }
            }
        }

        if(!canCastHouseTeleport()){
            withdrawHouseTeleportRunesFromBank();
        }

        boolean hasTabs = inventoryHasEnoughHouseTabs(config);
        if(!canCastHouseTeleport() || !hasTabs){
            if(!hasTabs){
                int houseTabId = net.runelite.api.gameval.ItemID.POH_TABLET_TELEPORTTOHOUSE;
                if(Rs2Bank.getBankItem(houseTabId) != null
                        && Rs2Bank.getBankItem(houseTabId).getQuantity() >= config.targetBarrowsTeleports()){
                    if(Rs2Bank.withdrawX(houseTabId, Rs2Random.between(config.minBarrowsTeleports(), config.targetBarrowsTeleports()))){
                        sleep(Rs2Random.between(300,750));
                    }
                } else if(Rs2Bank.getBankItem("Teleport to house") != null
                        && Rs2Bank.getBankItem("Teleport to house").getQuantity() >= config.targetBarrowsTeleports()){
                    if(Rs2Bank.withdrawX("Teleport to house", Rs2Random.between(config.minBarrowsTeleports(), config.targetBarrowsTeleports()))){
                        sleep(Rs2Random.between(300,750));
                    }
                } else if(!canCastHouseTeleport()){
                    Microbot.log("Can't cast Teleport to House (need Law + Dust/Air+Earth in inv/pouch) and no house tabs available. stopping...");
                    super.shutdown();
                }
            }
        }
    }

    private void withdrawHouseTeleportRunesFromBank(){
        // Prefer Dust (Air+Earth combo); only pull separate Air/Earth if dust unavailable.
        if(!hasRuneInInventoryOrPouch("Dust rune", Runes.DUST)
                && !(hasRuneInInventoryOrPouch("Air rune", Runes.AIR)
                    && hasRuneInInventoryOrPouch("Earth rune", Runes.EARTH))){
            if(bankHasRune("Dust rune")){
                withdrawRuneStack("Dust rune");
            } else {
                if(!hasRuneInInventoryOrPouch("Air rune", Runes.AIR) && bankHasRune("Air rune")){
                    withdrawRuneStack("Air rune");
                }
                if(!hasRuneInInventoryOrPouch("Earth rune", Runes.EARTH) && bankHasRune("Earth rune")){
                    withdrawRuneStack("Earth rune");
                }
            }
        }
        if(!hasRuneInInventoryOrPouch("Law rune", Runes.LAW) && bankHasRune("Law rune")){
            withdrawRuneStack("Law rune");
        }
    }

    private boolean bankHasRune(String rune){
        return Rs2Bank.getBankItem(rune) != null && Rs2Bank.getBankItem(rune).getQuantity() >= 1;
    }

    private void withdrawRuneStack(String rune){
        int bankQty = Rs2Bank.getBankItem(rune).getQuantity();
        int withdraw = Math.min(bankQty, Rs2Random.between(10, 50));
        if(Rs2Bank.withdrawX(rune, withdraw)){
            sleepUntil(() -> Rs2Inventory.contains(rune), Rs2Random.between(1500, 3000));
        }
    }

    /** Prefer casting Teleport to House; fall back to a house tablet whenever cast cannot happen. */
    private boolean teleToPoh(){
        if(canCastHouseTeleport() && Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)){
            requireHouseTabsToTravel = false;
            Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
            return true;
        }
        // Fall back to tabs whenever the cast cannot happen.
        if(hasHouseTeleportTablet()){
            requireHouseTabsToTravel = false;
            if(Rs2Inventory.interact("Teleport to house", "Inside")){
                return true;
            }
            return Rs2Inventory.interact("Teleport to house", "Break");
        }
        // Runes present but canCast false (or cast skipped) and no tabs — stick bank request
        // so the next suppliesCheck cannot clear shouldBank and loop on the Magic tab.
        requireHouseTabsToTravel = true;
        return false;
    }

    public void handlePOH(BarrowsConfig config){
        if(!isPohTravelMode(config)){
            return;
        }
        Client client = Microbot.getClient();
        if(client == null){
            return;
        }
        WorldView worldView = client.getTopLevelWorldView();
        if(worldView == null){
            return;
        }
        if(!worldView.isInstance()){
            return;
        }
        Rs2TileObjectModel pohThing = rs2TileObjectCache.query().withId(4525).nearestOnClientThread();
        if(pohThing == null){
            return;
        }
        Microbot.log("We're in our POH");
        Rs2TileObjectModel rejPool = rs2TileObjectCache.query().withIds(29238,29239,29241,29240).nearestOnClientThread();
        if(rejPool != null){
            if(rejPool.click("Drink")){
                sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
            }
        }
        Rs2TileObjectModel regularPortal = rs2TileObjectCache.query().withIds(37603,37615,37591).nearestOnClientThread();
        if(regularPortal != null){
            for(int pohPortalAttempts = 0; pohPortalAttempts < 40; pohPortalAttempts++){
                if(!super.isRunning()){
                    break;
                }
                pohThing = rs2TileObjectCache.query().withId(4525).nearestOnClientThread();
                if(pohThing == null){
                    break;
                }
                regularPortal = rs2TileObjectCache.query().withIds(37603,37615,37591).nearestOnClientThread();
                if(regularPortal == null){
                    break;
                }
                if(Rs2Player.isMoving()){
                    sleep(Rs2Random.between(200, 600));
                    continue;
                }
                if(regularPortal.click("Enter")){
                    sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                    sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
                    sleepUntil(()-> rs2TileObjectCache.query().withIds(37603,37615,37591).nearestOnClientThread() == null, Rs2Random.between(10000,15000));
                } else {
                    break;
                }
            }
        } else {
            Microbot.log("No nexus support yet, shutting down");
            super.shutdown();
        }
    }

    public boolean everyBrotherWasKilled(){
        return countKilledBrothers() >= 6;
    }

    private int countKilledBrothers(){
        return Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM);
    }

    /**
     * Tunnel coffin entry only after the other five brothers are dead.
     * The tunnel brother has no kill varbit until killed in the tunnels, so ready == 5.
     */
    private boolean readyForTunnelEntry(){
        return countKilledBrothers() >= 5;
    }

    /**
     * True only in the barrows tunnels (plane 0, underground Y).
     * Brother crypts also use Y ~9600 but are plane 3 — those must not count as tunnels.
     */
    private boolean isInTunnelCoords(){
        WorldPoint loc = Rs2Player.getWorldLocation();
        if(loc == null){
            return false;
        }
        // Plane 3 = crypt mound. Plane 0 + underground Y = tunnels.
        if(loc.getPlane() != 0){
            return false;
        }
        int y = loc.getY();
        return y > 9600 && y < 9730;
    }

    /** Still standing in a brother crypt (empty coffin / tunnel entry room). */
    private boolean isInCryptMound(){
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && loc.getPlane() == 3;
    }

    /** @deprecated use {@link #enterTunnelsFromDialogue()} */
    public void dialogueEnterTunnels(){
        enterTunnelsFromDialogue();
    }

    /**
     * Complete the empty-coffin dialogue into the crypt tunnels.
     * Only sets {@code inTunnels} after we are actually on tunnel coordinates.
     */
    public boolean enterTunnelsFromDialogue(){
        if(!Rs2Dialogue.isInDialogue() && !isInTunnelCoords()){
            return false;
        }
        if(isInTunnelCoords()){
            inTunnels = true;
            disableProtectPrayers();
            // Chest pathing is owned by the tunnels tick — do not start a nested walk here
            // (causes "concurrent walk request" when the main loop also calls ensureChestWalk).
            return true;
        }

        long deadline = System.currentTimeMillis() + Rs2Random.between(12000, 18000);
        while(System.currentTimeMillis() < deadline){
            if(!super.isRunning()){
                return false;
            }
            if(isInTunnelCoords()){
                inTunnels = true;
                disableProtectPrayers();
                return true;
            }

            if(Rs2Dialogue.hasContinue()){
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")
                                || !Rs2Dialogue.isInDialogue()
                                || isInTunnelCoords(),
                        Rs2Random.between(2000, 4000));
                sleep(300, 600);
                continue;
            }

            if(Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")){
                if(Rs2Dialogue.clickOption("Yeah I'm fearless!")){
                    sleepUntil(this::isInTunnelCoords, Rs2Random.between(4000, 8000));
                    if(isInTunnelCoords()){
                        sleep(1000, 2000);
                        inTunnels = true;
                        disableProtectPrayers();
                        return true;
                    }
                }
                sleep(200, 400);
                continue;
            }

            // Dialogue can briefly disappear between Continue and the fearless option.
            if(!Rs2Dialogue.isInDialogue()){
                sleepUntil(() -> Rs2Dialogue.isInDialogue() || isInTunnelCoords(),
                        Rs2Random.between(800, 1500));
                if(!Rs2Dialogue.isInDialogue() && !isInTunnelCoords()){
                    return false;
                }
                continue;
            }

            sleep(200, 400);
        }
        return isInTunnelCoords();
    }

    public void digIntoTheMound(Rs2WorldArea moundArea){
        digIntoTheMound(moundArea, true);
    }

    public void digIntoTheMound(Rs2WorldArea moundArea, boolean prepareForFight){
        while (moundArea.contains(Rs2Player.getWorldLocation()) && Rs2Player.getWorldLocation().getPlane() != 3) {
            checkForWorldMap();

            if (!super.isRunning()) break;

            // Only antipattern-pray when we expect a brother fight (not empty-coffin tunnel entry).
            if(prepareForFight){
                antiPatternEnableWrongPrayer();
                antiPatternActivatePrayer();
            }

            if (Rs2Inventory.contains("Spade")) {
                if (Rs2Inventory.interact("Spade", "Dig")) {
                    sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 3, Rs2Random.between(3000, 5000));
                }
            }

            if (Rs2Player.getWorldLocation().getPlane() == 3) break;
        }
    }

    public void goToTheMound(Rs2WorldArea moundArea){
        while (!moundArea.contains(Rs2Player.getWorldLocation())) {
            checkForWorldMap();
            int totalTiles = moundArea.toWorldPointList().size();
            WorldPoint randomMoundTile;
            if (!super.isRunning()) break;

            antiPatternEnableWrongPrayer();
            antiPatternActivatePrayer();
            antiPatternDropVials();

            randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));

            Rs2Walker.walkTo(randomMoundTile, 0);
            sleepUntil(() -> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));

            if (moundArea.contains(Rs2Player.getWorldLocation())) {
                if(!Rs2Player.isMoving()) break;
            } else {
                Microbot.log("At the mound, but we can't dig yet.");
                randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));

                Rs2NpcModel strangeOldMan = rs2NpcCache.query().withName("Strange Old Man").nearestOnClientThread();

                if(strangeOldMan !=null){
                    if(strangeOldMan.getWorldLocation() != null){
                        if(strangeOldMan.getWorldLocation().equals(randomMoundTile)){
                            while(strangeOldMan.getWorldLocation().equals(randomMoundTile)){
                                if(!super.isRunning()){break;}
                                randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                sleep(250,500);
                            }
                        }
                    }
                }

                Rs2Walker.walkCanvas(randomMoundTile);
                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
            }
        }
    }

    public void leaveTheMound(){
        if(Rs2Player.getWorldLocation().getPlane() != 3){
            return;
        }
        // Never climb out while our hinted brother is still alive.
        Rs2NpcModel livingBrother = findTunnelBrother();
        if(livingBrother != null && !livingBrother.isDead()){
            Microbot.log("Refusing to leave mound — " + livingBrother.getName() + " is still alive.");
            return;
        }
        Rs2TileObjectModel stairs = rs2TileObjectCache.query().withIds(20668,20669,20670,20671,20672,20667).nearest();
        if(stairs != null) {
            if (Rs2Walker.canReach(stairs.getWorldLocation())) {
                while (Rs2Player.getWorldLocation().getPlane() == 3) {
                    Microbot.log("Leaving the mound");
                    if (!super.isRunning()) break;

                    livingBrother = findTunnelBrother();
                    if(livingBrother != null && !livingBrother.isDead()){
                        Microbot.log("Brother reappeared — aborting leave.");
                        return;
                    }

                    if (stairs.click("Climb-up")) {
                        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != 3, Rs2Random.between(3000, 6000));
                    }

                    if (Rs2Player.getWorldLocation().getPlane() != 3) {
                        disablePrayer();
                        break;
                    }
                }
            }
        }
    }

    /** Count of each Barrows equipment piece currently in inventory (by name). */
    private Map<String, Integer> snapshotBarrowsPieceCounts(){
        Map<String, Integer> counts = new HashMap<>();
        List<Rs2ItemModel> items = Rs2Inventory.all(it -> it != null && isBarrowsEquipmentName(it.getName()));
        if(items == null){
            return counts;
        }
        for(Rs2ItemModel item : items){
            String name = item.getName();
            if(name == null){
                continue;
            }
            counts.merge(name, Math.max(1, item.getQuantity()), Integer::sum);
        }
        return counts;
    }

    /**
     * Record newly gained Barrows pieces for the overlay. Pass the inventory snapshot from
     * before loot; pass an empty map to record every piece currently in inventory (bank backup).
     */
    private void recordNewBarrowsPieces(Map<String, Integer> before){
        Map<String, Integer> after = snapshotBarrowsPieceCounts();
        if(after.isEmpty()){
            return;
        }
        Map<String, Integer> baseline = before != null ? before : new HashMap<>();
        boolean recorded = false;
        for(Map.Entry<String, Integer> entry : after.entrySet()){
            int gained = entry.getValue() - baseline.getOrDefault(entry.getKey(), 0);
            // Bank backup with empty baseline: only add names not already listed (avoid spam).
            if(baseline.isEmpty()){
                if(!barrowsPieces.contains(entry.getKey())){
                    barrowsPieces.add(entry.getKey());
                    recorded = true;
                    Microbot.log("Barrows piece recorded: " + entry.getKey());
                }
                continue;
            }
            for(int i = 0; i < gained; i++){
                barrowsPieces.add(entry.getKey());
                recorded = true;
                Microbot.log("Barrows piece recorded: " + entry.getKey());
            }
        }
        if(recorded){
            barrowsPieces.remove("Nothing yet.");
        }
    }

    private boolean isBarrowsEquipmentName(String name){
        if(name == null || name.isEmpty()){
            return false;
        }
        String n = name.toLowerCase();
        // Brother gear only — not amulets/teleports/etc.
        return n.contains("ahrim's")
                || n.contains("dharok's")
                || n.contains("guthan's")
                || n.contains("karil's")
                || n.contains("torag's")
                || n.contains("verac's");
    }

    public void lootChampionScroll(){
        Rs2TileItemModel championScroll = rs2TileItemCache.query().withId(ItemID.SKELETON_CHAMPION_SCROLL).nearest();
        if(championScroll != null){
            if(championScroll.isReachable() && championScroll.isLootAble()){
                while(rs2TileItemCache.query().withId(ItemID.SKELETON_CHAMPION_SCROLL).nearest() != null && !Rs2Inventory.contains(championScroll.getId())){
                    if(!super.isRunning()) break;
                    championScroll.click("Take");
                    sleepUntil(()-> !Rs2Player.isMoving() && Rs2Inventory.contains(championScroll.getId()), Rs2Random.between(4000,12000));
                }
            }
        }
    }

    public void gainRP(BarrowsConfig config){
        if(!needsMoreRewardPotential()){
            return;
        }
        fightTunnelMonster(config);
    }

    /**
     * Player world point in the same coordinate space as NPCs/objects (scene/instance).
     * Never fall back to {@link Rs2Player#getWorldLocation()} here — that is template/overworld
     * space in instances, so same-room NPCs look impossibly far and we walk past them until
     * a plugin restart clears whatever made the scene read fail (sticky interrupt).
     */
    private WorldPoint getScenePlayerLocation(){
        try {
            if(Microbot.getClientThread().isClientThread()){
                Player p = Microbot.getClient().getLocalPlayer();
                return p != null ? p.getWorldLocation() : null;
            }
        } catch (Exception ignored) {
            // fall through to invoke
        }
        Thread.interrupted();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player p = Microbot.getClient().getLocalPlayer();
            return p != null ? p.getWorldLocation() : null;
        }).orElse(null);
    }

    private int distancePlayerToNpc(Rs2NpcModel npc){
        if(npc == null){
            return Integer.MAX_VALUE;
        }
        WorldPoint here = getScenePlayerLocation();
        WorldPoint there = npc.getWorldLocation();
        if(here == null || there == null){
            return Integer.MAX_VALUE;
        }
        return here.distanceTo(there);
    }

    /** Fight nearest same-room tunnel monster (counts toward the per-room kill cap). */
    private void fightTunnelMonster(BarrowsConfig config){
        if(findTunnelBrother() != null){
            return;
        }
        if(!clearTunnelTrashAggressor(config)){
            return;
        }
        if(findTunnelBrother() != null){
            return;
        }

        Rs2NpcModel monster = findTunnelMonster();
        if(monster == null){
            return;
        }

        stopFutureWalker();
        int rpTarget = getRpTargetForEightySix();
        String label = monster.getName() != null ? monster.getName() : "monster";
        boolean killed = false;

        if(!isInteractingWith(monster)){
            if(!tryAttackNpc(monster)){
                Microbot.log(label + " gone or Attack unavailable — continuing.");
                return;
            }
            sleepUntil(() -> isInteractingWith(monster) && !Rs2Player.isMoving()
                            || monster.isDead()
                            || !npcHasAttackOption(monster),
                    Rs2Random.between(4000,8000));
            if(monster.isDead() || !npcHasAttackOption(monster)){
                if(monster.isDead()){
                    monstersKilledThisRoom++;
                    Microbot.log("Room kills: " + monstersKilledThisRoom + "/" + MAX_MONSTERS_PER_ROOM);
                }
                return;
            }
        }

        if(Rs2Player.isInCombat() || isInteractingWith(monster)){
            while(Rs2Player.isInCombat() || isInteractingWith(monster)){
                Microbot.log("Fighting " + label + ".");
                if (!super.isRunning()) break;

                if(findTunnelBrother() != null){
                    break;
                }

                if(isDoorPuzzleOpenRaw()){
                    break;
                }

                if(findTunnelTrashAggressor() != null){
                    if(!clearTunnelTrashAggressor(config)){
                        break;
                    }
                    continue;
                }

                if(monster != null && !monster.isDead() && !isInteractingWith(monster)){
                    if(!tryAttackNpc(monster)){
                        Microbot.log(label + " gone or Attack unavailable — continuing.");
                        break;
                    }
                }

                sleep(750,1500);
                eatFood();
                outOfSupplies(config);
                antiPatternDropVials();

                if(shouldBank){
                    break;
                }

                if(!Rs2Player.isInCombat() && !isInteractingWith(monster)){
                    break;
                }

                if (monster != null && (monster.isDead() || !npcHasAttackOption(monster))) {
                    killed = monster.isDead();
                    break;
                }

                if(Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL) >= rpTarget){
                    shouldAttackSkeleton = false;
                    Microbot.log("RP target reached (" + rpTarget + ") — stopping monster fights.");
                    break;
                }
            }
        }

        if(killed || (monster != null && monster.isDead())){
            monstersKilledThisRoom++;
            Microbot.log("Room kills: " + monstersKilledThisRoom + "/" + MAX_MONSTERS_PER_ROOM);
        }
    }

    /** True when we still need RP and can take another same-room kill this room. */
    private boolean shouldFightMonsterOnWayToChest(){
        if(!needsMoreRewardPotential()){
            return false;
        }
        if(monstersKilledThisRoom >= MAX_MONSTERS_PER_ROOM){
            return false;
        }
        return findTunnelMonster() != null;
    }

    /**
     * Live RP check for ~86% (870/1012, leaving room for the tunnel brother when alive).
     * Clears {@link #shouldAttackSkeleton} as soon as the varbit hits the target.
     */
    private boolean needsMoreRewardPotential(){
        if(isAtOrAboveRpTarget()){
            shouldAttackSkeleton = false;
            return false;
        }
        return shouldAttackSkeleton;
    }

    private boolean isAtOrAboveRpTarget(){
        return Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL) >= getRpTargetForEightySix();
    }

    /** Kill-cap resets at puzzle doors — not by a static "room size" distance. */
    private void updateTunnelRoomTracking(){
        // no-op: hallways pass near side rooms, so distance cannot define a room.
    }

    private void resetTunnelRoomKills(){
        monstersKilledThisRoom = 0;
    }

    /**
     * Nearest RP monster we are walking past on the chest hallway (or already fighting nearby).
     * Never chase fodder two rooms away — hallway polyline + short look-ahead + scene range.
     * <p>
     * Cache query stays on the client thread; route math stays on the script thread.
     */
    private Rs2NpcModel findTunnelMonster(){
        Thread.interrupted();
        final WorldPoint here = Rs2Player.getWorldLocation();
        if(here == null){
            return null;
        }
        List<Rs2NpcModel> candidates = rs2NpcCache.query()
                .where(npc -> npc != null && !npc.isDead() && isTunnelRpMonsterName(npc.getName()))
                .toListOnClientThread();
        if(candidates == null || candidates.isEmpty()){
            return null;
        }

        List<WorldPoint> chestPath = resolveChestRoutePath(here);
        Rs2NpcModel best = null;
        int bestDist = Integer.MAX_VALUE;
        for(Rs2NpcModel npc : candidates){
            int sceneDist = distancePlayerToNpc(npc);
            if(sceneDist > EN_ROUTE_MAX_SCENE_DISTANCE){
                continue;
            }
            boolean accept = isNpcEngagedWithUs(npc)
                    || isNpcOnChestHallway(npc, here, chestPath);
            if(!accept){
                continue;
            }
            if(sceneDist < bestDist){
                bestDist = sceneDist;
                best = npc;
            }
        }
        return best;
    }

    private boolean isNpcEngagedWithUs(Rs2NpcModel npc){
        if(npc == null){
            return false;
        }
        if(isInteractingWith(npc)){
            return true;
        }
        Player local = getLocalPlayerSafe();
        return local != null && Objects.equals(npc.getInteracting(), local);
    }

    /**
     * Prefer the active chest walker's path; otherwise plan player→chest once.
     * Never plan npc→chest here (collision from arbitrary starts is unreliable off-thread).
     */
    private List<WorldPoint> resolveChestRoutePath(WorldPoint here){
        if(here == null){
            return Collections.emptyList();
        }
        try {
            Optional<Rs2RouteResult> active = Rs2PathApi.getActiveRoute();
            if(active.isPresent()){
                Rs2RouteResult route = active.get();
                List<WorldPoint> path = route.getPath();
                if(path != null && path.size() >= 2){
                    WorldPoint end = path.get(path.size() - 1);
                    boolean towardChest = (end != null && end.distanceTo(Chest) <= 8)
                            || (route.getTargets() != null && route.getTargets().stream()
                            .anyMatch(t -> t != null && t.distanceTo(Chest) <= 8));
                    if(towardChest){
                        return path;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to plan
        }
        try {
            List<WorldPoint> planned = Rs2Walker.getWalkPath(here, Chest);
            if(planned != null && planned.size() >= 2){
                return planned;
            }
        } catch (Exception ignored) {
            // empty
        }
        return Collections.emptyList();
    }

    /**
     * True when the NPC sits on/near the chest hallway just ahead of us.
     * No polyline → do not guess (triangle fallback chased distant/side-room rats).
     */
    private boolean isNpcOnChestHallway(Rs2NpcModel npc, WorldPoint here, List<WorldPoint> chestPath){
        WorldPoint npcWp = npcWorldPointForPathCompare(npc);
        if(npcWp == null || here == null || chestPath == null || chestPath.size() < 2){
            return false;
        }
        int npcIdx = nearestPathIndex(chestPath, npcWp);
        if(npcIdx < 0){
            return false;
        }
        int npcOffPath = npcWp.distanceTo(chestPath.get(npcIdx));
        if(npcOffPath > EN_ROUTE_PATH_PROXIMITY_TILES){
            return false;
        }
        int playerIdx = nearestPathIndex(chestPath, here);
        if(playerIdx < 0){
            return false;
        }
        // Just ahead (or one step behind while we walk past) — not two chambers down the path.
        return npcIdx >= playerIdx - 1
                && npcIdx <= playerIdx + EN_ROUTE_MAX_PATH_STEPS_AHEAD;
    }

    private int nearestPathIndex(List<WorldPoint> path, WorldPoint point){
        if(path == null || point == null){
            return -1;
        }
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for(int i = 0; i < path.size(); i++){
            WorldPoint step = path.get(i);
            if(step == null){
                continue;
            }
            int d = point.distanceTo(step);
            if(d < bestDist){
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * True when fighting this NPC does not pull us off the chest route:
     * tiles(player→npc) + tiles(npc→chest) <= tiles(player→chest) + slack,
     * and the NPC is strictly closer (in path tiles) than the chest so it is ahead
     * on the route rather than behind us / in a side spur.
     */
    private boolean isNpcOnWayToChest(Rs2NpcModel npc, WorldPoint here, int directToChest){
        WorldPoint npcWp = npcWorldPointForPathCompare(npc);
        if(npcWp == null || here == null || directToChest <= 0){
            return false;
        }
        int toNpc = safeTotalTiles(here, npcWp);
        if(toNpc == Integer.MAX_VALUE || toNpc <= 0 || toNpc >= directToChest){
            return false;
        }
        int onward = safeTotalTiles(npcWp, Chest);
        if(onward == Integer.MAX_VALUE){
            return false;
        }
        return toNpc + onward <= directToChest + EN_ROUTE_PATH_SLACK_TILES;
    }

    private int safeTotalTiles(WorldPoint from, WorldPoint to){
        if(from == null || to == null){
            return Integer.MAX_VALUE;
        }
        try {
            int tiles = Rs2Walker.getTotalTiles(from, to);
            if(tiles < 0 || tiles == Integer.MAX_VALUE){
                return Integer.MAX_VALUE;
            }
            return tiles;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Project NPC location into the same space as walker pathfinding (player /
     * template coords). Scene vs template mismatch is corrected via player offset.
     */
    private WorldPoint npcWorldPointForPathCompare(Rs2NpcModel npc){
        WorldPoint npcLoc = npc.getWorldLocation();
        WorldPoint playerScene = getScenePlayerLocation();
        WorldPoint playerWp = Rs2Player.getWorldLocation();
        if(npcLoc == null || playerWp == null){
            return npcLoc;
        }
        if(playerScene == null || playerScene.distanceTo(playerWp) <= 64){
            return npcLoc;
        }
        return new WorldPoint(
                playerWp.getX() + (npcLoc.getX() - playerScene.getX()),
                playerWp.getY() + (npcLoc.getY() - playerScene.getY()),
                playerWp.getPlane());
    }

    private Player getLocalPlayerSafe(){
        try {
            if(Microbot.getClientThread().isClientThread()){
                return Microbot.getClient().getLocalPlayer();
            }
        } catch (Exception ignored) {
            // fall through
        }
        Thread.interrupted();
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getLocalPlayer()
        ).orElse(null);
    }

    private boolean isTunnelRpMonsterName(String name){
        if(name == null){
            return false;
        }
        if(isBarrowsBrotherName(name)){
            return false;
        }
        return name.equals("Skeleton")
                || name.contains("Bloodworm")
                || name.contains("Crypt rat")
                || name.contains("Crypt spider")
                || name.contains("Giant crypt");
    }

    /** @deprecated use {@link #findTunnelMonster()} */
    private Rs2NpcModel findFightableSkeleton(){
        return findTunnelMonster();
    }

    private Rs2NpcModel findReachableSkeleton(){
        return findTunnelMonster();
    }

    /**
     * Target RP for ~86% chest. If the tunnel brother is still alive, leave room for his
     * combat-level RP; once all six are dead, require the full 870.
     */
    private int getRpTargetForEightySix(){
        if(countKilledBrothers() >= 6){
            return 870;
        }
        return Math.max(0, 870 - getTunnelBrotherRewardPotential());
    }

    private int getRpTargetBeforeTunnelBrother(){
        return getRpTargetForEightySix();
    }

    private int getTunnelBrotherRewardPotential(){
        if(WhoisTun == null || WhoisTun.equals("Unknown")){
            return 98;
        }
        for(BarrowsBrothers brother : BarrowsBrothers.values()){
            if(brother.name.equals(WhoisTun) || WhoisTun.contains(brother.name.split(" ")[0])){
                return brother.getCombatLevel();
            }
        }
        return 98;
    }

    /**
     * Door-spawned crypt trash targeting the player. Holds attack priority until dead.
     * Once RP is at the 86% target, ignore RP fodder so we do not keep farming past it.
     */
    private Rs2NpcModel findTunnelTrashAggressor(){
        // Past RP target: never pick fights with crypt fodder (even if they attack us).
        if(isAtOrAboveRpTarget()){
            shouldAttackSkeleton = false;
            return null;
        }
        Player localPlayer = null;
        try {
            if(Microbot.getClientThread().isClientThread()){
                localPlayer = Microbot.getClient().getLocalPlayer();
            }
        } catch (Exception ignored) {
            // fall through
        }
        if(localPlayer == null){
            Thread.interrupted();
            localPlayer = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getLocalPlayer()
            ).orElse(null);
        }
        if(localPlayer == null){
            return null;
        }
        final Player local = localPlayer;
        Rs2NpcModel trash = rs2NpcCache.query()
                .where(npc -> npc != null && !npc.isDead() && npc.getCombatLevel() > 0)
                .where(npc -> {
                    String name = npc.getName();
                    if(name == null) return false;
                    if("Skeleton".equals(name)) return false;
                    if(isBarrowsBrotherName(name)) return false;
                    return Objects.equals(npc.getInteracting(), local);
                })
                .nearestOnClientThread();
        // Ignore "attacking us" from another chamber — do not path across rooms for trash.
        if(trash != null && distancePlayerToNpc(trash) > EN_ROUTE_MAX_SCENE_DISTANCE){
            return null;
        }
        return trash;
    }

    private boolean isBarrowsBrotherName(String name){
        if(name == null){
            return false;
        }
        return name.contains("Dharok") || name.contains("Guthan") || name.contains("Karil")
                || name.contains("Torag") || name.contains("Verac") || name.contains("Ahrim");
    }

    /**
     * Kill door-spawn trash when it has combat priority so skeleton/brother attacks can succeed.
     * @return false if we should abort the current combat goal (supplies / shutdown)
     */
    private boolean clearTunnelTrashAggressor(BarrowsConfig config){
        Rs2NpcModel trash = findTunnelTrashAggressor();
        if(trash == null){
            return true;
        }

        Microbot.log("Tunnel aggressor " + trash.getName() + " has combat priority; clearing it.");
        stopFutureWalker();

        long deadline = System.currentTimeMillis() + Rs2Random.between(45000, 75000);
        while(trash != null && !trash.isDead() && System.currentTimeMillis() < deadline){
            if(!super.isRunning()){
                return false;
            }
            final Rs2NpcModel target = trash;
            if(!isInteractingWith(target)){
                if(!tryAttackNpc(target)){
                    Microbot.log("Aggressor gone or Attack unavailable — continuing.");
                    break;
                }
                sleepUntil(() -> isInteractingWith(target) || target.isDead()
                                || !npcHasAttackOption(target),
                        Rs2Random.between(2000, 4000));
            }
            if(target.isDead() || !npcHasAttackOption(target)){
                break;
            }
            sleep(500, 1000);
            eatFood();
            outOfSupplies(config);
            if(shouldBank){
                return false;
            }
            trash = findTunnelTrashAggressor();
        }
        return findTunnelTrashAggressor() == null;
    }

    /** Pause chest pathing for open puzzle, brother, trash, or a same-room RP kill under the cap. */
    private boolean shouldDeferChestWalk(){
        Thread.interrupted();
        return isDoorPuzzleOpenRaw() || findTunnelBrother() != null
                || findTunnelTrashAggressor() != null || shouldFightMonsterOnWayToChest();
    }

    // Same widgets RuneLite's Barrows plugin uses (InterfaceID.BarrowsPuzzle).
    private static final int[] PUZZLE_OPTION_WIDGETS = {1638413, 1638415, 1638417}; // PIC_A/B/C
    private static final int PUZZLE_SEQUENCE_WIDGET = 1638403; // _1 — answer = modelId - 3

    private boolean isDoorPuzzleOpen(){
        // Clear stale interrupts — they make client-thread widget reads return empty forever
        // until the plugin is toggled (matches "works after restart").
        Thread.interrupted();
        boolean open = isDoorPuzzleOpenRaw();
        if(open){
            waitingOnPuzzle = true;
            return true;
        }
        // Stick until solvePuzzleUntilClosed confirms closed and clears the flag.
        // Do not clear on a single false read — that was the door-spam loop.
        return waitingOnPuzzle;
    }

    /**
     * Stop walker and click the correct puzzle answer.
     * @return false while puzzle still open (caller must not walk)
     */
    private boolean solvePuzzleUntilClosed(){
        Thread.interrupted();
        // Only poll briefly for a late-opening puzzle when we already know one is up
        // (or sticky). Do NOT sleep 600–1200ms every tunnels tick — that re-arms interrupt
        // and is what makes puzzle/skeleton detection die until plugin restart.
        if(!isDoorPuzzleOpen()){
            return true;
        }
        if(!isDoorPuzzleOpenRaw()){
            // Sticky flag with no widgets — clear and continue.
            waitingOnPuzzle = false;
            return true;
        }

        waitingOnPuzzle = true;
        stopFutureWalker();
        Microbot.log("Door puzzle open — solving (walker frozen).");

        long deadline = System.currentTimeMillis() + 12000;
        while(isDoorPuzzleOpenRaw() && System.currentTimeMillis() < deadline && super.isRunning()){
            Thread.interrupted();
            stopFutureWalker();

            Integer answerWidgetId = findPuzzleAnswerWidgetId();
            if(answerWidgetId == null){
                Microbot.log("Puzzle models not matched yet — waiting.");
                sleep(250, 450);
                continue;
            }

            Microbot.log("Puzzle solution widget " + answerWidgetId);
            boolean clicked = Rs2Widget.clickWidget(answerWidgetId);
            if(!clicked){
                final int clickId = answerWidgetId;
                Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    Widget w = Microbot.getClient().getWidget(clickId);
                    if(w != null && !w.isHidden() && w.getBounds() != null){
                        Microbot.getMouse().click(w.getBounds());
                    }
                    return true;
                });
            }
            sleepUntil(() -> {
                Thread.interrupted();
                return !isDoorPuzzleOpenRaw();
            }, Rs2Random.between(1000, 1800));
            Thread.interrupted();
        }

        Thread.interrupted();
        if(isDoorPuzzleOpenRaw()){
            Microbot.log("Puzzle still open — holding (will not click doors).");
            stopFutureWalker();
            waitingOnPuzzle = true;
            return false;
        }
        waitingOnPuzzle = false;
        Microbot.log("Puzzle solved.");
        return true;
    }

    private boolean isDoorPuzzleOpenRaw(){
        Thread.interrupted();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for(int id : PUZZLE_OPTION_WIDGETS){
                Widget w = Microbot.getClient().getWidget(id);
                if(w != null && !w.isHidden()){
                    return true;
                }
            }
            Widget root = Microbot.getClient().getWidget(25, 0);
            return root != null && !root.isHidden();
        }).orElse(false);
    }

    /**
     * Match RuneLite: answer model = sequence(_1).modelId - 3, then click PIC_A/B/C with that model.
     * Do not fall back to "any known puzzle model" — all three options are known shapes, so that
     * clicked the wrong door almost every time.
     */
    private Integer findPuzzleAnswerWidgetId(){
        Thread.interrupted();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget seq = Microbot.getClient().getWidget(PUZZLE_SEQUENCE_WIDGET);
            if(seq == null || seq.getModelId() <= 0){
                Microbot.log("Puzzle sequence widget missing or has no model.");
                return null;
            }
            int answerModel = seq.getModelId() - 3;

            StringBuilder dbg = new StringBuilder("Puzzle seq=")
                    .append(seq.getModelId())
                    .append(" answer=")
                    .append(answerModel);
            for(int widgetId : PUZZLE_OPTION_WIDGETS){
                Widget w = Microbot.getClient().getWidget(widgetId);
                if(w == null){
                    dbg.append(" ").append(widgetId).append("=null");
                    continue;
                }
                int model = w.getModelId();
                dbg.append(" ").append(widgetId).append("=").append(model);
                if(model == answerModel){
                    return widgetId;
                }
            }
            Microbot.log(dbg.toString());
            return null;
        }).orElse(null);
    }

    public void solvePuzzle(){
        solvePuzzleUntilClosed();
    }

    /**
     * Our living tunnel/crypt brother.
     * Ownership is the yellow hint arrow — other players' brothers spawn in the same
     * tunnels without an arrow and must never be chased.
     */
    private Rs2NpcModel findTunnelBrother(){
        Rs2NpcModel candidate = findTunnelBrotherCandidate();
        if(candidate == null){
            return null;
        }
        if(inTunnels && !isTunnelBrotherEngageable(candidate)){
            return null;
        }
        return candidate;
    }

    private Rs2NpcModel findTunnelBrotherCandidate(){
        Rs2NpcModel hinted = hintNpcModel();
        if(hinted != null && !hinted.isDead() && isBarrowsBrotherName(hinted.getName())){
            return hinted;
        }

        // Finish a fight we already started if the arrow briefly clears mid-combat.
        Actor interacting = Rs2Player.getInteracting();
        if(interacting instanceof NPC){
            int index = ((NPC) interacting).getIndex();
            Rs2NpcModel current = rs2NpcCache.query()
                    .where(npc -> npc != null && npc.getIndex() == index)
                    .nearestOnClientThread();
            if(current != null && !current.isDead() && isBarrowsBrotherName(current.getName())){
                return current;
            }
        }

        // Defend only — never path to a brother that is not arrowed / not fighting us.
        // Nearby name matches and "on chest route" picks are other players' brothers.
        final Player local = getLocalPlayerSafe();
        if(local == null){
            return null;
        }
        Rs2NpcModel attackingUs = rs2NpcCache.query()
                .where(npc -> npc != null
                        && !npc.isDead()
                        && isBarrowsBrotherName(npc.getName())
                        && Objects.equals(npc.getInteracting(), local))
                .nearestOnClientThread();
        if(attackingUs == null){
            return null;
        }
        // In tunnels, if we already know which tomb was empty, ignore other names that
        // happen to be hitting us (multi-combat / wrong target).
        if(inTunnels && WhoisTun != null && !WhoisTun.equals("Unknown")
                && !matchesWhoisTun(attackingUs.getName())){
            return null;
        }
        return attackingUs;
    }

    /**
     * Hint-arrow brother is always ours. Otherwise only continue a fight already linked
     * to us — never chase a visible/LOS brother without an arrow.
     */
    private boolean isTunnelBrotherEngageable(Rs2NpcModel brother){
        if(brother == null || brother.isDead()){
            return false;
        }
        Rs2NpcModel hinted = hintNpcModel();
        if(hinted != null && hinted.getIndex() == brother.getIndex()){
            return true;
        }
        if(isInteractingWith(brother)){
            return true;
        }
        Player local = getLocalPlayerSafe();
        return local != null && Objects.equals(brother.getInteracting(), local);
    }

    private boolean matchesWhoisTun(String npcName){
        if(npcName == null || WhoisTun == null || WhoisTun.equals("Unknown")){
            return false;
        }
        if(npcName.equals(WhoisTun) || WhoisTun.equals(npcName)){
            return true;
        }
        String shortName = WhoisTun.contains(" ") ? WhoisTun.split(" ")[0] : WhoisTun;
        return npcName.contains(shortName);
    }

    private boolean isFightingBarrowsBrother(){
        Rs2NpcModel brother = findTunnelBrother();
        if(brother == null || brother.getName() == null){
            return false;
        }
        return isInteractingWith(brother);
    }

    private Rs2PrayerEnum prayerForBrother(Rs2NpcModel brother){
        if(brother == null || brother.getName() == null){
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        String name = brother.getName();
        for(BarrowsBrothers b : BarrowsBrothers.values()){
            if(name.contains(b.name.split(" ")[0])){
                return b.getWhatToPray();
            }
        }
        if(name.contains("Ahrim")) return Rs2PrayerEnum.PROTECT_MAGIC;
        if(name.contains("Karil")) return Rs2PrayerEnum.PROTECT_RANGE;
        return Rs2PrayerEnum.PROTECT_MELEE;
    }

    public void suppliesCheck(BarrowsConfig config){
        if(!usingPoweredStaffs) {
            // Prefer highest castable tier already stocked above min (Wrath→Blood→Death).
            if (switchToInventoryRuneTier(minRuneAmt)) {
                // neededRune updated from inventory stock
            } else if (!inventoryHasEnoughRunes(neededRune, minRuneAmt)) {
                Microbot.log("We have less than " + minRuneAmt + " " + neededRune);
                shouldBank = true;
                return;
            }
        }

        if(usingPoweredStaffs){
            if(outOfPoweredStaffCharges){
                Microbot.log("We're out of staff charges.");
                shouldBank = true;
                return;
            }
        }

        if (!hasDuelingRingAvailable()) {
            Microbot.log("We don't have a Ring of dueling (inventory or equipped).");
            shouldBank = true;
            return;
        }
        if (!Rs2Inventory.contains("Spade")) {
            Microbot.log("We don't have a spade.");
            shouldBank = true;
            return;
        }

        int foodCount = Rs2Inventory.count(config.food().getName());
        if (foodCount < config.minFood()) {
            Microbot.log("We have " + foodCount + " food (min " + config.minFood() + ").");
            shouldBank = true;
            return;
        }

        if(isPohTravelMode(config)){
            if(!canTravelToPoh()){
                Microbot.log(requireHouseTabsToTravel
                        ? "Need a house tablet to travel to POH (cast unavailable)."
                        : "Can't cast Teleport to House and no house tablet.");
                shouldBank = true;
                return;
            }
        } else if (Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null) {
            Microbot.log("We don't have a "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName());
            shouldBank = true;
            return;
        }

        int brewCount = Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew("));
        if (brewCount < minForgottenBrews) {
            Microbot.log("Forgotten brew " + brewCount + " (min " + minForgottenBrews + ").");
            shouldBank = true;
            return;
        }

        String name = config.prayerRestoreType().getPrayerRestoreTypeName();
        if(name.contains("(")) name = config.prayerRestoreType().getPrayerRestoreTypeName().split("\\(")[0];
        String splitName = name;
        int prayerCount = Rs2Inventory.count(it->it!=null&&it.getName().toLowerCase().contains(splitName.toLowerCase()));
        if (prayerCount < config.minPrayerPots()) {
            Microbot.log("We have " + prayerCount + " " + splitName + " (min " + config.minPrayerPots() + ").");
            shouldBank = true;
            return;
        }

        shouldBank = false;
    }

    public void stuckInTunsCheck(){
        //needed for rare occasions where the walker messes up
        if(tunnelLoopCount < 1){
            FirstLoopTile = Rs2Player.getWorldLocation();
        }
        if(tunnelLoopCount >= 15){
            WorldPoint currentTile = Rs2Player.getWorldLocation();
            if(currentTile!=null&&FirstLoopTile!=null){
                if(currentTile.equals(FirstLoopTile)){
                    Microbot.log("We seem to be stuck. Resetting the walker");
                    stopFutureWalker();
                    tunnelLoopCount = 0;
                }
            }
        }
        if(tunnelLoopCount >= 30) tunnelLoopCount = 0;
    }

    public void swapTheSpellbook(){
        if(!Rs2Magic.getSpellbook().equals(Rs2Spellbook.MODERN)){
            WorldPoint swapLocation = Rs2Magic.getSpellbook().getSwitchLocation();

            if(Rs2Player.getWorldLocation().distanceTo(swapLocation) > 5) Rs2Walker.walkTo(swapLocation);

            Rs2Spellbook.MODERN.switchTo();
        }
    }

    public void gettheRune(){
        int min = Math.max(minRuneAmt, 1);
        // Prefer highest castable tier already in inventory above min (never lock Death over Blood).
        if (switchToInventoryRuneTier(min)) {
            return;
        }
        neededRune = getHighestCastableRune();
    }

    private String getHighestCastableRune() {
        // Spell requirements use current (boosted) Magic — Forgotten brew can unlock Wind Wave.
        int magicLvl = Rs2Player.getBoostedSkillLevel(Skill.MAGIC);
        if (magicLvl >= 81) return "Wrath rune";
        if (magicLvl >= 62) return "Blood rune";
        if (magicLvl >= 41) return "Death rune";
        return "Death rune";
    }

    private List<String> getCastableRuneTiers() {
        String highest = getHighestCastableRune();
        List<String> castable = new ArrayList<>();
        boolean reached = false;
        for (String tier : RUNE_TIERS) {
            if (tier.equals(highest)) {
                reached = true;
            }
            if (reached) {
                castable.add(tier);
            }
        }
        return castable;
    }

    private boolean bankHasEnoughRunes(String rune, int minAmount) {
        if (rune == null || "unknown".equals(rune)) return false;
        if (Rs2Bank.getBankItem(rune) == null) return false;
        return Rs2Bank.getBankItem(rune).getQuantity() > minAmount;
    }

    private boolean inventoryHasEnoughRunes(String rune, int minAmount) {
        if (rune == null || "unknown".equals(rune)) return false;
        Rs2ItemModel item = Rs2Inventory.get(rune);
        return item != null && item.getQuantity() > minAmount;
    }

    private boolean switchToInventoryRuneTier(int minAmount) {
        for (String tier : getCastableRuneTiers()) {
            if (inventoryHasEnoughRunes(tier, minAmount)) {
                neededRune = tier;
                return true;
            }
        }
        return false;
    }

    private boolean downgradeRuneTier(BarrowsConfig config) {
        List<String> castable = getCastableRuneTiers();
        int currentIndex = castable.indexOf(neededRune);
        if (currentIndex < 0) {
            currentIndex = -1;
        }
        for (int i = currentIndex + 1; i < castable.size(); i++) {
            String tier = castable.get(i);
            if (bankHasEnoughRunes(tier, config.minRuneAmount()) || inventoryHasEnoughRunes(tier, config.minRuneAmount())) {
                Microbot.log("Out of " + neededRune + " — falling back to " + tier);
                neededRune = tier;
                return true;
            }
        }
        return false;
    }

    private void withdrawNeededRunes(BarrowsConfig config) {
        int bankQty = Rs2Bank.getBankItem(neededRune).getQuantity();
        if (Rs2Bank.withdrawX(neededRune, Rs2Random.between(config.minRuneAmount(), bankQty))) {
            String therune = neededRune;
            sleepUntil(() -> Rs2Inventory.get(therune) != null && Rs2Inventory.get(therune).getQuantity() > config.minRuneAmount(), Rs2Random.between(2000, 4000));
        }
    }

    public void setAutoCast(){
        if("Wrath rune".equals(neededRune)){
            if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_SURGE) {
                Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_SURGE, false);
            }
        }

        if("Blood rune".equals(neededRune)){
            if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_WAVE) {
                Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_WAVE, false);
            }
        }

        if("Death rune".equals(neededRune)){
            if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_BLAST) {
                Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_BLAST, false);
            }
        }
    }

    public void activatePrayer(Rs2PrayerEnum prayer){
        if(!Rs2Prayer.isPrayerActive(prayer)){
            Microbot.log("Turning on Prayer.");
            drinkPrayerPot();
            Rs2Prayer.toggle(prayer);
        }
    }
    public void antiPatternEnableWrongPrayer(){
        if(!Rs2Prayer.isPrayerActive(NeededPrayer)){
            if(Rs2Random.between(0,100) <= Rs2Random.between(1,4)) {
                Rs2PrayerEnum wrongPrayer = null;
                int random = Rs2Random.between(0,100);
                if(random <= 50) wrongPrayer = Rs2PrayerEnum.PROTECT_MELEE;

                if(random > 50 && random < 75) wrongPrayer = Rs2PrayerEnum.PROTECT_RANGE;

                if(random >= 75) wrongPrayer = Rs2PrayerEnum.PROTECT_MAGIC;

                drinkPrayerPot();
                Rs2Prayer.toggle(wrongPrayer);
                sleep(0, 750);
            }
        }
    }
    public void antiPatternActivatePrayer(){
        if(!Rs2Prayer.isPrayerActive(NeededPrayer)){
            if(Rs2Random.between(0,100) <= Rs2Random.between(1,8)) {
                drinkPrayerPot();
                Rs2Prayer.toggle(NeededPrayer);
                sleep(0, 750);
            }
        }
    }
    public void antiPatternDropVials(){
        if(Rs2Random.between(0,100) <= Rs2Random.between(1,25)) {
            Rs2ItemModel whatToDrop = Rs2Inventory.get(it->it!=null&&it.getName().contains("Vial")||it.getName().contains("Butterfly jar"));
            if(whatToDrop!=null) {
                if (Rs2Inventory.contains(whatToDrop.getName())) {
                    if (Rs2Inventory.drop(whatToDrop.getName())) sleep(0, 750);
                }
            }
        }
    }
    public void outOfSupplies(BarrowsConfig config){
        suppliesCheck(config);
        if(!shouldBank){
            return;
        }
        // Only ring-tele out of places we cannot safely walk from (tunnels / crypt / POH).
        // Overworld restock walks to the nearest bank — never pathing to Ferox on foot.
        boolean needFeroxRingTeleport = false;
        if(inTunnels){
            needFeroxRingTeleport = true;
        }
        if(Rs2Player.getWorldLocation().getPlane() == 3){
            needFeroxRingTeleport = true;
        }
        if(isInPlayerOwnedHouse()){
            needFeroxRingTeleport = true;
        }
        if(!needFeroxRingTeleport){
            return;
        }
        if(tryFeroxTeleportViaRingOfDueling()){
            Microbot.log("We're out of supplies. Teleporting to Ferox Enclave.");
            if(inTunnels){
                inTunnels = false;
            }
            sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
            sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
        }
    }

    /**
     * Restock at the geographically nearest bank — never hardcode Ferox
     * (Barrows→Ferox on foot routes through the wilderness / GE transports).
     */
    private void goToNearestBankForRestock(){
        if(Rs2Bank.isOpen()){
            return;
        }
        BankLocation nearest = Rs2Bank.getNearestBank();
        if(nearest != null){
            Microbot.log("Walking to nearest bank for restock: " + nearest);
            Rs2Bank.walkToBankAndUseBank(nearest);
        } else {
            Microbot.log("Walking to nearest bank for restock.");
            Rs2Bank.walkToBankAndUseBank();
        }
    }

    private static final int[] DUELING_RING_IDS = new int[]{
            ItemID.RING_OF_DUELING1,
            ItemID.RING_OF_DUELING2,
            ItemID.RING_OF_DUELING3,
            ItemID.RING_OF_DUELING4,
            ItemID.RING_OF_DUELING5,
            ItemID.RING_OF_DUELING6,
            ItemID.RING_OF_DUELING7,
            ItemID.RING_OF_DUELING8
    };

    private boolean hasDuelingRingInInventory(){
        for(int id : DUELING_RING_IDS){
            if(Rs2Inventory.hasItem(id)){
                return true;
            }
        }
        return Rs2Inventory.contains(it -> it != null && it.getName() != null && it.getName().contains("Ring of dueling"));
    }

    private boolean hasDuelingRingEquipped(){
        for(int id : DUELING_RING_IDS){
            if(Rs2Equipment.isWearing(id)){
                return true;
            }
        }
        return Rs2Equipment.isWearing("Ring of dueling", false);
    }

    private boolean hasDuelingRingAvailable(){
        return hasDuelingRingInInventory() || hasDuelingRingEquipped();
    }

    /** Withdraw Ring of dueling into inventory (never equip — rub/tele from inv). */
    private void ensureRingOfDuelingFromBank(){
        if(!Rs2Bank.isOpen()){
            return;
        }
        if(hasDuelingRingInInventory()){
            return;
        }

        Microbot.log("Withdrawing Ring of dueling for inventory Ferox teleports.");
        if(!withdrawOneDuelingRingFromBank()){
            Microbot.log("Out of Rings of dueling — stopping.");
            super.shutdown();
        }
    }

    private boolean withdrawOneDuelingRingFromBank(){
        // Prefer lowest charge first so we finish partial rings instead of stockpiling (8)s.
        for(int ringId : DUELING_RING_IDS){
            if(Rs2Bank.count(ringId) > 0){
                final int id = ringId;
                if(Rs2Bank.withdrawX(id, 1)){
                    sleepUntil(() -> Rs2Inventory.hasItem(id), Rs2Random.between(2000, 5000));
                    return Rs2Inventory.hasItem(id);
                }
            }
        }
        // Name fallback: try (1)..(8) then bare name if IDs miss a variant.
        for(int charges = 1; charges <= 8; charges++){
            String name = "Ring of dueling(" + charges + ")";
            if(Rs2Bank.hasBankItem(name) && Rs2Bank.withdrawOne(name)){
                sleepUntil(this::hasDuelingRingInInventory, Rs2Random.between(2000, 5000));
                return hasDuelingRingInInventory();
            }
        }
        if(Rs2Bank.hasBankItem("Ring of dueling") && Rs2Bank.withdrawOne("Ring of dueling")){
            sleepUntil(this::hasDuelingRingInInventory, Rs2Random.between(2000, 5000));
            return hasDuelingRingInInventory();
        }
        return false;
    }

    private boolean isAtFeroxEnclave(){
        WorldPoint loc = Rs2Player.getWorldLocation();
        if(loc == null){
            return false;
        }
        // Ferox enclave / pool area
        return loc.distanceTo(new WorldPoint(3130, 3631, 0)) < 40;
    }

    /** After nearest-bank restock: RoD to Ferox and drink from the restoration pool. */
    private void restoreAtFeroxPool(){
        if(!isAtFeroxEnclave()){
            if(tryFeroxTeleportViaRingOfDueling()){
                Microbot.log("Teleporting to Ferox Enclave for the restoration pool.");
                sleepUntil(Rs2Player::isAnimating, Rs2Random.between(2000, 4000));
                sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                sleepUntil(this::isAtFeroxEnclave, Rs2Random.between(4000, 8000));
            } else {
                Microbot.log("Could not RoD to Ferox — skipping pool (need a Ring of dueling in inventory).");
                return;
            }
        }
        if(isAtFeroxEnclave()){
            reJfount();
        }
    }

    private boolean isInPlayerOwnedHouse(){
        Client c = Microbot.getClient();
        if(c == null){
            return false;
        }
        WorldView wv = c.getTopLevelWorldView();
        if(wv == null){
            return false;
        }
        if(!wv.isInstance()){
            return false;
        }
        if(inTunnels){
            return false;
        }
        Rs2TileObjectModel portal = rs2TileObjectCache.query().withId(4525).nearestOnClientThread();
        return portal != null;
    }

    private boolean tryFeroxTeleportViaRingOfDueling(){
        // Prefer inventory rub/tele; fall back to an already-equipped ring so upgrades from
        // older versions (ring worn, none in inv) can still leave Barrows/tunnels.
        for(int idx = DUELING_RING_IDS.length - 1; idx >= 0; idx--){
            int ringId = DUELING_RING_IDS[idx];
            if(!Rs2Inventory.hasItem(ringId)){
                continue;
            }
            if(tryRubInventoryRingToFerox(ringId)){
                return true;
            }
        }
        Rs2ItemModel invRing = Rs2Inventory.get(it -> it != null && it.getName() != null && it.getName().contains("Ring of dueling"));
        if(invRing != null && tryRubInventoryRingToFerox(invRing.getId())){
            return true;
        }
        return tryEquippedRingToFerox();
    }

    private boolean tryRubInventoryRingToFerox(int ringId){
        String feroxLabel = JewelleryLocationEnum.FEROX_ENCLAVE.getDestination();
        if(Rs2Inventory.interact(ringId, feroxLabel)){
            return true;
        }
        if(Rs2Inventory.interact(ringId, "Rub")){
            sleepUntil(() -> Rs2Dialogue.hasDialogueOption(feroxLabel), Rs2Random.between(1500, 3500));
            if(Rs2Dialogue.clickOption(feroxLabel)){
                return true;
            }
            return Rs2Dialogue.clickOption(feroxLabel, false);
        }
        return false;
    }

    private boolean tryEquippedRingToFerox(){
        if(!hasDuelingRingEquipped()){
            return false;
        }
        String feroxLabel = JewelleryLocationEnum.FEROX_ENCLAVE.getDestination();
        if(Rs2Equipment.interact(EquipmentInventorySlot.RING, feroxLabel)){
            return true;
        }
        if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Rub")){
            sleepUntil(() -> Rs2Dialogue.hasDialogueOption(feroxLabel), Rs2Random.between(1500, 3500));
            if(Rs2Dialogue.clickOption(feroxLabel)){
                return true;
            }
            return Rs2Dialogue.clickOption(feroxLabel, false);
        }
        return false;
    }
    public void disablePrayer(){
        disablePrayer(false);
    }

    public void disablePrayer(boolean force){
        if(force || Rs2Random.between(0,100) >= Rs2Random.between(0,5)) {
            Rs2Prayer.disableAllPrayers();
            sleep(0,750);
        }
    }

    /** Drop overhead protection when we are not fighting a barrows brother. */
    private void disableProtectPrayers(){
        if(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)
                || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)
                || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)){
            disablePrayer(true);
        }
    }
    public void reJfount(){
        int rejat = Rs2Random.between(10,30);
        int runener = Rs2Random.between(50,65);
        while(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) < rejat || Rs2Player.getRunEnergy() <= runener){
            if (!super.isRunning()) break;

            if(Rs2Bank.isOpen()){
                if(Rs2Bank.closeBank()) sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,4000));

            } else {
                Rs2TileObjectModel rej = rs2TileObjectCache.query().withId(39651).nearest();
                if(rej == null) break;
                Microbot.log("Drinking");
                if(rej.click("Drink")){
                    sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(1000,3000));
                    sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(5000,10000));
                    sleepUntil(()-> Rs2Player.isAnimating(), Rs2Random.between(1000,4000));
                    sleepUntil(()-> !Rs2Player.isAnimating(), Rs2Random.between(1000,4000));
                }
            }

            if(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= rejat && Rs2Player.getRunEnergy() >= runener) break;
        }
    }
    public void drinkPrayerPot(){
        boolean skipThePot = false;
        if(hintNpcModel() != null && !hintNpcModel().getName().contains("Dharok") && hintNpcModel().getHealthPercentage() < Rs2Random.between(40,50)) skipThePot = true;

        if(!skipThePot) {
            if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= Rs2Random.between(8, 15)) {
                if (Rs2Inventory.contains(it -> it != null && it.getName().contains("Prayer potion") || it.getName().contains("Moonlight moth"))) {
                    Rs2ItemModel prayerpotion = Rs2Inventory.get(it -> it != null && it.getName().contains("Prayer potion") || it.getName().contains("Moonlight moth"));
                    String action = "Drink";
                    if (prayerpotion.getName().equals("Moonlight moth")) action = "Release";

                    if (Rs2Inventory.interact(prayerpotion, action)) sleep(0, 750);
                }
            }
        }
    }

    public Rs2NpcModel hintNpcModel(){
        Optional<NPC> hintNpc = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getHintArrowNpc()
        );

        if(hintNpc.isPresent() && hintNpc.get() != null){
            return new Rs2NpcModel(hintNpc.get());
        }
        return null;
    }

    /** True when our current attack target is this NPC instance (index), not just same name. */
    private boolean isInteractingWith(Rs2NpcModel npc){
        if(npc == null){
            return false;
        }
        Actor interacting = Rs2Player.getInteracting();
        if(!(interacting instanceof NPC)){
            return false;
        }
        return ((NPC) interacting).getIndex() == npc.getIndex();
    }

    /**
     * Click Attack when available. Returns false if the NPC died, despawned, or its Attack
     * action is null/missing — callers should drop the target and continue the script.
     */
    private boolean tryAttackNpc(Rs2NpcModel npc){
        if(npc == null){
            return false;
        }
        try {
            if(npc.isDead()){
                return false;
            }
            if(!npcHasAttackOption(npc)){
                return false;
            }
            return npc.click("Attack");
        } catch (Exception e) {
            Microbot.log("Attack unavailable (" + e.getClass().getSimpleName() + ") — continuing.");
            return false;
        }
    }

    private boolean npcHasAttackOption(Rs2NpcModel npc){
        if(npc == null){
            return false;
        }
        Thread.interrupted();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPC n = npc.getNpc();
            if(n == null){
                return false;
            }
            NPCComposition comp = n.getTransformedComposition();
            if(comp == null){
                comp = n.getComposition();
            }
            if(comp == null){
                return false;
            }
            String[] actions = comp.getActions();
            if(actions == null){
                return false;
            }
            for(String action : actions){
                if(action != null && action.equalsIgnoreCase("Attack")){
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public void checkForAndFightBrother(BarrowsConfig config){
        Rs2NpcModel currentBrother = findTunnelBrother();
        if (currentBrother == null) {
            return;
        }

        shouldAttackSkeleton = false;
        stopFutureWalker();

        Rs2PrayerEnum neededprayer = prayerForBrother(currentBrother);
        if(config.shouldPrayAgainstWeakerBrothers()){
            activatePrayer(neededprayer);
        } else if(currentBrother.getName() != null
                && !currentBrother.getName().contains("Torag")
                && !currentBrother.getName().contains("Guthan")
                && !currentBrother.getName().contains("Verac")){
            activatePrayer(neededprayer);
        }

        long deadline = System.currentTimeMillis() + Rs2Random.between(90000, 120000);
        while(System.currentTimeMillis() < deadline){
            currentBrother = findTunnelBrother();
            if(currentBrother == null || currentBrother.isDead()){
                Microbot.log("Breaking out the brother is gone.");
                disablePrayer(true);
                break;
            }

            Microbot.log("Fighting the brother: " + currentBrother.getName());

            if (!super.isRunning()){
                break;
            }

            if(inTunnels && findTunnelTrashAggressor() != null){
                if(!clearTunnelTrashAggressor(config)){
                    break;
                }
                continue;
            }

            neededprayer = prayerForBrother(currentBrother);
            if(config.shouldPrayAgainstWeakerBrothers()){
                activatePrayer(neededprayer);
            } else if(currentBrother.getName() != null
                    && !currentBrother.getName().contains("Torag")
                    && !currentBrother.getName().contains("Guthan")
                    && !currentBrother.getName().contains("Verac")){
                activatePrayer(neededprayer);
            }

            if(!isInteractingWith(currentBrother)){
                final Rs2NpcModel attackTarget = currentBrother;
                if(!tryAttackNpc(attackTarget)){
                    Microbot.log("Brother gone or Attack unavailable — continuing.");
                    disablePrayer(true);
                    break;
                }
                sleepUntil(() -> isInteractingWith(attackTarget)
                                || findTunnelBrother() == null
                                || attackTarget.isDead()
                                || !npcHasAttackOption(attackTarget),
                        Rs2Random.between(3000,6000));
            }

            sleep(750,1500);
            drinkPrayerPot();
            eatFood();
            outOfSupplies(config);
            antiPatternDropVials();
            drinkforgottonbrew();

            if(shouldBank){
                break;
            }

            Rs2NpcModel after = findTunnelBrother();
            if(after == null || after.isDead() || !npcHasAttackOption(after)) {
                Microbot.log("Breaking out the brother is dead/gone.");
                disablePrayer(true);
                sleep(300, 800);
                break;
            }
        }
    }

    public void stopFutureWalker(){
        boolean futureActive = WalkToTheChestFuture != null
                && !WalkToTheChestFuture.isCancelled()
                && !WalkToTheChestFuture.isDone();
        if (futureActive) {
            // cancel(false): never interrupt the shared executor thread — that leaves
            // InterruptedException on the next walkTo / client-thread wait.
            WalkToTheChestFuture.cancel(false);
        }
        WalkToTheChestFuture = null;

        // Only clear an active route — avoid spam-clearing every combat tick with reason=<missing>.
        if (Rs2Walker.getCurrentTarget() != null) {
            Rs2Walker.clearWalkingRoute("barrows:stop-chest-walker");
        }
    }

    /**
     * Drive chest pathing each tunnels tick via walkWithStateUntil so we abort mid-path
     * for puzzle / brother / same-room monsters instead of walking past them.
     */
    private void ensureChestWalk(){
        Thread.interrupted();
        if(!inTunnels || !isInTunnelCoords()){
            return;
        }
        if(shouldDeferChestWalk()){
            stopFutureWalker();
            return;
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        if(here != null && here.distanceTo(Chest) <= 2){
            return;
        }

        // Already pathing to the chest — do not re-enter the walker (same-thread lock wait).
        WorldPoint currentTarget = Rs2Walker.getCurrentTarget();
        if(currentTarget != null && currentTarget.distanceTo(Chest) <= 2){
            return;
        }

        try {
            Rs2Walker.walkWithStateUntil(Chest, 2, this::shouldDeferChestWalk);
        } catch (Exception e) {
            Microbot.log("walkToChest failed: " + e.getClass().getSimpleName());
        } finally {
            // walkWithState / sleepUntil almost always leave this set.
            Thread.interrupted();
        }
        if(isDoorPuzzleOpenRaw()){
            waitingOnPuzzle = true;
            stopFutureWalker();
            return;
        }
        if(shouldDeferChestWalk()){
            stopFutureWalker();
        }
    }

    private void walkToChest(){
        ensureChestWalk();
    }

    private void startWalkingToTheChest() {
        ensureChestWalk();
    }

    public void drinkforgottonbrew() {
        if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Forgotten brew"))) {
            if(Rs2Player.getBoostedSkillLevel(Skill.MAGIC) <= (Rs2Player.getRealSkillLevel(Skill.MAGIC) + Rs2Random.between(1,4))) {
                Microbot.log("Drinking a Forgotten brew.");
                String[] priorityOfBrews = {"Forgotten brew(1)", "Forgotten brew(2)", "Forgotten brew(3)", "Forgotten brew(4)" };

                for (String brew : priorityOfBrews) {
                    if(Rs2Inventory.contains(brew)) {
                        if(Rs2Inventory.interact(brew, "Drink")){
                            sleep(300,1000);
                            break;
                        }
                    }
                }
            }
        }
    }

    public void eatFood(){
        if(Rs2Player.getHealthPercentage() <= 60){
            if(Rs2Inventory.contains(it->it!=null&&it.isFood())){
                Rs2ItemModel food = Rs2Inventory.get(it->it!=null&&it.isFood());
                if(Rs2Inventory.interact(food, "Eat")){
                    sleep(0,750);
                }
            }
        }
    }


    public enum BarrowsBrothers {
        DHAROK ("Dharok the Wretched", new Rs2WorldArea(3573,3296,3,3,0), Rs2PrayerEnum.PROTECT_MELEE, 115),
        GUTHAN ("Guthan the Infested", new Rs2WorldArea(3575,3280,3,3,0), Rs2PrayerEnum.PROTECT_MELEE, 115),
        KARIL  ("Karil the Tainted", new Rs2WorldArea(3564,3274,3,3,0), Rs2PrayerEnum.PROTECT_RANGE, 98),
        TORAG  ("Torag the Corrupted", new Rs2WorldArea(3552,3282,2,2,0), Rs2PrayerEnum.PROTECT_MELEE, 115),
        VERAC  ("Verac the Defiled", new Rs2WorldArea(3556,3297,3,3,0), Rs2PrayerEnum.PROTECT_MELEE, 115),
        AHRIM  ("Ahrim the Blighted", new Rs2WorldArea(3563,3288,3,3,0), Rs2PrayerEnum.PROTECT_MAGIC, 98);

        private String name;

        private Rs2WorldArea humpWP;

        private Rs2PrayerEnum whatToPray;

        /** Combat level — equals reward potential granted when this brother is killed. */
        private int combatLevel;


        BarrowsBrothers(String name, Rs2WorldArea humpWP, Rs2PrayerEnum whatToPray, int combatLevel) {
            this.name = name;
            this.humpWP = humpWP;
            this.whatToPray = whatToPray;
            this.combatLevel = combatLevel;
        }

        public String getName() { return name; }
        public Rs2WorldArea getHumpWP() { return humpWP; }
        public Rs2PrayerEnum getWhatToPray() { return whatToPray; }
        public int getCombatLevel() { return combatLevel; }

    }

    /** Resolve the configured Inventory Setup dropdown selection, or null if none. */
    private Rs2InventorySetup resolveInventorySetup(BarrowsConfig config) {
        try {
            InventorySetup selected = config.inventorySetup();
            if (selected == null) {
                return null;
            }
            loggedCachedInventorySetupWarning = false;
            return new Rs2InventorySetup(selected, mainScheduledFuture);
        } catch (Exception e) {
            if (!loggedCachedInventorySetupWarning) {
                Microbot.log("Inventory Setups unavailable (" + e.getClass().getSimpleName()
                        + ") — using current gear.");
                loggedCachedInventorySetupWarning = true;
            }
            return null;
        }
    }

    /**
     * Hub workaround for client Rs2InventorySetup.loadEquipment aborting when
     * depositAllExcept returns false (nothing deposited) even though bank has the
     * missing gear. Withdraw/equip missing slots directly by name.
     */
    private void tryDirectEquipMissingGear(Rs2InventorySetup inventorySetup) {
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) {
            return;
        }
        List<InventorySetupsItem> equipment = inventorySetup.getEquipmentItems();
        if (equipment == null || equipment.isEmpty()) {
            return;
        }
        Microbot.log("loadEquipment aborted before withdraw; trying direct withdrawAndEquip for missing gear...");
        for (InventorySetupsItem item : equipment) {
            if (!super.isRunning()) {
                return;
            }
            if (item == null || InventorySetupsItem.itemIsDummy(item)) {
                continue;
            }
            String name = item.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (Rs2Equipment.isWearing(name)) {
                continue;
            }
            if (Rs2Inventory.hasItem(name)) {
                Rs2Bank.wearItem(name);
            } else if (Rs2Bank.hasBankItem(name)) {
                Rs2Bank.withdrawAndEquip(name);
            }
            sleep(Rs2Random.between(300, 600));
        }
    }

    @Override
    public void shutdown() {
        loggedCachedInventorySetupWarning = false;
        startingEquipmentReady = false;
        firstRun = true;
        waitingOnPuzzle = false;
        requireHouseTabsToTravel = false;
        resetTunnelRoomKills();
        // Cancel walkers / stale loops, but leave mainScheduledFuture for Script.shutdown()
        // so base cleanup (ShortestPathPlugin.exit, pause/spec reset) still runs.
        RUN_GENERATION.incrementAndGet();
        stopFutureWalker();
        ScheduledFuture<?> active = activeMainFuture;
        if (active != null) {
            active.cancel(false);
            activeMainFuture = null;
        }
        Thread.interrupted();
        try {
            Rs2Combat.setAutoRetaliate(true);
        } catch (Exception ignored) {
            // best-effort restore
        }
        super.shutdown();
    }

    private void stopAllScriptTasks(){
        // Invalidate every previously scheduled main loop (across Script instance recreations).
        RUN_GENERATION.incrementAndGet();
        stopFutureWalker();
        // cancel(false): never interrupt the shared executor thread — that poisons the next
        // run() with a sticky interrupt (puzzle/NPC client-thread reads fail until restart).
        ScheduledFuture<?> active = activeMainFuture;
        if (active != null) {
            active.cancel(false);
            activeMainFuture = null;
        }
        if(mainScheduledFuture != null){
            mainScheduledFuture.cancel(false);
            mainScheduledFuture = null;
        }
        Thread.interrupted();
    }
}
