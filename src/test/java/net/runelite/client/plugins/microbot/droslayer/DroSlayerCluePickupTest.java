package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerCluePickupTest {
    @Test public void rejectionStopsHardBoxesAndCluesButLeavesOtherLootEligible() {
        var policy = new DroSlayerCluePickup();
        assertTrue(policy.mayAttempt("Scroll box (hard)", 1000));
        assertTrue(policy.onMessage("You can't take any more of that tier of scroll box.", 1500));
        assertFalse(policy.mayAttempt("Scroll box (hard)", 100000));
        assertFalse(policy.mayAttempt("Clue scroll (hard)", 100000));
        assertTrue(policy.mayAttempt("Clue scroll (elite)", 100000));
        assertTrue(policy.mayAttempt("Coins", 100001));
        assertFalse(policy.onMessage("You load the cannon with 25 cannonballs.", 100002));
    }

    @Test public void attemptsAreSpacedWhileWaitingForChatAndRestartClearsTheBlock() {
        var policy = new DroSlayerCluePickup();
        assertTrue(policy.mayAttempt("Scroll box (hard)", 0));
        assertFalse(policy.mayAttempt("Scroll box (hard)", 4999));
        assertFalse(policy.mayAttempt("Scroll box (elite)", 4999));
        assertTrue(policy.mayAttempt("Scroll box (hard)", 5000));
        assertTrue(policy.onMessage("You can’t take any more of that tier of scroll box.", 5500));
        policy.reset();
        assertTrue(policy.mayAttempt("Scroll box (hard)", 5501));
    }

    @Test public void staleOrUnrelatedChatCannotBlockAClueTier() {
        var policy = new DroSlayerCluePickup();
        assertFalse(policy.onMessage("You can't take any more of that tier of scroll box.", 0));
        assertTrue(policy.mayAttempt("Scroll box (hard)", 1000));
        assertFalse(policy.onMessage("You can't take any more of that tier of scroll box.", 16001));
        assertTrue(policy.mayAttempt("Scroll box (hard)", 16001));
        assertTrue(policy.onMessage("You already have a clue scroll of this tier.", 16002));
        assertFalse(policy.mayAttempt("Clue scroll (hard)", 30000));
    }
}
