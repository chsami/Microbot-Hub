/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrayerLedgerTest {
    private static final int MELEE=1,RANGE=2,MAGIC=4,OVERHEADS=7,OFFENCE=8;
    private static PrayerLedger synced(int observed) {
        PrayerLedger ledger=new PrayerLedger(4);
        ledger.beginTick(10,1_000_000_000L,observed);
        ledger.beginTick(11,1_600_000_000L,observed);
        ledger.beginTick(12,2_200_000_000L,observed);
        return ledger;
    }
    @Test void aDueHitIsOneOnNeverAResetPair() {
        PrayerLedger ledger=synced(MAGIC);
        assertTrue(ledger.plan(2_230_000_000L,MAGIC,MAGIC,OVERHEADS).isEmpty(),"An active guard is not toggled off and on");
        List<PrayerLedger.Command> on=synced(0).plan(2_230_000_000L,0,MAGIC,OVERHEADS);
        assertEquals(1,on.size());assertEquals(2,on.get(0).channel);assertEquals(PrayerLedger.Type.ON,on.get(0).type);
    }
    @Test void aVerifiedGapFlicksTheGuardOffEarlyInTheTick() {
        PrayerLedger ledger=synced(MAGIC);
        List<PrayerLedger.Command> off=ledger.plan(2_230_000_000L,MAGIC,0,OVERHEADS);
        assertEquals(1,off.size());assertEquals(PrayerLedger.Type.OFF,off.get(0).type);
        assertTrue(synced(MAGIC).plan(2_550_000_000L,MAGIC,0,OVERHEADS).isEmpty(),"No late OFF that could race the next hit");
    }
    @Test void aStyleSwitchIsASingleOnOfTheNewOverhead() {
        List<PrayerLedger.Command> switched=synced(MAGIC).plan(2_230_000_000L,MAGIC,RANGE,OVERHEADS);
        assertEquals(1,switched.size());assertEquals(1,switched.get(0).channel);assertEquals(PrayerLedger.Type.ON,switched.get(0).type);
    }
    @Test void offenceFlicksOffAfterTheShot() {
        List<PrayerLedger.Command> off=synced(MAGIC|OFFENCE).plan(2_230_000_000L,MAGIC|OFFENCE,MAGIC,OVERHEADS);
        assertTrue(off.stream().anyMatch(c->c.channel==3&&c.type==PrayerLedger.Type.OFF));
    }
    @Test void noHitDueNeverBlocksInputWhileTheOffIsPending() {
        PrayerLedger ledger=synced(MAGIC);
        ledger.sent(new PrayerLedger.Command(2,PrayerLedger.Type.OFF),OVERHEADS);
        assertTrue(ledger.committed(MAGIC,0,OVERHEADS));
        assertFalse(ledger.committed(0,MELEE,OVERHEADS));
    }
    @Test void aHeldPrayerIsResetOnceEarlyInEachTick() {
        PrayerLedger ledger=synced(MAGIC|OFFENCE);
        List<PrayerLedger.Command> reset=ledger.plan(2_230_000_000L,MAGIC|OFFENCE,MAGIC|OFFENCE,OVERHEADS,4);
        assertEquals(1,reset.size());assertEquals(PrayerLedger.Type.RESET,reset.get(0).type);
        assertEquals(MAGIC|OFFENCE,reset.get(0).mask,"Every active slot is toggled together");
        ledger.sent(reset.get(0),OVERHEADS);
        assertTrue(ledger.plan(2_240_000_000L,MAGIC|OFFENCE,MAGIC|OFFENCE,OVERHEADS,4).isEmpty(),"Once per tick");
        assertEquals(0,ledger.pendingMask(),"A reset ends ON and awaits no acknowledgement");
        assertTrue(ledger.committed(MAGIC|OFFENCE,MAGIC,OVERHEADS));
        assertTrue(synced(MAGIC).plan(2_400_000_000L,MAGIC,MAGIC,OVERHEADS,4).isEmpty(),"No late reset that could split across ticks");
        assertTrue(synced(MAGIC|OFFENCE).plan(2_230_000_000L,MAGIC|OFFENCE,MAGIC|OFFENCE,OVERHEADS,1).isEmpty(),
            "A limited input never resets only part of the active set");
        assertEquals(PrayerLedger.Type.RESET,synced(MAGIC).plan(2_230_000_000L,MAGIC,MAGIC,OVERHEADS,1).get(0).type);
    }
    @Test void aPendingChangeIsNeverReset() {
        assertEquals(PrayerLedger.Type.OFF,synced(MAGIC|OFFENCE).plan(2_230_000_000L,MAGIC|OFFENCE,MAGIC,OVERHEADS,4).get(0).type);
        PrayerLedger ledger=synced(MAGIC);
        ledger.sent(new PrayerLedger.Command(3,PrayerLedger.Type.ON),OVERHEADS);
        assertTrue(ledger.plan(2_230_000_000L,MAGIC,MAGIC|OFFENCE,OVERHEADS,4).isEmpty());
    }
}
