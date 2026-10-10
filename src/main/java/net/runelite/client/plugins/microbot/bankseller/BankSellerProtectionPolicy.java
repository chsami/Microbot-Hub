package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ItemComposition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

/** Capture item identities once, before the script can deposit or withdraw anything. */
final class BankSellerProtectionPolicy {
    private BankSellerProtectionPolicy() {
    }

    /**
     * Prefer a real container, which can legitimately omit trailing empty
     * slots. An absent container is not automatically empty: only a stable
     * logged-in client and a complete explicit slot snapshot can replace it.
     */
    static List<Integer> resolveContainerIds(Collection<Integer> containerIds,
                                             Collection<Integer> explicitSlotIds,
                                             int expectedSlotCount,
                                             boolean stableLoggedIn) {
        Collection<Integer> source = containerIds;
        if (source == null) {
            if (!stableLoggedIn || expectedSlotCount <= 0 || explicitSlotIds == null
                    || explicitSlotIds.size() != expectedSlotCount) {
                return null;
            }
            source = explicitSlotIds;
        }
        List<Integer> snapshot = new ArrayList<>(source.size());
        for (Integer itemId : source) {
            if (itemId == null || itemId < -1) {
                return null;
            }
            snapshot.add(itemId);
        }
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * Null means a starting snapshot is incomplete and selling must wait for a
     * complete one. Known empty containers produce an immutable empty set.
     * Notes share the real item's identity; charged variants remain distinct.
     */
    static Set<Integer> snapshotProtectedIds(Collection<Integer> inventoryIds,
                                            Collection<Integer> equipmentIds,
                                            IntFunction<ItemComposition> definitions) {
        if (inventoryIds == null || equipmentIds == null || definitions == null) {
            return null;
        }

        Set<Integer> protectedIds = new HashSet<>();
        if (!addCanonicalIds(inventoryIds, definitions, protectedIds)
                || !addCanonicalIds(equipmentIds, definitions, protectedIds)) {
            return null;
        }
        return Collections.unmodifiableSet(protectedIds);
    }

    private static boolean addCanonicalIds(Collection<Integer> itemIds,
                                           IntFunction<ItemComposition> definitions,
                                           Set<Integer> protectedIds) {
        for (Integer itemId : itemIds) {
            if (itemId == null) {
                return false;
            }
            // Empty inventory/equipment slots are represented by invalid IDs.
            if (itemId <= 0) {
                continue;
            }
            int canonicalId = BankSellerItemPolicy.canonicalItemId(itemId, definitions);
            if (canonicalId <= 0 || definitions.apply(canonicalId) == null) {
                return false;
            }
            protectedIds.add(canonicalId);
        }
        return true;
    }
}
