package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.mining.data.Rocks;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class MiningBankRule {
    private static final String UNCUT_GEM_PREFIX = "uncut ";
    private static final String DEFAULT_ITEM_TO_KEEP = "pickaxe";

    private MiningBankRule() {
    }

    static List<String> parseItemNames(String itemsToBank) {
        return Arrays.stream(itemsToBank.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    static List<String> parseItemsToKeep(String itemsToKeep) {
        List<String> keep = parseItemNames(itemsToKeep);
        return keep.isEmpty() ? Collections.singletonList(DEFAULT_ITEM_TO_KEEP) : keep;
    }

    static boolean shouldBank(String itemName, Collection<Rocks> rocks, List<String> itemNames) {
        if (itemName == null) {
            return false;
        }
        String name = itemName.toLowerCase(Locale.ROOT);
        if (itemNames.stream().anyMatch(name::contains)) {
            return true;
        }
        boolean minedResource = rocks.stream()
                .anyMatch(rock -> rock != null && rock.getOreName() != null && name.equals(rock.getOreName().toLowerCase(Locale.ROOT)));
        if (minedResource) {
            return true;
        }
        return name.startsWith(UNCUT_GEM_PREFIX);
    }
}
