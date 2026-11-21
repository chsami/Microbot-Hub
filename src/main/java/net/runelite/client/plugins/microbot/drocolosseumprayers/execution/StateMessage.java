package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.State;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Messages.Worn;

final class StateMessage
{
	private static final Set<String> SAME_SECTIONS = Set.of("prayers", "equipment", "widgets");
	private static final String[] SKILLS = {"hitpoints", "prayer", "ranged", "magic", "attack", "strength", "defence"};

	private static final Gson TREE = new Gson();

	State state;

	private volatile Set<String> sections = Set.of();
	private volatile int[] area;
	private volatile int[] widgets = new int[0];

	private final Map<String, JsonElement> lastSent = new HashMap<>();
	private int[] lastCollision;
	private java.util.List<String> tickPrayers;

	synchronized void capturePrayers()
	{
		tickPrayers = state != null && state.loggedIn() ? state.activePrayers() : null;
	}

	synchronized void welcome(Set<String> sections, int[] area, int[] screen, int[] widgets)
	{
		this.sections = new java.util.HashSet<>(sections);
		this.sections.retainAll(Set.of("player", "npcs", "prayers", "events", "projectiles", "equipment", "collision", "widgets"));
		this.area = area;
		this.widgets = widgets;
		lastSent.clear();
		lastCollision = null;
	}

	synchronized void clear()
	{
		welcome(Set.of(), null, null, new int[0]);
	}

	synchronized JsonObject build(long tick, JsonArray events)
	{
		java.util.List<String> prayers = tickPrayers;
		tickPrayers = null;
		JsonObject s = new JsonObject();
		s.addProperty("type", "snapshot");
		s.addProperty("tick", tick);
		s.addProperty("t", System.currentTimeMillis());
		if (!state.loggedIn())
		{
			return s;
		}
		JsonArray same = new JsonArray();
		if (sections.contains("player"))
		{
			s.add("player", player());
		}
		if (sections.contains("npcs"))
		{
			state.beginNpcSnapshot(tick);
            state.observeNpcEvents(events);
			s.add("npcs", TREE.toJsonTree(state.npcs()));
		}
		if (sections.contains("prayers"))
		{
			section(s, same, "prayers", TREE.toJsonTree(prayers != null ? prayers : state.activePrayers()));
		}
		if (sections.contains("events"))
		{
			s.add("events", events);
		}
		if (sections.contains("projectiles"))
		{
			s.add("projectiles", TREE.toJsonTree(state.projectiles()));
		}
		if (sections.contains("equipment"))
		{
			section(s, same, "equipment", equipment());
		}
		if (sections.contains("widgets") && state != null)
		{
			JsonArray list = new JsonArray();
			for (int id : widgets)
			{
				if (state.widgetVisible(id))
				{
					JsonObject obj = new JsonObject();
					obj.addProperty("id", id);
					list.add(obj);
				}
			}
			section(s, same, "widgets", list);
		}
		if (sections.contains("collision"))
		{
			JsonObject c = collision();
			if (c != null)
			{
				s.add("collision", c);
			}
		}
		if (same.size() > 0)
		{
			s.add("same", same);
		}
		return s;
	}

