package net.runelite.client.plugins.microbot.autoessencemining;

import net.runelite.client.config.*;

@ConfigGroup("EssenceMining")
public interface AutoEssenceMiningConfig extends Config {
    @ConfigItem(
            keyName = "Guide",
            name = "Usage guide",
            description = "Usage guide",
            position = 1
    )
    default String GUIDE() {
        return  "Begin anywhere with a pickaxe wielded or in your inventory...";
    }
}