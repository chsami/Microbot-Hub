package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One exact owned offer at a time. All prices follow a complete reliable supply receipt. */
final class ArenaGeBuyer {
    enum Phase { BANK, PLAN, PREPARE_WALLET, PRICE, WALLET, OPEN_GE, PLACE, WAIT, CANCEL, WAIT_CANCEL, COLLECT, CLEAR, VERIFY, DONE, STOPPED }
    enum WalletPreparation { WAITING, READY }
    enum OfferState { EMPTY, BUYING, BOUGHT, CANCELLED, OTHER }
    static final class Offer {
        final OfferState state; final int id, quantity, price, filled; final long spent;
        Offer(OfferState state, int id, int quantity, int price, int filled, long spent) {
            this.state=state; this.id=id; this.quantity=quantity; this.price=price; this.filled=filled; this.spent=spent;
        }
        static Offer empty() { return new Offer(OfferState.EMPTY,0,0,0,0,0); }
        boolean terminal() { return state == OfferState.BOUGHT || state == OfferState.CANCELLED; }
    }
    static final class Quote {
        final int id, price; final String name; final boolean tradeable;
        Quote(int id, String name, int price, boolean tradeable) { this.id=id;this.name=name;this.price=price;this.tradeable=tradeable; }
    }
    /** Persisted before dispatch. A timeout can never blindly submit this order again. */
    static final class Order {
        int id, slot, quantity, price, filled, attempt;
        long spent, ownedBefore, coinsBefore, priorSpent, placedAt;
        boolean acknowledged, collecting;
    }
    interface Port {
        boolean allowed();
        /** Opens/refreshes the GE bank, then returns one atomic live receipt; null means pending. */
        ArenaSupplyReceipt stock();
        /** One bank deposit at most, followed by observed capacity and bank/inventory receipts. */
        WalletPreparation prepareWallet(long now);
        Quote quote(int id);
        void withdrawCoins(int amount); int carriedCoins();
        boolean closeBank(); boolean bankOpen();
        boolean exchangeOpen(); void openExchange(); void closeExchange();
        int emptySlot(); Offer offer(int slot);
        void place(Order order, String name); void cancel(Order order); void collect(Order order);
        Order load(); void save(Order order);
        default void poll() { }
    }
    private final Port port;
    private final ArenaPreparationConfig config;
    private List<ArenaSupplies.Supply> supplies;
    private final Map<Integer, Quote> quotes = new HashMap<>();
    private final Map<Integer, Integer> attempts = new HashMap<>();
    private Phase phase=Phase.BANK;
    private String status="Checking owned supplies against a live bank";
    private long phaseAt, spent;
    private Order order;
    private ArenaSupplies.Supply current;
    private int quantity, price;
    private String itemName;
    private long ownedBefore, coinsBefore;
    private boolean cancelled;

