package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.support.CaveAccessRequirements;
import net.runelite.client.plugins.microbot.shortestpath.PurchasableItemCatalog;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

/**
 * Shantay Pass access and execution support for Efficient Walker.
 *
 * <p>Handles the three ways to cross south into the Kharidian Desert (carried {@link ItemID#SHANTAY_PASS},
 * buying a pass from Shantay NPC {@code 4642} for 5 coins, or Elite Desert Diary free passage) and
 * accepts the desert warning interface (widget group {@code 565}, child {@code 17}, {@code "Yes"}).</p>
 */
final class ShantayPassAccess
{
    static final int WARNING_GROUP = CaveAccessRequirements.SHANTAY_WARNING_WIDGET_GROUP;
    static final int WARNING_YES_CHILD = CaveAccessRequirements.SHANTAY_WARNING_YES_CHILD;
    static final int PASS_COST = CaveAccessRequirements.SHANTAY_PASS_COST;

    private ShantayPassAccess()
    {
    }

    static boolean isPass(Transport transport)
    {
        return CaveAccessRequirements.isShantayPass(transport);
    }

    static boolean isSouthboundPass(Transport transport)
    {
        return CaveAccessRequirements.isShantayPassSouthbound(transport);
    }

    static boolean isMainPass(Transport transport)
    {
        return isPass(transport) && transport.getObjectId() == CaveAccessRequirements.SHANTAY_PASS_MAIN_OBJECT_ID;
    }

    static boolean replacesPreservedEdge(int objectId, WorldPoint origin)
    {
        return origin != null && CaveAccessRequirements.isShantayPassObjectId(objectId);
    }

    static boolean hasDesertDiaryElite()
    {
        return Microbot.getVarbitValue(CaveAccessRequirements.DESERT_DIARY_ELITE_VARBIT) == 1;
    }

    static boolean isDesertTarget(WorldPoint target)
    {
        return CaveAccessRequirements.isDesertSouthOfShantay(target);
    }

    static boolean needsSouthboundEntry(WorldPoint start, WorldPoint target)
    {
        return CaveAccessRequirements.isNorthOfShantay(start) && CaveAccessRequirements.isDesertSouthOfShantay(target);
    }

    static boolean requirementsSatisfied(Transport transport, boolean eliteDiary, int carriedPasses, int carriedCoins)
    {
        if (!isPass(transport))
        {
            return false;
        }
        if (!isSouthboundPass(transport) || eliteDiary)
        {
            return true;
        }
        if (carriedPasses > 0)
        {
            return true;
        }
        return isMainPass(transport) && carriedCoins >= PASS_COST;
    }

    static final class Warning
    {
        final int group;
        final Selection selection;

        Warning(int group, Selection selection)
        {
            this.group = group;
            this.selection = selection;
        }
    }

    static final class Selection
    {
        final Widget identity;
        final int id;
        final int index;
        final int operation;
        final MenuAction type;
        final String option;
        final Rectangle bounds;

        Selection(Widget identity, int operation, MenuAction type, String option, Rectangle bounds)
        {
            this.identity = identity;
            this.id = identity.getId();
            this.index = identity.getIndex();
            this.operation = operation;
            this.type = type;
            this.option = option;
            this.bounds = new Rectangle(bounds);
        }

        NewMenuEntry menu()
        {
            return new NewMenuEntry()
                .widget(identity)
                .param0(index)
                .param1(id)
                .identifier(operation)
                .type(type)
                .option(option)
                .target("")
                .itemId(-1)
                .forceLeftClick(false);
        }
    }

    /** CLIENT THREAD. Inspects widget group 565 (desert warning) for the "Yes" confirmation button. */
    static Warning warning(Client client)
    {
        if (client == null)
        {
            return new Warning(-1, null);
        }
        Widget yesWidget = client.getWidget(WARNING_GROUP, WARNING_YES_CHILD);
        if (yesWidget != null && !yesWidget.isHidden())
        {
            Selection direct = selection(yesWidget);
            if (direct != null)
            {
                return new Warning(WARNING_GROUP, direct);
            }
        }
        Widget root = client.getWidget(WARNING_GROUP, 0);
        if (root == null || root.isHidden())
        {
            return new Warning(-1, null);
        }
        Selection scanned = findAffirmative(root, Collections.newSetFromMap(new IdentityHashMap<>()));
        return new Warning(WARNING_GROUP, scanned);
    }

