package net.runelite.client.plugins.microbot.droslayer.support;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Point;

/** Scoped combat ownership: synthetic motion stays inside; real human events are untouched. */
public final class CanvasPointerLease implements AutoCloseable {
    private static final AtomicInteger OWNERS=new AtomicInteger();
    private boolean closed;
    private CanvasPointerLease(){OWNERS.incrementAndGet();}
    public static CanvasPointerLease acquire(){return new CanvasPointerLease();}
    public static boolean active(){return OWNERS.get()>0;}
    public static Point constrain(int x,int y,int width,int height){
        if(!active()||width<1||height<1)return new Point(x,y);
        return new Point(Math.max(0,Math.min(width-1,x)),Math.max(0,Math.min(height-1,y)));
    }
    @Override public synchronized void close(){if(!closed){closed=true;OWNERS.decrementAndGet();}}
}
