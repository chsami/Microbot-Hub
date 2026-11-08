package net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action;

import java.util.ArrayDeque;
import java.util.Deque;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

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

    public boolean offerAtPlayerPosition(Client client, Runnable action)
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return false;
        }
        WorldPoint origin = player.getWorldLocation();
        return offer(() ->
        {
            Player current = client.getLocalPlayer();
            if (client.getGameState() == GameState.LOGGED_IN && current != null && !current.isDead()
                    && current.getWorldView() == client.getTopLevelWorldView()
                    && origin.equals(current.getWorldLocation()))
            {
                action.run();
            }
        });
    }
}
