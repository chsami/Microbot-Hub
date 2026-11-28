package net.runelite.client.plugins.microbot.irkedmlm;

/** Cross-session persisted totals + the intended loadout. Serialized via ConfigManager (Gson). */
public class MlmStats {
    public long oresMined;       // total ores banked
    public long nuggets;         // golden nuggets gained, accumulated across sessions
    public long xpGained;        // Mining xp gained across sessions
    public long runtimeMs;       // accumulated runtime
    public long sacksEmptied;    // completed sack empties
    public long lastSavedEpochMs;
    public boolean loadoutUseImcando;   // intended: was Imcando hammer mode on
}
