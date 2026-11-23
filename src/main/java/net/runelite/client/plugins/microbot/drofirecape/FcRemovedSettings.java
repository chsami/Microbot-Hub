/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Settings 0.3.89 removed. A player who had one on relied on the plugin leaving part of the run to them, so the first
 * start after the update stops once with an explanation instead of silently running the full automation. The keys are
 * cleared at the same time, so the next start runs normally.
 */
final class FcRemovedSettings {
    static final Map<String,String> REMOVED=new LinkedHashMap<>();
    static {
        REMOVED.put("prayersOnly","Prayers only");
        REMOVED.put("manualMovement","Manual movement");
        REMOVED.put("startAtEntrance","Start at entrance");
        REMOVED.put("logoutNoSupplies","Logout No Supplies");
    }
    private FcRemovedSettings() { }

    /** The names of the removed settings that were on. Every removed key is cleared, on or not. */
    static List<String> takeEnabled(Function<String,String> read,Consumer<String> unset) {
        List<String> enabled=new ArrayList<>();
        for(Map.Entry<String,String> setting:REMOVED.entrySet()) {
            String value=read.apply(setting.getKey());
            if(value==null)continue;
            if(Boolean.parseBoolean(value.trim()))enabled.add(setting.getValue());
            unset.accept(setting.getKey());
        }
        return enabled;
    }

    static String message(List<String> enabled) {
        return "[Dro] Firecape "+DroFirecapePlugin.version+" removed "+String.join(", ",enabled)
            +", which you had on. The plugin now always runs the whole Fight Cave"
            +" itself: banking, travel, movement, attacks, prayers and supplies. It did not start this time and the old"
            +" setting"+(enabled.size()==1?" has":"s have")+" been cleared. Turn the plugin on again to run it.";
    }
}
