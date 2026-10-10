package net.runelite.client.plugins.microbot.drofirecape.optional;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class PureModeTest {
    @Test public void purePocketPositionsAreIncludedInInstanceCapture(){
        assertTrue(RecordedLureBook.anchors().contains(RecordedLureBook.POCKET_EAST));
        assertTrue(RecordedLureBook.anchors().contains(RecordedLureBook.POCKET_WEST));
    }
    private Mob mob(int id,Kind kind,Tile tile){return new Mob(id,kind,tile,kind.size,10,10,-1,kind.protection,true);}
    private Snapshot scene(int tick,CollisionGrid grid,Tile p,Mob... mobs){return new Snapshot(tick,p,grid,List.of(mobs),100,true,7,Protection.NONE);}
    @Test public void urgentDoseRetryStillRequiresObservedConsumption() {
        SupplyAck ack=new SupplyAck();ack.sent(6687,2,10,SupplyAck.Kind.BREW);
        assertEquals(SupplyAck.Result.WAITING,ack.observe(2,11,2));
        assertEquals(SupplyAck.Result.CONSUMED,ack.observe(1,12,2));
        assertEquals(SupplyAck.Result.TIMED_OUT,ack.observe(2,12,2));
        assertEquals(SupplyAck.Result.WAITING,ack.observe(2,12));
    }
    @Test public void bothOptionsDefaultOffAndPrayerCheckboxImmediatelyFollowsPure()throws Exception {
        DroFirecapeConfig config=new DroFirecapeConfig(){};
        assertFalse(config.pureMode());assertFalse(config.nativeTickPrayers());
        int pure=DroFirecapeConfig.class.getMethod("pureMode").getAnnotation(ConfigItem.class).position();
        assertEquals(pure+1,DroFirecapeConfig.class.getMethod("nativeTickPrayers").getAnnotation(ConfigItem.class).position());
    }
    private Plan lurePlan(Snapshot s,boolean pure,Tile main,Tile pull) {
        return pure?new PureLureController().decide(s,new PureCombatPlanner(),-1,List.of(main),0,main,pull,null,main.add(-1,0)):
            new LureController().decide(s,new CombatPlanner(),-1,List.of(main),0,main,pull,null,main.add(-1,0));
    }
    @Test public void loneBatUsesOriginalControllerInBothModes() {
        Tile main=new Tile(44,41),pull=new Tile(54,61);
        Snapshot distant=scene(85,new CollisionGrid(new int[104][104]),main,mob(40979,Kind.BAT,new Tile(18,42)));
        assertEquals(lurePlan(distant,false,main,pull),lurePlan(distant,true,main,pull));
        Snapshot close=scene(86,new CollisionGrid(new int[104][104]),main,mob(40979,Kind.BAT,new Tile(40,41)));
        Plan plan=lurePlan(close,true,main,pull);
        assertEquals(main,plan.nextStep());assertEquals(40979,plan.targetIndex());
    }
    @Test public void crowdedGroupUsesExistingNorthernPullBeforeRangerApproach() {
        Tile main=new Tile(46,44),pull=new Tile(54,61);
        Snapshot crowded=scene(1,new CollisionGrid(new int[104][104]),main,
            mob(1,Kind.RANGER,new Tile(30,50)),mob(2,Kind.MELEER,new Tile(35,45)),mob(3,Kind.BLOB,new Tile(35,50)));
        Plan plan=lurePlan(crowded,true,main,pull);
        assertEquals(pull,plan.destination());assertTrue(plan.reason().startsWith("Pure:"));
        assertFalse(lurePlan(crowded,false,main,pull).reason().startsWith("Pure:"));
    }
    @Test public void accessibleRangerDoesNotPreventPullWhenLargeMeleeIsClosing() {
        Tile main=new Tile(46,44),pull=new Tile(54,61);
        Snapshot crowded=scene(1,new CollisionGrid(new int[104][104]),main,
            mob(1,Kind.RANGER,new Tile(42,44)),mob(2,Kind.MELEER,new Tile(35,45)));
        assertTrue(CombatPlanner.playerCanAttack(crowded,main,crowded.mobs().get(0)));
        assertEquals(pull,lurePlan(crowded,true,main,pull).destination());
    }
    @Test public void rangerBodyBlockDoesNotCountAsPermanentRockCover() {
        Tile main=new Tile(46,44),pull=new Tile(54,61);
        Mob big=mob(2,Kind.MELEER,new Tile(38,44));
        Snapshot crowded=scene(1,new CollisionGrid(new int[104][104]),main,
            mob(1,Kind.RANGER,new Tile(42,44)),big);
        assertTrue(CaveSafety.trapped(crowded,big));
        assertTrue(PureCombatPolicy.crowded(crowded));
        assertFalse(PureCombatPolicy.coveredMelee(crowded));
        assertEquals(pull,lurePlan(crowded,true,main,pull).destination());
    }
    @Test public void conservativePullUsesOriginalPauseAndReturnSequence() {
        Tile main=new Tile(46,44),pull=new Tile(54,61);CollisionGrid grid=new CollisionGrid(new int[104][104]);
        Mob ranger=mob(1,Kind.RANGER,new Tile(30,50)),big=mob(2,Kind.MELEER,new Tile(35,45));
        PureLureController lure=new PureLureController();PureCombatPlanner planner=new PureCombatPlanner();
        Plan start=lure.decide(scene(1,grid,main,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        assertEquals(pull,start.destination());assertTrue(lure.hasPendingReturn());
        Plan waiting=lure.decide(scene(2,grid,pull,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        assertEquals(pull,waiting.nextStep());
        Plan returning=lure.decide(scene(5,grid,pull,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        assertEquals(main,returning.destination());assertTrue(lure.hasPendingReturn());
    }
    @Test public void batAcquisitionCannotBeBlockedByCrowdingOrAnOwedReturn(){
        CollisionGrid grid=new CollisionGrid(new int[104][104]);Tile main=new Tile(44,41),pull=new Tile(54,61);
        Mob ranger=mob(1,Kind.RANGER,new Tile(35,41)),big=mob(2,Kind.MELEER,new Tile(46,45));
        PureLureController lure=new PureLureController();
        lure.decide(scene(1,grid,main,ranger,big),new PureCombatPlanner(),-1,List.of(main),0,main,pull,null,main.add(-1,0));
        assertTrue(lure.hasPendingReturn());
        Mob bat=mob(3,Kind.BAT,pull.add(-1,0));
        assertTrue(lure.allowsImmediateShot(scene(2,grid,pull,ranger,big,bat),bat));
        assertFalse(lure.allowsImmediateShot(scene(2,grid,pull,ranger,big,bat),ranger));
    }
    @Test public void completedConservativePullReenablesRegularRangerAcquisition(){
        CollisionGrid grid=new CollisionGrid(new int[104][104]);Tile main=new Tile(44,41),pull=new Tile(54,61);
        Mob ranger=mob(1,Kind.RANGER,new Tile(35,41)),big=mob(2,Kind.MELEER,new Tile(46,45));
        PureLureController lure=new PureLureController();PureCombatPlanner planner=new PureCombatPlanner();
        lure.decide(scene(1,grid,main,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        lure.decide(scene(2,grid,pull,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        lure.decide(scene(5,grid,pull,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        for(int tick=6;tick<=10;tick++)lure.decide(scene(tick,grid,main,ranger,big),planner,-1,List.of(main),0,main,pull,null,main.add(-1,0));
        assertFalse(lure.hasPendingReturn());
        assertTrue(PureCombatPolicy.crowded(scene(10,grid,main,ranger,big)));
        assertTrue(lure.allowsImmediateShot(scene(10,grid,main,ranger,big),ranger));
    }
    @Test public void existingRockTrapKeepsRangerShotWithoutMoving() {
        int[][] flags=new int[104][104];for(int y=0;y<104;y++)flags[30][y]=CollisionGrid.FULL|CollisionGrid.PROJECTILE_OBJECT;
        Tile player=new Tile(31,25);
        Snapshot s=scene(1,new CollisionGrid(flags),player,mob(1,Kind.MELEER,new Tile(25,25)),mob(2,Kind.RANGER,new Tile(35,25)));
        Plan plan=lurePlan(s,true,player,new Tile(40,40));
        assertEquals(player,plan.nextStep());assertEquals(2,plan.targetIndex());
    }
    @Test public void exposurePreferencePenalizesCrowdedContactButNotSingleBat() {
        CollisionGrid grid=new CollisionGrid(new int[64][64]);Tile player=new Tile(25,19);
        Snapshot mixed=scene(1,grid,player,mob(1,Kind.MELEER,new Tile(26,19)),mob(2,Kind.RANGER,new Tile(18,15)));
        assertTrue(PureCombatPolicy.penalty(mixed,player,mixed.mobs())>0);
        Snapshot bat=scene(1,grid,player,mob(1,Kind.BAT,new Tile(26,19)));
        assertEquals(0,PureCombatPolicy.penalty(bat,player,bat.mobs()));
    }
    private Client nativeClient() {
        Client c=mock(Client.class);when(c.isClientThread()).thenReturn(true);when(c.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(c.getLocalPlayer()).thenReturn(mock(Player.class));when(c.getWidget(anyInt())).thenReturn(mock(Widget.class));
        when(c.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);when(c.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(99);return c;
    }
    @Test public void nativeWidgetActionIsStateCheckedAndDoesNotToggleActivePrayerOff() {
        Client c=nativeClient();Rs2PrayerEnum prayer=Rs2PrayerEnum.PROTECT_MAGIC;
        when(c.getVarbitValue(prayer.getVarbit())).thenReturn(1);
        assertFalse(FcNativePrayer.dispatch(c,prayer,true));verify(c,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
        when(c.getVarbitValue(prayer.getVarbit())).thenReturn(0);
        assertTrue(FcNativePrayer.dispatch(c,prayer,true));
        verify(c).menuAction(-1,prayer.getIndex(),MenuAction.CC_OP,1,-1,"Activate",prayer.getName());
    }
    @Test public void nativeActionRequiresReadyClientAndPrayerPoints() {
        Client c=nativeClient();when(c.isClientThread()).thenReturn(false);assertFalse(FcNativePrayer.dispatch(c,Rs2PrayerEnum.PROTECT_MELEE,true));
        when(c.isClientThread()).thenReturn(true);when(c.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
        assertFalse(FcNativePrayer.dispatch(c,Rs2PrayerEnum.PROTECT_MELEE,true));verify(c,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
    }
    @Test public void nativeForecastPrearmsMagicOneTickBeforeTheNextLaunch() {
        TickProtection clock=new TickProtection();Tile p=new Tile(20,20);CollisionGrid grid=new CollisionGrid(new int[64][64]);
        Mob mage=mob(1,Kind.MAGER,new Tile(25,20)),melee=mob(2,Kind.BABY,new Tile(19,20));
        for(int tick=0;tick<=10;tick++) {
            if(tick==1||tick==5||tick==9)clock.animation(1,Kind.MAGER,2647);
            if(tick==0||tick==4||tick==8)clock.animation(2,Kind.BABY,2625);
            clock.beginTick(tick,List.of(mage,melee));
        }
        Snapshot ten=scene(10,grid,p,mage,melee);
        assertEquals(Protection.MAGIC,clock.chooseNative(ten,false,Protection.MAGIC,Protection.NONE).protection);
        clock.beginTick(11,List.of(mage,melee));Snapshot eleven=scene(11,grid,p,mage,melee);
        assertEquals(Protection.MAGIC,clock.choose(eleven,false,Protection.MELEE,Protection.NONE).protection);
        assertEquals(Protection.MELEE,clock.chooseNative(eleven,false,Protection.MELEE,Protection.NONE).protection);
        clock.beginTick(12,List.of(mage,melee));
        assertEquals(Protection.MAGIC,clock.chooseNative(scene(12,grid,p,mage,melee),false,Protection.MELEE,Protection.NONE).protection);
    }
}
