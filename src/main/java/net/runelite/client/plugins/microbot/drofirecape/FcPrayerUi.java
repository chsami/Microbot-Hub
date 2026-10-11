/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.MenuAction;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** One tab transition per step; only live, visible prayer buttons supply mouse targets. */
final class FcPrayerUi {
    interface Input {
        View capture(InterfaceTab tab, Rs2PrayerEnum prayer);
        default Rectangle tabButton(InterfaceTab tab) {return null;}
        void key(int key);
        void move(Point point,BooleanSupplier permitted);
        boolean click(Point point, NewMenuEntry entry,BooleanSupplier permitted);
    }
    enum Result { SENT, WAITING, SKIPPED }
    private static final class ClickUnconfirmed extends IllegalStateException {
        ClickUnconfirmed(){super("Visible prayer click not consumed by client");}
    }

    static final class View {
        final int tab, key;
        final Rectangle button;
        final boolean active;
        View(int tab, int key, Rectangle button, boolean active) {
            this.tab=tab;this.key=key;this.button=button;this.active=active;
        }
    }

    private final Input input;
    private final BooleanSupplier allowed;
    private final LongSupplier millis;
    private InterfaceTab pending;
    private boolean urgentTab;
    private boolean pendingKey;
    private long keyAt;
    private volatile String problem="";
    private static final long TAB_RETRY_MS=400;

