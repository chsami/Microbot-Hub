package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

/** Thresholds that end a trip. */
public final class TripRules
{
    private TripRules()
    {
    }

    /** Returns why the trip should end, or null while the supplies last. */
    public static String leaveReason(boolean needsFood, int food, boolean needsPrayer, int restores, int freeSlots)
    {
        if (food == 0 && needsFood)
        {
            return "Out of food";
        }
        if (restores == 0 && needsPrayer)
        {
            return "Out of prayer restores";
        }
        if (freeSlots == 0 && food == 0)
        {
            return "Inventory full";
        }
        return null;
    }

    /** The phase a session starts in, from where the player stands. */
    public static TripPhase startPhase(boolean atFerox, boolean inTemple, boolean onRoute, String leaveReason)
    {
        if (inTemple)
        {
            return leaveReason == null ? TripPhase.FIGHTING : TripPhase.LEAVING;
        }
        if (onRoute)
        {
            return TripPhase.TRAVELLING;
        }
        return atFerox ? TripPhase.BANKING : null;
    }
}
