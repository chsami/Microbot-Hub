package net.runelite.client.plugins.microbot.drofirecape;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Replays the actual size-four footprint and the positions of the one-tile chase. */
public class Wave15LureTest {
    private final Tile home=new Tile(54,36),peek=new Tile(52,36),bigPeek=new Tile(50,36);
    private BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8));
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private List<Snapshot> recorded() throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader r=resource("wave15-live-collision.csv")) {
            r.readLine();String line;while((line=r.readLine())!=null) {
                String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);
            }
        }
        List<Snapshot> frames=new ArrayList<>();CollisionGrid grid=new CollisionGrid(flags);
        try(Reader r=resource("wave15-live-frames.json")) {
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
    private Plan decide(LureController l,Snapshot s) {
        return l.decide(s,new CombatPlanner(),-1,List.of(home),0,home,null,null,peek);
    }

    @Test public void actualWave15BigMeleeUsesDemonstratedFourTileRelease()throws Exception {
        Snapshot s=recorded().get(0);LureController l=new LureController();
        decide(l,at(s,1,home,s.mobs()));
        Plan outward=decide(l,at(s,4,home,s.mobs()));
        assertEquals(bigPeek,outward.destination());assertTrue(CombatPlanner.actionable(outward,false));
        assertEquals(-1,outward.targetIndex());
    }




    @Test public void exposedLargeMeleeAndBlobsPrecedeMageOnAllOverheadPhases() {
        Tile player=new Tile(50,50);CollisionGrid grid=new CollisionGrid(new int[104][104]);
        List<Mob> mobs=List.of(new Mob(1,Kind.MAGER,new Tile(40,50),5,10,10,-1,Protection.MAGIC,true),
            new Mob(2,Kind.MELEER,new Tile(50,51),4,10,10,-1,Protection.MELEE,true),
            new Mob(3,Kind.BLOB,new Tile(49,50),2,10,10,-1,Protection.MELEE,true),
            new Mob(4,Kind.BABY,new Tile(51,50),1,10,10,-1,Protection.MELEE,true));
        Snapshot s=new Snapshot(1,player,grid,mobs,100,false,7,Protection.NONE);
        for(Protection p:Protection.values()) {
            assertTrue(CaveSafety.targetPriority(s,mobs.get(1),p)>CaveSafety.targetPriority(s,mobs.get(2),p));
            assertTrue(CaveSafety.targetPriority(s,mobs.get(2),p)>CaveSafety.targetPriority(s,mobs.get(3),p));
            assertTrue(CaveSafety.targetPriority(s,mobs.get(3),p)>CaveSafety.targetPriority(s,mobs.get(0),p));
        }
    }
    @Test public void southTrappedMageUsesNorthernPullForOtherMonstersAndNeverRushesMage() {
        int[][] flags=new int[104][104];for(int x=45;x<=65;x++)for(int y=25;y<=30;y++)flags[x][y]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        CollisionGrid grid=new CollisionGrid(flags);
        Mob mage=new Mob(1,Kind.MAGER,new Tile(54,20),5,10,10,-1,Protection.MAGIC,true);
        Mob blob=new Mob(2,Kind.BLOB,new Tile(40,40),2,10,10,-1,Protection.MELEE,true);
        Snapshot s=new Snapshot(1,home,grid,List.of(mage,blob),100,true,7,Protection.NONE);
        assertTrue(CaveSafety.southTrappedMage(s,mage,home));Tile pull=new Tile(62,53);LureController l=new LureController();
        Plan p=l.decide(s,new CombatPlanner(),-1,List.of(home),0,home,pull,null,peek,home.add(-1,-5));
        assertEquals(pull,p.destination());assertTrue(l.hasPendingReturn());assertEquals(-1,p.targetIndex());
        Snapshot alone=new Snapshot(2,home,grid,List.of(mage),100,true,7,Protection.NONE);
        LureController cleanup=new LureController();Plan wall=null;
        for(int tick=2;tick<=22;tick++)wall=cleanup.decide(at(alone,tick,home,List.of(mage)),new CombatPlanner(),-1,List.of(home),0,home,pull,null,peek,home.add(-1,-5));
        assertNotNull(wall);assertTrue(CombatPlanner.playerCanAttack(alone,wall.destination(),mage));assertTrue(CaveSafety.clearOfMagers(alone,alone.mobs(),wall.destination()));
    }
    @Test public void activeBlobsPrecedeHarmlessMageEvenDuringMeleeProtection() {
        Tile player=new Tile(50,50);
        Mob mage=new Mob(1,Kind.MAGER,new Tile(10,10),5,10,10,-1,Protection.MAGIC,false);
        Mob blob=new Mob(2,Kind.BLOB,new Tile(49,50),2,10,10,-1,Protection.MELEE,true);
        Mob baby=new Mob(3,Kind.BABY,new Tile(51,50),1,10,10,-1,Protection.MELEE,true);
        Snapshot s=new Snapshot(1,player,new CollisionGrid(new int[104][104]),List.of(mage,blob,baby),100,false,7,Protection.NONE);
        assertFalse(CaveSafety.active(s,mage));
        for(Protection p:Protection.values()) {
            assertTrue(CaveSafety.targetPriority(s,blob,p)>CaveSafety.targetPriority(s,baby,p));
            assertTrue(CaveSafety.targetPriority(s,baby,p)>CaveSafety.targetPriority(s,mage,p));
        }
    }
    @Test public void runningPastFollowingMeleeDoesNotBecomeRepeatedOneTileClicks() {
        Tile start=new Tile(50,50),destination=new Tile(48,49);
        Mob big=new Mob(1,Kind.MELEER,new Tile(50,46),4,10,10,-1,Protection.MELEE,true);
        Snapshot s=new Snapshot(1,start,new CollisionGrid(new int[104][104]),List.of(big),100,true,7,Protection.NONE);
        Plan run=MinimapMovement.checked(s,destination,destination,Protection.MELEE,"Run around following melee");
        assertTrue(run.reason(),CombatPlanner.actionable(run,false));assertEquals(destination,run.nextStep());
        // Ordinary followers are not terrain walls; their threat remains modeled.
        Plan blocked=MinimapMovement.checked(s,new Tile(51,48),new Tile(51,48),Protection.MELEE,"Blocked");
        assertTrue(CombatPlanner.actionable(blocked,false));
    }

    @Test public void remainingMageIsApproachedWhenWallExposesItBeforeOurShot() {
        int[][] flags=new int[104][104];
        for(int x=45;x<=65;x++)for(int y=25;y<=30;y++)flags[x][y]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        Mob mage=new Mob(1,Kind.MAGER,new Tile(54,20),5,10,10,-1,Protection.MAGIC,true);
        Snapshot trapped=new Snapshot(1,home,new CollisionGrid(flags),List.of(mage),100,true,5,Protection.NONE);
        LureController l=new LureController();decide(l,trapped);
        Tile wall=home.add(-1,-5);
        Snapshot exposed=new Snapshot(2,wall,new CollisionGrid(new int[104][104]),List.of(mage),100,true,5,Protection.NONE);
        assertTrue(CaveSafety.active(exposed,mage));assertFalse(CaveSafety.southTrappedMage(exposed,mage,home));
        assertFalse(CombatPlanner.playerCanAttack(exposed,wall,mage));
        for(int tick=2;tick<=5;tick++) {
            Snapshot s=new Snapshot(tick,wall,exposed.grid(),List.of(mage),100,true,5,Protection.NONE);
            Plan p=l.recoverRecorded(s,home,peek);
            if(p==null)p=l.decide(s,new CombatPlanner(),-1,List.of(home),0,home,null,null,peek,wall);
            assertNotNull(p);assertTrue(CombatPlanner.playerCanAttack(s,p.destination(),mage));assertTrue(CaveSafety.clearOfMagers(s,s.mobs(),p.destination()));
        }
    }

    @Test public void rangerAlsoWinsWhileReturnIsSettlingAtCover()throws Exception {
        Snapshot original=recorded().get(0);LureController l=new LureController();
        l.recoverRecorded(at(original,100,home,original.mobs()),home,peek);
        l.finishReturn(at(original,101,bigPeek,original.mobs()));
        Mob big=new Mob(1,Kind.MELEER,new Tile(50,32),4,10,10,-1,Protection.MELEE,true);
        Mob ranger=new Mob(2,Kind.RANGER,new Tile(52,28),3,10,10,-1,Protection.RANGE,true);
        Snapshot cover=new Snapshot(102,home,new CollisionGrid(new int[104][104]),List.of(big,ranger),100,true,7,Protection.NONE);
        Plan shot=l.finishReturn(cover);assertFalse(l.hasPendingReturn());
        assertEquals(home,shot.destination());assertEquals(ranger.index(),shot.targetIndex());
    }



}
