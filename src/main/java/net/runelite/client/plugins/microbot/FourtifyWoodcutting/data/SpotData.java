package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SpotData {
    private SpotData() {
    }

    static List<SpotRow> all() {
        List<SpotRow> all = new ArrayList<>();
        Collections.addAll(all, SpotDataA.values());
        Collections.addAll(all, SpotDataB.values());
        Collections.addAll(all, SpotDataC.values());
        Collections.addAll(all, SpotDataD.values());
        return all;
    }
}
