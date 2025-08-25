package net.runelite.client.plugins.microbot.valetotems.enums;

/**
 * Enum containing all game object IDs for the Vale Totems minigame
 * Based on the provided documentation
 * Updated to include search terms for string-based object identification
 */
public enum GameObjectId {
    // Banking
    BANK_BOOTH(57330, "bank", "Bank booth for depositing/withdrawing items"),
    
    // Totem construction states
    TOTEM_SITE(57064, "totem", "Empty site for totem construction - Action: Build"),
    EMPTY_TOTEM(57080, "totem", "Built totem ready for carving"),
    TOTEM_READY_FOR_DECORATION(57081, "totem", "Totem ready for decoration - Action: Decorate"),
    
    // Ent trails (optional for extra points)
    ENT_TRAIL_1(57116, "ent", "Ent trail for bonus points"),
    ENT_TRAIL_2(57115, "ent", "Ent trail for bonus points"),
    
    // Offerings (rewards) - different states based on reward count
    OFFERINGS_MANY(57098, "offering", "Offerings pile with many rewards - Action: Claim"),
    OFFERINGS_SOME(57096, "offering", "Offerings pile with some rewards - Action: Claim"),
    OFFERINGS_EMPTY(57095, "offering", "Empty offerings pile - no rewards to collect");

    private final int id;
    private final String searchTerm;
    private final String description;

    GameObjectId(int id, String searchTerm, String description) {
        this.id = id;
        this.searchTerm = searchTerm;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get game object by ID
     * @param id the object ID to search for
     * @return corresponding GameObjectId enum, or null if not found
     */
    public static GameObjectId getById(int id) {
        for (GameObjectId obj : values()) {
            if (obj.getId() == id) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Check if the given ID represents an offerings pile
     * @param id the object ID to check
     * @return true if it's any type of offerings pile
     */
    public static boolean isOfferingsPile(int id) {
        return id == OFFERINGS_MANY.getId() || 
               id == OFFERINGS_SOME.getId() || 
               id == OFFERINGS_EMPTY.getId();
    }

    /**
     * Check if the offerings pile has claimable rewards
     * @param id the object ID to check
     * @return true if offerings can be claimed
     */
    public static boolean hasClaimableOfferings(int id) {
        return id == OFFERINGS_MANY.getId() || id == OFFERINGS_SOME.getId();
    }

    /**
     * Check if the given ID represents an ent trail
     * @param id the object ID to check
     * @return true if it's an ent trail
     */
    public static boolean isEntTrail(int id) {
        return id == ENT_TRAIL_1.getId() || id == ENT_TRAIL_2.getId();
    }

    /**
     * Check if a search term relates to offerings
     * @param searchTerm the search term to check
     * @return true if it's an offerings-related term
     */
    public static boolean isOfferingsSearchTerm(String searchTerm) {
        return searchTerm != null && searchTerm.toLowerCase().contains("offering");
    }

    /**
     * Check if a search term relates to totems
     * @param searchTerm the search term to check
     * @return true if it's a totem-related term
     */
    public static boolean isTotemSearchTerm(String searchTerm) {
        return searchTerm != null && searchTerm.toLowerCase().contains("totem");
    }

    /**
     * Check if a search term relates to ent trails
     * @param searchTerm the search term to check
     * @return true if it's an ent trail-related term
     */
    public static boolean isEntTrailSearchTerm(String searchTerm) {
        return searchTerm != null && searchTerm.toLowerCase().contains("ent");
    }
} 