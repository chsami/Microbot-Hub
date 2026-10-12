package net.runelite.client.plugins.microbot.zulrahslayer.rotationutils;

import lombok.Getter;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.StandLocation;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.VenomTiming;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Getter
public final class ZulrahAttributes {
    @Nonnull
    private final StandLocation standLocation;
    @Nullable
    private final Rs2PrayerEnum prayer;
    /** Whether/when this phase spews venom, driving the end-of-phase pre-move. */
    @Nonnull
    private final VenomTiming venomTiming;

    public ZulrahAttributes(@Nonnull StandLocation standLocation, @Nullable Rs2PrayerEnum prayer) {
        this(standLocation, prayer, VenomTiming.NONE);
    }

    public ZulrahAttributes(@Nonnull StandLocation standLocation, @Nullable Rs2PrayerEnum prayer,
                            @Nonnull VenomTiming venomTiming) {
        this.standLocation = standLocation;
        this.prayer = prayer;
        this.venomTiming = venomTiming;
    }
}