    ArenaGeBuyer(Port port, ArenaPreparationConfig config, List<ArenaSupplies.Supply> supplies, long now) {
        this.port=port;this.config=config;this.supplies=List.copyOf(supplies);phaseAt=now;
        order=port.load();
        if(order!=null) {
            if(order.slot<0||order.slot>=8||order.id<=0||order.quantity<=0||order.price<=0||order.filled<0||order.filled>order.quantity||order.spent<0)
                stop("Saved GE ownership receipt is invalid; inspect your offers before restarting");
            else {
                spent=order.priorSpent; attempts.put(order.id,order.attempt);
                phase=Phase.OPEN_GE;status="Reconciling the saved owned GE offer before supply planning";
            }
        }
    }
    Phase phase(){return phase;} String status(){return status;} boolean done(){return phase==Phase.DONE;} boolean stopped(){return phase==Phase.STOPPED;}
    long spent(){return spent;}
    void cancel(){cancelled=true;}
    void resumeAfter(long millis){
        if(millis<=0)return;
        if(order==null&&!done()&&!stopped())go(Phase.BANK,phaseAt+millis,"Input resumed; refreshing owned supplies before any purchase");
        else if(phase!=Phase.WAIT)phaseAt+=millis;
    }
    void continueWith(List<ArenaSupplies.Supply> next,long now){
        if(!done()||order!=null)throw new IllegalStateException("Previous supplies are not settled");
        supplies=List.copyOf(next);go(Phase.BANK,now,"Checking the audited supply basket against a fresh bank");
    }
    void tick(long now) {
        if(cancelled||done()||stopped()||!port.allowed())return;
        try {
            port.poll();
            if(now-phaseAt>180_000&&phase!=Phase.WAIT){stop("Supply preparation timed out in "+phase+"; inspect the owned offer before retrying");return;}
            switch(phase) {
                case BANK:
                    if(port.stock()!=null)go(Phase.PLAN,now,"Live bank, inventory and equipment received");
                    break;
                case PLAN:
                    plan(now,false);
                    break;
                case PREPARE_WALLET:
                    if(port.prepareWallet(now)==WalletPreparation.READY)
                        go(Phase.PRICE,now,"Wallet capacity observed; refreshing owned supplies before pricing");
                    break;
                case PRICE:
                    plan(now,true);
                    break;
                case WALLET:
                    if(port.carriedCoins()<(long)quantity*price)break;
                    if(port.bankOpen()){port.closeBank();break;}
                    go(Phase.OPEN_GE,now,"Opening Grand Exchange for missing supplies");break;
                case OPEN_GE:
                    if(!port.exchangeOpen()){port.openExchange();break;}
                    go(order==null?Phase.PLACE:Phase.CANCEL,now,order==null?"Waiting for an empty GE slot":"Checking the saved owned offer");break;
                case PLACE:
                    if(!port.exchangeOpen()){go(Phase.OPEN_GE,now,"Reopening Grand Exchange");break;}
                    int slot=port.emptySlot();
                    if(slot<0){status="No free GE slot; existing player offers are preserved";break;}
                    if(port.offer(slot).state!=OfferState.EMPTY)break;
                    Order next=new Order();next.id=current.id;next.slot=slot;next.quantity=quantity;next.price=price;
                    next.ownedBefore=ownedBefore;next.coinsBefore=coinsBefore;next.priorSpent=spent;next.placedAt=now;
                    next.attempt=attempts.merge(current.id,1,Integer::sum);
                    order=next;port.save(order);
                    go(Phase.WAIT,now,"Waiting for the exact owned offer acknowledgement");
                    port.place(order,itemName);break;
                case WAIT:
                    Offer offer=owned(now);
                    if(offer==null)break;
                    if(offer.terminal())go(Phase.COLLECT,now,"Collecting only the owned offer and refund");
                    else if(now-phaseAt>=Math.max(15,config.offerWaitSeconds())*1000L)go(Phase.CANCEL,now,"Cancelling only the unfilled owned slot");
                    else status="Buying missing item "+order.id+": "+offer.filled+"/"+order.quantity;
                    break;
                case CANCEL:
                    if(!port.exchangeOpen()){port.openExchange();break;}
                    if(order.collecting&&port.offer(order.slot).state==OfferState.EMPTY) {
                        port.closeExchange();go(Phase.VERIFY,now,"Verifying the previously collected owned offer");break;
                    }
                    Offer before=owned(now);if(before==null)break;
                    if(before.terminal()){go(Phase.COLLECT,now,"Owned offer is ready to collect");break;}
                    port.cancel(order);go(Phase.WAIT_CANCEL,now,"Waiting for cancellation acknowledgement");break;
                case WAIT_CANCEL:
                    Offer after=owned(now);if(after==null)break;
                    if(after.terminal())go(Phase.COLLECT,now,"Collecting the observed partial fill and refund");
                    else if(now-phaseAt>15_000)stop("Cancellation was not acknowledged; saved offer retained for inspection");
                    break;
                case COLLECT:
                    Offer terminal=owned(now);if(terminal==null)break;
                    if(!terminal.terminal()){stop("Owned offer changed before collection; leaving it untouched");break;}
                    order.collecting=true;port.save(order);port.collect(order);go(Phase.CLEAR,now,"Waiting for the owned slot to clear");break;
                case CLEAR:
                    Offer remaining=port.offer(order.slot);
                    if(remaining.state==OfferState.EMPTY){port.closeExchange();go(Phase.VERIFY,now,"Verifying purchased items and refunded coins at the bank");}
                    else if(!matches(remaining)){stop("Owned GE slot changed during collection; leaving it untouched");}
                    else if(now-phaseAt>15_000)stop("Collection was not acknowledged; saved ownership retained");
                    break;
                case VERIFY:
                    ArenaSupplyReceipt receipt=port.stock();if(receipt==null)break;
                    if(receipt.owned(order.id)<Math.addExact(order.ownedBefore,order.filled)
                        ||receipt.coins()<Math.subtractExact(order.coinsBefore,order.spent)) {
                        if(now-phaseAt>20_000)stop("GE slot cleared but items or coin refund were not received; no replacement offer submitted");
                        break;
                    }
                    spent=Math.addExact(order.priorSpent,order.spent);port.save(null);order=null;
                    go(Phase.PLAN,now,"Items and coin refund verified; checking remaining shortages");break;
                default:break;
            }
        } catch(RuntimeException error) {
            stop("Supply preparation stopped: "+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
        }
    }
    private void plan(long now,boolean walletPrepared) {
        ArenaSupplyReceipt stock=port.stock();
        if(stock==null){go(Phase.BANK,now,"Refreshing live stock before any pricing");return;}
        // Compute every deficit first. Owned supplies never reach quote(), unit caps or cost math.
        Map<ArenaSupplies.Supply,Integer> deficits=stock.deficits(supplies);
        if(deficits.isEmpty()){go(Phase.DONE,now,"All requested supplies are owned; no purchase prices needed");return;}
        if(!walletPrepared){go(Phase.PREPARE_WALLET,now,"Preparing one observed inventory slot for the coin withdrawal");return;}
        long cost=0;
        for(Map.Entry<ArenaSupplies.Supply,Integer> missing:deficits.entrySet()) {
            int count=attempts.getOrDefault(missing.getKey().id,0);
            if(count>=3){stop("Missing item "+missing.getKey().id+" remains after three verified offers; restock and restart");return;}
            Quote quote=quotes.computeIfAbsent(missing.getKey().id,port::quote);
            if(quote==null||quote.id!=missing.getKey().id||!quote.tradeable||quote.price<=0||quote.id==995)
                throw new IllegalStateException("Missing item "+missing.getKey().id+" has no supported tradeable price");
            int limit=unitPrice(quote.price,config.priceMarkupPercent()+count*5,config.maxUnitPrice());
            cost=Math.addExact(cost,Math.multiplyExact((long)missing.getValue(),limit));
        }
        long available=Math.min(Math.max(0,stock.coins()-Math.max(0,config.coinReserve())),Math.max(0,(long)config.purchaseBudget()-spent));
        if(cost>available){stop("Missing supplies exceed the purchase budget or available coins after reserve; no partial basket purchased");return;}
        current=deficits.keySet().iterator().next();quantity=deficits.get(current);
        Quote quote=quotes.get(current.id);itemName=quote.name;
        price=unitPrice(quote.price,config.priceMarkupPercent()+attempts.getOrDefault(current.id,0)*5,config.maxUnitPrice());
        ownedBefore=stock.owned(current.id);coinsBefore=stock.coins();
        long needed=Math.multiplyExact((long)quantity,price);
        if(needed>Integer.MAX_VALUE)throw new IllegalStateException("Missing-supply order exceeds the coin stack limit");
        if(stock.inventory(995)<needed)port.withdrawCoins(Math.toIntExact(needed-stock.inventory(995)));
        go(Phase.WALLET,now,"Withdrawing coins only for missing "+itemName+" x "+quantity);
    }
    static int unitPrice(int reference,int markup,int cap) {
        if(reference<=0||cap<=0||markup<0)throw new IllegalArgumentException("Invalid missing-supply quote");
        long quoted=Math.max(1,Math.floorDiv(Math.multiplyExact((long)reference,100L+markup)+99,100));
        if(quoted>cap||quoted>Integer.MAX_VALUE)throw new IllegalArgumentException("Missing-supply price exceeds the configured unit cap");
        return (int)quoted;
    }
    private boolean matches(Offer live) {
        return order!=null&&live.id==order.id&&live.quantity==order.quantity&&live.price==order.price
            &&(live.state==OfferState.BUYING||live.terminal());
    }
    private Offer owned(long now) {
        if(order==null){stop("Lost the owned GE offer receipt");return null;}
        Offer live=port.offer(order.slot);
        if(live.state==OfferState.EMPTY) {
            if(order.acknowledged||now-order.placedAt>30_000)stop("Ambiguous owned GE offer state; inspect the saved slot before restarting");
            return null;
        }
        if(!matches(live)||live.filled<order.filled||live.filled>order.quantity||live.spent<order.spent
            ||live.spent>(long)order.quantity*order.price||live.spent>(long)live.filled*order.price) {
            stop("GE slot does not match the exact owned offer or its counters; leaving it untouched");return null;
        }
        order.acknowledged=true;order.filled=live.filled;order.spent=live.spent;port.save(order);
        return live;
    }
    private void go(Phase next,long now,String text){phase=next;phaseAt=now;status=text;}
    private void stop(String text){phase=Phase.STOPPED;status=text;}
}
