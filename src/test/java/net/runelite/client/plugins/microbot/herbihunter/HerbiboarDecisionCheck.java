package net.runelite.client.plugins.microbot.herbihunter;

import net.runelite.api.coords.WorldPoint;

/** Only the pure decisions are exercised: walking, clicking and the hole climb need a client. */
public final class HerbiboarDecisionCheck
{
    private static int checks;
    private static int failures;

    /** Runs every check and returns how many failed. */
    static int run()
    {
        checks = 0;
        failures = 0;
        travelStartsOnlyOffTheIsland();
        firstStallIsToleratedAsNormalWalkerExit();
        theWayBackGoesStraightToTheNextTarget();
        secondStallClimbsTheShortcut();
        shortcutKeepsBeingTriedBeforeGivingUp();
        fifthStallResets();
        finishIdMustIndexARealEndLocation();
        holeIsOnlyUsedWhenTheTripActuallyCrossesIt();
        crossingPicksTheMouthOnOurOwnSide();
        holeIsPreferredOnlyFromWithinReachOfIt();
        onlyOneDoseIsDrunkPerPass();
        aTurnPicksTheKeyThatClosesTheAngle();
        distractionsStayInTheirMoments();
        afkNeverReachesTheWatchdog();
        aLostTrailGoesBackToAStartRock();
        aDispatchedClickIsWaitedOnExactlyOnce();
        aBankTripIsOnlyWorthMakingIfItCanHelp();
        aBankTripNeverAbandonsABoar();
        theHoleIsUsedForTheFourRealCrossingsOnly();
        aResetResumesTheLiveTrailInsteadOfWalkingAway();
        onlyComingBackInReadsTheTrailAgain();
        theHoleIsClimbedOnlyWhenTheRouteJumpsIt();
        onlyRealInViewClickboxesAreClicked();
        aClickIsOnlySentWhereTheGameWouldDoTheSameOnALeftClick();
        aStuckScriptGivesUpInsteadOfIdlingIntoALogout();
        aSlidingTargetIsLedByHowFarItMovesWhileTheCursorTravels();
        noTwoSessionsShareTheSameHabits();
        everyConfigOptionDoesWhatItSays();
        everyConfigOptionIsReadByThePlugin();
        theConfigPanelReadsTheWayItWasDesigned();
        theInfoPanelShowsHonestNumbers();
        antibanPanelOverridesAreReported();
        thePanelKeepsItsHouseColours();
        theSeededTilesAreKept();
        thePaceModelBehaves();
        switchingStyleKeepsThePersonality();
        noLapseWhileTheBoarIsOut();
        boarWaitDoesOneThingOnly();
        theStepTimerSplitsADelayIntoItsPhases();
        grassNeverReplacesASearchSpot();
        upkeepOnlyHoldsTheNextClickWhenThereIsSomethingToDo();
        focusedMovesTheMouseFast();
        theLogWritesOnlyWhatTheSettingAllows();
        theCatchLineAndTheNothingMessage();
        reaching99LogsOutOnlyWhenAsked();
        tooLowALevelStopsTheRun();
        breaksAreRespectedAndNotCounted();
        theStrongerPotionsAreSupported();
        focusedFollowsTheTrailWithTheCamera();
        aMinimapStepOnlyEverWalks();
        givingUpAndComingBackLookHuman();
        aBreakIsNotWaitedForUnderAttack();

        System.out.println();
        System.out.println(failures == 0
            ? "ALL " + checks + " CHECKS PASSED"
            : failures + " of " + checks + " CHECKS FAILED");
        return failures;
    }

    private static void firstStallIsToleratedAsNormalWalkerExit()
    {
        check("stall 0 continues", HerbiboarScript.StallAction.CONTINUE, HerbiboarScript.stallAction(0));
        check("stall 1 climbs", HerbiboarScript.StallAction.SHORTCUT, HerbiboarScript.stallAction(1));
    }

    /** A walk that moved the player nowhere is the signature of the walker abandoning the hole. */
    private static void secondStallClimbsTheShortcut()
    {
        check("stall 2 climbs", HerbiboarScript.StallAction.SHORTCUT, HerbiboarScript.stallAction(2));
    }

    private static void shortcutKeepsBeingTriedBeforeGivingUp()
    {
        check("stall 3 climbs", HerbiboarScript.StallAction.SHORTCUT, HerbiboarScript.stallAction(3));
        check("stall 4 climbs", HerbiboarScript.StallAction.SHORTCUT, HerbiboarScript.stallAction(4));
    }

    /** The ladder must terminate, or a blocked route loops forever. */
    private static void fifthStallResets()
    {
        check("stall 5 resets", HerbiboarScript.StallAction.RESET, HerbiboarScript.stallAction(5));
        check("stall 9 resets", HerbiboarScript.StallAction.RESET, HerbiboarScript.stallAction(9));
    }

    /** finishId is 1-based and comes straight from a varbit, so both ends need guarding. */
    private static void finishIdMustIndexARealEndLocation()
    {
        check("finishId 0 rejected", false, HerbiboarScript.validFinishId(0, 9));
        check("finishId -1 rejected", false, HerbiboarScript.validFinishId(-1, 9));
        check("finishId 1 accepted", true, HerbiboarScript.validFinishId(1, 9));
        check("finishId 9 accepted", true, HerbiboarScript.validFinishId(9, 9));
        check("finishId 10 rejected", false, HerbiboarScript.validFinishId(10, 9));
        check("no end locations rejects everything", false, HerbiboarScript.validFinishId(1, 0));
    }

    private static void holeIsOnlyUsedWhenTheTripActuallyCrossesIt()
    {
        WorldPoint south = new WorldPoint(3716, 3815, 0);
        WorldPoint north = new WorldPoint(3713, 3830, 0);
        WorldPoint farNorth = new WorldPoint(3700, 3880, 0);
        check("south to north crosses", true, HerbiboarScript.crossesHole(south, north));
        check("north to south crosses", true, HerbiboarScript.crossesHole(north, south));
        check("south to south does not", false, HerbiboarScript.crossesHole(south, new WorldPoint(3720, 3810, 0)));
        check("north to far north does not", false, HerbiboarScript.crossesHole(north, farNorth));
        check("null is never a crossing", false, HerbiboarScript.crossesHole(null, north));
    }


    private static void crossingPicksTheMouthOnOurOwnSide()
    {
        WorldPoint south = new WorldPoint(3740, 3805, 0);
        WorldPoint north = new WorldPoint(3700, 3880, 0);
        check("from the south we head for the south mouth", 3815,
            HerbiboarScript.holeMouthNearest(south).getY());
        check("from the north we head for the north mouth", 3830,
            HerbiboarScript.holeMouthNearest(north).getY());
    }


    /** Clicking a visible object across the hole makes the game walk round the cliff. */
    private static void holeIsPreferredOnlyFromWithinReachOfIt()
    {
        WorldPoint atSouthMouth = new WorldPoint(3716, 3816, 0);
        WorldPoint justNorth = new WorldPoint(3713, 3832, 0);
        WorldPoint farNorthEast = new WorldPoint(3751, 3893, 0);
        check("standing at the mouth, target over it: use the hole", true,
            HerbiboarScript.shouldUseHole(atSouthMouth, justNorth));
        check("same side: no hole", false,
            HerbiboarScript.shouldUseHole(atSouthMouth, new WorldPoint(3720, 3812, 0)));
        check("far from the hole: walk round instead", false,
            HerbiboarScript.shouldUseHole(farNorthEast, new WorldPoint(3740, 3805, 0)));
    }

    /** Drinking a 4-dose makes a 3-dose, which a following {@code if} would drink too. */
    private static void onlyOneDoseIsDrunkPerPass()
    {
        int four = 4, three = 3, two = 2, one = 1;
        check("started 2-dose finished before a fresh 4-dose", two,
            HerbiboarScript.lowestDoseHeld(id -> id == four || id == two, one, two, three, four));
        check("a fresh 4-dose is drunk when it is all there is", four,
            HerbiboarScript.lowestDoseHeld(id -> id == four, one, two, three, four));
        check("1-dose chosen when only 1 is held", one,
            HerbiboarScript.lowestDoseHeld(id -> id == one, one, two, three, four));
        check("nothing held drinks nothing", -1,
            HerbiboarScript.lowestDoseHeld(id -> false, one, two, three, four));
    }


    private static void aTurnPicksTheKeyThatClosesTheAngle()
    {
        int left = java.awt.event.KeyEvent.VK_LEFT, right = java.awt.event.KeyEvent.VK_RIGHT;
        check("yaw ahead of target (getAngleTo > 0): hold left, the short way", left, HerbiboarScript.cameraKeyFor(90, 30));
        check("yaw behind target: hold right (e.g. 116 -> 130 wanting 262)", right, HerbiboarScript.cameraKeyFor(-90, 30));
        check("already within tolerance: no key", 0, HerbiboarScript.cameraKeyFor(20, 30));
        check("just outside tolerance: turn", left, HerbiboarScript.cameraKeyFor(31, 30));
        check("dead on target: no key", 0, HerbiboarScript.cameraKeyFor(0, 30));
    }



    private static void aLostTrailGoesBackToAStartRock()
    {
        check("no path and no group: the trail is gone", true,
            HerbiboarScript.trailIsLost(true, true));
        check("fresh trail: group set, spots not in yet", false,
            HerbiboarScript.trailIsLost(true, false));
        check("mid trail: keep following it", false,
            HerbiboarScript.trailIsLost(false, false));
        check("spots but no group: not the reset signature, keep going", false,
            HerbiboarScript.trailIsLost(false, true));
    }

    private static void aBankTripNeverAbandonsABoar()
    {
        check("trail may bank", true, HerbiboarScript.bankAllowedIn(HerbiboarState.TRAIL));
        check("start may bank", true, HerbiboarScript.bankAllowedIn(HerbiboarState.START));
        check("tunnel may not", false, HerbiboarScript.bankAllowedIn(HerbiboarState.TUNNEL));
        check("harvest may not", false, HerbiboarScript.bankAllowedIn(HerbiboarState.HARVEST));
        check("bank does not re-enter", false, HerbiboarScript.bankAllowedIn(HerbiboarState.BANK));
        check("restock does not", false, HerbiboarScript.bankAllowedIn(HerbiboarState.RESTOCK));
        check("initializing does not", false, HerbiboarScript.bankAllowedIn(HerbiboarState.INITIALIZING));
        check("sack: 1 free banks at the catch", true, HerbiboarScript.bankAtCatch(1, true));
        check("sack: 2 free keeps hunting", false, HerbiboarScript.bankAtCatch(2, true));
        check("no sack: 5 free banks", true, HerbiboarScript.bankAtCatch(5, false));
        check("no sack: 6 free keeps hunting", false, HerbiboarScript.bankAtCatch(6, false));
        check("trail locks breaks", true, HerbiboarScript.breaksLocked(HerbiboarState.TRAIL));
        check("tunnel locks breaks", true, HerbiboarScript.breaksLocked(HerbiboarState.TUNNEL));
        check("harvest locks breaks", true, HerbiboarScript.breaksLocked(HerbiboarState.HARVEST));
        // Out on the shore a character that stands still is attacked, and no break begins in combat.
        check("between trails breaks stay locked too", true, HerbiboarScript.breaksLocked(HerbiboarState.START));
        check("only the bank leaves breaks open", false, HerbiboarScript.breaksLocked(HerbiboarState.BANK));
        check("the walk back locks breaks", true, HerbiboarScript.breaksLocked(HerbiboarState.RETURN_FROM_ISLAND));
        check("the G.E. trip locks breaks", true, HerbiboarScript.breaksLocked(HerbiboarState.RESTOCK));
        check("bank exit with a trail keeps TRAIL locked", true, HerbiboarScript.keepBreaksLockedAtBankExit(HerbiboarState.TRAIL));
        check("bank exit with a trail keeps TUNNEL locked", true, HerbiboarScript.keepBreaksLockedAtBankExit(HerbiboarState.TUNNEL));
        check("bank exit without a trail unlocks", false, HerbiboarScript.keepBreaksLockedAtBankExit(HerbiboarState.START));
    }

