package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Identifiers, tiles and messages for the Ancient Guthixian Temple trip. */
public final class TempleData
{
    public static final Set<Integer> DEMON_IDS = Set.of(13599, 13600);
    public static final int DEMON_SIZE = 3;
    public static final int MELEE_ANIMATION = 11392;
    public static final int RANGED_ANIMATION = 11389;
    public static final int MAGIC_ANIMATION = 11388;
    public static final int BIND_ANIMATION = 11387;
    public static final int DEATH_ANIMATION = 11394;
    /** A demon coming back plays this for 8 ticks and cannot be attacked until it ends. */
    public static final int SUMMON_ANIMATION = 11395;
    public static final int RANGED_PROJECTILE = 2857;
    public static final int MAGIC_PROJECTILE = 2853;
    public static final int BOMB_PROJECTILE = 2855;
    public static final int BIND_PROJECTILE = 178;
    public static final int BIND_SPOT_ANIMATION = 179;
    public static final String BIND_MESSAGE = "The demon's spell binds you!";
    public static final String QUEST_MESSAGE = "You need to complete the While Guthix Sleeps quest before you can teleport to Ancient Guthixian Temple.";

    public static final int TEMPLE_TELEPORT = 29684;
    public static final int TORMENTED_SYNAPSE = 29580;
    public static final int BURNING_CLAW = 29574;
    public static final List<Integer> DUELING_RINGS = List.of(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566);
    public static final List<Integer> PRAYER_RESTORES = List.of(143, 141, 139, 2434, 3030, 3028, 3026, 3024);

    public static final int FIRST_WALL = 53623;
    public static final int SECOND_WALL = 53624;
    public static final int TEMPLE_DOOR = 54082;
    public static final int TEMPLE_OPENING = 53622;
    public static final WorldPoint SCROLL_ARRIVAL = new WorldPoint(4063, 4557, 0);
    public static final WorldPoint TEMPLE_ENTRY = new WorldPoint(4061, 4466, 0);

    public static final WorldPoint FEROX_ARRIVAL = new WorldPoint(3151, 3636, 0);
    public static final WorldPoint FEROX_BANK = new WorldPoint(3130, 3632, 0);
    public static final WorldPoint FEROX_POOL = new WorldPoint(3128, 3635, 0);
    public static final int FEROX_POOL_OBJECT = 39651;
    public static final int FEROX_BANK_CHEST = 26711;

    private TempleData()
    {
    }

    /** The scroll lands inside the eight-tile polygon south of the chasm walls. */
    public static boolean atScrollArrival(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getX() >= 4060 && point.getX() <= 4066
            && point.getY() >= 4554 && point.getY() <= 4560;
    }

    /** Plane 1 is the ledge between the two walls; plane 2 is the top, at the temple door. */
    public static boolean onChasmLedge(WorldPoint point, int plane)
    {
        return point != null && point.getPlane() == plane && point.getX() >= 4055 && point.getX() <= 4070
            && point.getY() >= 4544 && point.getY() <= 4560;
    }

    public static boolean inTemple(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getX() >= 4000 && point.getX() <= 4170
            && point.getY() >= 4340 && point.getY() <= 4475;
    }

    public static boolean atFerox(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getX() >= 3120 && point.getX() <= 3160
            && point.getY() >= 3620 && point.getY() <= 3645;
    }
}
