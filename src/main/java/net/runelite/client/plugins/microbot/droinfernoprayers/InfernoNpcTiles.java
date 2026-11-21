package net.runelite.client.plugins.microbot.droinfernoprayers;

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
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

final class InfernoNpcTiles
{
	static final Color RANGED = new Color(0, 220, 70);
	static final Color MAGIC = new Color(40, 150, 255);
	static final Color MELEE = new Color(235, 40, 40);
	static final Color MIXED = new Color(255, 150, 0);
	static final Color BOSS = new Color(255, 225, 0);
	static final Color SHIELD = new Color(240, 240, 240);
	private static final int FILL_ALPHA = 35;
	private static final Stroke STROKE = new BasicStroke(2f);

	private InfernoNpcTiles()
	{
	}

	static Color colour(int id)
	{
		switch (id)
		{
			case NpcID.INFERNO_CREATURE_HARPIE:
			case NpcID.INFERNO_CREATURE_SPLITTER_RANGE:
			case NpcID.INFERNO_CREATURE_RANGER:
			case NpcID.INFERNO_RANGER_FINALWAVE:
				return RANGED;
			case NpcID.INFERNO_CREATURE_SPLITTER_MAGE:
			case NpcID.INFERNO_CREATURE_MAGER:
			case NpcID.INFERNO_MAGER_FINALWAVE:
				return MAGIC;
			case NpcID.INFERNO_NIBBLER:
			case NpcID.INFERNO_CREATURE_SPLITTER_MELEE:
			case NpcID.INFERNO_CREATURE_MELEE:
			case NpcID.INFERNO_JAD_HEALER:
			case NpcID.INFERNO_JAD_HEALER_FINALWAVE:
				return MELEE;
			case NpcID.INFERNO_CREATURE_SPLITTER:
			case NpcID.INFERNO_JAD:
			case NpcID.INFERNO_JAD_FINALWAVE:
				return MIXED;
			case NpcID.INFERNO_TZKALZUK_PLACEHOLDER:
			case NpcID.INFERNO_ZUK_HEALER:
				return BOSS;
			case NpcID.INFERNO_MOVING_SAFESPOT:
				return SHIELD;
			default:
				return null;
		}
	}

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
