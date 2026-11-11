package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

/** Her form, read from her NPC ID. */
public enum Stage
{
    ASLEEP,
    WAKING,
    SHIELD,
    TOTEMS,
    BLAST,
    DESPERATION,
    DYING;

    public static Stage fromNpcId(int id)
    {
        if (id == DreamData.INITIAL)
        {
            return WAKING;
        }
        if (DreamData.SHIELD.contains(id))
        {
            return SHIELD;
        }
        if (DreamData.TOTEM_PHASE.contains(id))
        {
            return TOTEMS;
        }
        if (id == DreamData.BLAST)
        {
            return BLAST;
        }
        if (id == DreamData.DESPERATION)
        {
            return DESPERATION;
        }
        if (id == DreamData.DYING)
        {
            return DYING;
        }
        if (id == DreamData.ASLEEP)
        {
            return ASLEEP;
        }
        return null;
    }
}
