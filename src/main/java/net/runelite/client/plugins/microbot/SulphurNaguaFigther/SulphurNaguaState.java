package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

/**
 * Defines the possible states for the Sulphur Nagua script.
 * Note: This enum seems to be an older or unused version. The active script uses an inner enum.
 */
public enum SulphurNaguaState {

    STARTING,           // The script is initializing.
    FIGHTING,           // The script is in combat.
    CHECK_REQUIREMENTS, // The script is checking for necessary items or stats.
    MAKING_POTIONS,     // The script is creating Moonlight potions.
    DRINKING_POTION,    // The script is drinking a potion.
    BANKING,            // The script is interacting with a bank.
    WALKING_TO_NAGUAS,  // The script is walking to the combat area.
    STOPPED             // The script has been stopped.
}