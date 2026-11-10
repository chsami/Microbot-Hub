package net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common;

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
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Messages;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Messages.Npc;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Messages.Projectile;
import net.runelite.client.plugins.microbot.drocolosseumprayers.execution.Messages.Worn;

public class State
{
	private static final Skill[] SKILLS = {
		Skill.HITPOINTS, Skill.PRAYER, Skill.RANGED, Skill.MAGIC, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
	};

	private static final int ANGLE_SCALE = 8;
	private static final int CYCLES_PER_TICK = 30;

	protected final Client client;
	protected final TemplateTiles tiles;
	private final NpcSpawnDiagnostics npcDiagnostics;
    private final java.util.IdentityHashMap<NPC,Long> npcIdentities=new java.util.IdentityHashMap<>();
    private long nextNpcIdentity;
    private com.google.gson.JsonArray npcEvents = new com.google.gson.JsonArray();

	public State(Client client)
	{
		this(client, null);
	}

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

	public boolean loggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN && me() != null;
	}


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

    public com.google.gson.JsonObject destination()
    {
        if(client==null||client.getTopLevelWorldView()==null)return null;
        LocalPoint local=client.getLocalDestinationLocation();
        if(local==null)return null;
        WorldPoint point=WorldPoint.fromLocalInstance(client,local);
        if(point==null)return null;
        var value=new com.google.gson.JsonObject();value.addProperty("tx",point.getX());value.addProperty("ty",point.getY());value.addProperty("plane",point.getPlane());return value;
    }

	public int templateX()
	{
		WorldPoint wp = me().getWorldLocation();
		return template(LocalPoint.fromWorld(client.getTopLevelWorldView(), wp), wp).getX();
	}

	public int templateY()
	{
		WorldPoint wp = me().getWorldLocation();
		return template(LocalPoint.fromWorld(client.getTopLevelWorldView(), wp), wp).getY();
	}

	public int hp()
	{
		return client.getBoostedSkillLevel(Skill.HITPOINTS);
	}

    public int poison()
    {
        return client.getVarpValue(VarPlayerID.POISON);
    }

    public int selectedModifier(){return client.getVarbitValue(VarbitID.COLOSSEUM_SELECTED_MODIFIER);}

    public int frailtyStacks(){return client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_FRAILTY_STACKS_CLIENT);}
    public int myopiaStacks(){return client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_MYOPIA_STACKS_CLIENT);}

    public com.google.gson.JsonObject modifierLevels(){
        var levels=new com.google.gson.JsonObject();
        levels.addProperty("blasphemy",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_BLASPHEMY_STACKS_CLIENT));
        levels.addProperty("toxicity",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_TOXICITY_STACKS_CLIENT));
        levels.addProperty("reentry",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_REENTRY_STACKS_CLIENT));
        levels.addProperty("myopia",myopiaStacks());
        levels.addProperty("frailty",frailtyStacks());
        levels.addProperty("solarflare",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_SOLARFLARE_STACKS_CLIENT));
        levels.addProperty("relentless",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_RELENTLESS_STACKS_CLIENT));
        levels.addProperty("volatility",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_VOLATILITY_STACKS_CLIENT));
        levels.addProperty("mantimayhem",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_MANTIMAYHEM_STACKS_CLIENT));
        levels.addProperty("doom",client.getVarbitValue(VarbitID.COLOSSEUM_MODIFIER_DOOM_STACKS_CLIENT));
        levels.addProperty("doomPoints",client.getVarbitValue(VarbitID.COLOSSEUM_DOOM_STACKS_CLIENT));
        return levels;
    }

    public com.google.gson.JsonArray moltenSand(){
        var values=new com.google.gson.JsonArray();
        if(client==null)return values;
        var view=client.getTopLevelWorldView();
        if(view==null||view.getScene()==null)return values;
        var tiles=view.getScene().getTiles();
        int plane=view.getPlane();
        if(tiles==null||plane<0||plane>=tiles.length||tiles[plane]==null)return values;
        for(var column:tiles[plane]){
            if(column==null)continue;
            for(var tile:column){
                if(tile==null||tile.getGameObjects()==null)continue;
                for(var object:tile.getGameObjects()){
                    if(object==null)continue;
                    int id=object.getId();

                    if(id<50743||id>50746)continue;
                    var point=WorldPoint.fromLocalInstance(client,object.getLocalLocation());
                    if(point==null)continue;
                    var value=new com.google.gson.JsonObject();
                    value.addProperty("id",id);value.addProperty("kind","game");
                    value.addProperty("tx",point.getX());value.addProperty("ty",point.getY());
                    values.add(value);
                }
            }
        }
        return values;
    }

    public int solGrapplePending(){return client.getVarbitValue(VarbitID.COLOSSEUM_SOL_GRAPPLE_PENDING);}

    public com.google.gson.JsonArray graphicsObjects(){
        var values=new com.google.gson.JsonArray();
        if(client==null)return values;
        var view=client.getTopLevelWorldView();
        if(view==null||view.getGraphicsObjects()==null)return values;
        for(var graphic:view.getGraphicsObjects()){
            if(graphic==null||graphic.getWorldView()!=view||graphic.getLocation()==null)continue;
            var tile= WorldPoint.fromLocalInstance(client,graphic.getLocation());
            if(tile==null)continue;
            var value=new com.google.gson.JsonObject();
            value.addProperty("id",graphic.getId());value.addProperty("tx",tile.getX());value.addProperty("ty",tile.getY());
            value.addProperty("plane",graphic.getLevel());
            value.addProperty("startTicks",Math.max(0,(graphic.getStartCycle()-client.getGameCycle()+29)/30));
            value.addProperty("finished",graphic.finished());values.add(value);
        }
        return values;
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

	public void beginNpcSnapshot(long snapshotTick)
	{
		if (npcDiagnostics != null) npcDiagnostics.beginSnapshot(snapshotTick);
	}

    public void observeNpcEvents(com.google.gson.JsonArray events)
    {
        npcEvents = events == null ? new com.google.gson.JsonArray() : events;
    }

	public List<Npc> npcs()
	{
		List<Npc> list = new ArrayList<>();

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
        var liveIdentities=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<NPC,Boolean>());
        liveIdentities.addAll(source);npcIdentities.keySet().removeIf(npc->!liveIdentities.contains(npc));
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
            n.key=Long.toString(npcIdentities.computeIfAbsent(npc,ignored->++nextNpcIdentity));
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
            n.animationFrame = npc.getAnimationFrame();
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

	public List<String> activePrayers()
	{
		List<String> list = new ArrayList<>();

		for (Prayer p : Prayer.values())
		{
			if (client.getVarbitValue(p.getVarbit()) == 1)
			{
				list.add(p.name());
			}
		}
		return list;
	}

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

    public byte[] hiddenTiles(int[] screen, java.util.Set<Integer> occluders)
    {
        return new ArenaOcclusion(client, tiles).mask(screen, occluders);
    }

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
