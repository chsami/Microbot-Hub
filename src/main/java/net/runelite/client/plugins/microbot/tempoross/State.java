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
        // No load cutoff anymore: the cycle is catch-and-cook batches all the way down, and the
        // ONLY cannon trips are the opening load and the emergency fill / endgame dump sweeps.
        // A live double spot suspends batching entirely (bag-full above is the only exit there);
        // below the old ~49% line the batch shrinks so the backlog stays cooked for the sweep —
        // cooked deposits 65 against 20 raw, so the dump should find as little raw as possible.
        if (TemporossScript.hasDoubleSpot()) {
            return false;
        }
        if (TemporossScript.ENERGY > 0 && TemporossScript.ENERGY <= TemporossScript.thresholdLoadEnergy) {
            return getRawFish() >= 4;
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
    // A live double spot overrides the opening target — stay on it until it dies or the bag is
    // full: double fish are pure surplus, and the skip-initial-cook path absorbs any batch size.
    INITIAL_CATCH(() -> ((getRawFish() >= TemporossScript.openingCatchTarget || getAllFish() >= 10)
            && !TemporossScript.hasDoubleSpot())
            || getAllFish() >= getTotalAvailableFishSlots(), INITIAL_COOK);

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
