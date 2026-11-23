package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation("Automated Herb Runs across all patches<br/><br/>" +
        "<b>Two Setup Options:</b><br/>" +
        "1. Inventory Setup: Use your custom inventory configuration<br/>" +
        "2. Auto Banking: Let the plugin handle everything!<br/><br/>" +
        "<b>Auto Banking withdraws:</b><br/>" +
        "• Farming tools (rake, spade, seed dibber, magic secateurs)<br/>" +
        "• Teleportation runes (law, air, earth, fire, water)<br/>" +
        "• Your selected herb seeds<br/>" +
        "• Your selected compost type<br/>" +
        "• Ectophial (if Morytania is enabled)<br/><br/>" +
        "Credits to liftedmango and See1Duck")
@ConfigGroup("AutoFarmer")
public interface AutoFarmerConfig extends Config {

    @ConfigSection(
            name = "Inventory Setup Method",
            description = "Choose between inventory setup or auto banking",
            position = 0
    )
    String inventorySection = "inventory";

    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use Inventory Setup",
            description = "Enable to use RuneLite inventory setups | Disable for automatic banking",
            section = inventorySection,
            position = 0
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup Name",
            description = "Select your pre-configured inventory setup",
            section = inventorySection,
            position = 1
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigSection(
            name = "Auto Banking Settings",
            description = "Configure automatic banking options",
            position = 1
    )
    String autoSection = "autobanking";

    @ConfigItem(
            keyName = "herbSeedType",
            name = "Herb Seed Type",
            description = "Choose which herb seeds to plant",
            section = autoSection,
            position = 0
    )
    default HerbSeedType herbSeedType() {
        return HerbSeedType.BEST;
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost Type",
            description = "Type of compost to use (select NONE to disable composting)",
            section = autoSection,
            position = 1
    )
    default CompostType compostType() {
        return CompostType.ULTRA;
    }

    @ConfigItem(
            keyName = "allowPartialRuns",
            name = "Allow Partial Runs",
            description = "Allow herb runs with fewer seeds than patches available",
            section = autoSection,
            position = 2
    )
    default boolean allowPartialRuns() {
        return false;
    }

    @ConfigItem(
            keyName = "dropEmptyBuckets",
            name = "Drop Empty Buckets",
            description = "Drop empty buckets after applying compost to patches",
            section = autoSection,
            position = 3
    )
    default boolean dropEmptyBuckets() {
        return true;
    }

    @ConfigItem(keyName = "buySeedsWhenEmpty", name = "GE seeds when empty (+99%)", description = "Only at zero plantable herb seeds in bank and inventory: buy 100 of the highest eligible/configured seed using +99%. Existing offers are left alone.", position = 21, section = settingsSection)
    default boolean buySeedsWhenEmpty() { return false; }

    @ConfigItem(keyName = "clearFailureAndResume", name = "Clear failure and resume", description = "After fixing the problem, enable this and start AutoFarmer again. Resets itself to off.", position = 20, section = settingsSection)
    default boolean clearFailureAndResume() { return false; }

    @ConfigItem(keyName = "dropEventFood", name = "Drop ordinary event food", description = "Drop only ordinary food newly received from a completed built-in random event; preserve existing food, money, gems, outfits and rare items", position = 19, section = settingsSection)
    default boolean dropEventFood() { return true; }

    @ConfigItem(keyName = "useLampsOnFarming", name = "Use event XP rewards on Farming", description = "Spend standard random-event lamps and Books of knowledge on Farming. Other reward items remain protected.", position = 18, section = settingsSection)
    default boolean useLampsOnFarming() { return false; }

    @ConfigItem(keyName = "builtInRandomEvents", name = "Built-in random events", description = "Accept Genie/Count Check XP rewards and dismiss other invitations. Spending lamps/books on Farming requires its own setting. Disable the standalone Local helper so this policy is in control.", position = 17, section = settingsSection)
    default boolean builtInRandomEvents() { return true; }

