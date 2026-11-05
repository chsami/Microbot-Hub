package net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action;

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
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DemonActionsTest
{
    private final TestWorld world = new TestWorld();
    private final DeferredExecutor worker = new DeferredExecutor();
    private final DemonActions actions = new DemonActions(world.client, new TestWorld.QueuedThread(), null, () -> worker);
    private final AtomicInteger sent = new AtomicInteger();

    @Test
    public void idsParseCommaSeparatedPositiveValuesInOrderWithoutDuplicates()
    {
        assertEquals(List.of(4675, 12002, 6585), new ArrayList<>(DemonActions.ids("4675, 12002 ,6585,4675")));
        assertTrue(DemonActions.ids("").isEmpty());
        assertTrue(DemonActions.ids("   ").isEmpty());
        assertTrue(DemonActions.ids(null).isEmpty());
        assertEquals(List.of(1, 2), new ArrayList<>(DemonActions.ids(",1,,2, ,")));
    }

    @Test
    public void idsRejectTextAndNonPositiveValuesWithTheFormat()
    {
        for (String text : new String[] {"abc", "1,Torva", "0", "-5", "12.5"})
        {
            try
            {
                DemonActions.ids(text);
                fail("Accepted " + text);
            }
            catch (IllegalArgumentException expected)
            {
                assertTrue(expected.getMessage().endsWith("use whole numbers separated by commas, like 4675,9185"), text);
            }
        }
    }

    @Test
    public void restockParsesItemQuantityPairsInOrder()
    {
        Map<Integer, Integer> parsed = DemonActions.restock("555:100, 560 : 50,565:2");
        assertEquals(List.of(555, 560, 565), new ArrayList<>(parsed.keySet()));
        assertEquals(Integer.valueOf(100), parsed.get(555));
        assertEquals(Integer.valueOf(50), parsed.get(560));
        assertEquals(Integer.valueOf(2), parsed.get(565));
        assertTrue(DemonActions.restock("").isEmpty());
        assertTrue(DemonActions.restock(null).isEmpty());
        assertEquals(List.of(560), new ArrayList<>(DemonActions.restock("560:500,").keySet()));
    }

    @Test
    public void restockRejectsMalformedPairsAndNonPositiveValuesWithAClearReason()
    {
        for (String text : new String[] {"555", "555:", "555:1:2", ":5", "a:5", "5:b", "0:5", "5:0", "-1:5", "5:-1"})
        {
            try
            {
                DemonActions.restock(text);
                fail("Accepted " + text);
            }
            catch (IllegalArgumentException expected)
            {
                assertFalse(expected.getMessage() == null, text);
            }
        }
        try
        {
            DemonActions.restock("555");
            fail();
        }
        catch (IllegalArgumentException expected)
        {
            assertEquals("\"555\" is not an itemID:quantity pair; use pairs separated by commas, like 560:500,9075:200",
                expected.getMessage());
        }
    }

    @Test
    public void atOrBelowComparesAgainstThePercentageOfTheMaximum()
    {
        assertTrue(DemonActions.atOrBelow(55, 55, 100));
        assertFalse(DemonActions.atOrBelow(56, 55, 100));
        assertTrue(DemonActions.atOrBelow(54, 55, 99));
        assertFalse(DemonActions.atOrBelow(55, 55, 99));
        assertTrue(DemonActions.atOrBelow(0, 0, 99));
        assertFalse(DemonActions.atOrBelow(1, 0, 99));
        assertTrue(DemonActions.atOrBelow(99, 100, 99));
    }

    @Test
    public void aDemonAtTwelveTilesIsAdmittedAndAtThirteenIsRefused()
    {
        TestWorld.DemonTarget near = world.demon(12);
        TestWorld.DemonTarget far = world.demon(13);
        assertTrue(actions.attack(near.npc, sent::incrementAndGet));
        assertFalse(actions.attack(far.npc, sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get());
        assertEquals(1, actions.getRequestsDispatched());
    }

    @Test
    public void demonDistanceIsTheLargerAxisDifference()
    {
        WorldPoint origin = world.position;
        TestWorld.DemonTarget diagonalIn = world.demonAt(new WorldPoint(origin.getX() + 12, origin.getY() - 12, 0));
        TestWorld.DemonTarget diagonalOut = world.demonAt(new WorldPoint(origin.getX() + 5, origin.getY() + 13, 0));
        assertTrue(actions.attack(diagonalIn.npc, null));
        assertFalse(actions.attack(diagonalOut.npc, null));
    }

    @Test
    public void demonsOnAnotherPlaneOrWorldViewOrDeadOrWithoutAttackAreRefused()
    {
        TestWorld.DemonTarget plane = world.demonAt(new WorldPoint(world.position.getX() + 3, world.position.getY(), 1));
        TestWorld.DemonTarget foreign = world.demon(3);
        foreign.topLevel = false;
        TestWorld.DemonTarget dead = world.demon(3);
        dead.dead = true;
        TestWorld.DemonTarget passive = world.demon(3);
        passive.actions = new String[] {"Examine"};
        TestWorld.DemonTarget wrongNpc = world.demon(3);
        wrongNpc.id = 1;
        for (TestWorld.DemonTarget target : List.of(plane, foreign, dead, passive, wrongNpc))
        {
            assertFalse(actions.attack(target.npc, null));
        }
        assertEquals(2, actions.demons().size());
    }

    @Test
    public void aDemonThatLeavesRangeBeforeDispatchIsRejectedAtDispatch()
    {
        TestWorld.DemonTarget target = world.demon(12);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        target.location = new WorldPoint(world.position.getX() + 13, world.position.getY(), 0);
        actions.dispatch();
        assertEquals(0, sent.get());
        assertEquals(0, actions.getRequestsDispatched());
        assertFalse(actions.isBusy());
    }

    @Test
    public void aDemonThatLeavesRangeBeforeTheWorkerRunsIsRejectedOnTheClientThread()
    {
        TestWorld.DemonTarget target = world.demon(12);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
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
        TestWorld.ObjectTarget near = world.object(TempleData.FIRST_WALL, 20, 20, "Climb-up");
        TestWorld.ObjectTarget far = world.object(TempleData.SECOND_WALL, 21, 21, "Climb-up");
        assertTrue(actions.object(near.object, "Climb-up", sent::incrementAndGet));
        assertFalse(actions.object(far.object, "Climb-up", sent::incrementAndGet));
        assertSame(near.object, actions.object(Set.of(TempleData.FIRST_WALL)));
        assertNull(actions.object(Set.of(TempleData.SECOND_WALL)));
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
        TestWorld.DemonTarget target = world.demon(3);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        assertTrue(actions.isBusy());
        assertEquals(0, sent.get());
        assertFalse(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get(), "The second queued request is refused while the first is in flight");
        assertFalse(actions.isBusy());
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(2, sent.get());
        assertEquals(2, actions.getRequestsDispatched());
    }

    @Test
    public void queuedRequestsDispatchFirstInFirstOutOneAtATime()
    {
        TestWorld.DemonTarget first = world.demon(3);
        TestWorld.DemonTarget second = world.demon(4);
        List<String> order = new ArrayList<>();
        assertTrue(actions.attack(first.npc, () -> order.add("first")));
        assertTrue(actions.attack(second.npc, () -> order.add("second")));
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
        TestWorld.DemonTarget target = world.demon(3);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
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
        TestWorld.DemonTarget target = world.demon(3);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        worker.runAll();
        assertEquals(1, sent.get());
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        actions.reset();
        worker.runAll();
        assertEquals(1, sent.get());
    }

    @Test
    public void resetAndTheNextTickDiscardRequestsThatWereNeverDispatched()
    {
        TestWorld.DemonTarget target = world.demon(3);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.reset();
        actions.dispatch();
        assertEquals(0, sent.get());
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.nextTick();
        actions.dispatch();
        assertEquals(0, sent.get());
        assertFalse(actions.isBusy());
    }

    @Test
    public void aRequestExpiresTwentyNineCyclesAfterItsSnapshot()
    {
        TestWorld.DemonTarget target = world.demon(3);
        world.gameCycle = 100;
        actions.nextTick();
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        world.gameCycle = 129;
        worker.runAll();
        assertEquals(0, sent.get());
        world.gameCycle = 100;
        actions.nextTick();
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        world.gameCycle = 128;
        worker.runAll();
        assertEquals(1, sent.get());
    }

    @Test
    public void aPlayerWhoMovesBeforeDispatchOrBeforeTheWorkerRunsCannotSend()
    {
        TestWorld.DemonTarget target = world.demon(3);
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        world.position = new WorldPoint(world.position.getX() + 1, world.position.getY(), 0);
        actions.dispatch();
        assertEquals(0, sent.get());
        assertFalse(actions.isBusy());
        actions.nextTick();
        assertTrue(actions.attack(target.npc, sent::incrementAndGet));
        actions.dispatch();
        world.position = new WorldPoint(world.position.getX() + 1, world.position.getY(), 0);
        worker.runAll();
        assertEquals(0, sent.get());
    }

    @Test
    public void requestsAreRefusedWhenThePlayerIsNotReady()
    {
        TestWorld.DemonTarget target = world.demon(3);
        world.dead = true;
        assertFalse(actions.attack(target.npc, null));
        world.dead = false;
        world.widgetSelected = true;
        assertFalse(actions.attack(target.npc, null));
        world.widgetSelected = false;
        world.topLevel = false;
        assertFalse(actions.attack(target.npc, null));
        world.topLevel = true;
        world.state = GameState.LOADING;
        assertFalse(actions.attack(target.npc, null));
        world.state = GameState.LOGGED_IN;
        world.playerPresent = false;
        assertFalse(actions.attack(target.npc, null));
        world.playerPresent = true;
        assertTrue(actions.attack(target.npc, null));
    }

    @Test
    public void aFailureRefusesEveryRequestUntilANewSessionAndKeepsTheFirstReason()
    {
        TestWorld.DemonTarget target = world.demon(3);
        actions.fail("Out of food");
        actions.fail("Something else");
        assertEquals("Out of food", actions.getFailure());
        assertFalse(actions.attack(target.npc, null));
        actions.newSession();
        assertNull(actions.getFailure());
        assertTrue(actions.attack(target.npc, null));
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
        world.position = TempleData.FEROX_BANK;
        assertFalse(actions.openBank(sent::incrementAndGet));
        TestWorld.ObjectTarget chest = world.object(TempleData.FEROX_BANK_CHEST, 3, 3, "Use");
        chest.location = TempleData.FEROX_BANK;
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
        world.position = TempleData.FEROX_BANK;
        TestWorld.ObjectTarget chest = world.object(TempleData.FEROX_BANK_CHEST, 3, 3, "Use");
        chest.location = new WorldPoint(TempleData.FEROX_BANK.getX() + 13, TempleData.FEROX_BANK.getY(), 0);
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
}
