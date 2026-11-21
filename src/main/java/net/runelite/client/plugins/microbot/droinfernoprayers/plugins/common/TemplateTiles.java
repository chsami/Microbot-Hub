package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public final class TemplateTiles
{
	private static final int RETRY_TICKS = 5;

	private final Client client;

	private Map<Long, int[]> scene = new HashMap<>();
	private int baseX = Integer.MIN_VALUE;
	private int baseY = Integer.MIN_VALUE;
	private int plane = -1;
	private int sinceEmpty;

	public TemplateTiles(Client client)
	{
		this.client = client;
	}

	public int[] scene(int tx, int ty)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return null;
		}
		if (wv.getBaseX() != baseX || wv.getBaseY() != baseY || wv.getPlane() != plane
			|| (scene.isEmpty() && ++sinceEmpty % RETRY_TICKS == 0))
		{
			map(wv);
		}
		return scene.get(key(tx, ty));
	}

	private void map(WorldView wv)
	{
		Map<Long, int[]> next = new HashMap<>();
		for (int sx = 0; sx < wv.getSizeX(); sx++)
		{
			for (int sy = 0; sy < wv.getSizeY(); sy++)
			{
				WorldPoint t = WorldPoint.fromLocalInstance(client, LocalPoint.fromScene(sx, sy, wv), wv.getPlane());
				if (t != null)
				{
					next.putIfAbsent(key(t.getX(), t.getY()), new int[]{sx, sy});
				}
			}
		}
		scene = next;
		baseX = wv.getBaseX();
		baseY = wv.getBaseY();
		plane = wv.getPlane();
	}

	private static long key(int x, int y)
	{
		return ((long) x << 32) | (y & 0xffffffffL);
	}
}
