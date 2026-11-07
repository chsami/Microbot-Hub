package net.runelite.client.plugins.microbot.geflipper;

import java.util.Locale;
import java.util.Objects;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;

/**
 * Guards for generic trading clicks and the offer-details abort button.
 * Callers read client-owned offers and menu entries on the client thread.
 */
final class TradingClickSafety {
    private TradingClickSafety() { }

    static boolean activeOfferMatches(GrandExchangeOffer offer, int expectedItemId) {
        if (offer == null || expectedItemId <= 0 || offer.getItemId() != expectedItemId) {
            return false;
        }
        GrandExchangeOfferState state = offer.getState();
        return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING;
    }

    static boolean abortDetailsMatches(GrandExchangeOffer[] offers, int selectedSlot,
                                       int suggestedSlot, int expectedItemId) {
        return offers != null && selectedSlot >= 0 && selectedSlot < offers.length
            && selectedSlot == suggestedSlot && activeOfferMatches(offers[selectedSlot], expectedItemId);
    }

    /** Dynamic children share a parent ID, so the parent alone cannot identify the target. */
    static boolean targetsWidget(MenuEntry entry, int widgetId, int childIndex, int itemId) {
        return entry != null && entry.getParam1() == widgetId
            && (childIndex < 0 || entry.getParam0() == childIndex)
            && (itemId <= 0 || entry.getItemId() <= 0 || entry.getItemId() == itemId);
    }

    static boolean safeDefault(MenuEntry entry) {
        return snapshot(entry) != null;
    }

    static boolean sameDefault(MenuEntry expected, MenuEntry actual) {
        MenuSnapshot captured = snapshot(expected);
        return captured != null && captured.matchesEntry(actual);
    }

    /** RuneLite keeps the actual left-click operation at the end of the menu array. */
    static MenuSnapshot captureDefault(MenuEntry[] entries) {
        return entries == null || entries.length == 0 ? null : snapshot(entries[entries.length - 1]);
    }

    private static MenuSnapshot snapshot(MenuEntry entry) {
        if (entry == null) return null;
        MenuSnapshot captured = new MenuSnapshot(entry);
        return safeOption(captured.type, captured.option) ? captured : null;
    }

    private static boolean safeOption(MenuAction type, String option) {
        if (type == null || type == MenuAction.CANCEL || option == null) return false;
        String plain = option.replaceAll("<[^>]*>", "").replace('\u00a0', ' ')
            .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return !plain.isEmpty() && !plain.contains("graph") && !plain.equals("cancel")
            && !plain.equals("abort offer") && !plain.equals("modify offer");
    }

    /** Copies values rather than retaining the client's mutable MenuEntry object. */
    static final class MenuSnapshot {
        private final MenuAction type;
        private final int identifier;
        private final int param0;
        private final int param1;
        private final int itemId;
        private final String option;
        private final String target;

        private MenuSnapshot(MenuEntry entry) {
            type = entry.getType();
            identifier = entry.getIdentifier();
            param0 = entry.getParam0();
            param1 = entry.getParam1();
            itemId = entry.getItemId();
            option = entry.getOption();
            target = entry.getTarget();
        }

        boolean matches(MenuEntry[] entries) {
            return entries != null && entries.length > 0 && matchesEntry(entries[entries.length - 1]);
        }

        private boolean matchesEntry(MenuEntry entry) {
            MenuSnapshot actual = snapshot(entry);
            return actual != null && type == actual.type && identifier == actual.identifier
                && param0 == actual.param0 && param1 == actual.param1 && itemId == actual.itemId
                && Objects.equals(option, actual.option) && Objects.equals(target, actual.target);
        }
    }
}
