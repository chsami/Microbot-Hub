package net.runelite.client.plugins.microbot.valetotems.enums;

/**
 * Enum representing the spirit animals that can be carved into totems
 * Each animal has an associated NPC ID and keyboard key for carving
 */
public enum SpiritAnimal {
    BUFFALO(14, '1', 14589, "Buffalo spirit"),
    JAGUAR(15, '2', 14590, "Jaguar spirit"),
    EAGLE(16, '3', 14591, "Eagle/Griffin spirit"),
    SNAKE(17, '4', 14592, "Snake spirit"),
    SCORPION(18, '5', 14593, "Scorpion spirit");

    private final int widgetChildId;
    private final char keyNumber;
    private final int npcId;
    private final String description;

    SpiritAnimal(int widgetChildId, char keyNumber, int npcId, String description) {
        this.widgetChildId = widgetChildId;
        this.keyNumber = keyNumber;
        this.npcId = npcId;
        this.description = description;
    }

    public int getWidgetChildId() {
        return widgetChildId;
    }

    public char getKeyNumber() {
        return keyNumber;
    }

    public int getNpcId() {
        return npcId;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get spirit animal by NPC ID
     * @param npcId the NPC ID to search for
     * @return corresponding SpiritAnimal enum, or null if not found
     */
    public static SpiritAnimal getByNpcId(int npcId) {
        for (SpiritAnimal animal : values()) {
            if (animal.getNpcId() == npcId) {
                return animal;
            }
        }
        return null;
    }

    /**
     * Get spirit animal by key number
     * @param keyNumber the key number (1-5) to search for
     * @return corresponding SpiritAnimal enum, or null if not found
     */    
    public static SpiritAnimal getByKeyNumber(char keyNumber) {
        for (SpiritAnimal animal : values()) {
            if (animal.getKeyNumber() == keyNumber) {
                return animal;
            }
        }
        return null;
    }

    /**
     * Check if the given NPC ID represents a spirit animal
     * @param npcId the NPC ID to check
     * @return true if it's a spirit animal
     */
    public static boolean isSpiritAnimal(int npcId) {
        return getByNpcId(npcId) != null;
    }
} 