/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BowPrayerClockTest {
    @Test void offenceIsOnOnlyForTheTickBeforeEachShot() {
        BowPrayerClock clock=new BowPrayerClock();
        clock.shot(10,1,1,426);
        assertFalse(clock.wantOn(9,1,1,true,true),"No offence before the first observed shot");
        assertFalse(clock.wantOn(11,1,1,true,true),"Unknown cadence: the next shot is not boosted");
        clock.shot(13,1,1,426);
        for(int tick=13;tick<=15;tick++)
            assertEquals(tick==15,clock.wantOn(tick,1,1,true,true),"One interval: on at 15 for the shot at 16, tick "+tick);
        clock.shot(16,1,1,426);
        assertFalse(clock.wantOn(16,1,1,true,true),"Off on the shot tick");
        assertFalse(clock.wantOn(17,1,1,true,true));
        assertTrue(clock.wantOn(19,1,1,true,true),"A late shot holds instead of cycling a stale clock");
        assertFalse(clock.wantOn(14,2,1,true,true),"Another weapon has no cadence yet");
    }
    @Test void atlatlSwingsSeedTheCadence() {
        BowPrayerClock clock=new BowPrayerClock();
        assertTrue(BowPrayerClock.attackAnimation(11057,false));
        clock.swing(10,1,1,11057,false);
        clock.swing(14,1,1,11057,false);
        for(int tick=14;tick<=17;tick++)
            assertEquals(tick==17,clock.wantOn(tick,1,1,true,true),"On one tick before the swing at 18, tick "+tick);
    }
}
