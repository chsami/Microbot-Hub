package net.runelite.client.plugins.microbot.eventdismiss;

/**
 * Widget ID constants for lamp interface interactions.
 * Centralized to improve maintainability and protect against game updates.
 * 
 * These IDs correspond to the experience lamp interface (widget group 240).
 * If the game updates these IDs, only this file needs to be modified.
 */
public final class LampWidgetConstants {
    
    // Prevent instantiation
    private LampWidgetConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Main lamp interface widget group
    public static final int LAMP_WIDGET_GROUP = 240;
    
    // Root widget of the lamp interface
    public static final int LAMP_WIDGET_ROOT = 0;
    
    // Confirm button to apply the selected skill
    public static final int LAMP_CONFIRM_BUTTON = 26;
    
    // Skill widget IDs within the lamp interface
    // Combat skills
    public static final int WIDGET_ATTACK = 2;
    public static final int WIDGET_STRENGTH = 3;
    public static final int WIDGET_RANGED = 4;
    public static final int WIDGET_MAGIC = 5;
    public static final int WIDGET_DEFENCE = 6;
    public static final int WIDGET_HITPOINTS = 7;
    public static final int WIDGET_PRAYER = 8;
    
    // Gathering skills
    public static final int WIDGET_MINING = 16;
    public static final int WIDGET_FISHING = 18;
    public static final int WIDGET_WOODCUTTING = 21;
    public static final int WIDGET_FARMING = 15;
    public static final int WIDGET_HUNTER = 24;
    
    // Production skills
    public static final int WIDGET_SMITHING = 17;
    public static final int WIDGET_COOKING = 19;
    public static final int WIDGET_FIREMAKING = 20;
    public static final int WIDGET_CRAFTING = 12;
    public static final int WIDGET_FLETCHING = 22;
    public static final int WIDGET_CONSTRUCTION = 23;
    public static final int WIDGET_HERBLORE = 10;
    
    // Support skills
    public static final int WIDGET_AGILITY = 9;
    public static final int WIDGET_THIEVING = 11;
    public static final int WIDGET_SLAYER = 14;
    public static final int WIDGET_RUNECRAFT = 13;
}