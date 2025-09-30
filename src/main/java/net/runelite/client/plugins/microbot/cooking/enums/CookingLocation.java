package net.runelite.client.plugins.microbot.cooking.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum CookingLocation
{
	// Free-to-play
	FALADOR_PVP(new WorldPoint(2968, 3332, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	FALADOR(new WorldPoint(3039, 3345, 0), CookingAreaType.RANGE, 40296),
	DRAYNOR_VILLAGE(new WorldPoint(3097, 3237, 0), CookingAreaType.FIRE, ObjectID.FIRE_COOK),
	AL_KHARID(new WorldPoint(3272, 3180, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	EDGEVILLE(new WorldPoint(3078, 3495, 0), CookingAreaType.RANGE, ObjectID._100_DAVE_STOVE),
	VARROCK_EAST(new WorldPoint(3247, 3397, 0), CookingAreaType.RANGE, ObjectID.FAI_VARROCK_RANGE),
	VARROCK_WEST(new WorldPoint(3160, 3428, 0), CookingAreaType.RANGE, ObjectID.FAI_VARROCK_RANGE),
	BARBARBIAN_VILLAGE(new WorldPoint(3106, 3433, 0), CookingAreaType.FIRE, ObjectID.FIRE_COOK),
	PORT_SARIM(new WorldPoint(3018, 3238, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	RIMMINGTON(new WorldPoint(2969, 3210, 0), CookingAreaType.RANGE, ObjectID.RIMMINGTON_POOR_RANGE),
	LUMBRIDGE_TUTOR(new WorldPoint(3231, 3196, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	// Pay-to-play
	MYTHS_GUILD(new WorldPoint(2465, 2848, 0), CookingAreaType.RANGE, ObjectID.DS2_GUILD_COOKING_RANGE),
	ROGUES_DEN(new WorldPoint(3043, 4972, 1), CookingAreaType.FIRE, ObjectID.FIRE_COOK),
	COOKS_GUILD(new WorldPoint(3146, 3452, 0), CookingAreaType.RANGE, ObjectID.FAI_VARROCK_RANGE),
	HOSIDIUS_CLAY_OVEN(new WorldPoint(1677, 3621, 0), CookingAreaType.RANGE, ObjectID.IZNOT_CLAY_RANGE),
	RUINS_OF_UNKAH(new WorldPoint(3155, 2818, 0), CookingAreaType.FIRE, ObjectID.FIRE),
	PORT_KHAZARD(new WorldPoint(2662, 3156, 0), CookingAreaType.FIRE, ObjectID.FIRE_COOK),
	WINTERTODT_CAMP(new WorldPoint(1631, 3940, 0), CookingAreaType.FIRE, ObjectID.WINT_BONFIRE),
	CATHERBY(new WorldPoint(2817, 3443, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	COOKS_KITCHEN(new WorldPoint(3211, 3216, 0), CookingAreaType.RANGE, ObjectID.COOKSQUESTRANGE),
	LANDS_END(new WorldPoint(1515, 3442, 0), CookingAreaType.RANGE, ObjectID.FAI_VARROCK_RANGE),
	FISHING_GUILD(new WorldPoint(2616, 3396, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	SEERS_VILLAGE(new WorldPoint(2715, 3477, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	EAST_ARDOUGNE_FARM(new WorldPoint(2642, 3356, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	EAST_ARDOUGNE_SOUTH(new WorldPoint(2647, 3298, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	YANILLE(new WorldPoint(2566, 3103, 0), CookingAreaType.RANGE, ObjectID.RANGE),
	TAI_BWO_WANNAI(new WorldPoint(2788, 3048, 0), CookingAreaType.FIRE, ObjectID.FIRE),
	PORT_PISCARILIUS(new WorldPoint(1806, 3735, 0), CookingAreaType.RANGE, ObjectID.PISCARILIUS_RANGE),
	HOSIDIUS(new WorldPoint(1738, 3612, 0), CookingAreaType.RANGE, ObjectID.HOS_COOKING_RANGE_02),
	NARDAH(new WorldPoint(3434, 2887, 0), CookingAreaType.RANGE, ObjectID.ELID_CLAY_OVEN),
	;

	private final WorldPoint cookingObjectWorldPoint;
	private final CookingAreaType cookingAreaType;
	private final int cookingObjectID;

	public static CookingLocation findNearestCookingLocation(CookingItem item)
	{
		Map<Integer, CookingLocation> distanceMap = new HashMap<>();

		for (CookingLocation location : values())
		{
			if (!location.hasRequirements())
			{
				continue;
			}

			if ((item.getCookingAreaType() != CookingAreaType.BOTH) &&
				(location.getCookingAreaType() != item.getCookingAreaType()))
			{
				continue;
			}

			int distance = Rs2Player.distanceTo(location.getCookingObjectWorldPoint());
			distanceMap.put(distance, location);
		}

		return distanceMap.entrySet()
			.stream()
			.min(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.orElse(null);
	}

	public boolean hasRequirements()
	{
		boolean hasLineOfSight = Microbot.getClient().getLocalPlayer().getWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), this.cookingObjectWorldPoint);
		switch (this)
		{
			case COOKS_GUILD:
				boolean hasCookingLevel = Rs2Player.getSkillRequirement(Skill.COOKING, 32);

				boolean hasCooksGuildEquipment =
					Rs2Equipment.isWearing("Chef's hat")
						|| Rs2Equipment.isWearing("Golden chef's hat")
						|| Rs2Equipment.isWearing("Cooking cape")
						|| Rs2Equipment.isWearing("Cooking hood")
						|| Rs2Equipment.isWearing("Max cape")
						|| Rs2Equipment.isWearing("Max hood")
						|| Rs2Equipment.isWearing("Varrock armour 3")
						|| Rs2Equipment.isWearing("Varrock armour 4");

				return hasCookingLevel && hasCooksGuildEquipment;
			case HOSIDIUS_CLAY_OVEN:
				boolean hasKourendEasyDiary = Microbot.getVarbitValue(VarbitID.KOUREND_DIARY_EASY_COMPLETE) == 1;

				if (hasLineOfSight && Rs2Player.isMember() && hasKourendEasyDiary)
				{
					return true;
				}
				return Rs2Player.isMember() && hasKourendEasyDiary;
			default:
				return true;
		}
	}
}
