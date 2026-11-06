package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.util.ArrayDeque;
import java.util.Deque;

/** One plugin session's bounded queue of requests for the current snapshot. */
public final class ActionQueue
{
    private final Deque<Runnable> queue = new ArrayDeque<>();

    public boolean offer(Runnable action)
    {
        if (queue.size() >= 50)
        {
            return false;
        }
        queue.addLast(action);
        return true;
    }

    public void clear()
    {
        queue.clear();
    }

    public void dispatch()
    {
        Runnable action = queue.pollFirst();
        if (action != null)
        {
            action.run();
        }
    }
    public static boolean withinWindow(int cycle, int tickCycle)
    {
        return cycle < tickCycle + 29;
    }

}
