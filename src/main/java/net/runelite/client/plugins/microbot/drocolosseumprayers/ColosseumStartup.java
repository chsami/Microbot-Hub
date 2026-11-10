package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

/** Local-only bank approach, lobby introduction and entrance confirmation. */
final class ColosseumStartup {
    static final WorldPoint BANK=new WorldPoint(1779,3096,0);
    private final LocalArenaActions local;
    private final BooleanSupplier allowed;
    private long startedAt,nextAction;
    private final java.util.function.LongSupplier clock;
    private boolean spoken,dialogueSeen;
    private final Runnable beforeEntry;
    private java.util.function.Consumer<Runnable> arenaEntry=Runnable::run;
    void arenaEntry(java.util.function.Consumer<Runnable> dispatch){arenaEntry=java.util.Objects.requireNonNull(dispatch);}
    private String status="Start at the bank outside the Colosseum";
    ColosseumStartup(BooleanSupplier allowed){this(allowed,()->{});}
    ColosseumStartup(BooleanSupplier allowed,Runnable beforeEntry){this(allowed,new LocalArenaActions(allowed),System::currentTimeMillis,beforeEntry);}
    ColosseumStartup(BooleanSupplier allowed,LocalArenaActions local,java.util.function.LongSupplier clock){this(allowed,local,clock,()->{});}
    ColosseumStartup(BooleanSupplier allowed,LocalArenaActions local,java.util.function.LongSupplier clock,Runnable beforeEntry){this.allowed=allowed;this.local=local;this.clock=clock;this.beforeEntry=beforeEntry;}
    String status(){return status;}
    boolean stopped(){return startedAt!=0&&clock.getAsLong()-startedAt>180_000L;}
    void shutdown(){local.cancel();}
    // One bank click per receipt window. A repeat click on an opening bank closes it again.
    static final long BANK_RECEIPT_MS=1_800L,BANK_RECEIPT_CAP_MS=8_000L;
    private long bankClickAt,bankRetryAt,bankMovedAt;
    private WorldPoint bankLastPosition;
    private java.util.function.LongSupplier bankRetryDelay=()->java.util.concurrent.ThreadLocalRandom.current().nextLong(250,751);
    void bankRetryDelay(java.util.function.LongSupplier delay){bankRetryDelay=java.util.Objects.requireNonNull(delay);}
    boolean openBank(){
        if(!allowed.getAsBoolean())return false;
        if(Rs2Bank.isOpen()&&local.near(BANK,6)){bankClickAt=0;bankRetryAt=0;return true;}
        long now=clock.getAsLong();
        if(bankClickAt!=0){
            // Wait for the observed bank interface while the player still walks to the booth.
            var position=local.position();
            if(position!=null&&!position.equals(bankLastPosition)){bankLastPosition=position;bankMovedAt=now;}
            boolean settled=now-bankClickAt>=BANK_RECEIPT_MS&&now-bankMovedAt>=BANK_RECEIPT_MS;
            if(!settled&&now-bankClickAt<BANK_RECEIPT_CAP_MS){status="Waiting for the Colosseum bank to open";return false;}
            bankClickAt=0;bankRetryAt=now+bankRetryDelay.getAsLong();
            status="Bank did not open; retrying the bank click";
            return false;
        }
        if(now<bankRetryAt)return false;
        status="Efficient Walker: bank outside the Colosseum";
        if(local.near(BANK,3)){
            if(local.object(52396,"Bank")){bankClickAt=now;bankMovedAt=now;bankLastPosition=local.position();}
            else bankRetryAt=now+bankRetryDelay.getAsLong();
        }else local.walk(BANK);
        return false;
    }
    boolean enter(){
        if(!allowed.getAsBoolean())return false;
        long now=clock.getAsLong();if(startedAt==0)startedAt=now;
        if(now-startedAt>180_000L){status="Colosseum entry timed out; inspect Minimus dialogue and restart";return false;}
        if(now<nextAction)return false;nextAction=now+1800;
        var position=local.position();if(position==null)return false;
        if(position.getY()<9000){
            status="Walking to the Colosseum lobby entrance";
            if(local.near(new WorldPoint(1793,3106,0),3)){beforeEntry.run();local.object(50749,"Enter");}else local.walk(new WorldPoint(1793,3106,0));
            return false;
        }
        beforeEntry.run();
        if(!spoken){
            status="Talking to Minimus before arena entry";
            if(!local.near(new WorldPoint(1805,9508,0),3)){local.walk(new WorldPoint(1805,9508,0));return false;}
            if(Rs2Dialogue.isInDialogue()){
                dialogueSeen=true;
                if(!local.continueDialogue()&&!local.choose("Goodbye."))Rs2Dialogue.clickOption("participate",false);
            }else if(dialogueSeen)spoken=true;
            else local.talk(12807);
            return false;
        }
        status="Entering the Fortis Colosseum; waiting for instance arrival";
        if(Rs2Dialogue.isInDialogue())local.choose("Yes, I understand the risks.");
        else if(local.near(new WorldPoint(1808,9506,0),3))arenaEntry.accept(()->{
            if(allowed.getAsBoolean()&&local.near(new WorldPoint(1808,9506,0),3)
                &&!Rs2Dialogue.isInDialogue())local.object(50751,"Enter");
        });
        else local.walk(new WorldPoint(1808,9506,0));
        return false; // Only the plugin's live instance boundary acknowledges arrival.
    }
}
