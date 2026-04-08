package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import com.google.common.collect.ImmutableMap;
import net.runelite.api.gameval.ObjectID1;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Cargo hold tile object IDs and capacities per wood tier and boat layout (raft / 2x5 skiff / large sloop).
 * {@link ObjectID1} defines three visuals per tier: {@code SAILING_BOAT_CARGO_HOLD_<tier>_<boat>},
 * {@code ..._NO_CARGO}, and {@code ..._CARGO}; all map to the same slot capacity.
 */
public final class CargoHoldObjectIds {

    private CargoHoldObjectIds() {
    }

    /**
     * Every cargo hold object ID (base, no-cargo, and cargo visuals) mapped to maximum <strong>slots</strong> for that
     * boat tier. Used occupancy is read from the open cargo-hold interface item grid (widgets) after opening the hold.
     */
    public static final Map<Integer, Integer> ID_TO_CAPACITY = buildIdToCapacity();

    public static final Set<Integer> ALL_IDS = Collections.unmodifiableSet(ID_TO_CAPACITY.keySet());

    private static Map<Integer, Integer> buildIdToCapacity() {
        ImmutableMap.Builder<Integer, Integer> b = ImmutableMap.builder();
        // Basic (Regular) — raft 20, skiff (2x5) 30, sloop (large) 40
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_CARGO, 20);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_CARGO, 30);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_CARGO, 40);
        // Oak — 30, 45, 60
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_CARGO, 30);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_2X5_CARGO, 45);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_CARGO, 60);
        // Teak — 45, 60, 90
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_CARGO, 45);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_CARGO, 60);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_CARGO, 90);
        // Mahogany — 60, 90, 120
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_CARGO, 60);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_CARGO, 90);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_CARGO, 120);
        // Camphor — 80, 120, 160
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_CARGO, 80);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_CARGO, 120);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_CARGO, 160);
        // Ironwood — 105, 150, 210
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_CARGO, 105);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_CARGO, 150);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_CARGO, 210);
        // Rosewood — 120, 180, 240
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_CARGO, 120);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_CARGO, 180);
        putTier(b, ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,
                ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_NO_CARGO, ObjectID1.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_CARGO, 240);
        return b.build();
    }

    private static void putTier(ImmutableMap.Builder<Integer, Integer> b, int baseId, int noCargoId, int cargoId, int capacity) {
        b.put(baseId, capacity);
        b.put(noCargoId, capacity);
        b.put(cargoId, capacity);
    }
}
