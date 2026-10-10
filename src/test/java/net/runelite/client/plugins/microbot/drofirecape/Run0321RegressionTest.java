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

/** Replays the supplied south/north demonstrations and successful/failed mage cover. */
public class Run0321RegressionTest {
    private final Tile home=new Tile(54,36),north=home.add(8,17);
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
    @Test public void loneMagesOutsideBowRangeLeaveMarkerAndGetARealFiringTile()throws Exception {
        for(int tick:List.of(3024,4456,6586)) {
            Snapshot f=frame("run0321-frames","run0321",tick);Mob mage=f.mobs().get(0);
            LureController l=new LureController();l.recover();Plan p=decide(l,f,-1);
            assertNotNull("tick "+tick,p);assertTrue(CombatPlanner.actionable(p,false));
            assertNotEquals(f.player(),p.destination());
            assertTrue("tick "+tick+" "+p,CombatPlanner.playerCanAttack(f,p.destination(),mage));
            assertTrue(CaveSafety.clearOfMagers(f,f.mobs(),p.destination()));
        }
    }
    @Test public void wave54BlockedRangerIsApproachedBeforeShootableBigMelee()throws Exception {
        Snapshot f=frame("run0321-frames","run0321",6858);LureController l=new LureController();
        l.finishMeleeTrap();Plan p=decide(l,f,50056);
        assertNotNull(p);assertNotEquals(50056,p.targetIndex());assertNotEquals(f.player(),p.destination());
        Mob ranger=f.mobs().stream().filter(m->m.kind()==Kind.RANGER).findFirst().get();
        assertTrue(CombatPlanner.playerCanAttack(f,p.destination(),ranger));
    }
    @Test public void blobDeathWaitsAtWorkingWallForSplitsInsteadOfOldCentreRoute()throws Exception {
        for(int[] pair:new int[][]{{1531,1537},{2614,2620}}) {
            LureController l=new LureController();Snapshot before=frame("run0321-frames","run0321",pair[0]);
            l.observeFight(before,home,-1);l.finishMeleeTrap();
            Snapshot after=frame("run0321-frames","run0321",pair[1]);Plan p=decide(l,after,-1);
            assertNotNull(p);assertEquals(after.player(),p.destination());
        }
    }
    @Test public void mageDeathReleasesHoldAndApproachesLastBlockedMelee()throws Exception {
        Snapshot f=frame("run0321-frames","run0321",5737);LureController l=new LureController();l.finishMeleeTrap();
        Plan p=decide(l,f,-1);assertNotNull(p);assertNotEquals(f.player(),p.destination());
        assertTrue(p.toString(),CombatPlanner.playerCanAttack(f,p.destination(),f.mobs().get(0)));
    }
    @Test public void nearbyFixedWallCanTrapFollowingMeleeWithoutRepeatingNorth()throws Exception {
        for(int tick:List.of(5406,5961)) {
            Snapshot original=frame("run0321-frames","run0321",tick);
            List<Mob> mobs=new ArrayList<>();for(Mob m:original.mobs())if(m.kind()!=Kind.BAT)mobs.add(m);
            Snapshot f=at(original,tick,original.player(),mobs);
            Tile wall=CaveSafety.nearbyWallTrap(f,home);assertNotNull("tick "+tick,wall);
            assertNotEquals(north,wall);assertTrue(wall.distance(f.player())<=8);
            LureController l=new LureController();l.observeFight(f,home,-1);Plan p=decide(l,f,-1);
            assertTrue(l.hasPendingReturn());assertEquals(wall,p.destination());
        }
    }
    @Test public void trappedMediumBlobShotIsNotBlockedByOutOfRangeMage()throws Exception {
        Snapshot f=frame("run0321-frames","run0321",4427);LureController l=new LureController();l.finishMeleeTrap();
        Plan p=decide(l,f,-1);assertNotNull(p);Mob blob=f.mobs().stream().filter(m->m.kind()==Kind.BLOB).findFirst().get();
        assertTrue(p.toString(),p.targetIndex()==41976||CombatPlanner.playerCanAttack(f,p.destination(),blob));
    }
}
