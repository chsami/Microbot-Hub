package net.runelite.client.plugins.microbot.fourtifysagility.util.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MarksOfGraceTrackerTest
{
    @Test
    public void existingMarksAreBaselineAndObservedPickupsAccumulate()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        assertEquals(0, tracker.getCollected());
        tracker.observe(41, true);
        tracker.observe(41, true);
        tracker.observe(44, true);
        assertEquals(4, tracker.getCollected());
    }

    @Test
    public void removingAndReplacingMarksDoesNotCountThemAgain()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        tracker.observe(42, true);
        tracker.observe(0, true);
        tracker.observe(40, true);
        tracker.observe(42, true);
        assertEquals(2, tracker.getCollected());
        tracker.observe(43, true);
        assertEquals(3, tracker.getCollected());
    }

    @Test
    public void bankDepositsAndWithdrawalsRebaselineWithoutCounting()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        tracker.observe(42, true);
        tracker.observe(0, false);
        tracker.observe(100, false);
        tracker.observe(100, true);
        tracker.observe(101, true);
        assertEquals(3, tracker.getCollected());
    }

    @Test
    public void depositingAllMarksAllowsLaterCoursePickups()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        tracker.observe(42, true);
        tracker.observe(0, false);
        tracker.observe(0, true);
        tracker.observe(1, true);
        assertEquals(3, tracker.getCollected());
    }

    @Test
    public void loginOrLoadingSnapshotDoesNotCountAsPickup()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        tracker.observe(41, true);
        tracker.suspend();
        tracker.observe(100, true);
        assertEquals(1, tracker.getCollected());
        tracker.observe(101, true);
        assertEquals(2, tracker.getCollected());
    }

    @Test
    public void newEnabledSessionClearsTotalAndExistingInventory()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        tracker.observe(41, true);
        tracker.reset();
        assertEquals(0, tracker.getCollected());
        tracker.observe(41, true);
        assertEquals(0, tracker.getCollected());
        tracker.observe(42, true);
        assertEquals(1, tracker.getCollected());
    }

    @Test
    public void invalidSnapshotIsNotTreatedAsAnEmptyInventory()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(40, true);
        tracker.observe(41, true);
        tracker.observe(-1, true);
        tracker.observe(41, true);
        tracker.observe(42, true);
        assertEquals(2, tracker.getCollected());
    }

    @Test
    public void totalCannotOverflowIntoNegativeValues()
    {
        InventoryGainTracker tracker = new InventoryGainTracker();
        tracker.observe(0, true);
        tracker.observe(Integer.MAX_VALUE, true);
        tracker.observe(0, false);
        tracker.observe(1, true);
        assertEquals(Integer.MAX_VALUE, tracker.getCollected());
    }
}
