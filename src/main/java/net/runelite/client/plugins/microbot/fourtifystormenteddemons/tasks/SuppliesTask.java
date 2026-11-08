package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.function.IntSupplier;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/** Eats the configured food and drinks prayer restores at their percentages, outside the fire bomb window. */
public final class SuppliesTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 3;
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final DemonFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Eating";

    public SuppliesTask(DemonActions actions, TormentedDemonsConfig config, DemonFight fight, TripState trip, IntSupplier tick)
    {
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
    }

    public boolean needsFood()
    {
        return actions.hitpoints() < actions.maxHitpoints()
            && DemonActions.atOrBelow(actions.hitpoints(), config.eatAt(), actions.maxHitpoints());
    }

    public boolean needsPrayer()
    {
        return actions.prayerPoints() < actions.maxPrayer()
            && DemonActions.atOrBelow(actions.prayerPoints(), config.drinkAt(), actions.maxPrayer());
    }

    /** The lowest-dose restore carried, or -1. */
    public int restore()
    {
        for (int id : TempleData.PRAYER_RESTORES)
        {
            if (actions.count(id) > 0)
            {
                return id;
            }
        }
        return -1;
    }

    @Override
    public boolean isValid()
    {
        int now = tick.getAsInt();
        return trip.getPhase() != TripPhase.BANKING && !fight.lockedOut(now) && now - requestedAt >= OBSERVE_TICKS
            && (needsFood() && actions.count(config.foodId()) > 0 || needsPrayer() && restore() > 0);
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        int now = tick.getAsInt();
        if (needsFood() && actions.count(config.foodId()) > 0)
        {
            status = "Eating";
            actions.inventory(config.foodId(), "Eat", () -> requestedAt = now);
            return;
        }
        int restore = restore();
        if (needsPrayer() && restore > 0)
        {
            status = "Drinking a prayer restore";
            actions.inventory(restore, "Drink", () -> requestedAt = now);
        }
    }
}
