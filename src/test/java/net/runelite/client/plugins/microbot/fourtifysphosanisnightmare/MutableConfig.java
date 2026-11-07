package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare;

import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.ParasiteCure;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.SpecTiming;

/** Defaults match the shipped settings except for gear; tests change the fields they care about. */
public final class MutableConfig implements PhosanisNightmareConfig
{
    public boolean offensive = false;
    public boolean thrall = false;
    public boolean charge = false;
    public boolean redeem = true;
    public String melee = "";
    public String magic = "";
    public String husk = "";
    public String parasite = "";
    public String sleepwalker = "";
    public String spec = "";
    public SpecTiming timing = SpecTiming.FINAL_PHASE;
    public String keep = "";
    public int food = 385;
    public int foodTotal = 8;
    public int eat = 70;
    public int combatPotion = 0;
    public int restores = 16;
    public int drink = 30;
    public ParasiteCure cure = ParasiteCure.SANFEW_SERUM;
    public int cures = 8;
    public int nextFood = 4;
    public int nextRestores = 4;
    public int nextCures = 4;
    public int lootValue = 5000;
    public String always = "Slepey tablet, Parasitic egg";
    public int delayMin = 1;
    public int delayMax = 3;
    public int delayTarget = 2;
    public int delayDeviation = 1;
    public boolean weighted = false;

    @Override
    public boolean offensivePrayers()
    {
        return offensive;
    }

    @Override
    public boolean ghostlyThrall()
    {
        return thrall;
    }

    @Override
    public boolean deathCharge()
    {
        return charge;
    }

    @Override
    public boolean redemption()
    {
        return redeem;
    }

    @Override
    public String meleeGear()
    {
        return melee;
    }

    @Override
    public String magicGear()
    {
        return magic;
    }

    @Override
    public String huskWeapon()
    {
        return husk;
    }

    @Override
    public String parasiteWeapon()
    {
        return parasite;
    }

    @Override
    public String sleepwalkerWeapon()
    {
        return sleepwalker;
    }

    @Override
    public String specWeapon()
    {
        return spec;
    }

    @Override
    public SpecTiming specTiming()
    {
        return timing;
    }

    @Override
    public String keepItems()
    {
        return keep;
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
    public int combatPotionId()
    {
        return combatPotion;
    }

    @Override
    public int restoreDoses()
    {
        return restores;
    }

    @Override
    public int drinkAt()
    {
        return drink;
    }

    @Override
    public ParasiteCure parasiteCure()
    {
        return cure;
    }

    @Override
    public int cureDoses()
    {
        return cures;
    }

    @Override
    public int nextKillFood()
    {
        return nextFood;
    }

    @Override
    public int nextKillRestores()
    {
        return nextRestores;
    }

    @Override
    public int nextKillCures()
    {
        return nextCures;
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
