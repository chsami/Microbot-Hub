package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.accountstarter.logic.GatherTarget;
import net.runelite.client.plugins.microbot.accountstarter.logic.LampChoice;
import net.runelite.client.plugins.microbot.accountstarter.logic.QuestLampChoice;
import net.runelite.client.plugins.microbot.accountstarter.logic.PacingMode;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.Target;

// keyNames are saved settings: reorder and relabel freely, never rename one
@ConfigGroup(AccountStarterConfig.GROUP)
@ConfigInformation(
        "<b>Version " + AccountStarterPlugin.version + "</b><br><br>"
        + "Sets up a fresh free-to-play account: completes the Combat and Gatherer Adventure Paths, "
        + "claims every reward and does Mudfoot's Big Adventure. Skills you leave out are never trained.<br><br>"
        + "You need: an Adventure Paths account (told the Gielinor Guide you are new or returning), not an ironman.<br>"
        + "Recommended: start in Lumbridge.<br>"
        + "Where to start: choose Paths under Setup, then turn the plugin on.<br>"
        + "On a main: turn on Quest only to do just Mudfoot's Big Adventure."
)
public interface AccountStarterConfig extends Config {
    String GROUP = "irkedadventurepaths";

    @ConfigSection(
            name = "Setup",
            description = "What to complete and how. Any action that would give experience to a skill you left out is refused.",
            position = 0
    )
    String SETUP_SECTION = "setup";

    @ConfigItem(keyName = "preset", name = "Paths",
            description = "Everything, gathering only, combat only, or Choose skills to set each skill in the Choose skills section.",
            section = SETUP_SECTION, position = 0)
    default Preset preset() { return Preset.EVERYTHING; }

    @ConfigItem(keyName = "pacing", name = "Task order",
            description = "Smart order picks the cheapest next step; the others finish one skill, one path, or rotate skills in timed blocks.",
            section = SETUP_SECTION, position = 1)
    default PacingMode pacing() { return PacingMode.SMART; }

    @ConfigItem(keyName = "buryBones", name = "Bury bones",
            description = "Bury bones from fights for Prayer; off keeps Prayer untouched.",
            section = SETUP_SECTION, position = 2)
    default boolean buryBones() { return true; }

    @ConfigItem(keyName = "cookFood", name = "Cook food",
            description = "Catch and cook food when combat needs it: on the Lumbridge kitchen range after Cook's Assistant, otherwise on a fire you light. Off keeps Cooking and Firemaking untouched.",
            section = SETUP_SECTION, position = 3)
    default boolean cookFood() { return true; }

    @ConfigItem(keyName = "lampSkill", name = "Lamp of the Gatherer",
            description = "Skill the Gatherer path's lamp (150 xp) goes to; a skill you left out falls back to the lowest chosen one.",
            section = SETUP_SECTION, position = 4)
    default LampChoice lampSkill() { return LampChoice.AUTO; }

    @ConfigItem(keyName = "allowOptIn", name = "Rejoin Adventure Paths",
            description = "If this account has opted out, ask Adventurer Jon to rejoin; off stops the plugin instead.",
            section = SETUP_SECTION, position = 5)
    default boolean allowOptIn() { return false; }

    @ConfigItem(keyName = "buyAtVarrockShops", name = "Buy at Varrock shops",
            description = "When a Ranged or Magic task is short and the bank and Captain Harlan cannot cover it, walk to Varrock and buy arrows at Lowe's or runes at Aubury's with your coins.",
            section = SETUP_SECTION, position = 6)
    default boolean buyAtVarrockShops() { return true; }

    @ConfigItem(keyName = "pickUpArrows", name = "Pick up arrows",
            description = "After a Ranged kill, pick up your own arrows near it so they last longer; off leaves them on the ground.",
            section = SETUP_SECTION, position = 7)
    default boolean pickUpArrows() { return true; }

    @ConfigItem(keyName = "doMudfoot", name = "Do Mudfoot's Big Adventure",
            description = "Once melee is done (Attack, Strength, Defence 10+), do this F2P quest before Ranged and Magic: it gives a bow, arrows, a staff, runes, 500 coins and four combat lamps.",
            section = SETUP_SECTION, position = 8)
    default boolean doMudfoot() { return true; }

    @ConfigItem(keyName = "questOnly", name = "Quest only (main account)",
            description = "Skip Adventure Paths: check the bank, take a staff and runes, do Mudfoot's Big Adventure and stop. Works on members worlds. Quest lamps are used only when you pick a skill below.",
            section = SETUP_SECTION, position = 9)
    default boolean questOnly() { return false; }

    @ConfigItem(keyName = "questLampSkill", name = "Quest lamps",
            description = "Skill the four Mudfoot's Big Adventure lamps (500 xp each) go to; a skill you left out falls back to the lowest chosen Ranged or Magic.",
            section = SETUP_SECTION, position = 10)
    default QuestLampChoice questLampSkill() { return QuestLampChoice.AUTO; }

