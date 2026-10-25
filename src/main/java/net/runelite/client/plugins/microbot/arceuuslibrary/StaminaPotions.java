package net.runelite.client.plugins.microbot.arceuuslibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure decisions for run-energy upkeep: which potions count, which to prefer, and when to
 * drink / enable run / restock. No client state so it can be unit tested.
 */
final class StaminaPotions
{
    /** Run is never toggled on at or below this energy (the client refuses anyway). */
    static final int MIN_ENERGY_TO_RUN = 10;

    static final int RANK_EXTENDED_STAMINA = 0;
    static final int RANK_STAMINA = 1;
    static final int RANK_SUPER_ENERGY = 2;
    static final int RANK_ENERGY = 3;
    static final int NOT_A_POTION = -1;

    private StaminaPotions() {}

    /** Preference rank of an item by name (lower is better), or {@link #NOT_A_POTION}. Any dose counts. */
    static int rank(String itemName)
    {
        if (itemName == null) return NOT_A_POTION;
        String n = itemName.toLowerCase(Locale.ROOT).trim();
        if (n.startsWith("extended stamina potion")) return RANK_EXTENDED_STAMINA;
        if (n.startsWith("stamina potion")) return RANK_STAMINA;
        if (n.startsWith("super energy")) return RANK_SUPER_ENERGY;
        if (n.startsWith("energy potion")) return RANK_ENERGY;
        return NOT_A_POTION;
    }

    static boolean isPotion(String itemName)
    {
        return rank(itemName) != NOT_A_POTION;
    }

    /** Whether the potion grants the slowed-depletion stamina effect (as opposed to a plain restore). */
    static boolean givesStaminaEffect(String itemName)
    {
        int r = rank(itemName);
        return r == RANK_EXTENDED_STAMINA || r == RANK_STAMINA;
    }

    /** Dose count from a name like "Stamina potion(3)"; 0 if absent. */
    static int dose(String itemName)
    {
        if (itemName == null) return 0;
        int open = itemName.lastIndexOf('(');
        int close = itemName.lastIndexOf(')');
        if (open < 0 || close < open + 2) return 0;
        try
        {
            return Integer.parseInt(itemName.substring(open + 1, close).trim());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /**
     * Index of the best name to drink: best rank first, then the lowest dose (finish partial
     * potions first). Returns -1 when none qualify.
     */
    static int bestIndex(List<String> names)
    {
        int best = -1;
        for (int i = 0; i < names.size(); i++)
        {
            int r = rank(names.get(i));
            if (r == NOT_A_POTION) continue;
            if (best < 0) { best = i; continue; }
            int br = rank(names.get(best));
            if (r < br || (r == br && dose(names.get(i)) < dose(names.get(best)))) best = i;
        }
        return best;
    }

    static boolean shouldEnableRun(boolean runEnabled, int energyPercent)
    {
        return !runEnabled && energyPercent > MIN_ENERGY_TO_RUN;
    }

    /**
     * Drink when the feature is on, a potion is held, energy is at or under the threshold and,
     * for stamina potions, no stamina effect is already running.
     */
    static boolean shouldDrink(boolean enabled, int energyPercent, int thresholdPercent,
                               boolean staminaEffectActive, boolean potionHeld, boolean potionGivesStamina)
    {
        if (!enabled || !potionHeld) return false;
        if (energyPercent > thresholdPercent) return false;
        return !(potionGivesStamina && staminaEffectActive);
    }

    /** Restock only when out of potions, energy is already low, and a previous trip has not failed. */
    static boolean shouldRestock(boolean enabled, int toBring, int potionsHeld, int energyPercent,
                                 int thresholdPercent, boolean restockGivenUp)
    {
        return enabled && toBring > 0 && potionsHeld == 0 && energyPercent <= thresholdPercent && !restockGivenUp;
    }

    /** A bank row candidate for withdrawal. */
    static final class Stock
    {
        final int id;
        final String name;
        final int quantity;

        Stock(int id, String name, int quantity)
        {
            this.id = id;
            this.name = name;
            this.quantity = quantity;
        }
    }

    /**
     * Picks which bank item ids to withdraw (one id per potion, repeats allowed for stacked
     * rows) to reach {@code count} potions, preferring the best kind, then the lowest dose.
     */
    static List<Integer> planWithdraw(List<Stock> bank, int count)
    {
        List<Integer> out = new ArrayList<>();
        int[] left = new int[bank.size()];
        for (int i = 0; i < bank.size(); i++) left[i] = Math.max(0, bank.get(i).quantity);
        while (out.size() < count)
        {
            int best = -1;
            for (int i = 0; i < bank.size(); i++)
            {
                if (left[i] <= 0 || !isPotion(bank.get(i).name)) continue;
                if (best < 0) { best = i; continue; }
                int r = rank(bank.get(i).name), br = rank(bank.get(best).name);
                if (r < br || (r == br && dose(bank.get(i).name) < dose(bank.get(best).name))) best = i;
            }
            if (best < 0) break;
            left[best]--;
            out.add(bank.get(best).id);
        }
        return out;
    }
}
