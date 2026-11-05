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
                + "Start at Otto's Grotto or the Mount Quidamortem lake with a Barbarian rod in your inventory (or a Pearl barbarian rod in your inventory or equipped) and feathers or bait.\n"
                + "At Mount Quidamortem (CoX), turn on Bank at CoX to bank your fish at the chest instead of dropping them.";
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
            description = "Fill a Fish barrel in your inventory before banking. Only used at Mount Quidamortem with Bank at CoX on, because the barrel is emptied at the CoX bank chest",
            position = 1,
            section = coxSection
    )
    default boolean useFishBarrel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "useDragonHarpoonSpec",
            name = "Dragon Harpoon Spec",
            description = "Automatically use Dragon harpoon special attack when at 100% spec energy (the Dragon harpoon must be equipped)",
            position = 2,
            section = generalSection
    )
    default boolean useDragonHarpoonSpec()
    {
        return true;
    }

    @ConfigItem(
            keyName = "bankAtCox",
            name = "Bank at CoX",
            description = "At Mount Quidamortem (CoX), walk to the bank chest and deposit fish when your inventory (and Fish barrel, if used) is full, instead of dropping them",
            position = 0,
            section = coxSection
    )
    default boolean bankAtCox()
    {
        return false;
    }
}
