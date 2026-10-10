package net.runelite.client.plugins.microbot.drofirecape.optional;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;

public class PureCombatRecoveryReplayTest {
    private List<Snapshot> scenes()throws Exception {
        return scenes("scenarios.json");
    }
    private List<Snapshot> scenes(String file)throws Exception {
        JsonArray cases=new JsonParser().parse(new InputStreamReader(getClass().getResourceAsStream("recovery/"+file),StandardCharsets.UTF_8)).getAsJsonArray();
        List<Snapshot> result=new ArrayList<>();
        for(JsonElement e:cases) {
            JsonObject f=e.getAsJsonObject();int[][] flags=new int[104][104];
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(
                getClass().getResourceAsStream("recovery/"+f.get("geometry").getAsString())),StandardCharsets.UTF_8))) {
                reader.readLine();String line;while((line=reader.readLine())!=null) {
                    String[] v=line.split(",");flags[Integer.parseInt(v[0])][Integer.parseInt(v[1])]=Integer.parseInt(v[2]);
                }
            }
            List<Mob> mobs=new ArrayList<>();
            for(JsonElement m:f.getAsJsonArray("mobs")) {
                JsonObject v=m.getAsJsonObject();mobs.add(new Mob(v.get("index").getAsInt(),Kind.valueOf(v.get("kind").getAsString()),
                    tile(v.getAsJsonArray("tile")),v.get("size").getAsInt(),v.get("healthRatio").getAsInt(),v.get("healthScale").getAsInt(),
                    v.get("attackTick").getAsInt(),Protection.valueOf(v.get("style").getAsString()),v.get("attackingPlayer").getAsBoolean()));
            }
            result.add(new Snapshot(f.get("tick").getAsInt(),tile(f.getAsJsonArray("player")),new CollisionGrid(flags),mobs,100,true,f.get("range").getAsInt(),Protection.NONE).atWave(f.get("wave").getAsInt()));
        }
        return result;
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private Snapshot at(Snapshot s,int tick,Tile player,List<Mob> mobs) {
        return new Snapshot(tick,player,s.grid(),mobs,s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave());
    }
    @Test public void allRecordedStallsProduceExecutableMovementOrAttack()throws Exception {
        for(Snapshot s:scenes()) {
            PureCombatRecovery recovery=new PureCombatRecovery();recovery.observe(s);
            Snapshot stalled=at(s,s.tick()+24,s.player(),s.mobs());recovery.observe(stalled);
            Plan plan=recovery.plan(stalled);assertNotNull("wave "+s.wave(),plan);
            assertTrue(CombatPlanner.actionable(plan,false));
            if(plan.nextStep().equals(s.player()))assertTrue(new PureCombatPlanner().attackAllowed(stalled,
                s.mobs().stream().filter(m->m.index()==plan.targetIndex()).findFirst().get(),plan.protection(),false));
            else {
                assertTrue(recovery.routeAllowed(stalled,plan.nextStep()));
                assertTrue(CombatPlanner.actionable(MinimapMovement.checked(stalled,plan.destination(),plan.nextStep(),plan.protection(),"replay dispatch"),false));
                Snapshot arrived=at(s,stalled.tick()+1,plan.destination(),s.mobs());recovery.observe(arrived);
                Plan shot=recovery.plan(arrived);assertNotNull(shot);assertTrue("arrival must allow firing",shot.targetIndex()>=0);
                assertTrue(new PureCombatPlanner().attackAllowed(arrived,
                    s.mobs().stream().filter(m->m.index()==shot.targetIndex()).findFirst().get(),shot.protection(),false));
            }
        }
    }
    @Test public void damageProgressPreservesWorkingCombatAndRecoveryShot()throws Exception {
        Snapshot s=scenes().get(0);PureCombatRecovery recovery=new PureCombatRecovery();
        for(int tick=0;tick<60;tick++) {
            List<Mob> mobs=new ArrayList<>();for(Mob m:s.mobs())mobs.add(new Mob(m.index(),m.kind(),m.tile(),m.size(),100-tick/8,100,m.lastAttackTick(),m.lastStyle(),true));
            recovery.observe(at(s,s.tick()+tick,s.player(),mobs));assertFalse(recovery.active());
        }
        recovery.reset();recovery.observe(s);Snapshot stalled=at(s,s.tick()+24,s.player(),s.mobs());recovery.observe(stalled);
        Plan shot=recovery.plan(stalled);assertTrue(shot.targetIndex()>=0);
        List<Mob> hurt=new ArrayList<>();for(Mob m:s.mobs())hurt.add(new Mob(m.index(),m.kind(),m.tile(),m.size(),m.index()==shot.targetIndex()?1:m.healthRatio(),m.healthScale(),m.lastAttackTick(),m.lastStyle(),true));
        Snapshot hit=at(s,stalled.tick()+1,s.player(),hurt);recovery.observe(hit);assertTrue("Do not resume lure after first successful hit",recovery.active());
        assertEquals(shot.targetIndex(),recovery.plan(hit).targetIndex());
        recovery.observe(at(s,hit.tick()+1,s.player(),List.of()));assertFalse(recovery.active());
    }
    @Test public void walkingCannotResetTheCombatDeadlineAndJadIsExcluded()throws Exception {
        Snapshot s=scenes().get(0);PureCombatRecovery recovery=new PureCombatRecovery();
        for(int i=0;i<=24;i++)recovery.observe(at(s,s.tick()+i,s.player().add(i%2,0),s.mobs()));
        assertTrue(recovery.active());
        Mob jad=new Mob(99,Kind.JAD,new Tile(30,30),5,-1,-1,-1,Protection.MAGIC,true);
        recovery.observe(at(s,s.tick()+25,s.player(),List.of(jad)));assertFalse(recovery.active());
    }
    @Test public void projectileFallbackNeverAllowsMageContact()throws Exception {
        Snapshot s=new Snapshot(1,new Tile(20,20),new CollisionGrid(new int[104][104]),
            List.of(new Mob(1,Kind.MAGER,new Tile(23,20),5,-1,-1,-1,Protection.MAGIC,true)),100,true,5,Protection.NONE);
        assertFalse(PureSafety.acquisitionRouteAllowed(s,new Tile(22,20)));
    }
    @Test public void stalledMageContactStillEscapesBeforeShooting() {
        Snapshot s=new Snapshot(1,new Tile(22,20),new CollisionGrid(new int[104][104]),
            List.of(new Mob(1,Kind.MAGER,new Tile(23,20),5,10,10,0,Protection.MELEE,true)),100,true,5,Protection.NONE);
        PureCombatRecovery recovery=new PureCombatRecovery();recovery.observe(s);
        s=at(s,25,s.player(),s.mobs());recovery.observe(s);Plan escape=recovery.plan(s);
        assertNotNull(escape);assertEquals(-1,escape.targetIndex());assertNotEquals(s.player(),escape.nextStep());
        assertTrue(CaveSafety.clearOfMagers(s,s.mobs(),escape.nextStep()));
        assertTrue(recovery.routeAllowed(s,escape.nextStep()));
    }
    @Test public void movingMonstersStillAllowAFiringOpportunityWithoutOscillation()throws Exception {
        for(Snapshot start:scenes()) {
            PureCombatRecovery recovery=new PureCombatRecovery();recovery.observe(start);
            Snapshot s=at(start,start.tick()+24,start.player(),start.mobs());
            Set<Tile> visited=new HashSet<>();visited.add(s.player());boolean fired=false;
            for(int tick=0;tick<30;tick++) {
                recovery.observe(s);Plan plan=recovery.plan(s);
                assertNotNull("no route at wave "+s.wave()+" tick "+s.tick()+" player "+s.player(),plan);
                if(plan.targetIndex()>=0) {
                    Mob target=null;for(Mob m:s.mobs())if(m.index()==plan.targetIndex())target=m;
                    assertNotNull(target);assertTrue(new PureCombatPlanner().attackAllowed(s,target,plan.protection(),false));
                    fired=true;break;
                }
                assertTrue(recovery.owns(plan));assertTrue(recovery.routeAllowed(s,plan.nextStep()));
                List<Tile> route=MinimapMovement.path(s,plan.nextStep());assertTrue(route.size()>1);
                Tile next=route.get(Math.min(2,route.size()-1));
                assertTrue("out/back loop at wave "+s.wave()+" tick "+s.tick(),visited.add(next));
                List<Mob> following=CombatPlanner.advance(s.grid(),s.mobs(),next,s.jadStyle());
                s=at(s,s.tick()+1,next,following);
            }
            assertTrue("no attack after 30 ticks at wave "+start.wave(),fired);
        }
    }
    @Test public void recordedNorthPullAndReturnAreNotCancelledAtOriginalWatchdogDeadline()throws Exception {
        JsonArray sequences=new JsonParser().parse(new InputStreamReader(getClass().getResourceAsStream("recovery/run62-lures.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        Snapshot template=scenes("run62-scenes.json").get(0);
        for(JsonElement e:sequences) {
            JsonArray steps=e.getAsJsonObject().getAsJsonArray("steps");
            int last=steps.get(steps.size()-1).getAsJsonObject().get("tick").getAsInt()+1;
            PureCombatRecovery recovery=new PureCombatRecovery();
            JsonObject first=steps.get(0).getAsJsonObject();
            recovery.observe(at(template,last-24,tile(first.getAsJsonArray("player")),template.mobs()));
            Tile player=null,goal=null;
            for(JsonElement step:steps) {
                JsonObject v=step.getAsJsonObject();int tick=v.get("tick").getAsInt();
                player=tile(v.getAsJsonArray("player"));goal=tile(v.getAsJsonArray("goal"));
                recovery.observe(at(template,tick,player,template.mobs()),goal);
                assertFalse("moving lure on wave "+e.getAsJsonObject().get("wave"),recovery.active());
            }
            recovery.observe(at(template,last,player,template.mobs()),goal);
            assertFalse("do not erase the owed return",recovery.active());
            recovery.observe(at(template,last+12,player,template.mobs()),goal);
            assertTrue("an actually blocked return must still recover",recovery.active());
        }
    }
    @Test public void committedLureCannotRenewDeadlineThroughOscillationOrEndlessNewGoals()throws Exception {
        Snapshot s=scenes().get(0);PureCombatRecovery recovery=new PureCombatRecovery();
        for(int tick=0;tick<=24;tick++)recovery.observe(at(s,s.tick()+tick,s.player().add(tick%2,0),s.mobs()),new Tile(20,20));
        assertTrue(recovery.active());recovery.reset();
        for(int tick=0;tick<=64;tick++)recovery.observe(at(s,s.tick()+tick,s.player(),s.mobs()),new Tile(20+tick,20));
        assertTrue("changing intentions cannot extend the hard deadline",recovery.active());
    }
    @Test public void finishedReturnGetsShortAttackWindowThenNormalWatchdogApplies()throws Exception {
        Snapshot s=scenes().get(0);PureCombatRecovery recovery=new PureCombatRecovery();
        for(int tick=0;tick<=30;tick++)recovery.observe(at(s,s.tick()+tick,new Tile(20+tick,20),s.mobs()),new Tile(50,20));
        recovery.observe(at(s,s.tick()+31,new Tile(50,20),s.mobs()));assertFalse(recovery.active());
        recovery.observe(at(s,s.tick()+37,new Tile(50,20),s.mobs()));assertTrue(recovery.active());
    }
    @Test public void recordedWave44AcquiresAttackingRangerBeforeSpendingMageKillUnderFire()throws Exception {
        Snapshot s=scenes("run62-scenes.json").get(1);PureCombatRecovery recovery=new PureCombatRecovery();
        recovery.observe(s);s=at(s,s.tick()+24,s.player(),s.mobs());recovery.observe(s);
        assertEquals(Kind.MAGER,PureCombatPolicy.preferredShot(s,Protection.MAGIC).kind());
        Plan route=recovery.plan(s);assertNotNull(route);assertNotEquals(s.player(),route.nextStep());
        assertTrue(recovery.routeAllowed(s,route.nextStep()));
        assertTrue("only a short range correction is needed",MinimapMovement.path(s,route.destination()).size()<=5);
        s=at(s,s.tick()+1,route.destination(),s.mobs());recovery.observe(s);
        Plan shot=recovery.plan(s);assertEquals(2,shot.targetIndex());
        assertTrue(new PureCombatPlanner().attackAllowed(s,s.mobs().get(1),shot.protection(),false));
    }
    @Test public void failedRangerCorrectionDoesNotRepeatTinyMovesForever()throws Exception {
        Snapshot s=scenes("run62-scenes.json").get(1);PureCombatRecovery recovery=new PureCombatRecovery();
        recovery.observe(s);s=at(s,s.tick()+24,s.player(),s.mobs());recovery.observe(s);
        assertNotEquals(s.player(),recovery.plan(s).nextStep());
        // A proposed click with no observed arrival is never progress.
        s=at(s,s.tick()+10,s.player(),s.mobs());recovery.observe(s);
        Plan fallback=recovery.plan(s);assertEquals(s.player(),fallback.nextStep());assertEquals(1,fallback.targetIndex());
    }
    @Test public void simultaneousMageAndBigMeleeUseDurableCoverBeyondLocalSearch()throws Exception {
        for(Snapshot start:scenes("run63-spacing.json")) {
            PureSpacing spacing=new PureSpacing();Plan route=spacing.decide(start,0);
            assertNotNull("same-tick pressure on wave "+start.wave(),route);
            Tile goal=route.destination();
            assertTrue("recorded cover is outside the old local search",goal.distance(start.player())>6);
            assertTrue(PureSafety.routeAllowed(start,goal));
            Snapshot s=start;Set<Tile> visited=new HashSet<>();visited.add(s.player());
            for(int i=0;i<24&&!s.player().equals(goal);i++) {
                Plan step=spacing.decide(s,0);assertNotNull("retain the complete route",step);
                assertEquals("do not alternate firing tiles",goal,step.destination());
                List<Tile> path=MinimapMovement.path(s,goal);assertTrue(path.size()>1);
                Tile next=path.get(Math.min(2,path.size()-1));assertTrue(visited.add(next));
                s=at(s,s.tick()+1,next,CombatPlanner.advance(s.grid(),s.mobs(),next,s.jadStyle()));
            }
            assertEquals(goal,s.player());
            for(int i=0;i<24;i++)s=at(s,s.tick()+1,goal,CombatPlanner.advance(s.grid(),s.mobs(),goal,s.jadStyle()));
            assertEquals("cover must remain safe after followers settle",0,PureSafety.stationaryCost(s));
            Snapshot settled=s;
            assertTrue("cover still provides a shot",s.mobs().stream().anyMatch(m->CombatPlanner.playerCanAttack(settled,goal,m)));
        }
    }
    @Test public void unknownOrStaleClocksDoNotEnableTheLargerConflictSearch()throws Exception {
        for(Snapshot s:scenes("run63-spacing.json")) {
            List<Mob> unknown=new ArrayList<>();
            for(Mob m:s.mobs())unknown.add(new Mob(m.index(),m.kind(),m.tile(),m.size(),m.healthRatio(),m.healthScale(),-1,m.lastStyle(),true));
            assertNull(new PureSpacing().decide(at(s,s.tick(),s.player(),unknown),0));
            assertNull(new PureSpacing().decide(at(s,s.tick()+10,s.player(),s.mobs()),0));
        }
    }
}
