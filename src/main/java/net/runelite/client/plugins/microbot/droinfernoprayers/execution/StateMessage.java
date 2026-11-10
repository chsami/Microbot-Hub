package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.State;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno.InfernoState;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.GroundItem;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.Worn;

/**
 * Builds the local decision snapshot every game tick from the common capture and Inferno extras.
 * The session explicitly selects the required capture sections.
 */
final class StateMessage
{
	/** Sections recorded only when changed; the local worker reconstructs entries named in "same". */
	private static final Set<String> SAME_SECTIONS =
		Set.of("prayers", "inventory", "equipment", "ground", "camera", "widgets", "dialogue");
	private static final String[] SKILLS = {"hitpoints", "prayer", "ranged", "magic", "attack", "strength", "defence"};
	/** Objects to JSON trees: null fields left out. */
	private static final Gson TREE = new Gson();

	State state;
	InfernoState infernoState;
    net.runelite.client.plugins.microbot.droinfernoprayers.ColosseumUi colosseumUi;

	// Sections selected by the local session.
	private volatile Set<String> sections = Set.of();
	/** The collision section's area and the on-screen mask's area, {x1, y1, x2, y2}, or null. */
	private volatile int[] area;
	private volatile int[] screen;
	/** Widgets the widgets section reports (packed ids). */
	private volatile int[] widgets = new int[0];

	private final Map<String, JsonElement> lastSent = new HashMap<>();
	private int[] lastCollision;

	/** Configure capture sections from now on, every section in full on the next tick. */
	synchronized void welcome(Set<String> sections, int[] area, int[] screen, int[] widgets)
	{
		this.sections = new java.util.HashSet<>(sections);
		this.sections.retainAll(Set.of("player", "npcs", "prayers", "inventory", "events", "projectiles", "equipment", "ground", "camera", "collision"));
        if(colosseumUi!=null){
            if(sections.contains("widgets"))this.sections.add("widgets");
            if(sections.contains("dialogue"))this.sections.add("dialogue");
        }
		this.area = area;
		this.screen = screen;
		this.widgets = widgets;
		lastSent.clear();
		lastCollision = null;
	}

	/** Stopped: capture nothing. */
	synchronized void clear()
	{
		welcome(Set.of(), null, null, new int[0]);
	}

	synchronized JsonObject build(long tick, JsonArray events)
	{
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
			state.observeNpcEvents(events);
			state.beginNpcSnapshot(tick);
			s.add("npcs", TREE.toJsonTree(state.npcs()));
		}
		if (sections.contains("prayers"))
		{
			section(s, same, "prayers", TREE.toJsonTree(state.activePrayers()));
		}
		if (sections.contains("inventory"))
		{
			section(s, same, "inventory", TREE.toJsonTree(state.inventory()));
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
		if (sections.contains("ground") && infernoState != null)
		{
			section(s, same, "ground", ground());
		}
        if(sections.contains("widgets")&&colosseumUi!=null){
            colosseumUi.beginSnapshot();JsonArray list=new JsonArray();
            for(int id:widgets){var widget=colosseumUi.widget(id);if(widget!=null)list.add(TREE.toJsonTree(widget));}
            section(s,same,"widgets",list);
        }
        if(sections.contains("dialogue")&&colosseumUi!=null)
            section(s,same,"dialogue",TREE.toJsonTree(colosseumUi.dialogue()));
		if (sections.contains("camera"))
		{
			section(s, same, "camera", camera());
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

    /**
     * Local decisions may skip an obsolete tick, so every queued snapshot must be self-contained.
     * Remote products keep the original bandwidth-saving delta contract in build().
     */
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
		p.addProperty("spellSelected", state.spellSelected());
		p.addProperty("moving", state.moving());
		p.addProperty("runEnergy", state.runEnergy());
		p.addProperty("runOn", state.runOn());
		p.addProperty("specEnergy", state.specEnergy());
		p.addProperty("specOn", state.specOn());
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
			JsonObject o = new JsonObject();
			o.addProperty("slot", w.slot);
			o.addProperty("id", w.id);
			o.add("name", text(w.name));
			if (w.gear != null) o.add("gear", TREE.toJsonTree(w.gear));
			list.add(o);
		}
		return list;
	}

	private JsonArray ground()
	{
		JsonArray list = new JsonArray();
		for (GroundItem g : infernoState.groundItems())
		{
			JsonObject o = new JsonObject();
			o.addProperty("id", g.id);
			o.add("name", text(g.name));
			o.addProperty("qty", g.qty);
			o.addProperty("tx", g.tx);
			o.addProperty("ty", g.ty);
			list.add(o);
		}
		return list;
	}

	private JsonObject camera()
	{
		JsonObject c = new JsonObject();
		c.addProperty("yaw", state.cameraYaw());
		c.addProperty("pitch", state.cameraPitch());
		c.addProperty("zoom", state.cameraZoom());
		String mask = onScreen();
		if (mask != null)
		{
			c.addProperty("onScreen", mask);
		}
		return c;
	}

	/** Add a section, or name it in `same` when it is unchanged since last sent. */
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

	/** The screen area as a bitmask: bit i = tile (x1 + i % width, y1 + i / width), LSB first per byte. */
	private String onScreen()
	{
		int[] r = screen;
		if (r == null)
		{
			return null;
		}
		int width = r[2] - r[0] + 1;
		int height = r[3] - r[1] + 1;
		byte[] bits = new byte[(width * height + 7) / 8];
		for (int i = 0; i < width * height; i++)
		{
			if (state.tileOnScreen(r[0] + i % width, r[1] + i / width))
			{
				bits[i >> 3] |= (byte) (1 << (i & 7));
			}
		}
		return Base64.getEncoder().encodeToString(bits);
	}

	/** The collision area, row-major from (x1, y1), or null when unchanged since last sent. */
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
