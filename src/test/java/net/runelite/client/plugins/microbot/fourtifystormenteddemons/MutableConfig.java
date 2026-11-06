package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonRoom;

/** Defaults match the shipped settings; tests change the fields they care about. */
public final class MutableConfig implements TormentedDemonsConfig
{
    public DemonRoom room = DemonRoom.PLINTH_CHAMBER;
    public boolean offensive;
    public String magic = "";
    public String ranged = "";
    public String melee = "";
    public String keep = "";
    public String restock = "";
    public int food = 385;
    public int foodTotal = 12;
    public int eat = 55;
    public int restores = 4;
    public int drink = 20;
    public int scrolls = 5;
    public int lootValue = 5000;
    public String always = "Tormented synapse, Burning claw, Guthixian temple teleport";
    public int emergency = 25;
    public int delayMin = 1;
    public int delayMax = 3;
    public int delayTarget = 2;
    public int delayDeviation = 1;
    public boolean weighted;

    @Override
    public DemonRoom room()
    {
        return room;
    }

    @Override
    public boolean offensivePrayer()
    {
        return offensive;
    }

    @Override
    public String magicGear()
    {
        return magic;
    }

    @Override
    public String rangedGear()
    {
        return ranged;
    }

    @Override
    public String meleeGear()
    {
        return melee;
    }

    @Override
    public String keepItems()
    {
        return keep;
    }

    @Override
    public String restockItems()
    {
        return restock;
    }

    @Override
    public int foodId()
    {
        return food;
    }

    @Override
    public int foodCount()
    {
        return foodTotal;
    }

    @Override
    public int eatAt()
    {
        return eat;
    }

    @Override
    public int restoreCount()
    {
        return restores;
    }

    @Override
    public int drinkAt()
    {
        return drink;
    }

    @Override
    public int scrollCount()
    {
        return scrolls;
    }

    @Override
    public int lootMinimum()
    {
        return lootValue;
    }

    @Override
    public String lootAlways()
    {
        return always;
    }

    @Override
    public int emergencyAt()
    {
        return emergency;
    }

    @Override
    public int tickDelayMin()
    {
        return delayMin;
    }

    @Override
    public int tickDelayMax()
    {
        return delayMax;
    }

    @Override
    public int tickDelayTarget()
    {
        return delayTarget;
    }

    @Override
    public int tickDelayDeviation()
    {
        return delayDeviation;
    }

    @Override
    public boolean tickDelayWeightedDistribution()
    {
        return weighted;
    }
}
