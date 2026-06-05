package net.runelite.client.plugins.microbot.pitfallhunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation("<html>"
        + "Local Sunlight Antelope pitfall hunter MVP.<br/>"
        + "Starts near the pitfall area and runs only the local NPC lure and closest-pit loop. "
        + "No banking, travel, anti-ban, randomization, or profit tracking.<br/><br/>"
        + "Required: knife and teasing stick.<br/>"
        + "Required if fletching: chisel.<br/>"
        + "Optional: meat pouch.<br/>"
        + "Supported: Kandarin headgear."
        + "</html>")
@ConfigGroup("pitfallhunter")
public interface PitfallHunterConfig extends Config
{
    enum BigBonesMode
    {
        KEEP,
        DROP,
        BURY_AFTER_LOOT,
        BURY_WHEN_FULL
    }

    enum AntelopeDropThreshold
    {
        OFF(0),
        BELOW_3(3),
        BELOW_4(4);

        private final int emptySlots;

        AntelopeDropThreshold(int emptySlots)
        {
            this.emptySlots = emptySlots;
        }

        public int emptySlots()
        {
            return emptySlots;
        }
    }

    @ConfigItem(
            position = 0,
            keyName = "maxNpcDistanceFromPit",
            name = "Max NPC distance",
            description = "Maximum tile distance from a selected pit used by legacy pit/NPC checks."
    )
    default int maxNpcDistanceFromPit()
    {
        return 10;
    }

    @ConfigItem(
            position = 1,
            keyName = "pitObjectSearchRadius",
            name = "Pit object radius",
            description = "Tile radius around each configured pit tile used to detect pitfall objects."
    )
    default int pitObjectSearchRadius()
    {
        return 2;
    }

    @ConfigItem(
            position = 2,
            keyName = "captureTimeoutMs",
            name = "Capture timeout",
            description = "Maximum time to wait after jumping for the pit to collapse before rotating to another pit."
    )
    default int captureTimeoutMs()
    {
        return 5000;
    }

    @ConfigItem(
            position = 3,
            keyName = "bigBonesMode",
            name = "Big bones",
            description = "How to handle Big bones from collapsed traps."
    )
    default BigBonesMode bigBonesMode()
    {
        return BigBonesMode.KEEP;
    }

    @ConfigItem(
            position = 4,
            keyName = "antelopeDropThreshold",
            name = "Drop antelope loot",
            description = "Drop Sunlight antelope meat and fur when empty slots fall below the selected threshold."
    )
    default AntelopeDropThreshold antelopeDropThreshold()
    {
        return AntelopeDropThreshold.OFF;
    }

    @ConfigItem(
            position = 5,
            keyName = "fletchAntlers",
            name = "Fletch antlers",
            description = "Use a chisel on Sunlight antelope antlers and complete the bolts dialogue when possible."
    )
    default boolean fletchAntlers()
    {
        return false;
    }

    @ConfigItem(
            position = 6,
            keyName = "logsToPrepare",
            name = "Logs to prepare",
            description = "Number of logs to cut when preparing tools/logs with Kandarin headgear."
    )
    default int logsToPrepare()
    {
        return 1;
    }
}
