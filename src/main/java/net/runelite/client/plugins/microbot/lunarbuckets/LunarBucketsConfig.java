package net.runelite.client.plugins.microbot.lunarbuckets;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("lunarbuckets")
@ConfigInformation(
        "\u2022 Automatically casts Humidify to fill buckets.\\n" +
        "\u2022 Ensure your bank contains a steam staff, astral runes and empty buckets.\\n" +
        "\u2022 No configuration is required."
)
public interface LunarBucketsConfig extends Config {
}
