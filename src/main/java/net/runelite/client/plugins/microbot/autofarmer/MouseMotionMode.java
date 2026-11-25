package net.runelite.client.plugins.microbot.autofarmer;
/** Client-managed motion is the default; legacy tuning is opt-in. */
public enum MouseMotionMode {
    CLIENT_PROFILE("Client profile"),
    LEGACY_SMOOTHING("Legacy smooth");
    private final String label;
    MouseMotionMode(String label) { this.label=label; }
    @Override public String toString() { return label; }
}
