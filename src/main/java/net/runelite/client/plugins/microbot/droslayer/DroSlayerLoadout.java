package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/** Private inventory-slot copy for Slayer overlays; never serializes UI metadata. */
final class DroSlayerLoadout {
    private DroSlayerLoadout() { }

    static InventorySetup copyForFoodOverlay(InventorySetup source) {
        return new InventorySetup(
                source.getInventory() == null ? new ArrayList<>() : new ArrayList<>(source.getInventory()),
                source.getEquipment() == null ? new ArrayList<>() : new ArrayList<>(source.getEquipment()),
                source.getRune_pouch(), source.getBoltPouch(), source.getQuiver(),
                source.getAdditionalFilteredItems() == null ? new HashMap<>() : new HashMap<>(source.getAdditionalFilteredItems()), source.getName(), source.getNotes(),
                source.getHighlightColor(), source.isHighlightDifference(), source.getDisplayColor(),
                source.isFilterBank(), source.isUnorderedHighlight(), source.getSpellBook(),
                source.isFavorite(), source.getIconID());
    }

    /** Strip obsolete potion rows from this runtime copy, leaving the saved setup intact. */
    static void removeUnneededAntifire(InventorySetup copy, List<DroSlayerTaskRequirements.Requirement> requirements) {
        if (DroSlayerBankSupplies.needsAntifire(requirements)) return;
        copy.getInventory().replaceAll(row -> row != null && DroSlayerBankSupplies.isAntifire(row.getName())
                ? InventorySetupsItem.getDummyItem() : row);
        copy.getAdditionalFilteredItems().values().removeIf(row -> row != null
                && DroSlayerBankSupplies.isAntifire(row.getName()));
    }

    /** Retain carried task extras without adding withdrawal requirements or fixed inventory slots. */
    static void retainCarried(InventorySetup copy, List<Rs2ItemModel> supplies) {
        for (Rs2ItemModel item : supplies) {
            if (item == null || item.isNoted() || item.getId() <= 0 || item.getQuantity() <= 0) continue;
            copy.getAdditionalFilteredItems().putIfAbsent(item.getId(),
                    new InventorySetupsItem(item.getId(), item.getName(), item.getQuantity(), false,
                            InventorySetupsStackCompareID.None, false, -1));
        }
    }

    static void overrideHands(InventorySetup copy, Rs2ItemModel bracelet) {
        int hands = net.runelite.api.EquipmentInventorySlot.GLOVES.getSlotIdx();
        if (bracelet == null || copy.getEquipment().size() <= hands) return;
        copy.getEquipment().set(hands, new InventorySetupsItem(bracelet.getId(), bracelet.getName(), 1,
                false, InventorySetupsStackCompareID.None, false, -1));
    }

    /** Limit runtime inventory requirements to one usable glory; saved rows remain untouched. */
    static void limitInventoryGlories(InventorySetup copy, Rs2ItemModel preferred) {
        copy.getAdditionalFilteredItems().values().removeIf(row -> row != null
                && DroSlayerBankSupplies.isGlory(row.getName()));
        boolean kept = false;
        for (int index = 0; index < copy.getInventory().size(); index++) {
            InventorySetupsItem row = copy.getInventory().get(index);
            if (row == null || row.getId() <= 0 || !DroSlayerBankSupplies.isGlory(row.getName())) continue;
            if (kept || (preferred == null && DroSlayerBankSupplies.chargedJewelryFamily(row.getName()) == null)) {
                copy.getInventory().set(index,InventorySetupsItem.getDummyItem());
                continue;
            }
            copy.getInventory().set(index,new InventorySetupsItem(
                    preferred == null ? row.getId() : preferred.getId(),
                    preferred == null ? row.getName() : preferred.getName(),1,false,
                    row.getStackCompare(),row.isLocked(),row.getSlot()));
            kept = true;
        }
    }

    /**
     * Bank-local substitute only; never change the user's saved setup or accept one-dose vials.
     * Match potion dose by item ID first, because bank item-model names can vary in spacing/formatting.
     */
    static void fallbackSuperCombat(InventorySetup copy, List<Rs2ItemModel> carried, List<Rs2ItemModel> bank) {
        if (bank.stream().anyMatch(item -> superCombatDose(item) == 4
                && item.getQuantity() > 0 && !item.isNoted())) return;

        List<Rs2ItemModel> available = new ArrayList<>(carried);
        available.addAll(bank);
        java.util.Map<Integer, Integer> counts = new HashMap<>();
        for (Rs2ItemModel item : available) {
            if (item != null && !item.isNoted() && item.getId() > 0) {
                counts.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        copy.getInventory().replaceAll(row -> {
            if (row == null || superCombatDose(row.getId(), row.getName()) != 4) return row;
            for (int d = 3; d >= 2; d--) {
                final int dose = d;
                Rs2ItemModel item = available.stream()
                        .filter(value -> value != null && !value.isNoted()
                                && superCombatDose(value) == dose
                                && counts.getOrDefault(value.getId(), 0) >= row.getQuantity())
                        .findFirst().orElse(null);
                if (item == null) continue;
                counts.merge(item.getId(), -row.getQuantity(), Integer::sum);
                return new InventorySetupsItem(item.getId(), item.getName(), row.getQuantity(), false,
                        row.getStackCompare(), row.isLocked(), row.getSlot());
            }
            return row;
        });
    }

    private static int superCombatDose(Rs2ItemModel item) {
        return item == null ? 0 : superCombatDose(item.getId(), item.getName());
    }

    private static int superCombatDose(int id, String name) {
        switch (id) {
            case 12695: return 4;
            case 12697: return 3;
            case 12699: return 2;
            case 12701: return 1;
            default:
                if (name == null) return 0;
                String normalized = name.toLowerCase(java.util.Locale.ROOT)
                        .replace(" ", "").replace("_", "");
                if (!normalized.startsWith("supercombatpotion(")) return 0;
                int close = normalized.indexOf(')');
                if (close < 0) return 0;
                try {
                    return Integer.parseInt(normalized.substring("supercombatpotion(".length(), close));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
        }
    }

    /** Reuse a usable charged variant already satisfying a saved travel-jewelry group. */
    static void reuseChargedJewelry(InventorySetup copy, List<Rs2ItemModel> carried) {
        copy.getInventory().replaceAll(row -> {
            if (row == null || row.getId() <= 0) return row;
            String family = DroSlayerBankSupplies.chargedJewelryFamily(row.getName());
            if (family == null) return row;
            int needed = copy.getInventory().stream().filter(other -> other != null
                    && family.equals(DroSlayerBankSupplies.chargedJewelryFamily(other.getName())))
                    .mapToInt(InventorySetupsItem::getQuantity).sum();
            Rs2ItemModel live = carried.stream().filter(item -> !item.isNoted()
                    && family.equals(DroSlayerBankSupplies.chargedJewelryFamily(item.getName())))
                    .filter(item -> carried.stream().filter(other -> !other.isNoted() && other.getId() == item.getId())
                            .mapToInt(Rs2ItemModel::getQuantity).sum() >= needed).findFirst().orElse(null);
            if (live == null || live.getId() == row.getId()) return row;
            return new InventorySetupsItem(live.getId(), live.getName(), row.getQuantity(), false,
                    row.getStackCompare(), row.isLocked(), row.getSlot());
        });
    }
}
