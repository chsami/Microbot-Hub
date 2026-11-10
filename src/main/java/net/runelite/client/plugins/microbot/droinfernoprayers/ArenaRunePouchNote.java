package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/** Standard pouch note conversion requires disappearance and appearance in the same run. */
final class ArenaRunePouchNote {
    interface Port {
        default boolean allowed(){return true;}
        long pouch();long note();long carriedPouch();long carriedNote();
        boolean openBank();boolean bankOpen();void closeBank();void withdraw();boolean exchange();
    }
    private final Port port;
    private long started=-1,nextAction,noteBefore,pouchBefore;
    private int attempts;
    private boolean requested;
    ArenaRunePouchNote(Port port){this.port=port;}
    ArenaRunePouchNote(ArenaSupplyPort supplies,BooleanSupplier allowed){this(new Port(){
        private <T>T read(Supplier<T> value,T fallback){return Microbot.getClientThread().runOnClientThreadOptional(value::get).orElse(fallback);}
        public boolean allowed(){return allowed.getAsBoolean()&&!Thread.currentThread().isInterrupted();}
        public long pouch(){return carriedPouch()+read(()->Rs2Bank.isOpen()?Rs2Bank.count(ArenaSupplies.POUCH):0,0);}
        public long note(){return carriedNote()+read(()->Rs2Bank.isOpen()?Rs2Bank.count(ArenaSupplies.NOTE):0,0);}
        public long carriedPouch(){return read(()->Rs2Inventory.itemQuantity(ArenaSupplies.POUCH),0);}
        public long carriedNote(){return read(()->Rs2Inventory.itemQuantity(ArenaSupplies.NOTE),0);}
        public boolean openBank(){return supplies.stock()!=null;}public boolean bankOpen(){return supplies.bankOpen();}public void closeBank(){supplies.closeBank();}
        public void withdraw(){if(allowed()){Rs2Bank.setWithdrawAsItem();Rs2Bank.withdrawOne(ArenaSupplies.NOTE);}}
        public boolean exchange(){
            var banker=read(()->Microbot.getRs2NpcCache().query().where(n->n.getName()!=null&&n.getName().toLowerCase(Locale.ROOT).contains("banker")).nearest(),null);
            return banker!=null&&allowed()&&Rs2Inventory.useItemOnNpc(ArenaSupplies.NOTE,banker.getNpc());
        }
    });}
    void resumeAfter(long millis){if(millis>0){if(started>=0)started+=millis;nextAction+=millis;}}
    boolean tick(long now){
        if(!port.allowed())return false;
        if(requested&&port.carriedPouch()>pouchBefore&&port.carriedNote()<noteBefore)return true;
        if(!requested&&port.pouch()>0)return true;
        if(started<0)started=now;
        if(now-started>60_000)throw new IllegalStateException("Rune pouch note conversion was not confirmed");
        if(now<nextAction)return false;
        if(requested&&port.carriedNote()==0)throw new IllegalStateException("Rune pouch note disappeared without a confirmed pouch");
        if(port.carriedNote()==0){
            if(!port.openBank())return false;
            if(port.note()==0)throw new IllegalStateException("Rune pouch note is missing after GE collection");
            port.withdraw();nextAction=now+1800;return false;
        }
        if(port.bankOpen()){port.closeBank();return false;}
        if(attempts>=3)throw new IllegalStateException("Banker did not confirm rune pouch note conversion");
        if(!requested){noteBefore=port.carriedNote();pouchBefore=port.carriedPouch();}
        attempts++;if(port.exchange())requested=true;nextAction=now+6000;return false;
    }
}
