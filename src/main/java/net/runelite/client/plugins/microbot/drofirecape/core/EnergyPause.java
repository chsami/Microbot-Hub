/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** One wave-end logout request, then recovery and an observed world-hop resume. */
public final class EnergyPause {
    public enum State { OFF, REQUESTED, RESTING, ARMING, HOPPING }
    private volatile State state=State.OFF;
    private int requestedWave=-1,lastRequestedWave=-1,sourceWorld=-1,targetWorld=-1;
    private int clearSince=-1,readySince=-1,hopAttempts;
    private boolean confirmed;
    private long requestAt,hopAt,retryAt;
    public State state(){return state;}
    public int requestedWave(){return requestedWave;}
    public int nextWave(){return Math.min(63,requestedWave+1);}
    public int targetWorld(){return targetWorld;}
    public int hopAttempts(){return hopAttempts;}
    public boolean confirmed(){return confirmed;}
    public boolean recovering(){return state==State.RESTING||state==State.ARMING||state==State.HOPPING;}
    public void reset(){state=State.OFF;requestedWave=lastRequestedWave=sourceWorld=targetWorld=-1;
        clearSince=readySince=-1;confirmed=false;requestAt=hopAt=retryAt=0;hopAttempts=0;}
    public boolean shouldRequest(boolean enabled,int startWave,int wave,boolean active) {
        return enabled&&wave>=Math.max(1,startWave)&&wave<63&&active&&state==State.OFF&&wave!=lastRequestedWave;
    }
    public void requested(int wave,int world,long now){state=State.REQUESTED;requestedWave=lastRequestedWave=wave;
        sourceWorld=world;targetWorld=-1;requestAt=now;confirmed=false;clearSince=readySince=-1;hopAttempts=0;retryAt=0;}
    public void confirmation(){if(state==State.REQUESTED)confirmed=true;}
    /** Three distinct clear ticks plus the last launch's flight allowance; split babies reset it. */
    public void observe(int wave,boolean noMobs,int tick,int lastDangerTick,long now) {
        if(state==State.REQUESTED&&wave>requestedWave){resumed();return;}
        if(!noMobs){clearSince=-1;return;}
        if(clearSince<0||tick<clearSince)clearSince=tick;
        if(state==State.REQUESTED&&confirmed&&wave==requestedWave&&clear(tick,lastDangerTick))state=State.RESTING;
    }
    public boolean clear(int tick,int lastDangerTick){return clearSince>=0&&tick-clearSince>=3&&tick-lastDangerTick>=8;}
    public void arm(){if(state==State.RESTING)state=State.ARMING;}
    public void recoverAgain(){if(state==State.ARMING)state=State.RESTING;}
    public boolean canHop(long now){return state==State.ARMING&&hopAttempts<3&&now>=retryAt;}
    public void hopping(int target,long now){state=State.HOPPING;targetWorld=target;hopAt=now;hopAttempts++;readySince=-1;}
    public boolean hopExpired(long now){return state==State.HOPPING&&now-hopAt>=35_000;}
    /** A rejected/timed-out request is retryable, but never a successful resume. */
    public void hopFailed(long now){if(state==State.HOPPING){state=State.ARMING;readySince=-1;retryAt=now+5000L*Math.max(1,hopAttempts);}}
    public boolean hopBlocked(){return hopAttempts>=3&&state==State.ARMING;}
    public boolean arrivedWorld(int world){return targetWorld>0&&world==targetWorld&&(state==State.HOPPING||state==State.ARMING);}
    public boolean resumedScene(boolean freshReady,int world,int tick,long capturedAt) {
        if((state!=State.HOPPING&&state!=State.ARMING)||targetWorld<=0||!freshReady||world!=targetWorld||capturedAt<=hopAt){readySince=-1;return false;}
        if(readySince<0||tick<readySince)readySince=tick;
        return tick-readySince>=2;
    }
    public void resumed(){state=State.OFF;confirmed=false;clearSince=readySince=-1;targetWorld=-1;retryAt=0;}
    public boolean requestExpired(long now){return state==State.REQUESTED&&now-requestAt>=180_000;}
    /** Separate from activeRun's next-wave hint: a restart must retain ownership of the pause. */
    public String saved(){return state==State.OFF?"":state+":"+requestedWave+":"+confirmed+":"+sourceWorld+":"+targetWorld;}
    public boolean restore(String saved,long now) {
        if(saved==null||saved.isEmpty())return false;
        try {
            String[] p=saved.split(":");State restored=State.valueOf(p[0]);int wave=Integer.parseInt(p[1]);
            if(p.length!=5||wave<1||wave>=63||restored==State.OFF)return false;
            requestedWave=lastRequestedWave=wave;confirmed=Boolean.parseBoolean(p[2]);sourceWorld=Integer.parseInt(p[3]);targetWorld=Integer.parseInt(p[4]);
            // Recheck clear scene and all recovery targets after a restart; a hop
            // already sent retains its expected world and never clicks Logout again.
            state=restored==State.HOPPING?State.HOPPING:confirmed?State.RESTING:State.REQUESTED;
            requestAt=hopAt=now;clearSince=readySince=-1;hopAttempts=0;retryAt=0;return true;
        }catch(RuntimeException e){reset();return false;}
    }
}
