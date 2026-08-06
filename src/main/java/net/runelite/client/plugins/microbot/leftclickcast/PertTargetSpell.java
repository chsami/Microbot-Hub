package net.runelite.client.plugins.microbot.leftclickcast;

import lombok.Getter;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

@Getter
public enum PertTargetSpell
{
	// Modern autocastable combat lines (Strike -> Surge)
	WIND_STRIKE("Wind Strike", MagicAction.WIND_STRIKE),
	WATER_STRIKE("Water Strike", MagicAction.WATER_STRIKE),
	EARTH_STRIKE("Earth Strike", MagicAction.EARTH_STRIKE),
	FIRE_STRIKE("Fire Strike", MagicAction.FIRE_STRIKE),
	WIND_BOLT("Wind Bolt", MagicAction.WIND_BOLT),
	WATER_BOLT("Water Bolt", MagicAction.WATER_BOLT),
	EARTH_BOLT("Earth Bolt", MagicAction.EARTH_BOLT),
	FIRE_BOLT("Fire Bolt", MagicAction.FIRE_BOLT),
	WIND_BLAST("Wind Blast", MagicAction.WIND_BLAST),
	WATER_BLAST("Water Blast", MagicAction.WATER_BLAST),
	EARTH_BLAST("Earth Blast", MagicAction.EARTH_BLAST),
	FIRE_BLAST("Fire Blast", MagicAction.FIRE_BLAST),
	WIND_WAVE("Wind Wave", MagicAction.WIND_WAVE),
	WATER_WAVE("Water Wave", MagicAction.WATER_WAVE),
	EARTH_WAVE("Earth Wave", MagicAction.EARTH_WAVE),
	FIRE_WAVE("Fire Wave", MagicAction.FIRE_WAVE),
	WIND_SURGE("Wind Surge", MagicAction.WIND_SURGE),
	WATER_SURGE("Water Surge", MagicAction.WATER_SURGE),
	EARTH_SURGE("Earth Surge", MagicAction.EARTH_SURGE),
	FIRE_SURGE("Fire Surge", MagicAction.FIRE_SURGE),

	// Ancient autocastable combat lines (Rush/Burst/Blitz/Barrage for each element)
	SMOKE_RUSH("Smoke Rush", MagicAction.SMOKE_RUSH),
	SHADOW_RUSH("Shadow Rush", MagicAction.SHADOW_RUSH),
	BLOOD_RUSH("Blood Rush", MagicAction.BLOOD_RUSH),
	ICE_RUSH("Ice Rush", MagicAction.ICE_RUSH),
	SMOKE_BURST("Smoke Burst", MagicAction.SMOKE_BURST),
	SHADOW_BURST("Shadow Burst", MagicAction.SHADOW_BURST),
	BLOOD_BURST("Blood Burst", MagicAction.BLOOD_BURST),
	ICE_BURST("Ice Burst", MagicAction.ICE_BURST),
	SMOKE_BLITZ("Smoke Blitz", MagicAction.SMOKE_BLITZ),
	SHADOW_BLITZ("Shadow Blitz", MagicAction.SHADOW_BLITZ),
	BLOOD_BLITZ("Blood Blitz", MagicAction.BLOOD_BLITZ),
	ICE_BLITZ("Ice Blitz", MagicAction.ICE_BLITZ),
	SMOKE_BARRAGE("Smoke Barrage", MagicAction.SMOKE_BARRAGE),
	SHADOW_BARRAGE("Shadow Barrage", MagicAction.SHADOW_BARRAGE),
	BLOOD_BARRAGE("Blood Barrage", MagicAction.BLOOD_BARRAGE),
	ICE_BARRAGE("Ice Barrage", MagicAction.ICE_BARRAGE),

	// Non-autocastable combat spells
	CRUMBLE_UNDEAD("Crumble Undead", MagicAction.CRUMBLE_UNDEAD),
	IBAN_BLAST("Iban Blast", MagicAction.IBAN_BLAST),
	MAGIC_DART("Magic Dart", MagicAction.MAGIC_DART),
	SARADOMIN_STRIKE("Saradomin Strike", MagicAction.SARADOMIN_STRIKE),
	CLAWS_OF_GUTHIX("Claws of Guthix", MagicAction.CLAWS_OF_GUTHIX),
	FLAMES_OF_ZAMORAK("Flames of Zamorak", MagicAction.FLAMES_OF_ZAMORAK),

	// Arceuus offensive target spells
	GHOSTLY_GRASP("Ghostly Grasp", MagicAction.GHOSTLY_GRASP),
	SKELETAL_GRASP("Skeletal Grasp", MagicAction.SKELETAL_GRASP),
	UNDEAD_GRASP("Undead Grasp", MagicAction.UNDEAD_GRASP),
	INFERIOR_DEMONBANE("Inferior Demonbane", MagicAction.INFERIOR_DEMONBANE),
	SUPERIOR_DEMONBANE("Superior Demonbane", MagicAction.SUPERIOR_DEMONBANE),
	DARK_DEMONBANE("Dark Demonbane", MagicAction.DARK_DEMONBANE),
	LESSER_CORRUPTION("Lesser Corruption", MagicAction.LESSER_CORRUPTION),
	GREATER_CORRUPTION("Greater Corruption", MagicAction.GREATER_CORRUPTION),

	// Utility target spells
	CONFUSE("Confuse", MagicAction.CONFUSE),
	WEAKEN("Weaken", MagicAction.WEAKEN),
	CURSE("Curse", MagicAction.CURSE),
	BIND("Bind", MagicAction.BIND),
	SNARE("Snare", MagicAction.SNARE),
	ENTANGLE("Entangle", MagicAction.ENTANGLE),
	VULNERABILITY("Vulnerability", MagicAction.VULNERABILITY),
	ENFEEBLE("Enfeeble", MagicAction.ENFEEBLE),
	STUN("Stun", MagicAction.STUN),
	TELE_BLOCK("Tele Block", MagicAction.TELE_BLOCK),
	TELEOTHER_LUMBRIDGE("Tele Other Lumbridge", MagicAction.TELEOTHER_LUMBRIDGE),
	TELEOTHER_FALADOR("Tele Other Falador", MagicAction.TELEOTHER_FALADOR),
	TELEOTHER_CAMELOT("Tele Other Camelot", MagicAction.TELEOTHER_CAMELOT);

	private final String displayName;
	private final MagicAction magicAction;

	PertTargetSpell(String displayName, MagicAction magicAction)
	{
		this.displayName = displayName;
		this.magicAction = magicAction;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
