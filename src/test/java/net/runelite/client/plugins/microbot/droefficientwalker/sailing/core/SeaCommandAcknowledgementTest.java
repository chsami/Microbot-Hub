package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Delayed native acknowledgements must belong to the command being retried. */
public class SeaCommandAcknowledgementTest {
    private static final Hull HULL=new Hull(1,2,0);
    private static final SeaPose POSE=new SeaPose(100,50,0);
    private static final SeaGrid OPEN=new SeaGrid() {
        public byte cell(int x,int y) {return OPEN;}
        public boolean live(int x,int y) {return true;}
    };
    private static final SeaGrid CHANNEL=new SeaGrid() {
        public byte cell(int x,int y) {return y>=48&&y<=52?OPEN:BLOCKED;}
        public boolean live(int x,int y) {return true;}
    };
    private static SeaPilot pilot() {
        return new SeaPilot(new SeaPilot.Settings(2,28,0,60000,8000,180000,550,100,
                36,3,2),new SeaPoint(200,50),0);
    }
    private static SeaPilot.Decision step(SeaPilot pilot,SeaGrid grid,SeaPose pose,int mode,int tick) {
        return pilot.step(grid,HULL,pose,mode,3.5,.5,false,tick,1000,tick*600L,()->false);
    }
    private static void delayedFullSails(SeaPilot pilot) {
        for(int tick:new int[]{1,4,7,10,13})
            assertEquals(SeaPilot.Action.START,step(pilot,OPEN,POSE,0,tick).action);
    }
    @Test public void newSlowCommandHasItsOwnRetryBudgetAfterDelayedFullSails() {
        SeaPilot pilot=pilot();delayedFullSails(pilot);
        assertEquals(SeaPilot.Action.SLOW,step(pilot,CHANNEL,POSE,0,14).action);
        assertEquals(SeaPilot.Action.SLOW,step(pilot,CHANNEL,POSE,0,17).action);
        assertEquals(SeaPilot.Action.WAIT,step(pilot,CHANNEL,POSE,1,18).action);
    }
    @Test public void headingRetryDoesNotInheritTheFullSailRetryCount() {
        SeaPilot pilot=pilot();delayedFullSails(pilot);
        SeaPose unacknowledged=new SeaPose(100,50,1);
        assertEquals(SeaPilot.Action.STEER,step(pilot,OPEN,unacknowledged,0,14).action);
        assertEquals(SeaPilot.Action.STEER,step(pilot,OPEN,unacknowledged,0,17).action);
    }
    @Test public void rejectingASpeedCommandDoesNotEraseAnUnacknowledgedHeading() {
        SeaPilot pilot=pilot();SeaPose wrongHeading=new SeaPose(100,50,1);
        assertEquals(SeaPilot.Action.STEER,step(pilot,OPEN,wrongHeading,0,1).action);
        // A newly required slowdown is rejected by the UI. The original heading
        // still has to observe its own deadline instead of being sent twice.
        SeaPilot.Decision slow=step(pilot,CHANNEL,wrongHeading,2,2);
        assertEquals(SeaPilot.Action.SLOW,slow.action);pilot.dispatched(slow,false);
        SeaPilot.Decision pending=step(pilot,OPEN,wrongHeading,0,3);
        assertEquals(SeaPilot.Action.WAIT,pending.action);
        assertTrue(pending.reason.contains("Heading command in flight"));
    }
    @Test public void acknowledgedHeadingDoesNotRenewAnUnacknowledgedSpeedBudget() {
        SeaPilot pilot=pilot();delayedFullSails(pilot);
        assertEquals(SeaPilot.Action.STEER,step(pilot,OPEN,new SeaPose(100,50,1),0,14).action);
        // Heading is now acknowledged, but the earlier sail command still has
        // no observed mode change after its own bounded retry allowance.
        assertEquals(SeaPilot.Action.BLOCKED,step(pilot,OPEN,POSE,0,17).action);
    }
}
