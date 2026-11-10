package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope;
import net.runelite.client.util.Text;

/** Local menu dispatch: the selected input mode, one priority lane, native NaturalMouse and observed presses. */
public final class ArenaMenuInput
{
    private ArenaMenuInput() { }

    /** Client thread only. The exact index also covers this tick's spawn before Main's cache refresh. */
    public static NPC npc(Client client, int index)
    {
        if(index<0||client==null||client.getTopLevelWorldView()==null)return null;
        var cache=Microbot.getRs2NpcCache();
        var cached=cache==null?null:cache.query().where(n->n.getIndex()==index).firstOnClientThread();
        var view=client.getTopLevelWorldView();
        if(view.npcs()==null)return null;
        NPC current=view.npcs().byIndex(index);
        if(cached!=null&&cached.getNpc()==current)current=cached.getNpc();
        return current!=null&&current.getIndex()==index&&current.getWorldView()==view&&!current.isDead()?current:null;
    }

    public static boolean tryInvoke(NewMenuEntry entry, Rectangle bounds)
    {
        Point point = ArenaInputScope.onClientThread(() -> {
            var client = Microbot.getClient();
            if (client == null || bounds == null || bounds.isEmpty()) return null;
            Rectangle safe = bounds.intersection(new Rectangle(2, 2, client.getCanvasWidth() - 4, client.getCanvasHeight() - 4));
            return safe.isEmpty() ? null : Rs2UiHelper.getClickingPoint(safe, true);
        }).orElse(null);
        return tryClick(entry, point);
    }

    /** Clicks collected by {@link #burst}, on the thread that builds them. */
    private static final ThreadLocal<java.util.List<Object[]>> BURST = new ThreadLocal<>();

    /**
     * Runs {@code body} (which builds several clicks, e.g. one Wear per gear piece) and sends them as one gesture in
     * the order built: invoked 8-11 ms apart, or clicked slot to slot by the Natural mouse without re-queueing between
     * pieces. Returns what {@code body} returned.
     */
    public static boolean burst(java.util.function.BooleanSupplier body)
    {
        if (BURST.get() != null) return body.getAsBoolean();
        java.util.List<Object[]> clicks = new java.util.ArrayList<>();
        BURST.set(clicks);
        boolean result;
        try { result = body.getAsBoolean(); }
        finally { BURST.remove(); }
        if (clicks.isEmpty()) return result;
        java.util.List<Point> points = new java.util.ArrayList<>();
        java.util.List<NewMenuEntry> entries = new java.util.ArrayList<>();
        for (Object[] click : clicks) { points.add((Point) click[0]); entries.add((ArenaMenuEntry) click[1]); }
        if (ArenaInputScope.customBurst(points, entries)) { for (NewMenuEntry sent : entries) ((ArenaMenuEntry) sent).pressed(); return result; }
        Runnable gesture = ArenaInputScope.capture(() -> {
            for (Object[] click : clicks)
            {
                ArenaMenuEntry guarded = (ArenaMenuEntry) click[1];
                try { naturalClick((Point) click[0], guarded); }
                catch (MouseMotionScope.Cancelled stale) {
                    if (Microbot.targetMenu == guarded) Microbot.targetMenu = null;
                    ArenaInputDiagnostics.event("GESTURE_CANCELLED", "OWNER_INVALID_BEFORE_PRESS");
                    return;
                }
            }
        });
        if (ArenaInputScope.scheduleGesture(gesture)) return result;
        ArenaInputDiagnostics.event("GESTURE_DROPPED", "OWNER_QUEUE_UNAVAILABLE");
        return false;
    }

