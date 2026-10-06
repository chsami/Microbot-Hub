package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.bankseller.BankSellerOriginalOffersJournal.Stage;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Parks original orders before any liquidation and restores their unfilled portions afterward.
 * All irreversible UI actions have durable intent records and independently checked results.
 */
final class BankSellerOriginalOffers {
    enum Result { WAITING, READY, ERROR }

    @FunctionalInterface
    interface SellPlacer {
        boolean place(Rs2ItemModel item, int quantity, int price);
    }

    @FunctionalInterface
    interface BuyPlacer {
        boolean place(GrandExchangeSlots slot, int itemId, String itemName, int quantity, int price);
    }

    private static final int WAIT_MS = 5_000;
    private final SellPlacer sellPlacer;
    private final BuyPlacer buyPlacer;
    private BankSellerOriginalOffersJournal.Journal journal;
    private Path journalPath;
    private String owner;
    private boolean initialized;
    private boolean recoveryOnly;
    private String error;
    private int preparationRetries;
    private int restorationRetries;

    BankSellerOriginalOffers(SellPlacer placer) {
        this(placer, null);
    }

    BankSellerOriginalOffers(SellPlacer placer, BuyPlacer buyer) {
        this.sellPlacer = placer;
        this.buyPlacer = buyer;
    }

    boolean isRecoveryOnly() {
        return recoveryOnly;
    }

    boolean hasPendingRestoration() {
        return journal != null;
    }

    boolean accountStillMatches() {
        return initialized && sameAccount();
    }

    Set<Integer> protectedItemIds() {
        return Collections.unmodifiableSet(BankSellerOriginalOffersJournal.protectedIds(journal));
    }

    String errorMessage() {
        return error == null ? "Original-offer recovery is not ready" : error;
    }

    Result prepare() {
        if (error != null) {
            return Result.ERROR;
        }
        if (!Microbot.isLoggedIn()) {
            return Result.WAITING;
        }
        if (!initialized) {
            return initialize();
        }
        if (!sameAccount()) {
            return fail("The account changed; the saved original offers were not touched");
        }
        if (journal == null) {
            return Result.READY;
        }
        GrandExchangeOffer[] offers = offers();
        if (offers == null) {
            return Result.WAITING;
        }
        for (BankSellerOriginalOffersJournal.Entry entry : journal.offers) {
            if (entry.stage == Stage.ORIGINAL || entry.stage == Stage.CANCEL_INTENT) {
                GrandExchangeOffer live = offers[entry.slot];
                if (!entry.originalMatches(live)) {
                    return fail("An original offer changed before its cancellation could be verified; "
                            + "saved recovery instructions were retained");
                }
                if (BankSellerOriginalOffersJournal.isTerminal(live.getState())) {
                    entry.recordTerminal(live, entry.stage == Stage.CANCEL_INTENT);
                    preparationRetries = 0;
                    return save() ? Result.WAITING : Result.ERROR;
                }
                if (recoveryOnly) {
                    // A persisted intent does not prove cancellation happened. An identical
                    // still-active order already satisfies restoration and must remain active.
                    entry.stage = Stage.UNCHANGED;
                    entry.restoreQuantity = 0;
                    return save() ? Result.WAITING : Result.ERROR;
                }
                if (!openOverview()) {
                    return retryPreparation("Could not open the GE to park the original offers");
                }
                entry.stage = Stage.CANCEL_INTENT;
                if (!save()) {
                    return Result.ERROR;
                }
                if (!abortOriginal(entry)) {
                    return retryPreparation("Original-offer cancellation was not dispatched safely");
                }
                if (sleepUntil(() -> {
                    GrandExchangeOffer[] after = offers();
                    return after != null && entry.originalMatches(after[entry.slot])
                            && BankSellerOriginalOffersJournal.isTerminal(after[entry.slot].getState());
                }, WAIT_MS)) {
                    preparationRetries = 0;
                    return Result.WAITING;
                }
                return retryPreparation("The original offer did not acknowledge cancellation");
            }
        }
        boolean needsCollection = false;
        for (BankSellerOriginalOffersJournal.Entry entry : journal.offers) {
            if (entry.stage != Stage.CANCELLED && entry.stage != Stage.COLLECT_INTENT) {
                continue;
            }
            GrandExchangeOffer live = offers[entry.slot];
            if (live.getState() == GrandExchangeOfferState.EMPTY && entry.stage == Stage.COLLECT_INTENT) {
                entry.stage = Stage.PARKED;
                if (!save()) {
                    return Result.ERROR;
                }
                preparationRetries = 0;
                return Result.WAITING;
            }
            if (!entry.terminalMatches(live)) {
                return fail("The cancelled original offer changed before collection; saved recovery was retained");
            }
            needsCollection = true;
        }
        if (!needsCollection) {
            return Result.READY;
        }
        if (!safeOriginalCollection(offers)) {
            return fail("Overview Collect would touch an unrelated or completed preserved offer; "
                    + "saved recovery was retained for reconciliation without repeating a trade");
        }
        if (!openOverview()) {
            return retryPreparation("Could not open the GE to collect the parked original offers");
        }
        for (BankSellerOriginalOffersJournal.Entry entry : journal.offers) {
            if (entry.stage == Stage.CANCELLED) {
                entry.stage = Stage.COLLECT_INTENT;
            }
        }
        if (!save()) {
            return Result.ERROR;
        }
        if (!collectOriginalsToBank()) {
            return retryPreparation("Collect-to-bank could not be verified; ensure the bank has space");
        }
        // Next pass verifies every cancelled slot EMPTY before normal selling starts.
        return retryPreparation("The original offers have not all collected; ensure the bank has space");
    }

