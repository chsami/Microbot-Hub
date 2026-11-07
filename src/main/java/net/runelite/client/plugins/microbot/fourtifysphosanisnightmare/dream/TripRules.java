package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

/** Thresholds that decide whether another kill starts, and where a session starts. */
public final class TripRules
{
    private TripRules()
    {
    }

    /** Returns why the trip should end before the next kill, or null while the supplies last. */
    public static String bankReason(int food, int minimumFood, int restoreDoses, int minimumRestores,
                                    int cureDoses, int minimumCures)
    {
        if (food < minimumFood)
        {
            return "Low on food";
        }
        if (restoreDoses < minimumRestores)
        {
            return "Low on restores";
        }
        if (cureDoses < minimumCures)
        {
            return "Low on parasite cures";
        }
        return null;
    }

    /** The phase a session starts in, from where the player stands. */
    public static TripPhase startPhase(boolean atFerox, boolean inSanctuary, boolean inDream)
    {
        if (inDream)
        {
            return TripPhase.FIGHTING;
        }
        if (inSanctuary)
        {
            return TripPhase.TRAVELLING;
        }
        return atFerox ? TripPhase.BANKING : null;
    }
}
