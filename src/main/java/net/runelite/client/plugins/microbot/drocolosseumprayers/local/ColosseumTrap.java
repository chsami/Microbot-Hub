package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

/**
 * 1.0.94: a Jaguar or Minotaur is trapped only when walls hold it off the tile.
 * <p>
 * What the recordings say (2026-11-09). A Jaguar's attack lands three hits in one tick (31+14+44, 22+11+42, 42+12+9
 * seen); it killed four of the day's six runs. The servers, on the same pure, kept Jaguars from attacking at all: of
 * 213 Jaguars in 39 server runs, 78% never clawed once, they were in claw reach 7% of their lives and parked out of
 * reach 77%, 1.8 claws per Jaguar. Our 47 live runs: 24% never clawed, 29% of the time in reach, 7.9 claws per Jaguar.
 * The servers stood on the pillar's west face, (1815,3114) or (1815,3113), for 42% of all Jaguar time, with the Jaguar
 * stuck in the slot north of it at (1815,3116).
 * <p>
 * Why ours were not trapped. The engine's trap test let another monster count as a wall, so a Jaguar queued behind a
 * Shaman passed for trapped and the player stayed on a tile it could reach (15:57 wave 3: 24 ticks on (1815,3115) with
 * the Jaguar waiting at (1817,3116) behind a Shaman; the Shaman died, the Jaguar walked in). Scored against every
 * Jaguar in 109 live recordings (772 stretches of the player holding a tile with a Jaguar after it, 20 ticks each):
 * <ul>
 * <li>the old test said "trapped" 693 times and the Jaguar reached the player anyway in 216 (31%; 67% within 40 ticks);</li>
 * <li>the walls-only test said "trapped" 367 times and it reached the player in 9 (2%); where it said "free", the
 * Jaguar arrived in 286 of 405 (71%; 96% within 40 ticks).</li>
 * </ul>
 * Every trap judgement in both engines goes through {@link #trapped} and is walls-only. The tile scoring that was
 * already there then walks to the trap tile by itself (replay of 15:57: to (1815,3114) three ticks after the Jaguar
 * spawns). Tried on top and removed, each worse or no better on the simulator: a rule that walks to a trap tile before
 * anything else (-0.31 waves), one that forbids any later move that frees a trapped Jaguar (-0.09), one that holds the
 * tile beside a Jaguar in reach (-0.06), and one that gave no credit for a shot at a Jaguar free to walk up (-0.24). -Dcolo.trap=false restores the 1.0.93 test for comparisons. No prayer code is
 * involved.
 */
final class ColosseumTrap
{
    static final boolean ON=!"false".equals(System.getProperty("colo.trap"));
    private ColosseumTrap(){}
    /** Held off {@code tile} by walls, whatever other monsters stand in the way. */
    static boolean trapped(ColosseumCollision collision,Npc n,Tile tile)
    {return ON?collision.npcWallTrapped(n,tile):collision.npcTrapped(n,tile);}
}
