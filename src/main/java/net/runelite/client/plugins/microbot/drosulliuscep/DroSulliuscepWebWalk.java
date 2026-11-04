/*
 * DroSulliuscep - Copyright (c) 2026, Dorian (DRO / droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by Dorian (DRO / droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

/**
 * Script-owned walker requests. Arrival is checked against the current location.
 * Hub build: the AIO Efficient Walker is not available, so walks use Microbot's Rs2Walker on a
 * script-owned worker thread; approaches stay non-blocking for the script loop.
 */
final class DroSulliuscepWebWalk
{
    private static final ExecutorService APPROACH_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DroSulliuscep-walker");
        thread.setDaemon(true);
        return thread;
    });
    private WorldPoint target;
    private WorldPoint lastPosition;
    private long progressAt;
    private long retryAt;
    private int failures;
    private Future<WalkerState> journey;

    WalkerState approach(WorldPoint destination)
    {
        return advance(destination, APPROACH_WORKER);
    }

    WalkerState travel(WorldPoint destination, ExecutorService executor)
    {
        return advance(destination, executor);
    }

    private WalkerState advance(WorldPoint destination, ExecutorService executor)
    {
        WorldPoint position = Microbot.getClientThread()
                .runOnClientThreadOptional(Rs2Player::getWorldLocation_Internal).orElse(null);
        return advance(destination, executor, position, System.currentTimeMillis());
    }

    WalkerState advance(WorldPoint destination, ExecutorService executor, WorldPoint position, long now)
    {
        if (position == null) return WalkerState.MOVING;
        if (arrived(position, destination))
        {
            cancelIfActive();
            return WalkerState.ARRIVED;
        }
        if (!destination.equals(target))
        {
            cancelIfActive();
            target = destination;
            lastPosition = position;
            progressAt = now;
            failures = 0;
            retryAt = 0L;
        }
        if (!position.equals(lastPosition))
        {
            lastPosition = position;
            progressAt = now;
            failures = 0;
        }
        if (failures >= 3) return WalkerState.UNREACHABLE;
        if (now < retryAt) return WalkerState.MOVING;
        if (now - progressAt > 30_000L) return retry(now);

        if (executor != null)
        {
            if (journey == null)
            {
                journey = executor.submit(() -> Rs2Walker.walkWithState(destination, 3));
                return WalkerState.MOVING;
            }
            if (!journey.isDone()) return WalkerState.MOVING;
            try{
                WalkerState result=journey.get();journey=null;
                if(result==WalkerState.MOVING)return result;
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return WalkerState.EXIT;}
            catch(java.util.concurrent.ExecutionException failure){throw new IllegalStateException("Sulliuscep walker request failed",failure.getCause());}
            // A completed task is not proof of arrival; the live location above owns success.
            return retry(now);
        }
        WalkerState result = walkStep(destination);
        if (result == WalkerState.MOVING) return result;
        // Even ARRIVED is verified by our own location check on the next iteration.
        return retry(now);
    }

    WalkerState walkStep(WorldPoint destination)
    {
        return Rs2Walker.walkWithState(destination, 3);
    }

    private WalkerState retry(long now)
    {
        stopOwnedWalk();
        failures++;
        retryAt = now + 2_000L;
        progressAt = retryAt;
        return failures >= 3 ? WalkerState.UNREACHABLE : WalkerState.MOVING;
    }

    static boolean arrived(WorldPoint position, WorldPoint destination)
    {
        return position != null && destination != null && position.getPlane() == destination.getPlane()
                && position.distanceTo(destination) <= 3;
    }

    void cancelIfActive()
    {
        stopOwnedWalk();
        target = null;
        lastPosition = null;
    }

    private void stopOwnedWalk()
    {
        if (journey != null) journey.cancel(true);
        journey = null;
        Rs2Walker.setTarget(null);
    }
}
