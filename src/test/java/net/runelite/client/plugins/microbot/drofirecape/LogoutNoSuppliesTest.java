/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.TerminalLogoutPrompt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogoutNoSuppliesTest {
    @Test void theCheckboxIsVisibleAndOffByDefault()throws Exception {
        ConfigItem item=DroFirecapeConfig.class.getMethod("logoutNoSupplies").getAnnotation(ConfigItem.class);
        assertNotNull(item);assertEquals("Logout No Supplies",item.name());assertFalse(item.hidden());
        assertFalse(mock(DroFirecapeConfig.class,CALLS_REAL_METHODS).logoutNoSupplies());
    }
    @Test void thePopupIsShownOnlyAfterTheLoginScreenIsReached() {
        TerminalLogoutPrompt prompt=new TerminalLogoutPrompt();List<String> shown=new ArrayList<>();
        prompt.begin("Logged out in the Fight Caves with no brews left (wave 40). Your character is still in the cave.");
        assertFalse(prompt.tick(()->Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED,shown::add));
        assertTrue(shown.isEmpty(),"No popup while still logged in");
        assertTrue(prompt.tick(()->Rs2Player.TerminalLogoutState.LOGGED_OUT,shown::add));
        assertEquals(List.of("Logged out in the Fight Caves with no brews left (wave 40). Your character is still in the cave."),shown);
    }
}
