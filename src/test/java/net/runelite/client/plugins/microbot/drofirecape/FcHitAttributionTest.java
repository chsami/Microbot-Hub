/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FcHitAttributionTest {
    private static FcHitAttribution prayers(Protection p,int from,int to) {
        FcHitAttribution a=new FcHitAttribution();
        for(int t=from;t<=to;t++)a.tick(t,p);
        return a;
    }
    @Test void landedProjectileNamesItsSourceAndPrayerIsJudgedOnTheAttackTick() {
        FcHitAttribution a=prayers(Protection.MAGIC,8,10);
        a.attack(10,5,Kind.RANGER,Protection.RANGE);
        a.attack(10,6,Kind.MAGER,Protection.MAGIC);
        a.tick(11,Protection.RANGE);a.tick(12,Protection.RANGE);a.tick(13,Protection.RANGE);a.tick(14,Protection.RANGE);
        a.shot(5,300,200);
        FcHitAttribution.Hit hit=a.hit(14,305,12,16);
        assertEquals(5,hit.attacker);assertEquals(Kind.RANGER,hit.kind);assertEquals("projectile",hit.method);
        assertEquals(4,hit.delay());assertEquals(Protection.MAGIC,hit.prayerAtAttack);assertEquals(Protection.RANGE,hit.prayerAtHit);
        assertEquals("simultaneous",hit.cause(),"Magic was protecting the Ket-Zek attack on the same tick");
        assertEquals("firecape_hit",hit.values(41).get("record"));assertEquals(41,hit.values(41).get("wave"));
    }
    @Test void meleeLandsOnItsAttackTickAndABlockedZeroIsAttributed() {
        FcHitAttribution a=prayers(Protection.MELEE,18,20);
        a.attack(20,7,Kind.MELEER,Protection.MELEE);
        FcHitAttribution.Hit hit=a.hit(20,600,0,12);
        assertEquals(7,hit.attacker);assertEquals("melee",hit.method);assertEquals("blocked",hit.cause());
    }
    @Test void withoutProjectileEvidenceDamagePointsAtTheUnprotectedShotInFlight() {
        FcHitAttribution a=prayers(Protection.MAGIC,28,34);
        a.attack(30,1,Kind.MAGER,Protection.MAGIC);a.attack(30,2,Kind.RANGER,Protection.RANGE);
        FcHitAttribution.Hit hit=a.hit(34,900,13,16);
        assertEquals(2,hit.attacker);assertEquals("window",hit.method);assertEquals(2,hit.candidates);assertEquals("simultaneous",hit.cause());
        FcHitAttribution.Hit blocked=a.hit(34,900,0,12);
        assertEquals(1,blocked.attacker,"The zero belongs to the protected Ket-Zek attack");assertEquals("blocked",blocked.cause());
    }
    @Test void shotsInFlightAreConsumedInOrderAndCausesAreNamed() {
        FcHitAttribution a=prayers(Protection.NONE,38,49);
        a.attack(40,3,Kind.RANGER,Protection.RANGE);a.attack(44,3,Kind.RANGER,Protection.RANGE);
        assertEquals(40,a.hit(45,0,8,16).attackTick);
        FcHitAttribution.Hit second=a.hit(49,0,9,16);
        assertEquals(44,second.attackTick);assertEquals("prayer-off",second.cause());
        a.tick(60,Protection.MELEE);a.attack(60,4,Kind.RANGER,Protection.RANGE);
        assertEquals("wrong-prayer",a.hit(64,0,5,16).cause());
    }
    @Test void noCandidateStaysUnattributedInsteadOfGuessing() {
        FcHitAttribution a=prayers(Protection.RANGE,1,5);
        FcHitAttribution.Hit hit=a.hit(5,100,4,16);
        assertFalse(hit.attributed());assertEquals("unattributed",hit.cause());assertEquals(-1,hit.values(3).get("attacker"));
        assertFalse(hit.values(3).containsValue(null),"Recorder diagnostics reject null values");
    }
}