    /** Called only after Bank Seller's own offers have been settled; never resumes liquidation. */
    Result restore() {
        if (error != null) {
            return Result.ERROR;
        }
        if (!initialized || !Microbot.isLoggedIn()) {
            return Result.WAITING;
        }
        if (!sameAccount()) {
            return fail("The account changed; saved original-offer restoration was not attempted");
        }
        if (journal == null) {
            return Result.READY;
        }
        GrandExchangeOffer[] offers = offers();
        if (offers == null) {
            return Result.WAITING;
        }
        for (BankSellerOriginalOffersJournal.Entry entry : journal.offers) {
            if (entry.stage == Stage.RESTORE_INTENT) {
                if (entry.restoredMatches(offers[entry.restoreSlot])) {
                    entry.stage = Stage.RESTORED;
                    return save() ? Result.WAITING : Result.ERROR;
                }
                return fail("A previous original-offer placement has an uncertain result. "
                        + "Not repeating it because that could duplicate a trade; saved recovery was retained");
            }
            if (entry.stage == Stage.RESTORED || entry.stage == Stage.UNCHANGED || entry.stage == Stage.SETTLED) {
                if (entry.stage != Stage.SETTLED && !entry.preservedOfferMatches(
                        offers[entry.stage == Stage.RESTORED ? entry.restoreSlot : entry.slot])) {
                    return fail("A preserved original offer changed during restoration; "
                            + "saved recovery was retained and no offer will be repeated");
                }
                continue;
            }
            if (entry.stage != Stage.PARKED) {
                return fail("Original offers still need safe collection before restoration");
            }
            if (entry.restoreQuantity == 0) {
                entry.stage = Stage.SETTLED;
                return save() ? Result.WAITING : Result.ERROR;
            }
            int slot = availableRestoreSlot(entry.slot, offers);
            if (slot < 0) {
                return fail("No safe GE slot is available to restore an original offer. "
                        + "Saved recovery was retained; clear a slot and re-enable Bank Seller");
            }
            String cannotRestore = restorationBlocker(entry);
            if (cannotRestore != null) {
                return fail(cannotRestore);
            }
            if (!prepareRestorationAssets(entry)) {
                if (error == null && ++restorationRetries >= 3) {
                    return fail("Could not prepare the banked original-offer assets after three attempts; "
                            + "saved recovery was retained");
                }
                return error != null ? Result.ERROR : Result.WAITING;
            }
            if (!openOverview()) {
                if (++restorationRetries >= 3) {
                    return fail("Could not open the GE for original-offer restoration after three attempts; "
                            + "saved recovery was retained");
                }
                return Result.WAITING;
            }
            GrandExchangeOffer[] before = offers();
            if (before == null || before[slot].getState() != GrandExchangeOfferState.EMPTY
                    || !sameAccount()) {
                return fail("The intended restoration slot or account changed before placing the offer");
            }
            entry.restoreSlot = slot;
            entry.stage = Stage.RESTORE_INTENT;
            if (!save()) {
                return Result.ERROR;
            }
            boolean dispatched;
            if (entry.buy) {
                dispatched = buyPlacer != null && buyPlacer.place(GrandExchangeSlots.values()[slot],
                        entry.itemId, entry.itemName, entry.restoreQuantity, (int) entry.price);
            } else {
                Rs2ItemModel item = Rs2Inventory.all(value -> canonicalId(value.getId()) == entry.itemId)
                        .stream().findFirst().orElse(null);
                dispatched = item != null && sellPlacer != null
                        && sellPlacer.place(item, entry.restoreQuantity, (int) entry.price);
            }
            boolean verified = sleepUntil(() -> {
                GrandExchangeOffer[] current = offers();
                return current != null && entry.restoredMatches(current[slot]);
            }, WAIT_MS);
            if (!verified) {
                return fail("Could not verify the exact original " + (entry.buy ? "buy" : "sell")
                        + " offer after " + (dispatched ? "placement" : "the placement attempt")
                        + ". Not repeating a potentially completed trade; recovery was retained");
            }
            entry.stage = Stage.RESTORED;
            restorationRetries = 0;
            return save() ? Result.WAITING : Result.ERROR;
        }
        // Later placements or external edits may have changed an earlier restored
        // order. Re-read every obligation together immediately before retiring recovery.
        Boolean verified = clientValue(() -> {
            GrandExchangeOffer[] current = Microbot.getClient().getGrandExchangeOffers();
            if (!ownerMatchesOnClientThread() || !validOffers(current)) {
                return false;
            }
            for (BankSellerOriginalOffersJournal.Entry entry : journal.offers) {
                if ((entry.stage == Stage.RESTORED || entry.stage == Stage.UNCHANGED)
                        && !entry.preservedOfferMatches(current[entry.stage == Stage.RESTORED
                            ? entry.restoreSlot : entry.slot])) {
                    return false;
                }
            }
            return true;
        });
        if (!Boolean.TRUE.equals(verified)) {
            return fail("A preserved original offer changed before final restoration verification; "
                    + "saved recovery was retained and no offer will be repeated");
        }
        try {
            Files.delete(journalPath);
            journal = null;
            return Result.READY;
        } catch (IOException exception) {
            return fail("The offers were restored, but the recovery file could not be cleared; "
                    + "no new liquidation will start until it is cleared safely");
        }
    }

