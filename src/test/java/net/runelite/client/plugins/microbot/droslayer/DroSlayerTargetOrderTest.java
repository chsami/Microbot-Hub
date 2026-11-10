package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DroSlayerTargetOrderTest {

    @Test
    void sortsByPrimaryThenSecondaryKey() {
        List<String> items = Arrays.asList("far-free", "near-free", "far-mine", "near-mine");
        Map<String, Integer> primary = Map.of("far-free", 1, "near-free", 1, "far-mine", 0, "near-mine", 0);
        Map<String, Integer> secondary = Map.of("far-free", 9, "near-free", 2, "far-mine", 7, "near-mine", 1);

        List<String> sorted = DroSlayerScript.sortBySnapshotKeys(items, primary::get, secondary::get);

        assertEquals(Arrays.asList("near-mine", "far-mine", "near-free", "far-free"), sorted);
    }

    @Test
    void readsEachKeyOncePerItemSoLiveKeysCannotBreakTheSort() {
        List<Integer> items = IntStream.range(0, 500).boxed().collect(Collectors.toList());
        Random random = new Random(42);
        Map<Integer, Integer> primaryReads = new HashMap<>();
        Map<Integer, Integer> secondaryReads = new HashMap<>();

        List<Integer> sorted = DroSlayerScript.sortBySnapshotKeys(items,
                item -> {
                    primaryReads.merge(item, 1, Integer::sum);
                    return random.nextInt(2);
                },
                item -> {
                    secondaryReads.merge(item, 1, Integer::sum);
                    return random.nextInt(50);
                });

        assertEquals(items.size(), sorted.size());
        assertEquals(new ArrayList<>(items), sorted.stream().sorted().collect(Collectors.toList()));
        assertTrue(primaryReads.values().stream().allMatch(count -> count == 1));
        assertTrue(secondaryReads.values().stream().allMatch(count -> count == 1));
        assertEquals(items.size(), primaryReads.size());
    }

}
