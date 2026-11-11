package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Doses;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuppliesTaskTest
{
    @Test
    public void aDivineDoseWaitsUntilItsHitpointsCostLeavesThePlayerAboveTheEatingPoint()
    {
        assertFalse(SuppliesTask.affordable("Divine super combat potion", 15, 50), "15 Hitpoints and nothing to eat");
        assertFalse(SuppliesTask.affordable("Divine super combat potion", 60, 50));
        assertTrue(SuppliesTask.affordable("Divine super combat potion", 61, 50));
        assertTrue(SuppliesTask.affordable("Super combat potion", 15, 50), "only divine potions cost Hitpoints");
    }

    @Test
    public void herBlastIsEatenForWithAHitToSpareOnceNoSleepwalkerIsComing()
    {
        TestWorld world = new TestWorld();
        world.instance = true;
        MutableConfig config = new MutableConfig();
        config.eat = 30;
        world.inventory = TestWorld.items(config.food, 3);
        world.hitpoints = 50;
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.BLAST);
        for (int index = 1; index <= 4; index++)
        {
            fight.observeSleepwalker(index, index == 1, false);
        }
        TestWorld.NpcTarget walker = world.npc(5);
        walker.id = DreamData.SLEEPWALKER;
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null);
        SuppliesTask task = new SuppliesTask(actions, config, fight, new TripState(), () -> 10);
        assertFalse(task.needsFood(), "eating would end the attack on the sleepwalker still coming");
        walker.animation = DreamData.SLEEPWALKER_ABSORBED;
        assertTrue(task.needsFood(), "50 Hitpoints would leave 9 after her 41 blast");
        world.hitpoints = 57;
        assertFalse(task.needsFood(), "57 keeps 16 after it");
    }

    @Test
    public void theEatingPointWaitsForHerSleepwalkersUnlessOtherHitsCouldLeaveTooLittle()
    {
        TestWorld world = new TestWorld();
        world.instance = true;
        MutableConfig config = new MutableConfig();
        config.eat = 70;
        world.inventory = TestWorld.items(config.food, 3);
        world.hitpoints = 58;
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.BLAST);
        for (int index = 1; index <= 4; index++)
        {
            fight.observeSleepwalker(index, false, false);
        }
        TestWorld.NpcTarget walker = world.npc(5);
        walker.id = DreamData.SLEEPWALKER;
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null);
        SuppliesTask task = new SuppliesTask(actions, config, fight, new TripState(), () -> 10);
        TestWorld.NpcTarget parasite = world.npc(2);
        parasite.id = DreamData.STRONG_PARASITE;
        assertFalse(task.needsFood(), "58 keeps more than her 5 blast, 15 to spare and a parasite's 22");
        TestWorld.NpcTarget husk = world.npc(3);
        husk.id = DreamData.MAGIC_HUSK;
        assertTrue(task.needsFood(), "a husk's 26 as well would leave too little");
        husk.id = -1;
        world.activeVarbits.add(DreamData.PARASITE_VARBIT);
        assertTrue(task.needsFood(), "a parasite growing unweakened bursts for about 90");
        fight.setWeakened(true);
        assertFalse(task.needsFood(), "a weakened one bursts for at most 10");
        world.hitpoints = 51;
        assertTrue(task.needsFood(), "51 is no more than 5, 15, 22 and 10");
        world.hitpoints = 58;
        walker.animation = DreamData.SLEEPWALKER_ABSORBED;
        assertTrue(task.needsFood(), "no sleepwalker is coming");
        walker.animation = -1;
        fight.observeBoss(Stage.DESPERATION);
        assertTrue(task.needsFood(), "her final phase sends sleepwalkers without end");
    }

    @Test
    public void theRestoreWaitsForHerSleepwalkersWhileThePointsLast()
    {
        TestWorld world = new TestWorld();
        world.instance = true;
        MutableConfig config = new MutableConfig();
        world.inventory = TestWorld.items(Doses.SUPER_RESTORE.get(2), 1);
        world.prayerPoints = config.drink;
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.BLAST);
        fight.observeSleepwalker(1, false, false);
        TestWorld.NpcTarget walker = world.npc(5);
        walker.id = DreamData.SLEEPWALKER;
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null);
        SuppliesTask task = new SuppliesTask(actions, config, fight, new TripState(), () -> 10);
        assertFalse(task.needsRestore(), "a drink would end the attack on the sleepwalker still coming");
        world.prayerPoints = SuppliesTask.PRAYER_HOLD_FLOOR;
        assertTrue(task.needsRestore(), "too few points to outlast them");
        world.prayerPoints = config.drink;
        walker.animation = DreamData.SLEEPWALKER_ABSORBED;
        assertTrue(task.needsRestore(), "no sleepwalker is coming");
        walker.animation = -1;
        fight.observeBoss(Stage.DESPERATION);
        assertTrue(task.needsRestore(), "her final phase sends sleepwalkers without end");
    }

    @Test
    public void theCombatPotionWaitsWhileHerSleepwalkersAreUp()
    {
        TestWorld world = new TestWorld();
        world.instance = true;
        world.inventory = TestWorld.items(12695, 1);
        MutableConfig config = new MutableConfig();
        config.combatPotion = 12695;
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.TOTEMS);
        TripState trip = new TripState();
        trip.setPhase(TripPhase.FIGHTING);
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null)
        {
            @Override
            public String itemName(int id)
            {
                return id == 12695 ? "Super combat potion(4)" : null;
            }
        };
        SuppliesTask task = new SuppliesTask(actions, config, fight, trip, () -> 10);
        assertTrue(task.needsBoost());
        fight.observeSleepwalker(1, false, false);
        assertFalse(task.needsBoost(), "a drink would end the attack on a sleepwalker");
    }

    @Test
    public void aDivineDoseThatWouldLeaveTooFewHitpointsGoesDownWithFood()
    {
        TestWorld world = new TestWorld();
        world.instance = true;
        MutableConfig config = new MutableConfig();
        config.combatPotion = 23685;
        config.eat = 70;
        world.hitpoints = 79;
        world.inventory = TestWorld.items(23685, 1);
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.TOTEMS);
        TripState trip = new TripState();
        trip.setPhase(TripPhase.FIGHTING);
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null)
        {
            @Override
            public String itemName(int id)
            {
                return id == 23685 ? "Divine super combat potion(4)" : null;
            }
        };
        SuppliesTask task = new SuppliesTask(actions, config, fight, trip, () -> 10);
        assertFalse(task.needsBoost(), "79 less the dose's 10 is under the eating point, with nothing to eat");
        world.inventory = TestWorld.items(23685, 1, config.food, 2);
        assertTrue(task.needsBoost(), "the boost does not lapse while there is food");
        assertTrue(task.eatsForBoost(), "food goes down on the same tick");
        world.hitpoints = 81;
        assertTrue(task.needsBoost());
        assertFalse(task.eatsForBoost(), "81 keeps 71 after the dose");
    }
}
