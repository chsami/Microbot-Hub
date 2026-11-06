package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankTaskTest
{
    private static final int FOOD = 385;
    private static final int RING = 2552;
    private static final int STAFF = 4675;
    private static final int BOW = 12002;
    private static final int SCROLL = TempleData.TEMPLE_TELEPORT;
    private final FakeActions actions = new FakeActions();
    private final FakeWalking walking = new FakeWalking(actions.world);
    private final MutableConfig config = new MutableConfig();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(100);
    private final Map<CombatStyle, Set<Integer>> sets = new LinkedHashMap<>();
    private final Set<Integer> keep = new LinkedHashSet<>();
    private final Map<Integer, Integer> restock = new LinkedHashMap<>();
    private final List<String> told = new ArrayList<>();
    private final BankTask task = new BankTask(actions, config, trip, walking, tick::get, sets, keep, restock, told::add);

    public BankTaskTest()
    {
        trip.setPhase(TripPhase.BANKING);
        actions.point = TempleData.FEROX_BANK;
        actions.bankIsOpen = true;
        config.foodTotal = 3;
        config.restores = 2;
        config.scrolls = 2;
    }

    @Test
    public void isValidOnlyWhileBankingAtFerox()
    {
        assertTrue(task.isValid());
        actions.point = TempleData.FEROX_ARRIVAL;
        assertTrue(task.isValid());
        actions.point = TempleData.TEMPLE_ENTRY;
        assertFalse(task.isValid());
        actions.point = TempleData.FEROX_BANK;
        for (TripPhase phase : List.of(TripPhase.TRAVELLING, TripPhase.FIGHTING, TripPhase.LEAVING))
        {
            trip.setPhase(phase);
            assertFalse(task.isValid(), phase.name());
        }
    }

    @Test
    public void keptItemsCoverSuppliesRingsRestocksAndEveryGearSet()
    {
        sets.put(CombatStyle.MAGIC, Set.of(STAFF));
        sets.put(CombatStyle.RANGED, Set.of(BOW));
        keep.add(1234);
        restock.put(555, 100);
        Set<Integer> kept = task.keptItems();
        assertTrue(kept.containsAll(Set.of(FOOD, SCROLL, STAFF, BOW, 1234, 555)));
        assertTrue(kept.containsAll(TempleData.PRAYER_RESTORES));
        assertTrue(kept.containsAll(TempleData.DUELING_RINGS));
        assertFalse(kept.contains(999));
    }

    @Test
    public void depositsEveryItemThatIsNotKeptBeforeWithdrawingAnything()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 1);
        actions.have(999, 4);
        actions.have(998, 1);
        actions.inBank(FOOD, 10);
        task.execute();
        assertEquals(List.of("deposit:999"), actions.requests);
        assertEquals("Depositing loot", task.getStatus());
        actions.inventory.remove(999);
        next();
        assertEquals("deposit:998", actions.requests.get(1));
        actions.inventory.remove(998);
        next();
        assertEquals("withdraw:" + FOOD + ":2", actions.requests.get(2));
    }

    @Test
    public void itemsFromTheKeepAndRestockListsAreNotDeposited()
    {
        keep.add(1234);
        restock.put(555, 100);
        actions.have(RING, 1);
        actions.have(1234, 1);
        actions.have(555, 100);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
        task.execute();
        assertEquals(List.of("closeBank"), actions.requests);
    }

    @Test
    public void fetchesARingFromTheBankWhenNoneIsCarriedOrWorn()
    {
        actions.inBank(2554, 1);
        actions.inBank(RING, 1);
        task.execute();
        assertEquals(List.of("withdraw:" + RING), actions.requests);
        assertEquals("Withdrawing a Ring of dueling", task.getStatus());
    }

    @Test
    public void aWornRingIsEnough()
    {
        actions.worn.add(RING);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
        task.execute();
        assertEquals(List.of("closeBank"), actions.requests);
    }

    @Test
    public void failsWithAReasonWhenNoRingIsInTheBank()
    {
        task.execute();
        assertEquals("No Ring of dueling in the bank; add one, then restart the plugin", actions.getFailure());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void withdrawsMissingEquipmentAfterTheRingAndBeforeSupplies()
    {
        sets.put(CombatStyle.MAGIC, new LinkedHashSet<>(List.of(STAFF)));
        sets.put(CombatStyle.RANGED, new LinkedHashSet<>(List.of(BOW)));
        actions.have(RING, 1);
        actions.inBank(STAFF, 1);
        actions.inBank(BOW, 1);
        actions.inBank(FOOD, 10);
        task.execute();
        assertEquals("withdraw:" + STAFF, actions.requests.get(0));
        assertEquals("Withdrawing equipment", task.getStatus());
        actions.have(STAFF, 1);
        next();
        assertEquals("withdraw:" + BOW, actions.requests.get(1));
        actions.worn.add(BOW);
        next();
        assertEquals("withdraw:" + FOOD + ":3", actions.requests.get(2));
    }

    @Test
    public void failsNamingTheEquipmentItemMissingFromBankAndInventory()
    {
        sets.put(CombatStyle.MELEE, Set.of(STAFF));
        actions.names.put(STAFF, "Ancient staff");
        actions.have(RING, 1);
        task.execute();
        assertEquals("Missing Ancient staff (" + STAFF + "): put it in your bank or remove it from your equipment IDs, then restart the plugin",
            actions.getFailure());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void withdrawsTheMissingFoodUntilTheConfiguredCount()
    {
        actions.have(RING, 1);
        actions.inBank(FOOD, 50);
        actions.have(FOOD, 2);
        task.execute();
        assertEquals(List.of("withdraw:" + FOOD), actions.requests);
        assertEquals("Withdrawing food", task.getStatus());
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
        next();
        assertEquals("closeBank", actions.requests.get(1));
    }

    @Test
    public void takesAllTheMissingFoodInOneRequestWithinTheBankAndTheFreeSlots()
    {
        actions.have(RING, 1);
        actions.inBank(FOOD, 2);
        task.execute();
        assertEquals(List.of("withdraw:" + FOOD + ":2"), actions.requests, "Only what the bank has");
        actions.inBank(FOOD, 50);
        for (int slot = 0; slot < 25; slot++)
        {
            keep.add(20_000 + slot);
            actions.have(20_000 + slot, 1);
        }
        next();
        assertEquals("withdraw:" + FOOD + ":2", actions.requests.get(1), "Only what fits");
    }

    @Test
    public void aStackOfTeleportsIsNotLimitedByTheFreeSlots()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.stackables.add(SCROLL);
        actions.inBank(SCROLL, 9);
        for (int slot = 0; slot < 24; slot++)
        {
            keep.add(20_000 + slot);
            actions.have(20_000 + slot, 1);
        }
        assertEquals(1, actions.freeSlots());
        task.execute();
        assertEquals(List.of("withdraw:" + SCROLL + ":2"), actions.requests);
    }

    @Test
    public void carriesOnWithSomeFoodWhenTheBankRunsOutButFailsWithNone()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 1);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
        task.execute();
        assertEquals(List.of("closeBank"), actions.requests);
        assertNull(actions.getFailure());
        actions.inventory.remove(FOOD);
        next();
        assertEquals("No food left in the bank; add more to the bank, then restart the plugin", actions.getFailure());
    }

    @Test
    public void withdrawsFourDoseRestoresFirst()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.inBank(139, 5);
        actions.inBank(2434, 5);
        actions.inBank(3030, 5);
        task.execute();
        assertEquals(List.of("withdraw:2434:2"), actions.requests);
        assertEquals("Withdrawing prayer restores", task.getStatus());
    }

    @Test
    public void usesAnotherRestoreDoseWhenNoFourDoseIsBanked()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.inBank(143, 5);
        actions.inBank(141, 5);
        task.execute();
        assertEquals(List.of("withdraw:141:2"), actions.requests);
    }

    @Test
    public void restoresAreCountedAcrossEveryDoseAndSortsOfRestore()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.have(3024, 1);
        actions.have(143, 1);
        actions.have(SCROLL, 2);
        task.execute();
        assertEquals(List.of("closeBank"), actions.requests);
    }

    @Test
    public void failsWhenNoRestoreIsCarriedOrBanked()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        task.execute();
        assertEquals("No prayer restores left in the bank; add more to the bank, then restart the plugin", actions.getFailure());
    }

    @Test
    public void withdrawsTempleTeleportsAfterRestoresAndFailsWhenNoneRemain()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.inBank(SCROLL, 9);
        task.execute();
        assertEquals(List.of("withdraw:" + SCROLL + ":2"), actions.requests);
        assertEquals("Withdrawing Guthixian temple teleports", task.getStatus());
        actions.bank.clear();
        next();
        assertEquals("No Guthixian temple teleports left in the bank; add more to the bank, then restart the plugin", actions.getFailure());
    }

    @Test
    public void withdrawsRestockItemsInTheConfiguredOrderAfterTheSupplies()
    {
        restock.put(560, 2);
        restock.put(555, 1);
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
        actions.inBank(560, 99);
        actions.inBank(555, 99);
        task.execute();
        assertEquals("withdraw:560:2", actions.requests.get(0));
        assertEquals("Withdrawing item 560", task.getStatus(), "Without a known name the ID stands alone");
        actions.have(560, 2);
        next();
        assertEquals("withdraw:555", actions.requests.get(1));
        actions.have(555, 1);
        next();
        assertEquals("closeBank", actions.requests.get(2));
    }

    @Test
    public void failsNamingARestockItemMissingFromBankAndInventory()
    {
        restock.put(560, 2);
        actions.names.put(560, "Death rune");
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
        task.execute();
        assertEquals("No Death rune (560) left in the bank; add more to the bank, then restart the plugin", actions.getFailure());
    }

    @Test
    public void closesTheBankOnceEverythingIsStocked()
    {
        stock();
        task.execute();
        assertEquals(List.of("closeBank"), actions.requests);
        assertEquals("Closing the bank", task.getStatus());
        assertEquals(TripPhase.BANKING, trip.getPhase());
        assertEquals(0, trip.getTrips());
    }

    @Test
    public void waitsTwoTicksAfterEachRequestBeforeTheNextOne()
    {
        stock();
        actions.inventory.clear();
        actions.have(RING, 1);
        actions.inBank(FOOD, 9);
        task.execute();
        assertEquals(1, actions.requests.size());
        tick.addAndGet(1);
        task.execute();
        assertEquals(1, actions.requests.size());
        tick.addAndGet(1);
        task.execute();
        assertEquals(2, actions.requests.size());
    }

    @Test
    public void aRefusedRequestIsNotRememberedAsSentAndIsRetriedNextTick()
    {
        actions.have(RING, 1);
        actions.inBank(FOOD, 9);
        actions.refused.add("withdraw");
        task.execute();
        task.execute();
        assertTrue(actions.requests.isEmpty());
        actions.refused.clear();
        task.execute();
        assertEquals(List.of("withdraw:" + FOOD + ":3"), actions.requests);
    }

    @Test
    public void doesNotDrinkAgainWhileWalkingToThePool()
    {
        WorldPoint pool = TempleData.FEROX_POOL;
        actions.point = new WorldPoint(pool.getX() + 4, pool.getY(), 0);
        stock();
        task.execute();
        actions.bankIsOpen = false;
        actions.fullyRestored = false;
        actions.objects.put(TempleData.FEROX_POOL_OBJECT,
            actions.world.object(TempleData.FEROX_POOL_OBJECT, 2, 2, "Drink").object);
        tick.addAndGet(2);
        task.execute();
        assertEquals(1, drinks());
        for (int step = 1; step <= 3; step++)
        {
            tick.addAndGet(1);
            actions.point = new WorldPoint(pool.getX() + 4 - step, pool.getY(), 0);
            task.execute();
        }
        tick.addAndGet(1);
        task.execute();
        assertEquals(1, drinks(), "Walking to the pool and just arrived");
        tick.addAndGet(1);
        task.execute();
        assertEquals(2, drinks(), "Standing still and still not restored");
    }

    private long drinks()
    {
        return actions.requests.stream().filter(request -> request.endsWith(":Drink")).count();
    }

    @Test
    public void opensTheBankWhenStandingNearIt()
    {
        actions.bankIsOpen = false;
        task.execute();
        assertEquals(List.of("openBank"), actions.requests);
        assertEquals("Opening the bank", task.getStatus());
        assertTrue(walking.destinations.isEmpty());
        assertEquals(1, walking.cancellations);
    }

    @Test
    public void walksToTheBankWhenFarFromItAndOpensItOnArrival()
    {
        actions.bankIsOpen = false;
        actions.point = TempleData.FEROX_ARRIVAL;
        task.execute();
        assertTrue(actions.requests.isEmpty());
        assertEquals(List.of(TempleData.FEROX_BANK), walking.destinations);
        assertEquals("Walking to the Ferox Enclave bank", task.getStatus());
        actions.point = new WorldPoint(TempleData.FEROX_BANK.getX() + 8, TempleData.FEROX_BANK.getY(), 0);
        task.execute();
        assertEquals(List.of("openBank"), actions.requests);
    }

    @Test
    public void aWalkingFailureEndsTheSessionNamingTheDestinationAndTheNextStep()
    {
        actions.bankIsOpen = false;
        actions.point = TempleData.FEROX_ARRIVAL;
        walking.state = Walking.State.FAILED;
        task.execute();
        assertEquals("Could not walk to the Ferox Enclave bank; walk there yourself, then restart the plugin", actions.getFailure());
    }

    @Test
    public void walksToThePoolAndDrinksUntilRestored()
    {
        stock();
        task.execute();
        actions.bankIsOpen = false;
        actions.fullyRestored = false;
        actions.point = TempleData.FEROX_ARRIVAL;
        tick.addAndGet(2);
        task.execute();
        assertEquals(List.of(TempleData.FEROX_POOL), walking.destinations);
        assertEquals("Walking to the Pool of Refreshment", task.getStatus());
        assertEquals(1, actions.requests.size());
        actions.point = new WorldPoint(TempleData.FEROX_POOL.getX() + 6, TempleData.FEROX_POOL.getY(), 0);
        tick.addAndGet(2);
        task.execute();
        assertEquals(1, actions.requests.size(), "No pool in range, so no drink request");
        actions.objects.put(TempleData.FEROX_POOL_OBJECT,
            actions.world.object(TempleData.FEROX_POOL_OBJECT, 2, 2, "Drink").object);
        tick.addAndGet(2);
        task.execute();
        assertEquals("object:" + TempleData.FEROX_POOL_OBJECT + ":Drink", actions.requests.get(1));
        assertEquals("Drinking from the Pool of Refreshment", task.getStatus());
        assertEquals(TripPhase.BANKING, trip.getPhase());
        tick.addAndGet(2);
        task.execute();
        assertEquals(3, actions.requests.size(), "Keeps drinking while not restored");
        assertEquals(0, trip.getTrips());
    }

    @Test
    public void startsTheNextTripOnceRestoredAndStocked()
    {
        stock();
        task.execute();
        actions.bankIsOpen = false;
        actions.fullyRestored = true;
        tick.addAndGet(2);
        int cancellations = walking.cancellations;
        task.execute();
        assertEquals(TripPhase.TRAVELLING, trip.getPhase());
        assertEquals(1, trip.getTrips());
        assertEquals("Supplies ready", task.getStatus());
        assertEquals(cancellations + 1, walking.cancellations);
        assertEquals(1, actions.requests.size());
    }

    @Test
    public void skipsThePoolWhenAlreadyRestoredAndCountsEachTrip()
    {
        stock();
        task.execute();
        actions.bankIsOpen = false;
        tick.addAndGet(2);
        task.execute();
        assertEquals(1, trip.getTrips());
        trip.arrivedAtBank();
        actions.bankIsOpen = true;
        tick.addAndGet(2);
        task.execute();
        assertEquals(List.of("closeBank", "closeBank"), actions.requests, "A new bank visit stocks again");
        actions.bankIsOpen = false;
        tick.addAndGet(2);
        task.execute();
        assertEquals(2, trip.getTrips());
    }

    @Test
    public void resetForgetsThatTheBankWasStocked()
    {
        stock();
        task.execute();
        actions.bankIsOpen = false;
        task.reset();
        actions.fullyRestored = false;
        task.execute();
        assertEquals("Opening the bank", task.getStatus());
    }

    private void stock()
    {
        actions.have(RING, 1);
        actions.have(FOOD, 3);
        actions.have(2434, 2);
        actions.have(SCROLL, 2);
    }

    @Test
    public void entersTheSavedBankPinOnceThenWaitsForThePlayer()
    {
        actions.bankIsOpen = false;
        actions.pinShown = true;
        task.execute();
        assertEquals(List.of("enterBankPin"), actions.requests);
        assertEquals("Entering the bank PIN", task.getStatus());
        next();
        next();
        assertEquals(List.of("enterBankPin"), actions.requests, "A wrong PIN is not typed again");
        assertEquals("Enter your bank PIN to continue", task.getStatus());
        next();
        assertEquals(List.of("Waiting for your bank PIN: type it in the bank window, and check the PIN saved in your Microbot profile;"
            + " the plugin continues after the bank opens"), told, "The player is told once per wait");
        assertNull(actions.getFailure());
        actions.pinShown = false;
        actions.bankIsOpen = true;
        actions.have(RING, 1);
        actions.inBank(FOOD, 10);
        next();
        assertEquals("withdraw:" + FOOD + ":3", actions.requests.get(1));
    }

    private void next()
    {
        tick.addAndGet(2);
        task.execute();
    }
}
