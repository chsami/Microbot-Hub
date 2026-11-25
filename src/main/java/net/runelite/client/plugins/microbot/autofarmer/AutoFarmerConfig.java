package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation("Choose crops and patches, then start. Banking and travel are handled automatically.<br/><br/>" +
        "Custom inventory, movement and recovery options are below.<br/>Credits: liftedmango and See1Duck")
@ConfigGroup("AutoFarmer")
public interface AutoFarmerConfig extends Config {

    @ConfigSection(name = "Custom inventory", description = "Custom inventory", position = 7, closedByDefault = true)
    String inventorySection = "inventory";

    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use saved setup",
            description = "Enable to use RuneLite inventory setups | Disable for automatic banking",
            section = inventorySection,
            position = 0
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Saved setup",
            description = "Select your pre-configured inventory setup",
            section = inventorySection,
            position = 1
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigSection(name = "Farm setup", description = "Farm setup", position = 0, closedByDefault = false)
    String autoSection = "autobanking";

    @ConfigItem(
            keyName = "herbSeedType",
            name = "Herb seeds",
            description = "Choose which herb seeds to plant",
            section = autoSection,
            position = 0
    )
    default HerbSeedType herbSeedType() {
        return HerbSeedType.BEST;
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost",
            description = "Type of compost to use (select NONE to disable composting)",
            section = autoSection,
            position = 1
    )
    default CompostType compostType() {
        return CompostType.ULTRA;
    }

    @ConfigItem(
            keyName = "allowPartialRuns",
            name = "Allow partial runs",
            description = "Allow herb runs with fewer seeds than patches available",
            section = advancedSection,
            position = 0
    )
    default boolean allowPartialRuns() {
        return false;
    }

    @ConfigItem(
            keyName = "dropEmptyBuckets",
            name = "Drop empty buckets",
            description = "Drop empty buckets after applying compost to patches",
            section = advancedSection,
            position = 1
    )
    default boolean dropEmptyBuckets() {
        return true;
    }

    @ConfigItem(keyName = "buySeedsWhenEmpty", name = "Buy seeds at GE", description = "Only at zero plantable herb seeds in bank and inventory: buy 100 of the highest eligible/configured seed using +99%. Existing offers are left alone.", position = 2, section = advancedSection)
    default boolean buySeedsWhenEmpty() { return false; }

    @ConfigItem(keyName = "clearFailureAndResume", name = "Clear failure", description = "After fixing the problem, enable this and start AutoFarmer again. Resets itself to off.", position = 0, section = sessionSection)
    default boolean clearFailureAndResume() { return false; }

    @ConfigItem(keyName = "dropEventFood", name = "Drop event food", description = "Drop only ordinary food newly received from a completed built-in random event; preserve existing food, money, gems, outfits and rare items", position = 2, section = eventsSection)
    default boolean dropEventFood() { return true; }

    @ConfigItem(keyName = "useLampsOnFarming", name = "Use XP on Farming", description = "Spend standard random-event lamps and Books of knowledge on Farming. Other reward items remain protected.", position = 1, section = eventsSection)
    default boolean useLampsOnFarming() { return false; }

    @ConfigItem(keyName = "builtInRandomEvents", name = "Handle random events", description = "Accept Genie/Count Check XP rewards and dismiss other invitations. Spending lamps/books on Farming requires its own setting. Disable the standalone Local helper so this policy is in control.", position = 0, section = eventsSection)
    default boolean builtInRandomEvents() { return true; }

    @ConfigItem(keyName = "useAirBattlestaff", name = "Use air battlestaff", description = "Equip a banked air battlestaff when usable and omit air rune withdrawals while equipped", position = 3, section = advancedSection)
    default boolean useAirBattlestaff() { return true; }
    @ConfigItem(keyName = "refillLeprechaun", name = "Top up compost", description = "Below 12 stored compost, carry up to 100 total from your bank as notes and deposit at the next patch", position = 4, section = advancedSection)
    default boolean refillLeprechaun() { return true; }

