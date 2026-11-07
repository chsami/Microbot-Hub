package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.config.TimingConfig;

@ConfigGroup(PhosanisNightmareConfig.GROUP)
public interface PhosanisNightmareConfig extends TimingConfig
{
    String GROUP = "fourtifyPhosanisNightmare";

    @ConfigSection(name = "Timing", description = "Adjust action delays. One game tick is about 600 milliseconds.", position = 80, closedByDefault = true)
    String TIMING = "advancedTiming";

    @ConfigSection(name = "General", description = "Bank at Ferox Enclave with a Ring of dueling and travel in with Drakan's medallion. Start at Ferox Enclave or in the Sisterhood Sanctuary.", position = 20, closedByDefault = false)
    String GENERAL = "general";

    @ConfigSection(name = "Equipment", description = "Use the equipment supplied by the player. Every listed ID must be worn or carried.", position = 30, closedByDefault = true)
    String EQUIPMENT = "equipment";

    @ConfigSection(name = "Supplies", description = "Choose supplies and the points at which to use them.", position = 40, closedByDefault = false)
    String SUPPLIES = "supplies";

    @ConfigSection(name = "Loot", description = "Choose which drops to pick up.", position = 50, closedByDefault = true)
    String LOOT = "loot";

    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show runtime, status, phase, kills, kills per hour and loot value while the plugin is enabled.", position = 0)
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(keyName = "offensivePrayers", name = "Offensive prayers", description = "Use Piety in melee gear and Augury in magic gear when unlocked.", position = 0, section = GENERAL)
    default boolean offensivePrayers()
    {
        return true;
    }

    @ConfigItem(keyName = "ghostlyThrall", name = "Ghostly thrall", description = "Summon the best ghostly thrall your Prayer points cover on the totems. Needs the Arceuus spellbook, runes and the Book of the Dead.", position = 1, section = GENERAL)
    default boolean ghostlyThrall()
    {
        return false;
    }

    @ConfigItem(keyName = "deathCharge", name = "Death Charge", description = "Cast Death Charge before using special attacks. Needs the Arceuus spellbook and runes.", position = 2, section = GENERAL)
    default boolean deathCharge()
    {
        return false;
    }

    @ConfigItem(keyName = "redemption", name = "Redemption", description = "Pray Redemption in the final phase, when she only uses her claws.", position = 3, section = GENERAL)
    default boolean redemption()
    {
        return true;
    }

    @ConfigItem(keyName = "meleeGear", name = "Melee gear IDs", description = "Melee equipment item IDs separated by commas, worn against the boss. Every ID must be supplied by the player.", position = 0, section = EQUIPMENT)
    default String meleeGear()
    {
        return "24419,29801,21295,24420,24421,24417,22322,22947,22981,31097,28307";
    }

    @ConfigItem(keyName = "magicGear", name = "Magic gear IDs", description = "Item IDs worn for the totems, separated by commas. Use a powered staff or set autocast before starting.", position = 1, section = EQUIPMENT)
    default String magicGear()
    {
        return "21018,12002,21791,21021,21024,27275,31106,28313";
    }

    @ConfigItem(keyName = "huskWeapon", name = "Husk weapon ID", description = "Weapon for the husks, wielded alone on its crush style: its max hit must reach 20 in either set. Blank uses the melee weapon.", position = 2, section = EQUIPMENT)
    default String huskWeapon()
    {
        return "34027";
    }

    @ConfigItem(keyName = "parasiteWeapon", name = "Parasite weapon ID", description = "Weapon for a weakened parasite, used on its crush style. It needs a 55 max hit. Blank uses the melee weapon.", position = 3, section = EQUIPMENT)
    default String parasiteWeapon()
    {
        return "34027";
    }

    @ConfigItem(keyName = "sleepwalkerWeapon", name = "Sleepwalker weapon ID", description = "Weapon for the sleepwalkers, such as blisterwood stakes. Blank uses the magic weapon.", position = 4, section = EQUIPMENT)
    default String sleepwalkerWeapon()
    {
        return "33716";
    }

    @ConfigItem(keyName = "specWeapon", name = "Spec weapon ID", description = "Special attack weapon. Blank uses no special attacks.", position = 5, section = EQUIPMENT)
    default String specWeapon()
    {
        return "33631";
    }

