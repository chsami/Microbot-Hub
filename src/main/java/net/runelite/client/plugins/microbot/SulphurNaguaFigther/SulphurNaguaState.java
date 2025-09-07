package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

/**
 * Defines the possible states for the Sulphur Nagua script.
 * Note: This enum seems to be an older or unused version. The active script uses an inner enum.
 */
public enum SulphurNaguaState {

    IDLE,

    BANKING,
    WALKING_TO_BANK,
    WALKING_TO_PREP,
    PREPARATION,
    PICKUP, // <--- NEU
    WALKING_TO_FIGHT,
    FIGHTING
}
