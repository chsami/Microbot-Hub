package net.runelite.client.plugins.microbot.droslayer;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Optional GE resupply. Only the offer created by this visit may be collected. */
@Slf4j
final class DroSlayerCannonPurchase {
    private enum Phase { IDLE, FUNDS, LEAVE_BANK, TRAVEL, OPEN, SUBMIT, CONFIRM, WAIT, COLLECT, RETURN }
    private Phase phase = Phase.IDLE;
    private int quantity, price, slot = -1;
    private long deadline, offerDeadline, retryAt, nextPurchaseAt;
    private int attempts;
    private int originalCoins;
    private static final WorldPoint GE = new WorldPoint(3164, 3487, 0);
    private static final long COOLDOWN = 30 * 60_000L;

    boolean isActive() { return phase != Phase.IDLE; }
    void reset() {
        phase = Phase.IDLE;
        slot = -1;
        nextPurchaseAt = 0L;
    }

    static int affordableQuantity(long coins, int unitPrice, int preferred) {
        if (unitPrice <= 0 || coins < (long) unitPrice * 1000) return 0;
        long affordable = coins / unitPrice;
        return affordable < 2000 ? 1000 : (int) Math.min(affordable, Math.max(2000, Math.min(3000, preferred)));
    }

    boolean begin() {
        if (isActive()) return true;
        if (System.currentTimeMillis() < nextPurchaseAt || !Rs2Bank.isOpen()) return false;
        nextPurchaseAt = System.currentTimeMillis() + COOLDOWN;
        if (Rs2Player.isIronman()) {
            log.info("Skipping cannonball purchase: iron account");
            return false;
        }
        var live = Rs2GrandExchange.getRealTimePrices(ItemID.STEEL_CANNONBALL);
        int reference = live != null && live.buyPrice > 0 ? live.buyPrice : Rs2GrandExchange.getPrice(ItemID.STEEL_CANNONBALL);
        long markedPrice = (long) Math.ceil(reference * 1.20);
        if (markedPrice <= 0 || markedPrice > Integer.MAX_VALUE) {
            log.warn("Skipping cannonball purchase: no usable GE price");
            return false;
        }
        price = (int) markedPrice;
        long coins = (long) Rs2Inventory.itemQuantity(ItemID.COINS_995) + Rs2Bank.count(ItemID.COINS_995);
        originalCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
        quantity = affordableQuantity(coins, price, ThreadLocalRandom.current().nextInt(2000, 3001));
        if (quantity == 0 || (long) price * quantity > Integer.MAX_VALUE) {
            log.warn("Skipping cannonball purchase: coins={} price={}; cannot afford 1000", coins, price);
            return false;
        }
        phase = Phase.FUNDS;
        deadline = System.currentTimeMillis() + 10 * 60_000L;
        retryAt = 0L;
        attempts = 0;
        slot = -1;
        log.info("Cannonball GE resupply started: quantity={} unitPrice={} maximumCost={}", quantity, price, (long) quantity * price);
        return true;
    }

