package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.function.IntSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/** Teleports to Ferox Enclave with the Ring of dueling when Hitpoints fall to the emergency percentage. */
public final class EmergencyTask implements FourtifysTask
{
    /** A jewellery teleport lands three ticks after the click; one more tick lets the arrival show. */
    private static final int RETRY_TICKS = 6;
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final TripState trip;
    private final IntSupplier tick;
    private int requestedAt = Integer.MIN_VALUE / 2;

    public EmergencyTask(DemonActions actions, TormentedDemonsConfig config, TripState trip, IntSupplier tick)
    {
        this.actions = actions;
        this.config = config;
        this.trip = trip;
        this.tick = tick;
    }

    @Override
    public boolean isValid()
    {
        WorldPoint position = actions.position();
        int hitpoints = actions.hitpoints();
        return hitpoints > 0 && position != null && !TempleData.atFerox(position) && trip.getPhase() != TripPhase.BANKING
            && DemonActions.atOrBelow(hitpoints, config.emergencyAt(), actions.maxHitpoints());
    }

    @Override
    public String getStatus()
    {
        return "Emergency teleport to Ferox Enclave";
    }

    @Override
    public void execute()
    {
        if (actions.duelingRing() <= 0)
        {
            actions.fail("Hitpoints are low and no Ring of dueling is worn or carried; teleport out yourself, then restart the plugin");
            return;
        }
        trip.leave("Low Hitpoints");
        int now = tick.getAsInt();
        if (now - requestedAt >= RETRY_TICKS)
        {
            actions.ringTeleport(() -> requestedAt = now);
        }
    }
}
