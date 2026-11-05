package net.runelite.client.plugins.microbot.fourtifysbarrows.config;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(BarrowsConfig.GROUP)
public interface BarrowsConfig extends TimingConfig
{
    String GROUP = "fourtifyBarrows";

    @ConfigSection(name = "Timing", description = "Adjust action delays. One game tick is about 600 milliseconds.", position = 10, closedByDefault = true)
    String TIMING = "advancedTiming";

    @ConfigSection(name = "General", description = "The plugin disables auto-retaliate to control reward potential. It remains off after stopping. Choose the reward target and banking policy.", position = 20, closedByDefault = false)
    String GENERAL = "general";

    @ConfigSection(name = "Supplies", description = "Choose supplies and the remaining points at which to use them.", position = 30, closedByDefault = false)
    String SUPPLIES = "supplies";

    @ConfigSection(name = "Travel to Bank", description = "Return to Ferox Enclave for banking and restoration.", position = 34, closedByDefault = false)
    String BANK_TRAVEL = "bankTravel";

    @ConfigSection(name = "Travel to Barrows", description = "Choose the prepared teleport used to reach Barrows.", position = 35, closedByDefault = false)
    String TRAVEL = "travel";

    @ConfigSection(name = "Equipment", description = "Use the equipment supplied by the player. Configure autocast or a powered weapon before starting.", position = 40, closedByDefault = true)
    String EQUIPMENT = "equipment";

    @ConfigSection(name = "Emergency teleport", description = "Choose the emergency teleport and low-health threshold.", position = 60, closedByDefault = true)
    String SAFETY = "safety";

    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show runtime, activity, status, and dispatched requests while the plugin is enabled.", position = 0)
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(keyName = "bankEachChest", name = "Bank after each chest", description = "Use a charged Ring of dueling to return to Ferox Enclave after every observed chest reward.", position = 1, section = GENERAL)
    default boolean bankEachChest()
    {
        return true;
    }

    @Range(min = 0, max = 880)
    @ConfigItem(keyName = "rewardTarget", name = "Reward potential target", description = "Auto-retaliate is disabled and remains off after stopping. Target reward points before searching the chest; remaining brother combat levels count toward this target.", position = 2, section = GENERAL)
    default int rewardTarget()
    {
        return 880;
    }

    @ConfigItem(keyName = "travelMethod", name = "Travel method", description = "Use the selected teleport only. Carry its item in inventory, or prepare the active Arceuus spellbook and the Barrows spell's requirements. The minigame teleport must be unlocked and ready.", position = 0, section = TRAVEL)
    default TravelMethod travelMethod()
    {
        return TravelMethod.BARROWS_TABLET;
    }

    @ConfigItem(keyName = "bankTeleport", name = "Travel method", description = "Use a charged Ring of Dueling to reach Ferox Enclave, then restore at the pool and use the bank.", position = 0, section = BANK_TRAVEL)
    default BankTeleport bankTeleport()
    {
        return BankTeleport.RING_OF_DUELING;
    }

    @Range(min = 1, max = 2147483647)
    @ConfigItem(keyName = "foodId", name = "Food item ID", description = "Unnoted food item ID to withdraw and eat. The default is shark.", position = 0, section = SUPPLIES)
    default int foodId()
    {
        return 385;
    }

    @Range(min = 1, max = 24)
    @ConfigItem(keyName = "foodCount", name = "Food count", description = "Number of food items to carry after resupply. Leave room for equipment, potions, a spade and loot.", position = 1, section = SUPPLIES)
    default int foodCount()
    {
        return 10;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "eatAt", name = "Eat at Hitpoints (%)", description = "Eat when current Hitpoints are at or below this percentage of maximum Hitpoints.", position = 2, section = SUPPLIES)
    default int eatAt()
    {
        return 55;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(keyName = "potionCount", name = "Prayer potion count", description = "Number of prayer potion bottles to carry. Accepts item IDs 2434, 139, 141 and 143, covering four to one doses.", position = 3, section = SUPPLIES)
    default int potionCount()
    {
        return 4;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "drinkAt", name = "Drink at Prayer points (%)", description = "Drink a prayer potion when current Prayer is at or below this percentage of maximum Prayer.", position = 4, section = SUPPLIES)
    default int drinkAt()
    {
        return 20;
    }

    @ConfigItem(keyName = "magicGear", name = "Magic equipment IDs", description = "Comma-separated item IDs to equip for brothers other than Ahrim. Configure autocast or a powered weapon before starting. Blank keeps the equipment supplied by the player.", position = 0, section = EQUIPMENT)
    default String magicGear()
    {
        return "21018,12002,21791,21021,21024,31113,27251,22947,31106,31097,28313";
    }

    @ConfigItem(keyName = "rangedGear", name = "Ahrim equipment IDs", description = "Comma-separated ranged equipment item IDs for Ahrim. Supply ammunition as required. Blank keeps the equipment supplied by the player.", position = 1, section = EQUIPMENT)
    default String rangedGear()
    {
        return "27235,33639,28951,27238,27241,12926,22947,26235,31097,28310";
    }

    @ConfigItem(keyName = "meleeGear", name = "Tunnel equipment IDs", description = "Comma-separated melee equipment item IDs for tunnel creatures. Blank keeps the equipment supplied by the player.", position = 2, section = EQUIPMENT)
    default String meleeGear()
    {
        return "";
    }

    @ConfigItem(keyName = "keepItems", name = "Retained inventory IDs", description = "Comma-separated extra inventory item IDs to retain when banking, such as runes, a rune pouch, teleports or weapon charges. These items must be supplied by the player.", position = 3, section = EQUIPMENT)
    default String keepItems()
    {
        return "";
    }

    @ConfigItem(keyName = "restockItems", name = "Additional supplies/ammunition", description = "Additional supplies and ammunition as itemID:quantity pairs, comma separated; include runes and ammunition needed per trip.", position = 4, section = EQUIPMENT)
    default String restockItems()
    {
        return "";
    }

    @ConfigItem(keyName = "emergencyTeleport", name = "Teleport", description = "Use a charged Ring of Dueling to reach Ferox Enclave when Hitpoints are low. Carry or wear the ring before starting.", position = 0, section = SAFETY)
    default EmergencyTeleport emergencyTeleport()
    {
        return EmergencyTeleport.RING_OF_DUELING;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "emergencyAt", name = "At or below HP (%)", description = "Attempt emergency escape when current Hitpoints are at or below this percentage of maximum Hitpoints.", position = 1, section = SAFETY)
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
