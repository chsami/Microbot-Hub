package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ChatMessageType;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class BankSellerScript extends Script {

    /** Coins (995) and platinum tokens (13204) are never sold. */
    private static final Set<Integer> IGNORED_ITEM_IDS = new HashSet<>(Arrays.asList(995, 13204));

    /** GE overview slot widgets: interface 465, children 7-14 (mirrors GrandExchangeWidget.getSlot). */
    private static final int GE_WIDGET_GROUP = 465;
    private static final int GE_FIRST_SLOT_CHILD = 7;

    private static final int SETUP_WAIT_TIMEOUT_MS = 5_000;
    private static final int BANK_OPEN_TIMEOUT_MS = 10_000;
    private static final int BANK_LOAD_GRACE_MS = 3_000;
    private static final int EXCHANGE_OPEN_TIMEOUT_MS = 10_000;
    private static final int CONFIRM_WAIT_TIMEOUT_MS = 10_000;
    private static final int MAX_OFFER_ATTEMPTS = 2;

    /** Give the GE overview at least one game tick to rebuild after collecting a slot. */
    private static final int COLLECTION_OVERVIEW_SETTLE_MIN_MS = 700;
    private static final int COLLECTION_OVERVIEW_SETTLE_MAX_MS = 1_000;

    /** An unsold leftover offer gets this long to fill before it is re-listed at 1gp. */
    private static final int LEFTOVER_OFFER_TIMEOUT_MS = 90_000;

    /** After the 1gp re-list the plugin stops anyway when nothing sold within this window. */
    private static final int LEFTOVER_GIVE_UP_MS = 60_000;

    /** A failed liquidation pass gets this many safe retries before an explicit stop. */
    private static final int MAX_LIQUIDATION_ATTEMPTS = 3;

    /**
     * An item whose offer flow keeps failing on transient UI errors (the setup
     * never opens, a widget times out, ...) is retried this many times before
     * it is parked for the rest of the session so the run can still finish.
     */
    private static final int MAX_TRANSIENT_FAILURES = 3;

    /** How a single offer placement ended. */
    private enum OfferResult {
        /** Offer confirmed, the items left the inventory. */
        PLACED,
        /** The setup showed the red trade-restriction notice - a definitive refusal. */
        RESTRICTED,
        /** Transient UI failure (setup never opened, a timeout, an exception) - safe to retry. */
        FAILED
    }

    /**
     * Items the Grand Exchange refused to sell this session, e.g. items on the
     * F2P new-account trade restriction list. They are put back in the bank
     * and are not withdrawn again. Only the explicit trade-restriction notice
     * lands an item here - transient UI failures never do.
     */
    private final Set<Integer> unsellableItemIds = new HashSet<>();

    /**
     * Items whose offer flow failed on transient UI errors too many times in a
     * row. Parked for the session (left in the bank) but NOT counted as
     * GE refusals - unlike {@link #unsellableItemIds} they may sell fine on a
     * later run.
     */
    private final Set<Integer> skippedItemIds = new HashSet<>();

    /** Consecutive transient offer-flow failures per item, reset on any success. */
    private final Map<Integer, Integer> transientFailureCounts = new HashMap<>();

    /**
     * Unnoted ids of every item this run successfully placed an offer for.
     * Drives the endgame liquidation: only slots holding one of these items
     * are aborted/collected/re-listed, so pre-existing offers (or offers from
     * other plugins) are never touched.
     */
    private final Set<Integer> ownedOfferItemIds = new HashSet<>();

    /**
     * GE slots that were already occupied when the plugin started. Their
     * offers are foreign to this run and are never aborted or collected, even
     * when they happen to be for an item this run also listed.
     */
    private final Set<Integer> foreignSlotOrdinals = new HashSet<>();

    private BankSellerPlugin plugin;

    /** True after a logged-in client snapshot established the foreign-slot boundary. */
    private boolean occupiedSlotsSnapshotted;

    /** Set when the current item's setup shows the red trade-restriction notice. */
    private String tradeRestrictionText;

    /**
     * Set once a bank scan finds nothing sellable and the inventory is empty.
     * The main loop then stops opening the bank/GE every cycle and only
     * watches the remaining offers (no mouse movement while waiting).
     */
    private boolean bankDrained;

    /** When set (leftover-offer liquidation), every offer is placed at this price. */
    private Integer forcedSellPrice;

    /** True while the liquidation pass re-lists items - restricts the sell pass to owned items. */
    private boolean liquidatingOwned;

    /** True while every usable GE slot is occupied and none may be collected safely. */
    private boolean waitingForOfferSlot;

    /** Last time the drained watch saw an offer sell or finish. */
    private long lastOfferProgressAt;

    /** True once leftover offers have been aborted and re-listed at 1gp. */
    private boolean leftoverLiquidated;

    /** Consecutive liquidation passes that aborted nothing. */
    private int liquidationAttempts;

    /** Session tallies driving the final chatbox verdict when nothing is left to sell. */
    private int sessionOffersPlaced;
    private int sessionStacksRefused;

    public boolean run(BankSellerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        this.plugin = plugin;
        unsellableItemIds.clear();
        skippedItemIds.clear();
        transientFailureCounts.clear();
        ownedOfferItemIds.clear();
        foreignSlotOrdinals.clear();
        occupiedSlotsSnapshotted = false;
        tradeRestrictionText = null;
        bankDrained = false;
        forcedSellPrice = null;
        liquidatingOwned = false;
        waitingForOfferSlot = false;
        lastOfferProgressAt = System.currentTimeMillis();
        leftoverLiquidated = false;
        liquidationAttempts = 0;
        sessionOffersPlaced = 0;
        sessionStacksRefused = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!occupiedSlotsSnapshotted) {
                    if (!snapshotOccupiedSlots()) {
                        return;
                    }
                    occupiedSlotsSnapshotted = true;
                }

                if (bankDrained) {
                    // Bank and inventory are both drained of sellables - just watch
                    // the remaining offers. No bank/GE opening, no mouse movement.
                    if (hasOwnedSoldOffer()) {
                        boolean allOwnedSlotsEmpty = collectPendingOffers();
                        if (!hasOwnedSoldOffer()) {
                            lastOfferProgressAt = System.currentTimeMillis();
                        }
                        if (allOwnedSlotsEmpty) {
                            announce(finalVerdictMessage());
                            Microbot.stopPlugin(plugin);
                            return;
                        }
                    }
                    if (!hasOwnedActiveOffers()) {
                        announce(finalVerdictMessage());
                        Microbot.stopPlugin(plugin);
                        return;
                    }
                    long watchIdleMs = System.currentTimeMillis() - lastOfferProgressAt;
                    if (!leftoverLiquidated && watchIdleMs > LEFTOVER_OFFER_TIMEOUT_MS) {
                        // An offer that will not fill at the instant-sell price gets
                        // one final attempt at 1gp so the run can actually finish
                        boolean done = liquidateLeftoverOffers();
                        if (done) {
                            liquidationAttempts = 0;
                            leftoverLiquidated = true;
                            lastOfferProgressAt = System.currentTimeMillis();
                        } else {
                            liquidationAttempts++;
                            if (liquidationAttempts >= MAX_LIQUIDATION_ATTEMPTS) {
                                announce("ERROR: unable to safely liquidate every Bank Seller offer; "
                                        + "leaving the remaining offers/items untouched. Stopping.");
                                Microbot.stopPlugin(plugin);
                                return;
                            }
                            // Retry in ~15s instead of waiting the full timeout again.
                            lastOfferProgressAt = System.currentTimeMillis()
                                    - LEFTOVER_OFFER_TIMEOUT_MS + 15_000;
                        }
                    } else if (leftoverLiquidated && watchIdleMs > LEFTOVER_GIVE_UP_MS) {
                        if (!hasOwnedActiveOffers()) {
                            // Only foreign offers are left open - they are not ours to collect
                            announce("Only pre-existing Grand Exchange offers remain - leaving them untouched. Stopping");
                        } else {
                            announce("1gp leftover offer(s) are still open - stopping anyway; "
                                    + "collect them at the Grand Exchange later");
                        }
                        Microbot.stopPlugin(plugin);
                    }
                    return;
                }

                if (waitingForOfferSlot
                        && Rs2GrandExchange.getAvailableSlotsCount() == 0
                        && !hasOwnedSoldOffer()) {
                    // Poll client-side until a safe slot opens; do not repeatedly
                    // bank, withdraw and reopen the GE while all slots are foreign.
                    return;
                }
                if (processOneSoldOffer()) {
                    // End this scheduled pass after one collection attempt. A
                    // fresh GE visit handles the next slot on the next pass.
                    return;
                }

                if (waitingForOfferSlot) {
                    if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                        return;
                    }
                    waitingForOfferSlot = false;
                    if (hasSellableInventoryItems()) {
                        sellInventory();
                        return;
                    }
                }

                // Always bank first: deposit everything, then pull out every sellable item as notes
                boolean bankHasSellables = bankAndWithdrawAll();

                if (!hasSellableInventoryItems()) {
                    if (bankHasSellables) {
                        // Withdrawing failed (e.g. bank unreachable) - retry next cycle
                        return;
                    }
                    // The bank scan found nothing sellable - switch to the idle
                    // drained watch from the next cycle on
                    bankDrained = true;
                    lastOfferProgressAt = System.currentTimeMillis();
                    return;
                }

                sellInventory();
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Remembers which GE slots were already occupied when the run started.
     * The offers in them belong to the user (or another plugin) and the
     * endgame liquidation must never abort, collect or re-list them.
     */
    private boolean snapshotOccupiedSlots() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length == 0) {
                return false;
            }
            for (int i = 0; i < offers.length; i++) {
                GrandExchangeOffer offer = offers[i];
                if (offer != null && offer.getItemId() > 0 && offer.getState() != GrandExchangeOfferState.EMPTY) {
                    foreignSlotOrdinals.add(i);
                }
            }
            return true;
        }, false);
    }

    private boolean isSellable(Rs2ItemModel item) {
        return item.isTradeable()
                && !IGNORED_ITEM_IDS.contains(item.getUnNotedId())
                && !unsellableItemIds.contains(item.getUnNotedId())
                && !skippedItemIds.contains(item.getUnNotedId());
    }

    /**
     * During the endgame liquidation only items this run placed offers for may
     * be re-listed - anything else in the inventory is left alone.
     */
    private boolean isLiquidationTarget(Rs2ItemModel item) {
        return item.isTradeable()
                && !IGNORED_ITEM_IDS.contains(item.getUnNotedId())
                && ownedOfferItemIds.contains(item.getUnNotedId());
    }

    private boolean hasSellableInventoryItems() {
        return Rs2Inventory.all(this::isSellable).size() > 0;
    }

    /**
     * @return true when this pass was reserved for an owned sold slot, even if
     *         the GE UI attempt failed and must be retried next pass
     */
    private boolean processOneSoldOffer() {
        if (!hasOwnedSoldOffer()) {
            return false;
        }
        if (!Rs2GrandExchange.openExchange()) {
            return true;
        }
        if (!sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return true;
        }
        collectOwnedSoldOffersToBank();
        Rs2GrandExchange.closeExchange();
        sleepUntil(() -> !Rs2GrandExchange.isOpen());
        return true;
    }

    /**
     * Collects finished offers and checks if any offers are still pending.
     * Only a positively opened exchange counts - a failed open means "unknown",
     * never "done".
     *
     * @return true when every owned Grand Exchange slot is empty and the plugin can stop
     */
    private boolean collectPendingOffers() {
        if (!Rs2GrandExchange.openExchange()) {
            return false;
        }
        if (!sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return false;
        }
        collectOwnedSoldOffersToBank();
        boolean allOwnedSlotsEmpty = !hasOwnedActiveOffers();
        Rs2GrandExchange.closeExchange();
        sleepUntil(() -> !Rs2GrandExchange.isOpen());
        return allOwnedSlotsEmpty;
    }

    /**
     * Slots currently holding an offer this run placed. Both conditions are
     * required, so foreign offers are never touched: the slot must not have
     * been occupied at startup, and the offer's item must be one this run
     * successfully listed.
     *
     * @return owned slots, or null when the client offer array could not be read
     */
    private List<GrandExchangeSlots> findOwnedActiveSlots() {
        return clientValue(() -> {
            List<GrandExchangeSlots> owned = new ArrayList<>();
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            GrandExchangeSlots[] slots = GrandExchangeSlots.values();
            if (offers == null || offers.length == 0) {
                return null;
            }
            int slotCount = Math.min(offers.length, slots.length);
            for (int i = 0; i < slotCount; i++) {
                if (isOwnedOffer(i, offers[i])) {
                    owned.add(slots[i]);
                }
            }
            return owned;
        }, null);
    }

    /** True while at least one non-empty slot still belongs to this run. */
    private boolean hasOwnedActiveOffers() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length == 0) {
                return true;
            }
            for (int i = 0; i < offers.length; i++) {
                if (isOwnedOffer(i, offers[i])) {
                    return true;
                }
            }
            return false;
        }, true);
    }

    /**
     * Checks only this run's slots. A completed foreign offer must not wake the
     * collector or reset the leftover-offer timer.
     */
    private boolean hasOwnedSoldOffer() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length == 0) {
                return false;
            }
            for (int i = 0; i < offers.length; i++) {
                GrandExchangeOffer offer = offers[i];
                if (isOwnedOffer(i, offer) && offer.getState() == GrandExchangeOfferState.SOLD) {
                    return true;
                }
            }
            return false;
        }, false);
    }

    private boolean isOwnedOffer(int slotOrdinal, GrandExchangeOffer offer) {
        return offer != null
                && offer.getItemId() > 0
                && (offer.getState() == GrandExchangeOfferState.SELLING
                    || offer.getState() == GrandExchangeOfferState.SOLD
                    || offer.getState() == GrandExchangeOfferState.CANCELLED_SELL)
                && !foreignSlotOrdinals.contains(slotOrdinal)
                && ownedOfferItemIds.contains(canonicalTradeItemId(offer.getItemId()));
    }

    /**
     * Collects at most one completed sell offer per invocation. The global
     * collect-all action is deliberately forbidden because it also clears
     * pre-existing buy and sell offers owned by the player.
     *
     * <p>Microbot's targeted collect returns as soon as the offer screen hides,
     * before the GE overview children have necessarily rebuilt. Opening a
     * second slot in the same pass races its collect-button population. The
     * caller therefore closes the GE after one slot and handles the next slot
     * on a later scheduled pass.</p>
     *
     * @return true when one owned sold slot was collected and resolved
     */
    private boolean collectOwnedSoldOffersToBank() {
        List<GrandExchangeSlots> ownedSlots = findOwnedActiveSlots();
        if (ownedSlots == null) {
            return false;
        }
        for (GrandExchangeSlots slot : ownedSlots) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (details == null || details.getState() != GrandExchangeOfferState.SOLD) {
                continue;
            }
            int expectedItemId = details.getItemId();
            if (ownedSlotState(slot, expectedItemId) != GrandExchangeOfferState.SOLD) {
                return false;
            }
            if (!Rs2GrandExchange.collectOffer(slot, true)
                    || !sleepUntil(() -> isOwnedSlotResolved(slot, expectedItemId), SETUP_WAIT_TIMEOUT_MS)) {
                if (Rs2GrandExchange.isOfferScreenOpen()) {
                    Rs2GrandExchange.backToOverview();
                }
                return false;
            }
            if (!sleepUntil(() -> Rs2GrandExchange.isOpen()
                    && !Rs2GrandExchange.isOfferScreenOpen(), SETUP_WAIT_TIMEOUT_MS)) {
                return false;
            }
            sleep(COLLECTION_OVERVIEW_SETTLE_MIN_MS, COLLECTION_OVERVIEW_SETTLE_MAX_MS);
            return true;
        }
        return false;
    }

    /**
     * The current state of the offer in a slot, or null when the slot no
     * longer holds the expected item (filled and collected meanwhile).
     */
    private GrandExchangeOfferState ownedSlotState(GrandExchangeSlots slot, int expectedItemId) {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slot.ordinal() >= offers.length) {
                return null;
            }
            GrandExchangeOffer offer = offers[slot.ordinal()];
            if (!isOwnedOffer(slot.ordinal(), offer)
                    || !sameTradeItem(offer.getItemId(), expectedItemId)) {
                return null;
            }
            return offer.getState();
        }, null);
    }

    /**
     * True only after a reliable client read proves that the expected owned
     * offer no longer occupies this slot. Unknown reads fail closed.
     */
    private boolean isOwnedSlotResolved(GrandExchangeSlots slot, int expectedItemId) {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slot.ordinal() >= offers.length) {
                return false;
            }
            GrandExchangeOffer offer = offers[slot.ordinal()];
            return offer == null
                    || offer.getState() == GrandExchangeOfferState.EMPTY
                    || !isOwnedOffer(slot.ordinal(), offer)
                    || !sameTradeItem(offer.getItemId(), expectedItemId);
        }, false);
    }

    /** True after a reliable read proves the expected offer left the supplied state. */
    private boolean hasOwnedSlotLeftState(GrandExchangeSlots slot, int expectedItemId,
                                           GrandExchangeOfferState state) {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slot.ordinal() >= offers.length) {
                return false;
            }
            GrandExchangeOffer offer = offers[slot.ordinal()];
            if (offer == null
                    || offer.getState() == GrandExchangeOfferState.EMPTY
                    || !isOwnedOffer(slot.ordinal(), offer)
                    || !sameTradeItem(offer.getItemId(), expectedItemId)) {
                return true;
            }
            return offer.getState() != state;
        }, false);
    }

    /**
     * Endgame sweep for offers that never filled: aborts them, takes the items
     * back into the inventory and re-lists each stack at 1gp so the run finishes.
     * Only offers this run placed are aborted and collected (slot by slot -
     * a collect-all could drag unrelated finished offers into the inventory
     * and re-list them at 1gp).
     *
     * @return true when the leftovers were re-listed (or nothing was left to do)
     */
    private boolean liquidateLeftoverOffers() {
        if (!Rs2GrandExchange.openExchange()
                || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return false;
        }
        List<GrandExchangeSlots> ownedSlots = findOwnedActiveSlots();
        if (ownedSlots == null) {
            return false;
        }
        if (ownedSlots.isEmpty()) {
            if (!Rs2Inventory.all(this::isLiquidationTarget).isEmpty()) {
                return relistRecoveredItemsAtOneGp();
            }
            // Everything this run listed already filled (or nothing was listed).
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen());
            return true;
        }
        announce("Leftover offer(s) not selling - re-listing at 1gp");

        // Freeze the item identity of each owned slot before any UI action so
        // every later abort/collect can be revalidated against the same offer.
        Map<GrandExchangeSlots, Integer> expectedItemsBySlot = new LinkedHashMap<>();
        for (GrandExchangeSlots slot : ownedSlots) {
            GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
            if (details == null) {
                return false;
            }
            expectedItemsBySlot.put(slot, details.getItemId());
        }

        // Abort every owned offer that is still open
        for (Map.Entry<GrandExchangeSlots, Integer> entry : expectedItemsBySlot.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            GrandExchangeSlots slot = entry.getKey();
            int itemId = entry.getValue();
            GrandExchangeOfferState state = ownedSlotState(slot, itemId);
            if (state == null) {
                if (isOwnedSlotResolved(slot, itemId)) {
                    continue;
                }
                return false;
            }
            if (state != GrandExchangeOfferState.SELLING) {
                continue;
            }
            abortSlotOffer(slot);
            // Wait for the abort to register before moving to the next slot
            if (!sleepUntil(() -> hasOwnedSlotLeftState(
                    slot, itemId, GrandExchangeOfferState.SELLING), SETUP_WAIT_TIMEOUT_MS)) {
                return false;
            }
            sleep(400, 700);
        }

        // Collect each owned slot on its own (cancelled offers return the items,
        // offers that filled meanwhile return coins)
        for (Map.Entry<GrandExchangeSlots, Integer> entry : expectedItemsBySlot.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            GrandExchangeSlots slot = entry.getKey();
            int itemId = entry.getValue();
            GrandExchangeOfferState state = ownedSlotState(slot, itemId);
            if (state == null) {
                if (isOwnedSlotResolved(slot, itemId)) {
                    continue;
                }
                return false;
            }
            if (state != GrandExchangeOfferState.CANCELLED_SELL && state != GrandExchangeOfferState.SOLD) {
                return false;
            }
            if (!Rs2GrandExchange.collectOffer(slot, false)
                    || !sleepUntil(() -> isOwnedSlotResolved(slot, itemId), SETUP_WAIT_TIMEOUT_MS)) {
                if (Rs2GrandExchange.isOfferScreenOpen()) {
                    Rs2GrandExchange.backToOverview();
                }
                return false;
            }
            if (Rs2GrandExchange.isOfferScreenOpen()) {
                Rs2GrandExchange.backToOverview();
            }
            if (!sleepUntil(() -> Rs2GrandExchange.isOpen()
                    && !Rs2GrandExchange.isOfferScreenOpen(), SETUP_WAIT_TIMEOUT_MS)) {
                return false;
            }
            sleep(COLLECTION_OVERVIEW_SETTLE_MIN_MS, COLLECTION_OVERVIEW_SETTLE_MAX_MS);
        }

        // Every original slot must be conclusively empty/replaced before any
        // recovered item is re-listed. A failed read or collect is retried.
        for (Map.Entry<GrandExchangeSlots, Integer> entry : expectedItemsBySlot.entrySet()) {
            if (!isOwnedSlotResolved(entry.getKey(), entry.getValue())) {
                return false;
            }
        }

        if (!Rs2Inventory.all(this::isLiquidationTarget).isEmpty()) {
            return relistRecoveredItemsAtOneGp();
        }

        // Every offer filled while we were aborting, so only coins came back.
        Rs2GrandExchange.closeExchange();
        sleepUntil(() -> !Rs2GrandExchange.isOpen());
        return true;
    }

    /** Re-lists every recovered owned stack and proves that each placement succeeded. */
    private boolean relistRecoveredItemsAtOneGp() {
        Set<Integer> recoveredItemIds = Rs2Inventory.all(this::isLiquidationTarget).stream()
                .map(Rs2ItemModel::getUnNotedId)
                .collect(Collectors.toSet());
        if (recoveredItemIds.isEmpty()) {
            return true;
        }
        int offersBefore = sessionOffersPlaced;
        forcedSellPrice = 1;
        liquidatingOwned = true;
        try {
            sellInventory();
        } finally {
            liquidatingOwned = false;
            forcedSellPrice = null;
        }
        boolean allRecoveredItemsLeftInventory = Rs2Inventory.all(this::isLiquidationTarget).isEmpty();
        int offersPlaced = sessionOffersPlaced - offersBefore;
        return allRecoveredItemsLeftInventory && offersPlaced >= recoveredItemIds.size();
    }

    /**
     * Slot-targeted variant of Rs2GrandExchange.abortOffer: aborts exactly this
     * slot and collects nothing. (abortOffer itself is name-based and ends in
     * a collect-all, which would hit unrelated offers.)
     */
    private void abortSlotOffer(GrandExchangeSlots slot) {
        int[] widgetId = new int[1];
        Rectangle[] boundsHolder = new Rectangle[1];
        boolean found = clientValue(() -> {
            Widget widget = Microbot.getClient().getWidget(GE_WIDGET_GROUP, GE_FIRST_SLOT_CHILD + slot.ordinal());
            if (!isVisible(widget)) {
                return false;
            }
            widgetId[0] = widget.getId();
            boundsHolder[0] = widget.getBounds();
            return true;
        }, false);
        if (!found) {
            return;
        }
        NewMenuEntry entry = new NewMenuEntry()
                .option("Abort offer")
                .target("")
                .identifier(2)
                .type(MenuAction.CC_OP)
                .param0(2)
                .param1(widgetId[0])
                .itemId(-1)
                .forceLeftClick(false);
        Rectangle bounds = boundsHolder[0] != null && Rs2UiHelper.isRectangleWithinCanvas(boundsHolder[0])
                ? boundsHolder[0]
                : Rs2UiHelper.getDefaultRectangle();
        Microbot.doInvoke(entry, bounds);
    }

    /**
     * Opens the bank, deposits the whole inventory and withdraws every sellable
     * item (as notes) until the inventory is full.
     *
     * @return true when the bank still holds sellable items after withdrawing,
     *         or when the bank could not be opened/read (so the cycle retries
     *         instead of wrongly concluding there is nothing left to sell)
     */
    private boolean bankAndWithdrawAll() {
        if (!Rs2Bank.openBank()) {
            return true;
        }
        if (!sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS)) {
            // Bank never opened (e.g. bank pin) - retry next cycle
            return true;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty);

        Rs2Bank.setWithdrawAsNote();

        // Give the bank contents a moment to load before trusting an empty scan
        sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), BANK_LOAD_GRACE_MS);

        boolean bankHasSellables = false;
        for (Rs2ItemModel item : Rs2Bank.bankItems()) {
            if (Thread.currentThread().isInterrupted()) {
                // Plugin was stopped mid-pass - stop clicking items
                break;
            }
            if (!isSellable(item)) {
                continue;
            }
            bankHasSellables = true;
            if (Rs2Inventory.isFull()) {
                break;
            }
            Rs2Bank.withdrawAll(item.getId());
            boolean got = sleepUntil(() -> Rs2Inventory.hasItem(item.getName(), true));
            if (!got) {
                // Items without a noted form ("This item cannot be withdrawn as
                // a note") still need to be sold - withdraw them unnoted
                Rs2Bank.setWithdrawAsItem();
                Rs2Bank.withdrawAll(item.getId());
                sleepUntil(() -> Rs2Inventory.hasItem(item.getName(), true));
                Rs2Bank.setWithdrawAsNote();
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        return bankHasSellables;
    }

    private void sellInventory() {
        // Group by item so the whole stack of an item is sold in a single offer.
        // The liquidation pass only re-lists items this run placed offers for.
        Map<Integer, List<Rs2ItemModel>> itemsById = Rs2Inventory.all(
                        liquidatingOwned ? this::isLiquidationTarget : this::isSellable).stream()
                .collect(Collectors.groupingBy(Rs2ItemModel::getUnNotedId, LinkedHashMap::new, Collectors.toList()));
        if (itemsById.isEmpty()) {
            return;
        }

        if (!Rs2GrandExchange.openExchange()) {
            return;
        }
        if (!sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return;
        }

        List<Rs2ItemModel> failedItems = new ArrayList<>();

        for (Map.Entry<Integer, List<Rs2ItemModel>> entry : itemsById.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                // Plugin was stopped mid-pass - a set interrupt flag makes every
                // sleepUntil return instantly, which would mass-flag good items
                break;
            }
            Rs2ItemModel item = entry.getValue().get(0);
            int quantity = entry.getValue().stream().mapToInt(Rs2ItemModel::getQuantity).sum();

            // A failed offer flow can leave the GE window closed (ESC abort) -
            // clicking "Offer" then hits the plain inventory and uses/drops items
            if (!Rs2GrandExchange.isOpen()) {
                if (!Rs2GrandExchange.openExchange()
                        || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
                    break;
                }
            }

            if (!waitForAvailableSlot()) {
                break;
            }
            if (!releaseEmptyForeignSlots()) {
                // Do not place an offer until a reliable client read establishes
                // which formerly foreign slots are now safe for Bank Seller to own.
                break;
            }

            int guidePrice = Rs2GrandExchange.getPrice(item.getUnNotedId());
            // Instant-sell pricing: half the active price so the offer fills
            // immediately (the endgame leftover sweep forces 1gp instead)
            int price = forcedSellPrice != null ? forcedSellPrice : Math.max(1, guidePrice / 2);

            OfferResult result = placeSellOffer(item, quantity, price);
            int remaining = inventoryTradeQuantity(item.getId());

            if (result == OfferResult.PLACED || remaining == 0) {
                // Offer confirmed - the items are gone even if the UI flow hiccuped
                sessionOffersPlaced++;
                ownedOfferItemIds.add(item.getUnNotedId());
                transientFailureCounts.remove(item.getUnNotedId());
            } else if (result == OfferResult.RESTRICTED) {
                // The GE explicitly refused the item (F2P trade restriction) - bank it for good
                unsellableItemIds.add(item.getUnNotedId());
                failedItems.addAll(entry.getValue());
                sessionStacksRefused++;
                transientFailureCounts.remove(item.getUnNotedId());
            } else {
                // Transient UI failure (the setup never opened, a widget timed
                // out, ...) - the item stays sellable, is NOT flagged unsellable
                // and is retried on a later pass. Only after repeated failures
                // it is parked so the run can still finish.
                int failures = transientFailureCounts.merge(item.getUnNotedId(), 1, Integer::sum);
                if (failures >= MAX_TRANSIENT_FAILURES) {
                    skippedItemIds.add(item.getUnNotedId());
                    Microbot.log("Skipping " + item.getName() + " after " + failures
                            + " failed offer attempts (Grand Exchange interface not responding)");
                }
            }
        }

        // A refused last item leaves its offer setup open - back out of it
        // before closing the GE so the window state is clean for the bank
        if (isSetupVisible()) {
            abortOfferSetup();
            sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
        }

        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            sleepUntil(() -> !Rs2GrandExchange.isOpen());
        }

        depositUnsellableItems(failedItems);
    }

    /**
     * Places a single sell offer for the full stack of an item using the same
     * Grand Exchange widget flow as GodsFlipper: open the setup with "Offer",
     * enter the price through the chatbox input, set the quantity with the
     * "All" button and confirm.
     *
     * <p>Only the explicit trade-restriction notice is a definitive refusal.
     * A setup that never opens or a step that times out is treated as a
     * transient UI failure and retried.</p>
     *
     * @return PLACED when the offer was confirmed and the items left the
     *         inventory, RESTRICTED on the trade-restriction notice, FAILED
     *         for transient UI failures
     */
    private OfferResult placeSellOffer(Rs2ItemModel item, int quantity, int price) {
        for (int attempt = 1; attempt <= MAX_OFFER_ATTEMPTS; attempt++) {
            try {
                OfferResult result = doPlaceSellOffer(item, quantity, price);
                if (result != OfferResult.FAILED || attempt == MAX_OFFER_ATTEMPTS) {
                    return result;
                }
                abortOfferSetup();
                sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
                if (!Rs2GrandExchange.isOpen()) {
                    if (!Rs2GrandExchange.openExchange()
                            || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
                        return OfferResult.FAILED;
                    }
                }
            } catch (RuntimeException ex) {
                // Transient UI errors (e.g. a widget without bounds on a slow client)
                // must not kill the whole sell pass or flag the item unsellable
                Microbot.log("Sell offer failed for " + item.getName() + ": " + ex.getMessage());
                abortOfferSetup();
                return OfferResult.FAILED;
            }
        }
        return OfferResult.FAILED;
    }

    private OfferResult doPlaceSellOffer(Rs2ItemModel item, int quantity, int price) {
        // A setup left open by a trade-restricted item is reused instead of
        // backing out: clicking 'Offer' on the next item switches the setup
        // straight over to it, so refused items never close the GE flow
        if (!Rs2Inventory.interact(item.getId(), "Offer")) {
            return OfferResult.FAILED;
        }

        // A reused setup stays visible the whole time - the wait must hold
        // out until the setup actually shows THIS item, not the previous one.
        // A timeout here is transient (slow client/UI) and is retried by the
        // caller - it never flags the item unsellable.
        if (!sleepUntil(() -> isSetupVisible() && sameTradeItem(resolveSetupItemId(item.getId()), item.getId()),
                SETUP_WAIT_TIMEOUT_MS)) {
            return OfferResult.FAILED;
        }

        // A red "restricted for trading" notice in the setup means Confirm is
        // dead for this item - the only genuine refusal. The setup is LEFT OPEN
        // so the next item's 'Offer' click switches straight over to it.
        String restrictionNotice = findTradeRestrictionNotice();
        if (restrictionNotice != null) {
            tradeRestrictionText = restrictionNotice;
            return OfferResult.RESTRICTED;
        }

        if (!enterPrice(price)) {
            abortOfferSetup();
            return OfferResult.FAILED;
        }

        if (!enterFullQuantity(quantity)) {
            abortOfferSetup();
            return OfferResult.FAILED;
        }

        Widget confirmButton = findConfirmButton();
        if (!clickWidget(confirmButton)) {
            abortOfferSetup();
            return OfferResult.FAILED;
        }

        // The GE warns when the price is far from the guide price (often, at
        // instant-sell prices) - keep accepting it until the setup closes
        long confirmDeadline = System.currentTimeMillis() + CONFIRM_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < confirmDeadline) {
            if (Thread.currentThread().isInterrupted()) {
                return OfferResult.FAILED;
            }
            boolean warningUp = isGePriceWarningVisible();
            if (!isSetupVisible() && !warningUp) {
                return OfferResult.PLACED;
            }
            if (warningUp) {
                acceptGePriceWarning();
            }
            sleep(200, 350);
        }

        // The offer can go through despite a lingering setup - trust the inventory
        return inventoryTradeQuantity(item.getId()) == 0 ? OfferResult.PLACED : OfferResult.FAILED;
    }

    /**
     * The GE price warning text varies by game version and side (buy/sell), so
     * match every known variant plus a generic chatbox "Select an Option" menu
     * (which is exactly what the Yes/No warning renders as).
     */
    private boolean isGePriceWarningVisible() {
        return Rs2Widget.hasWidget("Your offer is much")
                || Rs2Widget.hasWidget("much lower than")
                || Rs2Widget.hasWidget("much higher than")
                || Rs2Widget.hasWidget("Select an Option");
    }

    private void acceptGePriceWarning() {
        if (!Rs2Widget.clickWidget("Yes")) {
            // Chatbox option menus also accept number keys: 1 = first option (Yes)
            Rs2Keyboard.keyPress(KeyEvent.VK_1);
        }
    }

    /**
     * Types the price into the GE "Enter price" chatbox input.
     */
    private boolean enterPrice(int price) {
        if (!clickWidget(findCustomPriceButton())) {
            return false;
        }
        if (!sleepUntil(this::isChatboxInputVisible, SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        if (!setChatboxInputValue(price)) {
            return false;
        }
        sleep(250, 400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> !isChatboxInputVisible(), SETUP_WAIT_TIMEOUT_MS);
    }

    /**
     * Sets the offer quantity to the whole stack with the "All" button,
     * falling back to the custom quantity chatbox input.
     */
    private boolean enterFullQuantity(int quantity) {
        Widget allButton = findAllQuantityButton();
        if (allButton != null && clickWidget(allButton)) {
            sleep(300, 600);
            return true;
        }

        if (!clickWidget(findQuantityButton())) {
            return false;
        }
        if (!sleepUntil(this::isChatboxInputVisible, SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        if (!setChatboxInputValue(quantity)) {
            return false;
        }
        sleep(250, 400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> !isChatboxInputVisible(), SETUP_WAIT_TIMEOUT_MS);
    }

    /**
     * Backs out of a half-finished offer setup so the next item starts clean.
     * Uses the setup's Back arrow (returning to the GE overview) - a plain
     * ESC would close the whole Grand Exchange window and force a reopen.
     */
    private void abortOfferSetup() {
        if (isChatboxInputVisible()) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(300, 600);
        }
        if (isSetupVisible()) {
            if (clickWidget(findBackButton()) && sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS)) {
                return;
            }
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
        }
    }

    private Widget findBackButton() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (!isVisible(setup)) {
                return null;
            }
            // The back arrow is a sibling of the setup panel, not inside it
            Widget parent = setup.getParent();
            Widget match = findWidgetRecursive(parent, this::isBackWidget, 0);
            return match != null ? match : findWidgetRecursive(setup, this::isBackWidget, 0);
        }, null);
    }

    private boolean isBackWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (normalize(action).equals("back")) {
                return true;
            }
        }
        return false;
    }

    private boolean waitForAvailableSlot() {
        while (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
            if (hasOwnedSoldOffer()) {
                boolean collected = collectOwnedSoldOffersToBank();
                if (!collected) {
                    // A slot-targeted collect failed. Leave the GE and retry on a
                    // later pass instead of spinning while the SOLD state remains.
                    waitForSafeOfferSlot();
                } else {
                    waitingForOfferSlot = false;
                }
                // Even when a slot was freed, stop this sell pass. Reopening the
                // GE on the next scheduled pass prevents a second collection from
                // racing the overview widget rebuild.
                return false;
            }
            if (Rs2GrandExchange.getAvailableSlotsCount() > 0) {
                waitingForOfferSlot = false;
                return true;
            }
            if (!hasOwnedActiveOffers()) {
                // Every occupied slot is foreign, so Bank Seller has nothing it
                // is allowed to collect in order to make room.
                waitForSafeOfferSlot();
                return false;
            }
            if (!sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0
                    || hasOwnedSoldOffer(), EXCHANGE_OPEN_TIMEOUT_MS)) {
                waitForSafeOfferSlot();
                return false;
            }
        }
        waitingForOfferSlot = false;
        return true;
    }

    /**
     * A slot occupied at startup remains foreign until a reliable client read
     * observes it EMPTY. Once empty it can safely be reused and owned by this run.
     */
    private boolean releaseEmptyForeignSlots() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length == 0) {
                return false;
            }
            foreignSlotOrdinals.removeIf(slotOrdinal -> {
                if (slotOrdinal < 0 || slotOrdinal >= offers.length) {
                    return false;
                }
                GrandExchangeOffer offer = offers[slotOrdinal];
                return offer == null || offer.getState() == GrandExchangeOfferState.EMPTY;
            });
            return true;
        }, false);
    }

    private void waitForSafeOfferSlot() {
        if (!waitingForOfferSlot) {
            Microbot.log("No safe Grand Exchange slot is available; waiting without touching pre-existing offers");
        }
        waitingForOfferSlot = true;
    }

    private void depositUnsellableItems(List<Rs2ItemModel> failedItems) {
        if (failedItems.isEmpty()) {
            return;
        }
        if (!Rs2Bank.openBank()) {
            return;
        }
        sleepUntil(Rs2Bank::isOpen);
        for (Rs2ItemModel item : failedItems) {
            Rs2Bank.depositAll(item.getId());
            sleepUntil(() -> !Rs2Inventory.hasItem(item.getId()));
        }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    // ------------------------------------------------------------------
    // Grand Exchange widget helpers (mirrors GodsFlipper's sell setup flow)
    // ------------------------------------------------------------------

    private int inventoryTradeQuantity(int itemId) {
        int quantity = 0;
        for (Rs2ItemModel inventoryItem : Rs2Inventory.all()) {
            if (inventoryItem != null && sameTradeItem(inventoryItem.getId(), itemId)) {
                quantity += Math.max(0, inventoryItem.getQuantity());
            }
        }
        return quantity;
    }

    private int canonicalTradeItemId(int itemId) {
        if (itemId <= 0) {
            return itemId;
        }
        int unnotedId = Rs2ItemModel.getUnNotedId(itemId);
        return unnotedId > 0 ? unnotedId : itemId;
    }

    private boolean sameTradeItem(int leftItemId, int rightItemId) {
        return leftItemId > 0
                && rightItemId > 0
                && canonicalTradeItemId(leftItemId) == canonicalTradeItemId(rightItemId);
    }

    private boolean isSetupVisible() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return isVisible(setup);
        }, false);
    }

    private boolean isChatboxInputVisible() {
        return clientValue(() -> {
            Widget input = Microbot.getClient().getWidget(ComponentID.CHATBOX_FULL_INPUT);
            return isVisible(input);
        }, false);
    }

    private boolean setChatboxInputValue(long value) {
        if (value <= 0) {
            return false;
        }
        return clientValue(() -> {
            Widget input = Microbot.getClient().getWidget(ComponentID.CHATBOX_FULL_INPUT);
            if (!isVisible(input)) {
                return false;
            }
            input.setText(value + "*");
            Microbot.getClient().setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(value));
            return true;
        }, false);
    }

    /**
     * Returns the item id shown in the open offer setup when it matches the
     * expected item (noted or unnoted), otherwise -1.
     */
    private int resolveSetupItemId(int expectedItemId) {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (!isVisible(setup)) {
                return -1;
            }
            return containsItemId(setup, expectedItemId, 0) ? expectedItemId : -1;
        }, -1);
    }

    /**
     * Returns the red trade-restriction notice shown inside the offer setup on
     * restricted accounts ("Your account will be restricted for trading until
     * ..."), or null when the setup is clear.
     */
    private String findTradeRestrictionNotice() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            Widget match = findWidgetRecursive(setup, widget -> {
                String value = normalize(widget == null ? null : widget.getText());
                return value.contains("restricted for trading") || value.contains("restrictions will lift");
            }, 0);
            return match == null ? null : match.getText().replaceAll("<[^>]*>", "").trim();
        }, null);
    }

    private boolean containsItemId(Widget root, int expectedItemId, int depth) {
        if (root == null || expectedItemId <= 0 || depth > 14) {
            return false;
        }
        if (root.getItemId() > 0 && sameTradeItem(root.getItemId(), expectedItemId)) {
            return true;
        }
        Widget[] dynamicChildren = root.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                if (containsItemId(child, expectedItemId, depth + 1)) {
                    return true;
                }
            }
        }
        Widget[] staticChildren = root.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                if (containsItemId(child, expectedItemId, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Widget findCustomPriceButton() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return findWidgetRecursive(setup, this::isCustomPriceWidget, 0);
        }, null);
    }

    private Widget findQuantityButton() {
        return clientValue(() -> {
            Widget offerContainer = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (isVisible(offerContainer)) {
                Widget exactButton = offerContainer.getChild(7);
                if (isClickable(exactButton)) {
                    return exactButton;
                }
            }
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return findWidgetRecursive(setup, this::isQuantityWidget, 0);
        }, null);
    }

    private Widget findAllQuantityButton() {
        return clientValue(() -> {
            Widget offerContainer = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (isVisible(offerContainer)) {
                Widget exactButton = offerContainer.getChild(50);
                if (isClickable(exactButton) && isAllQuantityWidget(exactButton)) {
                    return exactButton;
                }
                Widget legacyButton = offerContainer.getChild(6);
                if (isClickable(legacyButton) && isAllQuantityWidget(legacyButton)) {
                    return legacyButton;
                }
                return findWidgetRecursive(offerContainer, this::isAllQuantityWidget, 0);
            }
            return null;
        }, null);
    }

    private Widget findConfirmButton() {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            return findWidgetRecursive(setup, this::isConfirmWidget, 0);
        }, null);
    }

    private Widget findWidgetRecursive(Widget root, Predicate<Widget> predicate, int depth) {
        if (!isVisible(root) || predicate == null || depth > 14) {
            return null;
        }
        if (predicate.test(root) && root.getBounds() != null) {
            return root;
        }
        Widget[] dynamicChildren = root.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                Widget match = findWidgetRecursive(child, predicate, depth + 1);
                if (match != null) {
                    return match;
                }
            }
        }
        Widget[] staticChildren = root.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                Widget match = findWidgetRecursive(child, predicate, depth + 1);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private boolean isCustomPriceWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            String value = normalize(action);
            if (value.equals("enter price")
                    || value.contains("custom price")
                    || value.equals("set price")) {
                return true;
            }
        }
        return false;
    }

    private boolean isQuantityWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String text = normalize(widget.getText());
        String[] actions = widget.getActions();
        if (actions != null) {
            for (String action : actions) {
                String value = normalize(action);
                if (value.equals("enter quantity")
                        || value.equals("set quantity")
                        || value.contains("custom quantity")) {
                    return true;
                }
            }
        }
        return text.contains("quantity") && hasAnyAction(widget);
    }

    private boolean isAllQuantityWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        if ("all".equals(normalize(widget.getText()))) {
            return true;
        }
        if (!hasAnyAction(widget)) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions != null) {
            for (String action : actions) {
                String value = normalize(action);
                if ("all".equals(value)
                        || value.contains("set all")
                        || (value.contains("all") && value.contains("quantity"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConfirmWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (normalize(action).contains("confirm")) {
                return true;
            }
        }
        return normalize(widget.getText()).contains("confirm") && hasAnyAction(widget);
    }

    private boolean clickWidget(Widget widget) {
        // Widget state (isHidden etc.) asserts the client thread on newer
        // clients, so every check must happen inside clientValue - a plain
        // null check is the only thing safe to do on the script thread
        if (widget == null) {
            return false;
        }
        try {
            return clientValue(() -> {
                if (!isClickable(widget)) {
                    return false;
                }
                return Rs2Widget.clickWidget(widget);
            }, false);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isClickable(Widget widget) {
        return isVisible(widget) && widget.getBounds() != null;
    }

    private boolean isVisible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    private boolean hasAnyAction(Widget widget) {
        String[] actions = widget == null ? null : widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && !action.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("<[^>]*>", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // User-facing chatbox messages (final verdict, 1gp leftover sweep)
    // ------------------------------------------------------------------

    /**
     * Posts a single user-facing line to the in-game chatbox and the client log.
     */
    private void announce(String message) {
        String line = "[BankSeller] " + message;
        Microbot.log(line);
        clientValue(() -> {
            Microbot.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null, false);
            return true;
        }, false);
    }

    /**
     * The single chatbox verdict when the bank is fully processed:
     * an error when the GE refused everything, a summary otherwise.
     */
    private String finalVerdictMessage() {
        if (sessionStacksRefused > 0 && sessionOffersPlaced == 0) {
            String message = "ERROR: the Grand Exchange refused every item (" + sessionStacksRefused
                    + " stack(s)) - nothing can be sold. Stopping.";
            if (tradeRestrictionText != null) {
                message += " " + tradeRestrictionText;
            }
            return message;
        }
        if (sessionOffersPlaced == 0 && !skippedItemIds.isEmpty()) {
            return "ERROR: the Grand Exchange interface did not respond - " + skippedItemIds.size()
                    + " stack(s) could not be sold and were left in the bank. Stopping.";
        }
        if (sessionStacksRefused > 0) {
            return "Nothing left to sell - " + sessionStacksRefused
                    + " stack(s) were refused by the GE. Stopping.";
        }
        return "Nothing left to sell and all Bank Seller offers are complete - stopping";
    }

    private <T> T clientValue(Supplier<T> supplier, T fallback) {
        if (supplier == null) {
            return fallback;
        }
        try {
            T value = Microbot.getClientThread().invoke(supplier);
            return value == null ? fallback : value;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
