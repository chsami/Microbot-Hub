package net.runelite.client.plugins.microbot.autofarmer;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.grandexchange.*;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
/** One journaled seed purchase at a time, never a blanket collect/cancel. */
final class SeedRestocker {
    static final int BATCH=100, PERCENT=99;
    private final Path journal;
    SeedRestocker(Path journal){this.journal=journal;}
    static boolean hasUsableStock(int level) {
        for(HerbSeedType seed:HerbSeedType.getPlantableHerbs(level))
            if(Rs2Bank.count(seed.getItemId())>0 || Rs2Inventory.itemQuantity(seed.getItemId())>0)return true;
        return false;
    }
    static HerbSeedType choose(HerbSeedType configured,int level) {
        if(configured!=HerbSeedType.BEST)return configured.canPlant(level)?configured:null;
        List<HerbSeedType> eligible=HerbSeedType.getPlantableHerbs(level);return eligible.isEmpty()?null:eligible.get(0);
    }
    boolean restockWhenEmpty(HerbSeedType configured,int level) {
        if(!Rs2Bank.isOpen())throw new IllegalStateException("Seed restock needs an open bank to verify zero stock");
        Properties saved=load();
        if(!saved.isEmpty()){resume(saved);return true;}
        if(hasUsableStock(level))return false;
        HerbSeedType seed=choose(configured,level);
        if(seed==null)throw new IllegalStateException("No configured herb seed can be planted at Farming "+level);
        int guide=Rs2GrandExchange.getOfferPrice(seed.getItemId());
        long cash=((guide*199L+99)/100)*BATCH+BATCH;
        if(guide<=0 || cash>Integer.MAX_VALUE)throw new IllegalStateException("Cannot obtain a valid seed purchase price");
        if(Rs2Bank.count(ItemID.COINS)<cash)throw new IllegalStateException("Insufficient banked GP for 100 "+seed.getSeedName()+" at +99%");
        Rs2Bank.setWithdrawAsItem();Rs2Bank.withdrawX(ItemID.COINS,(int)cash);
        if(!Global.sleepUntil(()->Rs2Inventory.itemQuantity(ItemID.COINS)>=cash,5000))throw new IllegalStateException("GE coin withdrawal not confirmed");
        Rs2Bank.closeBank();
        Rs2Walker.walkTo(new WorldPoint(3165,3487,0),8);
        if(!Rs2GrandExchange.openExchange())throw new IllegalStateException("Cannot open Grand Exchange");
        if(Rs2GrandExchange.hasBuyOffer(seed.getItemId())!=null)throw new IllegalStateException("An existing buy offer for "+seed.getSeedName()+" needs manual review; no duplicate offer placed");
        GrandExchangeSlots slot=Rs2GrandExchange.getAvailableSlot();
        if(slot==null)throw new IllegalStateException("No free GE slot; existing offers left untouched");
        saved.setProperty("slot",slot.name());saved.setProperty("itemId",Integer.toString(seed.getItemId()));saved.setProperty("quantity",Integer.toString(BATCH));saved.setProperty("itemName",seed.getSeedName());saved.setProperty("guidePrice",Integer.toString(guide));save(saved);
        Microbot.log("Seed stock exhausted: buying 100 "+seed.getSeedName()+" using +99% in GE slot "+slot);
        // The client applies percent(99) through its +X% price button.
        Rs2GrandExchange.processOffer(GrandExchangeRequest.builder().slot(slot).action(GrandExchangeAction.BUY)
            .itemName(seed.getSeedName()).exact(true).quantity(BATCH).price(guide).percent(PERCENT).closeAfterCompletion(false).build());
        finish(saved);
        return true;
    }
    private void resume(Properties saved) {
        Rs2Bank.closeBank();Rs2Walker.walkTo(new WorldPoint(3165,3487,0),8);
        if(!Rs2GrandExchange.openExchange())throw new IllegalStateException("Cannot reopen GE for pending seed purchase");
        Microbot.log("Resuming journaled seed purchase; no additional offer placed");finish(saved);
    }
    private boolean matches(GrandExchangeOfferDetails offer,Properties saved) {
        return offer!=null && offer.getItemId()==Integer.parseInt(saved.getProperty("itemId"))
            && !offer.isSelling() && offer.getTotalQuantity()==BATCH;
    }
    private void finish(Properties saved) {
        GrandExchangeSlots slot=GrandExchangeSlots.valueOf(saved.getProperty("slot"));
        GrandExchangeOfferDetails initial=Rs2GrandExchange.getOfferDetails(slot);
        if(initial==null || initial.getState()==GrandExchangeOfferState.EMPTY){clear();throw new IllegalStateException("Seed offer not present/confirmed; no further order placed");}
        if(!matches(initial,saved))throw new IllegalStateException("Pending seed GE slot no longer matches; existing offer left untouched");
        boolean filled=Global.sleepUntil(()->{
            GrandExchangeOfferDetails details=Rs2GrandExchange.getOfferDetails(slot);
            return matches(details,saved) && details.getState()==GrandExchangeOfferState.BOUGHT && details.getQuantitySold()>=BATCH;
        },120000);
        if(!filled){
            GrandExchangeOfferDetails current=Rs2GrandExchange.getOfferDetails(slot);
            if(matches(current,saved)){
                Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot),true);
                Rs2GrandExchange.collectOffer(slot,true);
                GrandExchangeOfferDetails after=Rs2GrandExchange.getOfferDetails(slot);
                if(after==null || after.getState()==GrandExchangeOfferState.EMPTY)clear();
            }
            Rs2GrandExchange.closeExchange();
            throw new IllegalStateException("Seed purchase did not fill within two minutes; cancellation/collection attempted for owned slot "+slot+" only. Check GE before clearing failure");
        }
        if(!Rs2GrandExchange.collectOffer(slot,true))throw new IllegalStateException("Seed offer filled but collection to bank was not confirmed");
        GrandExchangeOfferDetails after=Rs2GrandExchange.getOfferDetails(slot);
        if(after!=null && after.getState()!=GrandExchangeOfferState.EMPTY)throw new IllegalStateException("Seed collection left an uncleared offer; purchase journal retained");
        clear();Rs2GrandExchange.closeExchange();
        if(!Rs2Bank.openBank())throw new IllegalStateException("Could not reopen bank after seed purchase");
        int id=Integer.parseInt(saved.getProperty("itemId"));
        if(!Global.sleepUntil(()->Rs2Bank.count(id)>0,5000))throw new IllegalStateException("Purchased seeds not confirmed in bank");
        Microbot.log("GE seed restock confirmed in bank: "+saved.getProperty("itemName"));
    }
    private Properties load(){Properties p=new Properties();if(Files.exists(journal))try(var in=Files.newInputStream(journal)){p.load(in);}catch(IOException e){throw new IllegalStateException("Cannot read seed purchase journal",e);}return p;}
    private void save(Properties p){try{Files.createDirectories(journal.getParent());try(var out=Files.newOutputStream(journal)){p.store(out,"Auto Farmer owned GE seed offer");}}catch(IOException e){throw new IllegalStateException("Cannot save seed offer journal; purchase not submitted",e);}}
    private void clear(){try{Files.deleteIfExists(journal);}catch(IOException e){throw new IllegalStateException("Cannot clear completed seed offer journal",e);}}
}