    @ConfigItem(keyName = "logOutWhenDone", name = "Log out when finished",
            description = "When there is nothing left to do, log out instead of standing idle.",
            section = SETUP_SECTION, position = 11)
    default boolean logOutWhenDone() { return false; }

    @ConfigItem(keyName = "recoverWhenStuck", name = "Recover when stuck",
            description = "Choose again when nothing changes for a few minutes, and park a task stuck three times; off only logs it.",
            section = SETUP_SECTION, position = 12)
    default boolean recoverWhenStuck() { return true; }

    @ConfigSection(
            name = "Choose skills",
            description = "Only used when Paths is set to Choose skills. Off leaves a skill untouched.",
            position = 1,
            closedByDefault = true
    )
    String SKILLS_SECTION = "chooseSkills";

    @ConfigItem(keyName = "attackTarget", name = "Attack target",
            description = "Highest Attack task to complete; only used with Choose skills.",
            section = SKILLS_SECTION, position = 0)
    default Target attackTarget() { return Target.LEVEL_20; }

    @ConfigItem(keyName = "strengthTarget", name = "Strength target",
            description = "Highest Strength task to complete; only used with Choose skills.",
            section = SKILLS_SECTION, position = 1)
    default Target strengthTarget() { return Target.LEVEL_20; }

    @ConfigItem(keyName = "defenceTarget", name = "Defence target",
            description = "Highest Defence task to complete; only used with Choose skills.",
            section = SKILLS_SECTION, position = 2)
    default Target defenceTarget() { return Target.LEVEL_20; }

    @ConfigItem(keyName = "rangedTarget", name = "Ranged target",
            description = "Highest Ranged task to complete; only used with Choose skills.",
            section = SKILLS_SECTION, position = 3)
    default Target rangedTarget() { return Target.LEVEL_20; }

    @ConfigItem(keyName = "magicTarget", name = "Magic target",
            description = "Highest Magic task to complete; only used with Choose skills.",
            section = SKILLS_SECTION, position = 4)
    default Target magicTarget() { return Target.LEVEL_20; }

    @ConfigItem(keyName = "fishingTarget", name = "Fishing target",
            description = "Level 5 completes the Fishing task; only used with Choose skills.",
            section = SKILLS_SECTION, position = 5)
    default GatherTarget fishingTarget() { return GatherTarget.LEVEL_5; }

    @ConfigItem(keyName = "woodcuttingTarget", name = "Woodcutting target",
            description = "Level 5 completes the Woodcutting task; only used with Choose skills.",
            section = SKILLS_SECTION, position = 6)
    default GatherTarget woodcuttingTarget() { return GatherTarget.LEVEL_5; }

    @ConfigItem(keyName = "miningTarget", name = "Mining target",
            description = "Level 5 completes the Mining task; only used with Choose skills.",
            section = SKILLS_SECTION, position = 7)
    default GatherTarget miningTarget() { return GatherTarget.LEVEL_5; }

    @Range(min = 0)
    @ConfigItem(keyName = "coinBudget", name = "Spending limit",
            description = "Most coins this account may spend in total; only used when buying is on.",
            hidden = true, position = 2)
    default int coinBudget() { return 5000; }

    @Range(min = 0)
    @ConfigItem(keyName = "coinReserve", name = "Coins to keep",
            description = "Coins never spent; only used when buying is on.",
            hidden = true, position = 3)
    default int coinReserve() { return 50; }


    @Range(min = 0)
    @ConfigItem(keyName = "deathBudget", name = "Grave fee limit",
            description = "Most coins to pay at a grave; items above it are left behind.",
            hidden = true, position = 0)
    default int deathBudget() { return 5000; }

    @ConfigItem(keyName = "useDeathsOffice", name = "Use Death's Office",
            description = "Collect items from Death's Office once the grave has expired; off leaves them to collect by hand.",
            hidden = true, position = 1)
    default boolean useDeathsOffice() { return false; }

    @ConfigSection(
            name = "Overlay",
            description = "The in-game session panel.",
            position = 2
    )
    String OVERLAY_SECTION = "overlay";
    String COMPACT_PANEL_KEY = "compactPanel";

    @ConfigItem(keyName = "showPanel", name = "Show panel",
            description = "Show the session panel: what it is doing, how far to the current goal, and anything that went wrong.",
            section = OVERLAY_SECTION, position = 0)
    default boolean showPanel() { return true; }

    @ConfigItem(keyName = COMPACT_PANEL_KEY, name = "Compact panel",
            description = "Shrink the panel to one line; only used when the panel is shown. Right-click the panel to switch too.",
            section = OVERLAY_SECTION, position = 1)
    default boolean compactPanel() { return false; }
}
