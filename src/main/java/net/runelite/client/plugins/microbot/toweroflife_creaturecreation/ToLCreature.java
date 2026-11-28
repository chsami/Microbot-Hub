package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;

@Getter
public enum ToLCreature {
    UNICOW(NpcID.TOL_UNICOW, new WorldPoint(3019, 4408, 0)),
    SPIDINE(NpcID.TOL_SPIDINE, new WorldPoint(3045, 4362, 0));

    final int npcId;
    final WorldPoint altarLocation;

    ToLCreature(int _npcId, WorldPoint _altarLocation)
        {
            this.npcId = _npcId;
            this.altarLocation = _altarLocation;
        }
}
