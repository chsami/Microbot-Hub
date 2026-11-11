package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** Admit the recorded Karamja volcano/TzHaar links already present in the shared catalogue. */
final class TzhaarAccess {
    private TzhaarAccess() { }
    static boolean isVent(Transport t){return isLink(t)&&t.getObjectId()==30266;}
    /** Accept the far side, including a few tiles beyond the catalog landing; never the approach side. */
    static boolean crossedVent(Transport t,WorldPoint player){
        if(!isVent(t)||player==null||player.getPlane()!=t.getDestination().getPlane()
            ||player.distanceTo2D(t.getDestination())>3)return false;
        WorldPoint from=t.getOrigin(),to=t.getDestination();
        if(from.getX()==to.getX())return to.getY()<from.getY()?player.getY()<=to.getY():player.getY()>=to.getY();
        if(from.getY()==to.getY())return to.getX()<from.getX()?player.getX()<=to.getX():player.getX()>=to.getX();
        return player.equals(to);
    }
    static boolean isLink(Transport t) {
        if(t==null||t.getType()!=TransportType.TRANSPORT||t.getOrigin()==null||t.getDestination()==null)return false;
        WorldPoint a=t.getOrigin(),b=t.getDestination();int id=t.getObjectId();String op=t.getAction();
        if(id==11441&&"Climb-down".equals(op))return surface(a)&&volcano(b);
        if(id==18969&&"Climb".equals(op))return volcano(a)&&surface(b);
        if(id==11835&&"Enter".equals(op))return cave(a)&&entrance(b);
        if(id==11836&&"Enter".equals(op))return entrance(a)&&cave(b);
        if(id==30266&&"Pass".equals(op))return
            (a.equals(new WorldPoint(2474,5138,0))&&b.equals(new WorldPoint(2474,5136,0)))||
            (b.equals(new WorldPoint(2474,5138,0))&&a.equals(new WorldPoint(2474,5136,0)));
        return false;
    }
    private static boolean surface(WorldPoint p){return area(p,2853,2859,3166,3171);}
    private static boolean volcano(WorldPoint p){return area(p,2853,2859,9566,9571);}
    private static boolean cave(WorldPoint p){return area(p,2861,2865,9570,9574);}
    private static boolean entrance(WorldPoint p){return area(p,2479,2481,5175,5175);}
    private static boolean area(WorldPoint p,int x1,int x2,int y1,int y2){return p.getPlane()==0&&p.getX()>=x1&&p.getX()<=x2&&p.getY()>=y1&&p.getY()<=y2;}
}
