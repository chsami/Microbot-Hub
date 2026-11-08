package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import java.util.Set;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Loadout;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GearTaskTest
{
    private final TestWorld world = new TestWorld();
    private final TripState trip = new TripState();
    private final GearTask gear = new GearTask(new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null),
        trip, () -> 0, Set.of(), Set.of(), Map.of(Loadout.SLEEPWALKER, 33716, Loadout.PARASITE, 34027));

    @Test
    public void aRangedSleepwalkerWeaponUsesItsRapidStyle()
    {
        world.styleButtons = new Object[][] {{0, 1, 3}, {"(Accurate)<br>(Light)", "(Rapid)<br>(Light)", "(Longrange)<br>(Light)"}};
        assertEquals(1, gear.style(Loadout.SLEEPWALKER));
        world.styleButtons = new Object[][] {{0, 1, 2, 3}, {"(Accurate)<br>(Slash)", "(Aggressive)<br>(Slash)",
            "(Aggressive)<br>(Crush)", "(Defensive)<br>(Slash)"}};
        assertEquals(-1, gear.style(Loadout.SLEEPWALKER), "a melee weapon keeps its style");
        assertEquals(2, gear.style(Loadout.PARASITE));
    }

    @Test
    public void theFirstSleepwalkerAttackDoesNotWaitForTheRapidStyle()
    {
        world.styleButtons = new Object[][] {{0, 1, 3}, {"(Accurate)<br>(Light)", "(Rapid)<br>(Light)", "(Longrange)<br>(Light)"}};
        world.equipment = new Item[] {new Item(33716, 50)};
        trip.setLoadout(Loadout.SLEEPWALKER);
        assertTrue(gear.ready(), "the stake is worn");
        assertFalse(gear.done(), "the rapid style is still to choose");
        trip.setLoadout(Loadout.PARASITE);
        world.equipment = new Item[] {new Item(34027, 1)};
        world.styleButtons = new Object[][] {{0, 1, 2, 3}, {"(Accurate)<br>(Slash)", "(Aggressive)<br>(Slash)",
            "(Aggressive)<br>(Crush)", "(Defensive)<br>(Slash)"}};
        assertFalse(gear.ready(), "a parasite waits for the crush style");
    }

    @Test
    public void sleepwalkersSwapOnlyTheStakeOrElseTheMagicSet()
    {
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null);
        Set<Integer> magic = Set.of(21018);
        assertEquals(Set.of(33716), new GearTask(actions, trip, () -> 0, Set.of(), magic,
            Map.of(Loadout.SLEEPWALKER, 33716)).wanted(Loadout.SLEEPWALKER));
        assertEquals(magic, new GearTask(actions, trip, () -> 0, Set.of(), magic, Map.of()).wanted(Loadout.SLEEPWALKER));
    }

    @Test
    public void aHeldPlayerWieldsOnlyTheStaffOnItsLongrangeStyle()
    {
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null);
        Set<Integer> magic = Set.of(27275);
        GearTask held = new GearTask(actions, trip, () -> 0, Set.of(), magic, Map.of(Loadout.SLEEPWALKER, 33716));
        assertEquals(magic, held.wanted(Loadout.LONGRANGE), "the staff, not the stake");
        world.styleButtons = new Object[][] {{0, 1, 3}, {"(Accurate)<br>(Magic)", "(Accurate)<br>(Magic)", "(Longrange)<br>(Magic)"}};
        assertEquals(3, held.style(Loadout.LONGRANGE));
        world.equipment = new Item[] {new Item(27275, 1)};
        trip.setLoadout(Loadout.LONGRANGE);
        assertFalse(held.ready(), "the first cast waits for the longrange style");
    }

    @Test
    public void aHuskSwapsOnlyItsWeaponAndSendsTheCrushStyleWithItOnceLearned()
    {
        List<String> sent = new ArrayList<>();
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> null)
        {
            @Override
            public boolean wieldWithStyle(int id, int style, Runnable callback)
            {
                sent.add("wield " + id + " with style " + style);
                return true;
            }

            @Override
            public boolean equipAll(List<Integer> ids, Runnable callback)
            {
                sent.add("equip " + ids);
                return true;
            }
        };
        GearTask husk = new GearTask(actions, trip, () -> 0, Set.of(24419), Set.of(21018), Map.of(Loadout.HUSK, 34027));
        assertEquals(Set.of(34027), husk.wanted(Loadout.HUSK), "a crush hit on a husk is always its max hit");
        world.inventory = TestWorld.items(34027, 1);
        trip.setLoadout(Loadout.HUSK);
        husk.execute();
        assertEquals(List.of("equip [34027]"), sent, "its crush button is not known yet");
        world.equipment = TestWorld.items(34027, 1);
        world.styleButtons = new Object[][] {{0, 1, 2, 3}, {"(Accurate)<br>(Slash)", "(Aggressive)<br>(Slash)",
            "(Aggressive)<br>(Crush)", "(Defensive)<br>(Slash)"}};
        husk.observe();
        world.equipment = TestWorld.items(21018, 1);
        sent.clear();
        husk.execute();
        assertEquals(List.of("wield 34027 with style 2"), sent);
    }
}
