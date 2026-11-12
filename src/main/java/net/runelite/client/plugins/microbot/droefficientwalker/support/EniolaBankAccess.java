package net.runelite.client.plugins.microbot.droefficientwalker.support;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;

/** Eniola must never be treated as a free bank after inventory settlement. */
public final class EniolaBankAccess {
    private static final java.util.Set<Runes> PAYMENT_RUNES = java.util.EnumSet.of(
        Runes.AIR, Runes.WATER, Runes.EARTH, Runes.FIRE, Runes.MIND, Runes.CHAOS,
        Runes.DEATH, Runes.BLOOD, Runes.COSMIC, Runes.NATURE, Runes.LAW, Runes.BODY,
        Runes.SOUL, Runes.ASTRAL);
    public static final WorldPoint RECOVERY_BANK = new WorldPoint(3165,3487,0);
    public static boolean nearby(WorldPoint point) {
        return point != null && point.getPlane() == 0
            && point.distanceTo(new WorldPoint(3014,5625,0)) <= 20;
    }
    public static boolean canPay(boolean earthOnly) {
        var client=Microbot.getClient();
        if(client==null)return false;
        if(client.isClientThread())return canPay(client.getItemContainer(InventoryID.INVENTORY),earthOnly);
        var thread=Microbot.getClientThread();
        return thread!=null&&thread.runOnClientThreadOptional(()->canPay(client.getItemContainer(InventoryID.INVENTORY),earthOnly)).orElse(false);
    }
    private static boolean canPay(ItemContainer inventory,boolean earthOnly) {
        // The convenience inventory and pouch flags can still show the items just banked.
        if(inventory==null||inventory.getItems()==null)return false;
        boolean carriesPouch=false;
        for(Item item:inventory.getItems()){
            if(item==null||item.getId()<=0||item.getQuantity()<=0)continue;
            if(item.getQuantity()>=20){
                if(earthOnly&&item.getId()==Runes.EARTH.getItemId())return true;
                if(!earthOnly)for(Runes rune:Runes.values())if(acceptedPayment(rune)&&item.getId()==rune.getItemId())return true;
            }
            for(RunePouchType pouch:RunePouchType.values())if(item.getId()==pouch.getItemId())carriesPouch=true;
        }
        return !earthOnly&&carriesPouch&&Rs2RunePouch.getRunes().entrySet().stream()
            .anyMatch(entry->acceptedPayment(entry.getKey())&&entry.getValue()>=20);
    }
    private static boolean acceptedPayment(Runes rune) {
        // These are the fourteen rune choices in the client's ZmiBankPayment interface.
        return PAYMENT_RUNES.contains(rune);
    }
    private EniolaBankAccess() {}
}