    @ConfigItem(keyName = "mouseMotionMode", name = "Mouse profile",
            description = "Client profile respects Microbot's Mouse settings without extra speed, reaction or deviation overrides. Legacy smoothing adds AutoFarmer's older slower movement tuning. Neither guarantees ban prevention.",
            position = 5, section = advancedSection)
    default MouseMotionMode mouseMotionMode() { return MouseMotionMode.CLIENT_PROFILE; }
    @ConfigItem(keyName = "relaxedPacing", name = "Relaxed pacing", description = "Add short varied pauses before farming and banking interactions", position = 6, section = advancedSection)
    default boolean relaxedPacing() { return true; }

    @ConfigItem(keyName = "logoutBetweenRuns", name = "Logout while growing", description = "Logout after the run and login when the next patch check is due. Keep the client open.", position = 1, section = settingsSection)
    default boolean logoutBetweenRuns() { return true; }

    @Range(min = 0, max = 1440)
    @ConfigItem(keyName = "cropLogoutThresholdMinutes", name = "Logout wait (min)",
            description = "Log out when the time until the next crop check exceeds this many minutes. 0 allows any positive wait.",
            position = 3, section = settingsSection)
    default int cropLogoutThresholdMinutes() { return 1; }

    @Range(min = 5, max = 180)
    @ConfigItem(keyName = "growthMinutes", name = "Crop wait (min)", description = "Approximate delay after planting; live patch state is checked on return", position = 2, section = settingsSection)
    default int growthMinutes() { return 90; }

    @ConfigItem(keyName = "repeatRuns", name = "Repeat runs", description = "Check for ready patches every five minutes after each run", position = 0, section = settingsSection)
    default boolean repeatRuns() { return true; }

    @ConfigSection(name = "Run schedule", description = "Run schedule", position = 1, closedByDefault = false)
    String settingsSection = "settings";

    @ConfigSection(name = "Advanced", description = "Optional banking and movement tuning", position = 8, closedByDefault = true)
    String advancedSection = "advanced";

    @ConfigSection(name = "Random events", description = "Random-event rewards and inventory cleanup", position = 6, closedByDefault = true)
    String eventsSection = "events";

    @ConfigItem(
            keyName = "goToBank",
            name = "Bank after run",
            description = "Go to closest bank after completing the herb run",
            position = 7,
            section = advancedSection
    )
    default boolean goToBank() {
        return true;
    }

    @ConfigItem(
            keyName = "enableTrollheim",
            name = "Trollheim",
            description = "Enable Trollheim patch in herb run (requires My Arm's Big Adventure)",
            position = 1,
            section = locationSection
    )
    default boolean enableTrollheim() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCatherby",
            name = "Catherby",
            description = "Enable Catherby patch in herb run",
            position = 2,
            section = locationSection
    )
    default boolean enableCatherby() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMorytania",
            name = "Morytania",
            description = "Enable Morytania patch in herb run",
            position = 3,
            section = locationSection
    )
    default boolean enableMorytania() {
        return true;
    }

    @ConfigItem(
            keyName = "enableVarlamore",
            name = "Varlamore",
            description = "Enable Varlamore patch in herb run",
            position = 4,
            section = locationSection
    )
    default boolean enableVarlamore() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHosidius",
            name = "Hosidius",
            description = "Enable Hosidius patch in herb run",
            position = 5,
            section = locationSection
    )
    default boolean enableHosidius() {
        return true;
    }

    @ConfigItem(
            keyName = "enableArdougne",
            name = "Ardougne",
            description = "Enable Ardougne patch in herb run",
            position = 6,
            section = locationSection
    )
    default boolean enableArdougne() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFalador",
            name = "Falador",
            description = "Enable Falador patch in herb run",
            position = 7,
            section = locationSection
    )
    default boolean enableFalador() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeiss",
            name = "Weiss",
            description = "Enable Weiss patch in herb run (requires Making Friends with My Arm)",
            position = 8,
            section = locationSection
    )
    default boolean enableWeiss() {
        return true;
    }

    @ConfigItem(
            keyName = "enableGuild",
            name = "Farming Guild",
            description = "Enable Farming Guild patch in herb run",
            position = 9,
            section = locationSection
    )
    default boolean enableGuild() {
        return true;
    }

    //    @ConfigItem(
