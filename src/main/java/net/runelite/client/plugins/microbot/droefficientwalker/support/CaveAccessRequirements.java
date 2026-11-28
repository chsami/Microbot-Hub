package net.runelite.client.plugins.microbot.droefficientwalker.support;

import java.util.Set;
import java.util.function.IntPredicate;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Shared access predicates for dark caves, Kalphite Lair, Dorgesh-Kaan underground bypasses,
 * and Shantay Pass so both {@code Rs2Walker} / {@code shortestpath} and {@code EfficientWalker}
 * enforce the same requirements during planning and execution.
 */
public final class CaveAccessRequirements
{
    public static final int SHANTAY_PASS_MAIN_OBJECT_ID = 4031;
    public static final int SHANTAY_PASS_SOUTH_OBJECT_ID = 41326;
    public static final int SHANTAY_PASS_SOUTH_LEGACY_OBJECT_ID = 41863;
    public static final int SHANTAY_WARNING_WIDGET_GROUP = 565;
    public static final int SHANTAY_WARNING_YES_CHILD = 17;
    public static final int SHANTAY_VENDOR_NPC_ID = 4642;
    public static final int SHANTAY_PASS_COST = 5;

    public static final int LUMBRIDGE_SWAMP_DARK_HOLE_ID = 5947;
    public static final int KALPHITE_ENTRANCE_ID = 3827;
    public static final int KALPHITE_ROPE_UP_ID = 3829;
    public static final int DORGESH_KALPHITE_BLOCKED_1 = 22656;
    public static final int DORGESH_KALPHITE_BLOCKED_2 = 22657;
    public static final int DORGESH_KALPHITE_EXIT = 23596;
    public static final int DORGESH_KAAN_DOOR_ID = 6919;

    public static final int SWAMP_CAVES_ROPED_VARBIT = VarbitID.SWAMP_CAVES_ROPED_ENTRANCE;
    public static final int KALPHITE_ROPE_1_VARBIT = VarbitID.KALPHITE_ROPE_1;
    public static final int DESERT_DIARY_HARD_VARBIT = VarbitID.DESERT_DIARY_HARD_COMPLETE;
    public static final int DESERT_DIARY_ELITE_VARBIT = VarbitID.DESERT_DIARY_ELITE_COMPLETE;

    private static final Set<Integer> LIT_LIGHT_SOURCES = Set.of(
        ItemID.WINT_TORCH,
        ItemID.WINT_TORCH_OFFHAND,
        ItemID.SEERS_HEADBAND_EASY,
        ItemID.SEERS_HEADBAND_MEDIUM,
        ItemID.SEERS_HEADBAND_HARD,
        ItemID.SEERS_HEADBAND_ELITE,
        ItemID.SKILLCAPE_FIREMAKING,
        ItemID.SKILLCAPE_FIREMAKING_TRIMMED,
        ItemID.SKILLCAPE_MAX,
        ItemID.BULLSEYE_LANTERN_LIT,
        ItemID.TOG_SAPPHIRE_LANTERN_LIT,
        ItemID.BULLSEYE_LANTERN_LIT_LUNAR_QUEST,
        ItemID.ABYSSAL_LANTERN_NORMAL,
        ItemID.ABYSSAL_LANTERN_NORMAL_BLUE,
        ItemID.ABYSSAL_LANTERN_NORMAL_RED,
        ItemID.ABYSSAL_LANTERN_NORMAL_WHITE,
        ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE,
        ItemID.ABYSSAL_LANTERN_NORMAL_GREEN,
        ItemID.ABYSSAL_LANTERN_OAK,
        ItemID.ABYSSAL_LANTERN_WILLOW,
        ItemID.ABYSSAL_LANTERN_MAPLE,
        ItemID.ABYSSAL_LANTERN_YEW,
        ItemID.ABYSSAL_LANTERN_BLISTERWOOD,
        ItemID.ABYSSAL_LANTERN_MAGIC,
        ItemID.ABYSSAL_LANTERN_REDWOOD,
        ItemID.CAVE_GOBLIN_MINING_HELMET_LIT,
        ItemID.OIL_LANTERN_LIT,
        ItemID.CANDLE_LANTERN_LIT,
        ItemID.CANDLE_LANTERN_BLACK_LIT,
        ItemID.OIL_LAMP_LIT,
        ItemID.TORCH_LIT,
        ItemID.LIT_CANDLE,
        ItemID.LIT_BLACK_CANDLE
    );

    private CaveAccessRequirements()
    {
    }

    public static Set<Integer> litLightSources()
    {
        return LIT_LIGHT_SOURCES;
    }

    public static boolean isLitLightSource(int itemId)
    {
        return LIT_LIGHT_SOURCES.contains(itemId);
    }

