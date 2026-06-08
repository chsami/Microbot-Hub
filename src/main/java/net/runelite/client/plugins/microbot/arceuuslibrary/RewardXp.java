package net.runelite.client.plugins.microbot.arceuuslibrary;

/**
 * Which experience to claim from the Book of arcane knowledge reward.
 * Magic = 15× Magic level, Runecraft = 5× Runecraft level.
 */
public enum RewardXp
{
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
}