    void tick(DroSlayerConfig config) {
        if (!isActive()) return;
        long now = System.currentTimeMillis();
        Microbot.status = "Buying cannonballs: " + phase;
        if (phase != Phase.RETURN && now >= deadline) fail("GE trip timed out");
        if (now < retryAt) return;
        retryAt = now + 2_000L;
        switch (phase) {
            case FUNDS:
                if (!Rs2Bank.isOpen()) { DroSlayerRuntime.walkToBankAndOpen(config, "Cannonball purchase"); return; }
                int cost = price * quantity;
                int carriedCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
                if (carriedCoins < cost) {
                    if (++attempts > 3) { fail("Coin withdrawal not observed"); return; }
                    input(() -> { Rs2Bank.setWithdrawAsItem(); return Rs2Bank.withdrawX(ItemID.COINS_995, cost - carriedCoins); });
                    return;
                }
                phase = Phase.LEAVE_BANK;
                attempts = 0;
                input(Rs2Bank::closeBank);
                return;
            case LEAVE_BANK:
                if (!Rs2Bank.isOpen()) phase = Phase.TRAVEL;
                else input(Rs2Bank::closeBank);
                return;
            case TRAVEL:
                if (DroSlayerRuntime.walkFull(config, GE, 5)) { DroSlayerRuntime.cancelWalk(); phase = Phase.OPEN; }
                return;
            case OPEN:
                if (Rs2GrandExchange.isOpen()) { phase = Phase.SUBMIT; return; }
                if (++attempts > 3) { fail("Cannot open Grand Exchange"); return; }
                input(Rs2GrandExchange::openExchange);
                return;
            case SUBMIT:
                // Refuse to claim another script's existing cannonball offer, even if completed.
                int free = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    var offers = Microbot.getClient().getGrandExchangeOffers();
                    if (offers == null) return -1;
                    for (int i = 0; i < offers.length; i++) {
                        var offer = offers[i];
                        if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY
                                && offer.getItemId() == ItemID.STEEL_CANNONBALL) return -1;
                    }
                    var available = Rs2GrandExchange.getAvailableSlot();
                    return available == null ? -1 : available.ordinal();
                }).orElse(-1);
                if (free < 0) { fail("Existing cannonball offer or no free GE slot"); return; }
                if (Rs2Inventory.itemQuantity(ItemID.COINS_995) < (long) price * quantity) { fail("Purchase funds no longer available"); return; }
                // Set confirmation phase before issuing input: a delayed server response must
                // never cause a second buy offer, even when the utility reports failure.
                slot = free;
                phase = Phase.CONFIRM;
                offerDeadline = now + 20_000L;
                input(() -> Rs2GrandExchange.buyItem("Steel cannonball", price, quantity));
                return;
            case CONFIRM:
                var confirmed = ownedState();
                if (confirmed == GrandExchangeOfferState.BUYING || confirmed == GrandExchangeOfferState.BOUGHT) {
                    phase = Phase.WAIT; offerDeadline = now + 3 * 60_000L;
                }
                else if (now >= offerDeadline) fail("Buy offer was not confirmed; no duplicate offer will be placed");
                return;
            case WAIT:
                var state = ownedState();
                if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY) phase = Phase.COLLECT;
                else if (now >= offerDeadline) fail("Offer still pending; left at GE for collection, retry held for 30 minutes");
                return;
            case COLLECT:
                var observed = ownedState();
                if (observed == GrandExchangeOfferState.EMPTY) {
                    log.info("Cannonball purchase collected; returning to bank to verify stock");
                    phase = Phase.RETURN;
                    return;
                }
                if (observed != GrandExchangeOfferState.BOUGHT && observed != GrandExchangeOfferState.CANCELLED_BUY) return;
                input(() -> Rs2GrandExchange.collectOffer(Rs2GrandExchange.getSlotFromIndex(slot), true));
                return;
            case RETURN:
                if (Rs2GrandExchange.isOpen()) { input(() -> { Rs2GrandExchange.closeExchange(); return !Rs2GrandExchange.isOpen(); }); return; }
                if (DroSlayerRuntime.walkToBankAndOpen(config, "Cannonball purchase")) {
                    int surplus = Rs2Inventory.itemQuantity(ItemID.COINS_995) - originalCoins;
                    if (surplus > 0) { input(() -> Rs2Bank.depositX(ItemID.COINS_995, surplus)); return; }
                    log.info("Cannonball GE trip finished: bankStock={}", Rs2Bank.count(ItemID.STEEL_CANNONBALL));
                    phase = Phase.IDLE;
                }
                return;
            default: return;
        }
    }

    private GrandExchangeOfferState ownedState() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slot < 0 || slot >= offers.length) return null;
            var offer = offers[slot];
            if (offer != null && offer.getState() == GrandExchangeOfferState.EMPTY) return GrandExchangeOfferState.EMPTY;
            return offer != null && offer.getItemId() == ItemID.STEEL_CANNONBALL
                    && offer.getPrice() == price && offer.getTotalQuantity() == quantity
                    && offer.getState() != GrandExchangeOfferState.EMPTY ? offer.getState() : null;
        }).orElse(null);
    }
    private void fail(String reason) {
        log.warn("Cannonball purchase unavailable: {}; continuing existing optional supply policy", reason);
        DroSlayerRuntime.cancelWalk();
        phase = Phase.RETURN;
    }
    private boolean input(BooleanSupplier action) {
        return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, action);
    }
}
