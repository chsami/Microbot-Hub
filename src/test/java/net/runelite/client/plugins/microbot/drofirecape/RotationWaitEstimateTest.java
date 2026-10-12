/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RotationWaitEstimateTest {
    @Test public void everyRotationEstimateMatchesFuturePredictorWindow() {
        for(int minute=0;minute<64;minute++)for(int second:new int[]{0,7,8,43,44,59})
            for(int desired=0;desired<=15;desired++) {
                int wait=FcRotationWaitEstimate.secondsUntil(minute,second,desired);
                assertTrue(wait>=0);int total=second+wait;
                int rotation=FcPredictorGate.rotationForColumn(FcSpawnPredictor.rotationColumn(minute+total/60));
                assertTrue(desired==0||desired==rotation);
                assertTrue(total%60>=8&&total%60<=43);
                if(wait>0) {
                    int before=total-1;
                    int earlier=FcPredictorGate.rotationForColumn(FcSpawnPredictor.rotationColumn(minute+before/60));
                    assertFalse((desired==0||earlier==desired)&&before%60>=8&&before%60<=43);
                }
            }
    }
    @Test public void unknownClockHasNoInventedCountdown() {
        assertEquals("",FcRotationWaitEstimate.suffix(-1,10,5));
        assertEquals("",FcRotationWaitEstimate.suffix(10,-1,5));
    }
    @Test public void anyRotationCountsDownOnlyToSafeSecond() {
        assertEquals(" (~8 sec)",FcRotationWaitEstimate.suffix(10,0,0));
        assertEquals("",FcRotationWaitEstimate.suffix(10,20,0));
        assertEquals(" (~9 sec)",FcRotationWaitEstimate.suffix(10,59,0));
    }
    @Test public void rotationWaitReasonIncludesEstimateWithoutChangingEntryDecision() {
        FcPredictorGate gate=new FcPredictorGate();Object source=new Object();
        int minute=100,current=FcPredictorGate.rotationForColumn(FcSpawnPredictor.rotationColumn(minute));
        int desired=current==15?1:current+1;
        for(int second=18;second<=20;second++) {
            FcPredictorGate.Sample sample=new FcPredictorGate.Sample(source,323,5,current,minute,second,
                1000+second*1000,false,true,false,"test");
            gate.reason(sample,sample.capturedAt,desired);
        }
        FcPredictorGate.Sample sample=new FcPredictorGate.Sample(source,323,5,current,minute,20,21000,false,true,false,"test");
        assertTrue(gate.reason(sample,21000,desired).contains("~"));
        assertFalse(gate.entryReady(sample,21000,desired));
    }
}
