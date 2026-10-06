package net.runelite.client.plugins.microbot.bankseller;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Durable, typed recovery instructions. Never discard this file on a UI failure. */
final class BankSellerOriginalOffersJournal {
    static final int SCHEMA = 1;
    private static final Gson GSON = new Gson();

    enum Stage {
        ORIGINAL, CANCEL_INTENT, CANCELLED, COLLECT_INTENT, PARKED,
        RESTORE_INTENT, RESTORED, UNCHANGED, SETTLED
    }

    static final class Entry {
        int slot;
        int itemId;
        String itemName;
        GrandExchangeOfferState originalState;
        int totalQuantity;
        int initialFilled;
        long price;
        long spent;
        boolean buy;
        Stage stage = Stage.ORIGINAL;
        int finalFilled = -1;
        int restoreQuantity;
        int restoreSlot = -1;

        boolean wasActive() {
            return originalState == GrandExchangeOfferState.BUYING
                    || originalState == GrandExchangeOfferState.SELLING;
        }

        boolean originalMatches(GrandExchangeOffer live) {
            return live != null && live.getItemId() == itemId
                    && live.getTotalQuantity() == totalQuantity && live.getPrice() == price
                    && sameSide(live.getState(), buy)
                    && live.getQuantitySold() >= initialFilled
                    && live.getQuantitySold() <= totalQuantity;
        }

        /** Collection must match the frozen cancellation result, not later progress. */
        boolean terminalMatches(GrandExchangeOffer live) {
            return finalFilled >= 0 && originalMatches(live)
                    && isTerminal(live.getState()) && live.getQuantitySold() == finalFilled;
        }

        boolean restoredMatches(GrandExchangeOffer live) {
            return live != null && restoreQuantity > 0 && live.getItemId() == itemId
                    && live.getTotalQuantity() == restoreQuantity && live.getPrice() == price
                    && live.getQuantitySold() >= 0 && live.getQuantitySold() <= restoreQuantity
                    && sameSide(live.getState(), buy)
                    && (live.getState() == (buy ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING)
                        || live.getState() == (buy ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD));
        }

        /** Recheck preserved orders at completion; an external cancellation is not success. */
        boolean preservedOfferMatches(GrandExchangeOffer live) {
            if (stage == Stage.RESTORED) {
                return restoredMatches(live);
            }
            return stage == Stage.UNCHANGED && originalMatches(live)
                    && (live.getState() == (buy ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING)
                        || live.getState() == (buy ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD));
        }

        /** Never sweep a completed preserved order away before final verification. */
        boolean collectionMatches(int liveSlot, GrandExchangeOffer live) {
            if (stage == Stage.CANCELLED || stage == Stage.COLLECT_INTENT) {
                return liveSlot == slot && terminalMatches(live);
            }
            if (stage == Stage.RESTORED || stage == Stage.UNCHANGED) {
                return liveSlot == (stage == Stage.RESTORED ? restoreSlot : slot)
                        && preservedOfferMatches(live) && !isTerminal(live.getState());
            }
            return false;
        }

        /** Only a cancellation initiated by us recreates the unfilled portion. */
        void recordTerminal(GrandExchangeOffer live, boolean ourCancellation) {
            if (!originalMatches(live) || !isTerminal(live.getState())) {
                throw new IllegalArgumentException("Original offer changed while it was being parked");
            }
            finalFilled = live.getQuantitySold();
            restoreQuantity = wasActive() && ourCancellation
                    ? totalQuantity - finalFilled : 0;
            stage = Stage.CANCELLED;
        }
    }

    static final class Journal {
        int schema = SCHEMA;
        String owner;
        List<Entry> offers = new ArrayList<>();
        List<Integer> initialEmptySlots = new ArrayList<>();
    }

    private BankSellerOriginalOffersJournal() {
    }

    static boolean sameSide(GrandExchangeOfferState state, boolean buy) {
        return buy ? state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY
                : state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD
                || state == GrandExchangeOfferState.CANCELLED_SELL;
    }

    static boolean isTerminal(GrandExchangeOfferState state) {
        return state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL
                || state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD;
    }

    static long buyingReserve(Journal journal) {
        long reserve = 0;
        for (Entry entry : journal.offers) {
            if (entry.buy && entry.wasActive()) {
                int remaining = entry.finalFilled >= 0 ? entry.restoreQuantity
                        : entry.totalQuantity - entry.initialFilled;
                reserve = Math.addExact(reserve, Math.multiplyExact(entry.price, (long) remaining));
            }
        }
        return reserve;
    }

    static Set<Integer> protectedIds(Journal journal) {
        Set<Integer> ids = new HashSet<>();
        if (journal != null) {
            for (Entry entry : journal.offers) {
                ids.add(entry.itemId);
            }
        }
        return ids;
    }