    /** The 60s watchdog excludes BANK, so nothing else breaks an island/bank shuttle. */
    private static void aBankTripIsOnlyWorthMakingIfItCanHelp()
    {
        check("full inventory always banks", true,
            HerbiboarScript.shouldBank(0, true));
        check("3 slots free with the sack open keeps hunting", false,
            HerbiboarScript.shouldBank(3, true));
        check("running 2 tiles takes a tick", 600L, HerbiboarScript.msToCover(256, true));
        check("walking 2 tiles takes two ticks", 1200L, HerbiboarScript.msToCover(256, false));
        check("restock offers 10% over guide, rounded up", 11002, HerbiboarScript.restockPrice(10001));
        check("no guide price, no offer", -1, HerbiboarScript.restockPrice(0));
        check("restock affordable from inventory + bank coins", true, HerbiboarScript.canAfford(1_200_000, 1_100_000));
        check("restock short of coins logs out", false, HerbiboarScript.canAfford(900_000, 1_100_000));
        check("an offer over the max cash stack is refused", false, HerbiboarScript.canAfford(Long.MAX_VALUE, 3_000_000_000L));
        check("the slot holds our offer: same item, amount and price", true, HerbiboarScript.isRestockOffer(12625, 100, 11002, false, 12625, 100, 11002));
        check("a changed amount is not our offer", false, HerbiboarScript.isRestockOffer(12625, 2000, 11002, false, 12625, 100, 11002));
        check("another item in the slot is not our offer", false, HerbiboarScript.isRestockOffer(3016, 100, 11002, false, 12625, 100, 11002));
        check("a sell offer in its place is not our offer", false, HerbiboarScript.isRestockOffer(12625, 100, 11002, true, 12625, 100, 11002));
        check("a buy at another price is not our offer", false, HerbiboarScript.isRestockOffer(12625, 100, 9000, false, 12625, 100, 11002));
        check("a slot that is not our buy is left alone, not cancelled", HerbiboarScript.RestockWait.GONE,
            HerbiboarScript.restockWait(false, false, false, 200_000));
        check("a climb that never crossed is tried again", false, HerbiboarScript.walksRoundAfterHoleFails(2));
        check("after three climbs that never crossed, walk round", true, HerbiboarScript.walksRoundAfterHoleFails(3));
        check("the open bank pauses the stuck clock", true, HerbiboarScript.stuckClockPaused(HerbiboarState.BANK, true));
        check("walking to the bank does not", false, HerbiboarScript.stuckClockPaused(HerbiboarState.BANK, false));
        check("the trail does not", false, HerbiboarScript.stuckClockPaused(HerbiboarState.TRAIL, false));
        check("the G.E. trip does", true, HerbiboarScript.stuckClockPaused(HerbiboarState.RESTOCK, false));
        check("our offer still buying: wait", HerbiboarScript.RestockWait.WAIT, HerbiboarScript.restockWait(true, false, false, 60_000));
        check("our offer bought: collect it", HerbiboarScript.RestockWait.COLLECT, HerbiboarScript.restockWait(true, true, false, 60_000));
        check("bought but still in the slot after a collect: collect again", HerbiboarScript.RestockWait.COLLECT,
            HerbiboarScript.restockWait(true, true, true, 60_000));
        check("slot empty after our collect: done", HerbiboarScript.RestockWait.DONE, HerbiboarScript.restockWait(false, false, true, 60_000));
        check("slot not yet showing a just-placed offer: wait", HerbiboarScript.RestockWait.WAIT,
            HerbiboarScript.restockWait(false, false, false, 1_000));
        check("our offer gone without our collect: stop", HerbiboarScript.RestockWait.GONE, HerbiboarScript.restockWait(false, false, false, 60_000));
        check("unfilled past the limit: cancel", HerbiboarScript.RestockWait.TIMED_OUT, HerbiboarScript.restockWait(true, false, false, 180_001));
        check("minimap hop aims in the outer band (low draw)", 16, HerbiboarScript.minimapReach(18, false, 0.0));
        check("minimap hop aims in the outer band (high draw)", 18, HerbiboarScript.minimapReach(18, false, 0.999));
        check("a short minimap hop can come down to the floor", 7, HerbiboarScript.minimapReach(18, true, 0.0));
        check("a route shorter than the floor is taken whole", 5, HerbiboarScript.minimapReach(5, false, 0.5));
        check("nothing on the minimap: no reach", 0, HerbiboarScript.minimapReach(0, false, 0.5));
        check("far spot on screen (55 tiles): walk, don't click", true, HerbiboarScript.walkFirst(true, 55));
        check("spot on screen within reach: click it", false, HerbiboarScript.walkFirst(true, 12));
        check("spot on screen 25 tiles off, zoomed out: click it, no minimap hop", false, HerbiboarScript.walkFirst(true, 25));
        check("spot on screen 30 tiles off: walk", true, HerbiboarScript.walkFirst(true, 30));
        check("spot off screen 10 tiles away: walk", true, HerbiboarScript.walkFirst(false, 10));
        check("spot beside us, off screen: turn the camera, no walk", false, HerbiboarScript.walkFirst(false, 2));
        check("two unanswered searches: keep trying", false, HerbiboarScript.trailIsStuck(2));
        check("three unanswered searches of one spot: the trail is stuck", true, HerbiboarScript.trailIsStuck(3));
        check("missing setup banks", true,
            HerbiboarScript.shouldBank(20, false));
        check("room and setup ready: no trip", false,
            HerbiboarScript.shouldBank(20, true));
    }

    /** K_PATCH is the only spot south of the divide; I_MUSHROOM -> K_PATCH sits 33 tiles west of the mouth, so it walks round. */
    private static void theHoleIsUsedForTheFourRealCrossingsOnly()
    {
        WorldPoint kPatch = new WorldPoint(3706, 3811, 0);
        WorldPoint iMushroom = new WorldPoint(3680, 3838, 0);
        WorldPoint tunnel2 = new WorldPoint(3702, 3808, 0);
        WorldPoint tunnel6 = new WorldPoint(3715, 3840, 0);
        WorldPoint jPatch = new WorldPoint(3713, 3840, 0);
        WorldPoint campBank = new WorldPoint(3739, 3804, 0);
        WorldPoint returnPoint = new WorldPoint(3703, 3878, 0);

        check("K_PATCH north to a northern tunnel: use the hole", true,
            HerbiboarScript.shouldUseHole(kPatch, tunnel6));
        check("J_PATCH south to a southern tunnel: use the hole", true,
            HerbiboarScript.shouldUseHole(jPatch, tunnel2));
        check("the one spot-to-spot crossing is too far west: walk round", false,
            HerbiboarScript.shouldUseHole(iMushroom, kPatch));
        check("northern spot to northern tunnel: no crossing at all", false,
            HerbiboarScript.shouldUseHole(jPatch, tunnel6));
        check("the camp bank is a crossing from the island", true,
            HerbiboarScript.shouldUseHole(jPatch, campBank));
        check("returning to the island from the camp bank crosses back", true,
            HerbiboarScript.shouldUseHole(kPatch, returnPoint));
    }

    private static void aDispatchedClickIsWaitedOnExactlyOnce()
    {
        check("nothing changed, deadline not reached, same target: keep waiting", false,
            HerbiboarScript.dispatchResolved(false, false, false));
        check("the state it was meant to change changed: it worked", true,
            HerbiboarScript.dispatchResolved(true, false, false));
        check("no answer by the deadline: retry", true,
            HerbiboarScript.dispatchResolved(false, true, false));
        check("trail moved to a different target: the old click is moot", true,
            HerbiboarScript.dispatchResolved(false, false, true));
        check("progress and deadline together still resolve", true,
            HerbiboarScript.dispatchResolved(true, true, false));
        check("hit after the click, standing still 2 ticks: interrupted", true,
            HerbiboarScript.hitInterrupted(105, 100, 107, false));
        check("hit only 1 tick ago: the answer may still come", false,
            HerbiboarScript.hitInterrupted(105, 100, 106, false));
        check("hit before the click: not this click's problem", false,
            HerbiboarScript.hitInterrupted(99, 100, 110, false));
        check("still walking there after a hit: not interrupted", false,
            HerbiboarScript.hitInterrupted(105, 100, 110, true));
        check("never hit", false,
            HerbiboarScript.hitInterrupted(-1, 100, 110, false));
        check("still searching (animating) after a hit: no second click", false,
            HerbiboarScript.hitInterrupted(105, 100, 110, true));
    }

    /** Walking to the start rock and inspecting it does not abandon a trail. */
    private static void aResetResumesTheLiveTrailInsteadOfWalkingAway()
    {
        check("trail ended at a tunnel: go to the tunnel", HerbiboarState.TUNNEL,
            HerbiboarScript.resumeState(3, true));
        check("tunnel known even with no group left: still the tunnel", HerbiboarState.TUNNEL,
            HerbiboarScript.resumeState(3, false));
        check("trail in progress: keep following it", HerbiboarState.TRAIL,
            HerbiboarScript.resumeState(0, true));
        check("no trail at all: find a start", HerbiboarState.START,
            HerbiboarScript.resumeState(0, false));
    }

    private static void onlyComingBackInReadsTheTrailAgain()
    {
        check("out -> in re-reads", true, HerbiHunterPlugin.reenteredArea(false, true));
        check("in -> in does not", false, HerbiHunterPlugin.reenteredArea(true, true));
        check("out -> out does not", false, HerbiHunterPlugin.reenteredArea(false, false));
        check("in -> out does not", false, HerbiHunterPlugin.reenteredArea(true, false));
    }

    private static void theHoleIsClimbedOnlyWhenTheRouteJumpsIt()
    {
        java.util.List<WorldPoint> walkRound = java.util.Arrays.asList(
            new WorldPoint(3706, 3807, 0), new WorldPoint(3705, 3808, 0), new WorldPoint(3700, 3821, 0),
            new WorldPoint(3699, 3822, 0), new WorldPoint(3698, 3823, 0), new WorldPoint(3690, 3830, 0),
            new WorldPoint(3680, 3836, 0));
        java.util.List<WorldPoint> viaHole = java.util.Arrays.asList(
            new WorldPoint(3705, 3810, 0), new WorldPoint(3715, 3815, 0), new WorldPoint(3713, 3830, 0),
            new WorldPoint(3713, 3840, 0));
        // Steps away from the divide may be coarse; the divide itself is crossed a tile at a time on foot.
        check("mushroom start to the western muddy patch walks round", false,
            HerbiboarScript.pathJumpsHole(walkRound));
        check("K_PATCH north goes through the hole", true, HerbiboarScript.pathJumpsHole(viaHole));
        check("no path: no hole", false, HerbiboarScript.pathJumpsHole(java.util.Collections.emptyList()));
    }

