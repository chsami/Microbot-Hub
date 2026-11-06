package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.client.plugins.microbot.fourtifysbarrows.config.TravelMethod;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

public final class TravelTask implements FourtifysTask
{
    private enum Phase
    {
        CONTROLS, ARRIVAL, LOCAL_WALK
    }

    private static final int CONTROL_TICKS = 24;
    private static final int ARRIVAL_TICKS = 90;
    private static final int LOCAL_WALK_TICKS = 300;
    private static final int MAX_ATTEMPTS = 3;
    private static final int CLOSE_RETRY_TICKS = 8;
    private final BarrowsActions actions;
    private TravelMethod method;
    private Phase phase = Phase.CONTROLS;
    private String status = "Travelling to Barrows";
    private int attempts;
    private int controlsDeadline = -1;
    private int arrivalDeadline;
    private int localWalkDeadline;
    private int generation;
    private boolean queued;
    private int queuedTick;
    private boolean failed;
    private int closeAttempts;
    private int nextClose;

    public TravelTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        TravelMethod selected = actions.config().travelMethod();
        if (method != selected)
        {
            reset();
            method = selected;
        }
        if (actions.bankPending() || actions.inside() || actions.atBarrowsSurface())
        {
            reset();
            return false;
        }
        return actions.ready() && !failed;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        if (!isValid())
        {
            return;
        }
        update();
        actions.status(status);
    }

    public void suspend()
    {
        if (phase == Phase.ARRIVAL)
        {
            arrivalDeadline = actions.tick() + ARRIVAL_TICKS;
        }
        if (phase == Phase.LOCAL_WALK)
        {
            localWalkDeadline = actions.tick() + LOCAL_WALK_TICKS;
        }
        if (controlsDeadline >= 0)
        {
            controlsDeadline = actions.tick() + CONTROL_TICKS;
        }
    }

    private void update()
    {
        if (phase == Phase.ARRIVAL)
        {
            if (actions.atTravelArrival(method))
            {
                observeArrival();
            }
            else if (actions.tick() < arrivalDeadline)
            {
                status = "Waiting for " + method + " arrival";
                return;
            }
            else if (attempts >= MAX_ATTEMPTS)
            {
                stop("Used " + method + " three times without arriving; check it, then enable the plugin again");
                return;
            }
            else
            {
                phase = Phase.CONTROLS;
                controlsDeadline = -1;
                queued = false;
            }
        }
        if (phase == Phase.CONTROLS)
        {
            // The cooldown outlasts a trip, so waiting at the bank beats stopping every other trip.
            long cooldown = method == TravelMethod.SHADES_MINIGAME ? actions.shadesCooldown() : 0;
            if (cooldown > 0)
            {
                status = "Shades teleport ready in " + cooldown + " min";
                controlsDeadline = -1;
                return;
            }
            // A start at the Ferox bank often finds it still open; close it rather than stopping.
            if (actions.bankingOpen())
            {
                closeBank();
                return;
            }
            // The game can still count the bank as open for a tick after its window closes.
            if (closeAttempts > 0 && !actions.bankCloseObserved() && actions.tick() < nextClose)
            {
                status = "Closing the bank";
                return;
            }
            closeAttempts = 0;
            String problem = actions.travelProblem(method);
            if (problem != null)
            {
                stop(problem);
                return;
            }
            if (actions.atTravelArrival(method))
            {
                observeArrival();
            }
        }
        if (phase == Phase.LOCAL_WALK)
        {
            if (actions.tick() >= localWalkDeadline)
            {
                stop("Did not reach Barrows after the selected teleport; walk to Barrows, then enable the plugin again");
                return;
            }
            status = "Walking from " + method + " to Barrows";
            actions.walk(Brother.DHAROK.getMound(), "Barrows mounds");
            return;
        }
        if (controlsDeadline < 0)
        {
            controlsDeadline = actions.tick() + CONTROL_TICKS;
        }
        if (actions.tick() >= controlsDeadline)
        {
            stop("Could not use " + method + "; check its requirements under Teleport to Barrows, then enable the plugin again");
            return;
        }
        status = "Using " + method;
        if (!queued || actions.tick() != queuedTick)
        {
            int requestGeneration = ++generation;
            TravelMethod requested = method;
            boolean accepted = actions.requestTravel(requested, () -> started(requested, requestGeneration));
            if (accepted && phase == Phase.CONTROLS)
            {
                queued = true;
                queuedTick = actions.tick();
            }
        }
    }

    private void closeBank()
    {
        status = "Closing the bank";
        if (actions.tick() < nextClose)
        {
            return;
        }
        if (closeAttempts >= MAX_ATTEMPTS)
        {
            stop("The bank did not close; close it, then enable the plugin again");
            return;
        }
        closeAttempts++;
        nextClose = actions.tick() + CLOSE_RETRY_TICKS;
        actions.closeBank(() -> { });
    }

    private void observeArrival()
    {
        phase = Phase.LOCAL_WALK;
        localWalkDeadline = actions.tick() + LOCAL_WALK_TICKS;
        generation++;
        queued = false;
    }

    private void started(TravelMethod requested, int requestGeneration)
    {
        if (generation != requestGeneration || phase != Phase.CONTROLS || method != requested
            || actions.config().travelMethod() != requested || failed
            || actions.bankPending() || actions.inside() || actions.atBarrowsSurface())
        {
            return;
        }
        attempts++;
        phase = Phase.ARRIVAL;
        queued = false;
        controlsDeadline = -1;
        arrivalDeadline = actions.tick() + ARRIVAL_TICKS;
    }

    private void reset()
    {
        generation++;
        phase = Phase.CONTROLS;
        attempts = 0;
        controlsDeadline = -1;
        arrivalDeadline = 0;
        localWalkDeadline = 0;
        queued = false;
        failed = false;
        closeAttempts = 0;
        nextClose = 0;
    }

    private void stop(String reason)
    {
        generation++;
        failed = true;
        status = reason;
        actions.fail(reason);
    }
}
