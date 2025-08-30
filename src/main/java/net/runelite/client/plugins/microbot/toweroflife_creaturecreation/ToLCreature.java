package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum ToLCreature {
    UNICOW(new WorldPoint(3019, 4408, 0)),
    SPIDINE(new WorldPoint(3045, 4362, 0));

    final WorldPoint altarLocation;

    ToLCreature(WorldPoint _altarLocation)
        {
            this.altarLocation = _altarLocation;
        }
}
