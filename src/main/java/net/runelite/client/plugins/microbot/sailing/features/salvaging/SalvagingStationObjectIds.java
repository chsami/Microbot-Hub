package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.gameval.ObjectID1;

import java.util.Set;

/**
 * Tile object IDs for salvaging stations the script can deposit shipwreck salvage into.
 * Boat stations are matched by ID because {@code Rs2TileObjectModel#getName()} is often null or not the
 * literal phrase &quot;salvaging station&quot; for sailing compositions.
 */
public final class SalvagingStationObjectIds {

    private SalvagingStationObjectIds() {
    }

    public static final Set<Integer> ALL_IDS = ImmutableSet.of(
            ObjectID1.SAILING_SALVAGING_STATION_2X5A,
            ObjectID1.SAILING_SALVAGING_STATION_2X5B,
            ObjectID1.SAILING_SALVAGING_STATION_3X8,
            ObjectID1.SAILING_PORT_SALVAGING_STATION
    );
}
