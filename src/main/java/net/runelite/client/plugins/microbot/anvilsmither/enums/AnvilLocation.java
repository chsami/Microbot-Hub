package net.runelite.client.plugins.microbot.anvilsmither.enums;

import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;

/**
 * Where to smith. Both anvils are the ordinary "Anvil" object with a "Smith" option and open the same
 * interface, so a location is nothing more than a pair of tiles plus whatever gates access to them.
 *
 * Both tiles are WALKABLE STAND TILES, not the tiles the objects occupy: the script walks to them and
 * clicks the object from there, and pathfinding to an object's own tile stops a step short.
 *
 * The Prifddinas tiles were read off a live client standing at the south bank, not off a wiki map.
 * Three anvils are reachable there — (3288,6053), (3287,6055) and (3285,6054) — and (3289,6053) is
 * where the walker lands coming from the bank, adjacent to the first. The bank has booths (id 36559 at
 * 3296,6058-6060) AND three Bankers behind them, so either interaction works.
 */
@Getter
public enum AnvilLocation {
    VARROCK_WEST(
            "Varrock west",
            new WorldPoint(3187, 3426, 0),
            new WorldPoint(3185, 3436, 0),
            null),

    /** Trahaearn district, south-east Prifddinas: the closest anvil to a bank in the game. */
    PRIFDDINAS_SOUTH(
            "Prifddinas south",
            new WorldPoint(3289, 6053, 0),
            new WorldPoint(3295, 6059, 0),
            Quest.SONG_OF_THE_ELVES);

    private final String label;
    private final WorldPoint anvil;
    private final WorldPoint bank;

    /** Quest that must be finished to be here at all, or null when anyone can walk in. */
    private final Quest requiredQuest;

    AnvilLocation(String label, WorldPoint anvil, WorldPoint bank, Quest requiredQuest) {
        this.label = label;
        this.anvil = anvil;
        this.bank = bank;
        this.requiredQuest = requiredQuest;
    }

    @Override
    public String toString() {
        return label;
    }
}
