/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.drofirecape.core.WaveBook;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.*;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection;

/** All input executes off the client thread. Read lambdas return immutable dispatch details. */
class FcActions {
    /** Java 11 value type; preserves the former record API and value semantics. */
    protected static final class Click {
        private final NewMenuEntry entry;
        private final Rectangle bounds;

        Click(NewMenuEntry entry, Rectangle bounds) {
            this.entry = entry;
            this.bounds = bounds;
        }

        public NewMenuEntry entry() { return entry; }
        public Rectangle bounds() { return bounds; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Click)) return false;
            Click that = (Click) other;
            return java.util.Objects.equals(entry, that.entry)
                && java.util.Objects.equals(bounds, that.bounds);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + java.util.Objects.hashCode(entry);
            result = 31 * result + java.util.Objects.hashCode(bounds);
            return result;
        }

        @Override
        public String toString() {
            return "Click[entry=" + entry + ", bounds=" + bounds + "]";
        }
    }
    protected final FcSpawnPredictor spawnPredictor=new FcSpawnPredictor();
    protected long uiAt,lastAttackAt;
    protected volatile String lastAttackResult="No attack requested";
    protected int lastAttackIndex=-1;
    protected long attackGap=115;
    protected volatile boolean observe;
    protected volatile FcTickPrayers tickPrayers;
    void tickPrayerDriver(FcTickPrayers driver){tickPrayers=driver;}
    protected FcTickPrayers ownedPrayerDriver(){FcTickPrayers d=tickPrayers;return d!=null&&d.ownsInput()?d:null;}
    Protection attackProtection(Protection fallback){FcTickPrayers d=ownedPrayerDriver();return d==null?fallback:d.requested();}
    String selectedOffence(){FcTickPrayers d=tickPrayers;return d!=null?d.selectedOffence():"Detecting";}
    protected final Object inputLock=new Object();
    protected final FcPrayerUi prayerUi=new FcPrayerUi(()->!observe&&!Microbot.pauseAllScripts.get()
        &&!InputArbiter.isHuman()&&!Thread.currentThread().isInterrupted());
    String prayerUiStatus(){return prayerUi.problem();}
    protected final net.runelite.client.plugins.microbot.drofirecape.core.PrayerSwitchGuard combatPrayerGuard=new net.runelite.client.plugins.microbot.drofirecape.core.PrayerSwitchGuard(COMBAT_PRAYERS.length);
    void observeOnly(boolean value){observe=value;}
    void reset(){combatPrayerGuard.reset();prayerUi.reset();uiAt=lastAttackAt=0;lastAttackIndex=-1;}
    static <T>T read(Callable<T> r,T fallback){return Microbot.getClientThread().runOnClientThreadOptional(r).orElse(fallback);}
    protected static Client client(){return Microbot.getClient();}
    protected boolean invoke(Click click){if(observe||click==null||click.bounds()==null)return false;synchronized(inputLock){Microbot.doInvoke(click.entry(),click.bounds());}return true;}
    protected boolean uiReady(long gap){return !observe&&System.currentTimeMillis()-uiAt>=gap;}
    protected void uiSent(){uiAt=System.currentTimeMillis();}

    protected static final Rs2PrayerEnum[] COMBAT_PRAYERS={Rs2PrayerEnum.PROTECT_MELEE,
        Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MAGIC,
        Rs2PrayerEnum.SHARP_EYE,Rs2PrayerEnum.HAWK_EYE,Rs2PrayerEnum.EAGLE_EYE,
        Rs2PrayerEnum.DEAD_EYE,Rs2PrayerEnum.RIGOUR,
        Rs2PrayerEnum.BURST_STRENGTH,Rs2PrayerEnum.SUPERHUMAN_STRENGTH,Rs2PrayerEnum.ULTIMATE_STRENGTH,
        Rs2PrayerEnum.CLARITY_THOUGHT,Rs2PrayerEnum.IMPROVED_REFLEXES,Rs2PrayerEnum.INCREDIBLE_REFLEXES,
        Rs2PrayerEnum.CHIVALRY,Rs2PrayerEnum.PIETY};
    /** Entry/exit acknowledgement only; cave combat is owned by FcTickPrayers. */
    protected static final class PrayerState {
        final int tick,points;
        final boolean[] active=new boolean[COMBAT_PRAYERS.length];
        PrayerState(Client c) {
            tick=c.getTickCount();points=c.getBoostedSkillLevel(Skill.PRAYER);
            for(int i=0;i<active.length;i++)active[i]=c.getVarbitValue(COMBAT_PRAYERS[i].getVarbit())==1;
            for(int i=3;i<active.length;i++)for(int j=i+1;j<active.length;j++)
                if(COMBAT_PRAYERS[i].getIndex()==COMBAT_PRAYERS[j].getIndex())
                    active[i]=active[j]=active[i]||active[j];
        }
    }
    /** Called only by the existing background prayer worker, never ClientTick. */
    boolean prepareTickPrayer(Rs2PrayerEnum prayer,java.util.function.BooleanSupplier valid) {
        synchronized(inputLock){return prayerUi.preparePrayer(prayer,valid);}
    }
    FcPrayerUi.Result tickPrayer(net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Command command,
        Rs2PrayerEnum prayer,java.util.function.BooleanSupplier early,java.util.function.BooleanSupplier valid) {
        synchronized(inputLock) {
            if(command.type==net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Type.RESET)
                return prayerUi.resetPrayer(prayer,early,valid);
            if(command.type==net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Type.OFF
                &&!early.getAsBoolean())return FcPrayerUi.Result.SKIPPED;
            return prayerUi.prayer(prayer,
                command.type==net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Type.ON,
                ()->valid.getAsBoolean()&&(command.type!=net.runelite.client.plugins.microbot.drofirecape.core.OneTickPrayerCycle.Type.OFF||early.getAsBoolean()))
                ?FcPrayerUi.Result.SENT:FcPrayerUi.Result.WAITING;
        }
    }
    /** Only entry pre-arm or outside-cave cleanup may use this bounded writer. */
    protected boolean outsidePrayers(Protection protection) {
        synchronized(inputLock) {
            PrayerState state=read(()->outsidePrayerScene()?new PrayerState(client()):null,null);
            if(state==null)return false;
            long now=System.currentTimeMillis();
            for(int i=0;i<COMBAT_PRAYERS.length;i++)combatPrayerGuard.acknowledge(i,state.active[i]);
            if(protection==Protection.NONE) {
                Set<Integer> slots=new HashSet<>();boolean off=true;
                for(int i=0;i<COMBAT_PRAYERS.length;i++)if(state.active[i]) {
                    off=false;
                    if(slots.add(COMBAT_PRAYERS[i].getIndex()))toggleOutsidePrayer(state,i,false,now);
                }
                return off;
            }
            int channel=protection.ordinal()-1;
            toggleOutsidePrayer(state,channel,true,now);
            return combatPrayerGuard.settled(channel,state.active[channel],true);
        }
    }
    /** Client-thread check, repeated by the visible writer after mouse travel. */
    protected boolean outsidePrayerScene() {
        Client c=client();
        if(ownedPrayerDriver()!=null||c==null||c.getGameState()!=GameState.LOGGED_IN
            ||c.getTopLevelWorldView()==null||c.getLocalPlayer()==null||c.getLocalPlayer().getLocalLocation()==null)return false;
        WorldPoint point=WorldPoint.fromLocalInstance(c,c.getLocalPlayer().getLocalLocation());
        return point!=null&&point.getRegionID()!=WaveBook.REGION;
    }
    protected void toggleOutsidePrayer(PrayerState state,int channel,boolean desired,long now) {
        if(observe||state.points<=0)return;
        if(combatPrayerGuard.shouldToggle(channel,state.active[channel],desired,state.tick,now)
            &&!prayerUi.prayer(COMBAT_PRAYERS[channel],desired,()->read(this::outsidePrayerScene,false)))
            combatPrayerGuard.cancel(channel);
    }
    /** A missing or suspended cave owner never falls back to a second writer. */
    boolean combatProtect(Protection ignored) {
        FcTickPrayers driver=ownedPrayerDriver();return driver!=null&&driver.protectionReady();
    }
    boolean overheadActive(Protection ignored){return combatProtect(ignored);}
    boolean staminaActive(){return Rs2Player.hasStaminaBuffActive();}
    boolean offenceReady(){FcTickPrayers driver=ownedPrayerDriver();return driver!=null&&driver.offenceReady();}
    void prayersOff(){if(!observe)outsidePrayers(Protection.NONE);}
    boolean prayersObservedOff(){FcTickPrayers driver=ownedPrayerDriver();return driver!=null&&driver.prayersOffReady();}
    void idlePrayersOff(){if(uiReady(1200)){prayersOff();uiSent();}}
    boolean autoRetaliateOff(){return !observe&&Rs2Combat.setAutoRetaliate(false);}
    void enableRun(){if(!observe)Rs2Player.toggleRunEnergy(true);}
    boolean zoomConfirmed(){return read(Rs2Camera::getZoom,-1)==150;}
    void restoreZoom(){if(!observe&&uiReady(1100)){Rs2Camera.setZoom(150);uiSent();}}
    boolean move(FcFrame f,FcModel.Tile scene){return move(f,scene,true);}
    boolean move(FcFrame f,FcModel.Tile scene,boolean minimapFirst) {
        if(observe||f==null||f.stale()||scene==null||Microbot.pauseAllScripts.get()||InputArbiter.isHuman())return false;
        WorldPoint destination=new WorldPoint(f.baseX+scene.x(),f.baseY+scene.y(),f.plane);
        // Long cave travel stays on the minimap. Exact short rock steps use a
        // visible ground tile through the same native mouse when it is on screen.
        return f.cave?(minimapFirst?minimapWalk(destination,f):visibleCaveStep(destination,f)):
            rawWalk(destination,f,minimapFirst);
    }
    protected boolean visibleCaveStep(WorldPoint destination,FcFrame expected) {
        if(expected.model.player().distance(new FcModel.Tile(destination.getX()-expected.baseX,
            destination.getY()-expected.baseY))>4)return false;
        synchronized(inputLock) {
            Click click=read(()->{
                Client c=client();WorldView view=c.getTopLevelWorldView();
                if(c.getGameState()!=GameState.LOGGED_IN||view==null||!currentPosition(expected))return null;
                LocalPoint local=LocalPoint.fromWorld(view,destination);if(local==null)return null;
                Point point=Perspective.localToCanvas(c,local,view.getPlane());if(point==null)return null;
                Rectangle viewport=new Rectangle(c.getViewportXOffset(),c.getViewportYOffset(),
                    c.getViewportWidth(),c.getViewportHeight());
                Rectangle canvas=new Rectangle(2,2,c.getCanvasWidth()-4,c.getCanvasHeight()-4);
                viewport=viewport.intersection(canvas);viewport.grow(-2,-2);
                if(!viewport.contains(point.getX(),point.getY()))return null;
                return new Click(new NewMenuEntry().param0(point.getX()).param1(point.getY())
                    .type(MenuAction.WALK).identifier(0).itemId(-1).option("Walk here"),
                    new Rectangle(point.getX(),point.getY(),1,1));
            },null);
            return invoke(click);
        }
    }
    boolean rawWalk(WorldPoint target){return rawWalk(target,null,false);}
    protected boolean currentPosition(FcFrame expected) {
        if(expected==null)return true;
        if(expected.cave&&(Microbot.pauseAllScripts.get()||InputArbiter.isHuman()))return false;
        Client c=client();WorldView v=c.getTopLevelWorldView();Player player=c.getLocalPlayer();
        if(expected.stale()||v==null||player==null||v.getBaseX()!=expected.baseX||v.getBaseY()!=expected.baseY
            ||v.getPlane()!=expected.plane||c.getWorld()!=expected.world||v.isInstance()!=expected.cave
            ||c.getTickCount()-expected.tick>2)return false;
        LocalPoint local=player.getLocalLocation();
        return local!=null&&local.getSceneX()==expected.model.player().x()&&local.getSceneY()==expected.model.player().y();
    }
    protected boolean minimapWalk(WorldPoint target,FcFrame expected) {
        Point minimap=read(()->{
            if(!currentPosition(expected))return null;
            Point p=Rs2MiniMap.worldToMinimap(target);
            return p!=null&&Rs2MiniMap.isPointInsideMinimap(p)?p:null;
        },null);
        if(minimap==null)return false;
        synchronized(inputLock){Microbot.getMouse().click(minimap);}return true;
    }
    protected boolean rawWalk(WorldPoint target,FcFrame expected,boolean minimapFirst) {
        if(observe||target==null)return false;
        if(minimapFirst&&minimapWalk(target,expected))return true;
        Click canvas=read(()->{
            Client c=client();WorldView v=c.getTopLevelWorldView();
            if(c.getGameState()!=GameState.LOGGED_IN||v==null||!currentPosition(expected))return null;
            LocalPoint local=LocalPoint.fromWorld(v,target);
            if(local==null||!Rs2Camera.isTileOnScreen(local))return null;
            Point p=Perspective.localToCanvas(c,local,v.getPlane());if(p==null)return null;
            return new Click(new NewMenuEntry().param0(p.getX()).param1(p.getY())
                .type(MenuAction.WALK).identifier(0).itemId(-1).option("Walk here"),new Rectangle(p.getX(),p.getY(),1,1));
        },null);
        if(canvas!=null)return invoke(canvas);
        return minimapWalk(target,expected);
    }
    /** Unknown/missing actors do not trigger camera recovery. Read only on client thread. */
    boolean npcCompletelyOutOfView(int index,String expectedName) {
        return read(()->{
            Client c=client();WorldView view=c.getTopLevelWorldView();
            if(c.getGameState()!=GameState.LOGGED_IN||view==null)return false;
            for(var cached:Microbot.getRs2NpcCache().query().withName(expectedName)
                .where(n->n.getIndex()==index).toList()) {
                NPC npc=cached.getNpc();
                if(npc==null||npc.isDead()||npc.getWorldView()!=view)continue;
                Rectangle viewport=new Rectangle(c.getViewportXOffset(),c.getViewportYOffset(),
                    c.getViewportWidth(),c.getViewportHeight());
                return net.runelite.client.plugins.microbot.drofirecape.core.NpcVisibility.completelyOutside(
                    npc.getConvexHull(),npc.getCanvasTilePoly(),viewport);
            }
            return false;
        },false);
    }
    boolean attack(int index,String expectedName,boolean force){return attack(index,expectedName,force,null);}
    boolean attack(int index,String expectedName,boolean force,FcFrame expected) {
        if(observe)return false;
        long now=System.currentTimeMillis();
        if(now-lastAttackAt<attackGap)return false;
        if(!force&&lastAttackIndex==index&&now-lastAttackAt<900)return false;
        Click click=npcClick(index,expectedName,"Attack",expected);
        if(click==null){lastAttackResult="No current Attack action for NPC "+index+" / "+expectedName;return false;}
        if(invoke(click)) {
            lastAttackResult="Attack dispatched: "+index+" / "+expectedName;
            lastAttackAt=now;lastAttackIndex=index;attackGap=ThreadLocalRandom.current().nextLong(110,121);return true;
        }
        return false;
    }
    String lastAttackResult(){return lastAttackResult;}
    protected Click npcClick(int index,String name,String action){return npcClick(index,name,action,null);}
    protected Click npcClick(int index,String name,String action,FcFrame expected) {
        return read(()->{
            Client c=client();WorldView v=c.getTopLevelWorldView();
            if(c.getGameState()!=GameState.LOGGED_IN||v==null||c.getLocalPlayer()==null||!currentPosition(expected))return null;
            NPC selected=null;
            for(var cached:Microbot.getRs2NpcCache().query().withName(name)
                .where(n->index<0||n.getIndex()==index).toList()) {
                NPC n=cached.getNpc();if(n==null||n.getWorldView()!=v)continue;
                if(selected==null||n.getWorldLocation().distanceTo2D(c.getLocalPlayer().getWorldLocation())<selected.getWorldLocation().distanceTo2D(c.getLocalPlayer().getWorldLocation()))selected=n;
            }
            if(selected==null)return null;
            NPCComposition comp=selected.getTransformedComposition();if(comp==null||comp.getActions()==null)return null;
            int option=actionIndex(comp.getActions(),action);if(option<0)return null;
            MenuAction[] op={MenuAction.NPC_FIRST_OPTION,MenuAction.NPC_SECOND_OPTION,MenuAction.NPC_THIRD_OPTION,MenuAction.NPC_FOURTH_OPTION,MenuAction.NPC_FIFTH_OPTION};
            java.awt.Shape hull=selected.getConvexHull();if(hull==null)return null;Rectangle bounds=hull.getBounds();
            bounds=bounds.intersection(new Rectangle(2,2,c.getCanvasWidth()-4,c.getCanvasHeight()-4));
            if(bounds.isEmpty())return null;
            return new Click(new NewMenuEntry().option(action).target(selected.getName()).param0(0).param1(0)
                .type(op[option]).identifier(selected.getIndex()).itemId(-1).actor(selected).worldViewId(v.getId()),bounds);
        },null);
    }
    boolean openTzhaarBank() {
        if(!uiReady(2300))return false;
        boolean clicked=invoke(npcClick(-1,"TzHaar-Ket-Zuh","Bank"));if(clicked)uiSent();return clicked;
    }
    boolean closeBank() {
        if(!uiReady(1400))return false;
        uiSent();
        // The native helper handles ESC configuration and verifies that the bank closed.
        // Do not depend on an unverified dynamic child index of the bank frame.
        return Rs2Bank.closeBank();
    }
    enum ItemResult { SENT, PREPARING, UNAVAILABLE }
    boolean item(FcFrame.ItemSlot item,String action){return itemStep(item,action)==ItemResult.SENT;}
    ItemResult itemStep(FcFrame.ItemSlot item,String action) {return itemStep(item,action,()->true);}
    ItemResult itemStep(FcFrame.ItemSlot item,String action,java.util.function.BooleanSupplier permitted) {
        if(observe||item==null||!permitted.getAsBoolean())return ItemResult.UNAVAILABLE;
        synchronized(inputLock){
            if(!prayerUi.inventory())return prayerUi.missingKey()?ItemResult.UNAVAILABLE:ItemResult.PREPARING;
            Click click=inventoryClick(item,action);
            if(click==null) {
                prayerUi.problem("Inventory item/button not yet visible: slot "+item.slot()+" action "+action);
                return ItemResult.PREPARING;
            }
            return prayerUi.widget(InterfaceTab.INVENTORY,click.entry(),()->{
                Click live=permitted.getAsBoolean()?inventoryClick(item,action):null;return live==null?null:live.bounds();
            })?ItemResult.SENT:ItemResult.PREPARING;
        }
    }
    protected Click inventoryClick(FcFrame.ItemSlot item,String action) {
        return read(()->{
            ItemContainer inv=client().getItemContainer(InventoryID.INVENTORY);
            Item actual=inv==null?null:inv.getItem(item.slot());
            if(actual==null||actual.getId()!=item.id())return null;
            Widget inventory=Rs2Inventory.getInventory();
            if(inventory==null||inventory.isHidden()||inventory.getChildren()==null)return null;
            for(Widget slot:inventory.getChildren()) {
                if(slot==null||slot.getIndex()!=item.slot()||slot.isHidden())continue;
                Rectangle bounds=FcPrayerUi.visibleBounds(slot.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
                int option=actionIndex(slot.getActions(),action);
                if(option<0)option=actionIndex(client().getItemDefinition(item.id()).getInventoryActions(),action);
                if(bounds==null||option<0)return null;
                return new Click(new NewMenuEntry().option(action).target(item.name()).param0(item.slot())
                    .param1(inventory.getId()).type(MenuAction.CC_OP).identifier(option+1).itemId(item.id()),bounds);
            }
            return null;
        },null);
    }
    boolean spellbook(){synchronized(inputLock){return prayerUi.open(InterfaceTab.MAGIC);}}
    void releaseTab(){synchronized(inputLock){prayerUi.releaseTab();}}
    boolean spell(int id,String name) {
        synchronized(inputLock){
            return prayerUi.widget(InterfaceTab.MAGIC,new NewMenuEntry().option("Cast").target(name)
                .param0(-1).param1(id).type(MenuAction.CC_OP).identifier(1).itemId(-1),()->read(()->{
                    Widget widget=client().getWidget(id);
                    return widget==null||widget.isHidden()?null:FcPrayerUi.visibleBounds(widget.getBounds(),
                        client().getCanvasWidth(),client().getCanvasHeight());
                },null));
        }
    }
    boolean special() {
        if(!uiReady(1800))return false;
        Rectangle rect=read(()->{Widget w=client().getWidget(10485795);return w==null||w.isHidden()?null:w.getBounds();},null);
        if(rect==null||rect.width<=0||rect.height<=0)return false;
        synchronized(inputLock){Microbot.getMouse().click(rect);}uiSent();return true;
    }
    static int actionIndex(String[] actions,String action){if(actions!=null)for(int i=0;i<Math.min(actions.length,5);i++)if(action.equalsIgnoreCase(actions[i]))return i;return -1;}
    FcModel.Tile exitTile(FcFrame f) {
        return read(()->{
            TileObject object=findCaveExit();if(object==null)return null;
            LocalPoint p=object.getLocalLocation();return p==null?null:new FcModel.Tile(p.getSceneX(),p.getSceneY());
        },null);
    }
    protected static List<TileObject> sceneObjects() {
        ArrayList<TileObject> objects=new ArrayList<>();Client c=client();WorldView v=c.getTopLevelWorldView();
        if(v==null||c.getLocalPlayer()==null||v.getScene()==null)return objects;
        net.runelite.api.Tile[][][] levels=v.getScene().getTiles();
        if(levels==null||v.getPlane()<0||v.getPlane()>=levels.length||levels[v.getPlane()]==null)return objects;
        Set<TileObject> seen=Collections.newSetFromMap(new IdentityHashMap<TileObject,Boolean>());
        for(net.runelite.api.Tile[] row:levels[v.getPlane()]) {
            if(row==null)continue;
            for(net.runelite.api.Tile tile:row) {
                if(tile==null)continue;
                // Cave portals can be wall/decorative objects, not just GameObjects.
                TileObject[] fixed={tile.getWallObject(),tile.getDecorativeObject(),tile.getGroundObject()};
                for(TileObject o:fixed)if(o!=null&&seen.add(o))objects.add(o);
                GameObject[] games=tile.getGameObjects();
                if(games!=null)for(GameObject o:games)if(o!=null&&seen.add(o))objects.add(o);
            }
        }
        return objects;
    }
    protected static TileObject findObject(int id,String action) {
        Client c=client();if(c.getLocalPlayer()==null)return null;
        TileObject best=null;int bestDistance=Integer.MAX_VALUE;
        for(TileObject o:sceneObjects()) {
            if(id>=0&&o.getId()!=id)continue;
            ObjectComposition comp=c.getObjectDefinition(o.getId());
            if(action!=null&&(comp==null||actionIndex(comp.getActions(),action)<0))continue;
            int d=o.getWorldLocation().distanceTo2D(c.getLocalPlayer().getWorldLocation());
            if(d<bestDistance){bestDistance=d;best=o;}
        }
        return best;
    }
    protected static TileObject findCaveExit() {
        TileObject exit=findObject(-1,"Exit");
        return exit!=null?exit:findObject(-1,"Leave");
    }
    protected static Click objectClick(TileObject object,String action) {
        if(object==null)return null;
        ObjectComposition comp=client().getObjectDefinition(object.getId());
        if(comp==null)return null;
        int option=actionIndex(comp.getActions(),action);if(option<0)return null;
        MenuAction[] op={MenuAction.GAME_OBJECT_FIRST_OPTION,MenuAction.GAME_OBJECT_SECOND_OPTION,MenuAction.GAME_OBJECT_THIRD_OPTION,MenuAction.GAME_OBJECT_FOURTH_OPTION,MenuAction.GAME_OBJECT_FIFTH_OPTION};
        LocalPoint local=object.getLocalLocation();if(local==null)return null;
        int x=local.getSceneX(),y=local.getSceneY();
        if(object instanceof GameObject) {
            Point min=((GameObject)object).getSceneMinLocation();if(min!=null){x=min.getX();y=min.getY();}
        }
        java.awt.Shape hull=object.getClickbox();
        Rectangle rect=hull==null?new Rectangle(1,1,2,2):hull.getBounds();
        return new Click(new NewMenuEntry().option(action).target(comp.getName()).identifier(object.getId())
            .type(op[option]).param0(x).param1(y).gameObject(object),rect);
    }
    boolean object(int id,String action) {
        if(!uiReady(1800))return false;
        Click click=read(()->objectClick(findObject(id,action),action),null);
        if(invoke(click)){uiSent();return true;}return false;
    }
    boolean exitCave() {
        if(!uiReady(1800))return false;
        Click click=read(()->{
            Client c=client();WorldView v=c.getTopLevelWorldView();
            if(c.getGameState()!=GameState.LOGGED_IN||v==null||!v.isInstance()||c.getLocalPlayer()==null)return null;
            WorldPoint template=WorldPoint.fromLocalInstance(c,c.getLocalPlayer().getLocalLocation());
            if(template==null||template.getRegionID()!=WaveBook.REGION)return null;
            TileObject o=findCaveExit();if(o==null)return null;
            ObjectComposition comp=c.getObjectDefinition(o.getId());
            String verb=comp!=null&&actionIndex(comp.getActions(),"Exit")>=0?"Exit":"Leave";
            return objectClick(o,verb);
        },null);
        if(invoke(click)){uiSent();return true;}return false;
    }
    boolean confirmCaveExit() {
        if(!uiReady(1200)||!Rs2Dialogue.hasSelectAnOption())return false;
        // Only called after our exit dispatch, never during normal combat/dialogues.
        if(Rs2Dialogue.clickOption("Yes")){uiSent();return true;}return false;
    }
    String exitDiagnostic() {
        return read(()->{
            StringJoiner result=new StringJoiner(" | ");
            for(TileObject o:sceneObjects()) {
                ObjectComposition comp=client().getObjectDefinition(o.getId());if(comp==null)continue;
                String name=comp.getName()==null?"":comp.getName();
                if(!name.toLowerCase(Locale.ROOT).contains("cave")&&actionIndex(comp.getActions(),"Exit")<0&&actionIndex(comp.getActions(),"Leave")<0)continue;
                result.add(o.getId()+" "+name+" "+Arrays.toString(comp.getActions())+" "+o.getClass().getSimpleName()+" "+o.getWorldLocation());
                if(result.length()>1600)break;
            }
            return result.toString();
        },"Scene not ready");
    }
    /** Entry protection uses the same live acknowledgement as the visible writer. */
    boolean entryProtectionReady() {
        // Match the final entry check's live prayer state. The shared cached
        // accessor can disagree with the visible writer after startup toggles.
        return outsidePrayers(Protection.MELEE);
    }
    /** Re-read the predictor after supply/prayer/UI work, immediately before dispatch. */
    boolean enterPredictedRotation(FcPredictorGate gate,int expectedRotation) {
        if(!uiReady(1800))return false;
        synchronized(inputLock) {
            Click click=read(()->{
                Client c=client();WorldView v=c.getTopLevelWorldView();
                if(c.getGameState()!=GameState.LOGGED_IN||v==null||v.isInstance()||c.getLocalPlayer()==null)return null;
                if(!gate.entryReady(predictorOnClientThread(false),System.currentTimeMillis(),expectedRotation))return null;
                if(!c.isPrayerActive(Prayer.PROTECT_FROM_MELEE)||c.getBoostedSkillLevel(Skill.PRAYER)<=1)return null;
                TileObject entrance=findObject(11833,"Enter");
                if(entrance==null||entrance.getWorldLocation().distanceTo2D(c.getLocalPlayer().getWorldLocation())>4)return null;
                return objectClick(entrance,"Enter");
            },null);
            if(invoke(click)){uiSent();return true;}return false;
        }
    }
    /** One explicit Logout request while fighting; wave completion acknowledges the pause. */
    boolean requestWavePause(int world,java.util.function.BooleanSupplier permitted) {
        if(!uiReady(1200)||!permitted.getAsBoolean())return false;
        synchronized(inputLock) {
            if(Rs2Tab.getCurrentTab()!=InterfaceTab.LOGOUT){Rs2Tab.switchTo(InterfaceTab.LOGOUT);uiSent();return false;}
            // Native input re-runs this supplier after mouse travel and at the
            // final press. The final kill must cancel the click, not log out.
            Click request=read(()->pauseButton(world),null);
            if(request==null)return false;
            boolean sent=prayerUi.requestButton(request.entry(),()->{
                if(!permitted.getAsBoolean())return null;
                Click current=read(()->pauseButton(world),null);
                return current!=null&&current.entry().getParam1()==request.entry().getParam1()?current.bounds():null;
            });
            if(sent)uiSent();return sent;
        }
    }
    protected Click pauseButton(int world) {
        Client c=client();WorldView v=c.getTopLevelWorldView();Player player=c.getLocalPlayer();
        if(c.getGameState()!=GameState.LOGGED_IN||c.getWorld()!=world||v==null||!v.isInstance()||player==null)return null;
        WorldPoint point=WorldPoint.fromLocalInstance(c,player.getLocalLocation());
        if(point==null||point.getRegionID()!=WaveBook.REGION)return null;
        boolean fighting=false;
        for(var cached:Microbot.getRs2NpcCache().query().toList()) {
            NPC npc=cached.getNpc();
            if(npc!=null&&npc.getWorldView()==v&&!npc.isDead()&&npc.getTransformedComposition()!=null
                &&FcModel.Kind.identify(npc.getName(),FcFrame.npcTileSize(npc))!=null
                &&!(npc.getHealthRatio()==0&&npc.getHealthScale()>0)
                &&(npc.getInteracting()==player||player.getInteracting()==npc)){fighting=true;break;}
        }
        if(!fighting)return null;
        // 182:6 opens world switching; 182:8 is the actual Logout operation.
        Widget button=c.getWidget(69,25);int component=(69<<16)|25;
        if(button==null||button.isHidden()){button=c.getWidget(182,8);component=(182<<16)|8;}
        if(button==null||button.isHidden())return null;
        Rectangle bounds=FcPrayerUi.visibleBounds(button.getBounds(),c.getCanvasWidth(),c.getCanvasHeight());
        if(bounds==null)return null;
        return new Click(new NewMenuEntry().param0(-1).param1(component).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option("Logout"),bounds);
    }
    /** Executes a single preparation UI step. The caller gates the teleport animation/landing. */
    String minigameStep() {
        if(!uiReady(900))return "Waiting for minigame interface";
        if(Rs2Tab.getCurrentTab()!=InterfaceTab.CHAT){Rs2Tab.switchTo(InterfaceTab.CHAT);uiSent();return "Opening grouping tab";}
        Widget grouping=read(()->client().getWidget(46333957),null);
        if(grouping==null)return "Waiting for grouping button";
        boolean selected=read(()->Arrays.equals(grouping.getOnOpListener(),new Object[]{489,0,0}),false);
        if(!selected){clickMinigameWidget(grouping,"Grouping",1);return "Selecting grouping interface";}
        String current=read(()->{Widget w=client().getWidget(4980747);return w==null?"":w.getText();},"");
        if(!minigameName(current).equalsIgnoreCase("TzHaar Fight Pit")) {
            Widget dropdown=read(()->client().getWidget(4980760),null);
            if(dropdown==null)return "Waiting for minigame selector";
            if(read(dropdown::getSpriteId,-1)!=773){clickMinigameWidget(dropdown,"Select",1);return "Opening minigame list";}
            return selectTzhaarMinigame();
        }
        if(!read(FcActions::teleportStationary,false))return "Waiting to stop moving before minigame teleport";
        synchronized(inputLock) {
            if(prayerUi.button(()->read(FcActions::teleportBounds,null))){uiSent();return "TELEPORT_SENT";}
        }
        return "Waiting for verified visible minigame teleport button";
    }
    protected static String minigameName(String text) {
        return text==null?"":text.replaceAll("<[^>]*>","").replace("&nbsp;"," ").replace('\u00a0',' ').trim();
    }
    /** Retain the original index-based selection: offscreen dropdown rows still
     * have valid CC_OP targets. Anchor the input inside the visible dropdown,
     * rather than rejecting the row or moving to the old screen-corner point. */
    protected String selectTzhaarMinigame() {
        Click selection=read(()->{
            Widget list=client().getWidget(4980758);
            if(list==null||list.getDynamicChildren()==null||list.isHidden())return null;
            Rectangle bounds=minigameBounds(list);
            if(bounds==null)bounds=minigameBounds(client().getWidget(4980760));
            if(bounds==null)return null;
            for(Widget row:list.getDynamicChildren()) {
                if(row==null||!minigameName(row.getText()).equalsIgnoreCase("TzHaar Fight Pit"))continue;
                return new Click(new NewMenuEntry().option("Select").target("").identifier(1).type(MenuAction.CC_OP)
                    .param0(row.getIndex()).param1(list.getId()).itemId(-1),bounds);
            }
            return null;
        },null);
        if(invoke(selection)){uiSent();return "Selecting TzHaar Fight Pit";}
        return "TzHaar selection not sent; waiting for visible dropdown and destination row";
    }
    /** Native button listener, with a fresh target check before mouse press. */
    protected static Rectangle teleportBounds() {
        Client c=client();
        if(!teleportStationary()||c.isMenuOpen())return null;
        Widget selected=c.getWidget(4980747),button=c.getWidget(InterfaceID.Grouping.TELEPORT),text=c.getWidget(InterfaceID.Grouping.TELEPORT_TEXT1);
        if(selected==null||!minigameName(selected.getText()).equalsIgnoreCase("TzHaar Fight Pit")
            ||text==null||!minigameName(text.getText()).equalsIgnoreCase("Teleport"))return null;
        Rectangle outer=startupBounds(button),inner=startupBounds(text);
        if(outer==null||inner==null)return null;
        Rectangle target=outer.intersection(inner);
        return target.width>0&&target.height>0?target:null;
    }
    protected static boolean teleportStationary() {
        Client c=client();Player player=c==null?null:c.getLocalPlayer();
        if(c==null||c.getGameState()!=GameState.LOGGED_IN||player==null)return false;
        if(player.getPoseAnimation()!=player.getIdlePoseAnimation())return false;
        LocalPoint destination=c.getLocalDestinationLocation(),location=player.getLocalLocation();
        return destination==null||(location!=null&&destination.distanceTo(location)<128);
    }
    protected Rectangle minigameBounds(Widget widget){return startupBounds(widget);}
    /** Startup UI only: visible parent clipping, and no minimap click targets. */
    static Rectangle startupBounds(Widget widget) {
        if(widget==null||widget.isHidden())return null;
        Rectangle bounds=widget.getBounds();if(bounds==null)return null;
        bounds=new Rectangle(bounds);
        Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Widget parent=widget.getParent();parent!=null&&seen.add(parent);parent=parent.getParent()) {
            if(parent.isHidden())return null;
            Rectangle clip=parent.getBounds();if(clip!=null&&clip.width>0&&clip.height>0)bounds=bounds.intersection(clip);
        }
        Rectangle visible=FcPrayerUi.visibleBounds(bounds,client().getCanvasWidth(),client().getCanvasHeight());
        if(visible==null)return null;
        for(int id:new int[]{InterfaceID.Toplevel.MINIMAP,InterfaceID.ToplevelOsrsStretch.MINIMAP,InterfaceID.ToplevelPreEoc.MINIMAP}) {
            Widget map=client().getWidget(id);
            if(map==null||map.isHidden())continue;
            Rectangle area=map.getBounds();
            if(area!=null&&area.width>0&&area.height>0&&area.intersects(visible))return null;
        }
        return visible;
    }
    protected boolean clickMinigameWidget(Widget widget,String option,int operation) {
        Click click=read(()->{
            Rectangle bounds=minigameBounds(widget);if(bounds==null)return null;
            return new Click(new NewMenuEntry().option(option).target("").identifier(operation)
                .type(MenuAction.CC_OP).param0(widget.getIndex()).param1(widget.getId()).itemId(-1),bounds);
        },null);
        if(invoke(click)){uiSent();return true;}return false;
    }
    int resumeWorld(int current) {
        try {
            var worlds=Microbot.getWorldService().getWorlds();if(worlds==null)return -1;
            return worlds.getWorlds().stream().filter(w->w.getId()!=current&&w.getPlayers()>0&&w.getPlayers()<1900)
                .filter(w->w.getTypes().stream().anyMatch(t->t.name().equals("MEMBERS")))
                .filter(w->w.getTypes().stream().allMatch(t->t.name().equals("MEMBERS")))
                .sorted(Comparator.comparingInt(w->Math.abs(w.getId()-current))).mapToInt(w->w.getId()).findFirst().orElse(-1);
        }catch(Exception e){Microbot.log("[Dro Firecape] World list unavailable: "+e.getMessage());return -1;}
    }
    /** Sole hop call: optional, CONFIRMED between-wave recovery resume, never entry search. */
    boolean resumePausedWave(int world) {
        if(observe||world<=0)return false;
        boolean cave=read(()->{
            Client c=client();WorldView v=c.getTopLevelWorldView();
            if(c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null||v==null||!v.isInstance())return false;
            WorldPoint point=WorldPoint.fromLocalInstance(c,c.getLocalPlayer().getLocalLocation());
            if(point==null||point.getRegionID()!=WaveBook.REGION)return false;
            for(var cached:Microbot.getRs2NpcCache().query().toList()) {
                NPC n=cached.getNpc();if(n!=null&&n.getWorldView()==v&&n.getTransformedComposition()!=null
                &&FcModel.Kind.identify(n.getName(),n.getTransformedComposition().getSize())!=null
                &&n.getHealthRatio()!=0)return false;
            }
            return true;
        },false);
        return cave&&Microbot.hopToWorld(world);
    }
    /** Advance cave introduction only; never choose an exit/leave dialogue option. */
    boolean continueCaveDialogue() {
        if(!uiReady(650)||!Rs2Dialogue.hasContinue())return false;
        synchronized(inputLock){Rs2Dialogue.clickContinue();}
        uiSent();return true;
    }
    protected FcPredictorGate.Sample predictorOnClientThread(boolean inside) {
        Client c=client();int world=c==null?-1:c.getWorld();long now=System.currentTimeMillis();
        if(c==null||c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null)
            return FcPredictorGate.Sample.missing(world,now,"Predictor unavailable during world transition");
        if(!inside) {
            WorldView view=c.getTopLevelWorldView();
            boolean outside=view!=null&&!view.isInstance()&&WaveBook.outerTzhaarRegion(c.getLocalPlayer().getWorldLocation().getRegionID());
            if(!outside)return spawnPredictor.sample(world,-1,-1,false,now);
            return spawnPredictor.sample(world,c.getVarpValue(VarPlayerID.DATE_MINUTES),
                c.getVarbitValue(VarbitID.DATE_SECONDS_PAST_MINUTE),true,now);
        }
        // Optional resume hint only. Fresh entry and all spawn tables are internal.
        Plugin selected=null;boolean installed=false;
        for(Plugin plugin:Microbot.getPluginManager().getPlugins()) {
            String type=plugin.getClass().getName().toLowerCase(Locale.ROOT);
            // Also supports a relocated Plugin Hub build; do not require one package name.
            if(!type.contains("spawnpredictor"))continue;
            try {plugin.getClass().getMethod("getRotationCol");plugin.getClass().getMethod("getCurrentRotation");}
            catch(NoSuchMethodException e){continue;}
            catch(SecurityException|LinkageError e){return FcPredictorGate.Sample.missing(world,now,"Incompatible predictor API; entry blocked");}
            installed=true;
            if(!Microbot.getPluginManager().isPluginEnabled(plugin))continue;
            if(selected!=null)return FcPredictorGate.Sample.missing(world,now,"Multiple FC Spawn Predictors enabled; entry blocked");
            selected=plugin;
        }
        if(selected==null)return FcPredictorGate.Sample.missing(world,now,installed
            ?"Optional instance predictor disabled; using internal wave tracking"
            :"No optional instance hint; using saved run, wave messages and spawn evidence");
        return FcPredictorGate.read(selected,world,now,inside);
    }
    FcPredictorGate.Sample predictor(boolean inside) {
        return read(()->predictorOnClientThread(inside),FcPredictorGate.Sample.missing(-1,System.currentTimeMillis(),"Unable to read FC Spawn Predictor; entry blocked"));
    }
    int predictorWave(FcPredictorGate.Sample sample) {
        return sample==null||!sample.inside||sample.source==null?-1:read(()->FcPredictorGate.instanceWave(sample.source),-1);
    }
    int externalRotation(boolean inside) {
        FcPredictorGate.Sample sample=predictor(inside);return sample.ready?sample.rotation:-1;
    }    // Optional controller extension points. Normal call sites retain their original overloads.
    boolean special(java.util.function.BooleanSupplier valid){return valid.getAsBoolean()&&special();}
    boolean entryProtectionReady(boolean conservation){return entryProtectionReady();}
    boolean enterPredictedRotation(FcPredictorGate gate,int rotation,boolean conservation){return enterPredictedRotation(gate,rotation);}
}
