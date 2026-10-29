package net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.SaleItem;

final class SaleCandidates {
    private SaleCandidates() { }

    static boolean allowed(SaleItem item) {
        return item.getItemId() > 0 && item.getInventoryId() > 0 && item.getQuantity() > 0
                && item.getItemId() != 995 && item.getInventoryId() != 995
                && !Bonds.IDS.contains(item.getItemId()) && !Bonds.IDS.contains(item.getInventoryId());
    }

    static List<SaleItem> ranked(List<SaleItem> items) {
        return items.stream().filter(SaleCandidates::allowed)
                .sorted(Comparator.comparing(SaleItem::value).reversed().thenComparingInt(SaleItem::getItemId))
                .collect(Collectors.toList());
    }

    static List<SaleItem> inventory(GameState state) {
        return ranked(state.getExchange().getInventorySales());
    }

    static int bankCount(GameState state, int id) {
        if (state.getExchange() == null || !state.getExchange().isBankLoaded()) return -1;
        return state.getExchange().getBankSales().stream().filter(i -> i.getItemId() == id)
                .mapToInt(SaleItem::getQuantity).sum();
    }

    static long held(GameState state, int id) {
        if (state.getExchange() == null) return -1;
        return state.getExchange().getInventorySales().stream().filter(i -> i.getItemId() == id)
                .mapToLong(SaleItem::getStockQuantity).sum();
    }

    static boolean hasBatch(GameState state) {
        ExchangeState exchange = state.getExchange();
        return exchange != null && (!inventory(state).isEmpty() || exchange.pendingSales() || exchange.collectableOffers());
    }
}
