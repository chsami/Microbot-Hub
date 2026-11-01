package net.runelite.client.plugins.microbot.cgprep.boss;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Scene overlay for the boss fight: the tile the brain wants next (cyan), floor tiles that are
 * orange now (red) or warning (yellow), so a dry run can be compared with what you'd do yourself.
 */
public final class BossFightOverlay extends Overlay
{
	private static final Color WARN = new Color(255, 200, 0, 140);
	private static final Color HIT = new Color(255, 40, 40, 90);
	private static final Color MOVE = new Color(0, 230, 255, 220);

	private final Client client;
	private final Supplier<BossFight> fight;
	private final Supplier<Boolean> enabled;

	public BossFightOverlay(Client client, Supplier<BossFight> fight, Supplier<Boolean> enabled)
	{
		this.client = client;
		this.fight = fight;
		this.enabled = enabled;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		BossFight f = fight.get();
		if (f == null || !f.active || !Boolean.TRUE.equals(enabled.get()))
		{
			return null;
		}
		FightObs o = f.lastObs;
		FightPlan p = f.lastPlan;
		if (o == null)
		{
			return null;
		}
		for (int i = 0; i < Arena.TILES; i++)
		{
			byte b = o.floor[i];
			if (b == FightObs.FLOOR_HIT)
			{
				fill(g, f, Arena.x(i), Arena.y(i), HIT);
			}
			else if (b == FightObs.FLOOR_WARN)
			{
				outline(g, f, Arena.x(i), Arena.y(i), WARN, 1f);
			}
		}
		if (p != null && p.moving())
		{
			outline(g, f, p.moveX, p.moveY, MOVE, 2.5f);
		}
		return null;
	}

	private Polygon poly(BossFight f, int x, int y)
	{
		if (client.getTopLevelWorldView() == null)
		{
			return null;
		}
		LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), new WorldPoint(f.originX + x, f.originY + y, f.plane));
		return lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
	}

	private void fill(Graphics2D g, BossFight f, int x, int y, Color c)
	{
		Polygon p = poly(f, x, y);
		if (p != null)
		{
			g.setColor(c);
			g.fill(p);
		}
	}

	private void outline(Graphics2D g, BossFight f, int x, int y, Color c, float w)
	{
		Polygon p = poly(f, x, y);
		if (p != null)
		{
			g.setColor(c);
			g.setStroke(new BasicStroke(w));
			g.draw(p);
		}
	}
}
