package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ItemComposition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankSellerItemPolicyTest {

    @Test
    void notesResolveToTheirRealItem() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        definitions.put(5318, definition(-1, -1));
        definitions.put(5319, definition(799, 5318));
        assertEquals(5318, BankSellerItemPolicy.canonicalItemId(5319, definitions::get),
                "A potato seed note must resolve to its real item identity");
        assertEquals(5318, BankSellerItemPolicy.canonicalItemId(5318, definitions::get),
                "An unnoted item keeps its own identity");
    }

    @Test
    void realStackableItemsAreNotConvertedToNotes() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        definitions.put(565, definition(-1, 566));
        assertEquals(565, BankSellerItemPolicy.canonicalItemId(565, definitions::get),
                "A real stackable item must retain its ID even if it has a linked note ID");
    }

    @Test
    void unknownOrInvalidItemsHaveNoIdentity() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        assertEquals(-1, BankSellerItemPolicy.canonicalItemId(5318, definitions::get),
                "An unknown definition must not invent a canonical identity");
        definitions.put(100001, definition(799, -1));
        assertEquals(-1, BankSellerItemPolicy.canonicalItemId(100001, definitions::get),
                "A note without a linked item must not resolve to a real identity");
        for (int invalidId : new int[]{-1, 0}) {
            assertEquals(-1, BankSellerItemPolicy.canonicalItemId(invalidId, definitions::get),
                    "Invalid item ID " + invalidId + " must not resolve to a real identity");
        }
    }

    private static ItemComposition definition(int note, int linkedNoteId) {
        return (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                new Class<?>[]{ItemComposition.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getNote": return note;
                        case "getLinkedNoteId": return linkedNoteId;
                        default: throw new AssertionError("Unexpected definition access: " + method.getName());
                    }
                });
    }
}