    private Result initialize() {
        String key = currentOwner();
        if (key == null) {
            return Result.WAITING;
        }
        owner = key;
        journalPath = RuneLite.RUNELITE_DIR.toPath().resolve("bankseller-original-offers")
                .resolve(owner + ".json");
        if (Files.exists(journalPath)) {
            try {
                journal = BankSellerOriginalOffersJournal.read(journalPath, owner);
                recoveryOnly = true;
                initialized = true;
                Microbot.log("[BankSeller] Recovering saved original GE offers before starting new bank selling");
                return Result.WAITING;
            } catch (IOException exception) {
                return fail("The original-offer recovery file could not be validated. "
                        + "It was retained and no GE offers were changed");
            }
        }
        GrandExchangeOffer[] current = offers();
        if (current == null) {
            return Result.WAITING;
        }
        BankSellerOriginalOffersJournal.Journal snapshot = new BankSellerOriginalOffersJournal.Journal();
        snapshot.owner = owner;
        for (int slot = 0; slot < current.length; slot++) {
            GrandExchangeOffer offer = current[slot];
            if (offer.getState() == GrandExchangeOfferState.EMPTY) {
                snapshot.initialEmptySlots.add(slot);
                continue;
            }
            BankSellerOriginalOffersJournal.Entry entry = new BankSellerOriginalOffersJournal.Entry();
            entry.slot = slot;
            entry.itemId = offer.getItemId();
            entry.totalQuantity = offer.getTotalQuantity();
            entry.initialFilled = offer.getQuantitySold();
            entry.price = offer.getPrice();
            entry.spent = offer.getSpent();
            entry.originalState = offer.getState();
            entry.buy = BankSellerOriginalOffersJournal.sameSide(offer.getState(), true);
            entry.itemName = clientValue(() -> {
                ItemComposition item = Microbot.getClient().getItemDefinition(entry.itemId);
                return item == null ? null : item.getName();
            });
            if (entry.itemName == null) {
                return Result.WAITING;
            }
            String blocker = restorationBlocker(entry);
            if (blocker != null) {
                return fail(blocker + "; no original offers were cancelled");
            }
            snapshot.offers.add(entry);
        }
        initialized = true;
        if (snapshot.offers.isEmpty()) {
            return Result.READY;
        }
        try {
            BankSellerOriginalOffersJournal.validate(snapshot, owner);
        } catch (IOException invalid) {
            return fail(invalid.getMessage() + "; no original offers were cancelled");
        }
        journal = snapshot;
        if (!save()) {
            return Result.ERROR;
        }
        // Persist the whole boundary before the first cancellation, and give the
        // caller a pass to freeze original-item protection before any bank input.
        return Result.WAITING;
    }

