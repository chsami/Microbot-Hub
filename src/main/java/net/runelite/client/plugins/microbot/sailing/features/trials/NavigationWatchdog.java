package net.runelite.client.plugins.microbot.sailing.features.trials;

final class NavigationWatchdog {

    enum Status {
        MOVING,
        STUCK,
        GIVE_UP
    }

    private final int noProgressTicks;
    private final int maxRecoveries;
    private int waypointIndex = -1;
    private int bestDistance = Integer.MAX_VALUE;
    private int lastProgressTick;
    private int recoveries;

    NavigationWatchdog(int noProgressTicks, int maxRecoveries) {
        this.noProgressTicks = noProgressTicks;
        this.maxRecoveries = maxRecoveries;
    }

    Status update(int waypointIndex, int distance, int tick) {
        if (waypointIndex != this.waypointIndex) {
            this.waypointIndex = waypointIndex;
            recoveries = 0;
            markProgress(distance, tick);
            return Status.MOVING;
        }
        if (distance < bestDistance || tick < lastProgressTick) {
            markProgress(distance, tick);
            return Status.MOVING;
        }
        if (tick - lastProgressTick < noProgressTicks) {
            return Status.MOVING;
        }
        recoveries++;
        bestDistance = Integer.MAX_VALUE;
        return recoveries > maxRecoveries ? Status.GIVE_UP : Status.STUCK;
    }

    int getRecoveries() {
        return recoveries;
    }

    void reset() {
        waypointIndex = -1;
        bestDistance = Integer.MAX_VALUE;
        recoveries = 0;
    }

    private void markProgress(int distance, int tick) {
        bestDistance = distance;
        lastProgressTick = tick;
    }
}
