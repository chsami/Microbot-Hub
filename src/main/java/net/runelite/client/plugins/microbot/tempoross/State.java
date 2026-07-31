package net.runelite.client.plugins.microbot.tempoross;

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
    // Stop catching at ~49% energy so there is time to cook and load before the last wave. Energy
    // must be non-zero: 0 means either the pool phase or a widget that has not parsed yet. Requires a
    // few fish to be worth it — with 1-2 in the bag this cutoff used to trigger a cook-and-load
    // sprint for a single fish right as the pool phase arrived; better to keep catching and stage at
    // the pool instead.
    THIRD_CATCH(() -> getAllFish() >= ((TemporossScript.temporossConfig.solo() && TemporossScript.ESSENCE > 20) ? 19 : getTotalAvailableFishSlots())
            || (!TemporossScript.temporossConfig.solo()
                && TemporossScript.ENERGY > 0
                && TemporossScript.ENERGY <= TemporossScript.thresholdLoadEnergy
                && getAllFish() >= 4), THIRD_COOK),
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
