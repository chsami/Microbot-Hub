package net.runelite.client.plugins.microbot.cgprep.actions;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;

/**
 * Get a target on screen BEFORE clicking it.
 *
 * Every Microbot entity click (NPC, object, ground item) turns the camera itself when the target
 * is off screen, by HOLDING the arrow keys until the angle is reached (up to 5 s per axis). Key
 * presses don't reach an unfocused client, so 0.3.23 sat for 23 s on a Dark Beast loot pile until
 * the user tabbed back in. Here the camera is turned with a middle-mouse drag instead (mouse events
 * reach the canvas without focus; HumanCamera, 1.2.0: it used to be snapped to the new angle);
 * when the target is still not on screen, the caller walks towards it rather than clicking.
 */
public final class Aim
{
	private Aim()
	{
	}

	/**
	 * Use an inventory item on THIS object. Rs2Inventory.useItemOnObject(item, objectId) picks
	 * "the nearest object with that id" itself (0.3.25: a vial fill at the fishing spot sat 5 s
	 * with no reaction before working on the retry).
	 */
	public static boolean useItemOn(int itemId, net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel obj)
	{
		lastFail = null;
		if (obj == null)
		{
			lastFail = "object not found";
			return false;
		}
		if (!clickable(obj))
		{
			lastFail = "not on screen (or behind an interface)";
			return false;
		}
		// An item left selected by an earlier try: using it again would just deselect it (1.6.0).
		if (net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isItemSelected()
			&& net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.getSelectedItemId() != itemId)
		{
			net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.deselect();
			sleepUntil(() -> !net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isItemSelected(), 600);
		}
		if (!(net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isItemSelected()
			&& net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.getSelectedItemId() == itemId))
		{
			if (!net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.use(itemId))
			{
				lastFail = "item " + itemId + " not usable (not in the bag?)";
				return false;
			}
			if (!sleepUntil(net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory::isItemSelected, 600))
			{
				lastFail = "item didn't get selected";
				return false;
			}
		}
		// With an item selected the model's click sends "Use <item> -> object" (WIDGET_TARGET_ON_GAME_OBJECT).
		// (1.7.3: replaces the retired game-object interact helper, which the Hub build test rejects.)
		if (!obj.click())
		{
			lastFail = "the click on the object didn't go out";
			return false;
		}
		return true;
	}

	/** Why the last {@link #useItemOn} returned false (for the log). */
	public static volatile String lastFail;

	/**
	 * 1.8.1: an object that can be clicked right now: on screen AND not hidden behind the chatbox,
	 * side panel or minimap. Microbot's object click turns the camera itself in that case, holding
	 * an arrow key until it faces the object within 5 degrees; at its turning speed the camera can
	 * pass that window between checks and spin (user, 1.8.0). Here the camera is turned to face it
	 * our way instead; false = still covered (the caller walks closer).
	 */
	public static boolean clickable(net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel obj)
	{
		if (obj == null || !onScreen(obj.getLocalLocation()))
		{
			return false;
		}
		if (clear(obj))
		{
			return true;
		}
		try
		{
			HumanCamera.get().face(obj.getLocalLocation());
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
		catch (Exception e)
		{
			return false;
		}
		return clear(obj);
	}

	/** The object's clickbox has a part clear of the interfaces (Microbot's own test before clicking). */
	static boolean clear(net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel obj)
	{
		try
		{
			java.awt.Rectangle box = net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.getObjectClickbox(obj.getTileObject());
			if (net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.isFallbackRectangle(box))
			{
				return true; // no clickbox known: Microbot clicks it without turning either
			}
			java.awt.Rectangle vp = net.runelite.client.plugins.microbot.Microbot.getClientThread()
				.runOnClientThreadOptional(net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper::sceneViewportBounds).orElse(null);
			return net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.clearOfInterfaces(box, vp,
				net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.visibleSceneOverlayBounds()) != null;
		}
		catch (Exception e)
		{
			return true;
		}
	}

	/** True when the tile is (now) on screen; never holds a key. */
	public static boolean onScreen(LocalPoint lp)
	{
		if (lp == null)
		{
			return false;
		}
		try
		{
			if (Rs2Camera.isTileOnScreen(lp))
			{
				return true;
			}
			// Turned like a player turns it (1.2.0): a middle-mouse drag, see HumanCamera.
			return HumanCamera.get().bringOnScreen(lp);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
		catch (Exception e)
		{
			return false;
		}
	}
}
