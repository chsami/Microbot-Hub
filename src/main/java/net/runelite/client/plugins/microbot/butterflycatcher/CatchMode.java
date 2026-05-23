package net.runelite.client.plugins.microbot.butterflycatcher;

/**
 * CatchMode
 *
 * Controls whether the plugin catches bare-handed (XP only) or uses a
 * butterfly net / magic butterfly net.
 *
 * Banking has been intentionally removed — the script runs indefinitely
 * without ever needing to visit a bank.
 */
public enum CatchMode {

    /**
     * Catch the butterfly/moth bare-handed.
     * The creature is instantly released on catch, so nothing enters the
     * inventory. Requires Hunter level = netLevelRequired + 10.
     */
    BAREHANDED,

    /**
     * Catch using a butterfly net (or magic butterfly net).
     * The net must be equipped before the script starts — the script will
     * verify this on startup and stop with a message if not equipped.
     * Requires Hunter level = netLevelRequired (the lower threshold).
     */
    BUTTERFLY_NET;

    @Override
    public String toString() {
        switch (this) {
            case BAREHANDED:    return "Barehanded";
            case BUTTERFLY_NET: return "Butterfly Net";
            default:            return name();
        }
    }
}
