package net.runelite.client.plugins.microbot.droslayer.walker.support.poh;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.droslayer.walker.MaxPohSupport;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Legacy Nexus calls share the walker's observed-house receipts and POH timing. */
final class NexusActions {
    private NexusActions() { }
    public static boolean usePortalNexus(NexusPortal portal) {
        return MaxPohSupport.executeFacility(portal);
    }
}
