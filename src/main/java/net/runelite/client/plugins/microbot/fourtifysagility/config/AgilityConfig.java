package net.runelite.client.plugins.microbot.fourtifysagility.config;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.util.config.TimingConfig;

@ConfigGroup("fourtifyAgility")
public interface AgilityConfig extends TimingConfig
{
    String GROUP = "fourtifyAgility";

    @ConfigSection(name = "General", description = "Choose a course and how to train.", position = 20, closedByDefault = false)
    String trainingSection = "training";

    @ConfigSection(name = "Seers Village", description = "Options for the Seers Village course.", position = 50, closedByDefault = true)
    String seersSection = "seersVillage";

    @ConfigSection(name = "Agility Pyramid", description = "Choose when to turn in Pyramid tops.", position = 60, closedByDefault = true)
    String pyramidSection = "agilityPyramid";

    @ConfigSection(name = "Brimhaven", description = "Options for the Brimhaven Agility Arena.", position = 70, closedByDefault = true)
    String brimhavenSection = "brimhaven";

    @ConfigSection(name = "Healing", description = "Configure food and the Hitpoints threshold for eating.", position = 30, closedByDefault = true)
    String suppliesSection = "supplies";

    @ConfigSection(name = "High Alchemy", description = "Configure alchemy while moving.", position = 40, closedByDefault = true)
    String alchemySection = "highAlchemy";

    @ConfigSection(name = "Timing", description = "Adjust action delays. One game tick is about 600 milliseconds.", position = 10, closedByDefault = true)
    String timingSection = "advancedTiming";

    @ConfigItem(keyName = "enableUI", name = "Show overlay", description = "Show runtime, course, status, and session progress while the plugin is enabled.", position = 0)
    default boolean enableUI()
    {
        return true;
    }

    @ConfigItem(keyName = "mode", name = "Course", description = "Choose the course to run. Walk closer to its first obstacle to begin.", position = 0, section = trainingSection)
    default AgilityCourse area()
    {
        return AgilityCourse.SEERS_VILLAGE;
    }

    /* Auto progress is temporarily disabled.
    @ConfigItem(keyName = "autoProgress", name = "Auto progress", description = "Select the next course for your level. Walk there yourself.", position = 1, section = trainingSection)
    default boolean autoProgress() {
        return false;
    }

    */

    @ConfigItem(keyName = "ignoreMarks", name = "Ignore marks of grace", description = "Leave marks of grace on the ground instead of picking them up.", position = 2, section = trainingSection)
    default boolean ignoreMarks()
    {
        return false;
    }

    @ConfigItem(keyName = "seersTeleport", name = "Use diary teleport", description = "Use the Seers Village diary teleport while running the Seers Village course. Requires the diary reward and teleport supplies.", position = 0, section = seersSection)
    default boolean seersTeleport()
    {
        return false;
    }

    @ConfigItem(keyName = "turnInTops", name = "Turn in Pyramid tops", description = "Turn in Pyramid tops after every lap or when your inventory is full.", position = 0, section = pyramidSection)
    default PyramidTopTurnIn turnInTops()
    {
        return PyramidTopTurnIn.EVERY_LAP;
    }

    @ConfigItem(keyName = "brimhavenLeave", name = "Leave the arena", description = "Finish crossing the arena and climb the exit ladder. Turn this off to enter again.", position = 0, section = brimhavenSection)
    default boolean brimhavenLeave()
    {
        return false;
    }

    @ConfigItem(keyName = "brimhavenVoucherReward", name = "Voucher reward on exit", description = "Buy the selected reward with carried vouchers after leaving. None leaves vouchers untouched.", position = 2, section = brimhavenSection)
    default BrimhavenVoucherReward brimhavenVoucherReward()
    {
        return BrimhavenVoucherReward.NONE;
    }

    @ConfigItem(keyName = "foodId", name = "Food item ID", description = "Item ID of the food to eat from your inventory when below the Hitpoints threshold.", position = 0, section = suppliesSection)
    default int foodId()
    {
        return 0;
    }

    @ConfigItem(keyName = "minHealth", name = "Eat below (Hitpoints)", description = "Eat carried food when your current Hitpoints fall below this value.", position = 1, section = suppliesSection)
    default int minHealth()
    {
        return 5;
    }

    @ConfigItem(keyName = "highAlch", name = "Use High Alchemy", description = "Cast alchemy on the configured item while moving. Carry the item and required runes.", position = 0, section = alchemySection)
    default boolean highAlch()
    {
        return false;
    }

    @ConfigItem(keyName = "alchId", name = "Alchemy item ID", description = "Item ID to cast alchemy on when Use High Alchemy is enabled.", position = 1, section = alchemySection)
    default int alchId()
    {
        return 856;
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayMin", name = "Minimum delay (ticks)", description = "Lowest action delay, in game ticks.", position = 0, section = timingSection)
    default int tickDelayMin()
    {
        return TimingConfig.super.tickDelayMin();
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayMax", name = "Maximum delay (ticks)", description = "Highest action delay, in game ticks.", position = 1, section = timingSection)
    default int tickDelayMax()
    {
        return TimingConfig.super.tickDelayMax();
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayTarget", name = "Target delay (ticks)", description = "Target value used to generate random action delays, in game ticks.", position = 2, section = timingSection)
    default int tickDelayTarget()
    {
        return TimingConfig.super.tickDelayTarget();
    }

    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "tickDelayDeviation", name = "Delay variation (ticks)", description = "Amount of random variation around the target delay, in game ticks.", position = 3, section = timingSection)
    default int tickDelayDeviation()
    {
        return TimingConfig.super.tickDelayDeviation();
    }

    @ConfigItem(keyName = "tickDelayWeightedDistribution", name = "Weighted distribution", description = "Change how random delays are distributed within the minimum and maximum.", position = 4, section = timingSection)
    default boolean tickDelayWeightedDistribution()
    {
        return TimingConfig.super.tickDelayWeightedDistribution();
    }
}
