package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Route-first, tick-driven boat controller. The long-distance chart proposes a
 * corridor; only observed live collision can authorize motion. Normal navigation
 * never uses reverse, arbitrary escape headings, sleep, or stop-to-replan cycles.
 */
public final class SeaPilot {
    public enum Action { WAIT, STEER, START, SLOW, STOP, REVERSE, ARRIVED, BLOCKED }
    public static final class Decision {
        public final Action action;
        public final SeaPoint waypoint;
        public final String reason;
        public final int heading;
        private Decision(Action action,SeaPoint waypoint,String reason,int heading) {
            this.action=action;this.waypoint=waypoint;this.reason=reason;this.heading=heading;
        }
        static Decision of(Action action,String reason) {return new Decision(action,null,reason,-1);}
        static Decision steer(SeaPoint waypoint,int heading,String reason) {return new Decision(Action.STEER,waypoint,reason,heading);}
    }
    public static final class Settings {
        public final int arrivalRadius,lookAhead,maxRecoveries,maxNodes,anticipationTiles,clearanceTiles,latencyTicks;
        public final long stallMillis,noProgressMillis,commandMillis,searchMillis;
        public Settings(int radius,int look,int recoveries,int nodes,long stall,long noProgress,long command,long search) {
            this(radius,look,recoveries,nodes,stall,noProgress,command,search,Math.max(32,look));
        }
        public Settings(int radius,int look,int recoveries,int nodes,long stall,long noProgress,long command,long search,int anticipation) {
            this(radius,look,recoveries,nodes,stall,noProgress,command,search,anticipation,3,2);
        }
        public Settings(int radius,int look,int recoveries,int nodes,long stall,long noProgress,long command,long search,
                        int anticipation,int clearance,int latency) {
            if(radius<1||look<4||nodes<100||stall<1800||noProgress<stall||command<300||search<10
                    ||anticipation<8||anticipation>64||clearance<0||clearance>6||latency<1||latency>4)
                throw new IllegalArgumentException("Invalid pilot settings");
            arrivalRadius=radius;lookAhead=look;maxRecoveries=recoveries;maxNodes=nodes;
            stallMillis=stall;noProgressMillis=noProgress;commandMillis=command;
            searchMillis=Math.min(100,search);anticipationTiles=anticipation;clearanceTiles=clearance;latencyTicks=latency;
        }
    }
    private final Settings settings;
    private final SeaPoint goal;
    private final SeaPathfinder pathfinder=new SeaPathfinder();
    private final ProgressWatch frontiers=new ProgressWatch();
    private SeaPathfinder.Result route;
    private RouteTrack track,voyage;
    private GlobalSeaPlanner.Result voyageResult;
    private SeaPose previous;
    private SeaPoint planGoal;
    private long lastTick=Long.MIN_VALUE,lastCommand=Long.MIN_VALUE/4,lastPlanTick=Long.MIN_VALUE/4;
    private long lastMoved,lastUseful,blockedSince=-1,lastKnown;
    private double speed,cruiseSpeed=3,bestGoalDistance=Double.POSITIVE_INFINITY,globalProgress,localProgress;
    private int stoppedTicks,pendingHeading=-1,pendingMode=-1,headingAttempts,modeAttempts,openTicks,replans,stops,starts,steers;
    private long headingTick,modeTick;
    private String failure,mode="Planning";
    private boolean arrivalBraking,approachMode;

