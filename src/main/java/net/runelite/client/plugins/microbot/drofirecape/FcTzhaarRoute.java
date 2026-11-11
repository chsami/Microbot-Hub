/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** TzHaar travel used while the grouping teleport is unavailable: the Microbot walker owns
 * the route on a background thread. An accepted request is never arrival; the caller
 * observes the TzHaar region and cancels only the journey it owns. */
final class FcTzhaarRoute {
    private static final ExecutorService WALKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"dro-firecape-tzhaar-route");t.setDaemon(true);return t;});
    private WorldPoint owned;
    private Future<?> journey;
    private volatile String failure;

    /** Null when the request is in flight or accepted; otherwise why it cannot be sent. */
    synchronized String request(WorldPoint target,boolean bankSupplies) {
        if(target.equals(owned)&&journey!=null&&!journey.isDone())return null;
        cancel();
        owned=target;failure=null;
        journey=WALKER.submit(()->{
            boolean arrived;
            try{arrived=bankSupplies?Rs2Walker.walkWithBankedTransports(target):Rs2Walker.walkTo(target);}
            catch(RuntimeException e){failure="Walker failed: "+e.getClass().getSimpleName();return;}
            if(!arrived&&!Thread.currentThread().isInterrupted())failure="Walker could not complete the TzHaar route";
        });
        return null;
    }
    synchronized String lastFailure(){return owned==null?null:failure;}
    synchronized void cancel() {
        if(owned==null)return;
        if(journey!=null&&!journey.isDone()){journey.cancel(true);Rs2Walker.setTarget(null);}
        journey=null;owned=null;failure=null;
    }
}
