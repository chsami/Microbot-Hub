package net.runelite.client.plugins.microbot.sailing.features.salvaging;

/**
 * Widget ids for the sailing cargo-hold interface not exposed on {@link net.runelite.api.gameval.InterfaceID}.
 */
public final class CargoHoldInterfaceWidgets {

    private CargoHoldInterfaceWidgets() {
    }

    /**
     * Occupied-slot count text while the hold is open ({@code client.getWidget(943, 4)}). In practice this child often
     * shows only {@code X} (occupied slots), not a full {@code X / N} line; we still parse the first run of digits from
     * {@link net.runelite.api.widgets.Widget#getText()} so either shape works.
     */
    public static final int CARGO_HOLD_OCCUPIED_TEXT_GROUP = 943;

    public static final int CARGO_HOLD_OCCUPIED_TEXT_CHILD = 4;
}
