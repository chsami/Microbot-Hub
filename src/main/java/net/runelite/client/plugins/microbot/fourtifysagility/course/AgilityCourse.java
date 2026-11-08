package net.runelite.client.plugins.microbot.fourtifysagility.course;

public enum AgilityCourse
{
    GNOME("Gnome"),
    DRAYNOR("Draynor"),
    AL_KHARID("Al Kharid"),
    AGILITY_PYRAMID("Agility Pyramid"),
    BRIMHAVEN("Brimhaven Agility Arena"),
    VARROCK("Varrock"),
    CANIFIS("Canifis"),
    FALADOR("Falador"),
    COLOSSAL_WYRM("Colossal Wyrm"),
    WILDERNESS("Wilderness"),
    SEERS_VILLAGE("Seers Village"),
    POLLNIVNEACH("Pollnivneach"),
    PRIFDDINAS("Prifddinas"),
    RELLEKKA("Rellekka"),
    ARDOUGNE("Ardougne"),
    APE_ATOLL("Ape Atoll");

    private final String displayName;

    AgilityCourse(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
