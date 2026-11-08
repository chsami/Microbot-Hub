package net.runelite.client.plugins.microbot.fourtifysgemmining;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.navigation.Walking;

/** Integration boundary; the shared walker adapter has its own lifecycle tests. */
final class FakeWalking extends Walking
{
    final List<WorldPoint> destinations = new ArrayList<>();
    final TestWorld world;
    boolean active;
    boolean fail;
    boolean cancelling;
    String cancellationFailure;
    int cancellations;
    Runnable afterCancel = () -> { };
    private WorldPoint target;

    FakeWalking(TestWorld world, ClientThread thread)
    {
        super(world.client, thread);
        this.world = world;
    }

    @Override
    public State walkTo(WorldPoint destination)
    {
        if (fail)
        {
            return State.FAILED;
        }
        if (world.position.equals(destination))
        {
            active = false;
            return State.ARRIVED;
        }
        if (!active || !destination.equals(target))
        {
            destinations.add(destination);
            target = destination;
        }
        active = true;
        return State.WALKING;
    }

    @Override
    public boolean isActive()
    {
        return active;
    }

    @Override
    public boolean isStopping()
    {
        return cancelling;
    }

    @Override
    public String getCancellationFailure()
    {
        return cancellationFailure;
    }

    @Override
    public void cancel()
    {
        active = false;
        target = null;
        cancellations++;
        Runnable callback = afterCancel;
        afterCancel = () -> { };
        callback.run();
    }

    @Override
    public String getFailureReason()
    {
        return "Microbot could not reach the destination";
    }
}
