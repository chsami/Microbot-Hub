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
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection;

/** Cave actions are menu actions through FcInput: direct invokes or the Natural mouse (walking always
 * uses the mouse). Read lambdas return immutable dispatch details on the client thread. */
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
    private String minigamePromptSent="";
    protected volatile String lastAttackResult="No attack requested";
    protected int lastAttackIndex=-1;
    protected long attackGap=115;
    protected volatile boolean observe;
    protected volatile FcPrayers tickPrayers;
    void tickPrayerDriver(FcPrayers driver){tickPrayers=driver;}
    protected FcPrayers ownedPrayerDriver(){FcPrayers d=tickPrayers;return d!=null&&d.ownsInput()?d:null;}
    Protection attackProtection(Protection fallback){FcPrayers d=ownedPrayerDriver();return d==null?fallback:d.requested();}
    String selectedOffence(){FcPrayers d=tickPrayers;return d!=null?d.selectedOffence():"Detecting";}
    protected final Object inputLock=new Object();
    protected volatile String inputProblem="";
    String inputStatus(){return inputProblem;}
    protected final net.runelite.client.plugins.microbot.drofirecape.core.PrayerSwitchGuard combatPrayerGuard=new net.runelite.client.plugins.microbot.drofirecape.core.PrayerSwitchGuard(COMBAT_PRAYERS.length);
    void observeOnly(boolean value){observe=value;}
    /** "Manual movement": no cave walk is ever sent; the player moves. */
    private volatile java.util.function.BooleanSupplier manualMovement=()->false;
    void manualMovement(java.util.function.BooleanSupplier value){manualMovement=value==null?()->false:value;}
    void reset(){combatPrayerGuard.reset();inputProblem="";uiAt=lastAttackAt=0;lastAttackIndex=-1;minigamePromptSent="";}
    static <T>T read(Callable<T> r,T fallback) {
        var thread=Microbot.getClientThread();
        return thread==null?fallback:thread.runOnClientThreadOptional(r).orElse(fallback);
    }
    protected static Client client(){return Microbot.getClient();}
    protected volatile FcArenaInput arena;
    protected volatile FcInput input=new FcInput(()->FcFightInput.NATURAL_MOUSE,null);
    void arenaInput(FcArenaInput arena,FcInput input){this.arena=arena;this.input=input;}
    FcInput input(){return input;}
    /** Minimap walking and plain UI presses use the arena fight mouse while its lease is held. */
    protected <T>T gesture(java.util.function.Supplier<T> action,T cancelled){FcArenaInput a=arena;return a==null?action.get():a.fight(action,cancelled);}
    /** Walking: a protection prayer queued during the travel takes the pointer first. */
    protected <T>T walkGesture(java.util.function.Supplier<T> action,T cancelled) {
        FcArenaInput a=arena;if(a==null)return action.get();
        long urgency=input.protectionUrgency();
        return a.fight(action,cancelled,()->input.protectionUrgency()==urgency);
    }
    protected boolean invoke(Click click){return invoke(click,()->true);}
    protected boolean invoke(Click click,java.util.function.BooleanSupplier live) {
        if(observe||click==null||(click.bounds()==null&&!input.invokes(click.entry().getType())))return false;
        synchronized(inputLock){return input.dispatch(click.entry(),click.bounds(),live);}
    }
    /** Opens the tab with the client script (no F-keys or tab clicks), then sends the widget action. */
    protected boolean widgetAction(InterfaceTab tab,NewMenuEntry entry,Callable<Rectangle> bounds,java.util.function.BooleanSupplier live) {
        if(observe||!live.getAsBoolean())return false;
        synchronized(inputLock) {
            Rectangle area=read(()->{
                if(tab!=null&&!FcInput.openTab(client(),tab)){inputProblem="Waiting for "+tab.getName()+" tab";return null;}
                Rectangle b=bounds.call();
                if(b==null)inputProblem="Waiting for visible "+entry.getOption()+" button";
                return b;
            },null);
            if(area==null&&!input.invokes(entry.getType()))return false;
            boolean sent=input.dispatch(entry,area,live);
            if(sent)inputProblem="";
            return sent;
        }
    }
    protected boolean uiReady(long gap){return !observe&&System.currentTimeMillis()-uiAt>=gap;}
    protected void uiSent(){uiAt=System.currentTimeMillis();}

    protected static final Rs2PrayerEnum[] COMBAT_PRAYERS={Rs2PrayerEnum.PROTECT_MELEE,
        Rs2PrayerEnum.PROTECT_RANGE,Rs2PrayerEnum.PROTECT_MAGIC,
        Rs2PrayerEnum.SHARP_EYE,Rs2PrayerEnum.HAWK_EYE,Rs2PrayerEnum.EAGLE_EYE,
        Rs2PrayerEnum.DEAD_EYE,Rs2PrayerEnum.RIGOUR,
        Rs2PrayerEnum.BURST_STRENGTH,Rs2PrayerEnum.SUPERHUMAN_STRENGTH,Rs2PrayerEnum.ULTIMATE_STRENGTH,
        Rs2PrayerEnum.CLARITY_THOUGHT,Rs2PrayerEnum.IMPROVED_REFLEXES,Rs2PrayerEnum.INCREDIBLE_REFLEXES,
        Rs2PrayerEnum.CHIVALRY,Rs2PrayerEnum.PIETY};
    /** Entry/exit acknowledgement only; cave combat is owned by FcPrayers. */
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
    /** The background prayer worker's Natural mouse press shares the cave input lock. */
    void prayerPulse(FcPrayers driver){synchronized(inputLock){driver.pulse();}}
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
        Rs2PrayerEnum prayer=COMBAT_PRAYERS[channel];
        if(combatPrayerGuard.shouldToggle(channel,state.active[channel],desired,state.tick,now)
            &&!widgetAction(InterfaceTab.PRAYER,FcPrayers.entry(prayer,desired),()->{
                Widget w=client().getWidget(prayer.getIndex());
                return w==null||w.isHidden()?null:FcInput.visibleBounds(w.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
            },()->read(()->outsidePrayerScene()&&(client().getVarbitValue(prayer.getVarbit())==1)!=desired,false)))
            combatPrayerGuard.cancel(channel);
    }
    /** A missing or suspended cave owner never falls back to a second writer. Pure fights at zero prayer. */
    boolean combatProtect(Protection ignored) {
        FcPrayers driver=ownedPrayerDriver();return driver!=null&&(driver.protectionReady()||driver.exhaustedCombatReady());
    }
    boolean overheadActive(Protection ignored){return combatProtect(ignored);}
    boolean staminaActive(){return Rs2Player.hasStaminaBuffActive();}
    boolean offenceReady(){FcPrayers driver=ownedPrayerDriver();return driver!=null&&driver.offenceReady();}
    void prayersOff(){if(!observe)outsidePrayers(Protection.NONE);}
    boolean prayersObservedOff(){FcPrayers driver=ownedPrayerDriver();return driver!=null&&driver.prayersOffReady();}
    void idlePrayersOff(){if(uiReady(1200)){prayersOff();uiSent();}}
    /** Varp 172 is 1 while auto retaliate is off. */
    boolean autoRetaliateOff() {
        if(!uiReady(900))return false;
        boolean sent=widgetAction(InterfaceTab.COMBAT,new NewMenuEntry().param0(-1).param1(InterfaceID.CombatInterface.RETALIATE)
            .type(MenuAction.CC_OP).identifier(1).itemId(-1).option("Auto retaliate").target(""),()->{
                Widget w=client().getWidget(InterfaceID.CombatInterface.RETALIATE);
                return w==null||w.isHidden()?null:FcInput.visibleBounds(w.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
            },()->read(()->client().getVarpValue(172)==0,false));
        if(sent)uiSent();
        return sent;
    }
    void enableRun() {
        widgetAction(null,new NewMenuEntry().param0(-1).param1(InterfaceID.Orbs.RUNBUTTON).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option("Toggle Run").target(""),()->{
                Widget w=client().getWidget(InterfaceID.Orbs.RUNBUTTON);
                return w==null||w.isHidden()?null:FcInput.visibleBounds(w.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
            },()->read(()->client().getVarpValue(173)==0&&client().getEnergy()>Math.max(0,Microbot.runEnergyThreshold),false));
    }
    boolean zoomConfirmed(){return read(Rs2Camera::getZoom,-1)>=0;}

    boolean move(FcFrame f,FcModel.Tile scene){return move(f,scene,true);}
    boolean move(FcFrame f,FcModel.Tile scene,boolean minimapFirst) {
        if(observe||f==null||f.stale()||scene==null||Microbot.pauseAllScripts.get()||InputArbiter.isHuman())return false;
        if(f.cave&&manualMovement.getAsBoolean())return false;
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
        return player.getWorldLocation()!=null&&FcFrame.trueTile(player,v).equals(expected.model.player());
    }
    protected boolean minimapWalk(WorldPoint target,FcFrame expected) {
        Point minimap=read(()->{
            if(!currentPosition(expected))return null;
            Point p=Rs2MiniMap.worldToMinimap(target);
            return p!=null&&Rs2MiniMap.isPointInsideMinimap(p)?p:null;
        },null);
        if(minimap==null)return false;
        synchronized(inputLock){return walkGesture(()->{Microbot.getMouse().click(minimap);return true;},false);}
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
                .where(n->n.getIndex()==index).toListOnClientThread()) {
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
        if(invoke(click,()->!Microbot.pauseAllScripts.get()&&!InputArbiter.isHuman())) {
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
                .where(n->index<0||n.getIndex()==index).toListOnClientThread()) {
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
        synchronized(inputLock) {
            if(!read(()->FcInput.openTab(client(),InterfaceTab.INVENTORY),false)){inputProblem="Waiting for Inventory tab";return ItemResult.PREPARING;}
            Click click=inventoryClick(item,action);
            if(click==null) {
                inputProblem="Inventory item/button not yet visible: slot "+item.slot()+" action "+action;
                return ItemResult.PREPARING;
            }
            // The item must still be in its slot when the action is delivered.
            boolean sent=input.dispatch(click.entry(),click.bounds(),()->permitted.getAsBoolean()&&slotHolds(item));
            if(sent)inputProblem="";
            return sent?ItemResult.SENT:ItemResult.PREPARING;
        }
    }
    /** Client thread: the inventory slot still holds this item. */
    protected static boolean slotHolds(FcFrame.ItemSlot item) {
        ItemContainer inv=client().getItemContainer(InventoryID.INVENTORY);
        Item actual=inv==null?null:inv.getItem(item.slot());
        return actual!=null&&actual.getId()==item.id();
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
                Rectangle bounds=FcInput.visibleBounds(slot.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
                int option=actionIndex(slot.getActions(),action);
                if(bounds==null||option<0)return null;
                return new Click(new NewMenuEntry().option(action).target(item.name()).param0(item.slot())
                    .param1(inventory.getId()).type(MenuAction.CC_OP).identifier(option+1).itemId(item.id()).widget(slot),bounds);
            }
            return null;
        },null);
    }
    boolean spellbook(){synchronized(inputLock){return read(()->FcInput.openTab(client(),InterfaceTab.MAGIC),false);}}
    boolean spell(int id,String name) {
        return widgetAction(InterfaceTab.MAGIC,new NewMenuEntry().option("Cast").target(name)
            .param0(-1).param1(id).type(MenuAction.CC_OP).identifier(1).itemId(-1),()->{
                Widget widget=client().getWidget(id);
                return widget==null||widget.isHidden()?null:FcInput.visibleBounds(widget.getBounds(),
                    client().getCanvasWidth(),client().getCanvasHeight());
            },()->true);
    }
    /**
     * Selects a combat spell and casts it on the NPC in one client-thread operation, sent directly in both input
     * modes: a prayer click clears a selected spell, so a flick between a separate select and cast click dropped the
     * cast (live 1.1.33: Ice Burst selected, then Deactivate Protect from Melee, then a plain Attack). When the game
     * has not shown the selection yet, the cast is retried on the next loop.
     */
    boolean cast(net.runelite.client.plugins.skillcalculator.skills.MagicAction spell,int index,String name,FcFrame expected) {
        if(observe||spell==null)return false;
        int widget=spell.getWidgetId();
        synchronized(inputLock) {
            Boolean sent=read(()->{
                Client c=client();
                if(!selected(widget)) {
                    if(!FcInput.openTab(c,InterfaceTab.MAGIC))return null;
                    Widget w=c.getWidget(widget);
                    if(w==null||w.isHidden())return null;
                    // A spell is selected with WIDGET_TARGET, identifier 0 (the player's own click, live 2026-11-06);
                    // CC_OP 1 on the spell icon never selected it, so no cast followed.
                    c.menuAction(-1,widget,MenuAction.WIDGET_TARGET,0,-1,"Cast",spell.getName());
                    if(!selected(widget))return null;
                }
                Click base=npcClick(index,name,"Attack",expected);
                if(base==null)return false;
                NewMenuEntry e=base.entry();
                FcMenuEntry cast=FcMenuEntry.guarded(new NewMenuEntry().option("Cast").target(spell.getName()+" -> "+e.getTarget())
                    .param0(0).param1(0).type(MenuAction.WIDGET_TARGET_ON_NPC).identifier(e.getIdentifier()).itemId(-1)
                    .actor(e.getActor()).worldViewId(e.getWorldViewId()),
                    ()->selected(widget)&&!Microbot.pauseAllScripts.get());
                return FcInput.invokeNow(c,cast);
            },false);
            if(sent==null){inputProblem="Selecting "+spell.getName()+"; casting next loop";return false;}
            if(!sent){inputProblem="No castable target for "+spell.getName();return false;}
            lastAttackAt=System.currentTimeMillis();lastAttackIndex=index;inputProblem="";
            return true;
        }
    }
    /** Client thread: this spell widget is the selected target spell. */
    protected static boolean selected(int widget) {
        Client c=client();
        if(!c.isWidgetSelected())return false;
        Widget w=c.getSelectedWidget();
        return w!=null&&w.getId()==widget;
    }
    boolean special(){return special(()->true);}
    boolean special(java.util.function.BooleanSupplier valid) {
        if(!uiReady(1800)||!valid.getAsBoolean())return false;
        int id=read(()->{
            Client c=client();
            if(c.getGameState()!=GameState.LOGGED_IN||c.getVarpValue(300)<500||c.getVarpValue(301)!=0)return -1;
            for(int candidate:new int[]{InterfaceID.Orbs.SPECBUTTON,10485795}) {
                Widget w=c.getWidget(candidate);if(w!=null&&!w.isHidden())return candidate;
            }
            return -1;
        },-1);
        if(id<0)return false;
        boolean sent=widgetAction(null,new NewMenuEntry().param0(-1).param1(id).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option("Use").target(""),()->{
                Widget w=client().getWidget(id);
                return w==null||w.isHidden()?null:FcInput.visibleBounds(w.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
            },()->valid.getAsBoolean()&&read(()->client().getVarpValue(300)>=500&&client().getVarpValue(301)==0,false));
        if(sent)uiSent();
        return sent;
    }
    /** Rapid is combat mode 1 for bows, crossbows, the blowpipe and thrown weapons (varp 43), as the arenas verify. */
    boolean rapid() {
        if(!uiReady(900))return false;
        boolean sent=widgetAction(InterfaceTab.COMBAT,new NewMenuEntry().param0(-1)
            .param1(net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_TWO.getId()).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option("Rapid").target(""),()->{
                Widget w=client().getWidget(net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_TWO);
                return w==null||w.isHidden()?null:FcInput.visibleBounds(w.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
            },()->read(()->client().getVarpValue(43)!=1,false));
        if(sent)uiSent();
        return sent;
    }
    /** Autocast selection through the Combat tab, one step per call (open the list, then choose the spell). */
    boolean autocast(net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells spell) {
        if(observe||spell==null||!uiReady(600))return false;
        boolean listOpen=read(()->{Widget w=client().getWidget(201,1);return w!=null&&!w.isHidden();},false);
        if(!listOpen) {
            int box=net.runelite.api.widgets.WidgetInfo.COMBAT_SPELL_BOX.getId();
            boolean sent=widgetAction(InterfaceTab.COMBAT,new NewMenuEntry().param0(-1).param1(box).type(MenuAction.CC_OP)
                .identifier(1).itemId(-1).option("Choose spell").target(""),()->{
                    Widget w=client().getWidget(box);
                    return w==null||w.isHidden()?null:FcInput.visibleBounds(w.getBounds(),client().getCanvasWidth(),client().getCanvasHeight());
                },()->true);
            if(sent)uiSent();
            return false;
        }
        Click choice=read(()->{
            Widget list=client().getWidget(201,1);
            if(list==null||list.isHidden())return null;
            // As the arenas: the entry whose op is the spell name, then the spell sprite.
            String name=spell.getMagicAction().getName();int sprite=spell.getMagicAction().getSprite();
            Widget match=null;int op=-1;
            for(Widget[] group:new Widget[][]{list.getDynamicChildren(),list.getStaticChildren(),list.getNestedChildren()}) {
                if(group==null)continue;
                for(Widget w:group) {
                    if(w==null||w.isHidden()||w.getActions()==null)continue;
                    String[] actions=w.getActions();
                    for(int i=0;i<actions.length&&match==null;i++)if(name.equalsIgnoreCase(actions[i])){match=w;op=i;}
                    if(match==null&&w.getSpriteId()==sprite)for(int i=0;i<actions.length&&op<0;i++)
                        if(actions[i]!=null&&!actions[i].isEmpty()){match=w;op=i;}
                    if(match!=null)break;
                }
                if(match!=null)break;
            }
            if(match==null)return null;
            return new Click(new NewMenuEntry().param0(match.getIndex()).param1(match.getId())
                .type(MenuAction.CC_OP).identifier(op+1).itemId(-1).option(match.getActions()[op]).target(name),
                FcInput.visibleBounds(match.getBounds(),client().getCanvasWidth(),client().getCanvasHeight()));
        },null);
        if(choice==null){inputProblem="Autocast list has no "+spell.getName();return false;}
        boolean sent=invoke(choice);
        if(sent)uiSent();
        return sent;
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
    /** The cave is entered with every combat prayer off; the tick driver flicks the first wave's hits. */
    boolean entryProtectionReady() {
        // Match the final entry check's live prayer state, not a cached accessor.
        return outsidePrayers(Protection.NONE);
    }
    /** Re-read the predictor after supply/prayer/UI work, immediately before dispatch. */
    boolean enterPredictedRotation(FcPredictorGate gate,int expectedRotation) {
        if(!uiReady(1800))return false;
        synchronized(inputLock) {
            Click click=read(()->{
                Client c=client();WorldView v=c.getTopLevelWorldView();
                if(c.getGameState()!=GameState.LOGGED_IN||v==null||v.isInstance()||c.getLocalPlayer()==null)return null;
                if(!gate.entryReady(predictorOnClientThread(false),System.currentTimeMillis(),expectedRotation))return null;
                if(c.getBoostedSkillLevel(Skill.PRAYER)<=1)return null;
                // Recheck every combat prayer slot after the outside cleanup.
                for(Rs2PrayerEnum prayer:COMBAT_PRAYERS)if(c.getVarbitValue(prayer.getVarbit())==1)return null;
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
            if(!read(()->FcInput.openTab(client(),InterfaceTab.LOGOUT),false)){uiSent();return false;}
            Click request=read(()->pauseButton(world),null);
            if(request==null)return false;
            // Re-checked at delivery: the final kill must cancel the request, not log out.
            boolean sent=invoke(request,()->permitted.getAsBoolean()&&read(()->{
                Click current=pauseButton(world);
                return current!=null&&current.entry().getParam1()==request.entry().getParam1();
            },false));
            if(sent)uiSent();return sent;
        }
    }
    protected Click pauseButton(int world) {
        Client c=client();WorldView v=c.getTopLevelWorldView();Player player=c.getLocalPlayer();
        if(c.getGameState()!=GameState.LOGGED_IN||c.getWorld()!=world||v==null||!v.isInstance()||player==null)return null;
        WorldPoint point=WorldPoint.fromLocalInstance(c,player.getLocalLocation());
        if(point==null||point.getRegionID()!=WaveBook.REGION)return null;
        boolean fighting=false;
        for(var cached:Microbot.getRs2NpcCache().query().toListOnClientThread()) {
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
        Rectangle bounds=FcInput.visibleBounds(button.getBounds(),c.getCanvasWidth(),c.getCanvasHeight());
        if(bounds==null)return null;
        return new Click(new NewMenuEntry().param0(-1).param1(component).type(MenuAction.CC_OP)
            .identifier(1).itemId(-1).option("Logout"),bounds);
    }
    /** Executes a single preparation UI step. The caller gates the teleport animation/landing. */
    String minigameStep() {
        // Bank closure is a separate observed step; never open grouping or teleport behind it.
        if(Rs2Bank.isOpen()) {
            closeBank();
            return "Waiting for bank closure before minigame teleport";
        }
        if(!uiReady(900))return "Waiting for minigame interface";
        if(!read(()->FcInput.openTab(client(),InterfaceTab.CHAT),false)){uiSent();return "Opening grouping tab";}
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
        Rectangle teleport=read(FcActions::teleportBounds,null);
        if(teleport!=null) synchronized(inputLock) {
            // Outside the cave: a plain Natural mouse press on the verified, stationary button.
            if(gesture(()->{
                if(read(FcActions::teleportBounds,null)==null)return false;
                Microbot.getMouse().click(teleport);return true;
            },false)){minigamePromptSent="";uiSent();return "TELEPORT_SENT";}
        }
        return "Waiting for verified visible minigame teleport button";
    }
    /** Only called while our Fight Pit teleport is awaiting its landing. */
    String minigameDialogueStep() {
        String option=read(()->{
            if(!Rs2Dialogue.hasSelectAnOption())return null;
            for(Widget widget:Rs2Dialogue.getDialogueOptions()) {
                if(widget==null||widget.isHidden())continue;
                String text=net.runelite.client.util.Text.removeTags(Objects.toString(widget.getText(),"")).trim();
                String normalized=text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
                if(normalized.equals("noanddontaskagain")||normalized.equals("nodontaskagain"))return text;
            }
            return null;
        },null);
        if(option!=null) {
            if(!"choice".equals(minigamePromptSent)&&uiReady(900)) {
                synchronized(inputLock) {
                    if(Rs2Dialogue.clickOption(option,true)){minigamePromptSent="choice";uiSent();}
                }
            }
            return "Confirming No, and don't ask again for Fight Pit teleport";
        }
        boolean notice=read(()->{
            if(!Rs2Dialogue.hasContinue())return false;
            String text=minigameName(Rs2Dialogue.getDialogueText()).toLowerCase(Locale.ROOT);
            return text.contains("more players")&&text.contains("fight pits")&&text.contains("world");
        },false);
        if(!notice)return null;
        if(!"notice".equals(minigamePromptSent)&&uiReady(900)) {
            synchronized(inputLock){Rs2Dialogue.clickContinue();}
            minigamePromptSent="notice";uiSent();
        }
        return "Continuing Fight Pit suggested-world notice";
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
        if(Rs2Bank.isOpen())return null;
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
        Rectangle visible=FcInput.visibleBounds(bounds,client().getCanvasWidth(),client().getCanvasHeight());
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
            for(var cached:Microbot.getRs2NpcCache().query().toListOnClientThread()) {
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
    }
}
