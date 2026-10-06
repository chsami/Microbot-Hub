package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pure recovery regression checks; never starts or controls a client. */
public final class BankSellerOriginalOffersJournalTest {
    private static int assertions;
    private static final String OWNER = BankSellerOriginalOffersJournal.ownerKey("test-rs-profile", "Player");

    private BankSellerOriginalOffersJournalTest() {
    }

    public static void main(String[] args) throws Exception {
        accountBindingSurvivesDisplayNameChanges();
        finalCancellationProgressPreventsDuplicateTrades();
        terminalCollectionRequiresFrozenProgressAndExactTerms();
        completedAndAlreadyCancelledOffersAreNeverRepeated();
        restorationRequiresExactIdentityAndSide();
        completionRequiresExactPreservedOffers();
        overviewCollectionRequiresExactSlotAndStage();
        allOriginalAssetsRemainProtected();
        malformedRecoveryFailsClosed();
        journalRoundTripAndDurableIntent();
        System.out.println("BankSellerOriginalOffersJournalTest passed (" + assertions + " assertions)");
    }

    private static void accountBindingSurvivesDisplayNameChanges() {
        check(OWNER.equals(BankSellerOriginalOffersJournal.ownerKey("test-rs-profile", "Renamed Player")),
                "Display-name changes must not hide an unfinished recovery file");
        check(!OWNER.equals(BankSellerOriginalOffersJournal.ownerKey("different-profile", "Player")),
                "Other accounts must not share restoration instructions");
        check(OWNER.matches("[a-f0-9]{64}"), "Recovery filename uses a stable non-identifying hash");
        expectIllegal(() -> BankSellerOriginalOffersJournal.ownerKey("", "Player"), "Missing profile fails closed");
        expectIllegal(() -> BankSellerOriginalOffersJournal.ownerKey("profile", ""), "Logged-in name is required");
    }

    private static void finalCancellationProgressPreventsDuplicateTrades() throws IOException {
        BankSellerOriginalOffersJournal.Journal journal = journal(true, GrandExchangeOfferState.BUYING);
        BankSellerOriginalOffersJournal.Entry entry = journal.offers.get(0);
        check(entry.initialFilled == 2, "Initial progress is captured");
        check(BankSellerOriginalOffersJournal.buyingReserve(journal) == 152, "Initial buy funding reserves unfilled units");
        entry.stage = BankSellerOriginalOffersJournal.Stage.CANCEL_INTENT;
        entry.recordTerminal(offer(entry.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_BUY), true);
        check(entry.restoreQuantity == 5, "Use final cancellation progress, not the initial eight remaining units");
        check(entry.finalFilled == 5, "Cancellation progress is durable");
        check(entry.price == 19, "Never reprice original buy offers");
        check(BankSellerOriginalOffersJournal.buyingReserve(journal) == 95, "Restore funding matches final remaining units");
        BankSellerOriginalOffersJournal.validate(journal, OWNER);
        check(true, "A partial-fill cancellation journal validates");
        expectIllegal(() -> entry.recordTerminal(offer(entry.itemId, 11, 5, 19,
                GrandExchangeOfferState.CANCELLED_BUY), true), "Changed total quantity is rejected");
        expectIllegal(() -> entry.recordTerminal(offer(entry.itemId, 10, 1, 19,
                GrandExchangeOfferState.CANCELLED_BUY), true), "Regressed fill count is rejected");

        BankSellerOriginalOffersJournal.Entry sell = journal(false, GrandExchangeOfferState.SELLING).offers.get(0);
        sell.recordTerminal(offer(sell.itemId, 10, 10, 19, GrandExchangeOfferState.SOLD), true);
        check(sell.restoreQuantity == 0, "An order which finishes during cancellation is not re-listed");
    }

