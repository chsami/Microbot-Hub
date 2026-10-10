package net.runelite.client.plugins.microbot.construction.enums;

public enum ConstructionState {
    Idle,
    Build,
    Remove,
    Butler,
    ReturnToHouse,
    Stopped;

    public static ConstructionState next(boolean hotspotFound, boolean built, boolean hasRequiredPlanks) {
        if (!hotspotFound) {
            return ReturnToHouse;
        }
        if (built) {
            return Remove;
        }
        return hasRequiredPlanks ? Build : Butler;
    }
}
