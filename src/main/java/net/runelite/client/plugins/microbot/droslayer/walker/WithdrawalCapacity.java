package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Map;
import java.util.function.IntPredicate;

final class WithdrawalCapacity
{
    private WithdrawalCapacity() { }

    static int spareFood(Map<Integer, Integer> inventory, Map<Integer, Integer> required,
                         IntPredicate nonStackableFood)
    {
        for (Map.Entry<Integer, Integer> item : inventory.entrySet())
            if (item.getValue() > 1 && !required.containsKey(item.getKey())
                && nonStackableFood.test(item.getKey())) return item.getKey();
        return -1;
    }

    static int additionalSlotsNeeded(Map<Integer, Integer> withdrawals,
                                     Map<Integer, Integer> inventory, IntPredicate stackable)
    {
        long slots = 0;
        for (Map.Entry<Integer, Integer> item : withdrawals.entrySet())
        {
            if (item.getValue() <= 0) continue;
            if (stackable.test(item.getKey()))
                slots += inventory.getOrDefault(item.getKey(), 0) > 0 ? 0 : 1;
            else slots += item.getValue();
        }
        return (int) Math.min(Integer.MAX_VALUE, slots);
    }
}
