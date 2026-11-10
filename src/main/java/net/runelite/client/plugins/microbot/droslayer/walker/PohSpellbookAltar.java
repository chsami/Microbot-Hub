package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Arrays;
import java.util.Set;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;

/**
 * Which POH altar menu option switches to a target spellbook. The occult altar offers each book
 * by name; the single-book Ancient, Lunar and Dark altars only offer "Venerate", which toggles
 * between Standard and their own book.
 */
public final class PohSpellbookAltar
{
    /** Base ids plus the occult altar's per-spellbook variants, so either form in the scene matches. */
    public static final Set<Integer> IDS = Set.of(
        ObjectID.POH_ALTAR_ANCIENT, ObjectID.POH_ALTAR_LUNAR, ObjectID.POH_ALTAR_DARK, ObjectID.POH_ALTAR_OCCULT,
        ObjectID.POH_ALTAR_OCCULT_STANDARD, ObjectID.POH_ALTAR_OCCULT_ANCIENT,
        ObjectID.POH_ALTAR_OCCULT_LUNAR, ObjectID.POH_ALTAR_OCCULT_ARCEUUS);

    private PohSpellbookAltar() { }

    /** The option to click, or null when this altar cannot reach the target from the current book. */
    public static String action(int objectId, String[] actions, Rs2Spellbook current, Rs2Spellbook target)
    {
        if (actions == null || target == null || target == current) return null;
        String named = WalkerSpellbookSupport.choice(target);
        if (Arrays.asList(actions).contains(named)) return named;
        Rs2Spellbook own = singleBook(objectId);
        if (own == null || !Arrays.asList(actions).contains("Venerate")) return null;
        boolean toOwn = target == own && current == Rs2Spellbook.MODERN;
        boolean toStandard = target == Rs2Spellbook.MODERN && current == own;
        return toOwn || toStandard ? "Venerate" : null;
    }

    private static Rs2Spellbook singleBook(int objectId)
    {
        if (objectId == ObjectID.POH_ALTAR_ANCIENT) return Rs2Spellbook.ANCIENT;
        if (objectId == ObjectID.POH_ALTAR_LUNAR) return Rs2Spellbook.LUNAR;
        if (objectId == ObjectID.POH_ALTAR_DARK) return Rs2Spellbook.ARCEUUS;
        return null;
    }
}
