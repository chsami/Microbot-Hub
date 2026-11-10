package net.runelite.client.plugins.microbot.droinfernoprayers;

/** The two assist modes of the Prayers plugin; full automatic play is deliberately not offered. */
public enum ArenaAssistMode {
    PRAYERS_ONLY("Prayers only"),
    AUTO_NO_MOVEMENT("Auto no movement");

    private final String label;
    ArenaAssistMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
