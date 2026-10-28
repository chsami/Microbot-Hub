package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.Constants;
import net.runelite.api.GroundObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class AgilityObjects
{
	private static final TileObjectType[] NON_GAME_TYPES = {
		TileObjectType.GROUND, TileObjectType.WALL, TileObjectType.DECORATIVE
	};

	private AgilityObjects()
	{
	}

	public static List<TileObject> all(Predicate<TileObject> predicate)
	{
		WorldPoint playerLocation = Rs2Player.getWorldLocation();
		List<TileObject> all = new ArrayList<>(near(playerLocation, Constants.SCENE_SIZE, predicate, TileObjectType.GAME));
		all.addAll(near(playerLocation, Constants.SCENE_SIZE, predicate, NON_GAME_TYPES));
		return all;
	}

	public static List<GroundObject> groundObjects()
	{
		return near(Rs2Player.getWorldLocation(), Constants.SCENE_SIZE / 2, o -> true, TileObjectType.GROUND).stream()
			.map(o -> (GroundObject) o)
			.collect(Collectors.toList());
	}

	public static TileObject nearestGameObject(int id, WorldPoint anchor, int distance)
	{
		List<TileObject> objects = near(anchor, distance, o -> o.getId() == id, TileObjectType.GAME);
		return objects.isEmpty() ? null : objects.get(0);
	}

	public static TileObject nearestNonGameObject(int id)
	{
		List<TileObject> objects = near(Rs2Player.getWorldLocation(), Constants.SCENE_SIZE / 2, o -> o.getId() == id, NON_GAME_TYPES);
		return objects.isEmpty() ? null : objects.get(0);
	}

	public static boolean interact(TileObject object)
	{
		if (object == null)
		{
			return false;
		}
		return new Rs2TileObjectModel(object).click();
	}

	public static boolean canReach(WorldPoint target, int objectSizeX, int objectSizeY, int pathSizeX, int pathSizeY)
	{
		if (target == null)
		{
			return false;
		}

		List<WorldPoint> path = Rs2Player.getRs2WorldPoint().pathTo(target, true);
		if (path == null || path.isEmpty())
		{
			return false;
		}

		WorldPoint pathEndpoint = path.get(path.size() - 1);
		WorldPoint pathSouthWest = new WorldPoint(
			pathEndpoint.getX() - pathSizeX / 2,
			pathEndpoint.getY() - pathSizeY / 2,
			pathEndpoint.getPlane()
		);
		WorldArea pathArea = new WorldArea(pathSouthWest, pathSizeX, pathSizeY);

		WorldPoint objectSouthWest = new WorldPoint(
			target.getX() - (objectSizeX + 2) / 2,
			target.getY() - (objectSizeY + 2) / 2,
			target.getPlane()
		);
		WorldArea objectArea = new WorldArea(objectSouthWest, objectSizeX + 2, objectSizeY + 2);

		return pathArea.intersectsWith2D(objectArea);
	}

	public static boolean canReach(WorldPoint target, int objectSizeX, int objectSizeY)
	{
		return canReach(target, objectSizeX, objectSizeY, 3, 3);
	}

	public static boolean canReach(WorldPoint target)
	{
		return canReach(target, 2, 2, 2, 2);
	}

	private static List<TileObject> near(WorldPoint anchor, int distance, Predicate<TileObject> predicate, TileObjectType... types)
	{
		LocalPoint anchorLocal = anchorLocal(anchor);
		if (anchorLocal == null)
		{
			return Collections.emptyList();
		}
		int maxDistance = Math.min(distance, Constants.SCENE_SIZE) * Perspective.LOCAL_TILE_SIZE;
		return Microbot.getRs2TileObjectCache().query()
			.fromWorldView()
			.ofType(types)
			.toList().stream()
			.map(Rs2TileObjectModel::getTileObject)
			.filter(o -> o.getLocalLocation().distanceTo(anchorLocal) <= maxDistance)
			.filter(predicate)
			.sorted(Comparator.comparingInt(o -> o.getLocalLocation().distanceTo(anchorLocal)))
			.collect(Collectors.toList());
	}

	private static LocalPoint anchorLocal(WorldPoint anchor)
	{
		if (anchor == null)
		{
			return null;
		}
		LocalPoint local = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), anchor);
		if (local != null)
		{
			return local;
		}
		Player player = Microbot.getClient().getLocalPlayer();
		return player != null ? player.getLocalLocation() : null;
	}
}
