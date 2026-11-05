package net.runelite.client.plugins.microbot.barbarianfishing;

import net.runelite.api.coords.WorldPoint;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Mount Quidamortem / Chambers of Xeric barbarian-fishing coordinates and banking helpers.
 */
public final class MountQuidamortem
{
    public static final int REGION_ID = 4919;

    public static final int BANK_CHEST_ID = 30087;
    public static final WorldPoint BANK_CHEST_TILE = new WorldPoint(1254, 3572, 0);
    public static final WorldPoint BANK_FRONT_TILE = new WorldPoint(1254, 3571, 0);
    public static final WorldPoint BANK_DIRECT_TILE = new WorldPoint(1254, 3570, 0);
    public static final WorldPoint BANK_NEAR_TILE = new WorldPoint(1255, 3568, 0);

    public static final WorldPoint REF_EAST = new WorldPoint(1276, 3562, 0);
    public static final WorldPoint REF_SOUTH = new WorldPoint(1258, 3545, 0);
    public static final WorldPoint REF_WEST = new WorldPoint(1248, 3561, 0);
    public static final WorldPoint REF_RETURN = new WorldPoint(1271, 3546, 0);

    // Lake recovery center tile when no spots are currently visible
    public static final WorldPoint RECOVERY_CENTER = new WorldPoint(1261, 3543, 0);

    public static final WorldPoint[] RECOVERY_REFERENCES = {
            REF_EAST,
            REF_SOUTH,
            REF_WEST,
            REF_RETURN
    };

    public enum BankApproach
    {
        DIRECT("direct", BANK_DIRECT_TILE, 0),
        FRONT("front", BANK_FRONT_TILE, 0),
        NEAR("near", BANK_NEAR_TILE, 0);

        final String label;
        final WorldPoint target;
        final int arrivalRadius;

        BankApproach(String label, WorldPoint target, int arrivalRadius)
        {
            this.label = label;
            this.target = target;
            this.arrivalRadius = arrivalRadius;
        }

        public WorldPoint getTarget()
        {
            return target;
        }

        public int getArrivalRadius()
        {
            return arrivalRadius;
        }
    }

    public static BankApproach randomBankApproach()
    {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60)
        {
            return BankApproach.DIRECT;
        }
        if (roll < 85)
        {
            return BankApproach.FRONT;
        }
        return BankApproach.NEAR;
    }

    private MountQuidamortem()
    {
    }

    public static boolean contains(WorldPoint point)
    {
        if (point == null || point.getPlane() != 0)
        {
            return false;
        }

        if (point.getRegionID() == REGION_ID)
        {
            return true;
        }

        return point.getX() >= 1216 && point.getX() <= 1300
                && point.getY() >= 3520 && point.getY() <= 3600;
    }

    public static boolean isAtBank(WorldPoint point)
    {
        return point != null && point.distanceTo(BANK_FRONT_TILE) <= 3;
    }

    public static WorldPoint randomReturnAnchor()
    {
        WorldPoint[] choices = {REF_RETURN, REF_SOUTH, REF_EAST, REF_WEST};
        return choices[ThreadLocalRandom.current().nextInt(choices.length)];
    }
}
