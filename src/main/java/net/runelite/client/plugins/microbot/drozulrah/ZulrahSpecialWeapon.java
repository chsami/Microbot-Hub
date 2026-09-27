package net.runelite.client.plugins.microbot.drozulrah;

import java.util.Locale;

/** Offensive specials suitable for the equipped Zulrah combat style. Energy uses varp units. */
enum ZulrahSpecialWeapon
{
    BLOWPIPE("Toxic blowpipe", 500, false),
    MSB("Magic shortbow", 550, false),
    MSB_I("Magic shortbow (i)", 500, false),
    MAGIC_LONGBOW("Magic longbow", 350, false),
    MAGIC_COMP_BOW("Magic comp bow", 350, false),
    DARK_BOW("Dark bow", 550, false),
    ARMADYL_CROSSBOW("Armadyl crossbow", 400, false),
    DRAGON_CROSSBOW("Dragon crossbow", 600, false),
    HEAVY_BALLISTA("Heavy ballista", 650, false),
    LIGHT_BALLISTA("Light ballista", 650, false),
    ELDRITCH("Eldritch nightmare staff", 550, true),
    VOLATILE("Volatile nightmare staff", 550, true);

    final String name;
    final int cost;
    final boolean magic;

    ZulrahSpecialWeapon(String name, int cost, boolean magic)
    {
        this.name = name.toLowerCase(Locale.ROOT);
        this.cost = cost;
        this.magic = magic;
    }

    static ZulrahSpecialWeapon find(String name)
    {
        if (name == null) return null;
        for (ZulrahSpecialWeapon weapon : values())
            if (weapon.name.equals(name.toLowerCase(Locale.ROOT))) return weapon;
        return null;
    }
}
