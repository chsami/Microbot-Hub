package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

/** GE-side orchestration: pipe audit, deficits-only shopping, pouch/charges and observed banking. */
final class ArenaSupplyPreparation implements AutoCloseable {
    enum Phase { STOCK, BOOTSTRAP, AUDIT, BUY, POUCH, PIPE, BANK, READY, STOPPED }
    private final ArenaPreparationConfig config;
    private final InventorySetup setup;
    private final BooleanSupplier allowed;
    private final ArenaSupplyPort port;
    private final ArenaBlowpipe pipe;
    private ArenaGeBuyer buyer;
    private ArenaRunePouchNote note;
    private Phase phase=Phase.STOCK;
    private String status="Obtaining a reliable bank, inventory and equipment supply receipt";
    private long started=-1;
    ArenaSupplyPreparation(ArenaPreparationConfig config,InventorySetup setup,BooleanSupplier allowed){
        this.config=config;this.setup=setup;this.allowed=allowed;port=new ArenaSupplyPort(allowed,setup);
        int selected=ArenaSupplies.rows(setup).stream().filter(row->ArenaBlowpipe.isPipe(row.getId())).mapToInt(row->row.getId()).findFirst().orElse(0);
        pipe=selected==0?null:new ArenaBlowpipe(selected,allowed);
    }
    String status(){return status;}boolean ready(){return phase==Phase.READY;}boolean stopped(){return phase==Phase.STOPPED;}
    void message(String text){if(pipe!=null)pipe.message(text);}
    void pause(){port.pause();}
    void resumeAfter(long millis){
        if(millis<=0)return;if(started>=0)started+=millis;if(buyer!=null)buyer.resumeAfter(millis);
        port.resumeAfter(millis);
        if(note!=null)note.resumeAfter(millis);if(pipe!=null)pipe.resumeAfter(millis);
    }
    void tick(long now){
        if(!allowed.getAsBoolean()||ready()||stopped())return;
        if(started<0)started=now;
        if(now-started>1_200_000){stop("GE preparation timed out; inspect supplies and the saved owned offer before restarting");return;}
        try{
            switch(phase){
                case STOCK:
                    ArenaSupplyReceipt receipt=port.stock();if(receipt==null)return;
                    String invalid=ArenaContainerPreparation.validationError(setup);
                    if(invalid!=null){stop(invalid+"; no supply purchase was started");return;}
                    List<ArenaSupplies.Supply> bootstrap=pipe!=null&&!pipe.owned()?List.of(new ArenaSupplies.Supply(pipe.purchaseId(),"Empty blowpipe",1)):List.of();
                    buyer=new ArenaGeBuyer(port,config,bootstrap,now);phase=Phase.BOOTSTRAP;break;
                case BOOTSTRAP:
                    if(!shop(now))return;phase=Phase.AUDIT;break;
                case AUDIT:
                    if(pipe!=null&&!pipe.audit()){status=pipe.status();if(pipe.failure()!=null)stop(status);return;}
                    ArenaSupplyReceipt audited=port.stock();if(audited==null)return;
                    buyer.continueWith(ArenaSupplies.basket(setup,audited,pipe,config.buyRunePouchNote(),port::canonical),now);
                    phase=Phase.BUY;break;
                case BUY:
                    if(!shop(now))return;phase=Phase.POUCH;break;
                case POUCH:
                    if(ArenaSupplies.rows(setup).stream().anyMatch(row->row.getId()==ArenaSupplies.POUCH)){
                        if(note==null)note=new ArenaRunePouchNote(port,allowed);
                        status="Confirming standard rune pouch ownership/conversion";
                        if(!note.tick(now))return;
                    }
                    phase=Phase.PIPE;break;
                case PIPE:
                    if(pipe!=null&&!pipe.finish()){status=pipe.status();if(pipe.failure()!=null)stop(status);return;}
                    phase=Phase.BANK;break;
                case BANK:
                    if(port.stock()==null)return;
                    status="Banking GE preparation items and verifying the final supply receipt";
                    if(!port.bankInventory())return;
                    phase=Phase.READY;status="Owned and purchased supplies confirmed; ready to travel";port.pause();break;
                default:break;
            }
        }catch(RuntimeException error){stop("Supply preparation stopped: "+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));}
    }
    private boolean shop(long now){buyer.tick(now);status=buyer.status();if(buyer.stopped()){stop(status);return false;}return buyer.done();}
    private void stop(String text){phase=Phase.STOPPED;status=text;port.pause();}
    @Override public void close(){if(buyer!=null)buyer.cancel();port.close();}
}
