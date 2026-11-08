package net.runelite.client.plugins.microbot.fourtifysbarrows.config;

public enum TravelMethod
{
    BARROWS_TABLET("Barrows teleport tablet"),
    MORTTON_SCROLL("Mort'ton teleport scroll"),
    ARCEUUS_SPELL("Barrows Teleport (Arceuus)"),
    MORYTANIA_LEGS_3("Morytania legs 3"),
    MORYTANIA_LEGS_4("Morytania legs 4"),
    SHADES_MINIGAME("Shades of Mort'ton");

    private final String label;

    TravelMethod(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
