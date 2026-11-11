package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NextKillTaskTest
{
    @Test
    public void theTripEndsOnlyOnceHerDropsAreTaken()
    {
        TestWorld world = new TestWorld();
        world.instance = true;
        MutableConfig config = new MutableConfig();
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.ASLEEP);
        TripState trip = new TripState();
        trip.setPhase(TripPhase.FIGHTING);
        int[] tick = {0};
        boolean[] dropsLeft = {true};
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null);
        NextKillTask task = new NextKillTask(actions, config, fight, trip, new SuppliesTask(actions, config, fight, trip,
            () -> tick[0]), () -> tick[0], () -> dropsLeft[0]);
        task.observe();
        tick[0] = NextKillTask.SETTLE_TICKS;
        assertFalse(task.isValid(), "a drop is still to take");
        dropsLeft[0] = false;
        assertTrue(task.isValid());
    }
}
