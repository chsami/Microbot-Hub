package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare;

import java.util.ArrayList;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Hazards;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Loadout;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.CombatTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.GearTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.TravelTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.navigation.Walking;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fight decisions on the fake client: which target, which gear and which supply comes first. */
public class TaskDecisionTest
{
    private static final int HALLOWFELL = 34027;
    private static final int MACE = 24417;
    private static final int DEFENDER = 22322;
    private static final int HELM = 24419;

    private static PluginTestFixture started() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        return fixture;
    }

    @Test
    public void aParasiteIsAttackedBeforeHer() throws Exception
    {
        PluginTestFixture fixture = started();
        TestWorld.NpcTarget parasite = fixture.world.npc(5);
        parasite.id = DreamData.WEAK_PARASITE;
        fixture.tick();
        assertEquals("Attacking the parasite", fixture.plugin.getStatus());
    }

    @Test
    public void aParasiteUnderHerWhileHerRiftsAreOutIsAttackedWithMagic() throws Exception
    {
        PluginTestFixture fixture = started();
        WorldPoint player = fixture.world.position;
        TestWorld.NpcTarget parasite = fixture.world.npcAt(player.dx(5).dy(2));
        parasite.id = DreamData.WEAK_PARASITE;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertEquals(Loadout.PARASITE, trip.getLoadout(), "without rifts the player can walk under her");
        fixture.world.graphic(DreamData.RIFT_GRAPHIC, -6);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertSame(parasite.npc, trip.getTarget());
        assertEquals(Loadout.MAGIC, trip.getLoadout(), "no safe tile beside it, so magic reaches it from where the player is");
    }

    @Test
    public void whileHerHazardsAreOutASafeTileInRangeOfASleepwalkerIsSteppedTo() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "33716";
        fixture.world.equipment = new Item[] {new Item(33716, 50)};
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget walker = fixture.world.npc(-12);
        walker.id = DreamData.SLEEPWALKER;
        fixture.world.npc(-20).id = DreamData.SLEEPWALKER;
        fixture.world.graphic(DreamData.RIFT_GRAPHIC, -1);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertSame(walker.npc, ((TripState) fixture.read("trip")).getTarget());
        assertEquals("Moving into range", fixture.plugin.getStatus(), "a sleepwalker out of range is not waited for");
    }

    @Test
    public void duringHerBlastTheSleepwalkersComeBeforeAParasite() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        fixture.world.npc(2).id = DreamData.WEAK_PARASITE;
        TestWorld.NpcTarget walker = fixture.world.npc(6);
        walker.id = DreamData.SLEEPWALKER;
        fixture.tick();
        assertSame(walker.npc, ((TripState) fixture.read("trip")).getTarget());
    }

    @Test
    public void withAllFourTotemsChargedTheSleepwalkerWeaponWaitsForHerBlast() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9421;
        int[] charged = {9436, 9439, 9442, 9445};
        TestWorld.NpcTarget[] totems = new TestWorld.NpcTarget[charged.length];
        for (int i = 0; i < charged.length; i++)
        {
            totems[i] = fixture.world.npc(8 + i);
            totems[i].id = charged[i];
        }
        fixture.world.npc(2).id = DreamData.WEAK_PARASITE;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertNull(trip.getTarget(), "a slow swing at the parasite would hold back the first throw");
        assertEquals(Loadout.SLEEPWALKER, trip.getLoadout());
        for (int i = 0; i < charged.length; i++)
        {
            totems[i].id = charged[i] - 2;
        }
        fixture.tick();
        assertNull(trip.getTarget(), "the totems leave their charged form as they fire, before her sleepwalkers rise");
        assertEquals(Loadout.SLEEPWALKER, trip.getLoadout());
        TestWorld.NpcTarget walker = fixture.world.npc(6);
        walker.id = DreamData.SLEEPWALKER;
        fixture.tick();
        assertSame(walker.npc, trip.getTarget(), "her sleepwalkers rise before she takes her blast form");
    }

    @Test
    public void herSleepwalkersAreAwaitedNearHerCentre() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "33716";
        fixture.world.equipment = new Item[] {new Item(33716, 50)};
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Moving to the middle for her sleepwalkers", fixture.plugin.getStatus(),
            "from her edge, a sleepwalker rising across the arena is out of targeting range");
        WorldPoint corner = fixture.boss.npc.getWorldLocation();
        fixture.world.position = corner.dx(2).dy(2);
        fixture.tick();
        fixture.tick();
        assertEquals("Moving to the middle for her sleepwalkers", fixture.plugin.getStatus(),
            "her centre is in line with two of the tiles they rise on");
        fixture.world.position = corner;
        fixture.tick();
        fixture.tick();
        assertEquals("Moving to the middle for her sleepwalkers", fixture.plugin.getStatus(),
            "from two off both ways, more of the ways they rise let one reach her");
        fixture.world.position = corner.dx(1);
        fixture.tick();
        fixture.tick();
        assertEquals("Moving to the middle for her sleepwalkers", fixture.plugin.getStatus(),
            "from one off one way and two the other, more of the ways they rise let one reach her");
        fixture.world.position = corner.dx(1).dy(1);
        fixture.tick();
        fixture.tick();
        assertEquals("Waiting for sleepwalkers", fixture.plugin.getStatus());
        fixture.world.position = corner.dx(-1);
        for (int step = 1; step <= 4; step++)
        {
            fixture.boss.location = corner.dx(-step).dy(-step);
            fixture.tick();
        }
        fixture.tick();
        assertEquals("Moving to the middle for her sleepwalkers", fixture.plugin.getStatus(),
            "she charges her blast at the arena's centre, wherever she walked to before");
    }

    @Test
    public void theSleepwalkerGoneForStaysTheTargetUntilItIsHit() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "33716";
        fixture.world.equipment = new Item[] {new Item(33716, 50)};
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        fixture.plugin.setTickDelay(0);
        List<TestWorld.NpcTarget> walkers = List.of(fixture.world.npc(-3), fixture.world.npc(-6), fixture.world.npc(-9));
        walkers.forEach(walker -> walker.id = DreamData.SLEEPWALKER);
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        NPC planned = trip.getTarget();
        TestWorld.NpcTarget other = walkers.stream().filter(walker -> walker.npc != planned).findFirst().orElseThrow();
        fixture.world.interacting = other.npc;
        fixture.tick();
        assertSame(other.npc, trip.getTarget(), "the player is on the way to it and has not attacked yet");
    }

    @Test
    public void herSleepwalkersAreTimedFromTheTickBeforeTheyAreSeen() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "33716";
        fixture.world.equipment = new Item[] {new Item(33716, 50)};
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        fixture.plugin.setTickDelay(0);
        WorldPoint corner = fixture.boss.npc.getWorldLocation();
        fixture.world.position = corner.dx(1);
        TestWorld.NpcTarget north = null;
        for (int[] offset : new int[][] {{-7, -4}, {-7, 8}, {-4, 11}})
        {
            TestWorld.NpcTarget walker = fixture.world.npcAt(corner.dx(offset[0]).dy(offset[1]));
            walker.id = DreamData.SLEEPWALKER;
            walker.animation = DreamData.SLEEPWALKER_RISE;
            north = offset[1] == 8 ? walker : north;
        }
        fixture.tick();
        assertSame(north.npc, ((TripState) fixture.read("trip")).getTarget(),
            "with a tick less to walk than they seem to have, the west one by her north corner comes first");
    }

    @Test
    public void sleepwalkersRisingBeforeHerBlastFormComeBeforeAParasite() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9421;
        fixture.world.npc(2).id = DreamData.WEAK_PARASITE;
        TestWorld.NpcTarget walker = fixture.world.npc(6);
        walker.id = DreamData.SLEEPWALKER;
        fixture.tick();
        assertSame(walker.npc, ((TripState) fixture.read("trip")).getTarget());
    }

    @Test
    public void theNextSleepwalkerIsTargetedOnceTheAttackOnTheNearestHasStarted() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget near = fixture.world.npc(-3);
        near.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget far = fixture.world.npc(-6);
        far.id = DreamData.SLEEPWALKER;
        fixture.world.interacting = near.npc;
        fixture.world.animation = 829;
        fixture.tick();
        assertSame(near.npc, ((TripState) fixture.read("trip")).getTarget(), "eating is not an attack");
        fixture.world.animation = 7617;
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget());
        fixture.world.animation = -1;
        fixture.world.interacting = null;
        for (int tick = 0; tick < 6; tick++)
        {
            fixture.tick();
        }
        assertSame(near.npc, ((TripState) fixture.read("trip")).getTarget(), "a sleepwalker still standing was not hit");
    }

    @Test
    public void theNextSleepwalkerIsAttackedOnTheTickTheThrowIsSeen() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "33716";
        fixture.world.equipment = new Item[] {new Item(33716, 50)};
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5, PluginTestFixture.STAFF, 1,
            PluginTestFixture.WHIP, 1);
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget near = fixture.world.npc(-3);
        near.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget far = fixture.world.npc(-6);
        far.id = DreamData.SLEEPWALKER;
        fixture.world.npc(-9).id = DreamData.SLEEPWALKER;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Attacking a sleepwalker", fixture.plugin.getStatus());
        fixture.world.interacting = near.npc;
        fixture.world.animation = 7617;
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget());
        assertEquals("Attacking a sleepwalker", fixture.plugin.getStatus(), "a stake throws every other tick");
    }

    @Test
    public void anAttackStillPlayingAfterASwitchDoesNotCountAsAStrikeOnTheNewTarget() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget near = fixture.world.npc(-3);
        near.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget far = fixture.world.npc(-6);
        far.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget last = fixture.world.npc(-9);
        last.id = DreamData.SLEEPWALKER;
        ((TripState) fixture.read("trip")).setLoadout(Loadout.SLEEPWALKER);
        fixture.world.interacting = near.npc;
        fixture.world.animation = 7617;
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget());
        fixture.world.interacting = far.npc;
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget(), "the throw at the first is still playing");
        fixture.tick();
        assertSame(last.npc, ((TripState) fixture.read("trip")).getTarget(), "the attack on the second has started");
    }

    @Test
    public void anAttackStartedOnTheTickOfASwitchCountsAsAStrikeAtOnce() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget near = fixture.world.npc(-3);
        near.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget far = fixture.world.npc(-6);
        far.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget last = fixture.world.npc(-9);
        last.id = DreamData.SLEEPWALKER;
        ((TripState) fixture.read("trip")).setLoadout(Loadout.SLEEPWALKER);
        fixture.world.interacting = near.npc;
        fixture.world.animation = 7617;
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget());
        fixture.world.interacting = far.npc;
        AnimationChanged throwing = new AnimationChanged();
        throwing.setActor(fixture.world.player);
        fixture.plugin.onAnimationChanged(throwing);
        fixture.tick();
        assertSame(last.npc, ((TripState) fixture.read("trip")).getTarget(), "the throw at the second went out on this tick");
    }

    @Test
    public void aBlockOutOfMeleeReachOfTheLastSleepwalkerIsNotAnAttackOnIt() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "22804";
        fixture.config.husk = "34027";
        fixture.world.equipment = TestWorld.items(34027, 1);
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5, PluginTestFixture.STAFF, 1,
            22804, 28);
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        fixture.plugin.setTickDelay(0);
        TestWorld.NpcTarget walker = fixture.world.npc(-3);
        walker.id = DreamData.SLEEPWALKER;
        TripState trip = (TripState) fixture.read("trip");
        trip.setLoadout(Loadout.HUSK);
        fixture.world.interacting = walker.npc;
        fixture.world.animation = 14471;
        fixture.tick();
        assertSame(walker.npc, trip.getTarget(), "Hallowfell's block three tiles short of it is not a hit");
        assertEquals(Loadout.HUSK, trip.getLoadout());
    }

    @Test
    public void inHerThirdPhaseHerSleepwalkersAreTakenInAnOrderForTheirLongerStand() throws Exception
    {
        // One by each pillar, from her south-west corner; the player waits one tile off her centre both ways.
        int[][] tiles = {{-7, -4}, {-7, 8}, {8, -7}, {11, 8}};
        assertEquals(0, firstSleepwalker(2, tiles), "her earlier phases' sleepwalkers walk after four ticks");
        assertEquals(3, firstSleepwalker(3, tiles), "her third phase's walk after six ticks, or seven");
    }

    @Test
    public void herThirdPhaseSleepwalkersAreOrderedForTheThrowingWeaponsReach() throws Exception
    {
        assertEquals(0, firstFromUnderHer(33716), "a stake throws from six tiles");
        assertEquals(2, firstFromUnderHer(22804), "a dragon knife throws from four");
    }

    private static int firstFromUnderHer(int weapon) throws Exception
    {
        int[][] tiles = {{-4, 11}, {11, 8}, {8, -7}, {-4, -7}};
        List<NPC> walkers = new ArrayList<>();
        PluginTestFixture fixture = risen(3, weapon, tiles, walkers);
        fixture.world.position = fixture.boss.npc.getWorldLocation().dx(1).dy(3);
        fixture.tick();
        return walkers.indexOf(((TripState) fixture.read("trip")).getTarget());
    }

    @Test
    public void aSleepwalkerOffTheTileItRoseOnIsWalking() throws Exception
    {
        int[][] tiles = {{-7, -4}, {-7, 8}, {11, -4}, {11, 8}};
        List<NPC> walkers = new ArrayList<>();
        PluginTestFixture fixture = risen(3, 33716, tiles, walkers);
        fixture.tick();
        WorldPoint corner = fixture.boss.npc.getWorldLocation();
        for (TestWorld.NpcTarget walker : fixture.world.npcs)
        {
            if (walker.id == DreamData.SLEEPWALKER)
            {
                // One step toward her, as each takes its first.
                int dx = walker.location.getX() < corner.getX() ? 1 : walker.location.getX() >= corner.getX() + 5 ? -1 : 0;
                int dy = walker.location.getY() < corner.getY() ? 1 : walker.location.getY() >= corner.getY() + 5 ? -1 : 0;
                walker.location = walker.location.dx(dx).dy(dy);
                walker.animation = -1;
            }
        }
        fixture.tick();
        assertEquals(0, walkers.indexOf(((TripState) fixture.read("trip")).getTarget()),
            "they are walking already, not still standing for their third phase's six ticks");
    }

    /** The sleepwalker attacked first in the phase, by its place in the tiles. */
    private static int firstSleepwalker(int phase, int[][] tiles) throws Exception
    {
        List<NPC> walkers = new ArrayList<>();
        PluginTestFixture fixture = risen(phase, 33716, tiles, walkers);
        fixture.tick();
        assertEquals(phase, ((NightmareFight) fixture.read("fight")).getPhase());
        return walkers.indexOf(((TripState) fixture.read("trip")).getTarget());
    }

    @Test
    public void afterHerLastBlastsSleepwalkersHerFinalPhaseSetGoesOnWithTheBar() throws Exception
    {
        PluginTestFixture fixture = afterTheSleepwalkers(3);
        TripState trip = (TripState) fixture.read("trip");
        assertNull(trip.getTarget());
        assertEquals(Loadout.SPEC, trip.getLoadout(), "her final phase comes next");
        // Item slots are unknown here, so the set counts as worn with the melee weapon worn too.
        fixture.world.equipment = TestWorld.items(13652, 1, PluginTestFixture.WHIP, 1);
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1);
        // On a tile where her sleepwalkers are awaited.
        fixture.world.position = fixture.boss.npc.getWorldLocation().dx(1).dy(1);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Turning on the special attack", fixture.plugin.getStatus());
    }

    @Test
    public void aParasiteOutAfterHerLastBlastsSleepwalkersDiesBeforeHerFinalPhaseSet() throws Exception
    {
        PluginTestFixture fixture = afterTheSleepwalkers(3);
        TestWorld.NpcTarget parasite = fixture.world.npc(2);
        parasite.id = DreamData.WEAK_PARASITE;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(parasite.npc, trip.getTarget(), "it would heal her through her final phase");
        assertEquals(Loadout.PARASITE, trip.getLoadout());
        fixture.world.npcs.remove(parasite);
        fixture.tick();
        assertEquals(Loadout.SPEC, trip.getLoadout());
    }

    @Test
    public void afterAnEarlierBlastsSleepwalkersTheirWeaponStays() throws Exception
    {
        PluginTestFixture fixture = afterTheSleepwalkers(2);
        assertEquals(Loadout.SLEEPWALKER, ((TripState) fixture.read("trip")).getLoadout(), "a shield phase comes next");
    }

    /** Her blast in the phase, with a special attack weapon set and its one sleepwalker attacked and gone. */
    private static PluginTestFixture afterTheSleepwalkers(int phase) throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.spec = "13652";
        fixture.config.redeem = false;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, 13652, 1);
        fixture.world.varps.put(VarPlayerID.SA_ENERGY, 1000);
        fixture.enableAndDrain();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        for (int blast = 1; blast < phase; blast++)
        {
            fight.observeBoss(Stage.TOTEMS);
            fight.observeBoss(Stage.BLAST);
            fight.observeBoss(Stage.SHIELD);
        }
        fight.observeBoss(Stage.TOTEMS);
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget walker = fixture.world.npc(-3);
        walker.id = DreamData.SLEEPWALKER;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(walker.npc, trip.getTarget());
        fixture.world.npcs.remove(walker);
        fixture.tick();
        assertEquals(phase, fight.getPhase());
        return fixture;
    }

    /** Her blast in the phase, with sleepwalkers rising on the tiles from her south-west corner. */
    private static PluginTestFixture risen(int phase, int weapon, int[][] tiles, List<NPC> walkers) throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = Integer.toString(weapon);
        fixture.world.equipment = new Item[] {new Item(weapon, 50)};
        fixture.enableAndDrain();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        for (int blast = 1; blast < phase; blast++)
        {
            fight.observeBoss(Stage.TOTEMS);
            fight.observeBoss(Stage.BLAST);
            fight.observeBoss(Stage.SHIELD);
        }
        fight.observeBoss(Stage.TOTEMS);
        fixture.boss.id = DreamData.BLAST;
        WorldPoint corner = fixture.boss.npc.getWorldLocation();
        fixture.world.position = corner.dx(3).dy(3);
        for (int[] tile : tiles)
        {
            TestWorld.NpcTarget walker = fixture.world.npcAt(corner.dx(tile[0]).dy(tile[1]));
            walker.id = DreamData.SLEEPWALKER;
            walker.animation = DreamData.SLEEPWALKER_RISE;
            walkers.add(walker.npc);
        }
        return fixture;
    }

    @Test
    public void anAttackOnASleepwalkerOutOfReachIsNotAStrike() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        ((TripState) fixture.read("trip")).setLoadout(Loadout.SLEEPWALKER);
        TestWorld.NpcTarget near = fixture.world.npc(3);
        near.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget far = fixture.world.npc(10);
        far.id = DreamData.SLEEPWALKER;
        fixture.world.interacting = near.npc;
        fixture.world.animation = 7617;
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget());
        fixture.world.interacting = far.npc;
        AnimationChanged throwing = new AnimationChanged();
        throwing.setActor(fixture.world.player);
        fixture.plugin.onAnimationChanged(throwing);
        fixture.tick();
        assertSame(far.npc, ((TripState) fixture.read("trip")).getTarget(),
            "the player runs to it with the throw at the first still playing");
    }

    @Test
    public void theLastSleepwalkerIsHitInMeleeWhenThatLandsSooner() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.sleepwalker = "33716";
        fixture.config.husk = "13576";
        fixture.world.equipment = new Item[] {new Item(33716, 50)};
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5, PluginTestFixture.STAFF, 1,
            13576, 1);
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget walker = fixture.world.npc(-2);
        walker.id = DreamData.SLEEPWALKER;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(walker.npc, trip.getTarget());
        assertSame(Loadout.HUSK, trip.getLoadout(), "a melee hit lands a tick or two before a throw");
        assertEquals("Attacking a sleepwalker", fixture.plugin.getStatus(), "the attack goes with the swap");
        fixture.world.equipment = new Item[] {new Item(13576, 1)};
        fixture.world.styleButtons = new Object[][] {{0, 1, 2, 3}, {"(Accurate)<br>(Stab)", "(Aggressive)<br>(Slash)",
            "(Aggressive)<br>(Crush)", "(Defensive)<br>(Stab)"}};
        fixture.tick();
        assertEquals("Attacking a sleepwalker", fixture.plugin.getStatus(), "any hit kills it, so no style is chosen first");
    }

    @Test
    public void theMissileHuskDiesBeforeTheMagicHusk() throws Exception
    {
        PluginTestFixture fixture = started();
        TestWorld.NpcTarget magic = fixture.world.npc(1);
        magic.id = DreamData.MAGIC_HUSK;
        TestWorld.NpcTarget ranged = fixture.world.npcAt(fixture.world.position.dx(-1));
        ranged.id = DreamData.RANGED_HUSK;
        fixture.tick();
        assertEquals("Attacking a husk", fixture.plugin.getStatus());
        assertSame(ranged.npc, ((TripState) fixture.read("trip")).getTarget());
    }

    @Test
    public void aHuskIsAttackedBeforeTheSwapWhileThePlayerStillAttacksHer() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.husk = "34027";
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, 34027, 1);
        fixture.enableAndDrain();
        TestWorld.NpcTarget husk = fixture.world.npc(1);
        husk.id = DreamData.RANGED_HUSK;
        fixture.world.interacting = fixture.boss.npc;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertSame(husk.npc, ((TripState) fixture.read("trip")).getTarget());
        assertEquals("Attacking a husk", fixture.plugin.getStatus(), "a swing at her would still spend the attack delay");
        fixture.world.interacting = husk.npc;
        fixture.tick();
        fixture.tick();
        assertEquals("Switching to husk gear", fixture.plugin.getStatus());
    }

    @Test
    public void aHuskUnderHerBesideAPlayerUnderHerTooIsCrushed() throws Exception
    {
        PluginTestFixture fixture = started();
        WorldPoint inside = fixture.boss.npc.getWorldLocation().dx(4);
        fixture.world.position = inside;
        TestWorld.NpcTarget husk = fixture.world.npcAt(inside.dx(-1));
        husk.id = DreamData.RANGED_HUSK;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(husk.npc, trip.getTarget());
        assertEquals(Loadout.HUSK, trip.getLoadout(), "both are under her, and the husk is beside the player");
        husk.location = inside.dx(-2);
        fixture.tick();
        assertEquals(Loadout.LONGRANGE, trip.getLoadout(), "two tiles away under her, melee cannot reach it");
    }

    @Test
    public void aHuskUnderHerComesAfterTheOtherAndIsCastAtWithTheStaff() throws Exception
    {
        PluginTestFixture fixture = started();
        TestWorld.NpcTarget magic = fixture.world.npcAt(fixture.world.position.dx(-1));
        magic.id = DreamData.MAGIC_HUSK;
        TestWorld.NpcTarget ranged = fixture.world.npc(4);
        ranged.id = DreamData.RANGED_HUSK;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(magic.npc, trip.getTarget(), "melee cannot reach the missile husk under her");
        assertEquals(Loadout.HUSK, trip.getLoadout());
        magic.id = 1;
        fixture.tick();
        assertSame(ranged.npc, trip.getTarget());
        assertEquals(Loadout.LONGRANGE, trip.getLoadout());
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Attacking a husk", fixture.plugin.getStatus(), "the longrange set names its target");
    }

    @Test
    public void aHuskBesideThePlayerDiesBeforeHerBlastsSleepwalkers() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget walker = fixture.world.npc(-9);
        walker.id = DreamData.SLEEPWALKER;
        fixture.world.npc(-12).id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget husk = fixture.world.npc(-1);
        husk.id = DreamData.MAGIC_HUSK;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(husk.npc, trip.getTarget(), "one crush hit frees the player to reach the sleepwalkers");
        assertEquals(Loadout.HUSK, trip.getLoadout());
        husk.id = 1;
        fixture.tick();
        assertEquals(DreamData.SLEEPWALKER, trip.getTarget().getId());
        assertEquals(Loadout.SLEEPWALKER, trip.getLoadout(), "a free player throws at the first of two");
    }

    @Test
    public void sleepwalkersRisingWhileAHuskUnderHerHoldsThePlayerAreCastAtFromLongrange() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget walker = fixture.world.npc(-9);
        walker.id = DreamData.SLEEPWALKER;
        TestWorld.NpcTarget husk = fixture.world.npc(4);
        husk.id = DreamData.MAGIC_HUSK;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        assertSame(walker.npc, trip.getTarget(), "melee cannot reach a husk under her");
        assertEquals(Loadout.LONGRANGE, trip.getLoadout());
    }

    @Test
    public void theLastTotemWaitsForHerHusksOrParasite() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9420;
        TestWorld.NpcTarget totem = fixture.world.npc(8);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        TestWorld.NpcTarget other = otherTotem(fixture, 9);
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.plugin.setTickDelay(0);
        TripState trip = (TripState) fixture.read("trip");
        fixture.tick();
        assertSame(totem.npc, trip.getTarget(), "two totems left: no wait");
        other.id = 1;
        fixture.tick();
        fixture.tick();
        assertNull(trip.getTarget());
        assertEquals("Holding the last totem for her husks", fixture.plugin.getStatus());
        fixture.boss.animation = DreamData.HUSKS;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.tick();
        assertSame(totem.npc, trip.getTarget(), "her husks came, so the last totem is charged");
    }

    @Test
    public void theLastTotemWaitsWhileHerNextSpecialCouldBeHusks() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.INITIAL;
        fixture.tick();
        fixture.boss.id = 9417;
        fixture.tick();
        special(fixture, DreamData.CURSE);
        special(fixture, DreamData.SPORES);
        fixture.boss.id = 9420;
        TestWorld.NpcTarget totem = fixture.world.npc(8);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.plugin.setTickDelay(0);
        TripState trip = (TripState) fixture.read("trip");
        fixture.tick();
        assertNull(trip.getTarget(), "the round has not had its husks");
        special(fixture, DreamData.HUSKS);
        fixture.tick();
        assertNull(trip.getTarget(), "her round is over, so husks may open the next");
        special(fixture, DreamData.SURGE_CHARGE);
        fixture.tick();
        assertNull(trip.getTarget(), "the new round has not had its husks");
        special(fixture, DreamData.HUSKS);
        fixture.tick();
        assertSame(totem.npc, trip.getTarget(), "the new round has had its husks");
    }

    @Test
    public void theLastTotemIsChargedWhenHerRoundHasHadItsHusks() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.INITIAL;
        fixture.tick();
        fixture.boss.id = 9417;
        fixture.tick();
        special(fixture, DreamData.HUSKS);
        fixture.boss.id = 9420;
        TestWorld.NpcTarget totem = fixture.world.npc(8);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.plugin.setTickDelay(0);
        TripState trip = (TripState) fixture.read("trip");
        fixture.tick();
        assertSame(totem.npc, trip.getTarget(), "husks came before her totems woke, in the same round");
    }

    private static void special(PluginTestFixture fixture, int animation)
    {
        fixture.boss.animation = animation;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.boss.animation = -1;
    }

    @Test
    public void theLastTotemWaitsOnlyAWhileForASetWithoutHusks() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9419;
        TestWorld.NpcTarget totem = fixture.world.npc(8);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        TripState trip = (TripState) fixture.read("trip");
        fixture.tick();
        assertNull(trip.getTarget());
        for (int tick = 0; tick < CombatTask.TOTEM_HOLD_TICKS; tick++)
        {
            fixture.tick();
        }
        assertSame(totem.npc, trip.getTarget(), "the wait ends");
    }

    @Test
    public void theTotemsAreChargedInTheMagicSet() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9419;
        TestWorld.NpcTarget totem = fixture.world.npc(8);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        otherTotem(fixture, 11);
        fixture.tick();
        assertEquals("Switching to magic gear", fixture.plugin.getStatus());
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Charging a totem", fixture.plugin.getStatus());
    }

    @Test
    public void everyMissingItemOfASetIsSwitchedOnTheSameTick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        int hat = 21018;
        fixture.config.magic = PluginTestFixture.STAFF + "," + hat;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, hat, 1);
        fixture.boss.id = 9419;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Switching to magic gear", fixture.plugin.getStatus());
        GearTask gear = (GearTask) fixture.read("gear");
        assertEquals(List.of(PluginTestFixture.STAFF, hat), gear.missing(Loadout.MAGIC));
    }

    @Test
    public void foodAndARestoreGoOnTheSameTick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5, 3024, 1);
        fixture.world.hitpoints = 40;
        fixture.world.prayerPoints = 5;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Eating and drinking a restore", fixture.plugin.getStatus());
    }

    @Test
    public void aBlastThatWouldKillIsEatenAgainstAboveTheEatingPoint() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.BLAST;
        fixture.world.hitpoints = 77;
        List<TestWorld.NpcTarget> walkers = new ArrayList<>();
        for (int walker = 0; walker < 4; walker++)
        {
            TestWorld.NpcTarget npc = fixture.world.npc(3 + walker);
            npc.id = DreamData.SLEEPWALKER;
            npc.index = 100 + walker;
            npc.animation = walker < 2 ? DreamData.SLEEPWALKER_ABSORBED : -1;
            walkers.add(npc);
        }
        fixture.tick();
        assertNotEquals("Eating", fixture.plugin.getStatus(), "two are still coming, and eating would end the attack");
        walkers.get(2).animation = DreamData.SLEEPWALKER_ABSORBED;
        walkers.get(3).dead = true;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Eating", fixture.plugin.getStatus(), "three of four reached her: 113, more than is left");
    }

    @Test
    public void aPrayerSwitchSharesTheTickWithAnAttack() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.animation = DreamData.RANGED_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.decisionOnly();
        fixture.clientTick();
        fixture.clientTick();
        assertEquals(2, fixture.requests(), "the protection and the attack");
    }

    @Test
    public void whileRiftsAreOutATotemIsChargedFromASafeTile() throws Exception
    {
        assertEquals("Moving into range", chargeWithRiftOut(9, true), "beyond casting range, a safe tile in range is stepped to");
        assertEquals("Waiting for a clear shot", chargeWithRiftOut(5, false), "no clear line");
        assertEquals("Charging a totem", chargeWithRiftOut(5, true));
    }

    /** A second ready totem, so the one a test charges is not the last. */
    private static TestWorld.NpcTarget otherTotem(PluginTestFixture fixture, int distance)
    {
        TestWorld.NpcTarget totem = fixture.world.npc(distance);
        totem.id = 9438;
        totem.actions = new String[] {"Charge"};
        return totem;
    }

    private static String chargeWithRiftOut(int distance, boolean clearLine) throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.inject("sight", (BiPredicate<WorldArea, WorldArea>) (from, to) -> clearLine);
        fixture.boss.id = 9419;
        fixture.boss.location = fixture.world.position.dx(-8);
        TestWorld.NpcTarget totem = fixture.world.npc(distance);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        otherTotem(fixture, distance + 3);
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.world.graphic(DreamData.RIFT_GRAPHIC, 2);
        fixture.enableAndDrain();
        fixture.tick();
        return fixture.plugin.getStatus();
    }

    @Test
    public void theTotemBeingChargedStaysTheTargetOnceAnotherIsAsNear() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9419;
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        TestWorld.NpcTarget first = fixture.world.npc(3);
        first.id = 9435;
        first.actions = new String[] {"Charge"};
        TestWorld.NpcTarget second = fixture.world.npc(-5);
        second.id = 9438;
        second.actions = new String[] {"Charge"};
        otherTotem(fixture, 12);
        TripState trip = (TripState) fixture.read("trip");
        fixture.tick();
        assertSame(first.npc, trip.getTarget());
        // A dodge takes the player west, now nearer the other one.
        fixture.world.position = fixture.world.position.dx(-3);
        fixture.tick();
        assertSame(first.npc, trip.getTarget(), "the part-charged totem is not left");
        first.id = 9436;
        fixture.tick();
        assertSame(second.npc, trip.getTarget(), "once charged, the nearest ready one");
    }

    @Test
    public void aReadyTotemOutOfRangeIsWalkedTo() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9419;
        TestWorld.NpcTarget totem = fixture.world.npc(14);
        totem.id = 9435;
        totem.actions = new String[] {"Charge"};
        otherTotem(fixture, 17);
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.tick();
        assertEquals("Moving to a totem", fixture.plugin.getStatus());
    }

    @Test
    public void totemsOutOfViewAreLookedForFromHerCentre() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = 9419;
        fixture.world.npc(8).id = 9436;
        fixture.world.npc(9).id = 9439;
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.tick();
        assertEquals("Moving to the middle for her totems", fixture.plugin.getStatus(),
            "two are charged and the other two are too far away to be seen");
        WorldPoint corner = fixture.boss.npc.getWorldLocation();
        fixture.world.position = corner.dx(3).dy(3);
        fixture.tick();
        fixture.tick();
        assertEquals("Waiting for a target", fixture.plugin.getStatus(), "from her centre all four would be in view");
        fixture.world.position = corner.dx(-10);
        TestWorld.NpcTarget ready = fixture.world.npc(14);
        ready.id = 9441;
        ready.actions = new String[] {"Charge"};
        otherTotem(fixture, 17);
        fixture.tick();
        assertEquals("Moving to a totem", fixture.plugin.getStatus(), "one in view is walked to directly");
    }

    @Test
    public void fromHerCornerWhileHerClawsAreOutTheMeleeStepsBesideHer() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.boss.id = DreamData.DESPERATION;
        // The player stands off her south-west corner: one tile away, but out of melee reach.
        fixture.boss.location = fixture.world.position.dx(1).dy(1);
        fixture.world.graphic(DreamData.RIFT_GRAPHIC, -4);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Moving beside her", fixture.plugin.getStatus());
    }

    @Test
    public void inHerFinalPhaseSleepwalkersAreLeftAndSheIsAttacked() throws Exception
    {
        PluginTestFixture fixture = started();
        fixture.boss.id = DreamData.DESPERATION;
        TestWorld.NpcTarget walker = fixture.world.npc(-2);
        walker.id = DreamData.SLEEPWALKER;
        TripState trip = (TripState) fixture.read("trip");
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
            assertSame(fixture.boss.npc, trip.getTarget(), "even one beside the player, tick " + tick);
            assertNotEquals(Loadout.SLEEPWALKER, trip.getLoadout());
        }
    }

    @Test
    public void withNoFoodLeftHerFinalPhaseIsLeftWhenThePlayerCannotOutlastHer() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.redeem = false;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1);
        fixture.boss.id = DreamData.DESPERATION;
        fixture.boss.healthScale = 30;
        fixture.boss.healthRatio = 30;
        fixture.world.hitpoints = 60;
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "60 Hitpoints outlast one sleepwalker");
        fixture.boss.healthRatio = 20;
        fixture.world.hitpoints = 30;
        fixture.tick();
        // 30 lost while she lost a third: 60 more expected.
        assertSame(TripPhase.LEAVING, ((TripState) fixture.read("trip")).getPhase());
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Teleporting to Ferox Enclave: Out of food in her final phase", fixture.plugin.getStatus());
    }

    @Test
    public void withFoodLeftHerFinalPhaseGoesOn() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.redeem = false;
        fixture.boss.id = DreamData.DESPERATION;
        fixture.boss.healthScale = 30;
        fixture.boss.healthRatio = 30;
        fixture.world.hitpoints = 60;
        fixture.enableAndDrain();
        fixture.tick();
        fixture.boss.healthRatio = 20;
        fixture.world.hitpoints = 30;
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "the food covers it");
    }

    @Test
    public void withNoFoodPrayerOrRestoresTheTripEndsBeforeHerFinalPhase() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1);
        fixture.boss.id = 9417;
        fixture.world.prayerPoints = 1;
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "Prayer points are left");
        fixture.world.prayerPoints = 0;
        fixture.tick();
        assertSame(TripPhase.LEAVING, ((TripState) fixture.read("trip")).getPhase());
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Teleporting to Ferox Enclave: Out of food and Prayer", fixture.plugin.getStatus());
    }

    @Test
    public void withNoFoodTheTripEndsAtTheEatingPointEvenWithRestores() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, 3024, 2);
        fixture.boss.id = 9417;
        fixture.world.hitpoints = fixture.config.eat + 1;
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "above the eating point");
        fixture.world.hitpoints = fixture.config.eat;
        fixture.tick();
        assertSame(TripPhase.LEAVING, ((TripState) fixture.read("trip")).getPhase());
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Teleporting to Ferox Enclave: Out of food", fixture.plugin.getStatus());
    }

    @Test
    public void foodOrARestoreKeepsTheFightGoingWithoutPrayer() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1,
            PluginTestFixture.FOOD, 1);
        fixture.boss.id = 9417;
        fixture.world.prayerPoints = 0;
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "food is left");
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, 3024, 1);
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "a restore is left");
    }

    @Test
    public void inHerFinalPhaseOnlyFoodDecides() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.redeem = false;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1);
        fixture.boss.id = DreamData.DESPERATION;
        fixture.boss.healthScale = 30;
        fixture.boss.healthRatio = 30;
        fixture.world.hitpoints = 60;
        fixture.world.prayerPoints = 0;
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase(), "60 Hitpoints outlast her");
    }

    @Test
    public void herFinalPhaseUsesTheSpecialAttackWeaponWithEnergy() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.spec = "13652";
        fixture.config.redeem = false;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, 13652, 1);
        fixture.world.varps.put(VarPlayerID.SA_ENERGY, 1000);
        fixture.boss.id = DreamData.DESPERATION;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Switching to spec gear", fixture.plugin.getStatus());
        fixture.world.varps.put(VarPlayerID.SA_ENERGY, 0);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Attacking The Nightmare", fixture.plugin.getStatus(), "no energy: the melee set again");
    }

    @Test
    public void aSpecialAttackThatSpendsNoEnergyGivesWayToTheMeleeSet() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.spec = "13652";
        fixture.config.redeem = false;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, PluginTestFixture.WHIP, 1);
        fixture.world.equipment = TestWorld.items(13652, 1);
        fixture.world.varps.put(VarPlayerID.SA_ENERGY, 1000);
        fixture.world.varps.put(VarPlayerID.SA_ATTACK, 1);
        fixture.boss.id = DreamData.DESPERATION;
        fixture.enableAndDrain();
        TripState trip = (TripState) fixture.read("trip");
        fixture.world.interacting = fixture.boss.npc;
        fixture.world.animation = 401;
        fixture.tick();
        assertEquals(Loadout.SPEC, trip.getLoadout(), "the bar is on and the swing has started");
        fixture.tick();
        fixture.tick();
        fixture.tick();
        assertEquals(Loadout.MELEE, trip.getLoadout(), "the swing spent no energy");
    }

    @Test
    public void aSpecialAttackThatSpendsEnergyKeepsTheWeapon() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.spec = "13652";
        fixture.config.redeem = false;
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, PluginTestFixture.WHIP, 1);
        fixture.world.equipment = TestWorld.items(13652, 1);
        fixture.world.varps.put(VarPlayerID.SA_ENERGY, 1000);
        fixture.world.varps.put(VarPlayerID.SA_ATTACK, 1);
        fixture.boss.id = DreamData.DESPERATION;
        fixture.enableAndDrain();
        TripState trip = (TripState) fixture.read("trip");
        fixture.world.interacting = fixture.boss.npc;
        fixture.world.animation = 401;
        fixture.tick();
        fixture.tick();
        fixture.world.varps.put(VarPlayerID.SA_ENERGY, 500);
        fixture.tick();
        fixture.tick();
        assertEquals(Loadout.SPEC, trip.getLoadout(), "half the energy is left for another");
    }

    @Test
    public void anUnweakenedParasiteIsCuredBeforeTheFightGoesOn() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.STAFF, 1, 10927, 1);
        fixture.world.activeVarbits.add(DreamData.PARASITE_VARBIT);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Drinking a parasite cure", fixture.plugin.getStatus());
    }

    @Test
    public void onceSheSleepsTheNextKillWaitsForTheLootThenDisturbsHer() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.nextRestores = 0;
        fixture.config.nextCures = 0;
        fixture.boss.id = DreamData.ASLEEP;
        fixture.boss.actions = new String[] {"Disturb"};
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Waiting", fixture.plugin.getStatus());
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Disturbing her", fixture.plugin.getStatus());
    }

    @Test
    public void lowSuppliesEndTheTripInsteadOfDisturbingHer() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.boss.id = DreamData.ASLEEP;
        fixture.enableAndDrain();
        for (int index = 0; index < 3; index++)
        {
            fixture.plugin.setTickDelay(0);
            fixture.tick();
        }
        TripState trip = (TripState) fixture.read("trip");
        assertEquals("Low on restores", trip.getLeaveReason());
    }

    @Test
    public void aTwoHandedSwapReplacesTheWeaponAndLeavesTheShieldOff()
    {
        TestWorld world = new TestWorld();
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, TestWorld.DirectExecutor::new)
        {
            @Override
            public int slot(int id)
            {
                return id == MACE || id == HALLOWFELL ? EquipmentInventorySlot.WEAPON.getSlotIdx()
                    : id == DEFENDER ? EquipmentInventorySlot.SHIELD.getSlotIdx() : EquipmentInventorySlot.HEAD.getSlotIdx();
            }

            @Override
            public boolean twoHanded(int id)
            {
                return id == HALLOWFELL;
            }
        };
        Map<Loadout, Integer> weapons = new EnumMap<>(Loadout.class);
        weapons.put(Loadout.SPEC, HALLOWFELL);
        weapons.put(Loadout.PARASITE, -1);
        GearTask gear = new GearTask(actions, new TripState(), () -> 0, Set.of(HELM, MACE, DEFENDER), Set.of(PluginTestFixture.STAFF), weapons);
        assertEquals(Set.of(HELM, HALLOWFELL), gear.wanted(Loadout.SPEC));
        assertEquals(Set.of(HELM, MACE, DEFENDER), gear.wanted(Loadout.PARASITE), "a blank weapon keeps the melee set");
        assertEquals(Set.of(HELM, MACE, DEFENDER), gear.wanted(Loadout.MELEE));
        assertEquals(List.of(PluginTestFixture.STAFF), List.copyOf(gear.wanted(Loadout.SLEEPWALKER)));
    }

    @Test
    public void enteringTheDreamLooksDownFromFarOutOnce()
    {
        TestWorld world = new TestWorld();
        TestWorld.QueuedThread thread = new TestWorld.QueuedThread();
        NightmareActions actions = new NightmareActions(world.client, thread, null, TestWorld.DirectExecutor::new)
        {
            @Override
            public boolean inInstance()
            {
                return true;
            }
        };
        TripState trip = new TripState();
        trip.setPhase(TripPhase.TRAVELLING);
        TravelTask travel = new TravelTask(actions, trip, new Walking(world.client, thread), () -> 0);
        travel.execute();
        travel.execute();
        thread.drain();
        assertEquals("Entered the dream", travel.getStatus());
        assertEquals(List.of("pitch 3064", "script 42 0 0"), world.camera, "the steepest pitch and the zoom script at 0");
    }

    @Test
    public void aLongConversationAtThePoolIsNotAStall()
    {
        TestWorld world = new TestWorld();
        int[] now = {0};
        String[] failed = {null};
        NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, TestWorld.DirectExecutor::new)
        {
            @Override
            public WorldPoint position()
            {
                return DreamData.POOL;
            }

            @Override
            public boolean inInstance()
            {
                return false;
            }

            @Override
            public boolean dialogueOption(String option, Runnable callback)
            {
                return false;
            }

            @Override
            public boolean dialogueContinue(Runnable callback)
            {
                callback.run();
                return true;
            }

            @Override
            public void fail(String reason)
            {
                failed[0] = reason;
            }
        };
        TripState trip = new TripState();
        trip.setPhase(TripPhase.TRAVELLING);
        TravelTask travel = new TravelTask(actions, trip, null, () -> now[0]);
        for (; now[0] < 60; now[0] += 2)
        {
            travel.execute();
        }
        assertNull(failed[0]);
        assertEquals("Entering the dream", travel.getStatus());
    }

    @Test
    public void aSurgeDownTheLaneSheFacesMovesThePlayerOut() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        fixture.boss.location = player.dx(-2).dy(2);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // She leaves, gone from the scene for a tick, then reappears at the start of a lane south of the player,
        // facing just west of north along it, still in her leaving animation.
        fixture.boss.topLevel = false;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        fixture.boss.topLevel = true;
        fixture.boss.location = player.dx(-2).dy(-12);
        fixture.boss.orientation = 970;
        fixture.boss.animation = DreamData.SURGE_LEAVE;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Dodging", fixture.plugin.getStatus());
    }

    @Test
    public void herSurgeChargeIsNotTakenForANewLane() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        fixture.boss.location = player.dx(-2).dy(2);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // The charge itself: she crosses to the far end of a lane beside the player in one tick.
        fixture.boss.location = player.dx(-8).dy(-12);
        fixture.boss.orientation = 1024;
        fixture.boss.animation = DreamData.SURGE_CHARGE;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        fixture.boss.location = player.dx(-8).dy(2);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertFalse(((Hazards) fixture.read("hazards")).isActive());
    }

    @Test
    public void outsideHerLaneMeleeHeadsToWhereTheSurgeLands() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        fixture.boss.location = player.dx(-2).dy(2);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // Her lane runs north just east of the player, so the player is already clear of it.
        fixture.boss.location = player.dx(1).dy(-12);
        fixture.boss.orientation = 1024;
        fixture.boss.animation = DreamData.SURGE_LEAVE;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Moving to where she lands", fixture.plugin.getStatus());
    }

    @Test
    public void aLaneStartingBesideWhereSheLeftFromIsStillDodged() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        fixture.boss.location = player.dx(-2).dy(3);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // From her centre she walks toward the south edge.
        for (int y = 1; y >= -3; y -= 2)
        {
            fixture.boss.location = player.dx(-1).dy(y);
            fixture.plugin.setTickDelay(0);
            fixture.tick();
        }
        fixture.boss.animation = DreamData.SURGE_LEAVE;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // She reappears two tiles from where she left, at the start of a lane north through the player's column.
        fixture.boss.location = player.dx(-2).dy(-5);
        fixture.boss.orientation = 0;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Dodging", fixture.plugin.getStatus());
    }

    @Test
    public void theTileSheLeftFromIsNoLaneWhileSheIsSlowToReappear() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        fixture.boss.location = player.dx(-2).dy(2);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // A step toward the player, then she starts to leave and is still seen there two ticks later.
        fixture.boss.location = player.dx(-2).dy(1);
        fixture.boss.animation = DreamData.SURGE_LEAVE;
        for (int i = 0; i < 3; i++)
        {
            fixture.plugin.setTickDelay(0);
            fixture.tick();
        }
        assertFalse(((Hazards) fixture.read("hazards")).isActive(), "a tile beside her centre starts no lane");
        // A tick late, she is seen at the start of a lane up the player's column.
        fixture.boss.location = player.dx(-2).dy(-12);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Dodging", fixture.plugin.getStatus());
    }

    @Test
    public void aStepSheFinishesAsSheLeavesForHerBlastIsNoLane() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        WorldPoint centre = player.dx(-2).dy(2);
        fixture.boss.location = centre;
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // She walks two tiles east and four south of her centre, starts to leave and is seen a step west.
        for (WorldPoint step : new WorldPoint[] {centre.dx(1).dy(-2), centre.dx(2).dy(-4)})
        {
            fixture.boss.location = step;
            fixture.plugin.setTickDelay(0);
            fixture.tick();
        }
        fixture.boss.animation = DreamData.SURGE_LEAVE;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        fixture.boss.location = centre.dx(1).dy(-4);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertFalse(((Hazards) fixture.read("hazards")).isActive(), "four tiles from her centre starts no lane");
        // She comes back to her centre for her blast.
        fixture.boss.animation = -1;
        fixture.boss.id = DreamData.BLAST;
        fixture.boss.location = centre;
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertFalse(((Hazards) fixture.read("hazards")).isActive());
    }

    @Test
    public void aLaneStartingOnTheTileSheLeftFromCountsWithOrWithoutHerReturnAnimation() throws Exception
    {
        for (boolean returnShown : new boolean[] {true, false})
        {
            PluginTestFixture fixture = new PluginTestFixture();
            WorldPoint player = fixture.world.position;
            fixture.boss.location = player.dx(-2).dy(6);
            fixture.enableAndDrain();
            fixture.plugin.setTickDelay(0);
            fixture.tick();
            // She walks to the south edge, two tiles a tick, and leaves from the start of a lane up the player's column.
            for (int y = 4; y >= -6; y -= 2)
            {
                fixture.boss.location = player.dx(-2).dy(y);
                fixture.plugin.setTickDelay(0);
                fixture.tick();
            }
            fixture.boss.animation = DreamData.SURGE_LEAVE;
            fixture.plugin.setTickDelay(0);
            fixture.tick();
            assertFalse(((Hazards) fixture.read("hazards")).isActive(), "leaving: no lane yet");
            fixture.boss.animation = returnShown ? DreamData.SURGE_RETURN : DreamData.SURGE_LEAVE;
            fixture.plugin.setTickDelay(0);
            fixture.tick();
            if (!returnShown)
            {
                fixture.plugin.setTickDelay(0);
                fixture.tick();
            }
            assertEquals("Dodging", fixture.plugin.getStatus(), returnShown ? "her return animation" : "two ticks on a lane start");
        }
    }

    @Test
    public void aStepLongAfterSheLeftAndCameBackToHerCentreIsNoLane() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        WorldPoint player = fixture.world.position;
        fixture.boss.location = player.dx(-2).dy(6);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        // Her flowers: she leaves and comes back to the centre tile she left from, with no return animation seen.
        fixture.boss.animation = DreamData.SURGE_LEAVE;
        for (int i = 0; i <= DreamData.REAPPEAR_WAIT_TICKS; i++)
        {
            fixture.plugin.setTickDelay(0);
            fixture.tick();
        }
        fixture.boss.animation = -1;
        fixture.boss.location = player.dx(-2).dy(5);
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertFalse(((Hazards) fixture.read("hazards")).isActive(), "a step toward the player is a walk, not a lane");
    }
}
