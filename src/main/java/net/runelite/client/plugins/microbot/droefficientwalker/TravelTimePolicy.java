package net.runelite.client.plugins.microbot.droefficientwalker;

/** Compare complete travel estimates. Monetary amounts are execution requirements, not scores. */
public final class TravelTimePolicy
{
    private TravelTimePolicy() { }

    public static boolean preferAlternative(int currentTime, int alternativeTime)
    {
        return alternativeTime >= 0 && alternativeTime < Integer.MAX_VALUE
            && currentTime >= 0 && alternativeTime < currentTime;
    }
}
