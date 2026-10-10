/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.CollisionGrid;
import net.runelite.client.plugins.microbot.drofirecape.core.CombatPlanner;
import net.runelite.client.plugins.microbot.drofirecape.core.LureController;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The live failure was a delayed outward click after EW had reported failure. */
public class LureReturnTest {
    private final Tile main=new Tile(54,36),peek=new Tile(52,36);
    private final CollisionGrid open=new CollisionGrid(new int[104][104]);

    private Mob mob(int index,Kind kind,Tile tile) {
        return new Mob(index,kind,tile,kind.size,10,10,-1,kind.protection,true);
    }
    private Snapshot state(int tick,Tile player,CollisionGrid grid,Mob... mobs) {
        return new Snapshot(tick,player,grid,List.of(mobs),100,true,7,Protection.NONE);
    }
    private Plan decide(LureController lure,Snapshot state) {
        return lure.decide(state,new CombatPlanner(),-1,List.of(main,peek,main),4,main,null,null,peek);
    }
    private LureController startedPeek(CollisionGrid grid,Mob blob) {
        LureController lure=new LureController();
        decide(lure,state(1,main,grid,blob));
        assertEquals(peek,decide(lure,state(4,main,grid,blob)).destination());
        assertTrue(lure.hasPendingReturn());
        return lure;
    }

    @Test public void delayedOutwardArrivalAfterFailureStillReturnsBeforeShooting() {
        Mob approaching=mob(59976,Kind.BLOB,new Tile(41,25));
        LureController lure=startedPeek(open,approaching);
        lure.movementFailed();
        // Latest live log: failure at cover, cover snapshot, then queued step lands.
        decide(lure,state(168,main,open,mob(59976,Kind.BLOB,new Tile(48,32))));
        assertTrue(lure.hasPendingReturn());
        Plan returned=decide(lure,state(169,peek,open,mob(59976,Kind.BLOB,new Tile(49,33))));
        assertEquals(main,returned.destination());
        assertEquals(-1,returned.targetIndex());
    }

    @Test public void arrivalAtPeekWaitsOneSnapshotBeforeReturningEvenWithANewLegalTarget() {
        LureController lure=startedPeek(open,mob(9,Kind.BLOB,main.add(-15,0)));
        assertEquals(peek,decide(lure,state(5,peek,open,mob(9,Kind.BLOB,peek.add(-5,0)))).destination());
        Plan returned=decide(lure,state(6,peek,open,mob(9,Kind.BLOB,peek.add(-5,0))));
        assertEquals(main,returned.destination());
        assertEquals(-1,returned.targetIndex());
    }

    @Test public void failedReturnAndOldTimeoutDoNotBecomePermanentExposedCombat() {
        LureController lure=startedPeek(open,mob(9,Kind.BLOB,main.add(-15,0)));
        decide(lure,state(5,peek,open,mob(9,Kind.BLOB,peek.add(-5,0))));
        lure.movementFailed();
        for(int tick:new int[]{6,40,1000}) {
            Plan returned=decide(lure,state(tick,peek,open,mob(9,Kind.BLOB,peek.add(-5,0))));
            assertEquals(main,returned.destination());
            assertEquals(-1,returned.targetIndex());
            assertTrue(lure.hasPendingReturn());
        }
    }

    @Test public void urgentBatShotDoesNotDiscardReturnForNextDecision() {
        Mob blob=mob(9,Kind.BLOB,main.add(-15,0));
        LureController lure=startedPeek(open,blob);
        Plan shot=decide(lure,state(5,peek,open,blob,mob(1,Kind.BAT,peek.add(1,0))));
        assertEquals(1,shot.targetIndex());
        assertTrue(lure.hasPendingReturn());
        assertEquals(peek,decide(lure,state(6,peek,open,mob(9,Kind.BLOB,peek.add(-5,0)))).destination());
        Plan returned=decide(lure,state(7,peek,open,mob(9,Kind.BLOB,peek.add(-5,0))));
        assertEquals(main,returned.destination());
        assertEquals(-1,returned.targetIndex());
    }

    @Test public void rebaseAndEmptyNpcFrameRetainTheSameReturnDestination() {
        LureController lure=startedPeek(open,mob(9,Kind.BLOB,main.add(-15,0)));
        lure.rebase(8,8);
        Snapshot empty=state(5,peek.add(8,8),open);
        Plan returned=lure.finishReturn(empty);
        assertEquals(main.add(8,8),returned.destination());
        assertTrue(lure.hasPendingReturn());
    }

