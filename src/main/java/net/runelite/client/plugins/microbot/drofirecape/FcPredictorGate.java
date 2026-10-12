/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.Method;
import java.time.LocalTime;
import net.runelite.client.plugins.microbot.drofirecape.core.WaveBook;

/**
 * Fail-closed entry gate for the embedded server-clock predictor. Reflection
 * remains for optional external instance hints and older compatibility tests.
 * No computer-clock or desired-rotation substitution.
 * Pure Java 11 so the reflection contract and stale-clock guard are testable.
 */
final class FcPredictorGate {
    static final int FIRST_ENTRY_SECOND=8, LAST_ENTRY_SECOND=43;
    private Object source;
    private int world=-1, lastSecond=-1, lastMinute=-1, advances;
    private long lastAdvanceAt;

    static final class Sample {
        final Object source;
        final int world, column, rotation, minute, second;
        final long capturedAt;
        final boolean inside, ready, safety;
        final String detail;
        Sample(Object source,int world,int column,int rotation,int minute,int second,
               long capturedAt,boolean inside,boolean ready,boolean safety,String detail) {
            this.source=source;this.world=world;this.column=column;this.rotation=rotation;
            this.minute=minute;this.second=second;this.capturedAt=capturedAt;
            this.inside=inside;this.ready=ready;this.safety=safety;this.detail=detail;
        }
        static Sample missing(int world,long now,String reason) {
            return new Sample(null,world,-1,-1,-1,-1,now,false,false,true,reason);
        }
        String diagnostic() {
            return "source="+(source==null?"none":source.getClass().getName())+" world="+world
                +" current="+rotation+" column="+column+" minute="+minute+" second="+second
                +" ready="+ready+" safety="+safety+" inside="+inside+" "+detail;
        }
    }

    static int rotationForColumn(int column) {
        return column>=1&&column<=16?WaveBook.rotationAtMinute(column-1):-1;
    }

    /** Called with client state on the client thread by FcActions. */
    static Sample read(Object plugin,int world,long now,boolean inside) {
        if(plugin==null)return Sample.missing(world,now,"Enable FC Spawn Predictor; no entry without its current rotation");
        try {
            if(inside) {
                boolean active=Boolean.TRUE.equals(optional(plugin,"isFightCavesActive"));
                int rotation=number(optional(plugin,"getCurrentRotation"));
                return new Sample(plugin,world,-1,rotation,-1,-1,now,true,
                    active&&rotation>=1&&rotation<=15,false,"Instance rotation (not outside clock)");
            }
            if(!Boolean.TRUE.equals(optional(plugin,"isInTzhaarArea")))
                return new Sample(plugin,world,-1,-1,-1,-1,now,false,false,true,"Predictor is not ready in outer TzHaar");
            int column=number(optional(plugin,"getRotationCol"));
            int rotation=rotationForColumn(column);
            int minute=-1,second=-1;
            boolean ready=false;
            String api;
            // Modern upstream API: authoritative DATE_MINUTES/DATE_SECONDS values.
            Object seconds=optional(plugin,"getServerSeconds");
            if(seconds instanceof Number) {
                minute=number(optional(plugin,"getServerTime"));second=number(seconds);
                ready=Boolean.TRUE.equals(optional(plugin,"hasServerTime"))&&minute>=0;
                api="server-varp API";
            } else {
                // Older Plugin Hub builds publish a calibrated LocalTime instead.
                Object time=optional(plugin,"getServerUTCTime");
                if(time instanceof LocalTime) {
                    LocalTime t=(LocalTime)time;minute=t.getHour()*60+t.getMinute();second=t.getSecond();
                    ready=Boolean.TRUE.equals(optional(plugin,"isServerUTCTimeSecondSet"));
                }
                api="calibrated-clock API";
            }
            Object safe=optional(plugin,"isActiveSafetyNet");
            Object near=optional(plugin,"isNearMinuteChange");
            boolean safety=Boolean.TRUE.equals(safe)||Boolean.TRUE.equals(near)
                ||(!(safe instanceof Boolean)&&!(near instanceof Boolean));
            ready=ready&&rotation>=1&&rotation<=15&&second>=0&&second<=59;
            return new Sample(plugin,world,column,rotation,minute,second,now,false,ready,safety,api);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
            return new Sample(plugin,world,-1,-1,-1,-1,now,inside,false,true,
                "Predictor API unavailable: "+e.getClass().getSimpleName()+" (entry blocked)");
        }
    }
    static int instanceWave(Object plugin) {
        if(plugin==null)return -1;
        try {
            if(!Boolean.TRUE.equals(optional(plugin,"isFightCavesActive")))return -1;
            int wave=number(optional(plugin,"getCurrentWave"));
            return wave>=1&&wave<=63?wave:-1;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){return -1;}
    }

