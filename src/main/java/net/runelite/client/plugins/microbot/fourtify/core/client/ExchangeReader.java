package net.runelite.client.plugins.microbot.fourtify.core.client;

import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtify.core.engine.GameReader;
import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeOffer;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.SaleItem;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adds an authoritative bank/inventory/offer snapshot in one client-thread hop. No saved bank mirror is used. */
public final class ExchangeReader implements GameReader {
    /** The game's inventory definition inv (93): size=28, even when its live array has fewer entries. */
    static final int INVENTORY_CAPACITY = 28;
    private final ClientReader reader;
    public ExchangeReader(Surroundings surroundings) { reader = new ClientReader(surroundings); }

    @Override
    public GameState read() {
        GameState state = reader.read();
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                read(Microbot.getClient(), Microbot.getItemManager(), state)).orElseThrow(() ->
                new IllegalStateException("the exchange snapshot was unavailable; tick skipped"));
    }

    static GameState read(Client client, ItemManager prices, GameState state) {
        boolean priceWarning = ExchangeWidgets.priceWarning(client);
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        // The game's ge_offers_index_drawslot script (798) opens slots 4 to 8 when varc 103 (playermember) is 1. The
        // server sets it by account membership (scripts 828 and 2498), so it does not depend on the world.
        boolean membersAccount = client.getVarcIntValue(VarClientID.PLAYERMEMBER) == 1;
        int slots = membersAccount ? 8 : 3;
        // A live check on 2026-10-28 found no INV container for a freshly logged-in empty inventory.
        // Accept that representation only when the ordinary inventory snapshot also reports all 28 slots empty.
        boolean emptyWithoutContainer = inventory == null && state.getItems().isEmpty()
                && state.getFreeSlots() == INVENTORY_CAPACITY;
        if (inventory == null && !emptyWithoutContainer || offers == null || offers.length < slots) {
            throw new IllegalStateException("inventory or exchange offers not loaded; tick skipped");
        }
        Item[] inventoryItems = inventory == null ? new Item[0] : inventory.getItems();
        ItemContainer bank = state.isBankOpen() ? client.getItemContainer(InventoryID.BANK) : null;
        ExchangeState.ExchangeStateBuilder exchange = ExchangeState.builder()
                .open(ExchangeWidgets.visible(client, ExchangeWidgets.CONTENTS))
                .setupOpen(ExchangeWidgets.visible(client, ExchangeWidgets.SETUP) || priceWarning)
                .detailsOpen(ExchangeWidgets.visible(client, ExchangeWidgets.DETAILS))
                .bankLoaded(bank != null)
                .membersAccount(membersAccount)
                .setupItemId(client.getVarpValue(ExchangeWidgets.ITEM_VARP))
                .setupQuantity(client.getVarbitValue(ExchangeWidgets.QUANTITY_VARBIT))
                .setupType(client.getVarbitValue(ExchangeWidgets.TYPE_VARBIT))
                .setupPrice(ExchangeWidgets.unitPrice(client))
                .priceInput(ExchangeWidgets.priceInput(client)).priceWarning(priceWarning)
                .inventorySales(sales(client, prices, inventoryItems, false, state.isMembersWorld()));
        if (bank != null) { exchange.bankSales(sales(client, prices, bank.getItems(), true, state.isMembersWorld())); }
        for (int slot = 0; slot < slots; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null || offer.getState() == null) {
                throw new IllegalStateException("exchange slot " + slot + " not loaded; tick skipped");
            }
            exchange.offer(new ExchangeOffer(slot, offer.getItemId(), offer.getTotalQuantity(),
                    offer.getQuantitySold(), offer.getPrice(), offer.getState()));
        }
        Map<Integer, Integer> items = new HashMap<>();
        for (Item item : inventoryItems) {
            if (item != null && item.getId() > 0 && item.getQuantity() > 0) {
                items.merge(item.getId(), item.getQuantity(), ExchangeReader::add);
            }
        }
        return state.toBuilder().clearItems().items(items)
                .freeSlots(Math.max(0, INVENTORY_CAPACITY - (inventory == null ? 0 : inventory.count())))
                .exchange(exchange.build()).build();
    }

    /** Keep the real carried form separate from the canonical offer ID; placeholders are never canonicalized. */
    static List<SaleItem> sales(Client client, ItemManager prices, Item[] items, boolean bank, boolean members) {
        Map<Integer, SaleItem> grouped = new LinkedHashMap<>();
        for (Item item : items) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) { continue; }
            ItemComposition definition = client.getItemDefinition(item.getId());
            if (definition.getPlaceholderTemplateId() != -1) { continue; }
            int id = definition.getNote() == -1 ? item.getId() : definition.getLinkedNoteId();
            if (id <= 0 || Bonds.IDS.contains(id) || Bonds.IDS.contains(item.getId()) || id == ItemID.COINS) { continue; }
            ItemComposition base = client.getItemDefinition(id);
            if (!base.isGeTradeable() || (base.isMembers() && !members)
                    || (!base.isStackable() && base.getLinkedNoteId() <= 0)) { continue; }
            int carried = bank ? (base.isStackable() ? id : base.getLinkedNoteId()) : item.getId();
            SaleItem previous = grouped.get(id);
            long stock = item.getQuantity() + (previous == null ? 0L : previous.getStockQuantity());
            int quantity = (int) Math.min(Integer.MAX_VALUE, stock);
            grouped.put(id, new SaleItem(id, previous == null ? carried : previous.getInventoryId(), base.getName(),
                    quantity, Math.max(0L, prices.getItemPrice(id)), stock));
        }
        List<SaleItem> result = new ArrayList<>(grouped.values());
        result.sort(Comparator.comparing(SaleItem::value).reversed().thenComparingInt(SaleItem::getItemId));
        return result;
    }

    private static int add(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, (long) a + b); }
}
