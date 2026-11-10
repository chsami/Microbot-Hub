package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.Npc;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.Projectile;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.Worn;

/**
 * Game state for the local controller, read once per game tick on the client thread.
 *
 * Coordinates and "actor" values: see server/Messages.java.
 */
public class State
{
	private static final Skill[] SKILLS = {
		Skill.HITPOINTS, Skill.PRAYER, Skill.RANGED, Skill.MAGIC, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
	};
	/** Normalize RuneLite camera angles from JAU14 (0-16383) to 0-2047. */
	private static final int ANGLE_SCALE = 8;
	private static final int CYCLES_PER_TICK = 30;

	protected final Client client;
	protected final TemplateTiles tiles;
	private final NpcSpawnDiagnostics npcDiagnostics;
    private com.google.gson.JsonArray npcEvents = new com.google.gson.JsonArray();

	public State(Client client)
	{
		this(client, null);
	}

	/** Retained local Inferno entry point; all arenas now capture fresh top-level NPCs. */
	public static State inferno(Client client, NpcSpawnDiagnostics npcDiagnostics)
	{
		return new State(client, npcDiagnostics);
	}

	public State(Client client, NpcSpawnDiagnostics npcDiagnostics)
	{
		this.client = client;
		this.tiles = new TemplateTiles(client);
		this.npcDiagnostics = npcDiagnostics;
	}

	private Player me()
	{
		return client.getLocalPlayer();
	}

	// ---- Login ----

