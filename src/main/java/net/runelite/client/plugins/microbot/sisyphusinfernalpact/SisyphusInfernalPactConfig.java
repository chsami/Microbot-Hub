package net.runelite.client.plugins.microbot.sisyphusinfernalpact;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Config for Sisyphus: Infernal Pact — Yama's Lair stepping-stone automation.
 */
@ConfigGroup("sisyphusinfernalpact")
public interface SisyphusInfernalPactConfig extends Config {

    @ConfigSection(
            name = "Yama Stepping Stones",
            description = "Configure the Infernal Pact stepping-stone runner",
            position = 0
    )
    String yamaSection = "yamaSection";

    @ConfigItem(
            keyName = "yamaStonesToStep",
            name = "Stones to Step",
            description = "Total safe stones to hop before stopping. Default 666 for the Demonic Pacts task.",
            position = 0,
            section = yamaSection
    )
    @Range(min = 1, max = 5000)
    default int yamaStonesToStep() { return 666; }
}
