package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;

public final class ArenaInputDispatcher implements AutoCloseable {
    private final Lane pointer=new Lane("dro-arena-pointer",true),keyboard=new Lane("dro-arena-keyboard",false),
        commands=new Lane("dro-arena-commands",false);

    static final long MIN_GAP_NANOS=18_000_000L,MAX_GAP_NANOS=28_000_000L;
    private final ArenaMouse mouse=new ArenaMouse();

    public ArenaMouse mouse(){return mouse;}
    private final BooleanSupplier allowed;
    private final Predicate<String> prepare;
    public ArenaInputDispatcher(BooleanSupplier allowed){this(allowed,ArenaInputDispatcher::prepareTab);}
    ArenaInputDispatcher(BooleanSupplier allowed,Predicate<String> prepare){this.allowed=allowed;this.prepare=prepare;}
    public boolean prepare(String op){return prepare.test(op);}
    public void execute(String op,int priority,Runnable task){execute(op,priority,()->true,task);}
    public void execute(String op,int priority,BooleanSupplier fresh,Runnable task){
        boolean keys=op.equals("dialogue.next")||op.equals("dialogue.choose")||op.equals("camera.set");

        (keys?keyboard:tab(op)!=null?pointer:commands).submit(priority,()->{
            if(!fresh.getAsBoolean()||!allowed.getAsBoolean()){task.run();return;}
            try(var speed=net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.speed(1.30)){
                task.run();
            }
        });
    }

    public void gesture(String op,int priority,BooleanSupplier fresh,Runnable task){
        pointer.submitGesture(priority,task);
    }
    static InterfaceTab tab(String op){
        if(op.startsWith("prayer."))return InterfaceTab.PRAYER;
        if(op.startsWith("inventory.")||op.equals("equipment.set")||op.equals("equipment.equip"))return InterfaceTab.INVENTORY;
        if(op.equals("equipment.slot"))return InterfaceTab.EQUIPMENT;
        if(op.equals("magic.armIce"))return InterfaceTab.MAGIC;
        if(op.equals("combat.style")||op.equals("combat.retaliate"))return InterfaceTab.COMBAT;
        if(op.equals("player.logout"))return InterfaceTab.LOGOUT;
        return null;
    }
    private static boolean prepareTab(String op){
        InterfaceTab tab=tab(op);if(tab==null)return true;
        return Microbot.getClientThread().runOnClientThreadOptional(()->openTab(Microbot.getClient(),tab)).orElse(false);
    }

    public static boolean openTab(net.runelite.api.Client client,InterfaceTab tab){
        net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.checkpoint();
        int wanted=tab.getVarcIntIndex();
        if(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)!=wanted)client.runScript(915,wanted);
        return client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)==wanted;
    }
    @Override public void close(){mouse.close();pointer.close();keyboard.close();commands.close();}
    private static final class Lane implements AutoCloseable {
        private final Semaphore slots=new Semaphore(64),gestureSlots=new Semaphore(64);
        private final AtomicLong sequence=new AtomicLong();
        private final ThreadPoolExecutor executor;
        private final String name;
        private final boolean staggered;
        private long lastFinished;
        Lane(String name,boolean staggered){this.name=name;this.staggered=staggered;executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new PriorityBlockingQueue<>(),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;});}
        Future<?> submit(int priority,Runnable work){return submit(priority,work,slots);}
        Future<?> submitGesture(int priority,Runnable work){return submit(priority,work,gestureSlots);}
        private Future<?> submit(int priority,Runnable work,Semaphore permits){
            if(!permits.tryAcquire())throw new RejectedExecutionException("Arena input backlog");
            long queuedAt=System.nanoTime();
            Runnable observed=ArenaInputDiagnostics.capture(()->{
                ArenaInputDiagnostics.event("LANE_STARTED",name,System.nanoTime()-queuedAt);
                if(staggered)stagger();
                long started=System.nanoTime();
                try { work.run(); }
                finally {
                    lastFinished=System.nanoTime();
                    ArenaInputDiagnostics.event("LANE_FINISHED",name,lastFinished-started);
                }
            });
            Runnable cancelled=ArenaInputDiagnostics.capture(()->ArenaInputDiagnostics.event("QUEUE_DROPPED",name+":CANCELLED",System.nanoTime()-queuedAt));
            Job job=new Job(priority,sequence.incrementAndGet(),observed,permits,cancelled);
            try{executor.execute(job);}catch(RejectedExecutionException closed){job.cancel(false);throw closed;}return job;
        }

        private void stagger(){
            long gap=ThreadLocalRandom.current().nextLong(MIN_GAP_NANOS,MAX_GAP_NANOS+1);
            long wait=lastFinished+gap-System.nanoTime();
            if(lastFinished!=0&&wait>0)java.util.concurrent.locks.LockSupport.parkNanos(wait);
        }
        private final class Job extends FutureTask<Void> implements Comparable<Job>{
            final int priority;final long order;final Semaphore permits;final Runnable cancelled;
            Job(int priority,long order,Runnable task,Semaphore permits,Runnable cancelled){super(task,null);this.priority=priority;this.order=order;this.permits=permits;this.cancelled=cancelled;}
            public int compareTo(Job other){int result=Integer.compare(other.priority,priority);return result==0?Long.compare(order,other.order):result;}
            @Override protected void done(){permits.release();if(isCancelled())cancelled.run();}
        }
        public void close(){for(Runnable task:executor.shutdownNow())((Future<?>)task).cancel(false);}
    }
}
