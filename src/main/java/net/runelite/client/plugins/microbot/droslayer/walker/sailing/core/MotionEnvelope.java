package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

/** Bounded-latency motion forecasts. Candidate trajectories are checked on LIVE collision, never imagery.
 * This is a bounded model, not a claim to reproduce every server effect or network delay. */
public final class MotionEnvelope {
    private MotionEnvelope() { }
    public static double stoppingDistance(double speed,int latencyTicks) {return stoppingDistance(speed,1,latencyTicks);}
    public static double stoppingDistance(double speed,double acceleration,int latencyTicks) {
        // Sailing clamps speed to the selected mode's cap; the acceleration varbit
        // is NOT a physical braking/deceleration constant. Reserve command latency
        // plus one observation tick, not an invented v^2/(2a) stopping distance.
        return Math.max(0,speed)*(Math.max(0,latencyTicks)+1)+.5;
    }
    public static boolean straight(SeaGrid g,Hull h,SeaPoint p,int heading,double distance) {
        SeaPose from=h.pose(p,heading);return h.translationClear(g,from,from.project(heading,distance),heading,true);
    }
    public static boolean brake(SeaGrid g,Hull hull,SeaPose pose,double speed,double acceleration,int latency) {
        return hull.bufferedTranslationClear(g,pose,pose.project(pose.heading,stoppingDistance(speed,acceleration,latency)),pose.heading,.0625,true);
    }
    /** Tests immediate and delayed heading changes with half/normal/double-heading-per-tick turn rates. */
    public static boolean course(SeaGrid grid,Hull hull,SeaPose pose,double wanted,double speed,
                                 double speedLimit,double acceleration,int latencyTicks,int coastTicks) {
        speed=Math.max(0,speed);speedLimit=Math.max(0,speedLimit);acceleration=Math.max(.125,acceleration);
        double turn=Math.abs(SeaPose.delta(pose.heading,wanted));
        if(turn<.001) {
            double distance=0,v=speed;
            for(int t=0;t<latencyTicks+coastTicks+2;t++) {
                if(t>=latencyTicks)v=approach(v,speedLimit,acceleration);
                distance+=v;
            }
            distance+=v+.5;
            return hull.bufferedTranslationClear(grid,pose,pose.project(wanted,distance),wanted,.125,true);
        }
        if(turn>2.01)return false; // Moving corrections are deliberately incremental.
        for(int delay=0;delay<=latencyTicks;delay++) for(double rate:new double[]{.5,1,2}) for(int order=0;order<2;order++) {
            SeaPose at=pose;double v=speed;
            int duration=delay+(int)Math.ceil(turn/rate)+coastTicks;
            for(int tick=0;tick<duration;tick++) {
                double nextHeading=tick<delay?at.heading:at.heading+Math.copySign(Math.min(rate,Math.abs(SeaPose.delta(at.heading,wanted))),SeaPose.delta(at.heading,wanted));
                double nv=tick<delay?v:approach(v,speedLimit,acceleration);
                // Include both movement-before-turn and movement-after-turn orderings.
                SeaPose a=at.project(at.heading,nv),b=at.project(nextHeading,nv);
                a=new SeaPose(a.x,a.y,nextHeading);b=new SeaPose(b.x,b.y,nextHeading);
                if(!hull.motionClear(grid,at,a,true,.125)||!hull.motionClear(grid,at,b,true,.125))return false;
                at=order==0?a:b;v=nv;
            }
            if(!brake(grid,hull,at,v,acceleration,0))return false;
        }
        return true;
    }
    public static boolean turn(SeaGrid g,Hull h,SeaPoint p,int from,int to,double speed,int latency) {
        return course(g,h,h.pose(p,from),to,speed,speed,1,latency,2);
    }
    static double approach(double current,double wanted,double amount) {
        return Math.max(0,Math.min(wanted,current+amount));
    }
}
