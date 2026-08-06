package net.runelite.client.plugins.microbot.colosseumprayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ColosseumPrayerConfig.configGroup)
@ConfigInformation("Prayer helper for Fortis Colosseum waves 1–11 (Manticore, Javelin Colossus, Serpent Shaman, Jaguar warrior, Shockwave Colossus). Wave 12 not included.")
public interface ColosseumPrayerConfig extends Config {
    String configGroup = "micro-fortiscolosseum-prayer";

    @ConfigItem(
            keyName = "helperEnabled",
            name = "Enable helper",
            description = "Turn the scheduled prayer arbiter loop on.",
            position = 0
    )
    default boolean helperEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLogSignals",
            name = "Debug: log arbitration",
            description = "Log winning prayer resolution each tick cycle (verbose).",
            position = 1
    )
    default boolean debugLogSignals() {
        return false;
    }
}