    private String restorationBlocker(BankSellerOriginalOffersJournal.Entry entry) {
        if (entry.slot >= maxSlots()) {
            return "An original offer occupies a member-only slot that cannot be restored on this world";
        }
        if (entry.price <= 0 || entry.price > Integer.MAX_VALUE) {
            return "An original offer's unit price is outside the supported exact-price range";
        }
        if (entry.wasActive()) {
            if (entry.buy && buyPlacer == null) {
                return "An exact-item original-buy restoration callback is unavailable";
            }
            ItemComposition definition = clientValue(() -> Microbot.getClient().getItemDefinition(entry.itemId));
            if (definition == null) {
                return "An original item's definition is unavailable for safe restoration";
            }
            Boolean eligible = clientValue(() -> BankSellerItemPolicy.geEligibility(entry.itemId,
                    Microbot.getClient()::getItemDefinition));
            if (!Boolean.TRUE.equals(eligible)) {
                return "An original item is no longer eligible to be re-listed on the Grand Exchange";
            }
            int quantity = entry.finalFilled >= 0 ? entry.restoreQuantity
                    : entry.totalQuantity - entry.initialFilled;
            if (!entry.buy && !canWithdrawStack(entry.itemId) && quantity > 28) {
                return "An original sell offer cannot fit in inventory as a stack or notes for exact restoration";
            }
        }
        if (entry.wasActive() && entry.buy && maxSlots() == 3) {
            Boolean members = clientValue(() -> {
                ItemComposition item = Microbot.getClient().getItemDefinition(entry.itemId);
                return item == null ? null : item.isMembers();
            });
            if (members == null || members) {
                return "An original members-item buy cannot be safely restored on a free-to-play world";
            }
        }
        return null;
    }

    /** Bank all carried items, never equipment; withdraw only this original order's exact assets. */
    private boolean prepareRestorationAssets(BankSellerOriginalOffersJournal.Entry entry) {
        if (!Rs2Bank.openBank() || !sleepUntil(Rs2Bank::isOpen, WAIT_MS)) {
            return false;
        }
        if (!Rs2Inventory.all().isEmpty()) {
            Rs2Bank.depositAll();
            if (!sleepUntil(() -> Rs2Inventory.all().isEmpty(), WAIT_MS)) {
                fail("Inventory could not be banked safely for exact original-offer restoration; "
                        + "ensure the bank has space");
                return false;
            }
        }
        int assetId;
        int assetQuantity;
        if (entry.buy) {
            long cost;
            try {
                cost = Math.multiplyExact(entry.price, (long) entry.restoreQuantity);
            } catch (ArithmeticException overflow) {
                fail("Original buy funding exceeds the supported coin-stack range");
                return false;
            }
            if (cost <= 0 || cost > Integer.MAX_VALUE) {
                fail("Original buy funding exceeds the supported coin-stack range");
                return false;
            }
            assetId = 995;
            assetQuantity = (int) cost;
        } else {
            assetId = entry.itemId;
            assetQuantity = entry.restoreQuantity;
        }
        if (Rs2Bank.count(assetId) < assetQuantity) {
            fail("The bank no longer contains the reserved " + (entry.buy ? "coins" : "original items")
                    + " needed to restore an offer; recovery was retained");
            return false;
        }
        boolean asNotes = !entry.buy && canWithdrawNotes(entry.itemId);
        if (!(asNotes ? Rs2Bank.setWithdrawAsNote() : Rs2Bank.setWithdrawAsItem())) {
            return false;
        }
        boolean withdrawn = Rs2Bank.withdrawX(assetId, assetQuantity);
        if (!withdrawn || !sleepUntil(() -> inventoryQuantity(assetId) == assetQuantity, WAIT_MS)) {
            fail("Could not withdraw the exact reserved quantity for original-offer restoration");
            return false;
        }
        // More than the reserved quantity would let a sell callback's All button
        // consume unrelated copies. Refuse unless this one exact stack is carried.
        if (inventoryQuantity(assetId) != assetQuantity || !Rs2Bank.closeBank()
                || !sleepUntil(() -> !Rs2Bank.isOpen(), WAIT_MS)) {
            return false;
        }
        return true;
    }

