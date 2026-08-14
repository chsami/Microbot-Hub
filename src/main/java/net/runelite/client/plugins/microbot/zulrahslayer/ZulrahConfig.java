package net.runelite.client.plugins.microbot.zulrahslayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup("donderzulrah")
public interface ZulrahConfig extends Config {
    @ConfigSection(
            name = "Zulrah",
            description = "",
            position = 0,
            closedByDefault = true
    )
    String zulrahSection = "zulrahSection";
    @ConfigItem(
            keyName = "mageInventorySetup",
            name = "Mage Inventory Setup",
            description = "Inventory Setup to use for magic",
            section = zulrahSection,
            position = 0
    )
    default InventorySetup mageInventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "rangeInventorySetup",
            name = "Range Inventory Setup",
            description = "Inventory Setup to use for range",
            section = zulrahSection,
            position = 1
    )
    default InventorySetup rangeInventorySetup() {
        return null;
    }

    @ConfigSection(
            name = "Banking",
            description = "Between-kills restock and travel",
            position = 1,
            closedByDefault = true
    )
    String bankingSection = "bankingSection";

    @ConfigItem(
            keyName = "cycleStartPoint",
            name = "Start point",
            description = "Where to begin when the plugin starts. Banking = full restock at the GE then "
                    + "travel to Zulrah; Travel to Zulrah = skip banking and just travel back from the "
                    + "house; Beginning of fight = assume we're already on the boat (continued) and just "
                    + "wait for Zulrah to spawn.",
            section = bankingSection,
            position = 0
    )
    default CycleStartPoint cycleStartPoint() {
        return CycleStartPoint.BEGINNING_OF_FIGHT;
    }

    @ConfigItem(
            keyName = "restockBetweenKills",
            name = "Restock between kills",
            description = "After looting, teleport to house, restore prayer, bank at the GE against the "
                    + "magic setup (eating lobsters to full if needed), then travel back to Zulrah.",
            section = bankingSection,
            position = 1
    )
    default boolean restockBetweenKills() {
        return false;
    }
}
