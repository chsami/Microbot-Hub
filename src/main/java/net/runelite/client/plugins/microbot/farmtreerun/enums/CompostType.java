package net.runelite.client.plugins.microbot.farmtreerun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum CompostType {
    NONE("None", -1, false),
    COMPOST("Compost", ItemID.BUCKET_COMPOST, false),
    SUPERCOMPOST("Supercompost", ItemID.BUCKET_SUPERCOMPOST, false),
    ULTRACOMPOST("Ultracompost", ItemID.BUCKET_ULTRACOMPOST, false),
    BOTTOMLESS_BUCKET("Bottomless bucket", ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, true);

    private final String name;
    private final int itemId;
    private final boolean reusable;

    @Override
    public String toString() {
        return name;
    }
}
