package net.runelite.client.plugins.microbot.Bones;

import net.runelite.api.ItemID;

public enum BoneType {
    BONES(ItemID.BONES, "Bury"),
    BIG_BONES(ItemID.BIG_BONES, "Bury");

    private final int itemId;
    private final String action;

    BoneType(int itemId, String action) {
        this.itemId = itemId;
        this.action = action;
    }

    public int getItemId() {
        return itemId;
    }

    public String getAction() {
        return action;
    }
}
