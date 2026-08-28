package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.NullObjectID;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;


public class TemporossWorkArea
{
    public final WorldPoint exitNpc;
    public final WorldPoint safePoint;
    public final WorldPoint bucketPoint;
    public final WorldPoint pumpPoint;
    public final WorldPoint ropePoint;
    public final WorldPoint hammerPoint;
    public final WorldPoint harpoonPoint;
    public final WorldPoint mastPoint;
    public final WorldPoint totemPoint;
    public final WorldPoint rangePoint;
    public final WorldPoint spiritPoolPoint;

    public final boolean isWest;
    /**
     * Each side has TWO exit ("Forfeit") NPCs: one on the ship and one by the totem, ~17 tiles apart.
     * Together they span our side — the totem one sits right by the fishing area. Nullable because it
     * may be outside NPC render distance at setup; captured later by the tick loop when seen.
     */
    private volatile WorldPoint totemExitNpc;

    /** Which half we are on, and every per-side id. Never null once the work area exists. */
    public final TemporossSide side;

    public TemporossWorkArea(WorldPoint exitNpc, boolean isWest, WorldPoint totemExitNpc, TemporossSide side)
    {
        this.isWest = isWest;
        this.exitNpc = exitNpc;
        this.totemExitNpc = totemExitNpc;
        this.side = side;
        this.safePoint = exitNpc.dx(1).dy(1);

        if (isWest)
        {
            this.bucketPoint = exitNpc.dx(-3).dy(-1);
            this.pumpPoint = exitNpc.dx(-3).dy(-2);
            this.ropePoint = exitNpc.dx(-3).dy(-5);
            this.hammerPoint = exitNpc.dx(-3).dy(-6);
            this.harpoonPoint = exitNpc.dx(-2).dy(-7);
            this.mastPoint = exitNpc.dx(0).dy(-3);
            this.totemPoint = exitNpc.dx(8).dy(15);
            this.rangePoint = exitNpc.dx(3).dy(21);
            this.spiritPoolPoint = exitNpc.dx(11).dy(4);
        }
        else
        {
            this.bucketPoint = exitNpc.dx(3).dy(1);
            this.pumpPoint = exitNpc.dx(3).dy(2);
            this.ropePoint = exitNpc.dx(3).dy(5);
            this.hammerPoint = exitNpc.dx(3).dy(6);
            this.harpoonPoint = exitNpc.dx(2).dy(7);
            this.mastPoint = exitNpc.dx(0).dy(3);
            this.totemPoint = exitNpc.dx(-15).dy(-13);
            this.rangePoint = exitNpc.dx(-23).dy(-19);
            this.spiritPoolPoint = exitNpc.dx(-11).dy(-4);
        }
    }

    private TemporossObjectSnapshot snapshot(Supplier<Rs2TileObjectModel> lookup)
    {
        Supplier<TemporossObjectSnapshot> operation = () -> {
            Rs2TileObjectModel object = lookup.get();
            return object == null ? null : new TemporossObjectSnapshot(
                    object.getId(), object.getLocalLocation(), object.getWorldLocation());
        };
        return Microbot.getClientThread().invoke(operation);
    }

    public TemporossObjectSnapshot getBucketCrate()
    {
        return snapshot(() -> Microbot.getRs2TileObjectCache().query().withId(ObjectID.BUCKETS).within(bucketPoint, 2).nearest());
    }

    public TemporossObjectSnapshot getPump()
    {
        return snapshot(() -> Microbot.getRs2TileObjectCache().query().withId(ObjectID.WATER_PUMP_41000).within(pumpPoint, 2).nearest());
    }

    public TemporossObjectSnapshot getRopeCrate()
    {
        return snapshot(() -> Microbot.getRs2TileObjectCache().query().withId(ObjectID.ROPES).within(ropePoint, 2).nearest());
    }

    public TemporossObjectSnapshot getHammerCrate()
    {
        return snapshot(() -> Microbot.getRs2TileObjectCache().query().withId(ObjectID.HAMMERS_40964).within(hammerPoint, 2).nearest());
    }

    public TemporossObjectSnapshot getHarpoonCrate()
    {
        return snapshot(() -> Microbot.getRs2TileObjectCache().query().withId(ObjectID.HARPOONS).within(harpoonPoint, 2).nearest());
    }

    public TemporossObjectSnapshot getMast() {
        // Our side's id only. Both masts exist in the scene (41352 west, 41353 east) and the wrong one
        // was previously excluded by radius alone.
        return snapshot(() -> ourSide(Microbot.getRs2TileObjectCache().query().withId(side.mastId).within(mastPoint, 10).toList()));
    }

    public TemporossObjectSnapshot getBrokenMast() {
        return snapshot(() -> ourSide(Microbot.getRs2TileObjectCache().query().withId(side.brokenMastId).within(mastPoint, 10).toList()));
    }

    /**
     * Radius around the exit NPC that still counts as our side. Our own range/totem sit ~17-25
     * tiles away; the opposite side's are 60+, so this disambiguates without needing the far-field
     * offsets to be right — and they demonstrably are not (see SIDE_ANCHOR_RADIUS usages).
     */
    private static final int SIDE_ANCHOR_RADIUS = 30;

