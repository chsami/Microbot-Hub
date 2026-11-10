package net.runelite.client.plugins.microbot.droslayer.walker.sailing.core;

import java.util.*;

/** Monotone arc-length following on a continuous, optionally fractional-origin route. */
public final class RouteTrack {
    private final List<SeaPoint> points;
    private final double[] lengths;
    private final double dx, dy;
    private double progress, crossTrack;
    public RouteTrack(List<SeaPoint> source) { this(source, 0, 0); }
    public RouteTrack(List<SeaPoint> source, double dx, double dy) {
        List<SeaPoint> copy = new ArrayList<>();
        for (SeaPoint p : source) if (copy.isEmpty() || !p.equals(copy.get(copy.size() - 1))) copy.add(p);
        points = Collections.unmodifiableList(copy); this.dx = dx; this.dy = dy;
        lengths = new double[points.size()];
        for (int i = 1; i < points.size(); i++) lengths[i] = lengths[i-1] + points.get(i-1).distance(points.get(i));
    }
    public List<SeaPoint> points() { return points; }
    public double length() { return lengths.length == 0 ? 0 : lengths[lengths.length-1]; }
    public double progress() { return progress; }
    public double remaining() { return Math.max(0, length()-progress); }
    public double crossTrack() { return crossTrack; }
    public void update(SeaPoint position, double ahead) { update(new SeaPose(position,0), ahead); }
    public void update(SeaPose position, double ahead) {
        if (points.size()<2) { crossTrack=points.isEmpty()?Double.POSITIVE_INFINITY:position.distance(atPose(0)); return; }
        double nearest=Double.POSITIVE_INFINITY, candidate=progress, limit=Math.min(length(),progress+Math.max(8,ahead));
        for(int i=1;i<points.size();i++) {
            if(lengths[i]<progress-1e-6 || lengths[i-1]>limit)continue;
            SeaPoint a=points.get(i-1),b=points.get(i);double vx=b.x-a.x,vy=b.y-a.y,l=lengths[i]-lengths[i-1];
            double t=((position.x-a.x-dx)*vx+(position.y-a.y-dy)*vy)/(l*l);
            double s=Math.max(Math.max(progress,lengths[i-1]),Math.min(Math.min(limit,lengths[i]),lengths[i-1]+t*l));
            t=(s-lengths[i-1])/l;
            double d=Math.hypot(position.x-a.x-dx-t*vx,position.y-a.y-dy-t*vy);
            if(d<nearest-1e-8) {nearest=d;candidate=s;}
        }
        progress=Math.min(length(),Math.max(progress,candidate));crossTrack=nearest;
    }
    public SeaPose aheadPose(double distance) {return atPose(progress+Math.max(0,distance));}
    public SeaPoint ahead(double distance) {SeaPose p=aheadPose(distance);return p==null?null:p.tile();}
    public SeaPoint at(double distance) {SeaPose p=atPose(distance);return p==null?null:p.tile();}
    public SeaPose atPose(double distance) {
        if(points.isEmpty())return null;
        distance=Math.max(0,Math.min(length(),distance));
        for(int i=1;i<points.size();i++)if(lengths[i]>=distance) {
            SeaPoint a=points.get(i-1),b=points.get(i);
            double t=(distance-lengths[i-1])/(lengths[i]-lengths[i-1]);
            return new SeaPose(a.x+dx+(b.x-a.x)*t,a.y+dy+(b.y-a.y)*t,SeaPose.bearing(a.x,a.y,b.x,b.y));
        }
        SeaPoint p=points.get(points.size()-1);return new SeaPose(p.x+dx,p.y+dy,0);
    }
    public int bendAhead(int heading,double horizon) {
        double maximum=0;
        for(double d=0;d<=Math.min(remaining(),horizon);d+=2)
            maximum=Math.max(maximum,Math.abs(SeaPose.delta(heading,aheadPose(d).heading)));
        return (int)Math.ceil(maximum);
    }
}
