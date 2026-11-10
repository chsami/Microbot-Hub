/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Kind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FcSpecialsTest {
    @Test void everyUsedSpecialHasItsCost() {
        assertEquals(500,FcSpecials.cost("Magic shortbow (i)"));
        assertEquals(550,FcSpecials.cost("Magic shortbow"));
        assertEquals(500,FcSpecials.cost("Toxic blowpipe"));
        assertEquals(250,FcSpecials.cost("Rosewood blowpipe"));
        assertEquals(750,FcSpecials.cost("Zaryte crossbow"));
        assertEquals(600,FcSpecials.cost("Dragon crossbow"));
        assertEquals(500,FcSpecials.cost("Armadyl crossbow"),"OSRS Wiki: Armadyl Eye 50%");
        assertEquals(-1,FcSpecials.cost("Dragon hunter crossbow"),"No special");
        assertEquals(-1,FcSpecials.cost("Twisted bow"));
    }
    @Test void onlyTheToxicBlowpipeWaitsForMissingHpAndBowsSkipSmallTargets() {
        assertTrue(FcSpecials.heals("Toxic blowpipe"));
        assertFalse(FcSpecials.heals("Rosewood blowpipe"));
        assertFalse(FcSpecials.worth("Magic shortbow (i)",Kind.BAT));
        assertTrue(FcSpecials.worth("Magic shortbow (i)",Kind.MAGER));
        assertTrue(FcSpecials.worth("Rosewood blowpipe",Kind.BABY));
    }
}
