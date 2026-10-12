package net.runelite.client.plugins.microbot.sailing.features.trials;

import net.runelite.api.Client;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class BoatLocation {
    public static WorldPoint fromLocal(Client client, LocalPoint local) {
        if (client == null || local == null || client.getLocalPlayer() == null) {
            return null;
        }

        WorldView wv = client.getLocalPlayer().getWorldView();
        if (wv == null || client.getTopLevelWorldView() == null) return null;
        int wvid = wv.getId();
        boolean isOnBoat = wvid != -1;
        if (isOnBoat) {
            WorldEntity we = client.getTopLevelWorldView().worldEntities().byIndex(wvid);
            if (we == null || we.getLocalLocation() == null) return null;
            return WorldPoint.fromLocalInstance(client, we.getLocalLocation());
        }
        return WorldPoint.fromLocalInstance(client, local);
    }
}
