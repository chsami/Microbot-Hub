package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class HealerControllerTest {
    private static Object get(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,args);
    }
    private static Mob healer(int index,boolean tagged) {
        return new Mob(index,Kind.HEALER,new Tile(index==1?28:34,30),1,tagged?1:10,10,-1,Protection.MELEE,tagged);
    }
    private static final class Fixture implements AutoCloseable {
        final DroFirecapeScript script=new DroFirecapeScript();
        final FcActions actions=mock(FcActions.class);
        final FcTickPrayers driver=mock(FcTickPrayers.class);
        final DroFirecapeConfig config=mock(DroFirecapeConfig.class);
        final HealerGroup group;
        final MockedStatic<InputArbiter> input=mockStatic(InputArbiter.class);
        final boolean paused=Microbot.pauseAllScripts.get();
        Fixture()throws Exception {
            Microbot.pauseAllScripts.set(false);input.when(InputArbiter::isHuman).thenReturn(false);
            RecoveryPolicyTest.set(script,"actions",actions);RecoveryPolicyTest.set(script,"tickPrayers",driver);
            RecoveryPolicyTest.set(script,"config",config);RecoveryPolicyTest.set(script,"enabled",true);
            when(actions.combatProtect(any())).thenReturn(true);when(actions.overheadActive(any())).thenReturn(true);
            when(actions.attackProtection(any())).thenReturn(Protection.MAGIC);
            when(actions.attack(anyInt(),anyString(),anyBoolean(),any())).thenReturn(true);
            when(actions.move(any(),any(),anyBoolean())).thenReturn(true);
            when(driver.ownsInput()).thenReturn(true);when(driver.protectionReady()).thenReturn(true);
            when(driver.movementReady(any())).thenReturn(true);when(driver.requested()).thenReturn(Protection.MAGIC);
            group=(HealerGroup)get(script,"healers");
        }
        FcFrame frame(int tick,boolean firstTagged,boolean secondTagged)throws Exception {
            FcFrame f=RecoveryPolicyTest.frame(tick,99,100);
            Snapshot model=new Snapshot(tick,new Tile(30,30),new CollisionGrid(new int[64][64]),
                List.of(new Mob(45,Kind.JAD,new Tile(40,30),5,29,30,tick-2,Protection.MAGIC,true),
                    healer(1,firstTagged),healer(2,secondTagged)),100,false,7,Protection.MAGIC);
            RecoveryPolicyTest.set(f,"model",model);RecoveryPolicyTest.set(f,"capturedAt",System.currentTimeMillis());
            RecoveryPolicyTest.set(script,"frame",f);RecoveryPolicyTest.set(script,"jadAttackTick",tick-2);
            group.observe(model,Set.of());return f;
        }
        boolean handle(FcFrame f)throws Exception {
            return (Boolean)call(script,"handleHealers",new Class<?>[]{FcFrame.class,Protection.class},f,Protection.MAGIC);
        }
        public void close(){input.close();Microbot.pauseAllScripts.set(paused);}
    }
    @Test public void firstTagConfirmationSelectsTheNextHealerBeforeAnyPullOrKill()throws Exception {
        try(Fixture f=new Fixture()) {
            f.frame(100,false,false);FcFrame current=f.frame(101,true,false);
            assertTrue(f.handle(current));assertEquals(2,f.group.pending());
            verify(f.actions).attack(eq(2),anyString(),eq(true),eq(current));
            verify(f.actions,never()).attack(eq(1),anyString(),anyBoolean(),any());
            verify(f.actions,never()).move(any(),any(),anyBoolean());assertNull(get(f.script,"healerRetreat"));
        }
    }
    @Test public void acknowledgedTagStopsExtraAutoAttacksWhileOtherHealersRemain()throws Exception {
        try(Fixture f=new Fixture()) {
            f.frame(100,false,false);FcFrame current=f.frame(101,true,false);
            RecoveryPolicyTest.set(current,"interactingIndex",1);
            assertTrue((Boolean)call(f.script,"cancelTaggedHealerAttack",new Class<?>[]{FcFrame.class},current));
            verify(f.actions).move(current,current.model.player(),false);
            assertFalse((Boolean)call(f.script,"cancelTaggedHealerAttack",new Class<?>[]{FcFrame.class},current));
            assertTrue(f.handle(current));verify(f.actions).attack(eq(2),anyString(),eq(true),eq(current));
        }
    }
    @Test public void jadWindupAndUnacknowledgedPrayerPreemptTheNextTag()throws Exception {
        try(Fixture f=new Fixture()) {
            f.frame(100,false,false);FcFrame current=f.frame(101,true,false);
            RecoveryPolicyTest.set(f.script,"jadAttackTick",101);assertTrue(f.handle(current));
            verify(f.actions,never()).attack(anyInt(),anyString(),anyBoolean(),any());
            current=f.frame(102,true,false);when(f.actions.overheadActive(any())).thenReturn(false);
            assertTrue(f.handle(current));verify(f.actions,never()).attack(anyInt(),anyString(),anyBoolean(),any());
            assertEquals(-1,f.group.pending());
        }
    }
    @Test public void returnedHealerCancelsAlreadyCommittedGroupMovement()throws Exception {
        try(Fixture f=new Fixture()) {
            f.frame(100,false,false);f.frame(101,true,true);FcFrame current=f.frame(102,true,true);
            assertEquals(HealerGroup.Phase.LURING,f.group.phase());
            Tile goal=new Tile(27,30);RecoveryPolicyTest.set(f.script,"healerRetreat",goal);
            MovementAck movement=(MovementAck)get(f.script,"movement");movement.sent(current.model.player(),goal,current.tick,List.of(current.model.player(),goal));
            f.group.observe(current.model,Set.of(2));
            assertTrue((Boolean)call(f.script,"cancelHealerRetreat",new Class<?>[]{FcFrame.class},current));
            assertFalse(movement.pending());assertNull(get(f.script,"healerRetreat"));
            verify(f.actions).move(current,current.model.player(),false);
        }
    }
    @Test public void newlySpawnedHealerAtFinalMovementGatePreventsAnOldPullDispatch()throws Exception {
        try(Fixture f=new Fixture()) {
            f.frame(100,false,false);f.frame(101,true,true);FcFrame current=f.frame(102,true,true);
            Tile goal=new Tile(30,32);RecoveryPolicyTest.set(f.script,"healerRetreat",goal);
            when(f.actions.overheadActive(any())).thenAnswer(i->{f.group.spawned(3,102);return true;});
            Plan pull=new Plan(goal,goal,Protection.MAGIC,-1,true,0,0,0,"Collect tagged healers");
            call(f.script,"movePlan",new Class<?>[]{FcFrame.class,Plan.class},current,pull);
            verify(f.actions,never()).move(any(),any(),anyBoolean());assertEquals(HealerGroup.Phase.TAGGING,f.group.phase());
        }
    }
    @Test public void pendingTagWaitsForAggroWithoutRepeatedTargetClicks()throws Exception {
        try(Fixture f=new Fixture()) {
            FcFrame current=f.frame(100,false,false);f.group.tagRequested(2,100);
            assertTrue(f.handle(current));verify(f.actions,never()).attack(anyInt(),anyString(),anyBoolean(),any());
            assertTrue(f.script.status().contains("tag reaches the server"));
        }
    }
}
