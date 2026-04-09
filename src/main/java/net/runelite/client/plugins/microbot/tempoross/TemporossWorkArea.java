package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.NullObjectID;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;


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

    public TemporossWorkArea(WorldPoint exitNpc, boolean isWest)
    {
        this.exitNpc = exitNpc;
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

    public Rs2TileObjectModel getBucketCrate()
    {
        return Microbot.getRs2TileObjectCache().query().withId(ObjectID.BUCKETS).within(bucketPoint, 2).nearest();
    }

    public Rs2TileObjectModel getPump()
    {
        return Microbot.getRs2TileObjectCache().query().withId(ObjectID.WATER_PUMP_41000).within(pumpPoint, 2).nearest();
    }

    public Rs2TileObjectModel getRopeCrate()
    {
        return Microbot.getRs2TileObjectCache().query().withId(ObjectID.ROPES).within(ropePoint, 2).nearest();
    }

    public Rs2TileObjectModel getHammerCrate()
    {
        return Microbot.getRs2TileObjectCache().query().withId(ObjectID.HAMMERS_40964).within(hammerPoint, 2).nearest();
    }

    public Rs2TileObjectModel getHarpoonCrate()
    {
        return Microbot.getRs2TileObjectCache().query().withId(ObjectID.HARPOONS).within(harpoonPoint, 2).nearest();
    }

    public Rs2TileObjectModel getMast() {
    Rs2TileObjectModel mast = Microbot.getRs2TileObjectCache().query().withIds(NullObjectID.NULL_41352, NullObjectID.NULL_41353).within(mastPoint, 2).nearest();
    return mast;
}

    public Rs2TileObjectModel getBrokenMast() {
    return Microbot.getRs2TileObjectCache().query().withIds(ObjectID.DAMAGED_MAST_40996, ObjectID.DAMAGED_MAST_40997).within(mastPoint, 2).nearest();
    }

    public Rs2TileObjectModel getTotem() {
    return Microbot.getRs2TileObjectCache().query().withIds(NullObjectID.NULL_41355, NullObjectID.NULL_41354).within(totemPoint, 2).nearest();
}

    public Rs2TileObjectModel getBrokenTotem() {
    return Microbot.getRs2TileObjectCache().query().withIds(ObjectID.DAMAGED_TOTEM_POLE, ObjectID.DAMAGED_TOTEM_POLE_41011).within(totemPoint, 2).nearest();
    }

    public Rs2TileObjectModel getRange()
    {
        return Microbot.getRs2TileObjectCache().query().withId(ObjectID.SHRINE_41236).within(rangePoint, 2).nearest();
    }

    public Rs2TileObjectModel getClosestTether() {
    Rs2TileObjectModel mast = getMast();
    Rs2TileObjectModel totem = getTotem();

    if (mast == null) {
        return totem;
    }

    if (totem == null) {
        return mast;
    }

    Rs2WorldPoint mastLocation = new Rs2WorldPoint(mast.getWorldLocation());
    Rs2WorldPoint totemLocation = new Rs2WorldPoint(totem.getWorldLocation());
    Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());

    return mastLocation.distanceToPath(playerLocation.getWorldPoint()) <
            totemLocation.distanceToPath(playerLocation.getWorldPoint()) ? mast : totem;
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