    synchronized JsonObject buildFull(long tick, JsonArray events)
    {
        JsonObject snapshot = build(tick, events);
        if (!state.loggedIn())
        {
            return snapshot;
        }
        for (Map.Entry<String, JsonElement> entry : lastSent.entrySet())
        {
            if (sections.contains(entry.getKey()) && !snapshot.has(entry.getKey()))
            {
                snapshot.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        if (sections.contains("collision") && !snapshot.has("collision") && lastCollision != null && area != null)
        {
            JsonObject collision = new JsonObject();
            collision.addProperty("x1", area[0]);
            collision.addProperty("y1", area[1]);
            collision.addProperty("x2", area[2]);
            collision.addProperty("y2", area[3]);
            collision.add("flags", TREE.toJsonTree(lastCollision));
            snapshot.add("collision", collision);
        }
        snapshot.remove("same");
        return snapshot;
    }

	private JsonObject player()
	{
		JsonObject p = new JsonObject();
		p.addProperty("x", state.x());
		p.addProperty("y", state.y());
		p.addProperty("plane", state.plane());
		p.addProperty("tx", state.templateX());
		p.addProperty("ty", state.templateY());
		p.addProperty("hp", state.hp());
        p.add("modifiers", state.modifierLevels());
		p.addProperty("maxHp", state.maxHp());
		p.addProperty("healthRatio", state.healthRatio());
		p.addProperty("healthScale", state.healthScale());
		p.addProperty("prayer", state.prayer());
		p.addProperty("anim", state.animation());
		Object target = state.interacting();
		if (target instanceof Number)
		{
			p.addProperty("interacting", ((Number) target).intValue());
		}
		p.addProperty("moving", state.moving());
        JsonObject destination = state.destination();
        if (destination != null) p.add("destination", destination);
		p.addProperty("runOn", state.runOn());
		JsonObject boosted = new JsonObject();
		JsonObject real = new JsonObject();
		for (String skill : SKILLS)
		{
			boosted.addProperty(skill, state.skill(skill, true));
			real.addProperty(skill, state.skill(skill, false));
		}
		JsonObject skills = new JsonObject();
		skills.add("boosted", boosted);
		skills.add("real", real);
		p.add("skills", skills);
		p.addProperty("staminaActive", state.staminaActive());
		p.addProperty("prayerRegenTimer", state.prayerRegenTimer());
		JsonObject best = new JsonObject();
		best.add("range", text(state.bestRangePrayer()));
		best.add("magic", text(state.bestMagicPrayer()));
		best.add("melee", text(state.bestMeleePrayer()));
		p.add("bestOffensive", best);
		p.addProperty("instance", state.inInstance());
		p.addProperty("attackStyle", state.attackStyle());
		JsonObject clock = new JsonObject();
		clock.addProperty("minutes", state.clockMinutes());
		clock.addProperty("seconds", state.clockSeconds());
		p.add("clock", clock);
		return p;
	}

	private JsonArray equipment()
	{
		JsonArray list = new JsonArray();
		for (Worn w : state.equipment())
		{
            if (w.slot != 3) continue;
			JsonObject o = new JsonObject();
			o.addProperty("slot", w.slot);
			o.addProperty("id", w.id);
			o.add("name", text(w.name));
			if (w.gear != null) o.add("gear", TREE.toJsonTree(w.gear));
			list.add(o);
		}
		return list;
	}

	private void section(JsonObject s, JsonArray same, String key, JsonElement value)
	{
		if (SAME_SECTIONS.contains(key))
		{
			if (value.equals(lastSent.get(key)))
			{
				same.add(key);
				return;
			}
			lastSent.put(key, value);
		}
		s.add(key, value);
	}

	private JsonObject collision()
	{
		int[] r = area;
		if (r == null)
		{
			return null;
		}
		int width = r[2] - r[0] + 1;
		int height = r[3] - r[1] + 1;
		int[] flags = new int[width * height];
		for (int i = 0; i < flags.length; i++)
		{
			flags[i] = state.collisionFlags(r[0] + i % width, r[1] + i / width);
		}
		if (Arrays.equals(flags, lastCollision))
		{
			return null;
		}
		lastCollision = flags;
		JsonObject c = new JsonObject();
		c.addProperty("x1", r[0]);
		c.addProperty("y1", r[1]);
		c.addProperty("x2", r[2]);
		c.addProperty("y2", r[3]);
		c.add("flags", TREE.toJsonTree(flags));
		return c;
	}

	private static JsonElement text(String s)
	{
		return s == null ? JsonNull.INSTANCE : TREE.toJsonTree(s);
	}
}
