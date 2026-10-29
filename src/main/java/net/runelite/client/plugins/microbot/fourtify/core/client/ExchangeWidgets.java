package net.runelite.client.plugins.microbot.fourtify.core.client;

import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GE interface 465 and scripts ge_offers_setup_init/draw: static panels and dynamic setup controls. */
final class ExchangeWidgets {
    static final int GROUP = 465;
    static final int CONTENTS = 1, BACK = 4, DETAILS = 15, SETUP = 26, CONFIRM = 30;
    static final int ALL = 6, PRICE = 12;
    static final int QUANTITY_VARBIT = 4396, TYPE_VARBIT = 4397;
    static final int ITEM_VARP = 1151;
    private static final Pattern COINS = Pattern.compile("([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+) coins?");
    private ExchangeWidgets() { }

    static boolean visible(Client client, int child) {
        Widget widget = client.getWidget(GROUP, child);
        return widget != null && !widget.isHidden();
    }

    static boolean priceInput(Client client) {
        Widget label = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
        Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        // The server supplies the prompt. Require a visible price-labelled input instead of guessing its mode number.
        return visible(client, SETUP) && client.getVarcIntValue(VarClientID.MESLAYERMODE) > 0
                && label != null && !label.isHidden() && input != null && !input.isHidden()
                && label.getText() != null && label.getText().toLowerCase(Locale.ROOT).contains("price");
    }

    /** Read the labelled unit price, not the total. Price varp types differ between client revisions. */
    static long unitPrice(Client client) {
        Widget setup = client.getWidget(GROUP, SETUP);
        if (setup == null || setup.isHidden()) { return -1L; }
        Widget label = setup.getChild(37);
        Widget value = setup.getChild(41);
        if (label == null || value == null || label.isHidden() || value.isHidden()
                || !"Price per item:".equals(text(label))) { return -1L; }
        Matcher match = COINS.matcher(text(value));
        if (!match.matches()) { return -1L; }
        try { return Long.parseLong(match.group(1).replace(",", "")); }
        catch (NumberFormatException invalid) { return -1L; }
    }

    /**
     * The generic popup interface 289 (popupoverlay) is shared with other popups. Script 5196 builds it from one
     * server string, "title|body|first button|second button": the body is a text child created inside the list
     * layer 289:6, and the two stone buttons 289:7 and 289:8 get their label as their first action. The server
     * string is sent at run time, so the order of the buttons is not known; the Yes button is found by its action.
     */
    static final int POPUP = 289, POPUP_BODY = 6, POPUP_BUTTON_0 = 7, POPUP_BUTTON_1 = 8;
    static final String LOW_PRICE_WARNING = "Your offer is much";

    /** The low-price warning is open: the visible popup body says so. */
    static boolean priceWarning(Client client) {
        Widget body = client.getWidget(POPUP, POPUP_BODY);
        if (body == null || body.isHidden()) { return false; }
        if (text(body).contains(LOW_PRICE_WARNING)) { return true; }
        Widget[] lines = body.getDynamicChildren();
        if (lines == null) { return false; }
        for (Widget line : lines) {
            if (line != null && !line.isHidden() && text(line).contains(LOW_PRICE_WARNING)) { return true; }
        }
        return false;
    }

    /** The visible popup button whose action is "Yes", or null. Call it only while {@link #priceWarning} holds. */
    static Widget warningYes(Client client) {
        for (int child : new int[]{POPUP_BUTTON_0, POPUP_BUTTON_1}) {
            Widget button = client.getWidget(POPUP, child);
            if (button != null && !button.isHidden() && actionIndex(button, "Yes") > 0) { return button; }
        }
        return null;
    }

    private static String text(Widget widget) {
        return widget.getText() == null ? "" : widget.getText().replaceAll("<[^>]*>", "").trim();
    }

    /**
     * The game's script 818 fills up to two collection slots in the offer details panel (465:24): the items and the
     * coins, such as a cancelled offer that partly filled. Script 9909 sets their actions.
     */
    static final int COLLECT_SLOTS = 2;
    /** Script 9909: coins and other items without a note form get "Collect"; the others "Collect-note(s)" and "Collect-item(s)". */
    private static final String[] COLLECT_ACTIONS = {"Collect", "Collect-note", "Collect-notes", "Collect-item", "Collect-items"};

    /** A finished offer that is not a bond: the same rule as {@code ExchangeOffer#collectable} without the one-coin stop. */
    static boolean collectable(Client client, int slot) {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        return offers != null && slot >= 0 && slot < offers.length && offers[slot] != null
                && ExchangeOffer.finished(offers[slot].getState()) && !Bonds.IDS.contains(offers[slot].getItemId());
    }

    /** The action that takes this slot's contents: "Bank" (script 9909 op 3) or the first collect action present. */
    static String collectAction(Widget widget, boolean toBank) {
        if (toBank) { return actionIndex(widget, "Bank") > 0 ? "Bank" : null; }
        for (String action : COLLECT_ACTIONS) {
            if (actionIndex(widget, action) > 0) { return action; }
        }
        return null;
    }

    /** The details panel can appear before its collect children are populated. */
    static List<Widget> collectionButtons(Client client, int slot, boolean toBank) {
        List<Widget> buttons = new ArrayList<>();
        if (!collectable(client, slot) || !visible(client, DETAILS) || client.getVarbitValue(4439) != slot + 1) { return buttons; }
        Widget parent = client.getWidget(GROUP, 24);
        if (parent == null || parent.isHidden() || parent.getDynamicChildren() == null) { return buttons; }
        for (Widget child : parent.getDynamicChildren()) {
            // The other dynamic children are backdrops and the "Lots!" label, which hold no item.
            if (child != null && !child.isHidden() && child.getItemId() > 0 && child.getItemQuantity() > 0
                    && collectAction(child, toBank) != null) {
                buttons.add(child);
            }
        }
        return buttons;
    }

    static Widget collectionButton(Client client, int slot, boolean toBank) {
        List<Widget> buttons = collectionButtons(client, slot, toBank);
        return buttons.isEmpty() ? null : buttons.get(0);
    }

    static int actionIndex(Widget widget, String action) {
        String[] actions = widget.getActions();
        if (actions != null) {
            for (int i = 0; i < actions.length; i++) {
                if (action.equals(actions[i])) { return i + 1; }
            }
        }
        return -1;
    }
}
