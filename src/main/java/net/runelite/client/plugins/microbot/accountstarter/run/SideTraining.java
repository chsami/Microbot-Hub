package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.LootRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;

public final class SideTraining {
    public enum Next { KEEP_GOING, COOK, BURY, BANK }

    /** F2P logs only, so no members log is ever taken. */
    static final Map<Integer, Integer> LOGS = new LinkedHashMap<>();
    static {
        LOGS.put(ItemID.LOGS, 1);
        LOGS.put(ItemID.OAK_LOGS, 15);
        LOGS.put(ItemID.WILLOW_LOGS, 30);
        LOGS.put(ItemID.MAPLE_LOGS, 45);
        LOGS.put(ItemID.YEW_LOGS, 60);
    }
    static final Map<Integer, Integer> RAW_FISH = Map.of(ItemID.RAW_SHRIMP, 1, ItemID.RAW_ANCHOVIES, 1, ItemID.RAW_SARDINE, 1,
            ItemID.RAW_HERRING, 5, ItemID.RAW_TROUT, 15, ItemID.RAW_SALMON, 25);
    static final Set<Integer> BONES = LootRules.BONES;

    private SideTraining() {}

    public static Next plan(Map<Integer, Integer> inventory, int freeSlots, Choices c,
                            int cookingLevel, boolean fireOrRangeNear) {
        if (freeSlots > 0) return Next.KEEP_GOING;
        if (fireOrRangeNear && c.isCook() && XpGuard.allowed(Action.COOK, c.getProtectedSkills())
                && firstUsable(inventory, RAW_FISH, cookingLevel) != 0) {
            return Next.COOK;
        }
        if (c.isBury() && XpGuard.allowed(Action.BURY, c.getProtectedSkills())
                && inventory.keySet().stream().anyMatch(BONES::contains)) {
            return Next.BURY;
        }
        return Next.BANK;
    }

    static int firstUsable(Map<Integer, Integer> inventory, Map<Integer, Integer> needs, int level) {
        for (Map.Entry<Integer, Integer> e : needs.entrySet()) {
            if (level >= e.getValue() && inventory.getOrDefault(e.getKey(), 0) > 0) return e.getKey();
        }
        return 0;
    }
}