    @ConfigItem(keyName = "useAirBattlestaff", name = "Use air battlestaff", description = "Equip a banked air battlestaff when usable and omit air rune withdrawals while equipped", position = 15, section = settingsSection)
    default boolean useAirBattlestaff() { return true; }
    @ConfigItem(keyName = "refillLeprechaun", name = "Refill leprechaun compost", description = "Below 12 stored compost, carry up to 100 total from your bank as notes and deposit at the next patch", position = 16, section = settingsSection)
    default boolean refillLeprechaun() { return true; }

    @ConfigItem(keyName = "smoothMouse", name = "Smooth mouse movement", description = "Use the client's curved mouse paths with more movement steps and varied reaction time", position = 13, section = settingsSection)
    default boolean smoothMouse() { return true; }
    @ConfigItem(keyName = "relaxedPacing", name = "Relaxed action pacing", description = "Add short varied pauses before farming and banking interactions", position = 14, section = settingsSection)
    default boolean relaxedPacing() { return true; }

    @ConfigItem(keyName = "logoutBetweenRuns", name = "Logout while crops grow", description = "Logout after the run and login when the next patch check is due. Keep the client open.", position = 11, section = settingsSection)
    default boolean logoutBetweenRuns() { return true; }

    @Range(min = 0, max = 1440)
    @ConfigItem(keyName = "cropLogoutThresholdMinutes", name = "Minimum crop wait to log out (minutes)",
            description = "Log out when the time until the next crop check exceeds this many minutes. 0 allows any positive wait.",
            position = 22, section = settingsSection)
    default int cropLogoutThresholdMinutes() { return 1; }

    @Range(min = 5, max = 180)
    @ConfigItem(keyName = "growthMinutes", name = "Crop check interval (minutes)", description = "Approximate delay after planting; live patch state is checked on return", position = 12, section = settingsSection)
    default int growthMinutes() { return 90; }

    @ConfigItem(keyName = "repeatRuns", name = "Repeat runs", description = "Check for ready patches every five minutes after each run", position = 10, section = settingsSection)
    default boolean repeatRuns() { return true; }

    @ConfigSection(
            name = "General Settings",
            description = "General plugin settings",
            position = 2
    )
    String settingsSection = "settings";

    @ConfigItem(
            keyName = "goToBank",
            name = "Bank After Run",
            description = "Go to closest bank after completing the herb run",
            position = 0,
            section = settingsSection
    )
    default boolean goToBank() {
        return true;
    }

    @ConfigItem(
            keyName = "enableTrollheim",
            name = "Enable Trollheim Patch",
            description = "Enable Trollheim patch in herb run (requires My Arm's Big Adventure)",
            position = 1,
            section = locationSection
    )
    default boolean enableTrollheim() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCatherby",
            name = "Enable Catherby Patch",
            description = "Enable Catherby patch in herb run",
            position = 2,
            section = locationSection
    )
    default boolean enableCatherby() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMorytania",
            name = "Enable Morytania Patch",
            description = "Enable Morytania patch in herb run",
            position = 3,
            section = locationSection
    )
    default boolean enableMorytania() {
        return true;
    }

    @ConfigItem(
            keyName = "enableVarlamore",
            name = "Enable Varlamore Patch",
            description = "Enable Varlamore patch in herb run",
            position = 4,
            section = locationSection
    )
    default boolean enableVarlamore() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHosidius",
            name = "Enable Hosidius Patch",
            description = "Enable Hosidius patch in herb run",
            position = 5,
            section = locationSection
    )
    default boolean enableHosidius() {
        return true;
    }

    @ConfigItem(
            keyName = "enableArdougne",
            name = "Enable Ardougne Patch",
            description = "Enable Ardougne patch in herb run",
            position = 6,
            section = locationSection
    )
    default boolean enableArdougne() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFalador",
            name = "Enable Falador Patch",
            description = "Enable Falador patch in herb run",
            position = 7,
            section = locationSection
    )
    default boolean enableFalador() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeiss",
            name = "Enable Weiss Patch",
            description = "Enable Weiss patch in herb run (requires Making Friends with My Arm)",
            position = 8,
            section = locationSection
    )
    default boolean enableWeiss() {
        return true;
    }

    @ConfigItem(
            keyName = "enableGuild",
            name = "Enable Farming Guild Patch",
            description = "Enable Farming Guild patch in herb run",
            position = 9,
            section = locationSection
    )
    default boolean enableGuild() {
        return true;
    }

    //    @ConfigItem(
