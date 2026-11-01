package net.runelite.client.plugins.microbot.barbarianfishing;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup(BarbarianFishingConfig.GROUP)
public interface BarbarianFishingConfig extends Config
{
    String GROUP = "BarbarianFishing";

    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    @ConfigSection(
            name = "CoX & Banking",
            description = "Chambers of Xeric and Banking settings",
            position = 1,
            closedByDefault = false
    )
    String coxSection = "cox";

    @ConfigItem(
            keyName = "GUIDE",
            name = "Guide",
            description = "Information on using the plugin",
            position = 0,
            section = generalSection
    )
    default String GUIDE()
    {
        return "Automated Barbarian Fishing at Otto's Grotto and Mount Quidamortem (CoX).\n\n"
                + "Start with a Barbarian rod (or Pearl barbarian rod) and feathers / bait in inventory.\n"
                + "At Mount Quidamortem (CoX), Use Fish Barrel enables walking to the bank chest when full.";
    }

    @ConfigItem(
            keyName = "dropOrder",
            name = "Drop Order",
            description = "The order in which to drop items",
            position = 1,
            section = generalSection
    )
    default InteractOrder dropOrder()
    {
        return InteractOrder.STANDARD;
    }

    @ConfigItem(
            keyName = "useFishBarrel",
            name = "Use Fish Barrel",
            description = "Use a Fish barrel if present in inventory to hold extra fish",
            position = 2,
            section = generalSection
    )
    default boolean useFishBarrel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "useDragonHarpoonSpec",
            name = "Dragon Harpoon Spec",
            description = "Automatically use Dragon harpoon special attack when at 100% spec energy",
            position = 3,
            section = generalSection
    )
    default boolean useDragonHarpoonSpec()
    {
        return true;
    }

    @ConfigItem(
            keyName = "bankAtCox",
            name = "Bank at CoX",
            description = "At Mount Quidamortem (CoX), walk to the bank chest and deposit fish when barrel and inventory are full",
            position = 0,
            section = coxSection
    )
    default boolean bankAtCox()
    {
        return true;
    }
}
