package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.inventory.InventoryQueries;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene.SceneQueries;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.widget.WidgetQueries;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Barrows' snapshot guards around the Microbot API calls; activity decisions stay with the tasks. */
public final class BarrowsInteractions
{
    private final Supplier<Client> clients;
    private final Function<Runnable, Boolean> submit;
    private final Supplier<MicrobotApi> api;
    private final Supplier<InteractionWorker> worker;
    private final BooleanSupplier ready;
    private long requests;

    public BarrowsInteractions(Supplier<Client> clients, Function<Runnable, Boolean> submit,
                                Supplier<MicrobotApi> api, Supplier<InteractionWorker> worker, BooleanSupplier ready)
    {
        this.clients = clients;
        this.submit = submit;
        this.api = api;
        this.worker = worker;
        this.ready = ready;
    }

    public long getRequestsDispatched()
    {
        return requests;
    }

    private boolean queueChecked(BooleanSupplier valid, BooleanSupplier operation, Runnable callback)
    {
        if (!ready.getAsBoolean() || !valid.getAsBoolean())
        {
            return false;
        }
        return submit.apply(() ->
        {
            if (ready.getAsBoolean() && valid.getAsBoolean() && worker.get() != null)
            {
                worker.get().submit(() -> ready.getAsBoolean() && valid.getAsBoolean(), operation, () ->
                {
                    requests++;
                    if (callback != null)
                    {
                        callback.run();
                    }
                });
            }
        });
    }

    public boolean npc(NPC npc, String option, BooleanSupplier allowed, Runnable callback)
    {
        if (npc == null || npc.getTransformedComposition() == null)
        {
            return false;
        }
        int id = npc.getId();
        int index = npc.getIndex();
        int action = WidgetQueries.indexOf(npc.getTransformedComposition().getActions(), option);
        BooleanSupplier live = () -> allowed.getAsBoolean() && action >= 0 && action <= 4
            && npc.getId() == id && npc.getIndex() == index && SceneQueries.isNpcLive(clients.get(), npc)
            && TargetRange.npcDistance(clients.get(), npc) <= TargetRange.NPC_SEARCH_RADIUS
            && npc.getTransformedComposition() != null
            && WidgetQueries.indexOf(npc.getTransformedComposition().getActions(), option) == action;
        return queueChecked(live, () -> api.get().npc(npc, option), callback);
    }

    public boolean object(TileObject object, String option, BooleanSupplier allowed, Runnable callback)
    {
        if (object == null)
        {
            return false;
        }
        int id = object.getId();
        WorldPoint location = object.getWorldLocation();
        Point minimum = object instanceof GameObject ? ((GameObject) object).getSceneMinLocation() : null;
        Point maximum = object instanceof GameObject ? ((GameObject) object).getSceneMaxLocation() : null;
        int action = WidgetQueries.objectActionIndex(clients.get(), object, option);
        BooleanSupplier live = () -> allowed.getAsBoolean() && action >= 0 && action <= 4
            && object.getId() == id && location != null && location.equals(object.getWorldLocation())
            && (!(object instanceof GameObject) || minimum != null && maximum != null
                && minimum.equals(((GameObject) object).getSceneMinLocation())
                && maximum.equals(((GameObject) object).getSceneMaxLocation()))
            && SceneQueries.isObjectLive(clients.get(), object)
            && TargetRange.objectDistance(clients.get(), object) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS
            && WidgetQueries.objectActionIndex(clients.get(), object, option) == action;
        return queueChecked(live, () -> api.get().object(object, option), callback);
    }

    public boolean widget(int id, String option, BooleanSupplier allowed, Runnable callback)
    {
        Widget widget = clients.get().getWidget(id);
        if (widget == null)
        {
            return false;
        }
        int slot = widget.getIndex();
        int item = widget.getItemId();
        return queueChecked(() -> allowed.getAsBoolean() && clients.get().getWidget(id) == widget
            && widget.getId() == id && widget.getIndex() == slot && widget.getItemId() == item
            && WidgetQueries.visible(widget) && defaultAction(widget, option),
            () -> api.get().widget(widget, option), callback);
    }

    /** The rewards Close button is dynamic child 11 that steelborder creates in frame 155:1. */
    public static Widget rewardsCloseButton(Client client)
    {
        Widget frame = client.getWidget((155 << 16) | 1);
        Widget[] children = frame == null ? null : frame.getDynamicChildren();
        return children == null || children.length <= 11 ? null : children[11];
    }

