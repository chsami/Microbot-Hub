package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.NullObjectID;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;

/**
 * Which half of the arena we are playing, and every id that belongs to it.
 *
 * <p>Almost everything in Tempoross is duplicated per side with <em>distinct ids</em>, so the side is
 * a lookup rather than a geometry problem. Captured live from the agent server across three games —
 * all four exit NPCs appear in a single instance, and the pairing is fixed:
 *
 * <pre>
 *   A: Captain Dudi 10587 (west ship) + First Mate Deri 10593 (north totem) + spots 10565
 *   B: Captain Pudi 10585 (east ship) + First Mate Peri 10596 (south totem) + spots 10568
 * </pre>
 *
 * <p>Measured fishing clusters were 40 tiles apart with no overlap (A at y 7442-7449, B at y
 * 7402-7411), and the crates/cannoneers/masts/totems split the same way.
 *
 * <p>This replaces distance-based side detection, which was provably unsafe: in the captured game the
 * west ship host sat <b>17</b> tiles from its own totem host and <b>18</b> from the other side's — a
 * one tile margin that any proximity rule would eventually get wrong.
 *
 * <p>Not covered here because both sides share the id: the shrine (41236), water pump (41000), supply
 * crates (40964-40967), spirit pool (10571) and the double fishing spot (10569). Those still need a
 * position check against our own anchors.
 */
public enum TemporossSide
{
    A(10587, 10593, NpcID.FISHING_SPOT_10565,
            NullObjectID.NULL_41352, ObjectID.DAMAGED_MAST_40996,
            NullObjectID.NULL_41354, ObjectID.DAMAGED_TOTEM_POLE,
            10576, 10577),

    B(10585, 10596, NpcID.FISHING_SPOT_10568,
            NullObjectID.NULL_41353, ObjectID.DAMAGED_MAST_40997,
            NullObjectID.NULL_41355, ObjectID.DAMAGED_TOTEM_POLE_41011,
            10578, 10579);

    /** "Forfeit" NPC on the ship. */
    public final int shipHostId;
    /** "Forfeit" NPC by the totem — the fishing-area anchor. */
    public final int totemHostId;
    /** Single fishing spot for this side. The double spot (10569) is shared and is not here. */
    public final int fishingSpotId;
    public final int mastId;
    public final int brokenMastId;
    public final int totemId;
    public final int brokenTotemId;
    public final int ammoCrateIdA;
    public final int ammoCrateIdB;

    TemporossSide(int shipHostId, int totemHostId, int fishingSpotId,
                  int mastId, int brokenMastId, int totemId, int brokenTotemId,
                  int ammoCrateIdA, int ammoCrateIdB)
    {
        this.shipHostId = shipHostId;
        this.totemHostId = totemHostId;
        this.fishingSpotId = fishingSpotId;
        this.mastId = mastId;
        this.brokenMastId = brokenMastId;
        this.totemId = totemId;
        this.brokenTotemId = brokenTotemId;
        this.ammoCrateIdA = ammoCrateIdA;
        this.ammoCrateIdB = ammoCrateIdB;
    }

    /** Resolves the side from any of our four exit NPC ids. Null when the id is not an exit NPC. */
    public static TemporossSide fromHostId(int npcId)
    {
        for (TemporossSide side : values())
        {
            if (side.shipHostId == npcId || side.totemHostId == npcId)
            {
                return side;
            }
        }
        return null;
    }

    public boolean isOurAmmoCrate(int npcId)
    {
        return npcId == ammoCrateIdA || npcId == ammoCrateIdB;
    }
}
