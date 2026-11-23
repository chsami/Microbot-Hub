/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.SupplyAck;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SupplyDispatchRegressionTest {
    @Test void acknowledgementDeadlineStartsAtDispatchNotPreClickFrameTick() {
        SupplyAck ack=new SupplyAck();ack.sent(6685,12,100,SupplyAck.Kind.BREW,10000);
        assertEquals(SupplyAck.Result.WAITING,ack.observe(12,105,10010L,2));
        assertEquals(SupplyAck.Result.WAITING,ack.observe(12,106,11199L,2));
        assertEquals(SupplyAck.Result.TIMED_OUT,ack.observe(12,106,11200L,2));
    }
    @Test void lateDoseAfterTimeoutAndRetryCreditsExactlyOneRequestInOrder() {
        SupplyAck ack=new SupplyAck();ack.sent(6685,12,100,SupplyAck.Kind.BREW,10000);
        assertEquals(SupplyAck.Result.TIMED_OUT,ack.observe(12,105,13000L,5));ack.clearPending();
        ack.sent(6685,12,105,SupplyAck.Kind.BREW,13010);
        List<SupplyAck.Consumption> one=ack.observeDoses(11,8,13100);assertEquals(1,one.size());assertEquals(SupplyAck.Kind.BREW,one.get(0).kind());
        assertTrue(ack.observeDoses(11,8,13200).isEmpty());
        assertEquals(SupplyAck.Result.WAITING,ack.observe(11,106,13200L,5));
        List<SupplyAck.Consumption> second=ack.observeDoses(10,8,13400);assertEquals(1,second.size());
        assertEquals(SupplyAck.Result.CONSUMED,ack.observe(10,106,13400L,5));assertTrue(ack.observeDoses(10,8,13500).isEmpty());
    }
    @Test void simultaneousLateFamilyDecreasesPreserveBrewRestoreDispatchOrder() {
        SupplyAck ack=new SupplyAck();ack.observeDoses(12,8,9000);
        ack.sent(6685,12,100,SupplyAck.Kind.BREW,10000);ack.clearPending();ack.sent(3024,8,102,SupplyAck.Kind.RESTORE,11200);
        List<SupplyAck.Consumption> used=ack.observeDoses(11,7,11400);assertEquals(2,used.size());
        assertEquals(SupplyAck.Kind.BREW,used.get(0).kind());assertEquals(SupplyAck.Kind.RESTORE,used.get(1).kind());assertTrue(ack.observeDoses(11,7,11500).isEmpty());
    }
    @Test void lostBottleAndUnrequestedDoseDoNotInventSeveralUses() {
        SupplyAck ack=new SupplyAck();ack.observeDoses(12,8,9000);ack.sent(6685,12,100,SupplyAck.Kind.BREW,10000);
        assertTrue(ack.observeDoses(8,8,10500).isEmpty());assertTrue(ack.observeDoses(8,7,10600).isEmpty());
    }
    @Test void healingTimeoutIsBoundedAndFullResetDropsLateEvidence() {
        SupplyAck ack=new SupplyAck();ack.sent(6685,12,100,SupplyAck.Kind.BREW,10000);
        assertEquals(SupplyAck.Result.TIMED_OUT,ack.observe(12,103,11800L,3));ack.reset();assertTrue(ack.observeDoses(11,8,12000).isEmpty());
    }
    @Test void regularLegacyOverloadKeepsTickDeadlineAndObservedConsumption() {
        SupplyAck ack=new SupplyAck();ack.sent(6685,1,100,SupplyAck.Kind.BREW);
        assertEquals(SupplyAck.Result.WAITING,ack.observe(1,104));assertEquals(SupplyAck.Result.TIMED_OUT,ack.observe(1,105));
        assertEquals(SupplyAck.Result.CONSUMED,ack.observe(0,101));
    }
}
