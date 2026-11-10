/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

/**
 * Every cave action except walking is one menu action (port of the arenas' ArenaInvoke/ArenaMenuInput).
 * Direct invokes send it with menuAction on the client thread. Natural mouse carries the same guarded
 * entry on a Natural mouse travel and press under the fight profile. Walking and outside-cave UI always
 * use the mouse. The entry's live check runs at delivery in both modes.
 */
final class FcInput {
    private final Supplier<FcFightInput> mode;
    private final FcArenaInput arena;
    private final BooleanSupplier cave;
    FcInput(Supplier<FcFightInput> mode,FcArenaInput arena){this(mode,arena,()->arena!=null&&arena.held());}
    /** The cave check is injectable for offline tests. */
    FcInput(Supplier<FcFightInput> mode,FcArenaInput arena,BooleanSupplier cave){this.mode=mode;this.arena=arena;this.cave=cave;}

    private final java.util.concurrent.atomic.AtomicLong protectionUrgency=new java.util.concurrent.atomic.AtomicLong();
    /** A protection prayer is waiting: walks still travelling yield the pointer (as in the hosted arenas). */
    void protectionQueued(){protectionUrgency.incrementAndGet();}
    long protectionUrgency(){return protectionUrgency.get();}
    FcFightInput mode(){FcFightInput m=mode==null?null:mode.get();return m==null?FcFightInput.NATURAL_MOUSE:m;}
    /** Direct invokes apply only to cave input, while the arena lease is held, and never to walking. */
    boolean invokes(MenuAction type) {
        return type!=null&&type!=MenuAction.WALK&&cave.getAsBoolean()&&mode()==FcFightInput.INVOKES;
    }

    /** Any thread. True means delivered (invoke) or pressed (mouse), not observed game completion. */
    boolean dispatch(NewMenuEntry entry,Rectangle bounds,BooleanSupplier live) {
        if(entry==null||entry.getType()==null)return false;
        FcMenuEntry guarded=FcMenuEntry.guarded(entry,live);
        Client client=Microbot.getClient();
        if(client==null)return false;
        if(invokes(entry.getType())) {
            if(client.isClientThread())return invokeNow(client,guarded);
            return FcActions.read(()->invokeNow(client,guarded),false);
        }
        // Mouse travel never runs on the client thread.
        if(client.isClientThread()||bounds==null||bounds.isEmpty())return false;
        if(arena==null||!arena.held()) {
            Microbot.doInvoke(guarded,bounds);
            return true;
        }
        boolean pressed;
        GESTURES.incrementAndGet();
        try {
            if(!prayer(entry)) {
                // Colosseum 1.0.80: a protection prayer queued during the travel takes the pointer first; any other
                // gesture (walk, drink, gear, attack, spell) yields instead of finishing under it.
                long urgency=protectionUrgency.get();
                pressed=arena.fight(()->strictInvoke(guarded,bounds),false,()->protectionUrgency.get()==urgency);
            } else pressed=arena.fight(()->strictInvoke(guarded,bounds),false);
        } finally {GESTURES.decrementAndGet();}
        if(pressed)pressing();
        return pressed;
    }

    /**
     * Colosseum 1.0.80 gear-switch failsafe: the game reads a canvas press on its next frame, and a tab opened before
     * then moves the click onto the new panel (Colosseum live run 1.0.79: a prayer press landed on a brew after a
     * queued gear switch opened the inventory). A different tab is not opened until the game has read the last mouse
     * press, for at most {@link #PRESS_READ_NANOS}; the caller retries on its next loop, so no thread waits.
     */
    static final long PRESS_READ_NANOS=150_000_000L;
    /**
     * Mouse gestures in flight. No tab is changed under one (Colosseum: "A tab cannot change underneath an
     * in-flight gesture"): live 1.1.39 wave 44, a Protect from Magic press was lost while a drink opened the
     * inventory, and a Ket-Zek hit 43.
     */
    private static final java.util.concurrent.atomic.AtomicInteger GESTURES=new java.util.concurrent.atomic.AtomicInteger();
    static boolean gestureInFlight(){return GESTURES.get()>0;}
    /** The prayer book's interface group (541): a prayer press, which other gestures yield to. */
    static boolean prayer(NewMenuEntry entry){return entry!=null&&(entry.getParam1()>>>16)==541;}
    private static volatile long pressAt,readAt;
    /** Input thread, after a Natural mouse press. */
    static void pressing(){pressAt=System.nanoTime();}
    /** Client thread (MenuOptionClicked): the game has read a click. */
    static void pressRead(){readAt=System.nanoTime();}
    static boolean pressUnread(long now){long at=pressAt;return at!=0&&readAt<at&&now-at<PRESS_READ_NANOS;}

    /** Client thread only: the guarded entry is checked and sent in one operation. */
    /** Strict fight dispatch: a missing or off-canvas target is dropped instead of clicked at the origin. */
    static boolean strictInvoke(NewMenuEntry entry,Rectangle rectangle) {
        if(entry==null||entry.getType()==null||rectangle==null||rectangle.isEmpty())return false;
        Client client=Microbot.getClient();
        if(client==null)return false;
        Rectangle visible=visibleBounds(rectangle,client.getCanvasWidth(),client.getCanvasHeight());
        if(visible==null||entry.getType()==MenuAction.WALK&&!visible.contains(entry.getParam0(),entry.getParam1()))return false;
        Microbot.doInvoke(entry,visible);
        return true;
    }

    static boolean invokeNow(Client client,FcMenuEntry entry) {
        if(!client.isClientThread()||!entry.liveValid(client))return false;
        MenuAction action=entry.getType();
        if(action==null||action==MenuAction.CANCEL)return false;
        client.menuAction(entry.getParam0(),entry.getParam1(),action,entry.getIdentifier(),
            entry.getItemId(),entry.getOption(),entry.getTarget());
        return true;
    }

    /** Client thread only: script dispatch and the panel receipt are one operation (no F-keys or tab clicks). */
    static boolean openTab(Client client,InterfaceTab tab) {
        int wanted=tab.getVarcIntIndex();
        if(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)!=wanted) {
            if(pressUnread(System.nanoTime())||gestureInFlight())return false;
            client.runScript(915,wanted);
        }
        return client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)==wanted;
    }

    /** A widget's visible click area inside the canvas, or null. */
    static Rectangle visibleBounds(Rectangle button,int width,int height) {
        if(button==null||button.width<=4||button.height<=4||width<=4||height<=4)return null;
        Rectangle inside=new Rectangle(button);
        inside.grow(-2,-2);
        Rectangle visible=inside.intersection(new Rectangle(2,2,width-4,height-4));
        return visible.width>0&&visible.height>0?visible:null;
    }
}
