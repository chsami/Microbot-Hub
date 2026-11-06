package net.runelite.client.plugins.microbot.fourtifysgemmining.util.lifecycle;

import java.lang.reflect.Method;
import net.runelite.client.callback.ClientThread;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/** Each plugin owns a separate lifecycle; generation checks reject stale callbacks. */
public final class SessionLifecycle
{
    private static final Method HEADER_PAUSE = pauseCheck("net.runelite.client.plugins.microbot.ScriptPauseController");
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

    public static boolean scriptsPaused()
    {
        return pauseReason() != null;
    }

    /** Why scripts are paused, or null: the header pause button, a Break Handler break or the player's own input. */
    public static String pauseReason()
    {
        // The Break Handler sets this flag; the client header pause button sets the pause controller.
        if (Microbot.pauseAllScripts.get() || headerPaused(HEADER_PAUSE))
        {
            return "Paused by Microbot (pause button or break). Resumes when unpaused";
        }
        return InputArbiter.isHuman() ? "Paused while you use the mouse or keyboard" : null;
    }

    /** The pause controller's check, or null on clients from before 2.6.53, which have no header pause button. */
    static Method pauseCheck(String controller)
    {
        try
        {
            return Class.forName(controller).getMethod("isPaused");
        }
        catch (ReflectiveOperationException | LinkageError missing)
        {
            return null;
        }
    }

    static boolean headerPaused(Method check)
    {
        try
        {
            return check != null && Boolean.TRUE.equals(check.invoke(null));
        }
        catch (ReflectiveOperationException failure)
        {
            return false;
        }
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
