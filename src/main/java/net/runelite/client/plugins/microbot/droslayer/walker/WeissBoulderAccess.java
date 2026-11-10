package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** The maintained crossing is needed by overland arrivals; icy basalt lands on the patch side. */
final class WeissBoulderAccess {
    static final WorldPoint EAST=new WorldPoint(2852,3936,0);
    static final WorldPoint WEST=new WorldPoint(2850,3936,0);

    static boolean isLink(Transport transport) {
        return transport!=null&&transport.getType()==TransportType.TRANSPORT
            &&transport.getObjectId()==ObjectID1.MY2ARM_HERBPATCH_ACCESS&&"Cross".equals(transport.getAction())
            &&(EAST.equals(transport.getOrigin())&&WEST.equals(transport.getDestination())
                ||WEST.equals(transport.getOrigin())&&EAST.equals(transport.getDestination()));
    }

    private WeissBoulderAccess() { }
}
