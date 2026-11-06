package net.runelite.client.plugins.microbot.fourtifysagility.config;

/** A specific item to buy with arena vouchers after leaving. */
public enum BrimhavenVoucherReward
{
    NONE("None", 0, 0, false),
    TOADFLAX("Toadflax", 3, 2999, true),
    SNAPDRAGON("Snapdragon", 10, 3001, true),
    AMYLASE_PACK("Amylase pack", 60, 12641, false),
    PIRATES_HOOK("Pirate's hook", 800, 2997, false);

    private final String label;
    private final int price;
    private final int itemId;
    private final boolean stackable;

    BrimhavenVoucherReward(String label, int price, int itemId, boolean stackable)
    {
        this.label = label;
        this.price = price;
        this.itemId = itemId;
        this.stackable = stackable;
    }

    public int price()
    {
        return price;
    }

    public int itemId()
    {
        return itemId;
    }

    public boolean stackable()
    {
        return stackable;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
