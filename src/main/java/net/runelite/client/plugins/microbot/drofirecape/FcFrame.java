/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Built on the client thread. No NPC, Widget or live collision array leaves capture(). */
public final class FcFrame {
    public int monsterCount(){return cave?model.mobs().size():0;}
    /** Java 11 value type; preserves the former record API and value semantics. */
    static final class ItemSlot {
        private final int slot;
        private final int id;
        private final int quantity;
        private final String name;
        private final List<String> actions;

        ItemSlot(int slot, int id, int quantity, String name, List<String> actions) {
            this.slot = slot;
            this.id = id;
            this.quantity = quantity;
            this.name = name;
            this.actions = actions;
        }

        public int slot() { return slot; }
        public int id() { return id; }
        public int quantity() { return quantity; }
        public String name() { return name; }
        public List<String> actions() { return actions; }

        boolean hasAction(String action){return actions.stream().anyMatch(a->a.equalsIgnoreCase(action));}

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ItemSlot)) return false;
            ItemSlot that = (ItemSlot) other;
            return slot == that.slot
                && id == that.id
                && quantity == that.quantity
                && java.util.Objects.equals(name, that.name)
                && java.util.Objects.equals(actions, that.actions);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(slot);
            result = 31 * result + Integer.hashCode(id);
            result = 31 * result + Integer.hashCode(quantity);
            result = 31 * result + java.util.Objects.hashCode(name);
            result = 31 * result + java.util.Objects.hashCode(actions);
            return result;
        }

        @Override
        public String toString() {
            return "ItemSlot[slot=" + slot + ", id=" + id + ", quantity=" + quantity + ", name=" + name + ", actions=" + actions + "]";
        }
    }
    final long capturedAt;
    final int tick,world,plane,baseX,baseY,hp,maxHp,prayer,maxPrayer,rawEnergy,serverMinute,serverSecond;
    final int attack,baseAttack,strength,baseStrength,defence,baseDefence,magic,baseMagic;
    final int ranged,baseRanged,attackStyle,weaponId,interactingIndex,specialEnergy,specialEnabled,playerAnimation;
    final boolean cave,inTzhaar,moving,running,autoRetaliate,containersReady;
    final String weapon;
    final WorldPoint location,template;
    final Protection overhead;
    final Snapshot model;
    final List<ItemSlot> inventory;
    final Map<WaveBook.Spawn,FcModel.Tile> spawns;
    final Map<FcModel.Tile,FcModel.Tile> recordedTiles;
    final Set<Integer> presentNpcIndices,healersTargetingJad;
    private FcFrame(Client c,WorldView view,Player player,List<Mob> mobs,CollisionGrid grid,
                    Protection jad,Map<WaveBook.Spawn,FcModel.Tile> spawnPoints,int weaponRange,Set<Integer> healersOnJad,Map<FcModel.Tile,FcModel.Tile> recordedPoints,boolean meleeMode) {
        recordedTiles=Map.copyOf(recordedPoints);
        capturedAt=System.currentTimeMillis();tick=c.getTickCount();world=c.getWorld();
        plane=view.getPlane();baseX=view.getBaseX();baseY=view.getBaseY();
        location=player.getWorldLocation();template=WorldPoint.fromLocalInstance(c,player.getLocalLocation());
        cave=view.isInstance()&&template!=null&&template.getRegionID()==WaveBook.REGION;
        inTzhaar=!view.isInstance()&&template!=null&&WaveBook.outerTzhaarRegion(template.getRegionID());
        hp=c.getBoostedSkillLevel(Skill.HITPOINTS);maxHp=c.getRealSkillLevel(Skill.HITPOINTS);
        prayer=c.getBoostedSkillLevel(Skill.PRAYER);maxPrayer=c.getRealSkillLevel(Skill.PRAYER);
        rawEnergy=c.getEnergy();running=c.getVarpValue(173)==1;
        autoRetaliate=c.getVarpValue(172)==0;
        moving=player.getPoseAnimation()!=player.getIdlePoseAnimation();
        attack=c.getBoostedSkillLevel(Skill.ATTACK);baseAttack=c.getRealSkillLevel(Skill.ATTACK);
        strength=c.getBoostedSkillLevel(Skill.STRENGTH);baseStrength=c.getRealSkillLevel(Skill.STRENGTH);
        defence=c.getBoostedSkillLevel(Skill.DEFENCE);baseDefence=c.getRealSkillLevel(Skill.DEFENCE);
        magic=c.getBoostedSkillLevel(Skill.MAGIC);baseMagic=c.getRealSkillLevel(Skill.MAGIC);
        containersReady=c.getItemContainer(InventoryID.INVENTORY)!=null&&c.getItemContainer(InventoryID.EQUIPMENT)!=null;
        ranged=c.getBoostedSkillLevel(Skill.RANGED);baseRanged=c.getRealSkillLevel(Skill.RANGED);
        attackStyle=c.getVarpValue(43);playerAnimation=player.getAnimation();
        specialEnergy=c.getVarpValue(300);specialEnabled=c.getVarpValue(301);
        Actor interacting=player.getInteracting();interactingIndex=interacting instanceof NPC?((NPC)interacting).getIndex():-1;
        overhead=c.isPrayerActive(Prayer.PROTECT_FROM_MAGIC)?Protection.MAGIC:
            c.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)?Protection.RANGE:
            c.isPrayerActive(Prayer.PROTECT_FROM_MELEE)?Protection.MELEE:Protection.NONE;
        int min=-1,sec=-1;
        if(!cave&&template!=null&&template.getRegionID()==9808) {
            min=c.getVarpValue(VarPlayerID.DATE_MINUTES);
            sec=c.getVarbitValue(VarbitID.DATE_SECONDS_PAST_MINUTE);
        }
        serverMinute=min;serverSecond=sec;
        ItemContainer equipped=c.getItemContainer(InventoryID.EQUIPMENT);
        Item w=equipped==null?null:equipped.getItem(3);
        weaponId=w==null?-1:w.getId();weapon=weaponId<=0?"":c.getItemDefinition(weaponId).getName();
        ArrayList<ItemSlot> inv=new ArrayList<>();ItemContainer container=c.getItemContainer(InventoryID.INVENTORY);
        if(container!=null) {
            Item[] items=container.getItems();
            for(int i=0;i<items.length;i++)if(items[i]!=null&&items[i].getId()>0) {
                Item item=items[i];ItemComposition def=c.getItemDefinition(item.getId());
                ArrayList<String> actions=new ArrayList<>();
                if(def.getInventoryActions()!=null)for(String a:def.getInventoryActions())if(a!=null)actions.add(a);
                inv.add(new ItemSlot(i,item.getId(),item.getQuantity(),def.getName(),List.copyOf(actions)));
            }
        }
        inventory=List.copyOf(inv);spawns=Map.copyOf(spawnPoints);healersTargetingJad=Set.copyOf(healersOnJad);
        HashSet<Integer> indices=new HashSet<>();for(Mob m:mobs)indices.add(m.index());presentNpcIndices=Set.copyOf(indices);
        LocalPoint local=player.getLocalLocation();
        model=new Snapshot(tick,new FcModel.Tile(local.getSceneX(),local.getSceneY()),grid,mobs,
                Math.max(0,(rawEnergy+99)/100),running,weaponRange,jad,meleeMode);
    }
    static FcFrame capture(Client c,int overrideRange,boolean meleeMode,Protection jad,Map<Integer,Integer> attackTicks,Map<Integer,Protection> attackStyles,Set<Integer> dead) {
        return capture(c,overrideRange,meleeMode,jad,attackTicks,attackStyles,dead,false);
    }
    static FcFrame captureOptional(Client c,int overrideRange,boolean meleeMode,Protection jad,Map<Integer,Integer> attackTicks,Map<Integer,Protection> attackStyles,Set<Integer> dead) {
        return capture(c,overrideRange,meleeMode,jad,attackTicks,attackStyles,dead,true);
    }
    private static FcFrame capture(Client c,int overrideRange,boolean meleeMode,Protection jad,Map<Integer,Integer> attackTicks,Map<Integer,Protection> attackStyles,Set<Integer> dead,boolean optionalAnchors) {
        if(c==null||c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null)return null;
        WorldView v=c.getTopLevelWorldView();if(v==null||v.getCollisionMaps()==null)return null;
        int plane=v.getPlane();if(plane<0||plane>=v.getCollisionMaps().length||v.getCollisionMaps()[plane]==null)return null;
        CollisionGrid grid=new CollisionGrid(v.getCollisionMaps()[plane].getFlags());
        Player p=c.getLocalPlayer();ArrayList<Mob> mobs=new ArrayList<>();Set<Integer> healersOnJad=new HashSet<>();
        for(var cached:net.runelite.client.plugins.microbot.Microbot.getRs2NpcCache().query().toList()) {
            NPC n=cached.getNpc();
            if(n==null||n.getWorldView()!=v||dead.contains(n.getIndex()))continue;
            NPCComposition comp=n.getTransformedComposition();if(comp==null)continue;
            int size=npcTileSize(n);Kind kind=Kind.identify(n.getName(),size);if(kind==null||size<1)continue;
            if(n.getHealthRatio()==0&&n.getHealthScale()>0)continue;
            if(kind==Kind.HEALER&&n.getInteracting() instanceof NPC
                &&"TzTok-Jad".equalsIgnoreCase(((NPC)n.getInteracting()).getName()))healersOnJad.add(n.getIndex());
            WorldArea a=n.getWorldArea();if(a==null)continue;
            FcModel.Tile tile=new FcModel.Tile(a.getX()-v.getBaseX(),a.getY()-v.getBaseY());
            mobs.add(new Mob(n.getIndex(),kind,tile,size,n.getHealthRatio(),n.getHealthScale(),
                    attackTicks.getOrDefault(n.getIndex(),-1),kind==Kind.JAD?jad:attackStyles.getOrDefault(n.getIndex(),kind.protection),n.getInteracting()==p));
        }
        mobs.sort(Comparator.comparingInt(Mob::index));
        Map<WaveBook.Spawn,FcModel.Tile> spawnPoints=new EnumMap<>(WaveBook.Spawn.class);
        Map<FcModel.Tile,FcModel.Tile> recordedPoints=new HashMap<>();
        WorldPoint template=WorldPoint.fromLocalInstance(c,p.getLocalLocation());
        if(v.isInstance()&&template!=null&&template.getRegionID()==WaveBook.REGION) {
            for(WaveBook.Spawn spawn:WaveBook.Spawn.values()) {
                WorldPoint target=new WorldPoint(WaveBook.BASE_X+spawn.x,WaveBook.BASE_Y+spawn.y,0);
                for(WorldPoint instancePoint:WorldPoint.toLocalInstance(c,target)) {
                    LocalPoint local=LocalPoint.fromWorld(v,instancePoint);
                    if(local!=null){spawnPoints.put(spawn,new FcModel.Tile(local.getSceneX(),local.getSceneY()));break;}
                }
            }
        }
        if(v.isInstance()&&template!=null&&template.getRegionID()==WaveBook.REGION) {
            Set<FcModel.Tile> points=new HashSet<>(optionalAnchors?RecordedLureBook.anchorsWithPockets():RecordedLureBook.anchors());
            for(int wave=1;wave<=25;wave++) {
                points.addAll(RecordedLureBook.candidates(5,wave));
                FcModel.Tile opening=RecordedLureBook.opening(5,wave);if(opening!=null)points.add(opening);
            }
            for(FcModel.Tile point:points) {
                WorldPoint target=new WorldPoint(WaveBook.BASE_X+point.x(),WaveBook.BASE_Y+point.y(),0);
                for(WorldPoint instancePoint:WorldPoint.toLocalInstance(c,target)) {
                    LocalPoint local=LocalPoint.fromWorld(v,instancePoint);
                    if(local!=null){recordedPoints.put(point,new FcModel.Tile(local.getSceneX(),local.getSceneY()));break;}
                }
            }
        }
        int range=meleeMode?1:overrideRange;
        if(range==0) {
            ItemContainer equipment=c.getItemContainer(InventoryID.EQUIPMENT);Item item=equipment==null?null:equipment.getItem(3);
            range=item==null?0:inferRange(c.getItemDefinition(item.getId()).getName());
        }
        return new FcFrame(c,v,p,mobs,grid,jad,spawnPoints,Math.max(1,range),healersOnJad,recordedPoints,meleeMode);
    }
    /** WorldArea and the planner use tile units. Never use the recorder's raw footprintSize. */
    static int npcTileSize(NPC npc) {
        WorldArea area=npc.getWorldArea();
        if(area!=null&&area.getWidth()==area.getHeight()&&area.getWidth()>=1&&area.getWidth()<=8)return area.getWidth();
        NPCComposition comp=npc.getTransformedComposition();
        int size=comp==null?0:comp.getSize();
        return size>=1&&size<=8?size:0;
    }
    FcModel.Tile recordedAnchor(FcModel.Tile template){return recordedTiles.get(template);}
    /** Instance mapping can be incomplete while the scene loads; omit unavailable positions. */
    List<FcModel.Tile> recordedAnchors(FcModel.Tile... templates){
        List<FcModel.Tile> result=new ArrayList<>();
        for(FcModel.Tile template:templates){
            FcModel.Tile tile=recordedAnchor(template);
            if(tile!=null)result.add(tile);
        }
        return List.copyOf(result);
    }
    List<FcModel.Tile> demonstratedPositions(int rotation,int wave) {
        ArrayList<FcModel.Tile> points=new ArrayList<>();
        for(FcModel.Tile template:RecordedLureBook.candidates(rotation,wave)) {
            FcModel.Tile scene=recordedTiles.get(template);if(scene!=null&&model.grid().open(scene))points.add(scene);
        }
        return points;
    }
    FcModel.Tile demonstratedOpening(int rotation,int wave) {
        FcModel.Tile template=RecordedLureBook.opening(rotation,wave);
        return template==null?null:recordedTiles.get(template);
    }
    static int inferRange(String name) {
        if(name==null)return 0;String w=name.toLowerCase(Locale.ROOT);
        if(w.contains("blowpipe"))return 5;
        if(w.contains("bow of faerdhinen")||w.contains("crystal bow")||w.contains("twisted bow")||w.contains("longbow"))return 10;
        if(w.contains("karil"))return 6;
        if(w.contains("crossbow"))return 7;
        if(w.contains("shortbow")||w.contains("webweaver")||w.contains("craw's")||w.contains("venator"))return 7;
        if(w.contains("knife"))return 4;
        if(w.contains("dart"))return 3;
        return 0;
    }
    Snapshot predictedWave(int rotation,int wave) {
        if(!WaveBook.validRotation(rotation)||wave<1||wave>63)return null;
        ArrayList<Mob> mobs=new ArrayList<>();int id=10000;
        for(WaveBook.Spawned spawn:WaveBook.wave(rotation,wave)) {
            FcModel.Tile tile=spawns.get(spawn.location());if(tile==null)return null;
            mobs.add(new Mob(id++,spawn.kind(),tile,spawn.kind().size,-1,-1,-1,spawn.kind().protection,true));
        }
        return new Snapshot(tick,model.player(),model.grid(),mobs,model.runEnergy(),running,model.weaponRange(),Protection.MAGIC,model.meleeMode());
    }
    int count(int id){return inventory.stream().filter(i->i.id()==id).mapToInt(ItemSlot::quantity).sum();}
    boolean sameScene(FcFrame other) {
        return other!=null&&world==other.world&&plane==other.plane&&baseX==other.baseX
            &&baseY==other.baseY&&cave==other.cave;
    }
    boolean stale(){return System.currentTimeMillis()-capturedAt>1800;}
    int energyPercent(){return Math.max(0,Math.min(100,rawEnergy/100));}
}
