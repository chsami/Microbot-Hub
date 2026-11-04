package net.runelite.client.plugins.microbot.driftnet;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;

import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/** Setup loader that reports a missing bank item to the script instead of logging out. */
public final class DriftNetInventorySetup extends Rs2InventorySetup {
    public DriftNetInventorySetup(InventorySetup setup, ScheduledFuture<?> scheduler, Consumer<String> onMissingItem) {
        super(setup, scheduler);
        withMissingSupplyHandler(onMissingItem);
    }
}
