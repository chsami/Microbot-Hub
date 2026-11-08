package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.BombStep;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/** Steps off a fire bomb tile after the bind ends and before the bombs land. */
public final class DodgeTask implements FourtifysTask
{
    private static final int RANGED_REACH = 7;
    private final DemonActions actions;
    private final DemonFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private final BiFunction<WorldPoint, Integer, Map<WorldPoint, Integer>> reachable;
    private final Supplier<WorldPoint> roomCenter;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Dodging fire bomb";

    public DodgeTask(DemonActions actions, DemonFight fight, TripState trip, IntSupplier tick,
        BiFunction<WorldPoint, Integer, Map<WorldPoint, Integer>> reachable, Supplier<WorldPoint> roomCenter)
    {
        this.actions = actions;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
        this.reachable = reachable;
        this.roomCenter = roomCenter;
    }

    @Override
    public boolean isValid()
    {
        WorldPoint position = actions.position();
        int now = tick.getAsInt();
        return position != null && requestedAt != now && fight.stepDue(now) && fight.onBomb(position, now);
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        WorldPoint position = actions.position();
        NPC target = trip.getTarget();
        WorldArea demon = target == null ? null : target.getWorldArea();
        int reach = trip.getStyle() == CombatStyle.MELEE ? 1 : RANGED_REACH;
        WorldPoint tile = BombStep.choose(position, fight.getBombs(), demon, reachable.apply(position, 2), reach,
            roomCenter.get());
        if (tile == null)
        {
            status = "No safe tile from the fire bomb";
            return;
        }
        status = "Dodging fire bomb";
        int now = tick.getAsInt();
        actions.step(tile, () -> requestedAt = now);
    }
}
