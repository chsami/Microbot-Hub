package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.List;
import java.util.function.LongSupplier;
import net.runelite.api.coords.WorldPoint;

/** Recorded bank-to-Ket-Keh approach only; never sacrifices a cape or assumes entry. */
final class InfernoApproach {
    static final WorldPoint BANK = new WorldPoint(2543, 5141, 0);
    static final WorldPoint KET_KEH = new WorldPoint(2494, 5112, 0);
    private static final List<WorldPoint> ROUTE = List.of(
        BANK, new WorldPoint(2535,5141,0), new WorldPoint(2527,5133,0),
        new WorldPoint(2523,5125,0), new WorldPoint(2523,5121,0),
        new WorldPoint(2518,5115,0), new WorldPoint(2512,5109,0),
        new WorldPoint(2506,5104,0), new WorldPoint(2500,5102,0),
        new WorldPoint(2496,5104,0), new WorldPoint(2495,5110,0), KET_KEH);
    enum Result { WALKING, ENTRY_REQUIRED, STOPPED }
    interface Port {
        WorldPoint location();
        boolean moving();
        boolean allowed();
        boolean canClick(WorldPoint target);
        boolean walk(WorldPoint target);
    }
    private final Port port;
    private final LongSupplier clock;
    private int index;
    private WorldPoint lastLocation,lastTarget;
    private long progressAt;
    private long nextClick;
    private boolean started;
    private Result result = Result.WALKING;
    private String status = "Following recorded route from Mor Ul Rek bank";

    InfernoApproach(Port port, LongSupplier clock) { this.port=port; this.clock=clock; }
    String status() { return status; }
    void resumeAfter(long millis) { if(millis>0){if(started)progressAt+=millis;nextClick+=millis;} }
    Result tick() {
        if (result!=Result.WALKING || !port.allowed()) return result;
        WorldPoint location=port.location();
        long now=clock.getAsLong();
        if (location==null) return stop("Route position unavailable; restart at Mor Ul Rek bank");
        if (!started) {
            if (location.distanceTo(BANK)>6) return stop("Recorded approach starts at Mor Ul Rek bank; move there and restart");
            started=true; progressAt=now;
        }
        // A long minimap leg can skip intermediate waypoints; observed forward progress
        // advances the route, without treating the click target as an arrival receipt.
        int nearest=index;int distance=Integer.MAX_VALUE;
        for(int i=Math.max(0,index-1);i<ROUTE.size();i++) {
            int d=location.distanceTo(ROUTE.get(i));if(d<distance){distance=d;nearest=i;}
        }
        if(location.getPlane()!=0||distance>16)return stop("Left recorded approach; restart at Mor Ul Rek bank");
        if(nearest>index)index=nearest;
        if (!location.equals(lastLocation)) { lastLocation=location; progressAt=now; }
        while (index<ROUTE.size() && location.distanceTo(ROUTE.get(index))<=1) index++;
        if (index==ROUTE.size()) {
            status="Recorded approach complete; preparing local Jump-in The Inferno";
            return result=Result.ENTRY_REQUIRED;
        }
        if (now-progressAt>=20_000L) return stop("Recorded approach stalled; inspect the route and restart");
        boolean moving=port.moving();
        if((!moving||lastTarget!=null&&location.distanceTo(lastTarget)<=6)&&now>=nextClick&&port.allowed()) {
            WorldPoint target=null;
            for(int i=ROUTE.size()-1;i>=index;i--)if(port.canClick(ROUTE.get(i))){target=ROUTE.get(i);break;}
            if(target==null){status="No recorded route point visible on minimap; waiting for progress";return result;}
            if(moving&&target.equals(lastTarget))return result;
            nextClick=now+1_800L;
            if(!port.walk(target))status="Recorded route tile not clickable; waiting for progress";
            else {lastTarget=target;status="Running toward the farthest visible Inferno route point";}
        }
        return result;
    }
    private Result stop(String reason) { status=reason; return result=Result.STOPPED; }
}