    /** The initial walk point is fixed here; ArenaMenuEntry reprojects the tile when Main builds its menu. */
    public static boolean tryClick(NewMenuEntry entry, Point point)
    {
        if (entry == null || point == null || entry.getType() == null) return false;
        ArenaMenuEntry guarded = ArenaMenuEntry.guarded(entry);
        if (!guarded.valid()) return false;
        java.util.List<Object[]> collecting = BURST.get();
        if (collecting != null) { collecting.add(new Object[]{point, guarded}); return true; }
        if (ArenaInputScope.customClick(point, false, guarded)) { guarded.pressed(); return true; }
        Runnable gesture = ArenaInputScope.capture(() -> {
            try { naturalClick(point, guarded); }
            catch (MouseMotionScope.Cancelled stale) {
                if (Microbot.targetMenu == guarded) Microbot.targetMenu = null;
                ArenaInputDiagnostics.event("GESTURE_CANCELLED", "OWNER_INVALID_BEFORE_PRESS");
            }
        });
        if (ArenaInputScope.scheduleGesture(gesture)) return true;
        ArenaInputDiagnostics.event("GESTURE_DROPPED", "OWNER_QUEUE_UNAVAILABLE");
        return false;
    }

    private static void naturalClick(Point initial, ArenaMenuEntry entry)
    {
        ArenaInputScope.checkpoint();
        var client = Microbot.getClient();
        if (client == null || client.isClientThread()) {
            ArenaInputDiagnostics.event("GESTURE_DROPPED", "NATURAL_MOUSE_REQUIRES_WORKER");
            return;
        }
        Dimension size = ArenaInputScope.onClientThread(() -> new Dimension(client.getCanvasWidth(), client.getCanvasHeight())).orElse(null);
        if (!ArenaMouse.valid(initial, size) || Microbot.naturalMouse == null) {
            ArenaInputDiagnostics.event("GESTURE_DROPPED", "INVALID_COMBAT_POINT_OR_MOUSE");
            return;
        }
        InputLoop.Result result = InputLoop.run(emit -> {
            ArenaInputScope.checkpoint();
            travel(initial);
            Point current = ArenaInputScope.onClientThread(() -> refresh(initial, entry, size)).orElse(null);
            if (current == null) { ArenaInputDiagnostics.event("GESTURE_DROPPED", "TARGET_UNAVAILABLE"); return; }
            if (!current.equals(initial)) travel(current);
            ArenaInputScope.beforeClick();
            // Re-query after the final motion. A moved target is dropped instead of pressing stale geometry.
            if (!PointerState.isAt(current.getX(), current.getY()) || !ArenaInputScope.onClientThread(() -> ready(current, entry)).orElse(false)) {
                ArenaInputDiagnostics.event("GESTURE_DROPPED", "TARGET_CHANGED_BEFORE_PRESS"); return;
            }
            ArenaInputScope.checkpoint();
            Microbot.targetMenu = entry;
            ArenaInputDispatcher.pressing();
            emit.press(current.getX(), current.getY(), MouseEvent.BUTTON1);
            ArenaInputDiagnostics.pressed();
            entry.pressed();
            emit.release(current.getX(), current.getY(), MouseEvent.BUTTON1);
            emit.click(current.getX(), current.getY(), MouseEvent.BUTTON1);
            Microbot.getMouse().setLastClick(current);
        });
        if (result == InputLoop.Result.ABORTED) ArenaInputDiagnostics.event("GESTURE_ABORTED", "INPUT_LOCK_OR_HUMAN_TAKEOVER");
    }

