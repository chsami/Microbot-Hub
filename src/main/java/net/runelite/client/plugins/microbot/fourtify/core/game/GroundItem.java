package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/** An item lying on the ground. */
@Value
@AllArgsConstructor
public class GroundItem {
    int id;
    String name;
    int quantity;
    WorldPoint location;
    /** Shown only to another player, whose drop it is: it cannot be taken. */
    boolean forOthers;

    public GroundItem(int id, String name, int quantity, WorldPoint location) {
        this(id, name, quantity, location, false);
    }
}
