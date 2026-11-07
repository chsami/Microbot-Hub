package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Canvas;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MuleRuntimeTest {
    @Test
    void receiverLoginRejectsManualTakeoverAndInterruptedWorkersBeforeReadingAnyProfile() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.gameState = GameState.LOGIN_SCREEN;
            InputArbiter.onRealKeyPressed(KeyEvent.VK_SHIFT);
            fixture.runtime.login(301);
            assertTrue(fixture.backend.events.isEmpty());
            InputArbiter.resetForTest();
            Thread.currentThread().interrupt();
            try {
                fixture.runtime.login(301);
                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(fixture.backend.events.isEmpty());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"WORLD", "CREDENTIALS", "SUBMIT"})
    void queuedLoginStepCannotDispatchAfterItsOwnerWasCancelled(String action) throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            AtomicBoolean owned = new AtomicBoolean(true);
            fixture.beforeClientRead = () -> owned.set(false);
            java.lang.reflect.Method method = MuleRuntime.class.getDeclaredMethod("performLoginStep",
                MuleLogin.Step.class, int.class, int.class, BooleanSupplier.class);
            method.setAccessible(true);
            assertEquals(Boolean.FALSE, method.invoke(fixture.runtime, MuleLogin.Step.valueOf(action), 2, 301,
                (BooleanSupplier) owned::get));
            assertEquals(0, fixture.loginIndexReads, "Cancellation is checked inside the queued callback");
            assertTrue(fixture.enteredAmounts.isEmpty());
            assertTrue(fixture.backend.events.isEmpty());
        }
    }

    @Test
    void disabledReceiverCannotOpenAListenerEvenWhileTradingActionsAreEnabled() throws Exception {
        MuleRuntime runtime = new MuleRuntime(() -> true, () -> false, () -> false);
        try {
            assertFalse(runtime.ensureServer(unusedLocalPort()));
            assertFalse(runtime.muleServerHealth().listening);
        } finally {
            runtime.stopServer();
        }
    }

    @Test
    void loggedOutReceiverMayListenButAStoppedRoleCannotReopenFromALateTick() throws Exception {
        AtomicBoolean receiving = new AtomicBoolean(true);
        // Listener ownership is independent of logged-in/native action availability.
        MuleRuntime runtime = new MuleRuntime(() -> false, () -> false, receiving::get);
        int port = unusedLocalPort();
        try {
            assertTrue(runtime.ensureServer(port));
            assertTrue(runtime.muleServerHealth().listening);
            receiving.set(false);
            runtime.stopServer();
            assertFalse(runtime.muleServerHealth().listening);
            assertFalse(runtime.ensureServer(port), "A late receiver tick cannot undo role cleanup");
            assertFalse(runtime.muleServerHealth().listening);
        } finally {
            runtime.stopServer();
        }
    }

    @Test
    void interruptedReceiverTickCannotStartAListenerOrClearItsCancellationFlag() throws Exception {
        MuleRuntime runtime = new MuleRuntime(() -> true, () -> false, () -> true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int port = unusedLocalPort();
        Thread worker = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                assertFalse(runtime.ensureServer(port));
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "Test worker receiver cancellation");
        try {
            worker.start();
            worker.join(3000);
            assertFalse(worker.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertFalse(runtime.muleServerHealth().listening);
            assertFalse(Thread.currentThread().isInterrupted(), "Only the isolated worker was cancelled");
        } finally {
            runtime.stopServer();
        }
    }

    private static int unusedLocalPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @Test
    void capitalIncludesCurrentTradeableInventoryAndBankCoinsButNotUnrelatedBankItems() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.untradeable(101);
        fixture.inventory(new Item(995, 5000), new Item(100, 10), new Item(101, 20));
        fixture.bank(new Item(995, 2000), new Item(100, 1_000_000));

        MuleSession.Snapshot snapshot = fixture.capture();
        assertEquals(7800, snapshot.wealthGp);
        assertEquals(5000, snapshot.inventoryCoins);
        assertEquals(2000, snapshot.knownBankCoins);
        assertTrue(snapshot.capitalVerified);
        assertTrue(snapshot.capitalComplete);
    }

    @Test
    void notedInventoryUsesTheUnnotedTradeableItemAndPrice() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.note(101, 100);
        fixture.inventory(new Item(101, 10));

        MuleSession.Snapshot snapshot = fixture.capture();
        assertEquals(800, snapshot.wealthGp);
        assertTrue(snapshot.capitalVerified);
        assertTrue(snapshot.capitalComplete);
    }

    @ParameterizedTest
    @EnumSource(GrandExchangeOfferState.class)
    void currentCapitalNeverAddsHistoricalCollectedFillsOrSpentAgain(GrandExchangeOfferState state) {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        // These two fills and their GP are already represented by current inventory.
        fixture.inventory(new Item(995, 200), new Item(100, 2));
        int total = state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD ? 2 : 10;
        fixture.offers[0] = offer(state, 100, total, 2, 100, 200);

        MuleSession.Snapshot snapshot = fixture.capture();
        long escrow = state == GrandExchangeOfferState.BUYING ? 800
            : state == GrandExchangeOfferState.SELLING ? 640 : 0;
        assertEquals(360 + escrow, snapshot.wealthGp);
        assertEquals(state == GrandExchangeOfferState.BUYING, snapshot.hasActiveBuys);
        assertTrue(snapshot.capitalVerified);
        assertEquals(state == GrandExchangeOfferState.EMPTY, snapshot.capitalComplete);
    }

    @Test
    void unsoldItemsNeverUseAnInflatedAskingPrice() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.offers[0] = offer(GrandExchangeOfferState.SELLING, 100, 10, 0, 1000, 0);
        assertEquals(800, fixture.capture().wealthGp);

        fixture.price(100, 2000);
        assertEquals(10000, fixture.capture().wealthGp, "The verified ask also limits the estimate");
    }

    @Test
    void tradeOfferedCoinsAreExcludedFromRuntimeCapitalAndIncludedBySessionOnce() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.inventory(new Item(995, 4000));
        fixture.containers.put(InventoryID.TRADE.getId(), new Item[]{new Item(995, 1000)});
        fixture.widgets.put(InterfaceID.Trademain.WHOLESCREEN, new Node(InterfaceID.Trademain.WHOLESCREEN));

        MuleSession.Snapshot snapshot = fixture.capture();
        assertEquals(4000, snapshot.wealthGp);
        assertEquals(1000, snapshot.ourOfferedCoins);
        assertEquals(5000, snapshot.workingCapitalGp());
        assertTrue(snapshot.capitalVerified);
    }

    @Test
    void missingOrPreviouslyCachedBankCannotInflateCurrentCapital() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.inventory(new Item(995, 1000));
        fixture.bank(new Item(995, 9000));
        assertEquals(10000, fixture.capture().wealthGp);

        fixture.containers.remove(InventoryID.BANK.getId());
        MuleSession.Snapshot unknown = fixture.capture();
        assertEquals(1000, unknown.wealthGp);
        assertEquals(0, unknown.knownBankCoins);
        assertTrue(unknown.capitalVerified, "A missing bank may safely contribute a lower bound of zero");
        assertFalse(unknown.capitalComplete);

        fixture.account++;
        assertEquals(1000, fixture.capture().wealthGp);
        assertEquals(0, fixture.capture().knownBankCoins);
    }

    @Test
    void freshBankCountsReplaceOldValuesAndLogoutDropsTheirAccountBinding() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.bank(new Item(995, 9000));
        assertEquals(9000, fixture.capture().wealthGp);
        fixture.bank(new Item(995, 2000));
        assertEquals(2000, fixture.capture().wealthGp);
        fixture.gameState = GameState.LOGIN_SCREEN;
        assertFalse(fixture.capture().loggedIn);
        fixture.gameState = GameState.LOGGED_IN;
        fixture.containers.remove(InventoryID.BANK.getId());
        assertEquals(0, fixture.capture().knownBankCoins);
    }

    @Test
    void unknownPricesAndOffersAreExplicitPartialLowerBounds() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.inventory(new Item(995, 500), new Item(100, 10));
        fixture.price(100, 0);
        fixture.offers = null;

        MuleSession.Snapshot snapshot = fixture.capture();
        assertEquals(500, snapshot.wealthGp);
        assertTrue(snapshot.capitalVerified);
        assertFalse(snapshot.capitalComplete);

        fixture.failPrices = true;
        assertEquals(500, fixture.capture().wealthGp);
        assertTrue(fixture.capture().capitalVerified);
        assertFalse(fixture.capture().capitalComplete);
    }

    @Test
    void missingOrInvalidInventoryCannotLookLikeATransferReceipt() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.containers.remove(InventoryID.INVENTORY.getId());
        assertNull(fixture.capture());
        fixture.inventory(new Item(995, -1));
        assertNull(fixture.capture());
        fixture.inventory(new Item(995, Integer.MAX_VALUE), new Item(995, 1));
        assertNull(fixture.capture());
    }

    @Test
    void invalidOfferCountersAndNegativePricesCannotAuthorizeATransfer() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 10, 11, 100, 0);
        assertFalse(fixture.capture().capitalVerified);
        fixture.offers[0] = offer(GrandExchangeOfferState.CANCELLED_BUY, 100, 10, 2, 100, -1);
        assertFalse(fixture.capture().capitalVerified);
        fixture.offers[0] = offer(GrandExchangeOfferState.SOLD, 100, 10, 2, 100, 200);
        assertFalse(fixture.capture().capitalVerified);
        fixture.offers[0] = offer(GrandExchangeOfferState.EMPTY, 0, 0, 0, 0, 0);
        fixture.price(100, -1);
        fixture.inventory(new Item(100, 1));
        assertFalse(fixture.capture().capitalVerified);
    }

    @Test
    void capitalArithmeticUsesLongAndRejectsTotalOverflow() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, Integer.MAX_VALUE);
        fixture.inventory(new Item(100, Integer.MAX_VALUE));
        MuleSession.Snapshot singleStack = fixture.capture();
        assertEquals((long) Integer.MAX_VALUE * Integer.MAX_VALUE, singleStack.wealthGp);
        assertTrue(singleStack.capitalVerified);

        fixture.inventory(new Item(100, Integer.MAX_VALUE), new Item(100, Integer.MAX_VALUE),
            new Item(100, Integer.MAX_VALUE));
        MuleSession.Snapshot overflowing = fixture.capture();
        assertFalse(overflowing.capitalVerified);
        assertTrue(overflowing.wealthGp >= 0, "Overflow must never wrap into an apparently spendable value");
    }

    @Test
    void nativeWidePricesRemainLongAndOverflowingProductsFailClosed() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 2, 0, 5_000_000_000L, 0);
        assertEquals(10_000_000_000L, fixture.capture().wealthGp);
        assertTrue(fixture.capture().capitalVerified);

        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 2, 0, Long.MAX_VALUE, 0);
        assertFalse(fixture.capture().capitalVerified);

        fixture.offers[0] = offer(GrandExchangeOfferState.EMPTY, 0, 0, 0, 0, 0);
        fixture.price(100, Long.MAX_VALUE);
        fixture.inventory(new Item(100, 2));
        assertFalse(fixture.capture().capitalVerified);
    }

    @Test
    void invalidBankCoinStacksNeverBecomeSpendableKnownCash() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.inventory(new Item(995, 1000));
        fixture.bank(new Item(995, Integer.MAX_VALUE), new Item(995, 1));
        MuleSession.Snapshot overflowing = fixture.capture();
        assertEquals(0, overflowing.knownBankCoins);
        assertFalse(overflowing.capitalVerified);

        fixture.bank(new Item(995, -1));
        assertEquals(0, fixture.capture().knownBankCoins);
        assertFalse(fixture.capture().capitalVerified);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void eachNativeCollectionSlotAddsOnlyItsCurrentAssetsAlongsideBuyEscrow(int slot) {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.inventory(new Item(995, 200));
        fixture.offers[slot] = offer(GrandExchangeOfferState.BUYING, 100, 10, 2, 100, 160);
        fixture.collection(slot, new Item(100, 2), new Item(995, 40));

        MuleSession.Snapshot before = fixture.capture();
        assertEquals(1200, before.wealthGp, "Inventory + remaining escrow + actual items/refund only");
        assertTrue(before.capitalVerified);
        assertTrue(before.capitalComplete);

        // Native collect containers shrink while the offer's filled/spent counters remain cumulative.
        fixture.inventory(new Item(995, 240), new Item(100, 2));
        fixture.collection(slot);
        assertEquals(1200, fixture.capture().wealthGp);
        assertTrue(fixture.capture().capitalComplete);

        fixture.offers[slot] = offer(GrandExchangeOfferState.BUYING, 100, 10, 3, 100, 240);
        fixture.collection(slot, new Item(100, 1), new Item(995, 20));
        assertEquals(1200, fixture.capture().wealthGp);
    }

    @Test
    void currentSellProceedsAreCountedOnceAlongsideUnsoldStock() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.inventory(new Item(995, 200));
        fixture.offers[0] = offer(GrandExchangeOfferState.SELLING, 100, 10, 2, 100, 180);
        fixture.collection(0, new Item(995, 180));
        assertEquals(1020, fixture.capture().wealthGp);
        assertTrue(fixture.capture().capitalComplete);

        fixture.inventory(new Item(995, 380));
        fixture.collection(0);
        assertEquals(1020, fixture.capture().wealthGp, "Collected historical proceeds are already inventory GP");
        assertTrue(fixture.capture().capitalVerified);
    }

    @Test
    void cancelledBuyIncludesItsActualRefundAndUncollectedItemsWithoutInventingEscrow() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.inventory(new Item(995, 200));
        fixture.offers[0] = offer(GrandExchangeOfferState.CANCELLED_BUY, 100, 10, 2, 100, 160);
        fixture.collection(0, new Item(100, 2), new Item(995, 840));
        MuleSession.Snapshot before = fixture.capture();
        assertEquals(1200, before.wealthGp);
        assertFalse(before.hasActiveBuys);
        assertTrue(before.capitalComplete);

        fixture.inventory(new Item(995, 200), new Item(100, 2));
        fixture.collection(0, new Item(995, 840));
        assertEquals(1200, fixture.capture().wealthGp);
        fixture.inventory(new Item(995, 1040), new Item(100, 2));
        fixture.collection(0);
        assertEquals(1200, fixture.capture().wealthGp);
    }

    @Test
    void cancelledSellValuesOnlyActualReturnedStockAndProceeds() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.inventory(new Item(995, 200));
        fixture.offers[0] = offer(GrandExchangeOfferState.CANCELLED_SELL, 100, 10, 2, 100, 180);
        fixture.collection(0, new Item(100, 8), new Item(995, 180));
        assertEquals(1020, fixture.capture().wealthGp);
        assertTrue(fixture.capture().capitalComplete);

        fixture.inventory(new Item(995, 380), new Item(100, 8));
        fixture.collection(0);
        assertEquals(1020, fixture.capture().wealthGp);
    }

    @Test
    void completedPurchaseUsesConservativeKnownCostForOnlyItsUncollectedItems() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 500);
        fixture.offers[0] = offer(GrandExchangeOfferState.BOUGHT, 100, 2, 2, 100, 120);
        fixture.collection(0, new Item(100, 2), new Item(995, 80));
        assertEquals(200, fixture.capture().wealthGp, "Two items at known average cost 60 plus exact refund 80");
        assertTrue(fixture.capture().capitalVerified);
        assertTrue(fixture.capture().capitalComplete);
    }

    @Test
    void unrelatedCollectionContentsCannotContributeEitherItemsOrCoinsToANewOffer() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.inventory(new Item(995, 200));
        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 10, 2, 100, 160);
        fixture.collection(0, new Item(995, 40), new Item(101, 2));
        MuleSession.Snapshot snapshot = fixture.capture();
        assertEquals(1000, snapshot.wealthGp, "A mismatched collection is omitted as a whole");
        assertTrue(snapshot.capitalVerified);
        assertFalse(snapshot.capitalComplete);
    }

    @Test
    void incoherentAndNegativeCollectionAmountsFailClosed() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.price(100, 80);
        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 10, 2, 100, 160);
        fixture.collection(0, new Item(100, 3));
        assertFalse(fixture.capture().capitalVerified);
        fixture.collection(0, new Item(995, 41));
        assertFalse(fixture.capture().capitalVerified, "The current refund cannot exceed its offer budget");
        fixture.collection(0, new Item(100, -1));
        assertFalse(fixture.capture().capitalVerified);

        fixture.offers[0] = offer(GrandExchangeOfferState.SELLING, 100, 10, 2, 100, 180);
        fixture.collection(0, new Item(995, 181));
        assertFalse(fixture.capture().capitalVerified);
        fixture.collection(0, new Item(100, 1));
        assertFalse(fixture.capture().capitalVerified, "Active sell stock is already counted as escrow");
    }

    @Test
    void missingCollectionAfterFillsIsPartialButAKnownEmptyCollectionIsComplete() {
        CapitalFixture fixture = new CapitalFixture();
        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 10, 0, 100, 0);
        assertTrue(fixture.capture().capitalComplete, "Unfilled active buys have no unknown payout");
        fixture.offers[0] = offer(GrandExchangeOfferState.BUYING, 100, 10, 2, 100, 160);
        assertFalse(fixture.capture().capitalComplete);
        assertTrue(fixture.capture().capitalVerified);
        fixture.collection(0);
        assertTrue(fixture.capture().capitalComplete);
        assertEquals(800, fixture.capture().wealthGp);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void decoratedCoinActionsKeepTheirNativeOperationAndInventorySlot(boolean varying, boolean all) throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.varying = varying;
            Node coins = fixture.coins();
            int amount = all ? coins.quantity : 1000;

            assertTrue(fixture.offer(amount, coins.quantity));
            assertEquals(List.of(InterfaceID.Tradeside.SIDE_LAYER), fixture.clicked);
            assertEquals(List.of("move", "press", "release", "click"), fixture.backend.events);
            assertEquals(all ? "Offer-All" : "Offer-X", fixture.backend.menu.getOption());
            assertEquals(all ? 4 : 5, fixture.backend.menu.getIdentifier());
            assertEquals(7, fixture.backend.menu.getParam0());
            assertEquals(InterfaceID.Tradeside.SIDE_LAYER, fixture.backend.menu.getParam1());
            assertEquals(995, fixture.backend.menu.getItemId());
            assertEquals(all ? 0 : amount, field(MuleRuntime.class, "pendingOfferAmount").getInt(fixture.runtime));
            assertTrue(fixture.enteredAmounts.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"<col=ff0000>Examine</col>", "<col=ff0000>Offer-X all</col>"})
    void decorationCannotTurnAnUnrelatedActionIntoOfferX(String action) throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.coins().actions = new String[]{action};
            assertFalse(fixture.runtime.offerCoins(1000, 5000));
            assertTrue(fixture.clicked.isEmpty());
            assertTrue(fixture.backend.events.isEmpty());
            assertEquals(0, field(MuleRuntime.class, "pendingOfferAmount").getInt(fixture.runtime));
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void changedCoinItemOrActionAfterMovementCannotOfferFromAStaleControl(boolean varying, boolean changedItem)
            throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.varying = varying;
            Node coins = fixture.coins();
            fixture.backend.afterMove = () -> {
                if (changedItem) coins.item = 100;
                else coins.actions[4] = "<col=ff0000>Examine</col>";
            };
            assertFalse(fixture.runtime.offerCoins(1000, 5000));
            assertTrue(fixture.clicked.isEmpty());
            assertEquals(List.of("move"), fixture.backend.events);
            assertEquals(0, field(MuleRuntime.class, "pendingOfferAmount").getInt(fixture.runtime));
            assertTrue(fixture.enteredAmounts.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 30})
    void offerXEntersOnlyItsPendingAmountThroughTheVisibleTradePrompt(int inputMode) throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.coins();
            assertTrue(fixture.offer(1000, 5000));
            fixture.inputMode = inputMode;
            fixture.next();

            assertTrue(fixture.runtime.offerCoins(1000, 5000));
            assertEquals(List.of(1000), fixture.enteredAmounts);
            assertEquals("1000", fixture.inputText);
            assertEquals("1000*", fixture.prompt.text);
            assertEquals(0, field(MuleRuntime.class, "pendingOfferAmount").getInt(fixture.runtime));
            assertEquals(1, fixture.clicked.size(), "Entering the amount must not dispatch another offer operation");
        }
    }

    @Test
    void unsupportedPromptDoesNotEnterCoinsIntoAnotherInput() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            Node coins = fixture.coins();
            assertTrue(fixture.offer(1000, 5000));
            fixture.inputMode = 1;
            coins.actions = null;
            fixture.next();

            assertFalse(fixture.runtime.offerCoins(1000, 5000));
            assertTrue(fixture.enteredAmounts.isEmpty());
            assertEquals("", fixture.inputText);
            assertEquals(1000, field(MuleRuntime.class, "pendingOfferAmount").getInt(fixture.runtime));
            assertEquals(1, fixture.clicked.size());
        }
    }

    private enum PromptChange { INPUT_MODE, HIDDEN_PROMPT, HIDDEN_TRADE, READ_BACK, PENDING_AMOUNT, PAUSED, HUMAN }

    @ParameterizedTest
    @EnumSource(PromptChange.class)
    void changedPromptOrOwnershipAfterWritingCannotDispatchEnter(PromptChange change) throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.coins();
            assertTrue(fixture.offer(1000, 5000));
            fixture.afterInputWrite = () -> {
                switch (change) {
                    case INPUT_MODE: fixture.inputMode = 0; break;
                    case HIDDEN_PROMPT: fixture.prompt.hidden = true; break;
                    case HIDDEN_TRADE: fixture.widgets.get(InterfaceID.Trademain.WHOLESCREEN).hidden = true; break;
                    case READ_BACK: fixture.inputText = "2000"; break;
                    case PENDING_AMOUNT:
                        try { field(MuleRuntime.class, "pendingOfferAmount").setInt(fixture.runtime, 2000); }
                        catch (Exception error) { throw new AssertionError(error); }
                        break;
                    case PAUSED: Microbot.pauseAllScripts.set(true); break;
                    case HUMAN: InputArbiter.onRealKeyPressed(KeyEvent.VK_SHIFT); break;
                    default: throw new AssertionError(change);
                }
            };
            fixture.next();

            assertFalse(fixture.runtime.offerCoins(1000, 5000));
            assertTrue(fixture.enteredAmounts.isEmpty(), "A changed prompt must never receive Enter");
            assertEquals(1, fixture.clicked.size());
            assertTrue(field(MuleRuntime.class, "pendingOfferAmount").getInt(fixture.runtime) > 0,
                "An uncertain write must not be marked as submitted");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returningDeclinesEitherTradeScreenBeforeCheckingBankOrExchange(boolean confirm) throws Exception {
        try (Fixture fixture = new Fixture(confirm)) {
            fixture.runtime.closeInterfaces();

            assertEquals(List.of(fixture.decline.id), fixture.clicked);
            assertEquals(List.of("move", "press", "release", "click"), fixture.backend.events);
            assertEquals("Decline", fixture.backend.menu.getOption());
            assertEquals(1, fixture.backend.menu.getIdentifier());
            assertEquals(-1, fixture.backend.menu.getParam0());
            fixture.assertNoBankOrExchangeReads();
        }
    }

    @Test
    void closeConsumesTheActionDelayOnceAndRateLimitsFurtherDeclines() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.runtime.closeInterfaces();
            assertEquals(1, fixture.clicked.size(), "A second action gate must not suppress the first decline");
            fixture.backend.menu = null; // The client has processed the first operation.
            fixture.runtime.closeInterfaces();
            assertEquals(1, fixture.clicked.size(), "Repeated ticks must still respect the action delay");

            field(MuleRuntime.class, "nextActionMillis").setLong(fixture.runtime, 0);
            fixture.runtime.closeInterfaces();
            assertEquals(2, fixture.clicked.size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void visibleTradeWithNoDeclineOperationDoesNotClickUnderlyingInterfaces(boolean hidden) throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            if (hidden) fixture.decline.hidden = true;
            else fixture.decline.actions = new String[]{"Accept"};
            fixture.runtime.closeInterfaces();

            assertTrue(fixture.clicked.isEmpty());
            assertTrue(fixture.backend.events.isEmpty());
            fixture.assertNoBankOrExchangeReads();
        }
    }

    @Test
    void changedDeclineControlAfterMovementCannotDispatchAStaleOperation() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.backend.afterMove = () -> fixture.decline.actions = new String[]{"Accept"};
            fixture.runtime.closeInterfaces();

            assertTrue(fixture.clicked.isEmpty());
            assertEquals(List.of("move"), fixture.backend.events);
            fixture.assertNoBankOrExchangeReads();
        }
    }

    @Test
    void pauseOrStoppedOwnerCannotDeclineAUserTrade() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            Microbot.pauseAllScripts.set(true);
            fixture.runtime.closeInterfaces();
            Microbot.pauseAllScripts.set(false);
            fixture.running = false;
            fixture.runtime.closeInterfaces();

            assertTrue(fixture.clicked.isEmpty());
            assertTrue(fixture.backend.events.isEmpty());
            assertTrue(fixture.widgetReads.isEmpty());
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static GrandExchangeOffer offer(GrandExchangeOfferState state, int item, int total,
                                            int filled, long price, long spent) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getState": return state;
                    case "getItemId": return item;
                    case "getTotalQuantity": return total;
                    case "getQuantitySold": return filled;
                    case "getPrice":
                        if (method.getReturnType() == long.class) return price;
                        return Math.toIntExact(price);
                    case "getSpent":
                        if (method.getReturnType() == long.class) return spent;
                        return Math.toIntExact(spent);
                    default: throw new AssertionError("Unexpected offer read: " + method.getName());
                }
            });
    }

    /** Calls the actual capture adapter with current native containers, without any global client or inputs. */
    private static final class CapitalFixture {
        private final Map<Integer, Item[]> containers = new HashMap<>();
        private final Map<Integer, Long> prices = new HashMap<>();
        private final Map<Integer, ItemComposition> definitions = new HashMap<>();
        private final Map<Integer, Node> widgets = new HashMap<>();
        private GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        private GameState gameState = GameState.LOGGED_IN;
        private long account = 1;
        private boolean failPrices;
        private final MuleRuntime runtime = new MuleRuntime(() -> true, () -> false, item -> {
            if (failPrices) throw new IllegalStateException("Price unavailable");
            return prices.getOrDefault(item, 0L);
        });
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getName": return "Test player";
                    case "getWorldLocation": return new WorldPoint(3164, 3487, 0);
                    default: throw new AssertionError("Unexpected player read: " + method.getName());
                }
            });
        private final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
            new Class<?>[]{Client.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getGameState": return gameState;
                    case "getLocalPlayer": return player;
                    case "getAccountHash": return account;
                    case "getWorld": return 301;
                    case "getGrandExchangeOffers": return offers;
                    case "getItemContainer":
                        int containerId = args[0] instanceof InventoryID ? ((InventoryID) args[0]).getId() : (Integer) args[0];
                        Item[] items = containers.get(containerId);
                        return items == null ? null : Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
                            new Class<?>[]{ItemContainer.class}, (container, query, values) -> {
                                if ("getItems".equals(query.getName())) return items;
                                throw new AssertionError("Unexpected container read: " + query.getName());
                            });
                    case "getItemDefinition": return definitions.get((Integer) args[0]);
                    case "getWidget":
                        int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                        return widgets.containsKey(id) ? widgets.get(id).widget : null;
                    default: throw new AssertionError("Unexpected capital client read: " + method.getName());
                }
            });

        private CapitalFixture() {
            inventory();
            bank();
            for (int i = 0; i < offers.length; i++) offers[i] = offer(GrandExchangeOfferState.EMPTY, 0, 0, 0, 0, 0);
        }

        private void inventory(Item... items) { containers.put(InventoryID.INVENTORY.getId(), items); }
        private void bank(Item... items) { containers.put(InventoryID.BANK.getId(), items); }
        private void collection(int slot, Item... items) {
            containers.put(slot < 6 ? 518 + slot : 539 + slot - 6, items);
        }
        private void price(int id, long price) { prices.put(id, price); definition(id, -1, -1, true); }
        private void untradeable(int id) { definition(id, -1, -1, false); }
        private void note(int id, int linked) { definition(id, 799, linked, false); }

        private void definition(int id, int note, int linked, boolean tradeable) {
            definitions.put(id, (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                new Class<?>[]{ItemComposition.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getNote": return note;
                        case "getLinkedNoteId": return linked;
                        case "isTradeable": return tradeable;
                        default: throw new AssertionError("Unexpected item definition read: " + method.getName());
                    }
                }));
        }

        private MuleSession.Snapshot capture() { return runtime.capture(client); }
    }

    /** Executes the real adapter and natural validated press with isolated client/widget proxies. */
    private static final class Fixture implements AutoCloseable {
        private final Field clientField = field(Microbot.class, "client");
        private final Field threadField = field(Microbot.class, "clientThread");
        private final Field mouseField = field(Microbot.class, "mouse");
        private final Object previousClient = clientField.get(null), previousThread = threadField.get(null);
        private final Object previousMouse = mouseField.get(null);
        private final MenuEntry previousMenu = Microbot.targetMenu;
        private final boolean previousPause = Microbot.pauseAllScripts.get();
        private final Map<Integer, Node> widgets = new HashMap<>();
        private final List<Integer> widgetReads = new ArrayList<>(), clicked = new ArrayList<>();
        private final List<Integer> enteredAmounts = new ArrayList<>();
        private final Canvas canvas = new Canvas();
        private final TestBackend backend = new TestBackend();
        private final Node decline;
        private final Node prompt = new Node(InterfaceID.Chatbox.MES_TEXT2);
        private boolean running = true, varying;
        private GameState gameState = GameState.LOGGED_IN;
        private int loginIndexReads;
        private Runnable beforeClientRead = () -> { };
        private int inputMode;
        private String inputText = "";
        private Runnable afterInputWrite = () -> { };
        private final MuleRuntime runtime = new MuleRuntime(() -> running, () -> varying);
        private final ClientThread inline = new ClientThread() {
            @Override public <T> Optional<T> runOnClientThreadOptional(Callable<T> call) {
                beforeClientRead.run();
                try { return Optional.ofNullable(call.call()); }
                catch (Exception error) { throw new AssertionError(error); }
            }
        };

        private Fixture(boolean confirm) throws Exception {
            int tradeRoot = confirm ? InterfaceID.Tradeconfirm.ROOT_RECT0 : InterfaceID.Trademain.WHOLESCREEN;
            widgets.put(tradeRoot, new Node(tradeRoot));
            int declineId = confirm ? InterfaceID.Tradeconfirm.TRADE2DECLINE : InterfaceID.Trademain.DECLINE;
            decline = new Node(declineId);
            decline.actions = new String[]{"Decline"};
            widgets.put(declineId, decline);
            prompt.hidden = true;
            widgets.put(prompt.id, prompt);
            canvas.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                        enteredAmounts.add(Integer.parseInt(inputText));
                        inputMode = 0;
                        prompt.hidden = true;
                    }
                }
            });
            // These must never be consulted or closed while a trade is present.
            widgets.put((InterfaceID.GE_OFFERS << 16) | 1, new Node((InterfaceID.GE_OFFERS << 16) | 1));
            widgets.put((12 << 16) | 1, new Node((12 << 16) | 1));
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> { throw new AssertionError("Unexpected player read"); });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getGameState": return gameState;
                        case "getLoginIndex": loginIndexReads++; return 2;
                        case "getLocalPlayer": return player;
                        case "isMenuOpen": return false;
                        case "isClientThread": return false;
                        case "getCanvas": return canvas;
                        case "getCanvasWidth": return 765;
                        case "getCanvasHeight": return 503;
                        case "getVarcIntValue": assertEquals(5, args[0]); return inputMode;
                        case "getVarcStrValue": assertEquals(359, args[0]); return inputText;
                        case "setVarcStrValue":
                            assertEquals(359, args[0]);
                            inputText = (String) args[1];
                            afterInputWrite.run();
                            return null;
                        case "getWidget":
                            int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                            widgetReads.add(id);
                            Node node = widgets.get(id);
                            return node == null ? null : node.widget;
                        default: throw new AssertionError("Unexpected client read: " + method.getName());
                    }
                });
            clientField.set(null, client);
            threadField.set(null, inline);
            mouseField.set(null, new IsolatedMouse());
            field(MuleRuntime.class, "mouseMotion").set(runtime,
                new GeflipperMouseMotion(backend, (minimum, maximum) -> minimum, new Random(1),
                    (nature, random, x, y) -> nature.getSystemCalls().setMousePosition(x, y)));
            Microbot.targetMenu = null;
            Microbot.pauseAllScripts.set(false);
            InputArbiter.resetForTest();
        }

        private Node coins() {
            Node side = new Node(InterfaceID.Tradeside.SIDE_LAYER);
            Node coins = new Node(side.id);
            coins.index = 7;
            coins.item = 995;
            coins.quantity = 5000;
            coins.actions = new String[]{"Offer-1", "Offer-5", null,
                "<col=ff9040>Offer-All</col>", " <col=00ff00>Offer-X</col> "};
            side.children = new Node[]{coins};
            widgets.put(side.id, side);
            return coins;
        }

        private boolean offer(int amount, int inventoryCoins) throws Exception {
            // An SDK candidate on an exclusive widget edge is safely rejected; emulate the next worker tick.
            for (int attempt = 0; attempt < 8; attempt++) {
                if (runtime.offerCoins(amount, inventoryCoins)) return true;
                assertTrue(clicked.isEmpty());
                assertTrue(backend.events.isEmpty());
                next();
            }
            return false;
        }

        private void next() throws Exception {
            field(MuleRuntime.class, "nextActionMillis").setLong(runtime, 0);
            backend.menu = null;
        }

        private void assertNoBankOrExchangeReads() {
            assertFalse(widgetReads.contains((InterfaceID.GE_OFFERS << 16) | 1));
            assertFalse(widgetReads.contains((12 << 16) | 1));
        }

        private final class TestBackend implements GeflipperMouseMotion.Backend {
            private final MouseMotionNature nature = new MouseMotionNature();
            private Point position = new Point(1, 1);
            private MenuEntry menu;
            private Runnable afterMove = () -> { };
            private final List<String> events = new ArrayList<>();

            private TestBackend() {
                nature.setTimeToStepsDivider(8);
                nature.setMinSteps(10);
                nature.setEffectFadeSteps(15);
                nature.setReactionTimeBaseMs(0);
                nature.setReactionTimeVariationMs(0);
                nature.setNoiseProvider(new DefaultNoiseProvider(2));
                nature.setDeviationProvider(new SinusoidalDeviationProvider(10));
                DefaultOvershootManager overshoot = new DefaultOvershootManager(new Random(2));
                overshoot.setOvershoots(0);
                nature.setOvershootManager(overshoot);
                nature.setSpeedManager(distance -> new Pair<>(new Flow(new double[]{1, 1}), 250L));
            }
            public boolean worker() { return true; }
            public boolean human() { return false; }
            public GeflipperMouseMotion.Snapshot snapshot() { return new GeflipperMouseMotion.Snapshot(765, 503, nature); }
            public Point position() { return position; }
            public boolean gesture(Consumer<GeflipperMouseMotion.Sink> action) {
                action.accept(new GeflipperMouseMotion.Sink() {
                    public void checkpoint() { }
                    public void move(int x, int y) { position = new Point(x, y); events.add("move"); afterMove.run(); }
                    public void press(int x, int y) { events.add("press"); }
                    public void release(int x, int y) { events.add("release"); }
                    public void click(int x, int y) { events.add("click"); }
                });
                return true;
            }
            public void delegate(GeflipperMouseMotion.Action action, Point point, NewMenuEntry entry) {
                throw new AssertionError("Trade closure must use the owned natural gesture");
            }
            public MenuEntry pendingMenu() { return menu; }
            public void pendingMenu(MenuEntry entry) { menu = entry; }
            public void clicked(Point point) {
                clicked.add(menu.getParam1());
                if ("Offer-X".equals(menu.getOption())) {
                    inputMode = 7;
                    prompt.hidden = false;
                }
            }
            public long millis() { return 0; }
            public void sleep(long millis) { }
        }

        public void close() throws Exception {
            InputArbiter.resetForTest();
            Microbot.pauseAllScripts.set(previousPause);
            Microbot.targetMenu = previousMenu;
            clientField.set(null, previousClient);
            threadField.set(null, previousThread);
            mouseField.set(null, previousMouse);
            ((ScheduledExecutorService) field(ClientThread.class, "scheduledExecutorService").get(inline)).shutdownNow();
        }
    }

    private static final class Node {
        private final int id;
        private boolean hidden;
        private int index = -1, item = -1, quantity;
        private String text = "";
        private Node[] children;
        private String[] actions;
        private final Widget widget;
        private Node(int id) {
            this.id = id;
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId": return this.id;
                        case "getIndex": return index;
                        case "getItemId": return item;
                        case "getItemQuantity": return quantity;
                        case "getText": return text;
                        case "setText": text = (String) args[0]; return null;
                        case "getChildren": case "getDynamicChildren":
                            if (children == null) return null;
                            Widget[] values = new Widget[children.length];
                            for (int i = 0; i < children.length; i++) values[i] = children[i] == null ? null : children[i].widget;
                            return values;
                        case "getActions": return actions;
                        case "isHidden": return hidden;
                        case "getBounds": return new Rectangle(100, 100, 80, 20);
                        default: throw new AssertionError("Unexpected widget read: " + method.getName());
                    }
                });
        }
    }

    /** No SDK fallback may send a real input event from these tests. */
    private static final class IsolatedMouse extends Mouse {
        public java.awt.Point getMousePosition() { return new java.awt.Point(1, 1); }
        public void setLastClick(Point point) { }
        public void setLastMove(Point point) { }
        public Mouse click(int x, int y) { throw new AssertionError(); }
        public Mouse click(double x, double y) { throw new AssertionError(); }
        public Mouse click(Rectangle bounds) { throw new AssertionError(); }
        public Mouse click(int x, int y, boolean right) { throw new AssertionError(); }
        public Mouse click(Point point) { throw new AssertionError(); }
        public Mouse click(Point point, boolean right) { throw new AssertionError(); }
        public Mouse click(Point point, NewMenuEntry entry) { throw new AssertionError(); }
        public Mouse click() { throw new AssertionError(); }
        public Mouse move(Point point) { throw new AssertionError(); }
        public Mouse move(Rectangle bounds) { throw new AssertionError(); }
        public Mouse move(int x, int y) { throw new AssertionError(); }
        public Mouse move(double x, double y) { throw new AssertionError(); }
        public Mouse move(Polygon polygon) { throw new AssertionError(); }
        public Mouse scrollDown(Point point) { throw new AssertionError(); }
        public Mouse scrollUp(Point point) { throw new AssertionError(); }
        public Mouse drag(Point from, Point to) { throw new AssertionError(); }
    }
}
