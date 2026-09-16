package net.runelite.client.plugins.microbot.pestcontrol;

public enum PestControlMeleeStyle {
    STAB("Stab"),
    SLASH("Slash"),
    CRUSH("Crush");

    private final String attackOption;

    PestControlMeleeStyle(String attackOption) {
        this.attackOption = attackOption;
    }

    String attackOption() {
        return attackOption;
    }
}
