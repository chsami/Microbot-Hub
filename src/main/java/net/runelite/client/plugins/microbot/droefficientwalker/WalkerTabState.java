package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.gameval.VarClientID;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

/** Read the actual selected panel before the engine activates a teleport item. */
public final class WalkerTabState {
    private WalkerTabState() { }

    public static boolean isCurrentTab(InterfaceTab tab) {
        if (tab == null || Microbot.getClient() == null || Microbot.getClientThread() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (tab.getVarcIntIndex() < 0) return Rs2Tab.isCurrentTab(tab);
            // The existing item helpers also consult this cache. Reconcile it from the
            // actual panel so they cannot wait for a missed same-tab change event.
            Rs2Tab.onVarClientIntChanged(new VarClientIntChanged(VarClientID.TOPLEVEL_PANEL));
            return Microbot.getClient().getVarcIntValue(VarClientID.TOPLEVEL_PANEL) == tab.getVarcIntIndex();
        })
                .orElse(false);
    }
}
