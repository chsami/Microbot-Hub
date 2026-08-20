package net.runelite.client.plugins.microbot.valetotems.enums;

/**
 * Enum representing the spirit animals that can be carved into totems
 * Each animal has an associated NPC ID and keyboard key for carving.
 * Declaration order matters: ordinal + 1 is the game's animal id, used by the
 * carve dialog keys (1-5) and the per-site animal/segment varbits.
 */
public enum SpiritAnimal {
    BUFFALO(15, '1', 14589, "Buffalo spirit"),
    JAGUAR(16, '2', 14590, "Jaguar spirit"),
    EAGLE(17, '3', 14591, "Eagle/Griffin spirit"),
    SNAKE(18, '4', 14592, "Snake spirit"),
    SCORPION(19, '5', 14593, "Scorpion spirit");

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

    /**
     * Get spirit animal by the game's animal id (1-5), as used by the per-site
     * ent_totems varbits (animal_1..3 and the low/mid/top carved segments)
     * @param value the varbit animal value
     * @return corresponding SpiritAnimal enum, or null if out of range
     */
    public static SpiritAnimal getByVarbitValue(int value) {
        if (value < 1 || value > values().length) {
            return null;
        }
        return values()[value - 1];
    }
} 