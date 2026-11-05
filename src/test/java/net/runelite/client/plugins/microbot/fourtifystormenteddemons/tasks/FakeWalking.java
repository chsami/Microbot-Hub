package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;

/** A walker that records destinations and reports a chosen state. */
final class FakeWalking extends Walking
{
    final List<WorldPoint> destinations = new ArrayList<>();
    State state = State.WALKING;
    int cancellations;

    FakeWalking(TestWorld world)
    {
        super(world.client, new TestWorld.QueuedThread());
    }

    @Override
    public State walkTo(WorldPoint destination)
    {
        destinations.add(destination);
        return state;
    }

    @Override
    public void cancel()
    {
        cancellations++;
    }

    @Override
    public String getFailureReason()
    {
        return "Walker could not reach the destination";
    }
}
