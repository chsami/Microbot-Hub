package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Whole-voyage coarse A*, separate from the tick-to-tick helmsman. Never returns executable collision. */
public final class GlobalSeaPlanner {
    public enum Outcome { PROVISIONAL, OUTSIDE_MAP, NO_CORRIDOR, LIMIT, CANCELLED }
    public static final class Result {
        public final Outcome outcome;public final List<SeaPoint> path;public final int expanded;public final long millis;
        public final String detail;
        Result(Outcome outcome,List<SeaPoint> path,int expanded,long millis,String detail){
            this.outcome=outcome;this.path=Collections.unmodifiableList(new ArrayList<>(path));
            this.expanded=expanded;this.millis=millis;this.detail=detail;
        }
        public boolean usable(){return outcome==Outcome.PROVISIONAL&&path.size()>1;}
    }
    private final SeaReferenceMap map;
    public GlobalSeaPlanner(SeaReferenceMap map){this.map=Objects.requireNonNull(map);}
    private static final int[] DX={1,1,0,-1,-1,-1,0,1},DY={0,1,1,1,0,-1,-1,-1};
    private static final class Node{final int i;final double g,f;Node(int i,double g,double f){this.i=i;this.g=g;this.f=f;}}
    private final class Space {
        final SeaGrid observed;final SeaPoint start,goal;final byte[] cached=new byte[map.width*map.height];
        Space(SeaGrid observed,SeaPoint start,SeaPoint goal){this.observed=observed;this.start=start;this.goal=goal;}
        boolean open(int i){
            if(i<0||i>=cached.length)return false;if(cached[i]!=0)return cached[i]==1;
            SeaPoint p=map.point(i);boolean block=false,allOpen=true;
            int x0=map.west+(i%map.width)*map.cellSize,y0=map.south+(i/map.width)*map.cellSize;
            for(int x=x0;x<x0+map.cellSize;x++)for(int y=y0;y<y0+map.cellSize;y++){
                byte cell=observed.cell(x,y);block|=cell==SeaGrid.BLOCKED;allOpen&=cell==SeaGrid.OPEN;
            }
            // Boat-scene observations can correct the older reference image.
            // This still returns a provisional course, never execution permission.
            boolean allowed=!block&&(map.cellAt(i)==SeaGrid.OPEN||allOpen);
            cached[i]=(byte)(allowed?1:2);return allowed;
        }
        int attach(SeaPoint p,int radius){
            int centre=map.index(p.x,p.y);if(centre<0)return -1;
            if(open(centre))return centre;int best=-1;double score=Double.POSITIVE_INFINITY;
            int cx=centre%map.width,cy=centre/map.width;
            for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++){
                int x=cx+dx,y=cy+dy;if(x<0||y<0||x>=map.width||y>=map.height)continue;
                int i=y*map.width+x;if(!open(i))continue;double d=p.distance(map.point(i));
                if(d<score){score=d;best=i;}
            }return best;
        }
        boolean line(int a,int b){
            int x=a%map.width,y=a/map.width,ex=b%map.width,ey=b/map.width;
            int nx=Math.abs(ex-x),ny=Math.abs(ey-y),sx=Integer.signum(ex-x),sy=Integer.signum(ey-y),ix=0,iy=0;
            int minimum=Math.min(3,Math.min(map.clearanceAt(a),map.clearanceAt(b)));
            if(!open(a))return false;
            while(ix<nx||iy<ny){long l=(1L+2L*ix)*ny,r=(1L+2L*iy)*nx;
                if(l==r){if(!open(y*map.width+x+sx)||!open((y+sy)*map.width+x))return false;x+=sx;y+=sy;ix++;iy++;}
                else if(l<r){x+=sx;ix++;}else{y+=sy;iy++;}if(!open(y*map.width+x)||map.clearanceAt(y*map.width+x)<minimum)return false;
            }return true;
        }
    }
    public Result plan(SeaGrid observed,SeaPoint start,SeaPoint goal,int arrivalRadius,long maxMillis,BooleanSupplier cancelled){
        long began=System.nanoTime(),deadline=began+Math.max(1,Math.min(5000,maxMillis))*1_000_000;
        if(cancelled.getAsBoolean())return result(Outcome.CANCELLED,List.of(),0,began,"Cancelled");
        if(!map.contains(start)||!map.contains(goal))return result(Outcome.OUTSIDE_MAP,List.of(),0,began,"Outside supplied map; local observed-water planning only");
        Space space=new Space(observed,start,goal);int first=space.attach(start,8),last=space.attach(goal,Math.max(3,arrivalRadius/map.cellSize+2));
        if(first<0||last<0)return result(Outcome.NO_CORRIDOR,List.of(),0,began,"No coarse water attachment; use local observations");
        double[] cost=new double[map.width*map.height];Arrays.fill(cost,Double.POSITIVE_INFINITY);
        int[] parent=new int[cost.length];Arrays.fill(parent,-1);
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble((Node n)->n.f).thenComparingDouble(n->-n.g));
        cost[first]=0;open.add(new Node(first,0,map.point(first).distance(map.point(last))));int expanded=0;
        while(!open.isEmpty()){
            if((expanded&255)==0){
                if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())return result(Outcome.CANCELLED,List.of(),expanded,began,"Cancelled");
                if(System.nanoTime()>deadline)return result(Outcome.LIMIT,List.of(),expanded,began,"Coarse search budget exhausted; local chart remains usable");
            }
            Node n=open.poll();if(n.g>cost[n.i]+1e-8)continue;expanded++;
            if(n.i==last){
                List<Integer> raw=new ArrayList<>();for(int i=last;i>=0;i=parent[i])raw.add(i);Collections.reverse(raw);
                List<SeaPoint> path=new ArrayList<>();path.add(start);
                // Retain enough coast buffer: simplify only over reference water and at most
                // 64 tiles at once. This is a hint line, never a guaranteed navigable passage.
                int i=0;while(i<raw.size()-1){int best=i+1;
                    for(int j=i+2;j<raw.size()&&map.point(raw.get(i)).distance(map.point(raw.get(j)))<=64;j++){
                        if(!space.line(raw.get(i),raw.get(j)))break;best=j;
                    }path.add(map.point(raw.get(best)));i=best;
                }
                if(!path.get(path.size()-1).equals(goal))path.add(goal);
                return result(Outcome.PROVISIONAL,path,expanded,began,"Whole-voyage image/observed-chart corridor; live validation required");
            }
            int x=n.i%map.width,y=n.i/map.width;
            for(int d=0;d<8;d++){
                int xx=x+DX[d],yy=y+DY[d];if(xx<0||yy<0||xx>=map.width||yy>=map.height)continue;
                int next=yy*map.width+xx;if(!space.open(next))continue;
                if((d&1)!=0&&(!space.open(y*map.width+xx)||!space.open(yy*map.width+x)))continue;
                double step=map.cellSize*((d&1)==0?1:Math.sqrt(2));
                double shore=Math.max(0,3-map.clearanceAt(next));
                double g=n.g+step*(1+shore*shore*.35);
                if(g<cost[next]-1e-8){cost[next]=g;parent[next]=n.i;open.add(new Node(next,g,g+map.point(next).distance(map.point(last))));}
            }
        }
        return result(Outcome.NO_CORRIDOR,List.of(),expanded,began,"No route in coarse reference; detailed live chart may still resolve it");
    }
    private static Result result(Outcome o,List<SeaPoint> p,int n,long start,String detail){return new Result(o,p,n,(System.nanoTime()-start)/1_000_000,detail);}
}
