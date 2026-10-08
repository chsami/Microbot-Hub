package net.runelite.client.plugins.microbot.drokbd;

import net.runelite.api.ItemID;

public enum DroKbdFood
{
    MANTA_RAY("Manta ray", ItemID.MANTA_RAY);

    private final String displayName;
    private final int itemId;

    DroKbdFood(String displayName, int itemId)
    {
        this.displayName = displayName;
        this.itemId = itemId;
    }

    public int getItemId() { return itemId; }

    @Override
    public String toString() { return displayName; }
}
