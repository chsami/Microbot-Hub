package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope;

public final class ArenaMouse implements ArenaInputScope.ClickHandler, AutoCloseable
{
    static final long TRAIL_NANOS=240_000_000L;

    static final double TRAVEL_JITTER=.08;
    static final long MIN_TRAVEL_NANOS=18_000_000L,MAX_TRAVEL_NANOS=60_000_000L;
    static final long MIN_SAMPLE_NANOS=3_500_000L,MAX_SAMPLE_NANOS=4_500_000L;
    private final LongSupplier clock;
    private final LongConsumer pause;
    private final Supplier<Random> random;
    private final ArrayDeque<Sample> trail=new ArrayDeque<>();
    private volatile boolean closed;
    private volatile Point cursor;

    public ArenaMouse(){this(System::nanoTime,LockSupport::parkNanos);}
    ArenaMouse(LongSupplier clock,LongConsumer pause){this(clock,pause,ThreadLocalRandom::current);}
    ArenaMouse(LongSupplier clock,LongConsumer pause,Supplier<Random> random){ArenaMousePath.preload();this.clock=clock;this.pause=pause;this.random=random;}

    @Override public void click(Point point,boolean rightClick,NewMenuEntry entry){
        if(closed||point==null){drop(closed?"MOUSE_CLOSED":"NULL_POINT");return;}
        Runnable gesture=ArenaInputScope.capture(()->{
            try { perform(point,rightClick,entry); }
            catch(MouseMotionScope.Cancelled ignored){ }
        });

        if(!ArenaInputScope.scheduleGesture(gesture))drop("OWNER_QUEUE_UNAVAILABLE");
    }

    private void perform(Point target,boolean rightClick,NewMenuEntry entry){
        checkpoint();
        if(closed){drop("MOUSE_CLOSED");return;}

        Dimension size=Microbot.getClientThread().runOnClientThreadOptional(()->
            new Dimension(Microbot.getClient().getCanvasWidth(),Microbot.getClient().getCanvasHeight())).orElse(null);
        if(!valid(target,size)){drop("INVALID_COMBAT_POINT");return;}
        InputLoop.run(emit->{
            checkpoint();
            if(closed){drop("MOUSE_CLOSED");return;}
            move(emit,target,size);

            Point corrected=refresh(target,entry,size);
            if(corrected==null){drop("TARGET_UNAVAILABLE");return;}
            if(!corrected.equals(target))move(emit,corrected,size);
            MouseMotionScope.beforeClick();
            if(closed){drop("MOUSE_CLOSED");return;}

            if(entry!=null)Microbot.targetMenu=entry;
            int button=rightClick?MouseEvent.BUTTON3:MouseEvent.BUTTON1;
            emit.press(corrected.getX(),corrected.getY(),button);
            ArenaInputDiagnostics.pressed();
            emit.release(corrected.getX(),corrected.getY(),button);
            emit.click(corrected.getX(),corrected.getY(),button);
            Microbot.getMouse().setLastClick(corrected);
        });
    }

    private Point refresh(Point target,NewMenuEntry entry,Dimension size){
        if(entry==null||(!Rs2UiHelper.hasActor(entry)&&!Rs2UiHelper.isGameObject(entry)))return target;
        return Microbot.getClientThread().runOnClientThreadOptional(()->{
            Rectangle bounds=Rs2UiHelper.hasActor(entry)?Rs2UiHelper.getActorClickbox(entry.getActor())
                :Rs2UiHelper.getObjectClickbox(entry.getGameObject());
            if(bounds==null||bounds.isEmpty()||bounds.x<=1&&bounds.y<=1)return null;
            Point next=bounds.contains(target.getX(),target.getY())?target:Rs2UiHelper.getClickingPoint(bounds,true);
            return valid(next,size)?next:null;
        }).orElse(null);
    }

    private void move(InputLoop.Emit emit,Point target,Dimension size){
        Point from=PointerState.get();
        if(!valid(from,size))from=clamp(from,size);
        Random random=this.random.get();
        long duration=travelNanos(durationNanos(from,target),random),started=clock.getAsLong();
        ArenaMousePath path=ArenaMousePath.choose(from,target,random);
        try {
            while(true){
                checkpoint();
                if(closed)return;
                long elapsed=Math.max(0,clock.getAsLong()-started);
                double t=duration==0?1:Math.min(1,(double)elapsed/duration);
                Point next=clamp(path.at(t),size);
                if(!PointerState.isAt(next.getX(),next.getY()))emit.move(next.getX(),next.getY());
                track(PointerState.get());
                if(t>=1)return;
                pause.accept(Math.min(sampleNanos(random),Math.max(1,duration-elapsed)));
            }
        } finally {ArenaInputDiagnostics.event("MOUSE_TRAVEL","ARENA_CURVE",clock.getAsLong()-started);}
    }

    private static void checkpoint(){MouseMotionScope.checkpoint();}
    private static void drop(String reason){ArenaInputDiagnostics.event("GESTURE_DROPPED",reason);}
    static boolean valid(Point p,Dimension size){return p!=null&&size!=null&&p.getX()>1&&p.getY()>1&&p.getX()<size.width-1&&p.getY()<size.height-1;}
    private static Point clamp(Point p,Dimension size){return new Point(Math.max(2,Math.min(size.width-2,p.getX())),Math.max(2,Math.min(size.height-2,p.getY())));}
    static long durationNanos(Point from,Point to){
        double distance=Math.hypot(to.getX()-from.getX(),to.getY()-from.getY());
        return distance==0?0:Math.round(Math.min(60,18+distance*0.055)*1_000_000L);
    }

    static long travelNanos(long nominal,Random random){
        if(nominal==0)return 0;
        double factor=1+TRAVEL_JITTER*(random.nextDouble()+random.nextDouble()-1);
        return Math.max(MIN_TRAVEL_NANOS,Math.min(MAX_TRAVEL_NANOS,Math.round(nominal*factor)));
    }
    static long sampleNanos(Random random){return MIN_SAMPLE_NANOS+(long)(random.nextDouble()*(MAX_SAMPLE_NANOS-MIN_SAMPLE_NANOS));}
    private synchronized void track(Point point){
        if(closed)return;
        cursor=point;trail.addLast(new Sample(point,clock.getAsLong()));
        while(trail.size()>96)trail.removeFirst();
    }
    Point cursor(){return closed?null:cursor;}
    @Override public synchronized void close(){closed=true;trail.clear();cursor=null;}
    static final class Sample {
        final Point point;final long at;
        Sample(Point point,long at){this.point=point;this.at=at;}
    }
}
