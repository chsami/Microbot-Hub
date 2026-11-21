package net.runelite.client.plugins.microbot.autofarmer.randomevents;

import javax.inject.Inject;
import java.awt.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.*;

/** Reusable event support owned by a skilling plugin; not a standalone plugin. */
public final class RandomEventSupport {
    @Inject private net.runelite.client.eventbus.EventBus eventBus;
    private volatile boolean externalHelperActive;
    private boolean registered;
    private final EventFoodTracker foodTracker = new EventFoodTracker();
    private java.util.Map<Integer,Integer> inventoryCounts() {
        java.util.Map<Integer,Integer> result = new java.util.HashMap<>();
        for (net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item : Rs2Inventory.all()) result.merge(item.getId(),item.getQuantity(),Integer::sum);
        return result;
    }
    public boolean isHandling(){return enabled && !externalHelperActive && owner!=null;}
    public boolean dropFoodReward() {
        if (owner != null || externalHelperActive || !enabled || Rs2Dialogue.isInDialogue()) return false;
        for (int id : EventFoodTracker.FOOD) {
            int before = Rs2Inventory.itemQuantity(id);
            if (foodTracker.allowance(id,before) <= 0) continue;
            if (net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen()) {
                net.runelite.client.plugins.microbot.util.bank.Rs2Bank.closeBank(); return true;
            }
            if (Rs2Inventory.drop(id) && net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id)<before,2500)) {
                foodTracker.dropped(id,before-Rs2Inventory.itemQuantity(id));
                Microbot.log("Dropped ordinary food received from random event (item " + id + ")");
            }
            return true;
        }
        return false;
    }

    private boolean hasExternalHelper() {
        for (Plugin plugin : Microbot.getPluginManager().getPlugins()) {
            if (plugin.getClass().getSimpleName().equals("LocalRandomEventPlugin") && Microbot.isPluginEnabled(plugin)) return true;
        }
        return false;
    }
    @Inject private Client client;
    @Inject private OverlayManager overlays;
    private volatile NPC owner;
    private volatile boolean enabled;
    private volatile boolean manual;
    private volatile String status="Waiting for your random event";
    private String eventName="", requestSpeech="";
    private int eventId=-1;
    private long beganAt,nextActionAt,absentSince;
    private boolean talked;
    private int sandwichRefreshes;
    private int dismissAttempts;
    private volatile NPC ignoredNpc;
    private volatile long ignoreUntil;
    private final BeekeeperSolver beekeeper = new BeekeeperSolver();
    private final PuzzleEventSolver puzzles = new PuzzleEventSolver();
    private final Object lock=new Object();
    private BlockingEventManager manager;
    private final BlockingEvent event=new BlockingEvent() {
        @Override public boolean validate(){ return enabled && !externalHelperActive && owner!=null; }
        @Override public BlockingEventPriority priority(){ return BlockingEventPriority.HIGH; }
        @Override public String getName(){return "Built-in random event (XP rewards only)";}
        @Override public boolean execute(){
            synchronized(lock) {
                if(!validate()) return true;
                // Stay within the same blocking task until completion or manual handling.
                long deadline=System.currentTimeMillis()+(RandomEventPolicy.puzzle(RandomEventPolicy.kind(eventId))?660_000L:240_000L);
                while(validate() && !Thread.currentThread().isInterrupted()) {
                    try {
                        if(!ScriptPauseController.isPaused() && !Microbot.pauseAllScripts.get()) step();
                    } catch(RuntimeException failure) {
                        Microbot.log("Random event handler exception: "+failure);
                        hold("Event handler could not confirm game state");
                    }
                    if(System.currentTimeMillis()>deadline && !manual) hold("Event timed out");
                    try { Thread.sleep(300L); } catch(InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                return true;
            }
        }
    };
    private final Overlay overlay=new Overlay() {
        {setPosition(OverlayPosition.TOP_RIGHT);setLayer(OverlayLayer.ABOVE_WIDGETS);}
        @Override public Dimension render(Graphics2D source){
            if (!enabled || externalHelperActive || owner == null) return null;
            Graphics2D g=(Graphics2D)source.create();
            try {
                g.setColor(new Color(0,0,0,215));g.fillRoundRect(0,0,380,70,8,8);
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));g.setColor(Color.CYAN);
                g.drawString("Auto Farmer - random event",9,18);
                g.setColor(manual?Color.ORANGE:Color.WHITE);
                String line=status; if(line.length()>57) line=line.substring(0,54)+"...";
                g.drawString(line,9,39);g.setColor(Color.LIGHT_GRAY);
                g.drawString(manual?(RandomEventPolicy.puzzle(RandomEventPolicy.kind(eventId)) || RandomEventPolicy.kind(eventId)==RandomEventPolicy.Kind.BEEKEEPER?"Finish event; resumes after your return":"Finish manually; toggle Built-in random events to resume"):"Handles event; farming resumes after your return",9,59);
                return new Dimension(380,70);
            }finally{g.dispose();}
        }
    };
    public void start(){
        if (registered) return;
        externalHelperActive=hasExternalHelper();
        eventBus.register(this);
        registered=true;
        owner=null;manual=false;talked=false;status="Waiting for your random event";enabled=true;
        manager=Microbot.getBlockingEventManager(); manager.add(event);overlays.add(overlay);
    }
    public void stop(){
        if (!registered) return;
        eventBus.unregister(this);
        registered=false;
        enabled=false;owner=null;
        if(manager!=null) manager.remove(event);
        overlays.remove(overlay);
    }
    @Subscribe public void onAnimationChanged(AnimationChanged change){
        if(enabled && !externalHelperActive && owner!=null)puzzles.animation(client,change);
    }
    @Subscribe public void onChatMessage(ChatMessage message){
        if(enabled && !externalHelperActive && owner!=null)puzzles.chat(client,message);
    }
    @Subscribe public void onInteractingChanged(InteractingChanged change){
        if (externalHelperActive) return;
        if(!(change.getSource() instanceof NPC)) return;
        claim((NPC)change.getSource(),change.getTarget());
    }
    @Subscribe public void onGameTick(GameTick tick){
        externalHelperActive=hasExternalHelper();
        if (externalHelperActive) {
            owner=null; manual=false; talked=false;
            status="Using standalone Local Random Event Helper";
            return;
        }
        if(!enabled || owner!=null || client.getGameState()!=GameState.LOGGED_IN || client.getTopLevelWorldView()==null) return;
        for(NPC npc:client.getTopLevelWorldView().npcs()) {
            if((npc.getId()==3235 || PuzzleEventSolver.arenaNpc(npc.getId(),PuzzleEventSolver.region(client))) && owner==null) claim(npc,client.getLocalPlayer());
            else claim(npc,npc.getInteracting());
            if(owner!=null) break;
        }
    }
    private void claim(NPC npc,Actor target){
        if(!enabled || externalHelperActive || owner!=null || npc==ignoredNpc && System.currentTimeMillis()<ignoreUntil) return;
        Player local=client.getLocalPlayer();
        if(local==null || npc.getWorldLocation()==null || local.getWorldLocation()==null) return;
        if(!((npc.getId()==3235 || PuzzleEventSolver.arenaNpc(npc.getId(),PuzzleEventSolver.region(client))) && target==local) && !RandomEventPolicy.ownedEvent(npc.getId(),target==local,
            npc.getWorldLocation().distanceTo(local.getWorldLocation()),npc.getWorldLocation().getPlane(),local.getWorldLocation().getPlane())) return;
        foodTracker.begin(inventoryCounts());
        eventId=npc.getId();eventName=npc.getName();requestSpeech="";talked=false;sandwichRefreshes=0;dismissAttempts=0;beekeeper.reset();puzzles.reset(RandomEventPolicy.kind(eventId));
        beganAt=System.currentTimeMillis();nextActionAt=beganAt+1_200L;absentSince=0L;
        manual=!RandomEventPolicy.dismissInvitation(eventId) && RandomEventPolicy.kind(eventId)==RandomEventPolicy.Kind.MANUAL;
        status=manual?"Manual event: "+eventName:"Handling "+eventName;
        net.runelite.client.plugins.microbot.util.walker.Rs2Walker.setTarget(null);
        Microbot.log("Random event claimed: "+eventName+" ("+eventId+")");
        owner=npc; // publish fully initialized session last
    }
    private void hold(String reason){if(!manual)Microbot.log("Random event needs help: "+eventName+": "+reason);manual=true;status=reason+" - manual help needed";}
    void step(){
        if(RandomEventPolicy.dismissInvitation(eventId)){dismissNonXpEvent();return;}

        if(RandomEventPolicy.puzzle(RandomEventPolicy.kind(eventId))) {
            if(System.currentTimeMillis()<nextActionAt)return;
            nextActionAt=System.currentTimeMillis()+500L;
            PuzzleEventSolver.Result result=puzzles.step(client,manual);
            if(!manual)status=eventName+": "+puzzles.status;
            if(result==PuzzleEventSolver.Result.COMPLETE){
                foodTracker.complete(inventoryCounts());owner=null;manual=false;status="Event finished - resuming farming";
                Microbot.log("Random event return confirmed; resuming farming");
            }
            if(result==PuzzleEventSolver.Result.MANUAL)hold(puzzles.status);
            return;
        }
        if(RandomEventPolicy.kind(eventId)==RandomEventPolicy.Kind.BEEKEEPER) {
            if (System.currentTimeMillis()<nextActionAt) return;
            nextActionAt=System.currentTimeMillis()+1200L;
            BeekeeperSolver.Result result=beekeeper.step(client,manual);
            if(!manual) status="Handling Bee keeper: "+beekeeper.status;
            if(result==BeekeeperSolver.Result.COMPLETE){foodTracker.complete(inventoryCounts());owner=null;manual=false;status="Bee keeper finished - resuming farming";}
            if(result==BeekeeperSolver.Result.MANUAL) hold(beekeeper.status);
            return;
        }
        if(manual) {
            RandomEventPolicy.Kind kind=RandomEventPolicy.kind(eventId);
            if(kind!=RandomEventPolicy.Kind.GIFT && kind!=RandomEventPolicy.Kind.SANDWICH)return;
            Frame observed=Microbot.getClientThread().runOnClientThreadOptional(this::snapshot).orElse(null);
            if(observed==null || !observed.loggedIn)return;
            long time=System.currentTimeMillis();
            if(!observed.present && !observed.dialogue && !observed.tray){
                if(absentSince==0)absentSince=time;
                if(time-absentSince>=1800){foodTracker.complete(inventoryCounts());owner=null;manual=false;status="Event ended - resuming farming";Microbot.log(status);}
                return;
            }
            absentSince=0;
            if(kind==RandomEventPolicy.Kind.SANDWICH && observed.tray && observed.foodWidget!=null && !observed.otherSpeaker){
                manual=false;sandwichRefreshes=0;beganAt=time;nextActionAt=0;Microbot.log("Sandwich tray resolved after refresh; resuming event");
            }else return;
        }
        long now=System.currentTimeMillis();
        if(now<nextActionAt) return;
        if(now-beganAt>90_000L){hold("No confirmed completion");return;}
        Frame f=Microbot.getClientThread().runOnClientThreadOptional(this::snapshot).orElse(null);
        if(f==null || !f.loggedIn || !event.validate()) return;
        if(!f.present && !f.dialogue && !f.tray){
            if(absentSince==0L) absentSince=now;
            if(now-absentSince>=1_800L){foodTracker.complete(inventoryCounts()); owner=null;status="Event ended - rewards ready for farmer";}
            return;
        }
        absentSince=0L;
        if(f.otherSpeaker){hold("Different dialogue opened");return;}
        if(f.full && !talked){hold("Free inventory space for event reward");return;}
        nextActionAt=now+1_200L;
        if(f.tray){
            if(RandomEventPolicy.kind(eventId)!=RandomEventPolicy.Kind.SANDWICH){hold("Unexpected event interface");return;}
            if(f.foodWidget==null){
                if(++sandwichRefreshes<6){status="Waiting for sandwich tray to load";return;}
                Microbot.log("Sandwich tray diagnostic: "+f.trayDiagnostic);
                hold("Cannot identify requested sandwich item");return;
            }
            sandwichRefreshes=0;
            Rs2Widget.clickWidget(f.foodWidget);return;
        }
        if(f.options){hold("Dialogue needs a manual choice");return;}
        if(f.canContinue && f.ownedDialogue){Rs2Dialogue.clickContinue();return;}
        if(f.dialogue){hold("Unrecognized dialogue");return;}
        if(f.moving || f.animating) return;
        if(!talked && f.present && f.targetingLocal && f.talkable){
            talked=Rs2Npc.interact(owner,"Talk-to");status="Talking to "+eventName;return;
        }
        // Allow a slow server response; do not repeatedly click an NPC after a successful talk.
        if(talked && now-beganAt>25_000L) hold("Waiting for reward or event dialogue");
    }
    private void releaseDismissed(String message,long now) {
        ignoredNpc=owner;ignoreUntil=now+90000L;owner=null;manual=false;status=message;Microbot.log(message);
    }
    private void dismissNonXpEvent(){
        long now=System.currentTimeMillis();if(now<nextActionAt)return;
        Frame f=Microbot.getClientThread().runOnClientThreadOptional(this::snapshot).orElse(null);
        if(f==null || !f.loggedIn)return;
        manual=false;
        if(f.declineWidget!=null){
            if(dismissAttempts>=3){releaseDismissed("Decline did not close "+eventName+"; resuming farming",now);return;}
            dismissAttempts++;Rs2Widget.clickWidget(f.declineWidget);nextActionAt=now+1500L;status="Declining "+eventName;return;
        }
        if(!f.present || !f.targetingLocal && !f.ownedDialogue && !f.dialogue && !f.tray){
            if(absentSince==0)absentSince=now;
            if(now-absentSince>=1800L)releaseDismissed("Non-XP event ended; resuming farming",now);
            return;
        }
        absentSince=0;
        if(dismissAttempts>=3){releaseDismissed("Could not dismiss "+eventName+" after three attempts; ignoring it and resuming farming",now);return;}
        dismissAttempts++;nextActionAt=now+1500L;status="Dismissing non-XP event: "+eventName;
        if(f.dismissable && !f.otherSpeaker){
            boolean clicked=Rs2Npc.interact(owner,"Dismiss");
            Microbot.log("Non-XP event "+eventName+": Dismiss click="+clicked+", attempt="+dismissAttempts);
        }
    }
    static boolean declineOption(String text){
        String s=RandomEventPolicy.plain(text).replaceFirst("^\\d+[.)]\\s*","");
        return java.util.Set.of("sorry, i'm busy.","no.","no thanks.","no, thanks.","no thank you.","not now.").contains(s);
    }
    Frame snapshot(){
        Frame f=new Frame();f.loggedIn=client.getGameState()==GameState.LOGGED_IN;
        if(!f.loggedIn || owner==null) return f;
        Player local=client.getLocalPlayer(); if(local==null) return f;
        f.moving=local.getPoseAnimation()!=local.getIdlePoseAnimation();f.animating=local.getAnimation()!=-1;
        for(NPC npc:client.getTopLevelWorldView().npcs()) if(npc==owner){f.present=true;break;}
        f.targetingLocal=f.present && owner.getInteracting()==local;
        if(f.present && owner.getComposition()!=null && owner.getComposition().getActions()!=null)
            for(String action:owner.getComposition().getActions()) {if("Talk-to".equalsIgnoreCase(action)) f.talkable=true;if("Dismiss".equalsIgnoreCase(action))f.dismissable=true;}
        f.full=Rs2Inventory.emptySlotCount()<RandomEventPolicy.rewardSlots(eventId);
        Widget speaker=client.getWidget(WidgetInfo.DIALOG_NPC_NAME);
        Widget speech=client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
        String name=visible(speaker)?speaker.getText():"";
        boolean ourSpeaker=RandomEventPolicy.sameSpeaker(eventName,name);
        f.otherSpeaker=!RandomEventPolicy.plain(name).isEmpty() && !ourSpeaker;
        if(ourSpeaker && visible(speech)) requestSpeech=speech.getText();
        f.dialogue=Rs2Dialogue.isInDialogue();f.options=Rs2Dialogue.hasSelectAnOption();f.canContinue=Rs2Dialogue.hasContinue();
        Widget playerSpeech=client.getWidget(WidgetInfo.DIALOG_PLAYER_TEXT);
        f.ownedDialogue=ourSpeaker || talked && visible(playerSpeech) && !requestSpeech.isEmpty();
        if(RandomEventPolicy.dismissInvitation(eventId) && f.options && !f.otherSpeaker && (f.targetingLocal || f.ownedDialogue)) {
            for(Widget option:Rs2Dialogue.getDialogueOptions())if(visible(option) && declineOption(option.getText())){f.declineWidget=option;break;}
        }
        Widget tray=client.getWidget(InterfaceID.SANDWICH_TRAY,0); f.tray=visible(tray);
        if(f.tray){
            SandwichChoice.Result choice=SandwichChoice.find(client,requestSpeech);
            f.foodWidget=choice.widget;f.trayDiagnostic=choice.diagnostic;
        }
        return f;
    }
    private static boolean visible(Widget widget){return widget!=null && !widget.isHidden();}
    static final class Frame {
        boolean loggedIn,present,targetingLocal,talkable,moving,animating,full,dialogue,options,canContinue,ownedDialogue,otherSpeaker,tray;
        boolean dismissable;Widget declineWidget;
        Widget foodWidget;String trayDiagnostic="";
    }
}
