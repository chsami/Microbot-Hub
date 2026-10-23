package net.runelite.client.plugins.microbot.blackjack;

public enum BlackjackTarget
{
    AUTO,
    BANDIT_41,
    BANDIT_56,
    MENAPHITE_THUG;

    public boolean matches(String name, int combatLevel)
    {
        if (name == null)
        {
            return false;
        }
        switch (this)
        {
            case BANDIT_41: return name.equalsIgnoreCase("Bandit") && combatLevel == 41;
            case BANDIT_56: return name.equalsIgnoreCase("Bandit") && combatLevel == 56;
            case MENAPHITE_THUG: return name.equalsIgnoreCase("Menaphite Thug");
            default: return false;
        }
    }

    public int requiredThievingLevel()
    {
        switch (this)
        {
            case BANDIT_56: return 55;
            case MENAPHITE_THUG: return 65;
            default: return 45;
        }
    }

    static BlackjackTarget forLevel(int level)
    {
        return level < 55 ? BANDIT_41 : level < 70 ? BANDIT_56 : MENAPHITE_THUG;
    }
}
