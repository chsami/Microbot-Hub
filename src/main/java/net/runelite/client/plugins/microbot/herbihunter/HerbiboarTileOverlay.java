/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.herbihunter;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ColorUtil;

import java.awt.*;
import java.util.List;

public class HerbiboarTileOverlay extends Overlay
{
	private final HerbiHunterPlugin plugin;
	private final HerbiboarConfig config;
	private final Client client;

	@Inject
	public HerbiboarTileOverlay(HerbiHunterPlugin plugin, HerbiboarConfig config, Client client)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.plugin = plugin;
		this.config = config;
		this.client = client;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInHerbiboarArea())
		{
			return null;
		}

		HerbiboarSearchSpot.Group currentGroup = plugin.getCurrentGroup();
		int finishId = plugin.getFinishId();

		if (config.isStartShown() && (currentGroup == null && finishId == 0))
		{
			plugin.getStarts().values().forEach((obj) -> OverlayUtil.renderTileOverlay(graphics, obj, "", config.getStartColor()));
		}

		if (config.isRouteShown())
		{
			drawRoute(graphics, plugin.getWalkRoute());
		}

		// A just-started trail has a group but no spot yet, and getLast throws on an empty path.
		if (config.isObjectShown() && !(finishId > 0 || currentGroup == null) && !plugin.getCurrentPath().isEmpty())
		{
			WorldPoint correct = Iterables.getLast(plugin.getCurrentPath()).getLocation();
			TileObject object = plugin.getTrailObjects().get(correct);
			drawObjectLocation(graphics, object, config.getObjectColor());
		}

		if (config.isTunnelShown() && HerbiboarScript.validFinishId(finishId, plugin.getEndLocations().size()))
		{
			WorldPoint finishLoc = plugin.getEndLocations().get(finishId - 1);
			TileObject object = plugin.getTunnels().get(finishLoc);
			drawObjectLocation(graphics, object, config.getTunnelColor());
		}

		return null;
	}

	private void drawRoute(Graphics2D graphics, List<WorldPoint> route)
	{
		WorldPoint me = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
		int from = HerbiHunterPlugin.routeAheadFrom(route, me);
		if (from < 0)
		{
			return;
		}
		graphics.setColor(config.getRouteColor());
		graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Point last = null;
		WorldPoint lastTile = null;
		for (int i = from; i < route.size(); i++)
		{
			WorldPoint tile = route.get(i);
			LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
			Point p = lp == null ? null : Perspective.localToCanvas(client, lp, tile.getPlane());
			// A hop (the hole) is a climb, not a walk: no line across it.
			if (p != null && last != null && lastTile.distanceTo(tile) <= 2)
			{
				graphics.drawLine(last.getX(), last.getY(), p.getX(), p.getY());
			}
			last = p;
			lastTile = tile;
		}
		WorldPoint end = route.get(route.size() - 1);
		LocalPoint endLp = LocalPoint.fromWorld(client.getTopLevelWorldView(), end);
		Polygon poly = endLp == null ? null : Perspective.getCanvasTilePoly(client, endLp);
		if (poly != null)
		{
			graphics.draw(poly);
		}
	}

	private void drawObjectLocation(Graphics2D graphics, TileObject object, Color color)
	{
		if (object == null)
		{
			return;
		}

		if (config.showClickBoxes())
		{
			Shape clickbox = object.getClickbox();
			if (clickbox != null)
			{
				Color clickBoxColor = ColorUtil.colorWithAlpha(color, color.getAlpha() / 12);

				graphics.setColor(color);
				graphics.draw(clickbox);
				graphics.setColor(clickBoxColor);
				graphics.fill(clickbox);
			}
		}
		else
		{
			OverlayUtil.renderTileOverlay(graphics, object, "", color);
		}
	}
}
