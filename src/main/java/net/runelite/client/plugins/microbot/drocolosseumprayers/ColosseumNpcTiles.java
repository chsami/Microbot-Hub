package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

/**
 * Colosseum monster true tiles, drawn with the community tile pack when "Show Colosseum tile pack" is on (user,
 * 2026-11-07: the Colosseum guide videos run Better NPC Highlight true tiles beside the tile pack). The true tile is
 * the server-side footprint, the same tile the engine reasons about, not the interpolated model position. The drawing
 * technique (south-west world tile shifted to the footprint centre, then a size-wide canvas area) follows Better NPC
 * Highlight's "True Tile" mode (BSD-2-Clause, MoreBuchus/riktenx). Colours follow the attack style to pray against:
 * green ranged, blue magic, red melee, orange for the Manticore's three styles, yellow for Sol and the Bees.
 * Drawing only: the engine does not read this.
 */
final class ColosseumNpcTiles
{
	static final Color RANGED = new Color(0, 220, 70);
	static final Color MAGIC = new Color(40, 150, 255);
	static final Color MELEE = new Color(235, 40, 40);
	static final Color MIXED = new Color(255, 150, 0);
	static final Color BOSS = new Color(255, 225, 0);
	private static final int FILL_ALPHA = 35;
	private static final Stroke STROKE = new BasicStroke(2f);

	private ColosseumNpcTiles()
	{
	}

	/** Outline colour for a Colosseum monster id, or null for non-combatants (Minimus, pillars, flares, spectators). */
	static Color colour(int id)
	{
		switch (id)
		{
			case 12814: // Fremennik warband archer
			case 12817: // Javelin Colossus
				return RANGED;
			case 12811: // Serpent shaman
			case 12815: // Fremennik warband seer
			case 12819: // Shockwave Colossus
				return MAGIC;
			case 12810: // Jaguar warrior
			case 12812: // Minotaur
			case 12813: // Minotaur (Red Flag)
			case 12816: // Fremennik warband berserker
				return MELEE;
			case 12818: // Manticore
				return MIXED;
			case 12821: // Sol Heredit
			case 12823: // Angry Bees
				return BOSS;
			default:
				return null;
		}
	}

	/** Draws every live Colosseum monster's true tile in the top-level instance. Client thread (overlay render). */
	static void render(Graphics2D graphics, Client client, WorldView wv)
	{
		Object antialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setStroke(STROKE);
		for (Rs2NpcModel model : Microbot.getRs2NpcCache().query().toList())
		{
			NPC npc = model.getNpc();
			if (npc == null || npc.isDead() || npc.getWorldView() != wv)
			{
				continue;
			}
			Color colour = colour(npc.getId());
			NPCComposition composition = npc.getTransformedComposition();
			if (colour == null || composition == null)
			{
				continue;
			}
			Polygon poly = trueTile(client, wv, npc.getWorldLocation(), Math.max(1, composition.getSize()));
			if (poly == null)
			{
				continue;
			}
			graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), FILL_ALPHA));
			graphics.fill(poly);
			graphics.setColor(colour);
			graphics.draw(poly);
		}
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : antialias);
	}

	/** Canvas polygon of a size-by-size footprint whose south-west tile is {@code southWest}; null off-scene. */
	static Polygon trueTile(Client client, WorldView wv, WorldPoint southWest, int size)
	{
		if (southWest == null)
		{
			return null;
		}
		LocalPoint lp = LocalPoint.fromWorld(wv, southWest);
		if (lp == null)
		{
			return null;
		}
		LocalPoint centre = new LocalPoint(lp.getX() + size * Perspective.LOCAL_TILE_SIZE / 2 - Perspective.LOCAL_HALF_TILE_SIZE,
			lp.getY() + size * Perspective.LOCAL_TILE_SIZE / 2 - Perspective.LOCAL_HALF_TILE_SIZE, wv);
		return Perspective.getCanvasTileAreaPoly(client, centre, size);
	}
}
