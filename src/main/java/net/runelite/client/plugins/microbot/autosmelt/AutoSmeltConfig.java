package net.runelite.client.plugins.microbot.autosmelt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.autosmelt.enums.BarType;

@ConfigGroup("jiggly-auto-smelt")
public interface AutoSmeltConfig extends Config {
    @ConfigSection(
            name = "Smelting Settings",
            description = "Configure smelting options",
            position = 0
    )
    String smeltingSection = "smelting";

    @ConfigItem(
            keyName = "barType",
            name = "Bar Type",
            description = "The type of bar to smelt",
            position = 0,
            section = smeltingSection
    )
    default BarType barType() {
        return BarType.BRONZE;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug Mode",
            description = "Enable debug information",
            position = 1,
            section = smeltingSection
    )
    default boolean debugMode() {
        return false;
    }

    @ConfigItem(
            keyName = "logoutWhenComplete",
            name = "Logout When Complete",
            description = "Logout when no more ores are available",
            position = 2,
            section = smeltingSection
    )
    default boolean logoutWhenComplete() {
        return false;
    }
}
