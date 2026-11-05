package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.function.IntSupplier;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.LootRule;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.scene.GroundDrop;

/** Picks up worthwhile drops in the selected room while no demon is engaged. */
public final class LootTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 3;
    private static final int WALK_TICKS = 10;
    private static final int STILL_TICKS = 2;
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final DemonFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private final LootRule rule;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private WorldPoint lastPosition;
    private int lastMoveTick = Integer.MIN_VALUE / 2;
    private String status = "Looting";

    public LootTask(DemonActions actions, TormentedDemonsConfig config, DemonFight fight, TripState trip, IntSupplier tick, LootRule rule)
    {
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
        this.rule = rule;
    }

    GroundDrop next()
    {
        for (GroundDrop drop : actions.groundDrops())
        {
            if (!config.room().contains(drop.getTile().getWorldLocation()))
            {
                continue;
            }
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
        // Also while the next demon attacks: in a room of aggressive demons there is no quiet moment.
        return trip.getPhase() == TripPhase.FIGHTING && !fight.lockedOut(now)
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
