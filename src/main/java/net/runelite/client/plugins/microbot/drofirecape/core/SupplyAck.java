/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;

/** A successful UI call is not a consumed dose. Totals cover every dose variant and duplicate slot. */
public final class SupplyAck {
    public enum Kind { OTHER, BREW, RESTORE }
    public enum Result { NONE, WAITING, CONSUMED, TIMED_OUT }
    private int itemId=-1,amount,tick;
    private Kind kind=Kind.OTHER;
    private long dispatchedAt=-1;
    private int brews=-1,restores=-1;
    private final List<DoseRequest> recent=new ArrayList<>();
    private DoseRequest currentDose;
    private static final long LATE_DOSE_MILLIS=6000;
    public static final class Consumption {
        private final int itemId;
        private final Kind kind;
        private Consumption(int itemId,Kind kind){this.itemId=itemId;this.kind=kind;}
        public int itemId(){return itemId;}
        public Kind kind(){return kind;}
    }
    private static final class DoseRequest {
        private final Consumption supply;
        private final long dispatchedAt;
        private boolean consumed;
        private DoseRequest(int id,Kind kind,long at){supply=new Consumption(id,kind);dispatchedAt=at;}
    }
    public boolean pending(){return itemId>=0;}
    public int itemId(){return itemId;}
    public Kind kind(){return kind;}
    public void clearPending(){itemId=-1;amount=0;tick=-1;kind=Kind.OTHER;dispatchedAt=-1;currentDose=null;}
    public void reset(){clearPending();recent.clear();brews=restores=-1;}
    public void sent(int id,int count,int now,Kind type) {
        clearPending();itemId=id;amount=count;tick=now;kind=type;
    }
    /** Keep the pre-click inventory baseline, but start the deadline after dispatch. */
    public void sent(int id,int count,int now,Kind type,long sentAt) {
        sent(id,count,now,type);dispatchedAt=sentAt;
        recent.removeIf(request->sentAt-request.dispatchedAt>LATE_DOSE_MILLIS);
        if(type==Kind.OTHER)return;
        if(type==Kind.BREW)brews=count;else restores=count;
        currentDose=new DoseRequest(id,type,sentAt);recent.add(currentDose);
    }
    /** Expired pending input does not erase late inventory evidence. Credit at
     * most one dose per dispatched request, in dispatch order, and only after a
     * family-wide dose decrease. A disappearing whole bottle is not several uses. */
    public List<Consumption> observeDoses(int liveBrews,int liveRestores,long observedAt) {
        recent.removeIf(request->observedAt-request.dispatchedAt>LATE_DOSE_MILLIS);
        int usedBrews=brews<0?0:Math.max(0,brews-liveBrews);
        int usedRestores=restores<0?0:Math.max(0,restores-liveRestores);
        brews=liveBrews;restores=liveRestores;
        if(usedBrews>recent.stream().filter(r->r.supply.kind()==Kind.BREW).count())usedBrews=0;
        if(usedRestores>recent.stream().filter(r->r.supply.kind()==Kind.RESTORE).count())usedRestores=0;
        List<Consumption> consumed=new ArrayList<>();
        for(Iterator<DoseRequest> it=recent.iterator();it.hasNext();) {
            DoseRequest request=it.next();
            boolean brew=request.supply.kind()==Kind.BREW;
            if((brew?usedBrews:usedRestores)<=0)continue;
            if(brew)usedBrews--;else usedRestores--;
            request.consumed=true;consumed.add(request.supply);it.remove();
        }
        return consumed;
    }
    public Result observe(int liveCount,int now) {
        return observe(liveCount,now,5);
    }
    /** A caller may use a shorter acknowledgement deadline for urgent healing. */
    public Result observe(int liveCount,int now,int timeoutTicks) {
        if(!pending())return Result.NONE;
        if(now>tick&&liveCount<amount)return Result.CONSUMED;
        return now<tick||now-tick>=timeoutTicks?Result.TIMED_OUT:Result.WAITING;
    }
    public Result observe(int liveCount,int now,long observedAt,int timeoutTicks) {
        if(dispatchedAt<0)return observe(liveCount,now,timeoutTicks);
        if(!pending())return Result.NONE;
        if(currentDose!=null?currentDose.consumed:liveCount<amount)return Result.CONSUMED;
        return observedAt-dispatchedAt>=Math.max(1,timeoutTicks)*600L?Result.TIMED_OUT:Result.WAITING;
    }
}
