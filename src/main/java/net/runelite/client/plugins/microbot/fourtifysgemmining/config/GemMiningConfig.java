package net.runelite.client.plugins.microbot.fourtifysgemmining.config;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.config.TimingConfig;

@ConfigGroup(GemMiningConfig.GROUP)
public interface GemMiningConfig extends TimingConfig
{
    String GROUP = "fourtifyGemMining";

    @ConfigSection(name = "Timing", description = "Adjust action delays. One game tick is about 600 milliseconds.", position = 10, closedByDefault = true)
    String TIMING = "advancedTiming";

    @ConfigSection(name = "General", description = "Choose where to mine. Both locations use normal Microbot walking.", position = 20, closedByDefault = false)
    String GENERAL = "general";

    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show runtime, mine, status and session gems.", position = 0)
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(keyName = "mine", name = "Mine", description = "Choose the mine. Walk to rocks and banking targets outside interaction range.", position = 0, section = GENERAL)
    default GemMine mine()
    {
        return GemMine.SURFACE;
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayMin", name = "Minimum delay (ticks)", description = "Lowest action delay, in game ticks.", position = 0, section = TIMING)
    default int tickDelayMin()
    {
        return TimingConfig.super.tickDelayMin();
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayMax", name = "Maximum delay (ticks)", description = "Highest action delay, in game ticks.", position = 1, section = TIMING)
    default int tickDelayMax()
    {
        return TimingConfig.super.tickDelayMax();
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayTarget", name = "Target delay (ticks)", description = "Target value used to generate random action delays, in game ticks.", position = 2, section = TIMING)
    default int tickDelayTarget()
    {
        return TimingConfig.super.tickDelayTarget();
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayDeviation", name = "Delay variation (ticks)", description = "Amount of random variation around the target delay, in game ticks.", position = 3, section = TIMING)
    default int tickDelayDeviation()
    {
        return TimingConfig.super.tickDelayDeviation();
    }

    @ConfigItem(keyName = "tickDelayWeightedDistribution", name = "Weighted distribution", description = "Change how random delays are distributed within the minimum and maximum.", position = 4, section = TIMING)
    default boolean tickDelayWeightedDistribution()
    {
        return TimingConfig.super.tickDelayWeightedDistribution();
    }
}
