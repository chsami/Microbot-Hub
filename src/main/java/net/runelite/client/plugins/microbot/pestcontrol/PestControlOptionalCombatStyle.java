package net.runelite.client.plugins.microbot.pestcontrol;

public enum PestControlOptionalCombatStyle {
    DISABLED(null),
    RANGED(PestControlCombatStyle.RANGED),
    MELEE(PestControlCombatStyle.MELEE),
    MAGIC(PestControlCombatStyle.MAGIC);

    private final PestControlCombatStyle combatStyle;

    PestControlOptionalCombatStyle(PestControlCombatStyle combatStyle) {
        this.combatStyle = combatStyle;
    }

    PestControlCombatStyle combatStyle() {
        return combatStyle;
    }
}
