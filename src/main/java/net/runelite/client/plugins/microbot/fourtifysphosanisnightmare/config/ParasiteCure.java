package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config;

import java.util.List;

/** The potion that weakens a parasite, with its doses from four down to one. */
public enum ParasiteCure
{
    SANFEW_SERUM(List.of(10925, 10927, 10929, 10931)),
    RELICYMS_BALM(List.of(4842, 4844, 4846, 4848));

    private final List<Integer> doses;

    ParasiteCure(List<Integer> doses)
    {
        this.doses = doses;
    }

    public List<Integer> getDoses()
    {
        return doses;
    }

    @Override
    public String toString()
    {
        return this == SANFEW_SERUM ? "Sanfew serum" : "Relicym's balm";
    }
}