    @ConfigItem(keyName = "specTiming", name = "Spec timing", description = "Use special attacks only in the final phase, or whenever energy allows.", position = 6, section = EQUIPMENT)
    default SpecTiming specTiming()
    {
        return SpecTiming.FINAL_PHASE;
    }

    @ConfigItem(keyName = "keepItems", name = "Keep items", description = "Extra inventory item IDs to keep when banking, separated by commas, such as a rune pouch or an imbued heart.", position = 7, section = EQUIPMENT)
    default String keepItems()
    {
        return "";
    }

    @Range(min = 1, max = 2147483647)
    @ConfigItem(keyName = "foodId", name = "Food ID", description = "Unnoted food item ID to withdraw and eat.", position = 0, section = SUPPLIES)
    default int foodId()
    {
        return 13441;
    }

    @Range(min = 0, max = 24)
    @ConfigItem(keyName = "foodCount", name = "Food count", description = "Number of food items to carry after banking.", position = 1, section = SUPPLIES)
    default int foodCount()
    {
        return 8;
    }

    @Range(min = 1, max = 120)
    @ConfigItem(keyName = "eatAt", name = "Eat at HP", description = "Eat when current Hitpoints are at or below this number.", position = 2, section = SUPPLIES)
    default int eatAt()
    {
        return 70;
    }

    @Range(min = 0, max = 2147483647)
    @ConfigItem(keyName = "combatPotionId", name = "Combat potion ID", description = "Item ID of the combat potion to carry; every dose of it is accepted. 0 carries none.", position = 3, section = SUPPLIES)
    default int combatPotionId()
    {
        return 23685;
    }

    @Range(min = 0, max = 96)
    @ConfigItem(keyName = "restoreDoses", name = "Restore doses", description = "Super restore or prayer potion doses to carry after banking.", position = 4, section = SUPPLIES)
    default int restoreDoses()
    {
        return 16;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "drinkAt", name = "Drink at prayer", description = "Drink a restore when current Prayer points are at or below this number.", position = 5, section = SUPPLIES)
    default int drinkAt()
    {
        return 30;
    }

    @ConfigItem(keyName = "parasiteCure", name = "Parasite cure", description = "Potion that weakens a parasite.", position = 6, section = SUPPLIES)
    default ParasiteCure parasiteCure()
    {
        return ParasiteCure.SANFEW_SERUM;
    }

    @Range(min = 0, max = 96)
    @ConfigItem(keyName = "cureDoses", name = "Cure doses", description = "Parasite cure doses to carry after banking.", position = 7, section = SUPPLIES)
    default int cureDoses()
    {
        return 8;
    }

    @Range(min = 0, max = 24)
    @ConfigItem(keyName = "nextKillFood", name = "Next kill: food", description = "Start another kill only with at least this much food; otherwise bank.", position = 8, section = SUPPLIES)
    default int nextKillFood()
    {
        return 4;
    }

    @Range(min = 0, max = 96)
    @ConfigItem(keyName = "nextKillRestores", name = "Next kill: restores", description = "Start another kill only with at least this many restore doses.", position = 9, section = SUPPLIES)
    default int nextKillRestores()
    {
        return 4;
    }

    @Range(min = 0, max = 96)
    @ConfigItem(keyName = "nextKillCures", name = "Next kill: cures", description = "Start another kill only with at least this many cure doses.", position = 10, section = SUPPLIES)
    default int nextKillCures()
    {
        return 4;
    }

    @Range(min = 0, max = 10000000)
    @ConfigItem(keyName = "lootMinimum", name = "Min loot value", description = "Pick up drops worth at least this many coins. Drops without a known price are taken only from the list below.", position = 0, section = LOOT)
    default int lootMinimum()
    {
        return 5000;
    }

    @ConfigItem(keyName = "lootAlways", name = "Always loot", description = "Comma-separated item names to pick up regardless of value.", position = 1, section = LOOT)
    default String lootAlways()
    {
        return "Slepey tablet, Parasitic egg, Jar of dreams, Little nightmare, Nightmare staff, Inquisitor's great helm, Inquisitor's hauberk, Inquisitor's plateskirt, Inquisitor's mace, Eldritch orb, Harmonised orb, Volatile orb";
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
