package net.runelite.client.plugins.microbot.theatre.Nylocas;

import lombok.Getter;
import net.runelite.api.Point;

import java.util.HashMap;

enum NylocasSpawnPoint
{
	WEST_NORTH(new Point(17, 25)),
	WEST_SOUTH(new Point(17, 24)),
	SOUTH_WEST(new Point(31, 9)),
	SOUTH_EAST(new Point(32, 9)),
	EAST_SOUTH(new Point(46, 24)),
	EAST_NORTH(new Point(46, 25)),
	EAST_BIG(new Point(47, 25)),
	WEST_BIG(new Point(18, 25)),
	SOUTH_BIG(new Point(32, 10));

	@Getter
	private Point point;

	@Getter
	private static final HashMap<Point, NylocasSpawnPoint> lookupMap;

	static
	{
		lookupMap = new HashMap<>();

		for (NylocasSpawnPoint v : NylocasSpawnPoint.values())
		{
			lookupMap.put(v.getPoint(), v);
		}
	}

	NylocasSpawnPoint(Point point)
	{
		this.point = point;
	}
}
