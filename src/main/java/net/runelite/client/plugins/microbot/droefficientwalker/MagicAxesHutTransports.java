package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** North-door crossing observed in recording 20261018-010109-649. */
public final class MagicAxesHutTransports {
    public static final WorldPoint OUTSIDE = new WorldPoint(3191, 3963, 0);
    public static final WorldPoint INSIDE = new WorldPoint(3191, 3962, 0);
    public static final int LOCKPICK = 1523;
    private MagicAxesHutTransports() {}

    public static List<Transport> create() {
        Transport entry = new Transport(OUTSIDE, INSIDE, "Magic axe hut: Pick-lock north door",
            TransportType.TRANSPORT, true, "Pick-lock", "Door", 11726, 6);
        entry.getSkillLevels()[Skill.THIEVING.ordinal()] = 23;
        entry.getItemRequirements().add(new TransportItemRequirement(Map.of(LOCKPICK, 1)));
        entry.getItemIdRequirements().add(Set.of(LOCKPICK));
        return List.of(entry);
    }

    static boolean isEntry(Transport transport) {
        return transport != null && transport.getObjectId() == 11726
            && OUTSIDE.equals(transport.getOrigin()) && INSIDE.equals(transport.getDestination());
    }
}
