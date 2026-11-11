package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Optional per-task override; old, empty and unsupported values retain weapon detection. */
public enum DroSlayerOffensivePrayer {
    AUTOMATIC(null),
    NONE(null),
    BURST_STRENGTH(Rs2PrayerEnum.BURST_STRENGTH),
    CLARITY_THOUGHT(Rs2PrayerEnum.CLARITY_THOUGHT),
    SHARP_EYE(Rs2PrayerEnum.SHARP_EYE),
    MYSTIC_WILL(Rs2PrayerEnum.MYSTIC_WILL),
    SUPERHUMAN_STRENGTH(Rs2PrayerEnum.SUPERHUMAN_STRENGTH),
    IMPROVED_REFLEXES(Rs2PrayerEnum.IMPROVED_REFLEXES),
    HAWK_EYE(Rs2PrayerEnum.HAWK_EYE),
    MYSTIC_LORE(Rs2PrayerEnum.MYSTIC_LORE),
    ULTIMATE_STRENGTH(Rs2PrayerEnum.ULTIMATE_STRENGTH),
    INCREDIBLE_REFLEXES(Rs2PrayerEnum.INCREDIBLE_REFLEXES),
    EAGLE_EYE(Rs2PrayerEnum.EAGLE_EYE),
    DEAD_EYE(Rs2PrayerEnum.DEAD_EYE),
    MYSTIC_MIGHT(Rs2PrayerEnum.MYSTIC_MIGHT),
    MYSTIC_VIGOUR(Rs2PrayerEnum.MYSTIC_VIGOUR),
    CHIVALRY(Rs2PrayerEnum.CHIVALRY),
    PIETY(Rs2PrayerEnum.PIETY),
    RIGOUR(Rs2PrayerEnum.RIGOUR),
    AUGURY(Rs2PrayerEnum.AUGURY);

    private final Rs2PrayerEnum prayer;
    DroSlayerOffensivePrayer(Rs2PrayerEnum prayer) { this.prayer = prayer; }
    public Rs2PrayerEnum getPrayer() { return prayer; }
    @Override public String toString() {
        if (this == NONE) return "None";
        return prayer == null ? "Automatic (from weapon)" : prayer.getName();
    }
    public static DroSlayerOffensivePrayer fromString(String value) {
        if (value == null || value.isBlank()) return AUTOMATIC;
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return AUTOMATIC; }
    }
}