    private CollisionGrid corner() {
        int[][] flags=new int[104][104];
        flags[54][34]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        flags[55][34]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        flags[54][35]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        return new CollisionGrid(flags);
    }

    @Test public void trappedBatUsesOneRecordedStepAndWaitsForReleaseOrShortTimeout() {
        CollisionGrid grid=corner();Mob bat=mob(1,Kind.BAT,new Tile(54,33));
        LureController lure=new LureController();
        Plan p=null;
        for(int tick=1;tick<=7;tick++)p=decide(lure,state(tick,main,grid,bat));
        Tile oneStep=new Tile(53,36);
        assertEquals(oneStep,p.destination());
        assertTrue(lure.hasPendingReturn());
        assertEquals(oneStep,decide(lure,state(8,oneStep,grid,bat)).destination());
        Plan returned=decide(lure,state(10,oneStep,grid,bat));
        assertEquals(main,returned.destination());
        assertEquals(-1,returned.targetIndex());
    }

    @Test public void movingBlobCannotCutTheTwoTileExcursionShort() {
        CollisionGrid grid=corner();Mob blob=mob(9,Kind.BLOB,new Tile(54,32));
        LureController lure=startedPeek(grid,blob);
        Mob moved=mob(9,Kind.BLOB,new Tile(52,33));
        Plan outward=decide(lure,state(5,new Tile(53,36),grid,moved));
        assertEquals(peek,outward.destination());assertEquals(-1,outward.targetIndex());
        assertEquals(peek,decide(lure,state(6,peek,grid,moved)).destination());
        Plan returned=decide(lure,state(7,peek,grid,moved));
        assertEquals(main,returned.destination());
        assertEquals(-1,returned.targetIndex());
    }

    @Test public void stationaryBlobWaitsPastOldTimerDespiteAnotherVisibleMovingNpc() {
        CollisionGrid grid=corner();Mob blob=mob(9,Kind.BLOB,new Tile(54,32));
        LureController lure=startedPeek(grid,blob);
        for(int tick=5;tick<=8;tick++) {
            Plan waiting=decide(lure,state(tick,peek,grid,blob,mob(3,Kind.RANGER,new Tile(54,40+tick%2))));
            assertEquals(peek,waiting.destination());assertEquals(-1,waiting.targetIndex());
            assertTrue(waiting.reason().contains("observed medium blob movement"));
        }
        Plan returned=decide(lure,state(9,peek,grid,mob(9,Kind.BLOB,new Tile(52,33))));
        assertEquals(main,returned.destination());assertEquals(-1,returned.targetIndex());
    }

    @Test public void liveItalyBlobLureUsesTwoTilesAndObservedMovement() {
        CollisionGrid grid=corner();Mob blob=mob(9,Kind.BLOB,new Tile(54,32));
        LureController lure=new LureController();
        lure.decide(state(1,main,grid,blob),new CombatPlanner(),-1,List.of(main),4,main,null,null,peek);
        Plan outward=lure.decide(state(4,main,grid,blob),new CombatPlanner(),-1,List.of(main),4,main,null,null,peek);
        assertEquals(peek,outward.destination());
        assertEquals(peek,decide(lure,state(5,peek,grid,blob)).destination());
        assertEquals(main,decide(lure,state(6,peek,grid,mob(9,Kind.BLOB,new Tile(52,33)))).destination());
    }

    @Test public void watchdogBlobRetryAlsoWaitsAtItsTwoTileDestination() {
        CollisionGrid grid=corner();Mob blob=mob(9,Kind.BLOB,new Tile(54,32));
        LureController lure=new LureController();lure.recover();
        Plan retry=lure.recoverRecorded(state(100,main,grid,blob),main,peek);
        assertNotNull(retry);assertEquals(peek,retry.destination());
        assertEquals(peek,decide(lure,state(101,new Tile(53,36),grid,blob)).destination());
        assertEquals(peek,decide(lure,state(102,peek,grid,blob)).destination());
        assertEquals(main,decide(lure,state(103,peek,grid,mob(9,Kind.BLOB,new Tile(52,33)))).destination());
    }