    public SeaPilot(Settings settings,SeaPoint goal,long now) {
        this.settings=Objects.requireNonNull(settings);this.goal=Objects.requireNonNull(goal);
        lastMoved=lastUseful=now;frontiers.reset(now);
    }
    public SeaPoint destination() {return goal;}
    public List<SeaPoint> path() {return voyage!=null?voyage.points():localPath();}
    public List<SeaPoint> localPath() {return route==null?List.of():route.path;}
    public int recoveries() {return 0;} // Kept for the external controller's existing API.
    public boolean needsVoyageReplan() {return blockedSince>=0;}
    public int replans() {return replans;}
    public int stopCount() {return stops;}
    public int startCount() {return starts;}
    public int steerCount() {return steers;}
    public double speed() {return speed;}
    public String mode() {return mode;}
    public String routeSource() {return voyageResult==null?"Observed local chart":voyageResult.detail;}
    public SeaPathfinder.Result lastRoute() {return route;}
    public List<HazardZone> temporaryHazards(long now) {return List.of();}
    public void fail(String reason) {failure=reason;}
    public void seedCruiseSpeed(double value) {if(Double.isFinite(value)&&value>0&&value<=12)cruiseSpeed=value;}
    /** Installed by the single control worker after an epoch-checked asynchronous search. */
    public void setVoyageRoute(GlobalSeaPlanner.Result result) {
        voyageResult=result;
        if(result!=null&&result.path.size()>1) {
            voyage=new RouteTrack(result.path);globalProgress=0;
            if(previous!=null)voyage.update(previous,Math.max(64,previous.distance(result.path.get(0))+16));
        }
    }
    public void resumeAfterPause(long now) {
        pendingMode=pendingHeading=-1;headingAttempts=modeAttempts=0;lastTick=Long.MIN_VALUE;previous=null;
        lastMoved=lastUseful=now;blockedSince=-1;route=null;track=null;planGoal=null;
        lastPlanTick=lastCommand=Long.MIN_VALUE/4;stoppedTicks=0;openTicks=0;speed=0;arrivalBraking=approachMode=false;
    }
    /** A rejected dispatch must not create a fictitious acknowledged command. */
    public void dispatched(Decision decision,boolean accepted) {
        if(!accepted&&(decision.action==Action.STEER||decision.action==Action.START||decision.action==Action.SLOW||decision.action==Action.STOP)) {
            if(decision.action==Action.STEER) {pendingHeading=-1;headingAttempts=0;steers--;}
            else {
                pendingMode=-1;modeAttempts=0;
                if(decision.action==Action.STOP)stops--;else starts--;
            }
        }
    }
    public long etaSeconds(SeaPoint position) {
        if(position==null)return -1;
        double distance=voyage!=null?voyage.remaining():position.distance(goal);
        return (long)Math.ceil(distance/Math.max(.5,speed>.1?speed:cruiseSpeed)*.6);
    }
    public Decision step(SeaGrid grid,Hull hull,SeaPoint position,int heading,boolean moving,
                         long tick,long known,long now,BooleanSupplier cancelled) {
        return step(grid,hull,position,heading,moving,false,tick,known,now,cancelled);
    }
    public Decision step(SeaGrid grid,Hull hull,SeaPoint position,int heading,boolean moving,boolean backwards,
                         long tick,long known,long now,BooleanSupplier cancelled) {
        return step(grid,hull,position==null?null:new SeaPose(position,heading),backwards?3:moving?2:0,
                cruiseSpeed,1,false,tick,known,now,cancelled);
    }
    public Decision step(SeaGrid grid,Hull hull,SeaPose pose,int movementMode,double speedCap,double acceleration,
                         boolean headingAutoStarts,long tick,long known,long now,BooleanSupplier cancelled) {
        if(tick<=lastTick)return waitFor("Waiting for a fresh server tick");
        long elapsedTicks=lastTick==Long.MIN_VALUE?1:tick-lastTick;lastTick=tick;
        if(cancelled.getAsBoolean())failure="Request cancelled";
        if(pose==null||grid==null||hull==null) {failure="Boat pose or live sea chart unavailable";return Decision.of(Action.BLOCKED,failure);}
        boolean observed=previous!=null&&elapsedTicks<=4;
        speed=observed?pose.distance(previous)/elapsedTicks:0;
        if(observed&&speed>12) {failure="Discontinuous boat position; awaiting a new voyage";speed=0;}
        previous=pose;
        boolean moving=movementMode!=0||speed>.05;
        stoppedTicks=movementMode==0&&observed&&speed<.05?stoppedTicks+1:0;
        if(speed>.05)lastMoved=now;
        if(movementMode==0)lastMoved=now; // A commanded stationary wait is not a collision stall.
        if(!Double.isFinite(acceleration)||acceleration<=0)acceleration=1;
        acceleration=Math.max(.125,Math.min(8,acceleration));
        if(Double.isFinite(speedCap)&&speedCap>0&&speedCap<=12)cruiseSpeed=speedCap;
        double fullSpeed=Math.max(Math.max(1,cruiseSpeed),speed);
        if(pendingHeading>=0&&Math.abs(SeaPose.delta(pose.heading,pendingHeading))<.2) {pendingHeading=-1;headingAttempts=0;}
        if(pendingMode==movementMode||pendingMode==2&&movementMode==4) {pendingMode=-1;modeAttempts=0;}
        if(failure!=null)return failSafely(moving,now,tick,failure);
        if(movementMode<0||movementMode>4) {failure="Unsupported boat movement mode "+movementMode;return failSafely(moving,now,tick,failure);}
        if(movementMode==3)return modeCommand(Action.STOP,"Ending backward mode before route following",now,tick);
        if(!hull.clear(grid,pose,true))return blocked(moving,now,tick,"Live hull clearance unavailable: "+hull.obstruction(grid,pose,true));
        if(grid.cell(goal.x,goal.y)==SeaGrid.BLOCKED) {
            failure="Selected destination is blocked/no-go; select open water beside the island";
            return failSafely(moving,now,tick,failure);
        }
        double distance=pose.distance(goal);
        if(distance<bestGoalDistance-.5||known>lastKnown+32) {bestGoalDistance=Math.min(bestGoalDistance,distance);lastKnown=known;lastUseful=now;}
        if(voyage!=null) {
            voyage.update(pose,Math.max(24,speed*8));
            if(voyage.progress()>globalProgress+.5) {globalProgress=voyage.progress();lastUseful=now;}
        }
        if(track!=null) {
            track.update(pose,Math.max(16,speed*7));
            if(voyage==null&&track.progress()>localProgress+.5) {localProgress=track.progress();lastUseful=now;}
        }
        if(now-lastUseful>settings.noProgressMillis) {failure="No forward voyage progress; stopped instead of looping";return failSafely(moving,now,tick,failure);}
        if(movementMode>0&&now-lastMoved>settings.stallMillis) {
            route=null;track=null;planGoal=null;lastPlanTick=Long.MIN_VALUE/4;
            return blocked(moving,now,tick,"Forward command produced no boat movement; stopping to replan the live corridor");
        }
        if(distance<=settings.arrivalRadius&&grid.open(goal.x,goal.y))arrivalBraking=true;
        if(arrivalBraking) {
            mode="Arrival";
            if(movementMode!=0)return modeCommand(Action.STOP,"Stopping at destination",now,tick);
            if(stoppedTicks<2)return waitFor("Verifying two observed stopped ticks");
            if(distance<=settings.arrivalRadius)return Decision.of(Action.ARRIVED,"Stopped inside arrival radius");
            arrivalBraking=false;route=null;track=null; // Do not report an overshoot as arrival.
        }
        SeaPoint target=localGoal(pose);
        boolean exhausted=track==null||track.remaining()<Math.max(8,speed*5);
        boolean offCourse=track!=null&&track.crossTrack()>Math.max(3,hull.beam);
        boolean stationaryTurn=!moving&&!headingAutoStarts;
        SeaPose aim=track==null?null:visibleAim(grid,hull,pose,Math.max(5,Math.min(settings.lookAhead,6+speed*4)),stationaryTurn);
        boolean replan=exhausted||offCourse||aim==null;
        if(replan&&tick-lastPlanTick>=2) {
            lastPlanTick=tick;replans++;
            Hull lattice=hull.atLattice(pose);
            SeaPathfinder.Result candidate=pathfinder.find(grid,lattice,pose.tile(),pose.heading16(),target,
                    target.equals(goal)?Math.max(0,settings.arrivalRadius-2):3,settings.maxNodes,settings.searchMillis,
                    frontiers::visits,cancelled,settings.clearanceTiles);
            if(cancelled.getAsBoolean())return waitFor("Cancelled during local planning");
            if(candidate.usable()) {
                RouteTrack proposed=new RouteTrack(candidate.path,lattice.latticeDx,lattice.latticeDy);
                proposed.update(pose,12);
                SeaPose proposedAim=visibleAim(proposed,grid,hull,pose,Math.max(5,Math.min(settings.lookAhead,6+speed*4)),stationaryTurn);
                // A fresh geometric route must not discard a still executable old prefix.
                if(proposedAim!=null) {
                    route=candidate;track=proposed;planGoal=target;localProgress=0;aim=proposedAim;
                } else if(track==null||aim==null)route=candidate;
            } else if(track==null||aim==null)route=candidate;
        }
        if(aim==null)return blocked(moving,now,tick,"No executable local corridor"+(route==null?"":": "+route.detail));
        double bearing=SeaPose.bearing(pose.x,pose.y,aim.x,aim.y);
        int wanted=Math.floorMod((int)Math.round(bearing),16);
        // Deadband prevents alternation at the midpoint between two native headings.
        if(Math.abs(SeaPose.delta(pose.heading,bearing))<.58)wanted=pose.heading16();
        double error=Math.abs(SeaPose.delta(pose.heading,wanted));
        double approach=MotionEnvelope.stoppingDistance(Math.max(speed,1),acceleration,settings.latencyTicks)+settings.arrivalRadius+3;
        if(distance<approach)approachMode=true;
        boolean arrivalZone=approachMode;
        boolean wide=hull.bufferedTranslationClear(grid,pose,pose.project(pose.heading,Math.max(5,Math.min(18,fullSpeed*4))),
                pose.heading,settings.clearanceTiles,true);
        boolean bend=track.bendAhead(pose.heading16(),Math.max(10,fullSpeed*6))>=3;
        boolean fullSafe=error<=2&&MotionEnvelope.course(grid,hull,pose,wanted,speed,fullSpeed,acceleration,settings.latencyTicks,2);
        boolean cruise=wide&&!arrivalZone&&!bend&&fullSafe;
        openTicks=cruise?openTicks+1:0;
        int desiredMode=cruise&&(movementMode==0||movementMode==2||movementMode==4||openTicks>=3)?2:1;
        mode=desiredMode==2?"Cruise":"Channel / approach";
        // A slowdown is sent before a sharp turn. No full-stop unless the live envelope demands it.
        if((movementMode==2||movementMode==4)&&desiredMode==1) {
            if(!MotionEnvelope.brake(grid,hull,pose,speed,acceleration,settings.latencyTicks))
                return modeCommand(Action.STOP,"Emergency brake: observed stopping corridor compromised",now,tick);
            return modeCommand(Action.SLOW,"Reducing speed before channel, bend or arrival",now,tick);
        }
        double limit=desiredMode==2?fullSpeed:1;
        if(error>.35) {
            if(pendingHeading>=0&&tick-headingTick<=settings.latencyTicks) {
                if(!MotionEnvelope.brake(grid,hull,pose,speed,acceleration,settings.latencyTicks))
                    return modeCommand(Action.STOP,"Emergency brake during heading acknowledgement",now,tick);
                return waitFor("Heading command in flight; live safety still checked");
            }
            int heading=wanted;
            if((moving||headingAutoStarts)&&error>2)heading=Math.floorMod(pose.heading16()+(int)Math.copySign(2,SeaPose.delta(pose.heading,wanted)),16);
            boolean safe;
            if(!moving&&!headingAutoStarts) safe=hull.turnClear(grid,pose,heading,true);
            else safe=MotionEnvelope.course(grid,hull,pose,heading,speed,headingAutoStarts&&!moving?fullSpeed:limit,
                    acceleration,settings.latencyTicks,1);
            if(!safe) {
                if(movementMode==2||movementMode==4)return modeCommand(Action.SLOW,"Slowing for a tighter course change",now,tick);
                if(moving)return modeCommand(Action.STOP,"Braking: turn sweep is not live-clear",now,tick);
                if(headingAutoStarts&&MotionEnvelope.course(grid,hull,pose,pose.heading,0,1,acceleration,settings.latencyTicks,2))
                    return modeCommand(Action.SLOW,"Slow departure before an auto-starting course change",now,tick);
                return blocked(false,now,tick,headingAutoStarts?"Unsafe auto-starting heading change; disable start-on-heading or reposition manually":"No hull-clear turn at this position");
            }
            return steering(pose,heading,now,tick);
        }
        boolean safe=MotionEnvelope.course(grid,hull,pose,pose.heading,speed,limit,acceleration,settings.latencyTicks,2);
        if(!safe) {
            if(moving)return modeCommand(Action.STOP,"Braking before the verified live horizon ends",now,tick);
            return blocked(false,now,tick,"Loaded corridor too short to begin forward movement");
        }
        if(movementMode!=desiredMode&&!(movementMode==4&&desiredMode==2))return modeCommand(desiredMode==2?Action.START:Action.SLOW,
                desiredMode==2?"Set sails: clear open-water corridor":"Slow forward: controlled local corridor",now,tick);
        blockedSince=-1;pendingMode=-1;modeAttempts=0;
        return waitFor(desiredMode==2?"Continuous cruise; following planned course":"Continuous slow forward; following channel");
    }
    private SeaPoint localGoal(SeaPose pose) {
        if(voyage==null||pose.distance(goal)<24||voyage.remaining()<8)return goal;
        // Rejoin the next portion, not the far destination through a peninsula.
        return voyage.ahead(Math.max(24,Math.min(48,settings.anticipationTiles)));
    }
    private SeaPose visibleAim(SeaGrid grid,Hull hull,SeaPose pose,double ahead,boolean stationaryTurn) {
        return visibleAim(track,grid,hull,pose,ahead,stationaryTurn);
    }
    private SeaPose visibleAim(RouteTrack track,SeaGrid grid,Hull hull,SeaPose pose,double ahead,boolean stationaryTurn) {
        if(track==null||track.remaining()<.25)return null;
        double start=Math.min(track.remaining(),ahead);
        for(double d=start;d>=Math.min(1.0,start);d-=1) {
            SeaPose target=track.aheadPose(d);
            if(pose.distance(target)<.5)continue;
            int h=(int)Math.round(SeaPose.bearing(pose.x,pose.y,target.x,target.y))&15;
            if(hull.translationClear(grid,pose,target,h,true)
                    &&hull.translationClear(grid,pose,pose.project(h,pose.distance(target)),h,true)
                    &&(!stationaryTurn||hull.turnClear(grid,pose,h,true)))return target;
        }
        return null;
    }
    private Decision steering(SeaPose pose,int heading,long now,long tick) {
        if(now-lastCommand<settings.commandMillis)return waitFor("Command spacing");
        if(pendingHeading!=heading)headingAttempts=0;
        if(pendingHeading==heading&&tick-headingTick>settings.latencyTicks&&++headingAttempts>3) {
            failure="Heading was not acknowledged after bounded retries";return modeCommand(Action.STOP,failure,now,tick);
        }
        blockedSince=-1;lastCommand=now;pendingHeading=heading;headingTick=tick;steers++;
        return Decision.steer(pose.project(heading,24).tile(),heading,"Minimap course correction h="+heading);
    }
    private Decision modeCommand(Action action,String reason,long now,long tick) {
        int desired=action==Action.START?2:action==Action.SLOW?1:0;
        if(pendingMode==desired&&tick-modeTick<=settings.latencyTicks)return waitFor(reason+" (awaiting mode)");
        if(now-lastCommand<(action==Action.STOP?Math.min(550,settings.commandMillis):settings.commandMillis))return waitFor(reason);
        if(pendingMode!=desired)modeAttempts=0;
        if(pendingMode==desired&&++modeAttempts>4) {failure="Movement control was not acknowledged";return Decision.of(Action.BLOCKED,failure);}
        lastCommand=now;pendingMode=desired;modeTick=tick;
        if(action==Action.STOP) {stops++;pendingHeading=-1;}else starts++;
        return Decision.of(action,reason);
    }
    private Decision blocked(boolean moving,long now,long tick,String reason) {
        mode="Hold";
        if(blockedSince<0)blockedSince=now;
        // Missing connectivity is a planning hold, not an acknowledged movement stall.
        // Retain the voyage and retry fresh charts for the full no-progress window.
        if(now-blockedSince>settings.noProgressMillis)failure=reason+"; no safe corridor after the recovery window";
        if(moving)return modeCommand(Action.STOP,reason,now,tick);
        return failure==null?waitFor(reason):Decision.of(Action.BLOCKED,failure);
    }
    private Decision failSafely(boolean moving,long now,long tick,String reason) {
        mode="Stopped";
        return moving?modeCommand(Action.STOP,reason,now,tick):Decision.of(stoppedTicks>=2?Action.BLOCKED:Action.WAIT,reason);
    }
    private static Decision waitFor(String reason) {return Decision.of(Action.WAIT,reason);}
}
