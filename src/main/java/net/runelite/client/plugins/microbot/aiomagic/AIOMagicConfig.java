package net.runelite.client.plugins.microbot.aiomagic;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.aiomagic.enums.MagicActivity;
import net.runelite.client.plugins.microbot.aiomagic.enums.SplashSpellMode;
import net.runelite.client.plugins.microbot.aiomagic.enums.StunSpell;
import net.runelite.client.plugins.microbot.aiomagic.enums.SuperHeatItem;
import net.runelite.client.plugins.microbot.aiomagic.enums.TeleportSpell;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

@ConfigGroup(AIOMagicConfig.configGroup)
public interface AIOMagicConfig extends Config {
	String configGroup = "aio-magic";
	String activity = "magicActivity";
	String combatSpell = "magicCombatSpell";
	String alchItems = "alchItems";
	String superHeatItem = "superHeatItem";
	String npcName = "npcName";
	String staff = "staff";
	String teleportSpell = "teleportSpell";
	String stunSpell = "stunSpell";
	String stunNpcName = "stunNpcName";
	String splashSpellMode = "splashSpellMode";
	String castsPerTrip = "splashCastsPerTrip";
	String buyRunesOnGe = "splashBuyRunesOnGe";
	String maxGpPerPurchase = "splashMaxGpPerPurchase";
	String requireSplashBonus = "splashRequireMagicBonus";
	String useElementalStaff = "splashUseElementalStaff";
	String autoSplashGear = "splashAutoGear";
	String buySplashGear = "splashBuyGear";

	@ConfigSection(
			name = "General Settings",
			description = "Configure general plugin configuration & preferences",
			position = 0
	)
	String generalSection = "general";

	@ConfigSection(
			name = "Splashing Settings",
			description = "Configure Stun settings",
			position = 1
	)
	String splashSection = "splash";

	@ConfigSection(
			name = "Stun Settings",
			description = "Configure splashing settings",
			position = 1
	)
	String stunSection = "stun";

	@ConfigSection(
			name = "Alch Settings",
			description = "Configure Alching settings",
			position = 2
	)
	String alchSection = "alch";

	@ConfigSection(
			name = "SuperHeat Settings",
			description = "Configure SuperHeat settings",
			position = 2
	)
	String superHeatSection = "superHeat";

	@ConfigSection(
			name = "Teleport Settings",
			description = "Configure teleport settings",
			position = 3
	)
	String teleportSection = "teleport";

	@ConfigItem(
			keyName = activity,
			name = "Activity",
			description = "Select the activity you would like to perform",
			position = 0,
			section = generalSection
	)
	default MagicActivity magicActivity() {
		return MagicActivity.SPLASHING;
	}

	@ConfigItem(
			keyName = stunNpcName,
			name = "Stun npc name",
			description = "Name of the npc to stun",
			position = 0,
			section = stunSection
	)
	default String stunNpcName() {
		return "";
	}
	@ConfigItem(
			keyName = stunSpell,
			name = "Stun spell",
			description = "Name of the stun spell",
			position = 1,
			section = stunSection
	)
	default StunSpell stunSpell() {
		return StunSpell.STUN;
	}

	@ConfigItem(
			keyName = npcName,
			name = "NPC Name",
			description = "Name of the NPC you would like to splash",
			position = 0,
			section = splashSection
	)
	default String npcName() {
		return "";
	}

	@ConfigItem(
			keyName = combatSpell,
			name = "Combat Spell",
			description = "Spell used when 'Splash spell' is set to 'Combat Spell setting'",
			position = 2,
			section = splashSection
	)
	default Rs2CombatSpells combatSpell() {
		return Rs2CombatSpells.WIND_STRIKE;
	}

	@ConfigItem(
			keyName = splashSpellMode,
			name = "Splash spell",
			description = "Auto picks the highest standard-book combat spell your Magic level and runes allow, and upgrades as you level up",
			position = 1,
			section = splashSection
	)
	default SplashSpellMode splashSpellMode() {
		return SplashSpellMode.AUTO;
	}

	@ConfigItem(
			keyName = castsPerTrip,
			name = "Casts per trip",
			description = "How many casts' worth of runes to withdraw when restocking at the bank",
			position = 3,
			section = splashSection
	)
	default int castsPerTrip() {
		return 1000;
	}

	@ConfigItem(
			keyName = useElementalStaff,
			name = "Use elemental staff",
			description = "Wield an owned elemental staff to save runes (only if it keeps you splash-safe)",
			position = 4,
			section = splashSection
	)
	default boolean useElementalStaff() {
		return true;
	}

	@ConfigItem(
			keyName = buyRunesOnGe,
			name = "Buy runes on the GE",
			description = "When runes are short and the bank has none, buy them on the Grand Exchange",
			position = 5,
			section = splashSection
	)
	default boolean buyRunesOnGe() {
		return false;
	}

	@ConfigItem(
			keyName = maxGpPerPurchase,
			name = "Max gp per purchase",
			description = "Never spend more than this on one GE rune purchase",
			position = 6,
			section = splashSection
	)
	default int maxGpPerPurchase() {
		return 100_000;
	}

	@ConfigItem(
			keyName = requireSplashBonus,
			name = "Require -65 magic bonus",
			description = "Stop if your equipment magic attack bonus is above -65 (you would hit instead of splash). Off = only warn",
			position = 7,
			section = splashSection
	)
	default boolean requireSplashBonus() {
		return true;
	}

	@ConfigItem(
			keyName = autoSplashGear,
			name = "Auto splash gear",
			description = "Before splashing, wear only metal armour (owned, else bought) until the magic attack bonus is -65 or lower",
			position = 8,
			section = splashSection
	)
	default boolean autoSplashGear() {
		return true;
	}

	@ConfigItem(
			keyName = buySplashGear,
			name = "Buy splash gear on the GE",
			description = "Buy the cheapest metal armour pieces needed to reach -65 (spend capped by 'Max gp per purchase')",
			position = 9,
			section = splashSection
	)
	default boolean buySplashGear() {
		return true;
	}

	@ConfigItem(
			keyName = alchItems,
			name = "Alch Items",
			description = "List of items you would like to alch",
			position = 0,
			section = alchSection
	)
	default String alchItems() {
		return "";
	}

	@ConfigItem(
			keyName = superHeatItem,
			name = "SuperHeat Items",
			description = "List of items you would like to superheat",
			position = 0,
			section = superHeatSection
	)
	default SuperHeatItem superHeatItem() {
		return SuperHeatItem.IRON;
	}

	@ConfigItem(
			keyName = teleportSpell,
			name = "Teleport Spell",
			description = "Select the teleport spell you would like to use",
			position = 0,
			section = teleportSection
	)
	default TeleportSpell teleportSpell() {
		return TeleportSpell.VARROCK_TELEPORT;
	}
}
