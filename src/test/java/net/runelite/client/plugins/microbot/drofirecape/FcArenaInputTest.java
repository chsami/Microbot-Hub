/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FcArenaInputTest {
    @AfterEach void reset(){InputArbiter.resetForTest();}

    private static ConfigManager configs(AtomicReference<String> value) {
        ConfigManager configs=mock(ConfigManager.class);
        when(configs.getConfiguration("microbot","disableInputYielding")).thenAnswer(i->value.get());
        doAnswer(i->{value.set(String.valueOf((Object)i.getArgument(2)));return null;}).when(configs).setConfiguration(eq("microbot"),eq("disableInputYielding"),any(Object.class));
        doAnswer(i->{value.set(String.valueOf((Object)i.getArgument(2)));return null;}).when(configs).setConfiguration(eq("microbot"),eq("disableInputYielding"),anyString());
        doAnswer(i->{value.set(null);return null;}).when(configs).unsetConfiguration("microbot","disableInputYielding");
        return configs;
    }

    @Test void caveRunForcesNoYieldEveryLoopAndExitRestoresTheUserSetting() {
        AtomicReference<String> value=new AtomicReference<>("false");
        InputArbiter.setDisabled(false);
        ConfigManager manager=configs(value);
        FcArenaInput input=new FcArenaInput(()->manager,()->true);
        input.hold(true);
        assertTrue(input.held());assertEquals("true",value.get());assertTrue(InputArbiter.isDisabled());
        value.set("false");InputArbiter.setDisabled(false);
        input.hold(true);
        assertEquals("true",value.get());assertTrue(InputArbiter.isDisabled());
        input.hold(false);
        assertFalse(input.held());assertEquals("false",value.get());assertFalse(InputArbiter.isDisabled());
    }
    @Test void pauseOrStopCancelsTheGestureAndReturnsTheFallback() {
        AtomicBoolean allowed=new AtomicBoolean(true);
        FcArenaInput input=new FcArenaInput(()->configs(new AtomicReference<>("false")),allowed::get);
        input.hold(true);
        allowed.set(false);
        assertEquals("not sent",input.fight(()->"sent","not sent"));
        allowed.set(true);
        assertEquals("sent",input.fight(()->"sent","not sent"));
        assertEquals("not sent",input.fight(()->"sent","not sent",()->false));
    }
    @Test void outsideTheCaveGesturesRunUnchanged() {
        FcArenaInput input=new FcArenaInput(()->configs(new AtomicReference<>("false")),()->false);
        assertEquals("sent",input.fight(()->"sent","not sent"));
    }
    @Test void missingConfigManagerLeavesInputUnchanged() {
        InputArbiter.setDisabled(false);
        FcArenaInput input=new FcArenaInput(()->null,()->true);
        input.hold(true);
        assertFalse(input.held());assertFalse(InputArbiter.isDisabled());
    }
}
