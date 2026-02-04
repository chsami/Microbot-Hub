package net.runelite.client.plugins.microbot.mahoganyhomez.v2;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@AllArgsConstructor
@Getter
public enum ContractLocation {
    MAHOGANY_HOMES_ARDOUGNE(new WorldPoint(2635, 3294, 0)),
    MAHOGANY_HOMES_FALADOR(new WorldPoint(2990, 3365, 0)),
    MAHOGANY_HOMES_HOSIDIUS(new WorldPoint(1781, 3626, 0)),
    MAHOGANY_HOMES_VARROCK(new WorldPoint(3240, 3471, 0));

    private final WorldPoint location;
}
