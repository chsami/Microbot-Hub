package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DroInfernoPrayersConfig.PRAYERS_GROUP)
public interface DroInfernoPrayersConfig extends Config {
    String PRAYERS_GROUP = "droinfernoprayers";

    @ConfigItem(keyName="showTilePack", name="Show Inferno tile pack", description="Draw the community Inferno tile pack: Zuk safe spots and wave tiles.", position=1)
    default boolean showTilePack() { return true; }

    @ConfigItem(keyName="logoutAfterRun", name="Log out after death or cape", description="Log out when the run ends.", position=2)
    default boolean logoutAfterRun() { return false; }
}
