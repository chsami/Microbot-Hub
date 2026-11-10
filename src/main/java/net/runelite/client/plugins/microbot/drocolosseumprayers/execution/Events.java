package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class Events
{
	private JsonArray pending = new JsonArray();
	private volatile boolean collecting;

	public void npcSpawned(int index)
	{
		JsonObject o = event("spawn");
		o.addProperty("index", index);
		push(o);
	}

	public void npcDespawned(int index)
	{
		JsonObject o = event("despawn");
		o.addProperty("index", index);
		push(o);
	}

	public void animation(Object actor, int anim)
	{
		JsonObject o = event("anim");
		actor(o, actor);
		o.addProperty("anim", anim);
		push(o);
	}

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

	public void hitsplat(Object actor, int type, int amount)
	{
		JsonObject o = event("hitsplat");
		actor(o, actor);
		o.addProperty("type", type);
		o.addProperty("amount", amount);
		push(o);
	}

	public void chat(String type, String text)
	{
		JsonObject o = event("chat");
		o.addProperty("type", type);
		o.addProperty("text", text);
		push(o);
	}

	public void graphicsObject(int id, int tx, int ty, int ticksLeft)
	{
		JsonObject o = event("gfxobj");
		o.addProperty("id", id);
		o.addProperty("tx", tx);
		o.addProperty("ty", ty);
		o.addProperty("ticksLeft", ticksLeft);
		push(o);
	}

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
