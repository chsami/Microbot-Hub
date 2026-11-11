/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.function.BooleanSupplier;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

/**
 * A cave menu entry that re-checks its target at delivery (port of the arenas' ArenaMenuEntry).
 * An invoke checks it immediately before menuAction; a Natural mouse press checks it on the
 * client thread when the menu is built, so a moved, dead or already-satisfied target becomes CANCEL.
 */
final class FcMenuEntry extends NewMenuEntry {
    private BooleanSupplier live;
    private volatile boolean cancelled;

    static FcMenuEntry guarded(NewMenuEntry entry,BooleanSupplier live) {
        FcMenuEntry guarded;
        if(entry instanceof FcMenuEntry)guarded=(FcMenuEntry)entry;
        else {
            guarded=new FcMenuEntry();
            guarded.param0(entry.getParam0()).param1(entry.getParam1()).type(entry.getType())
                .identifier(entry.getIdentifier()).itemId(entry.getItemId()).option(entry.getOption()).target(entry.getTarget())
                .actor(entry.getActor()).gameObject(entry.getGameObject()).widget(entry.getWidget()).worldViewId(entry.getWorldViewId());
        }
        guarded.live=live;
        return guarded;
    }

    /** Client thread only: widget visibility, NPC identity and the caller's state check. */
    boolean liveValid(net.runelite.api.Client client) {
        if(cancelled)return false;
        try {
            if(client==null)return cancel();
            if(getWidget()!=null&&getWidget().isHidden())return cancel();
            var npc=getNpc();
            if(npc!=null&&(npc.isDead()||client.getTopLevelWorldView()==null
                ||client.getTopLevelWorldView().npcs().byIndex(npc.getIndex())!=npc))return cancel();
            if(live==null||live.getAsBoolean())return true;
        } catch(RuntimeException ignored) { }
        return cancel();
    }

    private boolean cancel() {
        cancelled=true;
        if(Microbot.targetMenu==this)Microbot.targetMenu=null;
        return false;
    }

    @Override public MenuAction getType() {
        if(cancelled)return MenuAction.CANCEL;
        // The Natural mouse press builds its menu on the client thread; re-check there.
        var client=Microbot.getClient();
        if(client!=null&&client.isClientThread()&&!liveValid(client))return MenuAction.CANCEL;
        return super.getType();
    }
}
