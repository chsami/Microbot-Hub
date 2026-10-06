package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    /** Give the last newly placed sale a short chance to join the overview collection. */
    private static final int COLLECTION_BATCH_READY_MS = 1_800;

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

    private enum CollectionResult {
        FAILED,
        BATCH
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

    /** Known GE eligibility by actual item id; unknown client reads are never cached. */
    private final Map<Integer, Boolean> geEligibilityByItemId = new HashMap<>();

    private final Map<Integer, Integer> canonicalItemIds = new HashMap<>();

    /**
     * Unnoted ids of every item this run successfully placed an offer for.
     * Only slots holding one of these items are owned by the run, so pre-existing offers (or offers from
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

    /** True while every usable GE slot is occupied and none may be collected safely. */
    private boolean waitingForOfferSlot;

    /** Keep selling this withdrawal across GE waits; never bank it again just to resume. */
    private boolean inventoryBatchPending;

    private final Map<Integer, PlacedOffer> placedOffersBySlot = new HashMap<>();
    private int completedSellingRounds;
    private PendingReprice pendingReprice;
    private boolean stopRequested;
    /** Mandatory safety: old saved checkbox values can never disable protection. */
    private final boolean protectStartingItems = true;
    private boolean protectionSnapshotted;
    private int protectionSnapshotAttempts;
    private Set<Integer> protectedStartingItemIds = Collections.emptySet();
    /** Retained even when recovery clears a journal or a completed original no longer has a slot. */
    private final Set<Integer> protectedOriginalItemIds = new HashSet<>();
    private BankSellerLoginReadiness startingLoginReadiness = new BankSellerLoginReadiness();
    private String startingProtectionProfile;
    private String protectionSnapshotFailure;
    private boolean protectionNeedsEquipmentTab;
    private boolean protectionNeedsInventoryTab;
    private List<Integer> pendingStartingInventoryIds;
    private List<Integer> pendingStartingEquipmentIds;
    private BankSellerOriginalOffers originalOffers;
    private boolean originalOffersPrepared;
    private boolean restoringOriginalOffers;
    private boolean cleanupForOriginalRestoration;
    private String completionMessage;
    private int cleanupFailures;
    private boolean energySettingOverridden;
    private boolean previousEnergyItemSetting;

    /** Frozen offer identity: partial fills change sold quantity, not these fields. */
    private static final class PlacedOffer {
        private final GrandExchangeSlots slot;
        private final int itemId;
        private final int quantity;
        private final long price;
        private final int placedRound;
        private final long placedAt;

        private PlacedOffer(GrandExchangeSlots slot, int itemId, int quantity, long price,
                            int placedRound, long placedAt) {
            this.slot = slot;
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
            this.placedRound = placedRound;
            this.placedAt = placedAt;
        }

        private boolean matches(GrandExchangeOffer offer) {
            return offer != null && offer.getItemId() == itemId
                    && offer.getTotalQuantity() == quantity && offer.getPrice() == price;
        }
    }

    /** Retained across UI retries so cancelled items cannot lose their reduced price. */
    private static final class PendingReprice {
        private final PlacedOffer original;
        private final int nextPrice;
        private final int inventoryBefore;
        private int remainingToRecover = -1;
        private boolean collected;
        private int failures;

        private PendingReprice(PlacedOffer original, int nextPrice, int inventoryBefore) {
            this.original = original;
            this.nextPrice = nextPrice;
            this.inventoryBefore = inventoryBefore;
        }
    }

    /** Session tallies driving the final chatbox verdict when nothing is left to sell. */
    private int sessionOffersPlaced;
    private int sessionStacksRefused;

    public boolean run(BankSellerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        if (!energySettingOverridden) {
            previousEnergyItemSetting = Microbot.useStaminaPotsIfNeeded;
            energySettingOverridden = true;
        }
        // Auto-drinking would change a protected potion's dose/item ID and
        // allow the resulting variant to escape starting-item protection.
        Microbot.useStaminaPotsIfNeeded = false;
        this.plugin = plugin;
        unsellableItemIds.clear();
        skippedItemIds.clear();
        transientFailureCounts.clear();
        geEligibilityByItemId.clear();
        canonicalItemIds.clear();
        ownedOfferItemIds.clear();
        foreignSlotOrdinals.clear();
        occupiedSlotsSnapshotted = false;
        tradeRestrictionText = null;
        bankDrained = false;
        waitingForOfferSlot = false;
        inventoryBatchPending = false;
        placedOffersBySlot.clear();
        completedSellingRounds = 0;
        pendingReprice = null;
        stopRequested = false;
        protectionSnapshotted = false;
        protectionSnapshotAttempts = 0;
        protectedStartingItemIds = Collections.emptySet();
        protectedOriginalItemIds.clear();
        startingLoginReadiness = new BankSellerLoginReadiness();
        startingProtectionProfile = null;
        protectionSnapshotFailure = null;
        protectionNeedsEquipmentTab = false;
        protectionNeedsInventoryTab = false;
        pendingStartingInventoryIds = null;
        pendingStartingEquipmentIds = null;
        originalOffers = createOriginalOffersController();
        originalOffersPrepared = false;
        restoringOriginalOffers = false;
        cleanupForOriginalRestoration = false;
        completionMessage = null;
        cleanupFailures = 0;
        sessionOffersPlaced = 0;
        sessionStacksRefused = 0;
        // Capture the enabled-state contents immediately when available, not
        // after the scheduler first gets time to run. Unknown containers still
        // fail closed and are retried before any banking or GE mutations.
        if (protectStartingItems && Microbot.isLoggedIn()) {
            protectionSnapshotted = snapshotStartingProtection();
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (stopRequested || !Microbot.isLoggedIn()) return;
                if (!protectionSnapshotted) {
                    if (!snapshotStartingProtection()) {
                        if (startingLoginReadiness.accountChanged()) {
                            stopSafely("The account changed while starting-item protection was being captured; "
                                    + "nothing has been banked or sold. Stopping.");
                            return;
                        }
                        // Modal bank/GE panels hide the side tabs. Close only
                        // their frame, then verify the missing slots on a later
                        // tick, before any deposit, withdrawal, Abort or Collect.
                        revealStartingProtectionTabs();
                        if (++protectionSnapshotAttempts >= 10) {
                            stopSafely("Unable to reliably snapshot starting inventory/equipment; "
                                    + (protectionSnapshotFailure == null ? "" : protectionSnapshotFailure + "; ")
                                    + "nothing has been banked or sold. Stopping.");
                        }
                        return;
                    }
                    protectionSnapshotted = true;
                }
                if (startingProtectionProfile != null && !clientValue(() ->
                        startingLoginReadiness.matchesProfile(Microbot.getConfigManager().getRSProfileKey()), false)) {
                    stopSafely("The account changed after starting-item protection was captured; "
                            + "stopping before further banking or selling.");
                    return;
                }
                if (originalOffersPrepared && !originalOffers.accountStillMatches()) {
                    stopSafely("The logged-in account changed. Starting-item protection and original GE "
                            + "recovery belong to the previous account; stopping before further selling.");
                    return;
                }
                Microbot.useStaminaPotsIfNeeded = false;
                if (!super.run()) return;
                if (!originalOffersPrepared) {
                    BankSellerOriginalOffers.Result prepared = originalOffers.prepare();
                    protectedOriginalItemIds.addAll(originalOffers.protectedItemIds());
                    if (prepared == BankSellerOriginalOffers.Result.ERROR) {
                        stopSafely("Existing GE offers could not be safely prepared: " + originalOffers.errorMessage());
                        return;
                    }
                    if (prepared != BankSellerOriginalOffers.Result.READY) {
                        return;
                    }
                    originalOffersPrepared = true;
                    if (originalOffers.isRecoveryOnly()) {
                        restoringOriginalOffers = true;
                    }
                }
                if (!originalOffers.accountStillMatches() || (startingProtectionProfile != null
                        && !clientValue(() -> startingLoginReadiness.matchesProfile(
                                Microbot.getConfigManager().getRSProfileKey()), false))) {
                    stopSafely("The logged-in account changed. Starting-item protection and original GE "
                            + "recovery belong to the previous account; stopping before further selling.");
                    return;
                }
                if (restoringOriginalOffers) {
                    restoreOriginalOffers();
                    return;
                }
                if (!occupiedSlotsSnapshotted) {
                    if (!snapshotOccupiedSlots()) {
                        return;
                    }
                    occupiedSlotsSnapshotted = true;
                }
                if (cleanupForOriginalRestoration) {
                    if (bankRemainingOwnedOffers()) {
                        cleanupForOriginalRestoration = false;
                        restoringOriginalOffers = true;
                    }
                    return;
                }

                // Recovery and stale-price checks must precede the full-slot idle
                // shortcut; otherwise an unsold full exchange can wait forever.
                if (pendingReprice != null) {
                    resumePendingReprice();
                    return;
                }
                if (hasUnexpectedCancelledOffer()) {
                    stopSafely("An owned offer was cancelled outside the repricing flow. "
                            + "Leaving the returned items for you to collect. Stopping.");
                    return;
                }
                if (repriceOneStaleOffer()) {
                    return;
                }
                if ((bankDrained || Rs2GrandExchange.getAvailableSlotsCount() == 0)
                        && allOwnedOffersStalledAtOneGp()) {
                    if (originalOffers.hasPendingRestoration()) {
                        pendingReprice = null;
                        cleanupForOriginalRestoration = true;
                        completionMessage = "No buyers for the final 1gp offers; unsold items were banked "
                                + "and your original GE offers restored.";
                    } else {
                        stopSafely("1gp offer(s) still have no buyer after 60 seconds; "
                                + "leaving them listed for you to collect later. Stopping.");
                    }
                    return;
                }
                if (bankDrained) {
                    // Bank and inventory are both drained of sellables - just watch
                    // the remaining offers. No bank/GE opening, no mouse movement.
                    if (hasOwnedSoldOffer()) {
                        boolean allOwnedSlotsEmpty = collectPendingOffers();
                        if (allOwnedSlotsEmpty) {
                            finishSellingRun();
                            return;
                        }
                    }
                    if (!hasOwnedActiveOffers()) {
                        finishSellingRun();
                        return;
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
                if (inventoryBatchPending) {
                    if (hasSellableInventoryItems()) {
                        // Fill available slots before collecting, and resume the
                        // same withdrawal even after a failed GE/collection attempt.
                        sellInventory();
                        return;
                    }
                    inventoryBatchPending = false;
                }

                if (processSoldOffers()) {
                    return;
                }

                // Normalize the initial inventory, then bank only between withdrawals.
                boolean bankHasSellables = bankAndWithdrawAll();

                if (!hasSellableInventoryItems()) {
                    if (bankHasSellables) {
                        // Withdrawing failed (e.g. bank unreachable) - retry next cycle
                        return;
                    }
                    // The bank scan found nothing sellable - switch to the idle
                    // drained watch from the next cycle on
                    bankDrained = true;
                    return;
                }

                inventoryBatchPending = true;
                sellInventory();
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        stopRequested = true;
        try {
            super.shutdown();
        } finally {
            if (energySettingOverridden) {
                Microbot.useStaminaPotsIfNeeded = previousEnergyItemSetting;
                energySettingOverridden = false;
            }
        }
    }

    /**
     * Remembers which GE slots were already occupied when the run started.
     * The offers in them belong to the user (or another plugin) and the
     * repricing must never abort, collect or re-list them.
     */
    private boolean snapshotOccupiedSlots() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return false;
            }
            for (GrandExchangeOffer offer : offers) {
                if (offer == null || offer.getState() == null) {
                    return false;
                }
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
        if (item == null) {
            return false;
        }
        int canonicalId = canonicalTradeItemId(item.getId());
        return canonicalId > 0 && isGeSellable(item.getId())
                && !protectedStartingItemIds.contains(canonicalId)
                && !protectedOriginalItemIds.contains(canonicalId)
                && !originalOffers.protectedItemIds().contains(canonicalId)
                && !IGNORED_ITEM_IDS.contains(canonicalId)
                && !unsellableItemIds.contains(canonicalId)
                && !skippedItemIds.contains(canonicalId);
    }

    /** This must run before any GE collection, bank deposit or withdrawal. */
    private boolean snapshotStartingProtection() {
        protectionNeedsInventoryTab = false;
        protectionNeedsEquipmentTab = false;
        Set<Integer> snapshot = clientValue(() -> {
            String profile = Microbot.getConfigManager().getRSProfileKey();
            boolean loggedIn = Microbot.getClient().getGameState() == GameState.LOGGED_IN;
            boolean playerPresent = Microbot.getClient().getLocalPlayer() != null;
            boolean stableLoggedIn = startingLoginReadiness.observe(loggedIn, playerPresent, profile,
                    Microbot.getClient().getTickCount());
            if (!loggedIn || !playerPresent || !startingLoginReadiness.matchesProfile(profile)) {
                protectionSnapshotFailure = "Logged-in account data is not ready or has changed";
                return null;
            }
            ItemContainer inventory = Microbot.getClient().getItemContainer(InventoryID.INVENTORY);
            ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
            if ((inventory != null && inventory.getItems() == null)
                    || (equipment != null && equipment.getItems() == null)) {
                protectionSnapshotFailure = "An item container has incomplete slot data";
                return null;
            }
            // Only one side-panel tab can be visible. Keep each independently
            // verified starting snapshot so two absent empty containers do not
            // cause endless inventory/equipment tab switching.
            if (pendingStartingInventoryIds == null) {
                pendingStartingInventoryIds = BankSellerProtectionPolicy.resolveContainerIds(
                        BankSellerStartingWidgets.containerIds(inventory),
                        inventory == null ? BankSellerStartingWidgets.inventoryIds(Microbot.getClient()) : null,
                        28, stableLoggedIn);
            }
            if (pendingStartingEquipmentIds == null) {
                pendingStartingEquipmentIds = BankSellerProtectionPolicy.resolveContainerIds(
                        BankSellerStartingWidgets.containerIds(equipment),
                        equipment == null ? BankSellerStartingWidgets.equipmentIds(Microbot.getClient()) : null,
                        11, stableLoggedIn);
            }
            List<Integer> inventoryIds = pendingStartingInventoryIds;
            List<Integer> equipmentIds = pendingStartingEquipmentIds;
            if (inventoryIds == null || equipmentIds == null) {
                protectionNeedsInventoryTab = inventoryIds == null && inventory == null;
                protectionNeedsEquipmentTab = equipmentIds == null && equipment == null;
                protectionSnapshotFailure = !stableLoggedIn && (inventory == null || equipment == null)
                        ? "Waiting for stable login before verifying empty containers"
                        : inventoryIds == null ? "Inventory slots could not be verified"
                        : "Equipment slots (including ring/ammo) could not be verified";
                return null;
            }
            Set<Integer> ids = BankSellerProtectionPolicy.snapshotProtectedIds(inventoryIds, equipmentIds,
                    Microbot.getClient()::getItemDefinition);
            if (ids != null) {
                startingProtectionProfile = profile;
            } else {
                protectionSnapshotFailure = "A starting item's definition is unavailable";
            }
            return ids;
        }, null);
        if (snapshot == null) {
            return false;
        }
        protectedStartingItemIds = snapshot;
        protectionSnapshotFailure = null;
        Microbot.log("[BankSeller] Protected " + snapshot.size() + " starting inventory/equipment item types");
        return true;
    }

    private void revealStartingProtectionTabs() {
        BankSellerStartingUiPolicy.Action action = clientValue(() -> BankSellerStartingUiPolicy.nextAction(
                protectionNeedsInventoryTab, protectionNeedsEquipmentTab,
                isVisible(Microbot.getClient().getWidget(InterfaceID.GeOffers.CONTENTS))
                        || isVisible(Microbot.getClient().getWidget(InterfaceID.GeOffers.FRAME)),
                isVisible(Microbot.getClient().getWidget(InterfaceID.Bankmain.UNIVERSE))
                        || isVisible(Microbot.getClient().getWidget(InterfaceID.Bankmain.FRAME))), null);
        if (action == null) {
            protectionSnapshotFailure = "The startup interfaces could not be read";
            return;
        }
        switch (action) {
            case CLOSE_EXCHANGE:
                closeStartingProtectionFrame(InterfaceID.GeOffers.FRAME, "Grand Exchange");
                return;
            case CLOSE_BANK:
                closeStartingProtectionFrame(InterfaceID.Bankmain.FRAME, "bank");
                return;
            case OPEN_INVENTORY:
                Rs2Tab.switchToInventoryTab();
                return;
            case OPEN_EQUIPMENT:
                Rs2Tab.switchToEquipmentTab();
                return;
            default:
                return;
        }
    }

    /** Both 2.6.26 modal frames use child 11 for X; never use offer or bank action buttons. */
    private void closeStartingProtectionFrame(int frameId, String name) {
        boolean clicked = clientValue(() -> {
            Widget frame = Microbot.getClient().getWidget(frameId);
            return isVisible(frame) && clickWidget(frame.getChild(11));
        }, false);
        if (!clicked) {
            protectionSnapshotFailure = "The " + name + " panel is blocking startup slot verification";
        }
        // Do not click tabs or continue the run in this iteration. The next
        // scheduled tick must observe actual containers or visible live slots;
        // an unacknowledged/failed close is never treated as empty equipment.
    }

    /** Login, reconnect and world-hop boundaries invalidate empty-widget readiness. */
    public void resetStartingReadiness() {
        clientValue(() -> {
            startingLoginReadiness.resetTiming();
            pendingStartingInventoryIds = null;
            pendingStartingEquipmentIds = null;
            return true;
        }, false);
    }

    private void finishSellingRun() {
        if (originalOffers.hasPendingRestoration()) {
            restoringOriginalOffers = true;
            completionMessage = finalVerdictMessage() + "; original GE offers restored at their saved prices";
        } else {
            stopRequested = true;
            announce(finalVerdictMessage());
            Microbot.stopPlugin(plugin);
        }
    }

    private void restoreOriginalOffers() {
        // restore() clears the durable journal only on verified success. Keep
        // every original type protected through the subsequent fresh run too.
        protectedOriginalItemIds.addAll(originalOffers.protectedItemIds());
        BankSellerOriginalOffers.Result result = originalOffers.restore();
        if (result == BankSellerOriginalOffers.Result.ERROR) {
            stopSafely("Original GE restoration is still pending: " + originalOffers.errorMessage()
                    + " The saved recovery record has been kept; enable Bank Seller again to recover.");
        } else if (result == BankSellerOriginalOffers.Result.READY) {
            boolean accountMatches = originalOffers.accountStillMatches() && clientValue(() ->
                    startingLoginReadiness.matchesProfile(Microbot.getConfigManager().getRSProfileKey()), false);
            BankSellerRecoveryPolicy.Action action = BankSellerRecoveryPolicy.nextAction(result,
                    originalOffers.isRecoveryOnly(), originalOffers.hasPendingRestoration(),
                    protectionSnapshotted, accountMatches);
            if (action == BankSellerRecoveryPolicy.Action.STOP) {
                stopSafely("Original-offer recovery could not safely hand back to bank selling; stopping.");
                return;
            }
            if (action == BankSellerRecoveryPolicy.Action.CONTINUE_BANK_SELLING) {
                beginSellingAfterRecovery();
                return;
            }
            stopRequested = true;
            announce(completionMessage);
            Microbot.stopPlugin(plugin);
        }
    }

    private BankSellerOriginalOffers createOriginalOffersController() {
        return new BankSellerOriginalOffers(
                (item, quantity, price) -> placeSellOffer(item, quantity, price) == OfferResult.PLACED,
                this::placeOriginalBuyOffer);
    }

    private void beginSellingAfterRecovery() {
        // Never call run() or resnapshot here: recovery has moved inventory.
        // The enabled-state item types and account binding must remain frozen.
        unsellableItemIds.clear();
        skippedItemIds.clear();
        transientFailureCounts.clear();
        tradeRestrictionText = null;
        ownedOfferItemIds.clear();
        placedOffersBySlot.clear();
        foreignSlotOrdinals.clear();
        occupiedSlotsSnapshotted = false;
        bankDrained = false;
        waitingForOfferSlot = false;
        inventoryBatchPending = false;
        pendingReprice = null;
        completedSellingRounds = 0;
        cleanupForOriginalRestoration = false;
        cleanupFailures = 0;
        sessionOffersPlaced = 0;
        sessionStacksRefused = 0;
        completionMessage = null;
        originalOffers = createOriginalOffersController();
        originalOffersPrepared = false;
        restoringOriginalOffers = false;
        announce("Saved original GE offers recovered; continuing with bank selling.");
        // A later tick must save and park the fresh original-offer boundary
        // before any bank liquidation. Its final restoration stops normally.
    }

    private boolean isGeSellable(int itemId) {
        Boolean eligible = geEligibilityByItemId.get(itemId);
        if (eligible == null) {
            eligible = clientValue(() -> BankSellerItemPolicy.geEligibility(itemId,
                    Microbot.getClient()::getItemDefinition), null);
            if (eligible != null) {
                geEligibilityByItemId.put(itemId, eligible);
            }
        }
        return Boolean.TRUE.equals(eligible);
    }

    private boolean hasSellableInventoryItems() {
        return Rs2Inventory.all(this::isSellable).size() > 0;
    }

    /**
     * @return true when this pass was reserved for an owned sold slot, even if
     *         the GE UI attempt failed and must be retried next pass
     */
    private boolean processSoldOffers() {
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

    /** True while at least one non-empty slot still belongs to this run. */
    private boolean hasOwnedActiveOffers() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return true;
            }
            for (int i = 0; i < offers.length; i++) {
                if (offers[i] == null || offers[i].getState() == null) {
                    return true;
                }
                if (isOwnedOffer(i, offers[i])) {
                    return true;
                }
            }
            return false;
        }, true);
    }

    /**
     * Checks only this run's slots. A completed foreign offer must not wake the
     * collector or count as a completed selling round.
     */
    private boolean hasOwnedSoldOffer() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
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

    private boolean hasUnexpectedCancelledOffer() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return false;
            }
            for (int slot = 0; slot < offers.length; slot++) {
                if (isOwnedOffer(slot, offers[slot])
                        && offers[slot].getState() == GrandExchangeOfferState.CANCELLED_SELL) {
                    return true;
                }
            }
            return false;
        }, false);
    }

    private boolean isOwnedOffer(int slotOrdinal, GrandExchangeOffer offer) {
        PlacedOffer placed = placedOffersBySlot.get(slotOrdinal);
        return offer != null
                && placed != null && placed.matches(offer)
                && offer.getItemId() > 0
                && (offer.getState() == GrandExchangeOfferState.SELLING
                    || offer.getState() == GrandExchangeOfferState.SOLD
                    || offer.getState() == GrandExchangeOfferState.CANCELLED_SELL)
                && !foreignSlotOrdinals.contains(slotOrdinal)
                && ownedOfferItemIds.contains(canonicalTradeItemId(offer.getItemId()));
    }

    /** Always collect from the overview, including when an owned sale is still pending. */
    private CollectionResult collectOwnedSoldOffersToBank() {
        if (!overviewCollectionIsSafe()) {
            return CollectionResult.FAILED;
        }
        if (clientValue(() -> BankSellerBatchPolicy.hasOnlyOwnedSales(
                Microbot.getClient().getGrandExchangeOffers(), this::isOwnedOffer), false)) {
            sleepUntil(() -> clientValue(() -> BankSellerBatchPolicy.allOwnedSalesCompleted(
                    Microbot.getClient().getGrandExchangeOffers(), this::isOwnedOffer), false),
                    COLLECTION_BATCH_READY_MS);
        }
        if (Thread.currentThread().isInterrupted()) {
            return CollectionResult.FAILED;
        }
        Map<Integer, Integer> batch = clientValue(() -> BankSellerBatchPolicy.completedOwnedBatch(
                Microbot.getClient().getGrandExchangeOffers(), this::isOwnedOffer), null);
        if (batch != null) {
            // A dispatched click may time out: never fall through to another
            // collection action against possibly changing widgets in this pass.
            return collectOverview(batch, true) ? CollectionResult.BATCH : CollectionResult.FAILED;
        }
        return CollectionResult.FAILED;
    }

    private boolean overviewCollectionIsSafe() {
        if (clientValue(() -> BankSellerBatchPolicy.hasForeignOccupiedOffer(
                Microbot.getClient().getGrandExchangeOffers(), this::isOwnedOffer), false)) {
            stopSafely("Overview Collect would also collect a pre-existing/unrelated offer. "
                    + "Leaving all offers untouched; clear those offers before restarting Bank Seller.");
            return false;
        }
        return !stopRequested;
    }

    private boolean collectOverview(Map<Integer, Integer> expectedItems, boolean toBank) {
        return collectOverview(expectedItems, toBank, false);
    }

    private boolean collectOverview(Map<Integer, Integer> expectedItems, boolean toBank, boolean allowCancelled) {
        if (!overviewCollectionIsSafe() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        boolean[] completedSale = {false};
        boolean dispatched = clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            Map<Integer, Integer> current = toBank && !allowCancelled
                    ? BankSellerBatchPolicy.completedOwnedBatch(offers, this::isOwnedOffer)
                    : BankSellerBatchPolicy.terminalOwnedBatch(offers, this::isOwnedOffer);
            if (!expectedItems.equals(current)) {
                return false;
            }
            Widget collect = Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL);
            if (!isClickable(collect) || isVisible(Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP))) {
                return false;
            }
            Rectangle bounds = collect.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0 || !Rs2UiHelper.isRectangleWithinCanvas(bounds)) {
                return false;
            }
            // Do not use collectAllToBank(): it may deposit the remaining
            // sellable inventory when full before it clicks the GE button.
            completedSale[0] = expectedItems.keySet().stream()
                    .anyMatch(slot -> offers[slot].getState() == GrandExchangeOfferState.SOLD);
            Microbot.doInvoke(new NewMenuEntry()
                    .option(toBank ? "Collect to bank" : "Collect to inventory")
                    .target("")
                    .identifier(toBank ? 2 : 1)
                    .type(MenuAction.CC_OP)
                    .param0(0)
                    .param1(InterfaceID.GeOffers.COLLECTALL)
                    .itemId(-1)
                    .forceLeftClick(false), bounds);
            return true;
        }, false);
        if (!dispatched || !sleepUntil(() -> clientValue(() -> BankSellerBatchPolicy.batchIsEmpty(
                Microbot.getClient().getGrandExchangeOffers(), expectedItems), false), SETUP_WAIT_TIMEOUT_MS)) {
            Microbot.log("[BankSeller] Batch collection was not confirmed; keeping the remaining inventory for retry");
            return false;
        }
        // Count real selling batches, not polling ticks, failed clicks, partial
        // proceeds or cancellation-only recovery. Repricing preserves this age.
        if (completedSale[0]) {
            completedSellingRounds++;
        }
        expectedItems.keySet().forEach(placedOffersBySlot::remove);
        if (!sleepUntil(() -> Rs2GrandExchange.isOpen()
                && !Rs2GrandExchange.isOfferScreenOpen(), SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        sleep(COLLECTION_OVERVIEW_SETTLE_MIN_MS, COLLECTION_OVERVIEW_SETTLE_MAX_MS);
        Microbot.log("[BankSeller] Collected " + expectedItems.size()
                + " offers using overview Collect; selling round " + completedSellingRounds);
        return true;
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

    private void stopSafely(String message) {
        stopRequested = true;
        if (originalOffers != null && originalOffers.hasPendingRestoration()) {
            message += " Original GE offers are saved for recovery on the next enable; no saved terms were discarded.";
        }
        announce(message);
        Microbot.stopPlugin(plugin);
    }

    /** If no buyer exists, return Bank Seller leftovers before restoring the user's original offers. */
    private boolean bankRemainingOwnedOffers() {
        if (!overviewCollectionIsSafe()) {
            return false;
        }
        if (!Rs2GrandExchange.openExchange()
                || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            cleanupAttemptFailed();
            return false;
        }
        for (PlacedOffer placed : new ArrayList<>(placedOffersBySlot.values())) {
            GrandExchangeOfferState state = ownedSlotState(placed.slot, placed.itemId);
            if (state != GrandExchangeOfferState.SELLING) {
                continue;
            }
            if (!abortSlotOffer(placed) || !sleepUntil(() -> {
                GrandExchangeOfferState current = ownedSlotState(placed.slot, placed.itemId);
                return current == GrandExchangeOfferState.CANCELLED_SELL || current == GrandExchangeOfferState.SOLD;
            }, SETUP_WAIT_TIMEOUT_MS)) {
                cleanupAttemptFailed();
                return false;
            }
            cleanupFailures = 0;
            sleep(400, 700);
            return false;
        }
        Map<Integer, Integer> terminal = clientValue(() -> BankSellerBatchPolicy.terminalOwnedBatch(
                Microbot.getClient().getGrandExchangeOffers(), this::isOwnedOffer), null);
        if (terminal != null && !collectOverview(terminal, true, true)) {
            cleanupAttemptFailed();
            return false;
        }
        if (hasOwnedActiveOffers()) {
            cleanupAttemptFailed();
            return false;
        }
        // A full GE may have left part of the current withdrawal carried.
        // Bank it even when every original offer filled and has zero quantity
        // to restore (in that case the restoration controller needs no assets).
        if (!Rs2Inventory.all().isEmpty()) {
            if (!Rs2Bank.openBank() || !sleepUntil(Rs2Bank::isOpen, BANK_OPEN_TIMEOUT_MS)) {
                cleanupAttemptFailed();
                return false;
            }
            Rs2Bank.depositAll();
            if (!sleepUntil(() -> Rs2Inventory.all().isEmpty(), SETUP_WAIT_TIMEOUT_MS)) {
                cleanupAttemptFailed();
                return false;
            }
            Rs2Bank.closeBank();
        }
        cleanupFailures = 0;
        return true;
    }

    private void cleanupAttemptFailed() {
        if (++cleanupFailures >= MAX_TRANSIENT_FAILURES) {
            stopSafely("Unable to safely bank the unsold Bank Seller offers before restoring original offers. "
                    + "Please clear the remaining Bank Seller offers and enable again for recovery.");
        }
    }

    private Set<Integer> emptyOfferSlots() {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return null;
            }
            Set<Integer> empty = new HashSet<>();
            for (int slot = 0; slot < offers.length; slot++) {
                if (offers[slot] == null) {
                    return null;
                }
                if (offers[slot].getState() == GrandExchangeOfferState.EMPTY) {
                    empty.add(slot);
                }
            }
            return empty;
        }, null);
    }

    /** Wait for the newly occupied slot, not just for the inventory to change. */
    private boolean rememberPlacedOffer(Set<Integer> previouslyEmpty, int itemId, int quantity, int price) {
        int[] placedSlot = {-1};
        boolean found = previouslyEmpty != null && sleepUntil(() -> clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return false;
            }
            for (int slot : previouslyEmpty) {
                GrandExchangeOffer offer = offers[slot];
                if (offer != null && !foreignSlotOrdinals.contains(slot)
                        && offer.getItemId() == itemId && offer.getTotalQuantity() == quantity
                        && offer.getPrice() == price
                        && (offer.getState() == GrandExchangeOfferState.SELLING
                            || offer.getState() == GrandExchangeOfferState.SOLD)) {
                    placedSlot[0] = slot;
                    return true;
                }
            }
            return false;
        }, false), SETUP_WAIT_TIMEOUT_MS);
        if (!found) {
            stopSafely("Unable to verify the newly placed offer's slot; leaving offers untouched. Stopping.");
            return false;
        }
        placedOffersBySlot.put(placedSlot[0], new PlacedOffer(GrandExchangeSlots.values()[placedSlot[0]],
                itemId, quantity, price, completedSellingRounds, System.currentTimeMillis()));
        return true;
    }

    private PlacedOffer staleOwnedOffer() {
        long now = System.currentTimeMillis();
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return null;
            }
            for (int slot = 0; slot < offers.length; slot++) {
                PlacedOffer placed = placedOffersBySlot.get(slot);
                GrandExchangeOffer offer = offers[slot];
                if (placed != null && placed.matches(offer) && isOwnedOffer(slot, offer)
                        && offer.getState() == GrandExchangeOfferState.SELLING
                        && BankSellerRepricePolicy.shouldReprice(placed.placedRound, completedSellingRounds,
                                placed.placedAt, now, placed.price)) {
                    return placed;
                }
            }
            return null;
        }, null);
    }

    private boolean allOwnedOffersStalledAtOneGp() {
        long now = System.currentTimeMillis();
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return false;
            }
            boolean foundOwned = false;
            for (int slot = 0; slot < offers.length; slot++) {
                GrandExchangeOffer offer = offers[slot];
                if (!isOwnedOffer(slot, offer)) {
                    continue;
                }
                PlacedOffer placed = placedOffersBySlot.get(slot);
                if (placed == null || !placed.matches(offer)
                        || offer.getState() != GrandExchangeOfferState.SELLING
                        || !BankSellerRepricePolicy.stalledAtOneGp(placed.placedAt, now, placed.price)) {
                    return false;
                }
                foundOwned = true;
            }
            return foundOwned;
        }, false);
    }

    private int recoverySlotsRequired(int itemId, int quantity) {
        int itemSlots = clientValue(() -> {
            ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
            if (definition == null) {
                return Integer.MAX_VALUE;
            }
            ItemComposition note = definition.getLinkedNoteId() > 0
                    ? Microbot.getClient().getItemDefinition(definition.getLinkedNoteId()) : null;
            return definition.isStackable() || (note != null && note.getNote() != -1)
                    ? 1 : Math.max(1, quantity);
        }, Integer.MAX_VALUE);
        return (int) Math.min(Integer.MAX_VALUE, (long) itemSlots + (Rs2Inventory.hasItem(995) ? 0 : 1));
    }

    /** One cancellation at a time leaves room for the returned stack and partial-fill GP. */
    private boolean repriceOneStaleOffer() {
        PlacedOffer stale = staleOwnedOffer();
        if (stale == null) {
            return false;
        }
        int remaining = clientValue(() -> {
            GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[stale.slot.ordinal()];
            return stale.matches(offer) ? Math.max(0, offer.getTotalQuantity() - offer.getQuantitySold()) : -1;
        }, -1);
        if (remaining <= 0) {
            return false;
        }
        if (Rs2Inventory.emptySlotCount() < recoverySlotsRequired(stale.itemId, remaining)) {
            if (hasOwnedSoldOffer()) {
                // Normal overview Collect-to-bank can free a slot first; selling
                // another inventory stack then creates the missing recovery room.
                return false;
            }
            if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                stopSafely("Not enough inventory room to safely recover an unsold offer and its coins. "
                        + "Leaving it listed. Stopping.");
                return true;
            }
            // Selling another inventory item first will create recovery space.
            return false;
        }
        pendingReprice = new PendingReprice(stale, BankSellerRepricePolicy.reducedPrice(stale.price),
                inventoryTradeQuantity(stale.itemId));
        announce("Unsold offer: lowering " + stale.itemId + " from " + stale.price
                + "gp to " + pendingReprice.nextPrice + "gp (90% price drop)");
        resumePendingReprice();
        return true;
    }

    private void resumePendingReprice() {
        PendingReprice pending = pendingReprice;
        if (pending == null || stopRequested || Thread.currentThread().isInterrupted()) {
            return;
        }
        if (!performPendingReprice(pending) && !stopRequested && pendingReprice == pending) {
            if (++pending.failures >= MAX_TRANSIENT_FAILURES) {
                stopSafely("Unable to safely recover/re-list the reduced-price offer after three attempts. "
                        + "Leaving remaining offers/items untouched. Stopping.");
            }
        }
    }

    private boolean performPendingReprice(PendingReprice pending) {
        PlacedOffer original = pending.original;
        if (!Rs2GrandExchange.openExchange()
                || !sleepUntil(Rs2GrandExchange::isOpen, EXCHANGE_OPEN_TIMEOUT_MS)) {
            return false;
        }
        if (isSetupVisible()) {
            abortOfferSetup();
            if (!sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS)) {
                return false;
            }
        }
        if (!overviewCollectionIsSafe()) {
            return false;
        }
        if (!pending.collected) {
            GrandExchangeOfferState state = ownedSlotState(original.slot, original.itemId);
            if (state == GrandExchangeOfferState.SELLING) {
                if (!abortSlotOffer(original)
                        || !sleepUntil(() -> {
                            GrandExchangeOfferState current = ownedSlotState(original.slot, original.itemId);
                            return current == GrandExchangeOfferState.CANCELLED_SELL
                                    || current == GrandExchangeOfferState.SOLD;
                        }, SETUP_WAIT_TIMEOUT_MS)) {
                    return false;
                }
                sleep(400, 700);
            }
            Map<Integer, Integer> recovery = clientValue(() -> BankSellerBatchPolicy.terminalOwnedBatch(
                    Microbot.getClient().getGrandExchangeOffers(), this::isOwnedOffer), null);
            if (recovery != null && recovery.containsKey(original.slot.ordinal())) {
                int remaining = clientValue(() -> {
                    GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[original.slot.ordinal()];
                    return original.matches(offer)
                            ? Math.max(0, offer.getTotalQuantity() - offer.getQuantitySold()) : -1;
                }, -1);
                if (remaining < 0) {
                    return false;
                }
                pending.remainingToRecover = remaining;
                if (!collectOverview(recovery, false)) {
                    return false;
                }
            } else if (pending.remainingToRecover < 0 || !clientValue(() -> {
                GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
                return offers != null && offers.length == GrandExchangeSlots.values().length
                        && offers[original.slot.ordinal()] != null
                        && offers[original.slot.ordinal()].getState() == GrandExchangeOfferState.EMPTY;
            }, false)) {
                return false;
            }
            if (!sleepUntil(() -> (long) inventoryTradeQuantity(original.itemId) - pending.inventoryBefore
                    >= pending.remainingToRecover, SETUP_WAIT_TIMEOUT_MS)) {
                return false;
            }
            pending.collected = true;
            pending.failures = 0;
        }
        if (pending.remainingToRecover == 0) {
            // The original offer filled during cancellation; only GP was collected.
            pendingReprice = null;
            return true;
        }
        Rs2ItemModel recovered = Rs2Inventory.all(item -> canonicalTradeItemId(item.getId()) == original.itemId)
                .stream().findFirst().orElse(null);
        int quantity = inventoryTradeQuantity(original.itemId);
        Set<Integer> emptyBefore = emptyOfferSlots();
        if (recovered == null || quantity <= 0 || emptyBefore == null || emptyBefore.isEmpty()) {
            return false;
        }
        OfferResult result = placeSellOffer(recovered, quantity, pending.nextPrice);
        if (result != OfferResult.PLACED && inventoryTradeQuantity(original.itemId) != 0) {
            return false;
        }
        sessionOffersPlaced++;
        ownedOfferItemIds.add(original.itemId);
        if (!rememberPlacedOffer(emptyBefore, original.itemId, quantity, pending.nextPrice)) {
            return false;
        }
        pendingReprice = null;
        waitingForOfferSlot = false;
        return true;
    }

    /**
     * Slot-targeted variant of Rs2GrandExchange.abortOffer: aborts exactly this
     * slot and collects nothing. (abortOffer itself is name-based and ends in
     * a collect-all, which would hit unrelated offers.)
     */
    private boolean abortSlotOffer(PlacedOffer original) {
        return clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int slot = original.slot.ordinal();
            if (offers == null || offers.length != GrandExchangeSlots.values().length
                    || !original.matches(offers[slot]) || !isOwnedOffer(slot, offers[slot])
                    || offers[slot].getState() != GrandExchangeOfferState.SELLING) {
                return false;
            }
            Widget widget = Microbot.getClient().getWidget(GE_WIDGET_GROUP, GE_FIRST_SLOT_CHILD + slot);
            if (!isClickable(widget)) {
                return false;
            }
            Rectangle bounds = widget.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0 || !Rs2UiHelper.isRectangleWithinCanvas(bounds)) {
                return false;
            }
            Microbot.doInvoke(new NewMenuEntry()
                    .option("Abort offer")
                    .target("")
                    .identifier(2)
                    .type(MenuAction.CC_OP)
                    .param0(2)
                    .param1(widget.getId())
                    .itemId(-1)
                    .forceLeftClick(false), bounds);
            return true;
        }, false);
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
            int quantityBefore = inventoryTradeQuantity(item.getId());
            Rs2Bank.withdrawAll(item.getId());
            boolean got = sleepUntil(() -> inventoryTradeQuantity(item.getId()) > quantityBefore);
            if (!got) {
                // Items without a noted form ("This item cannot be withdrawn as
                // a note") still need to be sold - withdraw them unnoted
                Rs2Bank.setWithdrawAsItem();
                Rs2Bank.withdrawAll(item.getId());
                sleepUntil(() -> inventoryTradeQuantity(item.getId()) > quantityBefore);
                Rs2Bank.setWithdrawAsNote();
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        return bankHasSellables;
    }

    private void sellInventory() {
        // Group by item so the whole stack of an item is sold in a single offer.
        Map<Integer, List<Rs2ItemModel>> itemsById = Rs2Inventory.all(this::isSellable).stream()
                .collect(Collectors.groupingBy(item -> canonicalTradeItemId(item.getId()),
                        LinkedHashMap::new, Collectors.toList()));
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
            if (stopRequested || Thread.currentThread().isInterrupted()) {
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
            // Collection may have just completed the second selling round for
            // an older offer. Reprice it before filling the newly freed slot.
            if (stopRequested || pendingReprice != null || repriceOneStaleOffer()) {
                break;
            }
            if (!releaseEmptyForeignSlots()) {
                // Do not place an offer until a reliable client read establishes
                // which formerly foreign slots are now safe for Bank Seller to own.
                break;
            }

            int guidePrice = Rs2GrandExchange.getPrice(entry.getKey());
            // Initial offers start at half the active price. Stale offers are
            // recovered and re-listed separately at 10% of their prior asking price.
            int price = Math.max(1, guidePrice / 2);
            Set<Integer> emptyBefore = emptyOfferSlots();
            if (emptyBefore == null || emptyBefore.isEmpty()) {
                break;
            }

            OfferResult result = placeSellOffer(item, quantity, price);
            int remaining = inventoryTradeQuantity(item.getId());

            if (result == OfferResult.PLACED || remaining == 0) {
                // Offer confirmed - the items are gone even if the UI flow hiccuped
                sessionOffersPlaced++;
                ownedOfferItemIds.add(entry.getKey());
                transientFailureCounts.remove(entry.getKey());
                if (!rememberPlacedOffer(emptyBefore, entry.getKey(), quantity, price)) {
                    break;
                }
            } else if (result == OfferResult.RESTRICTED) {
                // The GE explicitly refused the item (F2P trade restriction) - bank it for good
                unsellableItemIds.add(entry.getKey());
                failedItems.addAll(entry.getValue());
                sessionStacksRefused++;
                transientFailureCounts.remove(entry.getKey());
                Microbot.log("[BankSeller] GE refused " + item.getName() + "; skipping it for this run");
                // The refused setup can retain its old item and dead Confirm
                // button. Start the next stack from a clean overview.
                abortOfferSetup();
                sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS);
            } else {
                // Transient UI failure (the setup never opened, a widget timed
                // out, ...) - the item stays sellable, is NOT flagged unsellable
                // and is retried on a later pass. Only after repeated failures
                // it is parked so the run can still finish.
                int failures = transientFailureCounts.merge(entry.getKey(), 1, Integer::sum);
                if (failures >= MAX_TRANSIENT_FAILURES) {
                    skippedItemIds.add(entry.getKey());
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

        if (!stopRequested) {
            depositUnsellableItems(failedItems);
        }
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
        if (stopRequested || Thread.currentThread().isInterrupted() || !originalOffers.accountStillMatches()) {
            return OfferResult.FAILED;
        }
        if (isSetupVisible()) {
            abortOfferSetup();
            if (!sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS)) {
                return OfferResult.FAILED;
            }
        }
        if (!Rs2Inventory.interact(item.getId(), "Offer")) {
            return OfferResult.FAILED;
        }

        // Wait for this item's identity before reading rejection text, so a
        // previous item's notice cannot be attributed to this stack.
        // A timeout here is transient (slow client/UI) and is retried by the
        // caller - it never flags the item unsellable.
        if (!sleepUntil(() -> isSetupVisible() && sameTradeItem(resolveSetupItemId(item.getId()), item.getId()),
                SETUP_WAIT_TIMEOUT_MS)) {
            return OfferResult.FAILED;
        }

        // A red trade-restriction notice means this item is genuinely refused.
        String restrictionNotice = findTradeRestrictionNotice();
        if (restrictionNotice != null) {
            tradeRestrictionText = restrictionNotice;
            return OfferResult.RESTRICTED;
        }

        if (!enterPrice(price)) {
            restrictionNotice = findTradeRestrictionNotice();
            if (restrictionNotice != null) {
                tradeRestrictionText = restrictionNotice;
                return OfferResult.RESTRICTED;
            }
            abortOfferSetup();
            return OfferResult.FAILED;
        }

        if (!enterFullQuantity(quantity)) {
            restrictionNotice = findTradeRestrictionNotice();
            if (restrictionNotice != null) {
                tradeRestrictionText = restrictionNotice;
                return OfferResult.RESTRICTED;
            }
            abortOfferSetup();
            return OfferResult.FAILED;
        }

        if (!sleepUntil(() -> offerSetupMatches(item.getId(), quantity, price), SETUP_WAIT_TIMEOUT_MS)) {
            abortOfferSetup();
            return OfferResult.FAILED;
        }
        boolean confirmed = clientValue(() -> originalOffers.accountStillMatches()
                && offerSetupMatches(item.getId(), quantity, price) && clickWidget(findConfirmButton()), false);
        if (!confirmed) {
            restrictionNotice = findTradeRestrictionNotice();
            if (restrictionNotice != null) {
                tradeRestrictionText = restrictionNotice;
                return OfferResult.RESTRICTED;
            }
            abortOfferSetup();
            return OfferResult.FAILED;
        }

        // The GE warns when the price is far from the guide price (often, at
        // instant-sell prices) - keep accepting it until the stack leaves inventory
        long confirmDeadline = System.currentTimeMillis() + CONFIRM_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < confirmDeadline) {
            if (Thread.currentThread().isInterrupted()) {
                return OfferResult.FAILED;
            }
            restrictionNotice = findTradeRestrictionNotice();
            if (restrictionNotice != null) {
                tradeRestrictionText = restrictionNotice;
                return OfferResult.RESTRICTED;
            }
            boolean warningUp = isGePriceWarningVisible();
            // Closing the setup alone is not proof of placement: a refusal can
            // close it while the whole stack remains in the inventory.
            if (inventoryTradeQuantity(item.getId()) == 0 && !warningUp) {
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

    /** Restore a buy only after checking its selected ID and exact setup terms. */
    private boolean placeOriginalBuyOffer(GrandExchangeSlots slot, int itemId, String itemName,
                                          int quantity, int price) {
        if (!originalOffers.accountStillMatches() || stopRequested || Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (isSetupVisible()) {
            abortOfferSetup();
            if (!sleepUntil(() -> !isSetupVisible(), SETUP_WAIT_TIMEOUT_MS)) {
                return false;
            }
        }
        Widget buyButton = clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || offers.length != 8 || offers[slot.ordinal()] == null
                    || offers[slot.ordinal()].getState() != GrandExchangeOfferState.EMPTY) {
                return null;
            }
            Widget slotWidget = Microbot.getClient().getWidget(GE_WIDGET_GROUP,
                    GE_FIRST_SLOT_CHILD + slot.ordinal());
            return slotWidget == null ? null : slotWidget.getChild(0);
        }, null);
        if (!clickWidget(buyButton) || !sleepUntil(() -> isSetupVisible()
                && Rs2Widget.hasWidget("Start typing the name of an item to search for it"), SETUP_WAIT_TIMEOUT_MS)) {
            return false;
        }
        // Never take the core's name-only previous-search shortcut. A result
        // may share a label with a different ID; read back its ID before buying.
        Rs2Keyboard.typeString(itemName);
        if (!sleepUntil(() -> clientValue(() -> Rs2GrandExchange.getSearchResultWidget(itemName, true) != null,
                false), SETUP_WAIT_TIMEOUT_MS)) {
            abortOfferSetup();
            return false;
        }
        boolean selected = clientValue(() -> {
            org.apache.commons.lang3.tuple.Pair<Widget, Integer> result =
                    Rs2GrandExchange.getSearchResultWidget(itemName, true);
            if (result == null || !isClickable(result.getLeft()) || !originalOffers.accountStillMatches()) {
                return false;
            }
            Rs2Widget.clickWidgetFast(result.getLeft(), result.getRight(), 1);
            return true;
        }, false);
        if (!selected || !sleepUntil(() -> resolveSetupItemId(itemId) > 0 && clientValue(() ->
                Microbot.getClient().getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == itemId, false),
                SETUP_WAIT_TIMEOUT_MS)) {
            abortOfferSetup();
            return false;
        }
        if (!enterPrice(price) || !enterExactQuantity(quantity)
                || !sleepUntil(() -> offerSetupMatches(itemId, quantity, price), SETUP_WAIT_TIMEOUT_MS)) {
            abortOfferSetup();
            return false;
        }
        boolean confirmed = clientValue(() -> {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (!originalOffers.accountStillMatches() || !offerSetupMatches(itemId, quantity, price)
                    || Microbot.getClient().getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) != itemId
                    || offers == null || offers.length != 8 || offers[slot.ordinal()] == null
                    || offers[slot.ordinal()].getState() != GrandExchangeOfferState.EMPTY) {
                return false;
            }
            return clickWidget(findConfirmButton());
        }, false);
        if (!confirmed) {
            abortOfferSetup();
            return false;
        }
        long deadline = System.currentTimeMillis() + CONFIRM_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (stopRequested || Thread.currentThread().isInterrupted() || !originalOffers.accountStillMatches()) {
                return false;
            }
            if (isGePriceWarningVisible()) {
                acceptGePriceWarning();
            } else if (!isSetupVisible()) {
                return true; // Controller independently verifies the exact live order.
            }
            sleep(200, 350);
        }
        return false;
    }

    private boolean offerSetupMatches(int itemId, int quantity, int price) {
        return clientValue(() -> {
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            Widget container = Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            Widget unitPrice = container == null ? null : container.getChild(41);
            return isVisible(setup) && containsItemId(setup, itemId, 0) && isVisible(unitPrice)
                    && Microbot.getClient().getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) == quantity
                    && BankSellerOfferSetupPolicy.unitPrice(unitPrice.getText()) == price;
        }, false);
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

        return enterExactQuantity(quantity);
    }

    private boolean enterExactQuantity(int quantity) {
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
            if (stopRequested || pendingReprice != null || Thread.currentThread().isInterrupted()) {
                return false;
            }
            if (hasOwnedSoldOffer()) {
                CollectionResult collected = collectOwnedSoldOffersToBank();
                if (collected == CollectionResult.BATCH && Rs2GrandExchange.getAvailableSlotsCount() > 0) {
                    // The overview stayed open and every expected slot is now
                    // empty: carry on with the fourth, fifth, ... inventory stack.
                    waitingForOfferSlot = false;
                    return true;
                }
                if (collected == CollectionResult.FAILED) {
                    // Collection failed. Leave the GE and retry on a
                    // later pass instead of spinning while the SOLD state remains.
                    waitForSafeOfferSlot();
                }
                return false;
            }
            if (repriceOneStaleOffer()) {
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
                    || hasOwnedSoldOffer() || staleOwnedOffer() != null, EXCHANGE_OPEN_TIMEOUT_MS)) {
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
            if (offers == null || offers.length != GrandExchangeSlots.values().length) {
                return false;
            }
            foreignSlotOrdinals.removeIf(slotOrdinal -> {
                if (slotOrdinal < 0 || slotOrdinal >= offers.length) {
                    return false;
                }
                GrandExchangeOffer offer = offers[slotOrdinal];
                return offer != null && offer.getState() == GrandExchangeOfferState.EMPTY;
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
        Integer knownId = canonicalItemIds.get(itemId);
        if (knownId != null) {
            return knownId;
        }
        int unnotedId = clientValue(() -> BankSellerItemPolicy.canonicalItemId(itemId,
                Microbot.getClient()::getItemDefinition), -1);
        if (unnotedId > 0) {
            canonicalItemIds.put(itemId, unnotedId);
        }
        return unnotedId;
    }

    private boolean sameTradeItem(int leftItemId, int rightItemId) {
        if (leftItemId <= 0 || rightItemId <= 0) {
            return false;
        }
        int leftCanonical = canonicalTradeItemId(leftItemId);
        return leftCanonical > 0 && leftCanonical == canonicalTradeItemId(rightItemId);
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
