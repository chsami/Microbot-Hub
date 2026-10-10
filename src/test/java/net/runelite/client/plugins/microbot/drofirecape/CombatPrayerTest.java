/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CombatPrayerTest {
    @Test public void absentOrSuspendedOwnerCannotApproveCaveActions() {
        FcActions actions=new FcActions();
        assertFalse(actions.combatProtect(Protection.MAGIC));assertFalse(actions.overheadActive(Protection.NONE));
        assertFalse(actions.offenceReady());assertFalse(actions.prayersObservedOff());
        FcTickPrayers driver=mock(FcTickPrayers.class);actions.tickPrayerDriver(driver);
        when(driver.protectionReady()).thenReturn(true);
        assertFalse(actions.combatProtect(Protection.MAGIC));verify(driver,never()).protectionReady();
    }
    @Test public void caveGatesUseTheSingleOwnersObservedProtection() {
        FcActions actions=new FcActions();FcTickPrayers driver=mock(FcTickPrayers.class);
        actions.tickPrayerDriver(driver);when(driver.ownsInput()).thenReturn(true);
        assertFalse(actions.combatProtect(Protection.MAGIC));
        when(driver.protectionReady()).thenReturn(true);assertTrue(actions.overheadActive(Protection.RANGE));
        when(driver.prayersOffReady()).thenReturn(true);assertTrue(actions.prayersObservedOff());
        actions.tickPrayerDriver(null);assertFalse(actions.combatProtect(Protection.NONE));
    }
}
