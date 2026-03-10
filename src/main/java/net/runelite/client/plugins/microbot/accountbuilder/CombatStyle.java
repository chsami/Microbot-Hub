package net.runelite.client.plugins.microbot.accountbuilder;

/** Preferred combat training style for melee tasks. */
public enum CombatStyle {
    ATTACK("Attack (Accurate)"),
    STRENGTH("Strength (Aggressive)"),
    DEFENCE("Defence (Defensive)"),
    CONTROLLED("Controlled");

    private final String displayName;

    CombatStyle(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
