package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

/** A game object as the player sees it: its visible name and menu actions, and whether a path leads to it. */
@Value
public class Scenery {
    int id;
    String name;
    WorldPoint location;
    List<String> actions;
    boolean reachable;

    public boolean hasAction(String action) {
        for (String candidate : actions) {
            if (candidate.equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }
}
