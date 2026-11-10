package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/**
 * "Blood spells on the last enemy": with one enemy left and HP under {@link #TARGET_PERCENT}, the mage setup casts its
 * Blood autocast. The game shows the autocast only while the staff is worn, so the level seen is remembered. A client
 * started mid-run has not seen it yet (1.0.49 manual 2026-11-05 00:47: the last Javelin fought at 72/98 HP in the
 * atlatl, no heal): on the Ancient spellbook the highest Blood spell the Magic level allows stands in until the staff
 * is worn. A worn staff without a Blood autocast turns the option off until one is seen.
 */
final class ColosseumBlood
{
    /** Heal to 95% of max HP (user, 2026-11-05: raised from 90%; full took too long). */
    static final int TARGET_PERCENT=95;
    /** Blood Rush, Burst, Blitz, Barrage. */
    private static final int[] LEVELS={92,80,68,56};
    /** 0 unknown, -1 the staff is worn without a Blood autocast, else the autocast's Magic level. */
    private int seen;

    boolean heal(ColosseumSnapshot f,int enemies,ColosseumGear gear)
    {
        int level=level(f);
        return wanted(f,enemies,gear,level)&&f.magic>=level;
    }
    /**
     * The heal is due but brews drained Magic below the Blood spell: a restore comes first (1.0.51 2026-11-05 07:40
     * wave 4: the last Shaman fought at 62/98 HP with Magic 74 under a Barrage autocast, no heal and no restore).
     * Only a level actually seen on the staff counts: the stand-in for a fresh client follows the drained Magic.
     */
    boolean drained(ColosseumSnapshot f,int enemies,ColosseumGear gear)
    {
        int level=level(f);
        return seen>0&&wanted(f,enemies,gear,level)&&f.magic<level;
    }
    /**
     * The second Blood heal (user, 2026-11-06, live 1.0.84: "conserve. blood burst or blitz it"): every Jaguar after the
     * player is wall-trapped and not beside it, one is in the Blood's reach with a clear shot from where the player
     * stands, no shooter has a firing ray to the player and no warband is alive. Then the Blood autocast heals on that
     * Jaguar to {@value #TARGET_PERCENT}% instead of a brew. Returns that Jaguar, or null.
     */
    Npc jaguarHeal(ColosseumSnapshot f,ColosseumCollision collision,ColosseumGear gear)
    {
        int level=level(f);
        if(level<=0||f.magic<level||f.hp*100>=f.effectiveMaxHp*TARGET_PERCENT||!collision.available())return null;
        int range=gear.range(f,ColosseumGear.Style.MAGE);if(range<=0)return null;
        ColosseumCollision.Tile at=new ColosseumCollision.Tile(f.x,f.y);Npc best=null;
        for(Npc n:f.npcs)
        {
            if(!n.alive()||!n.enemy()||n.role==Role.FLARE)continue;
            if(n.warband())return null;
            if(n.role==Role.JAGUAR)
            {
                if(!n.targetsPlayer())continue;
                int d=n.distance(f.x,f.y);
                if(d<=1||!ColosseumTrap.trapped(collision,n,at))return null;
                if(d<=range&&collision.firingSight(n,at)&&(best==null||d<best.distance(f.x,f.y)))best=n;
                continue;
            }
            if(n.meleeOnly()){if(n.distance(f.x,f.y)<=1)return null;continue;}
            if(n.distance(f.x,f.y)<=(n.role==Role.SHAMAN?10:15)&&collision.firingSight(n,at))return null;
        }
        return best;
    }
    private static boolean wanted(ColosseumSnapshot f,int enemies,ColosseumGear gear,int level)
    {return enemies==1&&f.hp*100<f.effectiveMaxHp*TARGET_PERCENT&&level>0&&gear.range(f,ColosseumGear.Style.MAGE)>0;}
    private int level(ColosseumSnapshot f)
    {
        if(f.bloodLevel>0)seen=f.bloodLevel;
        else if(ColosseumGear.type(f.weapon())==ColosseumGear.Style.MAGE&&!bool(f.player,"bloodAutocast",true)&&seen==0)seen=-1;
        return f.bloodLevel>0?f.bloodLevel:!bool(f.player,"ancientSpellbook",false)?0:seen!=0?seen:assumed(f.magic);
    }
    private static int assumed(int magic){for(int level:LEVELS)if(magic>=level)return level;return 0;}
}
