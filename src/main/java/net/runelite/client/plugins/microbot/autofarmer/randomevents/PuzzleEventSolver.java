package net.runelite.client.plugins.microbot.autofarmer.randomevents;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.*;
import net.runelite.api.events.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Session-scoped solvers; all UI reads are captured on the client thread. */
final class PuzzleEventSolver {
    enum Result { WORKING, COMPLETE, MANUAL }
    private RandomEventPolicy.Kind kind;
    private boolean entered,ownedDialogue;
    private long began,absent,nextAction,submittedAt;
    private volatile int mimeAnimation=-1,mimeGeneration,exercise,exerciseGeneration;
    private int submittedMime=-1,submittedExercise=-1,exerciseAttempts,lastExerciseGeneration=-1;
    private String lastAction="";
    private int actionAttempts;
    String status="waiting for event";
    static final int[] MATS={ObjectID.BARRACK_MAT_1,ObjectID.BARRACK_MAT_2,ObjectID.BARRACK_MAT_3,ObjectID.BARRACK_MAT_4};
    static final int[] GRAVES={9364,9365,9366,9367,10049}, EMPTY={10050,10051,10052,10053,10054};
    static final int[] COFFINS={7587,7588,7589,7590,7591};
    static final int[] EMOTES={857,861,1130,1131,860,866,1129,1128};
    void reset(RandomEventPolicy.Kind kind){
        this.kind=kind;entered=false;ownedDialogue=false;began=System.currentTimeMillis();absent=0;nextAction=0;
        mimeAnimation=-1;mimeGeneration=0;exercise=0;exerciseGeneration=0;submittedMime=-1;submittedExercise=-1;exerciseAttempts=0;lastExerciseGeneration=-1;submittedAt=0;lastAction="";actionAttempts=0;
    }
    static int region(Client client){
        if(client.getLocalPlayer()==null)return -1;
        return WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation()).getRegionID();
    }
    static int regionFor(RandomEventPolicy.Kind kind){
        switch(kind){case MAZE:return 11591;case MIME:return 8010;case DRILL:return 12619;case GRAVE:return 7758;default:return -1;}
    }
    static boolean arenaNpc(int id,int region){
        return id==321 && region==8010 || id==337 && region==12619 || id==6745 && region==7758 || id==325 && region==11591;
    }
    static int arenaId(RandomEventPolicy.Kind kind){switch(kind){case MIME:return 321;case DRILL:return 337;case GRAVE:return 6745;case MAZE:return 325;default:return -1;}}
    static int inviteId(RandomEventPolicy.Kind kind){switch(kind){case MIME:return 6753;case DRILL:return 6743;case GRAVE:return 6746;case MAZE:return 6752;default:return -1;}}
    static int mimeButton(int animation){for(int i=0;i<EMOTES.length;i++)if(EMOTES[i]==animation)return InterfaceID.MacroMimeEmotes.BUTTON_0+i;return -1;}
    static int requestedExercise(String speech){
        String s=RandomEventPolicy.plain(speech).replace("-"," ");
        if(s.endsWith("get yourself over there and jog on that mat, private!"))return 1;
        if(s.endsWith("get on that mat and give me sit ups, private!"))return 2;
        if(s.endsWith("drop and give me push ups on that mat, private!"))return 3;
        if(s.endsWith("i want to see you on that mat doing star jumps, private!"))return 4;
        return 0;
    }
    void animation(Client client,AnimationChanged e){
        if(kind!=RandomEventPolicy.Kind.MIME || region(client)!=8010 || !(e.getActor() instanceof NPC) || ((NPC)e.getActor()).getId()!=321)return;
        int animation=e.getActor().getAnimation();if(animation==-1 || animation==858)return;
        mimeAnimation=animation;mimeGeneration++;
    }
    void chat(Client client,ChatMessage e){
        if(kind!=RandomEventPolicy.Kind.DRILL || region(client)!=12619 || e.getType()!=ChatMessageType.DIALOG)return;
        exercise=requestedExercise(e.getMessage());exerciseGeneration++;
    }
    Result step(Client client,boolean observeOnly){
        Frame f=Microbot.getClientThread().runOnClientThreadOptional(()->snapshot(client)).orElse(null);
        if(f==null || !f.loggedIn)return Result.WORKING;
        long now=System.currentTimeMillis();if(f.arena)entered=true;
        if(entered && !f.arena && !f.dialogue && !f.mimeUI){
            if(absent==0)absent=now;
            if(now-absent>=3000){status="returned from event";return Result.COMPLETE;}return Result.WORKING;
        }
        absent=0;
        if(observeOnly)return Result.WORKING;
        if(now-began>600000){status="Event exceeded ten minutes";return Result.MANUAL;}
        if(now<nextAction)return Result.WORKING;nextAction=now+1200;
        if(f.dialogue){
            if(kind==RandomEventPolicy.Kind.DRILL && requestedExercise(f.speech)>0 && exercise!=requestedExercise(f.speech)){
                exercise=requestedExercise(f.speech);exerciseGeneration++;
            }
            if(!f.owned){status="Unrecognized event dialogue";return Result.MANUAL;}
            ownedDialogue=true;
            if(f.option!=null){Rs2Widget.clickWidget(f.option);return Result.WORKING;}
            if(f.options){status="Unrecognized event choice";return Result.MANUAL;}
            if(f.continueDialogue){Rs2Dialogue.clickContinue();return Result.WORKING;}
            return Result.WORKING;
        }
        if(!f.arena){if(f.npc!=null){status="accepting invitation";ownedDialogue=Rs2Npc.interact(f.npc,"Talk-to");nextAction=now+3000;}return Result.WORKING;}
        if(kind==RandomEventPolicy.Kind.MIME)return mime(f,now);
        if(f.moving || f.animating)return Result.WORKING;
        switch(kind){
            case DRILL:return drill(f,now);
            case GRAVE:return grave(f,now);
            case MAZE:return maze(f,now);
            default:status="Unknown event";return Result.MANUAL;
        }
    }
    Result mime(Frame f,long now){
        status="watching mime";
        if(!f.mimeUI)return Result.WORKING;
        if(mimeGeneration==0 && f.npcAnimation!=-1){mimeAnimation=f.npcAnimation;mimeGeneration++;}
        if(mimeButton(mimeAnimation)<0){status="Waiting for a recognized mime demonstration";return Result.WORKING;}
        if(submittedMime==mimeGeneration)return Result.WORKING;
        Widget answer=f.mimeAnswer;
        if(answer==null){status="Waiting for mime answer button";return Result.WORKING;}
        if(Rs2Widget.clickWidget(answer)){submittedMime=mimeGeneration;status="copied mime demonstration";Microbot.log("Mime answer submitted for animation "+mimeAnimation+", round "+mimeGeneration);}
        return Result.WORKING;
    }
    static int matIndex(int[] posts,int requested){
        if(requested<1 || requested>4)return -1;int result=-1;
        for(int i=0;i<posts.length;i++)if(posts[i]==requested){if(result>=0)return -1;result=i;}return result;
    }
    Result drill(Frame f,long now){
        status="waiting for drill instruction";
        if(exercise==0 && requestedExercise(f.speech)>0){exercise=requestedExercise(f.speech);exerciseGeneration++;}
        if(submittedExercise==exerciseGeneration){
            if(now-submittedAt<10000)return Result.WORKING;
            // No new command arrived. Retry at most twice, never spam the mat.
            submittedExercise=-1;
        }
        if(lastExerciseGeneration!=exerciseGeneration){exerciseAttempts=0;lastExerciseGeneration=exerciseGeneration;}
        int i=matIndex(f.posts,exercise);
        if(i>=0 && f.mats[i]!=null){
            String action=action(f.mats[i],"Exercise","Do-exercise","Use");
            if(action==null){status="Unrecognized exercise mat action";return Result.MANUAL;}
            if(exerciseAttempts>=3){status="Drill exercise did not receive a new instruction";return Result.MANUAL;}
            if(f.mats[i].click(action)){submittedExercise=exerciseGeneration;submittedAt=now;exerciseAttempts++;status="performing requested exercise";Microbot.log("Drill exercise "+exercise+" on post "+(i+1)+", instruction "+exerciseGeneration);}
            return Result.WORKING;
        }
        if(exercise>0){status="Cannot uniquely match drill instruction to mat";return Result.MANUAL;}
        if(f.npc!=null){ownedDialogue=Rs2Npc.interact(f.npc,"Talk-to");nextAction=now+3000;}return Result.WORKING;
    }
    // Positive means place inventory coffin, negative means remove a mismatched coffin, 0 means all correct.
    static int gravePlan(int[] required,int[] placed,boolean[] carried,int free){
        if(required.length!=5 || placed.length!=5 || carried.length!=5)return 99;
        for(int i=0;i<5;i++)if(required[i]<0 || required[i]>4 || placed[i]<0 || placed[i]>5)return 99;
        for(int i=0;i<5;i++)if(placed[i]==5 && carried[required[i]])return i+1;
        // Continue the current exchange cycle before removing an unrelated coffin.
        for(int i=0;i<5;i++)if(placed[i]!=5 && placed[i]!=required[i] && carried[required[i]])return free>0?-(i+1):98;
        for(int i=0;i<5;i++)if(placed[i]!=5 && placed[i]!=required[i])return free>0?-(i+1):98;
        for(int i=0;i<5;i++)if(placed[i]!=required[i])return 99;
        return 0;
    }
    Result grave(Frame f,long now){
        int plan=gravePlan(f.required,f.placed,f.carried,f.free);
        if(plan==98){status="Need another free inventory slot to exchange coffins";return Result.MANUAL;}
        if(plan==99){status="Cannot verify coffin and grave state";return Result.MANUAL;}
        if(plan==0){status="all five coffins matched; claiming reward";if(f.npc!=null){ownedDialogue=Rs2Npc.interact(f.npc,"Talk-to");nextAction=now+3000;}return Result.WORKING;}
        int slot=Math.abs(plan)-1;
        String signature=Arrays.toString(f.placed)+Arrays.toString(f.carried)+":"+plan;
        if(signature.equals(lastAction))actionAttempts++;else{lastAction=signature;actionAttempts=1;}
        if(actionAttempts>3){status="Coffin exchange did not update grave state";return Result.MANUAL;}
        nextAction=now+3000;
        if(plan>0){
            if(f.empty[slot]==null){status="Cannot find matching empty grave";return Result.MANUAL;}
            status="placing matching coffin in grave "+(slot+1);
            Rs2Inventory.useItemOnObject(COFFINS[f.required[slot]],f.empty[slot].getId());
        }else{
            if(f.filled[slot]==null){status="Cannot find mismatched coffin";return Result.MANUAL;}
            String a=action(f.filled[slot],"Take-coffin","Remove-coffin","Take","Remove");
            if(a==null){status="Unrecognized coffin removal action";return Result.MANUAL;}
            status="removing mismatched coffin from grave "+(slot+1);f.filled[slot].click(a);
        }
        return Result.WORKING;
    }
    Result maze(Frame f,long now){
        status="finding route to maze shrine";
        if(f.shrine==null || f.flags==null)return Result.WORKING;
        List<int[]> path=MazeRoute.path(f.flags,f.sx,f.sy,f.gx,f.gy,f.doors);
        if(Math.abs(f.sx-f.gx)+Math.abs(f.sy-f.gy)<=1){
            String a=action(f.shrine,"Touch","Activate","Use");
            if(a==null){status="Unrecognized maze shrine action";return Result.MANUAL;}
            f.shrine.click(a);nextAction=now+3000;status="touching exit shrine";return Result.WORKING;
        }
        if(path.isEmpty()){status="No traversable maze route found";return Result.WORKING;}
        int[] first=path.get(0);
        for(MazeRoute.Edge edge:f.doors)if(edge.joins(f.sx,f.sy,first[0],first[1]) && !MazeRoute.canStep(f.flags,f.sx,f.sy,first[0],first[1])){
            Rs2TileObjectModel door=f.doorObjects.get(edge.door);String a=f.doorActions.get(edge.door);
            door.click(a);nextAction=now+1800;status="opening maze passage";return Result.WORKING;
        }
        int[] target=first;int px=f.sx,py=f.sy,dx=first[0]-px,dy=first[1]-py;
        for(int i=0;i<Math.min(8,path.size());i++){
            int[] p=path.get(i);
            if(p[0]-px!=dx || p[1]-py!=dy || !MazeRoute.canStep(f.flags,px,py,p[0],p[1]))break;
            target=p;px=p[0];py=p[1];
        }
        Rs2Walker.walkFastCanvas(new WorldPoint(f.baseX+target[0],f.baseY+target[1],f.plane));status="walking through maze";return Result.WORKING;
    }
    static String action(Rs2TileObjectModel obj,String... candidates){for(String a:candidates)if(obj.hasAction(a))return a;return null;}
    static boolean visible(Widget w){return w!=null && !w.isHidden();}
    Frame snapshot(Client client){
        Frame f=new Frame();f.loggedIn=client.getGameState()==GameState.LOGGED_IN;
        if(!f.loggedIn || client.getLocalPlayer()==null || client.getTopLevelWorldView()==null)return f;
        Player local=client.getLocalPlayer();f.view=client.getTopLevelWorldView();
        f.arena=region(client)==regionFor(kind);f.moving=local.getPoseAnimation()!=local.getIdlePoseAnimation();f.animating=local.getAnimation()!=-1;
        for(NPC npc:f.view.npcs()){
            if(f.arena && npc.getId()==arenaId(kind)){f.npc=npc;f.npcAnimation=npc.getAnimation();break;}
            if(npc.getId()==inviteId(kind) && npc.getInteracting()==local)f.npc=npc;
        }
        Widget name=client.getWidget(WidgetInfo.DIALOG_NPC_NAME),speech=client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
        String speaker=visible(name)?RandomEventPolicy.plain(name.getText()):"";
        f.speech=visible(speech)?speech.getText():"";
        f.dialogue=Rs2Dialogue.isInDialogue();f.options=Rs2Dialogue.hasSelectAnOption();f.continueDialogue=Rs2Dialogue.hasContinue();
        String expected=kind==RandomEventPolicy.Kind.DRILL?"sergeant damien":kind==RandomEventPolicy.Kind.GRAVE?"leo":kind==RandomEventPolicy.Kind.MIME && f.arena?"mime":"mysterious old man";
        f.owned=!speaker.isEmpty()?(speaker.equals(expected) || f.npc!=null && RandomEventPolicy.sameSpeaker(f.npc.getName(),speaker) || kind==RandomEventPolicy.Kind.MIME && speaker.equals("mysterious old man")):ownedDialogue && (visible(client.getWidget(WidgetInfo.DIALOG_PLAYER_TEXT)) || f.options);
        if(f.options && f.owned)for(Widget option:Rs2Dialogue.getDialogueOptions())if(visible(option) && allowedOption(option.getText(),f.arena,kind)){f.option=option;break;}
        f.mimeUI=visible(client.getWidget(InterfaceID.MacroMimeEmotes.UNIVERSE));
        if(f.mimeUI){
            int animation=mimeGeneration==0?f.npcAnimation:mimeAnimation;int button=mimeButton(animation);
            if(button>=0){Widget parent=client.getWidget(button);if(visible(parent)){
                Widget[] children=parent.getDynamicChildren();f.mimeAnswer=children!=null && children.length>0?children[children.length-1]:parent;
                if(!visible(f.mimeAnswer))f.mimeAnswer=null;
            }}
        }
        if(!f.arena)return f;
        if(kind==RandomEventPolicy.Kind.DRILL){
            for(int i=0;i<4;i++){f.posts[i]=client.getVarbitValue(VarbitID.MACRO_DRILLDEMON_POST_1+i);f.mats[i]=Microbot.getRs2TileObjectCache().query().withIds(MATS[i]).nearest();}
        }else if(kind==RandomEventPolicy.Kind.GRAVE){
            f.free=Rs2Inventory.emptySlotCount();
            for(int i=0;i<5;i++){
                f.required[i]=client.getVarbitValue(VarbitID.MACRO_DIGGER_GRAVE_1+i);f.placed[i]=client.getVarbitValue(VarbitID.MACRO_DIGGER_COFFIN_1+i);f.carried[i]=Rs2Inventory.hasItem(COFFINS[i]);
                f.empty[i]=Microbot.getRs2TileObjectCache().query().withIds(EMPTY[i]).nearest();f.filled[i]=Microbot.getRs2TileObjectCache().query().withIds(GRAVES[i]).nearest();
            }
        }else if(kind==RandomEventPolicy.Kind.MAZE && !f.moving && !f.animating && !f.dialogue){snapshotMaze(f,local);}
        return f;
    }
    static boolean allowedOption(String text,boolean inside,RandomEventPolicy.Kind eventKind){
        String s=RandomEventPolicy.plain(text).replaceFirst("^\\d+[.)]\\s*","");
        if(eventKind==RandomEventPolicy.Kind.MAZE && s.equals("sure. i like exploring mazes."))return true;
        if(BeekeeperSolver.allowedOption(s))return true;
        return inside && (s.equals("i've finished.") || s.equals("i'm done.") || s.equals("i've put all the coffins in the right graves.") || s.equals("i think i've finished."));
    }
    void snapshotMaze(Frame f,Player local){
        f.shrine=Microbot.getRs2TileObjectCache().query().withIds(ObjectID.MACRO_MAZE_COMPLETE).nearest();
        if(f.shrine==null || f.view.getCollisionMaps()==null || f.view.getCollisionMaps()[f.view.getPlane()]==null)return;
        f.baseX=f.view.getBaseX();f.baseY=f.view.getBaseY();f.plane=f.view.getPlane();
        LocalPoint lp=local.getLocalLocation(),gp=f.shrine.getLocalLocation();f.sx=lp.getSceneX();f.sy=lp.getSceneY();f.gx=gp.getSceneX();f.gy=gp.getSceneY();
        int[][] live=f.view.getCollisionMaps()[f.view.getPlane()].getFlags();f.flags=new int[live.length][];for(int i=0;i<live.length;i++)f.flags[i]=live[i].clone();
        for(Rs2TileObjectModel obj:Microbot.getRs2TileObjectCache().query().toList()){
            String a=action(obj,"Open","Pass-through","Climb-over");if(a==null || obj.getPlane()!=f.view.getPlane())continue;
            LocalPoint p=obj.getLocalLocation();if(p==null)continue;int x=p.getSceneX(),y=p.getSceneY();int index=f.doorObjects.size();
            f.doorObjects.add(obj);f.doorActions.add(a);
            if(obj.getTileObject() instanceof WallObject){
                WallObject wall=(WallObject)obj.getTileObject();int orientation=wall.getOrientationA()|wall.getOrientationB();
                for(int d=0;d<4;d++)if((orientation&(1<<d))!=0)f.doors.add(new MazeRoute.Edge(x,y,x+MazeRoute.DX[d],y+MazeRoute.DY[d],index));
            }else{
                // A gate occupying a tile can only join its cardinal neighbors.
                for(int d=0;d<4;d++)f.doors.add(new MazeRoute.Edge(x,y,x+MazeRoute.DX[d],y+MazeRoute.DY[d],index));
            }
        }
    }
    static final class Frame {
        boolean loggedIn,arena,moving,animating,dialogue,options,continueDialogue,owned,mimeUI;
        String speech="";NPC npc;int npcAnimation=-1;Widget option,mimeAnswer;
        int[] posts=new int[4],required=new int[5],placed=new int[5];boolean[] carried=new boolean[5];int free;
        Rs2TileObjectModel[] mats=new Rs2TileObjectModel[4],empty=new Rs2TileObjectModel[5],filled=new Rs2TileObjectModel[5];
        WorldView view;Rs2TileObjectModel shrine;int[][] flags;int sx,sy,gx,gy,baseX,baseY,plane;
        List<MazeRoute.Edge> doors=new ArrayList<>();List<Rs2TileObjectModel> doorObjects=new ArrayList<>();List<String> doorActions=new ArrayList<>();
    }
}
