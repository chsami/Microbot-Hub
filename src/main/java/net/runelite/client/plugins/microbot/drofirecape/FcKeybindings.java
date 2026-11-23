/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.function.BooleanSupplier;

/**
 * Startup keybinding step. The player's account-wide game keybindings are never changed: the script uses the live
 * bindings and falls back to clicking the visible tab when an F-key does not open it.
 */
final class FcKeybindings {
    static final String RETAINED="Retained the player's game keybindings; visible-tab fallback available";
    private boolean done;
    private String result="";

    FcKeybindings(BooleanSupplier allowed) { }
    void reset(){done=false;result="";}
    String status(){return "Using the current game keybindings";}
    String result(){return result;}

    boolean step(boolean inCave) {
        if(!done){done=true;result=RETAINED;}
        return true;
    }
}
