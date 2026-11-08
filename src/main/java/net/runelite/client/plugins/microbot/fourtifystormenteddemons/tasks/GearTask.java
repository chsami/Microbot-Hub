package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.GearChoice;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/** Wears the configured style the engaged demon does not protect against, one item per request. */
public final class GearTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 2;
    private final DemonActions actions;
    private final DemonFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private final Map<CombatStyle, Set<Integer>> sets;
    private int requestedAt = Integer.MIN_VALUE / 2;

    public GearTask(DemonActions actions, DemonFight fight, TripState trip, IntSupplier tick, Map<CombatStyle, Set<Integer>> sets)
    {
        this.actions = actions;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
        this.sets = sets;
    }

    /** Chooses the style for this tick from the engaged demon's overhead. */
    public void observe()
    {
        NPC target = trip.getTarget();
        CombatStyle protectedStyle = target == null ? null : CombatStyle.fromOverhead(target.getOverheadSpriteIds());
        trip.setStyle(GearChoice.against(trip.getStyle(), protectedStyle, sets));
    }

    /** The first item of the chosen style that is carried but not worn, or -1. */
    int missing()
    {
        Set<Integer> ids = trip.getStyle() == null ? null : sets.get(trip.getStyle());
        if (ids == null)
        {
            return -1;
        }
        for (int id : ids)
        {
            if (!actions.wearing(id))
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
        return trip.getPhase() == TripPhase.FIGHTING && !fight.lockedOut(now) && now - requestedAt >= OBSERVE_TICKS
            && missing() > 0;
    }

    @Override
    public String getStatus()
    {
        return "Switching to " + trip.getStyle() + " gear";
    }

    @Override
    public void execute()
    {
        int id = missing();
        if (actions.count(id) == 0)
        {
            actions.fail("Missing " + actions.describe(id) + ": carry it or remove it from your equipment IDs, then restart the plugin");
            return;
        }
        int now = tick.getAsInt();
        Runnable sent = () ->
        {
            requestedAt = now;
            trip.equipmentChanged(now);
        };
        if (!actions.inventory(id, "Wield", sent))
        {
            actions.inventory(id, "Wear", sent);
        }
    }
}
