package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;

@ConfigGroup(GiantsFoundryConfig.GROUP)
@ConfigInformation(
        "• Start at the giants foundry minigame. <br />" +
                "• Please select the bars or items in your UI <br />" +
                "• Make sure you type in the names of the items correctly! <br />" +
                "• Make sure you are wearing ice gloves & no weapon/shield equipped <br />"
)
public interface GiantsFoundryConfig extends Config {

    String GROUP = "GiantsFoundry";

    @ConfigSection(
            name = "Bar Configuration",
            description = "Settings for when using bars",
            position = 1
    )
    String barSection = "barSection";

    @ConfigSection(
            name = "Item Configuration",
            description = "Settings for when using items",
            position = 2
    )
    String itemSection = "itemSection";

    @ConfigItem(
            keyName = "useBars",
            name = "Use Bars",
            description = "Should use bars instead of items?",
            position = 0
    )
    default boolean useBars()
    {
        return true;
    }

    @ConfigItem(
            keyName = "FirstBar",
            name = "First Bar",
            description = "Choose the first type of bar",
            position = 1,
            section = barSection
    )
    default SmithableBars FirstBar()
    {
        return SmithableBars.STEEL_BAR;
    }

    @ConfigItem(
            keyName = "SecondBars",
            name = "Second bar",
            description = "Choose the second type of bar",
            position = 2,
            section = barSection
    )
    default SmithableBars SecondBar()
    {
        return SmithableBars.MITHRIL_BAR;
    }

    @ConfigItem(
            keyName = "firstItem",
            name = "First Item",
            description = "Item type to use",
            section = itemSection,
            position = 1
    )
    default String firstItem()
    {
        return "";
    }

    @ConfigItem(
            keyName = "secondItem",
            name = "Second Item",
            description = "Second item type",
            section = itemSection,
            position = 2
    )
    default String secondItem()
    {
        return "";
    }

    @ConfigItem(
            keyName = "firstBarAmount",
            name = "First Bar / Item Amount",
            description = "Choose the amount of first item",
            position = 3
    )
    default int firstBarAmount()
    {
        return 14;
    }

    @ConfigItem(
            keyName = "secondBarAmount",
            name = "Second Bar / Item Amount",
            description = "Choose the amount of second item",
            position = 4
    )
    default int secondBarAmount()
    {
        return 14;
    }

}
