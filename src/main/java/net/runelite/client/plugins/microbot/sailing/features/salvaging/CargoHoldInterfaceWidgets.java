package net.runelite.client.plugins.microbot.sailing.features.salvaging;

/**
 * Widget ids for the sailing cargo-hold interface not exposed on {@link net.runelite.api.gameval.InterfaceID}.
 */
public final class CargoHoldInterfaceWidgets {

    private CargoHoldInterfaceWidgets() {
    }

    /**
     * Occupied-line text while the hold is open ({@code client.getWidget(943, 4)}). The first number in {@link net.runelite.api.widgets.Widget#getText()}
     * is treated as occupied slots (e.g. {@code 160} or {@code 160 / 160}).
     */
    public static final int CARGO_HOLD_OCCUPIED_TEXT_GROUP = 943;

    public static final int CARGO_HOLD_OCCUPIED_TEXT_CHILD = 4;
}
