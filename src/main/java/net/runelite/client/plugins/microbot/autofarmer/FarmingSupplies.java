package net.runelite.client.plugins.microbot.autofarmer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
final class FarmingSupplies {
    static int notedId(int id) {
        if (id == ItemID.BUCKET_COMPOST) return ItemID.Cert.BUCKET_COMPOST;
        if (id == ItemID.BUCKET_SUPERCOMPOST) return ItemID.Cert.BUCKET_SUPERCOMPOST;
        if (id == ItemID.BUCKET_ULTRACOMPOST) return ItemID.Cert.BUCKET_ULTRACOMPOST;
        return -1;
    }
    static int stored(int id) {
        if (id == ItemID.BUCKET_COMPOST) return Microbot.getVarbitValue(VarbitID.FARMING_TOOLS_COMPOST) + Microbot.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRACOMPOST);
        if (id == ItemID.BUCKET_SUPERCOMPOST) return Microbot.getVarbitValue(VarbitID.FARMING_TOOLS_SUPERCOMPOST) + Microbot.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST);
        if (id == ItemID.BUCKET_ULTRACOMPOST) return Microbot.getVarbitValue(VarbitID.FARMING_TOOLS_ULTRACOMPOST);
        return 0;
    }
    static int refillQuantity(int stored, int banked, int carried) {
        if (stored >= 12 || carried > 0) return 0;
        return Math.max(0, Math.min(Math.max(0, banked), 100 - Math.max(0, stored)));
    }
}
