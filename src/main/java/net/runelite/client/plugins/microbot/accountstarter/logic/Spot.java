package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import java.util.Set;

@Value
public class Spot {
    String name;
    /** Must be walkable: never water, a tree or a rock. */
    WorldPoint center;
    int radius;
    Set<String> targets;
    int minLevel;
    int maxLevel;
    boolean aggressive;
    int npcLevel;
    int npcMaxHit;
}
