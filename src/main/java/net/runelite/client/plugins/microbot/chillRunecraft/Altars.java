package net.runelite.client.plugins.microbot.chillRunecraft;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

@Getter
@RequiredArgsConstructor
public enum Altars
{
    AIR_ALTAR("Air Altar", ObjectID.AIR_ALTAR, 34813, 34748 ,new WorldPoint(2990,3291,0), "Air tiara", "Air Talisman", "Air Rune", ItemID.AIR_TALISMAN, BankLocation.FALADOR_EAST, null),
    EARTH_ALTAR("Earth Altar", ObjectID.EARTH_ALTAR, 34816, 34751 ,new WorldPoint(3302,3470,0), "Earth tiara", "Earth Talisman", "Earth Rune", ItemID.EARTH_TALISMAN, BankLocation.VARROCK_EAST, null),
    WATER_ALTAR("Water Altar", ObjectID.WATER_ALTAR, 34815, 34750 ,new WorldPoint(3190,3162,0), "Water tiara", "Water Talisman", "Water Rune", ItemID.WATER_TALISMAN, BankLocation.LUMBRIDGE_TOP, null),
    FIRE_ALTAR("Fire Altar", ObjectID.FIRE_ALTAR, 34817, 34752 ,new WorldPoint(3309,3252,0), "Fire tiara", "Fire Talisman", "Fire Rune", ItemID.FIRE_TALISMAN, BankLocation.AL_KHARID, BankLocation.DUEL_ARENA),
    NATURE_ALTAR("Nature Altar", ObjectID.NATURE_ALTAR, 34821, 34756 ,new WorldPoint(2872,3015,0), "Nature tiara", "Nature Talisman", "Nature Rune", ItemID.NATURE_TALISMAN, null, BankLocation.SHILO_VILLAGE),
    BODY_ALTAR("Body Altar", ObjectID.BODY_ALTAR, 34818, 34753 ,new WorldPoint(3053,3443,0), "Body tiara", "Body Talisman", "Body Rune", ItemID.BODY_TALISMAN, BankLocation.EDGEVILLE, null);

    private final String altarName;
    private final int altarID;
    private final int altarRuinsID;
    private final int portalID;
    private final WorldPoint altarWorldPoint;
    private final String tiaraName;
    private final String talismanName;
    private final String runeName;
    private final int talismanID;
    private final BankLocation bank;
    private final BankLocation membersBank;

    public BankLocation getPreferredBank()
    {
        return membersBank != null && membersBank.hasRequirements() ? membersBank : bank;
    }
}
