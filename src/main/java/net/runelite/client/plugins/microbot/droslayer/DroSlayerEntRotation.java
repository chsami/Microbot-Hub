package net.runelite.client.plugins.microbot.droslayer;

import java.util.HashSet;
import java.util.Set;

/** Rotate after a confirmed Ent kill when no live targets remain nearby. */
final class DroSlayerEntRotation {
    private int world = -1;
    private final Set<Integer> killed = new HashSet<>();
    private long nextAttempt;

    static boolean isEntTask(String task) {
        return task != null && ("Ent".equalsIgnoreCase(task.trim()) || "Ents".equalsIgnoreCase(task.trim()));
    }
    synchronized void reset() { world = -1; killed.clear(); nextAttempt = 0; }
    private void observeWorld(int observed) {
        if (observed != world) { reset(); world = observed; }
    }
    synchronized void killed(int observedWorld, int npcIndex) {
        observeWorld(observedWorld);
        killed.add(npcIndex);
    }
    synchronized boolean pending(int observedWorld) {
        if (observedWorld < 0) return false;
        observeWorld(observedWorld);
        return !killed.isEmpty();
    }
    synchronized boolean readyToHop(int observedWorld, int killLimit, boolean liveTargets) {
        return pending(observedWorld) && (!liveTargets || killed.size() >= killLimit);
    }
    synchronized boolean tryHop(long now) {
        if (now < nextAttempt) return false;
        nextAttempt = now + DroSlayerHopPolicy.RETRY_GAP_MS;
        return true;
    }
}