    /** {@link ArenaSnapWalk}: the same checks and press as {@link #naturalClick}, with the pointer placed instead of travelled. */
    static void snapClick(Point initial, ArenaMenuEntry entry)
    {
        ArenaInputScope.checkpoint();
        var client = Microbot.getClient();
        if (client == null || client.isClientThread()) {
            ArenaInputDiagnostics.event("GESTURE_DROPPED", "SNAP_WALK_REQUIRES_WORKER");
            return;
        }
        Dimension size = ArenaInputScope.onClientThread(() -> new Dimension(client.getCanvasWidth(), client.getCanvasHeight())).orElse(null);
        if (!ArenaMouse.valid(initial, size)) {
            ArenaInputDiagnostics.event("GESTURE_DROPPED", "INVALID_COMBAT_POINT");
            return;
        }
        InputLoop.Result result = InputLoop.run(emit -> {
            Point current = ArenaInputScope.onClientThread(() -> refresh(initial, entry, size)).orElse(null);
            if (current == null) { ArenaInputDiagnostics.event("GESTURE_DROPPED", "TARGET_UNAVAILABLE"); return; }
            emit.move(current.getX(), current.getY());
            ArenaInputScope.beforeClick();
            if (!ArenaInputScope.onClientThread(() -> ready(current, entry)).orElse(false)) {
                ArenaInputDiagnostics.event("GESTURE_DROPPED", "TARGET_CHANGED_BEFORE_PRESS"); return;
            }
            ArenaInputScope.checkpoint();
            Microbot.targetMenu = entry;
            ArenaInputDispatcher.pressing();
            emit.press(current.getX(), current.getY(), MouseEvent.BUTTON1);
            ArenaInputDiagnostics.pressed();
            entry.pressed();
            emit.release(current.getX(), current.getY(), MouseEvent.BUTTON1);
            emit.click(current.getX(), current.getY(), MouseEvent.BUTTON1);
            Microbot.getMouse().setLastClick(current);
        });
        if (result == InputLoop.Result.ABORTED) ArenaInputDiagnostics.event("GESTURE_ABORTED", "INPUT_LOCK_OR_HUMAN_TAKEOVER");
    }

    private static void travel(Point point)
    {
        long started = System.nanoTime();
        try { Microbot.naturalMouse.moveTo(point.getX(), point.getY()); }
        finally { ArenaInputDiagnostics.event("MOUSE_TRAVEL", "NATURAL_MOUSE", System.nanoTime() - started); }
    }

    /** Client thread only. */
    private static Point refresh(Point point, ArenaMenuEntry entry, Dimension size)
    {
        if (!entry.liveValid()) return null;
        Rectangle bounds = entityBounds(entry);
        if (bounds == null) return entry.getActor() == null && entry.getGameObject() == null ? point : null;
        if (bounds.isEmpty() || bounds.x <= 1 && bounds.y <= 1) return null;
        Point next = bounds.contains(point.getX(), point.getY()) ? point : Rs2UiHelper.getClickingPoint(bounds, true);
        return ArenaMouse.valid(next, size) ? next : null;
    }

    /** Client thread only. */
    private static boolean ready(Point point, ArenaMenuEntry entry)
    {
        if (!entry.liveValid()) return false;
        Rectangle bounds = entityBounds(entry);
        return bounds == null ? entry.getActor() == null && entry.getGameObject() == null : bounds.contains(point.getX(), point.getY());
    }

    private static Rectangle entityBounds(NewMenuEntry entry)
    {
        if (entry.getActor() != null) {
            Shape hull = entry.getActor().getConvexHull();
            return hull == null ? null : hull.getBounds();
        }
        if (entry.getGameObject() != null) {
            Shape box = entry.getGameObject().getClickbox();
            return box == null ? null : box.getBounds();
        }
        if (entry.getWidget() != null) return entry.getWidget().isHidden() ? new Rectangle() : entry.getWidget().getBounds();
        return null;
    }

    /** The same live item/widget identity checks as native inventory input, with local mode routing. */
    public static boolean inventory(Client client, int slot, int itemId, String option)
    {
        return inventory(client, slot, itemId, option, null);
    }

