/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.prayer.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection;

/** All input executes off the client thread. Read lambdas return immutable dispatch details. */
class OptionalActions extends FcActions {
    @Override
    /** A missing or suspended cave owner never falls back to a second writer. */
    boolean combatProtect(Protection ignored) {
        FcTickPrayers driver=ownedPrayerDriver();return driver!=null&&(driver.protectionReady()||driver.exhaustedCombatReady());
    }
    @Override
    boolean special(){return special(()->true);}
    boolean special(java.util.function.BooleanSupplier valid) {
        if(!uiReady(1800)||!valid.getAsBoolean())return false;
        Rectangle rect=read(()->{
            Client c=client();
            if(c.getGameState()!=GameState.LOGGED_IN||c.getVarpValue(300)<500||c.getVarpValue(301)!=0)return null;
            for(int id:new int[]{InterfaceID.Orbs.SPECBUTTON,10485795}) {
                Widget w=c.getWidget(id);if(w==null||w.isHidden())continue;
                Rectangle bounds=FcPrayerUi.visibleBounds(w.getBounds(),c.getCanvasWidth(),c.getCanvasHeight());
                if(bounds!=null)return bounds;
            }
            return null;
        },null);
        if(rect==null)return false;
        synchronized(inputLock){if(!valid.getAsBoolean())return false;Microbot.getMouse().click(rect);}
        uiSent();return true;
    }
    @Override
    /** Entry protection uses the same live acknowledgement as the visible writer. */
    boolean entryProtectionReady() {return entryProtectionReady(false);}
    boolean entryProtectionReady(boolean conservation) {
        // Match the final entry check's live prayer state. The shared cached
        // accessor can disagree with the visible writer after startup toggles.
        return outsidePrayers(conservation?Protection.NONE:Protection.MELEE);
    }
    @Override
    /** Re-read the predictor after supply/prayer/UI work, immediately before dispatch. */
    boolean enterPredictedRotation(FcPredictorGate gate,int expectedRotation) {
        return enterPredictedRotation(gate,expectedRotation,false);
    }
    boolean enterPredictedRotation(FcPredictorGate gate,int expectedRotation,boolean conservation) {
        if(!uiReady(1800))return false;
        synchronized(inputLock) {
            Click click=read(()->{
                Client c=client();WorldView v=c.getTopLevelWorldView();
                if(c.getGameState()!=GameState.LOGGED_IN||v==null||v.isInstance()||c.getLocalPlayer()==null)return null;
                if(!gate.entryReady(predictorOnClientThread(false),System.currentTimeMillis(),expectedRotation))return null;
                if(c.getBoostedSkillLevel(Skill.PRAYER)<=1)return null;
                if(conservation) {
                    // Recheck all combat prayer slots after the outside cleanup,
                    // rather than treating an attempted OFF as acknowledgement.
                    for(Rs2PrayerEnum prayer:COMBAT_PRAYERS)
                        if(c.getVarbitValue(prayer.getVarbit())==1)return null;
                } else if(!c.isPrayerActive(Prayer.PROTECT_FROM_MELEE))return null;
                TileObject entrance=findObject(11833,"Enter");
                if(entrance==null||entrance.getWorldLocation().distanceTo2D(c.getLocalPlayer().getWorldLocation())>4)return null;
                return objectClick(entrance,"Enter");
            },null);
            if(invoke(click)){uiSent();return true;}return false;
        }
    }}
