package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Proxy;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingClickSafetyTest {
    private static final int ITEM_ID = 100;

    @Test
    void onlyMatchingActiveBuyAndSellOffersCanBeAborted() {
        for (GrandExchangeOfferState state : GrandExchangeOfferState.values()) {
            boolean active = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING;
            if (active) {
                assertTrue(TradingClickSafety.activeOfferMatches(offer(ITEM_ID, state), ITEM_ID), state.name());
            } else {
                assertFalse(TradingClickSafety.activeOfferMatches(offer(ITEM_ID, state), ITEM_ID), state.name());
            }
        }
    }

    @Test
    void missingOrDifferentItemsCannotBeAborted() {
        assertFalse(TradingClickSafety.activeOfferMatches(null, ITEM_ID));
        assertFalse(TradingClickSafety.activeOfferMatches(offer(ITEM_ID, null), ITEM_ID));
        assertFalse(TradingClickSafety.activeOfferMatches(offer(ITEM_ID + 1, GrandExchangeOfferState.BUYING), ITEM_ID));
        assertFalse(TradingClickSafety.activeOfferMatches(offer(0, GrandExchangeOfferState.BUYING), 0));
        assertFalse(TradingClickSafety.activeOfferMatches(offer(-1, GrandExchangeOfferState.SELLING), -1));
    }

    @Test
    void offerDetailsMustBeTheSuggestedActiveSlotAndItem() {
        GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        offers[3] = offer(ITEM_ID, GrandExchangeOfferState.SELLING);
        assertTrue(TradingClickSafety.abortDetailsMatches(offers, 3, 3, ITEM_ID));
        assertFalse(TradingClickSafety.abortDetailsMatches(offers, 3, 2, ITEM_ID));
        assertFalse(TradingClickSafety.abortDetailsMatches(offers, 2, 2, ITEM_ID));
        assertFalse(TradingClickSafety.abortDetailsMatches(offers, 3, 3, ITEM_ID + 1));
        assertFalse(TradingClickSafety.abortDetailsMatches(offers, 3, 3, 0));
        assertFalse(TradingClickSafety.abortDetailsMatches(offers, -1, -1, ITEM_ID));
        assertFalse(TradingClickSafety.abortDetailsMatches(offers, 8, 8, ITEM_ID));
        assertFalse(TradingClickSafety.abortDetailsMatches(null, 3, 3, ITEM_ID));
        assertFalse(TradingClickSafety.abortDetailsMatches(new GrandExchangeOffer[0], 0, 0, ITEM_ID));
    }

    @Test
    void cancelledAndCompletedOffersCannotBeAbortedAgain() {
        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
            GrandExchangeOfferState.CANCELLED_BUY, GrandExchangeOfferState.CANCELLED_SELL,
            GrandExchangeOfferState.BOUGHT, GrandExchangeOfferState.SOLD}) {
            GrandExchangeOffer[] offers = {offer(ITEM_ID, state)};
            assertFalse(TradingClickSafety.abortDetailsMatches(offers, 0, 0, ITEM_ID), state.name());
        }
    }

    @Test
    void aDynamicWidgetRequiresItsOwnParentChildAndKnownItem() {
        MutableEntry current = entry("Sell");
        assertTrue(TradingClickSafety.targetsWidget(current.menu, current.param1, current.param0, ITEM_ID));
        assertFalse(TradingClickSafety.targetsWidget(current.menu, current.param1 + 1, current.param0, ITEM_ID));
        assertFalse(TradingClickSafety.targetsWidget(current.menu, current.param1, current.param0 + 1, ITEM_ID));
        assertFalse(TradingClickSafety.targetsWidget(current.menu, current.param1, current.param0, ITEM_ID + 1));
        assertFalse(TradingClickSafety.targetsWidget(null, current.param1, current.param0, ITEM_ID));
    }

    @Test
    void aStaticWidgetDoesNotRequireADynamicChildOrItem() {
        MutableEntry current = entry("Confirm");
        assertTrue(TradingClickSafety.targetsWidget(current.menu, current.param1, -1, -1));
        current.param0 = -1;
        assertTrue(TradingClickSafety.targetsWidget(current.menu, current.param1, -1, 0));
        assertFalse(TradingClickSafety.targetsWidget(current.menu, current.param1 + 1, -1, -1));
    }

    @Test
    void anUnavailableMenuItemIdDoesNotRejectAnOtherwiseMatchingDynamicWidget() {
        MutableEntry current = entry("Collect");
        current.itemId = -1;
        assertTrue(TradingClickSafety.targetsWidget(current.menu, current.param1, current.param0, ITEM_ID));
        current.itemId = 0;
        assertTrue(TradingClickSafety.targetsWidget(current.menu, current.param1, current.param0, ITEM_ID));
        assertFalse(TradingClickSafety.targetsWidget(current.menu, current.param1, current.param0 + 1, ITEM_ID));
    }

    @Test
    void ordinaryBuySellCollectAndNavigationDefaultsRemainUsable() {
        for (String option : new String[]{"Buy", "Sell", "Collect", "View offer", "Confirm", "Close",
            "<col=ffffff>Collect</col>", " Buy ", "View\u00a0offer"}) {
            assertTrue(TradingClickSafety.safeDefault(entry(option).menu), option);
        }
    }

    @Test
    void graphDefaultsAreRejectedRegardlessOfCaseOrFormatting() {
        for (String option : new String[]{"Graph", "Copilot graph", "View price graph", "Show Copilot Graph",
            "<col=ffff00>SHOW PRICE GRAPH</col>", " <col=ff00ff>Price</col> graph "}) {
            assertFalse(TradingClickSafety.safeDefault(entry(option).menu), option);
        }
    }

    @Test
    void destructiveDefaultsRequireTheirDedicatedVerifiedHandler() {
        for (String option : new String[]{"Abort offer", "Modify offer", " ABORT  OFFER ",
            "<col=ffff00>Modify offer</col>", "Abort\u00a0offer", " <col=ffffff>Modify\u00a0offer</col> "}) {
            assertFalse(TradingClickSafety.safeDefault(entry(option).menu), option);
        }
    }

    @Test
    void cancelAndAbsentDefaultsAreNotActions() {
        assertFalse(TradingClickSafety.safeDefault(null));
        for (String option : new String[]{null, "", " ", "Cancel", "<col=ffffff>CANCEL</col>"}) {
            assertFalse(TradingClickSafety.safeDefault(entry(option).menu));
        }
        MutableEntry cancelled = entry("Collect");
        cancelled.type = MenuAction.CANCEL;
        assertFalse(TradingClickSafety.safeDefault(cancelled.menu));
        cancelled.type = null;
        assertFalse(TradingClickSafety.safeDefault(cancelled.menu));
    }

    @Test
    void aGraphOnTopCannotFallBackToTheLegitimateEntryUnderneath() {
        assertNull(TradingClickSafety.captureDefault(new MenuEntry[]{entry("Collect").menu, entry("Price graph").menu}));
        assertNull(TradingClickSafety.captureDefault(null));
        assertNull(TradingClickSafety.captureDefault(new MenuEntry[0]));
        assertNull(TradingClickSafety.captureDefault(new MenuEntry[]{null}));
    }

    @Test
    void equivalentDefaultMenuTuplesMatch() {
        MutableEntry expected = entry("Collect");
        MutableEntry actual = entry("Collect");
        TradingClickSafety.MenuSnapshot captured = TradingClickSafety.captureDefault(new MenuEntry[]{expected.menu});
        assertNotNull(captured);
        assertTrue(captured.matches(new MenuEntry[]{actual.menu}));
        assertTrue(captured.matches(new MenuEntry[]{entry("View price graph").menu, actual.menu}));
        assertTrue(TradingClickSafety.sameDefault(expected.menu, actual.menu));
    }

    @Test
    void anAbsentTargetOnANavigationActionIsCapturedWithoutInventingAnItem() {
        MutableEntry expected = entry("Confirm");
        MutableEntry actual = entry("Confirm");
        expected.target = null;
        actual.target = null;
        TradingClickSafety.MenuSnapshot captured = TradingClickSafety.captureDefault(new MenuEntry[]{expected.menu});
        assertNotNull(captured);
        assertTrue(captured.matches(new MenuEntry[]{actual.menu}));
        actual.target = "Item";
        assertFalse(captured.matches(new MenuEntry[]{actual.menu}));
    }

    @Test
    void changedActionTypeIdentifierWidgetItemOptionOrTargetCannotMatch() {
        for (int field = 0; field < 7; field++) {
            MutableEntry expected = entry("Collect");
            MutableEntry actual = entry("Collect");
            switch (field) {
                case 0: actual.type = MenuAction.RUNELITE; break;
                case 1: actual.identifier++; break;
                case 2: actual.param0++; break;
                case 3: actual.param1++; break;
                case 4: actual.itemId++; break;
                case 5: actual.option = "View offer"; break;
                case 6: actual.target = "Different target"; break;
                default: throw new AssertionError();
            }
            assertFalse(TradingClickSafety.sameDefault(expected.menu, actual.menu), "changed field " + field);
        }
    }

    @Test
    void immutableSnapshotDetectsInPlaceMenuMutation() {
        MutableEntry mutable = entry("Collect");
        MenuEntry[] menu = {mutable.menu};
        TradingClickSafety.MenuSnapshot captured = TradingClickSafety.captureDefault(menu);
        assertNotNull(captured);
        mutable.param1++;
        assertFalse(captured.matches(menu));
        mutable.param1--;
        assertTrue(captured.matches(menu));
        mutable.option = "Price graph";
        assertFalse(captured.matches(menu));
        mutable.option = "Collect";
        mutable.target = null;
        assertFalse(captured.matches(menu));
    }

    @Test
    void menuDisappearanceOrNewUnsafeTopEntryCancelsTheCapturedClick() {
        TradingClickSafety.MenuSnapshot captured = TradingClickSafety.captureDefault(new MenuEntry[]{entry("Buy").menu});
        assertNotNull(captured);
        assertFalse(captured.matches(null));
        assertFalse(captured.matches(new MenuEntry[0]));
        assertFalse(captured.matches(new MenuEntry[]{null}));
        assertFalse(captured.matches(new MenuEntry[]{entry("Buy").menu, entry("Modify offer").menu}));
        assertFalse(TradingClickSafety.sameDefault(entry("Graph").menu, entry("Graph").menu));
    }

    private static GrandExchangeOffer offer(int itemId, GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItemId": return itemId;
                    case "getState": return state;
                    default: throw new AssertionError("Unexpected offer read: " + method.getName());
                }
            });
    }

    private static MutableEntry entry(String option) {
        return new MutableEntry(option);
    }

    private static final class MutableEntry {
        private MenuAction type = MenuAction.CC_OP;
        private int identifier = 1;
        private int param0 = 2;
        private int param1 = 465 << 16 | 7;
        private int itemId = ITEM_ID;
        private String option;
        private String target = "Item";
        private final MenuEntry menu;

        private MutableEntry(String option) {
            this.option = option;
            menu = (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(),
                new Class<?>[]{MenuEntry.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getType": return type;
                        case "getIdentifier": return identifier;
                        case "getParam0": return param0;
                        case "getParam1": return param1;
                        case "getItemId": return itemId;
                        case "getOption": return this.option;
                        case "getTarget": return target;
                        default: throw new AssertionError("Unexpected menu read: " + method.getName());
                    }
                });
        }
    }
}
