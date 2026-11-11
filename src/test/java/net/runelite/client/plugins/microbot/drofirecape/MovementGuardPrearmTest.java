/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementGuardPrearmTest {
    private static final int RANGE=CombatPlanner.bit(Protection.RANGE),MAGIC=CombatPlanner.bit(Protection.MAGIC),
        MELEE=CombatPlanner.bit(Protection.MELEE);
    private static TickProtection.Decision forecast(Protection p,int due,int uncertain) {
        return new TickProtection.Decision(1,p,due,uncertain,"forecast");
    }
    private static boolean wins(TickProtection.Decision d,Protection guard,boolean waiting) {
        return FcPrayers.movementGuardWins(d,guard,false,waiting);
    }

    @Test void wave38UncertainRangeForecastNoLongerHoldsAPreArmedMagicMove() {
        assertTrue(wins(forecast(Protection.RANGE,RANGE,RANGE),Protection.MAGIC,true));
    }
    @Test void certainDueAttacksStillOutrankThePreArm() {
        assertFalse(wins(forecast(Protection.RANGE,RANGE,0),Protection.MAGIC,true),"An observed Tok-Xil attack keeps Range");
        assertFalse(wins(forecast(Protection.MELEE,MELEE|RANGE,RANGE),Protection.MAGIC,true),"A certain melee hit keeps its guard");
    }
    @Test void walkInProgressKeepsTheExistingArbiter() {
        assertFalse(wins(forecast(Protection.RANGE,RANGE,RANGE),Protection.MAGIC,false));
    }
    @Test void onlyMagicPreArmsGetTheNewRule() {
        assertFalse(wins(forecast(Protection.MAGIC,MAGIC,MAGIC),Protection.RANGE,true));
        assertFalse(wins(forecast(Protection.RANGE,RANGE,RANGE),Protection.MELEE,true));
    }
    @Test void existingRulesAreUnchanged() {
        assertTrue(wins(forecast(Protection.NONE,0,0),Protection.RANGE,false),"Clear forecast");
        assertTrue(wins(forecast(Protection.MAGIC,MAGIC,0),Protection.MAGIC,false),"Same guard");
        assertTrue(FcPrayers.movementGuardWins(forecast(Protection.RANGE,RANGE,RANGE),Protection.MAGIC,true,true),
            "Hidden Jad: no Jad demand, movement guard applies");
    }
}
