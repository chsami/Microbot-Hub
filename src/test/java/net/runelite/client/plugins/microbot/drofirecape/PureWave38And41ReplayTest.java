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

/** Frames from the 2026-10-20 pure run (pure-071). Wave 41: a firing approach to the
 * Tok-Xil started on the Ket-Zek's diagonal corner and ran its whole south edge, taking
 * 22 and 23 melee. Wave 38: the lure leg to the north was refused outright, which left
 * the player standing while the Tok-Xil and Ket-Zek closed in. */
class PureWave38And41ReplayTest {
    private static JsonArray json(String name)throws Exception {
        try(Reader r=new InputStreamReader(Objects.requireNonNull(PureWave38And41ReplayTest.class.getResourceAsStream(name),name),StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonArray();
        }
    }
    private static Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    static Snapshot scene(int tick)throws Exception {return scene("pure-071",tick);}
    static Snapshot scene(String run,int tick)throws Exception {
        for(JsonElement e:json(run+".json")) {
            JsonObject f=e.getAsJsonObject();if(f.get("tick").getAsInt()!=tick)continue;
            int[][] flags=new int[104][104];
            for(JsonElement r:json(f.get("geometry").getAsString())) {
                JsonArray row=r.getAsJsonArray();
                for(int y=row.get(1).getAsInt();y<=row.get(2).getAsInt();y++)flags[row.get(0).getAsInt()][y]=row.get(3).getAsInt();
            }
            List<Mob> mobs=new ArrayList<>();
            for(JsonElement n:f.getAsJsonArray("mobs")) {
                JsonObject m=n.getAsJsonObject();
                mobs.add(new Mob(m.get("index").getAsInt(),Kind.valueOf(m.get("kind").getAsString()),tile(m.getAsJsonArray("tile")),
                    m.get("size").getAsInt(),m.get("healthRatio").getAsInt(),m.get("healthScale").getAsInt(),m.get("attackTick").getAsInt(),
                    Protection.valueOf(m.get("style").getAsString()),m.get("attackingPlayer").getAsBoolean()));
            }
            return new Snapshot(tick,tile(f.getAsJsonArray("player")),new CollisionGrid(flags),mobs,100,
                f.get("running").getAsBoolean(),f.get("range").getAsInt(),Protection.NONE).atWave(f.get("wave").getAsInt());
        }
        throw new AssertionError("Missing fixture tick "+tick);
    }
    static Mob kind(Snapshot s,Kind kind){return s.mobs().stream().filter(m->m.kind()==kind).findFirst().orElseThrow();}
    private static Snapshot at(Snapshot s,Tile player){return new Snapshot(s.tick(),player,s.grid(),s.mobs(),s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle()).atWave(s.wave());}
    /** Tiles of the predicted walk, after the start, that touch the Ket-Zek's melee edge. */
    private static List<Tile> contactTiles(Snapshot s,Plan p) {
        Mob mage=kind(s,Kind.MAGER);List<Tile> out=new ArrayList<>();
        List<Tile> route=MinimapMovement.path(s,p.nextStep());
        for(int i=1;i<route.size();i++)if(s.grid().melee(mage,route.get(i)))out.add(route.get(i));
        return out;
    }

    @Test void ketZekCornerDoesNotLicenseAWalkAlongItsEdge()throws Exception {
        Snapshot s=scene(8831);Mob mage=kind(s,Kind.MAGER);
        assertEquals(new Tile(59,61),s.player());
        assertFalse(s.grid().melee(mage,s.player()),"The diagonal corner is not melee contact");
        Tile edge=new Tile(58,61);
        assertTrue(s.grid().melee(mage,edge));
        assertFalse(CaveSafety.mageStep(s,s.mobs(),s.player(),edge));
        assertTrue(CaveSafety.mageStep(s,s.mobs(),s.player(),new Tile(58,60)),"Stepping away from the corner stays legal");
    }
    @Test void wave41RangerApproachNeverRunsTheKetZekEdge()throws Exception {
        for(int tick:new int[]{8831,8832}) {
            Snapshot s=scene(tick);Mob ranger=kind(s,Kind.RANGER);
            for(Tile main:Arrays.asList(null,RecordedLureBook.ITALY)) {
                Plan p=PureCombatPolicy.approach(s,ranger,main,"replay");
                if(p==null||!CombatPlanner.actionable(p,false)||p.nextStep().equals(s.player()))continue;
                assertEquals(List.of(),contactTiles(s,p),"tick "+tick+" main "+main+" plan "+p);
            }
        }
    }
    @Test void minimapRouteToTheRecordedStepIsRefused()throws Exception {
        Snapshot s=scene(8831);
        Plan p=MinimapMovement.checked(s,new Tile(40,71),new Tile(46,66),Protection.MAGIC,"replay");
        if(CombatPlanner.actionable(p,false))assertEquals(List.of(),contactTiles(s,p),String.valueOf(p));
    }
    private static Tile leg(Snapshot s,Plan full,Protection protection) {
        return PureSafety.allowedLeg(s,full.nextStep(),t->PureSafety.routeAllowed(s,t),
            t->CombatPlanner.actionable(MinimapMovement.checked(s,full.destination(),t,protection,"replay"),false));
    }
    @Test void wave38RefusedLureLegStillAdvancesAlongTheSameWalk()throws Exception {
        Snapshot s=scene(7664);
        assertEquals(new Tile(54,36),s.player());
        for(Protection protection:new Protection[]{Protection.NONE,Protection.MAGIC}) {
            Plan full=MinimapMovement.route(s,new Tile(53,26),new Tile(53,36),protection,false,"Luring through checked cover");
            assertTrue(CombatPlanner.actionable(full,false),String.valueOf(full));
            assertFalse(PureSafety.routeAllowed(s,full.nextStep()),"The recorded full leg is the one the pure check refused");
            Tile leg=leg(s,full,protection);
            assertNotNull(leg,"A shorter part of the lure walk passes the same checks");
            assertTrue(leg.distance(s.player())>=2,"No one-tile shuffle: "+leg);
            assertTrue(MinimapMovement.path(s,full.nextStep()).contains(leg),"Leg lies on the refused walk");
            assertTrue(PureSafety.routeAllowed(s,leg));
        }
    }
    @Test void shortenedLegKeepsTheMeleeFollowerMinimum() {
        CollisionGrid open=new CollisionGrid(new int[104][104]);
        Snapshot alone=new Snapshot(1,new Tile(20,20),open,List.of(new Mob(1,Kind.RANGER,new Tile(40,40),3,-1,-1,-1,Protection.RANGE,true)),100,true,5,Protection.NONE);
        assertEquals(new Tile(22,20),PureSafety.allowedLeg(alone,new Tile(30,20),t->t.x()<=22,t->true));
        assertNull(PureSafety.allowedLeg(alone,new Tile(30,20),t->t.x()<=21,t->true),"One tile is a shuffle, not a leg");
        Snapshot followed=new Snapshot(1,new Tile(20,20),open,List.of(new Mob(1,Kind.BLOB,new Tile(16,16),2,-1,-1,-1,Protection.MELEE,true)),100,true,5,Protection.NONE);
        assertNull(PureSafety.allowedLeg(followed,new Tile(30,20),t->t.x()<=22,t->true));
        assertEquals(new Tile(24,20),PureSafety.allowedLeg(followed,new Tile(30,20),t->t.x()<=24,t->true));
        assertNull(PureSafety.allowedLeg(followed,new Tile(30,20),t->true,t->false),"Every leg must also be dispatchable");
    }
    @Test void realContactStillEscapes()throws Exception {
        Snapshot s=at(scene(8831),new Tile(56,61));Mob mage=kind(s,Kind.MAGER);
        assertTrue(s.grid().melee(mage,s.player()));
        Plan escape=CaveSafety.escapeMage(s);
        assertNotNull(escape);assertTrue(CombatPlanner.actionable(escape,false));
        assertTrue(CaveSafety.clearOfMagers(s,s.mobs(),escape.nextStep()));
        assertEquals(List.of(),contactTiles(s,escape));
    }
}
