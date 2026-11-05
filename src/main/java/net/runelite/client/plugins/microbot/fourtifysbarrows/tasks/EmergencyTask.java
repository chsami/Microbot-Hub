package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

public final class EmergencyTask implements FourtifysTask
{
    private static final int CONTROL_TICKS = 24;
    private static final int OBSERVATION_TICKS = 8;
    private static final int MAX_ATTEMPTS = 3;
    private final BarrowsActions actions;
    private boolean armed;
    private boolean pending;
    private boolean escaping;
    private int attempts;
    private int deadline;
    private int controlsDeadline;
    private WorldPoint origin;
    private int prayerDeadline = -1;
    private String status = "Emergency recovery";

    public EmergencyTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        if (!actions.ready())
        {
            return false;
        }
        if (armed)
        {
            return true;
        }
        if (!actions.emergencyHealth())
        {
            escaping = false;
            return false;
        }
        // The ring only helps at Barrows; elsewhere the bank trip restores Hitpoints.
        return actions.hp() > 0 && !escaping && actions.atBarrows();
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
        if (!armed)
        {
            armed = true;
            attempts = 0;
            controlsDeadline = actions.tick() + CONTROL_TICKS;
        }
        if (pending)
        {
            WorldPoint current = actions.position();
            if (origin != null && current != null && (origin.getPlane() != current.getPlane()
                || origin.distanceTo(current) > 8))
            {
                // The plugin stops on arrival, so a protection prayer left on would keep draining.
                Prayer protection = actions.activeProtection();
                if (prayerDeadline < 0)
                {
                    prayerDeadline = actions.tick() + OBSERVATION_TICKS;
                }
                if (protection != null && actions.tick() < prayerDeadline)
                {
                    status = "Turning off protection prayer after emergency teleport";
                    actions.status(status);
                    actions.protect(protection, false, () -> { });
                    return;
                }
                prayerDeadline = -1;
                status = "Emergency teleport completed; check character before restarting";
                armed = false;
                pending = false;
                actions.fail(status);
                return;
            }
            if (actions.tick() < deadline)
            {
                status = "Waiting for emergency teleport arrival";
                actions.status(status);
                return;
            }
            pending = false;
            if (attempts >= MAX_ATTEMPTS)
            {
                escape("Emergency teleport failed three times; escaping to bank");
                return;
            }
            controlsDeadline = actions.tick() + CONTROL_TICKS;
        }
        if (!actions.hasBankTravelRing())
        {
            escape("Ring of dueling unavailable; escaping to bank");
            return;
        }
        if (actions.bankingOpen())
        {
            escape("Finish bank resupply before emergency recovery");
            return;
        }
        if (actions.tick() >= controlsDeadline)
        {
            escape("Ring of dueling action unavailable; escaping to bank");
            return;
        }
        status = "Use Ring of dueling to Ferox Enclave";
        actions.status(status);
        actions.requestEmergencyTravel(this::dispatched);
    }

    private void dispatched()
    {
        origin = actions.position();
        pending = true;
        attempts++;
        deadline = actions.tick() + OBSERVATION_TICKS;
        status = "Waiting for emergency teleport arrival";
    }

    private void escape(String reason)
    {
        armed = false;
        pending = false;
        escaping = true;
        status = reason;
        actions.status(reason);
        actions.requestBank();
    }
}
