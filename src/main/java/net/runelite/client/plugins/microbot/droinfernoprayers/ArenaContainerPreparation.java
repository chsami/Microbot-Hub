package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.Bolts;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsQuiverPanel;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.util.Text;

/** Final bank-side container load. Every input has a later inventory/varbit receipt. */
final class ArenaContainerPreparation {
    enum Result { WAITING, READY, STOPPED }
    enum Kind {
        RUNES("Rune pouch"), BOLTS("Bolt pouch"), QUIVER("Quiver");
        final String label;
        Kind(String label) { this.label=label; }
    }
    interface Port {
        boolean allowed();
        default void poll() { }
        Snapshot snapshot();
        void openBank(); void closeBank();
        void withdraw(int id,int amount); void deposit(int id,int amount);
        void unequip(int id); void equip(int id);
        void open(Kind kind,int id); void close(Kind kind);
        void emptyRunes(); void remove(Kind kind,int ammo);
        void use(int id); void clearSelection();
    }
    /** Immutable live receipt; a null snapshot means unknown, never an empty container. */
    static final class Snapshot {
        final boolean bankOpen;
        final int freeSlots,selectedItem;
        final Map<Integer,Integer> inventory,equipment,bank;
        final Map<Kind,Map<Integer,Integer>> contents;
        final Map<Kind,Boolean> open;
        Snapshot(boolean bankOpen,int freeSlots,int selectedItem,Map<Integer,Integer> inventory,
                 Map<Integer,Integer> equipment,Map<Integer,Integer> bank,
                 Map<Kind,Map<Integer,Integer>> contents,Map<Kind,Boolean> open) {
            this.bankOpen=bankOpen;this.freeSlots=freeSlots;this.selectedItem=selectedItem;
            this.inventory=Map.copyOf(inventory);this.equipment=Map.copyOf(equipment);this.bank=Map.copyOf(bank);
            this.contents=new EnumMap<>(Kind.class);
            contents.forEach((kind,rows)->this.contents.put(kind,Map.copyOf(rows)));
            this.open=new EnumMap<>(Kind.class);this.open.putAll(open);
        }
        int carried(int id) { return inventory.getOrDefault(id,0); }
        int stocked(int id) { return bank.getOrDefault(id,0); }
        Map<Integer,Integer> contents(Kind kind) { return contents.get(kind); }
        boolean open(Kind kind) { return open.getOrDefault(kind,false); }
    }
    private static final class Target {
        final Kind kind;
        final int selected;
        final Map<Integer,Integer> contents;
        Target(Kind kind,int selected,Map<Integer,Integer> contents) {
            this.kind=kind;this.selected=selected;this.contents=Map.copyOf(contents);
        }
    }
    private static final long TIMEOUT=180_000,RECEIPT_TIMEOUT=15_000;
    private static final Set<Integer> ARROWS=Set.of(
        ItemID.BRONZE_ARROW,ItemID.IRON_ARROW,ItemID.STEEL_ARROW,ItemID.MITHRIL_ARROW,ItemID.ADAMANT_ARROW,ItemID.RUNE_ARROW,
        ItemID.BRONZE_ARROW_P,ItemID.IRON_ARROW_P,ItemID.STEEL_ARROW_P,ItemID.MITHRIL_ARROW_P,ItemID.ADAMANT_ARROW_P,ItemID.RUNE_ARROW_P,
        ItemID.BRONZE_ARROW_P_,ItemID.IRON_ARROW_P_,ItemID.STEEL_ARROW_P_,ItemID.MITHRIL_ARROW_P_,ItemID.ADAMANT_ARROW_P_,ItemID.RUNE_ARROW_P_,
        ItemID.BRONZE_ARROW_P__,ItemID.IRON_ARROW_P__,ItemID.STEEL_ARROW_P__,ItemID.MITHRIL_ARROW_P__,ItemID.ADAMANT_ARROW_P__,ItemID.RUNE_ARROW_P__,
        ItemID.DRAGON_ARROW,ItemID.DRAGON_ARROW_P,ItemID.DRAGON_ARROW_P_,ItemID.DRAGON_ARROW_P__,
        ItemID.AMETHYST_ARROW,ItemID.AMETHYST_ARROW_P,ItemID.AMETHYST_ARROW_P_,ItemID.AMETHYST_ARROW_P__,
        ItemID.SLAYER_BROAD_ARROWS,ItemID.SEEKING_SLAYER_BROAD_ARROWS,ItemID.ICE_ARROW,ItemID.OGRE_ARROW,
        ItemID.ZOGRE_BRUTAL_BRONZE,ItemID.ZOGRE_BRUTAL_IRON,ItemID.ZOGRE_BRUTAL_STEEL,ItemID.ZOGRE_BRUTAL_BLACK,
        ItemID.ZOGRE_BRUTAL_MITHRIL,ItemID.ZOGRE_BRUTAL_ADAMANT,ItemID.ZOGRE_BRUTAL_RUNE);
    private final Port port;
    private final LongSupplier clock;
    private final List<Target> targets=new ArrayList<>();
    private Result result=Result.WAITING;
    private String status="Verifying selected pouch and quiver contents";
    private Map<Integer,Integer> originalInventory,originalEquipment;
    private long started=-1,pendingAt;
    private Predicate<Snapshot> pending;
    private String pendingStatus;