    private static Object optional(Object plugin,String method) throws ReflectiveOperationException {
        final Method m;
        try {m=plugin.getClass().getMethod(method);}catch(NoSuchMethodException e){return null;}
        return m.invoke(plugin);
    }
    private static int number(Object value){return value instanceof Number?((Number)value).intValue():-1;}

    synchronized void reset(){source=null;world=-1;lastSecond=lastMinute=-1;advances=0;lastAdvanceAt=0;}
    private void sample(Sample s,long now) {
        if(s==null||!s.ready||s.inside||now<s.capturedAt||now-s.capturedAt>1000) {reset();return;}
        if(source!=s.source||world!=s.world) {
            reset();source=s.source;world=s.world;lastSecond=s.second;lastMinute=s.minute;lastAdvanceAt=now;return;
        }
        if(s.second==lastSecond&&s.minute==lastMinute)return;
        int delta=Math.floorMod(s.second-lastSecond,60);
        // Clock jumps (including a suspended client) must reacquire evidence.
        if(delta>=1&&delta<=3&&now-lastAdvanceAt<=3500)advances=Math.min(advances+1,3);
        else advances=0;
        lastSecond=s.second;lastMinute=s.minute;lastAdvanceAt=now;
    }
    synchronized boolean entryReady(Sample s,long now){return entryReady(s,now,0);}
    synchronized boolean entryReady(Sample s,long now,int requiredRotation) {
        sample(s,now);
        return s!=null&&s.source!=null&&s.ready&&!s.inside&&!s.safety&&s.world==world
            &&WaveBook.validRotation(s.rotation)&&(requiredRotation==0||s.rotation==requiredRotation)&&s.second>=FIRST_ENTRY_SECOND&&s.second<=LAST_ENTRY_SECOND
            &&advances>=2&&now>=lastAdvanceAt&&now-lastAdvanceAt<=3500;
    }
    synchronized String reason(Sample s,long now){return reason(s,now,0);}
    synchronized String reason(Sample s,long now,int requiredRotation) {
        boolean allowed=entryReady(s,now,requiredRotation);
        if(s==null)return "Waiting for FC Spawn Predictor";
        if(s.source==null||!s.ready)return s.detail;
        if(s.inside)return "Outside-cave predictor gate is not an instance-resume gate";
        if(now<s.capturedAt||now-s.capturedAt>1000||now-lastAdvanceAt>3500)return "Predictor clock stale; entry blocked";
        if(requiredRotation!=0&&s.rotation!=requiredRotation)return "Current rotation "+s.rotation+"; waiting here for "+requiredRotation+net.runelite.client.plugins.microbot.drofirecape.FcRotationWaitEstimate.suffix(s.minute,s.second,requiredRotation);
        if(s.safety||s.second<FIRST_ENTRY_SECOND||s.second>LAST_ENTRY_SECOND)return "Rotation "+s.rotation+"; waiting for a safe entry second (8-43)"+net.runelite.client.plugins.microbot.drofirecape.FcRotationWaitEstimate.suffix(s.minute,s.second,requiredRotation);
        if(!allowed)return "Rotation "+s.rotation+"; confirming advancing predictor clock";
        return "FC Spawn Predictor confirms rotation "+s.rotation+"; guarded entry ready";
    }
}
