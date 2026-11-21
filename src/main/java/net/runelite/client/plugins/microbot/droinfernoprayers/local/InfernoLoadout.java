package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Item;

final class InfernoLoadout
{
    private InfernoLoadout() { }

    enum Mode { MAGIC, RANGED, BLOOD, ICE }

    static Mode weaponType(Item item)
    {
        if (item == null) return null;
        return isMagicWeapon(item) ? Mode.MAGIC : Mode.RANGED;
    }

    static boolean isMagicWeapon(Item item)
    {
        if (item == null) return false;
        String n = item.lower;
        return n.contains("wand") || n.contains("staff") || n.contains("sceptre")
            || item.id == 4675 || item.id == 6914 || item.id == 27624
            || item.id == 28262 || item.id == 28260 || item.id == 21006;
    }

    static int speed(Item item)
    {
        if (item == null) return 0;
        String n = item.lower;
        if (n.contains("blowpipe")) return 2;
        if (isMagicWeapon(item) || item.id == 20997 || n.contains("twisted bow") || n.contains("crossbow") || n.contains("bolts")) return 5;
        if (n.contains("chinchompa") || n.contains("bow of faerdhinen") || n.contains("crystal bow")) return 4;
        return 0;
    }
}
