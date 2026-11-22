/*
 * Copyright (c) 2018, Kamiel
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

import com.google.inject.Inject;

import java.awt.*;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class HerbiboarMinimapOverlay extends Overlay
{
	private final HerbiHunterPlugin plugin;
	private final HerbiboarConfig config;
	private final Client client;

	@Inject
	public HerbiboarMinimapOverlay(HerbiHunterPlugin plugin, HerbiboarConfig config, Client client)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
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
		if (config.isRouteShown())
		{
			drawRoute(graphics, plugin.getWalkRoute());
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
		graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Point last = null;
		WorldPoint lastTile = null;
		for (int i = from; i < route.size(); i++)
		{
			WorldPoint tile = route.get(i);
			LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
			Point p = lp == null ? null : Perspective.localToMinimap(client, lp);
			if (p != null && last != null && lastTile.distanceTo(tile) <= 2)
			{
				graphics.drawLine(last.getX(), last.getY(), p.getX(), p.getY());
			}
			last = p;
			lastTile = tile;
		}
	}
}
