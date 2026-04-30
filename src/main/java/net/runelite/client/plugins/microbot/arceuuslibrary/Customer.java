package net.runelite.client.plugins.microbot.arceuuslibrary;

/**
 * The three Arceuus Library customer NPCs. Each exposes a "Help" menu option.
 * IDs verified against the OSRS wiki and used by the upstream Kourend Library
 * plugin's {@code isLibraryCustomer} check (ARCEUUS_LIBRARY_CUSTOMER_2/3/4).
 */
public enum Customer
{
    VILLIA("Villia", 7047),
    GRACKLEBONE("Professor Gracklebone", 7048),
    SAM("Sam", 7049);

    private final String npcName;
    private final int npcId;

    Customer(String npcName, int npcId)
    {
        this.npcName = npcName;
        this.npcId = npcId;
    }

    public String getNpcName() { return npcName; }
    public int getNpcId() { return npcId; }

    public static int[] allIds()
    {
        Customer[] values = values();
        int[] ids = new int[values.length];
        for (int i = 0; i < values.length; i++) ids[i] = values[i].npcId;
        return ids;
    }
}
