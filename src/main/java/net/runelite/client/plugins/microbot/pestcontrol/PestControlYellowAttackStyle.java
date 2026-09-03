package net.runelite.client.plugins.microbot.pestcontrol;

public enum PestControlYellowAttackStyle {
    STAB(PestControlMeleeStyle.STAB, "Stab"),
    SLASH(PestControlMeleeStyle.SLASH, "Slash");

    private final PestControlMeleeStyle meleeStyle;
    private final String label;

    PestControlYellowAttackStyle(PestControlMeleeStyle meleeStyle, String label) {
        this.meleeStyle = meleeStyle;
        this.label = label;
    }

    PestControlMeleeStyle meleeStyle() {
        return meleeStyle;
    }

    @Override
    public String toString() {
        return label;
    }
}
