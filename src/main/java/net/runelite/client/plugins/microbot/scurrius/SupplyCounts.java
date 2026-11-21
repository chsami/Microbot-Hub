package net.runelite.client.plugins.microbot.scurrius;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Counting of supplies by quantity, never by slot: teleport tabs are one stack, so a stack of 5 is 5 and not 1. Pure.
 */
public final class SupplyCounts {
    private SupplyCounts() {
    }

    /** Sum of the quantities of every slot holding the item (a stack counts by its quantity, single items by 1 each). */
    public static <T> int quantity(Collection<T> slots, ToIntFunction<T> idOf, ToIntFunction<T> quantityOf, int id) {
        int total = 0;
        for (T slot : slots) {
            if (idOf.applyAsInt(slot) == id) {
                total += Math.max(0, quantityOf.applyAsInt(slot));
            }
        }
        return total;
    }

    /** Description of what is short, like "food 18/20, tabs 1/5", or null when everything is at its configured amount. */
    public static String shortage(int foodHave, int foodNeed, int potionsHave, int potionsNeed, int tabsHave, int tabsNeed) {
        List<String> short_ = new ArrayList<>();
        if (foodHave < foodNeed) {
            short_.add("food " + foodHave + "/" + foodNeed);
        }
        if (potionsHave < potionsNeed) {
            short_.add("prayer potions " + potionsHave + "/" + potionsNeed);
        }
        if (tabsHave < tabsNeed) {
            short_.add("teleport tabs " + tabsHave + "/" + tabsNeed);
        }
        return short_.isEmpty() ? null : String.join(", ", short_);
    }
}
