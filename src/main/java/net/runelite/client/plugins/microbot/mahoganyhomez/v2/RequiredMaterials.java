package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import lombok.Getter;

/**
 * Represents the required materials for a Mahogany Homes contract
 *
 */
@Getter()
public class RequiredMaterials {

    private final int minPlanks;
    private final int maxPlanks;
    private final int minSteelBars;
    private final int maxSteelBars;

    public RequiredMaterials(int minPlanks, int maxPlanks, int minSteelBars, int maxSteelBars) {
        if (minPlanks < 0 || maxPlanks < 0 || minSteelBars < 0 || maxSteelBars < 0) {
            throw new IllegalArgumentException("Material counts cannot be negative");
        }
        if (minPlanks > maxPlanks || minSteelBars > maxSteelBars) {
            throw new IllegalArgumentException("Minimum cannot exceed maximum");
        }

        this.minPlanks = minPlanks;
        this.maxPlanks = maxPlanks;
        this.minSteelBars = minSteelBars;
        this.maxSteelBars = maxSteelBars;
    }

    /**
     * Formats the plank requirements as a string
     */
    public String formatPlanks() {
        if (minPlanks == maxPlanks) {
            return String.format("%d planks", minPlanks);
        }
        return String.format("%d - %d planks", minPlanks, maxPlanks);
    }

    /**
     * Formats the steel bar requirements as a string, or null if no steel bars needed
     */
    public String formatSteelBars() {
        if (minSteelBars + maxSteelBars == 0) {
            return null;
        }
        if (minSteelBars == maxSteelBars) {
            return String.format("%d steel bar%s", minSteelBars, minSteelBars == 1 ? "" : "s");
        }
        return String.format("%d - %d steel bars", minSteelBars, maxSteelBars);
    }
}
