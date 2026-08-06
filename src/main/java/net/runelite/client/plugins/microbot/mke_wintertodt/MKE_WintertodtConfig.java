package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.mke_wintertodt.enums.Brazier;
import net.runelite.client.plugins.microbot.mke_wintertodt.enums.HealingMethod;

/**
 * Configuration interface for the Wintertodt bot plugin.
 * Provides comprehensive settings for customizing bot behavior including
 * food management, brazier preferences, and various gameplay options.
 */
@ConfigGroup("wintertodt")
@ConfigInformation(
    "<html>" +
    "🤖 <b>MKE WINTERTODT BOT - SETUP GUIDE</b><br /><br />" +
    
    "🔴 <b>REQUIREMENTS:</b><br />" +
    "✅ Membership + Firemaking 50+<br />" +
    "✅ 4+ warm clothing pieces (Pyromancer, Santa, Hunter gear, etc.)<br />" +
    "✅ Tools in bank: Axe, Knife, Hammer, Tinderbox<br /><br />" +
    
    "🥄 <b>HEALING METHODS:</b><br />" +
    "• <b>Potions (Recommended):</b> FREE, crafted automatically inside Wintertodt<br />" +
    "• <b>Food:</b> Withdrawn from bank automatically<br /><br />" +
    
    "🔥 <b>FEATURES:</b><br />" +
    "• Auto navigation from anywhere<br />" +
    "• <b>Auto gear optimization - NO manual gearing required!</b><br />" +
    "• Brazier management & repairs<br />" +
    "• Fletching & reward collection<br />" +
    "• Custom break system with AFK/logout<br /><br />" +
    
    "🎯 <b>QUICK START:</b> Choose healing method below, configure options, start anywhere! <b>Bot handles all gearing automatically!</b>"
)
public interface MKE_WintertodtConfig extends Config {

    // Configuration sections
    @ConfigSection(
            name = "⚙️ General Settings",
            description = "Core bot functionality and activity preferences",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Healing Method",
            description = "Choose your healing method: Potions (free, recommended) or Food (costs GP)",
            position = 1
    )
    String healingSection = "healing";

    @ConfigSection(
            name = "🍽️ Healing Settings",
            description = "Settings that apply to both healing methods (potions and food)",
            position = 2
    )
    String healingSettingsSection = "healingSettings";

    @ConfigSection(
            name = "🔥 Brazier Management",
            description = "Brazier location and maintenance preferences",
            position = 3
    )
    String brazierSection = "brazier";

    @ConfigSection(
            name = "🏆 Reward Collection",
            description = "Automatic reward cart looting when you have enough points",
            position = 4
    )
    String rewardSection = "reward";

    @ConfigSection(
            name = "🛌 Break System",
            description = "Smart AFK and logout breaks for enhanced anti-detection",
            position = 5
    )
    String breakSection = "breaks";

    @ConfigSection(
            name = "🎛️ Advanced Options",
            description = "Timing, antiban, and behavior customization",
            position = 6
    )
    String advancedSection = "advanced";

    // ==================== GENERAL SETTINGS ====================
    @ConfigItem(
            keyName = "RelightBrazier",
            name = "🔥 Relight Braziers",
            description = "Automatically relight braziers when they go out",
            position = 1,
            section = generalSection
    )
    default boolean relightBrazier() {
        return true;
    }

    @ConfigItem(
            keyName = "FletchRoots",
            name = "➳ Fletch Roots to Kindling",
            description = "Convert bruma roots to kindling for fletching XP and more points",
            position = 2,
            section = generalSection
    )
    default boolean fletchRoots() {
        return true;
    }

    @ConfigItem(
            keyName = "FixBrazier",
            name = "⚒️ Fix Broken Braziers",
            description = "Repair broken braziers with hammer",
            position = 3,
            section = generalSection
    )
    default boolean fixBrazier() {
        return true;
    }

    @ConfigItem(
            keyName = "DodgeSnowfall",
            name = "❄️ Dodge Snowfall",
            description = "Step out of incoming snowfall AoE damage. Most players don't bother — eating food/potions is usually more efficient than interrupting your action to step.",
            position = 4,
            section = generalSection
    )
    default boolean dodgeSnowfall() {
        return false;
    }

    // ==================== HEALING METHOD ====================
    @ConfigItem(
            keyName = "HealingMethod",
            name = "Healing Method Selection",
            description = "<html><b>Choose your preferred healing method:</b><br /><br />" +
                    "🥄 <b>POTIONS (Recommended):</b><br />" +
                    "• FREE - crafted from crate materials<br />" +
                    "• More efficient, no banking needed<br />" +
                    "• Works for ALL players<br /><br />" +
                    "🍖 <b>FOOD:</b><br />" +
                    "• Withdrawn from bank<br />" +
                    "• Less efficient, requires banking<br />" +
                    "• Works immediately</html>",
            position = 1,
            section = healingSection
    )
    default HealingMethod healingMethod() {
        return HealingMethod.POTIONS;
    }

    @ConfigItem(
            keyName = "Food",
            name = "🍖 Food Type",
            description = "Type of food to withdraw from bank (only used when Food healing method is selected)",
            position = 2,
            section = healingSection
    )
    default Rs2Food food() {
        return Rs2Food.SALMON;
    }

    // ==================== HEALING SETTINGS ====================
    @ConfigItem(
            keyName = "Amount",
            name = "💊 Healing Items Per Trip",
            description = "Number of healing items (potions/food) to carry per trip",
            position = 0,
            section = healingSettingsSection
    )
    default int healingAmount() {
        return 2;
    }

