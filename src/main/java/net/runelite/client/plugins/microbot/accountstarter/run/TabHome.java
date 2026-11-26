package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Random;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

public final class TabHome {
    private long since;
    private long waitMs;

    public void tick(boolean free, Random random) {
        if (!free || Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY) {
            since = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (since == 0) {
            since = now;
            waitMs = delayMs(random.nextDouble());
            return;
        }
        if (now - since < waitMs) return;
        since = 0;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
    }

    static long delayMs(double roll) {
        return 800 + Math.round(roll * roll * 6000);
    }
}
