package net.runelite.client.plugins.microbot.drofirecape;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Replays the supplied south/north demonstrations and successful/failed mage cover. */
public class RecordedMeleeTrapTest {
    private final Tile home=new Tile(62,60),north=home.add(8,17);
    private BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8));
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private List<Snapshot> recorded(String data,String collision) throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader r=resource(collision+"-collision.csv")) {
            r.readLine();String line;while((line=r.readLine())!=null) {
                String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);
            }
        }
        List<Snapshot> frames=new ArrayList<>();CollisionGrid grid=new CollisionGrid(flags);
        try(Reader r=resource(data+".json")) {
            for(JsonElement e:new JsonParser().parse(r).getAsJsonArray()) {
                JsonObject f=e.getAsJsonObject();List<Mob> mobs=new ArrayList<>();
                for(JsonElement value:f.getAsJsonArray("mobs")) {
                    JsonObject m=value.getAsJsonObject();mobs.add(new Mob(m.get("index").getAsInt(),
                        Kind.valueOf(m.get("kind").getAsString()),tile(m.getAsJsonArray("tile")),m.get("size").getAsInt(),
                        m.get("healthRatio").getAsInt(),m.get("healthScale").getAsInt(),m.get("attackTick").getAsInt(),
                        Protection.valueOf(m.get("style").getAsString()),m.get("attackingPlayer").getAsBoolean()));
                }
                frames.add(new Snapshot(f.get("tick").getAsInt(),tile(f.getAsJsonArray("player")),grid,mobs,
                    100,f.get("running").getAsBoolean(),f.get("range").getAsInt(),Protection.NONE));
            }
        }
        return frames;
    }
    private Snapshot at(Snapshot s,int tick,Tile player,List<Mob> mobs) {
        return new Snapshot(tick,player,s.grid(),mobs,100,true,7,Protection.NONE);
    }
    private Snapshot frame(String data,String collision,int tick)throws Exception {return recorded(data,collision).stream().filter(s->s.tick()==tick).findFirst().get();}
    private Plan decide(LureController l,Snapshot s,int target) {
        return l.decide(s,new CombatPlanner(),target,List.of(home),0,home,north,null,home.add(-2,0),home.add(-1,-5));
    }
    @Test public void southRecordingUsesFourTilesAndWaitsForReleaseBeforeReturning()throws Exception {
        List<Snapshot> demo=recorded("melee-demo-south","melee-loop");RecordedMeleeTrap trap=new RecordedMeleeTrap();
        assertTrue(trap.start(demo.get(0),home,-1));
        for(Snapshot s:demo)if(s.tick()<=646) {
            Plan p=trap.plan(s);
            if(s.tick()<=639){assertNotNull(p);assertEquals("tick "+s.tick(),home.add(-4,0),p.destination());}
            if(s.tick()==640)assertEquals(home,p.destination());
        }
        assertFalse(trap.active());Snapshot end=frame("melee-demo-south","melee-loop",650);
        Mob big=end.mobs().get(0);assertTrue(CaveSafety.trapped(end,big));assertTrue(CombatPlanner.playerCanAttack(end,end.player(),big));
    }
    @Test public void northRecordingCommitsElbowNorthThenWallWithoutNeighbourRetries()throws Exception {
        List<Snapshot> demo=recorded("melee-demo-north","melee-loop");RecordedMeleeTrap trap=new RecordedMeleeTrap();
        Snapshot start=frame("melee-demo-north","melee-loop",485);assertTrue(trap.start(start,home,-1));
        Plan p=trap.plan(start);assertEquals(home.add(6,0),p.destination());
        for(Snapshot s:demo)if(s.tick()>485&&s.tick()<=515) {
            p=trap.plan(s);if(p==null)continue;
            if(s.tick()>=488&&s.tick()<500)assertEquals("tick "+s.tick(),north,p.destination());
            if(s.tick()>=500&&s.tick()<=514)assertEquals(home.add(-1,-6),p.destination());
        }
        assertFalse(trap.active());Snapshot end=frame("melee-demo-north","melee-loop",522);
        assertTrue(CaveSafety.trapped(end,end.mobs().get(0)));assertTrue(CombatPlanner.playerCanAttack(end,end.player(),end.mobs().get(0)));
    }
    @Test public void northwestMageMustStayBehindDragonRockThroughExcursionAndReturn()throws Exception {
        Snapshot s=frame("mage-cover-frames","mage-cover",999);
        assertFalse(CaveSafety.pocketAllowed(s,home,north));
        assertTrue(CaveSafety.pocketAllowed(s,home,home));
        Plan p=decide(new LureController(),s,-1);assertNotNull(p);assertEquals(home,p.destination());
        assertFalse(p.reason().contains("northern pull"));
    }
    @Test public void successfulWave49PullAndStationaryMageShotRemainAvailable()throws Exception {
        Snapshot opening=frame("mage-cover-frames","mage-cover",1173);
        assertTrue(CaveSafety.preservesMageCover(opening,north));
        assertEquals(north,decide(new LureController(),opening,-1).destination());
        Snapshot fighting=frame("mage-cover-frames","mage-cover",1209);
        Plan p=decide(new LureController(),fighting,41224);
        assertEquals(home,p.destination());assertEquals(41224,p.targetIndex());
        RecordedMeleeTrap trap=new RecordedMeleeTrap();assertFalse(trap.start(fighting,home,41224));
    }
    @Test public void actualChaseCommandsDoNotShrinkIntoOneTileSteps()throws Exception {
        int checked=0;
        for(Snapshot s:recorded("melee-loop-frames","melee-loop"))if(s.tick()>=146&&s.tick()<=207) {
            Plan p=MinimapMovement.route(s,home,null,Protection.MELEE,false,"Full rock return");
            assertTrue("tick "+s.tick()+" "+p.reason(),CombatPlanner.actionable(p,false));
            assertTrue("tick "+s.tick()+" "+p.nextStep(),p.nextStep().equals(home)||p.nextStep().distance(s.player())>=4);checked++;
        }
        assertTrue(checked>=10);
    }
    @Test public void allOrdinaryMeleeFollowersAllowFullRoutePastTheirMovingFootprint() {
        for(Kind k:List.of(Kind.MELEER,Kind.BLOB,Kind.BABY,Kind.BAT)) {
            Mob m=new Mob(1,k,new Tile(50,50),k==Kind.MELEER?4:k==Kind.BLOB?2:1,10,10,-1,Protection.MELEE,true);
            Snapshot s=new Snapshot(1,new Tile(49,50),new CollisionGrid(new int[104][104]),List.of(m),100,true,7,Protection.NONE);
            Plan p=MinimapMovement.route(s,new Tile(60,50),null,Protection.MELEE,false,"Recorded rock run");
            assertTrue(k+" "+p.reason(),CombatPlanner.actionable(p,false));assertEquals(new Tile(60,50),p.nextStep());
        }
    }
    @Test public void activeStationaryFightIsNotInterruptedByAnotherTrap()throws Exception {
        Snapshot s=frame("melee-loop-frames","melee-loop",173);Mob big=s.mobs().get(0);
        assertTrue(CombatPlanner.playerCanAttack(s,s.player(),big));
        assertFalse(new RecordedMeleeTrap().start(s,home,big.index()));
    }
    @Test public void batsAndRangersPrecedeRecordedBigMeleeRoute()throws Exception {
        Snapshot s=frame("melee-demo-south","melee-loop",630);
        for(Kind k:List.of(Kind.BAT,Kind.RANGER)) {
            List<Mob> mobs=new ArrayList<>(s.mobs());mobs.add(new Mob(9,k,home.add(-6,0),1,10,10,-1,k.protection,true));
            assertFalse(new RecordedMeleeTrap().start(at(s,s.tick(),home,mobs),home,-1));
        }
    }
    @Test public void exhaustedRouteCannotRestartForSameMelee()throws Exception {
        Snapshot s=frame("melee-demo-south","melee-loop",630);RecordedMeleeTrap trap=new RecordedMeleeTrap();
        assertTrue(trap.start(s,home,-1));trap.failed();assertTrue(trap.active());trap.failed();assertFalse(trap.active());
        assertFalse(trap.start(s,home,-1));
    }
    @Test public void rebasePreservesRecordedDestination()throws Exception {
        Snapshot s=frame("melee-demo-south","melee-loop",630);RecordedMeleeTrap trap=new RecordedMeleeTrap();
        assertTrue(trap.start(s,home,-1));trap.plan(s);trap.rebase(4,8);
        Mob moved=s.mobs().get(0).at(s.mobs().get(0).tile().add(4,8));
        Snapshot rebased=new Snapshot(s.tick()+1,home.add(4,8),new CollisionGrid(new int[104][104]),List.of(moved),100,true,7,Protection.NONE);
        assertEquals(home.add(0,8),trap.plan(rebased).destination());
    }
    @Test public void projectileStackUsesSoleInsteadOfRepeatingMeleePeek()throws Exception {
        Snapshot s=frame("mage-cover-frames","mage-cover",1461);
        LureController l=new LureController();Plan p=decide(l,s,-1);
        assertEquals(home.add(-1,-5),p.destination());assertFalse(p.reason().contains("peek"));
        assertEquals(home.add(-1,-5),l.recoverRecorded(s,home,home.add(-2,0)).destination());
    }
    @Test public void failedNorthwestMageRouteDoesNotSuppressFollowingMeleeShot()throws Exception {
        Snapshot initial=frame("mage-cover-frames","mage-cover",999);LureController l=new LureController();decide(l,initial,-1);
        Snapshot bad=frame("mage-cover-frames","mage-cover",1035);
        List<Mob> mobs=new ArrayList<>();for(Mob m:bad.mobs())mobs.add(m.kind()==Kind.MAGER?initial.mobs().get(0):m);
        Snapshot fighting=at(bad,1035,home,mobs);Plan p=decide(l,fighting,38101);
        assertEquals(home,p.destination());assertEquals(38101,p.targetIndex());
    }
    @Test public void wave51FallbackDoesNotInventAdjacentKiteDestinations()throws Exception {
        CombatPlanner planner=new CombatPlanner();
        for(int tick:List.of(1695,1699,1703,1707,1710,1714,1718)) {
            Snapshot f=frame("mage-cover-frames","mage-cover",tick);
            Plan p=planner.plan(f,home);
            assertTrue("tick "+tick+" "+p,p.destination().equals(f.player())||p.destination().distance(f.player())>=4);
        }
    }
    @Test public void lastProjectileShooterUsesAnInRangeTileEvenAfterGeneralRecovery()throws Exception {
        Snapshot s=frame("mage-cover-frames","mage-cover",1748);LureController l=new LureController();l.recover();
        Plan p=decide(l,s,-1);assertNotNull(p);assertTrue(CombatPlanner.playerCanAttack(s,p.destination(),s.mobs().get(0)));
    }
    @Test public void manualTrappedMeleeHandoffDoesNotRunToWaveOpening()throws Exception {
        Snapshot s=frame("ranger-handoff-frames","ranger-handoff",204);
        assertTrue(CombatPlanner.playerCanAttack(s,s.player(),s.mobs().get(0)));
        for(int current:List.of(53082,-1)) {
            LureController l=new LureController();Plan p=decide(l,s,current);
            assertEquals(s.player(),p.destination());assertEquals(53082,p.targetIndex());assertFalse(l.hasPendingReturn());
        }
    }
    @Test public void rangerDeathDoesNotAbandonAlreadyTrappedMelee()throws Exception {
        Snapshot s=frame("ranger-handoff-frames","ranger-handoff",204);
        assertTrue(CaveSafety.trapped(s,s.mobs().get(0)));
        List<Mob> before=new ArrayList<>(s.mobs());before.add(new Mob(9,Kind.RANGER,s.player().add(-3,0),3,10,10,-1,Protection.RANGE,true));
        LureController l=new LureController();l.observeFight(at(s,203,s.player(),before),home,9);l.observeFight(s,home,-1);
        assertFalse(l.hasPendingReturn());assertEquals(s.player(),decide(l,s,-1).destination());
    }
    @Test public void completedNorthernPullCannotBeRepeatedByBigMeleeController()throws Exception {
        LureController l=new LureController();
        decide(l,frame("mage-cover-frames","mage-cover",1596),-1);
        decide(l,frame("mage-cover-frames","mage-cover",1611),-1);
        decide(l,frame("mage-cover-frames","mage-cover",1614),-1);
        Snapshot base=frame("melee-demo-north","melee-loop",485);
        for(int tick=1618;tick<=1623;tick++) {
            Snapshot atHome=at(base,tick,home,base.mobs());Plan p=decide(l,atHome,-1);
            if(p!=null){assertNotEquals(north,p.destination());assertNotEquals(home.add(6,0),p.destination());}
        }
    }
}
