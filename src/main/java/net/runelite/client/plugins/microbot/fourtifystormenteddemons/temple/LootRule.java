package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Decides which dropped items are worth a pickup. */
public final class LootRule
{
    private final Set<String> always;
    private final long minimum;

    public LootRule(String alwaysTake, long minimum)
    {
        this.always = Collections.unmodifiableSet(names(alwaysTake));
        this.minimum = minimum;
    }

    public static Set<String> names(String text)
    {
        Set<String> result = new LinkedHashSet<>();
        if (text != null)
        {
            for (String name : text.split(","))
            {
                if (!name.isBlank())
                {
                    result.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    /** A price of 0 means unknown, not worthless: such an item is taken only from the always-take list. */
    public boolean take(String name, long unitPrice, int quantity)
    {
        if (name != null && always.contains(name.trim().toLowerCase(Locale.ROOT)))
        {
            return true;
        }
        if (unitPrice <= 0 || quantity <= 0)
        {
            return false;
        }
        return unitPrice * quantity >= minimum;
    }
}
