package net.runelite.client.plugins.microbot.droslayer.walker.support;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.*;
import java.util.*;

/** Charged Bryophyta's staff proves one stored nature rune, never unlimited supply. */
public final class TravelRunes {
    private TravelRunes() {}
    public static Map<Runes, Integer> getRunes() { return getRunes(RuneFilter.builder().build()); }
    public static Map<Runes, Integer> getRunes(RuneFilter filter) {
        Rs2ItemModel weapon = filter.isIncludeEquipment() ? Rs2Equipment.get(EquipmentInventorySlot.WEAPON) : null;
        if (weapon == null || Rs2Staff.byItemId(weapon.getId()) != Rs2Staff.BRYOPHYTAS_STAFF)
            return Rs2Magic.getRunes(filter);
        Map<Runes, Integer> runes = new HashMap<>(Rs2Magic.getRunes(RuneFilter.builder()
                .includeInventory(filter.isIncludeInventory()).includeEquipment(false)
                .includeRunePouch(filter.isIncludeRunePouch()).includeBank(filter.isIncludeBank())
                .includeComboRunes(filter.isIncludeComboRunes()).build()));
        runes.merge(Runes.NATURE, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
        Rs2ItemModel shield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (shield != null) Rs2Tome.byItemId(shield.getId()).getRunes()
                .forEach(rune -> runes.put(rune, Integer.MAX_VALUE));
        return runes;
    }
}
