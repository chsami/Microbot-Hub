package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.Item;

/**
 * 1.0.89: the ranged special attacks the engine uses (user, 2026-11-09: the Toxic blowpipe's spec was never sent, and the
 * Magic shortbow, Zaryte, Armadyl and Dragon crossbow specs were not wired). 1.0.86 knew the Rosewood blowpipe only.
 * Costs are in the snapshot's units (1000 = 100%).
 */
final class ColosseumSpecs
{
    static final int ROSEWOOD_BLOWPIPE=31583,TOXIC_BLOWPIPE=12926,TOXIC_BLOWPIPE_ORNAMENT=28688,MAGIC_SHORTBOW=861,
        MAGIC_SHORTBOW_I=12788,ZARYTE_CROSSBOW=26374,ARMADYL_CROSSBOW=11785,DRAGON_CROSSBOW=21902;
    /** The Toxic blowpipe's spec heals half its damage: it is kept until this much HP is missing. */
    static final int HEAL_MISSING=15;
    private ColosseumSpecs(){}

    /** Special attack energy the weapon's spec costs, or -1 for a weapon whose spec the engine does not use. */
    static int cost(int id)
    {
        switch(id)
        {
            case ROSEWOOD_BLOWPIPE:return 250;
            case TOXIC_BLOWPIPE:case TOXIC_BLOWPIPE_ORNAMENT:return 500;
            case MAGIC_SHORTBOW:return 550;
            case MAGIC_SHORTBOW_I:return 500;
            case ZARYTE_CROSSBOW:return 750;
            case ARMADYL_CROSSBOW:return 500;
            case DRAGON_CROSSBOW:return 600;
            default:return -1;
        }
    }
    /** The worn weapon has a spec, the energy for it, and (for the healing Toxic blowpipe) enough HP missing to use it. */
    static boolean due(ColosseumSnapshot f,Item weapon)
    {
        if(weapon==null)return false;
        int cost=cost(weapon.id);
        if(cost<0||f.specEnergy<cost)return false;
        boolean heals=weapon.id==TOXIC_BLOWPIPE||weapon.id==TOXIC_BLOWPIPE_ORNAMENT;
        return !heals||f.hp<=f.effectiveMaxHp-HEAL_MISSING;
    }
}