    @ConfigItem(
            keyName = "MinFood",
            name = "📦 Restock Healing Items Threshold",
            description = "When game round ends, restock healing items if below this amount",
            position = 1,
            section = healingSettingsSection
    )
    default int minHealingItems() {
        return 2;
    }

    @ConfigItem(
            keyName = "Eat at warmth level",
            name = "🌡️ Consume at Warmth Level",
            description = "Use healing items when warmth drops to this or lower",
            position = 2,
            section = healingSettingsSection
    )
    default int eatAtWarmthLevel() {
        return 45;
    }



    // ==================== BRAZIER MANAGEMENT ====================
    @ConfigItem(
            keyName = "Brazier",
            name = "🎯 Preferred Brazier Location",
            description = "Which brazier to primarily use",
            position = 1,
            section = brazierSection
    )
    default Brazier brazierLocation() {
        return Brazier.SOUTH_EAST;
    }

    // ==================== REWARD COLLECTION ====================
    @ConfigItem(
            keyName = "EnableRewardCartLooting",
            name = "🏆 Enable Reward Cart Collection",
            description = "Automatically collect rewards when you have enough points",
            position = 1,
            section = rewardSection
    )
    default boolean enableRewardCartLooting() {
        return false;
    }

    @ConfigItem(
            keyName = "MinimumRewardsForCollection",
            name = "📊 Minimum Rewards Threshold",
            description = "Collect rewards when cart has this many rewards (±variance)",
            position = 2,
            section = rewardSection
    )
    default int minimumRewardsForCollection() {
        return 20;
    }

    @ConfigItem(
            keyName = "RewardsVariance",
            name = "🎲 Collection Variance",
            description = "Random variance (±) added to minimum threshold for natural timing",
            position = 3,
            section = rewardSection
    )
    default int rewardsVariance() {
        return 10;
    }

    // ==================== BREAK SYSTEM ====================
    @ConfigItem(
            keyName = "EnableCustomBreaks",
            name = "🛌 Enable Smart Break System",
            description = "Intelligent AFK and logout breaks for better anti-detection",
            position = 1,
            section = breakSection
    )
    default boolean enableCustomBreaks() {
        return true;
    }

    @ConfigItem(
            keyName = "MinBreakInterval",
            name = "⏰ Min Break Interval (minutes)",
            description = "Minimum time between breaks",
            position = 2,
            section = breakSection
    )
    default int minBreakInterval() {
        return 20;
    }

    @ConfigItem(
            keyName = "MaxBreakInterval",
            name = "⏰ Max Break Interval (minutes)",
            description = "Maximum time between breaks",
            position = 3,
            section = breakSection
    )
    default int maxBreakInterval() {
        return 140;
    }

    @ConfigItem(
            keyName = "LogoutBreakChance",
            name = "🚪 Logout Break Chance (%)",
            description = "Percentage chance for logout vs AFK breaks (0-100%)",
            position = 4,
            section = breakSection
    )
    default int logoutBreakChance() {
        return 40;
    }

    @ConfigItem(
            keyName = "AfkBreakMinDuration",
            name = "😴 AFK Break Min Duration (minutes)",
            description = "Minimum AFK break duration (mouse goes offscreen)",
            position = 5,
            section = breakSection
    )
    default int afkBreakMinDuration() {
        return 2;
    }

    @ConfigItem(
            keyName = "AfkBreakMaxDuration",
            name = "😴 AFK Break Max Duration (minutes)",
            description = "Maximum AFK break duration",
            position = 6,
            section = breakSection
    )
    default int afkBreakMaxDuration() {
        return 6;
    }

    @ConfigItem(
            keyName = "LogoutBreakMinDuration",
            name = "🚪 Logout Break Min Duration (minutes)",
            description = "Minimum logout break duration",
            position = 7,
            section = breakSection
    )
    default int logoutBreakMinDuration() {
        return 5;
    }

    @ConfigItem(
            keyName = "LogoutBreakMaxDuration",
            name = "🚪 Logout Break Max Duration (minutes)",
            description = "Maximum logout break duration",
            position = 8,
            section = breakSection
    )
    default int logoutBreakMaxDuration() {
        return 40;
    }

    // ==================== ADVANCED OPTIONS ====================
    @ConfigItem(
            keyName = "HumanizedTiming",
            name = "🎭 Humanized Timing",
            description = "Add random delays for more human-like behavior",
            position = 1,
            section = advancedSection
    )
    default boolean humanizedTiming() {
        return true;
    }

    @ConfigItem(
            keyName = "MouseMovements",
            name = "🖱️ Random Mouse Movements",
            description = "Occasional random mouse movements",
            position = 2,
            section = advancedSection
    )
    default boolean randomMouseMovements() {
        return true;
    }

    @ConfigItem(
            keyName = "CameraMovementFrequency",
            name = "📹 Camera Movement Frequency",
            description = "Higher value = less frequent camera movements, lower value = more frequent. Set to 0 to disable.",
            position = 3,
            section = advancedSection
    )
    default int cameraMovementFrequency() {
        return 10;
    }

    @ConfigItem(
            keyName = "ShowAntibanOverlay",
            name = "📊 Show Antiban Overlay",
            description = "Display antiban information in overlay",
            position = 4,
            section = advancedSection
    )
    default boolean showAntibanOverlay() {
        return true;
    }
}