    ArenaContainerPreparation(InventorySetup setup,BooleanSupplier allowed) {
        this(setup,new NativePort(allowed),System::currentTimeMillis);
    }
    ArenaContainerPreparation(InventorySetup setup,Port port,LongSupplier clock) {
        this.port=port;this.clock=clock;
        try { targets.addAll(parse(setup)); }
        catch(IllegalArgumentException invalid) { stop(invalid.getMessage()); }
    }
    /** Pure saved-setup validation, suitable before any price lookup or purchase. */
    static String validationError(InventorySetup setup) {
        try { parse(setup);return null; }
        catch(IllegalArgumentException invalid) { return invalid.getMessage(); }
    }
    private static List<Target> parse(InventorySetup setup) {
        if(setup==null)throw new IllegalArgumentException("Select an Inventory Setup before loading containers");
        List<Target> result=new ArrayList<>();
        Target runes=target(setup,Kind.RUNES,setup.getRune_pouch());if(runes!=null)result.add(runes);
        Target bolts=target(setup,Kind.BOLTS,setup.getBoltPouch());if(bolts!=null)result.add(bolts);
        Target quiver=target(setup,Kind.QUIVER,setup.getQuiver());if(quiver!=null)result.add(quiver);
        return result;
    }
    String status() { return status; }
    void resumeAfter(long millis) {
        if(millis>0) { if(started>=0)started+=millis;if(pending!=null)pendingAt+=millis; }
    }
    private static Target target(InventorySetup setup,Kind kind,List<InventorySetupsItem> rows) {
        if(rows==null)return null;
        List<InventorySetupsItem> containers=new ArrayList<>();
        for(List<InventorySetupsItem> section:Arrays.asList(setup.getInventory(),setup.getEquipment()))
            if(section!=null)for(InventorySetupsItem row:section)
                if(row!=null&&row.getQuantity()>0&&isContainer(kind,row.getId()))containers.add(row);
        if(containers.size()!=1||containers.get(0).getQuantity()!=1)
            throw new IllegalArgumentException(kind.label+" contents require exactly one selected container");
        int selected=containers.get(0).getId();
        Map<Integer,Integer> desired=new LinkedHashMap<>();
        for(InventorySetupsItem row:rows) {
            if(row!=null&&(row.getQuantity()<0||row.getId()<=0&&row.getQuantity()>0))
                throw new IllegalArgumentException(kind.label+" has an invalid saved item or quantity");
            if(row==null||row.getId()<=0||row.getQuantity()<=0)continue;
            if(kind==Kind.RUNES&&Runes.byItemId(row.getId())==null)
                throw new IllegalArgumentException("Unsupported rune-pouch item "+row.getId());
            if(kind==Kind.BOLTS&&Arrays.stream(Bolts.values()).noneMatch(bolt->bolt.getItemId()==row.getId()))
                throw new IllegalArgumentException("Unsupported bolt-pouch ammunition "+row.getId());
            if(kind==Kind.QUIVER&&!ARROWS.contains(row.getId())&&Arrays.stream(Bolts.values()).noneMatch(bolt->bolt.getItemId()==row.getId()))
                throw new IllegalArgumentException("Quiver contents must be arrows or bolts; item "+row.getId()+" is not ammunition");
            if(desired.put(row.getId(),row.getQuantity())!=null)
                throw new IllegalArgumentException(kind.label+" repeats ammunition "+row.getId()+" in multiple slots");
            if(kind==Kind.RUNES&&row.getQuantity()>16_000||kind==Kind.BOLTS&&row.getQuantity()>255)
                throw new IllegalArgumentException(kind.label+" quantity exceeds container capacity for item "+row.getId());
        }
        int capacity=kind==Kind.QUIVER?1:kind==Kind.BOLTS?4:
            InventorySetupsRunePouchPanel.RUNE_POUCH_DIVINE_IDS_SET.contains(selected)?4:3;
        if(desired.size()>capacity)throw new IllegalArgumentException(kind.label+" has more selected ammunition types than slots");
        return new Target(kind,selected,desired);
    }
    private static boolean isContainer(Kind kind,int id) {
        if(kind==Kind.BOLTS)return id==ItemID.XBOWS_BOLT_POUCH;
        if(kind==Kind.QUIVER)return InventorySetupsQuiverPanel.DIZANA_QUIVER_IDS_SET.contains(id);
        return InventorySetupsRunePouchPanel.RUNE_POUCH_IDS_SET.contains(id)
            ||InventorySetupsRunePouchPanel.RUNE_POUCH_DIVINE_IDS_SET.contains(id);
    }
    private boolean anyContainer(int id) {
        return Arrays.stream(Kind.values()).anyMatch(kind->isContainer(kind,id));
    }
    private int carriedContainer(Snapshot state,Kind kind) {
        return state.inventory.keySet().stream().filter(id->isContainer(kind,id)).findFirst().orElse(-1);
    }
    private int wornContainer(Snapshot state,Kind kind) {
        return state.equipment.keySet().stream().filter(id->isContainer(kind,id)).findFirst().orElse(-1);
    }
    private boolean exact(Snapshot state) {
        return targets.stream().allMatch(target->target.contents.equals(state.contents(target.kind))
            &&(carriedContainer(state,target.kind)>0||wornContainer(state,target.kind)>0));
    }
    Result tick() {
        if(result==Result.STOPPED)return result;
        if(!port.allowed()||Thread.currentThread().isInterrupted())return Result.WAITING;
        long now=clock.getAsLong();
        if(started<0)started=now;
        Snapshot state=port.snapshot();
        if(result==Result.READY) {
            // Native inventory repair can change a pouch after the first READY receipt.
            if(state!=null&&exact(state))return Result.READY;
            result=Result.WAITING;originalInventory=null;originalEquipment=null;
        }
        if(now-started>TIMEOUT)return stop("Container preparation timed out; exact contents were not confirmed");
        if(state==null) {
            status="Waiting for a live inventory, equipment and container receipt";
            if(pending!=null&&now-pendingAt>RECEIPT_TIMEOUT)return stop(pendingStatus+"; live receipt became unavailable");
            return result;
        }
        if(pending!=null) {
            if(!pending.test(state)) {
                status=pendingStatus;
                if(now-pendingAt>RECEIPT_TIMEOUT)return stop(pendingStatus+"; action was not acknowledged");
                if(port.allowed()&&!Thread.currentThread().isInterrupted())port.poll();
                return result;
            }
            pending=null;
        }
        if(originalInventory==null) {
            originalInventory=state.inventory;originalEquipment=state.equipment;
            for(Target target:targets) {
                long present=state.inventory.entrySet().stream().filter(e->isContainer(target.kind,e.getKey())).mapToLong(Map.Entry::getValue).sum()
                    +state.equipment.entrySet().stream().filter(e->isContainer(target.kind,e.getKey())).mapToLong(Map.Entry::getValue).sum();
                if(present!=1)return stop(target.kind.label+" must be carried exactly once after native setup loading");
            }
        }
        for(Target target:targets) {
            Map<Integer,Integer> contents=state.contents(target.kind);
            if(contents==null) { status="Waiting for live "+target.kind.label.toLowerCase(Locale.ROOT)+" contents";return result; }
            if(target.kind==Kind.RUNES&&!target.contents.equals(contents))return runes(target,state,now);
        }
        // Stock was credited across both ammo containers. Free every surplus first so it
        // can satisfy the other container without a duplicate purchase or a false shortfall.
        for(Target target:targets)if(target.kind!=Kind.RUNES&&state.contents(target.kind).entrySet().stream()
            .anyMatch(e->e.getValue()>target.contents.getOrDefault(e.getKey(),0)))return ammunition(target,state,now);
        for(Target target:targets)if(target.kind!=Kind.RUNES&&!target.contents.equals(state.contents(target.kind)))
            return ammunition(target,state,now);
        return restore(state,now);
    }
    private Result runes(Target target,Snapshot state,long now) {
        if(!bank(state,now))return result;
        if(!state.open(Kind.RUNES)) {
            act("Opening selected rune-pouch bank configuration",s->s.open(Kind.RUNES),
                ()->port.open(Kind.RUNES,carriedContainer(state,Kind.RUNES)),now);return result;
        }
        Map<Integer,Integer> contents=state.contents(Kind.RUNES);
        if(contents.entrySet().stream().anyMatch(e->e.getValue()>target.contents.getOrDefault(e.getKey(),0))) {
            act("Banking previous rune-pouch contents",s->s.contents(Kind.RUNES)!=null&&s.contents(Kind.RUNES).isEmpty(),port::emptyRunes,now);
            return result;
        }
        for(Map.Entry<Integer,Integer> row:target.contents.entrySet()) {
            int before=contents.getOrDefault(row.getKey(),0),missing=row.getValue()-before;
            if(missing<=0)continue;
            if(state.stocked(row.getKey())<missing)return stop("Bank is short "+missing+" rune-pouch runes for item "+row.getKey());
            act("Loading full rune-pouch quantity for item "+row.getKey(),
                s->s.contents(Kind.RUNES)!=null&&s.contents(Kind.RUNES).getOrDefault(row.getKey(),0).intValue()==row.getValue().intValue(),
                ()->port.withdraw(row.getKey(),missing),now);return result;
        }
        return result;
    }
    private Result ammunition(Target target,Snapshot state,long now) {
        if(closeOtherInterfaces(state,target.kind,now))return result;
        int container=carriedContainer(state,target.kind);
        if(container<=0) {
            int worn=wornContainer(state,target.kind);
            if(worn<=0)return stop("Selected "+target.kind.label.toLowerCase(Locale.ROOT)+" is no longer carried");
            if(!space(state,1,now))return result;
            if(state.bankOpen) { closeBank(state,now);return result; }
            act("Removing quiver temporarily for ammunition loading",s->s.carried(worn)>0&&!s.equipment.containsKey(worn),
                ()->port.unequip(worn),now);return result;
        }
        Map<Integer,Integer> contents=state.contents(target.kind);
        for(Map.Entry<Integer,Integer> row:contents.entrySet()) {
            if(row.getValue()<=target.contents.getOrDefault(row.getKey(),0))continue;
            if(state.carried(row.getKey())==0&&!space(state,1,now))return result;
            if(state.bankOpen) { closeBank(state,now);return result; }
            if(!state.open(target.kind)) {
                act("Opening "+target.kind.label.toLowerCase(Locale.ROOT)+" to remove previous ammunition",s->s.open(target.kind),
                    ()->port.open(target.kind,container),now);return result;
            }
            int before=row.getValue();
            act("Removing previous "+target.kind.label.toLowerCase(Locale.ROOT)+" ammunition "+row.getKey(),
                s->s.contents(target.kind)!=null&&s.contents(target.kind).getOrDefault(row.getKey(),0)<before,
                ()->port.remove(target.kind,row.getKey()),now);return result;
        }
        if(state.open(target.kind)) { close(target.kind,now);return result; }
        for(Map.Entry<Integer,Integer> row:target.contents.entrySet()) {
            int before=contents.getOrDefault(row.getKey(),0),missing=row.getValue()-before;
            if(missing<=0)continue;
            int carried=state.carried(row.getKey());
            // Use-on-container transfers a whole stack. Isolate the exact top-up before selecting it.
            if(carried>missing) {
                if(!bank(state,now))return result;
                act("Banking surplus carried ammunition before exact container loading",s->s.carried(row.getKey())==missing,
                    ()->port.deposit(row.getKey(),carried-missing),now);return result;
            }
            if(carried<missing) {
                if(!bank(state,now))return result;
                if(carried==0&&!space(state,1,now))return result;
                int withdraw=missing-carried;
                if(state.stocked(row.getKey())<withdraw)return stop("Bank is short "+withdraw+" "+target.kind.label.toLowerCase(Locale.ROOT)+" ammunition for item "+row.getKey());
                act("Withdrawing exact "+target.kind.label.toLowerCase(Locale.ROOT)+" ammunition "+row.getKey(),s->s.carried(row.getKey())==missing,
                    ()->port.withdraw(row.getKey(),withdraw),now);return result;
            }
            if(state.bankOpen) { closeBank(state,now);return result; }
            if(state.selectedItem!=row.getKey()) {
                if(state.selectedItem!=0) {
                    act("Clearing a previous item selection",s->s.selectedItem==0,port::clearSelection,now);return result;
                }
                act("Selecting exact container ammunition "+row.getKey(),s->s.selectedItem==row.getKey(),
                    ()->port.use(row.getKey()),now);return result;
            }
            act("Waiting for exact "+target.kind.label.toLowerCase(Locale.ROOT)+" loading receipt for item "+row.getKey(),
                s->s.contents(target.kind)!=null&&s.contents(target.kind).getOrDefault(row.getKey(),0).intValue()==row.getValue().intValue()
                    &&s.carried(row.getKey())==0&&s.selectedItem==0,
                ()->port.use(container),now);return result;
        }
        return result;
    }
    private Result restore(Snapshot state,long now) {
        if(closeOtherInterfaces(state,null,now))return result;
        for(Map.Entry<Integer,Integer> row:originalEquipment.entrySet()) {
            if(!isContainer(Kind.QUIVER,row.getKey())||state.equipment.containsKey(row.getKey()))continue;
            if(state.carried(row.getKey())<=0)return stop("Original worn quiver is missing during final setup restoration");
            if(state.bankOpen) { closeBank(state,now);return result; }
            act("Restoring selected worn quiver",s->s.equipment.getOrDefault(row.getKey(),0).intValue()==row.getValue().intValue(),
                ()->port.equip(row.getKey()),now);return result;
        }
        if(!state.equipment.equals(originalEquipment))return stop("Equipment changed during container preparation; restore the selected native setup");
        for(Map.Entry<Integer,Integer> row:state.inventory.entrySet()) {
            int excess=row.getValue()-originalInventory.getOrDefault(row.getKey(),0);
            if(excess<=0)continue;
            if(!bank(state,now))return result;
            act("Banking temporary container-loading items",s->s.carried(row.getKey())==row.getValue()-excess,
                ()->port.deposit(row.getKey(),excess),now);return result;
        }
        for(Map.Entry<Integer,Integer> row:originalInventory.entrySet()) {
            int missing=row.getValue()-state.carried(row.getKey());
            if(missing<=0)continue;
            if(!bank(state,now))return result;
            if(state.stocked(row.getKey())<missing)return stop("Bank cannot restore selected inventory item "+row.getKey()+" after container loading");
            act("Restoring selected inventory item "+row.getKey(),s->s.carried(row.getKey())==row.getValue(),
                ()->port.withdraw(row.getKey(),missing),now);return result;
        }
        if(!exact(state))return result;
        status="Full selected rune-pouch, bolt-pouch and quiver contents verified; inventory and equipment restored";
        return result=Result.READY;
    }
    private boolean bank(Snapshot state,long now) {
        if(state.selectedItem!=0) {
            act("Clearing previous item selection before banking",s->s.selectedItem==0,port::clearSelection,now);return false;
        }
        if(state.bankOpen)return true;
        if(closeOtherInterfaces(state,null,now))return false;
        act("Opening bank for exact container preparation",s->s.bankOpen,port::openBank,now);return false;
    }
    private void closeBank(Snapshot state,long now) {
        if(state.open(Kind.RUNES)) { close(Kind.RUNES,now);return; }
        act("Closing bank for container interaction",s->!s.bankOpen,port::closeBank,now);
    }
    private boolean closeOtherInterfaces(Snapshot state,Kind keep,long now) {
        for(Kind kind:Kind.values())if(kind!=keep&&state.open(kind)) { close(kind,now);return true; }
        return false;
    }
    private void close(Kind kind,long now) {
        act("Closing "+kind.label.toLowerCase(Locale.ROOT)+" configuration",s->!s.open(kind),()->port.close(kind),now);
    }
    private boolean space(Snapshot state,int required,long now) {
        if(state.freeSlots>=required)return true;
        if(!bank(state,now))return false;
        for(Map.Entry<Integer,Integer> row:state.inventory.entrySet())if(!anyContainer(row.getKey())) {
            act("Temporarily banking selected supplies for container-loading space",s->s.carried(row.getKey())==0,
                ()->port.deposit(row.getKey(),row.getValue()),now);return false;
        }
        stop("No bankable inventory space is available for container preparation");return false;
    }
    private void act(String text,Predicate<Snapshot> receipt,Runnable action,long now) {
        if(!port.allowed()||Thread.currentThread().isInterrupted())return;
        status=text;pendingStatus=text;pending=receipt;pendingAt=now;
        action.run();
    }
    private Result stop(String text) { status=text;return result=Result.STOPPED; }

