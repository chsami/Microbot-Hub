package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

@ConfigGroup("pestcontrol")
@ConfigInformation(
        "<html><b>Pest Control setup</b><br />"
                + "Start on the Void Knights' Outpost near the boat for your combat level.<br /><br />"
                + "Configure exact weapon and off-hand names for Style 1 and any optional styles. "
                + "Use <b>None</b> for a two-handed weapon or empty off-hand, and keep one inventory slot free when an equipped off-hand must be removed.<br /><br />"
                + "Portal styles: Purple = Ranged, Blue = Magic, Yellow = Stab/Slash, Red = Crush. "
                + "Configure either melee weapon entry; when one is blank, the other weapon, off-hand, and configured attack style are reused for both portals. "
                + "Magic autocast is checked once at startup and then remembered by the weapon.<br /><br />"
                + "Void helmet switching is automatic when every enabled style's helmet is equipped or in your inventory. "
                + "Carry the Void ranger, mage, and/or melee helms required by your enabled styles. "
                + "If that complete helmet set is not available, the head slot is left untouched so non-Void setups continue to work. "
                + "No armour slots other than the Void helmet are switched.</html>")
public interface PestControlConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "Travel, prayer, and activity settings",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Combat styles",
            description = "Ordered combat-style selection",
            position = 1
    )
    String combatStylesSection = "combatStylesSection";

    @ConfigSection(
            name = "Ranged loadout",
            description = "Ranged weapon and off-hand",
            position = 2
    )
    String rangedSection = "rangedSection";

    @ConfigSection(
            name = "Magic loadout",
            description = "Magic weapon, off-hand, and casting mode",
            position = 3
    )
    String magicSection = "magicSection";

    @ConfigSection(
            name = "Melee loadouts",
            description = "Yellow Stab/Slash and Red Crush switches; one melee weapon entry is sufficient",
            position = 4
    )
    String meleeSection = "meleeSection";

    @ConfigSection(
            name = "Opening strategy",
            description = "Opening-side selection and weights",
            position = 5
    )
    String openingSection = "openingSection";

    @ConfigSection(
            name = "Special attacks",
            description = "Portal-only special attack settings",
            position = 6
    )
    String specialSection = "specialSection";

    @ConfigItem(
            keyName = "Alch in boat",
            name = "Alch while waiting",
            description = "Alch while waiting in the boat",
            position = 0,
            section = generalSection
    )
    default boolean alchInBoat() {
        return false;
    }

    @ConfigItem(
            keyName = "itemToAlch",
            name = "Item to alch",
            description = "Exact item name to alch",
            position = 1,
            section = generalSection
    )
    default String alchItem() {
        return "";
    }

    @ConfigItem(
            keyName = "QuickPrayer",
            name = "Enable QuickPrayer",
            description = "Enable quick prayer at the start of each round",
            position = 2,
            section = generalSection
    )
    default boolean quickPrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "World",
            name = "World",
            description = "Pest Control world",
            position = 3,
            section = generalSection
    )
    default int world() {
        return 344;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "activityRecoveryStart",
            name = "Activity recovery start",
            description = "Acquire a nearby combat target at this activity percentage",
            position = 4,
            section = generalSection
    )
    default int activityRecoveryStart() {
        return 40;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "activityRecoveryTarget",
            name = "Activity recovery target",
            description = "Resume portal strategy after activity recovers to this percentage",
            position = 5,
            section = generalSection
    )
    default int activityRecoveryTarget() {
        return 70;
    }

    @ConfigItem(
            keyName = "primaryCombatStyle",
            name = "Style 1 (main)",
            description = "Mandatory main style and fallback loadout",
            position = 0,
            section = combatStylesSection
    )
    default PestControlCombatStyle primaryCombatStyle() {
        return PestControlCombatStyle.RANGED;
    }

    @ConfigItem(
            keyName = "secondaryCombatStyle",
            name = "Style 2",
            description = "Optional second combat style",
            position = 1,
            section = combatStylesSection
    )
    default PestControlOptionalCombatStyle secondaryCombatStyle() {
        return PestControlOptionalCombatStyle.DISABLED;
    }

    @ConfigItem(
            keyName = "tertiaryCombatStyle",
            name = "Style 3",
            description = "Optional third combat style",
            position = 2,
            section = combatStylesSection
    )
    default PestControlOptionalCombatStyle tertiaryCombatStyle() {
        return PestControlOptionalCombatStyle.DISABLED;
    }

    @ConfigItem(
            keyName = "rangedWeapon",
            name = "Weapon",
            description = "Exact ranged weapon name",
            position = 0,
            section = rangedSection
    )
    default String rangedWeapon() {
        return "Adamant crossbow";
    }

    @ConfigItem(
            keyName = "rangedOffhand",
            name = "Off-hand",
            description = "Exact ranged off-hand name, or None for a two-handed/empty off-hand loadout",
            position = 1,
            section = rangedSection
    )
    default String rangedOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "magicWeapon",
            name = "Weapon",
            description = "Exact magic weapon name",
            position = 0,
            section = magicSection
    )
    default String magicWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "magicOffhand",
            name = "Off-hand",
            description = "Exact magic off-hand name, or None for a two-handed/empty off-hand loadout",
            position = 1,
            section = magicSection
    )
    default String magicOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "magicCastingMode",
            name = "Casting mode",
            description = "Powered staff attacks directly; autocast is prepared once at plugin start",
            position = 2,
            section = magicSection
    )
    default PestControlMagicMode magicCastingMode() {
        return PestControlMagicMode.POWERED_STAFF;
    }

    @ConfigItem(
            keyName = "magicAutocastSpell",
            name = "Autocast spell",
            description = "Spell remembered for the configured magic weapon",
            position = 3,
            section = magicSection
    )
    default Rs2CombatSpells magicAutocastSpell() {
        return Rs2CombatSpells.WIND_STRIKE;
    }

    @ConfigItem(
            keyName = "primaryMeleeStyle",
            name = "Legacy default melee variant",
            description = "Retained only for compatibility with older saved configurations",
            position = 0,
            section = meleeSection,
            hidden = true
    )
    default PestControlMeleeStyle primaryMeleeStyle() {
        return PestControlMeleeStyle.SLASH;
    }

    @ConfigItem(
            keyName = "stabWeapon",
            name = "Stab weapon",
            description = "Legacy duplicate input retained for compatibility",
            position = 1,
            section = meleeSection,
            hidden = true
    )
    default String stabWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "stabOffhand",
            name = "Stab off-hand",
            description = "Legacy duplicate input retained for compatibility",
            position = 2,
            section = meleeSection,
            hidden = true
    )
    default String stabOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "slashStabWeapon",
            name = "Yellow weapon",
            description = "Exact weapon name for Yellow's selected Stab or Slash style; falls back to Red crush weapon when blank",
            position = 1,
            section = meleeSection
    )
    default String slashStabWeapon() {
        return "Dragon scimitar";
    }

    @ConfigItem(
            keyName = "slashOffhand",
            name = "Yellow off-hand",
            description = "Exact off-hand name for the yellow portal, or None",
            position = 2,
            section = meleeSection
    )
    default String slashOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "crushWeapon",
            name = "Red crush weapon",
            description = "Exact crush-capable weapon name for Red; also used for Yellow when the Yellow weapon is blank",
            position = 3,
            section = meleeSection
    )
    default String crushWeapon() {
        return "None";
    }

    @ConfigItem(
            keyName = "crushOffhand",
            name = "Red crush off-hand",
            description = "Exact off-hand name for the red crush weapon, or None",
            position = 4,
            section = meleeSection
    )
    default String crushOffhand() {
        return "None";
    }

    @ConfigItem(
            keyName = "yellowMeleeStyle",
            name = "Yellow attack style",
            description = "Attack style used with the Yellow weapon",
            position = 0,
            section = meleeSection
    )
    default PestControlYellowAttackStyle yellowMeleeStyle() {
        return PestControlYellowAttackStyle.SLASH;
    }

    @ConfigItem(
            keyName = "redMeleeStyle",
            name = "Red portal variant",
            description = "Legacy value retained for compatibility; Red now always uses Crush",
            position = 8,
            section = meleeSection,
            hidden = true
    )
    default PestControlMeleeStyle redMeleeStyle() {
        return PestControlMeleeStyle.CRUSH;
    }

    @ConfigItem(
            keyName = "openingMode",
            name = "Opening selection",
            description = "Use Style 1, equal random selection, or configured weights",
            position = 0,
            section = openingSection
    )
    default PestControlOpeningMode openingMode() {
        return PestControlOpeningMode.WEIGHTED_RANDOM;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "rangedOpeningWeight",
            name = "Purple (ranged) weight",
            description = "Relative Purple opening weight",
            position = 1,
            section = openingSection
    )
    default int rangedOpeningWeight() {
        return 55;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "magicOpeningWeight",
            name = "Blue (magic) weight",
            description = "Relative Blue opening weight",
            position = 2,
            section = openingSection
    )
    default int magicOpeningWeight() {
        return 23;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "meleeOpeningWeight",
            name = "Yellow (melee) weight",
            description = "Relative Yellow opening weight; Red is never an opening target",
            position = 3,
            section = openingSection
    )
    default int meleeOpeningWeight() {
        return 22;
    }

    @ConfigItem(
            keyName = "usePurpleSpecialAttack",
            name = "Use special (purple)",
            description = "Use the equipped weapon's special attack only while attacking Purple",
            position = 0,
            section = specialSection
    )
    default boolean usePurpleSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "useBlueSpecialAttack",
            name = "Use special (blue)",
            description = "Use the equipped weapon's special attack only while attacking Blue",
            position = 1,
            section = specialSection
    )
    default boolean useBlueSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "useYellowSpecialAttack",
            name = "Use special (yellow)",
            description = "Use the equipped weapon's special attack only while attacking Yellow",
            position = 2,
            section = specialSection
    )
    default boolean useYellowSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "useRedSpecialAttack",
            name = "Use special (red)",
            description = "Use the equipped weapon's special attack only while attacking Red",
            position = 3,
            section = specialSection
    )
    default boolean useRedSpecialAttack() {
        return false;
    }
}