    /** {@code onPressed} runs once when the click is actually sent; a gesture cancelled or dropped before its press never runs it. */
    public static boolean inventory(Client client, int slot, int itemId, String option, Runnable onPressed)
    {
        if (slot < 0 || slot >= 28 || itemId <= 0 || option == null) return false;
        return ArenaInputScope.onClientThread(() -> {
            Rs2ItemModel item = Rs2Inventory.getItemInSlot(slot);
            Widget container = client.getWidget(ComponentID.INVENTORY_CONTAINER);
            Widget widget = container == null ? null : container.getChild(slot);
            if (item == null || item.getId() != itemId || widget == null || widget.isHidden() || widget.getItemId() != itemId) return false;
            int identifier = action(widget.getActions(), option);
            MenuAction type = MenuAction.CC_OP;
            String target = item.getName();
            if (option.equalsIgnoreCase("use")) type = MenuAction.WIDGET_TARGET;
            else if (option.equalsIgnoreCase("cast")) type = MenuAction.WIDGET_TARGET_ON_WIDGET;
            else if (identifier < 0) {
                Map.Entry<String, Integer> sub = item.getIndexOfSubAction(option);
                int parent = sub == null ? -1 : action(widget.getActions(), sub.getKey());
                if (sub == null || sub.getValue() == null || sub.getValue() < 0 || parent < 1) return false;
                identifier = NewMenuEntry.findIdentifier(sub.getValue() + 1, parent);
                target = "";
            }
            ArenaMenuEntry entry = new ArenaMenuEntry().onPressed(onPressed);
            entry.liveCheck(() -> {
                var current = client.getWidget(ComponentID.INVENTORY_CONTAINER);
                var currentSlot = current == null ? null : current.getChild(slot);
                return current != null && !current.isHidden() && currentSlot != null && !currentSlot.isHidden()
                    && currentSlot.getItemId() == itemId && !client.isWidgetSelected();
            });
            entry.param0(slot).param1(ComponentID.INVENTORY_CONTAINER).type(type).identifier(identifier)
                .itemId(itemId).option(option).target(target).widget(widget).worldViewId(client.getTopLevelWorldView().getId());
            return !container.isHidden() && !client.isWidgetSelected() && tryInvoke(entry, widget.getBounds());
        }).orElse(false);
    }

    public static int action(String[] actions, String option)
    {
        for (int i = 0; actions != null && i < actions.length; i++)
            if (actions[i] != null && option.equalsIgnoreCase(Text.removeTags(actions[i]))) return i + 1;
        return -1;
    }

    public static boolean equipment(Client client, int slot, String option)
    {
        int child;
        switch (slot) {
            case 0: child=15; break; case 1: child=16; break; case 2: child=17; break;
            case 3: child=18; break; case 4: child=19; break; case 5: child=20; break;
            case 7: child=21; break; case 9: child=22; break; case 10: child=23; break;
            case 12: child=24; break; case 13: child=25; break; default: return false;
        }
        return ArenaInputScope.onClientThread(() -> {
            Rs2ItemModel item=Rs2Equipment.items().stream().filter(i->i.getSlot()==slot).findFirst().orElse(null);
            Widget widget=client.getWidget(net.runelite.api.gameval.InterfaceID.WORNITEMS,child);
            if(item==null||widget==null||widget.isHidden()||client.isWidgetSelected())return false;
            int identifier=action(widget.getActions(),option);
            if(identifier<1) {
                if("remove".equalsIgnoreCase(option))identifier=1;
                else {
                    for(int i=0;i<item.getEquipmentActions().size();i++)
                        if(option.equalsIgnoreCase(item.getEquipmentActions().get(i)))identifier=i+2;
                }
            }
            if(identifier<1)return false;
            ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->!widget.isHidden()&&!client.isWidgetSelected()
                &&Rs2Equipment.items().stream().anyMatch(i->i.getSlot()==slot&&i.getId()==item.getId())
                &&("remove".equalsIgnoreCase(option)||action(widget.getActions(),option)>0||item.getEquipmentActions().stream().anyMatch(option::equalsIgnoreCase)));
            entry.param0(-1).param1(widget.getId()).type(MenuAction.CC_OP).identifier(identifier)
                .itemId(item.getId()).option(option).target(item.getName()).widget(widget);
            return tryInvoke(entry,widget.getBounds());
        }).orElse(false);
    }
}
