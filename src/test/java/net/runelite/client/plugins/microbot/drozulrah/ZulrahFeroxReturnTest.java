package net.runelite.client.plugins.microbot.drozulrah;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahFeroxReturnTest {
    private final List<String> calls=new ArrayList<>();
    private WorldPoint here=new WorldPoint(3151,3634,0);
    private boolean bankOpen;
    private boolean cameraWorks=true;
    private final ZulrahFeroxReturn.Actions actions=new ZulrahFeroxReturn.Actions() {
        public boolean turnPool(){calls.add("camera");return cameraWorks;}
        public boolean walkPool(WorldPoint p){calls.add("pool-fallback");here=p;return true;}
        public boolean walkKbd(WorldPoint p){calls.add("kbd-walk");here=ZulrahFeroxReturn.POOL;return true;}
        public void drink(){calls.add("drink");here=ZulrahFeroxReturn.POOL;}
        public boolean park(){calls.add("park");return true;}
        public boolean walkBank(WorldPoint p){calls.add("bank-walk");here=p;return true;}
        public boolean openBank(){calls.add("bank");bankOpen=true;return true;}
        public boolean closeBank(){calls.add("close");bankOpen=false;return true;}
        public boolean skills(){calls.add("skills");return true;}
        public boolean hoverSkill(net.runelite.api.Skill skill){calls.add("xp");return true;}
        public boolean inventory(){calls.add("inventory");return true;}
    };
    private void pump(ZulrahFeroxReturn run) {
        for(long t=0;t<30000 && !run.ready() && !run.failed();t+=100)
            run.tick(here,false,bankOpen,t,actions);
        assertTrue(run.ready(),run.status());
    }
    @Test public void cameraSelectionOnlyRollsOptionalAfkAndXp() {
        List<Integer> bounds=new ArrayList<>();
        ZulrahFeroxReturn run=ZulrahFeroxReturn.select(bound->{bounds.add(bound);return 0;},(lo,hi)->lo);
        pump(run);
        assertEquals(Arrays.asList(10,35),bounds);
        assertEquals(ZulrahFeroxReturn.Route.CAMERA,run.route);
        assertTrue(run.poolAfk);assertTrue(run.xpCheck);
    }
    @Test public void productionSelectionAlwaysUsesTestThreeCameraRoute() {
        ZulrahFeroxReturn run=ZulrahFeroxReturn.select(bound->bound-1,(lo,hi)->lo);
        assertEquals(ZulrahFeroxReturn.Route.CAMERA,run.route);
        assertFalse(run.poolAfk);assertFalse(run.xpCheck);
    }
    @Test public void xpSkillIsSelectedOnceFromRangedMagicOrHitpoints() {
        net.runelite.api.Skill[] expected={net.runelite.api.Skill.RANGED,net.runelite.api.Skill.MAGIC,net.runelite.api.Skill.HITPOINTS};
        for(int choice=0;choice<3;choice++) {
            final int selected=choice;
            ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,false,true,
                (lo,hi)->lo==0 && hi==2?selected:lo);
            assertEquals(expected[choice],run.xpSkill);
            pump(run);
            assertEquals(expected[choice],run.xpSkill);
        }
    }
    @Test public void cameraRouteExtrasHaveOrderedSinglePoolClickAndXpBeforeRegearReady() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,true,true,(lo,hi)->hi);
        pump(run);
        assertEquals(1,Collections.frequency(calls,"drink"));
        assertEquals(1,Collections.frequency(calls,"park"));
        assertEquals(1,Collections.frequency(calls,"xp"));
        assertTrue(calls.indexOf("camera")<calls.indexOf("drink"));
        assertTrue(calls.indexOf("drink")<calls.indexOf("park"));
        assertTrue(calls.indexOf("close")<calls.indexOf("skills"));
        assertTrue(calls.indexOf("xp")<calls.indexOf("inventory"));
        assertEquals(2,Collections.frequency(calls,"bank"));
        assertFalse(calls.contains("kbd-walk"));
    }
    @Test public void sameAfkAndXpDetoursApplyToKbdRoute() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.KBD,true,true,(lo,hi)->lo);
        pump(run);
        assertTrue(calls.containsAll(Arrays.asList("kbd-walk","drink","park","close","skills","xp","inventory")));
        assertFalse(calls.contains("camera"));
        assertEquals(1,Collections.frequency(calls,"drink"));
    }
    @Test public void unselectedDetoursDoNotIssueExtraInputsAndDoneNeverActsAgain() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.KBD,false,false,(lo,hi)->lo);
        pump(run);
        assertFalse(calls.contains("park"));assertFalse(calls.contains("xp"));assertFalse(calls.contains("close"));
        List<String> done=new ArrayList<>(calls);
        run.tick(here,false,true,40000,actions);assertEquals(done,calls);
    }
    @Test public void cameraPoolClickWaitsForArrivalPauseWithoutWalking() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,false,false,(lo,hi)->hi);
        run.tick(here,false,false,0,actions);
        run.tick(here,false,false,1199,actions);
        assertTrue(calls.isEmpty());
        run.tick(here,false,false,1200,actions);
        run.tick(here,false,false,1300,actions);
        run.tick(here,false,false,1400,actions);
        assertEquals(Arrays.asList("camera","drink"),calls);
    }
    @Test public void parkedHoldAndXpHoverRespectSelectedMaximumDeadlines() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,true,true,(lo,hi)->hi);
        long parkedAt=-1,hoverAt=-1;
        for(long t=0;t<30000 && !run.ready();t+=100) {
            ZulrahFeroxReturn.Stage before=run.stage();
            run.tick(here,false,bankOpen,t,actions);
            if(before==ZulrahFeroxReturn.Stage.PARK && run.stage()==ZulrahFeroxReturn.Stage.AFK)parkedAt=t;
            if(before==ZulrahFeroxReturn.Stage.AFK && run.stage()!=before)assertTrue(t-parkedAt>=4800);
            if(before==ZulrahFeroxReturn.Stage.HOVER && run.stage()==ZulrahFeroxReturn.Stage.XP_WAIT)hoverAt=t;
            if(before==ZulrahFeroxReturn.Stage.XP_WAIT && run.stage()!=before)assertTrue(t-hoverAt>=4200);
        }
        assertTrue(run.ready());assertTrue(parkedAt>=0);assertTrue(hoverAt>=0);
    }
    @Test public void failedCameraUsesOriginalPoolWalkAndPreservesSelectedExtras() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,true,true,(lo,hi)->lo);
        cameraWorks=false;
        ZulrahFeroxReturn.Actions failedCamera=actions;
        for(long t=0;t<30000 && !run.ready() && !run.failed();t+=100)
            run.tick(here,false,bankOpen,t,failedCamera);
        assertTrue(run.ready(),run.status());
        assertTrue(calls.contains("pool-fallback"));assertFalse(calls.contains("kbd-walk"));
        assertEquals(1,Collections.frequency(calls,"drink"));
        assertEquals(1,Collections.frequency(calls,"park"));
        assertEquals(1,Collections.frequency(calls,"xp"));
    }
    @Test public void stalledPoolStageRecoversWithoutRestartOrReroll() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,true,true,(lo,hi)->lo);
        run.tick(here,false,false,0,actions);
        run.tick(here,false,false,120001L,actions);
        assertEquals(ZulrahFeroxReturn.Stage.POOL_APPROACH,run.stage());
        assertFalse(run.failed());assertTrue(run.poolAfk);assertTrue(run.xpCheck);
        run.tick(here,false,false,120101L,actions);
        assertTrue(calls.contains("pool-fallback"));assertFalse(calls.contains("kbd-walk"));
    }
    @Test public void leavingFeroxStopsInputsInsteadOfContinuingReturn() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.KBD,true,true,(lo,hi)->lo);
        run.tick(new WorldPoint(3200,3200,0),false,false,0,actions);
        assertTrue(run.failed());assertTrue(calls.isEmpty());
    }
    @Test public void longSmartBreakPreservesRouteAndRemainingArrivalPause() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,true,true,(lo,hi)->hi);
        run.tick(here,false,false,0,actions);
        run.pause(100L);
        run.tick(here,false,false,600100L,actions);
        assertFalse(run.failed());assertTrue(calls.isEmpty());
        run.tick(here,false,false,601199L,actions);
        assertTrue(calls.isEmpty());
        run.tick(here,false,false,601200L,actions);
        run.tick(here,false,false,601300L,actions);
        assertEquals(Collections.singletonList("camera"),calls);
        assertEquals(ZulrahFeroxReturn.Route.CAMERA,run.route);
        assertTrue(run.poolAfk);assertTrue(run.xpCheck);
    }
    @Test public void walkingFallbackDoesNotReplacePendingMovementClicks() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,false,false,(lo,hi)->lo);
        run.tick(here,false,false,0,actions);
        run.tick(here,false,false,120001L,actions);
        for(long t=120101;t<123000;t+=100)run.tick(here,true,false,t,actions);
        assertFalse(calls.contains("pool-fallback"));
        run.tick(here,false,false,123100,actions);
        assertEquals(1,Collections.frequency(calls,"pool-fallback"));
    }
    @Test public void recordedPoolLandingProceedsToBankWithoutExtraPoolClick() {
        ZulrahFeroxReturn run=new ZulrahFeroxReturn(ZulrahFeroxReturn.Route.CAMERA,false,false,(lo,hi)->lo);
        for(long t=0;t<15000 && !run.ready();t+=100) {
            if(run.stage()==ZulrahFeroxReturn.Stage.RESTORE)here=new WorldPoint(3129,3635,0);
            run.tick(here,false,bankOpen,t,actions);
        }
        assertTrue(run.ready(),run.status());
        assertEquals(1,Collections.frequency(calls,"drink"));
        assertEquals(1,Collections.frequency(calls,"bank"));
    }
}
