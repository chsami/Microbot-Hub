package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DroColosseumPrayersConfig.PRAYERS_GROUP)
public interface DroColosseumPrayersConfig extends Config {
    String PRAYERS_GROUP = "drocolosseumprayers";

    @ConfigItem(keyName="showTilePack", name="Show Colosseum tile pack", description="Draw the community Colosseum tile pack: pillar spots, steps and spawn outlines.", position=1)
    default boolean showTilePack() { return true; }

    @ConfigItem(keyName="logoutAfterRun", name="Log out after death or completion", description="Log out when the run ends.", position=2)
    default boolean logoutAfterRun() { return false; }
}