    private int inventoryQuantity(int itemId) {
        long total = 0;
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (canonicalId(item.getId()) == itemId) {
                total += Math.max(0, item.getQuantity());
            }
        }
        return total > Integer.MAX_VALUE ? -1 : (int) total;
    }

    private int canonicalId(int id) {
        Integer canonical = clientValue(() -> BankSellerItemPolicy.canonicalItemId(id,
                Microbot.getClient()::getItemDefinition));
        return canonical == null ? -1 : canonical;
    }

    private boolean canWithdrawNotes(int itemId) {
        Boolean canNote = clientValue(() -> {
            ItemComposition item = Microbot.getClient().getItemDefinition(itemId);
            if (item == null || item.getLinkedNoteId() <= 0) {
                return false;
            }
            ItemComposition note = Microbot.getClient().getItemDefinition(item.getLinkedNoteId());
            return note != null && note.getNote() != -1 && note.getLinkedNoteId() == itemId;
        });
        return Boolean.TRUE.equals(canNote);
    }

    private boolean canWithdrawStack(int itemId) {
        Boolean stackable = clientValue(() -> {
            ItemComposition item = Microbot.getClient().getItemDefinition(itemId);
            return item != null && item.isStackable();
        });
        return Boolean.TRUE.equals(stackable) || canWithdrawNotes(itemId);
    }

    private int availableRestoreSlot(int preferred, GrandExchangeOffer[] offers) {
        int count = maxSlots();
        // SELL's inventory Offer action uses the first available slot, ignoring
        // any requested slot. Choose that same slot for its durable intent.
        for (int slot = 0; slot < count; slot++) {
            if (offers[slot].getState() == GrandExchangeOfferState.EMPTY) {
                return slot;
            }
        }
        return -1;
    }

    private boolean safeOriginalCollection(GrandExchangeOffer[] offers) {
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer.getState() == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            boolean known = false;
            for (BankSellerOriginalOffersJournal.Entry entry : journal.offers) {
                if (entry.collectionMatches(slot, offer)) {
                    known = true;
                }
            }
            if (!known) {
                return false;
            }
        }
        return true;
    }

    private boolean abortOriginal(BankSellerOriginalOffersJournal.Entry entry) {
        Boolean result = clientValue(() -> {
            GrandExchangeOffer[] live = Microbot.getClient().getGrandExchangeOffers();
            if (!ownerMatchesOnClientThread() || !validOffers(live) || !entry.originalMatches(live[entry.slot])
                    || live[entry.slot].getState() != (entry.buy ? GrandExchangeOfferState.BUYING
                                                              : GrandExchangeOfferState.SELLING)) {
                return false;
            }
            Widget slot = Microbot.getClient().getWidget(465, 7 + entry.slot);
            Rectangle bounds = validBounds(slot);
            if (bounds == null) {
                return false;
            }
            Microbot.doInvoke(new NewMenuEntry().option("Abort offer").target("").identifier(2)
                    .type(MenuAction.CC_OP).param0(2).param1(slot.getId()).itemId(-1)
                    .forceLeftClick(false), bounds);
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private boolean collectOriginalsToBank() {
        Boolean result = clientValue(() -> {
            GrandExchangeOffer[] live = Microbot.getClient().getGrandExchangeOffers();
            if (!ownerMatchesOnClientThread() || !validOffers(live) || !safeOriginalCollection(live)) {
                return false;
            }
            Widget setup = Microbot.getClient().getWidget(InterfaceID.GeOffers.SETUP);
            if (setup != null && !setup.isHidden()) {
                return false;
            }
            Widget collect = Microbot.getClient().getWidget(InterfaceID.GeOffers.COLLECTALL);
            Rectangle bounds = validBounds(collect);
            if (bounds == null) {
                return false;
            }
            Microbot.doInvoke(new NewMenuEntry().option("Collect to bank").target("").identifier(2)
                    .type(MenuAction.CC_OP).param0(0).param1(InterfaceID.GeOffers.COLLECTALL)
                    .itemId(-1).forceLeftClick(false), bounds);
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private Rectangle validBounds(Widget widget) {
        if (widget == null || widget.isHidden()) {
            return null;
        }
        Rectangle bounds = widget.getBounds();
        return bounds != null && bounds.width > 0 && bounds.height > 0
                && Rs2UiHelper.isRectangleWithinCanvas(bounds) ? bounds : null;
    }

    private boolean openOverview() {
        if (!Rs2GrandExchange.openExchange() || !sleepUntil(Rs2GrandExchange::isOpen, WAIT_MS)) {
            return false;
        }
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
        }
        return sleepUntil(() -> Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen(), WAIT_MS);
    }

    private int maxSlots() {
        Boolean members = clientValue(() -> Microbot.getClient().getWorldType().contains(WorldType.MEMBERS));
        return Boolean.TRUE.equals(members) ? 8 : 3;
    }

    private Result retryPreparation(String message) {
        return ++preparationRetries >= 3 ? fail(message + "; original-offer recovery was retained") : Result.WAITING;
    }

    private Result fail(String message) {
        error = message;
        return Result.ERROR;
    }

    private boolean save() {
        try {
            BankSellerOriginalOffersJournal.write(journalPath, journal);
            return true;
        } catch (IOException | RuntimeException exception) {
            fail("Could not commit the original-offer recovery record; no further GE actions will be taken");
            return false;
        }
    }

    private boolean sameAccount() {
        return owner != null && owner.equals(currentOwner());
    }

    private String currentOwner() {
        return clientValue(() -> {
            if (!Microbot.isLoggedIn() || Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return BankSellerOriginalOffersJournal.ownerKey(Microbot.getConfigManager().getRSProfileKey(),
                    Microbot.getClient().getLocalPlayer().getName());
        });
    }

    private boolean ownerMatchesOnClientThread() {
        return Microbot.isLoggedIn() && Microbot.getClient().getLocalPlayer() != null
                && owner.equals(BankSellerOriginalOffersJournal.ownerKey(
                Microbot.getConfigManager().getRSProfileKey(), Microbot.getClient().getLocalPlayer().getName()));
    }

    private GrandExchangeOffer[] offers() {
        return clientValue(() -> {
            GrandExchangeOffer[] current = Microbot.getClient().getGrandExchangeOffers();
            if (!validOffers(current)) {
                return null;
            }
            GrandExchangeOffer[] frozen = new GrandExchangeOffer[8];
            for (int slot = 0; slot < frozen.length; slot++) {
                frozen[slot] = new FrozenOffer(current[slot]);
            }
            return frozen;
        });
    }

    private boolean validOffers(GrandExchangeOffer[] offers) {
        if (offers == null || offers.length != 8) {
            return false;
        }
        for (GrandExchangeOffer offer : offers) {
            if (offer == null || offer.getState() == null) {
                return false;
            }
        }
        return true;
    }

    private <T> T clientValue(Supplier<T> read) {
        try {
            return Microbot.getClientThread().invoke(read);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static final class FrozenOffer implements GrandExchangeOffer {
        private final int itemId;
        private final int total;
        private final int filled;
        private final long price;
        private final long spent;
        private final GrandExchangeOfferState state;

        FrozenOffer(GrandExchangeOffer original) {
            itemId = original.getItemId();
            total = original.getTotalQuantity();
            filled = original.getQuantitySold();
            price = original.getPrice();
            spent = original.getSpent();
            state = original.getState();
        }

        public int getItemId() { return itemId; }
        public int getTotalQuantity() { return total; }
        public int getQuantitySold() { return filled; }
        public long getPrice() { return price; }
        public long getSpent() { return spent; }
        public GrandExchangeOfferState getState() { return state; }
    }
}
