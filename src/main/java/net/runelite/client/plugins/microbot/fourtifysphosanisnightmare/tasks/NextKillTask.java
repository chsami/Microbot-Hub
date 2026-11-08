package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripRules;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/** After the loot, disturbs her for another kill while the supplies last, otherwise ends the trip. */
public final class NextKillTask implements FourtifysTask
{
    /** Her drops appear as she falls asleep; looting gets these ticks to see them first. */
    static final int SETTLE_TICKS = 2;
    private static final int RETRY_TICKS = 6;
    private final NightmareActions actions;
    private final PhosanisNightmareConfig config;
    private final NightmareFight fight;
    private final TripState trip;
    private final SuppliesTask supplies;
    private final BooleanSupplier dropsLeft;
    private final IntSupplier tick;
    private int asleepSince = -1;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Disturbing her";

    public NextKillTask(NightmareActions actions, PhosanisNightmareConfig config, NightmareFight fight, TripState trip,
        SuppliesTask supplies, IntSupplier tick, BooleanSupplier dropsLeft)
    {
        this.dropsLeft = dropsLeft;
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.trip = trip;
        this.supplies = supplies;
        this.tick = tick;
    }

    /** Counts the ticks she has slept, read each tick before any task runs. */
    public void observe()
    {
        boolean asleep = actions.inInstance() && fight.getStage() == Stage.ASLEEP;
        if (!asleep)
        {
            asleepSince = -1;
        }
        else if (asleepSince < 0)
        {
            asleepSince = tick.getAsInt();
        }
    }

    /** Why the trip ends before the next kill, or null while the supplies last. */
    public String bankReason()
    {
        return TripRules.bankReason(supplies.food(), config.nextKillFood(), supplies.restoreDoses(), config.nextKillRestores(),
            supplies.cureDoses(), config.nextKillCures());
    }

    @Override
    public boolean isValid()
    {
        int now = tick.getAsInt();
        // A pickup waits a few ticks for its result; the trip ends only once nothing worth taking is left.
        return trip.getPhase() == TripPhase.FIGHTING && asleepSince >= 0 && now - asleepSince >= SETTLE_TICKS
            && now - requestedAt >= RETRY_TICKS && !dropsLeft.getAsBoolean();
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        String reason = bankReason();
        if (reason != null)
        {
            status = reason;
            trip.leave(reason);
            return;
        }
        NPC boss = actions.boss();
        if (boss == null)
        {
            status = "Waiting for her";
            return;
        }
        status = "Disturbing her";
        int now = tick.getAsInt();
        actions.npc(boss, "Disturb", () -> requestedAt = now);
    }

    public void reset()
    {
        asleepSince = -1;
        requestedAt = Integer.MIN_VALUE / 2;
    }
}
