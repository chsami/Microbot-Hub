package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.http.api.RuneLiteAPI;

/** Main adapters: live supply snapshots and narrow GE UI steps, all input off the client thread. */
final class ArenaSupplyPort implements ArenaGeBuyer.Port, AutoCloseable {
    private static final String JOURNAL="ownedSupplyOffer";
    static final WorldPoint GE_BANK=new WorldPoint(3163,3485,0);
    private final BooleanSupplier allowed;
    private final InventorySetup setup;
    private final LocalArenaActions local;
    private boolean bankSeen;
    private int requiredEpoch=-1;
    private long nextAction;
    private Submission submission;
    private ArenaGeBuyer.Order collecting;
    private long collectAt;
    private int depositEpoch=-1;
    private long depositAt;
    private WalletDeposit walletDeposit;
    private volatile boolean closed;
    ArenaSupplyPort(BooleanSupplier allowed, InventorySetup setup) {
        this.allowed=allowed;this.setup=setup;local=new LocalArenaActions(allowed);
    }
    private <T> T read(Supplier<T> value,T fallback) {
        return Microbot.getClientThread().runOnClientThreadOptional(value::get).orElse(fallback);
    }
    public boolean allowed(){return !closed&&allowed.getAsBoolean()&&!Thread.currentThread().isInterrupted();}
    private void requireAllowed(){if(!allowed())throw new IllegalStateException("Preparation input is held");}
    public boolean bankOpen(){return read(Rs2Bank::isOpen,false);}
    public ArenaSupplyReceipt stock() {
        if(!allowed())return null;
        String failure=local.routeFailure();if(failure!=null)throw new IllegalStateException(failure);
        if(!local.near(GE_BANK,12)) {local.walkWithSupplies(GE_BANK);return null;}
        local.cancel();
        if(exchangeOpen()){closeExchange();return null;}
        boolean open=bankOpen();int epoch=Rs2Bank.getBankLiveEpoch();
        if(!open) {
            if(bankSeen||requiredEpoch<0)requiredEpoch=epoch;
            bankSeen=false;
            if(System.currentTimeMillis()>=nextAction){requireAllowed();Rs2Bank.openBank();nextAction=System.currentTimeMillis()+1800;}
            return null;
        }
        if(requiredEpoch<0)requiredEpoch=0;
        if(epoch<=requiredEpoch)return null;
        bankSeen=true;
        return read(()->capture(Microbot.getClient(),Microbot.getItemManager(),setup,Rs2Bank.getBankLiveEpoch()),null);
    }
    /** Called only on the client thread. A missing container is unknown stock, never zero stock. */
    static ArenaSupplyReceipt capture(Client client,ItemManager items,InventorySetup setup,int epoch) {
        if(client==null||items==null||epoch<=0||!Rs2Bank.isOpen())return null;
        ItemContainer bank=client.getItemContainer(InventoryID.BANK),inventory=client.getItemContainer(InventoryID.INVENTORY),equipment=client.getItemContainer(InventoryID.EQUIPMENT);
        if(bank==null||inventory==null||equipment==null||bank.getItems()==null||inventory.getItems()==null||equipment.getItems()==null)return null;
        Map<Integer,Long> bankCounts=counts(bank.getItems(),items),inventoryCounts=counts(inventory.getItems(),items),equipmentCounts=counts(equipment.getItems(),items);
        if(bankCounts==null||inventoryCounts==null||equipmentCounts==null)return null;
        Map<Integer,String> names=new HashMap<>();
        for(Item[] container:List.of(bank.getItems(),inventory.getItems(),equipment.getItems()))for(Item item:container)
            if(item!=null&&item.getId()>0&&item.getQuantity()>0){
                ItemComposition composition=items.getItemComposition(item.getId());
                if(composition.getPlaceholderTemplateId()>0)continue;
                int id=canonicalizeWornItem(composition.getNote()!=-1?composition.getLinkedNoteId():item.getId());
                if(composition.getName()!=null)names.put(id,composition.getName());
            }
        Map<Integer,Long> stored=new HashMap<>();
        ArenaSupplyReceipt receipt=new ArenaSupplyReceipt(epoch,bankCounts,inventoryCounts,equipmentCounts,Map.of());
        if(Arrays.stream(RunePouchType.getPouchIds()).anyMatch(id->receipt.owned(id)>0)) {
            int[] types={Varbits.RUNE_POUCH_RUNE1,Varbits.RUNE_POUCH_RUNE2,Varbits.RUNE_POUCH_RUNE3,Varbits.RUNE_POUCH_RUNE4};
            int[] amounts={Varbits.RUNE_POUCH_AMOUNT1,Varbits.RUNE_POUCH_AMOUNT2,Varbits.RUNE_POUCH_AMOUNT3,Varbits.RUNE_POUCH_AMOUNT4};
            for(int i=0;i<types.length;i++) {
                Runes rune=Runes.byVarbitId(client.getVarbitValue(types[i]));int amount=client.getVarbitValue(amounts[i]);
                if(rune!=null&&amount>0)stored.merge(rune.getItemId(),(long)amount,Math::addExact);
            }
        }
        MInventorySetupsPlugin plugin=Microbot.getPlugin(MInventorySetupsPlugin.class);
        if(plugin!=null&&plugin.getAmmoHandler()!=null&&setup!=null) {
            if(setup.getBoltPouch()!=null&&receipt.owned(net.runelite.api.gameval.ItemID.XBOWS_BOLT_POUCH)>0)
                addStored(stored,plugin.getAmmoHandler().getBoltPouchData());
            boolean quiver=net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsQuiverPanel.DIZANA_QUIVER_IDS_SET.stream()
                .mapToInt(ArenaSupplyPort::canonicalizeWornItem).anyMatch(id->receipt.owned(id)>0);
            if(setup.getQuiver()!=null&&quiver)addStored(stored,plugin.getAmmoHandler().getQuiverData());
        }
        return new ArenaSupplyReceipt(epoch,bankCounts,inventoryCounts,equipmentCounts,stored,names);
    }
    private static void addStored(Map<Integer,Long> result,List<InventorySetupsItem> rows) {
        if(rows==null)return;
        for(InventorySetupsItem row:rows)if(row!=null&&row.getId()>0&&row.getQuantity()>0)result.merge(row.getId(),(long)row.getQuantity(),Math::addExact);
    }
    static Map<Integer,Long> counts(Item[] source,ItemManager items) {
        Map<Integer,Long> result=new HashMap<>();
        for(Item item:source)if(item!=null&&item.getId()>0&&item.getQuantity()>0) {
            ItemComposition composition=items.getItemComposition(item.getId());if(composition==null)return null;
            if(composition.getPlaceholderTemplateId()>0)continue;
            int id=canonicalizeWornItem(composition.getNote()!=-1?composition.getLinkedNoteId():item.getId());
            if(id<=0)return null;
            result.merge(id,(long)item.getQuantity(),Math::addExact);
        }
        return result;
    }
    public ArenaGeBuyer.Quote quote(int id) {
        requireAllowed();
        String name=read(()->{
            ItemComposition c=Microbot.getItemManager().getItemComposition(id);
            return c!=null&&c.isTradeable()&&c.getNote()==-1&&c.getPlaceholderTemplateId()<=0?c.getName():null;
        },null);
        if(name==null)return new ArenaGeBuyer.Quote(id,"Item "+id,0,false);
        // Price networking stays on this preparation worker, after the buyer recorded every deficit.
        int price=Rs2GrandExchange.getPrice(id);
        return new ArenaGeBuyer.Quote(id,name,price,true);
    }
    /** Make one physical slot available; a native helper's return value is never a deposit receipt. */
    public ArenaGeBuyer.WalletPreparation prepareWallet(long now){
        if(!allowed())return ArenaGeBuyer.WalletPreparation.WAITING;
        WalletStock state=walletStock();
        if(walletDeposit!=null){
            if(now-walletDeposit.at>15_000)throw new IllegalStateException("Wallet-space deposit was not acknowledged; no coin withdrawal was issued");
            if(state==null||now<nextAction||!walletDeposit.received(state))return ArenaGeBuyer.WalletPreparation.WAITING;
            walletDeposit=null;
            return ArenaGeBuyer.WalletPreparation.READY;
        }
        if(state==null){stock();return ArenaGeBuyer.WalletPreparation.WAITING;}
        if(now<nextAction)return ArenaGeBuyer.WalletPreparation.WAITING;
        if(state.ready())return ArenaGeBuyer.WalletPreparation.READY;
        WalletDeposit pending=new WalletDeposit(state,now);
        walletDeposit=pending;nextAction=now+1800;
        guarded(()->walletSourceUnchanged(pending),()->{
            // A quantity-one item frees one slot; a larger stack occupies one slot as well.
            if(pending.amount==1)Rs2Bank.depositOne(pending.rawId);
            else Rs2Bank.depositAll(pending.rawId);
        });
        return ArenaGeBuyer.WalletPreparation.WAITING;
    }
    private WalletStock walletStock(){return read(()->{
        Client client=Microbot.getClient();ItemManager items=Microbot.getItemManager();
        ArenaSupplyReceipt receipt=capture(client,items,setup,Rs2Bank.getBankLiveEpoch());
        if(receipt==null)return null;
        Item[] inventory=client.getItemContainer(InventoryID.INVENTORY).getItems();
        int occupied=0,slot=-1;
        for(int i=0;i<inventory.length;i++)if(inventory[i]!=null&&inventory[i].getId()>0&&inventory[i].getQuantity()>0){
            occupied++;if(slot<0&&inventory[i].getId()!=995)slot=i;
        }
        if(slot<0)return new WalletStock(receipt,28-occupied,-1,0,0,0);
        Item selected=inventory[slot];ItemComposition composition=items.getItemComposition(selected.getId());
        int canonical=canonicalizeWornItem(composition.getNote()!=-1?composition.getLinkedNoteId():selected.getId());
        return new WalletStock(receipt,28-occupied,slot,selected.getId(),canonical,selected.getQuantity());
    },null);}
    private boolean walletSourceUnchanged(WalletDeposit pending){
        return allowed()&&read(()->{
            Client client=Microbot.getClient();ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);
            if(!Rs2Bank.isOpen()||inventory==null||inventory.getItems()==null||pending.slot>=inventory.getItems().length)return false;
            Item source=inventory.getItems()[pending.slot];
            return source!=null&&source.getId()==pending.rawId&&source.getQuantity()==pending.amount;
        },false);
    }
    private static final class WalletStock{
        final ArenaSupplyReceipt receipt;final int freeSlots,slot,rawId,id,amount;
        WalletStock(ArenaSupplyReceipt receipt,int freeSlots,int slot,int rawId,int id,int amount){
            this.receipt=receipt;this.freeSlots=freeSlots;this.slot=slot;this.rawId=rawId;this.id=id;this.amount=amount;
        }
        boolean ready(){return freeSlots>0||receipt.inventory(995)>0;}
    }
    private static final class WalletDeposit{
        final int slot,rawId,id,amount,epoch;final long inventoryBefore,bankBefore;long at;
        WalletDeposit(WalletStock state,long at){
            if(state.slot<0||state.amount<=0)throw new IllegalStateException("No bankable inventory slot is available for coins");
            slot=state.slot;rawId=state.rawId;id=state.id;amount=state.amount;epoch=state.receipt.epoch;
            inventoryBefore=state.receipt.inventory(id);bankBefore=state.receipt.bank(id);this.at=at;
        }
        boolean received(WalletStock state){
            return state.ready()&&state.receipt.epoch>epoch
                &&state.receipt.inventory(id)==inventoryBefore-amount&&state.receipt.bank(id)>=Math.addExact(bankBefore,amount);
        }
    }
    public void withdrawCoins(int amount){
        requireAllowed();WalletStock state=walletStock();
        if(walletDeposit!=null||state==null||!state.ready())throw new IllegalStateException("Coin withdrawal requires an observed existing coin stack or free inventory slot");
        guarded(()->{
            WalletStock current=walletStock();return current!=null&&current.ready()&&walletDeposit==null;
        },()->{Rs2Bank.setWithdrawAsItem();Rs2Bank.withdrawX(995,amount);});
    }
    public int carriedCoins(){return read(()->Rs2Inventory.itemQuantity(995),0);}
    public boolean closeBank(){requireAllowed();bankSeen=false;requiredEpoch=Rs2Bank.getBankLiveEpoch();return Rs2Bank.closeBank();}
    public boolean exchangeOpen(){return read(()->Rs2GrandExchange.isOpen()||Rs2GrandExchange.isOfferScreenOpen(),false);}
    public void openExchange(){requireAllowed();if(bankOpen()){closeBank();return;}Rs2GrandExchange.openExchange();}
    public void closeExchange(){if(!allowed())return;Rs2GrandExchange.closeExchange();bankSeen=false;requiredEpoch=Rs2Bank.getBankLiveEpoch();}
    public int emptySlot(){return read(()->{
        GrandExchangeOffer[] offers=Microbot.getClient().getGrandExchangeOffers();if(offers==null)return -1;
        for(int i=0;i<Math.min(8,offers.length);i++)if(offers[i]==null||offers[i].getState()==GrandExchangeOfferState.EMPTY)return i;
        return -1;
    },-1);}
    public ArenaGeBuyer.Offer offer(int slot){return read(()->{
        GrandExchangeOffer[] offers=Microbot.getClient().getGrandExchangeOffers();
        if(offers==null||slot<0||slot>=offers.length)throw new IllegalStateException("GE offer snapshot unavailable");
        GrandExchangeOffer offer=offers[slot];if(offer==null||offer.getState()==GrandExchangeOfferState.EMPTY)return ArenaGeBuyer.Offer.empty();
        GrandExchangeOfferState state=offer.getState();
        ArenaGeBuyer.OfferState kind=state==GrandExchangeOfferState.BUYING?ArenaGeBuyer.OfferState.BUYING:
            state==GrandExchangeOfferState.BOUGHT?ArenaGeBuyer.OfferState.BOUGHT:
            state==GrandExchangeOfferState.CANCELLED_BUY?ArenaGeBuyer.OfferState.CANCELLED:ArenaGeBuyer.OfferState.OTHER;
        return new ArenaGeBuyer.Offer(kind,offer.getItemId(),offer.getTotalQuantity(),Math.toIntExact(offer.getPrice()),offer.getQuantitySold(),offer.getSpent());
    },null);}
    public ArenaGeBuyer.Order load(){
        String raw=Microbot.getConfigManager().getRSProfileConfiguration(DroInfernoConfig.GROUP,JOURNAL);
        if(raw==null||raw.isBlank())return null;
        try{return RuneLiteAPI.GSON.fromJson(raw,ArenaGeBuyer.Order.class);}
        catch(RuntimeException invalid){throw new IllegalStateException("Saved GE ownership receipt could not be read; inspect offers before restarting");}
    }
    public void save(ArenaGeBuyer.Order order){
        if(order==null)Microbot.getConfigManager().unsetRSProfileConfiguration(DroInfernoConfig.GROUP,JOURNAL);
        else Microbot.getConfigManager().setRSProfileConfiguration(DroInfernoConfig.GROUP,JOURNAL,RuneLiteAPI.GSON.toJson(order));
    }
    public void place(ArenaGeBuyer.Order order,String name){
        requireAllowed();if(offer(order.slot).state!=ArenaGeBuyer.OfferState.EMPTY||!exchangeOpen())throw new IllegalStateException("GE slot changed before buy form");
        submission=new Submission(order,name);
    }
    private void exact(ArenaGeBuyer.Order order) {
        if(!owned(order,false))
            throw new IllegalStateException("Owned GE slot identity changed; no input issued");
    }
    private boolean owned(ArenaGeBuyer.Order order,boolean terminal){
        if(!journalOwns(order))return false;
        ArenaGeBuyer.Offer live=offer(order.slot);
        return live!=null&&live.id==order.id&&live.quantity==order.quantity&&live.price==order.price
            &&(terminal?live.terminal():live.state==ArenaGeBuyer.OfferState.BUYING||live.terminal());
    }
    private boolean journalOwns(ArenaGeBuyer.Order expected){
        try{
            ArenaGeBuyer.Order saved=load();
            return saved!=null&&saved.slot==expected.slot&&saved.id==expected.id
                &&saved.quantity==expected.quantity&&saved.price==expected.price
                &&saved.attempt==expected.attempt&&saved.placedAt==expected.placedAt;
        }catch(RuntimeException unavailable){return false;}
    }
    public void cancel(ArenaGeBuyer.Order order){
        requireAllowed();exact(order);
        WidgetTarget target=widget((465<<16)|(7+order.slot),-1);
        invoke(target,"Abort offer",2,2,-1,()->owned(order,false));
    }
    public void collect(ArenaGeBuyer.Order order){
        requireAllowed();exact(order);collecting=order;collectAt=System.currentTimeMillis();
        invoke(widget((465<<16)|(7+order.slot),-1),"View offer",1,2,-1,()->owned(order,true));
    }
    public void poll(){
        if(!allowed())return;
        if(submission!=null){if(submission.tick())submission=null;return;}
        if(collecting==null)return;
        if(offer(collecting.slot).state==ArenaGeBuyer.OfferState.EMPTY){collecting=null;return;}
        exact(collecting);
        if(System.currentTimeMillis()-collectAt>12_000)throw new IllegalStateException("Owned slot collection was not acknowledged");
        int[] control=read(()->{
            Client c=Microbot.getClient();
            if(c.getVarbitValue(VarbitID.GE_SELECTEDSLOT)!=collecting.slot+1||!Rs2GrandExchange.isOfferScreenOpen())return null;
            Widget parent=c.getWidget(465,24);if(parent==null||parent.isHidden()||parent.getChildren()==null)return null;
            for(Widget child:parent.getChildren())if(child!=null&&!child.isHidden()&&child.getItemQuantity()>0&&child.getActions()!=null)
                for(int i=0;i<child.getActions().length;i++)if("Bank".equalsIgnoreCase(child.getActions()[i]))
                    return new int[]{child.getId(),child.getIndex(),i+1,child.getItemId()};
            return null;
        },null);
        if(control!=null){
            ArenaGeBuyer.Order expected=collecting;
            invoke(widget(control[0],control[1]),"Bank",control[2],control[1],control[3],
                ()->owned(expected,true)&&read(()->Microbot.getClient().getVarbitValue(VarbitID.GE_SELECTEDSLOT)==expected.slot+1,false));
        }
    }
    private boolean prompt(){return read(()->Rs2Widget.isWidgetVisible(net.runelite.api.gameval.InterfaceID.Chatbox.MES_TEXT2),false);}
    private boolean selected(int id){return read(()->Microbot.getClient().getVarpValue(VarPlayer.CURRENT_GE_ITEM)==id,false);}
    private boolean priced(int value){return read(()->Microbot.getClient().getVarpLongValue(5753)==value,false);}
    private boolean quantity(int value){return read(()->Microbot.getClient().getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)==value,false);}
    private void typeAllowed(String text){
        for(char key:text.toCharArray()){
            requireAllowed();Rs2Keyboard.keyPress(key);
            long until=System.nanoTime()/1_000_000+net.runelite.client.plugins.microbot.util.math.Rs2Random.logNormalBounded(100,200);
            net.runelite.client.plugins.microbot.util.Global.sleepUntil(()->!allowed()||System.nanoTime()/1_000_000>=until,300);
        }
        requireAllowed();
    }
    private void requireBuySlot(ArenaGeBuyer.Order order){
        if(!read(()->Microbot.getClient().getVarbitValue(VarbitID.GE_SELECTEDSLOT)==order.slot+1,false))
            throw new IllegalStateException("GE buy form changed to another slot; no confirmation issued");
    }
    /** Re-read the slot and every final field together immediately before dispatching Confirm. */
    void confirmOrder(ArenaGeBuyer.Order order){
        requireAllowed();
        requireBuyFields(order);
        click(named("Confirm"),()->buyFields(order));
    }
    void confirmExpensiveOrder(ArenaGeBuyer.Order order){
        requireAllowed();requireBuyFields(order);
        click(named("Yes"),()->buyFields(order)&&read(()->Rs2Widget.hasWidget("Your offer is much"),false));
    }
    private void requireBuyFields(ArenaGeBuyer.Order order){
        if(!buyFields(order))throw new IllegalStateException("GE buy slot or fields changed before confirmation; no confirmation issued");
    }
    private boolean buyFields(ArenaGeBuyer.Order order){
        if(!journalOwns(order))return false;
        return read(()->{
            Client c=Microbot.getClient();
            GrandExchangeOffer[] offers=c.getGrandExchangeOffers();
            return c.getVarbitValue(VarbitID.GE_SELECTEDSLOT)==order.slot+1
                &&c.getVarpValue(VarPlayer.CURRENT_GE_ITEM)==order.id
                &&c.getVarpLongValue(5753)==order.price
                &&c.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)==order.quantity
                &&offers!=null&&order.slot>=0&&order.slot<offers.length
                &&(offers[order.slot]==null||offers[order.slot].getState()==GrandExchangeOfferState.EMPTY);
        },false);
    }
    private static final class WidgetTarget {
        final int id,index;final Rectangle bounds;
        WidgetTarget(int id,int index,Rectangle bounds){this.id=id;this.index=index;this.bounds=bounds;}
    }
    private WidgetTarget widget(int id,int index){return read(()->{
        Widget parent=Microbot.getClient().getWidget(id);Widget target=parent==null||index<0?parent:parent.getChild(index);
        return target==null||target.isHidden()||target.getBounds()==null?null:new WidgetTarget(target.getId(),target.getIndex(),new Rectangle(target.getBounds()));
    },null);}
    private void click(WidgetTarget target){requireAllowed();if(target==null||target.bounds.isEmpty())throw new IllegalStateException("GE control unavailable");Microbot.getMouse().click(target.bounds);}
    private boolean visible(WidgetTarget target){
        WidgetTarget current=widget(target.id,target.index);
        return current!=null&&target.bounds.equals(current.bounds);
    }
    /** Keep the native preparation motion/background profile, adding a final ownership checkpoint. */
    private void guarded(BooleanSupplier ownership,Runnable input){
        requireAllowed();
        BooleanSupplier parent=MouseMotionScope.isPresent()?MouseMotionScope.capture(()->{
            try{MouseMotionScope.checkpoint();return true;}catch(MouseMotionScope.Cancelled stale){return false;}
        }):()->true;
        try(var scope=MouseMotionScope.open(new MouseMotionScope.Profile(){
            public MouseMotionFactory motion(MouseMotionFactory source){return source;}
            public long clickPause(){return 0;}
            public boolean isValid(){return allowed()&&parent.getAsBoolean();}
            public boolean shouldClick(){return ownership.getAsBoolean();}
        })){input.run();}
    }
    private void click(WidgetTarget target,BooleanSupplier ownership){
        requireAllowed();if(target==null||target.bounds.isEmpty())throw new IllegalStateException("GE control unavailable");
        guarded(()->ownership.getAsBoolean()&&visible(target),()->Microbot.getMouse().click(target.bounds));
    }
    private void invoke(WidgetTarget target,String action,int identifier,int param0,int itemId,BooleanSupplier ownership){
        requireAllowed();if(target==null||target.bounds.isEmpty())throw new IllegalStateException("Owned GE control unavailable");
        BooleanSupplier live=()->allowed()&&ownership.getAsBoolean()&&visible(target);
        guarded(live,()->{
            ArenaMenuEntry entry=ArenaMenuEntry.guarded(new NewMenuEntry().option(action).target("").identifier(identifier)
                .type(MenuAction.CC_OP).param0(param0).param1(target.id).itemId(itemId).forceLeftClick(false));
            entry.liveCheck(live);
            Microbot.doInvoke(entry,target.bounds);
        });
    }
    private WidgetTarget named(String name){return read(()->{
        Widget w=Rs2Widget.findWidget(name);return w==null||w.isHidden()?null:new WidgetTarget(w.getId(),w.getIndex(),new Rectangle(w.getBounds()));
    },null);}
    private final class Submission {
        final ArenaGeBuyer.Order order;final String name;
        int phase;long phaseAt=System.currentTimeMillis(),next;
        Submission(ArenaGeBuyer.Order order,String name){this.order=order;this.name=name;}
        void advance(){phase++;phaseAt=System.currentTimeMillis();next=phaseAt+600;}
        boolean tick(){
            requireAllowed();long now=System.currentTimeMillis();if(now<next)return false;
            ArenaGeBuyer.Offer live=offer(order.slot);
            if(live.state!=ArenaGeBuyer.OfferState.EMPTY){exact(order);return true;}
            if(now-phaseAt>10_000)throw new IllegalStateException("GE buy form step "+phase+" was not acknowledged");
            switch(phase){
                case 0:click(widget((465<<16)|(7+order.slot),0));advance();break;
                case 1:
                    if(!read(()->Rs2Widget.isWidgetVisible(net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_OFFER_DESCRIPTION),false))break;
                    requireBuySlot(order);
                    typeAllowed(name.length()>25?name.substring(0,25):name);advance();break;
                case 2:
                    WidgetTarget result=read(()->{
                        var pair=Rs2GrandExchange.getSearchResultWidget(name,true);Widget w=pair==null?null:pair.getLeft();
                        return w==null||w.isHidden()?null:new WidgetTarget(w.getId(),w.getIndex(),new Rectangle(w.getBounds()));
                    },null);
                    if(result!=null){click(result);advance();}break;
                case 3:if(selected(order.id)){if(priced(order.price)){phase=5;advance();}else{click(widget((465<<16)|26,12));advance();}}break;
                case 4:if(prompt()){typeAllowed(Integer.toString(order.price));requireAllowed();Rs2Keyboard.enter();advance();}break;
                case 5:if(!prompt()&&priced(order.price))advance();break;
                case 6:if(quantity(order.quantity)){phase=8;advance();}else{click(widget((465<<16)|26,7));advance();}break;
                case 7:if(prompt()){typeAllowed(Integer.toString(order.quantity));requireAllowed();Rs2Keyboard.enter();advance();}break;
                case 8:if(!prompt()&&quantity(order.quantity))advance();break;
                case 9:
                    confirmOrder(order);advance();break;
                case 10:
                    if(read(()->Rs2Widget.hasWidget("Your offer is much"),false)){confirmExpensiveOrder(order);advance();}break;
                default:break;
            }
            return false;
        }
    }
    void pause(){local.cancel();}
    int canonical(int id){return read(()->{
        ItemComposition c=Microbot.getItemManager().getItemComposition(id);
        if(c==null)throw new IllegalStateException("Selected item metadata unavailable; no purchase allowed");
        return canonicalizeWornItem(c.getNote()!=-1?c.getLinkedNoteId():id);
    },id);}
    boolean bankInventory(){
        if(!allowed()||!bankOpen())return false;
        int first=read(()->{
            ItemContainer inv=Microbot.getClient().getItemContainer(InventoryID.INVENTORY);
            if(inv==null||inv.getItems()==null)return -2;
            for(Item item:inv.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)return item.getId();
            return -1;
        },-2);
        if(first==-2)return false;
        if(first==-1)return depositEpoch<0||Rs2Bank.getBankLiveEpoch()>depositEpoch;
        long now=System.currentTimeMillis();
        if(depositEpoch>=0&&now-depositAt>30_000)throw new IllegalStateException("Banking preparation items was not acknowledged");
        if(now<nextAction)return false;
        if(depositEpoch<0){depositEpoch=Rs2Bank.getBankLiveEpoch();depositAt=now;}
        // Explicit item deposits preserve user locks and still clear completed travel/preparation items.
        requireAllowed();Rs2Bank.depositAll(first);nextAction=now+1800;return false;
    }
    void resumeAfter(long millis){
        if(millis<=0)return;nextAction+=millis;if(collectAt>0)collectAt+=millis;if(depositAt>0)depositAt+=millis;
        if(submission!=null){submission.phaseAt+=millis;submission.next+=millis;}
        if(walletDeposit!=null)walletDeposit.at+=millis;
    }
    @Override public void close(){closed=true;local.cancel();submission=null;collecting=null;walletDeposit=null;}
    /** The public client has no worn-item identity table; ids are compared as they are. */
    private static int canonicalizeWornItem(int id){return id;}
}
