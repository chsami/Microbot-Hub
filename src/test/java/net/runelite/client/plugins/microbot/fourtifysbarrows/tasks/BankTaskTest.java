package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Prayer;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankTaskTest
{
    @Test
    public void hiddenBankWaitsForBusyFlagToClearBeforeTripCompletion()
    {
        FakeActions actions = feroxActions();
        actions.bankOpen = true;
        BankTask task = new BankTask(actions);
        task.execute();
        actions.bankOpen = false;
        actions.bankBusy = true;
        for (int tick : new int[] {1, 7, 8, 9})
        {
            actions.now = tick;
            task.execute();
            assertEquals(0, actions.completed);
            assertEquals(0, actions.opens);
            assertEquals(0, actions.teleports);
        }
        actions.bankBusy = false;
        task.execute();
        assertEquals(1, actions.completed);
        assertEquals(1, actions.closes);
        assertEquals("", actions.failure);
    }

    @Test
    public void hiddenBankWithBusyFlagSetHasABoundedCloseFailure()
    {
        FakeActions actions = feroxActions();
        actions.bankOpen = true;
        BankTask task = new BankTask(actions);
        task.execute();
        actions.bankOpen = false;
        actions.bankBusy = true;
        for (int tick : new int[] {8, 16, 24})
        {
            actions.now = tick;
            task.execute();
        }
        assertTrue(actions.failure.contains("Bank close not acknowledged"));
        assertEquals(0, actions.completed);
        assertEquals(0, actions.opens);
        assertEquals(0, actions.walks);
        assertEquals(0, actions.teleports);
    }

    @Test
    public void hiddenBankCannotStartPoolOrBootstrapTeleportUntilCloseAcknowledged()
    {
        for (boolean pool : new boolean[] {false, true})
        {
            FakeActions actions = feroxActions();
            actions.bankOpen = true;
            actions.differentBank = !pool;
            actions.poolRestored = !pool;
            actions.poolArrived = true;
            actions.ringArrival = pool;
            BankTask task = new BankTask(actions);
            task.execute();
            actions.bankOpen = false;
            actions.bankBusy = true;
            task.execute();
            assertEquals(0, actions.poolDrinks);
            assertEquals(0, actions.teleports);
            actions.bankBusy = false;
            task.execute();
            assertEquals(pool ? 1 : 0, actions.poolDrinks);
            assertEquals(pool ? 0 : 1, actions.teleports);
        }
    }

    @Test
    public void bootstrapBankElsewhereClosesAndUsesFeroxInsteadOfCompletingThere()
    {
        FakeActions actions = feroxActions();
        actions.bankOpen = true;
        actions.differentBank = true;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        assertEquals(0, actions.teleports);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.completed);
        assertEquals(0, actions.walks);
    }

    @Test
    public void rewardsCloseMustBeObservedBeforeAnyRingTeleport()
    {
        FakeActions actions = feroxActions();
        actions.inside = true;
        actions.rewards = true;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.rewardCloses);
        assertEquals(0, actions.teleports);
        actions.now = 7;
        task.execute();
        assertEquals(1, actions.rewardCloses);
        assertEquals(0, actions.teleports);
        actions.rewards = false;
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.walks);
    }

    @Test
    public void failedRewardCloseStopsWithoutTeleportingOrDiscardingRewards()
    {
        FakeActions actions = feroxActions();
        actions.inside = true;
        actions.rewards = true;
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.rewardCloses);
        assertEquals(0, actions.teleports);
        assertTrue(actions.failure.contains("close Barrows rewards"));
    }

    @Test
    public void postRewardTunnelTripUsesSelectedTeleportThenWaitsForArrival()
    {
        FakeActions actions = feroxActions();
        actions.inside = true;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.walks);
        actions.now = 7;
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.opens);
        actions.inside = false;
        actions.ringArrival = true;
        actions.arrived = true;
        task.execute();
        assertEquals(1, actions.opens);
        assertEquals(0, actions.completed);
    }

    @Test
    public void tunnelTeleportWithoutObservedArrivalFailsWithoutCompletingTrip()
    {
        FakeActions actions = feroxActions();
        actions.inside = true;
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 90, 180, 270})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.teleports);
        assertTrue(actions.failure.contains("after three tries"));
        assertEquals(0, actions.completed);
        assertEquals(0, actions.walks);
    }

    @Test
    public void ordinaryTunnelBankIntentUsesTheRingWithoutLadderOrWalking()
    {
        FakeActions actions = feroxActions();
        actions.inside = true;
        new BankTask(actions).execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.walks);
    }

    @Test
    public void feroxArrivalRestoresBeforeOpeningTheBankAndWaitsForObservedStats()
    {
        FakeActions actions = feroxActions();
        actions.poolRestored = false;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.poolWalks);
        actions.ringArrival = true;
        task.execute();
        assertEquals(1, actions.poolWalks);
        assertEquals(0, actions.walks);
        assertEquals(0, actions.opens);
        actions.poolArrived = true;
        task.execute();
        assertEquals(1, actions.poolDrinks);
        actions.now = 7;
        task.execute();
        assertEquals(1, actions.poolDrinks);
        assertEquals(0, actions.opens);
        actions.poolRestored = true;
        actions.arrived = true;
        task.execute();
        assertEquals(1, actions.opens);
        assertEquals(0, actions.completed);
        actions.bankOpen = true;
        task.execute();
        assertEquals(1, actions.closes);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.completed);
    }

    @Test
    public void feroxArrivalTurnsOffTheTunnelProtectionPrayerBeforeThePool()
    {
        FakeActions actions = feroxActions();
        actions.poolRestored = false;
        actions.protection = Prayer.PROTECT_FROM_MELEE;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.prayerOffs, "Protection stays on in the tunnels until the ring lands");
        actions.ringArrival = true;
        task.execute();
        assertEquals(1, actions.prayerOffs);
        assertEquals(0, actions.poolWalks);
        task.execute();
        assertEquals(1, actions.prayerOffs, "Waits for the prayer to be seen off");
        assertEquals(0, actions.poolWalks);
        actions.protection = null;
        task.execute();
        assertEquals(1, actions.prayerOffs);
        assertEquals(1, actions.poolWalks);
    }

    @Test
    public void alreadyOpenBankClosesForPoolWithoutCompletingTheTrip()
    {
        FakeActions actions = feroxActions();
        actions.bankOpen = true;
        actions.poolRestored = false;
        actions.poolArrived = true;
        actions.ringArrival = true;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        task.execute();
        assertEquals(0, actions.completed);
        assertEquals(0, actions.poolDrinks);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.poolDrinks);
        assertEquals(0, actions.completed);
    }

    @Test
    public void feroxRestorationIsBoundedWhenDrinkHasNoObservedEffect()
    {
        FakeActions actions = feroxActions();
        actions.ringArrival = true;
        actions.poolArrived = true;
        actions.poolRestored = false;
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.poolDrinks);
        assertTrue(actions.failure.contains("after three tries"));
        assertTrue(actions.failure.contains("Ferox Pool"));
        assertEquals(0, actions.opens);
        assertEquals(0, actions.completed);
    }

    @Test
    public void absentFeroxPoolActionStopsWithoutInventingAnAttempt()
    {
        FakeActions actions = feroxActions();
        actions.ringArrival = true;
        actions.poolArrived = true;
        actions.poolRestored = false;
        actions.poolAvailable = false;
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(0, actions.poolDrinks);
        assertTrue(actions.failure.contains("Could not drink from the Ferox Pool of Refreshment after three tries"));
        assertEquals(0, actions.completed);
    }

    @Test
    public void unreachableFeroxPoolHasABoundedApproachDeadline()
    {
        FakeActions actions = feroxActions();
        actions.ringArrival = true;
        actions.poolRestored = false;
        BankTask task = new BankTask(actions);
        task.execute();
        actions.now = 60;
        task.execute();
        assertTrue(actions.failure.contains("Ferox pool restoration timed out"));
        assertEquals(0, actions.poolDrinks);
        assertEquals(0, actions.opens);
    }



    @Test
    public void runEnergySpentAfterBankReadinessDoesNotRestartRestoration()
    {
        FakeActions actions = feroxActions();
        actions.bankOpen = true;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        actions.bankOpen = false;
        actions.poolRestored = false;
        task.execute();
        assertEquals(1, actions.completed);
        assertEquals(0, actions.poolDrinks);
    }

    @Test
    public void observedPoolRestorationSurvivesEnergySpentWalkingToBankAndSceneLoads()
    {
        FakeActions actions = feroxActions();
        actions.ringArrival = true;
        actions.poolArrived = true;
        actions.poolRestored = false;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.poolDrinks);
        actions.poolRestored = true;
        task.execute();
        assertEquals(1, actions.walks);
        actions.poolRestored = false;
        task.suspend();
        actions.arrived = true;
        task.execute();
        assertEquals(1, actions.opens);
        actions.bankOpen = true;
        task.execute();
        assertEquals(1, actions.closes);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.completed);
        assertEquals(1, actions.poolDrinks);
        assertEquals("", actions.failure);

        actions.pending = true;
        task.execute();
        assertEquals(2, actions.poolDrinks);
        assertEquals(1, actions.completed);
    }

    @Test
    public void alreadyOpenBankRestoresOnceAndReopensWithoutRepeatingThePool()
    {
        FakeActions actions = feroxActions();
        actions.bankOpen = true;
        actions.ringArrival = true;
        actions.poolArrived = true;
        actions.arrived = true;
        actions.poolRestored = false;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.poolDrinks);
        actions.poolRestored = true;
        task.execute();
        assertEquals(1, actions.opens);
        actions.poolRestored = false;
        actions.bankOpen = true;
        task.execute();
        assertEquals(2, actions.closes);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.completed);
        assertEquals(1, actions.poolDrinks);
    }

    @Test
    public void replacementTaskAfterSessionCancellationRequiresFreshRestoration()
    {
        FakeActions actions = feroxActions();
        actions.ringArrival = true;
        actions.poolArrived = true;
        new BankTask(actions).execute();
        actions.poolRestored = false;
        new BankTask(actions).execute();
        assertEquals(1, actions.poolDrinks);
        assertEquals(0, actions.completed);
    }

    private static FakeActions feroxActions()
    {
        FakeActions actions = new FakeActions();
        actions.inventory.put(2552, 1);
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        return actions;
    }

    @Test
    public void selectedRingWaitsForArrivalAndDoesNotFallBackToWalking()
    {
        FakeActions actions = new FakeActions();
        actions.inventory.put(2552, 1);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.walks);
        for (int tick = 1; tick < 12; tick++)
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(1, actions.teleports);
        assertEquals(2, actions.selections);
        assertEquals(0, actions.walks);
        actions.ringArrival = true;
        task.execute();
        assertEquals(1, actions.walks);
        assertEquals(0, actions.opens);
        actions.arrived = true;
        task.execute();
        assertEquals(1, actions.opens);
    }

    @Test
    public void unchangedDestinationMenuRetriesBoundedlyWithoutRubbingAgain()
    {
        FakeActions actions = new FakeActions();
        actions.inventory.put(2552, 1);
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 1, 9, 17, 25})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(1, actions.teleports);
        assertEquals(3, actions.selections);
        assertTrue(actions.failure.contains("Ferox Enclave teleport failed three times"));
    }

    @Test
    public void unobservedRingArrivalStopsAfterThreeAttempts()
    {
        FakeActions actions = new FakeActions();
        actions.inventory.put(2552, 1);
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 90, 180, 270})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.teleports);
        assertEquals(0, actions.walks);
        assertTrue(actions.failure.contains("after three tries"));
    }

    @Test
    public void lastChargeRequiresObservedReplacementBeforeBankClose()
    {
        FakeActions actions = new FakeActions();
        actions.inventory.clear();
        actions.bankOpen = true;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        actions.bankStock.add(2554);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(Integer.valueOf(2554), actions.lastWithdraw);
        task.execute();
        assertEquals(0, actions.closes);
        actions.inventory.put(2554, 1);
        task.execute();
        assertEquals(1, actions.closes);
    }

    @Test
    public void missingChargedRingCannotStartBankTravelOrUseAnotherRoute()
    {
        FakeActions actions = new FakeActions();
        actions.inventory.clear();
        actions.inventory.put(2572, 1);
        BankTask task = new BankTask(actions);
        task.execute();
        assertTrue(actions.failure.contains("charged"));
        assertEquals(0, actions.teleports);
        assertEquals(0, actions.walks);
    }

    @Test
    public void waitsForObservedFeroxArrivalBeforeWalkingOrOpeningBank()
    {
        FakeActions actions = new FakeActions();
        actions.inside = true;
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.teleports);
        assertEquals(0, actions.opens);
        actions.inside = false;
        actions.ringArrival = true;
        actions.arrived = true;
        task.execute();
        assertEquals(1, actions.opens);
        assertTrue(actions.pending);
    }

    @Test
    public void openingIsNotSuccessUntilBankIsObserved()
    {
        FakeActions actions = new FakeActions();
        actions.arrived = true;
        BankTask task = new BankTask(actions);
        task.execute();
        actions.now = 7;
        task.execute();
        assertEquals(1, actions.opens);
        assertEquals(0, actions.deposits);
        actions.bankOpen = true;
        actions.loot = 2;
        task.execute();
        assertEquals(1, actions.deposits);
    }

    @Test
    public void partialDepositsAndWithdrawalsNeedObservedQuantityChanges()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.loot = 2;
        BankTask task = new BankTask(actions);
        task.execute();
        task.execute();
        assertEquals(1, actions.deposits);
        actions.loot = 1;
        task.execute();
        assertEquals(2, actions.deposits);
        actions.loot = 0;
        task.execute();
        assertEquals(Integer.valueOf(952), actions.lastWithdraw);
        task.execute();
        assertEquals(1, actions.withdrawals);
        actions.inventory.put(952, 1);
        task.execute();
        assertEquals(Integer.valueOf(385), actions.lastWithdraw);
        actions.inventory.put(385, 1);
        task.execute();
        assertEquals(Integer.valueOf(385), actions.lastWithdraw);
        assertEquals(3, actions.withdrawals);
        actions.inventory.put(385, 2);
        task.execute();
        assertEquals(Integer.valueOf(2434), actions.lastWithdraw);
    }

    @Test
    public void withdrawsTheMissingFoodInOneRequestWithinTheFreeSlots()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.inventory.put(952, 1);
        actions.bankStock.add(385);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(Integer.valueOf(385), actions.lastWithdraw);
        assertEquals(2, actions.lastAmount);
        FakeActions full = new FakeActions();
        full.bankOpen = true;
        full.inventory.put(952, 1);
        full.bankStock.add(385);
        full.slots = 1;
        new BankTask(full).execute();
        assertEquals(1, full.lastAmount, "Only what fits");
    }

    @Test
    public void aStackableSupplyIsNotLimitedByTheFreeSlots()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        actions.restock.put(560, 100);
        actions.bankStock.add(560);
        actions.stackables.add(560);
        actions.slots = 1;
        new BankTask(actions).execute();
        assertEquals(Integer.valueOf(560), actions.lastWithdraw);
        assertEquals(100, actions.lastAmount);
    }

    @Test
    public void tripCompletesOnlyAfterBankCloseIsObserved()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        assertEquals(0, actions.completed);
        task.execute();
        assertEquals(1, actions.closes);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.completed);
        assertEquals(0, actions.opens);
        assertFalse(task.isValid());
    }

    @Test
    public void failedDispatchedWithdrawalStopsAfterThreeAttempts()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.withdrawals);
        assertTrue(actions.failure.contains("after three tries"));
        assertEquals(0, actions.completed);
    }

    @Test
    public void aFullLoadoutLeavesTheBankBecauseOverflowLootIsPickedUpLater()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.slots = 2;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        assertEquals("", actions.failure);
        assertEquals(2, actions.foodCount());
    }

    @Test
    public void closingStillRequiresObservedBankClose()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.slots = 9;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        assertEquals(0, actions.completed);
        actions.bankOpen = false;
        task.execute();
        assertEquals(1, actions.completed);
        assertEquals("", actions.failure);
    }

    @Test
    public void unavailableSupplyStopsWithoutInventingDispatchOrSuccess()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.available = false;
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(0, actions.withdrawals);
        assertEquals("Out of Spade in the bank: bank a spade", actions.failure);
        assertEquals(0, actions.completed);
    }

    @Test
    public void resuppliesFromPartialDoseBottlesWhenFullBottlesAreUnavailable()
    {
        for (int potion : new int[] {139, 141, 143})
        {
            FakeActions actions = new FakeActions();
            actions.bankOpen = true;
            actions.inventory.put(952, 1);
            actions.inventory.put(385, 2);
            actions.bankStock.clear();
            actions.bankStock.add(potion);
            BankTask task = new BankTask(actions);
            task.execute();
            assertEquals(Integer.valueOf(potion), actions.lastWithdraw);
            task.execute();
            assertEquals(1, actions.withdrawals);
            actions.inventory.put(potion, 1);
            task.execute();
            assertEquals(1, actions.closes);
            assertEquals("", actions.failure);
        }
    }

    @Test
    public void foodMissingFromTheBankStopsWithItsNameAndTheSettingToChange()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.available = false;
        actions.inventory.put(952, 1);
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals("Out of Shark in the bank: restock it or lower Food count", actions.failure);
    }

    @Test
    public void foodLeftInTheBankButNotWithdrawnStopsWithTheNextStep()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.available = false;
        actions.inventory.put(952, 1);
        actions.bankStock.add(385);
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals("Could not withdraw Shark; free inventory space, then enable the plugin again", actions.failure);
    }

    @Test
    public void missingEveryPotionVariantStopsAfterBoundedWait()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.bankStock.clear();
        BankTask task = new BankTask(actions);
        for (int tick : new int[] {0, 14, 28, 42})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(0, actions.withdrawals);
        assertEquals(0, actions.completed);
        assertEquals("Out of prayer potions in the bank: restock them or lower Prayer potion count", actions.failure);
    }

    @Test
    public void restocksConfiguredRunesAndAmmunitionBeforeClosingBank()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.inventory.put(952, 1);
        actions.inventory.put(385, 2);
        actions.inventory.put(2434, 1);
        actions.restock.put(560, 2);
        actions.restock.put(892, 1);
        BankTask task = new BankTask(actions);
        task.execute();
        assertEquals(Integer.valueOf(560), actions.lastWithdraw);
        task.execute();
        assertEquals(1, actions.withdrawals);
        actions.inventory.put(560, 1);
        task.execute();
        assertEquals(Integer.valueOf(560), actions.lastWithdraw);
        actions.inventory.put(560, 2);
        task.execute();
        assertEquals(Integer.valueOf(892), actions.lastWithdraw);
        assertEquals(0, actions.closes);
        actions.inventory.put(892, 1);
        task.execute();
        assertEquals(1, actions.closes);
    }

    @Test
    public void suspensionDoesNotSpendRetryBudgetDuringSceneLoad()
    {
        FakeActions actions = new FakeActions();
        actions.arrived = true;
        BankTask task = new BankTask(actions);
        task.execute();
        actions.now = 100;
        task.suspend();
        task.execute();
        assertEquals(1, actions.opens);
        assertEquals("", actions.failure);
    }

    private static final class FakeActions extends BarrowsActions
    {
        private final Map<Integer, Integer> inventory = new HashMap<>(Map.of(2552, 1));
        private final Map<Integer, Integer> restock = new LinkedHashMap<>();
        private final Set<Integer> bankStock = new HashSet<>(Set.of(2434));
        private final BarrowsConfig config = new BarrowsConfig()
        {
            @Override
            public int foodCount()
            {
                return 2;
            }

            @Override
            public int potionCount()
            {
                return 1;
            }
        };
        private boolean inside;
        private boolean arrived;
        private boolean rewards;
        private int rewardCloses;
        private boolean bankOpen;
        private boolean bankBusy;
        private boolean differentBank;
        private boolean pending = true;
        private boolean available = true;
        private int walks;
        private int teleports;
        private int selections;
        private boolean ringArrival;
        private int opens;
        private int deposits;
        private int withdrawals;
        private int closes;
        private int completed;
        private int now;
        private int loot;
        private int slots = 28;
        private int poolWalks;
        private int poolDrinks;
        private boolean poolArrived;
        private boolean poolRestored = true;
        private boolean poolAvailable = true;
        private Integer lastWithdraw;
        private int lastAmount;
        private final Set<Integer> stackables = new HashSet<>();
        private String failure = "";
        private Prayer protection;
        private int prayerOffs;

        @Override
        public boolean prayerActive(Prayer prayer)
        {
            return prayer == protection;
        }

        @Override
        public boolean protect(Prayer prayer, boolean active, Runnable callback)
        {
            if (!active && prayer == protection)
            {
                prayerOffs++;
                callback.run();
            }
            return true;
        }

        @Override
        public boolean ready()
        {
            return failure.isEmpty();
        }

        @Override
        public boolean tripPending()
        {
            return pending;
        }

        @Override
        public boolean visible(int id)
        {
            return rewards && id == (155 << 16);
        }

        @Override
        public boolean closeRewards(Runnable callback)
        {
            if (!rewards)
            {
                return false;
            }
            rewardCloses++;
            callback.run();
            return true;
        }

        @Override
        public boolean inside()
        {
            return inside;
        }

        @Override
        public boolean walkToBank()
        {
            walks++;
            return arrived;
        }

        @Override
        public boolean atSelectedBank()
        {
            return !differentBank && (arrived || bankOpen);
        }

        @Override
        public boolean atBankTravelArrival()
        {
            return ringArrival;
        }

        @Override
        public boolean feroxRestored()
        {
            return poolRestored;
        }

        @Override
        public boolean walkToFeroxPool()
        {
            poolWalks++;
            return poolArrived;
        }

        @Override
        public boolean drinkFeroxPool(Runnable callback)
        {
            if (!poolAvailable)
            {
                return false;
            }
            poolDrinks++;
            callback.run();
            return true;
        }

        @Override
        public boolean hasBankTravelRing()
        {
            return java.util.Arrays.stream(new int[] {2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566})
                .anyMatch(id -> count(id) > 0);
        }

        @Override
        public String bankTravelProblem()
        {
            return hasBankTravelRing() ? null : "Missing charged bank ring";
        }

        @Override
        public boolean requestBankTravel(Runnable callback)
        {
            teleports++;
            callback.run();
            return true;
        }

        @Override
        public boolean selectBankTravelDestination(Runnable callback)
        {
            selections++;
            callback.run();
            return true;
        }

        @Override
        public boolean bankTravelDialogueVisible()
        {
            return true;
        }

        @Override
        public int bankReplacementRing()
        {
            return java.util.Arrays.stream(new int[] {2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566}).filter(this::bankHas).findFirst().orElse(-1);
        }

        @Override
        public BarrowsConfig config()
        {
            return config;
        }

        @Override
        public boolean bankingOpen()
        {
            return bankOpen;
        }

        @Override
        public boolean bankCloseObserved()
        {
            return !bankOpen && !bankBusy;
        }

        @Override
        public boolean openBank(Runnable onDispatched)
        {
            opens++;
            onDispatched.run();
            return true;
        }

        @Override
        public boolean hasLoot()
        {
            return loot > 0;
        }

        @Override
        public int lootCount()
        {
            return loot;
        }

        @Override
        public boolean depositLoot(Runnable onDispatched)
        {
            deposits++;
            onDispatched.run();
            return true;
        }

        @Override
        public int count(int id)
        {
            return inventory.getOrDefault(id, 0);
        }

        @Override
        public int foodCount()
        {
            return count(385);
        }

        @Override
        public int freeSlots()
        {
            return slots;
        }

        @Override
        public int potionCount()
        {
            return count(2434) + count(139) + count(141) + count(143);
        }

        @Override
        public Map<Integer, Integer> restockItems()
        {
            return restock;
        }

        @Override
        public boolean bankHas(int id)
        {
            return bankStock.contains(id);
        }

        @Override
        public boolean withdraw(int id, int amount, Runnable onDispatched)
        {
            if (!available)
            {
                return false;
            }
            lastWithdraw = id;
            lastAmount = amount;
            withdrawals++;
            onDispatched.run();
            return true;
        }

        @Override
        public String itemName(int id)
        {
            return id == 385 ? "Shark" : "item " + id;
        }

        @Override
        public int bankCount(int id)
        {
            return bankStock.contains(id) ? 1000 : 0;
        }

        @Override
        public boolean stackable(int id)
        {
            return stackables.contains(id);
        }

        @Override
        public boolean withdrawOne(int id, Runnable onDispatched)
        {
            if (!available)
            {
                return false;
            }
            lastWithdraw = id;
            withdrawals++;
            onDispatched.run();
            return true;
        }

        @Override
        public boolean closeBank(Runnable onDispatched)
        {
            closes++;
            onDispatched.run();
            return true;
        }

        @Override
        public void completeTrip()
        {
            completed++;
            pending = false;
        }

        @Override
        public void fail(String reason)
        {
            failure = reason;
        }

        @Override
        public void status(String value)
        {
        }

        @Override
        public int tick()
        {
            return now;
        }
    }
}
