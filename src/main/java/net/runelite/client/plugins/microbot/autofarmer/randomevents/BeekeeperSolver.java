package net.runelite.client.plugins.microbot.autofarmer.randomevents;

import java.awt.Rectangle;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/** Visible-interface Bee Keeper handler. Never confirms an unverified arrangement. */
final class BeekeeperSolver {
    enum Result { WORKING, COMPLETE, MANUAL }
    static final int[] MODELS={28806,28428,28803,28808}; // lid, body, entrance, legs
    static final int[] START={InterfaceID.Beehive.START_1,InterfaceID.Beehive.START_2,InterfaceID.Beehive.START_3,InterfaceID.Beehive.START_4};
    static final int[] DEST={InterfaceID.Beehive.DEST_1,InterfaceID.Beehive.DEST_2,InterfaceID.Beehive.DEST_3,InterfaceID.Beehive.DEST_4};
    String status="waiting for event";
    private long began,absent;
    private boolean entered,confirmed;
    private int lastSlot=-1,attempts;
    void reset(){began=System.currentTimeMillis();absent=0;entered=false;confirmed=false;lastSlot=-1;attempts=0;}
    static int sourceFor(int[] source,int model){int found=-1;for(int i=0;i<source.length;i++)if(source[i]==model){if(found>=0)return -1;found=i;}return found;}
    static int nextSlot(int[] dest){for(int i=0;i<4;i++)if(dest[i]!=MODELS[i])return i;return -1;}
    Result step(Client client){return step(client,false);}
    Result step(Client client,boolean observeOnly){
        if(began==0)reset();
        Frame f=Microbot.getClientThread().runOnClientThreadOptional(()->snapshot(client)).orElse(null);
        if(f==null || !f.loggedIn)return Result.WORKING;
        if(f.arena || f.puzzle)entered=true;
        if(entered && !f.arena && !f.puzzle && !f.dialogue){
            if(absent==0)absent=System.currentTimeMillis();
            if(System.currentTimeMillis()-absent>=3000L){status="returned from event";return Result.COMPLETE;}
            return Result.WORKING;
        }
        absent=0;
        if(observeOnly)return Result.WORKING;
        if(System.currentTimeMillis()-began>240000L){status="Bee keeper exceeded four minutes";return Result.MANUAL;}
        if(f.puzzle){
            status="assembling hive";
            if(confirmed)return Result.WORKING;
            int slot=nextSlot(f.destModels);
            if(slot<0){if(f.confirm!=null){Rs2Widget.clickWidget(f.confirm);confirmed=true;status="waiting for hive result";}return Result.WORKING;}
            int source=sourceFor(f.sourceModels,MODELS[slot]);
            if(source<0 || f.sources[source]==null || f.destinations[slot]==null){status="Cannot identify hive piece or destination";return Result.MANUAL;}
            if(lastSlot==slot)attempts++;else{lastSlot=slot;attempts=1;}
            if(attempts>3){status="Hive drag did not update destination";return Result.MANUAL;}
            Rectangle a=f.sources[source],b=f.destinations[slot];
            Microbot.getMouse().drag(new net.runelite.api.Point(a.x+a.width/2,a.y+a.height/2),new net.runelite.api.Point(b.x+b.width/2,b.y+b.height/2));
            return Result.WORKING;
        }
        if(f.dialogue){
            if(!f.owned){status="Unrecognized Bee keeper dialogue";return Result.MANUAL;}
            if(f.option!=null){Rs2Widget.clickWidget(f.option);return Result.WORKING;}
            if(f.options){status="Unrecognized Bee keeper choice";return Result.MANUAL;}
            if(f.continueDialogue){Rs2Dialogue.clickContinue();return Result.WORKING;}
            return Result.WORKING;
        }
        if(f.moving)return Result.WORKING;
        if(f.arena && f.hive!=null && !confirmed){status="opening hive puzzle";f.hive.click("Build");return Result.WORKING;}
        if(f.npc!=null){status=entered?"claiming reward":"accepting invitation";Rs2Npc.interact(f.npc,"Talk-to");}
        return Result.WORKING;
    }
    private static Rectangle bounds(Widget w){if(w==null || w.isHidden())return null;Rectangle b=w.getBounds();return b!=null && b.width>0 && b.height>0?b:null;}
    private static boolean visible(Widget w){return bounds(w)!=null;}
    static boolean allowedOption(String text){
        String s=RandomEventPolicy.plain(text).replaceFirst("^\\d+[.)]\\s*","");
        return Set.of("yes","yes.","yes, please.","okay, let's go.","sure.","sure","okay.","ok.","i'll help you.","i'll help.","experience","experience.","xp").contains(s)
            || s.startsWith("yes, i'll help") || s.startsWith("i'd like experience") || s.startsWith("experience reward");
    }
    Frame snapshot(Client client){
        Frame f=new Frame();f.loggedIn=client.getGameState()==GameState.LOGGED_IN;
        if(!f.loggedIn || client.getLocalPlayer()==null || client.getTopLevelWorldView()==null)return f;
        Player player=client.getLocalPlayer();f.moving=player.getPoseAnimation()!=player.getIdlePoseAnimation();
        for(NPC npc:client.getTopLevelWorldView().npcs()){
            if(npc.getId()==3235 && npc.getWorldLocation().distanceTo(player.getWorldLocation())<=20){f.arena=true;f.npc=npc;break;}
            if(npc.getId()==6747 && npc.getInteracting()==player)f.npc=npc;
        }
        f.puzzle=visible(client.getWidget(InterfaceID.Beehive.UNIVERSE));
        if(f.puzzle){
            f.confirm=client.getWidget(InterfaceID.Beehive.CONFIRM);
            for(int i=0;i<4;i++){
                Widget s=client.getWidget(START[i]),d=client.getWidget(DEST[i]);
                f.sources[i]=bounds(s);f.destinations[i]=bounds(d);
                f.sourceModels[i]=s==null?-1:s.getModelId();f.destModels[i]=d==null?-1:d.getModelId();
            }
        }
        f.dialogue=Rs2Dialogue.isInDialogue();f.options=Rs2Dialogue.hasSelectAnOption();f.continueDialogue=Rs2Dialogue.hasContinue();
        Widget name=client.getWidget(WidgetInfo.DIALOG_NPC_NAME);
        f.owned=visible(name) && RandomEventPolicy.sameSpeaker("Bee keeper",name.getText())
            || (f.arena || f.npc!=null) && (visible(client.getWidget(WidgetInfo.DIALOG_PLAYER_TEXT)) || f.options);
        if(f.options && f.owned){for(Widget option:Rs2Dialogue.getDialogueOptions())if(visible(option) && allowedOption(option.getText())){f.option=option;break;}}
        if(f.arena && !f.dialogue && !f.puzzle)f.hive=Microbot.getRs2TileObjectCache().query().withIds(20859).nearest();
        return f;
    }
    static final class Frame {
        boolean loggedIn,arena,puzzle,dialogue,options,continueDialogue,owned,moving;
        NPC npc;Rs2TileObjectModel hive;Widget confirm,option;
        int[] sourceModels=new int[4],destModels=new int[4];
        Rectangle[] sources=new Rectangle[4],destinations=new Rectangle[4];
    }
}
