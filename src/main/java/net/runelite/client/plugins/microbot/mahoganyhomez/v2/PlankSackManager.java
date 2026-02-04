package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.primitives.Ints;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

/**
 * Manages plank sack state and operations
 */
@Slf4j
@Singleton
public class PlankSackManager {

    private static final List<Integer> PLANK_IDS = Arrays.asList(
        ItemID.PLANK,
        ItemID.OAK_PLANK,
        ItemID.TEAK_PLANK,
        ItemID.MAHOGANY_PLANK
    );

    public static final List<String> PLANK_NAMES = Arrays.asList(
        "Plank",
        "Oak plank",
        "Teak plank",
        "Mahogany plank"
    );

    private final Client client;

    @Getter
    private int plankCount = -1;

    @Inject
    public PlankSackManager(Client client) {
        this.client = client;
    }

    public void setPlankCount(int count) {
        this.plankCount = Ints.constrainToRange(count, 0, 28);
        log.info("New plank count: {}", this.plankCount);
    }

    public void processChatMessage(String message) {
        final String cleanMessage = Text.removeTags(message);

        if (cleanMessage.contains("planks:")) {
            log.info("Parsing");
            parsePlankCountFromMessage(cleanMessage);
        } else if (cleanMessage.equals("Your sack is full.")) {
            setPlankCount(28);
        } else if (cleanMessage.equals("Your sack is currently empty.")) {
            setPlankCount(0);
        }
    }

    public Multiset<Integer> createInventorySnapshot() {
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        return createSnapshot(container);
    }

    private void parsePlankCountFromMessage(String message) {
        try {
            int totalPlanks = Arrays.stream(message.split(","))
                .mapToInt(s -> Integer.parseInt(s.split(":\u00A0")[1]))
                .sum();
            setPlankCount(totalPlanks);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse plank count from message: {}", message, e);
        }
    }

    private Multiset<Integer> createSnapshot(ItemContainer container) {
        if (container == null) {
            return HashMultiset.create();
        }

        Multiset<Integer> snapshot = HashMultiset.create();
        Arrays.stream(container.getItems())
            .filter(item -> PLANK_IDS.contains(item.getId()))
            .forEach(item -> snapshot.add(item.getId(), item.getQuantity()));

        return snapshot;
    }

    public boolean isFull() {
        return plankCount >= 28;
    }

    public boolean isEmpty() {
        return plankCount == 0;
    }
}
