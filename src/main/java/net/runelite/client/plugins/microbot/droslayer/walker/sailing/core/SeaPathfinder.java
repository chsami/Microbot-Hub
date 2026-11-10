package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/** Local heading-aware A*: cached hull tests, direct rays, bounded frontier search. */
public final class SeaPathfinder {
    public enum Outcome { COMPLETE, FRONTIER, TARGET_BLOCKED, START_BLOCKED, NO_ROUTE, SEARCH_LIMIT, CANCELLED }
    public static final class Result {
        public final Outcome outcome;
        public final List<SeaPoint> path;
        public final int expanded;
        public final double cost;
        public final String detail;
        public Result(Outcome outcome, List<SeaPoint> path, int expanded, double cost, String detail) {
            this.outcome = outcome; this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.expanded = expanded; this.cost = cost; this.detail = detail;
        }
        public boolean usable() { return (outcome == Outcome.COMPLETE || outcome == Outcome.FRONTIER) && path.size() > 1; }
    }
    private static final int[] DX = {1, 2, 1, 1, 0, -1, -1, -2, -1, -2, -1, -1, 0, 1, 1, 2};
    private static final int[] DY = {0, 1, 1, 2, 1, 2, 1, 1, 0, -1, -1, -2, -1, -2, -1, -1};
    private static final class Node {
        final SeaPoint p; final int h; final double g, f; final Node parent;
        Node(SeaPoint p, int h, double g, double f, Node parent) { this.p=p; this.h=h; this.g=g; this.f=f; this.parent=parent; }
    }
    /** Caches only against this immutable input snapshot; never reuses old live collision. */
    private static final class Space {
        final SeaGrid grid; final Hull hull;
        final Map<Long, Integer> masks = new HashMap<>(), rotations = new HashMap<>();
        Space(SeaGrid grid, Hull hull) { this.grid=grid; this.hull=hull; }
        boolean fits(SeaPoint p, int h) {
            h &= 15;
            if (p.x<0 || p.y<0 || p.x>16383 || p.y>16383) return false;
            long key = SeaPoint.key(p.x,p.y);
            int mask = masks.getOrDefault(key,0), bit=1<<h;
            if ((mask & (bit<<16))==0) {
                mask |= bit<<16;
                if (hull.clear(grid,p,h,false)) mask |= bit;
                masks.put(key,mask);
            }
            return (mask & bit)!=0;
        }
        boolean turnEdge(SeaPoint p,int edge) {
            edge &= 15;long k=SeaPoint.key(p.x,p.y);int mask=rotations.getOrDefault(k,0),bit=1<<edge;
            if((mask&(bit<<16))==0) {
                mask|=bit<<16;
                // The cached quarter-heading sweep is deliberately inflated. A
                // negative result is not proof of a collision: use the finer,
                // still conservative continuous sweep before rejecting the edge.
                if(hull.turnClear(grid,p,edge,(edge+1)&15,false)
                        ||hull.turnClear(grid,hull.pose(p,edge),(edge+1)&15,false))mask|=bit;
                rotations.put(k,mask);
            }
            return (mask&bit)!=0;
        }
        boolean turn(SeaPoint p,int from,int to) {
            if(!fits(p,to))return false;
            int delta=(int)SeaPose.delta(from,to);
            for(int i=0;i<Math.abs(delta);i++)if(!turnEdge(p,from+(delta>0?i:-i-1)))return false;
            if(Math.abs(delta)==8)for(int i=0;i<8;i++)if(!turnEdge(p,from+i))return false;
            return true;
        }
        boolean line(SeaPoint a, SeaPoint b, int h) {
            int nx=Math.abs(b.x-a.x),ny=Math.abs(b.y-a.y),sx=Integer.signum(b.x-a.x),sy=Integer.signum(b.y-a.y);
            int x=a.x,y=a.y,ix=0,iy=0;
            if (!fits(a,h)) return false;
            while(ix<nx || iy<ny) {
                long l=(1L+2L*ix)*ny,r=(1L+2L*iy)*nx;
                if(l==r) {
                    if(!fits(new SeaPoint(x+sx,y),h)||!fits(new SeaPoint(x,y+sy),h)) return exactLine(a,b,h);
                    x+=sx;y+=sy;ix++;iy++;
                } else if(l<r) {x+=sx;ix++;} else {y+=sy;iy++;}
                if(!fits(new SeaPoint(x,y),h)) return exactLine(a,b,h);
            }
            return true;
        }
        private boolean exactLine(SeaPoint a,SeaPoint b,int h) {
            // A diagonal voyage does not execute the extra orthogonal hull
            // placements sampled by the fast supercover. Verify its actual
            // entire translated footprint rather than discarding a clear channel.
            return fits(b,h)&&hull.translationClear(grid,a,b,h,false);
        }
    }
    private static long key(SeaPoint p, int h) { return ((long)p.x<<18)|((long)p.y<<4)|(h&15); }
    public Result find(SeaGrid grid, Hull hull, SeaPoint start, int heading, SeaPoint goal,
                       int radius, int maxNodes, long maxMillis, ToIntFunction<SeaPoint> visits, BooleanSupplier cancelled) {
        return find(grid,hull,start,heading,goal,radius,maxNodes,maxMillis,visits,cancelled,3);
    }
    public Result find(SeaGrid grid, Hull hull, SeaPoint start, int heading, SeaPoint goal,
                       int radius, int maxNodes, long maxMillis, ToIntFunction<SeaPoint> visits,
                       BooleanSupplier cancelled, int preferredClearance) {
        Objects.requireNonNull(grid); Objects.requireNonNull(hull); Objects.requireNonNull(start); Objects.requireNonNull(goal);
        Objects.requireNonNull(visits); Objects.requireNonNull(cancelled);
        if(radius<0||maxNodes<1||maxMillis<1||preferredClearance<0||preferredClearance>6) throw new IllegalArgumentException("Invalid search settings");
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()) return empty(Outcome.CANCELLED,0,"Cancelled before search");
        Space space = new Space(grid,hull);
        if(!space.fits(start,heading)) return empty(Outcome.START_BLOCKED,0,"Current hull is not known-clear");
        long deadline=System.nanoTime()+Math.min(maxMillis,10_000)*1_000_000;
        boolean targetKnown=grid.cell(goal.x,goal.y)!=SeaGrid.UNKNOWN;
        // An arrival radius permits a clear berth near a blocked centre, not travel through it.
        boolean anyArrival=false;
        for(int x=goal.x-radius;x<=goal.x+radius&&!anyArrival;x++) for(int y=goal.y-radius;y<=goal.y+radius;y++)
            if(Math.hypot(x-goal.x,y-goal.y)<=radius && grid.cell(x,y)==SeaGrid.OPEN) {anyArrival=true;break;}
        if(targetKnown&&!anyArrival) return empty(Outcome.TARGET_BLOCKED,0,"No open arrival tile inside the requested radius");
        int toward=Hull.heading(start,goal);
        if(start.distance(goal)<=radius && targetKnown) return new Result(Outcome.COMPLETE,List.of(start),0,0,"Inside arrival area");
        ShoreClearance clearance=new ShoreClearance(grid,hull,start,goal,preferredClearance,radius);
        // Open-water fast path; stop at UNKNOWN, never at a known obstacle posing as a frontier.
        if(space.turn(start,heading,toward)) {
            SeaPoint last=start; boolean unknown=false,blocked=false;
            int distance=(int)Math.ceil(Math.min(64,start.distance(goal)));
            for(int d=1;d<=distance;d++) {
                if((d&15)==0&&cancelled.getAsBoolean()) return empty(Outcome.CANCELLED,0,"Cancelled direct ray");
                SeaPoint next=Hull.project(start,toward,d);
                if(!space.line(last,next,toward)) {
                    unknown=nearUnknown(grid,hull,next); blocked=!unknown; break;
                }
                // Do not let a direct shortcut discard the requested shore preference.
                if(clearance.penalty(next,toward)>.75) {blocked=true;break;}
                last=next;
                if(targetKnown&&last.distance(goal)<=radius)
                    return new Result(Outcome.COMPLETE,List.of(start,last),0,start.distance(last),"Direct known-water ray");
            }
            if(!blocked && (unknown || distance==64) && last.distance(start)>=12)
                return new Result(Outcome.FRONTIER,List.of(start,last),0,start.distance(last),"Direct observed horizon");
        }
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble((Node n)->n.f).thenComparingDouble(n->-n.g));
        Map<Long,Double> best=new HashMap<>();
        Node first=new Node(start,heading,0,heuristic(start,goal,radius),null);
        open.add(first);best.put(key(start,heading),0.0);
        Node frontier=null, approach=null;
        double frontierScore=Double.POSITIVE_INFINITY, approachDistance=start.distance(goal);
        int expanded=0; boolean limited=false;
        while(!open.isEmpty()) {
            if((expanded&31)==0) {
                if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()) return empty(Outcome.CANCELLED,expanded,"Cancelled search");
                if(System.nanoTime()>=deadline) {limited=true;break;}
            }
            Node node=open.poll();
            if(node.g>best.getOrDefault(key(node.p,node.h),Double.POSITIVE_INFINITY)+1e-8)continue;
            if(++expanded>maxNodes) {limited=true;break;}
            if(targetKnown&&node.p.distance(goal)<=radius) return result(Outcome.COMPLETE,node,expanded,"Hull-clear local route");
            // A provisional voyage waypoint may be visible but disconnected from this
            // scene's reachable water. Keep an executable prefix that makes substantial
            // progress rather than discarding every safe step with the unreachable tail.
            double goalDistance=node.p.distance(goal);
            if(node.p.distance(start)>=12 && goalDistance<=start.distance(goal)-8
                    && visits.applyAsInt(node.p)==0 && goalDistance<approachDistance-.5) {
                approach=node;approachDistance=goalDistance;
            }
            if(node.p.distance(start)>=12 && nearUnknown(grid,hull,node.p)) {
                double score=node.g+node.p.distance(goal)+visits.applyAsInt(node.p)*80.0;
                if(score<frontierScore) {frontier=node;frontierScore=score;}
                // An unknown destination does not justify exploring every heading in the entire scene.
                if(!targetKnown && visits.applyAsInt(node.p)==0 && node.p.distance(start)>=18)
                    return result(Outcome.FRONTIER,node,expanded,"Observed frontier toward voyage corridor");
            }
            for(int d=0;d<16;d++) {
                SeaPoint next=node.p.add(DX[d],DY[d]);
                double distance=Math.hypot(DX[d],DY[d]);
                double g=node.g+distance+Hull.turnDistance(node.h,d)*.25;
                long k=key(next,d); double old=best.getOrDefault(k,Double.POSITIVE_INFINITY);
                if(g>=old-1e-8 || !space.turn(node.p,node.h,d) || !space.line(node.p,next,d)) continue;
                g+=clearance.penalty(next,d)*distance;
                if(g>=old-1e-8)continue;
                best.put(k,g);
                open.add(new Node(next,d,g,g+heuristic(next,goal,radius),node));
            }
        }
        if(frontier!=null)return result(Outcome.FRONTIER,frontier,expanded,limited?"Budget-limited observed frontier":"Observed frontier only");
        if(approach!=null)return result(Outcome.FRONTIER,approach,expanded,"Hull-clear partial approach; remaining corridor requires replanning");
        return empty(limited?Outcome.SEARCH_LIMIT:Outcome.NO_ROUTE,expanded,limited?"Local search budget exhausted":"No connected hull-clear local path");
    }
    private static double heuristic(SeaPoint a,SeaPoint b,int r) {return Math.max(0,a.distance(b)-r);}
    private static boolean nearUnknown(SeaGrid grid,Hull hull,SeaPoint p) {
        int r=(int)Math.ceil(hull.reach)+2;
        for(int d=0;d<8;d++) if(grid.cell(p.x+Integer.signum(DX[d*2])*r,p.y+Integer.signum(DY[d*2])*r)==SeaGrid.UNKNOWN)return true;
        return false;
    }
    private static Result result(Outcome o,Node n,int expanded,String detail) {
        List<SeaPoint> path=new ArrayList<>();for(Node p=n;p!=null;p=p.parent)path.add(p.p);Collections.reverse(path);
        return new Result(o,path,expanded,n.g,detail);
    }
    private static Result empty(Outcome o,int n,String text) {return new Result(o,Collections.emptyList(),n,Double.POSITIVE_INFINITY,text);}
}
