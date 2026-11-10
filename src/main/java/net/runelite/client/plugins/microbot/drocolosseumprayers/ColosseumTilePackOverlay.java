package net.runelite.client.plugins.microbot.drocolosseumprayers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Community Colosseum tile pack (78 Ground Markers tiles, region 7216; osrstools.net "Colosseum Tile Pack"), drawn in the
 * arena instance when "Show Colosseum tile pack" is on. Labels A/B mark the NW pillar's west face (1815,3115)/(1815,3114)
 * and the centre pillar's off-tick spots; 1/2 the west steps. Drawing only: the engine does not read these tiles.
 */
public final class ColosseumTilePackOverlay extends Overlay
{
	static final String RESOURCE = "colosseum-tile-pack.json";
	/** The arena's template region; monster tiles are drawn only there, not in other instances. */
	static final int COLOSSEUM_REGION = 7216;
	private static final Color UNCOLOURED = new Color(255, 255, 255, 90);
	private static final Stroke STROKE = new BasicStroke(1.5f);

	static final class Tile
	{
		final WorldPoint template;
		final Color color;
		final String label;

		Tile(WorldPoint template, Color color, String label)
		{
			this.template = template;
			this.color = color;
			this.label = label;
		}
	}

	private final Client client;
	private final DroColosseumConfig config;
	private final List<Tile> tiles;
	private int cachedTick = -1;
	private List<Object[]> cached = Collections.emptyList();

	@Inject
	ColosseumTilePackOverlay(Client client, DroColosseumConfig config)
	{
		this.client = client;
		this.config = config;
		this.tiles = load();
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	static List<Tile> load()
	{
		List<Tile> out = new ArrayList<>();
		try (InputStream in = ColosseumTilePackOverlay.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				return out;
			}
			JsonArray array = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement e : array)
			{
				JsonObject o = e.getAsJsonObject();
				WorldPoint wp = WorldPoint.fromRegion(o.get("regionId").getAsInt(), o.get("regionX").getAsInt(),
					o.get("regionY").getAsInt(), o.has("z") ? o.get("z").getAsInt() : 0);
				out.add(new Tile(wp, color(o.has("color") ? o.get("color").getAsString() : null),
					o.has("label") ? o.get("label").getAsString() : null));
			}
		}
		catch (Exception e)
		{
			return new ArrayList<>();
		}
		return out;
	}

	/** Ground Markers colours are #AARRGGBB; near-transparent pack tiles get a faint outline so they stay visible. */
	static Color color(String hex)
	{
		if (hex == null || !hex.startsWith("#") || hex.length() != 9)
		{
			return UNCOLOURED;
		}
		long argb = Long.parseLong(hex.substring(1), 16);
		int alpha = (int) (argb >>> 24) & 0xff;
		if (alpha < 0x20)
		{
			return UNCOLOURED;
		}
		return new Color((int) (argb >> 16) & 0xff, (int) (argb >> 8) & 0xff, (int) argb & 0xff, alpha);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showTilePack() || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || !wv.isInstance())
		{
			return null;
		}
		if (cachedTick != client.getTickCount())
		{
			List<Object[]> points = new ArrayList<>();
			for (Tile t : tiles)
			{
				for (WorldPoint wp : WorldPoint.toLocalInstance(wv, t.template))
				{
					points.add(new Object[]{wp, t});
				}
			}
			cached = points;
			cachedTick = client.getTickCount();
		}
		for (Object[] p : cached)
		{
			WorldPoint wp = (WorldPoint) p[0];
			Tile t = (Tile) p[1];
			LocalPoint lp = LocalPoint.fromWorld(wv, wp);
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, t.color, new Color(0, 0, 0, 30), STROKE);
			}
			if (t.label != null && !t.label.isEmpty())
			{
				Point text = Perspective.getCanvasTextLocation(client, graphics, lp, t.label, 0);
				if (text != null)
				{
					OverlayUtil.renderTextLocation(graphics, text, t.label, t.color);
				}
			}
		}
		// Monster true tiles on top of the pack (user, 2026-11-07), only inside the Colosseum's own region.
		LocalPoint me = client.getLocalPlayer().getLocalLocation();
		if (me != null && WorldPoint.fromLocalInstance(client, me).getRegionID() == COLOSSEUM_REGION)
		{
			ColosseumNpcTiles.render(graphics, client, wv);
		}
		return null;
	}
}
