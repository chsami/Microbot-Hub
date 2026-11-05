package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.Set;
import java.util.function.IntSupplier;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/**
 * Leaves with the Ring of dueling, and travels in with a Guthixian temple teleport: two wall climbs, the
 * temple opening, then a walk to the selected room. Each step waits for the observed position or plane.
 * A fight that drifts out of the room is finished there; the walk back waits until no demon is engaged.
 */
public final class TravelTask implements FourtifysTask
{
    static final int RETRY_TICKS = 6;
    static final int STALL_TICKS = 30;
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final TripState trip;
    private final Walking walking;
    private final IntSupplier tick;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private WorldPoint lastPosition;
    private int lastMoveTick;
    private TripPhase lastPhase;
    private int lastStage = -1;
    private String status = "Travelling";

    public TravelTask(DemonActions actions, TormentedDemonsConfig config, TripState trip, Walking walking, IntSupplier tick)
    {
        this.actions = actions;
        this.config = config;
        this.trip = trip;
        this.walking = walking;
        this.tick = tick;
    }

    @Override
    public boolean isValid()
    {
        TripPhase phase = trip.getPhase();
        return phase == TripPhase.LEAVING || phase == TripPhase.TRAVELLING
            || phase == TripPhase.FIGHTING && trip.getTarget() == null && !config.room().contains(actions.position());
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
        WorldPoint position = actions.position();
        if (position != null && !position.equals(lastPosition))
        {
            lastPosition = position;
            lastMoveTick = now;
        }
        int stage = stage(position);
        if (trip.getPhase() != lastPhase || stage != lastStage)
        {
            // A new step is sent at once; walking to an object or climbing it is not a reason to resend.
            lastPhase = trip.getPhase();
            lastStage = stage;
            lastMoveTick = now;
            requestedAt = Integer.MIN_VALUE / 2;
        }
        switch (trip.getPhase())
        {
            case LEAVING:
                leave(now, position);
                break;
            case TRAVELLING:
                travelIn(now, position);
                break;
            default:
                walkToRoom(position);
                break;
        }
    }

    private void leave(int now, WorldPoint position)
    {
        walking.cancel();
        if (TempleData.atFerox(position))
        {
            trip.arrivedAtBank();
            status = "Arrived at Ferox Enclave";
            return;
        }
        status = "Teleporting to Ferox Enclave: " + trip.getLeaveReason();
        if (actions.duelingRing() <= 0)
        {
            actions.fail("No Ring of dueling to leave the temple");
            return;
        }
        if (retryDue(now))
        {
            actions.ringTeleport(() -> requestedAt = now);
        }
    }

    private void travelIn(int now, WorldPoint position)
    {
        if (TempleData.inTemple(position))
        {
            trip.setPhase(TripPhase.FIGHTING);
            status = "Entered the temple";
            return;
        }
        if (now - lastMoveTick > STALL_TICKS)
        {
            actions.fail("Travel to the temple stopped at " + position);
            return;
        }
        if (!retryDue(now))
        {
            return;
        }
        Player player = actions.player();
        if (player != null && player.getAnimation() != -1)
        {
            // A climb sent while the last one still plays is ignored, so the next step waits for it to end.
            return;
        }
        Runnable observed = () -> requestedAt = now;
        if (TempleData.atFerox(position))
        {
            if (actions.count(TempleData.TEMPLE_TELEPORT) == 0)
            {
                actions.fail("No Guthixian temple teleports carried");
                return;
            }
            status = "Reading a Guthixian temple teleport";
            actions.inventory(TempleData.TEMPLE_TELEPORT, "Teleport", observed);
        }
        else if (TempleData.atScrollArrival(position))
        {
            climb(TempleData.FIRST_WALL, "Climb-up", "Climbing the first wall", observed);
        }
        else if (TempleData.onChasmLedge(position, 1))
        {
            climb(TempleData.SECOND_WALL, "Climb-up", "Climbing the second wall", observed);
        }
        else if (TempleData.onChasmLedge(position, 2))
        {
            TileObject opening = actions.object(Set.of(TempleData.TEMPLE_DOOR, TempleData.TEMPLE_OPENING));
            status = "Climbing through the opening";
            if (opening != null)
            {
                actions.object(opening, "Climb-through", observed);
            }
        }
        else
        {
            status = "Waiting for the teleport";
        }
    }

    /** The first request of a step goes at once; a resend waits six ticks and for the player to stand still. */
    private boolean retryDue(int now)
    {
        if (requestedAt == Integer.MIN_VALUE / 2)
        {
            return true;
        }
        Player player = actions.player();
        boolean still = lastMoveTick < now && (player == null || player.getAnimation() == -1);
        return now - requestedAt >= RETRY_TICKS && still;
    }

    /** The travel step a position belongs to: Ferox, the scroll arrival, each ledge, the temple or elsewhere. */
    private static int stage(WorldPoint position)
    {
        if (TempleData.atFerox(position))
        {
            return 0;
        }
        if (TempleData.atScrollArrival(position))
        {
            return 1;
        }
        if (TempleData.onChasmLedge(position, 1))
        {
            return 2;
        }
        if (TempleData.onChasmLedge(position, 2))
        {
            return 3;
        }
        return TempleData.inTemple(position) ? 4 : 5;
    }

    private void climb(int id, String action, String label, Runnable observed)
    {
        TileObject wall = actions.object(Set.of(id));
        status = label;
        if (wall != null)
        {
            actions.object(wall, action, observed);
        }
    }

    private void walkToRoom(WorldPoint position)
    {
        if (!TempleData.inTemple(position))
        {
            actions.fail("Left the temple; disable and restart from Ferox Enclave");
            return;
        }
        status = "Walking to " + config.room().getDisplayName();
        Walking.State state = walking.walkTo(config.room().getCenter());
        if (state == Walking.State.FAILED)
        {
            actions.fail(walking.getFailureReason());
        }
    }

    public void reset()
    {
        requestedAt = Integer.MIN_VALUE / 2;
        lastPosition = null;
        lastPhase = null;
        lastStage = -1;
        lastMoveTick = tick.getAsInt();
    }
}
