package net.runelite.client.plugins.microbot.fourtifysbarrows.crypt;

import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum Brother
{
    DHAROK("Dharok the Wretched", 458, Prayer.PROTECT_FROM_MELEE,
        3575, 3299, 3547, 9709, 14, 11, 3554, 9713, 3557, 9717),
    AHRIM("Ahrim the Blighted", 457, Prayer.PROTECT_FROM_MAGIC,
        3564, 3291, 3549, 9693, 14, 13, 3554, 9699, 3558, 9702),
    KARIL("Karil the Tainted", 460, Prayer.PROTECT_FROM_MISSILES,
        3567, 3274, 3544, 9678, 16, 13, 3549, 9683, 3547, 9685),
    VERAC("Verac the Defiled", 462, Prayer.PROTECT_FROM_MELEE,
        3556, 3297, 3567, 9701, 14, 12, 3572, 9706, 3577, 9703),
    GUTHAN("Guthan the Infested", 459, Prayer.PROTECT_FROM_MELEE,
        3578, 3281, 3533, 9698, 16, 14, 3539, 9702, 3535, 9705),
    TORAG("Torag the Corrupted", 461, Prayer.PROTECT_FROM_MELEE,
        3553, 3281, 3563, 9682, 15, 13, 3568, 9686, 3565, 9684);

    private final String npcName;
    private final int killedVarbit;
    private final Prayer prayer;
    private final WorldPoint mound;
    private final WorldPoint sarcophagus;
    private final WorldPoint stairs;
    private final WorldArea tomb;

    Brother(String npcName, int killedVarbit, Prayer prayer, int moundX, int moundY,
        int tombX, int tombY, int width, int height, int sarcophagusX, int sarcophagusY,
        int stairsX, int stairsY)
    {
        this.npcName = npcName;
        this.killedVarbit = killedVarbit;
        this.prayer = prayer;
        mound = new WorldPoint(moundX, moundY, 0);
        sarcophagus = new WorldPoint(sarcophagusX, sarcophagusY, 3);
        stairs = new WorldPoint(stairsX, stairsY, 3);
        tomb = new WorldArea(tombX, tombY, width, height, 3);
    }

    public String getNpcName()
    {
        return npcName;
    }

    public int getKilledVarbit()
    {
        return killedVarbit;
    }

    public Prayer getPrayer()
    {
        return prayer;
    }

    public WorldPoint getMound()
    {
        return mound;
    }

    public WorldPoint getSarcophagus()
    {
        return sarcophagus;
    }

    public WorldPoint getStairs()
    {
        return stairs;
    }

    public boolean containsTomb(WorldPoint point)
    {
        return point != null && tomb.contains(point);
    }

    public static Brother inTomb(WorldPoint point)
    {
        for (Brother brother : values())
        {
            if (brother.containsTomb(point))
            {
                return brother;
            }
        }
        return null;
    }

    public static Brother fromNpcName(String name)
    {
        for (Brother brother : values())
        {
            if (brother.npcName.equalsIgnoreCase(name))
            {
                return brother;
            }
        }
        return null;
    }
}
