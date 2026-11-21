package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.GameState;

/** A bounded loading grace for callers waiting on an accepted journey. */
public final class TravelSessionState
{
    private long loadingSince = -1;
    public boolean canContinue(GameState state, boolean ownsJourney, long now)
    {
        if (state == GameState.LOGGED_IN) { loadingSince = -1; return true; }
        if (state != GameState.LOADING || !ownsJourney) { loadingSince = -1; return false; }
        if (loadingSince < 0) loadingSince = now;
        return now - loadingSince < 15000;
    }
}
