package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.*;

/** Charged item and per-destination marker requirements shared by all planning paths. */
public final class SailorsAmuletTransports {
    private SailorsAmuletTransports() { }
    public static void apply(Transport row) {
        if (row.getType() != TransportType.TELEPORTATION_ITEM
            || row.getItemRequirements().stream().noneMatch(r -> r.getAlternatives().containsKey(32399))) return;
        // 32399 is the charged item; 32398 is the inert item and is not a candidate.
        // The charged item identity is sufficient, including in a bank snapshot; requiring
        // a second counter snapshot can wrongly exclude an otherwise usable item.
        int marker = row.getDestination().equals(new WorldPoint(1889,3292,0)) ? 19540
            : row.getDestination().equals(new WorldPoint(2797,2521,0)) ? 19541
            : row.getDestination().equals(new WorldPoint(1943,2757,0)) ? 19543 : 0;
        if (marker != 0) row.getVarbits().add(new TransportVarbit(marker,1,TransportVarbit.Operator.EQUAL));
    }
    public static List<Transport> additional() {
        Map<String,String> fields = new HashMap<>();
        fields.put("Destination", "2797 2521 0"); fields.put("Items", "32399=1");
        fields.put("Display info", "Sailors' amulet: Red Rock"); fields.put("Duration", "4");
        fields.put("Consumable", "Y"); fields.put("Members", "T"); fields.put("Wilderness level", "20");
        return List.of(net.runelite.client.plugins.microbot.droefficientwalker.support.TransportFactory.row(fields, TransportType.TELEPORTATION_ITEM));
    }
}