    /** Native input remains on the preparation worker; only snapshots run on the client thread. */
    private static final class NativePort implements Port {
        private final BooleanSupplier allowed;
        private AmountAction amountAction;
        NativePort(BooleanSupplier allowed) { this.allowed=allowed; }
        private <T>T read(Supplier<T> value,T fallback) {
            return Microbot.getClientThread().runOnClientThreadOptional(value::get).orElse(fallback);
        }
        public boolean allowed() { return allowed.getAsBoolean()&&!Thread.currentThread().isInterrupted(); }
        public Snapshot snapshot() { return read(()->capture(Microbot.getClient()),null); }
        public void openBank() { if(allowed())Rs2Bank.openBank(); }
        public void closeBank() { if(allowed())Rs2Bank.closeBank(); }
        public void withdraw(int id,int amount) { if(allowed()) { amountAction=new AmountAction(id,amount,true);poll(); } }
        public void deposit(int id,int amount) { if(allowed()) { amountAction=new AmountAction(id,amount,false);poll(); } }
        public void poll() { if(amountAction!=null&&allowed()&&amountAction.tick())amountAction=null; }
        public void unequip(int id) { if(allowed())Rs2Equipment.unEquip(id); }
        public void equip(int id) { if(allowed())Rs2Inventory.wear(id); }
        public void use(int id) { if(allowed())Rs2Inventory.use(id); }
        public void clearSelection() { if(allowed())Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE); }
        public void open(Kind kind,int id) {
            String action=read(()->{
                var item=Rs2Inventory.get(id);if(item==null)return null;
                List<String> choices=kind==Kind.RUNES?List.of("Configure"):kind==Kind.BOLTS?List.of("Open","Check"):List.of("Check","Open","Configure");
                for(String choice:choices)if(Arrays.stream(item.getInventoryActions()).anyMatch(a->choice.equalsIgnoreCase(a))
                    ||item.getIndexOfSubAction(choice)!=null)return choice;
                return null;
            },null);
            if(action!=null&&allowed())Rs2Inventory.interact(id,action);
        }
        public void close(Kind kind) {
            click(kind==Kind.RUNES?InterfaceID.Bankside.RUNEPOUCH_DISMISS:kind==Kind.BOLTS?InterfaceID.XbowsPouch.CLOSE:InterfaceID.DizanasQuiver.CLOSE);
        }
        public void emptyRunes() { click(InterfaceID.Bankside.RUNEPOUCH_BANKALL); }
        public void remove(Kind kind,int ammo) {
            int[] widgets=kind==Kind.BOLTS?new int[]{InterfaceID.XbowsPouch.BOLT_1,InterfaceID.XbowsPouch.BOLT_2,InterfaceID.XbowsPouch.BOLT_3,InterfaceID.XbowsPouch.BOLT_4}:
                new int[]{InterfaceID.DizanasQuiver.AMMO_OBJ,InterfaceID.DizanasQuiver.AMMO_SLOT};
            WidgetAction action=read(()->{
                for(String wanted:List.of("removeall","withdrawall","empty","remove","withdraw"))for(int id:widgets) {
                    Widget parent=Microbot.getClient().getWidget(id);if(parent==null)continue;
                    List<Widget> candidates=new ArrayList<>();candidates.add(parent);
                    if(parent.getChildren()!=null)candidates.addAll(Arrays.asList(parent.getChildren()));
                    for(Widget widget:candidates) {
                        if(widget==null||widget.isHidden()||widget.getItemId()!=ammo||widget.getActions()==null)continue;
                        String[] actions=widget.getActions();
                        for(int i=0;i<actions.length;i++)if(actions[i]!=null&&normalize(actions[i]).equals(wanted))
                            return new WidgetAction(widget.getId(),widget.getIndex(),ammo,i+1,actions[i],widget.getBounds());
                    }
                }
                return null;
            },null);
            invoke(action);
        }
        private static String normalize(String text) { return Text.removeTags(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z]",""); }
        private void invoke(WidgetAction action) {
            if(action!=null&&!action.bounds.isEmpty()&&allowed())Microbot.doInvoke(new NewMenuEntry().option(action.action).target("").type(MenuAction.CC_OP)
                .identifier(action.op).param0(action.index).param1(action.id).itemId(action.item).forceLeftClick(false),action.bounds);
        }
        private boolean amountPrompt() {
            return read(()->{
                Widget text=Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT);
                return text!=null&&!text.isHidden()&&"enter amount:".equalsIgnoreCase(Text.removeTags(text.getText()).trim());
            },false);
        }
        /** One menu or prompt step per poll; a paused amount is never submitted into another UI. */
        private final class AmountAction {
            final int id,amount;
            final boolean withdraw;
            final boolean runeOpen;
            int stage;
            int tabRequested=-1;
            boolean noteRequested,scrolled,aborted;
            AmountAction(int id,int amount,boolean withdraw) {
                this.id=id;this.amount=amount;this.withdraw=withdraw;
                runeOpen=read(()->visible(Microbot.getClient(),InterfaceID.Bankside.RUNEPOUCH_CONTAINER),false);
            }
            private boolean sameBank() {
                return read(()->Rs2Bank.isOpen()&&visible(Microbot.getClient(),InterfaceID.Bankside.RUNEPOUCH_CONTAINER)==runeOpen,false);
            }
            boolean tick() {
                if(aborted||!allowed())return false;
                if(!sameBank())throw new IllegalStateException("Container amount input lost its bank or pouch configuration");
                if(stage==0) {
                    if(withdraw&&read(()->Microbot.getClient().getVarbitValue(VarbitID.BANK_WITHDRAWNOTES)!=0,true)) {
                        if(!noteRequested) { noteRequested=true;click(InterfaceID.Bankmain.NOTE); }
                        return false;
                    }
                    if(withdraw) {
                        int[] row=read(()->{
                            var item=Rs2Bank.getBankItemForSavedId(id);
                            return item==null?null:new int[]{item.getId(),item.getSlot(),Rs2Bank.getItemTabForBankItem(item.getSlot())};
                        },null);
                        if(row==null||row[0]!=id||row[2]<0)return false;
                        if(!read(()->Rs2Bank.isTabOpen(row[2]),false)) {
                            if(tabRequested!=row[2]) {
                                WidgetAction tab=read(()->{
                                    Widget widget=Rs2Bank.getTabWidget(row[2]);
                                    return widget==null||widget.isHidden()?null:new WidgetAction(widget.getId(),10+row[2],-1,1,"View tab",widget.getBounds());
                                },null);
                                if(tab!=null) { tabRequested=row[2];invoke(tab); }
                            }
                            return false;
                        }
                        if(!scrolled) {
                            if(allowed())scrolled=read(()->Rs2Bank.scrollBankToSlot(row[1]),false);
                            return false;
                        }
                    }
                    WidgetAction action=read(()->transferAction(Microbot.getClient(),id,amount,withdraw),null);
                    if(action==null)return false;
                    // A visible stale amount prompt belongs to something else; never type into it.
                    if(amountPrompt())throw new IllegalStateException("Another bank amount prompt is already open");
                    invoke(action);
                    if(!normalize(action.action).endsWith("x"))return true;
                    stage=1;return false;
                }
                if(!amountPrompt())return false;
                for(char key:Integer.toString(amount).toCharArray()) {
                    if(!allowed()||!sameBank()||!amountPrompt()) { aborted=true;return false; }
                    Rs2Keyboard.keyPress(key);
                    long until=System.nanoTime()/1_000_000+net.runelite.client.plugins.microbot.util.math.Rs2Random.logNormalBounded(100,200);
                    net.runelite.client.plugins.microbot.util.Global.sleepUntil(()->!allowed()||System.nanoTime()/1_000_000>=until,300);
                }
                if(!allowed()||!sameBank()||!amountPrompt()) { aborted=true;return false; }
                Rs2Keyboard.enter();return true;
            }
        }
        private void click(int id) {
            Rectangle bounds=read(()->{
                Widget widget=Microbot.getClient().getWidget(id);
                return widget==null||widget.isHidden()||widget.getBounds()==null?null:new Rectangle(widget.getBounds());
            },null);
            if(bounds!=null&&!bounds.isEmpty()&&allowed())Microbot.getMouse().click(bounds);
        }
    }
    /** Client thread only: use the current bank row's operations instead of fixed menu offsets. */
    static WidgetAction transferAction(Client client,int item,int amount,boolean withdraw) {
        Widget parent=client.getWidget(withdraw?ComponentID.BANK_ITEM_CONTAINER:ComponentID.BANK_INVENTORY_ITEM_CONTAINER);
        if(parent==null||parent.isHidden()||parent.getChildren()==null)return null;
        String prefix=withdraw?"withdraw":"deposit";
        for(String wanted:List.of(prefix+amount,prefix+"x"))for(Widget widget:parent.getChildren()) {
            if(widget==null||widget.isHidden()||widget.getItemId()!=item||widget.getActions()==null)continue;
            String[] actions=widget.getActions();
            for(int i=0;i<actions.length;i++)if(actions[i]!=null) {
                String normalized=Text.removeTags(actions[i]).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if(normalized.equals(wanted))return new WidgetAction(widget.getId(),widget.getIndex(),item,i+1,actions[i],widget.getBounds());
            }
        }
        return null;
    }
    static final class WidgetAction {
        final int id,index,item,op;
        final String action;
        final Rectangle bounds;
        WidgetAction(int id,int index,int item,int op,String action,Rectangle bounds) {
            this.id=id;this.index=index;this.item=item;this.op=op;this.action=action;this.bounds=bounds==null?new Rectangle():new Rectangle(bounds);
        }
    }
    /** Client thread only. Read actual item containers and varbits together, without cached ammo totals. */
    static Snapshot capture(Client client) {
        if(client==null)return null;
        ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY),equipment=client.getItemContainer(InventoryID.EQUIPMENT);
        if(inventory==null||equipment==null||inventory.getItems()==null||equipment.getItems()==null)return null;
        boolean bankOpen=Rs2Bank.isOpen();
        ItemContainer bank=bankOpen?client.getItemContainer(InventoryID.BANK):null;
        if(bankOpen&&(bank==null||bank.getItems()==null||Rs2Bank.getBankLiveEpoch()<=0))return null;
        Map<Integer,Integer> bankCounts=bankOpen?counts(bank.getItems()):Map.of();
        if(bankCounts==null)return null;
        EnumMap<Kind,Map<Integer,Integer>> contents=readContents(client);
        if(contents==null)return null;
        EnumMap<Kind,Boolean> open=new EnumMap<>(Kind.class);
        open.put(Kind.RUNES,visible(client,InterfaceID.Bankside.RUNEPOUCH_CONTAINER));
        open.put(Kind.BOLTS,visible(client,InterfaceID.XbowsPouch.CONTENT));
        open.put(Kind.QUIVER,visible(client,InterfaceID.DizanasQuiver.CONTENT));
        Widget selected=client.isWidgetSelected()?client.getSelectedWidget():null;
        int selectedId=client.isWidgetSelected()?selected==null?-1:selected.getItemId():0;
        int occupied=(int)Arrays.stream(inventory.getItems()).filter(i->i!=null&&i.getId()>0&&i.getQuantity()>0).count();
        return new Snapshot(bankOpen,28-occupied,selectedId,counts(inventory.getItems()),counts(equipment.getItems()),bankCounts,contents,open);
    }
    private static boolean visible(Client client,int id) { Widget widget=client.getWidget(id);return widget!=null&&!widget.isHidden(); }
    private static Map<Integer,Integer> counts(Item[] items) {
        Map<Integer,Integer> result=new LinkedHashMap<>();
        for(Item item:items)if(item!=null&&item.getId()>0&&item.getQuantity()>0)result.merge(item.getId(),item.getQuantity(),Math::addExact);
        return result;
    }
    static EnumMap<Kind,Map<Integer,Integer>> readContents(Client client) {
        EnumMap<Kind,Map<Integer,Integer>> result=new EnumMap<>(Kind.class);
        Map<Integer,Integer> runes=new LinkedHashMap<>(),bolts=new LinkedHashMap<>(),quiver=new LinkedHashMap<>();
        int[] runeTypes={VarbitID.RUNE_POUCH_TYPE_1,VarbitID.RUNE_POUCH_TYPE_2,VarbitID.RUNE_POUCH_TYPE_3,VarbitID.RUNE_POUCH_TYPE_4};
        int[] runeAmounts={VarbitID.RUNE_POUCH_QUANTITY_1,VarbitID.RUNE_POUCH_QUANTITY_2,VarbitID.RUNE_POUCH_QUANTITY_3,VarbitID.RUNE_POUCH_QUANTITY_4};
        int[] boltTypes={VarbitID.XBOWS_POUCH_SLOT1,VarbitID.XBOWS_POUCH_SLOT2,VarbitID.XBOWS_POUCH_SLOT3,VarbitID.XBOWS_POUCH_SLOT4};
        int[] boltAmounts={VarbitID.XBOWS_POUCH_NUM1,VarbitID.XBOWS_POUCH_NUM2,VarbitID.XBOWS_POUCH_NUM3,VarbitID.XBOWS_POUCH_NUM4};
        for(int i=0;i<runeTypes.length;i++) {
            int amount=client.getVarbitValue(runeAmounts[i]);
            if(amount<0)return null;
            if(amount>0) {
                Runes rune=Runes.byVarbitId(client.getVarbitValue(runeTypes[i]));if(rune==null)return null;
                runes.merge(rune.getItemId(),amount,Math::addExact);
            }
            int boltAmount=client.getVarbitValue(boltAmounts[i]);
            if(boltAmount<0)return null;
            if(boltAmount>0) {
                Bolts bolt=Bolts.getBolt(client.getVarbitValue(boltTypes[i]));if(bolt==null)return null;
                bolts.merge(bolt.getItemId(),boltAmount,Math::addExact);
            }
        }
        int amount=client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
        if(amount<0)return null;
        if(amount>0) {
            int id=client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);if(id<=0)return null;
            quiver.put(id,amount);
        }
        result.put(Kind.RUNES,runes);result.put(Kind.BOLTS,bolts);result.put(Kind.QUIVER,quiver);return result;
    }
}
