package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DroSlayerConfig.GROUP)
@ConfigInformation(
        "<b>WARNING Krystilia is Wilderness Slayer, you may die! Choose an InventorySetup accordingly.</b><br>" +
        "DRO's Slayer walks with its own built-in walker.<br><br>" +
        "Set up a <b>slayerdefault</b> Inventory Setup.<br><br>" +
        "<b>Optional task-specific setups:</b><br>" +
        "• melee &nbsp; • ven &nbsp; • ranged &nbsp; • basilisk<br>" +
        "• dragon &nbsp; • blue dragon &nbsp; • magic &nbsp; • stab<br>" +
        "• burst &nbsp; • burstwildy &nbsp; • wyvern &nbsp; • demon &nbsp; • kalphite<br>" +
        "• kurask &nbsp; • zygo &nbsp; • wyrm")
public interface DroSlayerConfig extends Config {

    String GROUP = "droslayer2";

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
            keyName = "editTaskPrayers",
            name = "Edit monster config",
            description = "Edit each monster's protection prayer, offensive prayer, and optional bracelets. Changes apply immediately.",
            position = -1,
            section = generalSection
    )
    default ConfigButton editTaskPrayers() { return new ConfigButton(); }

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
    default DroSlayerMaster slayerMaster() {
        return DroSlayerMaster.DURADEL;
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

    @ConfigItem(
            keyName = "autoRecoverDisconnects",
            name = "Recover disconnects",
            description = "After Dro Slayer has been logged in once, automatically retry login after an unexpected disconnect. BaseProfileDro break logins remain separate.",
            position = 4,
            section = generalSection
    )
    default boolean autoRecoverDisconnects() {
        return true;
    }

    @ConfigSection(name = "Wilderness Slayer", description = "Krystilia supplies and recovery", position = 5, closedByDefault = true)
    String wildernessSection = "Wilderness Slayer";

    @ConfigItem(keyName = "wildernessInventorySetup", name = "Wilderness setup",
            description = "Base melee loadout for Krystilia, including Bloodvelds. Save rangewildy for Aviansies and burstwildy for optional Dust devils/Jellies bursting. Regular task/default setups are never used as fallbacks.", position = 0, section = wildernessSection)
    default InventorySetup wildernessInventorySetup() { return null; }

    @ConfigItem(keyName = "wildernessPreferCannon", name = "Cannon Wilderness tasks",
            description = "Use a cannon wherever the chosen Krystilia location allows one. Separate from the ordinary Slayer cannon toggle.", position = 1, section = wildernessSection)
    default boolean wildernessPreferCannon() { return true; }

    @Range(min = 5, max = 10)
    @ConfigItem(keyName = "wildernessRecoveryFood", name = "Recovery food",
            description = "Food carried on a Wilderness death recovery trip, with one restore and Protect Item.", position = 2, section = wildernessSection)
    default int wildernessRecoveryFood() { return 8; }

    @Range(min = 0, max = 2000)
    @ConfigItem(keyName = "wildernessIceSacks", name = "Ice sacks",
            description = "Blighted ancient ice sacks withdrawn for burst/barrage profiles when available; runes remain a fallback. Zero disables the extra withdrawal.", position = 3, section = wildernessSection)
    default int wildernessIceSacks() { return 300; }

    @Range(min = 200, max = 5000)
    @ConfigItem(keyName = "wildernessWeaponCharges", name = "Revenant weapon charges",
            description = "Usable ether charges prepared at the bank for a selected chainmace or bow. Activation ether is additional.", position = 4, section = wildernessSection)
    default int wildernessWeaponCharges() { return 2000; }

    @ConfigItem(keyName = "wildernessBurstDustDevils", name = "Burst Dust devils",
            description = "For Krystilia Dust devils, use only the saved burstwildy loadout instead of the general Wilderness setup, and require Ancient spellbook. Off uses the general Wilderness setup without spellbook swapping. Save rangewildy for Aviansies; burstwildy is only for selected Dust devils/Jellies bursting.",
            position = 5, section = wildernessSection)
    default boolean wildernessBurstDustDevils() { return false; }

    @ConfigItem(keyName = "wildernessBurstJellies", name = "Burst Jellies",
            description = "For Krystilia Jellies, use the saved burstwildy loadout and Ancient spellbook. Off uses the general Wilderness setup without spellbook swapping. Aviansies always use rangewildy; Bloodvelds use the general melee setup and never burst.",
            position = 6, section = wildernessSection)
    default boolean wildernessBurstJellies() { return false; }

    @ConfigItem(keyName = "wildernessElderChaosDruids", name = "Elder Chaos Druids",
            description = "Choose Elder Chaos druids at Chaos Temple for Krystilia Chaos druid tasks. Off uses ordinary Chaos druids under Edgeville. Both locations support the Wilderness cannon setting.",
            position = 7, section = wildernessSection)
    default boolean wildernessElderChaosDruids() { return false; }

    // =====================
    // Dro Profile Section
    // =====================
    @ConfigSection(
            name = "Dro Profile",
            description = "BaseProfileDro humanisation, mouse parking, and smart breaks",
            position = 100,
            closedByDefault = false
    )
    String droProfileSection = "Dro Profile";

    @ConfigItem(
            keyName = "mouseActivity",
            name = "Mouse activity",
            description = "How often BaseProfileDro parks the mouse fully off-screen during natural Slayer idle/combat windows",
            position = 0,
            section = droProfileSection
    )
    default BaseProfileDro.MouseActivity mouseActivity() {
        return BaseProfileDro.MouseActivity.BALANCED;
    }

    @ConfigItem(
            keyName = "parkSide",
            name = "Off-screen side",
            description = "Preferred side used when BaseProfileDro parks the mouse completely off-screen",
            position = 1,
            section = droProfileSection
    )
    default BaseProfileDro.AfkParkSide parkSide() {
        return BaseProfileDro.AfkParkSide.RANDOM;
    }

    @ConfigItem(
            keyName = "smartBreaks",
            name = "Smart breaks",
            description = "Enable BaseProfileDro AFK/logout breaks with automatic login return",
            position = 10,
            section = droProfileSection
    )
    default boolean smartBreaks() { return false; }

    @Range(min = 1, max = 720)
    @ConfigItem(keyName = "breakIntervalMin", name = "Break interval min", description = "Minimum minutes between smart breaks", position = 11, section = droProfileSection)
    default int breakIntervalMin() { return 25; }

    @Range(min = 1, max = 720)
    @ConfigItem(keyName = "breakIntervalMax", name = "Break interval max", description = "Maximum minutes between smart breaks", position = 12, section = droProfileSection)
    default int breakIntervalMax() { return 150; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "logoutBreakChance", name = "Logout break chance", description = "Percent of smart breaks that log out", position = 13, section = droProfileSection)
    default int logoutBreakChance() { return 75; }

    @Range(min = 1, max = 120)
    @ConfigItem(keyName = "afkBreakMin", name = "AFK break min", description = "Minimum AFK break duration in minutes", position = 14, section = droProfileSection)
    default int afkBreakMin() { return 2; }

    @Range(min = 1, max = 120)
    @ConfigItem(keyName = "afkBreakMax", name = "AFK break max", description = "Maximum AFK break duration in minutes", position = 15, section = droProfileSection)
    default int afkBreakMax() { return 7; }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "logoutBreakMin", name = "Logout break min", description = "Minimum logout break duration in minutes", position = 16, section = droProfileSection)
    default int logoutBreakMin() { return 5; }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "logoutBreakMax", name = "Logout break max", description = "Maximum logout break duration in minutes", position = 17, section = droProfileSection)
    default int logoutBreakMax() { return 16; }

    @Range(min = 0, max = 30)
    @ConfigItem(keyName = "postLoginSettleSeconds", name = "Post-login settle", description = "Seconds to settle after BaseProfileDro logs back in", position = 18, section = droProfileSection)
    default int postLoginSettleSeconds() { return 3; }

    // =====================
    // Travel Engine Section
    // =====================
    @ConfigSection(
            name = "Travel Engine",
            description = "Built-in walker routing with scripted dungeon-entry handoffs",
            position = 90,
            closedByDefault = false
    )
    String travelEngineSection = "Travel Engine";

    @Range(min = 15, max = 180)
    @ConfigItem(
            keyName = "routeStallSeconds",
            name = "Route stall timeout",
            description = "Seconds without positional progress before Dro Slayer cancels and replans a route",
            position = 1,
            section = travelEngineSection
    )
    default int routeStallSeconds() { return 45; }

    @Range(min = 2, max = 12)
    @ConfigItem(
            keyName = "maxRecoveryAttempts",
            name = "Recovery attempts",
            description = "Repeated route/setup recovery attempts before safely logging out instead of hanging indefinitely",
            position = 2,
            section = travelEngineSection
    )
    default int maxRecoveryAttempts() { return 6; }

    // =====================
    // Inventory Setup Section
    // =====================
    @ConfigSection(
            name = "Inventory Setup",
            description = "Choose or create the Inventory Setup Dro Slayer 2 should use when a task does not have a matching named setup",
            position = 4,
            closedByDefault = false
    )
    String inventorySetupSection = "Inventory Setup";

    @ConfigItem(
            keyName = "defaultInventorySetup",
            name = "Default setup",
            description = "Fallback Inventory Setup. Task-specific setups still win when they exist. This is also used for tasks such as Custodian Stalkers that do not define a special setup.",
            position = 0,
            section = inventorySetupSection
    )
    default InventorySetup defaultInventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "captureInventorySetupName",
            name = "New setup name",
            description = "Name used by Save current loadout. Equip the gear and inventory you want first.",
            position = 1,
            section = inventorySetupSection
    )
    default String captureInventorySetupName() {
        return "Dro Slayer 2";
    }

    @ConfigItem(
            keyName = "captureCurrentInventorySetup",
            name = "Save current loadout",
            description = "Creates or updates an Inventory Setup from your CURRENT equipment, inventory, rune pouch, quiver, and spellbook. Re-open this config afterward if the new setup is not immediately visible in the Default setup dropdown.",
            position = 2,
            section = inventorySetupSection
    )
    default ConfigButton captureCurrentInventorySetup() {
        return new ConfigButton();
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
        return "Spiritual creatures";
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
            keyName = "selectedProtectionPrayer",
            name = "Protection Prayer",
            description = "Select the protection prayer used on tasks. Task profile uses the per-task profile; None disables protection prayer.",
            position = 5,
            section = combatSection
    )
    default DroSlayerPrayer selectedProtectionPrayer() {
        return DroSlayerPrayer.TASK_PROFILE;
    }

    @ConfigItem(
            keyName = "drinkPrayerAt",
            name = "Drink Prayer At",
            description = "Drink prayer potion when prayer points drop below this amount",
            position = 6,
            section = combatSection
    )
    default int drinkPrayerAt() {
        return 20;
    }

    @ConfigItem(
            keyName = "useCombatPotions",
            name = "Use Combat Potions",
            description = "Drink combat potions (attack, strength, defence, ranging, magic)",
            position = 7,
            section = combatSection
    )
    default boolean useCombatPotions() {
        return false;
    }

    @ConfigItem(
            keyName = "useOffensivePrayers",
            name = "Use Offensive Prayers",
            description = "Activate offensive prayers (Piety/Rigour/Augury) based on combat style",
            position = 8,
            section = combatSection
    )
    default boolean useOffensivePrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "dodgeProjectiles",
            name = "Dodge AOE Attacks",
            description = "Automatically dodge AOE projectile attacks (e.g., dragon poison pools)",
            position = 9,
            section = combatSection
    )
    default boolean dodgeProjectiles() {
        return false;
    }

    @ConfigItem(
            keyName = "hopWhenCrashed",
            name = "Hop When Crashed",
            description = "Hop when another player occupies the task area, or no attackable targets are found for 20 seconds",
            position = 10,
            section = combatSection
    )
    default boolean hopWhenCrashed() {
        return true;
    }

    @ConfigItem(
            keyName = "hopWorldList",
            name = "Hop World List",
            description = "Comma-separated list of worlds to hop to (e.g., 390,391,392). Leave empty for random members world.",
            position = 11,
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
            description = "Top up below this count; also triggers banking when Bank for Cannonballs is enabled. Wilderness restock threshold is capped at 100 for the 350-ball loadout.",
            position = 1,
            section = cannonSection
    )
    default int cannonballThreshold() {
        return 100;
    }

    @ConfigItem(
            keyName = "bankForCannonballs",
            name = "Bank for Cannonballs",
            description = "With Auto Banking enabled, replenish below Cannonball Threshold for regular and Wilderness Slayer. Missing stock continues ordinary combat.",
            position = 2,
            section = cannonSection
    )
    default boolean bankForCannonballs() {
        return false;
    }

    @ConfigItem(keyName = "autoBuyCannonballs", name = "Auto Buy Cannonballs",
            description = "With Bank for Cannonballs enabled, walk to the GE to buy 2000–3000 steel cannonballs when owned stock cannot fill the next trip, or 1000 if funds are limited. Iron accounts skip buying.",
            position = 3, section = cannonSection)
    default boolean autoBuyCannonballs() { return true; }

    @ConfigItem(
            keyName = "cannonTaskList",
            name = "Cannon Tasks (Optional)",
            description = "Only use cannon for these tasks (comma-separated). Leave empty to cannon all supported tasks.",
            position = 4,
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
            description = "Minimum total GE value to loot. Defaults to 5,000 gp; untradeables and your Item List can still be looted separately.",
            position = 3,
            section = lootSection
    )
    default int minLootValue() {
        return 5000;
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
            description = "Only loot coin stacks at least this amount (0 = loot all). Defaults to the same 5,000 gp intuition as item looting.",
            position = 6,
            section = lootSection
    )
    default int minCoinStack() {
        return 5000;
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
