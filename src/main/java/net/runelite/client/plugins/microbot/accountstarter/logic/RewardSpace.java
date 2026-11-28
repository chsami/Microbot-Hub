package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.RewardItem;

public final class RewardSpace {
    // only the stackables Jon hands out: extend if rewards change
    private static final Set<Integer> STACKABLE = Set.of(995, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 882, 884, 313);

    private RewardSpace() {
    }

    public static Set<Integer> itemIds(List<ApTask> tasks) {
        return tasks.stream().flatMap(t -> t.f2pRewards().stream()).map(RewardItem::getItemId).collect(Collectors.toSet());
    }

    public static Set<Integer> toBank(List<ApTask> tasks, Set<Integer> usedLater) {
        Set<Integer> out = new HashSet<>(itemIds(tasks));
        out.removeAll(usedLater);
        return out;
    }

    public static int slotsNeeded(List<ApTask> unclaimed, Map<Integer, Integer> inventory) {
        Set<Integer> stacks = new HashSet<>();
        int slots = 0;
        for (ApTask task : unclaimed) {
            for (RewardItem item : task.f2pRewards()) {
                if (!STACKABLE.contains(item.getItemId())) {
                    slots += item.getQuantity();
                } else if (!inventory.containsKey(item.getItemId()) && stacks.add(item.getItemId())) {
                    slots++;
                }
            }
        }
        return slots;
    }
}
