package net.runelite.client.plugins.microbot.gildedaltar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("GildedAltar")
public interface GildedAltarConfig extends Config {

    @ConfigItem(
            keyName = "Guide",
            name = "How to use",
            description = "How to use the script",
            position = 0
    )
    default String GUIDE() {
        return "Start in Rimmington with noted bones and GP. The script will automatically use house advertisements on W330.";
    }
}