//            keyName = "enableHarmony",
//            name = "Enable Harmony Island Patch",
//            description = "Enable Harmony Island patch in herb run",
//            position = 9,
//            section = locationSection
//    )
//    default boolean enableHarmony() {
//        return false;
//    }
    @ConfigSection(
            name = "Location toggles",
            description = "Location toggles",
            position = 3
    )
    String locationSection = "Location";

    @ConfigSection(
            name = "Allotments",
            description = "Allotment patch settings",
            position = 4
    )
    String allotmentSection = "allotments";

    @ConfigItem(
            keyName = "enableAllotments",
            name = "Enable Allotments",
            description = "Plant and harvest allotment patches at each location",
            section = allotmentSection,
            position = 0
    )
    default boolean enableAllotments() {
        return false;
    }

    @ConfigItem(
            keyName = "allotmentSeedType",
            name = "Allotment Seed Type",
            description = "Choose which allotment seeds to plant (3 seeds per patch)",
            section = allotmentSection,
            position = 1
    )
    default AllotmentSeedType allotmentSeedType() {
        return AllotmentSeedType.SWEETCORN;
    }

    @ConfigSection(
            name = "Flowers",
            description = "Flower patch settings",
            position = 5
    )
    String flowerSection = "flowers";

    @ConfigItem(
            keyName = "enableFlowers",
            name = "Enable Flowers",
            description = "Plant and harvest flower patches at each location",
            section = flowerSection,
            position = 0
    )
    default boolean enableFlowers() {
        return false;
    }

    @ConfigItem(
            keyName = "flowerSeedType",
            name = "Flower Seed Type",
            description = "Choose which flower seeds to plant (White lily protects all allotments)",
            section = flowerSection,
            position = 1
    )
    default FlowerSeedType flowerSeedType() {
        return FlowerSeedType.LIMPWURT;
    }


    @ConfigSection(name = "Session limits", description = "Daily break and patch recovery", position = 31)
    String sessionSection = "sessionLimits";

    @ConfigItem(keyName = "dailyBreak", name = "Daily break",
            description = "Log out for 3 to 6 hours at a random time, at most once per local calendar day",
            section = sessionSection, position = 0)
    default boolean dailyBreak() { return false; }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "patchRetryLimit", name = "Patch retry limit",
            description = "Failed attempts at the same action before deferring the patch for 15 minutes",
            section = sessionSection, position = 1)
    default int patchRetryLimit() { return 3; }

    @ConfigSection(name = "Trees", description = "Regular and fruit tree runs", position = 30)
    String treeSection = "trees";

    @ConfigItem(keyName = "enableTrees", name = "Enable Regular Trees",
            description = "Check health, clear, and replant regular trees", section = treeSection, position = 0)
    default boolean enableTrees() { return false; }

    @ConfigItem(keyName = "regularSapling", name = "Regular Tree Sapling",
            description = "Keep this sapling in the bank or your inventory", section = treeSection, position = 1)
    default TreeSapling.Regular regularSapling() { return TreeSapling.Regular.Willow; }

    @ConfigItem(keyName = "enableFruitTrees", name = "Enable Fruit Trees",
            description = "Check health, clear, and replant fruit trees", section = treeSection, position = 2)
    default boolean enableFruitTrees() { return false; }

    @ConfigItem(keyName = "fruitSapling", name = "Fruit Tree Sapling",
            description = "Keep this sapling in the bank or your inventory", section = treeSection, position = 3)
    default TreeSapling.Fruit fruitSapling() { return TreeSapling.Fruit.Curry; }

    @ConfigItem(keyName = "payGardenerToClear", name = "Pay Gardener to Clear",
            description = "Pay 200 GP per mature tree after checking health; requires coins instead of an axe",
            section = treeSection, position = 4)
    default boolean payGardenerToClear() { return false; }

}
