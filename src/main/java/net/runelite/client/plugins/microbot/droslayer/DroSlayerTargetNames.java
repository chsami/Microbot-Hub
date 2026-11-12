package net.runelite.client.plugins.microbot.droslayer;

import java.util.Locale;

/** Matches plural profile labels without broadening a selected monster variant. */
final class DroSlayerTargetNames
{
    private DroSlayerTargetNames() {}

    static boolean sameVariant(String npcName, String targetName)
    {
        if (npcName == null || targetName == null) return false;
        String npc = npcName.trim().toLowerCase(Locale.ROOT);
        String target = targetName.trim().toLowerCase(Locale.ROOT);
        if (npc.isEmpty() || target.isEmpty()) return false;
        return npc.equals(target) || (npc + "s").equals(target) || npc.equals(target + "s");
    }
}
