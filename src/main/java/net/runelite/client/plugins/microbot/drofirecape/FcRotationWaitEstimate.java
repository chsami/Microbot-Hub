/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

/** Display-only estimate from the predictor's server clock and safe entry window. */
public final class FcRotationWaitEstimate {
    private FcRotationWaitEstimate() { }
    public static int secondsUntil(int minute,int second,int requiredRotation) {
        if(minute<0||second<0||second>59||requiredRotation<0||requiredRotation>15)return -1;
        for(int offset=0;offset<=16;offset++) {
            int rotation=FcPredictorGate.rotationForColumn(FcSpawnPredictor.rotationColumn(minute+offset));
            if(requiredRotation!=0&&rotation!=requiredRotation)continue;
            if(offset==0&&second>FcPredictorGate.LAST_ENTRY_SECOND)continue;
            return offset==0?Math.max(0,FcPredictorGate.FIRST_ENTRY_SECOND-second):
                offset*60+FcPredictorGate.FIRST_ENTRY_SECOND-second;
        }
        return -1;
    }
    public static String suffix(int minute,int second,int requiredRotation) {
        int seconds=secondsUntil(minute,second,requiredRotation);
        if(seconds<=0)return "";
        return " (~"+(seconds>=60?(seconds+59)/60+" min":seconds+" sec")+")";
    }
}
