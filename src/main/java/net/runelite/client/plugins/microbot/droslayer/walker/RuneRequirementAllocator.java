package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Allocates available rune-provider items to outstanding requirements.
 * Combination runes are credited against every rune they provide.
 */
final class RuneRequirementAllocator
{
    private RuneRequirementAllocator()
    {
        // Utility class.
    }

    static Map<Integer, Integer> allocateRequiredRunes(
        Map<Runes, Integer> requirements,
        Map<Integer, Integer> availableItemCounts,
        IntPredicate itemAllowed)
    {
        final Map<Runes, Integer> remaining = new EnumMap<>(Runes.class);
        remaining.putAll(requirements);

        final Map<Integer, Integer> selected = new LinkedHashMap<>();

        for (Runes requiredRune : Runes.values())
        {
            for (Runes provider : Runes.values())
            {
                final int requiredAmount = remaining.getOrDefault(requiredRune, 0);
                if (requiredAmount <= 0)
                {
                    break;
                }

                final int itemId = provider.getItemId();
                if (!provider.providesRune(requiredRune) || !itemAllowed.test(itemId))
                {
                    continue;
                }

                final int availableAmount = availableItemCounts.getOrDefault(itemId, 0)
                    - selected.getOrDefault(itemId, 0);
                final int amount = Math.min(requiredAmount, availableAmount);
                if (amount <= 0)
                {
                    continue;
                }

                selected.merge(itemId, amount, Integer::sum);

                for (Runes suppliedRune : Runes.values())
                {
                    if (provider.providesRune(suppliedRune))
                    {
                        remaining.computeIfPresent(
                            suppliedRune,
                            (rune, current) -> subtractAllocatedRuneQuantity(amount, rune, current));
                    }
                }
            }

            if (remaining.getOrDefault(requiredRune, 0) > 0)
            {
                return null;
            }
        }

        return selected;
    }

    private static Integer subtractAllocatedRuneQuantity(int used, Runes ignored, Integer current)
    {
        return Math.max(0, current - used);
    }
}
