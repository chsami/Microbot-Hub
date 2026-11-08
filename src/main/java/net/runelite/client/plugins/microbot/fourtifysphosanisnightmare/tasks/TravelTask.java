package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.function.IntSupplier;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/**
 * Leaves with the Ring of dueling, and travels in with Drakan's medallion: it is worn for its Slepe teleport,
 * which lands in the Sisterhood Sanctuary, then the walk to the Pool of Nightmares, its drink and the
 * confirmation enter her dream. Each step waits for the observed position, dialogue or instance.
 */
public final class TravelTask implements FourtifysTask
{
    static final int RETRY_TICKS = 6;
    static final int STALL_TICKS = 30;
    private static final int POOL_REACH = 5;
    private final NightmareActions actions;
    private final TripState trip;
    private final Walking walking;
    private final IntSupplier tick;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private WorldPoint lastPosition;
    private int lastMoveTick;
    private TripPhase lastPhase;
    private String status = "Travelling";

    public TravelTask(NightmareActions actions, TripState trip, Walking walking, IntSupplier tick)
    {
        this.actions = actions;
        this.trip = trip;
        this.walking = walking;
        this.tick = tick;
    }

    @Override
    public boolean isValid()
    {
        TripPhase phase = trip.getPhase();
        return phase == TripPhase.LEAVING || phase == TripPhase.TRAVELLING
            || phase == TripPhase.FIGHTING && !actions.inInstance();
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
        if (trip.getPhase() != lastPhase)
        {
            lastPhase = trip.getPhase();
            lastMoveTick = now;
            requestedAt = Integer.MIN_VALUE / 2;
        }
        if (trip.getPhase() == TripPhase.LEAVING)
        {
            leave(now, position);
        }
        else if (trip.getPhase() == TripPhase.FIGHTING)
        {
            // Left the dream without a teleport, such as through its barrier.
            trip.setPhase(TripPhase.TRAVELLING);
        }
        else
        {
            travelIn(now, position);
        }
    }

    private void leave(int now, WorldPoint position)
    {
        walking.cancel();
        if (DreamData.atFerox(position))
        {
            if (trip.getStopMessage() != null)
            {
                actions.fail(trip.getStopMessage());
                return;
            }
            trip.arrivedAtBank();
            status = "Arrived at Ferox Enclave";
            return;
        }
        status = "Teleporting to Ferox Enclave: " + trip.getLeaveReason();
        if (actions.duelingRing() <= 0)
        {
            actions.fail("No Ring of dueling to leave; teleport out yourself, then restart the plugin");
            return;
        }
        if (retryDue(now))
        {
            actions.ringTeleport(() -> requestedAt = now);
        }
    }

    private void travelIn(int now, WorldPoint position)
    {
        if (actions.inInstance())
        {
            walking.cancel();
            actions.overheadCamera();
            trip.setPhase(TripPhase.FIGHTING);
            status = "Entered the dream";
            return;
        }
        if (now - lastMoveTick > STALL_TICKS)
        {
            actions.fail("Could not reach the dream; walk to Ferox Enclave or the Sisterhood Sanctuary, then restart the plugin");
            return;
        }
        Runnable observed = () -> requestedAt = now;
        if (DreamData.atFerox(position))
        {
            teleport(now, observed);
        }
        else if (DreamData.inSanctuary(position))
        {
            enterDream(now, position, observed);
        }
        else
        {
            status = "Waiting for the teleport";
        }
    }

    private void teleport(int now, Runnable observed)
    {
        if (actions.varbit(DreamData.SLEPE_TELEPORT_VARBIT) == 0)
        {
            actions.fail("Use a Slepey tablet on Drakan's medallion to unlock its Slepe teleport, then restart the plugin");
            return;
        }
        if (!retryDue(now))
        {
            return;
        }
        if (!actions.wearing(DreamData.MEDALLION))
        {
            if (actions.count(DreamData.MEDALLION) == 0)
            {
                actions.fail("No Drakan's medallion carried; carry it, then restart the plugin");
                return;
            }
            status = "Wearing Drakan's medallion";
            actions.equip(DreamData.MEDALLION, observed);
            return;
        }
        status = "Teleporting to Slepe";
        actions.equipment(DreamData.MEDALLION, "Slepe", observed);
    }

    private void enterDream(int now, WorldPoint position, Runnable observed)
    {
        // A conversation moves on without the player moving, such as Sister Senga's long first-drink talk.
        Runnable talked = () ->
        {
            requestedAt = now;
            lastMoveTick = now;
        };
        if (actions.dialogueOption("Yes.", talked))
        {
            status = "Entering the dream";
            return;
        }
        if (actions.dialogueContinue(talked))
        {
            status = "Entering the dream";
            return;
        }
        if (position.distanceTo(DreamData.POOL) > POOL_REACH)
        {
            status = "Walking to the Pool of Nightmares";
            if (walking.walkTo(DreamData.POOL) == Walking.State.FAILED)
            {
                actions.fail("Could not walk to the Pool of Nightmares; walk there yourself, then restart the plugin");
            }
            return;
        }
        walking.cancel();
        TileObject pool = actions.object(DreamData.POOL_OBJECTS);
        if (pool != null && retryDue(now))
        {
            status = "Drinking from the Pool of Nightmares";
            actions.object(pool, "Drink-from", observed);
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

    public void reset()
    {
        requestedAt = Integer.MIN_VALUE / 2;
        lastPosition = null;
        lastPhase = null;
        lastMoveTick = tick.getAsInt();
    }
}
