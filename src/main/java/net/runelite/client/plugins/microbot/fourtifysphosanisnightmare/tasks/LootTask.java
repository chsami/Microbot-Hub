package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.function.IntSupplier;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.LootRule;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene.GroundDrop;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/** Picks up worthwhile drops in the dream once she is down. */
public final class LootTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 3;
    private static final int WALK_TICKS = 10;
    private static final int STILL_TICKS = 2;
    private final NightmareActions actions;
    private final NightmareFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private LootRule rule;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private WorldPoint lastPosition;
    private int lastMoveTick = Integer.MIN_VALUE / 2;
    private String status = "Looting";

    public LootTask(NightmareActions actions, NightmareFight fight, TripState trip, IntSupplier tick, LootRule rule)
    {
        this.actions = actions;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
        this.rule = rule;
    }

    public void setRule(LootRule rule)
    {
        this.rule = rule;
    }

    /** The next drop worth taking that fits, or null. */
    public GroundDrop next()
    {
        for (GroundDrop drop : actions.groundDrops())
        {
            TileItem item = drop.getItem();
            int id = item.getId();
            boolean fits = actions.freeSlots() > 0 || actions.count(id) > 0 && actions.stackable(id);
            if (fits && rule.take(actions.itemName(id), actions.price(id), item.getQuantity()))
            {
                return drop;
            }
        }
        return null;
    }

    @Override
    public boolean isValid()
    {
        int now = tick.getAsInt();
        WorldPoint position = actions.position();
        if (position != null && !position.equals(lastPosition))
        {
            if (lastPosition != null)
            {
                lastMoveTick = now;
            }
            lastPosition = position;
        }
        // A pickup walks to the drop first; it is only sent again once the player stands still.
        boolean walkingToDrop = now - requestedAt < WALK_TICKS && now - lastMoveTick < STILL_TICKS;
        return trip.getPhase() == TripPhase.FIGHTING && actions.inInstance() && !fight.isFighting()
            && now - requestedAt >= OBSERVE_TICKS && !walkingToDrop && next() != null;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        GroundDrop drop = next();
        if (drop == null)
        {
            return;
        }
        status = "Looting " + actions.itemName(drop.getItem().getId());
        int now = tick.getAsInt();
        actions.pickup(drop, () -> requestedAt = now);
    }
}
