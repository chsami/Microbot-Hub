package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Collection;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

/** Scope the recorded bank exit and its recovery to the Sticky Swamp mushtree. */
public final class StickySwampExit
{
    static final WorldPoint BANK_APPROACH = new WorldPoint(3765, 3898, 0);
    /** Mirrors the graph's Sticky Swamp bank journey for the complete-route planner. */
    private static volatile boolean bankJourney;
    private StickySwampExit() { }

    static void setBankJourney(boolean active) { bankJourney = active; }
    public static boolean bankJourney() { return bankJourney; }

    /** The zip line belongs only to the Sticky Swamp bank journey; other routes must not select it. */
    public static boolean ziplineAllowed(Transport source) { return !zipline(source) || bankJourney; }

    static boolean origin(WorldPoint point)
    {
        return new WorldPoint(3677, 3755, 0).equals(point)
            || new WorldPoint(3676, 3755, 0).equals(point)
            || new WorldPoint(3676, 3756, 0).equals(point);
    }

    static boolean nearby(WorldPoint point)
    {
        return point != null && point.getPlane() == 0
            && point.getX() >= 3674 && point.getX() <= 3685
            && point.getY() >= 3752 && point.getY() <= 3759;
    }

    static boolean recoveryArea(WorldPoint point)
    {
        return nearby(point) || point != null && point.getPlane() == 1
            && point.getX() >= 3762 && point.getX() <= 3766
            && point.getY() >= 3878 && point.getY() <= 3884;
    }

    static boolean replaces(Transport source)
    {
        return source.getType() == TransportType.TRANSPORT && origin(source.getOrigin())
            && source.getObjectId() == 30924 && "Use".equalsIgnoreCase(source.getAction())
            && "Magic Mushtree".equalsIgnoreCase(source.getName());
    }

    static boolean bankTarget(WorldPoint target)
    {
        return BankLocation.FOSSIL_ISLAND.getWorldPoint().equals(target)
            || BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint().equals(target)
            || BANK_APPROACH.equals(target);
    }

    static boolean zipline(Transport source)
    {
        return source.getType() == TransportType.AGILITY_SHORTCUT && source.getObjectId() == 57667
            && new WorldPoint(3765, 3883, 1).equals(source.getOrigin())
            && new WorldPoint(3765, 3898, 0).equals(source.getDestination())
            && "Teeth-grip".equalsIgnoreCase(source.getAction()) && "Zip line".equalsIgnoreCase(source.getName());
    }

    /** Eligible landings come from the existing carried/worn teleport providers. */
    static WorldPoint teleportBank(Collection<WorldPoint> landings, Collection<WorldPoint> banks)
    {
        WorldPoint best = null;
        int distance = 25;
        for (WorldPoint landing : landings)
        {
            if (landing == null) continue;
            for (WorldPoint bank : banks)
            {
                if (bank == null || bank.getPlane() != landing.getPlane()) continue;
                int remaining = landing.distanceTo2D(bank);
                if (remaining < distance)
                {
                    distance = remaining;
                    best = bank;
                }
            }
        }
        return best;
    }
}