    public boolean closeRewards(BooleanSupplier allowed, Runnable callback)
    {
        Widget button = rewardsCloseButton(clients.get());
        if (button == null)
        {
            return false;
        }
        return queueChecked(() -> allowed.getAsBoolean() && rewardsCloseButton(clients.get()) == button
            && WidgetQueries.visible(button) && WidgetQueries.indexOf(button.getActions(), "Close") == 0,
            () -> api.get().widget(button, "Close"), callback);
    }

    private boolean defaultAction(Widget widget, String option)
    {
        String[] options = widget.getActions();
        if (options != null && Arrays.stream(options).anyMatch(value -> value != null && !value.isBlank()))
        {
            return WidgetQueries.indexOf(options, option) == 0;
        }
        int id = widget.getId();
        return Set.of((25 << 16) | 12, (25 << 16) | 14, (25 << 16) | 16).contains(id) && "Select".equals(option);
    }

    public boolean inventory(int id, String option, BooleanSupplier allowed, Runnable callback)
    {
        return inventory(id, option, option, allowed, callback);
    }

    /** Sends {@code option} directly, or as a sub-option of the item's {@code menu} action. */
    public boolean inventory(int id, String option, String menu, BooleanSupplier allowed, Runnable callback)
    {
        Widget parent = clients.get().getWidget(149 << 16);
        return itemRequest(parent, item -> item == id, option, menu,
            () -> allowed.getAsBoolean() && !bankingOpen() && clients.get().getWidget(149 << 16) == parent,
            child ->
            {
                int slot = child.getIndex();
                return () -> api.get().inventoryInteract(id, slot, option);
            }, callback);
    }

    public boolean bankingOpen()
    {
        return WidgetQueries.visible(clients.get().getWidget((12 << 16) | 1))
            || WidgetQueries.visible(clients.get().getWidget((192 << 16) | 24))
            || WidgetQueries.visible(clients.get().getWidget((15 << 16) | 3));
    }

    private Widget bankInventory()
    {
        Widget deposit = clients.get().getWidget((192 << 16) | 24);
        return WidgetQueries.visible(deposit) ? deposit : clients.get().getWidget((15 << 16) | 3);
    }

    public boolean deposit(IntPredicate eligible, BooleanSupplier allowed, Runnable callback)
    {
        Widget parent = bankInventory();
        return itemRequest(parent, eligible, "Deposit-All", "Deposit-All", () -> allowed.getAsBoolean() && bankInventory() == parent,
            child ->
            {
                int item = child.getItemId();
                return () -> api.get().bankDepositAll(item);
            }, callback);
    }

    private boolean itemRequest(Widget parent, IntPredicate eligible, String option, String menu, BooleanSupplier allowed,
                                Function<Widget, BooleanSupplier> operation, Runnable callback)
    {
        if (!WidgetQueries.visible(parent) || parent.getDynamicChildren() == null)
        {
            return false;
        }
        for (Widget child : parent.getDynamicChildren())
        {
            if (child != null && eligible.test(child.getItemId()) && validSlot(parent, child, eligible)
                && queueWidget(child, option, menu, () -> allowed.getAsBoolean() && validSlot(parent, child, eligible),
                    operation.apply(child), callback))
            {
                return true;
            }
        }
        return false;
    }

    private boolean validSlot(Widget parent, Widget child, IntPredicate eligible)
    {
        if (!WidgetQueries.visible(parent) || !WidgetQueries.visible(child) || !eligible.test(child.getItemId())
            || parent.getDynamicChildren() == null
            || Arrays.stream(parent.getDynamicChildren()).noneMatch(candidate -> candidate == child))
        {
            return false;
        }
        int slot = child.getIndex();
        Item[] items = InventoryQueries.items(clients.get());
        return slot >= 0 && slot < items.length && items[slot] != null && items[slot].getId() == child.getItemId();
    }

    public boolean hasBankItem(int id)
    {
        Widget parent = clients.get().getWidget(InterfaceID.Bankmain.ITEMS);
        return WidgetQueries.visible(parent) && parent.getDynamicChildren() != null
            && Arrays.stream(parent.getDynamicChildren()).anyMatch(child -> child != null
                && child.getItemId() == id && child.getItemQuantity() > 0);
    }

