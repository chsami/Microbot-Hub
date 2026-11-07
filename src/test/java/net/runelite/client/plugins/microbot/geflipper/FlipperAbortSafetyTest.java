package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the script's actual Abort wiring, including Copilot reflection and the one-based selected slot. */
class FlipperAbortSafetyTest {
    private static final int ITEM_ID = 100;
    private static final int SLOT = 3;

    @Test
    void matchingActiveBuyAndSellDetailsPermitAbortOnTheClientThread() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
                GrandExchangeOfferState.BUYING, GrandExchangeOfferState.SELLING}) {
                fixture.offers[SLOT] = fixture.offer(ITEM_ID, state);
                assertTrue(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT, ITEM_ID)), state.name());
            }
            assertTrue(fixture.uiReads > 0, "Offer and selected-slot checks must use the client thread");
        }
    }

    @Test
    void differentViewedSlotCannotAbortTheSuggestedOffer() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.offers[SLOT + 1] = fixture.offer(ITEM_ID, GrandExchangeOfferState.BUYING);
            fixture.selectedSlot = SLOT + 2;
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT, ITEM_ID)));
            fixture.selectedSlot = SLOT + 1;
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT + 1, ITEM_ID)));
        }
    }

    @Test
    void aReplacementItemInTheSameSlotCannotBeAborted() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT, ITEM_ID + 1)));
            fixture.offers[SLOT] = fixture.offer(ITEM_ID + 1, GrandExchangeOfferState.SELLING);
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT, ITEM_ID)));
            assertTrue(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT, ITEM_ID + 1)));
        }
    }

    @Test
    void repeatedCancelledOrCompletedRecommendationsCannotAbortAgain() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Suggestion suggestion = new Suggestion(SLOT, ITEM_ID);
            assertTrue(fixture.script.abortOfferDetailsMatches(suggestion));
            for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
                GrandExchangeOfferState.CANCELLED_BUY, GrandExchangeOfferState.CANCELLED_SELL,
                GrandExchangeOfferState.BOUGHT, GrandExchangeOfferState.SOLD, GrandExchangeOfferState.EMPTY}) {
                fixture.offers[SLOT] = fixture.offer(ITEM_ID, state);
                assertFalse(fixture.script.abortOfferDetailsMatches(suggestion), state.name());
            }
        }
    }

    @Test
    void aMissingOrOutOfRangeSelectionNeverPermitsAbort() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Suggestion suggestion = new Suggestion(SLOT, ITEM_ID);
            for (int selected : new int[]{0, -1, 9}) {
                fixture.selectedSlot = selected;
                assertFalse(fixture.script.abortOfferDetailsMatches(suggestion), "selected varbit " + selected);
            }
            fixture.selectedSlot = SLOT + 1;
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(-1, ITEM_ID)));
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(8, ITEM_ID)));
            assertFalse(fixture.script.abortOfferDetailsMatches(new Suggestion(SLOT, 0)));
            fixture.offers[SLOT] = null;
            assertFalse(fixture.script.abortOfferDetailsMatches(suggestion));
            fixture.offers = null;
            assertFalse(fixture.script.abortOfferDetailsMatches(suggestion));
        }
    }

    @Test
    void unavailableCopilotIdentifiersFailClosed() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertFalse(fixture.script.abortOfferDetailsMatches(null));
            assertFalse(fixture.script.abortOfferDetailsMatches(new Object()));
            assertFalse(fixture.script.abortOfferDetailsMatches(new BrokenSuggestion()));
        }
    }

    @Test
    void onlyVisibleWidgetsWithAnActualAbortOperationAreAbortButtons() {
        assertTrue(FlipperScript.hasAbortAction(widget(false, "Abort")));
        assertTrue(FlipperScript.hasAbortAction(widget(false, "Abort offer")));
        assertTrue(FlipperScript.hasAbortAction(widget(false, null, "<col=ff0000>Abort offer</col>")));
        assertFalse(FlipperScript.hasAbortAction(widget(true, "Abort offer")));
        assertFalse(FlipperScript.hasAbortAction(widget(false, "Graph")));
        assertFalse(FlipperScript.hasAbortAction(widget(false, "Copilot graph", "View offer")));
        assertFalse(FlipperScript.hasAbortAction(widget(false, "Modify offer")));
        assertFalse(FlipperScript.hasAbortAction(widget(false, "Do not abort offer")));
        assertFalse(FlipperScript.hasAbortAction(widget(false)));
        assertFalse(FlipperScript.hasAbortAction(widget(false, (String[]) null)));
        assertFalse(FlipperScript.hasAbortAction(null));
    }

    public static final class Suggestion {
        private final int slot, itemId;
        public Suggestion(int slot, int itemId) { this.slot = slot; this.itemId = itemId; }
        public int getBoxId() { return slot; }
        public int getItemId() { return itemId; }
    }

    public static final class BrokenSuggestion {
        public int getBoxId() { throw new IllegalStateException("Synthetic unavailable suggestion"); }
        public int getItemId() { return ITEM_ID; }
    }

    private static Widget widget(boolean hidden, String... actions) {
        return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isHidden": return hidden;
                    case "getActions": return actions;
                    default: throw new AssertionError("Unexpected Abort-widget read: " + method.getName());
                }
            });
    }

    private static final class Fixture implements AutoCloseable {
        private final Field clientField = field(Microbot.class, "client");
        private final Field threadField = field(Microbot.class, "clientThread");
        private final Object previousClient = clientField.get(null);
        private final Object previousThread = threadField.get(null);
        private final FlipperScript script = new FlipperScript();
        private GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        private int selectedSlot = SLOT + 1;
        private int uiReads;
        private boolean onClientThread;
        private final ClientThread inline = new ClientThread() {
            @Override public <T> Optional<T> runOnClientThreadOptional(Callable<T> call) {
                assertFalse(onClientThread, "Abort validation must not nest client-thread dispatch");
                onClientThread = true;
                uiReads++;
                try { return Optional.ofNullable(call.call()); }
                catch (Exception error) { throw new AssertionError(error); }
                finally { onClientThread = false; }
            }
        };

        private Fixture() throws Exception {
            offers[SLOT] = offer(ITEM_ID, GrandExchangeOfferState.BUYING);
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    assertTrue(onClientThread, "Abort UI read outside client thread: " + method.getName());
                    switch (method.getName()) {
                        case "getVarbitValue":
                            assertEquals(VarbitID.GE_SELECTEDSLOT, args[0]);
                            return selectedSlot;
                        case "getGrandExchangeOffers": return offers;
                        default: throw new AssertionError("Unexpected Abort-client read: " + method.getName());
                    }
                });
            clientField.set(null, client);
            threadField.set(null, inline);
        }

        private GrandExchangeOffer offer(int itemId, GrandExchangeOfferState state) {
            return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
                new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                    assertTrue(onClientThread, "Offer state must be inspected on the client thread");
                    switch (method.getName()) {
                        case "getItemId": return itemId;
                        case "getState": return state;
                        default: throw new AssertionError("Unexpected Abort-offer read: " + method.getName());
                    }
                });
        }

        @Override public void close() throws Exception {
            clientField.set(null, previousClient);
            threadField.set(null, previousThread);
            ((ScheduledExecutorService) field(ClientThread.class, "scheduledExecutorService").get(inline)).shutdownNow();
        }
    }
}
