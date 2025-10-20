package net.runelite.client.plugins.microbot.wildernessagility;

import com.google.common.collect.ImmutableSet;
import net.runelite.client.game.ItemManager;

import java.util.HashMap;
import static net.runelite.api.ItemID.*;

public class WildernessAgilityItems {

    private final ItemManager itemManager;
    private final HashMap<String, Integer> nameToItemID = new HashMap<>();

    public WildernessAgilityItems(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    // Items from https://oldschool.runescape.wiki/w/Agility_dispenser
    static final ImmutableSet<Integer> ITEM_IDS = ImmutableSet.of(
            // All Laps
            BLIGHTED_ANGLERFISH, BLIGHTED_MANTA_RAY, BLIGHTED_KARAMBWAN, BLIGHTED_SUPER_RESTORE4,
            MITHRIL_PLATESKIRT, MITHRIL_PLATELEGS, ADAMANT_PLATEBODY, RUNE_MED_HELM, ADAMANT_FULL_HELM, ADAMANT_PLATELEGS,

            // Laps 1-15
            STEEL_PLATEBODY,

            // Lap 1-30
            MITHRIL_CHAINBODY,

            // Lap 16-60+
            RUNE_CHAINBODY, RUNE_KITESHIELD
    );

    public Integer nameToItemId(String name) {
        // All wildy agility items noted so get noted version
        return itemManager.getItemComposition(nameToItemID.get(name)).getLinkedNoteId();
    }

    public void setupWildernessItemsIfEmpty() {
        if (nameToItemID.isEmpty()) {
            for (Integer itemID : ITEM_IDS) {
                nameToItemID.put(itemManager.getItemComposition(itemID).getName(), itemID);
            }
        }
    }
}
