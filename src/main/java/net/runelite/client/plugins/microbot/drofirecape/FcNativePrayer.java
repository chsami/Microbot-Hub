/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.api.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Opt-in NO_MENU widget action. No mouse, tab switches, waits or generic click bounds. */
final class FcNativePrayer {
    private FcNativePrayer() { }
    static boolean dispatch(Client client,Rs2PrayerEnum prayer,boolean on) {
        if(!client.isClientThread()||client.getGameState()!=GameState.LOGGED_IN
            ||client.getLocalPlayer()==null||client.getWidget(prayer.getIndex())==null)return false;
        if((client.getVarbitValue(prayer.getVarbit())==1)==on)return false;
        if(on&&(client.getBoostedSkillLevel(Skill.PRAYER)<=0||client.getRealSkillLevel(Skill.PRAYER)<prayer.getLevel()))return false;
        // Exclusive protection ON actions switch directly; acknowledgement belongs to the tick owner.
        client.menuAction(-1,prayer.getIndex(),MenuAction.CC_OP,1,-1,on?"Activate":"Deactivate",prayer.getName());
        return true;
    }
    /** Both toggle packets are queued in the same client-thread invocation.
     * The second toggle deliberately ignores the unchanged server varbit. */
    static boolean reset(Client client,Rs2PrayerEnum prayer) {
        if(!client.isClientThread()||client.getGameState()!=GameState.LOGGED_IN
            ||client.getLocalPlayer()==null||client.getWidget(prayer.getIndex())==null
            ||client.getBoostedSkillLevel(Skill.PRAYER)<=0
            ||client.getVarbitValue(prayer.getVarbit())!=1)return false;
        client.menuAction(-1,prayer.getIndex(),MenuAction.CC_OP,1,-1,"Deactivate",prayer.getName());
        // Do not yield, wait for acknowledgement, consult a clock or test the
        // old varbit between halves. Once OFF is sent the paired ON is mandatory.
        client.menuAction(-1,prayer.getIndex(),MenuAction.CC_OP,1,-1,"Activate",prayer.getName());
        return true;
    }

}
