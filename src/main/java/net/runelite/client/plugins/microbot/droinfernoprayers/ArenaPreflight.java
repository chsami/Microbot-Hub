package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/** Destination-bank pre-sips only. Final saved quantities remain reserved in the bank. */
final class ArenaPreflight {
    enum Result { WAITING, READY, STOPPED }
    private static final int[][] POTS={{6685,6687,6689,6691},{3024,3026,3028,3030},{2444,169,171,173},{12625,12627,12629,12631}};
    private static final String[] NAMES={"brew","super restore","ranging potion","stamina potion"};
    interface Port {
        boolean allowed();boolean bank();boolean open();boolean close();boolean emptyInventory();boolean deposit();
        int bankCount(int id);int count(int id);boolean withdraw(int id);boolean drink(int id);
    }
    private final Port port;
    private final LongSupplier clock;
    private final Map<Integer,Integer> reserved=new HashMap<>();
    private final int[] selected=new int[4];
    private Result result=Result.WAITING;
    private long started=-1,next;
    private int stage,sip,before;
    private boolean cleared,drinking;
    private String status="Preparing brew, restore, ranging and stamina pre-sips";
    ArenaPreflight(BooleanSupplier allowed,boolean ignored,boolean preSip){this(allowed,ignored,preSip,null);}
    ArenaPreflight(BooleanSupplier allowed,boolean ignored,boolean preSip,InventorySetup setup){this(new NativePort(allowed),System::currentTimeMillis,setup);}
    ArenaPreflight(Port port,LongSupplier clock,InventorySetup setup){
        this.port=port;this.clock=clock;
        if(setup!=null&&setup.getInventory()!=null)for(var row:setup.getInventory())if(row!=null&&row.getId()>0&&row.getQuantity()>0)reserved.merge(row.getId(),row.getQuantity(),Math::addExact);
    }
    String status(){return status;}
    void resumeAfter(long millis){if(millis>0){if(started>=0)started+=millis;next+=millis;}}
    void message(String ignored){}
    private Result stop(String text){status=text;return result=Result.STOPPED;}
    Result tick(){
        if(result!=Result.WAITING||!port.allowed())return result;
        long now=clock.getAsLong();if(started<0)started=now;
        if(now-started>120_000)return stop("Destination pre-sips timed out; final setup was not loaded");
        if(now<next)return result;
        if(stage==0){
            if(!port.bank()){port.open();return result;}
            if(!cleared){if(!port.emptyInventory()){port.deposit();next=now+1800;return result;}cleared=true;}
            for(int i=0;i<POTS.length;i++){
                if(selected[i]!=0&&port.count(selected[i])>0)continue;
                int chosen=selected[i];
                if(chosen==0)for(int j=3;j>=0;j--){int id=POTS[i][j];if(port.bankCount(id)>reserved.getOrDefault(id,0)){chosen=id;break;}}
                if(chosen==0)return stop("No spare pre-sip "+NAMES[i]+" beyond final setup quantities");
                if(port.bankCount(chosen)<=reserved.getOrDefault(chosen,0))return stop("Reserved final setup potions cannot be used for pre-sips");
                selected[i]=chosen;port.withdraw(chosen);next=now+1800;return result;
            }
            stage=1;
        }
        if(stage==1){
            if(port.bank()){port.close();return result;}
            if(sip==POTS.length){stage=2;return result;}
            int id=selected[sip];
            if(drinking){
                if(port.count(id)>=before)return stop("Pre-sip was not confirmed: "+NAMES[sip]);
                drinking=false;sip++;return result;
            }
            if(port.count(id)==0)return stop("Pre-sip potion disappeared: "+NAMES[sip]);
            before=port.count(id);drinking=true;port.drink(id);next=now+1800;return result;
        }
        if(!port.bank()){port.open();return result;}
        if(!port.emptyInventory()){port.deposit();next=now+1800;return result;}
        status="Brew, restore, ranging and stamina sips confirmed; loading final setup";
        return result=Result.READY;
    }
    static final class NativePort implements Port {
        private final BooleanSupplier allowed;NativePort(BooleanSupplier allowed){this.allowed=allowed;}
        private <T>T read(java.util.function.Supplier<T> value,T fallback){return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(value::get).orElse(fallback);}
        private boolean bulkAttempted;
        public boolean allowed(){return allowed.getAsBoolean();}public boolean bank(){return read(Rs2Bank::isOpen,false);}
        public boolean open(){return allowed()&&Rs2Bank.openBank();}public boolean close(){return allowed()&&Rs2Bank.closeBank();}
        public boolean emptyInventory(){return read(Rs2Inventory::isEmpty,false);}
        public boolean deposit(){
            if(!allowed())return false;
            if(!bulkAttempted){bulkAttempted=true;return Rs2Bank.depositAll();}
            // Bulk deposit honors user locks. Explicit input clears remaining travel items without editing locks.
            var first=read(()->Rs2Inventory.all().stream().findFirst().orElse(null),null);
            return first!=null&&Rs2Bank.depositAll(first.getId());
        }
        public int bankCount(int id){return read(()->Rs2Bank.isOpen()?Rs2Bank.count(id):0,0);}public int count(int id){return read(()->Rs2Inventory.itemQuantity(id),0);}
        public boolean withdraw(int id){return allowed()&&Rs2Bank.setWithdrawAsItem()&&Rs2Bank.withdrawOne(id);}
        public boolean drink(int id){return allowed()&&Rs2Inventory.interact(id,"Drink");}
    }
}
