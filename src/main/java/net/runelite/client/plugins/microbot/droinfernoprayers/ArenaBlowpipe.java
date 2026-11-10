package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/** Local GE-side audit and loading. An accepted click is never a charge receipt. */
public final class ArenaBlowpipe {
    public static final int DRAGON_DART=11230,SCALES=12934,MIN_DARTS=2000;
    public static final int MAX_SCALES=16383,MIN_SCALES=3277;
    public static final double MIN_PERCENT=20;
    static final class Charge {final String dart;final int darts,scales;final double percent;Charge(String dart,int darts,double percent){this(dart,darts,percent,-1);}Charge(String dart,int darts,double percent,int scales){this.dart=dart;this.darts=darts;this.percent=percent;this.scales=scales;}}
    private static final java.util.regex.Pattern CHARGES=java.util.regex.Pattern.compile("Darts:\\s*(?:(.+?)\\s+x\\s*([\\d,]+)|None|Empty)\\.?\\s*Scales:\\s*(?:([\\d,]+)\\s*\\(\\s*([\\d.]+)%\\s*\\)|([\\d.]+)%)",java.util.regex.Pattern.CASE_INSENSITIVE);
    static Charge parse(String text){
        if(text==null)return null;var m=CHARGES.matcher(net.runelite.client.util.Text.removeTags(text));if(!m.find())return null;
        try{int darts=m.group(2)==null?0:Integer.parseInt(m.group(2).replace(",",""));double percent=Double.parseDouble(m.group(4)==null?m.group(5):m.group(4));
            int scales=m.group(3)==null?-1:Integer.parseInt(m.group(3).replace(",",""));
            return darts<0||scales>MAX_SCALES||!Double.isFinite(percent)||percent<0||percent>100?null:new Charge(m.group(1)==null?"Dragon dart":m.group(1).trim().replaceAll("(?i)darts$","dart"),darts,percent,scales);
        }catch(NumberFormatException invalid){return null;}
    }
    public static boolean isPipe(int id){return id==12924||id==12926||id==28687||id==28688;}
    public interface Port {
        boolean allowed();boolean bank();boolean openBank();boolean closeBank();
        int inventory(int id);boolean worn(int id);int bankCount(int id);int freeSlots();
        boolean withdraw(int id,int amount);boolean unequip(int id);boolean interact(int id,String action);boolean combine(int resource,int pipe);
        default boolean makeSpace(){return false;}
    }
    private final Port port;
    private final LongSupplier clock;
    private final int empty,loaded;
    private long started=-1,nextAction;
    private int checks,unloads,loadingId,loadingBefore,loadAttempts,spaceAttempts,missingPipeChecks;
    private boolean audited,finishing,done,unloadPending,recheckingMissingPipe;
    private volatile boolean waitingCheck;
    private volatile Charge received;
    private Charge observed;
    private String status="Checking selected blowpipe",failure;
    public ArenaBlowpipe(int selected,BooleanSupplier allowed){this(selected,new NativePort(allowed),System::currentTimeMillis);}
    public ArenaBlowpipe(int selected,Port port,LongSupplier clock){
        if(!isPipe(selected))throw new IllegalArgumentException("Unsupported blowpipe");
        empty=selected==28687||selected==28688?28687:12924;loaded=empty==28687?28688:12926;
        this.port=port;this.clock=clock;
    }
    public int purchaseId(){return empty;}
    public int actualId(){return port.inventory(loaded)>0||port.worn(loaded)||port.bankCount(loaded)>0?loaded:empty;}
    public boolean owned(){return port.inventory(empty)+port.inventory(loaded)+port.bankCount(empty)+port.bankCount(loaded)>0||port.worn(empty)||port.worn(loaded);}
    public int dartsNeeded(){return observed==null?MIN_DARTS:Math.max(0,MIN_DARTS-observed.darts);}
    public int scalesNeeded(){return observed==null?MIN_SCALES:observed.scales>=0?Math.max(0,MIN_SCALES-observed.scales):observed.percent>=MIN_PERCENT?0:Math.max(1,MIN_SCALES-(int)Math.floor(Math.max(0,observed.percent-0.1)*MAX_SCALES/100));}
    public String status(){return failure==null?status:failure;}
    void resumeAfter(long millis){if(millis>0){if(started>=0)started+=millis;nextAction+=millis;}}
    public String failure(){return failure;}
    public void message(String text){if(waitingCheck){var charge=parse(text);if(charge!=null)received=charge;}}
    public boolean audit(){return step(false);}
    public boolean finish(){
        if(!finishing){finishing=true;started=-1;audited=false;observed=null;received=null;waitingCheck=false;checks=0;nextAction=0;}
        return step(true);
    }
    private boolean stop(String text){failure=text;return false;}
    private boolean step(boolean load){
        if(failure!=null||!port.allowed())return false;
        if(done||!load&&audited)return true;
        long now=clock.getAsLong();if(started<0)started=now;
        if(now-started>120_000)return stop("Blowpipe preparation timed out; no charge success was assumed");
        if(now<nextAction)return false;
        if(recheckingMissingPipe){
            // The live stock audit and the bank helper can briefly disagree after GE collection.
            // Observe a closed bank before opening it again; never infer missing gear from one read.
            if(port.bank()){port.closeBank();nextAction=now+1800;return false;}
            recheckingMissingPipe=false;port.openBank();nextAction=now+1800;return false;
        }
        if(loadingId!=0){
            if(port.inventory(loadingId)<loadingBefore){loadingId=0;loadAttempts=0;observed=null;received=null;waitingCheck=false;checks=0;}
            else if(++loadAttempts>3)return stop("Blowpipe loading was not confirmed");
            else{port.combine(loadingId,port.inventory(loaded)>0?loaded:empty);nextAction=now+2500;return false;}
        }
        if(port.inventory(empty)+port.inventory(loaded)==0){
            if(!port.bank()){port.openBank();return false;}
            if(port.freeSlots()<3){makeSpace(now);return false;}
            if(port.worn(loaded)||port.worn(empty))port.unequip(port.worn(loaded)?loaded:empty);
            else if(port.bankCount(loaded)>0)port.withdraw(loaded,1);
            else if(port.bankCount(empty)>0)port.withdraw(empty,1);
            else{
                if(++missingPipeChecks>3)return stop("Selected blowpipe is missing after three bank rechecks following GE collection");
                status="Refreshing bank to recheck selected blowpipe after GE collection";
                recheckingMissingPipe=true;port.closeBank();nextAction=now+1800;return false;
            }
            nextAction=now+1800;return false;
        }
        missingPipeChecks=0;
        int pipe=port.inventory(loaded)>0?loaded:empty;
        if(received!=null){
            observed=received;received=null;waitingCheck=false;checks=0;
        }
        if(observed!=null){
            if(!"Dragon dart".equalsIgnoreCase(observed.dart)&&observed.darts>0){
                if(unloadPending)return stop("Blowpipe still contains non-Dragon darts after Unload");
                if(port.freeSlots()<2){makeSpace(now);return false;}
                if(port.bank()){port.closeBank();return false;}
                if(++unloads>3)return stop("Blowpipe Unload action could not be confirmed");
                if(port.interact(pipe,"Unload")){unloadPending=true;observed=null;}
                nextAction=now+2500;return false;
            }
            unloadPending=false;
        }
        // Empty item ID is an observed zero-charge receipt; loaded pipes need Check chat.
        if(observed==null&&pipe==empty&&!waitingCheck)observed=new Charge("Dragon dart",0,0,0);
        if(observed==null){
            if(port.bank()){port.closeBank();return false;}
            if(checks++>=3)return stop("Blowpipe Check did not produce a charge response");
            waitingCheck=true;port.interact(pipe,"Check");nextAction=now+4000;return false;
        }
        if(!load){audited=true;status="Blowpipe charges observed; calculating GE shortfalls";return true;}
        if(observed.darts>=MIN_DARTS&&scalesNeeded()==0){done=true;status="Blowpipe verified: at least 2,000 Dragon darts and 20% scales";return true;}
        int resource=dartsNeeded()>0?DRAGON_DART:SCALES;
        int required=resource==DRAGON_DART?dartsNeeded():scalesNeeded();
        if(port.inventory(resource)<required){
            if(!port.bank()){port.openBank();return false;}
            if(port.inventory(resource)==0&&port.freeSlots()<1){makeSpace(now);return false;}
            int missing=required-port.inventory(resource);
            if(port.bankCount(resource)<missing)return stop("Collected blowpipe loading supplies are insufficient");
            port.withdraw(resource,missing);nextAction=now+1800;return false;
        }
        if(port.bank()){port.closeBank();return false;}
        loadingId=resource;loadingBefore=port.inventory(resource);loadAttempts=0;
        port.combine(resource,pipe);nextAction=now+2500;return false;
    }
    private void makeSpace(long now){
        status="Banking excess items before blowpipe preparation";
        if(!port.bank()){port.openBank();nextAction=now+1200;return;}
        if(++spaceAttempts>8){stop("Unable to confirm safe inventory space for blowpipe preparation");return;}
        port.makeSpace();nextAction=now+1800;
    }
    private static final class NativePort implements Port {
        private final BooleanSupplier allowed;NativePort(BooleanSupplier allowed){this.allowed=allowed;}
        private <T>T read(java.util.function.Supplier<T> value,T fallback){return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(value::get).orElse(fallback);}
        public boolean allowed(){return allowed.getAsBoolean();}
        public boolean bank(){return read(Rs2Bank::isOpen,false);}public boolean openBank(){return allowed()&&Rs2Bank.openBank();}
        public boolean closeBank(){return allowed()&&Rs2Bank.closeBank();}
        public int inventory(int id){return read(()->Rs2Inventory.itemQuantity(id),0);}public int bankCount(int id){return read(()->Rs2Bank.isOpen()?Rs2Bank.count(id):0,0);}
        public boolean worn(int id){return read(()->Rs2Equipment.isWearing(id),false);}public int freeSlots(){return read(Rs2Inventory::emptySlotCount,0);}
        public boolean withdraw(int id,int amount){return allowed()&&Rs2Bank.setWithdrawAsItem()&&Rs2Bank.withdrawX(id,amount);}
        public boolean unequip(int id){return allowed()&&Rs2Equipment.unEquip(id);}
        public boolean interact(int id,String action){return allowed()&&Rs2Inventory.interact(id,action);}
        public boolean combine(int resource,int pipe){return allowed()&&Rs2Inventory.combine(resource,pipe);}
        public boolean makeSpace(){
            if(!allowed()||!bank())return false;
            var item=read(()->Rs2Inventory.all().stream().filter(i->!isPipe(i.getId())).findFirst().orElse(null),null);
            return item!=null&&Rs2Bank.depositAll(item.getId());
        }
    }
}