//            keyName = "enableHarmony",
//            name = "Harmony Island",
//            description = "Enable Harmony Island patch in herb run",
//            position = 9,
//            section = locationSection
//    )
//    default boolean enableHarmony() {
//        return false;
//    }
    @ConfigSection(name = "Herb patches", description = "Herb patches", position = 2, closedByDefault = true)
    String locationSection = "Location";

    @ConfigSection(name = "Allotments", description = "Allotments", position = 4, closedByDefault = true)
    String allotmentSection = "allotments";

    @ConfigItem(
            keyName = "enableAllotments",
            name = "Grow allotments",
            description = "Plant and harvest allotment patches at each location",
            section = allotmentSection,
            position = 0
    )
    default boolean enableAllotments() {
        return false;
    }

    @ConfigItem(
            keyName = "allotmentSeedType",
            name = "Allotment seeds",
            description = "Choose which allotment seeds to plant (3 seeds per patch)",
            section = allotmentSection,
            position = 1
    )
    default AllotmentSeedType allotmentSeedType() {
        return AllotmentSeedType.SWEETCORN;
    }

    @ConfigSection(name = "Flowers", description = "Flowers", position = 5, closedByDefault = true)
    String flowerSection = "flowers";

    @ConfigItem(
            keyName = "enableFlowers",
            name = "Grow flowers",
            description = "Plant and harvest flower patches at each location",
            section = flowerSection,
            position = 0
    )
    default boolean enableFlowers() {
        return false;
    }

    @ConfigItem(
            keyName = "flowerSeedType",
            name = "Flower seeds",
            description = "Choose which flower seeds to plant (White lily protects all allotments)",
            section = flowerSection,
            position = 1
    )
    default FlowerSeedType flowerSeedType() {
        return FlowerSeedType.LIMPWURT;
    }


    @ConfigSection(name = "Recovery", description = "Recovery", position = 9, closedByDefault = true)
    String sessionSection = "sessionLimits";

    @ConfigItem(keyName = "dailyBreak", name = "Daily break",
            description = "Log out for 3 to 6 hours at a random time, at most once per local calendar day",
            section = settingsSection, position = 4)
    default boolean dailyBreak() { return false; }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "patchRetryLimit", name = "Patch retry limit",
            description = "Failed attempts at the same action before deferring the patch for 15 minutes",
            section = sessionSection, position = 1)
    default int patchRetryLimit() { return 3; }

    @ConfigSection(name = "Trees", description = "Trees", position = 3, closedByDefault = true)
    String treeSection = "trees";

    @ConfigItem(keyName = "enableTrees", name = "Regular trees",
            description = "Check health, clear, and replant regular trees", section = treeSection, position = 0)
    default boolean enableTrees() { return false; }

    @ConfigItem(keyName = "regularSapling", name = "Tree sapling",
            description = "Keep this sapling in the bank or your inventory", section = treeSection, position = 1)
    default TreeSapling.Regular regularSapling() { return TreeSapling.Regular.Willow; }

    @ConfigItem(keyName = "enableFruitTrees", name = "Fruit trees",
            description = "Check health, clear, and replant fruit trees", section = treeSection, position = 2)
    default boolean enableFruitTrees() { return false; }

    @ConfigItem(keyName = "fruitSapling", name = "Fruit sapling",
            description = "Keep this sapling in the bank or your inventory", section = treeSection, position = 3)
    default TreeSapling.Fruit fruitSapling() { return TreeSapling.Fruit.Curry; }

    @ConfigItem(keyName = "payGardenerToClear", name = "Pay to clear trees",
            description = "Pay 200 GP per mature tree after checking health; requires coins instead of an axe",
            section = treeSection, position = 4)
    default boolean payGardenerToClear() { return false; }

}
