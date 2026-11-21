package net.runelite.client.plugins.microbot.droelf;

public enum MouseActivity {
    AFK(-1, 0.0, 0.0), BALANCED(-2, 1.0, 1.0), ACTIVE(6, 1.8, 1.2);

    private final int parkChance;
    private final double hoverMultiplier;
    private final double glanceMultiplier;

    MouseActivity(int parkChance, double hoverMultiplier, double glanceMultiplier) {
        this.parkChance = parkChance;
        this.hoverMultiplier = hoverMultiplier;
        this.glanceMultiplier = glanceMultiplier;
    }

    public int offScreenChance(SessionPersonality p) {
        if (parkChance == -1) return 100;
        if (parkChance == -2) return p.offScreenParkChance();
        return parkChance;
    }
    public boolean isPureAfk() { return this == AFK; }
    public int earlyHoverChance(SessionPersonality p) { return scale(p.earlyHoverChance(), hoverMultiplier); }
    public int lateHoverChance(SessionPersonality p) { return scale(p.lateHoverChance(), hoverMultiplier); }
    public int inventoryGlanceChance(SessionPersonality p) { return scale(p.inventoryGlanceChance(), glanceMultiplier); }
    private static int scale(int base, double multiplier) {
        return Math.max(0, Math.min(100, (int) Math.round(base * multiplier)));
    }
    @Override public String toString() { return name().charAt(0) + name().substring(1).toLowerCase(); }
}