    private static void completedAndAlreadyCancelledOffersAreNeverRepeated() {
        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{GrandExchangeOfferState.BOUGHT,
                GrandExchangeOfferState.SOLD, GrandExchangeOfferState.CANCELLED_BUY,
                GrandExchangeOfferState.CANCELLED_SELL}) {
            boolean buy = BankSellerOriginalOffersJournal.sameSide(state, true);
            BankSellerOriginalOffersJournal.Entry entry = journal(buy, state).offers.get(0);
            entry.recordTerminal(offer(entry.itemId, 10, 5, 19, state), true);
            check(entry.restoreQuantity == 0, "Already terminal " + state + " must only bank assets");
            check(entry.stage == BankSellerOriginalOffersJournal.Stage.CANCELLED, "Terminal assets await collection");
        }
        BankSellerOriginalOffersJournal.Entry external = journal(true, GrandExchangeOfferState.BUYING).offers.get(0);
        external.recordTerminal(offer(external.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_BUY), false);
        check(external.restoreQuantity == 0, "An externally cancelled order without our intent is not repeated");
    }

    private static void terminalCollectionRequiresFrozenProgressAndExactTerms() {
        for (boolean buy : new boolean[]{false, true}) {
            GrandExchangeOfferState active = buy ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING;
            GrandExchangeOfferState cancelled = buy ? GrandExchangeOfferState.CANCELLED_BUY
                    : GrandExchangeOfferState.CANCELLED_SELL;
            GrandExchangeOfferState completed = buy ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD;
            GrandExchangeOfferState oppositeCancelled = buy ? GrandExchangeOfferState.CANCELLED_SELL
                    : GrandExchangeOfferState.CANCELLED_BUY;
            BankSellerOriginalOffersJournal.Entry entry = journal(buy, active).offers.get(0);
            entry.recordTerminal(offer(entry.itemId, 10, 5, 19, cancelled), true);
            check(entry.terminalMatches(offer(entry.itemId, 10, 5, 19, cancelled)),
                    "Exact frozen " + cancelled + " progress authorizes collection");
            entry.stage = BankSellerOriginalOffersJournal.Stage.COLLECT_INTENT;
            check(entry.terminalMatches(offer(entry.itemId, 10, 5, 19, cancelled)),
                    "A durable collection intent retains the frozen terminal match");
            check(entry.originalMatches(offer(entry.itemId, 10, 6, 19, active)),
                    "Pre-cancellation identity matching still permits legitimate fill progress");

            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 4, 19, cancelled),
                    "Terminal progress below the saved final fill is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 6, 19, cancelled),
                    "Terminal progress above the saved final fill is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 1, 19, cancelled),
                    "Terminal progress below the initial fill is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 11, 19, cancelled),
                    "Terminal progress above the original total is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 5, 19, active),
                    "An active replacement cannot be collected as a cancelled original");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId + 1, 10, 5, 19, cancelled),
                    "A wrong terminal item is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 5, 19, oppositeCancelled),
                    "An opposite-side terminal offer is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 11, 5, 19, cancelled),
                    "A changed terminal total quantity is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 5, 18, cancelled),
                    "A changed terminal unit price is rejected");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(entry.itemId, 10, 5, 0, cancelled),
                    "A zeroed terminal unit price is not assumed to be normalization");
            rejectTerminalWithoutChangingSavedTerms(entry, null, "Unknown terminal data fails closed");
            rejectTerminalWithoutChangingSavedTerms(entry, offer(0, 0, 0, 0, GrandExchangeOfferState.EMPTY),
                    "An empty slot cannot authorize collection without the separate durable-intent recovery path");

            BankSellerOriginalOffersJournal.Entry unsnapshotted = journal(buy, active).offers.get(0);
            rejectTerminalWithoutChangingSavedTerms(unsnapshotted, offer(unsnapshotted.itemId, 10, 5, 19, cancelled),
                    "An offer without a saved terminal fill cannot authorize collection");
            BankSellerOriginalOffersJournal.Entry filled = journal(buy, active).offers.get(0);
            filled.recordTerminal(offer(filled.itemId, 10, 10, 19, completed), true);
            check(filled.terminalMatches(offer(filled.itemId, 10, 10, 19, completed)),
                    "Exact fully filled " + completed + " assets remain collectable without another trade");
        }

        BankSellerOriginalOffersJournal.Entry stale = journal(false, GrandExchangeOfferState.SELLING).offers.get(0);
        stale.itemId = 1001;
        stale.totalQuantity = 12;
        stale.initialFilled = 0;
        stale.price = 1000;
        stale.spent = 0;
        stale.recordTerminal(offer(1001, 12, 0, 1000, GrandExchangeOfferState.CANCELLED_SELL), true);
        rejectTerminalWithoutChangingSavedTerms(stale, offer(1001, 12, 0, 500, GrandExchangeOfferState.SELLING),
                "An active replacement at 500 cannot overwrite the saved 1000-price original");
        rejectTerminalWithoutChangingSavedTerms(stale, offer(1001, 12, 0, 500, GrandExchangeOfferState.CANCELLED_SELL),
                "Cancelling the replacement would not make its different price an exact saved original");
        check(stale.price == 1000 && stale.totalQuantity == 12 && stale.finalFilled == 0
                        && stale.restoreQuantity == 12,
                "Rejected stale-price offers retain the exact original restoration instructions");
    }

    private static void rejectTerminalWithoutChangingSavedTerms(BankSellerOriginalOffersJournal.Entry entry,
                                                                GrandExchangeOffer live, String message) {
        checkOfferMatchWithoutChangingSavedTerms(entry, live, false, true, message);
    }

    private static void checkOfferMatchWithoutChangingSavedTerms(BankSellerOriginalOffersJournal.Entry entry,
                                                                 GrandExchangeOffer live, boolean expected,
                                                                 boolean terminal, String message) {
        checkMatchingWithoutChangingSavedTerms(entry,
                () -> terminal ? entry.terminalMatches(live) : entry.preservedOfferMatches(live), expected, message);
    }

    private static void checkMatchingWithoutChangingSavedTerms(BankSellerOriginalOffersJournal.Entry entry,
                                                               java.util.function.BooleanSupplier comparison,
                                                               boolean expected, String message) {
        int slot = entry.slot;
        int itemId = entry.itemId;
        String itemName = entry.itemName;
        GrandExchangeOfferState originalState = entry.originalState;
        int totalQuantity = entry.totalQuantity;
        int initialFilled = entry.initialFilled;
        long price = entry.price;
        long spent = entry.spent;
        boolean buy = entry.buy;
        BankSellerOriginalOffersJournal.Stage stage = entry.stage;
        int finalFilled = entry.finalFilled;
        int restoreQuantity = entry.restoreQuantity;
        int restoreSlot = entry.restoreSlot;
        check(comparison.getAsBoolean() == expected, message);
        check(entry.slot == slot && entry.itemId == itemId && java.util.Objects.equals(entry.itemName, itemName)
                        && entry.originalState == originalState && entry.totalQuantity == totalQuantity
                        && entry.initialFilled == initialFilled && entry.price == price && entry.spent == spent
                        && entry.buy == buy && entry.stage == stage && entry.finalFilled == finalFilled
                        && entry.restoreQuantity == restoreQuantity && entry.restoreSlot == restoreSlot,
                message + ": matching is read-only and preserves all saved terms");
    }

    private static void restorationRequiresExactIdentityAndSide() {
        BankSellerOriginalOffersJournal.Entry entry = journal(false, GrandExchangeOfferState.SELLING).offers.get(0);
        entry.recordTerminal(offer(entry.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_SELL), true);
        entry.restoreSlot = 0;
        entry.stage = BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT;
        check(entry.restoredMatches(offer(entry.itemId, 5, 0, 19, GrandExchangeOfferState.SELLING)),
                "Exact active original sell restoration is recognized");
        check(entry.restoredMatches(offer(entry.itemId, 5, 5, 19, GrandExchangeOfferState.SOLD)),
                "Instantly filled restoration is recognized without another placement");
        check(!entry.restoredMatches(offer(entry.itemId, 10, 0, 19, GrandExchangeOfferState.SELLING)),
                "Replaying original total is not restoration");
        check(!entry.restoredMatches(offer(entry.itemId, 5, 0, 18, GrandExchangeOfferState.SELLING)),
                "A changed unit price is not restoration");
        check(!entry.restoredMatches(offer(entry.itemId + 1, 5, 0, 19, GrandExchangeOfferState.SELLING)),
                "A wrong item is not restoration");
        check(!entry.restoredMatches(offer(entry.itemId, 5, 0, 19, GrandExchangeOfferState.BUYING)),
                "A buy must never be mistaken for a sell");
        check(!entry.restoredMatches(offer(entry.itemId, 5, 0, 19, GrandExchangeOfferState.CANCELLED_SELL)),
                "Externally cancelled restoration needs attention");
        check(!entry.restoredMatches(offer(0, 0, 0, 0, GrandExchangeOfferState.EMPTY)),
                "Empty slot after an uncertain intent must never authorize another trade");
        check(!entry.restoredMatches(null), "Unknown intent result remains uncertain");
        check(!entry.restoredMatches(offer(entry.itemId, 5, 6, 19, GrandExchangeOfferState.SOLD)),
                "Invalid restored progress is not an exact matching order");

        BankSellerOriginalOffersJournal.Entry buy = journal(true, GrandExchangeOfferState.BUYING).offers.get(0);
        buy.recordTerminal(offer(buy.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_BUY), true);
        check(buy.restoredMatches(offer(buy.itemId, 5, 3, 19, GrandExchangeOfferState.BUYING)),
                "Partial buy restoration is recognized");
        check(buy.restoredMatches(offer(buy.itemId, 5, 5, 19, GrandExchangeOfferState.BOUGHT)),
                "Immediately bought restoration is recognized");
        check(!buy.restoredMatches(offer(buy.itemId, 5, 0, 19, GrandExchangeOfferState.SELLING)),
                "Original buy side is preserved");
    }

    private static void completionRequiresExactPreservedOffers() {
        for (boolean buy : new boolean[]{false, true}) {
            GrandExchangeOfferState active = buy ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING;
            GrandExchangeOfferState completed = buy ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD;
            GrandExchangeOfferState cancelled = buy ? GrandExchangeOfferState.CANCELLED_BUY
                    : GrandExchangeOfferState.CANCELLED_SELL;
            GrandExchangeOfferState oppositeActive = buy ? GrandExchangeOfferState.SELLING : GrandExchangeOfferState.BUYING;
            GrandExchangeOfferState oppositeCompleted = buy ? GrandExchangeOfferState.SOLD : GrandExchangeOfferState.BOUGHT;
            for (BankSellerOriginalOffersJournal.Stage stage : new BankSellerOriginalOffersJournal.Stage[]{
                    BankSellerOriginalOffersJournal.Stage.RESTORED, BankSellerOriginalOffersJournal.Stage.UNCHANGED}) {
                BankSellerOriginalOffersJournal.Entry entry = journal(buy, active).offers.get(0);
                boolean restored = stage == BankSellerOriginalOffersJournal.Stage.RESTORED;
                if (restored) {
                    entry.recordTerminal(offer(entry.itemId, 10, 5, 19, cancelled), true);
                    entry.restoreSlot = 0;
                }
                entry.stage = stage;
                int quantity = restored ? entry.restoreQuantity : entry.totalQuantity;
                int minimumFilled = restored ? 0 : entry.initialFilled;
                String prefix = stage + " " + active + ": ";

                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled, 19, active),
                        true, false, prefix + "exact active offer is preserved");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled + 1, 19, active),
                        true, false, prefix + "legitimate partial-fill progress remains preserved");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, quantity, 19, completed),
                        true, false, prefix + "an exact naturally completed offer remains preserved");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled, 19, cancelled),
                        false, false, prefix + "external cancellation is not successful preservation");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(0, 0, 0, 0, GrandExchangeOfferState.EMPTY),
                        false, false, prefix + "an empty slot cannot clear recovery instructions");
                checkOfferMatchWithoutChangingSavedTerms(entry, null,
                        false, false, prefix + "unknown offer data cannot clear recovery instructions");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled, 19, oppositeActive),
                        false, false, prefix + "an opposite-side active offer is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, quantity, 19, oppositeCompleted),
                        false, false, prefix + "an opposite-side completed offer is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId + 1, quantity, minimumFilled, 19, active),
                        false, false, prefix + "a changed item is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity + 1, minimumFilled, 19, active),
                        false, false, prefix + "a changed quantity is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled, 18, active),
                        false, false, prefix + "a changed price is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled, 0, active),
                        false, false, prefix + "a zeroed price is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, quantity + 1, 19, completed),
                        false, false, prefix + "progress above the exact quantity is rejected");
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled - 1, 19, active),
                        false, false, prefix + "regressed or negative progress is rejected");
                entry.stage = BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT;
                checkOfferMatchWithoutChangingSavedTerms(entry, offer(entry.itemId, quantity, minimumFilled, 19, active),
                        false, false, prefix + "an unverified placement intent is not already preserved");
            }
        }
    }

    private static void overviewCollectionRequiresExactSlotAndStage() {
        for (boolean buy : new boolean[]{false, true}) {
            GrandExchangeOfferState active = buy ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING;
            GrandExchangeOfferState completed = buy ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD;
            GrandExchangeOfferState cancelled = buy ? GrandExchangeOfferState.CANCELLED_BUY
                    : GrandExchangeOfferState.CANCELLED_SELL;
            BankSellerOriginalOffersJournal.Entry cancelledEntry = journal(buy, active).offers.get(0);
            cancelledEntry.recordTerminal(offer(cancelledEntry.itemId, 10, 5, 19, cancelled), true);
            for (BankSellerOriginalOffersJournal.Stage stage : new BankSellerOriginalOffersJournal.Stage[]{
                    BankSellerOriginalOffersJournal.Stage.CANCELLED, BankSellerOriginalOffersJournal.Stage.COLLECT_INTENT}) {
                cancelledEntry.stage = stage;
                checkCollectionWithoutChangingSavedTerms(cancelledEntry, 0,
                        offer(cancelledEntry.itemId, 10, 5, 19, cancelled), true,
                        stage + " exact terminal original is safe in its saved slot");
                checkCollectionWithoutChangingSavedTerms(cancelledEntry, 1,
                        offer(cancelledEntry.itemId, 10, 5, 19, cancelled), false,
                        stage + " cannot claim an identical terminal offer in another slot");
                checkCollectionWithoutChangingSavedTerms(cancelledEntry, 0,
                        offer(cancelledEntry.itemId, 10, 6, 19, cancelled), false,
                        stage + " rejects changed final fill immediately before collection");
                checkCollectionWithoutChangingSavedTerms(cancelledEntry, 0,
                        offer(cancelledEntry.itemId, 10, 5, 19, active), false,
                        stage + " cannot collect an active replacement as its cancelled original");
            }
            for (BankSellerOriginalOffersJournal.Stage stage : new BankSellerOriginalOffersJournal.Stage[]{
                    BankSellerOriginalOffersJournal.Stage.RESTORED, BankSellerOriginalOffersJournal.Stage.UNCHANGED}) {
                BankSellerOriginalOffersJournal.Entry preserved = journal(buy, active).offers.get(0);
                boolean restored = stage == BankSellerOriginalOffersJournal.Stage.RESTORED;
                if (restored) {
                    preserved.recordTerminal(offer(preserved.itemId, 10, 5, 19, cancelled), true);
                    preserved.restoreSlot = 2;
                }
                preserved.stage = stage;
                int savedSlot = restored ? 2 : 0;
                int quantity = restored ? 5 : 10;
                int filled = restored ? 1 : 3;
                checkCollectionWithoutChangingSavedTerms(preserved, savedSlot,
                        offer(preserved.itemId, quantity, filled, 19, active), true,
                        stage + " exact partial active offer is recognized in its actual saved slot");
                checkCollectionWithoutChangingSavedTerms(preserved, restored ? 0 : 1,
                        offer(preserved.itemId, quantity, filled, 19, active), false,
                        stage + " rejects the original or wrong slot instead of the actual preserved slot");
                checkCollectionWithoutChangingSavedTerms(preserved, savedSlot,
                        offer(preserved.itemId, quantity, quantity, 19, completed), false,
                        stage + " completed proof must not be swept away by overview Collect");
                checkCollectionWithoutChangingSavedTerms(preserved, savedSlot,
                        offer(preserved.itemId, quantity, filled, 19, cancelled), false,
                        stage + " externally cancelled proof must not be swept away");
            }
            for (BankSellerOriginalOffersJournal.Stage stage : new BankSellerOriginalOffersJournal.Stage[]{
                    BankSellerOriginalOffersJournal.Stage.ORIGINAL, BankSellerOriginalOffersJournal.Stage.CANCEL_INTENT,
                    BankSellerOriginalOffersJournal.Stage.PARKED, BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT,
                    BankSellerOriginalOffersJournal.Stage.SETTLED}) {
                cancelledEntry.stage = stage;
                checkCollectionWithoutChangingSavedTerms(cancelledEntry, 0,
                        offer(cancelledEntry.itemId, 10, 5, 19, cancelled), false,
                        stage + " cannot authorize overview collection");
            }
            BankSellerOriginalOffersJournal.Entry filled = journal(buy, active).offers.get(0);
            filled.recordTerminal(offer(filled.itemId, 10, 10, 19, completed), true);
            checkCollectionWithoutChangingSavedTerms(filled, 0, offer(filled.itemId, 10, 10, 19, completed), true,
                    "An exact original completed during cancellation can still collect its saved assets");
        }
    }

    private static void checkCollectionWithoutChangingSavedTerms(BankSellerOriginalOffersJournal.Entry entry,
                                                                 int liveSlot, GrandExchangeOffer live,
                                                                 boolean expected, String message) {
        checkMatchingWithoutChangingSavedTerms(entry, () -> entry.collectionMatches(liveSlot, live), expected, message);
    }

    private static void allOriginalAssetsRemainProtected() {
        BankSellerOriginalOffersJournal.Journal journal = journal(true, GrandExchangeOfferState.BOUGHT);
        BankSellerOriginalOffersJournal.Entry sell = journal(false, GrandExchangeOfferState.CANCELLED_SELL).offers.get(0);
        sell.slot = 1;
        sell.itemId = 200;
        journal.offers.add(sell);
        journal.initialEmptySlots.remove(Integer.valueOf(1));
        check(BankSellerOriginalOffersJournal.protectedIds(journal).contains(100), "Bought original assets are protected");
        check(BankSellerOriginalOffersJournal.protectedIds(journal).contains(200), "Cancelled original assets are protected");
        check(BankSellerOriginalOffersJournal.protectedIds(journal).size() == 2, "Protect only original item types");
        check(BankSellerOriginalOffersJournal.protectedIds(null).isEmpty(), "No journal adds no original exclusions");
    }

    private static void malformedRecoveryFailsClosed() throws IOException {
        BankSellerOriginalOffersJournal.Journal journal = journal(true, GrandExchangeOfferState.BUYING);
        BankSellerOriginalOffersJournal.validate(journal, OWNER);
        check(true, "Complete original eight-slot boundary validates");
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal,
                BankSellerOriginalOffersJournal.ownerKey("different-profile", "Player")), "Wrong account is refused");
        journal.initialEmptySlots.remove(Integer.valueOf(7));
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Incomplete slot boundary is refused");
        journal.initialEmptySlots.add(7);
        journal.initialEmptySlots.add(0);
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Occupied/empty slot overlap is refused");
        journal.initialEmptySlots.remove(Integer.valueOf(0));
        BankSellerOriginalOffersJournal.Entry entry = journal.offers.get(0);
        entry.price = (long) Integer.MAX_VALUE + 1;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Unsupported long unit price is refused");
        entry.price = Integer.MAX_VALUE;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Aggregate refund above coin capacity is refused");
        entry.price = 19;
        entry.stage = BankSellerOriginalOffersJournal.Stage.PARKED;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Parking needs post-cancel fill count");
        entry.finalFilled = 5;
        entry.restoreQuantity = 8;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Stale remaining quantity is refused");
        entry.restoreQuantity = 5;
        entry.stage = BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Intent needs a frozen destination slot");
        entry.restoreSlot = 1;
        BankSellerOriginalOffersJournal.validate(journal, OWNER);
        check(true, "Complete restoration intent validates");
        entry.stage = BankSellerOriginalOffersJournal.Stage.SETTLED;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Settled entries cannot request duplicate placement");
        entry.restoreQuantity = 0;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Settled entries cannot claim a placed order slot");
        entry.restoreSlot = -1;
        BankSellerOriginalOffersJournal.validate(journal, OWNER);
        check(true, "A genuinely settled order validates");
        entry.stage = BankSellerOriginalOffersJournal.Stage.UNCHANGED;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Unchanged entries cannot claim cancellation results");
        journal.schema = 99;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(journal, OWNER), "Unknown schema is refused");

        BankSellerOriginalOffersJournal.Journal twoIntents = journal(true, GrandExchangeOfferState.BUYING);
        BankSellerOriginalOffersJournal.Entry first = twoIntents.offers.get(0);
        first.recordTerminal(offer(first.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_BUY), true);
        first.stage = BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT;
        first.restoreSlot = 2;
        BankSellerOriginalOffersJournal.Entry second = journal(false, GrandExchangeOfferState.SELLING).offers.get(0);
        second.slot = 1;
        second.recordTerminal(offer(second.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_SELL), true);
        second.stage = BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT;
        second.restoreSlot = 3;
        twoIntents.offers.add(second);
        twoIntents.initialEmptySlots.remove(Integer.valueOf(1));
        expectIo(() -> BankSellerOriginalOffersJournal.validate(twoIntents, OWNER), "Only one unresolved placement intent may exist");
        first.stage = BankSellerOriginalOffersJournal.Stage.RESTORED;
        second.restoreSlot = 2;
        expectIo(() -> BankSellerOriginalOffersJournal.validate(twoIntents, OWNER), "Restored offers cannot share a slot");
        second.restoreSlot = 3;
        BankSellerOriginalOffersJournal.validate(twoIntents, OWNER);
        check(true, "A verified prior restoration and one pending intent validate");
    }

    private static void journalRoundTripAndDurableIntent() throws Exception {
        Path directory = Files.createTempDirectory("bankseller-original-journal-test-");
        Path path = directory.resolve("recovery.json");
        try {
            BankSellerOriginalOffersJournal.Journal journal = journal(false, GrandExchangeOfferState.SELLING);
            BankSellerOriginalOffersJournal.Entry entry = journal.offers.get(0);
            entry.recordTerminal(offer(entry.itemId, 10, 5, 19, GrandExchangeOfferState.CANCELLED_SELL), true);
            entry.stage = BankSellerOriginalOffersJournal.Stage.PARKED;
            BankSellerOriginalOffersJournal.write(path, journal);
            BankSellerOriginalOffersJournal.Journal loaded = BankSellerOriginalOffersJournal.read(path, OWNER);
            check(loaded.offers.get(0).price == 19L, "Long unit price survives typed JSON without Integer casts");
            check(loaded.offers.get(0).restoreQuantity == 5, "Remaining quantity survives restart");
            check(loaded.initialEmptySlots.size() == 7, "All initial empty slots survive restart");
            entry.stage = BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT;
            entry.restoreSlot = 2;
            BankSellerOriginalOffersJournal.write(path, journal);
            loaded = BankSellerOriginalOffersJournal.read(path, OWNER);
            check(loaded.offers.get(0).stage == BankSellerOriginalOffersJournal.Stage.RESTORE_INTENT,
                    "Potentially irreversible placement intent is durably stored");
            check(loaded.offers.get(0).restoreSlot == 2, "Intent freezes its destination slot");
            expectIo(() -> BankSellerOriginalOffersJournal.read(path,
                    BankSellerOriginalOffersJournal.ownerKey("other-account", "Player")), "Cross-account file loading is refused");
            Files.writeString(path, "{broken", StandardCharsets.UTF_8);
            expectIo(() -> BankSellerOriginalOffersJournal.read(path, OWNER), "Corrupt JSON fails closed");
            check(Files.exists(path), "Reading corrupt recovery never deletes the saved evidence");
        } finally {
            Files.deleteIfExists(path);
            Files.delete(directory);
        }
    }

    private static BankSellerOriginalOffersJournal.Journal journal(boolean buy, GrandExchangeOfferState state) {
        BankSellerOriginalOffersJournal.Journal journal = new BankSellerOriginalOffersJournal.Journal();
        journal.owner = OWNER;
        BankSellerOriginalOffersJournal.Entry entry = new BankSellerOriginalOffersJournal.Entry();
        entry.slot = 0;
        entry.itemId = 100;
        entry.itemName = "Original item";
        entry.originalState = state;
        entry.totalQuantity = 10;
        entry.initialFilled = 2;
        entry.price = 19;
        entry.spent = 38;
        entry.buy = buy;
        journal.offers.add(entry);
        for (int slot = 1; slot < 8; slot++) {
            journal.initialEmptySlots.add(slot);
        }
        return journal;
    }

    private static GrandExchangeOffer offer(int itemId, int quantity, int filled, long price,
                                           GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
                new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getItemId": return itemId;
                        case "getTotalQuantity": return quantity;
                        case "getQuantitySold": return filled;
                        case "getPrice": return price;
                        case "getSpent": return price * filled;
                        case "getState": return state;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @FunctionalInterface
    private interface IoAction { void run() throws IOException; }

    private static void expectIo(IoAction action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            assertions++;
        }
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }
}
