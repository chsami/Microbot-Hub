package net.runelite.client.plugins.microbot.drofirecape.optional;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.CollisionGrid;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.Protection;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.Mob;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.Kind;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.Tile;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.OneTickPrayerCycle;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VisibleTickPrayerTest {
    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);
        final FcActions actions=mock(FcActions.class);
        final AtomicLong clock=new AtomicLong(1_000_000_000L);
        final java.util.ArrayList<String> logs=new java.util.ArrayList<>();
        final FcTickPrayers driver=new FcTickPrayers(client,false,logs::add,clock::get);
        final MockedStatic<Microbot> microbot=mockStatic(Microbot.class);
        Fixture() {
            ClientThread thread=mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call->
                Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            when(client.isClientThread()).thenReturn(true);
            when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(99);
            Widget widget=mock(Widget.class);when(client.getWidget(anyInt())).thenReturn(widget);
            when(actions.tickPrayer(any(),any(),any(),any())).thenReturn(FcPrayerUi.Result.SENT);
        }
        FcFrame frame(int tick)throws Exception {return frame(tick,List.of());}
        FcFrame frame(int tick,List<Mob> mobs)throws Exception {
            return frame(tick,mobs,new Tile(28,28),new CollisionGrid(new int[64][64]));
        }
        FcFrame frame(int tick,List<Mob> mobs,Protection jadStyle)throws Exception {
            return frame(tick,mobs,new Tile(28,28),new CollisionGrid(new int[64][64]),jadStyle);
        }
        FcFrame frame(int tick,List<Mob> mobs,Tile position,CollisionGrid grid)throws Exception {
            return frame(tick,mobs,position,grid,Protection.MAGIC);
        }
        FcFrame frame(int tick,List<Mob> mobs,Tile position,CollisionGrid grid,Protection jadStyle)throws Exception {
            WorldView view=mock(WorldView.class);Player player=mock(Player.class);
            when(client.getTickCount()).thenReturn(tick);when(client.getWorld()).thenReturn(630);
            when(client.getWorldView(anyInt())).thenReturn(view);when(client.getTopLevelWorldView()).thenReturn(view);
            when(view.getBaseX()).thenReturn(10304);when(view.getBaseY()).thenReturn(5248);
            when(view.isInstance()).thenReturn(true);
            int[][][] chunks=new int[4][13][13];
            for(int x=0;x<13;x++)for(int y=0;y<13;y++)chunks[0][x][y]=((2368/8+x)<<14)|((5056/8+y)<<3);
            when(view.getInstanceTemplateChunks()).thenReturn(chunks);
            when(player.getLocalLocation()).thenReturn(new LocalPoint(position.x()*128+64,position.y()*128+64,WorldView.TOPLEVEL));
            when(player.getWorldLocation()).thenReturn(new WorldPoint(10332,5276,0));
            Constructor<FcFrame> ctor=FcFrame.class.getDeclaredConstructor(Client.class,WorldView.class,Player.class,
                List.class,CollisionGrid.class,Protection.class,Map.class,int.class,Set.class,Map.class,boolean.class);
            ctor.setAccessible(true);
            FcFrame frame=ctor.newInstance(client,view,player,mobs,grid,
                jadStyle,Map.of(),7,Set.of(),Map.of(),false);
            assertTrue(frame.cave);return frame;
        }
        void begin(int tick)throws Exception {driver.gameTick(frame(tick),Protection.MAGIC,clock.get());clock.addAndGet(30_000_000L);}
        void plan(){driver.clientTick(true,false,false);}
        public void close(){microbot.close();}
    }
    @Test public void pureCanActAtZeroPrayerWithoutClaimingProtectionAndResumesPrayerWhenRestored()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.pureTinyPrayers(true);f.driver.nativeWidgets(true);
            when(f.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
            f.begin(100);f.plan();
            assertFalse(f.driver.protectionReady());
            assertTrue(f.driver.exhaustedCombatReady());
            assertEquals(Protection.NONE,f.driver.requested());
            assertTrue(f.driver.attackInputWindow(100,180));
            assertTrue(f.driver.optionalInputWindow(100,900));
            assertTrue(f.driver.movementReady(Protection.MAGIC));
            verify(f.client,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
            f.driver.clientTick(false,false,false);assertFalse(f.driver.exhaustedCombatReady());
            assertFalse(f.driver.attackInputWindow(100,180));
            when(f.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(1);
            f.clock.addAndGet(570_000_000L);f.begin(101);f.plan();
            assertFalse(f.driver.exhaustedCombatReady());
            assertEquals(Protection.MAGIC,f.driver.requested());
        }
    }
    @Test public void zeroPrayerBypassNeverAppliesOutsidePureMode()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
            f.begin(100);f.plan();
            assertFalse(f.driver.exhaustedCombatReady());
            assertFalse(f.driver.attackInputWindow(100,180));
            assertFalse(f.driver.movementReady(Protection.MAGIC));
        }
    }
    @Test public void nativeOptionDispatchesInClientTickWithoutVisibleMouseWork()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(f.client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(f.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
            f.driver.nativeWidgets(true);f.begin(100);f.plan();f.driver.pulse(f.actions);
            verify(f.client).menuAction(-1,Rs2PrayerEnum.PROTECT_MAGIC.getIndex(),MenuAction.CC_OP,1,-1,
                "Activate",Rs2PrayerEnum.PROTECT_MAGIC.getName());
            verifyNoInteractions(f.actions);
            f.plan();verify(f.client,times(1)).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
            assertFalse("A dispatched widget request still needs observed acknowledgement",f.driver.protectionReady());
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            f.plan();assertTrue(f.driver.protectionReady());
        }
    }
    @Test public void betaMovementDoesNotHoldPrayerThroughVerifiedCooldownGap()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(f.client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(f.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
            java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger(1);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenAnswer(call->active.get());
            doAnswer(call->{active.set("Activate".equals(call.getArgument(5))?1:0);return null;})
                .when(f.client).menuAction(anyInt(),eq(Rs2PrayerEnum.PROTECT_MAGIC.getIndex()),any(),anyInt(),anyInt(),anyString(),anyString());
            f.driver.nativeWidgets(true);
            Mob mage=new Mob(1,Kind.MAGER,new Tile(33,28),5,10,10,-1,Protection.MAGIC,true);
            for(int tick=100;tick<=110;tick++) {
                if(tick==101||tick==105||tick==109)f.driver.npcAnimation(1,Kind.MAGER,2647);
                f.driver.gameTick(f.frame(tick,List.of(mage)),Protection.NONE,f.clock.get());
                f.clock.addAndGet(30_000_000L);f.plan();if(tick<110)f.clock.addAndGet(570_000_000L);
            }
            f.driver.primeMovement(110,Protection.MAGIC);f.plan();
            assertEquals(Protection.NONE,f.driver.requested());
            assertTrue(f.driver.movementReady(Protection.MAGIC));
            assertTrue(f.driver.status().contains("BETA"));
            verify(f.client,atLeastOnce()).menuAction(-1,Rs2PrayerEnum.PROTECT_MAGIC.getIndex(),MenuAction.CC_OP,1,-1,
                "Deactivate",Rs2PrayerEnum.PROTECT_MAGIC.getName());
            verifyNoInteractions(f.actions);
        }
    }
    @Test public void enablingBetaInvalidatesAnInFlightVisiblePrayer()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(100);f.plan();
            when(f.actions.tickPrayer(any(),any(),any(),any())).thenAnswer(call->{
                f.driver.nativeWidgets(true);
                java.util.function.BooleanSupplier valid=call.getArgument(3);
                assertFalse(valid.getAsBoolean());
                return FcPrayerUi.Result.SKIPPED;
            });
            f.driver.pulse(f.actions);assertFalse(f.driver.protectionReady());
            f.driver.pulse(f.actions);verify(f.actions,times(1)).tickPrayer(any(),any(),any(),any());
        }
    }
    @Test public void clientTickQueuesOnlyAndProtectionNeedsObservedOn()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();assertFalse(f.driver.protectionReady());verifyNoInteractions(f.actions);
            verify(f.client,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
            f.driver.pulse(f.actions);assertFalse(f.driver.protectionReady());
            verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_MAGIC),any(),any());
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            f.plan();assertTrue(f.driver.protectionReady());
        }
    }
    @Test public void detachedOwnerDropsQueuedInputAndIgnoresEveryLaterCallback()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();f.driver.detach();
            f.driver.pulse(f.actions);f.driver.gameTick(f.frame(11),Protection.MAGIC,f.clock.get());
            f.driver.clientTick(true,true,false);f.driver.pulse(f.actions);
            assertFalse(f.driver.ownsInput());assertFalse(f.driver.protectionReady());verifyNoInteractions(f.actions);
        }
    }
    @Test public void observedJadWindupPreemptsAQueuedPrayerDuringHealerCollection()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();
            f.driver.npcAnimation(1,Kind.JAD,2652);f.driver.pulse(f.actions);
            assertFalse(f.driver.protectionReady());verifyNoInteractions(f.actions);
            List<Mob> mobs=List.of(new Mob(1,Kind.JAD,new Tile(33,28),5,20,30,10,Protection.RANGE,true),
                new Mob(2,Kind.HEALER,new Tile(30,30),1,10,10,-1,Protection.MELEE,true),
                new Mob(3,Kind.HEALER,new Tile(33,27),1,10,10,-1,Protection.MELEE,false));
            f.clock.addAndGet(570_000_000L);f.driver.gameTick(f.frame(11,mobs),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);
            assertEquals(Protection.RANGE,f.driver.requested());
            verify(f.actions).tickPrayer(any(),eq(Rs2PrayerEnum.PROTECT_RANGE),any(),any());
        }
    }
    private List<Mob> healerGapMobs(boolean aggro) {
        return healerGapMobs(aggro,Protection.MAGIC);
    }
    private List<Mob> healerGapMobs(boolean aggro,Protection style) {
        return List.of(new Mob(1,Kind.JAD,new Tile(33,28),5,20,30,17,style,true),
            new Mob(2,Kind.HEALER,new Tile(27,28),1,10,10,-1,Protection.MELEE,aggro));
    }
    private void calibrateJad(Fixture f,boolean magicLast)throws Exception {
        for(int tick=1;tick<=22;tick++) {
            boolean magic=tick<9||tick>=17&&magicLast;
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(magic?1:0);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_RANGE.getVarbit())).thenReturn(magic?0:1);
            if(tick==1||tick==9||tick==17)f.driver.npcAnimation(1,Kind.JAD,magic?2656:2652);
            f.clock.set(1_000_000_000L+(tick-1)*600_000_000L);
            Protection style=magic?Protection.MAGIC:Protection.RANGE;
            f.driver.gameTick(f.frame(tick,healerGapMobs(true,style),style),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);
            assertEquals("Observed Jad style must agree with the frame during calibration",style,f.driver.requested());
        }
        verifyNoInteractions(f.actions); // The matching observed guard needs no switch during calibration.
    }
    @Test public void calibratedHealerGapUsesOneMeleeClickAndRearmsJadOnTheNextTick()throws Exception {
        try(Fixture f=new Fixture()) {
            calibrateJad(f,true);f.clock.set(14_200_000_000L);
            f.driver.gameTick(f.frame(23,healerGapMobs(false)),Protection.NONE,f.clock.get(),Set.of(2));
            f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);
            assertEquals("Previously observed aggro remains a threat during an idle interaction",Protection.MELEE,f.driver.requested());
            verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_MELEE),any(),any());
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(0);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MELEE.getVarbit())).thenReturn(1);
            f.clock.set(14_800_000_000L);f.driver.gameTick(f.frame(24,healerGapMobs(true)),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);
            assertEquals(Protection.MAGIC,f.driver.requested());
            verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_MAGIC),any(),any());
            verify(f.actions,never()).tickPrayer(argThat(c->c.type!=OneTickPrayerCycle.Type.ON),any(),any(),any());
        }
    }
    @Test public void lateHealerSwitchKeepsEitherObservedJadGuard()throws Exception {
        for(boolean magic:new boolean[]{false,true})try(Fixture f=new Fixture()) {
            calibrateJad(f,magic);f.clock.set(14_200_000_000L);
            Protection style=magic?Protection.MAGIC:Protection.RANGE;
            f.driver.gameTick(f.frame(23,healerGapMobs(true,style),style),Protection.NONE,f.clock.get());
            f.clock.addAndGet(200_000_000L);f.plan();f.driver.pulse(f.actions);
            assertEquals(style,f.driver.requested());
            verify(f.actions,never()).tickPrayer(any(),eq(Rs2PrayerEnum.PROTECT_MELEE),any(),any());
        }
    }
    @Test public void queuedHealerMeleeExpiresBeforeALateWorkerWithoutAnotherClientTick()throws Exception {
        for(boolean magic:new boolean[]{false,true})try(Fixture f=new Fixture()) {
            calibrateJad(f,magic);f.clock.set(14_200_000_000L);
            Protection style=magic?Protection.MAGIC:Protection.RANGE;
            f.driver.gameTick(f.frame(23,healerGapMobs(true,style),style),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();assertEquals(Protection.MELEE,f.driver.requested());
            f.clock.addAndGet(170_000_000L);f.driver.pulse(f.actions);
            verifyNoInteractions(f.actions);
            f.plan();f.driver.pulse(f.actions);assertEquals(style,f.driver.requested());
            verifyNoInteractions(f.actions);
        }
    }
    @Test public void newGameTickRejectsQueuedHealerMeleeBeforeTheNextClientPlan()throws Exception {
        try(Fixture f=new Fixture()) {
            calibrateJad(f,true);f.clock.set(14_200_000_000L);
            f.driver.gameTick(f.frame(23,healerGapMobs(true)),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();assertEquals(Protection.MELEE,f.driver.requested());
            f.clock.set(14_800_000_000L);f.driver.gameTick(f.frame(24,healerGapMobs(true)),Protection.NONE,f.clock.get());
            f.driver.pulse(f.actions);verifyNoInteractions(f.actions);
            f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);
            assertEquals(Protection.MAGIC,f.driver.requested());verifyNoInteractions(f.actions);
        }
    }
    @Test public void newJadAnimationCancelsMeleeEvenAfterTheGapWasQueued()throws Exception {
        try(Fixture f=new Fixture()) {
            calibrateJad(f,true);f.clock.set(14_200_000_000L);
            f.driver.gameTick(f.frame(23,healerGapMobs(true)),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();assertEquals(Protection.MELEE,f.driver.requested());
            f.driver.npcAnimation(1,Kind.JAD,2652);f.driver.pulse(f.actions);
            assertFalse(f.driver.protectionReady());verifyNoInteractions(f.actions);
        }
    }
    @Test public void pauseDiscardsQueuedClick()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();f.driver.clientTick(false,false,false);f.driver.pulse(f.actions);
            verifyNoInteractions(f.actions);assertFalse(f.driver.protectionReady());
        }
    }
    @Test public void sceneTransitionDiscardsQueuedClick()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();f.driver.transition();f.driver.pulse(f.actions);
            verifyNoInteractions(f.actions);assertFalse(f.driver.ownsInput());
        }
    }
    @Test public void changedMovementPlanCancelsOldQueuedPrayer()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();f.driver.clientTick(true,false,true);f.driver.pulse(f.actions);
            verifyNoInteractions(f.actions);assertFalse(f.driver.protectionReady());
        }
    }
    @Test public void steadyOverheadIsNotRepeatedlyClicked()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            f.begin(10);f.plan();f.driver.pulse(f.actions);
            f.clock.set(1_600_000_000L);f.begin(11);f.plan();f.driver.pulse(f.actions);
            f.clock.set(2_200_000_000L);f.begin(12);f.plan();
            f.driver.pulse(f.actions);f.plan();assertTrue(f.driver.protectionReady());
            verifyNoInteractions(f.actions);
        }
    }
    @Test public void zeroPrayerDiscardsOldClickSoRestoreCanOpenInventory()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();
            when(f.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
            f.plan();f.driver.pulse(f.actions);verifyNoInteractions(f.actions);
            assertEquals("Restore prayer",f.driver.status());
        }
    }
    @Test public void visibleDriverDispatchesSingleButtonSwitchesForMageRangeAndBigMelee()throws Exception {
        try(Fixture f=new Fixture()) {
            java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
            for(Rs2PrayerEnum p:List.of(Rs2PrayerEnum.PROTECT_MAGIC,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MELEE))
                when(f.client.getVarbitValue(p.getVarbit())).thenAnswer(a->active.get()==p.getVarbit()?1:0);
            when(f.actions.tickPrayer(any(),any(),any(),any())).thenAnswer(a->{
                OneTickPrayerCycle.Command command=a.getArgument(0);Rs2PrayerEnum prayer=a.getArgument(1);
                assertNotEquals("Switches must use the new button, not turn the previous protection off",OneTickPrayerCycle.Type.OFF,command.type);
                active.set(prayer.getVarbit());return FcPrayerUi.Result.SENT;
            });
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,28),4,10,10,-1,Protection.MAGIC,true);
            Mob ranger=new Mob(2,Kind.RANGER,new Tile(28,21),3,10,10,-1,Protection.RANGE,true);
            Mob melee=new Mob(3,Kind.MELEER,new Tile(24,28),4,10,10,-1,Protection.MELEE,true);
            for(int tick=1;tick<=18;tick++) {
                if((tick-1)%4==0)f.driver.npcAnimation(1,Kind.MAGER,2647);
                if(tick>=2&&(tick-2)%4==0)f.driver.npcAnimation(2,Kind.RANGER,2633);
                if(tick>=3&&(tick-3)%4==0)f.driver.npcAnimation(3,Kind.MELEER,2637);
                f.clock.set(1_000_000_000L+(tick-1)*600_000_000L);
                f.driver.gameTick(f.frame(tick,List.of(mage,ranger,melee)),Protection.NONE,f.clock.get());
                f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);f.plan();
                if(tick>=12) {
                    Rs2PrayerEnum wanted=tick%4==0?Rs2PrayerEnum.PROTECT_MAGIC:tick%4==1?Rs2PrayerEnum.PROTECT_RANGE:
                        tick%4==2?Rs2PrayerEnum.PROTECT_MELEE:Rs2PrayerEnum.PROTECT_MAGIC;
                    assertEquals("Visible prayer for next server tick "+(tick+1),wanted.getVarbit(),active.get());
                    assertTrue(f.driver.protectionReady());
                }
            }
            verify(f.actions,atLeastOnce()).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_RANGE),any(),any());
            verify(f.actions,atLeastOnce()).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_MELEE),any(),any());
        }
    }

    @Test public void uncertainMixedFightHoldsMagicInsteadOfResettingItOrCasting()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,28),5,10,10,-1,Protection.MAGIC,true);
            Mob ranger=new Mob(2,Kind.RANGER,new Tile(28,21),3,10,10,-1,Protection.RANGE,true);
            Mob melee=new Mob(3,Kind.MELEER,new Tile(24,28),4,10,10,-1,Protection.MELEE,true);
            for(int tick=1;tick<=4;tick++) {
                f.clock.set(1_000_000_000L+(tick-1)*600_000_000L);
                f.driver.gameTick(f.frame(tick,List.of(mage,ranger,melee)),Protection.NONE,f.clock.get());
                f.clock.addAndGet(30_000_000L);f.plan();f.driver.pulse(f.actions);
                assertEquals(Protection.MAGIC,f.driver.requested());
                assertFalse(f.driver.optionalInputWindow(tick,600));
            }
            verifyNoInteractions(f.actions);
        }
    }
    @Test public void singleStyleFightStillAllowsSuppliesAndThralls()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,28),5,10,10,-1,Protection.MAGIC,true);
            f.driver.gameTick(f.frame(1,List.of(mage)),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();
            assertTrue(f.driver.optionalInputWindow(1,900));
            assertTrue(f.driver.optionalInputWindow(1,1400));
            assertFalse(f.driver.optionalInputWindow(2,900));
        }
    }

    @Test public void movementCannotTreatNoneAsReadyForRequiredMagicOrUseAnAmbiguousOverhead()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.gameTick(f.frame(1),Protection.NONE,f.clock.get());f.plan();
            assertTrue(f.driver.protectionReady());
            f.driver.primeMovement(1,Protection.MAGIC);assertFalse(f.driver.movementReady(Protection.MAGIC));
            f.plan();assertEquals(Protection.MAGIC,f.driver.requested());f.driver.pulse(f.actions);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            f.plan();assertFalse("Local ON alone cannot release movement",f.driver.movementReady(Protection.MAGIC));
            f.clock.set(1_600_000_000L);f.driver.gameTick(f.frame(2),Protection.NONE,f.clock.get());f.plan();
            assertTrue(f.driver.movementReady(Protection.MAGIC));
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MELEE.getVarbit())).thenReturn(1);
            f.clock.set(2_200_000_000L);f.driver.gameTick(f.frame(3),Protection.NONE,f.clock.get());
            f.driver.primeMovement(3,Protection.MAGIC);f.plan();
            assertFalse("Recorded 37 mask is not exclusive Magic confirmation",f.driver.movementReady(Protection.MAGIC));
        }
    }
    private List<Mob> learnAdjacentMageMeleePhases(Fixture f)throws Exception {
        List<Mob> mobs=List.of(new Mob(1,Kind.MAGER,new Tile(20,28),5,10,10,-1,Protection.MAGIC,true),
            new Mob(2,Kind.MELEER,new Tile(28,29),4,10,10,-1,Protection.MELEE,true));
        when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
        for(int tick=1;tick<=13;tick++) {
            if((tick-1)%4==0)f.driver.npcAnimation(1,Kind.MAGER,2647);
            if(tick%4==3)f.driver.npcAnimation(2,Kind.MELEER,2637);
            f.clock.set(1_000_000_000L+(tick-1)*600_000_000L);
            f.driver.gameTick(f.frame(tick,mobs),Protection.NONE,f.clock.get());f.clock.addAndGet(30_000_000L);f.plan();
        }
        return mobs;
    }
    @Test public void movementAcceptsConfirmedHigherPriorityMagicInsteadOfWaitingForRange()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,28),5,10,10,-1,Protection.MAGIC,true);
            f.driver.gameTick(f.frame(1,List.of(mage)),Protection.NONE,f.clock.get());f.plan();
            f.driver.primeMovement(1,Protection.RANGE);f.plan();
            assertEquals(Protection.MAGIC,f.driver.requested());assertTrue(f.driver.movementReady(Protection.RANGE));
        }
    }
    @Test public void lateMeleeSwitchKeepsMagicAndNextButtonCanBePreparedBeforeItsTick()throws Exception {
        try(Fixture f=new Fixture()) {
            List<Mob> mobs=learnAdjacentMageMeleePhases(f);
            // Mage next launches on 17; the earlier melee gap is tick 14.
            // Tick 15 is now reserved for the return to Magic.
            // Discard stale queued work and prepare the following tick's button.
            f.driver.pulse(f.actions);clearInvocations(f.actions);f.plan();f.driver.pulse(f.actions);
            verify(f.actions).prepareTickPrayer(eq(Rs2PrayerEnum.PROTECT_MELEE),any());
            f.clock.set(8_800_000_000L);f.driver.gameTick(f.frame(14,mobs),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();assertEquals(Protection.MELEE,f.driver.requested());
            clearInvocations(f.actions);f.clock.addAndGet(200_000_000L);f.driver.pulse(f.actions);
            verifyNoInteractions(f.actions);f.plan();assertEquals(Protection.MAGIC,f.driver.requested());
            assertTrue(f.driver.protectionReady());
        }
    }

    @Test public void failedPrayerPreparationRetriesAndSuccessfulPreparationIsNotRepeated()throws Exception {
        try(Fixture f=new Fixture()) {
            learnAdjacentMageMeleePhases(f);f.driver.pulse(f.actions);clearInvocations(f.actions);
            when(f.actions.prepareTickPrayer(eq(Rs2PrayerEnum.PROTECT_MELEE),any())).thenReturn(false,true);
            f.plan();f.driver.pulse(f.actions);f.driver.pulse(f.actions);f.driver.pulse(f.actions);
            verify(f.actions,times(2)).prepareTickPrayer(eq(Rs2PrayerEnum.PROTECT_MELEE),any());
        }
    }
    @Test public void movingBetweenKnownAttacksStillPreparesTheNextPrayerButton()throws Exception {
        try(Fixture f=new Fixture()) {
            learnAdjacentMageMeleePhases(f);f.driver.pulse(f.actions);clearInvocations(f.actions);
            f.driver.clientTick(true,false,true);f.driver.pulse(f.actions);
            verify(f.actions).prepareTickPrayer(eq(Rs2PrayerEnum.PROTECT_MELEE),any());
        }
    }
    @Test public void eagleEyeSurvivesBriefTargetHandoffButStopsForMovementCoverOrEmptyRoom()throws Exception {
        for(String end:List.of("handoff","movement","cover","empty"))try(Fixture f=new Fixture()) {
            when(f.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(77);
            when(f.client.getRealSkillLevel(Skill.DEFENCE)).thenReturn(1);
            when(f.client.getVarbitValue(Rs2PrayerEnum.EAGLE_EYE.getVarbit())).thenReturn(1);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            Mob first=new Mob(1,Kind.MAGER,new Tile(35,28),5,10,10,-1,Protection.MAGIC,true);
            for(int tick=10;tick<=11;tick++) {
                f.clock.set(1_000_000_000L+(tick-10)*600_000_000L);
                f.driver.gameTick(f.frame(tick,List.of(first)),Protection.NONE,f.clock.get());
                f.clock.addAndGet(30_000_000L);f.driver.primeAttack(tick,1);
                f.driver.clientTick(true,true,false);f.driver.pulse(f.actions);
            }
            clearInvocations(f.actions);
            Mob next=new Mob(2,Kind.MAGER,end.equals("cover")?new Tile(50,28):new Tile(35,28),5,10,10,-1,Protection.MAGIC,true);
            f.clock.set(2_200_000_000L);
            f.driver.gameTick(f.frame(12,end.equals("empty")?List.of():List.of(next)),Protection.NONE,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.driver.clientTick(true,true,end.equals("movement"));f.driver.pulse(f.actions);
            if(end.equals("handoff"))verify(f.actions,never()).tickPrayer(any(),eq(Rs2PrayerEnum.EAGLE_EYE),any(),any());
            else {
                // Clear overheads can be queued first; both OFF requests fit the same early tick.
                f.driver.clientTick(true,true,end.equals("movement"));f.driver.pulse(f.actions);
                verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.OFF),eq(Rs2PrayerEnum.EAGLE_EYE),any(),any());
            }
        }
    }

    @Test public void prayerWhichCompletesAcrossGameTickIsLoggedAndDoesNotConfirmReadiness()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.plan();
            when(f.actions.tickPrayer(any(),any(),any(),any())).thenAnswer(a->{
                f.clock.set(1_600_000_000L);f.driver.gameTick(f.frame(11),Protection.MAGIC,f.clock.get());
                return FcPrayerUi.Result.SENT;
            });
            f.driver.pulse(f.actions);assertFalse(f.driver.protectionReady());
            assertTrue(f.logs.stream().anyMatch(s->s.startsWith("tick-prayer-input tick=10")&&s.contains("completedTick=11")));
        }
    }

    @Test public void recordedTwoTickMageRangerOffsetHasAnUnchangedSixHundredMsAttackWindow()throws Exception {
        try(Fixture f=new Fixture()) {
            java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
            for(Rs2PrayerEnum p:List.of(Rs2PrayerEnum.PROTECT_MAGIC,Rs2PrayerEnum.PROTECT_RANGE))
                when(f.client.getVarbitValue(p.getVarbit())).thenAnswer(a->active.get()==p.getVarbit()?1:0);
            when(f.actions.tickPrayer(any(),any(),any(),any())).thenAnswer(a->{
                Rs2PrayerEnum p=a.getArgument(1);active.set(p.getVarbit());
                f.clock.addAndGet(160_000_000L);return FcPrayerUi.Result.SENT;
            });
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,28),5,10,10,-1,Protection.MAGIC,true);
            Mob ranger=new Mob(2,Kind.RANGER,new Tile(28,21),3,10,10,-1,Protection.RANGE,true);
            int openings=0;
            for(int tick=1;tick<=24;tick++) {
                if(tick%4==2)f.driver.npcAnimation(1,Kind.MAGER,2647);
                if(tick%4==0)f.driver.npcAnimation(2,Kind.RANGER,2633);
                f.clock.set(1_000_000_000L+(tick-1)*600_000_000L);
                f.driver.gameTick(f.frame(tick,List.of(mage,ranger)),Protection.NONE,f.clock.get());
                f.clock.addAndGet(10_000_000L);f.plan();f.driver.pulse(f.actions);f.plan();
                if(tick>=12&&f.driver.optionalInputWindow(tick,600))openings++;
            }
            assertTrue("The handoff must use existing long windows; do not shorten the prayer deadline",openings>=3);
        }
    }
    @Test public void threeStyleSwitchesAdmitShortNpcClickButKeepLateAndSupplyGatesClosed()throws Exception {
        try(Fixture f=new Fixture()) {
            java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
            for(Rs2PrayerEnum p:List.of(Rs2PrayerEnum.PROTECT_MAGIC,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MELEE))
                when(f.client.getVarbitValue(p.getVarbit())).thenAnswer(a->active.get()==p.getVarbit()?1:0);
            when(f.actions.tickPrayer(any(),any(),any(),any())).thenAnswer(a->{
                Rs2PrayerEnum p=a.getArgument(1);active.set(p.getVarbit());
                f.clock.addAndGet(160_000_000L);return FcPrayerUi.Result.SENT;
            });
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,28),5,10,10,-1,Protection.MAGIC,true);
            Mob ranger=new Mob(2,Kind.RANGER,new Tile(28,21),3,10,10,-1,Protection.RANGE,true);
            Mob melee=new Mob(3,Kind.MELEER,new Tile(24,28),4,10,10,-1,Protection.MELEE,true);
            int shortWindows=0;
            for(int tick=1;tick<=24;tick++) {
                if((tick-1)%4==0)f.driver.npcAnimation(1,Kind.MAGER,2647);
                if(tick>=2&&(tick-2)%4==0)f.driver.npcAnimation(2,Kind.RANGER,2633);
                if(tick>=3&&(tick-3)%4==0)f.driver.npcAnimation(3,Kind.MELEER,2637);
                long received=1_000_000_000L+(tick-1)*600_000_000L;f.clock.set(received);
                f.driver.gameTick(f.frame(tick,List.of(mage,ranger,melee)),Protection.NONE,received);
                f.clock.addAndGet(10_000_000L);f.plan();f.driver.pulse(f.actions);f.plan();
                if(tick>=12&&!f.driver.optionalInputWindow(tick,600)
                    &&f.driver.optionalInputWindow(tick,DroFirecapeScript.ATTACK_INPUT_BUDGET_MS)) {
                    shortWindows++;assertFalse(f.driver.optionalInputWindow(tick,900));
                    f.clock.set(received+500_000_000L);
                    assertFalse(f.driver.optionalInputWindow(tick,DroFirecapeScript.ATTACK_INPUT_BUDGET_MS));
                }
            }
            assertTrue("A ranger click cannot require a multi-tick gap",shortWindows>=3);
        }
    }
    @Test public void uncertainContactRangerAllowsOnlyEarlyConfirmedAttackNotSupplyOrLateClick()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            Mob mage=new Mob(1,Kind.MAGER,new Tile(20,22),5,10,10,-1,Protection.MAGIC,true);
            Mob ranger=new Mob(2,Kind.RANGER,new Tile(25,28),3,10,10,-1,Protection.RANGE,true);
            f.driver.gameTick(f.frame(1,List.of(mage,ranger)),Protection.NONE,f.clock.get());
            f.clock.addAndGet(20_000_000L);f.plan();
            assertTrue(f.driver.protectionReady());assertEquals(Protection.MAGIC,f.driver.requested());
            assertFalse(f.driver.optionalInputWindow(1,250));assertFalse(f.driver.optionalInputWindow(1,900));
            assertTrue(f.driver.attackInputWindow(1,250));
            f.clock.addAndGet(300_000_000L);assertFalse(f.driver.attackInputWindow(1,250));
            assertFalse(f.driver.attackInputWindow(0,250));
        }
    }

    @Test public void expiredUnsentMovementRetryReleasesBetaAttackWindow()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(f.client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(f.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
            f.driver.nativeWidgets(true);f.driver.pureTinyPrayers(true);
            java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
            for(Rs2PrayerEnum p:List.of(Rs2PrayerEnum.PROTECT_MAGIC,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MELEE))
                when(f.client.getVarbitValue(p.getVarbit())).thenAnswer(a->active.get()==p.getVarbit()?1:0);
            doAnswer(a->{
                int widget=a.getArgument(1);
                for(Rs2PrayerEnum p:List.of(Rs2PrayerEnum.PROTECT_MAGIC,Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MELEE))
                    if(p.getIndex()==widget)active.set(active.get()==p.getVarbit()?0:p.getVarbit());
                return null;
            }).when(f.client).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
            Tile stopped=new Tile(57,55);
            List<Mob> mobs=List.of(
                new Mob(1,Kind.MAGER,new Tile(61,45),5,-1,-1,99,Protection.MAGIC,true),
                new Mob(2,Kind.BABY,new Tile(57,56),1,-1,-1,-1,Protection.MELEE,true),
                new Mob(3,Kind.BABY,new Tile(57,57),1,-1,-1,-1,Protection.MELEE,true));
            net.runelite.client.plugins.microbot.drofirecape.optional.core.MovementAck movement=
                new net.runelite.client.plugins.microbot.drofirecape.optional.core.MovementAck();
            movement.sent(stopped,new Tile(57,54),92);
            f.driver.gameTick(f.frame(100,mobs,stopped,new CollisionGrid(new int[104][104])),Protection.NONE,f.clock.get());
            f.clock.addAndGet(20_000_000L);
            f.driver.clientTick(true,false,movement.pending());
            f.driver.clientTick(true,false,movement.pending());
            assertFalse(f.driver.attackInputWindow(100,250));
            assertEquals(net.runelite.client.plugins.microbot.drofirecape.optional.core.MovementAck.Result.FAILED,
                movement.observe(stopped,101));
            f.driver.clientTick(true,false,movement.pending());
            f.driver.clientTick(true,false,movement.pending());
            assertTrue("Expiry must release a confirmed short attack window without changing prayer timing",
                f.driver.attackInputWindow(100,250));
        }
    }

    @Test public void pureBetaPrearmsNearbyBlobChildrenInsteadOfNextWaveRanger()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.nativeWidgets(true);f.driver.pureTinyPrayers(true);f.driver.pureWave(10);
            Tile player=new Tile(62,60);CollisionGrid grid=new CollisionGrid(new int[104][104]);
            Mob blob=new Mob(2,Kind.BLOB,new Tile(60,60),2,5,30,-1,Protection.MELEE,true);
            f.driver.gameTick(f.frame(263,List.of(blob),player,grid),Protection.NONE,f.clock.get());f.plan();
            for(int tick=264;tick<=267;tick++) {
                f.clock.addAndGet(600_000_000L);
                f.driver.gameTick(f.frame(tick,List.of(),player,grid),Protection.RANGE,f.clock.get());f.plan();
                assertEquals("Wave 10 has not finished while children are spawning",Protection.MELEE,f.driver.requested());
            }
            f.clock.addAndGet(600_000_000L);
            f.driver.gameTick(f.frame(268,List.of(
                new Mob(3,Kind.BABY,new Tile(61,60),1,-1,-1,-1,Protection.MELEE,true),
                new Mob(4,Kind.BABY,new Tile(62,61),1,-1,-1,-1,Protection.MELEE,true)),player,grid),Protection.RANGE,f.clock.get());
            f.plan();assertEquals(Protection.MELEE,f.driver.requested());
        }
    }
    @Test public void betaOnlyRetainsExistingNextWavePrearmDuringEmptyCapture()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.nativeWidgets(true);f.driver.pureWave(10);
            f.driver.gameTick(f.frame(263,List.of(new Mob(2,Kind.BLOB,new Tile(27,28),2,5,30,-1,Protection.MELEE,true))),
                Protection.NONE,f.clock.get());f.plan();
            f.clock.addAndGet(600_000_000L);
            f.driver.gameTick(f.frame(264),Protection.RANGE,f.clock.get());f.plan();
            assertEquals(Protection.RANGE,f.driver.requested());
        }
    }

    @Test public void pureBetaMixedRoomDoesNotTurnMeleeOffBeforeChildrenAppear()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.nativeWidgets(true);f.driver.pureTinyPrayers(true);f.driver.pureWave(21);
            Tile player=new Tile(62,60);CollisionGrid grid=new CollisionGrid(new int[104][104]);
            Mob distant=new Mob(1,Kind.MELEER,new Tile(20,20),4,-1,-1,-1,Protection.MELEE,true);
            f.driver.gameTick(f.frame(402,List.of(distant,
                new Mob(2,Kind.BLOB,new Tile(60,59),2,3,30,-1,Protection.MELEE,true)),player,grid),Protection.NONE,f.clock.get());f.plan();
            for(int tick=403;tick<=405;tick++) {
                f.clock.addAndGet(600_000_000L);
                f.driver.gameTick(f.frame(tick,List.of(distant),player,grid),Protection.NONE,f.clock.get());f.plan();
                assertEquals("Remaining monsters must not cancel the imminent child guard",Protection.MELEE,f.driver.requested());
            }
            f.clock.addAndGet(600_000_000L);
            f.driver.gameTick(f.frame(406,List.of(distant,
                new Mob(3,Kind.BABY,new Tile(61,60),1,-1,-1,-1,Protection.MELEE,true)),player,grid),Protection.NONE,f.clock.get());
            f.plan();assertEquals(Protection.MELEE,f.driver.requested());
        }
    }

    @Test public void pureBetaDueRangerReleasesMeleeRoutePreviewOnlyAfterConfirmation()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.nativeWidgets(true);f.driver.pureTinyPrayers(true);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_RANGE.getVarbit())).thenReturn(1);
            Tile player=new Tile(62,60);
            List<Mob> mobs=List.of(
                new Mob(1,Kind.MELEER,new Tile(62,46),4,-1,-1,-1,Protection.MELEE,true),
                new Mob(2,Kind.RANGER,new Tile(62,43),3,-1,-1,-1,Protection.RANGE,true),
                new Mob(3,Kind.BLOB,new Tile(60,60),2,-1,-1,-1,Protection.MELEE,true));
            f.driver.gameTick(f.frame(60,mobs,player,new CollisionGrid(new int[104][104])),Protection.NONE,f.clock.get());
            f.driver.primeMovement(60,Protection.MELEE);f.plan();
            assertEquals(Protection.RANGE,f.driver.requested());
            assertTrue("A confirmed higher-priority ranger guard must not deadlock the checked route",f.driver.movementReady(Protection.MELEE));
            assertFalse("Range must not authorize entering a planned magic threat",f.driver.movementReady(Protection.MAGIC));
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_RANGE.getVarbit())).thenReturn(0);
            f.plan();assertFalse("A requested but unacknowledged prayer cannot release movement",f.driver.movementReady(Protection.MELEE));
        }
    }
    @Test public void betaOnlyKeepsExistingExactMeleeRouteHandoff()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.nativeWidgets(true);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_RANGE.getVarbit())).thenReturn(1);
            f.driver.gameTick(f.frame(60,List.of(
                new Mob(2,Kind.RANGER,new Tile(28,18),3,-1,-1,-1,Protection.RANGE,true),
                new Mob(3,Kind.BLOB,new Tile(26,28),2,-1,-1,-1,Protection.MELEE,true))),Protection.NONE,f.clock.get());
            f.driver.primeMovement(60,Protection.MELEE);f.plan();
            assertEquals(Protection.RANGE,f.driver.requested());assertFalse(f.driver.movementReady(Protection.MELEE));
        }
    }

    private CollisionGrid recordedItalyGrid()throws Exception {
        int[][] flags=new int[104][104];
        try(java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(
            getClass().getResourceAsStream("run0322-collision.csv"),java.nio.charset.StandardCharsets.UTF_8))) {
            r.readLine();String line;while((line=r.readLine())!=null) {
                String[] c=line.split(",");flags[Integer.parseInt(c[0])][Integer.parseInt(c[1])]=Integer.parseInt(c[2]);
            }
        }
        return new CollisionGrid(flags);
    }
    @Test public void successfulRunJadOpeningPrearmsBeforeLeavingItaly()throws Exception {
        // 0.3.27 tick 9232: planned firing approach (50,29), but no input for 228 ticks.
        try(Fixture f=new Fixture()) {
            CollisionGrid grid=recordedItalyGrid();Tile player=new Tile(54,36);
            List<Mob> mobs=List.of(new Mob(41776,Kind.JAD,new Tile(55,20),5,-1,-1,-1,Protection.MAGIC,true));
            FcFrame frame=f.frame(9232,mobs,player,grid);
            net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.Plan plan=
                new net.runelite.client.plugins.microbot.drofirecape.optional.core.CombatPlanner().plan(frame.model,player);
            assertNotEquals(player,plan.destination());
            f.driver.gameTick(frame,Protection.NONE,f.clock.get());f.clock.addAndGet(20_000_000L);f.plan();
            assertEquals(Protection.NONE,f.driver.requested());
            f.driver.primeMovement(frame.tick,Protection.MAGIC);f.plan();
            assertEquals("Hidden Jad must not cancel the prayer required to approach him",Protection.MAGIC,f.driver.requested());
            assertFalse(f.driver.movementReady(Protection.MAGIC));
            f.driver.pulse(f.actions);
            verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_MAGIC),any(),any());
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            f.clock.set(1_600_000_000L);f.driver.gameTick(f.frame(9233,mobs,player,grid),Protection.NONE,f.clock.get());
            f.clock.addAndGet(20_000_000L);f.plan();assertTrue(f.driver.movementReady(Protection.MAGIC));
            f.driver.movementSent();f.driver.clientTick(true,false,true);
            assertEquals("Hold the approach protection throughout the accepted route",Protection.MAGIC,f.driver.requested());
        }
    }
    @Test public void hiddenJadWithTaggedHealerCanStillPrearmAnApproach()throws Exception {
        try(Fixture f=new Fixture()) {
            List<Mob> mobs=List.of(new Mob(1,Kind.JAD,new Tile(55,20),5,10,30,-1,Protection.MAGIC,true),
                new Mob(2,Kind.HEALER,new Tile(54,35),1,10,30,-1,Protection.MELEE,true));
            f.driver.gameTick(f.frame(10,mobs,new Tile(54,36),recordedItalyGrid()),Protection.NONE,f.clock.get());
            f.clock.addAndGet(20_000_000L);f.plan();assertEquals(Protection.MELEE,f.driver.requested());
            f.driver.primeMovement(10,Protection.MAGIC);f.plan();assertEquals(Protection.MAGIC,f.driver.requested());
        }
    }
    @Test public void observedJadRangeWindupOverridesStaleMagicMovementGuard()throws Exception {
        try(Fixture f=new Fixture()) {
            List<Mob> mobs=List.of(new Mob(1,Kind.JAD,new Tile(55,20),5,10,30,-1,Protection.MAGIC,true));
            f.driver.npcAnimation(1,Kind.JAD,2652);
            f.driver.gameTick(f.frame(10,mobs,new Tile(54,36),recordedItalyGrid()),Protection.NONE,f.clock.get());
            f.clock.addAndGet(20_000_000L);f.driver.primeMovement(10,Protection.MAGIC);f.plan();
            assertEquals(Protection.RANGE,f.driver.requested());f.driver.pulse(f.actions);
            verify(f.actions).tickPrayer(any(),eq(Rs2PrayerEnum.PROTECT_RANGE),any(),any());
            verify(f.actions,never()).tickPrayer(any(),eq(Rs2PrayerEnum.PROTECT_MAGIC),any(),any());
        }
    }

    @Test public void confirmedPauseUsesSameOwnerAndWaitsForEveryOwnedPrayerOff()throws Exception {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            when(f.client.getVarbitValue(Rs2PrayerEnum.EAGLE_EYE.getVarbit())).thenReturn(1);
            f.begin(10);f.driver.recoveryPause(true,Protection.NONE);f.plan();
            assertFalse(f.driver.prayersOffReady());f.driver.pulse(f.actions);
            verifyNoInteractions(f.actions); // Stable early tick evidence is required for OFF.
            for(int tick=11;tick<=12;tick++) {
                f.clock.addAndGet(570_000_000L);f.begin(tick);f.plan();
            }
            assertTrue(f.driver.ownsInput());assertFalse(f.driver.prayersOffReady());f.driver.pulse(f.actions);
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(0);
            f.clock.addAndGet(570_000_000L);f.begin(13);f.plan();f.driver.pulse(f.actions);
            assertFalse(f.driver.prayersOffReady());
            when(f.client.getVarbitValue(Rs2PrayerEnum.EAGLE_EYE.getVarbit())).thenReturn(0);
            f.clock.addAndGet(570_000_000L);f.begin(14);f.plan();assertTrue(f.driver.prayersOffReady());
            verify(f.actions,atLeastOnce()).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.OFF),any(),any(),any());
        }
    }
    @Test public void pauseArmsNextWaveWithoutAnotherPrayerWriter()throws Exception {
        try(Fixture f=new Fixture()) {
            f.begin(10);f.driver.recoveryPause(true,Protection.NONE);f.plan();assertTrue(f.driver.prayersOffReady());
            f.driver.recoveryPause(true,Protection.MAGIC);f.plan();assertFalse(f.driver.protectionReady());f.driver.pulse(f.actions);
            verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_MAGIC),any(),any());
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);f.plan();
            assertTrue(f.driver.protectionReady());assertFalse(f.driver.prayersOffReady());
        }
    }
    @Test public void livingJadImmediatelyOverridesPausedOffRequest()throws Exception {
        try(Fixture f=new Fixture()) {
            f.driver.recoveryPause(true,Protection.NONE);
            Mob jad=new Mob(9,Kind.JAD,new Tile(20,26),5,10,10,-1,Protection.MAGIC,true);
            f.driver.npcAnimation(9,Kind.JAD,2652);f.driver.gameTick(f.frame(10,List.of(jad)),Protection.MAGIC,f.clock.get());
            f.clock.addAndGet(30_000_000L);f.plan();assertEquals(Protection.RANGE,f.driver.requested());
            f.driver.pulse(f.actions);
            verify(f.actions).tickPrayer(argThat(c->c.type==OneTickPrayerCycle.Type.ON),eq(Rs2PrayerEnum.PROTECT_RANGE),any(),any());
        }
    }

}