    FcPrayerUi(BooleanSupplier allowed) {
        this(new Input() {
            public View capture(InterfaceTab tab, Rs2PrayerEnum prayer) {
                return FcActions.read(()->{
                    net.runelite.api.Client client=Microbot.getClient();
                    if(client==null||client.getGameState()!=GameState.LOGGED_IN)return null;
                    Rectangle bounds=null;
                    boolean active=false;
                    if(prayer!=null) {
                        Widget widget=Rs2Widget.getWidget(prayer.getIndex());
                        if(widget!=null&&!widget.isHidden())
                            bounds=visibleBounds(widget.getBounds(),client.getCanvasWidth(),client.getCanvasHeight());
                        active=client.getVarbitValue(prayer.getVarbit())==1;
                        if(prayer==Rs2PrayerEnum.EAGLE_EYE||prayer==Rs2PrayerEnum.DEAD_EYE)
                            active|=client.getVarbitValue(Rs2PrayerEnum.EAGLE_EYE.getVarbit())==1
                                ||client.getVarbitValue(Rs2PrayerEnum.DEAD_EYE.getVarbit())==1;
                    }
                    int binding=tab.getHotkeyVarbit()<0?0:client.getVarbitValue(tab.getHotkeyVarbit());
                    int key=binding>=1&&binding<=12?KeyEvent.VK_F1+binding-1:
                        binding==13?KeyEvent.VK_ESCAPE:-1;
                    return new View(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL),key,bounds,active);
                },null);
            }
            public void key(int key) {Rs2Keyboard.keyPress(key);}
            public Rectangle tabButton(InterfaceTab tab) {
                return FcActions.read(()->{
                    net.runelite.api.Client client=Microbot.getClient();
                    if(client==null||client.getGameState()!=GameState.LOGGED_IN)return null;
                    int id;
                    boolean bottom=client.isResized()&&client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT)==1;
                    switch(tab) {
                        case INVENTORY: id=!client.isResized()?InterfaceID.Toplevel.STONE3:
                            bottom?InterfaceID.ToplevelPreEoc.STONE3:InterfaceID.ToplevelOsrsStretch.STONE3;break;
                        case PRAYER: id=!client.isResized()?InterfaceID.Toplevel.STONE5:
                            bottom?InterfaceID.ToplevelPreEoc.STONE5:InterfaceID.ToplevelOsrsStretch.STONE5;break;
                        case MAGIC: id=!client.isResized()?InterfaceID.Toplevel.STONE6:
                            bottom?InterfaceID.ToplevelPreEoc.STONE6:InterfaceID.ToplevelOsrsStretch.STONE6;break;
                        case SETTINGS: id=!client.isResized()?InterfaceID.Toplevel.STONE11:
                            bottom?InterfaceID.ToplevelPreEoc.STONE11:InterfaceID.ToplevelOsrsStretch.STONE11;break;
                        default: return null;
                    }
                    Widget widget=Rs2Widget.getWidget(id);
                    return widget==null||widget.isHidden()?null:
                        visibleBounds(widget.getBounds(),client.getCanvasWidth(),client.getCanvasHeight());
                },null);
            }
            public void move(Point point,BooleanSupplier permitted) {
                InputLoop.run(emit->{if(permitted.getAsBoolean())moveNative(point,emit);});
            }
            public boolean click(Point point, NewMenuEntry entry,BooleanSupplier permitted) {
                AtomicBoolean sent=new AtomicBoolean();
                InputLoop.Result result=InputLoop.run(emit->{
                    // Recheck at the shared mouse lock, and again only if another
                    // gesture moved the pointer since our initial travel.
                    if(!permitted.getAsBoolean())return;
                    if(!PointerState.isAt(point.getX(),point.getY())) {
                        moveNative(point,emit);
                        if(!permitted.getAsBoolean())return;
                    }
                    Microbot.targetMenu=entry;
                    emit.press(point.getX(),point.getY(),MouseEvent.BUTTON1);
                    emit.release(point.getX(),point.getY(),MouseEvent.BUTTON1);
                    emit.click(point.getX(),point.getY(),MouseEvent.BUTTON1);
                    Microbot.getMouse().setLastClick(point);sent.set(true);
                });
                if(result!=InputLoop.Result.COMPLETED||!sent.get())return false;
                // Tab stones/settings buttons may use native click listeners
                // rather than menu operations. Their caller observes the UI change.
                if(entry==null)return true;
                // Let the client consume this click before arming a second one.
                // Use short polls rather than the normal 40-320ms utility polls.
                boolean consumed=net.runelite.client.plugins.microbot.util.Global.sleepUntil(
                    ()->Microbot.targetMenu!=entry,()->{},80,4);
                if(!consumed) {
                    if(Microbot.targetMenu==entry)Microbot.targetMenu=null;
                    throw new ClickUnconfirmed();
                }
                return true;
            }
        },()->allowed.getAsBoolean()&&Microbot.getClient()!=null&&!Microbot.getClient().isClientThread(),
            System::currentTimeMillis);
    }

    FcPrayerUi(Input input, BooleanSupplier allowed, LongSupplier millis) {
        this.input=input;this.allowed=allowed;this.millis=millis;
    }
    void reset(){releaseTab();keyAt=0;problem="";}
    String problem(){return problem;}
    boolean missingKey(){return problem.startsWith("Bind ");}
    void problem(String text){problem=text;}

    boolean inventory() {return open(InterfaceTab.INVENTORY);}
    boolean open(InterfaceTab tab) {
        if(!allowed.getAsBoolean())return false;
        boolean ready=tabReady(tab,input.capture(tab,null));
        if(ready&&tab!=InterfaceTab.PRAYER){pending=tab;urgentTab=false;keyAt=millis.getAsLong();}
        return ready;
    }
    void releaseTab(){pending=null;urgentTab=false;pendingKey=false;}
    /** A real on-screen UI click; only the observed component supplies its bounds. */
    boolean button(Supplier<Rectangle> bounds) {return button(null,bounds);}
    /** One explicit request; an unacknowledged press must not be sent again. */
    boolean requestButton(NewMenuEntry entry,Supplier<Rectangle> bounds) {
        try {return button(entry,bounds);}
        catch(ClickUnconfirmed e){return true;}
    }
    private boolean button(NewMenuEntry entry,Supplier<Rectangle> bounds) {
        if(!allowed.getAsBoolean())return false;
        Rectangle button=bounds.get();if(button==null)return false;
        Point point=new Point(button.x+button.width/2,button.y+button.height/2);
        BooleanSupplier permitted=()->{
            Rectangle current=bounds.get();
            return allowed.getAsBoolean()&&current!=null&&current.contains(point.getX(),point.getY());
        };
        input.move(point,permitted);
        return input.click(point,entry,permitted);
    }
    /** Native mouse movement to a live widget; no screen-sized fallback rectangle. */
    boolean widget(InterfaceTab tab,NewMenuEntry entry,Supplier<Rectangle> bounds) {
        if(!open(tab))return false;
        Rectangle button=bounds.get();if(button==null)return false;
        Point point=new Point(button.x+button.width/2,button.y+button.height/2);
        BooleanSupplier permitted=()->{
            View view=input.capture(tab,null);Rectangle current=bounds.get();
            return allowed.getAsBoolean()&&view!=null&&view.tab==tab.getVarcIntIndex()
                &&current!=null&&current.contains(point.getX(),point.getY());
        };
        input.move(point,permitted);
        boolean sent=input.click(point,entry,permitted);
        if(sent)releaseTab();
        return sent;
    }
    void inventoryUsed(){if(pending==InterfaceTab.INVENTORY)releaseTab();}

    boolean prayer(Rs2PrayerEnum prayer, boolean desired) {
        return prayer(prayer,desired,()->true);
    }

    /** Prepare the next visible button while the current protection stays on. */
    boolean preparePrayer(Rs2PrayerEnum prayer,BooleanSupplier valid) {
        if(!allowed.getAsBoolean()||!valid.getAsBoolean()||pending!=null&&pending!=InterfaceTab.PRAYER)return false;
        View view=input.capture(InterfaceTab.PRAYER,prayer);
        if(view==null||view.tab!=InterfaceTab.PRAYER.getVarcIntIndex()||view.button==null)return false;
        Point point=new Point(view.button.x+view.button.width/2,view.button.y+view.button.height/2);
        BooleanSupplier permitted=()->allowed.getAsBoolean()&&valid.getAsBoolean()
            &&visibleAt(input.capture(InterfaceTab.PRAYER,prayer),point);
        input.move(point,permitted);
        return permitted.getAsBoolean();
    }
    boolean prayer(Rs2PrayerEnum prayer, boolean desired,BooleanSupplier valid) {
        if(!allowed.getAsBoolean()||!valid.getAsBoolean())return false;
        View view=input.capture(InterfaceTab.PRAYER,prayer);
        if(view==null)return false;
        // A queued toggle must never undo a prayer which already reached its goal.
        if(view.active==desired)return false;
        // An actual protection change preempts optional tab work; a reset never does.
        if(desired&&(prayer==Rs2PrayerEnum.PROTECT_MAGIC||prayer==Rs2PrayerEnum.PROTECT_RANGE
            ||prayer==Rs2PrayerEnum.PROTECT_MELEE)) {
            if(pending!=InterfaceTab.PRAYER)pending=null;
            urgentTab=true;
        }
        if(!tabReady(InterfaceTab.PRAYER,view))return false;
        if(view.button==null){problem="Waiting for visible Prayer-book button";return false;}
        if(!allowed.getAsBoolean()||!valid.getAsBoolean())return false;
        Rectangle b=view.button;
        Point point=new Point(b.x+b.width/2,b.y+b.height/2);
        input.move(point,()->allowed.getAsBoolean()&&valid.getAsBoolean());
        if(!input.click(point,entry(prayer,desired),()->{
            View pressed=input.capture(InterfaceTab.PRAYER,prayer);
            return visibleAt(pressed,point)&&pressed.active!=desired&&allowed.getAsBoolean()&&valid.getAsBoolean();
        }))return false;
        releaseTab();problem="";
        return true;
    }

    /** Travel first with protection held. Never begin OFF after the early window. */
    Result resetPrayer(Rs2PrayerEnum prayer,BooleanSupplier early,BooleanSupplier valid) {
        if(!allowed.getAsBoolean()||!valid.getAsBoolean())return Result.WAITING;
        if(!early.getAsBoolean())return Result.SKIPPED;
        View view=input.capture(InterfaceTab.PRAYER,prayer);
        if(!tabReady(InterfaceTab.PRAYER,view)||view.button==null||!view.active)return Result.WAITING;
        Rectangle b=view.button;Point point=new Point(b.x+b.width/2,b.y+b.height/2);
        input.move(point,()->allowed.getAsBoolean()&&valid.getAsBoolean());
        if(!allowed.getAsBoolean()||!valid.getAsBoolean())return Result.WAITING;
        if(!early.getAsBoolean())return Result.SKIPPED;
        boolean offSent=false;
        try {
            offSent=input.click(point,entry(prayer,false),()->{
                View pressed=input.capture(InterfaceTab.PRAYER,prayer);
                return visibleAt(pressed,point)&&pressed.active&&allowed.getAsBoolean()
                    &&valid.getAsBoolean()&&early.getAsBoolean();
            });
            if(!offSent)return early.getAsBoolean()?Result.WAITING:Result.SKIPPED;
        } catch(ClickUnconfirmed e) {offSent=true;throw e;}
        finally {
            if(offSent) {
                // ON repairs an already-started pair even if the early window
                // ended. Never schedule an isolated OFF to a later pulse.
                if(!input.click(point,entry(prayer,true),()->
                    visibleAt(input.capture(InterfaceTab.PRAYER,prayer),point)&&allowed.getAsBoolean()))
                    throw new IllegalStateException("Prayer reset ON click rejected");
            }
        }
        releaseTab();problem="";return Result.SENT;
    }

    private static NewMenuEntry entry(Rs2PrayerEnum prayer,boolean desired) {
        return new NewMenuEntry().param0(-1).param1(prayer.getIndex()).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option(desired?"Activate":"Deactivate").target(prayer.getName());
    }
    private static boolean visibleAt(View view,Point point) {
        return view!=null&&view.tab==InterfaceTab.PRAYER.getVarcIntIndex()&&view.button!=null
            &&view.button.contains(point.getX(),point.getY());
    }
    private static void moveNative(Point point,InputLoop.Emit emit) {
        if(PointerState.isAt(point.getX(),point.getY()))return;
        if(Microbot.naturalMouse!=null)Microbot.naturalMouse.moveTo(point.getX(),point.getY());
        else emit.move(point.getX(),point.getY());
    }

    private boolean tabReady(InterfaceTab tab, View view) {
        if(view==null)return false;
        long now=millis.getAsLong();
        // Finish a pending supply click before the prayer worker steals the tab.
        if(pending!=null&&pending!=tab&&now-keyAt<TAB_RETRY_MS
            &&(pending!=InterfaceTab.PRAYER||urgentTab))return false;
        if(view.tab==tab.getVarcIntIndex()){problem="";return true;}
        if(pending==tab&&now-keyAt<TAB_RETRY_MS)return false;
        if(!allowed.getAsBoolean())return false;
        boolean fKey=view.key>=KeyEvent.VK_F1&&view.key<=KeyEvent.VK_F12;
        boolean keyTimedOut=pending==tab&&pendingKey;
        if(!fKey||keyTimedOut) {
            if(input.tabButton(tab)!=null) {
                boolean sent=button(()->{
                    View live=input.capture(tab,null);
                    return live==null||live.tab==tab.getVarcIntIndex()?null:input.tabButton(tab);
                });
                if(sent) {
                    pending=tab;pendingKey=false;keyAt=now;
                    problem="Opening "+tab.getName()+" by visible tab ("+
                        (fKey?"F-key did not open it":"F-key unavailable: "+view.key)+")";
                }
                return false;
            }
            if(!fKey) {
                problem="Bind "+tab.getName()+" to an F-key; visible tab is unavailable (key="+view.key+")";
                return false;
            }
        }
        input.key(view.key);pending=tab;pendingKey=true;keyAt=now;
        problem="Opening "+tab.getName()+" with F-key";
        // Observe the real tab on the next pulse before any mouse click.
        return false;
    }

    static Rectangle visibleBounds(Rectangle button,int width,int height) {
        if(button==null||button.width<=4||button.height<=4||width<=4||height<=4)return null;
        Rectangle inside=new Rectangle(button);
        inside.grow(-2,-2);
        Rectangle visible=inside.intersection(new Rectangle(2,2,width-4,height-4));
        return visible.width>0&&visible.height>0?visible:null;
    }
}
