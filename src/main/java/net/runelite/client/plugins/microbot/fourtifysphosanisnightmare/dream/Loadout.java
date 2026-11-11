package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

/** The equipment worn for a target: the melee or magic set, with the weapon swapped for some targets. */
public enum Loadout
{
    MELEE(false),
    SPEC(false),
    HUSK(false),
    PARASITE(false),
    MAGIC(true),
    SLEEPWALKER(true),
    /**
     * The magic set's weapon alone, on its longrange style, for targets a held player cannot close in on: sleepwalkers
     * while husks hold the player, and a husk risen under her.
     */
    LONGRANGE(true);

    private final boolean magic;

    Loadout(boolean magic)
    {
        this.magic = magic;
    }

    /** Built on the magic set rather than the melee set. */
    public boolean isMagic()
    {
        return magic;
    }
}
