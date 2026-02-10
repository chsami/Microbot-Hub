package net.runelite.client.plugins.microbot.slayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("microbot-slayer")
@ConfigInformation("Automated slayer task completion with banking and travel support")
public interface SlayerConfig extends Config {

    // =====================
    // General Section
    // =====================
    @ConfigSection(
            name = "General",
            description = "General plugin settings",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "General";

    @ConfigItem(
            keyName = "enablePlugin",
            name = "Enable Plugin",
            description = "Toggle the slayer plugin on/off",
            position = 0,
            section = generalSection
    )
    default boolean enablePlugin() {
        return true;
    }

    @ConfigItem(
            keyName = "enableAutoTravel",
            name = "Auto Travel",
            description = "Automatically travel to slayer task location",
            position = 1,
            section = generalSection
    )
    default boolean enableAutoTravel() {
        return true;
    }

    @ConfigItem(
            keyName = "slayerMaster",
            name = "Slayer Master",
            description = "Which slayer master to get tasks from",
            position = 2,
            section = generalSection
    )
    default SlayerMaster slayerMaster() {
        return SlayerMaster.DURADEL;
    }

    @ConfigItem(
            keyName = "getNewTask",
            name = "Get New Task",
            description = "Automatically get a new task when current task is complete",
            position = 3,
            section = generalSection
    )
    default boolean getNewTask() {
        return true;
    }

    // =====================
    // Task Management Section
    // =====================
    @ConfigSection(
            name = "Task Management",
            description = "Task skip and block settings",
            position = 5,
            closedByDefault = false
    )
    String taskSection = "Task Management";

    @ConfigItem(
            keyName = "enableAutoSkip",
            name = "Auto Skip Tasks",
            description = "Automatically skip tasks on the skip list (costs 30 points)",
            position = 0,
            section = taskSection
    )
    default boolean enableAutoSkip() {
        return false;
    }

    @ConfigItem(
            keyName = "skipTaskList",
            name = "Skip Task List",
            description = "Comma-separated list of task names to skip (e.g., 'Black demons, Hellhounds, Greater demons')",
            position = 1,
            section = taskSection
    )
    default String skipTaskList() {
        return "";
    }

    @ConfigItem(
            keyName = "minPointsToSkip",
            name = "Min Points to Skip",
            description = "Minimum slayer points required before skipping (keeps a reserve)",
            position = 2,
            section = taskSection
    )
    default int minPointsToSkip() {
        return 100;
    }

    @ConfigItem(
            keyName = "enableAutoBlock",
            name = "Auto Block Tasks",
            description = "Automatically block tasks on the block list (costs 100 points, permanent)",
            position = 3,
            section = taskSection
    )
    default boolean enableAutoBlock() {
        return false;
    }

    @ConfigItem(
            keyName = "blockTaskList",
            name = "Block Task List",
            description = "Comma-separated list of task names to permanently block (e.g., 'Spiritual creatures, Drakes')",
            position = 4,
            section = taskSection
    )
    default String blockTaskList() {
        return "";
    }

    @ConfigItem(
            keyName = "minPointsToBlock",
            name = "Min Points to Block",
            description = "Minimum slayer points required before blocking (keeps a reserve)",
            position = 5,
            section = taskSection
    )
    default int minPointsToBlock() {
        return 150;
    }

    // =====================
    // Banking Section
    // =====================
    @ConfigSection(
            name = "Banking",
            description = "Banking and inventory settings",
            position = 10,
            closedByDefault = false
    )
    String bankingSection = "Banking";

    @ConfigItem(
            keyName = "enableAutoBanking",
            name = "Auto Banking",
            description = "Automatically bank when supplies are low",
            position = 0,
            section = bankingSection
    )
    default boolean enableAutoBanking() {
        return true;
    }

    @ConfigItem(
            keyName = "foodThreshold",
            name = "Food Threshold",
            description = "Bank when food count drops below this amount (0 to disable)",
            position = 1,
            section = bankingSection
    )
    default int foodThreshold() {
        return 3;
    }

    @ConfigItem(
            keyName = "potionThreshold",
            name = "Prayer Potion Threshold",
            description = "Bank when prayer potion/super restore doses drop below this amount (0 to disable)",
            position = 2,
            section = bankingSection
    )
    default int potionThreshold() {
        return 4;
    }

    // =====================
    // POH Section
    // =====================
    @ConfigSection(
            name = "POH (House)",
            description = "Player Owned House settings for restoration",
            position = 15,
            closedByDefault = false
    )
    String pohSection = "POH (House)";

    @ConfigItem(
            keyName = "usePohPool",
            name = "Use POH Pool",
            description = "Use POH rejuvenation pool to restore HP/Prayer/Run energy",
            position = 0,
            section = pohSection
    )
    default boolean usePohPool() {
        return false;
    }

    @ConfigItem(
            keyName = "pohTeleportMethod",
            name = "Teleport Method",
            description = "How to teleport to your house",
            position = 1,
            section = pohSection
    )
    default PohTeleportMethod pohTeleportMethod() {
        return PohTeleportMethod.HOUSE_TAB;
    }

    @ConfigItem(
            keyName = "usePohBeforeBanking",
            name = "Use Before Banking",
            description = "Use POH pool to restore before going to bank (saves supplies)",
            position = 2,
            section = pohSection
    )
    default boolean usePohBeforeBanking() {
        return true;
    }

    @ConfigItem(
            keyName = "usePohAfterTask",
            name = "Use After Task",
            description = "Use POH pool after completing a task (before getting new task)",
            position = 3,
            section = pohSection
    )
    default boolean usePohAfterTask() {
        return true;
    }

    @ConfigItem(
            keyName = "pohRestoreThreshold",
            name = "Restore Below HP %",
            description = "Use POH when HP drops below this percentage (0 to only restore at task end)",
            position = 4,
            section = pohSection
    )
    default int pohRestoreThreshold() {
        return 50;
    }

    // =====================
    // Combat Section
    // =====================
    @ConfigSection(
            name = "Combat",
            description = "Combat settings",
            position = 20,
            closedByDefault = false
    )
    String combatSection = "Combat";

    @ConfigItem(
            keyName = "enableAutoCombat",
            name = "Auto Combat",
            description = "Automatically attack slayer task monsters",
            position = 0,
            section = combatSection
    )
    default boolean enableAutoCombat() {
        return true;
    }

    @ConfigItem(
            keyName = "attackRadius",
            name = "Attack Radius",
            description = "Maximum distance to search for monsters",
            position = 1,
            section = combatSection
    )
    default int attackRadius() {
        return 10;
    }

    @ConfigItem(
            keyName = "prioritizeSuperiors",
            name = "Prioritize Superiors",
            description = "Always attack superior slayer monsters first when they spawn",
            position = 2,
            section = combatSection
    )
    default boolean prioritizeSuperiors() {
        return true;
    }

    @ConfigItem(
            keyName = "eatAtHealthPercent",
            name = "Eat at HP %",
            description = "Eat food when health drops below this percentage",
            position = 3,
            section = combatSection
    )
    default int eatAtHealthPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "prayerFlickStyle",
            name = "Prayer Style",
            description = "How to manage prayer during combat",
            position = 4,
            section = combatSection
    )
    default PrayerFlickStyle prayerFlickStyle() {
        return PrayerFlickStyle.OFF;
    }

