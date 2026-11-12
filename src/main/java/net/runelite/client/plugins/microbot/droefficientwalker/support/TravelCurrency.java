package net.runelite.client.plugins.microbot.droefficientwalker.support;

/** Currency eligibility for preparation searches and carried-only execution. */
public final class TravelCurrency
{
    private TravelCurrency() { }

    public static boolean hasAmount(int carried, int banked, int required, boolean allowBank)
    {
        long available = Math.max(0, carried);
        if (allowBank) available += Math.max(0, banked);
        return available >= Math.max(0, required);
    }
}
