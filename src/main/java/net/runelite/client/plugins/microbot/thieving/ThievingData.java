package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Arrays;

public final class ThievingData {
    public static final WorldPoint NULL_WORLD_POINT = new WorldPoint(-1,-1,-1);
    public static final WorldPoint OUTSIDE_HALLOWED_BANK = new WorldPoint(3654,3384,0);
    public static final WorldArea ARDOUGNE_AREA = new WorldArea(2649, 3280, 7, 8, 0);

    public static final Set<String> VYRE_SET = Set.of(
            "vyre noble shoes",
            "vyre noble legs",
            "vyre noble top"
    );

    public static final Set<String> ROGUE_SET = Set.of(
            "rogue mask",
            "rogue top",
            "rogue trousers",
            "rogue boots",
            "rogue gloves"
    );

    private static final Map<String, WorldPoint[]> VYRE_HOUSES = Map.of(
            "Vallessia von Pitt", new WorldPoint[]{new WorldPoint(3661, 3378, 0), new WorldPoint(3661, 3381, 0), new WorldPoint(3667, 3381, 0), new WorldPoint(3667, 3376, 0), new WorldPoint(3664, 3376, 0), new WorldPoint(3664, 3378, 0)},
            "Misdrievus Shadum", new WorldPoint[]{new WorldPoint(3608, 3346, 0), new WorldPoint(3611, 3346, 0), new WorldPoint(3611, 3343, 0), new WorldPoint(3608, 3343, 0)},
            "Natalidae Shadum", new WorldPoint[]{new WorldPoint(3608, 3342, 0), new WorldPoint(3611, 3342, 0), new WorldPoint(3611, 3337, 0), new WorldPoint(3608, 3337, 0)},
            "Vonnetta Varnis", new WorldPoint[]{new WorldPoint(3640, 3384, 0), new WorldPoint(3640, 3388, 0), new WorldPoint(3645, 3388, 0), new WorldPoint(3645, 3384, 0)},
            "Nakasa Jovkai", new WorldPoint[]{new WorldPoint(3608, 3322, 0), new WorldPoint(3608, 3327, 0), new WorldPoint(3612, 3327, 0), new WorldPoint(3612, 3322, 0)}
            // add more...
    );

    public static WorldPoint[] getVyreHouse(String vyreName) {
        if (vyreName == null) return null;
        final WorldPoint[] house = VYRE_HOUSES.get(vyreName);
        if (house == null) throw new IllegalArgumentException("Vyre House is not defined for " + vyreName);
        return house;
    }

    private static final Map<String, WorldPoint> VYRE_ESCAPE = Map.of(
            "Vallessia von Pitt", NULL_WORLD_POINT,
            "Misdrievus Shadum", NULL_WORLD_POINT,
            "Natalidae Shadum", NULL_WORLD_POINT,
            "Vonnetta Varnis", NULL_WORLD_POINT,
            "Nakasa Jovkai", new WorldPoint(3611, 3323, 1) // upstairs
            // add more...
    );

    public static WorldPoint getVyreEscape(String vyreName) {
        if (vyreName == null) return NULL_WORLD_POINT;
        assert VYRE_ESCAPE.containsKey(vyreName);
        return VYRE_ESCAPE.getOrDefault(vyreName, NULL_WORLD_POINT);
    }

    private static final Map<String, WorldPoint> VYRE_POSITION = Map.of(
            "Vallessia von Pitt", NULL_WORLD_POINT,
            "Misdrievus Shadum", NULL_WORLD_POINT,
            "Natalidae Shadum", NULL_WORLD_POINT,
            "Vonnetta Varnis", NULL_WORLD_POINT,
            "Nakasa Jovkai", NULL_WORLD_POINT
            // add more...
    );

    public static WorldPoint getVyrePosition(String vyreName) {
        assert VYRE_HOUSES.containsKey(vyreName);
        return VYRE_POSITION.getOrDefault(vyreName, NULL_WORLD_POINT);
    }

    public static final Set<String> ELVES = Set.of("Anaire","Aranwe","Aredhel","Caranthir","Celebrian","Celegorm",
            "Cirdan","Curufin","Earwen","Edrahil", "Elenwe","Elladan","Enel","Erestor","Enerdhil","Enelye","Feanor",
            "Findis","Finduilas","Fingolfin", "Fingon","Galathil","Gelmir","Glorfindel","Guilin","Hendor","Idril",
            "Imin","Iminye","Indis","Ingwe", "Ingwion","Lenwe","Lindir","Maeglin","Mahtan","Miriel","Mithrellas",
            "Nellas","Nerdanel","Nimloth", "Oropher","Orophin","Saeros","Salgant","Tatie","Thingol","Turgon","Vaire",
            "Goreu");

    public static final Set<String> VYRES = VYRE_HOUSES.keySet();

    public static final List<String> ANCIENT_BREW_DOSES = Arrays.asList(
        "Ancient brew(1)",
        "Ancient brew(2)",
        "Ancient brew(3)",
        "Ancient brew(4)"
    );
}
