package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config;

/** When the special attack weapon is used. */
public enum SpecTiming
{
    FINAL_PHASE("Final phase"),
    ANY_PHASE("Any phase");

    private final String label;

    SpecTiming(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
