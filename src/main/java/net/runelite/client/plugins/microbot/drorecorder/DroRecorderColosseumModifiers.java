package net.runelite.client.plugins.microbot.drorecorder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.VarbitID;

/** Colosseum modifier client varbits: the chosen modifier, each modifier's tier, and Doom points. */
final class DroRecorderColosseumModifiers
{
    private static final Map<Integer, String> VARBITS;
    static
    {
        Map<Integer, String> v = new LinkedHashMap<>();
        v.put(VarbitID.COLOSSEUM_SELECTED_MODIFIER, "selected");
        v.put(VarbitID.COLOSSEUM_MODIFIER_BLASPHEMY_STACKS_CLIENT, "blasphemy");
        v.put(VarbitID.COLOSSEUM_MODIFIER_TOXICITY_STACKS_CLIENT, "toxicity");
        v.put(VarbitID.COLOSSEUM_MODIFIER_REENTRY_STACKS_CLIENT, "reentry");
        v.put(VarbitID.COLOSSEUM_MODIFIER_MYOPIA_STACKS_CLIENT, "myopia");
        v.put(VarbitID.COLOSSEUM_MODIFIER_FRAILTY_STACKS_CLIENT, "frailty");
        v.put(VarbitID.COLOSSEUM_MODIFIER_SOLARFLARE_STACKS_CLIENT, "solarflare");
        v.put(VarbitID.COLOSSEUM_MODIFIER_RELENTLESS_STACKS_CLIENT, "relentless");
        v.put(VarbitID.COLOSSEUM_MODIFIER_VOLATILITY_STACKS_CLIENT, "volatility");
        v.put(VarbitID.COLOSSEUM_MODIFIER_MANTIMAYHEM_STACKS_CLIENT, "mantimayhem");
        v.put(VarbitID.COLOSSEUM_MODIFIER_DOOM_STACKS_CLIENT, "doom");
        v.put(VarbitID.COLOSSEUM_DOOM_STACKS_CLIENT, "doomPoints");
        VARBITS = Collections.unmodifiableMap(v);
    }
    private static final Pattern WAVE = Pattern.compile("^Wave: (\\d+)$");

    private DroRecorderColosseumModifiers() { }

    static String name(int varbitId) { return VARBITS.get(varbitId); }

    /** Every tracked value, in a stable order; names are the modifier, values are raw varbit values. */
    static Map<String, Integer> snapshot(IntUnaryOperator varbit)
    {
        Map<String, Integer> result = new LinkedHashMap<>();
        VARBITS.forEach((id, name) -> result.put(name, varbit.applyAsInt(id)));
        return result;
    }

    /** Outside the Colosseum all values are zero; such snapshots carry nothing. */
    static boolean any(Map<String, Integer> snapshot)
    {
        return snapshot.values().stream().anyMatch(value -> value != 0);
    }

    /** The wave number of a tag-free "Wave: N" game message, or -1. */
    static int wave(String plainMessage)
    {
        if (plainMessage == null) { return -1; }
        Matcher m = WAVE.matcher(plainMessage.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }
}
