package net.runelite.client.plugins.microbot.fourtifysagility.config;

public enum PyramidTopTurnIn
{
    EVERY_LAP("Every lap"),
    FULL_INVENTORY("Full inventory");

    private final String displayName;

    PyramidTopTurnIn(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
