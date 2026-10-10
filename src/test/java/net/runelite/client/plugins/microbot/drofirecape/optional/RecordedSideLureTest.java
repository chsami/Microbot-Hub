package net.runelite.client.plugins.microbot.drofirecape.optional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;

public class RecordedSideLureTest {
    private final Tile home=new Tile(62,60);
    private CollisionGrid grid()throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("pure-wave21-trap-collision.csv"),StandardCharsets.UTF_8))) {
            r.readLine();String line;while((line=r.readLine())!=null){String[] f=line.split(",");flags[Integer.parseInt(f[0])][Integer.parseInt(f[1])]=Integer.parseInt(f[2]);}
        }
        return new CollisionGrid(flags);
    }
    private Mob mob(int id,Kind kind,Tile p){return new Mob(id,kind,p,kind.size,10,10,-1,kind.protection,true);}
    private Snapshot scene(int tick,CollisionGrid g,Tile player,Tile big,Tile mage) {
        return new Snapshot(tick,player,g,List.of(mob(1,Kind.MELEER,big),mob(2,Kind.MAGER,mage)),100,true,5,Protection.NONE).atWave(47);
    }
    @Test public void eastShelfWaitsForObservedFollowerInsteadOfFixedThreeTickReturn()throws Exception {
        CollisionGrid g=grid();RecordedSideLure route=new RecordedSideLure();
        assertTrue(route.startFromNorth(scene(100,g,new Tile(70,77),new Tile(38,75),new Tile(66,46)),home));
        assertEquals(new Tile(69,60),route.plan(scene(100,g,new Tile(70,77),new Tile(38,75),new Tile(66,46))).destination());
        Snapshot arrived=scene(110,g,new Tile(69,60),new Tile(38,65),new Tile(70,47));
        assertEquals(arrived.player(),route.plan(arrived).destination());
        Snapshot waiting=scene(114,g,arrived.player(),new Tile(42,65),new Tile(70,47));
        assertEquals(waiting.player(),route.plan(waiting).destination());assertTrue(route.active());
        Snapshot released=scene(130,g,arrived.player(),new Tile(61,62),new Tile(70,47));
        assertEquals(new Tile(61,54),route.plan(released).destination());
        Snapshot wall=scene(138,g,new Tile(61,54),new Tile(63,62),new Tile(63,47));
        route.plan(wall);assertNull(route.plan(scene(139,g,wall.player(),new Tile(63,62),new Tile(63,47))));
        assertFalse(route.active());
        assertFalse("Same follower cannot restart this excursion",route.startFromNorth(scene(140,g,new Tile(70,77),new Tile(38,75),new Tile(66,46)),home));
    }
    @Test public void westernMageUsesNorthernPassageThenDragonFiringHold()throws Exception {
        CollisionGrid g=grid();RecordedSideLure route=new RecordedSideLure();
        assertTrue(route.startFromNorth(scene(100,g,new Tile(70,77),new Tile(59,59),new Tile(37,74)),home));
        assertEquals(new Tile(56,76),route.plan(scene(100,g,new Tile(70,77),new Tile(59,59),new Tile(37,74))).destination());
        assertEquals(new Tile(44,64),route.plan(scene(110,g,new Tile(56,76),new Tile(63,71),new Tile(37,74))).destination());
    }
    @Test public void alreadyShootableTrapAndNearbyBatPreventNewNorthernExcursion()throws Exception {
        CollisionGrid g=grid();RecordedSideLure route=new RecordedSideLure();
        Snapshot safe=scene(100,g,new Tile(61,55),new Tile(61,60),new Tile(70,47));
        assertFalse(route.startFromNorth(safe,home));
        Snapshot north=scene(100,g,new Tile(70,77),new Tile(38,75),new Tile(66,46));
        Snapshot bat=new Snapshot(100,north.player(),g,List.of(north.mobs().get(0),north.mobs().get(1),mob(3,Kind.BAT,new Tile(69,76))),100,true,5,Protection.NONE);
        assertFalse(route.startFromNorth(bat,home));
    }
    @Test public void finiteWaitAndFailedRouteCannotBecomeAnAdjacentRetryLoop()throws Exception {
        CollisionGrid g=grid();RecordedSideLure route=new RecordedSideLure();
        assertTrue(route.startFromNorth(scene(100,g,new Tile(70,77),new Tile(38,75),new Tile(66,46)),home));
        Snapshot hold=scene(110,g,new Tile(69,60),new Tile(38,65),new Tile(70,47));route.plan(hold);
        assertNull(route.plan(scene(142,g,hold.player(),new Tile(38,65),new Tile(70,47))));
        assertFalse(route.active());assertFalse(route.startFromNorth(scene(143,g,new Tile(70,77),new Tile(38,75),new Tile(66,46)),home));
    }
    @Test public void shortEastCornerKeepsCommittedWallEndpointWithRangerPresent()throws Exception {
        CollisionGrid g=grid();RecordedSideLure route=new RecordedSideLure();
        Snapshot start=new Snapshot(100,home,g,List.of(mob(1,Kind.MELEER,new Tile(60,60)),mob(2,Kind.RANGER,new Tile(61,47))),100,true,5,Protection.NONE).atWave(28);
        assertTrue(route.startCorner(start,home));assertEquals(new Tile(68,60),route.plan(start).destination());
        Snapshot elbow=new Snapshot(104,new Tile(68,60),g,start.mobs(),100,true,5,Protection.NONE).atWave(28);
        assertEquals(new Tile(61,53),route.plan(elbow).destination());
    }
    @Test public void manualUsableTrapEndsActiveExcursionWithoutLeavingWall()throws Exception {
        CollisionGrid g=grid();RecordedSideLure route=new RecordedSideLure();
        assertTrue(route.startFromNorth(scene(100,g,new Tile(70,77),new Tile(38,75),new Tile(66,46)),home));
        Snapshot trapped=new Snapshot(105,new Tile(61,54),g,List.of(
            mob(1,Kind.MELEER,new Tile(61,60)),mob(2,Kind.MAGER,new Tile(63,47)),
            mob(3,Kind.BABY,new Tile(61,52))),100,true,5,Protection.NONE).atWave(47);
        assertNotNull(PureCombatPolicy.establishedMeleeTrap(trapped));
        assertNotNull(PureCombatPolicy.preferredShot(trapped,Protection.MAGIC));
        assertNull(route.plan(trapped));assertFalse(route.active());
    }
    @Test public void southTrappedMeleeGetsRecordedFiringApproachInsteadOfNewPull()throws Exception {
        CollisionGrid g=grid();Mob big=mob(1,Kind.MELEER,new Tile(63,48));
        Snapshot s=new Snapshot(100,home,g,List.of(big),100,true,5,Protection.NONE).atWave(46);
        assertTrue(CaveSafety.trapped(s,big));assertFalse(CombatPlanner.playerCanAttack(s,s.player(),big));
        Plan p=PureCombatPolicy.approach(s,big,home,"Recorded south cleanup");
        assertNotNull(p);assertEquals("Approach recorded rock wall without releasing trapped melee",p.reason());
        assertTrue(List.of(new Tile(61,53),new Tile(61,51)).contains(p.destination()));
        assertTrue(CombatPlanner.playerCanAttack(s,p.destination(),big));
    }

    @Test public void pureControllerHandsNorthernPullToRecordedEastShelf()throws Exception {
        CollisionGrid g=grid();PureLureController lure=new PureLureController();
        Snapshot start=scene(100,g,home,new Tile(28,68),new Tile(53,33));
        Plan pull=lure.decide(start,new PureCombatPlanner(),-1,List.of(home),0,home,home.add(8,17),null,home.add(-1,0));
        assertEquals(home.add(8,17),pull.destination());
        Snapshot north=scene(110,g,home.add(8,17),new Tile(38,75),new Tile(66,46));
        lure.decide(north,new PureCombatPlanner(),-1,List.of(home),0,home,home.add(8,17),null,home.add(-1,0));
        Snapshot settled=scene(113,g,north.player(),new Tile(38,75),new Tile(66,46));
        Plan side=lure.decide(settled,new PureCombatPlanner(),-1,List.of(home),0,home,home.add(8,17),null,home.add(-1,0));
        assertEquals(new Tile(69,60),side.destination());assertTrue(lure.hasPendingReturn());
        lure.recover();assertFalse(lure.hasPendingReturn());
    }

}
