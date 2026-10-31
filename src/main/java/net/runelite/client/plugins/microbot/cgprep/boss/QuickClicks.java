package net.runelite.client.plugins.microbot.cgprep.boss;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Random;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.util.Text;

/**
 * Builds the fight's menu entries and where on screen they are (one client-thread read each),
 * the same entries Microbot's Rs2Inventory / Rs2NpcModel / walker / Rs2Prayer build. They're then
 * sent either instantly ({@link #sendQuick}: Microbot.doInvokeQuick, the cursor jumps) or by
 * {@link HumanInput} (the cursor travels there and clicks a point inside the target).
 */
final class QuickClicks
{
	/** Inventory widget 149:0 (the param1 Rs2Inventory uses with no bank/shop open). */
	static final int INVENTORY = 0x950000;

	private static final Random RND = new Random();

	/** A menu entry plus where to click it. */
	static final class Target
	{
		NewMenuEntry entry;
		/** Clickable area (null: not on screen, e.g. its tab is closed). */
		Rectangle rect;
		/** Exact clickable shape when there is one (an NPC's hull). */
		Shape shape;
		/** A point that must be clicked exactly (a walk: the entry carries it). */
		java.awt.Point fixed;
	}

	private QuickClicks()
	{
	}

	/** An inventory item's action ("Eat", "Drink", "Wield") on the item in this slot. */
	static Target item(Client client, int slot, int itemId, String action)
	{
		if (slot < 0 || itemId <= 0)
		{
			return null;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget inv = client.getWidget(INVENTORY);
			Widget w = null;
			if (inv != null && inv.getChildren() != null)
			{
				for (Widget c : inv.getChildren())
				{
					if (c != null && c.getIndex() == slot)
					{
						w = c;
						break;
					}
				}
			}
			if (w != null && w.getItemId() != itemId)
			{
				return null; // the bag changed since the observation
			}
			String[] actions = w != null && w.getActions() != null ? w.getActions() : null;
			if (actions == null)
			{
				ItemComposition ic = client.getItemDefinition(itemId);
				actions = ic == null ? null : ic.getInventoryActions();
			}
			int idx = -1;
			if (actions != null)
			{
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null && Text.removeTags(actions[i]).equalsIgnoreCase(action))
					{
						idx = i;
						break;
					}
				}
			}
			if (idx < 0)
			{
				return null;
			}
			Target t = new Target();
			t.entry = new NewMenuEntry().option(action).param0(slot).param1(INVENTORY).opcode(MenuAction.CC_OP.getId())
				.identifier(idx + 1).itemId(itemId).target(w == null || w.getName() == null ? "" : w.getName());
			t.rect = w == null || w.isHidden() ? null : shrink(w.getBounds(), 3);
			return t;
		}).orElse(null);
	}

	/** A prayer's toggle (the entry Rs2Prayer sends). Its rect is null unless the prayer tab is open. */
	static Target prayer(Client client, Rs2PrayerEnum p)
	{
		if (p == null)
		{
			return null;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Target t = new Target();
			t.entry = new NewMenuEntry().param0(-1).param1(p.getIndex()).opcode(MenuAction.CC_OP.getId()).identifier(1).itemId(-1)
				.option("Activate").target("");
			Widget w = client.getWidget(p.getIndex());
			t.rect = w == null || w.isHidden() ? null : shrink(w.getBounds(), 4);
			return t;
		}).orElse(null);
	}

	/** "Walk here" on a point inside the tile, in the 3D view. Null if the tile isn't in view. */
	static Target walk(Client client, WorldPoint wp)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			if (client.getTopLevelWorldView() == null)
			{
				return null;
			}
			LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), wp);
			if (lp == null)
			{
				return null;
			}
			java.awt.Point pt = null;
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				Rectangle b = poly.getBounds();
				for (int i = 0; i < 12 && pt == null; i++)
				{
					int x = (int) Math.round(b.getCenterX() + RND.nextGaussian() * b.width / 7.0);
					int y = (int) Math.round(b.getCenterY() + RND.nextGaussian() * b.height / 7.0);
					if (poly.contains(x, y))
					{
						pt = new java.awt.Point(x, y);
					}
				}
			}
			if (pt == null)
			{
				Point c = Perspective.localToCanvas(client, lp, client.getTopLevelWorldView().getPlane());
				pt = c == null ? null : new java.awt.Point(c.getX(), c.getY());
			}
			if (pt == null || !Rs2UiHelper.isRectangleWithinViewport(new Rectangle(pt.x, pt.y, 1, 1)))
			{
				return null;
			}
			Target t = new Target();
			t.fixed = pt;
			t.rect = new Rectangle(pt.x, pt.y, 1, 1);
			t.entry = new NewMenuEntry().option("Walk here").target("").param0(pt.x).param1(pt.y)
				.type(MenuAction.WALK).identifier(0).itemId(0);
			return t;
		}).orElse(null);
	}

	/** "Attack" on the NPC with this index. */
	static Target attack(Client client, int npcIndex)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			if (client.getTopLevelWorldView() == null)
			{
				return null;
			}
			NPC n = null;
			for (NPC x : client.getTopLevelWorldView().npcs())
			{
				if (x != null && x.getIndex() == npcIndex)
				{
					n = x;
					break;
				}
			}
			if (n == null || n.isDead())
			{
				return null;
			}
			NPCComposition comp = n.getTransformedComposition();
			String[] actions = comp == null ? null : comp.getActions();
			int idx = -1;
			if (actions != null)
			{
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null && actions[i].equalsIgnoreCase("Attack"))
					{
						idx = i;
						break;
					}
				}
			}
			MenuAction[] ops = {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION,
				MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION};
			if (idx < 0 || idx >= ops.length)
			{
				return null;
			}
			Target t = new Target();
			t.entry = new NewMenuEntry().option("Attack").target(n.getName() == null ? "" : n.getName()).param0(0).param1(0)
				.opcode(ops[idx].getId()).identifier(npcIndex).itemId(-1).actor(n);
			t.shape = n.getConvexHull();
			Rectangle box = Rs2UiHelper.getActorClickbox(n);
			t.rect = box == null || Rs2UiHelper.isFallbackRectangle(box) ? null : Rs2UiHelper.clipToCanvas(box);
			return t;
		}).orElse(null);
	}

	/** Send it the instant way (cursor jumps). */
	static boolean sendQuick(Target t)
	{
		if (t == null)
		{
			return false;
		}
		Microbot.doInvokeQuick(t.entry, t.rect == null ? Rs2UiHelper.getDefaultRectangle() : t.rect);
		return true;
	}

	private static Rectangle shrink(Rectangle r, int by)
	{
		if (r == null || r.width <= 2 * by || r.height <= 2 * by)
		{
			return r;
		}
		return new Rectangle(r.x + by, r.y + by, r.width - 2 * by, r.height - 2 * by);
	}
}
