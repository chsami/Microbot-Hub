package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ItemComposition;

import java.util.function.IntFunction;

/** Item definitions distinguish player trading from eligibility to sell on the GE. */
final class BankSellerItemPolicy {
    private BankSellerItemPolicy() {
    }

    /** Resolve notes only; a real stackable item must never be mapped to its note. */
    static int canonicalItemId(int itemId, IntFunction<ItemComposition> definitions) {
        if (itemId <= 0) {
            return -1;
        }
        ItemComposition definition = definitions.apply(itemId);
        if (definition == null) {
            return -1;
        }
        if (definition.getNote() != -1) {
            return definition.getLinkedNoteId() > 0 ? definition.getLinkedNoteId() : -1;
        }
        return itemId;
    }

    /** Null means the definition could not be read and must be retried, not cached. */
    static Boolean geEligibility(int itemId, IntFunction<ItemComposition> definitions) {
        if (itemId <= 0) {
            return false;
        }
        ItemComposition original = definitions.apply(itemId);
        if (original == null) {
            return null;
        }
        if (original.getPlaceholderTemplateId() != -1) {
            return false;
        }
        int canonicalId = canonicalItemId(itemId, definitions);
        if (canonicalId <= 0 || canonicalId == 995 || canonicalId == 13204) {
            return false;
        }
        ItemComposition definition = canonicalId == itemId ? original : definitions.apply(canonicalId);
        if (definition == null) {
            return null;
        }
        return definition.getPlaceholderTemplateId() == -1 && definition.isGeTradeable();
    }
}
