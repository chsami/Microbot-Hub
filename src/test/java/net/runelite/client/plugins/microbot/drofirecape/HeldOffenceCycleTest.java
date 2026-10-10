/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HeldOffenceCycleTest {
    @Test public void overheadResetsWhileOffenceStaysOnUntilCombatEnds() {
        OneTickPrayerCycle cycle=new OneTickPrayerCycle(4);
        int magic=4,offence=8;
        cycle.beginTick(10,1_000_000_000L,magic|offence);
        cycle.beginTick(11,1_600_000_000L,magic|offence);
        cycle.beginTick(12,2_200_000_000L,magic|offence);
        List<OneTickPrayerCycle.Command> fighting=cycle.plan(2_230_000_000L,magic|offence,magic|offence,7,7,true);
        assertEquals(1,fighting.size());assertEquals(2,fighting.get(0).channel);
        assertEquals(OneTickPrayerCycle.Type.RESET,fighting.get(0).type);
        List<OneTickPrayerCycle.Command> paused=cycle.plan(2_230_000_000L,magic|offence,magic,7,7,true);
        assertTrue(paused.stream().anyMatch(c->c.channel==3&&c.type==OneTickPrayerCycle.Type.OFF));
    }
}
