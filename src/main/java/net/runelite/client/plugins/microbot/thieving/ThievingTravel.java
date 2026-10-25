package net.runelite.client.plugins.microbot.thieving;

import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers deciding whether the thieving script should travel to the target NPC's area,
 * hop worlds or start thieving. Kept free of client state so it can be unit tested.
 */
final class ThievingTravel {
    /** Distance (tiles) within which the player counts as "on site". */
    static final int TARGET_RADIUS = 15;

    enum Decision {
        /** Thieve (or fall through to the regular logic). */
        THIEVE,
        /** Walk to the target area; never hop while travelling. */
        WALK,
        /** On site but the NPC is absent or contested: hop worlds. */
        HOP,
        /** No known target area: stop instead of hopping blindly. */
        STOP
    }

    private ThievingTravel() {
    }

    /**
     * @param npcNear          a matching NPC is available within {@link #TARGET_RADIUS} tiles of the player
     * @param walkEnabled      "Walk to NPC location" is on and applies to this NPC
     * @param hopAllowed       hopping is allowed for this NPC (false for wealthy citizens)
     * @param hasTarget        a target area is known (anchor, visible far NPC or spawn data)
     * @param distanceToTarget tiles from the player to the target (Integer.MAX_VALUE for another plane)
     */
    static Decision decide(boolean npcNear, boolean walkEnabled, boolean hopAllowed, boolean hasTarget, int distanceToTarget) {
        if (npcNear) return Decision.THIEVE;
        if (!walkEnabled) return hopAllowed ? Decision.HOP : Decision.THIEVE;
        if (!hasTarget) return Decision.STOP;
        if (distanceToTarget > TARGET_RADIUS) return Decision.WALK;
        return hopAllowed ? Decision.HOP : Decision.THIEVE;
    }

    /**
     * Names to try (in order) against the client's NPC spawn data, whose keys are properly cased
     * (for example "Man", "Warrior (Thieving)"). Empty when the spawn data cannot help.
     */
    static List<String> locationNames(ThievingNpc npc, String fallbackName) {
        final List<String> names = new ArrayList<>();
        if (npc == null) return names;
        switch (npc) {
            case WARRIOR:
                names.add("Warrior (Thieving)");
                names.add("Warrior woman");
                names.add("Al-Kharid warrior");
                break;
            case MASTER_FARMER:
                // No dedicated spawn data; "Farmer" would lead to the wrong NPC.
            case WEALTHY_CITIZEN:
            case VYRES:
            case ELVES:
                break;
            case NONE:
                if (fallbackName != null && !fallbackName.isBlank()) {
                    names.add(fallbackName);
                    names.add(titleCase(fallbackName));
                }
                break;
            default:
                names.add(titleCase(npc.getName()));
                names.add(npc.getName());
                break;
        }
        return names;
    }

    /**
     * True for tiles in the Wilderness (surface 2944-3392 x 3520-3967, wilderness dungeons y 9920-10367).
     * Conservative on purpose: Ferox Enclave and the Black Knights' Fortress are treated as Wilderness too.
     */
    static boolean isWilderness(int x, int y, int plane) {
        if (plane < 0 || plane > 3) return false;
        final boolean inX = x >= 2944 && x <= 3392;
        return inX && ((y >= 3520 && y <= 3967) || (y >= 9920 && y <= 10367));
    }

    /** True when the location name or its centre tile is in the Wilderness. */
    static boolean isWildernessLocation(String locationName, int x, int y, int plane) {
        return (locationName != null && locationName.toLowerCase().contains("wilderness")) || isWilderness(x, y, plane);
    }

    static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        final StringBuilder sb = new StringBuilder(s.length());
        boolean up = true;
        for (char c : s.toCharArray()) {
            sb.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return sb.toString();
    }
}
