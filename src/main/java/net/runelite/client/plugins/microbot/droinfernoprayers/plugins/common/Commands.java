package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaMenuInput;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaMenuEntry;
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
    private final net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct product;

	public Commands(Client client, java.util.function.BooleanSupplier allowed)
	{
        this(client,allowed,net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct.INFERNO);
    }
    private volatile java.util.function.BooleanSupplier prayersOnly=()->false;

    public Commands prayersOnly(java.util.function.BooleanSupplier value){prayersOnly=value==null?()->false:value;return this;}
    public Commands(Client client, java.util.function.BooleanSupplier allowed,
            net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct product) {
        this.client=client;
        this.allowed=()->{
            try{net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.checkpoint();}
            catch(net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.Cancelled stale){return false;}
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

        var trace=net.runelite.client.plugins.microbot.droinfernoprayers.execution.PrayerDiagnostics.current();
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean())return false;
            observePrayers(id->client.getVarbitValue(id)==1);
            boolean active=prayerSnapshot.get(prayer);
            if(trace!=null)trace.before(active);
            return active==on || invokePrayer(prayer, on, trace);
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
        return walks.walk(x,y,plane).accepted();
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

    private boolean walkClick(int x,int y,int plane,WalkVerification.Click click){
        net.runelite.api.Point canvas = ArenaInputScope.onClientThread(() -> canvasPoint(x,y,plane)).orElse(null);
        if (canvas==null) return false;
        ArenaMenuEntry entry = new ArenaMenuEntry().liveWalkPoint(() -> {
            var current = canvasPoint(x,y,plane);
            if (current != null) click.built(client.getGameCycle());
            return current;
        });
        entry.param0(canvas.getX()).param1(canvas.getY()).type(net.runelite.api.MenuAction.WALK)
            .identifier(0).itemId(-1).option("Walk here").worldViewId(client.getTopLevelWorldView().getId());
        return ArenaMenuInput.tryClick(entry, canvas);
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







    public net.runelite.client.plugins.microbot.droinfernoprayers.ColosseumUi interfaceState(){
        return new net.runelite.client.plugins.microbot.droinfernoprayers.ColosseumUi(client,allowed);
    }
    private ArenaGameControls controls(){return new ArenaGameControls(client,allowed);}
    public boolean dialogueChoose(String text){return allowed.getAsBoolean()&&net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(text);}
    public boolean dialogueChoose(int n){return allowed.getAsBoolean()&&net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(n);}

    private boolean invokePrayer(Rs2PrayerEnum p, boolean desired,
            net.runelite.client.plugins.microbot.droinfernoprayers.execution.PrayerDiagnostics.Ticket trace) {
            if (!allowed.getAsBoolean()) return false;

            if(prayersOnly.getAsBoolean()){
                if(trace!=null)trace.inputQueued();
                client.menuAction(-1,p.getIndex(),net.runelite.api.MenuAction.CC_OP,1,-1,"Activate","");
                return true;
            }
            if(!net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDispatcher.openTab(client,
                    net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.PRAYER)){net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDiagnostics.event("TAB_NOT_READY","INVOKE_PRAYER");return false;}
            var widget=client.getWidget(p.getIndex());
            if(widget==null||widget.isHidden()){net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDiagnostics.event("PRAYER_WIDGET_UNAVAILABLE","MISSING_OR_HIDDEN");return false;}
            var bounds=widget.getBounds();if(bounds==null||bounds.width<=0||bounds.height<=0){net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDiagnostics.event("PRAYER_WIDGET_UNAVAILABLE","EMPTY_BOUNDS");return false;}
            if(trace!=null)trace.inputQueued();

            ArenaMenuEntry entry = new ArenaMenuEntry().liveCheck(() -> {
                if (!allowed.getAsBoolean()) return false;
                boolean active = client.getVarbitValue(p.getVarbit()) == 1;
                if (active != desired) return true;
                if (trace != null) trace.before(active);
                net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDiagnostics.event(
                    "PRAYER_ALREADY_APPLIED", "LIVE_STATE_ALREADY_SATISFIED");
                return false;
            });
            return ArenaMenuInput.tryInvoke(entry
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
