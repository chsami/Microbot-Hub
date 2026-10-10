package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ItemComposition;

import java.util.function.IntFunction;

/** Resolves the item identity shared by noted and unnoted copies. */
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
}
