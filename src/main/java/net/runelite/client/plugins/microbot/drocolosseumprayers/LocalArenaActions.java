package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

/** Local preparation capabilities, never registered with CommandRunner or Connection. */
final class LocalArenaActions {
    private final BooleanSupplier allowed;
    LocalArenaActions(BooleanSupplier allowed){this.allowed=allowed;}
    WorldPoint position(){return Microbot.getClientThread().runOnClientThreadOptional(()->{
        var c=Microbot.getClient();return c.getLocalPlayer()==null?null:c.getLocalPlayer().getWorldLocation();
    }).orElse(null);}
    boolean near(WorldPoint target,int distance){var p=position();return p!=null&&p.distanceTo(target)<=distance;}
    /** Automatic travel belongs to the AIO startup, which this plugin does not include. */
    private static final String NO_TRAVEL = "Automatic travel is not available; walk to the entrance yourself";
    private String routeError;
    void walk(WorldPoint target) { routeError = NO_TRAVEL; }
    void walkWithSupplies(WorldPoint target) { routeError = NO_TRAVEL; }
    String routeFailure() { return routeError; }
    void cancel() { routeError = null; }

    boolean objectAvailable(int id,String action){
        return Microbot.getClientThread().runOnClientThreadOptional(()->Microbot.getRs2TileObjectCache().query().where(o->{
            var composition=o.getObjectComposition();
            return composition!=null&&composition.getId()==id&&composition.getActions()!=null&&Arrays.asList(composition.getActions()).contains(action);
        }).nearestOnClientThread()!=null).orElse(false);
    }
    boolean object(int id,String action){
        if(!allowed.getAsBoolean())return false;
        var object=Microbot.getClientThread().runOnClientThreadOptional(()->Microbot.getRs2TileObjectCache().query().where(o->{
            var composition=o.getObjectComposition();return composition!=null&&composition.getId()==id&&composition.getActions()!=null&&Arrays.asList(composition.getActions()).contains(action);
        }).nearestOnClientThread()).orElse(null);
        return object!=null&&allowed.getAsBoolean()&&object.click(action);
    }
    boolean useCape(int npcId){
        if(!allowed.getAsBoolean())return false;
        var npc=Microbot.getRs2NpcCache().query().withId(npcId).nearest();
        return npc!=null&&allowed.getAsBoolean()&&Rs2Inventory.useItemOnNpc(6570,npc.getNpc());
    }
    enum CapeSupply { READY, WAITING, MISSING }
    boolean capeCarried(){return Rs2Inventory.hasItem(6570);}
    CapeSupply supplyCape(){
        if(!allowed.getAsBoolean())return CapeSupply.WAITING;
        if(capeCarried())return CapeSupply.READY;
        if(Rs2Inventory.isFull()){
            if(net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen())
                net.runelite.client.plugins.microbot.util.bank.Rs2Bank.depositAllExcept(6570);
            else net.runelite.client.plugins.microbot.util.bank.Rs2Bank.walkToBankAndUseBank();
            return CapeSupply.WAITING;
        }
        if(net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(6570)){
            net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.unEquip(6570);return CapeSupply.WAITING;
        }
        if(!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen()){
            net.runelite.client.plugins.microbot.util.bank.Rs2Bank.walkToBankAndUseBank();return CapeSupply.WAITING;
        }
        if(!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.hasItem(6570))return CapeSupply.MISSING;
        net.runelite.client.plugins.microbot.util.bank.Rs2Bank.withdrawOne(6570);return CapeSupply.WAITING;
    }
    boolean talk(int npcId){return allowed.getAsBoolean()&&Rs2Npc.interact(npcId,"Talk-to");}
    boolean infernoUnlocked(){return Microbot.getClientThread().runOnClientThreadOptional(()->
        Microbot.getClient().getVarbitValue(net.runelite.api.gameval.VarbitID.INFERNO_SACRIFICED_FIRECAPE)!=0).orElse(false);}
    boolean ketKehDialogue(){return Microbot.getClientThread().runOnClientThreadOptional(()->{
        var name=Microbot.getClient().getWidget(net.runelite.api.gameval.InterfaceID.ChatLeft.NAME);
        return name!=null&&!name.isHidden()&&"TzHaar-Ket-Keh".equals(net.runelite.client.util.Text.removeTags(name.getText()));
    }).orElse(false);}
    boolean confirmCape(){
        String option=Microbot.getClientThread().runOnClientThreadOptional(()->Rs2Dialogue.getDialogueOptions().stream()
            .map(w->net.runelite.client.util.Text.removeTags(w.getText())).filter(LocalArenaActions::affirmativeCapeOption).findFirst().orElse(null)).orElse(null);
        return option!=null&&choose(option);
    }
    static boolean affirmativeCapeOption(String text){
        if(text==null)return false;String lower=text.toLowerCase(java.util.Locale.ROOT);
        return lower.matches("yes([ ,.!].*)?")&&!lower.contains("don't")&&!lower.contains("not ");
    }
    boolean continueDialogue(){if(!allowed.getAsBoolean()||!Rs2Dialogue.hasContinue())return false;Rs2Dialogue.clickContinue();return true;}
    boolean choose(String text){return allowed.getAsBoolean()&&Rs2Dialogue.clickOption(text,true);}
}