    /**
     * Picks our side's copy of an object: on our half, and of those the one closest to the exit NPC.
     *
     * <p>Deliberately not {@code nearest()} — that resolves against the *player*, so a lookup made
     * while crossing the arena returns the opposite side's object. Doing that once used to be
     * permanent, because the result was cached and then used as the reference for "our side".
     */
    private Rs2TileObjectModel ourSide(List<Rs2TileObjectModel> candidates) {
        return candidates.stream()
                .filter(o -> o != null && isOnOurSide(o.getWorldLocation()))
                .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(exitNpc)))
                .orElse(null);
    }

    public TemporossObjectSnapshot getTotem() {
        // Our side's totem id only. Previously both were queried and the other side's sat 19 tiles
        // away — inside the 30 radius, rejected by a single tile of isOnOurSide margin.
        return snapshot(() -> ourSide(Microbot.getRs2TileObjectCache().query().withId(side.totemId).within(exitNpc, SIDE_ANCHOR_RADIUS).toList()));
    }

    public TemporossObjectSnapshot getBrokenTotem() {
        return snapshot(() -> ourSide(Microbot.getRs2TileObjectCache().query().withId(side.brokenTotemId).within(exitNpc, SIDE_ANCHOR_RADIUS).toList()));
    }

    public TemporossObjectSnapshot getRange()
    {
        return snapshot(() -> ourSide(Microbot.getRs2TileObjectCache().query().withId(ObjectID.SHRINE_41236).within(exitNpc, SIDE_ANCHOR_RADIUS).toList()));
    }

    /**
     * Where the range actually is, falling back to the offset guess only when no shrine resolves.
     * Never cached: a single wrong resolution used to persist for the whole game.
     */
    public WorldPoint getRangeLocation()
    {
        TemporossObjectSnapshot range = getRange();
        return range != null ? range.worldLocation : rangePoint;
    }

    /**
     * Where the totem actually is, for the same reason as {@link #getRangeLocation()}.
     */
    public WorldPoint getTotemLocation()
    {
        TemporossObjectSnapshot totem = getTotem();
        if (totem == null)
        {
            totem = getBrokenTotem();
        }
        return totem != null ? totem.worldLocation : totemPoint;
    }

    public TemporossObjectSnapshot getClosestTether() {
        TemporossObjectSnapshot mast = getMast();
        TemporossObjectSnapshot totem = getTotem();

        if (mast == null) {
            return totem;
        }

        if (totem == null) {
            return mast;
        }

        LocalPoint playerLocal = TemporossScript.cachedPlayerLocal;
        if (playerLocal == null) {
            return mast;
        }

        int mastDist = playerLocal.distanceTo(mast.localLocation);
        int totemDist = playerLocal.distanceTo(totem.localLocation);
        return mastDist <= totemDist ? mast : totem;
    }

    /** Reach of each of our two anchors. Their union covers our side without touching the other. */
    private static final int ANCHOR_RADIUS = 18;
    /** Wider reach for the ship anchor alone, while the totem anchor has not been sighted yet. */
    private static final int LONE_ANCHOR_RADIUS = 25;
    /** A second Forfeit NPC further than this from ours belongs to the other side. */
    public static final int TOTEM_EXIT_MAX_DISTANCE = 25;

    /**
     * Records the totem-side exit NPC once it is seen. Safe against the other side's exits: the
     * distance gate keeps anything 25+ tiles from our ship exit out, and theirs are 40+ away.
     */
    public void setTotemExitNpc(WorldPoint point) {
        if (totemExitNpc == null && point != null
                && point.distanceTo(exitNpc) <= TOTEM_EXIT_MAX_DISTANCE) {
            totemExitNpc = point;
        }
    }

    public WorldPoint getTotemExitNpc() {
        return totemExitNpc;
    }

    /**
     * Is this entity on our half of the arena? Ours means near either of OUR two exit NPCs — the ship
     * one or the totem one.
     *
     * <p>Distance-based on purpose. The real map is fixed and the two sides are mirrors, but the
     * instance assembles its chunks with per-game rotation, so no offset table in raw instance space
     * holds from one game to the next (measured: fishing at exit -17y one game, +16y another, both
     * labeled "west"). Distances between things on our side survive any rotation; offsets do not.
     */
    public boolean isOnOurSide(WorldPoint point) {
        if (point == null) {
            return false;
        }
        if (totemExitNpc == null) {
            return point.distanceTo(exitNpc) <= LONE_ANCHOR_RADIUS;
        }
        return point.distanceTo(exitNpc) <= ANCHOR_RADIUS
                || point.distanceTo(totemExitNpc) <= ANCHOR_RADIUS;
    }

    public String getAllPointsAsString() {
        String sb = "exitNpc=" + exitNpc +
                ", safePoint=" + safePoint +
                ", bucketPoint=" + bucketPoint +
                ", pumpPoint=" + pumpPoint +
                ", ropePoint=" + ropePoint +
                ", hammerPoint=" + hammerPoint +
                ", harpoonPoint=" + harpoonPoint +
                ", mastPoint=" + mastPoint +
                ", totemPoint=" + totemPoint +
                ", rangePoint=" + rangePoint +
                ", spiritPoolPoint=" + spiritPoolPoint;

        return sb;
    }
}