    public static boolean hasAnyLitLightSource(IntPredicate owned)
    {
        if (owned == null)
        {
            return false;
        }
        for (int id : LIT_LIGHT_SOURCES)
        {
            if (owned.test(id))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isLumbridgeSwampDarkHole(Transport transport)
    {
        if (transport == null || transport.getType() != TransportType.TRANSPORT
            || transport.getObjectId() != LUMBRIDGE_SWAMP_DARK_HOLE_ID
            || !"Climb-down".equalsIgnoreCase(transport.getAction()))
        {
            return false;
        }
        return isLumbridgeSwampDarkHole(transport.getOrigin(), transport.getDestination());
    }

    public static boolean isLumbridgeSwampDarkHole(WorldPoint from, WorldPoint to)
    {
        return from != null && to != null && from.getPlane() == 0 && to.getPlane() == 0
            && from.getX() >= 3167 && from.getX() <= 3171
            && from.getY() >= 3171 && from.getY() <= 3174
            && to.getX() >= 3166 && to.getX() <= 3172
            && to.getY() >= 9570 && to.getY() <= 9575;
    }

    public static boolean isKalphiteLairRopeEdge(Transport transport)
    {
        if (transport == null || transport.getType() != TransportType.TRANSPORT)
        {
            return false;
        }
        int id = transport.getObjectId();
        if (id != KALPHITE_ENTRANCE_ID && id != KALPHITE_ROPE_UP_ID)
        {
            return false;
        }
        WorldPoint origin = transport.getOrigin();
        WorldPoint dest = transport.getDestination();
        if (origin == null || dest == null)
        {
            return false;
        }
        boolean down = id == KALPHITE_ENTRANCE_ID
            && origin.getPlane() == 0 && dest.getPlane() == 2
            && origin.getX() >= 3225 && origin.getX() <= 3230 && origin.getY() >= 3106 && origin.getY() <= 3111
            && dest.getX() >= 3482 && dest.getX() <= 3485 && dest.getY() >= 9508 && dest.getY() <= 9511;
        boolean up = id == KALPHITE_ROPE_UP_ID
            && origin.getPlane() == 2 && dest.getPlane() == 0
            && origin.getX() >= 3482 && origin.getX() <= 3485 && origin.getY() >= 9508 && origin.getY() <= 9511
            && dest.getX() >= 3225 && dest.getX() <= 3230 && dest.getY() >= 3106 && dest.getY() <= 3111;
        return down || up;
    }

    public static boolean isDorgeshKaanKalphiteTunnel(Transport transport)
    {
        if (transport == null || transport.getType() != TransportType.TRANSPORT)
        {
            return false;
        }
        int id = transport.getObjectId();
        return id == DORGESH_KALPHITE_BLOCKED_1
            || id == DORGESH_KALPHITE_BLOCKED_2
            || id == DORGESH_KALPHITE_EXIT;
    }

    public static boolean isDorgeshKaanDoor(Transport transport)
    {
        if (transport == null || transport.getType() != TransportType.TRANSPORT
            || transport.getObjectId() != DORGESH_KAAN_DOOR_ID)
        {
            return false;
        }
        WorldPoint origin = transport.getOrigin();
        WorldPoint dest = transport.getDestination();
        return origin != null && dest != null
            && origin.getX() >= 3315 && origin.getX() <= 3320 && origin.getY() >= 9600 && origin.getY() <= 9605
            && dest.getX() >= 2745 && dest.getX() <= 2750 && dest.getY() >= 5372 && dest.getY() <= 5376;
    }

    public static boolean isShantayPass(Transport transport)
    {
        if (transport == null || transport.getType() != TransportType.TRANSPORT
            || !"Go-through".equalsIgnoreCase(transport.getAction()))
        {
            return false;
        }
        int id = transport.getObjectId();
        return id == SHANTAY_PASS_MAIN_OBJECT_ID
            || id == SHANTAY_PASS_SOUTH_OBJECT_ID
            || id == SHANTAY_PASS_SOUTH_LEGACY_OBJECT_ID;
    }

    public static boolean isShantayPassObjectId(int objectId)
    {
        return objectId == SHANTAY_PASS_MAIN_OBJECT_ID
            || objectId == SHANTAY_PASS_SOUTH_OBJECT_ID
            || objectId == SHANTAY_PASS_SOUTH_LEGACY_OBJECT_ID;
    }

    public static boolean isShantayPassSouthbound(Transport transport)
    {
        if (!isShantayPass(transport) || transport.getOrigin() == null || transport.getDestination() == null)
        {
            return false;
        }
        WorldPoint from = transport.getOrigin();
        WorldPoint to = transport.getDestination();
        if (transport.getObjectId() == SHANTAY_PASS_MAIN_OBJECT_ID)
        {
            return from.getY() >= 3117 && to.getY() <= 3115;
        }
        // Southern desert passes (41326 / 41863): entering desert is eastbound (3193 -> 3196) or southbound (2819 -> 2816).
        return (from.getX() <= 3193 && to.getX() >= 3196)
            || (from.getY() >= 2819 && to.getY() <= 2816);
    }

    public static boolean isDesertSouthOfShantay(WorldPoint point)
    {
        return point != null && point.getPlane() == 0
            && point.getX() >= 3130 && point.getX() <= 3540
            && point.getY() >= 2700 && point.getY() <= 3115;
    }

    public static boolean isNorthOfShantay(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getY() >= 3116;
    }
}