    /** How many of the item the open bank shows, or 0. */
    public int bankCount(int id)
    {
        Widget parent = clients.get().getWidget(InterfaceID.Bankmain.ITEMS);
        if (!WidgetQueries.visible(parent) || parent.getDynamicChildren() == null)
        {
            return 0;
        }
        return Arrays.stream(parent.getDynamicChildren())
            .filter(child -> child != null && child.getItemId() == id)
            .mapToInt(Widget::getItemQuantity)
            .sum();
    }

    /** Withdraws an amount in one request: Withdraw-1 for one item, Withdraw-X above that. */
    public boolean withdraw(int id, int amount, BooleanSupplier allowed, Runnable callback)
    {
        if (amount <= 1)
        {
            return withdrawOne(id, allowed, callback);
        }
        Widget parent = clients.get().getWidget(InterfaceID.Bankmain.ITEMS);
        if (!WidgetQueries.visible(parent) || parent.getDynamicChildren() == null)
        {
            return false;
        }
        for (Widget child : parent.getDynamicChildren())
        {
            if (child != null && child.getItemId() == id && child.getItemQuantity() >= amount
                && queueWidget(child, "Withdraw-X", "Withdraw-X", () -> allowed.getAsBoolean() && bankingOpen()
                    && clients.get().getVarbitValue(3958) == 0 && clients.get().getWidget(InterfaceID.Bankmain.ITEMS) == parent
                    && child.getItemQuantity() >= amount && WidgetQueries.live(clients.get(), child),
                    () -> api.get().bankWithdrawX(id, amount), callback))
            {
                return true;
            }
        }
        return false;
    }

    public boolean withdrawOne(int id, BooleanSupplier allowed, Runnable callback)
    {
        Widget parent = clients.get().getWidget(InterfaceID.Bankmain.ITEMS);
        if (!WidgetQueries.visible(parent) || parent.getDynamicChildren() == null)
        {
            return false;
        }
        for (Widget child : parent.getDynamicChildren())
        {
            if (child != null && child.getItemId() == id && child.getItemQuantity() > 0
                && queueWidget(child, "Withdraw-1", "Withdraw-1", () -> allowed.getAsBoolean() && bankingOpen()
                    && clients.get().getVarbitValue(3958) == 0 && clients.get().getWidget(InterfaceID.Bankmain.ITEMS) == parent
                    && child.getItemQuantity() > 0 && WidgetQueries.live(clients.get(), child),
                    () -> api.get().bankWithdrawOne(id), callback))
            {
                return true;
            }
        }
        return false;
    }

    private boolean queueWidget(Widget widget, String option, String menu, BooleanSupplier allowed,
                                BooleanSupplier operation, Runnable callback)
    {
        int action = WidgetQueries.indexOf(widget.getActions(), menu);
        int id = widget.getId();
        int slot = widget.getIndex();
        int item = widget.getItemId();
        return queueChecked(() -> allowed.getAsBoolean() && WidgetQueries.visible(widget) && action >= 0 && action <= 9
            && widget.getId() == id && widget.getIndex() == slot && widget.getItemId() == item
            && WidgetQueries.indexOf(widget.getActions(), menu) == action
            && (option.equals(menu) || api.get().inventorySubOption(item, slot, menu, option)), operation, callback);
    }

    public boolean closeBank(BooleanSupplier allowed, Runnable callback)
    {
        if (!bankingOpen())
        {
            return true;
        }
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int group = WidgetQueries.visible(clients.get().getWidget((192 << 16) | 24)) ? 192 : 12;
        if (closeInTree(clients.get().getWidget(group, 0), visited, allowed, callback))
        {
            return true;
        }
        for (Widget root : clients.get().getWidgetRoots())
        {
            if (root != null && (root.getId() >>> 16) == group && closeInTree(root, visited, allowed, callback))
            {
                return true;
            }
        }
        return false;
    }

    private boolean closeInTree(Widget widget, Set<Widget> visited, BooleanSupplier allowed, Runnable callback)
    {
        if (!WidgetQueries.visible(widget) || !visited.add(widget))
        {
            return false;
        }
        if (queueWidget(widget, "Close", "Close", () -> allowed.getAsBoolean() && bankingOpen()
                && WidgetQueries.live(clients.get(), widget), () -> api.get().widget(widget, "Close"), callback))
        {
            return true;
        }
        for (Widget[] children : new Widget[][] {widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren()})
        {
            if (children != null)
            {
                for (Widget child : children)
                {
                    if (closeInTree(child, visited, allowed, callback))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
