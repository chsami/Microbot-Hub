/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Startup-only native keybinding reset. Never opens settings during a live wave. */
final class FcKeybindings {
    private enum Step { OPEN, SEARCH, TYPE, KEYBINDS, RESET, OBSERVE, CLOSE, RETURN, DONE }
    private final BooleanSupplier allowed;
    private final FcPrayerUi ui;
    private Step step=Step.OPEN;
    private long startedAt,actionAt;
    private String status="Opening game keybindings",result="";
    private boolean escapeClosed;
    private int closingPanels=-1;
    private boolean closeKeySent,closeClickSent;

    FcKeybindings(BooleanSupplier allowed) {
        this.allowed=allowed;ui=new FcPrayerUi(allowed);
    }
    void reset(){step=Step.OPEN;startedAt=actionAt=0;result="";escapeClosed=false;closingPanels=-1;closeKeySent=closeClickSent=false;ui.reset();}
    String status(){return status;}
    String result(){return result;}

    boolean step(boolean inCave) {
        if(step==Step.DONE)return true;
        if(inCave) {
            result="Live cave resume: retained game bindings; visible-tab fallback available";
            step=Step.DONE;return true;
        }
        if(!allowed.getAsBoolean())return false;
        long now=System.currentTimeMillis();
        if(startedAt==0) {
            startedAt=now;
            escapeClosed=FcActions.read(()->Microbot.getClient().getVarbitValue(VarbitID.KEYBINDING_ESC_TO_CLOSE)==1,false);
        }
        if(now-startedAt>12_000&&step.ordinal()<Step.CLOSE.ordinal()) {
            result="Default-keybinding setup timed out; using live bindings and visible-tab fallback";
            step=Step.CLOSE;
        }
        if(now-actionAt<400)return false;
        switch(step) {
            case OPEN:
                if(visible(InterfaceID.Keybinding.DEFAULT_BUTTON)){step=Step.RESET;break;}
                if(visible(InterfaceID.Settings.UNIVERSE)){step=Step.SEARCH;break;}
                if(ui.open(InterfaceTab.SETTINGS)&&click(InterfaceID.SettingsSide.SETTINGS_OPEN))actionAt=now;
                break;
            case SEARCH:
                status="Finding Keybinds in game settings";
                if(click(InterfaceID.Settings.SEARCHBAR_IMAGE)){step=Step.TYPE;actionAt=now;}
                break;
            case TYPE:
                if(!visible(InterfaceID.Settings.SEARCH_TEXT))break;
                // Use the existing keyboard input; no client-varbit writes or scripts.
                Rs2Keyboard.typeString("keybind");step=Step.KEYBINDS;actionAt=now;
                break;
            case KEYBINDS:
                if(visible(InterfaceID.Keybinding.DEFAULT_BUTTON)){step=Step.RESET;break;}
                if(ui.button(FcKeybindings::keybindButton)){actionAt=now;}
                break;
            case RESET:
                status="Restoring default game keybindings";
                if(click(InterfaceID.Keybinding.DEFAULT_BUTTON)){step=Step.OBSERVE;actionAt=now;}
                break;
            case OBSERVE:
                // Record the game's live values, including any default Escape
                // mapping. Tab opening itself accepts F-keys or visible clicks.
                result=FcActions.read(()->"Restore-defaults click sent; live bindings Inventory="+
                    Microbot.getClient().getVarbitValue(VarbitID.STONE_INV_KEY)+" Prayer="+
                    Microbot.getClient().getVarbitValue(VarbitID.STONE_PRAYER_KEY)+" Magic="+
                    Microbot.getClient().getVarbitValue(VarbitID.STONE_MAGIC_KEY),"Waiting for game keybindings");
                if(escapeClosed&&FcActions.read(()->Microbot.getClient().getVarbitValue(VarbitID.KEYBINDING_ESC_TO_CLOSE)!=1,false)) {
                    if(click(InterfaceID.Keybinding.ESC_OPTION))actionAt=now;
                    break;
                }
                step=Step.CLOSE;break;
            case CLOSE:
                status="Closing game settings";
                int panels=(visible(InterfaceID.Settings.UNIVERSE)?1:0)
                    |(visible(InterfaceID.Keybinding.DEFAULT_BUTTON)?2:0);
                if(panels==0) {
                    step=Step.RETURN;ui.releaseTab();break;
                }
                // One close input per observed modal state. A delayed native
                // close must not enqueue another click at its old scene position.
                if(panels!=closingPanels){closingPanels=panels;closeKeySent=closeClickSent=false;}
                if(!closeKeySent&&FcActions.read(()->Microbot.getClient().getVarbitValue(VarbitID.KEYBINDING_ESC_TO_CLOSE)==1,false)) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);closeKeySent=true;actionAt=System.currentTimeMillis();
                } else if(!closeKeySent&&!closeClickSent&&ui.button(()->
                    visible(InterfaceID.Settings.UNIVERSE)?bounds(InterfaceID.Settings.CLOSE):null)) {
                    closeClickSent=true;actionAt=System.currentTimeMillis();
                }
                // Never open a tab or start travel through a still-visible modal.
                if(now-startedAt>18_000)status="Waiting for game settings to close; close input already sent";
                break;
            case RETURN:
                if(ui.inventory()||now-startedAt>18_000){step=Step.DONE;ui.releaseTab();}
                break;
            default: break;
        }
        return step==Step.DONE;
    }
    private boolean click(int id){return ui.button(()->bounds(id));}
    private static boolean visible(int id) {
        // Visibility is separate from click safety: a settings panel may cover
        // the underlying minimap, while none of its buttons may click through it.
        return FcActions.read(()->{
            Widget widget=Rs2Widget.getWidget(id);
            return widget!=null&&!widget.isHidden()&&FcPrayerUi.visibleBounds(widget.getBounds(),
                Microbot.getClient().getCanvasWidth(),Microbot.getClient().getCanvasHeight())!=null;
        },false);
    }
    private static Rectangle bounds(int id) {
        return FcActions.read(()->bounds(Rs2Widget.getWidget(id)),null);
    }
    private static Rectangle bounds(Widget widget) {
        return FcActions.startupBounds(widget);
    }
    private static Rectangle keybindButton() {
        return FcActions.read(()->{
            Widget zone=Rs2Widget.getWidget(InterfaceID.Settings.SETTINGS_CLICKZONE);
            if(zone==null||zone.isHidden()||zone.getDynamicChildren()==null)return null;
            for(Widget child:zone.getDynamicChildren()) {
                if(child==null||child.isHidden()||child.getActions()==null)continue;
                for(String action:child.getActions())
                    if(action!=null&&action.toLowerCase(Locale.ROOT).contains("keybind"))return bounds(child);
            }
            return null;
        },null);
    }
}
