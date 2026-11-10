package net.runelite.client.plugins.microbot.drocolosseumprayers;

/** Colosseum style choices. killWith and warbandFirst use different server words for ranged/mage. */
public enum ColosseumCombatStyle
{
    RANGED("Ranged","ranged","ranger"),
    MELEE("Melee","melee","melee"),
    MAGE("Mage","mage","mager");

    private final String label,killWith,warbandFirst;
    ColosseumCombatStyle(String label,String killWith,String warbandFirst){this.label=label;this.killWith=killWith;this.warbandFirst=warbandFirst;}
    /** Server accepts melee, ranged or mage. */
    public String killWith(){return killWith;}
    /** Server accepts melee, ranger or mager. */
    public String warbandFirst(){return warbandFirst;}
    @Override public String toString(){return label;}
}