    private static Selection findAffirmative(Widget widget, Set<Widget> seen)
    {
        if (widget == null || !seen.add(widget) || widget.isHidden())
        {
            return null;
        }
        Selection match = selection(widget);
        if (match != null)
        {
            return match;
        }
        for (Widget[] group : new Widget[][]{
            widget.getChildren(), widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()
        })
        {
            if (group == null)
            {
                continue;
            }
            for (Widget child : group)
            {
                Selection nested = findAffirmative(child, seen);
                if (nested != null)
                {
                    return nested;
                }
            }
        }
        return null;
    }

    private static Selection selection(Widget widget)
    {
        Rectangle bounds = widget.getBounds();
        if (bounds == null || bounds.isEmpty())
        {
            return null;
        }
        String[] actions = widget.getActions();
        if (actions != null)
        {
            for (int i = 0; i < actions.length; i++)
            {
                String action = normalized(actions[i]);
                if ("yes".equals(action) || "proceed".equals(action) || "enter".equals(action))
                {
                    return new Selection(widget, i + 1, MenuAction.CC_OP, actions[i], bounds);
                }
            }
        }
        String text = normalized(widget.getText());
        if ("yes".equals(text) || "proceed".equals(text))
        {
            if (!widget.isIf3())
            {
                return new Selection(widget, 0, MenuAction.WIDGET_TYPE_1, "Yes", bounds);
            }
            return new Selection(widget, 1, MenuAction.CC_OP, "Yes", bounds);
        }
        return null;
    }

    static boolean acceptWarning(Client client, Transport transport, Warning expected)
    {
        if (!isPass(transport) || expected == null || expected.group < 0 || expected.selection == null
            || !LumbridgeSwampCaveAccess.inputAllowed())
        {
            return false;
        }
        Selection fresh = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!LumbridgeSwampCaveAccess.inputAllowed() || client.getGameState() != GameState.LOGGED_IN
                || client.getLocalPlayer() == null || client.getLocalPlayer().getWorldLocation() == null
                || (transport.getOrigin() != null
                    && client.getLocalPlayer().getWorldLocation().distanceTo(transport.getOrigin()) > 12))
            {
                return null;
            }
            Warning current = warning(client);
            return current != null && current.group == expected.group && current.selection != null
                ? current.selection : null;
        }).orElse(null);
        if (fresh == null || !LumbridgeSwampCaveAccess.inputAllowed())
        {
            return false;
        }
        Microbot.doInvoke(fresh.menu(), fresh.bounds);
        return true;
    }

    /**
     * Buys a Shantay pass from Shantay ({@code 4642}, {@code Buy-pass}) when crossing south without
     * an existing pass or Desert Diary Elite.
     */
    static boolean ensurePassBeforeCrossing(Transport transport)
    {
        if (!isSouthboundPass(transport) || hasDesertDiaryElite() || Rs2Inventory.hasItem(ItemID.SHANTAY_PASS))
        {
            return true;
        }
        if (!isMainPass(transport) || !LumbridgeSwampCaveAccess.inputAllowed())
        {
            return false;
        }
        PurchasableItemCatalog.PurchasableItem purchasable = PurchasableItemCatalog.forTransport(transport);
        int vendorId = purchasable != null ? purchasable.vendorNpcId : CaveAccessRequirements.SHANTAY_VENDOR_NPC_ID;
        String action = purchasable != null ? purchasable.vendorAction : "Buy-pass";
        int itemId = purchasable != null ? purchasable.itemId : ItemID.SHANTAY_PASS;
        if (Rs2Inventory.itemQuantity(ItemID.COINS) < PASS_COST)
        {
            return false;
        }
        if (!Rs2Npc.interact(vendorId, action))
        {
            return false;
        }
        return Global.sleepUntil(() -> Rs2Inventory.hasItem(itemId) || !LumbridgeSwampCaveAccess.inputAllowed(), 4_000)
            && Rs2Inventory.hasItem(itemId);
    }

    private static String normalized(String text)
    {
        return text == null ? "" : text.replaceAll("<[^>]*>", " ").replace('\u2019', '\'')
            .replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
