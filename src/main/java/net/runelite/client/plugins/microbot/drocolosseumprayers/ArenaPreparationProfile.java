package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;


/**
 * Prep worker only. Never wraps connection startup or arena commands.
 * Every prep action runs through the same BaseProfileDro humanizer as other AIO coordinator
 * actions (natural mouse, simulated mistakes, LOW intensity). The background lease adds no
 * motion or click dwell of its own; it only revokes queued prep input at combat handoff.
 */
public final class ArenaPreparationProfile implements AutoCloseable,net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope.Profile {
    private static final AtomicInteger PREPARING=new AtomicInteger();
    private volatile boolean ended;
    private boolean started;
    private AutoCloseable lease;
    private final ArenaHumanizedActions actions=new ArenaHumanizedActions();

    /** True while any arena preparation lease is live; the arena policy then keeps the prep intensity. */
    static boolean preparing(){return PREPARING.get()>0;}

    public synchronized void activate(){
        if(ended||lease!=null)return;
        lease=net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope.background(this);
        PREPARING.incrementAndGet();
    }
    /** Runs one prep action. {@code permission} runs after the pre-action delay and may throw to refuse input. */
    public boolean perform(BooleanSupplier action,Runnable permission){
        // After handoff the step itself still runs, as before; it just is no longer humanized.
        if(ended){permission.run();return action.getAsBoolean();}
        activate();started=true;
        return actions.perform(action,permission);
    }
    public long clickPause(){return 0;}
    public boolean isValid(){return !ended;}
    /** Null keeps the caller's motion: the walker's own profile, or NaturalMouse's antiban factory. */
    public net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory motion(net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory source){
        return source;
    }
    @Override public void close(){
        synchronized(this){
            if(ended)return;ended=true;
            if(lease!=null){
                PREPARING.decrementAndGet();
                try{lease.close();}catch(Exception e){throw new IllegalStateException(e);}
            }
        }
        if(!started)return;
        boolean overwrite=net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.overwriteScriptSettings;
        boolean enabled=net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.antibanEnabled;
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.overwriteScriptSettings=true;
        try{actions.close();}finally{
            net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.overwriteScriptSettings=overwrite;
            net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.antibanEnabled=enabled;
        }
    }
}
