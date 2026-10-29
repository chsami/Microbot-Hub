package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import java.util.List;

/**
 * A non-player character as the player sees it: its visible name and menu actions (from its transformed
 * definition), its combat level, whether it is attacking or talking to the player, and its health as a
 * percentage (-1 while its health bar has not shown), whether it is after someone else, and whether it is the
 * player's target. NPCs move, so it is found again by its index.
 */
@Value
@AllArgsConstructor
public class Npc {
    int id;
    /** The client's index for this NPC, unique while it exists. */
    int index;
    String name;
    WorldPoint location;
    int combatLevel;
    List<String> actions;
    boolean interactingWithPlayer;
    double healthPercent;
    /** Attacking or talking to someone other than the player, such as another player's fight. */
    boolean interactingWithOther;
    /** The player is attacking or talking to it. */
    boolean playerTarget;

    public Npc(int id, int index, String name, WorldPoint location, int combatLevel, List<String> actions,
               boolean interactingWithPlayer, double healthPercent) {
        this(id, index, name, location, combatLevel, actions, interactingWithPlayer, healthPercent, false, false);
    }

    public boolean hasAction(String action) {
        for (String candidate : actions) {
            if (candidate.equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }
}
