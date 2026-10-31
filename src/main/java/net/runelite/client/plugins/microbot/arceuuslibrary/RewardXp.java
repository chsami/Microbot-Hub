package net.runelite.client.plugins.microbot.arceuuslibrary;

/**
 * Which experience to claim from the Book of arcane knowledge reward.
 * Magic = 15x Magic level, Runecraft = 5x Runecraft level. AUTO picks the higher payout.
 */
public enum RewardXp
{
    AUTO("Auto (higher XP)"),
    MAGIC("Magic"),
    RUNECRAFT("Runecraft");

    private final String dialogueOption;

    RewardXp(String dialogueOption)
    {
        this.dialogueOption = dialogueOption;
    }

    public String getDialogueOption()
    {
        return dialogueOption;
    }

    @Override
    public String toString()
    {
        return dialogueOption;
    }

    /** Resolves AUTO against the player's levels (ties go to Magic); explicit choices pass through. */
    public RewardXp resolve(int magicLevel, int runecraftLevel)
    {
        if (this != AUTO) return this;
        return 5L * runecraftLevel > 15L * magicLevel ? RUNECRAFT : MAGIC;
    }
}
