package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * What happened between two game ticks, in the order it happened. Call these from your client's event hooks (see
 * Main); the connection sends them with the next tick's state. Only NPCs and you count, never other players.
 *
 * actor = Messages.PLAYER for you, or the NPC's index.
 */
public final class Events
{
	private JsonArray pending = new JsonArray();
	private volatile boolean collecting;

	/** An NPC appeared. */
	public void npcSpawned(int index)
	{
		JsonObject o = event("spawn");
		o.addProperty("index", index);
		push(o);
	}

	/** An NPC went away. */
	public void npcDespawned(int index)
	{
		JsonObject o = event("despawn");
		o.addProperty("index", index);
		push(o);
	}

	/** An actor's animation changed. */
	public void animation(Object actor, int anim)
	{
		JsonObject o = event("anim");
		actor(o, actor);
		o.addProperty("anim", anim);
		push(o);
	}

	/** An actor's spot animations changed: every spot animation id on it now. */
	public void graphic(Object actor, int[] spotAnims)
	{
		JsonObject o = event("graphic");
		actor(o, actor);
		JsonArray ids = new JsonArray();
		for (int id : spotAnims)
		{
			ids.add(id);
		}
		o.add("spotAnims", ids);
		push(o);
	}

	/** A hitsplat landed on an actor. type = the hitsplat type id. */
	public void hitsplat(Object actor, int type, int amount)
	{
		JsonObject o = event("hitsplat");
		actor(o, actor);
		o.addProperty("type", type);
		o.addProperty("amount", amount);
		push(o);
	}

	/** A game message in the chatbox (types GAMEMESSAGE and SPAM only), colour tags removed. */
	public void chat(String type, String text)
	{
		JsonObject o = event("chat");
		o.addProperty("type", type);
		o.addProperty("text", text);
		push(o);
	}

	/**
	 * A graphics object appeared on a tile (any id). Template tile; ticksLeft = max(1, (startCycle - gameCycle + 29) / 30).
	 */
	public void graphicsObject(int id, int tx, int ty, int ticksLeft)
	{
		JsonObject o = event("gfxobj");
		o.addProperty("id", id);
		o.addProperty("tx", tx);
		o.addProperty("ty", ty);
		o.addProperty("ticksLeft", ticksLeft);
		push(o);
	}

	// ---- used by Connection ----

	void collect(boolean on)
	{
		collecting = on;
		if (!on)
		{
			drain();
		}
	}

	synchronized JsonArray drain()
	{
		JsonArray out = pending;
		pending = new JsonArray();
		return out;
	}

	private static JsonObject event(String kind)
	{
		JsonObject o = new JsonObject();
		o.addProperty("e", kind);
		return o;
	}

	/** Queued only while connected, so nothing piles up. */
	private synchronized void push(JsonObject o)
	{
		if (collecting && pending.size() < 512)
		{
			pending.add(o);
		}
	}

	private static void actor(JsonObject o, Object actor)
	{
		if (actor instanceof Number)
		{
			o.addProperty("actor", ((Number) actor).intValue());
		}
		else
		{
			o.addProperty("actor", Messages.PLAYER);
		}
	}
}
