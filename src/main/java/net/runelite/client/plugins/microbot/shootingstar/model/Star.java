package net.runelite.client.plugins.microbot.shootingstar.model;

import java.util.EnumSet;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.http.api.worlds.WorldType;

public interface Star
{

	long getCalledAt();

	long getEndsAt();

	void setEndsAt(long endsAt);

	int getWorld();

	Object getLocationKey();

	String getRawLocation();

	ShootingStarLocation getShootingStarLocation();

	void setShootingStarLocation(ShootingStarLocation shootingStarLocation);

	int getTier();

	void setTier(int tier);

	boolean isSelected();

	void setSelected(boolean selected);

	boolean isHidden();

	boolean isGameModeWorld();

	void setGameModeWorld(boolean gameModeWorld);

	boolean isSeasonalWorld();

	void setSeasonalWorld(boolean seasonalWorld);

	boolean isMemberWorld();

	void setMemberWorld(boolean memberWorld);

	void setHidden(boolean hidden);

	default boolean hasRequirements()
	{
		return hasLocationRequirements() && hasMiningLevel();
	}

	default boolean hasMiningLevel()
	{
		return Rs2Player.getSkillRequirement(Skill.MINING, getMiningLevel(), true);
	}

	default boolean hasLocationRequirements()
	{
		return getShootingStarLocation().hasRequirements();
	}

	default boolean isInWilderness()
	{
		return getShootingStarLocation().isInWilderness();
	}

	default int getObjectId() {
		switch (getTier()) {
			case 1:
				return ObjectID.STAR_SIZE_ONE_STAR;
			case 2:
				return ObjectID.STAR_SIZE_TWO_STAR;
			case 3:
				return ObjectID.STAR_SIZE_THREE_STAR;
			case 4:
				return ObjectID.STAR_SIZE_FOUR_STAR;
			case 5:
				return ObjectID.STAR_SIZE_FIVE_STAR;
			case 6:
				return ObjectID.STAR_SIZE_SIX_STAR;
			case 7:
				return ObjectID.STAR_SIZE_SEVEN_STAR;
			case 8:
				return ObjectID.STAR_SIZE_EIGHT_STAR;
			case 9:
				return ObjectID.STAR_SIZE_NINE_STAR;
			default:
				return -1;
		}
	}

	default int getMiningLevel() {
		switch (getTier()) {
			case 1:
				return 10;
			case 2:
				return 20;
			case 3:
				return 30;
			case 4:
				return 40;
			case 5:
				return 50;
			case 6:
				return 60;
			case 7:
				return 70;
			case 8:
				return 80;
			case 9:
				return 90;
			default:
				return -1;
		}
	}

	default int getTierBasedOnObjectId(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.STAR_SIZE_ONE_STAR:
				return 1;
			case ObjectID.STAR_SIZE_TWO_STAR:
				return 2;
			case ObjectID.STAR_SIZE_THREE_STAR:
				return 3;
			case ObjectID.STAR_SIZE_FOUR_STAR:
				return 4;
			case ObjectID.STAR_SIZE_FIVE_STAR:
				return 5;
			case ObjectID.STAR_SIZE_SIX_STAR:
				return 6;
			case ObjectID.STAR_SIZE_SEVEN_STAR:
				return 7;
			case ObjectID.STAR_SIZE_EIGHT_STAR:
				return 8;
			case ObjectID.STAR_SIZE_NINE_STAR:
				return 9;
			default:
				return -1;
		}
	}

	default EnumSet<WorldType> getGameModeWorldTypes()
	{
		return EnumSet.of(
			WorldType.PVP,
			WorldType.HIGH_RISK,
			WorldType.BOUNTY,
			WorldType.SKILL_TOTAL,
			WorldType.LAST_MAN_STANDING,
			WorldType.QUEST_SPEEDRUNNING,
			WorldType.BETA_WORLD,
			WorldType.DEADMAN,
			WorldType.PVP_ARENA,
			WorldType.TOURNAMENT,
			WorldType.FRESH_START_WORLD
		);
	}
}