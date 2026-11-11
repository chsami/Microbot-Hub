package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Prayer flicking styles for slayer combat.
 * Based on AIOFighter's prayer styles.
 */
@Getter
@RequiredArgsConstructor
public enum PrayerFlickStyle {
    OFF("Off", "Prayer management disabled"),
    ALWAYS_ON("Always On", "Prayer stays on during combat"),
    LAZY_FLICK("Lazy Flick", "Flicks prayer tick before enemy hit"),
    PERFECT_LAZY_FLICK("Perfect Lazy Flick", "Flicks prayer on enemy hit tick"),
    MIXED_LAZY_FLICK("Mixed Lazy Flick", "Randomly flicks on hit or tick before");

    private final String name;
    private final String description;

    @Override
    public String toString() {
        return name;
    }
}
