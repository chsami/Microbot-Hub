package net.runelite.client.plugins.microbot.fourtifysgemmining.util.lifecycle;

import net.runelite.client.callback.ClientThread;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/** Each plugin owns a separate lifecycle; generation checks reject stale callbacks. */
public final class SessionLifecycle
{
    private volatile boolean enabled;
    private volatile boolean started;
    private volatile int generation;

    public int enable()
    {
        int current = ++generation;
        started = false;
        enabled = true;
        return current;
    }

    public int disable()
    {
        enabled = false;
        return ++generation;
    }

    public boolean isCurrentStart(int expected)
    {
        return enabled && generation == expected && !started;
    }

    public boolean isCurrentStop(int expected)
    {
        return !enabled && generation == expected;
    }

    public boolean isCurrent(int expected)
    {
        return enabled && generation == expected;
    }

    public boolean canDispatch(Client client)
    {
        return enabled && started && client.getGameState() == GameState.LOGGED_IN;
    }

    /** The Microbot pause control and Break Handler breaks set the pause flag; the player's own input counts too. */
    public static boolean scriptsPaused()
    {
        return Microbot.pauseAllScripts.get() || InputArbiter.isHuman();
    }

    public int generation()
    {
        return generation;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public boolean isStarted()
    {
        return started;
    }

    public void setStarted(boolean started)
    {
        this.started = started;
    }

    public void scheduleStart(ClientThread thread, Runnable start)
    {
        int current = enable();
        thread.invokeLater(() ->
        {
            if (isCurrentStart(current))
            {
                start.run();
            }
        });
    }

    public void scheduleStop(ClientThread thread, Runnable stop)
    {
        int current = disable();
        thread.invokeLater(() ->
        {
            if (isCurrentStop(current))
            {
                stop.run();
            }
        });
    }
}
