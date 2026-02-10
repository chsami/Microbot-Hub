package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.List;

/**
 * Predefined cannon placement spots for slayer tasks.
 * Data sourced from RuneLite's CannonSpots enum.
 */
@Getter
@RequiredArgsConstructor
public enum CannonSpot {
    ABERRANT_SPECTRES("Aberrant spectre", new WorldPoint(2456, 9791, 0)),
    ANKOU("Ankou", new WorldPoint(3177, 10193, 0), new WorldPoint(3360, 10077, 0)),
    BANDIT("Bandit", new WorldPoint(3037, 3700, 0)),
    BEAR("Bear", new WorldPoint(3113, 3672, 0)),
    BLACK_DEMONS("Black demon",
            new WorldPoint(2859, 9778, 0),
            new WorldPoint(2841, 9791, 0),
            new WorldPoint(1421, 10089, 1),
            new WorldPoint(3174, 10154, 0),
            new WorldPoint(3089, 9960, 0)),
    BLACK_DRAGON("Black dragon", new WorldPoint(3239, 10206, 0), new WorldPoint(3362, 10156, 0)),
    BLACK_KNIGHTS("Black knight", new WorldPoint(2906, 9685, 0), new WorldPoint(3053, 3852, 0)),
    BLOODVELDS("Bloodveld",
            new WorldPoint(2439, 9821, 0),
            new WorldPoint(2448, 9821, 0),
            new WorldPoint(2472, 9832, 0),
            new WorldPoint(2453, 9817, 0),
            new WorldPoint(3596, 9743, 0)),
    BLUE_DRAGON("Blue dragon", new WorldPoint(1933, 8973, 1)),
    BRINE_RAT("Brine rat", new WorldPoint(2707, 10132, 0)),
    CAVE_HORROR("Cave horror", new WorldPoint(3785, 9460, 0)),
    DAGANNOTH("Dagannoth",
            new WorldPoint(2524, 10020, 0),
            new WorldPoint(2478, 10443, 0),
            new WorldPoint(2420, 10425, 0)),
    DARK_BEAST("Dark beast", new WorldPoint(1992, 4655, 0)),
    DARK_WARRIOR("Dark warrior", new WorldPoint(3030, 3632, 0)),
    DUST_DEVIL("Dust devil", new WorldPoint(3218, 9366, 0)),
    EARTH_WARRIOR("Earth warrior", new WorldPoint(3120, 9987, 0)),
    ELDER_CHAOS_DRUID("Elder Chaos druid", new WorldPoint(3237, 3622, 0)),
    ELVES("Elf", new WorldPoint(3278, 6098, 0)),
    FIRE_GIANTS("Fire giant",
            new WorldPoint(2393, 9782, 0),
            new WorldPoint(2412, 9776, 0),
            new WorldPoint(2401, 9780, 0),
            new WorldPoint(3047, 10340, 0)),
    GREATER_DEMONS("Greater demon",
            new WorldPoint(1435, 10086, 2),
            new WorldPoint(3224, 10132, 0),
            new WorldPoint(3427, 10149, 0)),
    GREEN_DRAGON("Green dragon", new WorldPoint(3225, 10068, 0), new WorldPoint(3399, 10122, 0)),
    HELLHOUNDS("Hellhound",
            new WorldPoint(2431, 9776, 0),
            new WorldPoint(2413, 9786, 0),
            new WorldPoint(2783, 9686, 0),
            new WorldPoint(3198, 10071, 0)),
    HILL_GIANT("Hill giant", new WorldPoint(3044, 10318, 0)),
    ICE_GIANT("Ice giant", new WorldPoint(3207, 10164, 0), new WorldPoint(3339, 10056, 0)),
    ICE_WARRIOR("Ice warrior", new WorldPoint(2955, 3876, 0)),
    KALPHITE("Kalphite", new WorldPoint(3307, 9528, 0)),
    LESSER_DEMON("Lesser demon",
            new WorldPoint(2838, 9559, 0),
            new WorldPoint(3163, 10114, 0),
            new WorldPoint(3338, 10134, 0)),
    LIZARDMEN("Lizardman", new WorldPoint(1507, 3705, 0)),
    LIZARDMEN_SHAMAN("Lizardman shaman", new WorldPoint(1423, 3715, 0)),
    MAGIC_AXE("Magic axe", new WorldPoint(3190, 3960, 0)),
    MAMMOTH("Mammoth", new WorldPoint(3168, 3595, 0)),
    MINIONS_OF_SCARABAS("Scarab", new WorldPoint(3297, 9252, 0)),
    MOSS_GIANT("Moss giant", new WorldPoint(3159, 9903, 0)),
    ROGUE("Rogue", new WorldPoint(3285, 3930, 0)),
    SCORPION("Scorpion", new WorldPoint(3233, 10335, 0)),
    SKELETON("Skeleton", new WorldPoint(3017, 3589, 0)),
    SMOKE_DEVIL("Smoke devil", new WorldPoint(2398, 9444, 0)),
    SPIDER("Spider", new WorldPoint(3169, 3886, 0)),
    SUQAHS("Suqah", new WorldPoint(2114, 3943, 0)),
    TROLLS("Troll", new WorldPoint(2401, 3856, 0), new WorldPoint(1242, 3517, 0)),
    WARPED_CREATURES("Warped creature", new WorldPoint(1490, 4263, 1)),
    WYRMS("Wyrm", new WorldPoint(1368, 9695, 0)),
    ZOMBIE("Zombie", new WorldPoint(3172, 3677, 0));

    private final String taskName;
    private final List<WorldPoint> spots;

    CannonSpot(String taskName, WorldPoint... spots) {
        this.taskName = taskName;
        this.spots = Arrays.asList(spots);
    }

    /**
     * Gets the first (primary) cannon spot for this task
     */
    public WorldPoint getPrimarySpot() {
        return spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * Finds a cannon spot for the given slayer task name
     * @param taskName The slayer task name to search for
     * @return The matching CannonSpot, or null if not found
     */
    public static CannonSpot forTask(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return null;
        }
        String taskLower = taskName.toLowerCase();
        for (CannonSpot spot : values()) {
            if (taskLower.contains(spot.getTaskName().toLowerCase()) ||
                spot.getTaskName().toLowerCase().contains(taskLower)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Finds the closest cannon spot to the given location
     * @param taskName The slayer task name
     * @param playerLocation Current player location
     * @return The closest WorldPoint for cannon placement, or null if no spots for this task
     */
    public static WorldPoint getClosestSpot(String taskName, WorldPoint playerLocation) {
        CannonSpot cannonSpot = forTask(taskName);
        if (cannonSpot == null || playerLocation == null) {
            return null;
        }

        WorldPoint closest = null;
        int closestDistance = Integer.MAX_VALUE;

        for (WorldPoint spot : cannonSpot.getSpots()) {
            int distance = playerLocation.distanceTo(spot);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = spot;
            }
        }

        return closest;
    }

    /**
     * Checks if a cannon spot exists for the given task
     */
    public static boolean hasSpotForTask(String taskName) {
        return forTask(taskName) != null;
    }
}
