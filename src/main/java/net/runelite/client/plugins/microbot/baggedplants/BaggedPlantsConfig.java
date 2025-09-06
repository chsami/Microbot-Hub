package net.runelite.client.plugins.microbot.baggedplants;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BaggedPlantsConfig.GROUP)
@ConfigInformation(
        "Inventory Requirements:<br /><br />" +
        "• Coins<br />" +
        "• 3 Watering Cans<br />" +
        "• Noted Bagged Plants 1 (653 needed for level 1-34)<br />" +
        "• Unnoted Bagged Plants<br /><br />" +
        "Start the script outside the POH in Rimmington or inside your house."
)
public interface BaggedPlantsConfig extends Config {

    String GROUP = "BaggedPlants";

    @ConfigSection(
            name = "General",
            description = "General configuration",
            position = 0
    )
    String generalSection = "general";



    @ConfigItem(
            keyName = "minWaitTime",
            name = "Minimum Wait Time (seconds)",
            description = "Minimum time to wait after entering house",
            position = 2,
            section = generalSection
    )
    default int minWaitTime() {
        return 2;
    }

    @ConfigItem(
            keyName = "maxWaitTime",
            name = "Maximum Wait Time (seconds)",
            description = "Maximum time to wait after entering house",
            position = 3,
            section = generalSection
    )
    default int maxWaitTime() {
        return 10;
    }

    @ConfigItem(
            keyName = "useCustomDelay",
            name = "Use Custom Delay",
            description = "Toggle to use custom action delay",
            position = 4,
            section = generalSection
    )
    default boolean useCustomDelay() {
        return false;
    }

    @ConfigItem(
            keyName = "actionDelay",
            name = "Action Delay",
            description = "Adjust the delay (in milliseconds) between actions",
            position = 5,
            section = generalSection
    )
    default int actionDelay() {
        return 600; // Default delay of 600 milliseconds
    }
}
