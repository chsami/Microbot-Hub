package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.function.BooleanSupplier;

public enum State {
    // Objective is permits per game, i.e. points, not XP. Nothing else can be done for points while
    // Tempoross is recharging, so hold the pool for the whole window (97-98%) rather than the old 94%.
    ATTACK_TEMPOROSS(() -> TemporossScript.ENERGY >= TemporossScript.thresholdFullEnergy, null),
    SECOND_FILL(() -> getCookedFish() == 0, ATTACK_TEMPOROSS),
    // Cook the whole bag. The old "energy < 50 with 16+ fish" bail-out moved to THIRD_CATCH, where
    // it belongs: at the cutoff we stop catching, but everything caught still gets cooked.
    THIRD_COOK(() -> getCookedFish() == ((TemporossScript.temporossConfig.solo() && TemporossScript.ESSENCE > 20) ? 19 : getAllFish()) || TemporossScript.INTENSITY >= 92, SECOND_FILL),
    THIRD_CATCH(() -> {
        // Bag full (or the solo essence target) always ends the catch.
        if (getAllFish() >= ((TemporossScript.temporossConfig.solo() && TemporossScript.ESSENCE > 20)
                ? 19 : getTotalAvailableFishSlots())) {
            return true;
        }
        if (TemporossScript.temporossConfig.solo()) {
            return false;
        }
        // Cutoff: stop catching just in time to cook and load the bag before the pool phase.
        // Adaptive when this game's drain rate is known — a fixed percentage is wrong on both ends
        // of the mass-world spread. Cooked fish deposit for 65 points against 20 raw, so arriving
        // at the pool with an uncooked bag wastes most of it; equally, cutting at 49% in a slow
        // game throws away catching time. Falls back to the old ~49% line until the rate has been
        // sampled. Energy must be non-zero: 0 means the pool phase or an unparsed widget. Requires
        // a few fish — with 1-2 in the bag this used to sprint a cook-and-load for a single fish.
        if (TemporossScript.ENERGY > 0 && getAllFish() >= 4) {
            int poolIn = TemporossScript.ticksUntilEnergy(5);
            if (poolIn != Integer.MAX_VALUE) {
                // ~2 ticks to cook each raw fish, ~1 to load each, plus fixed walking overhead.
                int needed = getRawFish() * 3 + 12;
                if (poolIn <= needed) {
                    Microbot.log("Adaptive cutoff: pool in ~" + poolIn + " ticks, need ~"
                            + needed + " for " + getRawFish() + " raw fish — cooking now");
                    return true;
                }
            } else if (TemporossScript.ENERGY <= TemporossScript.thresholdLoadEnergy) {
                return true;
            }
        }
        // Otherwise work in batches: catch 7, cook them, repeat. A double spot overrides that — while
        // one is up it is worth staying out and filling the bag, and the cook interrupt in
        // handleStateLoop pulls us back out to it if one appears mid-cook.
        return getAllFish() >= TemporossScript.thirdCatchBatch && !TemporossScript.hasDoubleSpot();
    }, THIRD_COOK),
    EMERGENCY_FILL(() -> getAllFish() == 0, THIRD_CATCH),
    INITIAL_FILL(() -> getCookedFish() == 0, THIRD_CATCH),
    SECOND_COOK(() -> getCookedFish() == (TemporossScript.temporossConfig.solo() ? 17 : getAllFish()), INITIAL_FILL),
    SECOND_CATCH(() -> getAllFish() >= (TemporossScript.temporossConfig.solo() ? 17 : getTotalAvailableFishSlots()), SECOND_COOK),
    INITIAL_COOK(() -> getRawFish() == 0, SECOND_CATCH),
    INITIAL_CATCH(() -> getRawFish() >= TemporossScript.openingCatchTarget || getAllFish() >= 10, INITIAL_COOK);

    public final BooleanSupplier isComplete;
    public final State next;

    State(BooleanSupplier isComplete, State next) {
        this.isComplete = isComplete;
        this.next = next;
    }

    public boolean isComplete() {
        return this.isComplete.getAsBoolean();
    }

    public static int getRawFish() {
        return Rs2Inventory.count(ItemID.TEMPOROSS_RAW_HARPOONFISH);
    }

    public static int getAllFish() {
        return getRawFish() + getCookedFish();
    }

    public static int getCookedFish() {
        return Rs2Inventory.count(ItemID.TEMPOROSS_HARPOONFISH);
    }

    public static int getTotalAvailableFishSlots() {
        return Rs2Inventory.emptySlotCount() + getAllFish();
    }

    public String toString() {
        return name().toLowerCase().replace("_", " ");
    }
}
