package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import java.util.*;
import java.util.function.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.Commands;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Local-only, bounded timing evidence. Never changes dispatch or waits for a prayer receipt. */
public final class PrayerDiagnostics {
    private static final ThreadLocal<Ticket> CURRENT=new ThreadLocal<>();
    private final Consumer<Map<String,Object>> sink;
    private final LongSupplier clock;
    private final Map<Long,Ticket> pending=new LinkedHashMap<>();
    private final Map<Rs2PrayerEnum,Boolean> states=new EnumMap<>(Rs2PrayerEnum.class);
    public PrayerDiagnostics(Consumer<Map<String,Object>> sink){this(sink,System::nanoTime);}
    PrayerDiagnostics(Consumer<Map<String,Object>> sink,LongSupplier clock){this.sink=sink;this.clock=clock;}
    public static Ticket current(){return CURRENT.get();}
    private long ms(long start){return Math.max(0,(clock.getAsLong()-start)/1_000_000L);}
    public final class Ticket {
        final long id,received,receivedWall,serverTick,localTick,deadline;
        final String op;
        final Rs2PrayerEnum prayer;
        final boolean desired;
        long started=-1,enqueued=-1;
        boolean confirmed,menuSeen;
        Ticket(long id,String op,String name,boolean desired,long serverTick,long localTick,long deadline){
            this.id=id;this.op=op;this.prayer=Commands.prayerEnum(name);this.desired=desired;
            this.serverTick=serverTick;this.localTick=localTick;this.deadline=deadline;received=clock.getAsLong();receivedWall=System.currentTimeMillis();
        }
        /** Called inside the command's existing client-thread state read. */
        public void before(boolean active){synchronized(PrayerDiagnostics.this){
            emit(this,"BEFORE",Map.of("active",active));
            if(active==desired){confirmed=true;pending.remove(id);emit(this,"ALREADY_SET",Map.of("active",active));}
        }}
        /** A menu request was handed to Dro; this is not proof the game applied it. */
        public void inputQueued(){synchronized(PrayerDiagnostics.this){enqueued=clock.getAsLong();emit(this,"INPUT_ENQUEUED",Map.of());}}
    }
    Ticket received(long id,String op,String name,boolean desired,long serverTick,long localTick,long deadline){
        synchronized(this){
            if(pending.size()>=128){var oldest=pending.values().iterator().next();pending.remove(oldest.id);emit(oldest,"UNCONFIRMED",Map.of("reason","DIAGNOSTIC_CAPACITY"));}
            Ticket ticket=new Ticket(id,op,name,desired,serverTick,localTick,deadline);pending.put(id,ticket);
            emit(ticket,"RECEIVED",Map.of());return ticket;
        }
    }
    interface Scope extends AutoCloseable {@Override void close();}
    Scope dispatch(Ticket ticket){
        Ticket previous=CURRENT.get();
        if(ticket!=null){synchronized(this){ticket.started=clock.getAsLong();emit(ticket,"DISPATCH",Map.of("queueMs",ms(ticket.received)));}CURRENT.set(ticket);}
        return ()->{if(previous==null)CURRENT.remove();else CURRENT.set(previous);};
    }
    void gesture(Ticket ticket,String stage){if(ticket!=null)synchronized(this){emit(ticket,stage,Map.of());}}
    void result(Ticket ticket,String outcome,boolean delivered){
        if(ticket==null)return;
        synchronized(this){
            emit(ticket,"RESULT",Map.of("outcome",outcome,"delivered",delivered,"stateConfirmed",ticket.confirmed));
            if(!delivered)pending.remove(ticket.id);
        }
    }
    /** Client thread only; the caller supplies the existing game's varbit accessor. */
    synchronized void observe(IntPredicate active){
        for(Rs2PrayerEnum prayer:Rs2PrayerEnum.values()){
            boolean on=active.test(prayer.getVarbit());Boolean previous=states.put(prayer,on);
            if(previous!=null&&previous!=on){
                var event=new LinkedHashMap<String,Object>();event.put("stage","STATE_CHANGED");event.put("prayer",prayer.name());event.put("active",on);event.put("observedAtMs",System.currentTimeMillis());publish(event);
            }
        }
        var iterator=pending.values().iterator();
        while(iterator.hasNext()){
            Ticket ticket=iterator.next();
            if(ticket.enqueued>=0&&ticket.prayer!=null&&states.get(ticket.prayer)==ticket.desired){
                ticket.confirmed=true;iterator.remove();emit(ticket,"CONFIRMED",Map.of("active",ticket.desired,"inputToObservedMs",ms(ticket.enqueued)));
            }else if(ticket.started>=0&&ms(ticket.started)>=2_000){iterator.remove();emit(ticket,"UNCONFIRMED",Map.of("reason","NO_STATE_RECEIPT_WITHIN_2000MS"));}
        }
    }
    synchronized void menu(int widget,boolean consumed){
        for(Ticket ticket:pending.values())if(ticket.enqueued>=0&&!ticket.menuSeen&&ticket.prayer!=null&&ticket.prayer.getIndex()==widget){
            ticket.menuSeen=true;emit(ticket,"MENU_OBSERVED",Map.of("consumedAtCapture",consumed));return;
        }
    }
    synchronized void clear(){for(Ticket ticket:pending.values())emit(ticket,"UNCONFIRMED",Map.of("reason","SESSION_CLEARED"));pending.clear();states.clear();}
    private void emit(Ticket ticket,String stage,Map<String,Object> extra){
        var values=new LinkedHashMap<String,Object>();values.put("stage",stage);values.put("id",ticket.id);values.put("op",ticket.op);
        values.put("prayer",ticket.prayer==null?"UNKNOWN":ticket.prayer.name());values.put("desired",ticket.desired);
        values.put("serverTick",ticket.serverTick);values.put("receivedTick",ticket.localTick);values.put("deadlineMs",ticket.deadline);
        values.put("receivedAtMs",ticket.receivedWall);values.put("elapsedMs",ms(ticket.received));
        if(ticket.started>=0)values.put("dispatchRunMs",ms(ticket.started));
        values.putAll(extra);publish(values);
    }
    private void publish(Map<String,Object> values){try{sink.accept(Collections.unmodifiableMap(values));}catch(RuntimeException ignored){/* Diagnostics cannot fail a combat action. */}}
}
