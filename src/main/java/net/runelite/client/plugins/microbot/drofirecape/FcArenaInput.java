/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/** Cave-run input hold. While held, input yielding is off so a hand on the mouse does not
 * pause the fight; it is re-enforced every loop and the user's own setting is restored on
 * release. Each fight gesture is checked against stop, pause and cave exit before it is sent. */
final class FcArenaInput {
    private static final String GROUP=MicrobotConfig.configGroup,KEY=MicrobotConfig.keyDisableInputYielding;
    private final Supplier<ConfigManager> configs;
    private final BooleanSupplier allowed;
    private ConfigManager held;
    private String originalSetting;
    private boolean originalDisabled;

    FcArenaInput(Supplier<ConfigManager> configs,BooleanSupplier allowed){this.configs=configs;this.allowed=allowed;}
    synchronized void hold(boolean run) {
        if(!run){release();return;}
        if(held==null) {
            ConfigManager manager=configs.get();
            if(manager==null)return;
            held=manager;originalSetting=manager.getConfiguration(GROUP,KEY);originalDisabled=InputArbiter.isDisabled();
        }
        if(!"true".equals(held.getConfiguration(GROUP,KEY)))held.setConfiguration(GROUP,KEY,true);
        InputArbiter.setDisabled(true);
    }
    synchronized boolean held(){return held!=null;}
    synchronized void release() {
        if(held==null)return;
        if(originalSetting==null)held.unsetConfiguration(GROUP,KEY);else held.setConfiguration(GROUP,KEY,originalSetting);
        InputArbiter.setDisabled(originalDisabled);
        held=null;originalSetting=null;
    }
    <T> T fight(Supplier<T> gesture,T cancelled){return fight(gesture,cancelled,null);}
    /** A gesture no longer wanted (stopped, paused, left the cave) returns the fallback. */
    <T> T fight(Supplier<T> gesture,T cancelled,BooleanSupplier wanted) {
        if(!held())return gesture.get();
        if(!allowed.getAsBoolean()||(wanted!=null&&!wanted.getAsBoolean()))return cancelled;
        return gesture.get();
    }
}
