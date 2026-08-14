package net.runelite.client.plugins.microbot.zulrahslayer.rotationutils;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.StandLocation;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.VenomTiming;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.ZulrahType;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public enum RotationType {
    // Venom timing per phase is taken from the OSRS wiki Zulrah rotation descriptions: START = clouds
    // spewed at the phase's opening (stay put — our tile is the safespot), END = clouds land after the
    // attacks (pre-move to the next tile), NONE = no venom that phase.
    ROT_A("Rotation A",
            ImmutableList.of(
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, null, VenomTiming.START),
                    add(ZulrahType.MELEE, StandLocation.NORTHEAST_NORTH, null),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END),
                    add(ZulrahType.MELEE, StandLocation.WEST_PILLAR_N, null),
                    add(ZulrahType.MAGIC, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.EAST_PILLAR_N, null, VenomTiming.START),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC, VenomTiming.END),
                    addJad(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END),
                    add(ZulrahType.MELEE, StandLocation.NORTHEAST_NORTH, null),
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END))),
    ROT_B("Rotation B",
            ImmutableList.of(
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, null, VenomTiming.START),
                    add(ZulrahType.MELEE, StandLocation.NORTHEAST_NORTH, null),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N   , null, VenomTiming.START),
                    add(ZulrahType.MAGIC, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC, VenomTiming.END),
                    add(ZulrahType.MELEE, StandLocation.WEST_PILLAR_N, null),
                    add(ZulrahType.RANGE, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC, VenomTiming.END),
                    addJad(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END),
                    add(ZulrahType.MELEE, StandLocation.NORTHEAST_NORTH, null),
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END))),
    ROT_C("Rotation C",
            ImmutableList.of(
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, null, VenomTiming.START),
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, Rs2PrayerEnum.PROTECT_RANGE),
                    add(ZulrahType.MELEE, StandLocation.EAST_PILLAR_N, null, VenomTiming.START),
                    add(ZulrahType.MAGIC, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, null, VenomTiming.START),
                    add(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC, VenomTiming.END),
                    addJad(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.MAGIC, StandLocation.NORTHEAST_NORTH, null),
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END))),
    ROT_D("Rotation D",
            ImmutableList.of(
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, null, VenomTiming.START),
                    add(ZulrahType.MAGIC, StandLocation.NORTHEAST_NORTH, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END),
                    add(ZulrahType.MAGIC, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.MELEE, StandLocation.EAST_PILLAR_N, null, VenomTiming.END),
                    add(ZulrahType.RANGE, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE),
                    add(ZulrahType.RANGE, StandLocation.WEST_PILLAR_N, null, VenomTiming.END),
                    add(ZulrahType.MAGIC, StandLocation.WEST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.RANGE, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_RANGE),
                    add(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC, VenomTiming.END),
                    addJad(ZulrahType.MAGIC, StandLocation.EAST_PILLAR_N, Rs2PrayerEnum.PROTECT_MAGIC),
                    add(ZulrahType.MAGIC, StandLocation.NORTHEAST_NORTH, null),
                    add(ZulrahType.RANGE, StandLocation.NORTHEAST_NORTH, Rs2PrayerEnum.PROTECT_RANGE, VenomTiming.END)));

    private static final List<RotationType> lookup = new ArrayList<>();

    static {
        lookup.addAll(EnumSet.allOf(RotationType.class));
    }

    @Getter
    private final String rotationName;
    @Getter
    private final List<ZulrahPhase> zulrahPhases;

    RotationType(String rotationName, List<ZulrahPhase> zulrahPhases) {
        this.rotationName = rotationName;
        this.zulrahPhases = zulrahPhases;
    }

    /** All rotations, as a fresh mutable list (starting point for narrowing). */
    public static List<RotationType> allRotations() {
        return new ArrayList<>(lookup);
    }

    /** Rotations from {@code base} whose form at {@code stage} matches the observed Zulrah. */
    public static List<RotationType> matching(List<RotationType> base, NPC npc, int stage) {
        ZulrahNpc observed = ZulrahNpc.valueOf(npc, false);
        if (observed == null || stage < 0) {
            log.warn("Cannot match rotations: observed={} stage={}", observed, stage);
            return new ArrayList<>();
        }
        return base.stream()
                .filter(type -> stage < type.getZulrahPhases().size())
                .filter(type -> type.getZulrahPhases().get(stage).getZulrahNpc().equals(observed))
                .collect(Collectors.toList());
    }

    private static ZulrahPhase add(ZulrahType type, StandLocation standLocation, Rs2PrayerEnum prayer) {
        return add(type, standLocation, prayer, VenomTiming.NONE);
    }

    private static ZulrahPhase add(ZulrahType type, StandLocation standLocation, Rs2PrayerEnum prayer,
                                   VenomTiming venomTiming) {
        return new ZulrahPhase(new ZulrahNpc(type, false), new ZulrahAttributes(standLocation, prayer, venomTiming));
    }

    private static ZulrahPhase addJad(ZulrahType type, StandLocation standLocation, Rs2PrayerEnum prayer) {
        return addJad(type, standLocation, prayer, VenomTiming.NONE);
    }

    private static ZulrahPhase addJad(ZulrahType type, StandLocation standLocation, Rs2PrayerEnum prayer,
                                      VenomTiming venomTiming) {
        return new ZulrahPhase(new ZulrahNpc(type, true), new ZulrahAttributes(standLocation, prayer, venomTiming));
    }
}
