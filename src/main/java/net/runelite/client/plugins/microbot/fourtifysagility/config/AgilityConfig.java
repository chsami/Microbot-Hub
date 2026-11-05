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

    @ConfigSection(name = "Timing", description = "Adjust action delays. One game tick is about 600 milliseconds.", position = 80, closedByDefault = true)
    String timingSection = "advancedTiming";

    @ConfigItem(keyName = "enableUI", name = "Show overlay", description = "Show runtime, course, status and session progress. The status line is the only place the plugin explains why it is waiting, so keep it on if the plugin seems stuck.", position = 0)
    default boolean enableUI()
    {
        return true;
    }

    @ConfigItem(keyName = "mode", name = "Course", description = "Choose the course to run. Start within 20 tiles of its first obstacle; the plugin does not travel between courses.", position = 0, section = trainingSection)
    default AgilityCourse area()
    {
        return AgilityCourse.SEERS_VILLAGE;
    }

    @ConfigItem(keyName = "ignoreMarks", name = "Ignore marks of grace", description = "Leave marks of grace on the ground instead of picking them up.", position = 2, section = trainingSection)
    default boolean ignoreMarks()
    {
        return false;
    }

    @ConfigItem(keyName = "seersTeleport", name = "Use diary teleport", description = "Cast Camelot Teleport after each Seers Village lap. Needs the Kandarin hard diary, the teleport set to Seers' Village (right-click the spell, Toggle-location), 45 Magic, and 1 law and 5 air runes or an air staff.", position = 0, section = seersSection)
    default boolean seersTeleport()
    {
        return false;
    }

    @ConfigItem(keyName = "turnInTops", name = "Turn in Pyramid tops", description = "Turn in Pyramid tops after every lap or when your inventory is full.", position = 0, section = pyramidSection)
    default PyramidTopTurnIn turnInTops()
    {
        return PyramidTopTurnIn.EVERY_LAP;
    }

    @ConfigItem(keyName = "brimhavenLeave", name = "Leave the arena", description = "On: head to the exit ladder and leave the arena, then buy the Voucher reward if one is set. Off: keep tagging dispensers and re-enter after each visit.", position = 0, section = brimhavenSection)
    default boolean brimhavenLeave()
    {
        return false;
    }

    @ConfigItem(keyName = "brimhavenVoucherReward", name = "Voucher reward on exit", description = "Spend carried vouchers on this reward at Pirate Jackie once the plugin has left the arena. Only used when Leave the arena is on. None keeps your vouchers.", position = 2, section = brimhavenSection)
    default BrimhavenVoucherReward brimhavenVoucherReward()
    {
        return BrimhavenVoucherReward.NONE;
    }

    @ConfigItem(keyName = "foodId", name = "Food item ID", description = "Item ID of the food to eat when Hitpoints fall below the threshold, for example 385 for a shark. At 0, the plugin does not eat and stands still below the threshold until you heal.", position = 0, section = suppliesSection)
    default int foodId()
    {
        return 0;
    }

    @ConfigItem(keyName = "minHealth", name = "Eat below (Hitpoints)", description = "Eat carried food when your current Hitpoints fall below this value.", position = 1, section = suppliesSection)
    default int minHealth()
    {
        return 5;
    }

    @ConfigItem(keyName = "highAlch", name = "Use High Alchemy", description = "Cast High Level Alchemy on the configured item while moving, or Low Level Alchemy below 55 Magic. Carry the item, nature runes and fire runes or a fire staff. If any are missing, alching is skipped.", position = 0, section = alchemySection)
    default boolean highAlch()
    {
        return false;
    }

    @ConfigItem(keyName = "alchId", name = "Alchemy item ID", description = "Item ID to alch, for example 856 for a noted yew longbow (the default).", position = 1, section = alchemySection)
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
