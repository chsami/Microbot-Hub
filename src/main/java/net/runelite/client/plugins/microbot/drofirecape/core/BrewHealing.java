/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** Threshold/low-prayer healing batches and one optional full overbrew per late wave. */
public final class BrewHealing {
    private boolean active,optional,confirmed;
    private int optionalWave=-1,usedWave=-1;
    public void reset(){cancel();optionalWave=usedWave=-1;}
    public void cancel(){active=optional=confirmed=false;}
    public void confirmed(){if(optional)confirmed=true;}
    public boolean needed(int hp,int max,int threshold,boolean topUp,boolean hasBrew) {
        return needed(hp,max,threshold,topUp,hasBrew,false,0);
    }
    public boolean needed(int hp,int max,int threshold,boolean topUp,boolean hasBrew,boolean overbrew,int wave) {
        if(!hasBrew||hp<=0||max<=0){cancel();return false;}
        if(optional&&(!overbrew||wave<53))cancel();
        if(!optional&&overbrew&&wave>=53&&usedWave!=wave&&hp<=(max*95+99)/100) {
            optional=true;optionalWave=wave;confirmed=false;
        }
        if(optional) {
            int target=max+max*15/100+2;
            if(hp<target)return true;
            if(confirmed)usedWave=optionalWave;
            cancel();return false;
        }
        int target=(max*Math.min(90,Math.max(80,threshold+20))+99)/100;
        if(hp>=target){cancel();return false;}
        // A pre-restore top-up must fit an entire brew dose. Low prayer by itself
        // is not a reason to consume healing supplies or repeatedly reboost.
        int dose=max*15/100+2;
        if(hp*100<=max*threshold||topUp&&max-hp>=dose)active=true;
        return active;
    }
}
