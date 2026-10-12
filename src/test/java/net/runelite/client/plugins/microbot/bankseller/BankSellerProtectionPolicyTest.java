package net.runelite.client.plugins.microbot.bankseller;

import org.junit.jupiter.api.Test;
import net.runelite.api.ItemComposition;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure starting-item protection checks; never starts or controls a client. */
public final class BankSellerProtectionPolicyTest {
    @Test
    void protectsInventoryAndEquipmentTogether() {
        Map<Integer, ItemComposition> definitions = definitions(4151, 11840, 565);
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(565, 4151), Arrays.asList(11840, 4151), definitions::get);
        check(protectedIds != null, "Known starting containers must produce a snapshot");
        check(protectedIds.size() == 3, "Inventory and equipment IDs must be deduplicated together");
        check(protectedIds.contains(4151), "Starting inventory equipment must be protected");
        check(protectedIds.contains(11840), "Worn equipment must be protected");
        check(protectedIds.contains(565), "Starting inventory supplies must be protected");
        check(!protectedIds.contains(5318), "Unrelated bank items must remain available to sell");
    }

    @Test
    void notesAndUnnotedBankDuplicatesUseOneIdentity() {
        Map<Integer, ItemComposition> definitions = definitions(5318);
        definitions.put(5319, definition(5319, 799, 5318));
        Set<Integer> fromNote = BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(5319), Collections.emptyList(), definitions::get);
        check(fromNote.equals(Collections.singleton(5318)),
                "Starting notes must protect the unnoted bank item ID");
        Set<Integer> fromBoth = BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(5318, 5319, 5319), Collections.emptyList(), definitions::get);
        check(fromBoth.equals(Collections.singleton(5318)),
                "Noted and unnoted copies must not create separate protection identities");
        check(fromBoth.contains(BankSellerItemPolicy.canonicalItemId(5319, definitions::get)),
                "A later withdrawn note must match the protected canonical identity");
        check(!fromBoth.contains(5319), "Raw note IDs must not enter the protected set");
    }

    @Test
    void chargedVariantsRemainDistinct() {
        Map<Integer, ItemComposition> definitions = definitions(11111, 11105, 2552, 2566);
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(11111), Collections.singletonList(2566), definitions::get);
        check(protectedIds.contains(11111), "The starting partially charged necklace must be protected");
        check(!protectedIds.contains(11105), "A different necklace charge variant must not be conflated");
        check(protectedIds.contains(2566), "The worn charged ring's exact variant must be protected");
        check(!protectedIds.contains(2552), "A different ring charge variant must remain independent");
    }

    @Test
    void stackableItemsKeepTheirOwnIdentity() {
        Map<Integer, ItemComposition> definitions = definitions(565, 566);
        definitions.put(565, definition(565, -1, 566));
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(565), Collections.emptyList(), definitions::get);
        check(protectedIds.equals(Collections.singleton(565)),
                "A real stackable item must not be protected under its linked note ID");
    }

    @Test
    void unknownInputsFailClosed() {
        Map<Integer, ItemComposition> definitions = definitions(4151);
        check(BankSellerProtectionPolicy.snapshotProtectedIds(null, Collections.emptyList(), definitions::get) == null,
                "An unavailable inventory must not be treated as empty");
        check(BankSellerProtectionPolicy.snapshotProtectedIds(Collections.emptyList(), null, definitions::get) == null,
                "Unavailable equipment must not be treated as empty");
        check(BankSellerProtectionPolicy.snapshotProtectedIds(Collections.emptyList(), Collections.emptyList(), null) == null,
                "Unavailable definition lookup must fail closed");
        check(BankSellerProtectionPolicy.snapshotProtectedIds(null, null, null) == null,
                "A completely unknown starting snapshot must fail closed");
    }

    @Test
    void unknownDefinitionsFailClosed() {
        Map<Integer, ItemComposition> definitions = definitions(4151);
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(4151, 11840), Collections.emptyList(), definitions::get) == null,
                "One unknown inventory definition must reject the whole snapshot");
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(4151), Collections.singletonList(11840), definitions::get) == null,
                "One unknown worn item definition must reject the whole snapshot");
        definitions.put(5319, definition(5319, 799, 5318));
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(5319), Collections.emptyList(), definitions::get) == null,
                "A note with an unavailable real-item definition must fail closed");
        definitions.put(5318, definition(5318, -1, -1));
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(5319), Collections.emptyList(), definitions::get)
                .equals(Collections.singleton(5318)),
                "The same note must become protectable when its real-item definition is available");
        definitions.put(90000, definition(90000, 799, -1));
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.singletonList(90000), Collections.emptyList(), definitions::get) == null,
                "A malformed note identity must reject the snapshot");
    }

    @Test
    void ignoresEmptySlotsButNotUnknownEntries() {
        Map<Integer, ItemComposition> definitions = definitions(4151);
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(-1, 0, 4151), Arrays.asList(-1, Integer.MIN_VALUE), definitions::get);
        check(protectedIds.equals(Collections.singleton(4151)),
                "Nonpositive empty-slot IDs must not prevent a valid snapshot");
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(4151, null), Collections.emptyList(), definitions::get) == null,
                "An unknown inventory entry must reject a partially known snapshot");
        check(BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.emptyList(), Arrays.asList(-1, null), definitions::get) == null,
                "An unknown equipment entry must reject a partially known snapshot");
    }

    @Test
    void completeEmptySnapshotsAreValid() {
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.emptyList(), Collections.emptyList(), id -> {
                    throw new AssertionError("Empty containers must not request definitions");
                });
        check(protectedIds != null && protectedIds.isEmpty(),
                "Known empty inventory and equipment must produce a valid empty snapshot");
        Set<Integer> emptySlots = BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(-1, 0), Collections.singletonList(-1), id -> {
                    throw new AssertionError("Invalid slots must not request definitions");
                });
        check(emptySlots != null && emptySlots.isEmpty(),
                "Containers with only explicit empty slots must produce a valid empty snapshot");
    }

    @Test
    void snapshotIsImmutableAndIndependentOfLaterChanges() {
        Map<Integer, ItemComposition> definitions = definitions(4151, 11840);
        List<Integer> inventory = new ArrayList<>(Collections.singletonList(4151));
        List<Integer> equipment = new ArrayList<>(Collections.singletonList(11840));
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(inventory, equipment, definitions::get);
        inventory.clear();
        equipment.clear();
        definitions.clear();
        check(protectedIds.size() == 2 && protectedIds.contains(4151) && protectedIds.contains(11840),
                "Clearing live containers or definitions must not change captured IDs");
        expectImmutable(() -> protectedIds.add(565), "Captured IDs must not accept later additions");
        expectImmutable(() -> protectedIds.remove(4151), "Captured IDs must not lose protection through mutation");
        expectImmutable(protectedIds::clear, "Captured IDs must not be cleared");
        Set<Integer> empty = BankSellerProtectionPolicy.snapshotProtectedIds(
                Collections.emptyList(), Collections.emptyList(), id -> null);
        expectImmutable(() -> empty.add(4151), "An empty snapshot must also be immutable");
    }

    @Test
    void currenciesRemainProtectedWithoutInventingOtherIds() {
        Map<Integer, ItemComposition> definitions = definitions(995, 13204);
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                Arrays.asList(995, 13204), Collections.emptyList(), definitions::get);
        check(protectedIds.equals(Set.of(995, 13204)),
                "Starting currencies may be protected; independent sellability policy keeps them unsellable");
    }

    @Test
    void fullyEmptySlotSnapshotsAllowAbsentContainers() {
        List<Integer> inventory = BankSellerProtectionPolicy.resolveContainerIds(
                null, Collections.nCopies(28, -1), 28, true);
        List<Integer> equipment = BankSellerProtectionPolicy.resolveContainerIds(
                null, Collections.nCopies(11, 0), 11, true);
        check(inventory != null && inventory.size() == 28,
                "Twenty-eight explicit empty inventory slots must replace an absent container");
        check(equipment != null && equipment.size() == 11,
                "Eleven explicit empty worn slots must allow a naked account to start");
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                inventory, equipment, id -> {
                    throw new AssertionError("Explicit empty slots must not request definitions");
                });
        check(protectedIds != null && protectedIds.isEmpty(),
                "Verified empty inventory and naked equipment must produce no protected item IDs");
    }

    @Test
    void explicitRingAndAmmoRemainProtected() {
        Map<Integer, ItemComposition> definitions = definitions(2552, 886, 5318);
        definitions.put(5319, definition(5319, 799, 5318));
        List<Integer> wornSlots = new ArrayList<>(Collections.nCopies(11, -1));
        // The caller supplies the eleven real worn slots; ring and ammo are the
        // final two. Player appearance alone would miss these invisible items.
        wornSlots.set(9, 2552);
        wornSlots.set(10, 886);
        List<Integer> equipment = BankSellerProtectionPolicy.resolveContainerIds(
                null, wornSlots, 11, true);
        List<Integer> inventorySlots = new ArrayList<>(Collections.nCopies(28, -1));
        inventorySlots.set(0, 5319);
        List<Integer> inventory = BankSellerProtectionPolicy.resolveContainerIds(
                null, inventorySlots, 28, true);
        Set<Integer> protectedIds = BankSellerProtectionPolicy.snapshotProtectedIds(
                inventory, equipment, definitions::get);
        check(protectedIds != null && protectedIds.size() == 3,
                "Slot fallback must preserve all positive inventory and worn item IDs");
        check(protectedIds.contains(2552), "A worn ring must stay protected when its container is absent");
        check(protectedIds.contains(886), "Worn ammo must stay protected when its container is absent");
        check(protectedIds.contains(5318) && !protectedIds.contains(5319),
                "A fallback inventory note must still protect its unnoted bank copies");
        check(protectedIds.contains(BankSellerItemPolicy.canonicalItemId(5319, definitions::get)),
                "Later noted bank withdrawals must match fallback-captured protection");
    }

    @Test
    void incompleteSlotSnapshotsFailClosed() {
        check(BankSellerProtectionPolicy.resolveContainerIds(null, null, 11, true) == null,
                "An absent equipment container without UI slots must remain unknown");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.emptyList(), 11, true) == null,
                "No occupied UI descendants must not be mistaken for eleven explicit empty slots");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.nCopies(10, -1), 11, true) == null,
                "One missing worn slot must reject a partial snapshot");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.nCopies(12, -1), 11, true) == null,
                "A wrong equipment-slot count must reject the snapshot");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.nCopies(27, -1), 28, true) == null,
                "One missing inventory slot must reject a partial snapshot");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.nCopies(29, -1), 28, true) == null,
                "Extra inventory slots must reject the snapshot");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.nCopies(11, -1), 11, false) == null,
                "An unstable login must not use otherwise complete equipment UI data");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.nCopies(28, -1), 28, false) == null,
                "An unstable login must not use otherwise complete inventory UI data");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.emptyList(), 0, true) == null,
                "A zero expected slot count must not fabricate a known empty container");
        check(BankSellerProtectionPolicy.resolveContainerIds(null, Collections.emptyList(), -1, true) == null,
                "A negative expected slot count must fail closed");
    }

    @Test
    void invalidSlotValuesFailClosed() {
        List<Integer> missingSlot = new ArrayList<>(Collections.nCopies(11, -1));
        missingSlot.set(10, null);
        check(BankSellerProtectionPolicy.resolveContainerIds(null, missingSlot, 11, true) == null,
                "An unknown ammo slot must not be treated as empty");
        List<Integer> invalidSlot = new ArrayList<>(Collections.nCopies(28, -1));
        invalidSlot.set(0, -2);
        check(BankSellerProtectionPolicy.resolveContainerIds(null, invalidSlot, 28, true) == null,
                "Only minus one or zero may represent explicit empty slots");
        invalidSlot.set(0, Integer.MIN_VALUE);
        check(BankSellerProtectionPolicy.resolveContainerIds(null, invalidSlot, 28, true) == null,
                "An invalid negative UI value must remain unknown");
        check(BankSellerProtectionPolicy.resolveContainerIds(
                Arrays.asList(4151, null), Collections.nCopies(11, -1), 11, true) == null,
                "An incomplete real container must not be replaced by apparently empty UI data");
        check(BankSellerProtectionPolicy.resolveContainerIds(
                Arrays.asList(4151, -2), Collections.nCopies(11, -1), 11, true) == null,
                "An invalid real-container value must not be replaced by empty UI data");
    }

    @Test
    void realContainerTakesPriorityWithoutInventingEmptySlots() {
        List<Integer> trimmedContainer = BankSellerProtectionPolicy.resolveContainerIds(
                Arrays.asList(4151, -1, 0), Collections.nCopies(11, -1), 11, true);
        check(trimmedContainer.equals(Arrays.asList(4151, -1, 0)),
                "A real container must outrank conflicting explicit slot data");
        List<Integer> duringLogin = BankSellerProtectionPolicy.resolveContainerIds(
                Collections.singletonList(4151), null, 11, false);
        check(duringLogin.equals(Collections.singletonList(4151)),
                "Known real-container IDs must not depend on UI loading or trailing empty slots");
        List<Integer> realEmpty = BankSellerProtectionPolicy.resolveContainerIds(
                Collections.emptyList(), Collections.singletonList(4151), 11, false);
        check(realEmpty != null && realEmpty.isEmpty(),
                "A known empty real container must outrank incomplete or stale UI slots");
    }

    @Test
    void resolvedSnapshotsAreCopiedAndImmutable() {
        List<Integer> container = new ArrayList<>(Arrays.asList(4151, -1));
        List<Integer> fromContainer = BankSellerProtectionPolicy.resolveContainerIds(container, null, 28, false);
        container.set(0, 11840);
        container.clear();
        check(fromContainer.equals(Arrays.asList(4151, -1)),
                "Later changes to a real container must not change the captured slot IDs");
        expectImmutable(() -> fromContainer.add(11840), "A captured real-container list must be immutable");
        expectImmutable(() -> fromContainer.set(0, 11840), "Captured real-container IDs must not be replaced");
        List<Integer> slots = new ArrayList<>(Collections.nCopies(11, -1));
        slots.set(9, 2552);
        List<Integer> fromSlots = BankSellerProtectionPolicy.resolveContainerIds(null, slots, 11, true);
        slots.clear();
        check(fromSlots.size() == 11 && fromSlots.get(9) == 2552,
                "Later UI changes must not change the captured fallback slot IDs");
        expectImmutable(fromSlots::clear, "A captured fallback list must not be cleared");
        expectImmutable(() -> fromSlots.remove(9), "Protected fallback slot IDs must not be removed");
        List<Integer> emptyContainer = BankSellerProtectionPolicy.resolveContainerIds(
                Collections.emptyList(), null, 28, false);
        expectImmutable(() -> emptyContainer.add(4151), "Known empty real-container snapshots must be immutable");
    }

    private static Map<Integer, ItemComposition> definitions(int... itemIds) {
        Map<Integer, ItemComposition> result = new HashMap<>();
        for (int itemId : itemIds) {
            result.put(itemId, definition(itemId, -1, -1));
        }
        return result;
    }

    private static ItemComposition definition(int id, int note, int linkedNoteId) {
        return (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                new Class<?>[]{ItemComposition.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId": return id;
                        case "getNote": return note;
                        case "getLinkedNoteId": return linkedNoteId;
                        default: throw new AssertionError("Unexpected definition access: " + method.getName());
                    }
                });
    }

    private static void expectImmutable(Runnable mutation, String message) {
        try {
            mutation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
