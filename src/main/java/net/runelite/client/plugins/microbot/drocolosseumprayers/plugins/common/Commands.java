package net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common;

import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMenuInput;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMenuEntry;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public class Commands
{
	protected final Client client;
    private final java.util.function.BooleanSupplier allowed;
    private final net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct product;

	public Commands(Client client, java.util.function.BooleanSupplier allowed)
	{
        this(client,allowed,net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.INFERNO);
    }
    private volatile java.util.function.BooleanSupplier prayersOnly=()->false;

    public Commands prayersOnly(java.util.function.BooleanSupplier value){prayersOnly=value==null?()->false:value;return this;}
    public Commands(Client client, java.util.function.BooleanSupplier allowed,
            net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct product) {
        this.client=client;
        this.allowed=()->{
            try{net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope.checkpoint();}
            catch(net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope.Cancelled stale){return false;}
            return allowed.getAsBoolean();
        };
        this.product=product;
	}

    private volatile java.util.Map<Rs2PrayerEnum,Boolean> prayerSnapshot=java.util.Map.of();
    private volatile long prayerSnapshotNanos;

    public void observePrayers(java.util.function.IntPredicate active) {
        var snapshot=new java.util.EnumMap<Rs2PrayerEnum,Boolean>(Rs2PrayerEnum.class);
        for(var prayer:Rs2PrayerEnum.values())snapshot.put(prayer,active.test(prayer.getVarbit()));
        prayerSnapshot=java.util.Collections.unmodifiableMap(snapshot);
        prayerSnapshotNanos=System.nanoTime();
    }
    public void clearPrayerSnapshot(){prayerSnapshot=java.util.Map.of();prayerSnapshotNanos=0;}
    public boolean prayerSet(String name, boolean on) {
        if(!allowed.getAsBoolean())return false;
        var prayer=prayer(name);if(prayer==null)return false;

        var trace=net.runelite.client.plugins.microbot.drocolosseumprayers.execution.PrayerDiagnostics.current();
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean())return false;
            observePrayers(id->client.getVarbitValue(id)==1);
            boolean active=prayerSnapshot.get(prayer);
            if(trace!=null)trace.before(active);
            return active==on || invokePrayer(prayer, trace);
        }).orElse(false);
    }
    public Boolean prayerActive(String name) {
        if(!allowed.getAsBoolean()||System.nanoTime()-prayerSnapshotNanos>1_200_000_000L)return null;
        var prayer=prayer(name);return prayer==null?null:prayerSnapshot.get(prayer);
    }

	public boolean prayerFlick(String prayer)
	{
        return prayerSet(prayer, true);
	}

	public boolean moveTo(int x, int y, int plane, boolean screenOnly)
	{
        if (!allowed.getAsBoolean()) return false;
		if (x<0||x>16383||y<0||y>16383||plane<0||plane>3) return false;
        if (modifierChoicePending()) return false;
        if (product==net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.COLOSSEUM) return walks.clickOnce(x,y,plane).accepted();
        return walks.walk(x,y,plane).accepted();
	}

    static final long MODIFIER_OPENING_NANOS=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2400);
    private volatile long startWaveAt;

    boolean modifierChoicePending(){
        if(product!=net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.COLOSSEUM)return false;
        long started=startWaveAt;
        if(started!=0&&System.nanoTime()-started<MODIFIER_OPENING_NANOS)return true;
        return ArenaInputScope.onClientThread(()->ArenaGameControls.modifierScreenVisible(client)).orElse(false);
    }

    private final WalkVerification walks=new WalkVerification(new WalkVerification.Probe(){
        @Override public WorldPoint target(int x,int y,int plane){
            return ArenaInputScope.onClientThread(()->world(x,y,plane)).orElse(null);
        }
        @Override public boolean click(int x,int y,int plane,WalkVerification.Click click){return walkClick(x,y,plane,click);}
        @Override public WalkVerification.Observation observe(){
            return ArenaInputScope.onClientThread(()->{
                var destination=client.getLocalDestinationLocation();var me=client.getLocalPlayer();
                return new WalkVerification.Observation(client.getGameCycle(),
                    destination==null?null:WorldPoint.fromLocal(client,destination),me==null?null:me.getWorldLocation());
            }).orElse(null);
        }
    },this::walkAllowed,System::nanoTime,java.util.concurrent.locks.LockSupport::parkNanos);

    private boolean walkAllowed(){return allowed.getAsBoolean();}

    private boolean walkClick(int targetX,int targetY,int plane,WalkVerification.Click click){
        boolean colosseum=product==net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.COLOSSEUM;

        int[] aim=ArenaInputScope.onClientThread(() -> {
            if(colosseum&&ArenaGameControls.modifierScreenVisible(client))return null;
            var point=canvasPoint(targetX,targetY,plane);
            if(point!=null)return new int[]{targetX,targetY,point.getX(),point.getY()};
            int[] toward=colosseum?visibleToward(targetX,targetY,plane):null;
            var near=toward==null?null:canvasPoint(toward[0],toward[1],plane);
            return near==null?null:new int[]{toward[0],toward[1],near.getX(),near.getY()};
        }).orElse(null);
        if (aim==null) return false;
        int x=aim[0],y=aim[1];
        net.runelite.api.Point canvas=new net.runelite.api.Point(aim[2],aim[3]);
        ArenaMenuEntry entry = new ArenaMenuEntry().liveWalkPoint(() -> {
            var current = canvasPoint(x,y,plane);
            if (current != null) click.built(client.getGameCycle());
            return current;
        });
        entry.param0(canvas.getX()).param1(canvas.getY()).type(net.runelite.api.MenuAction.WALK)
            .identifier(0).itemId(-1).option("Walk here").worldViewId(client.getTopLevelWorldView().getId());
        return ArenaMenuInput.tryClick(entry, canvas);
    }

    private int[] visibleToward(int x,int y,int plane){
        var me=client.getLocalPlayer();if(me==null)return null;

        WorldPoint here=me.getWorldLocation();
        var local=here==null?null:net.runelite.api.coords.LocalPoint.fromWorld(client.getTopLevelWorldView(),here);
        WorldPoint from=local==null?null:WorldPoint.fromLocalInstance(client,local);
        if(from==null||from.getPlane()!=plane)return null;
        int dx=x-from.getX(),dy=y-from.getY(),steps=Math.max(Math.abs(dx),Math.abs(dy));
        for(int i=steps-1;i>=1;i--){
            int tx=from.getX()+Math.round((float)dx*i/steps),ty=from.getY()+Math.round((float)dy*i/steps);
            if(canvasPoint(tx,ty,plane)!=null)return new int[]{tx,ty};
        }
        return null;
    }

    private net.runelite.api.Point canvasPoint(int x,int y,int plane){
        WorldPoint p=world(x,y,plane);
        if (p==null) return null;
        var local=net.runelite.api.coords.LocalPoint.fromWorld(client.getTopLevelWorldView(),p);
        if (local==null) return null;
        var point=net.runelite.api.Perspective.localToCanvas(client,local,plane);
        if (point==null || point.getX()<client.getViewportXOffset() || point.getY()<client.getViewportYOffset()
            || point.getX()>=client.getViewportXOffset()+client.getViewportWidth()
            || point.getY()>=client.getViewportYOffset()+client.getViewportHeight()) return null;
        return point;
    }



    private final java.util.Map<Integer,Long> equipClickedAt=new java.util.concurrent.ConcurrentHashMap<>();
    static final long EQUIP_RECLICK_NANOS=1_200_000_000L;







    public net.runelite.client.plugins.microbot.drocolosseumprayers.ColosseumUi interfaceState(){
        return new net.runelite.client.plugins.microbot.drocolosseumprayers.ColosseumUi(client,allowed);
    }
    private ArenaGameControls controls(){return new ArenaGameControls(client,allowed,product);}
    private boolean dialogueAllowed(){
        if(!allowed.getAsBoolean())return false;
        if(product!=net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct.COLOSSEUM)return true;
        return ArenaInputScope.onClientThread(()->
            allowed.getAsBoolean()&&!ArenaGameControls.leaveConfirmationVisible(client)
                &&!ArenaGameControls.forfeitConfirmationVisible(client)).orElse(false);
    }

    public boolean dialogueChoose(String text){return dialogueAllowed()&&net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(text);}
    public boolean dialogueChoose(int n){return dialogueAllowed()&&net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(n);}

    private boolean invokePrayer(Rs2PrayerEnum p,
            net.runelite.client.plugins.microbot.drocolosseumprayers.execution.PrayerDiagnostics.Ticket trace) {
            if (!allowed.getAsBoolean()) return false;

            if(prayersOnly.getAsBoolean()){
                if(trace!=null)trace.inputQueued();
                client.menuAction(-1,p.getIndex(),net.runelite.api.MenuAction.CC_OP,1,-1,"Activate","");
                return true;
            }
            if(!net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputDispatcher.openTab(client,
                    net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.PRAYER)){net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputDiagnostics.event("TAB_NOT_READY","INVOKE_PRAYER");return false;}
            var widget=client.getWidget(p.getIndex());
            if(widget==null||widget.isHidden()){net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputDiagnostics.event("PRAYER_WIDGET_UNAVAILABLE","MISSING_OR_HIDDEN");return false;}
            var bounds=widget.getBounds();if(bounds==null||bounds.width<=0||bounds.height<=0){net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputDiagnostics.event("PRAYER_WIDGET_UNAVAILABLE","EMPTY_BOUNDS");return false;}
            if(trace!=null)trace.inputQueued();
            return ArenaMenuInput.tryInvoke(new net.runelite.client.plugins.microbot.util.menu.NewMenuEntry()
                .param0(-1).param1(p.getIndex()).type(net.runelite.api.MenuAction.CC_OP)
                .identifier(1).itemId(-1).option("Activate").widget(widget),
                bounds);
    }

    public static Rs2PrayerEnum prayerEnum(String name){return prayer(name);}

	protected static Rs2PrayerEnum prayer(String name)
	{
		switch (name)
		{
			case "BURST_OF_STRENGTH":
				return Rs2PrayerEnum.BURST_STRENGTH;
			case "CLARITY_OF_THOUGHT":
				return Rs2PrayerEnum.CLARITY_THOUGHT;
			case "PROTECT_FROM_MAGIC":
				return Rs2PrayerEnum.PROTECT_MAGIC;
			case "PROTECT_FROM_MISSILES":
				return Rs2PrayerEnum.PROTECT_RANGE;
			case "PROTECT_FROM_MELEE":
				return Rs2PrayerEnum.PROTECT_MELEE;
			case "DEADEYE":
				return Rs2PrayerEnum.DEAD_EYE;
			default:
				try
				{
					return Rs2PrayerEnum.valueOf(name);
				}
				catch (IllegalArgumentException e)
				{
					return null;
				}
		}
	}

	protected WorldPoint world(int x, int y, int plane)
	{
		WorldPoint tile = new WorldPoint(x, y, plane);
		if (!client.getTopLevelWorldView().isInstance())
		{
			return null;
		}
		Collection<WorldPoint> shown = WorldPoint.toLocalInstance(client.getTopLevelWorldView(), tile);
		return shown.isEmpty() ? null : shown.iterator().next();
	}
}
