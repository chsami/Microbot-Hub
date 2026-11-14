package net.runelite.client.plugins.microbot.droefficientwalker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BankActionAckTest {
    @Test
    public void delayedTabletInventoryCannotAuthorizeASecondWithdrawal() {
        BankActionAck ack = new BankActionAck();
        ack.begin(31441, 0, 1, 100, 1000, 10000);
        assertEquals(BankActionAck.Result.WAITING, ack.observe(0, 104, 3400));
        assertTrue(ack.pending());
        rejectRepeat(() -> ack.begin(31441, 0, 1, 104, 3400, 10000));
        assertEquals(BankActionAck.Result.ACKNOWLEDGED, ack.observe(1, 108, 5800));
        assertFalse(ack.pending());
    }

    @Test
    public void partialCoinCountAndSameTickSnapshotDoNotAcknowledgeRequestedAmount() {
        BankActionAck ack = new BankActionAck();
        ack.begin(995, 0, 5000, 100, 1000, 10000);
        assertEquals(BankActionAck.Result.WAITING, ack.observe(5000, 100, 1001));
        assertEquals(BankActionAck.Result.WAITING, ack.observe(2000, 101, 1600));
        assertEquals(BankActionAck.Result.ACKNOWLEDGED, ack.observe(5000, 102, 2200));
    }

    @Test
    public void timeoutStaysLatchedAndDoesNotAuthorizeAnUncertainRepeat() {
        BankActionAck ack = new BankActionAck();
        ack.begin(31441, 0, 1, 10, 1000, 10000);
        assertEquals(BankActionAck.Result.TIMED_OUT, ack.observe(0, 28, 11000));
        assertTrue(ack.pending());
        rejectRepeat(() -> ack.begin(995, 0, 5000, 29, 11600, 10000));
    }

    private void rejectRepeat(Runnable dispatch) {
        try { dispatch.run(); fail("Unacknowledged withdrawal accepted a repeat"); }
        catch (IllegalStateException expected) { }
    }
}
