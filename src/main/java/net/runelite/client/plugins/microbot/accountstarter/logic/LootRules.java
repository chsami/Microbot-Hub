package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Set;
import net.runelite.api.gameval.ItemID;

public final class LootRules {
    public static final Set<Integer> BONES = Set.of(ItemID.BONES, ItemID.BIG_BONES);

    private LootRules() {}

    public static boolean take(int itemId, int quantity, Choices c, boolean ownArrow) {
        if (quantity <= 0) return false;
        if (ownArrow || itemId == ItemID.COINS) return true;
        return BONES.contains(itemId) && c.isBury() && XpGuard.allowed(Action.BURY, c.getProtectedSkills());
    }
}
