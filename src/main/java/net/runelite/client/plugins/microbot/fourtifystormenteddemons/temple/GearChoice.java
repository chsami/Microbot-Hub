package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/** Chooses which configured equipment style to wear against the demon's current protection. */
public final class GearChoice
{
    /** Styles in the order of the equipment settings. */
    private static final CombatStyle[] ORDER = {CombatStyle.MAGIC, CombatStyle.RANGED, CombatStyle.MELEE};

    private GearChoice()
    {
    }

    /** The configured style whose items are all worn, or else the first configured style, or null. */
    public static CombatStyle worn(Map<CombatStyle, Set<Integer>> sets, IntPredicate wearing)
    {
        CombatStyle first = null;
        for (CombatStyle style : ORDER)
        {
            Set<Integer> ids = sets.get(style);
            if (ids == null || ids.isEmpty())
            {
                continue;
            }
            if (first == null)
            {
                first = style;
            }
            if (ids.stream().allMatch(wearing::test))
            {
                return style;
            }
        }
        return first;
    }

    /** Keeps the current style unless the demon protects against it; then the next configured style it does not. */
    public static CombatStyle against(CombatStyle current, CombatStyle protectedStyle, Map<CombatStyle, Set<Integer>> sets)
    {
        if (current != null && current != protectedStyle)
        {
            return current;
        }
        for (CombatStyle style : ORDER)
        {
            Set<Integer> ids = sets.get(style);
            if (style != protectedStyle && ids != null && !ids.isEmpty())
            {
                return style;
            }
        }
        return current;
    }
}
