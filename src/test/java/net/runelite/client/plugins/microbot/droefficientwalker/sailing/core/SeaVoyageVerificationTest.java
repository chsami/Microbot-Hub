package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Full voyages with native hulls, delayed input, fractional poses and observed arrival.
 * The boat is a bounded test model, not a replacement for in-client verification. */
public class SeaVoyageVerificationTest {
    private static final Hull SLOOP=Hull.nativeBounds(0,-256,384,1280,0);
    private static final SeaGrid OPEN=new SeaGrid() {
        public byte cell(int x,int y) {return OPEN;}
        public boolean live(int x,int y) {return true;}
    };
    private static final class Pending {
        final SeaPilot.Decision decision;final int due;
        Pending(SeaPilot.Decision d,int due) {decision=d;this.due=due;}
    }
    static void sail(SeaGrid grid,SeaPose start,SeaPoint goal,int initialMode,int delay) {
        SeaPilot pilot=new SeaPilot(new SeaPilot.Settings(6,28,0,60000,8000,180000,550,100,
                36,3,2),goal,0);
        SeaPose pose=start;int mode=initialMode;double speed=mode==2?3.5:mode==1?1:0;
        List<Pending> pending=new ArrayList<>();
        String last="";List<String> trace=new ArrayList<>();
        for(int tick=1;tick<=400;tick++) {
            for(Iterator<Pending> it=pending.iterator();it.hasNext();) {
                Pending p=it.next();if(p.due>tick)continue;it.remove();
                if(p.decision.action==SeaPilot.Action.STEER)pose=new SeaPose(pose.x,pose.y,p.decision.heading);
                else mode=p.decision.action==SeaPilot.Action.START?2:p.decision.action==SeaPilot.Action.SLOW?1:0;
            }
            assertTrue(SLOOP.clear(grid,pose,true), "Observed native hull must remain clear at tick "+tick);
            SeaPilot.Decision d=pilot.step(grid,SLOOP,pose,mode,3.5,.5,false,tick,1000,tick*600L,()->false);
            String event=d.action+" h="+d.heading+" "+d.reason;
            if(!event.equals(last)) {trace.add(tick+" "+pose+" mode="+mode+" "+event);last=event;}
            if(trace.size()>20)trace.remove(0);
            assertNotEquals(SeaPilot.Action.BLOCKED, d.action, "Unexpected permanent failure: "+d.reason+"\n"+String.join("\n",trace));
            if(d.action==SeaPilot.Action.ARRIVED) {
                assertEquals(0,mode);assertEquals(0,speed,0.001);assertTrue(pose.distance(goal)<=6);return;
            }
            if(d.action==SeaPilot.Action.START||d.action==SeaPilot.Action.SLOW||d.action==SeaPilot.Action.STOP||d.action==SeaPilot.Action.STEER) {
                pending.add(new Pending(d,tick+delay));pilot.dispatched(d,true);
            }
            speed=MotionEnvelope.approach(speed,mode==2?3.5:mode==1?1:0,.5);
            SeaPose next=pose.project(pose.heading,speed);
            assertTrue(SLOOP.translationClear(grid,pose,next,pose.heading,true), "No simulated movement through rocks at tick "+tick);
            pose=next;
        }
        fail("Voyage did not arrive: "+pilot.mode()+" at "+pose);
    }
    @Test public void longOpenWaterVoyageWithDelayedSailChangesArrivesStopped() {
        sail(OPEN,new SeaPose(100.25,100,0),new SeaPoint(320,100),0,2);
    }
    @Test public void aNewVoyageCanTakeOverAnAlreadyMovingBoat() {
        sail(OPEN,new SeaPose(100.25,100,0),new SeaPoint(250,140),2,2);
    }
    @Test public void narrowStraightChannelUsesSlowForwardAndArrivesStopped() {
        SeaGrid channel=new SeaGrid() {
            public byte cell(int x,int y) {return y>=98&&y<=102?OPEN:BLOCKED;}
            public boolean live(int x,int y) {return true;}
        };
        sail(channel,new SeaPose(100.25,100,0),new SeaPoint(165,100),0,2);
    }
    @Test public void coastRouteKeepsTheNativeHullOutsideARock() {
        SeaGrid coast=new SeaGrid() {
            public byte cell(int x,int y) {return x>=150&&x<=165&&y>=94&&y<=106?BLOCKED:OPEN;}
            public boolean live(int x,int y) {return true;}
        };
        sail(coast,new SeaPose(100.25,100,0),new SeaPoint(220,100),0,2);
    }
}