    @Test public void unmovingBlobEventuallyAbortsToCoverWithoutCallingItReleased() {
        CollisionGrid grid=corner();Mob blob=mob(9,Kind.BLOB,new Tile(54,32));
        LureController lure=startedPeek(grid,blob);
        for(int tick=5;tick<=10;tick++)assertEquals(peek,decide(lure,state(tick,peek,grid,blob)).destination());
        Plan abort=decide(lure,state(11,peek,grid,blob));
        assertEquals(main,abort.destination());assertTrue(abort.reason().contains("stalled"));
        assertEquals(-1,abort.targetIndex());assertTrue(lure.hasPendingReturn());
    }

    @Test public void sceneRebaseDoesNotInventBlobMovement() {
        CollisionGrid grid=corner();Mob blob=mob(9,Kind.BLOB,new Tile(54,32));
        LureController lure=startedPeek(grid,blob);lure.rebase(8,8);
        assertEquals(peek.add(8,8),decide(lure,state(5,peek.add(8,8),open,mob(9,Kind.BLOB,new Tile(62,40)))).destination());
        assertEquals(main.add(8,8),decide(lure,state(6,peek.add(8,8),open,mob(9,Kind.BLOB,new Tile(61,40)))).destination());
    }

    @Test public void returnIsCompleteOnlyAfterStableCoverSnapshots() {
        LureController lure=startedPeek(open,mob(9,Kind.BLOB,main.add(-15,0)));
        lure.finishReturn(state(5,peek,open));
        for(int tick=6;tick<9;tick++) {
            lure.finishReturn(state(tick,main,open));
            assertTrue(lure.hasPendingReturn());
        }
        lure.finishReturn(state(9,main,open));
        assertFalse(lure.hasPendingReturn());
    }
    @Test public void newlyVisibleBatAtPeekDoesNotCancelTheStepBack() {
        CollisionGrid grid=corner();Mob trapped=mob(1,Kind.BAT,new Tile(54,33));
        LureController lure=new LureController();
        for(int tick=1;tick<=7;tick++)decide(lure,state(tick,main,grid,trapped));
        Tile oneStep=new Tile(53,36);
        Plan returning=decide(lure,state(8,oneStep,open,mob(1,Kind.BAT,new Tile(51,33))));
        assertEquals(main,returning.destination());assertEquals(-1,returning.targetIndex());
    }
    @Test public void genericItalyManeuverWaitsForBlobToReachRockBeforePeeking() {
        LureController lure=new LureController();Mob blob=mob(9,Kind.BLOB,new Tile(30,20));
        for(int tick=1;tick<=4;tick++) {
            Plan p=lure.decide(state(tick,main,open,blob),new CombatPlanner(),-1,List.of(main),4,main,null,null,peek);
            assertEquals(main,p.destination());assertFalse(lure.hasPendingReturn());
        }
    }

    @Test public void watchdogRetriesOneWestStepEvenAfterEarlierExcursionWasExhausted() {
        CollisionGrid grid=corner();Mob bat=mob(1,Kind.BAT,new Tile(54,33));
        LureController lure=new LureController();lure.recover();
        Plan retry=lure.recoverRecorded(state(100,main,grid,bat),main,peek);
        assertNotNull(retry);assertEquals(new Tile(53,36),retry.destination());
        assertTrue(lure.hasPendingReturn());
        assertEquals(new Tile(53,36),decide(lure,state(101,new Tile(53,36),grid,bat)).destination());
        Plan returned=decide(lure,state(103,new Tile(53,36),grid,bat));
        assertEquals(main,returned.destination());assertEquals(-1,returned.targetIndex());
    }
    @Test public void repeatedMovementFailuresAndWatchdogNeverDiscardOwedCover() {
        LureController lure=startedPeek(open,mob(9,Kind.BLOB,main.add(-15,0)));
        for(int i=0;i<5;i++)lure.movementFailed();
        lure.recover();assertTrue(lure.hasPendingReturn());
        Plan recovery=lure.recoverRecorded(state(100,peek,open),main,peek);
        assertEquals(main,recovery.destination());assertEquals(-1,recovery.targetIndex());
    }
    @Test public void watchdogLeavesLegalBatShotAndJadToExistingCombat() {
        LureController lure=new LureController();
        assertNull(lure.recoverRecorded(state(100,main,open,mob(1,Kind.BAT,main.add(-3,0))),main,peek));
        assertNull(lure.recoverRecorded(state(100,main,corner(),mob(2,Kind.JAD,new Tile(54,33))),main,peek));
        assertFalse(lure.hasPendingReturn());
    }

}