    @ConfigItem(
            keyName = "drinkPrayerAt",
            name = "Drink Prayer At",
            description = "Drink prayer potion when prayer points drop below this amount",
            position = 5,
            section = combatSection
    )
    default int drinkPrayerAt() {
        return 20;
    }

    @ConfigItem(
            keyName = "useCombatPotions",
            name = "Use Combat Potions",
            description = "Drink combat potions (attack, strength, defence, ranging, magic)",
            position = 6,
            section = combatSection
    )
    default boolean useCombatPotions() {
        return false;
    }

    @ConfigItem(
            keyName = "useOffensivePrayers",
            name = "Use Offensive Prayers",
            description = "Activate offensive prayers (Piety/Rigour/Augury) based on combat style",
            position = 7,
            section = combatSection
    )
    default boolean useOffensivePrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "dodgeProjectiles",
            name = "Dodge AOE Attacks",
            description = "Automatically dodge AOE projectile attacks (e.g., dragon poison pools)",
            position = 8,
            section = combatSection
    )
    default boolean dodgeProjectiles() {
        return false;
    }

    @ConfigItem(
            keyName = "hopWhenCrashed",
            name = "Hop When Crashed",
            description = "Hop worlds if no attackable targets found for 20 seconds (someone else has aggro)",
            position = 9,
            section = combatSection
    )
    default boolean hopWhenCrashed() {
        return true;
    }

    @ConfigItem(
            keyName = "hopWorldList",
            name = "Hop World List",
            description = "Comma-separated list of worlds to hop to (e.g., 390,391,392). Leave empty for random members world.",
            position = 10,
            section = combatSection
    )
    default String hopWorldList() {
        return "";
    }

    // =====================
    // Cannon Section
    // =====================
    @ConfigSection(
            name = "Cannon",
            description = "Dwarf multicannon settings",
            position = 25,
            closedByDefault = false
    )
    String cannonSection = "Cannon";

    @ConfigItem(
            keyName = "enableCannon",
            name = "Enable Cannon",
            description = "Use dwarf multicannon for tasks with predefined cannon spots",
            position = 0,
            section = cannonSection
    )
    default boolean enableCannon() {
        return false;
    }

    @ConfigItem(
            keyName = "cannonballThreshold",
            name = "Cannonball Threshold",
            description = "Bank when cannonball count drops below this amount",
            position = 1,
            section = cannonSection
    )
    default int cannonballThreshold() {
        return 100;
    }

    @ConfigItem(
            keyName = "cannonTaskList",
            name = "Cannon Tasks (Optional)",
            description = "Only use cannon for these tasks (comma-separated). Leave empty to cannon all supported tasks.",
            position = 2,
            section = cannonSection
    )
    default String cannonTaskList() {
        return "";
    }

    // =====================
    // Loot Section
    // =====================
    @ConfigSection(
            name = "Loot",
            description = "Looting settings",
            position = 30,
            closedByDefault = false
    )
    String lootSection = "Loot";

    @ConfigItem(
            keyName = "enableLooting",
            name = "Enable Looting",
            description = "Pick up loot from killed monsters",
            position = 0,
            section = lootSection
    )
    default boolean enableLooting() {
        return true;
    }

    @ConfigItem(
            keyName = "lootStyle",
            name = "Loot Style",
            description = "How to determine what to loot",
            position = 1,
            section = lootSection
    )
    default LootStyle lootStyle() {
        return LootStyle.MIXED;
    }

    @ConfigItem(
            keyName = "lootItemList",
            name = "Item List",
            description = "Comma-separated list of items to always loot. Uses exact name matching. Use * for wildcards (e.g., 'totem piece, *clue scroll*, ancient shard')",
            position = 2,
            section = lootSection
    )
    default String lootItemList() {
        return "";
    }

    @ConfigItem(
            keyName = "lootExcludeList",
            name = "Exclude List",
            description = "Comma-separated list of items to NEVER loot. Uses exact name matching. Use * for wildcards (e.g., 'vial, jug, *bones')",
            position = 3,
            section = lootSection
    )
    default String lootExcludeList() {
        return "";
    }

    @ConfigItem(
            keyName = "minLootValue",
            name = "Min Loot Value",
            description = "Minimum GE value to loot (0 to disable price filtering)",
            position = 3,
            section = lootSection
    )
    default int minLootValue() {
        return 1000;
    }

    @ConfigItem(
            keyName = "maxLootValue",
            name = "Max Loot Value",
            description = "Maximum GE value to loot (0 for no limit)",
            position = 4,
            section = lootSection
    )
    default int maxLootValue() {
        return 0;
    }

    @ConfigItem(
            keyName = "lootCoins",
            name = "Loot Coins",
            description = "Pick up coin stacks",
            position = 5,
            section = lootSection
    )
    default boolean lootCoins() {
        return true;
    }

    @ConfigItem(
            keyName = "minCoinStack",
            name = "Min Coin Stack",
            description = "Only loot coins if stack is at least this amount (0 = loot all)",
            position = 6,
            section = lootSection
    )
    default int minCoinStack() {
        return 0;
    }

    @ConfigItem(
            keyName = "lootArrows",
            name = "Loot Arrows",
            description = "Pick up arrow stacks (10+ arrows)",
            position = 7,
            section = lootSection
    )
    default boolean lootArrows() {
        return false;
    }

    @ConfigItem(
            keyName = "lootRunes",
            name = "Loot Runes",
            description = "Pick up rune stacks (2+ runes)",
            position = 8,
            section = lootSection
    )
    default boolean lootRunes() {
        return false;
    }

    @ConfigItem(
            keyName = "lootUntradables",
            name = "Loot Untradables",
            description = "Pick up untradable items (clue scrolls, keys, etc.)",
            position = 9,
            section = lootSection
    )
    default boolean lootUntradables() {
        return true;
    }

    @ConfigItem(
            keyName = "lootBones",
            name = "Loot Bones",
            description = "Pick up bones from killed monsters",
            position = 10,
            section = lootSection
    )
    default boolean lootBones() {
        return false;
    }

    @ConfigItem(
            keyName = "buryBones",
            name = "Bury Bones",
            description = "Automatically bury bones after picking them up",
            position = 11,
            section = lootSection
    )
    default boolean buryBones() {
        return false;
    }

    @ConfigItem(
            keyName = "scatterAshes",
            name = "Scatter Ashes",
            description = "Pick up and scatter demonic/infernal ashes",
            position = 12,
            section = lootSection
    )
    default boolean scatterAshes() {
        return false;
    }

    @ConfigItem(
            keyName = "forceLoot",
            name = "Force Loot",
            description = "Loot items even while in combat",
            position = 13,
            section = lootSection
    )
    default boolean forceLoot() {
        return false;
    }

    @ConfigItem(
            keyName = "onlyLootMyItems",
            name = "Only Loot My Items",
            description = "Only loot items dropped by/for you",
            position = 14,
            section = lootSection
    )
    default boolean onlyLootMyItems() {
        return false;
    }

    @ConfigItem(
            keyName = "delayedLooting",
            name = "Delayed Looting",
            description = "Wait before looting (lets items pile up)",
            position = 15,
            section = lootSection
    )
    default boolean delayedLooting() {
        return false;
    }

    @ConfigItem(
            keyName = "eatForLootSpace",
            name = "Eat For Loot Space",
            description = "Eat food to make room for valuable loot",
            position = 15,
            section = lootSection
    )
    default boolean eatForLootSpace() {
        return false;
    }

    @ConfigItem(
            keyName = "enableHighAlch",
            name = "Enable High Alch",
            description = "High alch items from your inventory while fighting",
            position = 20,
            section = lootSection
    )
    default boolean enableHighAlch() {
        return false;
    }

    @ConfigItem(
            keyName = "highAlchItemList",
            name = "High Alch Items",
            description = "Comma-separated list of items to high alch. Uses exact name matching. Use * for wildcards (e.g., 'Rune platelegs, Rune full helm, *dragon*')",
            position = 21,
            section = lootSection
    )
    default String highAlchItemList() {
        return "";
    }

    @ConfigItem(
            keyName = "highAlchExcludeList",
            name = "High Alch Exclude",
            description = "Comma-separated list of items to NEVER high alch. Uses exact name matching. Use * for wildcards (e.g., 'Dragon scimitar, *shield*')",
            position = 22,
            section = lootSection
    )
    default String highAlchExcludeList() {
        return "";
    }
}
