package net.runelite.client.plugins.microbot.fourtifystormenteddemons.config;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.config.TimingConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonRoom;

@ConfigGroup(TormentedDemonsConfig.GROUP)
public interface TormentedDemonsConfig extends TimingConfig
{
    String GROUP = "fourtifyTormentedDemons";

    @ConfigSection(name = "Timing", description = "Adjust action delays. One game tick is about 600 milliseconds.", position = 10, closedByDefault = true)
    String TIMING = "advancedTiming";

    @ConfigSection(name = "General", description = "Requires While Guthix Sleeps. Bank at Ferox Enclave with a Ring of dueling and travel in with Guthixian temple teleports.", position = 20, closedByDefault = false)
    String GENERAL = "general";

    @ConfigSection(name = "Equipment", description = "Use the equipment supplied by the player. Fill in two or more styles; the plugin wears another one when the demon's overhead protects against the current style.", position = 30, closedByDefault = true)
    String EQUIPMENT = "equipment";

    @ConfigSection(name = "Supplies", description = "Choose supplies and the points at which to use them.", position = 40, closedByDefault = false)
    String SUPPLIES = "supplies";

    @ConfigSection(name = "Loot", description = "Choose which drops to pick up.", position = 50, closedByDefault = true)
    String LOOT = "loot";

    @ConfigSection(name = "Emergency teleport", description = "Teleport to Ferox Enclave with the Ring of dueling when Hitpoints are low.", position = 60, closedByDefault = true)
    String SAFETY = "safety";

    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show runtime, room, status, kills and loot while the plugin is enabled.", position = 0)
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(keyName = "room", name = "Room", description = "The single-combat room to fight in.", position = 0, section = GENERAL)
    default DemonRoom room()
    {
        return DemonRoom.PLINTH_CHAMBER;
    }

    @ConfigItem(keyName = "offensivePrayer", name = "Offensive prayer", description = "Use Piety, Rigour or Augury for the current equipment style when unlocked.", position = 1, section = GENERAL)
    default boolean offensivePrayer()
    {
        return false;
    }

    @ConfigItem(keyName = "magicGear", name = "Magic equipment IDs", description = "Comma-separated magic equipment item IDs. Configure autocast or a powered weapon before starting. Blank skips this style.", position = 0, section = EQUIPMENT)
    default String magicGear()
    {
        return "";
    }

    @ConfigItem(keyName = "rangedGear", name = "Ranged equipment IDs", description = "Comma-separated ranged equipment item IDs. Wear the ammunition; it stays worn for the other styles. Blank skips this style.", position = 1, section = EQUIPMENT)
    default String rangedGear()
    {
        return "27235,33639,28951,27238,27241,29591,26235,31097,28310";
    }

    @ConfigItem(keyName = "meleeGear", name = "Melee equipment IDs", description = "Comma-separated melee equipment item IDs. Blank skips this style.", position = 2, section = EQUIPMENT)
    default String meleeGear()
    {
        return "26382,29801,21295,26384,26386,29589,22322,22981,31097,28307";
    }

    @ConfigItem(keyName = "keepItems", name = "Retained inventory IDs", description = "Comma-separated extra inventory item IDs to retain when banking, such as runes, a rune pouch or weapon charges. These items must be supplied by the player.", position = 3, section = EQUIPMENT)
    default String keepItems()
    {
        return "";
    }

    @ConfigItem(keyName = "restockItems", name = "Additional supplies/ammunition", description = "Additional supplies and ammunition as itemID:quantity pairs, comma separated; include runes and ammunition needed per trip.", position = 4, section = EQUIPMENT)
    default String restockItems()
    {
        return "";
    }

    @Range(min = 1, max = 2147483647)
    @ConfigItem(keyName = "foodId", name = "Food item ID", description = "Unnoted food item ID to withdraw and eat. The default is dark crab.", position = 0, section = SUPPLIES)
    default int foodId()
    {
        return 11936;
    }

    @Range(min = 1, max = 24)
    @ConfigItem(keyName = "foodCount", name = "Food count", description = "Number of food items to carry after banking.", position = 1, section = SUPPLIES)
    default int foodCount()
    {
        return 12;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "eatAt", name = "Eat at Hitpoints (%)", description = "Eat when current Hitpoints are at or below this percentage of maximum Hitpoints. A fire bomb hits up to 45.", position = 2, section = SUPPLIES)
    default int eatAt()
    {
        return 55;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(keyName = "restoreCount", name = "Prayer restore count", description = "Number of prayer potions or super restores to carry. Accepts all doses of both.", position = 3, section = SUPPLIES)
    default int restoreCount()
    {
        return 4;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "drinkAt", name = "Drink at Prayer points (%)", description = "Drink a restore when current Prayer is at or below this percentage of maximum Prayer.", position = 4, section = SUPPLIES)
    default int drinkAt()
    {
        return 20;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "scrollCount", name = "Temple teleports", description = "Number of Guthixian temple teleports to carry after banking.", position = 5, section = SUPPLIES)
    default int scrollCount()
    {
        return 5;
    }

    @Range(min = 0, max = 10000000)
    @ConfigItem(keyName = "lootMinimum", name = "Minimum value", description = "Pick up drops worth at least this many coins. Drops without a known price are taken only from the list below.", position = 0, section = LOOT)
    default int lootMinimum()
    {
        return 5000;
    }

    @ConfigItem(keyName = "lootAlways", name = "Always take", description = "Comma-separated item names to pick up regardless of value.", position = 1, section = LOOT)
    default String lootAlways()
    {
        return "Tormented synapse, Burning claw, Guthixian temple teleport";
    }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "emergencyAt", name = "At or below HP (%)", description = "Teleport to Ferox Enclave when current Hitpoints are at or below this percentage of maximum Hitpoints.", position = 0, section = SAFETY)
    default int emergencyAt()
    {
        return 25;
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
