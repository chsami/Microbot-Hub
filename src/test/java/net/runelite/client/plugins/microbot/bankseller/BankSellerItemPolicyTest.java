package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.ItemComposition;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Regression cases from the live bank; main() never starts or controls a client. */
public final class BankSellerItemPolicyTest {
    private static int assertions;

    private BankSellerItemPolicyTest() {
    }

    public static void main(String[] args) {
        membersItemsUseGrandExchangeEligibility();
        playerTradeableItemsCanStillBeRejectedByTheExchange();
        currenciesAndPlaceholdersStayInTheBank();
        notesUseTheirRealItemsEligibility();
        chargedVariantsKeepTheirOwnEligibility();
        missingDefinitionsRemainUnknown();
        realStackableItemsAreNotConvertedToNotes();
        System.out.println("BankSellerItemPolicyTest passed (" + assertions + " assertions)");
    }

    private static void membersItemsUseGrandExchangeEligibility() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        // Potato seed, curry and blood rune: live F2P definitions deny player
        // trading but permit GE selling. None should be left behind in the bank.
        for (int id : new int[]{5318, 2011, 565}) {
            definitions.put(id, item(id, false, true, true));
            check(Boolean.TRUE.equals(BankSellerItemPolicy.geEligibility(id, definitions::get)),
                    "Members item " + id + " must be sellable when its GE flag is true");
        }
    }

    private static void playerTradeableItemsCanStillBeRejectedByTheExchange() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        // Burnt shrimp, chocolate slice and burnt fish caused attempts to offer
        // items which can be traded with players but cannot be listed on GE.
        for (int id : new int[]{7954, 1901, 343, 367}) {
            definitions.put(id, item(id, true, false, false));
            check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(id, definitions::get)),
                    "Player-tradeable non-GE item " + id + " must be skipped before offering");
        }
    }

    private static void currenciesAndPlaceholdersStayInTheBank() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        for (int id : new int[]{995, 13204}) {
            // Explicit currency exclusion must hold even if metadata says true.
            definitions.put(id, item(id, true, true, false));
            check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(id, definitions::get)),
                    "Currency " + id + " must never be offered");
        }
        definitions.put(100001, definition(100001, true, true, false,
                -1, 5318, 14401, false));
        check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(100001, definitions::get)),
                "A bank placeholder must not become a sellable item through its linked ID");
    }

    private static void notesUseTheirRealItemsEligibility() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        definitions.put(5318, item(5318, false, true, true));
        definitions.put(5319, definition(5319, false, false, true,
                799, 5318, -1, true));
        check(BankSellerItemPolicy.canonicalItemId(5319, definitions::get) == 5318,
                "A potato seed note must resolve to its real item identity");
        check(Boolean.TRUE.equals(BankSellerItemPolicy.geEligibility(5319, definitions::get)),
                "A note must inherit the GE flag from its unnoted members item");

        definitions.put(1901, item(1901, true, false, false));
        definitions.put(1902, definition(1902, true, true, false,
                799, 1901, -1, true));
        check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(1902, definitions::get)),
                "A note must not make a non-GE chocolate slice sellable");

        definitions.put(100002, definition(100002, true, true, false,
                799, 995, -1, true));
        check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(100002, definitions::get)),
                "Currency exclusion must also hold after resolving a note");
    }

    private static void chargedVariantsKeepTheirOwnEligibility() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        definitions.put(11111, item(11111, true, false, true));
        definitions.put(11105, item(11105, false, true, true));
        check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(11111, definitions::get)),
                "A partially charged skills necklace must not be mapped to a sellable full variant");
        check(Boolean.TRUE.equals(BankSellerItemPolicy.geEligibility(11105, definitions::get)),
                "A fully charged skills necklace must remain eligible for GE selling");
    }

    private static void missingDefinitionsRemainUnknown() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        check(BankSellerItemPolicy.geEligibility(5318, definitions::get) == null,
                "A missing item definition is unknown, not a permanent rejection");
        check(BankSellerItemPolicy.canonicalItemId(5318, definitions::get) == -1,
                "An unknown definition must not invent a canonical identity");

        definitions.put(5319, definition(5319, false, false, true,
                799, 5318, -1, true));
        check(BankSellerItemPolicy.geEligibility(5319, definitions::get) == null,
                "A missing unnoted definition must remain retryable");
        definitions.put(5318, item(5318, false, true, true));
        check(Boolean.TRUE.equals(BankSellerItemPolicy.geEligibility(5319, definitions::get)),
                "The same note becomes eligible when its previously missing definition loads");

        for (int invalidId : new int[]{-1, 0}) {
            check(Boolean.FALSE.equals(BankSellerItemPolicy.geEligibility(invalidId, definitions::get)),
                    "Invalid item ID " + invalidId + " must not be eligible");
            check(BankSellerItemPolicy.canonicalItemId(invalidId, definitions::get) == -1,
                    "Invalid item ID " + invalidId + " must not resolve to a real identity");
        }
    }

    private static void realStackableItemsAreNotConvertedToNotes() {
        Map<Integer, ItemComposition> definitions = new HashMap<>();
        definitions.put(565, definition(565, false, true, true,
                -1, 566, -1, true));
        definitions.put(566, item(566, true, false, true));
        check(BankSellerItemPolicy.canonicalItemId(565, definitions::get) == 565,
                "A real stackable item must retain its ID even if it has a linked note ID");
        check(Boolean.TRUE.equals(BankSellerItemPolicy.geEligibility(565, definitions::get)),
                "A real stackable item must use its own GE flag, not the linked note flag");
    }

    private static ItemComposition item(int id, boolean playerTradeable, boolean geTradeable,
                                        boolean members) {
        return definition(id, playerTradeable, geTradeable, members, -1, -1, -1, false);
    }

    private static ItemComposition definition(int id, boolean playerTradeable, boolean geTradeable,
                                              boolean members, int note, int linkedNoteId,
                                              int placeholderTemplate, boolean stackable) {
        return (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                new Class<?>[]{ItemComposition.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId": return id;
                        case "isTradeable": return playerTradeable;
                        case "isGeTradeable": return geTradeable;
                        case "isMembers": return members;
                        case "getNote": return note;
                        case "getLinkedNoteId": return linkedNoteId;
                        case "getPlaceholderTemplateId": return placeholderTemplate;
                        case "isStackable": return stackable;
                        default: throw new AssertionError("Unexpected definition access: " + method.getName());
                    }
                });
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
