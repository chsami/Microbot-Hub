package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(BlackjackConfig.GROUP)
public interface BlackjackConfig extends Config
{
    String GROUP = "blackjack";

    @ConfigSection(
            name = "Setup",
            description = "Blackjack setup requirements",
            position = 0,
            closedByDefault = false
    )
    String setupSection = "setup";

    @ConfigSection(
            name = "Supplies",
            description = "Healing and restocking",
            position = 1,
            closedByDefault = false
    )
    String suppliesSection = "supplies";

    @ConfigSection(
            name = "Humanizer randomizer",
            description = "Bounded timing, mouse, mistake, and break variation",
            position = 2,
            closedByDefault = false
    )
    String humanizerSection = "humanizer";

    @ConfigItem(
            keyName = "guide",
            name = "Requirements",
            description = "Required equipment, supplies and plugin pairing",
            position = 0,
            section = setupSection
    )
    default String guide()
    {
        return "Requires a client providing the cooperative BreakPreparation API. Enable BOTH [JOJ] Blackjack and [JOJ] Blackjack Entry Swap. Blackjack handles movement, clicks, healing and wine runs; Entry Swap chooses Knock-Out or Pickpocket as the left-click action from the NPC animation. " +
                "Start in Pollnivneach with a blackjack equipped and a full inventory: coins, one noted-wine stack, unnoted wines in the remaining slots, and optionally one escape teleport replacing a wine. Allow five coins per wine to unnote (130 coins covers 26 wines). Keep NPC attack options hidden. " +
                "Menaphite Thugs require 65 Thieving; level-41/56 Bandits require 45/55. Wine is the supported healing supply. The optional combat reset temporarily drops one full wine to unequip the blackjack, then re-equips and recovers it.";
    }

    @ConfigItem(keyName = "caveats", name = "Scope and caveats",
            description = "Primary workflow and manual Bandit preparation", position = 3, section = setupSection)
    default String caveats()
    {
        return "Primarily designed for Menaphite Thugs: automatic southern-tent luring, house setup, extra-thug removal, combat recovery and wine unnoting/return. " +
                "Bandits are supported but not feature-complete: manually lure and isolate the selected Bandit in the highlighted northern house near 3358,2993 before starting. Bandit modes retain healing, combat recovery and wine unnoting/return, but do not automatically lure or remove extras. " +
                "AUTO selects from your starting level and keeps that choice until re-enabled. Contributions and fixes for any issues are welcome.";
    }

    @ConfigItem(keyName = "showBanditHouse", name = "Highlight Bandit house",
            description = "Mark the supported northern house while a Bandit target is selected", position = 4, section = setupSection)
    default boolean showBanditHouse()
    {
        return true;
    }

    @ConfigItem(
            keyName = "target",
            name = "Pickpocket target",
            description = "Target to blackjack; Menaphite Thugs are automatically prepared in the southern tent",
            position = 1,
            section = setupSection
    )
    default BlackjackTarget target()
    {
        return BlackjackTarget.AUTO;
    }

    @Range(min = 0, max = 1004)
    @ConfigItem(
            keyName = "returnCameraZoom",
            name = "Return camera zoom",
            description = "Preferred zoom maintained only while blackjacking; 0 captures the zoom on enable. Temporary visibility recovery may zoom out. Higher values zoom in further.",
            position = 2,
            section = setupSection
    )
    default int returnCameraZoom()
    {
        return 0;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "healBelowPercent",
            name = "Heal below % HP",
            description = "Start drinking wine below this Hitpoints level",
            position = 0,
            section = suppliesSection
    )
    default int healBelowPercent()
    {
        return 40;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "healToPercent",
            name = "Heal to HP",
            description = "Keep drinking wine until this Hitpoints level is reached",
            position = 1,
            section = suppliesSection
    )
    default int healToPercent()
    {
        return 75;
    }

    @ConfigItem(
            keyName = "autoRestockWine",
            name = "Auto-restock wine",
            description = "Leave before the remaining wines can no longer reach Heal to HP, then exchange noted wines or use Faisal",
            position = 2,
            section = suppliesSection
    )
    default boolean autoRestockWine()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dropWineForDisarmedReset",
            name = "Drop wine for combat reset",
            description = "Temporarily drop one full wine when the inventory is full so the blackjack can be unequipped, then recover it after re-equipping",
            position = 3,
            section = suppliesSection
    )
    default boolean dropWineForDisarmedReset()
    {
        return true;
    }

    @ConfigItem(
            keyName = "humanizerEnabled",
            name = "Enable humanizer",
            description = "Enable bounded timing variation, mouse recovery, and scheduled breaks",
            position = 0,
            section = humanizerSection
    )
    default boolean humanizerEnabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "randomMouseRecovery",
            name = "Mouse wander and recover",
            description = "Occasionally move away from the target and naturally recover before continuing",
            position = 1,
            section = humanizerSection
    )
    default boolean randomMouseRecovery()
    {
        return true;
    }

    @ConfigItem(
            keyName = "humanizerBreaks",
            name = "Random breaks",
            description = "Take scheduled breaks using the interval and duration ranges below, at safe blackjacking boundaries",
            position = 2,
            section = humanizerSection
    )
    default boolean humanizerBreaks()
    {
        return true;
    }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "microBreakIntervalMin", name = "Micro interval min (minutes)",
            description = "Minimum time before a micro break; applied when the next break is scheduled",
            position = 3, section = humanizerSection)
    default int microBreakIntervalMin() { return 9; }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "microBreakIntervalMax", name = "Micro interval max (minutes)",
            description = "Maximum time before a micro break; reversed bounds are automatically ordered",
            position = 4, section = humanizerSection)
    default int microBreakIntervalMax() { return 11; }

    @Range(min = 1, max = 600)
    @ConfigItem(keyName = "microBreakDurationMin", name = "Micro duration min (seconds)",
            description = "Minimum micro-break duration; applied when the break starts",
            position = 5, section = humanizerSection)
    default int microBreakDurationMin() { return 25; }

    @Range(min = 1, max = 600)
    @ConfigItem(keyName = "microBreakDurationMax", name = "Micro duration max (seconds)",
            description = "Maximum micro-break duration; reversed bounds are automatically ordered",
            position = 6, section = humanizerSection)
    default int microBreakDurationMax() { return 35; }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "smallBreakIntervalMin", name = "Small interval min (minutes)",
            description = "Minimum time before a small break; applied when the next break is scheduled",
            position = 7, section = humanizerSection)
    default int smallBreakIntervalMin() { return 20; }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "smallBreakIntervalMax", name = "Small interval max (minutes)",
            description = "Maximum time before a small break; reversed bounds are automatically ordered",
            position = 8, section = humanizerSection)
    default int smallBreakIntervalMax() { return 30; }

    @Range(min = 1, max = 600)
    @ConfigItem(keyName = "smallBreakDurationMin", name = "Small duration min (seconds)",
            description = "Minimum small-break duration; applied when the break starts",
            position = 9, section = humanizerSection)
    default int smallBreakDurationMin() { return 60; }

    @Range(min = 1, max = 600)
    @ConfigItem(keyName = "smallBreakDurationMax", name = "Small duration max (seconds)",
            description = "Maximum small-break duration; reversed bounds are automatically ordered",
            position = 10, section = humanizerSection)
    default int smallBreakDurationMax() { return 120; }
}
