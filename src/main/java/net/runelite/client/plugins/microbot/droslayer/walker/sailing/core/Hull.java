package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Oriented rectangular collision bounds about the boat's actual world-entity
 * origin. All hard tests include the WHOLE hull, continuous translation, and
 * rotational sweep. A source map image is never consulted by this class.
 *
 * Native bound interpretation was cross-checked against RuneLite WorldEntityConfig
 * and Dazuzi/Chart Plotter (BSD-2-Clause); see THIRD_PARTY_NOTICES.md.
 */
public final class Hull {
    public final int width, length; // Nominal display/extent compatibility.
    public final double padding, beam, foreAft, along, across;
    public final double latticeDx, latticeDy, reach;
    public final boolean nativeGeometry;
    private final int[][][] offsets = new int[16][][];
    private final int[][][] turnOffsets = new int[16][][];
    private static final double EPS = 1e-9;
    interface CellDistance { int at(int x, int y); }

    public Hull(int width, int length, double padding) {
        this(width, length, 0, 0, padding, false, 0, 0);
    }
    private Hull(double beam, double foreAft, double along, double across,
                 double padding, boolean nativeGeometry, double dx, double dy) {
        if (!Double.isFinite(beam + foreAft + along + across + padding + dx + dy)
                || beam <= 0 || foreAft <= 0 || beam > 32 || foreAft > 48
                || Math.abs(along) > 32 || Math.abs(across) > 32 || padding < 0 || padding > 3)
            throw new IllegalArgumentException("Invalid hull bounds");
        this.beam=beam; this.foreAft=foreAft; this.along=along; this.across=across;
        this.width=(int)Math.ceil(beam); this.length=(int)Math.ceil(foreAft);
        this.padding=padding; this.nativeGeometry=nativeGeometry;
        this.latticeDx=dx; this.latticeDy=dy;
        reach=Math.hypot(Math.abs(along)+foreAft/2+padding, Math.abs(across)+beam/2+padding);
        if (reach>64) throw new IllegalArgumentException("Unsupported hull reach");
        int r=(int)Math.ceil(reach+2);
        for(int h=0;h<16;h++) {
            List<int[]> cells=new ArrayList<>();
            for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)
                if(overlaps(x,y,dx,dy,h,0,0,0)) cells.add(new int[]{x,y});
            offsets[h]=cells.toArray(new int[0][]);
            // One quarter-heading subdivision covers continuous turning, without doing
            // trigonometry thousands of times inside A*. Inflate by a vertex-motion bound.
            double extra=2*reach*Math.sin(Math.PI/128);
            List<int[]> swept=new ArrayList<>();
            for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++) {
                boolean hit=false;
                for(int sample=0;sample<4&&!hit;sample++)
                    hit=overlaps(x,y,dx,dy,h+(sample+.5)/4.0,0,0,extra);
                if(hit)swept.add(new int[]{x,y});
            }
            turnOffsets[h]=swept.toArray(new int[0][]);
        }
    }
    /** RuneLite local bounds are in 1/128-tile units, at native orientation zero (south). */
    public static Hull nativeBounds(int boundsX, int boundsY, int boundsWidth, int boundsHeight, double margin) {
        return new Hull(boundsWidth/128.0,boundsHeight/128.0,-boundsY/128.0,boundsX/128.0,
                margin+1.0/128, true,0,0);
    }
    /** A* retains the observed fractional origin on its lattice instead of snapping it into a wall. */
    public Hull atLattice(SeaPose origin) {
        SeaPoint p=origin.tile(); double dx=origin.x-p.x,dy=origin.y-p.y;
        if(Math.abs(dx-latticeDx)<EPS&&Math.abs(dy-latticeDy)<EPS)return this;
        return new Hull(beam,foreAft,along,across,padding,nativeGeometry,dx,dy);
    }
    public SeaPose pose(SeaPoint p,int h) {return new SeaPose(p.x+latticeDx,p.y+latticeDy,h);}
    public boolean clear(SeaGrid grid,SeaPoint p,int h,boolean liveOnly) {
        for(int[] o:offsets[Math.floorMod(h,16)])
            if(!allowed(grid,p.x+o[0],p.y+o[1],liveOnly))return false;
        return true;
    }
    public boolean poseClear(SeaGrid grid,double x,double y,double heading,boolean liveOnly) {
        return swept(grid,x,y,x,y,heading,0,liveOnly);
    }
    public boolean clear(SeaGrid grid,SeaPose pose,boolean liveOnly) {
        return poseClear(grid,pose.x,pose.y,pose.heading,liveOnly);
    }
    private static boolean allowed(SeaGrid grid,int x,int y,boolean liveOnly) {
        return grid.open(x,y)&&(!liveOnly||grid.live(x,y));
    }
    /** SAT against the convex sweep of an oriented rectangle along one straight segment. */
    private boolean overlaps(int tx,int ty,double ax,double ay,double heading,double dx,double dy,double extra) {
        double a=heading*Math.PI/8, ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double hl=foreAft/2+padding+extra,hw=beam/2+padding+extra;
        double cx=ax+along*ux+across*vx+dx/2,cy=ay+along*uy+across*vy+dy/2;
        double rx=tx-cx,ry=ty-cy;
        if(Math.abs(rx)>=hl*Math.abs(ux)+hw*Math.abs(vx)+Math.abs(dx)/2+.5-EPS
                ||Math.abs(ry)>=hl*Math.abs(uy)+hw*Math.abs(vy)+Math.abs(dy)/2+.5-EPS)return false;
        double tile=.5*(Math.abs(ux)+Math.abs(uy));
        if(Math.abs(rx*ux+ry*uy)>=hl+Math.abs(dx*ux+dy*uy)/2+tile-EPS
                ||Math.abs(rx*vx+ry*vy)>=hw+Math.abs(dx*vx+dy*vy)/2+tile-EPS)return false;
        if(dx!=0||dy!=0) {
            double nx=-dy,ny=dx;
            double extent=hl*Math.abs(nx*ux+ny*uy)+hw*Math.abs(nx*vx+ny*vy)+.5*(Math.abs(nx)+Math.abs(ny));
            if(Math.abs(rx*nx+ry*ny)>=extent-EPS)return false;
        }
        return true;
    }
    private boolean swept(SeaGrid grid,double ax,double ay,double bx,double by,double heading,double extra,boolean liveOnly) {
        double a=heading*Math.PI/8,ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double hl=foreAft/2+padding+extra,hw=beam/2+padding+extra;
        double cx=(ax+bx)/2+along*ux+across*vx,cy=(ay+by)/2+along*uy+across*vy;
        double ex=hl*Math.abs(ux)+hw*Math.abs(vx)+Math.abs(bx-ax)/2+.5;
        double ey=hl*Math.abs(uy)+hw*Math.abs(vy)+Math.abs(by-ay)/2+.5;
        for(int x=(int)Math.ceil(cx-ex+EPS);x<cx+ex-EPS;x++)
            for(int y=(int)Math.ceil(cy-ey+EPS);y<cy+ey-EPS;y++)
                if(overlaps(x,y,ax,ay,heading,bx-ax,by-ay,extra)&&!allowed(grid,x,y,liveOnly))return false;
        return true;
    }
    /** Extra clearance is a SPEED preference, never a replacement for the hard footprint. */
    public boolean bufferedTranslationClear(SeaGrid grid,SeaPose a,SeaPose b,double heading,double buffer,boolean liveOnly) {
        return swept(grid,a.x,a.y,b.x,b.y,heading,Math.max(0,buffer),liveOnly);
    }
    public boolean translationClear(SeaGrid grid,SeaPose a,SeaPose b,double heading,boolean liveOnly) {
        return swept(grid,a.x,a.y,b.x,b.y,heading,0,liveOnly);
    }
    public boolean translationClear(SeaGrid grid,SeaPoint a,SeaPoint b,int h,boolean liveOnly) {
        return translationClear(grid,pose(a,h),pose(b,h),h,liveOnly);
    }
    /** Conservative continuous rotation + translation: angular subdivisions include a proven vertex-displacement padding. */
    public boolean motionClear(SeaGrid grid,SeaPose a,SeaPose b,boolean liveOnly) {
        return motionClear(grid,a,b,liveOnly,0);
    }
    public boolean motionClear(SeaGrid grid,SeaPose a,SeaPose b,boolean liveOnly,double uncertainty) {
        double delta=SeaPose.delta(a.heading,b.heading);
        int steps=Math.max(1,(int)Math.ceil(Math.abs(delta)*8));
        double step=delta/steps,extra=2*reach*Math.sin(Math.abs(step)*Math.PI/32)+Math.max(0,uncertainty);
        for(int i=0;i<steps;i++) {
            double t0=(double)i/steps,t1=(double)(i+1)/steps;
            if(!swept(grid,a.x+(b.x-a.x)*t0,a.y+(b.y-a.y)*t0,
                    a.x+(b.x-a.x)*t1,a.y+(b.y-a.y)*t1,
                    a.heading+step*(i+.5),extra,liveOnly))return false;
        }
        return true;
    }
    public boolean turnClear(SeaGrid grid,SeaPose p,double to,boolean liveOnly) {
        if(!motionClear(grid,p,new SeaPose(p.x,p.y,to),liveOnly))return false;
        // Both 180-degree resolutions must be valid; do not assume which way the client turns.
        if(Math.abs(SeaPose.delta(p.heading,to))>=8-EPS) {
            SeaPose mid=new SeaPose(p.x,p.y,p.heading+4);
            return motionClear(grid,p,mid,liveOnly)&&motionClear(grid,mid,new SeaPose(p.x,p.y,to),liveOnly);
        }
        return true;
    }
    public boolean turnClear(SeaGrid grid,SeaPoint p,int from,int to,boolean liveOnly) {
        int delta=(int)SeaPose.delta(from,to);
        if(delta==0)return clear(grid,p,from,liveOnly);
        for(int step=0;step<Math.abs(delta);step++) {
            int edge=Math.floorMod(from+(delta>0?step:-step-1),16);
            for(int[] o:turnOffsets[edge])if(!allowed(grid,p.x+o[0],p.y+o[1],liveOnly))return false;
        }
        if(Math.abs(delta)==8)for(int step=0;step<8;step++) {
            int edge=Math.floorMod(from+step,16);
            for(int[] o:turnOffsets[edge])if(!allowed(grid,p.x+o[0],p.y+o[1],liveOnly))return false;
        }
        return true;
    }
    public boolean stepClear(SeaGrid grid,SeaPoint a,SeaPoint b,int from,int to,boolean liveOnly) {
        return turnClear(grid,a,from,to,liveOnly)&&translationClear(grid,a,b,to,liveOnly);
    }
    public boolean lineClear(SeaGrid grid,SeaPoint a,SeaPoint b,int oldHeading,boolean liveOnly) {
        int h=heading(a,b);return stepClear(grid,a,b,oldHeading,h,liveOnly);
    }
    public boolean movingTurnClear(SeaGrid grid,SeaPoint p,int from,int to,double speed,int latency) {
        return MotionEnvelope.course(grid,this,pose(p,from),to,speed,speed,1,latency,3);
    }
    int clearance(SeaPoint p,int h,int cap,CellDistance distance) {
        int best=cap;for(int[] o:offsets[Math.floorMod(h,16)])best=Math.min(best,distance.at(p.x+o[0],p.y+o[1]));return best;
    }
    public String obstruction(SeaGrid grid,SeaPoint p,int h,boolean liveOnly) {
        return obstruction(grid,pose(p,h),liveOnly);
    }
    public String obstruction(SeaGrid grid,SeaPose p,boolean liveOnly) {
        int r=(int)Math.ceil(reach+1);
        for(int x=(int)Math.floor(p.x)-r;x<=(int)Math.ceil(p.x)+r;x++)
            for(int y=(int)Math.floor(p.y)-r;y<=(int)Math.ceil(p.y)+r;y++)
                if(overlaps(x,y,p.x,p.y,p.heading,0,0,0)&&!allowed(grid,x,y,liveOnly))
                    return "tile="+x+","+y+" cell="+grid.cell(x,y)+" live="+grid.live(x,y)+" pose="+p;
        return "none";
    }
    public static int heading(SeaPoint a,SeaPoint b) {return Math.floorMod((int)Math.round(SeaPose.bearing(a.x,a.y,b.x,b.y)),16);}
    public static int turnDistance(int a,int b) {return (int)Math.abs(SeaPose.delta(a,b));}
    public static SeaPoint project(SeaPoint p,int h,double distance) {
        double a=h*Math.PI/8;return p.add((int)Math.round(Math.cos(a)*distance),(int)Math.round(Math.sin(a)*distance));
    }
    @Override public String toString() {
        return String.format(java.util.Locale.ROOT,"%.3fx%.3f+%.3f offset=%.3f,%.3f %s",beam,foreAft,padding,along,across,nativeGeometry?"native":"nominal");
    }
}
