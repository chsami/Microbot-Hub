package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope;

/**
 * Manual movement's Javelin sidestep. The player is steering with their own mouse, so a Natural mouse walk would
 * travel against it and be dropped when the pointer left its target. The pointer jumps to the tile instead and
 * clicks there in one gesture on the arena's queue; prayer still outranks it and a stale walk is still cancelled.
 */
public final class ArenaSnapWalk implements ArenaInputScope.ClickHandler
{
    @Override public void click(Point point, boolean rightClick, NewMenuEntry entry)
    {
        if (rightClick || entry == null || entry.getType() != net.runelite.api.MenuAction.WALK)
        { ArenaInputDiagnostics.event("GESTURE_DROPPED", "SNAP_WALK_ONLY"); return; }
        ArenaMenuEntry guarded = ArenaMenuEntry.guarded(entry);
        Runnable gesture = ArenaInputScope.capture(() ->
        {
            try { ArenaMenuInput.snapClick(point, guarded); }
            catch (MouseMotionScope.Cancelled stale)
            {
                if (Microbot.targetMenu == guarded) Microbot.targetMenu = null;
                ArenaInputDiagnostics.event("GESTURE_CANCELLED", "OWNER_INVALID_BEFORE_PRESS");
            }
        });
        if (!ArenaInputScope.scheduleGesture(gesture)) ArenaInputDiagnostics.event("GESTURE_DROPPED", "OWNER_QUEUE_UNAVAILABLE");
    }
}
