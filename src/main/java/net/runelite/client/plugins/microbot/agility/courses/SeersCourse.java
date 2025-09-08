package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;

public class SeersCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2729, 3486, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_TIGHTROPE),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_1),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_2),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_LEAPDOWN)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 60;
	}

	@Override
	public boolean handleWalkToStart(WorldPoint playerWorldLocation)
	{
		if (Microbot.getClient().getTopLevelWorldView().getPlane() != 0)
		{
			return false;
		}
		if (getCurrentObstacleIndex() > 0)
		{
			return false;
		}

		if (Microbot.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1
			&& Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT)
			&& playerWorldLocation.distanceTo(getStartPoint()) > 12)

		{
			Rs2Magic.cast(Rs2Spells.CAMELOT_TELEPORT, "Seers'", 2);
			return Global.sleepUntil(() -> {
				WorldPoint currentLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
				return currentLocation.distanceTo(getStartPoint()) <= 12;
			}, 5000);
		}

		if (playerWorldLocation.distanceTo(getStartPoint()) > 12)
		{
			Microbot.log("Going back to course's starting point");
			Rs2Walker.walkTo(getStartPoint(), 2);
			return true;
		}
		return false;
	}
}
