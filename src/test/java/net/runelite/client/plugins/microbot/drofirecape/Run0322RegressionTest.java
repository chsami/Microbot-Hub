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

/** Regression scenes from the full 0.3.22 recording, plus explicit prayer phases. */
public class Run0322RegressionTest {
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
    @Test public void wave58ArrivalAtHomeAcceptsExistingTrapBeforeWestRelease()throws Exception {
        Snapshot before=frame("run0322-frames","run0322",7915),arrived=frame("run0322-frames","run0322",7930);
        RecordedMeleeTrap trap=new RecordedMeleeTrap();assertTrue(trap.start(before,home,-1));
        assertEquals(home,trap.plan(before).destination());
        Mob big=arrived.mobs().stream().filter(m->m.kind()==Kind.MELEER).findFirst().get();
        assertTrue(CaveSafety.trapped(arrived,big));assertTrue(CombatPlanner.playerCanAttack(arrived,arrived.player(),big));
        Plan p=trap.plan(arrived);assertFalse(trap.active());assertEquals(arrived.player(),p.destination());
        assertTrue(p.toString(),p.targetIndex()>=0);assertTrue(p.reason().contains("already trapped"));
    }
    @Test public void wave54RejectedRouteApproachesRangerBeforeShootableBigMelee()throws Exception {
        Snapshot f=frame("run0322-frames","run0322",7067);LureController l=new LureController();
        Plan p=l.recoverBlockedRoute(f,home);assertNotNull(p);assertTrue(CombatPlanner.actionable(p,false));
        Mob ranger=f.mobs().stream().filter(m->m.kind()==Kind.RANGER).findFirst().get();
        assertNotEquals("Big melee must not take the blocked ranger's priority",58827,p.targetIndex());
        assertTrue(p.toString(),CombatPlanner.playerCanAttack(f,p.destination(),ranger));
    }
    @Test public void wave47DeliberateMageApproachDoesNotCreateAnotherReturn()throws Exception {
        Snapshot start=frame("run0322-frames","run0322",5712),arrived=frame("run0322-frames","run0322",5722);
        LureController l=new LureController();
        Plan p=decide(l,start,-1);assertNotNull(p);assertEquals(p.toString(),arrived.player(),p.nextStep());
        l.observeFight(arrived,home,-1);assertFalse("Firing approach must not restart the old camp return",l.hasPendingReturn());
        p=decide(l,arrived,-1);assertNotNull(p);assertEquals(arrived.player(),p.destination());
    }
    private Snapshot timed(int tick,List<Mob> mobs) {
        return new Snapshot(tick,new Tile(30,30),new CollisionGrid(new int[64][64]),mobs,100,true,7,Protection.NONE);
    }
    @Test public void magicReturnsTwoTicksBeforeLaunchInsteadOfTakingLastMeleeGap() {
        for(boolean moving:List.of(false,true)) {
            TickProtection prayers=new TickProtection();
            List<Mob> mobs=List.of(new Mob(1,Kind.MAGER,new Tile(40,28),5,10,10,-1,Protection.MAGIC,true),
                new Mob(2,Kind.MELEER,new Tile(31,29),4,10,10,-1,Protection.MELEE,true));
            for(int tick=98;tick<=110;tick++) {
                if(tick>=100&&tick%4==0)prayers.animation(1,Kind.MAGER,2647);
                if(tick%4==3)prayers.animation(2,Kind.MELEER,2637);
                prayers.beginTick(tick,mobs);
            }
            assertEquals("Melee is due on 111, mage on 112: keep Magic instead of taking the last narrow gap",Protection.MAGIC,
                prayers.choose(timed(110,mobs),moving,Protection.MELEE,Protection.NONE).protection);
        }
    }
}
