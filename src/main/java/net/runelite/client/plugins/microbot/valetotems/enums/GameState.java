package net.runelite.client.plugins.microbot.valetotems.enums;

/**
 * Enum representing the different states the bot can be in during the Vale Totems minigame
 */
public enum GameState {
    IDLE("Bot is idle, waiting for instructions"),
    BANKING("Withdrawing items from bank"),
    NAVIGATING_TO_TOTEM("Walking to the next totem location"),
    BUILDING_TOTEM("Building the totem base"),
    IDENTIFYING_ANIMALS("Scanning for spirit animals around totem"),
    CARVING_TOTEM("Carving identified animals into the totem"),
    FLETCHING("Fletching"),
    DECORATING_TOTEM("Decorating the totem with fletched bows"),
    COLLECTING_REWARDS("Claiming offerings from the pile"),
    RETURNING_TO_BANK("Walking back to bank after completing all totems"),
    ERROR("An error occurred, bot needs attention"),
    STOPPING("Critical error occurred, stopping bot gracefully"),
    COMPLETED("All totems completed, ready for next round");

    private final String description;

    GameState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if the current state involves totem construction activities
     * @return true if in any totem-related state
     */
    public boolean isTotemActivity() {
        return this == BUILDING_TOTEM || 
               this == IDENTIFYING_ANIMALS || 
               this == CARVING_TOTEM || 
               this == FLETCHING || 
               this == DECORATING_TOTEM;
    }

    /**
     * Check if the current state involves movement
     * @return true if in any movement-related state
     */
    public boolean isMoving() {
        return this == NAVIGATING_TO_TOTEM || 
               this == RETURNING_TO_BANK;
    }

    /**
     * Check if the bot is actively working
     * @return true if not idle, error, stopping, or completed
     */
    public boolean isActive() {
        return this != IDLE && this != ERROR && this != STOPPING && this != COMPLETED;
    }

    /**
     * Check if the bot is in a stopping state
     * @return true if in STOPPING state
     */
    public boolean isStopping() {
        return this == STOPPING;
    }
} 