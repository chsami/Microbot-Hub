/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Replays the wave-44 movement loop, ranger selection and manual northern pull. */
public class CombatLoopRegressionTest {
    private final Tile home=new Tile(62,60),peek=new Tile(60,60),north=new Tile(70,77);
    private BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8));
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private List<Snapshot> recorded() throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader r=resource("combat-loop-collision.csv")) {
            r.readLine();String line;while((line=r.readLine())!=null) {
                String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);
            }
        }
        List<Snapshot> frames=new ArrayList<>();CollisionGrid grid=new CollisionGrid(flags);
        try(Reader r=resource("combat-loop-frames.json")) {
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
    private Snapshot frame(int tick)throws Exception {return recorded().stream().filter(s->s.tick()==tick).findFirst().get();}
    private Plan decide(LureController l,Snapshot s,int target) {
        return l.decide(s,new CombatPlanner(),target,List.of(home),0,home,north,null,peek,home.add(-1,-5));
    }
    @Test public void actualRangerDeathCommitsNorthernPullForMediumBlobs()throws Exception {
        LureController l=new LureController();Snapshot fighting=frame(222);
        l.observeFight(fighting,home,43211);
        Plan p=decide(l,frame(231),-1);
        assertEquals(north,p.destination());assertTrue(l.hasPendingReturn());
        assertEquals(north,l.recoverRecorded(frame(236),home,peek).destination());
    }
    @Test public void repeatedReturnClicksBecomeProtectedAttackWithoutAnotherBounce()throws Exception {
        LureController l=new LureController();Plan p=null;int stopped=-1;
        for(Snapshot s:recorded())if(s.tick()>=231&&s.tick()<=303) {
            p=decide(l,s,-1);
            assertNotNull("tick "+s.tick(),p);
            if(p.targetIndex()>=0) {if(stopped<0)stopped=s.tick();assertEquals(s.player(),p.destination());}
            if(stopped>=0)assertTrue("Cannot restart the loop at "+s.tick(),p.targetIndex()>=0);
        }
        assertTrue("Break A-B-A-B by tick 260, not after the full 70-tick loop",stopped>0&&stopped<=260);
        assertFalse(l.hasPendingReturn());
    }
    @Test public void npcShufflingCannotHideThirtyTicksWithoutDamage()throws Exception {
        Snapshot base=frame(246);CombatProgress progress=new CombatProgress();
        for(int tick=1;tick<=31;tick++) {
            List<Mob> mobs=new ArrayList<>(base.mobs());Mob m=mobs.remove(mobs.size()-1);mobs.add(m.at(m.tile().add(tick%2,0)));
            Snapshot s=at(base,tick,base.player().add(tick%2,0),mobs);progress.observe(s);
            if(tick<11)assertFalse(progress.due(s));
            if(tick==31)assertTrue(progress.due(s));
        }
    }
    @Test public void normalDetourDoesNotFailBecauseItMovesAwayFromGoal()throws Exception {
        Snapshot base=frame(246);LureProgress p=new LureProgress();Tile goal=new Tile(62,60);
        for(int tick=1;tick<=30;tick++)assertFalse(p.stalled(at(base,tick,new Tile(30-tick/2,20+tick),base.mobs()),goal));
    }
    @Test public void alreadyShootableRangerWinsOverEqualPriorityBlockedRanger()throws Exception {
        Snapshot s=frame(711);Mob ranger=CaveSafety.rangedTarget(s);
        assertEquals(50814,ranger.index());assertTrue(CombatPlanner.playerCanAttack(s,s.player(),ranger));
        assertEquals(ranger.index(),decide(new LureController(),s,ranger.index()).targetIndex());
    }
    @Test public void demonstrationSeparatesMageFromTheMeleeFiringPosition()throws Exception {
        Snapshot s=frame(557);
        Mob mage=s.mobs().stream().filter(m->m.kind()==Kind.MAGER).findFirst().get();
        Mob blob=s.mobs().stream().filter(m->m.kind()==Kind.BLOB).findFirst().get();
        assertEquals(0,CombatPlanner.threats(s.grid(),mage,s.player(),Protection.NONE));
        assertTrue(CombatPlanner.playerCanAttack(s,s.player(),blob));
        assertEquals(blob.index(),CaveSafety.preferredShot(s,Protection.MELEE).index());
    }
    @Test public void followingMeleeDoesNotCancelNorthernPullHalfway()throws Exception {
        LureController l=new LureController();decide(l,frame(231),-1);
        Snapshot recorded=frame(539);Mob blob=recorded.mobs().stream().filter(m->m.kind()==Kind.BLOB).findFirst().get();
        List<Mob> following=new ArrayList<>();
        for(Mob m:recorded.mobs())following.add(m.index()==blob.index()?m.at(new Tile(59,52)):m);
        Snapshot halfway=at(recorded,232,new Tile(60,54),following);
        assertTrue(halfway.grid().melee(following.get(1),halfway.player()));
        assertEquals(north,decide(l,halfway,-1).destination());
        assertEquals(north,l.recoverRecorded(halfway,home,peek).destination());
    }
}
