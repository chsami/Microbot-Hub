package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

public final class ArenaMenuEntry extends NewMenuEntry
{
    private BooleanSupplier allowed;
    private BooleanSupplier liveCheck;
    private Supplier<Point> liveWalkPoint;
    private Point projection;
    private volatile boolean cancelled;

    public ArenaMenuEntry() { }

    static ArenaMenuEntry guarded(NewMenuEntry entry)
    {
        ArenaMenuEntry guarded;
        if (entry instanceof ArenaMenuEntry) guarded = (ArenaMenuEntry) entry;
        else {
            guarded = new ArenaMenuEntry();
            guarded.param0(entry.getParam0()).param1(entry.getParam1()).type(entry.getType())
                .identifier(entry.getIdentifier()).itemId(entry.getItemId()).option(entry.getOption()).target(entry.getTarget())
                .actor(entry.getActor()).gameObject(entry.getGameObject()).widget(entry.getWidget()).worldViewId(entry.getWorldViewId());
        }
        guarded.allowed = ArenaInputScope.capture(() -> {
            try { ArenaInputScope.checkpoint(); return true; }
            catch (net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.Cancelled stale) { return false; }
        });
        return guarded;
    }

    public ArenaMenuEntry liveWalkPoint(Supplier<Point> point) { liveWalkPoint = point; return this; }
    public ArenaMenuEntry liveCheck(BooleanSupplier check) { liveCheck = check; return this; }

    boolean liveValid()
    {
        if (!valid()) return false;
        try {
            var client=Microbot.getClient();
            if(client==null)return false;
            if(getWidget()!=null&&getWidget().isHidden())return false;
            var npc=getNpc();
            if(npc!=null) {
                if(ArenaMenuInput.npc(client,npc.getIndex())!=npc)return false;
            }
            if (liveCheck == null || liveCheck.getAsBoolean()) return true;
        }
        catch (RuntimeException ignored) { }
        cancel();
        return false;
    }

    public boolean valid()
    {
        if (cancelled) return false;
        try { if (allowed == null || allowed.getAsBoolean()) return true; }
        catch (RuntimeException ignored) { }
        cancel();
        return false;
    }

    private void cancel()
    {
        cancelled = true;
        if (Microbot.targetMenu == this) Microbot.targetMenu = null;
    }

    @Override public MenuAction getType()
    {
        if (!valid()) return MenuAction.CANCEL;

        var client=Microbot.getClient();
        if(client!=null&&client.isClientThread()) {
            if(!liveValid()) { cancel(); return MenuAction.CANCEL; }

            if(liveWalkPoint!=null&&super.getType()==MenuAction.WALK) {
                try { projection=liveWalkPoint.get(); }
                catch(RuntimeException unavailable) { projection=null; }
                if(projection==null) { cancel(); return MenuAction.CANCEL; }
            }
        }
        return super.getType();
    }

    @Override public int getParam0()
    {
        return projection == null ? super.getParam0() : projection.getX();
    }

    @Override public int getParam1() { return projection == null ? super.getParam1() : projection.getY(); }
}