    /** The client's stand-in clickbox is the whole canvas. */
    private static void onlyRealInViewClickboxesAreClicked()
    {
        check("an object's clickbox in view is clickable", true,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(200, 150, 40, 30), 4, 4, 512, 334));
        check("no clickbox at all is not", false,
            HerbiboarScript.insideViewport(null, 4, 4, 512, 334));
        check("the whole-canvas stand-in is not (random point or (1,1) click)", false,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(-1, -1, 765, 503), 4, 4, 512, 334));
        check("the stand-in shifted to 0,0 is not either", false,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(0, 0, 765, 503), 4, 4, 512, 334));
        check("half off the viewport edge is not", false,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(500, 150, 40, 30), 4, 4, 512, 334));
        check("under the side panels is not", false,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(600, 300, 30, 30), 4, 4, 512, 334));
        check("most of it in view at the bottom edge is (a player clicks what shows)", true,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(200, 320, 40, 21), 4, 4, 512, 334));
        check("a sliver at the edge is not", false,
            HerbiboarScript.insideViewport(new java.awt.Rectangle(200, 334, 40, 21), 4, 4, 512, 334));
        java.util.List<java.awt.Rectangle> out = HerbiboarScript.outsideView(4, 4, 512, 334);
        check("aims keep off the part below the view", true, HerbiboarScript.covered(220, 345, out));
        check("and stay free inside it", false, HerbiboarScript.covered(220, 330, out));
    }

    /** Resizable mode draws panels over the game view, so a clickbox can be "in view" and still covered. */
    private static void aClickIsOnlySentWhereTheGameWouldDoTheSameOnALeftClick()
    {
        check("top entry is our search on our object", true,
            HerbiboarScript.leftClickDoes("Search", 30539, "Search", 30539));
        check("case of the option does not matter", true,
            HerbiboarScript.leftClickDoes("search", 30539, "Search", 30539));
        check("over the minimap there is no such entry", false,
            HerbiboarScript.leftClickDoes("Cancel", 0, "Search", 30539));
        check("over the inventory the item's option is on top", false,
            HerbiboarScript.leftClickDoes("Drink", 12625, "Search", 30539));
        check("a crab in front takes the left click", false,
            HerbiboarScript.leftClickDoes("Attack", 7, "Search", 30539));
        check("the same option on another object is not ours", false,
            HerbiboarScript.leftClickDoes("Search", 30540, "Search", 30539));
        check("an empty menu is not", false,
            HerbiboarScript.leftClickDoes(null, 0, "Search", 30539));
        // The game's top entry on a start rock is "Inspect Rock".
        check("the trail action is what the game offers on a start rock", true,
            HerbiboarScript.leftClickDoes("Inspect", 30521, HerbiboarScript.TRAIL_ACTION, 30521));
        check("a blank side-panel area covers the world", true,
            HerbiboarScript.uiCovers(net.runelite.api.MenuAction.CANCEL));
        check("a tab button covers the world", true,
            HerbiboarScript.uiCovers(net.runelite.api.MenuAction.CC_OP));
        check("an NPC in front is the world, not a panel", false,
            HerbiboarScript.uiCovers(net.runelite.api.MenuAction.NPC_FIRST_OPTION));
        check("walk here is the world", false,
            HerbiboarScript.uiCovers(net.runelite.api.MenuAction.WALK));
    }

    private static void aStuckScriptGivesUpInsteadOfIdlingIntoALogout()
    {
        check("first stuck minute resumes", false, HerbiboarScript.giveUpWhenStuck(1));
        check("second stuck minute resumes", false, HerbiboarScript.giveUpWhenStuck(2));
        check("third stuck minute in a row gives up", true, HerbiboarScript.giveUpWhenStuck(3));
        check("and beyond", true, HerbiboarScript.giveUpWhenStuck(7));
        check("two failed bank or start-up tries: try again", false, HerbiboarScript.outOfTries(2));
        check("three failed bank or start-up tries in a row: give up", true, HerbiboarScript.outOfTries(3));
    }

    private static void aSlidingTargetIsLedByHowFarItMovesWhileTheCursorTravels()
    {
        java.awt.Point at = new java.awt.Point(300, 200);
        check("a still target is aimed at where it is", at,
            HerbiboarScript.lead(at, new java.awt.Point(300, 200), new java.awt.Point(300, 200), 50, 250));
        check("10px per 50ms, 250ms of travel: 50px ahead", new java.awt.Point(350, 190),
            HerbiboarScript.lead(at, new java.awt.Point(100, 100), new java.awt.Point(110, 98), 50, 250));
        check("a sample over no time leads nowhere", at,
            HerbiboarScript.lead(at, new java.awt.Point(100, 100), new java.awt.Point(140, 100), 0, 250));
        // NaturalMouse's durations are random (base..2x base): lead by the longest recent one so the cursor lands ahead.
        check("lead horizon is the longest recent move", 290L,
            HerbiboarScript.leadHorizonMs(new long[]{150, 290, 0, 0, 0, 0, 0, 0}, 2, 250));
        check("an outlier is clamped to twice the average", 500L,
            HerbiboarScript.leadHorizonMs(new long[]{150, 900, 0, 0, 0, 0, 0, 0}, 2, 250));
        check("no moves yet: the running average", 250L,
            HerbiboarScript.leadHorizonMs(new long[8], 0, 250));
        check("follow move led 462ms, moved in 203ms: watch until it arrives", 379L,
            HerbiboarScript.followWaitMs(462, 203));
        check("follow move not led: one frame margin", 120L,
            HerbiboarScript.followWaitMs(0, 210));
        // A blocker over the left 15px of a 20x10 spot: aim only at what it leaves showing, 3px clear of its edge.
        java.awt.Rectangle spot = new java.awt.Rectangle(100, 100, 20, 10);
        java.util.List<java.awt.Shape> avoid = java.util.List.of(
            HerbiboarScript.blockerZone(new java.awt.Rectangle(90, 95, 25, 20)));
        boolean allClear = true;
        for (int i = 0; i < 50; i++) {
            java.awt.Point p = HerbiboarScript.pointInside(spot, avoid);
            allClear &= p != null && p.x >= 118 && spot.contains(p);
        }
        check("visible part: always found, always 3px clear of the blocker", true, allClear);
        check("a blocker over all but its 2px edge: none clear by 3px, walk closer", true,
            HerbiboarScript.pointInside(spot, java.util.List.of(
                HerbiboarScript.blockerZone(new java.awt.Rectangle(90, 95, 28, 20)))) == null);
        check("just off the blocker's edge counts as on it", true,
            HerbiboarScript.covered(116, 105, avoid));
        check("spot 20 tiles off, 30 deg past the edge: turn a little", true, HerbiboarScript.smallTurnShows(20, true, -30, 45, 28));
        check("spot behind us: walk, never spin round", false, HerbiboarScript.smallTurnShows(20, true, 150, 45, 28));
        check("spot out of click range: walk", false, HerbiboarScript.smallTurnShows(35, true, 10, 45, 28));
        check("spot not drawn yet: walk", false, HerbiboarScript.smallTurnShows(20, false, 10, 45, 28));
        check("spot beside us: no turn needed", false, HerbiboarScript.smallTurnShows(2, true, 30, 45, 28));
        check("already faced but off the top: a turn shows nothing", false, HerbiboarScript.smallTurnShows(27, true, 0, 45, 28));
        check("diagonal slide, 17px off its line: within its width across, wait for it", true,
            HerbiboarScript.msUntilUnderCursor(new java.awt.Rectangle(0, 0, 32, 36), new java.awt.Point(16 + 19, 18 + 6), 0.08, -0.07, 600) >= 0);
        check("upward slide, 14px off a 32-wide box: wait for it", true,
            HerbiboarScript.msUntilUnderCursor(new java.awt.Rectangle(0, 0, 32, 27), new java.awt.Point(16 - 10, 13 - 33), 0.01, -0.11, 600) >= 0);
        check("width across a sideways slide is half its height", 15.0,
            HerbiboarScript.halfWidthAcross(new java.awt.Rectangle(0, 0, 40, 30), 0.2, 0));
        check("cursor behind a target sliding away: slid past", true,
            HerbiboarScript.slidPast(new java.awt.Rectangle(946, 183, 31, 11), new java.awt.Point(995, 184), -0.06, 0));
        check("cursor ahead of it, a little off its line: not slid past", false,
            HerbiboarScript.slidPast(new java.awt.Rectangle(946, 183, 31, 11), new java.awt.Point(930, 180), -0.06, 0));
        check("not sliding: never slid past", false,
            HerbiboarScript.slidPast(new java.awt.Rectangle(946, 183, 31, 11), new java.awt.Point(995, 184), 0, 0));
        java.awt.Rectangle box = new java.awt.Rectangle(100, 100, 30, 30); // centre 115,115, sliding right at 0.2 px/ms
        check("cursor ahead on its line: wait ~150ms for it, don't chase", 150L,
            HerbiboarScript.msUntilUnderCursor(box, new java.awt.Point(145, 118), 0.2, 0, 600));
        check("cursor behind it (it slid past): chase", -1L,
            HerbiboarScript.msUntilUnderCursor(box, new java.awt.Point(90, 115), 0.2, 0, 600));
        check("cursor ahead but off its line: chase", -1L,
            HerbiboarScript.msUntilUnderCursor(box, new java.awt.Point(145, 160), 0.2, 0, 600));
        check("too far ahead to wait for: chase", -1L,
            HerbiboarScript.msUntilUnderCursor(box, new java.awt.Point(400, 115), 0.2, 0, 600));
        check("not sliding: chase", -1L,
            HerbiboarScript.msUntilUnderCursor(box, new java.awt.Point(145, 115), 0, 0, 600));
        check("a jump bigger than any scroll (camera snap) is not led", at,
            HerbiboarScript.lead(at, new java.awt.Point(100, 100), new java.awt.Point(260, 100), 50, 250));
        check("raw yaws across the 0/16383 wrap are close", 4, HerbiboarScript.angleUnitsApart(16382, 2));
        check("raw yaws half a turn apart", 8192, HerbiboarScript.angleUnitsApart(0, 8192));
        check("raw yaw above one turn (live values like 10648) wraps", 0, HerbiboarScript.angleUnitsApart(10648, 10648 - 16384));
        java.util.List<net.runelite.api.coords.WorldPoint> route = java.util.Arrays.asList(
            new net.runelite.api.coords.WorldPoint(3700, 3870, 0), new net.runelite.api.coords.WorldPoint(3701, 3871, 0),
            new net.runelite.api.coords.WorldPoint(3702, 3872, 0), new net.runelite.api.coords.WorldPoint(3703, 3873, 0));
        check("route is drawn from where the player stands, walked part dropped", 1,
            HerbiHunterPlugin.routeAheadFrom(route, new net.runelite.api.coords.WorldPoint(3701, 3871, 0)));
        check("route is gone once the player reaches its end", -1,
            HerbiHunterPlugin.routeAheadFrom(route, new net.runelite.api.coords.WorldPoint(3703, 3874, 0)));
        check("no route, nothing drawn", -1,
            HerbiHunterPlugin.routeAheadFrom(java.util.Collections.emptyList(), new net.runelite.api.coords.WorldPoint(3702, 3872, 0)));
        check("a route we stay near is kept", true, HerbiboarScript.reusesRoute(true, 20, 2, 60_000));
        check("a route we left is asked for again", false, HerbiboarScript.reusesRoute(true, 20, 5, 100));
        check("arrived at the end of the route: kept", true, HerbiboarScript.reusesRoute(true, 20, -1, 60_000));
        check("a new target always gets a new route", false, HerbiboarScript.reusesRoute(false, 20, 0, 0));
        check("an empty route is not asked for again at once", true, HerbiboarScript.reusesRoute(true, 0, -1, 1_000));
        check("an empty route is asked for again after a while", false, HerbiboarScript.reusesRoute(true, 0, -1, 3_000));
    }

    private static void everyConfigOptionDoesWhatItSays()
    {
        HerbiboarConfig defaults = new HerbiboarConfig() { };
        HerbiboarConfig.RunEnergyOption stamina = HerbiboarConfig.RunEnergyOption.STAMINA_POTION;
        HerbiboarConfig.RunEnergyOption superEnergy = HerbiboarConfig.RunEnergyOption.SUPER_ENERGY_POTION;
        HerbiboarConfig.RunEnergyOption none = HerbiboarConfig.RunEnergyOption.NONE;

        check("energy item None never drinks", false, HerbiboarScript.shouldUseEnergyItem(none, true, false, 1, 100));
        check("super energy drinks at the threshold", true, HerbiboarScript.shouldUseEnergyItem(superEnergy, false, false, 50, 50));
        check("super energy waits above the threshold", false, HerbiboarScript.shouldUseEnergyItem(superEnergy, false, false, 51, 50));
        check("raising the threshold drinks sooner", true, HerbiboarScript.shouldUseEnergyItem(superEnergy, false, false, 70, 80));
        check("stamina, effect ended, keep-up ON: drinks at full energy", true,
            HerbiboarScript.shouldUseEnergyItem(stamina, true, false, 100, 50));
        check("stamina, effect ended, keep-up OFF: waits for the threshold", false,
            HerbiboarScript.shouldUseEnergyItem(stamina, false, false, 100, 50));
        check("stamina effect still running: no second dose, keep-up or not", false,
            HerbiboarScript.shouldUseEnergyItem(stamina, true, true, 60, 50));
        check("keep-up only applies to stamina", false, HerbiboarScript.shouldUseEnergyItem(superEnergy, true, false, 100, 50));

        check("herb sack ON and missing: setup incomplete", false, HerbiboarScript.setupOk(true, true, false, false, false));
        check("herb sack OFF: not needed", true, HerbiboarScript.setupOk(true, false, false, false, false));
        check("secateurs ON and missing: setup incomplete", false, HerbiboarScript.setupOk(true, false, false, true, false));
        check("secateurs OFF: not needed", true, HerbiboarScript.setupOk(true, false, false, false, false));
        check("out of the energy item: setup incomplete", false, HerbiboarScript.setupOk(false, false, false, false, false));

        check("restock ON, out of stamina: G.E. trip", true, HerbiboarScript.shouldRestock(true, false, stamina));
        check("restock OFF, out of stamina: no trip (logs out)", false, HerbiboarScript.shouldRestock(false, false, stamina));
        check("restock ON, potions still held: no trip", false, HerbiboarScript.shouldRestock(true, true, stamina));
        check("restock never buys strange fruit", false,
            HerbiboarScript.shouldRestock(true, false, HerbiboarConfig.RunEnergyOption.STRANGE_FRUIT));

        check("all drop options off: nothing dropped", true, HerbiboarScript.itemsToDrop(defaults).isEmpty());
        check("drop empty vials adds the vial", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.VIAL_EMPTY),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropEmptyVials() { return true; } }));
        check("drop guam adds only guam", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.UNIDENTIFIED_GUAM),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropGuam() { return true; } }));
        check("drop torstol adds only torstol", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.UNIDENTIFIED_TORSTOL),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropTorstol() { return true; } }));
        check("drop small fossil drops only the unidentified one", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.FOSSIL_SMALL_UNID),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropSmallFossil() { return true; } }));
        check("drop medium fossil drops only the unidentified one", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.FOSSIL_MEDIUM_UNID),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropMediumFossil() { return true; } }));
        check("drop large fossil drops only the unidentified one", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.FOSSIL_LARGE_UNID),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropLargeFossil() { return true; } }));
        check("drop rare fossil drops only the unidentified one", java.util.Collections.singletonList(net.runelite.api.gameval.ItemID.FOSSIL_RARE_UNID),
            HerbiboarScript.itemsToDrop(new HerbiboarConfig() { @Override public boolean dropRareFossil() { return true; } }));
        check("the unidentified fossil ids are the game's", java.util.Arrays.asList(21562, 21564, 21566, 21568),
            java.util.Arrays.asList(net.runelite.api.gameval.ItemID.FOSSIL_SMALL_UNID, net.runelite.api.gameval.ItemID.FOSSIL_MEDIUM_UNID,
                net.runelite.api.gameval.ItemID.FOSSIL_LARGE_UNID, net.runelite.api.gameval.ItemID.FOSSIL_RARE_UNID));
        int dropOptions = 0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (java.lang.reflect.Method m : HerbiboarConfig.class.getDeclaredMethods())
        {
            if (!m.getName().startsWith("drop") || m.getReturnType() != boolean.class) continue;
            dropOptions++;
            HerbiboarConfig only = (HerbiboarConfig) java.lang.reflect.Proxy.newProxyInstance(
                HerbiboarConfig.class.getClassLoader(), new Class<?>[]{HerbiboarConfig.class},
                (proxy, method, args) -> method.getName().equals(m.getName()) ? Boolean.TRUE
                    : method.getReturnType() == boolean.class ? Boolean.FALSE : null);
            java.util.List<Integer> ids = HerbiboarScript.itemsToDrop(only);
            check("drop option " + m.getName() + " drops something of its own", true, !ids.isEmpty() && seen.addAll(ids));
        }
        check("all 19 drop options are checked", 19, dropOptions);
    }

    private static void everyConfigOptionIsReadByThePlugin()
    {
        java.nio.file.Path dir = java.nio.file.Paths.get("src/main/java/net/runelite/client/plugins/microbot/herbihunter");
        StringBuilder code = new StringBuilder();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir))
        {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator)
            {
                if (!f.getFileName().toString().equals("HerbiboarConfig.java") && f.toString().endsWith(".java"))
                {
                    code.append(new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        catch (java.io.IOException e)
        {
            check("plugin sources readable from " + dir.toAbsolutePath(), true, false);
            return;
        }
        for (java.lang.reflect.Method m : HerbiboarConfig.class.getDeclaredMethods())
        {
            if (!m.isDefault() || m.getAnnotation(net.runelite.client.config.ConfigItem.class) == null) continue;
            check("config option " + m.getName() + " is used by the plugin", true, code.indexOf("." + m.getName() + "()") >= 0);
        }
    }

    private static void theConfigPanelReadsTheWayItWasDesigned()
    {
        java.util.TreeMap<Integer, String> sections = new java.util.TreeMap<>();
        for (java.lang.reflect.Field f : HerbiboarConfig.class.getDeclaredFields())
        {
            net.runelite.client.config.ConfigSection s = f.getAnnotation(net.runelite.client.config.ConfigSection.class);
            if (s != null) sections.put(s.position(), s.name());
        }
        java.util.List<String> order = new java.util.ArrayList<>(sections.values());
        net.runelite.client.config.ConfigInformation brief = HerbiboarConfig.class.getAnnotation(net.runelite.client.config.ConfigInformation.class);
        check("the panel opens with a brief", true, brief != null);
        check("the brief states the level requirements", true,
            brief != null && brief.value().contains("80 Hunter") && brief.value().contains("31 Herblore"));
        check("sections in set-up order, Overlay last with the drop lists above it",
            java.util.Arrays.asList("Setup", "Restock", "Drop fossils", "Drop herbs", "Overlay"), order);
        for (java.lang.reflect.Method m : HerbiboarConfig.class.getDeclaredMethods())
        {
            net.runelite.client.config.ConfigItem item = m.getAnnotation(net.runelite.client.config.ConfigItem.class);
            if (item == null) continue;
            String d = item.description().trim();
            check("option '" + item.name() + "' has a one-sentence-or-more tooltip ending in a period", true,
                d.length() > 10 && d.endsWith("."));
            check("option '" + item.name() + "' label fits the panel (<= 23 chars)", true, item.name().length() <= 23);
        }
    }

    private static void theInfoPanelShowsHonestNumbers()
    {
        check("no rates in the first minute", false, HerbiboarOverlay.ratesReady(40, 5));
        check("no rates from one early catch (the 2M XP/hr spike)", false, HerbiboarOverlay.ratesReady(120, 1));
        check("rates after 3 catches", true, HerbiboarOverlay.ratesReady(120, 3));
        check("rates after 5 minutes", true, HerbiboarOverlay.ratesReady(300, 0));
        check("small numbers in full", "9,999", HerbiboarOverlay.compact(9999));
        check("XP/hr in thousands", "124.3K", HerbiboarOverlay.compact(124_300));
        check("XP/hr in millions", "1.25M", HerbiboarOverlay.compact(1_250_000));
        check("time to level in hours", "1h 12m", HerbiboarOverlay.hoursMinutes(72 * 60_000L));
        check("time to level in minutes", "45m", HerbiboarOverlay.hoursMinutes(45 * 60_000L));
        check("level bar halfway", 50.0, HerbiboarOverlay.levelProgress(150, 100, 200));
        check("level bar never above 100", 100.0, HerbiboarOverlay.levelProgress(250, 100, 200));
        check("hunting reads green", HerbiboarOverlay.HUNTING, HerbiboarOverlay.statusColor(HerbiboarState.TRAIL));
        check("recovering reads red", HerbiboarOverlay.RECOVERING, HerbiboarOverlay.statusColor(HerbiboarState.RESET));
        check("banking reads blue, not green", HerbiboarOverlay.AWAY, HerbiboarOverlay.statusColor(HerbiboarState.BANK));
        check("status: searching names the spot", "Searching spot 3",
            HerbiboarOverlay.activityText(HerbiboarState.TRAIL, "Inspect WorldPoint(x=3694, y=3847, plane=0)", 3));
        check("status: running between spots", "Running to the next spot",
            HerbiboarOverlay.activityText(HerbiboarState.TRAIL, "closing on WorldPoint(x=3710, y=3877, plane=0)", 2));
        check("status: the hole", "Taking the hole", HerbiboarOverlay.activityText(HerbiboarState.TRAIL, "closing on the hole", 2));
        check("status: running to the tunnel", "Running to the tunnel",
            HerbiboarOverlay.activityText(HerbiboarState.TUNNEL, "walking toward WorldPoint(x=3681, y=3863, plane=0) until it is on screen", 5));
        check("status: attacking", "Attacking the tunnel",
            HerbiboarOverlay.activityText(HerbiboarState.TUNNEL, "Attack WorldPoint(x=3681, y=3863, plane=0)", 5));
        check("status: boar wait", "Waiting for the herbiboar",
            HerbiboarOverlay.activityText(HerbiboarState.TUNNEL, "waiting for the herbiboar at WorldPoint(x=3681, y=3863, plane=0)", 5));
        check("status: start rock", "Inspecting a start rock",
            HerbiboarOverlay.activityText(HerbiboarState.START, "Inspect WorldPoint(x=3686, y=3870, plane=0)", 0));
        check("status: nothing to wait on shows the state", "Banking", HerbiboarOverlay.activityText(HerbiboarState.BANK, null, 0));
        check("status: a lapse reads as a moment", "Taking a moment", HerbiboarOverlay.activityText(HerbiboarState.TRAIL, "taking a moment", 2));

        long t0 = 1_000_000_000L, ten = 600_000L;
        java.util.List<Long> times = java.util.Arrays.asList(t0 + 60_000, t0 + 120_000, t0 + ten + 30_000);
        int[] b = HerbiboarSession.buckets(times, t0, t0 + 2 * ten, 4, ten);
        check("trend: two windows before the start are blank", -1, b[0] + b[1] == -2 ? -1 : 0);
        check("trend: 2 catches then 1", "2,1", b[2] + "," + b[3]);

        check("no history, no record line", true, HerbiboarOverlay.historyLine(java.util.Collections.emptyList()) == null);
        HerbiboarSession done = new HerbiboarSession(t0);
        done.endMs = t0 + 60 * 60_000L;
        for (int k = 0; k < 61; k++) done.recordCatch(t0 + k * 50_000L);
        check("last session line", "Last 1h 0m · 61 caught",
            HerbiboarOverlay.historyLine(java.util.Collections.singletonList(done)));
        check("best rate line", "Best 61/hr", HerbiboarOverlay.bestLine(java.util.Collections.singletonList(done)));
        try
        {
            java.io.File dir = java.nio.file.Files.createTempDirectory("herbiboar-sessions").toFile();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            HerbiboarSession.saveFinished(dir, gson, done);
            HerbiboarSession running = new HerbiboarSession(t0 + 2 * 60 * 60_000L);
            running.recordCatch(t0 + 2 * 60 * 60_000L + 1000);
            HerbiboarSession.checkpoint(dir, gson, running);
            HerbiboarSession.recoverCheckpoint(dir, gson);
            java.util.List<HerbiboarSession> back = HerbiboarSession.loadHistory(dir, gson);
            check("history keeps every session, the crashed one included", 2, back.size());
            check("a finished session comes back intact", 61, back.get(0).caught);
            check("the crashed one is labelled", "client closed or crashed", back.get(1).stopReason);
            check("the checkpoint is gone once filed", false, HerbiboarSession.checkpointFile(dir).exists());
            // As if the delete after a good append had failed: the next start must not file it again.
            HerbiboarSession.checkpoint(dir, gson, running);
            HerbiboarSession.recoverCheckpoint(dir, gson);
            check("a checkpoint already in the history is not filed twice", 2, HerbiboarSession.loadHistory(dir, gson).size());
            check("and it is cleared", false, HerbiboarSession.checkpointFile(dir).exists());
            // A stop mid-append leaves half a line: the next record must start on its own line.
            java.nio.file.Files.write(HerbiboarSession.historyFile(dir).toPath(), "{\"startMs\":12".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
            HerbiboarSession later = new HerbiboarSession(t0 + 5 * 60 * 60_000L);
            later.recordCatch(t0 + 5 * 60 * 60_000L + 1000);
            HerbiboarSession.checkpoint(dir, gson, later);
            HerbiboarSession.recoverCheckpoint(dir, gson);
            java.util.List<HerbiboarSession> afterPartial = HerbiboarSession.loadHistory(dir, gson);
            check("a session recovered after a half-written line is kept", 3, afterPartial.size());
            check("and it is the recovered one", later.startMs, afterPartial.get(2).startMs);
        }
        catch (java.io.IOException e)
        {
            check("temp directory for the history round trip", true, false);
        }

        HerbiboarOverlay.Snapshot s = new HerbiboarOverlay.Snapshot();
        s.state = HerbiboarState.TRAIL;
        s.activity = "Searching spot 3";
        s.runTime = "1:42:17";
        s.caught = 104;
        s.perHour = "61";
        s.xp = "212,640";
        s.xpPerHour = "124.3K";
        s.level = 92;
        s.levelPct = 68;
        s.toLevel = "1h 12m";
        s.spark = new int[]{-1, -1, 9, 10, 11, 9, 10, 12, 10, 11, 9, 11};
        s.footer = HerbiboarOverlay.footerLine(HerbiboarConfig.PlayStyle.RELAXED, 14, 3);
        s.history = "Last 2h 5m · 126 caught";
        s.best = "Best 64/hr";
        for (boolean small : new boolean[]{false, true})
        {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(260, 230, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(new java.awt.Color(74, 88, 52));
            g.fillRect(0, 0, 260, 230);
            g.translate(10, 10);
            java.awt.Dimension d = HerbiboarOverlay.paint(g, s, small);
            g.dispose();
            check((small ? "compact" : "full") + " panel paints a sensible size", true, d.width > 60 && d.height > 15 && d.height < 220);
            try
            {
                javax.imageio.ImageIO.write(img.getSubimage(0, 0, Math.min(260, d.width + 20), Math.min(230, d.height + 20)), "png",
                    new java.io.File("build/herbiboar-panel-" + (small ? "compact" : "full") + ".png"));
            }
            catch (java.io.IOException ignored)
            {
            }
        }
    }

    private static void antibanPanelOverridesAreReported()
    {
        check("no override, no warning", true, HerbiboarScript.antibanOverride(false, false) == null);
        check("universal antiban is named", true, HerbiboarScript.antibanOverride(true, false).contains("Universal"));
        check("overwrite is named", true, HerbiboarScript.antibanOverride(false, true).contains("Overwrite"));
        String both = HerbiboarScript.antibanOverride(true, true);
        check("both are named", true, both.contains("Universal") && both.contains("Overwrite"));
        check("footer leads with the mode", "Relaxed · 14 doses left · 3 banks",
            HerbiboarOverlay.footerLine(HerbiboarConfig.PlayStyle.RELAXED, 14, 3));
        check("footer without a potion", "Focused · 1 bank", HerbiboarOverlay.footerLine(HerbiboarConfig.PlayStyle.FOCUSED, -1, 1));
        for (String r : new String[]{"taking a moment", "checking xp", "looking at skills", "looking around", "glancing away"})
            check("status shows " + r, true, !HerbiboarOverlay.activityText(HerbiboarState.TRAIL, r, 2)
                .equals(HerbiboarState.TRAIL.getDescription()));
        com.google.gson.Gson gson = new com.google.gson.Gson();
        HerbiboarSession s = new HerbiboarSession(1000);
        s.playStyle = "Focused";
        check("play style survives the session JSON", "Focused", gson.fromJson(gson.toJson(s), HerbiboarSession.class).playStyle);
        check("an old line without it loads", true, gson.fromJson("{\"startMs\":5,\"caught\":3}", HerbiboarSession.class).playStyle == null);
    }

    /** The panel palette is fixed: a restyle must not drift these. */
    private static void thePanelKeepsItsHouseColours()
    {
        check("frame colour", new java.awt.Color(105, 114, 107), HerbiboarOverlay.FRAME_INNER);
        check("trend colour", new java.awt.Color(101, 100, 77), HerbiboarOverlay.SPARK);
        check("meter track colour", new java.awt.Color(65, 84, 84), HerbiboarOverlay.TRACK);
    }

    /** The tile table is the cursor's head start; a refactor must not drop rows from it. */
    private static void theSeededTilesAreKept()
    {
        check("seeded tile rows", 9, HerbiboarScript.SEEDED_BOAR_TILES.size());
        check("seeded row (3705,3814)", new net.runelite.api.coords.WorldPoint(3707, 3801, 0),
            HerbiboarScript.SEEDED_BOAR_TILES.get(new net.runelite.api.coords.WorldPoint(3705, 3814, 0)));
        check("seeded row (3700,3777)", new net.runelite.api.coords.WorldPoint(3665, 3784, 0),
            HerbiboarScript.SEEDED_BOAR_TILES.get(new net.runelite.api.coords.WorldPoint(3700, 3777, 0)));
    }

    private static void switchingStyleKeepsThePersonality()
    {
        HerbiboarPace session = HerbiboarPace.relaxed(42L, 1_000L);
        HerbiboarConfig.PlayStyle f = HerbiboarConfig.PlayStyle.FOCUSED, r = HerbiboarConfig.PlayStyle.RELAXED;
        check("focused is the focused pace", false, HerbiboarScript.choose(f, session).isRelaxed());
        check("relaxed is the session pace", true, HerbiboarScript.choose(r, session) == session);
        HerbiboarScript.choose(f, session);
        check("relaxed again is the same instance", true, HerbiboarScript.choose(r, session) == session);
    }

    private static void noLapseWhileTheBoarIsOut()
    {
        check("lapse on the trail", true, HerbiboarScript.lapseAllowed(HerbiboarState.TRAIL, false, false));
        check("lapse at a start", true, HerbiboarScript.lapseAllowed(HerbiboarState.START, false, false));
        check("no lapse in the tunnel", false, HerbiboarScript.lapseAllowed(HerbiboarState.TUNNEL, false, false));
        check("no lapse harvesting", false, HerbiboarScript.lapseAllowed(HerbiboarState.HARVEST, false, false));
        check("no lapse with the boar out", false, HerbiboarScript.lapseAllowed(HerbiboarState.TRAIL, true, false));
        check("no lapse with a click pending", false, HerbiboarScript.lapseAllowed(HerbiboarState.TRAIL, false, true));
    }

    private static void distractionsStayInTheirMoments()
    {
        check("no distraction in the tunnel", false, HerbiboarScript.distractionAllowed(HerbiboarState.TUNNEL, false));
        check("no distraction harvesting", false, HerbiboarScript.distractionAllowed(HerbiboarState.HARVEST, false));
        check("no distraction with a boar out", false, HerbiboarScript.distractionAllowed(HerbiboarState.TRAIL, true));
        check("distraction at a start", true, HerbiboarScript.distractionAllowed(HerbiboarState.START, false));
        check("distraction at the bank", true, HerbiboarScript.distractionAllowed(HerbiboarState.BANK, false));
        HerbiboarPace f = HerbiboarPace.focused();
        java.util.Random rng = new java.util.Random(7);
        boolean quiet = true;
        for (int i = 0; i < 5000; i++)
        {
            quiet &= f.afterCatch(rng) == HerbiboarPace.AfterCatch.NONE && f.afkAfterBankMs(rng) == 0
                && !f.driftCameraDuringSearch(rng);
        }
        check("focused never distracts", true, quiet);
        for (long seed = 1; seed <= 20; seed++)
        {
            HerbiboarPace p = HerbiboarPace.relaxed(seed, 0);
            int xp = 0, tab = 0, drift = 0, n = 20000;
            for (int i = 0; i < n; i++)
            {
                HerbiboarPace.AfterCatch a = p.afterCatch(rng);
                if (a == HerbiboarPace.AfterCatch.XP_GLANCE) xp++;
                if (a == HerbiboarPace.AfterCatch.SKILL_TAB) tab++;
                if (p.driftCameraDuringSearch(rng)) drift++;
            }
            check("xp glance share seed " + seed, true, Math.abs(xp / (double) n - p.xpGlanceChance) < 0.02
                && p.xpGlanceChance >= 0.12 && p.xpGlanceChance <= 0.20);
            check("skill tab share seed " + seed, true, Math.abs(tab / (double) n - p.skillTabChance) < 0.02
                && p.skillTabChance >= 0.03 && p.skillTabChance <= 0.06);
            check("drift share seed " + seed, true, Math.abs(drift / (double) n - p.driftChance) < 0.02
                && p.driftChance >= 0.015 && p.driftChance <= 0.06);
        }
    }

    private static void afkNeverReachesTheWatchdog()
    {
        boolean turns = true;
        for (int d = 10; d <= 25; d++)
            for (int tol = HerbiboarScript.DRIFT_TOLERANCE_MIN; tol <= HerbiboarScript.DRIFT_TOLERANCE_MAX; tol++)
                turns &= HerbiboarScript.cameraKeyFor(d, tol) != 0 && HerbiboarScript.cameraKeyFor(-d, tol) != 0;
        check("search drift always presses a key", true, turns);
        afkChecks();
    }

    private static void travelStartsOnlyOffTheIsland()
    {
        WorldPoint ge = new WorldPoint(3167, 3487, 0), camp = new WorldPoint(3739, 3804, 0);
        WorldPoint hill = new WorldPoint(3763, 3869, 1), barge = new WorldPoint(3361, 3445, 0);
        check("G.E. is off the island", false, HerbiboarScript.onFossilIsland(ge));
        check("camp is on the island", true, HerbiboarScript.onFossilIsland(camp));
        check("House on the Hill upstairs is on the island", true, HerbiboarScript.onFossilIsland(hill));
        check("no location never travels", false, HerbiboarScript.needsTravel(HerbiboarState.START, null));
        check("START off the island travels", true, HerbiboarScript.needsTravel(HerbiboarState.START, ge));
        check("START on the island hunts", false, HerbiboarScript.needsTravel(HerbiboarState.START, camp));
        check("BANK off the island banks first", false, HerbiboarScript.needsTravel(HerbiboarState.BANK, ge));
        check("pendant from the G.E.", true, HerbiboarScript.pendantHelps(ge));
        check("no pendant beside the barge", false, HerbiboarScript.pendantHelps(barge));
        check("travel may still bank", true, HerbiboarScript.bankAllowedIn(HerbiboarState.TRAVEL));
    }

    private static void afkChecks()
    {
        java.util.Random rng = new java.util.Random(11);
        long max = 0;
        int afk = 0, n = 20000;
        HerbiboarPace p = HerbiboarPace.relaxed(5, 0);
        for (int i = 0; i < n; i++)
        {
            long ms = p.afkAfterBankMs(rng);
            max = Math.max(max, ms);
            if (ms > 0) afk++;
            if (ms > 0 && ms < 2000) max = Long.MAX_VALUE;
        }
        check("afk never above 15 s and never below 2 s", true, max <= 15_000);
        check("afk chance in range", true, afk / (double) n >= 0.28 && afk / (double) n <= 0.52);
        check("status: checking xp", "Checking XP", HerbiboarOverlay.activityText(HerbiboarState.START, "checking xp", 0));
        check("status: looking at skills", "Looking at skills", HerbiboarOverlay.activityText(HerbiboarState.START, "looking at skills", 0));
        check("status: looking around", "Looking around", HerbiboarOverlay.activityText(HerbiboarState.TRAIL, "looking around", 0));
    }

    private static void boarWaitDoesOneThingOnly()
    {
        HerbiboarScript.BoarWait p = HerbiboarScript.BoarWait.PRE_HOVER;
        check("focused pre-hovers on any roll", true, HerbiboarScript.boarWaitChoice(false, 0.99, 1.0) == p
                && HerbiboarScript.boarWaitChoice(false, 0.99, 0.5) == p);
        check("relaxed low roll pre-hovers", true, HerbiboarScript.boarWaitChoice(true, 0.2, 0.5) == p);
        check("relaxed mid roll glances away", true, HerbiboarScript.boarWaitChoice(true, 0.55, 0.5) == HerbiboarScript.BoarWait.MOUSE_AWAY);
        check("relaxed high roll does nothing", true, HerbiboarScript.boarWaitChoice(true, 0.9, 0.5) == HerbiboarScript.BoarWait.NOTHING);
        check("status: a glance reads as one", "Glancing away", HerbiboarOverlay.activityText(HerbiboarState.TUNNEL, "glancing away", 0));
    }

    private static void noTwoSessionsShareTheSameHabits()
    {
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean inRange = true;
        for (long seed = 1; seed <= 50; seed++)
        {
            HerbiboarHabits h = HerbiboarHabits.draw(seed);
            seen.add(h.describe());
            inRange &= h.turnComfortDeg >= 18 && h.turnComfortDeg < h.turnMaxDeg && h.turnMaxDeg <= 75
                && h.blockerRecall >= 0.6 && h.blockerRecall < 0.9 && h.impatience > 0 && h.impatience < 0.06
                && h.clickRangeTiles >= 26 && h.clickRangeTiles <= 31;
        }
        check("50 sessions draw 50 different habit sets", 50, seen.size());
        check("every habit stays in its sane range", true, inRange);

        HerbiboarHabits h = HerbiboarHabits.draw(7);
        java.util.Random r = new java.util.Random(7);
        int near = 0, mid = 0, far = 0;
        int midDeg = (h.turnComfortDeg + h.turnMaxDeg) / 2;
        for (int i = 0; i < 2000; i++)
        {
            if (h.turnsFor(h.turnComfortDeg, r)) near++;
            if (h.turnsFor(-midDeg, r)) mid++;
            if (h.turnsFor(h.turnMaxDeg, r)) far++;
        }
        check("within its comfort it always turns", 2000, near);
        check("halfway to its limit it turns about half the time, no hard line", true, mid > 700 && mid < 1300);
        check("at its limit it walks instead", 0, far);

        int soon = 0, minutes = 0, long_ = 0;
        for (int i = 0; i < 4000; i++)
        {
            long d = h.pitchFixDelayMs(r);
            if (d < 30_000) soon++;
            else if (d < 300_000) minutes++;
            else long_++;
            if (d < 3_000 || d > 900_000) inRange = false;
        }
        check("a flat camera is fixed at all three paces, never instantly", true,
            soon > 0 && minutes > 0 && long_ > 0 && inRange);

        java.util.Set<Integer> spotsBeforeTurn = new java.util.HashSet<>(), landed = new java.util.HashSet<>();
        int partial = 0, turns = 0, behindSpots = 0, neverSessions = 0, quickest = Integer.MAX_VALUE, leftOver = Integer.MIN_VALUE;
        double rarestRate = 0, busiestRate = Double.MAX_VALUE;
        boolean frontCalms = true;
        for (long seed = 1; seed <= 40; seed++)
        {
            HerbiboarHabits s = HerbiboarHabits.draw(seed);
            java.util.Random rr = new java.util.Random(seed * 31);
            double a = 0, bp = s.breakingPoint(rr);
            int turnsBefore = turns, behindBefore = behindSpots, behindSince = 0;
            for (int spot = 1, sinceTurn = 1; spot <= 300; spot++, sinceTurn++)
            {
                // About one in-reach spot in four is behind the camera.
                int off = rr.nextDouble() < 0.25 ? 170 : 10;
                if (off == 170) { behindSpots++; behindSince++; }
                a = s.annoyance(a, off, rr);
                if (off == 10 && a > 0 && s.annoyance(a, off, rr) >= a) frontCalms = false;
                if (a < bp) continue;
                spotsBeforeTurn.add(sinceTurn);
                quickest = Math.min(quickest, behindSince);
                behindSince = 0;
                int to = HerbiboarHabits.turnAroundTarget(90, off, s.turnMaxDeg, rr);
                landed.add(to);
                int left = Math.floorMod(to - (90 - off), 360);
                leftOver = Math.max(leftOver, Math.min(left, 360 - left) - s.turnMaxDeg);
                if (Math.abs(Math.floorMod(to - (90 - off), 360) - 0) > 30
                    && Math.abs(Math.floorMod(to - (90 - off), 360) - 360) > 30) partial++;
                turns++;
                a = 0;
                bp = s.breakingPoint(rr);
                sinceTurn = 0;
            }
            int sessionTurns = turns - turnsBefore;
            if (sessionTurns == 0) neverSessions++;
            else
            {
                double rate = (behindSpots - behindBefore) / (double) sessionTurns;
                rarestRate = Math.max(rarestRate, rate);
                busiestRate = Math.min(busiestRate, rate);
            }
        }
        check("a turn round comes after no fixed number of spots", true, spotsBeforeTurn.size() >= 5);
        check("a turn round lands at a spread of angles, not one", true, landed.size() >= Math.min(30, turns / 2));
        check("some sessions never turn round, most do", true, neverSessions > 0 && neverSessions < 20);
        check("sessions that turn round do so at very different rates, not one steady rate", true,
            rarestRate >= 2 * busiestRate);
        check("a turn round never comes on the first spot behind", true, quickest >= 2);
        check("some turns round stop part way, most do not", true, partial > 0 && partial < turns * 0.6);
        check("a part-way turn round never leaves the spot past the session's turn limit", true, leftOver <= 0);
        int[] t = {50, 40};
        net.runelite.api.MenuAction obj = net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION;
        check("a tunnel 10 tiles off is not our tunnel", false, HerbiboarScript.onOurTile(obj, 60, 40, t));
        check("a wide object a tile over is still ours", true, HerbiboarScript.onOurTile(obj, 51, 41, t));
        check("an NPC entry is never tile-checked", true,
            HerbiboarScript.onOurTile(net.runelite.api.MenuAction.NPC_FIRST_OPTION, 0, 0, t));
        check("an unknown tile never blocks a click", true, HerbiboarScript.onOurTile(obj, 99, 99, new int[0]));
        java.util.Set<Long> sackRates = new java.util.HashSet<>();
        boolean sackInRange = true;
        for (long seed = 1; seed <= 30; seed++)
        {
            double sf = HerbiboarHabits.draw(seed).sackFirst;
            sackRates.add(Math.round(sf * 100));
            sackInRange &= sf >= 0.15 && sf <= 0.85;
        }
        check("bank trips mix both sack orders, at a different rate each session", true, sackInRange && sackRates.size() >= 20);
        check("a spot in front lets the annoyance fade", true, frontCalms);
        check("a spot in front never turns us round on its own", 0.0, HerbiboarHabits.draw(3).annoyance(0, 10, r));
        check("Focused sessions differ in reaction too, all near 280", true,
            HerbiboarPace.focused(1).reactionMedian != HerbiboarPace.focused(2).reactionMedian
                && HerbiboarPace.focused(1).reactionMedian >= 250 && HerbiboarPace.focused(2).reactionMedian <= 310);
    }

    private static void thePaceModelBehaves()
    {
        java.util.Random r = new java.util.Random(7);
        long t0 = 1_000_000L;
        long hour = 3_600_000L;
        HerbiboarPace f = HerbiboarPace.focused();
        int min = Integer.MAX_VALUE;
        double sum = 0;
        boolean hoverAlways = true;
        boolean lapseNever = true;
        for (int i = 0; i < 2000; i++)
        {
            int ms = f.reactionMs(t0, r);
            min = Math.min(min, ms);
            sum += ms;
            hoverAlways &= f.preHover(r);
            lapseNever &= f.lapseMs(r) == 0;
        }
        check("focused reaction floor", true, min >= 140);
        check("focused reaction mean ~280", true, Math.abs(sum / 2000 - 280) <= 15);
        // Right-skewed, not piled on the floor.
        int atFloor = 0, above400 = 0, below180 = 0;
        for (int i = 0; i < 4000; i++)
        {
            int ms = f.reactionMs(t0, r);
            if (ms <= 150) atFloor++;
            if (ms >= 400) above400++;
            if (ms <= 180) below180++;
        }
        check("focused reactions do not pile up on the floor", true, atFloor < 40);
        check("focused reactions have a longer slow tail than fast tail", true, above400 > below180);
        java.util.Random fr = new java.util.Random(3);
        int stepAways = 0;
        HerbiboarPace fs = HerbiboarPace.focused(11);
        for (int i = 0; i < 2000; i++)
        {
            long a = fs.afkAfterBankMs(fr);
            if (a > 0) stepAways++;
            if (a > HerbiboarPace.AFK_MAX_MS) stepAways = -100_000;
        }
        check("a Focused session now and then steps away after banking, never long", true, stepAways > 60 && stepAways < 450);
        check("Focused sessions differ in how often they step away", true,
            HerbiboarPace.focused(1).afkChance != HerbiboarPace.focused(2).afkChance);
        check("focused always pre-hovers", true, hoverAlways);
        check("focused never lapses", true, lapseNever);
        check("focused has no fatigue", 1.0, f.fatigue(t0 + 5 * hour));
        check("focused pre-hover chance", 1.0, f.preHoverChance());
        check("focused is not relaxed", false, f.isRelaxed());

        java.util.Set<Double> medians = new java.util.HashSet<>();
        boolean inRange = true;
        for (long seed = 1; seed <= 50; seed++)
        {
            HerbiboarPace p = HerbiboarPace.relaxed(seed * 7919, t0);
            medians.add(p.reactionMedian);
            inRange &= p.reactionMedian >= 320 && p.reactionMedian <= 460
                && p.attention >= 0.6 && p.attention <= 0.95
                && p.preHoverChance() >= 0.35 && p.preHoverChance() <= 0.75
                && p.lapseChance >= 0.02 && p.lapseChance <= 0.06
                && p.fatiguePerHour >= 0.03 && p.fatiguePerHour <= 0.08;
        }
        check("relaxed personalities in range", true, inRange);
        check("relaxed medians are distinct", true, medians.size() >= 45);
        check("same seed, same personality", HerbiboarPace.relaxed(42, t0).describe(), HerbiboarPace.relaxed(42, t0).describe());

        HerbiboarPace p = HerbiboarPace.relaxed(99, t0);
        check("relaxed is relaxed", true, p.isRelaxed());
        check("relaxed fatigue starts at 1", 1.0, p.fatigue(t0));
        check("relaxed fatigue grows", true, p.fatigue(t0 + 2 * hour) > 1.0);
        check("relaxed fatigue caps", 1.35, p.fatigue(t0 + 20 * hour));
        int rmin = Integer.MAX_VALUE;
        for (int i = 0; i < 2000; i++)
        {
            rmin = Math.min(rmin, p.reactionMs(t0, r));
        }
        check("relaxed reaction floor", true, rmin >= 160);

        int lapses = 0;
        boolean lapseInRange = true;
        for (int i = 0; i < 10000; i++)
        {
            long l = p.lapseMs(r);
            if (l != 0)
            {
                lapses++;
                lapseInRange &= l >= 1500 && l <= 6000;
            }
        }
        check("relaxed lapse share matches its chance", true, Math.abs(lapses / 10000.0 - p.lapseChance) <= 0.01);
        check("relaxed lapses last 1.5-6 s", true, lapseInRange);
    }

    private static void theWayBackGoesStraightToTheNextTarget()
    {
        java.util.List<WorldPoint> ends = java.util.List.of(new WorldPoint(3693, 3798, 0), new WorldPoint(3702, 3808, 0));
        WorldPoint kLoc = HerbiboarSearchSpot.K_PATCH.getLocation();
        java.util.List<HerbiboarSearchSpot> cPath = java.util.List.of(HerbiboarSearchSpot.C_PATCH);
        java.util.List<HerbiboarSearchSpot> ckPath = java.util.List.of(HerbiboarSearchSpot.C_PATCH, HerbiboarSearchSpot.K_PATCH);
        check("ended trail -> its tunnel", ends.get(1), HerbiboarScript.nextTargetOf(2, cPath, ends));
        check("live trail -> the last (next) spot", kLoc, HerbiboarScript.nextTargetOf(0, ckPath, ends));
        check("no trail -> nothing", "null", String.valueOf(HerbiboarScript.nextTargetOf(0, java.util.List.of(), ends)));
        check("bad finish id -> next spot, no crash", kLoc,
                HerbiboarScript.nextTargetOf(9, java.util.List.of(HerbiboarSearchSpot.K_PATCH), ends));
        check("empty route is unreachable", -1, HerbiboarScript.routeCost(java.util.List.of()));
        check("null route is unreachable", -1, HerbiboarScript.routeCost(null));
        check("plain route costs its tiles", 3, HerbiboarScript.routeCost(java.util.List.of(
                new WorldPoint(3700, 3800, 0), new WorldPoint(3701, 3800, 0), new WorldPoint(3702, 3800, 0))));
        check("hole hop adds the climb", 2 + HerbiboarScript.HOLE_ROUTE_PENALTY, HerbiboarScript.routeCost(java.util.List.of(
                new WorldPoint(3716, 3815, 0), new WorldPoint(3713, 3830, 0))));
        WorldPoint mid = new WorldPoint(3686, 3870, 0), lep = new WorldPoint(3705, 3830, 0),
                camp = new WorldPoint(3704, 3810, 0), ghost = new WorldPoint(3695, 3800, 0), drift = new WorldPoint(3751, 3850, 0);
        java.util.List<WorldPoint> cands = java.util.List.of(mid, lep, camp, ghost, drift);
        java.util.Map<WorldPoint, Integer> costs = java.util.Map.of(mid, 95, lep, 41, camp, 37, ghost, 52, drift, 60);
        check("lowest route wins over straight line", camp, HerbiboarScript.nearestByRoute(cands, costs::get, null));
        check("skipped start is passed over", lep, HerbiboarScript.nearestByRoute(cands, costs::get, camp));
        java.util.Map<WorldPoint, Integer> onlyGhost = java.util.Map.of(mid, -1, lep, -1, camp, -1, ghost, 52, drift, -1);
        check("unreachable ones are ignored", ghost, HerbiboarScript.nearestByRoute(cands, onlyGhost::get, null));
        check("none reachable -> null (fallback)", "null", String.valueOf(HerbiboarScript.nearestByRoute(cands, c -> -1, null)));
        check("tie keeps list order", mid, HerbiboarScript.nearestByRoute(java.util.List.of(mid, lep), c -> 40, null));
        check("bank resumes a live trail, not START", HerbiboarState.TRAIL, HerbiboarScript.resumeState(0, true));
    }

    /** The timing lines must add up, or the numbers they give would point at the wrong wait. */
    private static void theStepTimerSplitsADelayIntoItsPhases()
    {
        HerbiboarStepTimer t = new HerbiboarStepTimer();
        check("nothing open closes to nothing", true, t.finish("click", 500) == null);
        t.landed(1000, "TRAIL");
        t.mark("seen", 1100);
        t.mark("react", 1400);
        t.mark("anim", 2300);
        t.mark("upkeep", 2350);
        t.mark("pass", 2500);
        t.mark("pass", 2600);
        t.mark("aim", 2900);
        check("a step splits into its phases",
            "[HB-T] TRAIL -> click total=2000 seen=100 react=300 anim=900 upkeep=50 pass=150 aim=400 press=100",
            t.finish("click", 3000));
        check("a closed step closes once", true, t.finish("click", 3100) == null);
        t.landed(4000, "TRAIL");
        t.landed(5000, "TUNNEL");
        check("a new step replaces an open one",
            "[HB-T] TUNNEL -> walk total=200 seen=0 react=0 anim=0 upkeep=0 pass=0 aim=0 press=200", t.finish("walk", 5200));
        t.landed(6000, "BOAR");
        t.reset();
        check("reset drops the open step", true, t.finish("click", 6100) == null);
    }

    /** fossil_grass_lush_* shares a tile with nine search spots; cached in a spot's place, a correct hover reads as a miss. */
    private static void upkeepOnlyHoldsTheNextClickWhenThereIsSomethingToDo()
    {
        check("nothing to do: no wait", false, HerbiboarScript.upkeepDue(true, false, false, false));
        check("inventory hidden: wait", true, HerbiboarScript.upkeepDue(false, false, false, false));
        check("something to drop: wait", true, HerbiboarScript.upkeepDue(true, true, false, false));
        check("closed sack: wait", true, HerbiboarScript.upkeepDue(true, false, true, false));
        check("drink due: wait", true, HerbiboarScript.upkeepDue(true, false, false, true));
    }

    private static void grassNeverReplacesASearchSpot()
    {
        check("a smelly mushroom is cached", true, HerbiboarSearchSpot.cacheable(30541));
        check("a muddy patch is cached", true, HerbiboarSearchSpot.cacheable(30543));
        check("long grass is not", false, HerbiboarSearchSpot.cacheable(30862));
        check("short grass is not", false, HerbiboarSearchSpot.cacheable(30863));
        check("sparse grass is not", false, HerbiboarSearchSpot.cacheable(30864));
    }

    /** The client picks the mouse profile from the activity intensity; herbiboar hunting is rated Low, the slow profile. */
    private static void focusedMovesTheMouseFast()
    {
        check("Focused uses the fast mouse profile",
            net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.HIGH, HerbiboarScript.mouseIntensity(false));
        check("Relaxed keeps the activity's own",
            net.runelite.client.plugins.microbot.util.antiban.enums.Activity.HUNTING_HERBIBOARS.getActivityIntensity(),
            HerbiboarScript.mouseIntensity(true));
    }

    private static void theLogWritesOnlyWhatTheSettingAllows()
    {
        final java.util.List<String> sunk = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final long[] clock = {1_000_000L};
        HerbiboarLog log = new HerbiboarLog((level, line) -> sunk.add(level + ":" + line), () -> "a3f9")
        {
            @Override
            long now()
            {
                return clock[0];
            }
        };
        HerbiboarConfig.LogDetail quiet = HerbiboarConfig.LogDetail.QUIET;
        HerbiboarConfig.LogDetail normal = HerbiboarConfig.LogDetail.NORMAL;
        HerbiboarConfig.LogDetail detailed = HerbiboarConfig.LogDetail.DETAILED;

        check("quiet hides events", false, HerbiboarLog.writes(quiet, 1));
        check("quiet keeps problems", true, HerbiboarLog.writes(quiet, 2));
        check("normal keeps events", true, HerbiboarLog.writes(normal, 1));
        check("normal hides detail", false, HerbiboarLog.writes(normal, 0));
        check("detailed keeps detail", true, HerbiboarLog.writes(detailed, 0));
        check("the setting reads as its name", "Detailed", detailed.toString());

        log.setDetail(detailed);
        log.start("1.2.0", "Focused");
        check("run start", "1:[HB-RUN] start v1.2.0 run=a3f9 style=Focused log=Detailed", sunk.get(0));
        check("catch line",
            "[HB-CATCH] #14 run=a3f9 52s steps=6 walks=2 slowest=3288ms TRAIL->click harvest=1625ms unanswered=0 misses=0",
            HerbiboarLog.catchLine(14, "a3f9", 52, 6, 2, 3288, "TRAIL->click", 1625, 0, 0));

        log.detail("CHAT", "{}", "100% {} C:\\x");
        check("text is written as given", "1:[HB-CHAT] 100% {} C:\\x", sunk.get(sunk.size() - 1));
        log.detail("CHAT", "50% done {}");
        check("a format with no args is the text", "1:[HB-CHAT] 50% done {}", sunk.get(sunk.size() - 1));
        log.problem("PROBLEM", "stuck at {}", 7);
        check("a problem is a warning", "2:[HB-PROBLEM] stuck at 7", sunk.get(sunk.size() - 1));

        log.setDetail(quiet);
        log.detail("WAIT", "x");
        check("hidden detail is debug", "0:[HB-WAIT] x", sunk.get(sunk.size() - 1));
        int before = sunk.size();
        log.event("BANK", "hidden");
        check("quiet writes no event", before, sunk.size());

        log.step("TRAIL", "walk", 900);
        log.step("TRAIL", "click", 3288);
        log.unanswered();
        log.miss();
        log.setDetail(normal);
        clock[0] += 52_000;
        log.caught(clock[0], 1625);
        check("counters ran while hidden",
            "1:[HB-CATCH] #1 run=a3f9 52s steps=2 walks=1 slowest=3288ms TRAIL->click harvest=1625ms unanswered=1 misses=1",
            sunk.get(sunk.size() - 1));

        for (int i = 0; i < 50; i++) log.event("RECOVER", "same");
        log.event("RECOVER", "other");
        check("repeats are counted, not rewritten", java.util.Arrays.asList("1:[HB-RECOVER] same", "1:[HB-RECOVER] same",
            "1:[HB-RECOVER] same", "1:[HB-RECOVER] same (repeated 47 more times)", "1:[HB-RECOVER] other"),
            new java.util.ArrayList<>(sunk.subList(sunk.size() - 5, sunk.size())));

        for (int i = 0; i < 9; i++) log.event("RECOVER", "tail");
        log.stop(12, 14, "stopped");
        check("stop flushes the repeat count", "1:[HB-RECOVER] tail (repeated 6 more times)", sunk.get(sunk.size() - 2));
        check("run stop", "1:[HB-RUN] stop run=a3f9 after 12m, 14 caught, stopped", sunk.get(sunk.size() - 1));

        log.setDetail(quiet);
        log.start("1.2.0", "Relaxed");
        check("quiet still marks the run", "1:[HB-RUN] start v1.2.0 run=a3f9 style=Relaxed log=Quiet", sunk.get(sunk.size() - 1));
        log.setDetail(normal);
        log.caught(clock[0], 0);
        check("a new run starts clean",
            "1:[HB-CATCH] #1 run=a3f9 0s steps=0 walks=0 slowest=0ms none harvest=0ms unanswered=0 misses=0",
            sunk.get(sunk.size() - 1));

        log.setDetail(normal);
        int unchanged = sunk.size();
        check("setting the same level writes nothing", unchanged, sunk.size());
        log.setDetail(quiet);
        check("a changed setting is always logged", "1:[HB-RUN] log=Quiet run=a3f9", sunk.get(sunk.size() - 1));
        log.setDetail(normal);
        check("and logged again on the way back", "1:[HB-RUN] log=Normal run=a3f9", sunk.get(sunk.size() - 1));

        sunk.clear();
        Thread[] two = new Thread[2];
        for (int t = 0; t < 2; t++)
        {
            final int id = t;
            two[t] = new Thread(() -> { for (int i = 0; i < 1000; i++) log.event("X", "t" + id + " line " + i); });
            two[t].start();
        }
        try
        {
            for (Thread t : two) t.join();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        check("two threads lose no line", 2000, new java.util.HashSet<>(sunk).size());
    }

    private static void aBreakIsNotWaitedForUnderAttack()
    {
        // The handler waits for "not in combat", and a crab keeps hitting an idle character, so the break never
        // begins; walking on to the next rock does not shake the crab either. Breaks only ever start at the bank.
        check("handler on and its timer has run out: a break is looming", true, HerbiboarScript.breakLooming(true, 0, false));
        check("handler on, two minutes to go: not yet", false, HerbiboarScript.breakLooming(true, 120, false));
        check("handler off: its timer means nothing", false, HerbiboarScript.breakLooming(false, 0, false));
        check("Break Handler V2 has asked: looming", true, HerbiboarScript.breakLooming(false, -1, true));
        check("a break looms: the catch goes to the bank", true, HerbiboarScript.bankForBreak(true, 600_000));
        check("we banked for one under ten minutes ago and it never came: hunt on", false, HerbiboarScript.bankForBreak(true, 599_999));
        check("nothing looming: no extra bank trip", false, HerbiboarScript.bankForBreak(false, 9_999_999));
        check("at the bank, trail finished, break asked for: it starts here", true, HerbiboarScript.breakStartsAtBank(false, true));
        check("at the bank mid-trail: the trail comes first", false, HerbiboarScript.breakStartsAtBank(true, true));
        check("at the bank, nothing asked for: back to hunting", false, HerbiboarScript.breakStartsAtBank(false, false));
    }

    private static void givingUpAndComingBackLookHuman()
    {
        // A stuck check on the shore with a crab attacking: the game refuses the logout, and the character is left
        // standing there with the plugin stopped.
        check("giving up out on the island: walk to the bank first", true, HerbiboarScript.walksToBankBeforeLogout(true, 60));
        check("already at the bank: log out here", false, HerbiboarScript.walksToBankBeforeLogout(true, 8));
        check("off the island (the G.E.): log out here", false, HerbiboarScript.walksToBankBeforeLogout(false, 60));
        // A disconnect must not be answered in the same second.
        check("the quickest return from a disconnect is five seconds", 5000L, HerbiboarScript.reloginWaitMs(0.0));
        check("the slowest is forty", 40000L, HerbiboarScript.reloginWaitMs(1.0));
        check("most are short: the middle roll waits under fourteen seconds", 13750L, HerbiboarScript.reloginWaitMs(0.5));
        check("no two rolls wait the same", true, HerbiboarScript.reloginWaitMs(0.31) != HerbiboarScript.reloginWaitMs(0.32));
    }

    private static void aMinimapStepOnlyEverWalks()
    {
        // A far step can land on the world map orb beside the minimap; the floating map then covers the view.
        check("the minimap offers Walk here: click", true, HerbiboarScript.minimapClickIsAWalk("Walk here"));
        // Over the minimap itself the menu holds only Cancel, and the click still walks.
        check("over the bare minimap the menu reads Cancel: click", true, HerbiboarScript.minimapClickIsAWalk("Cancel"));
        check("the cursor is over the world map orb: do not click", false,
            HerbiboarScript.minimapClickIsAWalk("Floating <col=ff9040>World Map</col>"));
        check("the cursor is over the compass: do not click", false, HerbiboarScript.minimapClickIsAWalk("Look North"));
        check("nothing known about the cursor yet: click, as before", true, HerbiboarScript.minimapClickIsAWalk(null));
    }

    private static void focusedFollowsTheTrailWithTheCamera()
    {
        check("spot behind the camera, in click range, roll under the odds: turn", true,
            HerbiboarScript.turnsToFollow(false, 14, 28, 0.8, 0.79));
        check("roll over the odds: walk, as before", false, HerbiboarScript.turnsToFollow(false, 14, 28, 0.8, 0.8));
        check("already on screen: nothing to turn for", false, HerbiboarScript.turnsToFollow(true, 14, 28, 0.8, 0.0));
        check("beyond click range: a turn would not make it clickable", false, HerbiboarScript.turnsToFollow(false, 29, 28, 0.8, 0.0));
        check("right beside us: no turn", false, HerbiboarScript.turnsToFollow(false, 2, 28, 0.8, 0.0));
        check("Relaxed (odds 0) never turns to follow", false, HerbiboarScript.turnsToFollow(false, 14, 28, 0.0, 0.0));
        for (long seed = 1; seed <= 200; seed++)
        {
            HerbiboarPace f = HerbiboarPace.focused(seed), r = HerbiboarPace.relaxed(seed, 0);
            if (f.followTurnChance < 0.55 || f.followTurnChance > 0.90 || r.followTurnChance != 0)
            {
                check("Focused follows 55-90%; Relaxed 0% (seed " + seed + ")", "in range",
                    f.followTurnChance + " vs " + r.followTurnChance);
            }
        }
        check("the Focused camera habits stay in their ranges over 200 sessions", true, true);
        check("and differ from session to session", true,
            HerbiboarPace.focused(1).followTurnChance != HerbiboarPace.focused(2).followTurnChance);
        check("an earlier draw is unchanged for a seed: Focused reaction",
            HerbiboarPace.focused(7).describe(), HerbiboarPace.focused(7).describe());
    }

    private static void theStrongerPotionsAreSupported()
    {
        HerbiboarConfig.RunEnergyOption extended = HerbiboarConfig.RunEnergyOption.EXTENDED_STAMINA_POTION;
        HerbiboarConfig.RunEnergyOption extreme = HerbiboarConfig.RunEnergyOption.EXTREME_ENERGY_POTION;
        check("extended stamina doses, 1 to 4", java.util.Arrays.asList(31647, 31644, 31641, 31638),
            java.util.Arrays.stream(HerbiboarScript.doseIds(extended)).boxed().collect(java.util.stream.Collectors.toList()));
        check("extreme energy doses, 1 to 4", java.util.Arrays.asList(31623, 31620, 31617, 31614),
            java.util.Arrays.stream(HerbiboarScript.doseIds(extreme)).boxed().collect(java.util.stream.Collectors.toList()));
        check("extended stamina restocks as its 4-dose", 31638, HerbiboarScript.fullDoseItemId(extended));
        check("extreme energy restocks as its 4-dose", 31614, HerbiboarScript.fullDoseItemId(extreme));
        check("stamina still restocks as its 4-dose", 12625, HerbiboarScript.fullDoseItemId(HerbiboarConfig.RunEnergyOption.STAMINA_POTION));
        check("strange fruit has no doses", 0, HerbiboarScript.doseIds(HerbiboarConfig.RunEnergyOption.STRANGE_FRUIT).length);
        check("extended stamina, effect running: no second dose", false,
            HerbiboarScript.shouldUseEnergyItem(extended, true, true, 50, 60));
        check("extended stamina, effect ended, keep-up on: drinks", true,
            HerbiboarScript.shouldUseEnergyItem(extended, true, false, 100, 50));
        check("extreme energy drinks at the threshold", true, HerbiboarScript.shouldUseEnergyItem(extreme, false, false, 50, 50));
        check("extreme energy waits above it", false, HerbiboarScript.shouldUseEnergyItem(extreme, false, false, 51, 50));
        check("out of extended stamina with restock on: buys more", true, HerbiboarScript.shouldRestock(true, false, extended));
        check("the log setting defaults to Normal", HerbiboarConfig.LogDetail.NORMAL, new HerbiboarConfig() { }.logDetail());

        final java.util.List<String> sunk = new java.util.ArrayList<>();
        HerbiboarLog log = new HerbiboarLog((level, line) -> sunk.add(line), () -> "a3f9");
        log.start("1.2.5", "Focused");
        log.stop(12, 14, "bank ran out of supplies");
        check("the stop line says why, so a Quiet log can", "[HB-RUN] stop run=a3f9 after 12m, 14 caught, bank ran out of supplies",
            sunk.get(sunk.size() - 1));
    }

    private static void breaksAreRespectedAndNotCounted()
    {
        check("disconnected, no break: log back in", true, HerbiboarScript.mayLogBackIn(true, false, false, true));
        check("the original handler's break: stay out", false, HerbiboarScript.mayLogBackIn(true, true, false, true));
        check("Break Handler V2's break: stay out", false, HerbiboarScript.mayLogBackIn(true, false, true, true));
        check("not at the login screen: nothing to do", false, HerbiboarScript.mayLogBackIn(false, false, false, true));
        check("just tried: wait before the next attempt", false, HerbiboarScript.mayLogBackIn(true, false, false, false));

        check("break asked for between trails: say ready", true, HerbiboarScript.readyForBreak(true, false, true));
        check("original handler, asked mid-trail: not ready yet", false, HerbiboarScript.readyForBreak(true, true, true));
        check("V2 asked mid-trail: ready now, V2 waits on the lock itself", true, HerbiboarScript.readyForBreak(true, true, false));
        check("nothing asked for: nothing to answer", false, HerbiboarScript.readyForBreak(false, false, false));
        check("a break is on, no trail live: pause", true, HerbiboarScript.pauseForBreak(true, false));
        check("a break is on mid-trail: finish the catch first", false, HerbiboarScript.pauseForBreak(true, true));
        check("no break: carry on", false, HerbiboarScript.pauseForBreak(false, false));
        check("level unknown at the start (login screen): never log out on it", false, HerbiboarScript.logOutAt99(true, 0, 99));

        HerbiboarSession early = new HerbiboarSession(0);
        early.pause(100);
        early.beginAt(1000);
        check("a pause from before the session began starts with it", 0L, early.durationMs(5000));
        early.resume(5000);
        check("and only the time after it counts", 1000L, early.durationMs(6000));

        check("XP per hour is over active time", 90_000, HerbiboarOverlay.xpPerHour(30_000, 1_200_000));
        check("no active time yet: no rate", 0, HerbiboarOverlay.xpPerHour(30_000, 0));
        check("micro breaks on, break handler on: may roll", true, HerbiboarScript.mayRollMicroBreak(true, true));
        check("micro breaks on, no break handler to end one: never", false, HerbiboarScript.mayRollMicroBreak(true, false));
        check("micro breaks off: never", false, HerbiboarScript.mayRollMicroBreak(false, true));
        check("at the bank, trail finished, roll under the odds: break", true, HerbiboarScript.microBreakNow(true, false, 0.05, 0.049));
        check("roll over the odds: carry on", false, HerbiboarScript.microBreakNow(true, false, 0.05, 0.05));
        check("a trail is still live: never, it would be left hanging", false, HerbiboarScript.microBreakNow(true, true, 0.05, 0.0));
        check("not allowed to roll: never", false, HerbiboarScript.microBreakNow(false, false, 0.05, 0.0));
        for (long seed = 1; seed <= 200; seed++)
        {
            double f = HerbiboarPace.focused(seed).microBreakChance;
            double r = HerbiboarPace.relaxed(seed, 0).microBreakChance;
            if (f < 0.01 || f > 0.04 || r < 0.04 || r > 0.10 || f >= r)
            {
                check("micro break odds per bank stop: Focused 1-4%, Relaxed 4-10% (seed " + seed + ")", "in range", f + " / " + r);
            }
        }
        check("micro break odds stay in their ranges over 200 sessions", true, true);
        check("the odds differ from session to session", true,
            HerbiboarPace.relaxed(1, 0).microBreakChance != HerbiboarPace.relaxed(2, 0).microBreakChance);

        HerbiboarSession s = new HerbiboarSession(0);
        s.caught = 30;
        s.pause(600_000);
        s.pause(700_000);
        check("time stops counting while paused", 600_000L, s.durationMs(900_000));
        check("resuming reports the pause, from its first moment", 600_000L, s.resume(1_200_000));
        check("resuming when not paused is nothing", 0L, s.resume(1_300_000));
        check("a finished pause stays out of the session time", 1_200_000L, s.durationMs(1_800_000));
        check("catches per hour is over active time", 90.0, s.catchesPerHour(1_800_000));

        com.google.gson.Gson gson = new com.google.gson.Gson();
        HerbiboarSession paused = new HerbiboarSession(1000);
        paused.pause(601_000);
        HerbiboarSession back = gson.fromJson(gson.toJson(paused), HerbiboarSession.class);
        back.endMs = 1_201_000;
        check("a pause still running is saved with the checkpoint", 600_000L, back.durationMs(0));
        back.resume(back.endMs);
        check("closing it at the end keeps the same active time", 600_000L, back.durationMs(0));
        check("and files it under break time", 600_000L, back.breakMs);

        final java.util.List<String> sunk = new java.util.ArrayList<>();
        final long[] clock = {5_000_000L};
        HerbiboarLog log = new HerbiboarLog((level, line) -> sunk.add(line), () -> "a3f9")
        {
            @Override
            long now()
            {
                return clock[0];
            }
        };
        log.start("1.2.3", "Focused");
        clock[0] += 100_000;
        log.paused(40_000);
        log.caught(clock[0], 1500);
        check("a break is not part of the catch's time", true, sunk.get(sunk.size() - 1).contains(" 60s steps="));
    }

    private static void reaching99LogsOutOnlyWhenAsked()
    {
        check("box off: 99 does nothing", false, HerbiboarScript.logOutAt99(false, 98, 99));
        check("box on, 98 -> 99: log out", true, HerbiboarScript.logOutAt99(true, 98, 99));
        check("box on, still 98: carry on", false, HerbiboarScript.logOutAt99(true, 97, 98));
        check("box on, already 99 at the start: carry on", false, HerbiboarScript.logOutAt99(true, 99, 99));
        check("the box is off by default", false, new HerbiboarConfig() { }.logoutAt99());
    }

    private static void theCatchLineAndTheNothingMessage()
    {
        HerbiboarStepTimer t = new HerbiboarStepTimer();
        t.landed(1000, "TRAIL");
        t.finish("click", 4288);
        check("the timer remembers the last step's total", 3288L, t.lastTotalMs());
        check("the timer remembers what the last step was", "TRAIL", t.lastWhat());
        check("trail moved on the message tick: not real", false, HerbiHunterPlugin.nothingWasReal(100, 100));
        check("trail moved the tick before: not real", false, HerbiHunterPlugin.nothingWasReal(100, 99));
        check("trail last moved long ago: real", true, HerbiHunterPlugin.nothingWasReal(100, 90));
        check("trail never moved: real", true, HerbiHunterPlugin.nothingWasReal(100, -1));
    }

    private static void tooLowALevelStopsTheRun()
    {
        check("80 Hunter and 31 Herblore can hunt", true, HerbiboarScript.levelShortfall(80, 31) == null);
        check("78 Hunter is refused", true, HerbiboarScript.levelShortfall(78, 99).contains("80 Hunter"));
        check("30 Herblore is refused", true, HerbiboarScript.levelShortfall(99, 30).contains("31 Herblore"));
    }

    private static void check(String what, Object expected, Object actual)
    {
        checks++;
        if (expected.equals(actual))
        {
            System.out.println("  ok   " + what);
        }
        else
        {
            failures++;
            System.out.println("  FAIL " + what + ": expected " + expected + " but got " + actual);
        }
    }
}
