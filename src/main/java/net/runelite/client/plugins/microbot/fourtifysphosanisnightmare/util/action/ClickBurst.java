package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntPredicate;

/**
 * Sends a batch of item clicks from the client thread, each once the game has taken the click before it. The
 * mouse moves on its own thread, so a click sent sooner could replace the menu entry of one not yet taken.
 */
final class ClickBurst
{
    /** Client ticks to wait for a click to be taken before the rest of the batch is dropped. */
    static final int CLICK_TIMEOUT_TICKS = 25;
    private final Deque<Integer> items = new ArrayDeque<>();
    private IntPredicate send = item -> false;
    private int waitingFor = -1;
    private int waited;

    /** Queues the items in order; {@code send} clicks one and says whether it did. */
    synchronized void start(Iterable<Integer> batch, IntPredicate send)
    {
        items.clear();
        batch.forEach(items::add);
        this.send = send;
        waitingFor = -1;
    }

    synchronized boolean isActive()
    {
        return waitingFor >= 0 || !items.isEmpty();
    }

    /** A click was sent and the game has not taken it yet. */
    synchronized boolean isClicking()
    {
        return waitingFor >= 0;
    }

    /** Runs on each client tick: sends the next click once the one before it is taken. */
    void advance()
    {
        int next;
        IntPredicate sender;
        synchronized (this)
        {
            if (waitingFor >= 0)
            {
                if (++waited >= CLICK_TIMEOUT_TICKS)
                {
                    // The click never reached the game, such as when the player took the mouse.
                    clear();
                }
                return;
            }
            Integer item = items.poll();
            if (item == null)
            {
                return;
            }
            next = item;
            sender = send;
            waitingFor = next;
            waited = 0;
        }
        if (!sender.test(next))
        {
            // Nothing was clicked, so nothing will be taken.
            clicked(next);
        }
    }

    /** The game took a click on the item. */
    synchronized void clicked(int item)
    {
        if (item == waitingFor)
        {
            waitingFor = -1;
        }
    }

    /** Drops the clicks not yet sent; one already sent is still waited for. */
    synchronized void drop()
    {
        items.clear();
    }

    synchronized void clear()
    {
        items.clear();
        waitingFor = -1;
    }
}
