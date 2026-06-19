package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.api.coords.WorldPoint;

/**
 * The three Arceuus Library customer NPCs. Each exposes a "Help" menu option.
 * IDs verified against the OSRS wiki and used by the upstream Kourend Library
 * plugin's {@code isLibraryCustomer} check (ARCEUUS_LIBRARY_CUSTOMER_2/3/4).
 *
 * The {@code roamCenter} is a tile near each customer's wander box — walking
 * within ~4 tiles reliably brings them into the player's scene cache. Used
 * when the active customer (per upstream) is the only un-attempted one but
 * isn't currently visible to {@code Rs2NpcCache}.
 */
public enum Customer
{
    VILLIA("Villia", 7047, new WorldPoint(1625, 3815, 0)),
    GRACKLEBONE("Professor Gracklebone", 7048, new WorldPoint(1625, 3800, 0)),
    SAM("Sam", 7049, new WorldPoint(1638, 3800, 0));

    private final String npcName;
    private final int npcId;
    private final WorldPoint roamCenter;

    Customer(String npcName, int npcId, WorldPoint roamCenter)
    {
        this.npcName = npcName;
        this.npcId = npcId;
        this.roamCenter = roamCenter;
    }

    public String getNpcName() { return npcName; }
    public int getNpcId() { return npcId; }
    public WorldPoint getRoamCenter() { return roamCenter; }

    public static int[] allIds()
    {
        Customer[] values = values();
        int[] ids = new int[values.length];
        for (int i = 0; i < values.length; i++) ids[i] = values[i].npcId;
        return ids;
    }

    public static Customer byId(int npcId)
    {
        for (Customer c : values()) if (c.npcId == npcId) return c;
        return null;
    }
}
