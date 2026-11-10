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

/**
 * Local Inferno actions dispatched through the plugin's input owner and Main's native mouse motion.
 *
 * Arena workers resolve commands; short item/widget dispatches run on the client thread so
 * generic worker-side tab waits and post-click pauses cannot delay subsequent arena input.
 *
 * True means already satisfied or submitted, not observed game completion. A queued gesture
 * can still be cancelled before pressing; the local controller reads the logical result as DONE or FAILED.
 */
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
    /** "Prayers only": prayers are invoked from any tab instead of through the prayer book. */
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
    /** Published on game ticks/varbit changes; pre-press checks never wait for another client hop. */
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
        // Preserve the command's diagnostic ticket across the client-thread hop.
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

    /** True once the game's walk destination is the requested tile, or the click was never observed (as before). */
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
    /** Includes the command's cancellation checkpoint, so a newer movement ends the wait. */
    private boolean walkAllowed(){return allowed.getAsBoolean();}

    /** The cursor travels to the point projected now; the entry re-projects it on the frame its menu is built. */
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

    /** Client thread only: the tile's viewport point, or null when it is off the viewport. */
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

	/** With a spell selected, Main's click casts it on the NPC instead. */
    public boolean attackNpc(int index) {
        if (!allowed.getAsBoolean()) return false;
        return ArenaInputScope.onClientThread(() -> {
            if (!allowed.getAsBoolean()) return false;
            var entry=ArenaMenuInput.attackEntry(client,index,allowed);
            return entry!=null&&ArenaMenuInput.tryInvoke(entry,entry.getActor().getConvexHull().getBounds());
        }).orElse(false);
    }

	public boolean equipSet(String name, int[] itemIds)
	{
        // All pieces in one gesture: the game wears several items in the same tick.
        return clientAction(()->ArenaMenuInput.burst(()->equipSetNow(itemIds)));
    }
    private final java.util.Map<Integer,Long> equipClickedAt=new java.util.concurrent.ConcurrentHashMap<>();
    static final long EQUIP_RECLICK_NANOS=1_200_000_000L;
    private boolean equipSetNow(int[] itemIds){
        if (!allowed.getAsBoolean()) return false;
		if (itemIds == null || itemIds.length > 28) return false;
        boolean any = false;
		for (int id : itemIds)
		{
            if (!allowed.getAsBoolean()) return false;
			if (Rs2Equipment.isWearing(id)) { any=true;continue; }
            // 1.0.74: an item clicked moments ago still shows in the inventory until the server's tick; a second click
            // on its slot can put the item it replaced back on (user: "goes back and switches, clicks again").
            Long clicked=equipClickedAt.get(id);
            if (clicked!=null&&System.nanoTime()-clicked<EQUIP_RECLICK_NANOS) { any=true;continue; }
            if (Rs2Inventory.hasItem(id))
			{
				var item = Rs2Inventory.get(id);
                boolean done = item != null && equipInventory(item);
                if (done) equipClickedAt.put(id,System.nanoTime());
                any |= done;
			}
		}
		return any;
	}

    private boolean equipInventory(Rs2ItemModel item) {
        for (String action : new String[]{"Wear", "Wield", "Equip"})
            if (ArenaMenuInput.inventory(client,item.getSlot(),item.getId(),action)) return true;
        return false;
    }

	public boolean inventoryInteract(int slot, int itemId, String option)
	{
        return clientAction(()->inventoryInteractNow(slot,itemId,option));
    }
    private boolean inventoryInteractNow(int slot,int itemId,String option){
        if (!allowed.getAsBoolean()) return false;
        if ("drop".equalsIgnoreCase(option))
            return net.runelite.client.plugins.microbot.droinfernoprayers.ArenaDropPolicy.drop(client,allowed,slot,itemId);
        if (slot<0 || slot>=28 || itemId<=0 || !safeInventoryOption(option)) return false;
		Rs2ItemModel item = Rs2Inventory.getItemInSlot(slot);
		return item != null && item.getId() == itemId && ArenaMenuInput.inventory(client, slot, itemId, option);
	}

    public boolean cameraSet(Integer yaw,Integer pitch,Integer zoom) {
        if(!allowed.getAsBoolean() || yaw!=null && (yaw<0 || yaw>2047)
            || pitch!=null && (pitch<128 || pitch>383) || zoom!=null && (zoom<0 || zoom>1004)) return false;
        return ArenaInputScope.onClientThread(() -> {
            if(!allowed.getAsBoolean()) return false;
            if(yaw!=null) client.setCameraYawTarget(yaw*8);
            if(pitch!=null) client.setCameraPitchTarget(pitch*8);
            if(zoom!=null) client.runScript(net.runelite.api.ScriptID.CAMERA_DO_ZOOM,zoom,zoom);
            return true;
        }).orElse(false);
    }

	public boolean specialAttack()
	{
        if (!allowed.getAsBoolean()) return false;
        return clientAction(() -> {
            if (client.getVarpValue(net.runelite.api.VarPlayer.SPECIAL_ATTACK_ENABLED) == 1) return true;
            // The clickable orb button, not SPECENERGY_BACKING (0x00a0_0023): the game ignores Use on the backing.
            int button = net.runelite.api.gameval.InterfaceID.Orbs.SPECBUTTON;
            var widget = client.getWidget(button);
            if (widget == null || widget.isHidden()) return false;
            var bounds = widget.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return false;
            // Supply a menu entry so Direct invokes does not silently become a mouse gesture.
            return ArenaMenuInput.tryInvoke(new net.runelite.client.plugins.microbot.util.menu.NewMenuEntry()
                .param0(-1).param1(button).type(net.runelite.api.MenuAction.CC_OP)
                .identifier(1).itemId(-1).option("Use").target("Special Attack").widget(widget), bounds);
        });
	}

    public boolean inventoryById(int itemId,String option) {
        if(!allowed.getAsBoolean())return false;
        return clientAction(()->{
            var item=Rs2Inventory.get(itemId);
            return item!=null&&allowed.getAsBoolean()&&ArenaMenuInput.inventory(client,item.getSlot(),itemId,option);
        });
    }
    public boolean equipmentSlot(int slot,String option) {
        if(!allowed.getAsBoolean()||!ArenaGameControls.safeEntityOption(option))return false;
        return clientAction(()->ArenaMenuInput.equipment(client,slot,option));
    }
    public boolean combatStyle(int style) {
        if(!allowed.getAsBoolean()||style<0||style>3)return false;
        var styles=new net.runelite.api.widgets.WidgetInfo[]{net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_ONE,
            net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_TWO,net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_THREE,
            net.runelite.api.widgets.WidgetInfo.COMBAT_STYLE_FOUR};
        return clientAction(()->{
            var widget=client.getWidget(styles[style]);
            return widgetInput(widget,"Select",1);
        });
    }
    public boolean autoRetaliate(boolean on) {
        return clientAction(()->{
            int expected=on?0:1;
            if(client.getVarpValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF)==expected)return true;
            var widget=client.getWidget(net.runelite.api.gameval.InterfaceID.CombatInterface.RETALIATE);
            if(widget==null||widget.isHidden()||widget.getBounds()==null||widget.getBounds().isEmpty())return false;
            return widgetInput(widget,"Auto retaliate",1);
        });
    }

    private boolean widgetInput(net.runelite.api.widgets.Widget widget,String option,int identifier) {
        if(widget==null||widget.isHidden()||widget.getBounds()==null||widget.getBounds().isEmpty())return false;
        ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()&&!widget.isHidden());
        entry.param0(-1).param1(widget.getId()).type(net.runelite.api.MenuAction.CC_OP)
            .identifier(identifier).itemId(-1).option(option).widget(widget);
        return ArenaMenuInput.tryInvoke(entry,widget.getBounds());
    }
    /** Inventory helpers submit immediately on the client thread, without their worker sleeps. */
    private boolean clientAction(java.util.function.BooleanSupplier action){
        if(!allowed.getAsBoolean())return false;
        return ArenaInputScope.onClientThread(()->allowed.getAsBoolean()&&action.getAsBoolean()).orElse(false);
    }
    public net.runelite.client.plugins.microbot.droinfernoprayers.ColosseumUi interfaceState(){
        return new net.runelite.client.plugins.microbot.droinfernoprayers.ColosseumUi(client,allowed);
    }
    private ArenaGameControls controls(){return new ArenaGameControls(client,allowed);}
    public boolean npcInteract(int index,String option){return controls().npc(index,option);}
    public boolean objectInteract(int[] ids,String name,String option,int radius,boolean walk){return controls().object(ids,name,option,radius);}
    public boolean widgetClick(int id,Integer child,String option){return controls().widget(id,child,option);}
    public boolean dialogueNext(){
        if(!allowed.getAsBoolean()||!ArenaInputScope.onClientThread(
            net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue::hasContinue).orElse(false))return false;
        if(!allowed.getAsBoolean())return false;
        net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.clickContinue();return true;
    }
    public boolean dialogueChoose(String text){return allowed.getAsBoolean()&&net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(text);}
    public boolean dialogueChoose(int n){return allowed.getAsBoolean()&&net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(n);}

    public static boolean safeInventoryOption(String option) {
        return ArenaGameControls.safeEntityOption(option);
    }
    private boolean invokePrayer(Rs2PrayerEnum p, boolean desired,
            net.runelite.client.plugins.microbot.droinfernoprayers.execution.PrayerDiagnostics.Ticket trace) {
            if (!allowed.getAsBoolean()) return false;
            // "Prayers only": the player is playing. Toggle from any tab, like Rs2Prayer's invoke: no prayer book,
            // no pointer travel, no gesture queue.
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
            // A varbit receipt can arrive while the queued invoke is waiting for this
            // client thread. Re-read it at native delivery, after the worker's cached
            // pre-press check, so an already satisfied request cannot toggle it away.
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

	// ---- helpers ----

    public static Rs2PrayerEnum prayerEnum(String name){return prayer(name);}

	/** A RuneLite Prayer name (used by the local controller) as Main's enum. */
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

	/** A tile from the server (a template tile inside an instance) as the world tile the game shows it on. */
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
