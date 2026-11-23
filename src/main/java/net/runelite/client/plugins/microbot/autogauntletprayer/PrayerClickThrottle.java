package net.runelite.client.plugins.microbot.autogauntletprayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.EnumMap;
import java.util.Map;

class PrayerClickThrottle {
    static final int COOLDOWN_TICKS = 2;

    private final Map<Rs2PrayerEnum, Integer> lastClickTicks = new EnumMap<>(Rs2PrayerEnum.class);

    boolean tryClick(Rs2PrayerEnum prayer, int tick) {
        Integer lastTick = lastClickTicks.get(prayer);
        if (lastTick != null) {
            int elapsed = tick - lastTick;
            if (elapsed >= 0 && elapsed < COOLDOWN_TICKS) {
                return false;
            }
        }
        lastClickTicks.put(prayer, tick);
        return true;
    }

    void reset() {
        lastClickTicks.clear();
    }
}
