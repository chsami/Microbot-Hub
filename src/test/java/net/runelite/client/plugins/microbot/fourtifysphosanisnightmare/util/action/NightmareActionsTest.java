package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Point;
import net.runelite.api.MenuAction;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NightmareActionsTest
{
    private final TestWorld world = new TestWorld();
    private final DeferredExecutor worker = new DeferredExecutor();
    private final NightmareActions actions = new NightmareActions(world.client, new TestWorld.QueuedThread(), null, () -> worker);
    private final AtomicInteger sent = new AtomicInteger();

    @Test
    public void idsParseCommaSeparatedPositiveValuesInOrderWithoutDuplicates()
    {
        assertEquals(List.of(4675, 12002, 6585), new ArrayList<>(NightmareActions.ids("4675, 12002 ,6585,4675")));
        assertTrue(NightmareActions.ids("").isEmpty());
        assertTrue(NightmareActions.ids("   ").isEmpty());
        assertTrue(NightmareActions.ids(null).isEmpty());
        assertEquals(List.of(1, 2), new ArrayList<>(NightmareActions.ids(",1,,2, ,")));
    }

    @Test
    public void idsRejectTextAndNonPositiveValuesWithTheFormat()
    {
        for (String text : new String[] {"abc", "1,Torva", "0", "-5", "12.5"})
        {
            try
            {
                NightmareActions.ids(text);
                fail("Accepted " + text);
            }
            catch (IllegalArgumentException expected)
            {
                assertTrue(expected.getMessage().endsWith("use whole numbers separated by commas, like 4675,9185"), text);
            }
        }
    }

    @Test
    public void theStyleButtonIsTheFirstWhoseDescriptionNamesTheType()
    {
        Object[] blunt = {0, 1, 3};
        Object[] bluntText = {"(Accurate)<br>(Crush)<br>(Attack XP)", "(Aggressive)<br>(Crush)<br>(Strength XP)",
            "(Defensive)<br>(Crush)<br>(Defence XP)"};
        assertEquals(0, NightmareActions.styleButton(blunt, bluntText, "(Crush)"));
        Object[] thrown = {0, 1, 3};
        Object[] thrownText = {"(Accurate)<br>(Light)<br>(Ranged XP)", "(Rapid)<br>(Light)<br>(Ranged XP)",
            "(Longrange)<br>(Light)<br>(Ranged XP)<br>(Defence XP)"};
        assertEquals(1, NightmareActions.styleButton(thrown, thrownText, "(Rapid)"));
        assertEquals(-1, NightmareActions.styleButton(thrown, thrownText, "(Crush)"));
        assertEquals(-1, NightmareActions.styleButton(null, thrownText, "(Rapid)"));
    }

    @Test
    public void atOrBelowComparesAgainstThePercentageOfTheMaximum()
    {
        assertTrue(NightmareActions.atOrBelow(55, 55, 100));
        assertFalse(NightmareActions.atOrBelow(56, 55, 100));
        assertTrue(NightmareActions.atOrBelow(54, 55, 99));
        assertFalse(NightmareActions.atOrBelow(55, 55, 99));
        assertTrue(NightmareActions.atOrBelow(0, 0, 99));
        assertFalse(NightmareActions.atOrBelow(1, 0, 99));
        assertTrue(NightmareActions.atOrBelow(99, 100, 99));
    }

    @Test
    public void anNpcAtTwelveTilesIsAdmittedAndAtThirteenIsRefused()
    {
        TestWorld.NpcTarget near = world.npc(12);
        TestWorld.NpcTarget far = world.npc(13);
        assertTrue(actions.npc(near.npc, "Attack", sent::incrementAndGet));
        assertFalse(actions.npc(far.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get());
        assertEquals(1, actions.getRequestsDispatched());
    }

    @Test
    public void npcDistanceIsTheLargerAxisDifference()
    {
        WorldPoint origin = world.position;
        TestWorld.NpcTarget diagonalIn = world.npcAt(new WorldPoint(origin.getX() + 12, origin.getY() - 12, 0));
        TestWorld.NpcTarget diagonalOut = world.npcAt(new WorldPoint(origin.getX() + 5, origin.getY() + 13, 0));
        assertTrue(actions.npc(diagonalIn.npc, "Attack", null));
        assertFalse(actions.npc(diagonalOut.npc, "Attack", null));
    }

    @Test
    public void onlyAPointInsideTheGameViewIsClickedInTheScene()
    {
        assertTrue(actions.inViewport(new Point(4, 4)));
        assertTrue(actions.inViewport(new Point(768, 506)));
        assertFalse(actions.inViewport(new Point(-1154, 491)), "a tile drawn far off the view");
        assertFalse(actions.inViewport(new Point(769, 100)));
        assertFalse(actions.inViewport(new Point(100, 3)));
        assertFalse(actions.inViewport(null), "a tile behind the camera");
    }

    @Test
    public void npcsOnAnotherPlaneOrWorldViewOrDeadOrWithoutAttackAreRefused()
    {
        TestWorld.NpcTarget plane = world.npcAt(new WorldPoint(world.position.getX() + 3, world.position.getY(), 1));
        TestWorld.NpcTarget foreign = world.npc(3);
        foreign.topLevel = false;
        TestWorld.NpcTarget dead = world.npc(3);
        dead.dead = true;
        TestWorld.NpcTarget passive = world.npc(3);
        passive.actions = new String[] {"Examine"};
        TestWorld.NpcTarget wrongNpc = world.npc(3);
        wrongNpc.id = 1;
        for (TestWorld.NpcTarget target : List.of(plane, foreign, dead, passive))
        {
            assertFalse(actions.npc(target.npc, "Attack", null));
        }
        assertEquals(2, actions.npcs(Set.of(9416)).size(), "the live NPCs of the ID in the top-level view");
        assertEquals(3, actions.npcs(Set.of(9416), true).size(), "and the dying one when asked");
    }

    @Test
    public void anNpcThatLeavesRangeBeforeDispatchIsRejectedAtDispatch()
    {
        TestWorld.NpcTarget target = world.npc(12);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        target.location = new WorldPoint(world.position.getX() + 13, world.position.getY(), 0);
        actions.dispatch();
        assertEquals(0, sent.get());
        assertEquals(0, actions.getRequestsDispatched());
        assertFalse(actions.isBusy());
    }

    @Test
    public void anNpcThatLeavesRangeBeforeTheWorkerRunsIsRejectedOnTheClientThread()
    {
        TestWorld.NpcTarget target = world.npc(12);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        assertTrue(actions.isBusy());
        target.location = new WorldPoint(world.position.getX() + 13, world.position.getY(), 0);
        worker.runAll();
        assertEquals(0, sent.get());
        assertEquals(0, actions.getRequestsDispatched());
        assertFalse(actions.isBusy());
    }

    @Test
    public void anObjectAtTwentyTilesIsAdmittedAndAtTwentyOneIsRefused()
    {
        TestWorld.ObjectTarget near = world.object(DreamData.FEROX_POOL_OBJECT, 20, 20, "Climb-up");
        TestWorld.ObjectTarget far = world.object(DreamData.FEROX_BANK_CHEST, 21, 21, "Climb-up");
        assertTrue(actions.object(near.object, "Climb-up", sent::incrementAndGet));
        assertFalse(actions.object(far.object, "Climb-up", sent::incrementAndGet));
        assertSame(near.object, actions.object(Set.of(DreamData.FEROX_POOL_OBJECT)));
        assertNull(actions.object(Set.of(DreamData.FEROX_BANK_CHEST)));
    }

    @Test
    public void objectDistanceUsesTheNearestEdgeOfTheFootprintAndTheLargerAxis()
    {
        TestWorld.ObjectTarget wide = world.object(1, 19, 30, "Use");
        assertTrue(actions.object(wide.object, "Use", null));
        TestWorld.ObjectTarget beyond = world.object(2, 21, 30, "Use");
        assertFalse(actions.object(beyond.object, "Use", null));
        TestWorld.ObjectTarget surrounding = world.object(3, -4, 40, "Use");
        assertTrue(actions.object(surrounding.object, "Use", null));
        TestWorld.ObjectTarget diagonalIn = world.object(4, 20, 20, "Use");
        diagonalIn.minimum = new Point(TestWorld.PLAYER_SCENE + 20, TestWorld.PLAYER_SCENE - 20);
        diagonalIn.maximum = new Point(TestWorld.PLAYER_SCENE + 20, TestWorld.PLAYER_SCENE - 20);
        assertTrue(actions.object(diagonalIn.object, "Use", null));
        TestWorld.ObjectTarget diagonalOut = world.object(5, 5, 5, "Use");
        diagonalOut.minimum = new Point(TestWorld.PLAYER_SCENE + 5, TestWorld.PLAYER_SCENE + 21);
        diagonalOut.maximum = new Point(TestWorld.PLAYER_SCENE + 5, TestWorld.PLAYER_SCENE + 21);
        assertFalse(actions.object(diagonalOut.object, "Use", null));
    }

    @Test
    public void objectsOnAnotherPlaneOrWorldViewOrWithoutTheActionAreRefused()
    {
        TestWorld.ObjectTarget plane = world.object(1, 3, 3, "Use");
        plane.plane = 1;
        TestWorld.ObjectTarget foreign = world.object(2, 3, 3, "Use");
        foreign.topLevel = false;
        TestWorld.ObjectTarget other = world.object(3, 3, 3, "Open");
        assertFalse(actions.object(plane.object, "Use", null));
        assertFalse(actions.object(foreign.object, "Use", null));
        assertFalse(actions.object(other.object, "Use", null));
        assertTrue(actions.object(other.object, "open", null));
        assertFalse(actions.object((TileObject) null, "Use", null));
    }

    @Test
    public void anObjectMovedOutOfRangeOrRemovedBeforeDispatchIsRejected()
    {
        TestWorld.ObjectTarget moved = world.object(1, 20, 20, "Use");
        assertTrue(actions.object(moved.object, "Use", sent::incrementAndGet));
        moved.minimum = new Point(TestWorld.PLAYER_SCENE + 21, TestWorld.PLAYER_SCENE);
        moved.maximum = new Point(TestWorld.PLAYER_SCENE + 21, TestWorld.PLAYER_SCENE);
        actions.dispatch();
        assertEquals(0, sent.get());
        world.objects.clear();
        TestWorld.ObjectTarget removed = world.object(2, 3, 3, "Use");
        assertTrue(actions.object(removed.object, "Use", sent::incrementAndGet));
        world.objects.clear();
        actions.dispatch();
        assertEquals(0, sent.get());
    }

    @Test
    public void onlyOneRequestIsInFlightAtATime()
    {
        TestWorld.NpcTarget target = world.npc(3);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        assertTrue(actions.isBusy());
        assertEquals(0, sent.get());
        assertFalse(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get(), "The second queued request is refused while the first is in flight");
        assertFalse(actions.isBusy());
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(2, sent.get());
        assertEquals(2, actions.getRequestsDispatched());
    }

    @Test
    public void queuedRequestsDispatchFirstInFirstOutOneAtATime()
    {
        TestWorld.NpcTarget first = world.npc(3);
        TestWorld.NpcTarget second = world.npc(4);
        List<String> order = new ArrayList<>();
        assertTrue(actions.npc(first.npc, "Attack", () -> order.add("first")));
        assertTrue(actions.npc(second.npc, "Attack", () -> order.add("second")));
        actions.dispatch();
        worker.runAll();
        assertEquals(List.of("first"), order);
        actions.dispatch();
        worker.runAll();
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    public void resetCancelsAnInFlightRequestBeforeItsHandoff()
    {
        TestWorld.NpcTarget target = world.npc(3);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        assertTrue(actions.isBusy());
        actions.reset();
        worker.runAll();
        assertEquals(0, sent.get());
        assertEquals(0, actions.getRequestsDispatched());
        assertFalse(actions.isBusy());
    }

    @Test
    public void resetAfterHandoffStillBumpsTheGenerationForTheNextRequest()
    {
        TestWorld.NpcTarget target = world.npc(3);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get());
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        actions.reset();
        worker.runAll();
        assertEquals(1, sent.get());
    }

    @Test
    public void resetAndTheNextTickDiscardRequestsThatWereNeverDispatched()
    {
        TestWorld.NpcTarget target = world.npc(3);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.reset();
        actions.dispatch();
        assertEquals(0, sent.get());
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.nextTick();
        actions.dispatch();
        assertEquals(0, sent.get());
        assertFalse(actions.isBusy());
    }

    @Test
    public void aRequestExpiresTwentyNineCyclesAfterItsSnapshot()
    {
        TestWorld.NpcTarget target = world.npc(3);
        world.gameCycle = 100;
        actions.nextTick();
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        world.gameCycle = 129;
        worker.runAll();
        assertEquals(0, sent.get());
        world.gameCycle = 100;
        actions.nextTick();
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        world.gameCycle = 128;
        worker.runAll();
        assertEquals(1, sent.get());
    }

    @Test
    public void aPlayerWhoMovesBeforeDispatchOrBeforeTheWorkerRunsCannotSend()
    {
        TestWorld.NpcTarget target = world.npc(3);
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        world.position = new WorldPoint(world.position.getX() + 1, world.position.getY(), 0);
        actions.dispatch();
        assertEquals(0, sent.get());
        assertFalse(actions.isBusy());
        actions.nextTick();
        assertTrue(actions.npc(target.npc, "Attack", sent::incrementAndGet));
        actions.dispatch();
        world.position = new WorldPoint(world.position.getX() + 1, world.position.getY(), 0);
        worker.runAll();
        assertEquals(0, sent.get());
    }

    @Test
    public void requestsAreRefusedWhenThePlayerIsNotReady()
    {
        TestWorld.NpcTarget target = world.npc(3);
        world.dead = true;
        world.hitpoints = 0;
        assertFalse(actions.npc(target.npc, "Attack", null));
        world.dead = false;
        world.hitpoints = 99;
        world.widgetSelected = true;
        assertFalse(actions.npc(target.npc, "Attack", null));
        world.widgetSelected = false;
        world.topLevel = false;
        assertFalse(actions.npc(target.npc, "Attack", null));
        world.topLevel = true;
        world.state = GameState.LOADING;
        assertFalse(actions.npc(target.npc, "Attack", null));
        world.state = GameState.LOGGED_IN;
        world.playerPresent = false;
        assertFalse(actions.npc(target.npc, "Attack", null));
        world.playerPresent = true;
        assertTrue(actions.npc(target.npc, "Attack", null));
    }

    @Test
    public void aFailureRefusesEveryRequestUntilANewSessionAndKeepsTheFirstReason()
    {
        TestWorld.NpcTarget target = world.npc(3);
        actions.fail("Out of food");
        actions.fail("Something else");
        assertEquals("Out of food", actions.getFailure());
        assertFalse(actions.npc(target.npc, "Attack", null));
        actions.newSession();
        assertNull(actions.getFailure());
        assertTrue(actions.npc(target.npc, "Attack", null));
    }

    @Test
    public void aDodgeCutsAGearSwitchShortInsteadOfWaitingForIt()
    {
        List<Integer> clicks = new ArrayList<>();
        WorldPoint tile = new WorldPoint(world.position.getX() + 1, world.position.getY(), world.position.getPlane());
        actions.burst.start(List.of(1, 2, 3), item -> clicks.add(item));
        actions.dispatch();
        assertEquals(List.of(1), clicks);
        assertFalse(actions.step(tile, null), "a plain step waits for the switch");
        assertTrue(actions.dodge(tile, sent::incrementAndGet), "a dodge does not");
        actions.dispatch();
        worker.runAll();
        assertEquals(0, sent.get(), "the dodge waits until the game takes the click in flight");
        actions.clicked(1);
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get());
        actions.dispatch();
        assertEquals(List.of(1), clicks, "the rest of the switch is dropped");
        assertFalse(actions.isBusy());
    }

    @Test
    public void aPrayerGoesBetweenTheClicksOfAGearSwitch()
    {
        List<Integer> clicks = new ArrayList<>();
        actions.burst.start(List.of(1, 2), item -> clicks.add(item));
        actions.dispatch();
        assertEquals(List.of(1), clicks);
        assertTrue(actions.prayer(Rs2PrayerEnum.PROTECT_MELEE, true, sent::incrementAndGet), "the burst does not hold a prayer back");
        actions.dispatch();
        worker.runAll();
        assertEquals(0, sent.get(), "the prayer waits until the game takes the click in flight");
        actions.clicked(1);
        actions.dispatch();
        assertEquals(List.of(1), clicks, "the burst waits for the prayer");
        worker.runAll();
        assertEquals(1, sent.get());
        actions.dispatch();
        assertEquals(List.of(1, 2), clicks);
    }

    @Test
    public void aPrayerWaitsForARunningClickInsteadOfBeingRefused()
    {
        TestWorld.NpcTarget target = world.npc(3);
        assertTrue(actions.npc(target.npc, "Attack", null));
        actions.dispatch();
        assertTrue(actions.isBusy(), "the attack is still on its way");
        assertTrue(actions.prayer(Rs2PrayerEnum.PROTECT_MELEE, true, sent::incrementAndGet), "the prayer is queued");
        actions.dispatch();
        assertEquals(0, sent.get(), "the prayer waits for the attack");
        worker.runAll();
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get(), "the prayer goes once the attack has finished");
    }

    @Test
    public void aPrayerRequestNeedsAChangeAndPrayerPointsToTurnOn()
    {
        Rs2PrayerEnum prayer = Rs2PrayerEnum.PROTECT_MELEE;
        assertTrue(actions.prayer(prayer, true, null));
        assertFalse(actions.prayer(prayer, false, null), "Turning off a prayer that is off");
        world.prayerPoints = 0;
        assertFalse(actions.prayer(prayer, true, null));
        world.prayerPoints = 99;
        world.activeVarbits.add(prayer.getVarbit());
        assertTrue(actions.prayerActive(prayer));
        assertFalse(actions.prayer(prayer, true, null));
        assertTrue(actions.prayer(prayer, false, null));
        assertFalse(actions.prayer(null, true, null));
    }

    @Test
    public void bankRequestsNeedAnOpenBankAndTheItemsInTheRightPlace()
    {
        world.inventory = TestWorld.items(385, 5, 555, 1);
        world.bank = TestWorld.items(2434, 3);
        assertFalse(actions.deposit(555, null));
        assertFalse(actions.withdrawOne(2434, null));
        assertFalse(actions.closeBank(null));
        world.bankVisible = true;
        assertTrue(actions.bankOpen());
        assertTrue(actions.deposit(555, null));
        assertFalse(actions.deposit(999, null));
        assertTrue(actions.withdrawOne(2434, null));
        assertFalse(actions.withdrawOne(999, null));
        assertTrue(actions.closeBank(null));
        world.inventory = fullInventory();
        assertFalse(actions.withdrawOne(2434, null), "No free slot to withdraw into");
    }

    @Test
    public void aWithdrawalNeedsTheWholeAmountInTheBankAndFreeSlotsForIt()
    {
        world.bankVisible = true;
        world.inventory = TestWorld.items(385, 1);
        world.bank = TestWorld.items(2434, 3);
        assertFalse(actions.withdraw(2434, 4, null), "Not enough in the bank");
        assertTrue(actions.withdraw(2434, 3, null));
        int[] pairs = new int[52];
        for (int index = 0; index < 26; index++)
        {
            pairs[index * 2] = 1000 + index;
            pairs[index * 2 + 1] = 1;
        }
        world.inventory = TestWorld.items(pairs);
        assertFalse(actions.withdraw(2434, 3, null), "Two free slots for three");
        assertTrue(actions.withdraw(2434, 2, null));
        world.inventory = fullInventory();
        assertFalse(actions.withdraw(2434, 1, null), "One at a time also needs a free slot");
    }

    @Test
    public void inventoryAndEquipmentRequestsAreRefusedWhileTheBankIsOpen()
    {
        world.equipment = TestWorld.items(2552, 1);
        assertTrue(actions.equipment(2552, "Ferox Enclave", null));
        assertFalse(actions.equipment(2553, "Ferox Enclave", null));
        world.bankVisible = true;
        assertFalse(actions.equipment(2552, "Ferox Enclave", null));
    }

    @Test
    public void theBankOpensThroughAChestInRangeOfTheBank()
    {
        world.position = DreamData.FEROX_BANK;
        assertFalse(actions.openBank(sent::incrementAndGet));
        TestWorld.ObjectTarget chest = world.object(DreamData.FEROX_BANK_CHEST, 3, 3, "Use");
        chest.location = DreamData.FEROX_BANK;
        assertTrue(actions.openBank(sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get());
        world.bankVisible = true;
        assertFalse(actions.openBank(null));
    }

    @Test
    public void aChestFarFromTheBankIsNotUsed()
    {
        world.position = DreamData.FEROX_BANK;
        TestWorld.ObjectTarget chest = world.object(DreamData.FEROX_BANK_CHEST, 3, 3, "Use");
        chest.location = new WorldPoint(DreamData.FEROX_BANK.getX() + 13, DreamData.FEROX_BANK.getY(), 0);
        assertFalse(actions.openBank(null));
    }

    @Test
    public void itemReadsCountStacksFreeSlotsAndTheFirstRing()
    {
        world.inventory = TestWorld.items(385, 5, 385, 2, 555, 100, 0, 0);
        assertEquals(7, actions.count(385));
        assertEquals(7, actions.count(Set.of(385)));
        assertEquals(107, actions.count(List.of(385, 555)));
        assertEquals(25, actions.freeSlots());
        assertEquals(Set.of(385, 555), actions.carriedIds());
        assertTrue(actions.inventoryLoaded());
        assertEquals(-1, actions.duelingRing());
        world.inventory = TestWorld.items(385, 5, 2554, 1);
        assertEquals(2554, actions.duelingRing());
        world.equipment = TestWorld.items(2556, 1);
        assertEquals(2554, actions.duelingRing());
        world.inventory = TestWorld.items(385, 5);
        assertEquals(2556, actions.duelingRing());
        assertTrue(actions.wearing(2556));
        assertFalse(actions.wearing(2554));
        world.bank = TestWorld.items(2434, 3);
        assertEquals(3, actions.bankCount(2434));
        assertEquals(0, actions.bankCount(385));
        world.inventoryPresent = false;
        assertFalse(actions.inventoryLoaded());
        assertEquals(0, actions.items().length);
    }

    @Test
    public void restoredNeedsFullHitpointsPrayerAndRunEnergy()
    {
        assertTrue(actions.restored());
        world.hitpoints = 98;
        assertFalse(actions.restored());
        world.hitpoints = 99;
        world.prayerPoints = 98;
        assertFalse(actions.restored());
        world.prayerPoints = 99;
        world.energy = 9_999;
        assertFalse(actions.restored());
    }

    @Test
    public void ringTeleportNeedsAnyRingOfDueling()
    {
        assertFalse(actions.ringTeleport(null));
    }

    @Test
    public void itemInformationIsUnknownWithoutAnItemManager()
    {
        assertEquals(0, actions.price(995));
        assertNull(actions.itemName(995));
        assertFalse(actions.stackable(995));
    }

    private static Item[] fullInventory()
    {
        int[] pairs = new int[56];
        for (int index = 0; index < 28; index++)
        {
            pairs[index * 2] = 1000 + index;
            pairs[index * 2 + 1] = 1;
        }
        return TestWorld.items(pairs);
    }

    /** Holds submitted work until the test runs it, like a worker thread that has not started yet. */
    private static final class DeferredExecutor extends AbstractExecutorService
    {
        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void execute(Runnable command)
        {
            queued.add(command);
        }

        private void runAll()
        {
            List<Runnable> batch = new ArrayList<>(queued);
            queued.clear();
            batch.forEach(Runnable::run);
        }

        @Override
        public void shutdown()
        {
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown()
        {
            return false;
        }

        @Override
        public boolean isTerminated()
        {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return true;
        }
    }

    @Test
    public void aSpellIsCastByPressingItsButtonByComponent()
    {
        NewMenuEntry entry = NightmareActions.castEntry("Death Charge", InterfaceID.MagicSpellbook.DEATH_CHARGE);
        assertEquals("Cast", entry.getOption());
        assertEquals(MenuAction.CC_OP, entry.getType());
        assertEquals(1, entry.getIdentifier());
        assertEquals(-1, entry.getParam0());
        assertEquals(InterfaceID.MagicSpellbook.DEATH_CHARGE, entry.getParam1(), "the button's own component, never a lookup");
    }
}
