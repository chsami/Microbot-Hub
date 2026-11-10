package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope;

/**
 * "Use invokes": the command's menu action is sent to the client directly, with no pointer travel and
 * no canvas press. Walking is not routed here: CommandRunner sends movement.tile through the Natural
 * mouse, because scene tile picking needs a real canvas click. It keeps the arena's own queue, so prayer
 * still outranks equipment and a command that went stale or was superseded is cancelled before it fires.
 * Only a command that carries a non-WALK menu entry can be invoked; anything else is dropped with a reason.
 */
public final class ArenaInvoke implements ArenaInputScope.ClickHandler
{
    static boolean invokable(NewMenuEntry entry)
    {
        return entry != null && entry.getType() != null && entry.getType() != net.runelite.api.MenuAction.WALK;
    }

    @Override public void click(Point point, boolean rightClick, NewMenuEntry entry)
    {
        if (!invokable(entry)) { drop(entry == null || entry.getType() == null ? "NO_MENU_ENTRY" : "WALK_USES_NATURAL_MOUSE"); return; }
        Runnable gesture = ArenaInputScope.capture(() ->
        {
            try { fire(entry); }
            catch (MouseMotionScope.Cancelled ignored) { }
        });
        if (!ArenaInputScope.scheduleGesture(gesture)) drop("OWNER_QUEUE_UNAVAILABLE");
    }

    /**
     * A gear switch: every piece is invoked in one gesture so all land in the same tick (the game wears several
     * items per tick), 8-11 ms apart so each reaches the client as its own action. A cancelled owner, a human
     * takeover or a pause stops the rest; the engine re-sends whatever is still not worn next tick.
     */
    @Override public void burst(java.util.List<Point> points, java.util.List<NewMenuEntry> entries)
    {
        java.util.List<NewMenuEntry> valid = new java.util.ArrayList<>();
        for (NewMenuEntry entry : entries) if (invokable(entry)) valid.add(entry); else drop("NO_MENU_ENTRY");
        if (valid.isEmpty()) return;
        Runnable gesture = ArenaInputScope.capture(() ->
        {
            try
            {
                for (int i = 0; i < valid.size(); i++)
                {
                    if (i > 0) java.util.concurrent.locks.LockSupport.parkNanos(
                        java.util.concurrent.ThreadLocalRandom.current().nextLong(BURST_MIN_GAP_NANOS, BURST_MAX_GAP_NANOS + 1));
                    fire(valid.get(i));
                }
            }
            catch (MouseMotionScope.Cancelled ignored) { }
        });
        if (!ArenaInputScope.scheduleGesture(gesture)) drop("OWNER_QUEUE_UNAVAILABLE");
    }
    static final long BURST_MIN_GAP_NANOS = 8_000_000L, BURST_MAX_GAP_NANOS = 11_000_000L;

    private static void fire(NewMenuEntry entry)
    {
        // Same last-moment checks as a click (owner still valid, no human input, prayer still needed),
        // on the input worker so the client thread never waits.
        MouseMotionScope.beforeClick();
        var trace = ArenaInputDiagnostics.current();
        boolean sent = ArenaInputScope.onClientThread(() ->
        {
            MouseMotionScope.checkpoint();
            var client = Microbot.getClient();
            if (client == null || entry instanceof ArenaMenuEntry && !((ArenaMenuEntry) entry).liveValid()) return false;
            if (trace != null) trace.invoke(false, entry.getType(), entry.getParam0(), entry.getParam1(), entry.getIdentifier(), entry.getItemId());
            client.menuAction(entry.getParam0(), entry.getParam1(), entry.getType(), entry.getIdentifier(),
                entry.getItemId(), entry.getOption(), entry.getTarget());
            if (trace != null) trace.invoke(true, entry.getType(), entry.getParam0(), entry.getParam1(), entry.getIdentifier(), entry.getItemId());
            return true;
        }).orElse(false);
        if (!sent) { drop("CLIENT_UNAVAILABLE"); return; }
        // INVOKE_FIRED records client submission; it is not a physical canvas press.
    }

    private static void drop(String reason) { ArenaInputDiagnostics.event("GESTURE_DROPPED", reason); }
}
