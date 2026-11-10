/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonObject;
import java.util.Comparator;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Item;

/** One deliberate inventory-space action, completed only by an observed slot. */
final class InfernoInventorySpace
{
    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_TICKS = 4;
    private int pendingSlot = -1, pendingId = -1, attempts;
    private int dropX, dropY;
    private long lastAttempt = -1000;
    private boolean pendingRestore, restoreReserved;
    private String status = "Waiting for an observed inventory slot";

    void reset()
    {
        pendingSlot = pendingId = -1; attempts = 0; lastAttempt = -1000;
        pendingRestore = restoreReserved = false;
        status = "Waiting for an observed inventory slot";
    }

    void observe(InfernoSnapshot f, boolean reserveVisible)
    {
        // A ground item prevents another restore drop, but does not create an
        // inventory slot. A restart may see a reserve and a refilled inventory.
        if (reserveVisible)
        {
            restoreReserved = true;
            // The game may drop the restore and refill its slot before the
            // next captured frame. Ground presence retires that drop request,
            // even if an identical potion now occupies the same slot. It does
            // not make the still-full inventory ready for a two-handed swap.
            if (pendingRestore)
            {
                pendingSlot = pendingId = -1; attempts = 0; lastAttempt = -1000;
                pendingRestore = false;
            }
            if (pendingSlot < 0 && f.inventory.size() >= 28)
                status = "Restore already reserved; inventory is still full";
        }
        if (pendingSlot >= 0 && f.raw.has("inventory") && f.inventory.size() < 28)
        {
            if (pendingRestore) restoreReserved = true;
            pendingSlot = pendingId = -1; attempts = 0; lastAttempt = -1000;
            pendingRestore = false;
            status = "Inventory space confirmed";
        }
        else if (pendingSlot >= 0 && attempts >= MAX_ATTEMPTS && f.tick - lastAttempt >= RETRY_TICKS)
            status = "Drop was not confirmed after three attempts; inventory is still full";
    }

    boolean canDropVial(InfernoSnapshot f)
    {
        if (pendingSlot >= 0)
            return !pendingRestore && attempts < MAX_ATTEMPTS && pendingStillPresent(f);
        return vial(f) != null;
    }

    boolean reserveBlocked()
    {
        return restoreReserved || pendingSlot >= 0 && attempts >= MAX_ATTEMPTS;
    }

    String status() { return status; }

    /** Restore permission is supplied only while standing on the safe reserve tile. */
    JsonObject drop(InfernoSnapshot f, boolean allowRestore)
    {
        if (!f.raw.has("inventory") || f.inventory.size() < 28) return null;
        if (pendingSlot >= 0)
        {
            if (pendingRestore && (!allowRestore || f.x != dropX || f.y != dropY)) return null;
            if (!pendingStillPresent(f))
            {
                status = "Selected drop item changed; waiting for an observed free slot";
                return null;
            }
            if (attempts >= MAX_ATTEMPTS)
            {
                status = "Drop was not confirmed after three attempts; inventory is still full";
                return null;
            }
            if (f.tick - lastAttempt < RETRY_TICKS) return null;
        }
        else
        {
            Item item = vial(f);
            boolean restore = false;
            if (item == null && allowRestore && !restoreReserved)
            {
                item = f.inventory.stream().filter(i -> dose(i) > 0)
                    .min(Comparator.comparingInt(InfernoInventorySpace::dose)
                        .thenComparingInt(i -> i.slot)).orElse(null);
                restore = item != null;
            }
            if (item == null)
            {
                status = restoreReserved
                    ? "Restore already reserved; inventory is full, preserving the remaining supplies"
                    : allowRestore ? "Inventory is full with no vial or restore available to reserve"
                    : "Inventory is full; restore reservation will resume between waves";
                return null;
            }
            pendingSlot = item.slot; pendingId = item.id; pendingRestore = restore;
            dropX = f.x; dropY = f.y; attempts = 0;
        }
        attempts++; lastAttempt = f.tick;
        status = pendingRestore ? "Confirming the single restore reserve" : "Confirming the empty-vial drop";
        return InfernoDecisionEngine.command("inventory.interact", "slot", pendingSlot,
            "itemId", pendingId, "option", "Drop");
    }

    private boolean pendingStillPresent(InfernoSnapshot f)
    {
        return f.inventory.stream().anyMatch(i -> i.slot == pendingSlot && i.id == pendingId && i.qty == 1);
    }

    private static Item vial(InfernoSnapshot f)
    {
        return f.inventory.stream().filter(i -> i.id == 229 && i.qty == 1 && "vial".equals(i.lower))
            .min(Comparator.comparingInt(i -> i.slot)).orElse(null);
    }

    private static int dose(Item item)
    {
        if (item.qty != 1 || !item.lower.startsWith("super restore(") || !item.lower.endsWith(")")) return -1;
        String value = item.lower.substring("super restore(".length(), item.lower.length() - 1);
        return value.length() == 1 && value.charAt(0) >= '1' && value.charAt(0) <= '4'
            ? value.charAt(0) - '0' : -1;
    }
}
