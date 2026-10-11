/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetainedRunRegressionTest {
    private JsonArray json(String name)throws Exception {
        try(Reader r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonArray();
        }
    }
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    List<Snapshot> scenes(String run)throws Exception {
        List<Snapshot> scenes=new ArrayList<>();
        for(JsonElement e:json(run+".json")) {
            JsonObject f=e.getAsJsonObject();int[][] flags=new int[104][104];
            for(JsonElement r:json(f.get("geometry").getAsString())) {
                JsonArray row=r.getAsJsonArray();for(int y=row.get(1).getAsInt();y<=row.get(2).getAsInt();y++)flags[row.get(0).getAsInt()][y]=row.get(3).getAsInt();
            }
            List<Mob> mobs=new ArrayList<>();
            for(JsonElement n:f.getAsJsonArray("mobs")) {
                JsonObject m=n.getAsJsonObject();mobs.add(new Mob(m.get("index").getAsInt(),Kind.valueOf(m.get("kind").getAsString()),
                    tile(m.getAsJsonArray("tile")),m.get("size").getAsInt(),m.get("healthRatio").getAsInt(),m.get("healthScale").getAsInt(),
                    m.get("attackTick").getAsInt(),Protection.valueOf(m.get("style").getAsString()),m.get("attackingPlayer").getAsBoolean()));
            }
            scenes.add(new Snapshot(f.get("tick").getAsInt(),tile(f.getAsJsonArray("player")),new CollisionGrid(flags),mobs,100,
                f.get("running").getAsBoolean(),f.get("range").getAsInt(),Protection.NONE).atWave(f.get("wave").getAsInt()));
        }
        return scenes;
    }
    private Snapshot at(Snapshot s,int tick,Tile player,List<Mob> mobs){return new Snapshot(tick,player,s.grid(),mobs,100,true,s.weaponRange(),Protection.NONE).atWave(s.wave());}
    @Test void firstCapeWallShotRetainsWorkingTrapAfterCompletion()throws Exception {
        for(Snapshot s:scenes("main-027"))if(s.wave()==58) {
            Mob shot=CaveSafety.retainedSafeShot(s,-1);assertNotNull(shot);assertEquals(57539,shot.index());
            LureController lure=new LureController();lure.finishMeleeTrap();Tile home=new Tile(54,36);
            Plan p=lure.decide(s,new CombatPlanner(),-1,List.of(home),0,home,home.add(8,17),null,home.add(-2,0),home.add(-1,-5));
            assertEquals(s.player(),p.destination());assertEquals(57539,p.targetIndex());
        }
    }
    @Test void secondCapeKeepsReachableShotsAndJadProtectionContracts()throws Exception {
        List<Snapshot> frames=scenes("main-029");assertTrue(frames.stream().anyMatch(s->s.wave()==63));
        for(Snapshot s:frames) {
            assertFalse(s.mobs().isEmpty());
            if(s.wave()==63){assertTrue(s.mobs().stream().anyMatch(m->m.kind()==Kind.JAD));continue;}
            Mob shot=CaveSafety.preferredShot(s,Protection.MAGIC);
            assertNotNull(shot,"Retained second-cape combat frame must offer a shot at tick "+s.tick());assertTrue(CombatPlanner.playerCanAttack(s,s.player(),shot));
        }
    }
    @Test void originalPureRangerReleaseRemainsGuardedAgainstAdditionalShooters()throws Exception {
        Snapshot s=scenes("pure-057").stream().filter(f->f.player().equals(new Tile(34,26))).findFirst().orElseThrow();
        Mob ranger=s.mobs().stream().filter(m->m.kind()==Kind.RANGER).findFirst().orElseThrow();
        assertTrue(PureSafety.releaseSafe(s,ranger,Protection.RANGE));assertTrue(new PureCombatPlanner().attackAllowed(s,ranger,Protection.RANGE,false));
        List<Mob> extra=new ArrayList<>(s.mobs());extra.add(new Mob(999,Kind.MAGER,new Tile(40,26),5,10,10,-1,Protection.MAGIC,true));
        assertFalse(PureSafety.releaseSafe(at(s,s.tick(),s.player(),extra),ranger,Protection.RANGE));
    }
    @Test void latestWave22FindsCheckedFullFiringApproachUnderPursuit()throws Exception {
        Snapshot s=scenes("pure-066").stream().filter(f->f.tick()==5362).findFirst().orElseThrow();
        assertEquals(new Tile(60,37),s.player());assertEquals(new Tile(60,23),s.mobs().get(0).tile());
        assertFalse(CombatPlanner.playerCanAttack(s,s.player(),s.mobs().get(0)));
        PureCombatRecovery recovery=new PureCombatRecovery();recovery.observe(s);s=at(s,s.tick()+24,s.player(),s.mobs());recovery.observe(s);
        Plan route=recovery.plan(s);assertNotNull(route);assertNotEquals(s.player(),route.destination());
        assertTrue(recovery.routeAllowed(s,route.destination()));assertTrue(CombatPlanner.actionable(MinimapMovement.checked(s,route.destination(),route.nextStep(),route.protection(),"full endpoint regression"),false));
        List<Tile> path=MinimapMovement.path(s,route.destination());assertTrue(path.size()>1);
        List<Mob> following=s.mobs();
        for(int i=1;i<path.size();i++)following=CombatPlanner.advance(s.grid(),following,path.get(i),Protection.NONE);
        Snapshot arrived=at(s,s.tick()+path.size(),route.destination(),following);recovery.observe(arrived);
        Plan shot=recovery.plan(arrived);assertNotNull(shot);assertEquals(60875,shot.targetIndex());
        assertTrue(new PureCombatPlanner().attackAllowed(arrived,following.get(0),shot.protection(),false));
        assertFalse(arrived.grid().melee(following.get(0),arrived.player()));
    }
    @Test void rejectedEndpointIsRememberedUntilMonsterGeometryChanges()throws Exception {
        Snapshot s=scenes("pure-066").get(0);PureCombatRecovery r=new PureCombatRecovery();r.observe(s);s=at(s,s.tick()+24,s.player(),s.mobs());r.observe(s);
        Plan first=r.plan(s);assertNotNull(first);r.rejected(s,first);s=at(s,s.tick()+1,s.player(),s.mobs());r.observe(s);Plan alternative=r.plan(s);assertNotNull(alternative);assertNotEquals(first.destination(),alternative.destination());
        java.lang.reflect.Field rejected=PureCombatRecovery.class.getDeclaredField("failed");rejected.setAccessible(true);
        assertTrue(((Set<?>)rejected.get(r)).contains(first.destination()));
        Mob m=s.mobs().get(0);Mob moved=new Mob(m.index(),m.kind(),m.tile().add(1,0),m.size(),m.healthRatio(),m.healthScale(),m.lastAttackTick(),m.lastStyle(),m.attackingPlayer());
        r.observe(at(s,s.tick()+1,s.player(),List.of(moved)));assertTrue(((Set<?>)rejected.get(r)).isEmpty());
    }
}
