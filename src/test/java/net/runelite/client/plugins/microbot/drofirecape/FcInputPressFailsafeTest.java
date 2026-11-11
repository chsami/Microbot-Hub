/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Colosseum 1.0.80 failsafe: no tab change until the game has read the last mouse press. */
class FcInputPressFailsafeTest {
    @Test void aTabChangeWaitsForTheLastPressToBeRead() {
        Client client=mock(Client.class);
        when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.PRAYER.getVarcIntIndex());
        FcInput.pressing();
        assertFalse(FcInput.openTab(client,InterfaceTab.INVENTORY));
        verify(client,never()).runScript(anyInt(),anyInt());
        assertTrue(FcInput.openTab(client,InterfaceTab.PRAYER),"The open tab needs no change");
        FcInput.pressRead();
        FcInput.openTab(client,InterfaceTab.INVENTORY);
        verify(client).runScript(915,InterfaceTab.INVENTORY.getVarcIntIndex());
    }
    @Test void prayerPressesAreRecognisedByThePrayerBookGroup() {
        assertTrue(FcInput.prayer(new net.runelite.client.plugins.microbot.util.menu.NewMenuEntry().param1(35454999)),"Protect from Melee");
        assertFalse(FcInput.prayer(new net.runelite.client.plugins.microbot.util.menu.NewMenuEntry().param1(9764864)),"Inventory");
    }
    @Test void anUnreadPressStopsHoldingAfter150Ms() {
        assertFalse(FcInput.pressUnread(0));
    }
}
