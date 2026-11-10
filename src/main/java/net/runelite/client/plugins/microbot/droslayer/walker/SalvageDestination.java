package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.SeaPoint;

/** Named sea destinations shared by Walker and the salvager. Collision checks still apply. */
public enum SalvageDestination {
    KHARIDIAN_SEA("Small - South of Pandemonium", 3122, 2986),
    CATHERBY_BAY("Fisherman - Catherby Bay", 2779, 3329),
    SEA_SPIRIT_DOCK("Barracuda - South of Pandemonium", 3005, 2930),
    GREAT_CONCH("Large - Great Conch", 3392, 2156),
    PEST_CONTROL("Pirate - South of Pest Control", 2633, 2544),
    SUNBLEAK("Mercenary - South-west of Sunbleak", 2142, 2209),
    UNGAEL("Fremennik - North of Ungael", 2268, 4129),
    DUSKS_MAW("Merchant - Dusk's Maw", 1061, 2964);

    private final String label;
    public final SeaPoint sea;
    SalvageDestination(String label, int x, int y) {
        this.label = label;
        sea = new SeaPoint(x, y);
    }
    @Override public String toString() { return label; }
}
