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
import java.lang.reflect.*;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class Run0329RegressionTest {
    private BufferedReader resource(String name){return new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(getClass().getResourceAsStream(name)),StandardCharsets.UTF_8));}
    private Tile tile(JsonArray a){return new Tile(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private List<Snapshot> wallFrames()throws Exception {
        int[][] flags=new int[104][104];try(BufferedReader r=resource("run0327-collision.csv")) {
            r.readLine();String line;while((line=r.readLine())!=null){String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);}
        }
        List<Snapshot> frames=new ArrayList<>();CollisionGrid grid=new CollisionGrid(flags);
        try(Reader r=resource("run0327-wall-shot.json")) {
            for(JsonElement value:new JsonParser().parse(r).getAsJsonArray()) {
                JsonObject e=value.getAsJsonObject();List<Mob> mobs=new ArrayList<>();
                for(JsonElement n:e.getAsJsonArray("mobs")) {
                    JsonObject m=n.getAsJsonObject();mobs.add(new Mob(m.get("index").getAsInt(),Kind.valueOf(m.get("kind").getAsString()),
                        tile(m.getAsJsonArray("tile")),m.get("size").getAsInt(),m.get("healthRatio").getAsInt(),m.get("healthScale").getAsInt(),
                        m.get("attackTick").getAsInt(),Protection.valueOf(m.get("style").getAsString()),m.get("attackingPlayer").getAsBoolean()));
                }
                frames.add(new Snapshot(e.get("tick").getAsInt(),tile(e.getAsJsonArray("player")),grid,mobs,100,
                    e.get("running").getAsBoolean(),e.get("range").getAsInt(),Protection.MAGIC).atWave(e.get("wave").getAsInt()));
            }
        }
        return frames;
    }
    private Plan decide(LureController l,Snapshot s,int target) {
        Tile home=new Tile(54,36);return l.decide(s,new CombatPlanner(),target,List.of(home),0,home,home.add(8,17),null,home.add(-2,0),home.add(-1,-5));
    }
    @Test public void wave58WallShotSurvivesTrapCompletionAndDelayedAttackAcknowledgement()throws Exception {
        LureController l=new LureController();l.finishMeleeTrap();
        for(Snapshot s:wallFrames())for(int interaction:List.of(-1,57539)) {
            Mob retained=CaveSafety.retainedSafeShot(s,interaction);assertNotNull("tick "+s.tick(),retained);assertEquals(57539,retained.index());
            Plan p=decide(l,s,interaction);assertEquals(s.player(),p.destination());assertEquals(57539,p.targetIndex());
        }
    }
    @Test public void fastAttackPathAlsoRetainsWallShotInsteadOfRejectingBlockedMage()throws Exception {
        Snapshot s=wallFrames().get(1);FcFrame f=RecoveryPolicyTest.frame(s.tick(),66,100);
        RecoveryPolicyTest.set(f,"model",s);RecoveryPolicyTest.set(f,"presentNpcIndices",Set.of(57538,57539,57541));
        RecoveryPolicyTest.set(f,"interactingIndex",-1);DroFirecapeScript script=new DroFirecapeScript();FcActions actions=mock(FcActions.class);
        RecoveryPolicyTest.set(script,"actions",actions);RecoveryPolicyTest.set(script,"config",mock(DroFirecapeConfig.class));
        RecoveryPolicyTest.set(script,"enabled",true);RecoveryPolicyTest.set(script,"frame",f);
        FcTickPrayers owner=mock(FcTickPrayers.class);when(owner.ownsInput()).thenReturn(true);when(owner.protectionReady()).thenReturn(true);
        when(owner.attackInputWindow(anyInt(),anyLong())).thenReturn(true);RecoveryPolicyTest.set(script,"tickPrayers",owner);
        when(actions.attackProtection(any())).thenReturn(Protection.MAGIC);when(actions.combatProtect(any())).thenReturn(true);
        when(actions.overheadActive(any())).thenReturn(true);when(actions.attack(eq(57539),anyString(),eq(true),eq(f))).thenReturn(true);
        Method m=DroFirecapeScript.class.getDeclaredMethod("tryImmediateAttack",FcFrame.class,Snapshot.class,Protection.class);m.setAccessible(true);
        // This fixture exercises target retention with an already-open owner window.
        // The client's background wait helper is outside that policy boundary.
        try(org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.Global> wait=
            mockStatic(net.runelite.client.plugins.microbot.util.Global.class)) {
            assertTrue((Boolean)m.invoke(script,f,s,Protection.NONE));
        }
        verify(actions).attack(eq(57539),anyString(),eq(true),eq(f));verify(actions,never()).move(any(),any());
    }
    @Test public void retainedShotReleasesOnDeathRangeLossOrCurrentContact()throws Exception {
        Snapshot s=wallFrames().get(0);Mob big=s.mobs().stream().filter(m->m.kind()==Kind.MELEER).findFirst().get();
        List<Mob> dead=new ArrayList<>(s.mobs());dead.remove(big);
        assertNull(CaveSafety.retainedSafeShot(new Snapshot(s.tick()+1,s.player(),s.grid(),dead,100,true,5,Protection.MAGIC),big.index()));
        assertNull(CaveSafety.retainedSafeShot(new Snapshot(s.tick()+1,new Tile(70,70),s.grid(),s.mobs(),100,true,5,Protection.MAGIC),big.index()));
        Snapshot contact=new Snapshot(100,new Tile(50,50),new CollisionGrid(new int[104][104]),
            List.of(new Mob(big.index(),Kind.MELEER,new Tile(46,50),4,10,10,-1,Protection.MELEE,true)),100,false,5,Protection.NONE);
        assertNull(CaveSafety.retainedSafeShot(contact,big.index()));
    }
    private Snapshot ranks(int wave,boolean attacking) {
        Tile player=new Tile(50,50);return new Snapshot(100,player,new CollisionGrid(new int[104][104]),List.of(
            new Mob(1,Kind.RANGER,new Tile(43,50),3,10,10,99,Protection.RANGE,attacking),
            new Mob(2,Kind.BAT,new Tile(50,49),1,10,10,-1,Protection.MELEE,true)),100,false,7,Protection.NONE).atWave(wave);
    }
    @Test public void onlyWaves56Through60PromoteCurrentlyAttackingRangerOverBat() {
        for(int wave:List.of(55,56,57,58,59,60,61))for(boolean attacking:List.of(false,true)) {
            Snapshot s=ranks(wave,attacking);int expected=attacking&&wave>=56&&wave<=60?1:2;
            assertEquals(expected,CaveSafety.preferredShot(s,Protection.RANGE).index());
            Plan lure=new LureController().decide(s,new CombatPlanner(),2,List.of(s.player()),0,s.player(),null,null,s.player().add(-2,0));
            assertEquals("wave "+wave+" attacking "+attacking,expected,lure.targetIndex());
        }
    }
    @Test public void liveBigMeleeWinsRangeButSmallContactAndTrappedMeleeDoNot() {
        Snapshot base=ranks(55,true);Mob ranger=base.mobs().get(0);
        Mob big=new Mob(3,Kind.MELEER,new Tile(46,50),4,10,10,-1,Protection.MELEE,true);
        Snapshot conflict=new Snapshot(100,base.player(),base.grid(),List.of(ranger,big),100,false,7,Protection.NONE);
        assertEquals(Protection.MELEE,HeldProtection.choose(conflict,Protection.RANGE,Protection.RANGE));
        assertEquals(Protection.MELEE,MinimapMovement.routeProtection(conflict,base.player(),Protection.RANGE));
        assertEquals(Protection.RANGE,HeldProtection.choose(base,Protection.MELEE,Protection.MELEE));
        Snapshot distant=new Snapshot(100,base.player(),base.grid(),List.of(ranger,big.at(new Tile(10,10))),100,false,7,Protection.NONE);
        assertEquals(Protection.RANGE,HeldProtection.choose(distant,Protection.MELEE,Protection.MELEE));
    }
}
