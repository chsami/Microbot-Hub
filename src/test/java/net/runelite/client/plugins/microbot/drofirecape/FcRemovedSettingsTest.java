/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FcRemovedSettingsTest {
    @Test void anEnabledRemovedSettingStopsOnceAndEveryOldKeyIsCleared() {
        Map<String,String> saved=new HashMap<>(Map.of("prayersOnly","true","startAtEntrance","false","restorePrayer","25"));
        List<String> cleared=new ArrayList<>();
        List<String> enabled=FcRemovedSettings.takeEnabled(saved::get,key->{cleared.add(key);saved.remove(key);});
        assertEquals(List.of("Prayers only"),enabled);
        assertEquals(List.of("prayersOnly","startAtEntrance"),cleared);
        assertTrue(saved.containsKey("restorePrayer"),"live settings are untouched");
        assertTrue(FcRemovedSettings.message(enabled).contains("Prayers only"));
        assertTrue(FcRemovedSettings.takeEnabled(saved::get,saved::remove).isEmpty(),"the next start runs normally");
    }
    @Test void noOldKeysMeansANormalStart() {
        List<String> cleared=new ArrayList<>();
        assertTrue(FcRemovedSettings.takeEnabled(key->null,cleared::add).isEmpty());
        assertTrue(cleared.isEmpty());
    }
    @Test void keybindingsAreNeverChanged() {
        FcKeybindings keybindings=new FcKeybindings(()->true);
        assertTrue(keybindings.step(false),"a fresh start outside the cave finishes at once");
        assertEquals(FcKeybindings.RETAINED,keybindings.result());
    }
}