	public boolean loggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN && me() != null;
	}

	public String playerName()
	{
		Player me = me();
		return me == null || me.getName() == null ? null : Text.toJagexName(me.getName());
	}

	// ---- Player ----

	public int x()
	{
		return me().getWorldLocation().getX();
	}

	public int y()
	{
		return me().getWorldLocation().getY();
	}

	public int plane()
	{
		return me().getWorldLocation().getPlane();
	}

	public int templateX()
	{
		// True (server) tile, not the animated local position, which lags while walking.
		WorldPoint wp = me().getWorldLocation();
		return template(LocalPoint.fromWorld(client.getTopLevelWorldView(), wp), wp).getX();
	}

	public int templateY()
	{
		// True (server) tile, not the animated local position, which lags while walking.
		WorldPoint wp = me().getWorldLocation();
		return template(LocalPoint.fromWorld(client.getTopLevelWorldView(), wp), wp).getY();
	}

	public int hp()
	{
		return client.getBoostedSkillLevel(Skill.HITPOINTS);
	}

	public int maxHp()
	{
		return client.getRealSkillLevel(Skill.HITPOINTS);
	}

	public int healthRatio()
	{
		return me().getHealthRatio();
	}

	public int healthScale()
	{
		return me().getHealthScale();
	}

	public int prayer()
	{
		return client.getBoostedSkillLevel(Skill.PRAYER);
	}

	public int animation()
	{
		return me().getAnimation();
	}

	public Object interacting()
	{
		Actor target = me().getInteracting();
		return target instanceof NPC ? ((NPC) target).getIndex() : null;
	}

	public boolean spellSelected()
	{
		return client.isWidgetSelected();
	}

	public boolean moving()
	{
		return me().getPoseAnimation() != me().getIdlePoseAnimation();
	}

	public int runEnergy()
	{
		return client.getEnergy();
	}

	public boolean runOn()
	{
		return client.getVarpValue(VarPlayerID.OPTION_RUN) == 1;
	}

	public int specEnergy()
	{
		return client.getVarpValue(VarPlayerID.SA_ENERGY);
	}

	public boolean specOn()
	{
		return client.getVarpValue(VarPlayerID.SA_ATTACK) == 1;
	}

	public int skill(String skill, boolean boosted)
	{
		for (Skill s : SKILLS)
		{
			if (s.getName().equalsIgnoreCase(skill))
			{
				return boosted ? client.getBoostedSkillLevel(s) : client.getRealSkillLevel(s);
			}
		}
		return 0;
	}

	public boolean staminaActive()
	{
		return client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
	}

	public int prayerRegenTimer()
	{
		return client.getVarbitValue(VarbitID.PRAYER_REGENERATION_POTION_TIMER);
	}

	public String bestRangePrayer()
	{
		int prayer = client.getRealSkillLevel(Skill.PRAYER);
		int defence = client.getRealSkillLevel(Skill.DEFENCE);
		if (client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) != 0 && prayer >= 74 && defence >= 70)
		{
			return "RIGOUR";
		}
		if (client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) != 0 && prayer >= 62)
		{
			return "DEADEYE";
		}
		return prayer >= 44 ? "EAGLE_EYE" : prayer >= 26 ? "HAWK_EYE" : prayer >= 8 ? "SHARP_EYE" : null;
	}

	public String bestMagicPrayer()
	{
		int prayer = client.getRealSkillLevel(Skill.PRAYER);
		int defence = client.getRealSkillLevel(Skill.DEFENCE);
		if (client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) != 0 && prayer >= 77 && defence >= 70)
		{
			return "AUGURY";
		}
		if (client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0 && prayer >= 62)
		{
			return "MYSTIC_VIGOUR";
		}
		return prayer >= 45 ? "MYSTIC_MIGHT" : prayer >= 27 ? "MYSTIC_LORE" : prayer >= 9 ? "MYSTIC_WILL" : null;
	}

	public String bestMeleePrayer()
	{
		int prayer = client.getRealSkillLevel(Skill.PRAYER);
		int defence = client.getRealSkillLevel(Skill.DEFENCE);
		// Both need King's Ransom's Knight Waves training done.
		boolean knightWaves = client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) == 8;
		if (knightWaves && prayer >= 70 && defence >= 70)
		{
			return "PIETY";
		}
		return knightWaves && prayer >= 60 && defence >= 65 ? "CHIVALRY" : null;
	}

	public boolean inInstance()
	{
		return client.getTopLevelWorldView().isInstance();
	}

	public int attackStyle()
	{
		return client.getVarpValue(VarPlayerID.COM_MODE);
	}

	public int clockMinutes()
	{
		return client.getVarpValue(VarPlayerID.DATE_MINUTES);
	}

	public int clockSeconds()
	{
		return client.getVarbitValue(VarbitID.DATE_SECONDS_PAST_MINUTE);
	}

	// ---- NPCs ----

	/** Local diagnostic clock only; never changes the snapshot or its NPC source. */
	public void beginNpcSnapshot(long wireTick)
	{
		if (npcDiagnostics != null) npcDiagnostics.beginSnapshot(wireTick);
	}

    /** Current tick's receipts supplement same-tick cached membership, without scanning a world view. */
    public void observeNpcEvents(com.google.gson.JsonArray events)
    {
        npcEvents = events == null ? new com.google.gson.JsonArray() : events;
    }

	public List<Npc> npcs()
	{
		List<Npc> list = new ArrayList<>();
		// Client-thread capture uses Main's singleton query API. Spawn/animation receipts fill
        // a same-tick cache gap through individual live index lookups; no private cache is created.
		WorldView worldView = client.getTopLevelWorldView();
        java.util.Map<Integer, NPC> observed = new java.util.LinkedHashMap<>();
        var cache = net.runelite.client.plugins.microbot.Microbot.getRs2NpcCache();
        if (worldView != null && cache != null)
            for (var model : cache.query().toList())
            {
                NPC cached = model.getNpc();
                if (cached == null || cached.getWorldView() != worldView) continue;
                NPC fresh = worldView.npcs().byIndex(cached.getIndex());
                if (fresh != null && fresh.getWorldView() == worldView && fresh.getIndex() == cached.getIndex())
                    observed.put(fresh.getIndex(), fresh);
            }
        if (worldView != null) for (com.google.gson.JsonElement value : npcEvents)
        {
            if (!value.isJsonObject()) continue;
            var event = value.getAsJsonObject();
            String kind = event.has("e") ? event.get("e").getAsString() : "";
            com.google.gson.JsonElement index = event.has("index") ? event.get("index") : event.get("actor");
            if (index == null || !index.isJsonPrimitive() || !index.getAsJsonPrimitive().isNumber()) continue;
            int id = index.getAsInt();
            if (id < 0 || id > 65535) continue;
            if ("despawn".equals(kind)) { observed.remove(id); continue; }
            NPC fresh = worldView.npcs().byIndex(id);
            if (fresh != null && fresh.getWorldView() == worldView && fresh.getIndex() == id) observed.put(id, fresh);
        }
		List<NPC> source = new ArrayList<>(observed.values());
		for (NPC npc : source)
		{
			if (npc == null || npc.getName() == null)
			{
				if (npcDiagnostics != null) npcDiagnostics.skipped(npc);
				continue;
			}
			Npc n = new Npc();
			WorldPoint wp = npc.getWorldLocation();
			WorldPoint t = template(LocalPoint.fromWorld(client.getTopLevelWorldView(), wp), wp);
			NPCComposition composition = npc.getTransformedComposition();
			n.index = npc.getIndex();
			n.id = npc.getId();
			n.name = Text.removeTags(npc.getName());
			n.x = wp.getX();
			n.y = wp.getY();
			n.tx = t.getX();
			n.ty = t.getY();
			n.size = composition == null ? 1 : Math.max(1, composition.getSize());
			n.dead = npc.isDead();
			n.orientation = npc.getCurrentOrientation();
			n.anim = npc.getAnimation();
			n.hpRatio = npc.getHealthRatio();
			n.hpScale = npc.getHealthScale();
			n.interacting = actor(npc.getInteracting());
			n.level = npc.getCombatLevel();
			List<Integer> spot = new ArrayList<>();
			npc.getSpotAnims().forEach(a -> spot.add(a.getId()));
			n.spot = spot.isEmpty() ? null : spot.stream().mapToInt(Integer::intValue).toArray();
			String say = npc.getOverheadText();
			n.say = say == null || say.isEmpty() ? null : Text.removeTags(say);
			list.add(n);
		}
		if (npcDiagnostics != null) npcDiagnostics.snapshot(worldView, source, list);
		return list;
	}

	// ---- Prayers ----

	public List<String> activePrayers()
	{
		List<String> list = new ArrayList<>();
		// Each prayer's own varbit: isPrayerActive mixes up Deadeye/Eagle Eye and Mystic Vigour/Might.
		for (Prayer p : Prayer.values())
		{
			if (client.getVarbitValue(p.getVarbit()) == 1)
			{
				list.add(p.name());
			}
		}
		return list;
	}

	// ---- Items ----

	public List<Messages.Item> inventory()
	{
		List<Messages.Item> list = new ArrayList<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		net.runelite.api.Item[] items = inv == null ? new net.runelite.api.Item[0] : inv.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i].getId() >= 0)
			{
				Messages.Item held = new Messages.Item(i, items[i].getId(), items[i].getQuantity(), itemName(items[i].getId()));
				held.gear = gear(items[i].getId());
				list.add(held);
			}
		}
		return list;
	}

	public List<Worn> equipment()
	{
		List<Worn> list = new ArrayList<>();
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			net.runelite.api.Item item = worn == null ? null : worn.getItem(slot.getSlotIdx());
			if (item != null && item.getId() >= 0)
			{
				Worn w = new Worn(slot.getSlotIdx(), item.getId(), itemName(item.getId()));
				w.gear = gear(item.getId());
				list.add(w);
			}
		}
		return list;
	}

	// ---- Projectiles ----

	public List<Projectile> projectiles()
	{
		List<Projectile> list = new ArrayList<>();
		for (net.runelite.api.Projectile p : client.getProjectiles())
		{
			Projectile o = new Projectile();
			o.id = p.getId();
			o.target = actor(p.getTargetActor());
			o.source = actor(p.getSourceActor());
			WorldPoint aimed = p.getTargetPoint();
			if (aimed != null)
			{
				WorldPoint t = template(LocalPoint.fromWorld(client.getTopLevelWorldView(), aimed), aimed);
				o.tx = t.getX();
				o.ty = t.getY();
			}
			int cycles = p.getEndCycle() - client.getGameCycle();
			o.ticksLeft = Math.max(0, (cycles + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK);
			list.add(o);
		}
		return list;
	}

	// ---- Camera ----

	public int cameraYaw()
	{
		return client.getCameraYaw() / ANGLE_SCALE;
	}

	public int cameraPitch()
	{
		return client.getCameraPitch() / ANGLE_SCALE;
	}

	public int cameraZoom()
	{
		return client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
	}

	public boolean tileOnScreen(int tx, int ty)
	{
		int[] s = tiles.scene(tx, ty);
		if (s == null)
		{
			return false;
		}
		WorldView wv = client.getTopLevelWorldView();
		Point c = Perspective.localToCanvas(client, LocalPoint.fromScene(s[0], s[1], wv), wv.getPlane());
		return c != null && c.getX() >= 0 && c.getX() < client.getViewportWidth() && c.getY() >= 0 && c.getY() < client.getViewportHeight();
	}

	// ---- Collision ----

	public int collisionFlags(int tx, int ty)
	{
		int[] s = tiles.scene(tx, ty);
		WorldView wv = client.getTopLevelWorldView();
		CollisionData[] maps = wv == null ? null : wv.getCollisionMaps();
		if (s == null || maps == null || maps[wv.getPlane()] == null)
		{
			return 0;
		}
		return maps[wv.getPlane()].getFlags()[s[0]][s[1]];
	}

	// ---- helpers ----

	/** "player" for you, an NPC's index, or null. */
	protected Object actor(Actor a)
	{
		if (a == null)
		{
			return null;
		}
		if (a == me())
		{
			return Messages.PLAYER;
		}
		return a instanceof NPC ? ((NPC) a).getIndex() : null;
	}

	/** The template tile of a local point (world tile outside an instance); `world` if it isn't in the scene. */
	protected WorldPoint template(LocalPoint local, WorldPoint world)
	{
		if (local == null || !client.getTopLevelWorldView().isInstance())
		{
			return world;
		}
		WorldPoint t = WorldPoint.fromLocalInstance(client, local);
		return t == null ? world : t;
	}

	protected String itemName(int id)
	{
		return Text.removeTags(client.getItemDefinition(id).getName());
	}

	/** Client thread. Null until the item stats have loaded, or for an item that can't be worn. */
	protected Messages.Gear gear(int id)
	{
		var items = net.runelite.client.plugins.microbot.Microbot.getItemManager();
		var stats = items == null ? null : items.getItemStats(id);
		var e = stats == null || !stats.isEquipable() ? null : stats.getEquipment();
		if (e == null) return null;
		Messages.Gear g = new Messages.Gear();
		g.eq = e.getSlot(); g.twoHanded = e.isTwoHanded();
		g.stab = e.getAstab(); g.slash = e.getAslash(); g.crush = e.getAcrush();
		g.magic = e.getAmagic(); g.range = e.getArange();
		g.str = e.getStr(); g.rstr = e.getRstr(); g.mdmg = e.getMdmg(); g.prayer = e.getPrayer();
		g.speed = e.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx() ? e.getAspeed() : 0;
		return g;
	}
}
