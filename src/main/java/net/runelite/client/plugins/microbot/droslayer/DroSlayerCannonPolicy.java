package net.runelite.client.plugins.microbot.droslayer;

import java.util.Locale;

/** Cannon supplies are optional; one failed trip must not become a placement or banking loop. */
final class DroSlayerCannonPolicy {
    private String task = "";
    private boolean disabled;
    private boolean bankVisit;
    private boolean restockSuppressed;

    void reset() {
        task = "";
        disabled = false;
        bankVisit = false;
        restockSuppressed = false;
    }

    void selectTask(String selected) {
        String normalized = selected == null ? "" : selected.trim().toLowerCase(Locale.ROOT);
        if (!task.equals(normalized)) {
            task = normalized;
            disabled = false;
            bankVisit = false;
            restockSuppressed = false;
        }
    }
    boolean isDisabled() { return disabled; }
    void disable() { disabled = true; }
    void leaveBank() { bankVisit = false; }
    void observeBank(boolean completeParts, int ammunition) {
        if (bankVisit) return;
        bankVisit = true;
        if (completeParts && ammunition > 0) disabled = false;
    }
    void observeRestock(int carried, int threshold) {
        // Exhausted bank stock must not send the same trip straight back to the bank.
        restockSuppressed = carried < threshold;
    }
    boolean needsRestock(boolean enabled, boolean autoBanking, boolean usingCannon,
                         int carried, boolean wilderness, int threshold) {
        return enabled && autoBanking && usingCannon && !disabled && !restockSuppressed
                && carried < restockThreshold(wilderness, threshold);
    }
    static int restockThreshold(boolean wilderness, int threshold) {
        // Wilderness carries only 350. A threshold at that same cap sends a fresh
        // cannon straight back after its first shots. Preserve lower user thresholds.
        return Math.min(wilderness ? 100 : ammunitionTarget(false, threshold), Math.max(1, threshold));
    }
    static boolean needsBankTopUp(boolean replenish, int carried, boolean wilderness, int threshold) {
        // A bank visit must leave enough ammunition for a new trip, not just enough to
        // pass the low-stock check before assembly transfers ammunition into the cannon.
        return carried < (replenish ? ammunitionTarget(wilderness, threshold)
                : restockThreshold(wilderness, threshold));
    }
    static boolean isSupply(String name) {
        if (name == null) return false;
        String value = name.toLowerCase(Locale.ROOT);
        return value.startsWith("cannon base") || value.startsWith("cannon stand")
                || value.startsWith("cannon barrels") || value.startsWith("cannon furnace")
                || value.equals("cannonball") || value.equals("steel cannonball") || value.equals("granite cannonball");
    }
    static boolean needsPurchase(int bankStock, int carried, int target) {
        return (long) bankStock + carried < target;
    }
    static boolean canDeploy(boolean completeParts, int ammunition) { return completeParts && ammunition > 0; }
    static int ammunitionTarget(boolean wilderness, int configuredThreshold) {
        return wilderness ? 350 : Math.max(1000, configuredThreshold);
    }
    static boolean suppressManualAttacks(boolean selected, boolean placed, boolean firing) {
        return selected && placed && firing;
    }
}
