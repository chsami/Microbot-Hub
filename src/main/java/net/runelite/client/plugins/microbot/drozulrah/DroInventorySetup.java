package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.*;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Equipment-only variant matching for this script; other banking remains in the standard helper. */
public final class DroInventorySetup extends Rs2InventorySetup {
    private final InventorySetup setup;
    public DroInventorySetup(InventorySetup setup, ScheduledFuture<?> scheduler) {
        super(setup, scheduler); this.setup = setup;
    }
    @Override public boolean loadEquipment() { return loadEquipment(true); }
    @Override public boolean loadEquipment(boolean skipIfMatching) {
        return super.loadEquipment(skipIfMatching);
    }
    private static boolean exact(InventorySetupsItem row) {
        return !row.isFuzzy() && !InventorySetupsItem.isBarrowsItem(row.getName().toLowerCase(Locale.ROOT));
    }
    static boolean matches(InventorySetupsItem row, Rs2ItemModel live) {
        if (live == null || live.isNoted() || live.getName() == null || row.getName() == null || row.getName().isEmpty()) return false;
        return exact(row) ? live.getName().equalsIgnoreCase(row.getName())
                : live.getName().toLowerCase(Locale.ROOT).contains(row.getName().toLowerCase(Locale.ROOT));
    }
}