    static String ownerKey(String profile, String playerName) {
        if (profile == null || profile.trim().isEmpty() || playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("A logged-in account profile is required");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    profile.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte next : hash) {
                value.append(String.format(java.util.Locale.ROOT, "%02x", next & 255));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static void validate(Journal journal, String owner) throws IOException {
        if (journal == null || journal.schema != SCHEMA || !owner.equals(journal.owner)
                || !owner.matches("[a-f0-9]{64}") || journal.offers == null
                || journal.offers.isEmpty() || journal.offers.size() > 8 || journal.initialEmptySlots == null) {
            throw new IOException("Invalid or wrong-account original-offer recovery file");
        }
        Set<Integer> slots = new HashSet<>();
        Set<Integer> restorationSlots = new HashSet<>();
        int outstandingIntents = 0;
        for (Entry entry : journal.offers) {
            if (entry == null || entry.slot < 0 || entry.slot >= 8 || !slots.add(entry.slot)
                    || entry.itemId <= 0 || entry.itemName == null || entry.itemName.trim().isEmpty()
                    || entry.stage == null || !sameSide(entry.originalState, entry.buy)
                    || entry.totalQuantity <= 0 || entry.initialFilled < 0
                    || entry.initialFilled > entry.totalQuantity || entry.price <= 0
                    || entry.price > Integer.MAX_VALUE || entry.spent < 0
                    || entry.finalFilled < -1 || entry.finalFilled > entry.totalQuantity
                    || (entry.finalFilled >= 0 && entry.finalFilled < entry.initialFilled)
                    || entry.restoreQuantity < 0 || entry.restoreQuantity > entry.totalQuantity
                    || entry.restoreSlot < -1 || entry.restoreSlot >= 8) {
                throw new IOException("Invalid original-offer recovery entry");
            }
            boolean cancelled = entry.stage == Stage.CANCELLED || entry.stage == Stage.COLLECT_INTENT
                    || entry.stage == Stage.PARKED || entry.stage == Stage.RESTORE_INTENT
                    || entry.stage == Stage.RESTORED || entry.stage == Stage.SETTLED;
            if (cancelled && entry.finalFilled < 0) {
                throw new IOException("Recovery entry is missing its cancellation result");
            }
            if (entry.restoreQuantity > 0 && (!entry.wasActive()
                    || entry.restoreQuantity != entry.totalQuantity - entry.finalFilled)) {
                throw new IOException("Recovery quantity does not match the unfilled original quantity");
            }
            if ((entry.stage == Stage.RESTORE_INTENT || entry.stage == Stage.RESTORED)
                    && (entry.restoreSlot < 0 || entry.restoreQuantity <= 0
                        || !restorationSlots.add(entry.restoreSlot))) {
                throw new IOException("Restoration intent is incomplete");
            }
            if ((entry.stage == Stage.ORIGINAL || entry.stage == Stage.CANCEL_INTENT)
                    && (entry.finalFilled != -1 || entry.restoreQuantity != 0 || entry.restoreSlot != -1)) {
                throw new IOException("Uncancelled recovery entry has premature restoration terms");
            }
            if ((entry.stage == Stage.SETTLED || entry.stage == Stage.UNCHANGED) && entry.restoreQuantity != 0) {
                throw new IOException("A completed or unchanged offer must not be placed again");
            }
            if (entry.stage != Stage.RESTORE_INTENT && entry.stage != Stage.RESTORED && entry.restoreSlot != -1) {
                throw new IOException("An unplaced offer cannot claim a restoration slot");
            }
            if (entry.stage == Stage.UNCHANGED && entry.finalFilled != -1) {
                throw new IOException("An unchanged offer cannot claim a cancellation result");
            }
            if (entry.stage == Stage.RESTORE_INTENT && ++outstandingIntents > 1) {
                throw new IOException("Multiple unresolved placement intents are unsafe");
            }
        }
        for (Integer emptySlot : journal.initialEmptySlots) {
            if (emptySlot == null || emptySlot < 0 || emptySlot >= 8 || !slots.add(emptySlot)) {
                throw new IOException("Invalid initial empty-slot snapshot");
            }
        }
        if (slots.size() != 8) {
            throw new IOException("The original eight-slot boundary is incomplete");
        }
        try {
            if (buyingReserve(journal) > Integer.MAX_VALUE) {
                throw new IOException("Original buy offers exceed the supported coin-stack limit");
            }
        } catch (ArithmeticException overflow) {
            throw new IOException("Original buy-offer reservation overflow", overflow);
        }
    }

    static Journal read(Path path, String owner) throws IOException {
        if (Files.size(path) > 256_000) {
            throw new IOException("Original-offer recovery file is unexpectedly large");
        }
        try {
            Journal journal = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Journal.class);
            validate(journal, owner);
            return journal;
        } catch (JsonParseException malformed) {
            throw new IOException("Unreadable original-offer recovery file", malformed);
        }
    }

    static void write(Path path, Journal journal) throws IOException {
        validate(journal, journal.owner);
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "original-offers-", ".tmp");
        try {
            byte[] bytes = GSON.toJson(journal).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Do not cancel an order unless recovery instructions can be committed atomically.
                throw new IOException("Atomic original-offer recovery storage is unavailable", unsupported);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
