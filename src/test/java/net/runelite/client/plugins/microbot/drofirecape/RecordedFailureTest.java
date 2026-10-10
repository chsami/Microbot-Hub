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

/** Geometry and decision regressions from the tester's supplied instance, not a live-game claim. */
public class RecordedFailureTest {
    private BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8));
    }
    private CollisionGrid grid() throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader r=resource("tester-italy-collision.csv")) {
            r.readLine();String line;
            while((line=r.readLine())!=null){String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);}
        }
        return new CollisionGrid(flags);
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private List<Snapshot> frames() throws Exception {
        List<Snapshot> all=new ArrayList<>();CollisionGrid grid=grid();
        try(BufferedReader reader=resource("tester-italy-frames.jsonl")) {
            String line;while((line=reader.readLine())!=null) {
                JsonObject f=new JsonParser().parse(line).getAsJsonObject();List<Mob> mobs=new ArrayList<>();
                for(JsonElement value:f.getAsJsonArray("mobs")) {
                    JsonObject m=value.getAsJsonObject();
                    mobs.add(new Mob(m.get("index").getAsInt(),Kind.valueOf(m.get("kind").getAsString()),tile(m.getAsJsonArray("tile")),
                        m.get("size").getAsInt(),m.get("healthRatio").getAsInt(),m.get("healthScale").getAsInt(),
                        m.get("attackTick").getAsInt(),Protection.valueOf(m.get("style").getAsString()),m.get("attackingPlayer").getAsBoolean()));
                }
                all.add(new Snapshot(f.get("tick").getAsInt(),tile(f.getAsJsonArray("player")),grid,mobs,
                    f.get("energyRaw").getAsInt(),f.get("running").getAsBoolean(),f.get("range").getAsInt(),Protection.MAGIC));
            }
        }
        return all;
    }
    private Snapshot tick(int tick)throws Exception{return frames().stream().filter(s->s.tick()==tick).findFirst().orElseThrow(AssertionError::new);}
    private Snapshot at(Snapshot s,int tick,Tile p,List<Mob> mobs){return new Snapshot(tick,p,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle());}
    private Mob mob(int id,Kind kind,Tile tile,int tick,Protection style){return new Mob(id,kind,tile,kind.size,10,10,tick,style,true);}

    @Test public void everyRecordedFatalContactFrameHasAnExecutableEscape()throws Exception {
        int checked=0;
        for(Snapshot s:frames())if(s.tick()>=21814&&CaveSafety.mageContact(s)) {
            Plan escape=CaveSafety.escapeMage(s);assertNotNull("tick "+s.tick(),escape);
            assertNotEquals(s.player(),escape.nextStep());assertTrue(CombatPlanner.actionable(escape,false));
            assertTrue(CaveSafety.clearOfMagers(s,s.mobs(),escape.nextStep()));
            Plan click=MinimapMovement.checked(s,escape.destination(),escape.nextStep(),Protection.MAGIC,escape.reason());
            assertTrue("Actual click must pass, tick "+s.tick()+": "+click.reason(),CombatPlanner.actionable(click,false));checked++;
        }
        assertTrue(checked>=5);
    }
    @Test public void observedKetZekMeleeReachesTheActualTickPrayerDecision()throws Exception {
        Snapshot s=tick(21818);
        assertEquals(Protection.MELEE,new TickProtection().choose(s,false,Protection.MAGIC,Protection.NONE).protection);
        assertEquals(Protection.MELEE,HeldProtection.choose(s,Protection.MAGIC,Protection.MAGIC));
        Plan escape=new LureController().decide(s,new CombatPlanner(),-1,List.of(s.player()));
        assertNotEquals(s.player(),escape.nextStep());
    }
    @Test public void anotherExposedMageStillWinsAndSeparationRestoresMagic()throws Exception {
        Snapshot s=tick(21818);List<Mob> two=new ArrayList<>(s.mobs());two.add(mob(1,Kind.MAGER,new Tile(57,38),21818,Protection.MAGIC));
        Snapshot mixed=at(s,s.tick(),s.player(),two);
        assertEquals(Protection.MAGIC,new TickProtection().choose(mixed,false,Protection.MELEE,Protection.NONE).protection);
        Plan escape=CaveSafety.escapeMage(s);Snapshot separated=at(s,21819,escape.nextStep(),s.mobs());
        assertEquals(Protection.MAGIC,new TickProtection().choose(separated,false,Protection.MELEE,Protection.NONE).protection);
    }
    @Test public void oldReturnToItalyIsRejectedBeforeItCreatesMageContact()throws Exception {
        Snapshot s=tick(21810);Tile italy=new Tile(54,36);
        assertFalse(CombatPlanner.actionable(MinimapMovement.checked(s,italy,italy,Protection.MAGIC,"old return"),false));
    }
    @Test public void recordedBigMeleeOscillationCannotResetLastMonsterDeadline()throws Exception {
        CombatProgress progress=new CombatProgress();boolean recovery=false;
        for(Snapshot s:frames())if(s.tick()>=18486&&s.tick()<18770) {
            progress.observe(s);
            if(progress.due(s)){recovery=true;break;}
        }
        assertTrue(recovery);
    }
    @Test public void healthDamageResetsWatchdogButPlayerStepsDoNot()throws Exception {
        Snapshot s=tick(21818);Mob blob=mob(4,Kind.BLOB,new Tile(54,24),-1,Protection.MELEE);
        CombatProgress progress=new CombatProgress();
        for(int t=1;t<=10;t++)progress.observe(at(s,t,new Tile(54+t%2,36),List.of(blob)));
        assertFalse(progress.due(at(s,10,s.player(),List.of(blob))));
        Snapshot stuck=at(s,11,s.player(),List.of(blob));progress.observe(stuck);assertTrue(progress.due(stuck));
        Mob hit=new Mob(4,Kind.BLOB,blob.tile(),2,9,10,-1,Protection.MELEE,true);
        Snapshot damaged=at(s,12,s.player(),List.of(hit));progress.observe(damaged);assertFalse(progress.due(damaged));
    }
    @Test public void suppliedSouthTipBlobCompletesTwoStepsAndClearsBeforeReturn()throws Exception {
        // Recorder scene (62,60) corresponds to tester scene (54,36): delta (-8,-24).
        Snapshot sample=tick(21818);Tile main=new Tile(54,36),peek=main.add(-2,0);
        Mob blob=mob(36432,Kind.BLOB,new Tile(54,24),-1,Protection.MELEE);
        LureController lure=new LureController();CombatPlanner planner=new CombatPlanner();
        lure.decide(at(sample,1,main,List.of(blob)),planner,-1,List.of(main),0,main,null,null,peek);
        Plan out=lure.decide(at(sample,4,main,List.of(blob)),planner,-1,List.of(main),0,main,null,null,peek);
        assertEquals(peek,out.destination());
        Mob sideways=blob.at(new Tile(53,24));
        Plan midway=lure.decide(at(sample,5,main.add(-1,0),List.of(sideways)),planner,-1,List.of(main),0,main,null,null,peek);
        assertEquals(peek,midway.destination());
        lure.decide(at(sample,6,peek,List.of(sideways)),planner,-1,List.of(main),0,main,null,null,peek);
        Mob around=blob.at(new Tile(52,25));
        Plan back=lure.decide(at(sample,7,peek,List.of(around)),planner,-1,List.of(main),0,main,null,null,peek);
        assertEquals(main,back.destination());assertEquals(-1,back.targetIndex());
    }
    @Test public void trappedRangerGetsAFiringApproachAndNeverOwesAPeekReturn()throws Exception {
        Snapshot sample=tick(21818);Tile main=new Tile(54,36);Mob ranger=mob(1,Kind.RANGER,new Tile(55,23),-1,Protection.RANGE);
        Snapshot s=at(sample,1,main,List.of(ranger));LureController lure=new LureController();
        Plan p=lure.decide(s,new CombatPlanner(),-1,List.of(main),0,main,new Tile(62,53),null,main.add(-2,0));
        assertNotNull(p);assertFalse(lure.hasPendingReturn());assertNotEquals(main,p.nextStep());
        assertTrue(p.reason().contains("ranged attacker"));
    }
    @Test public void actualWave15BlockerRecoversToALegalShotWithinBoundedTicks()throws Exception {
        Snapshot s=tick(18486);Tile main=new Tile(54,36),peek=main.add(-2,0);
        LureController lure=new LureController();CombatPlanner planner=new CombatPlanner();CombatProgress progress=new CombatProgress();
        for(int n=0;n<100;n++) {
            progress.observe(s);Plan p=null;
            if(progress.due(s)) {p=lure.recoverRecorded(s,main,peek);progress.recovering(s.tick());}
            if(p==null)p=lure.decide(s,planner,-1,List.of(main),0,main,null,null,peek);
            if(p==null)p=planner.plan(s,main);
            if(p.targetIndex()>=0) {
                Mob target=s.mobs().get(0);
                assertTrue(CombatPlanner.playerCanAttack(s,s.player(),target));
                assertFalse("Ranged recovery must not stand in melee",s.grid().melee(target,s.player()));
                return;
            }
            assertTrue("Recovery route: "+p.reason(),CombatPlanner.actionable(p,false));
            Tile player=p.nextStep();
            s=at(s,s.tick()+1,player,CombatPlanner.advance(s.grid(),s.mobs(),player,s.jadStyle()));
        }
        fail("Recorded melee remained blocked after recovery deadline");
    }

}
