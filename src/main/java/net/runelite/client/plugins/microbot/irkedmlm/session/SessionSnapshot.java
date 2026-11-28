package net.runelite.client.plugins.microbot.irkedmlm.session;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import net.runelite.client.plugins.microbot.irkedmlm.SackState;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMStatus;

/**
 * Immutable snapshot of the current Motherlode Mine session state, published once per script tick and
 * read by the overlay. Every field here is something the overlay actually draws — the script keeps its
 * own working state to itself.
 */
@Value
@AllArgsConstructor
public class SessionSnapshot {

    MLMStatus     status;
    MLMMiningSpot miningSpot;

    /** Pay-dirt in the sack, or {@link SackState#UNKNOWN} when it has not been observed. */
    int sackCount;
    int maxSackSize;
    /** True while capacity is still the config hint rather than varbit 5556. */
    boolean capacityAssumed;

    long startTimeMs;

    long totalValueGained;
    int  gainedNuggets;
    int  startXp;
    int  currentXp;

    int runiteCount;
    int adamantiteCount;
    int mithrilCount;
    int goldCount;
    int coalCount;

    /** Current session phase, e.g. "Mining Vein" — never null, defaults to "Idle". */
    String subStateLabel;

    /** When the current {@link #status} was entered (script's own {@code statusEnteredMs}). */
    long statusEnteredMs;

    /**
     * Timestamp of the last Mining XP drop (the script's {@code globalLastXpTime}), or 0 before the
     * first ore. This is the only honest "vein activity" signal MLM offers — veins collapse on a
     * random per-ore roll, so there is no respawn/depletion countdown to display.
     */
    long lastOreMs;

    /** Logged-in time this session; the panel's run time and the base of every rate. */
    @With long activeMs;
    /** Sacks emptied this session. */
    @With int sacksEmptied;
    /** {time, amount} per ore gain, for the sparkline. */
    @With List<long[]> oreCredits;

    /** The original positional form; the later fields start empty and are set with the withers. */
    public SessionSnapshot(MLMStatus status, MLMMiningSpot miningSpot, int sackCount, int maxSackSize,
                           boolean capacityAssumed, long startTimeMs, long totalValueGained, int gainedNuggets,
                           int startXp, int currentXp, int runiteCount, int adamantiteCount, int mithrilCount,
                           int goldCount, int coalCount, String subStateLabel, long statusEnteredMs, long lastOreMs) {
        this(status, miningSpot, sackCount, maxSackSize, capacityAssumed, startTimeMs, totalValueGained,
                gainedNuggets, startXp, currentXp, runiteCount, adamantiteCount, mithrilCount, goldCount,
                coalCount, subStateLabel, statusEnteredMs, lastOreMs, 0L, 0, List.of());
    }

    /** Every ore counted this session. */
    public int totalOres() {
        return runiteCount + adamantiteCount + mithrilCount + goldCount + coalCount;
    }

    /** Fully-initialised empty snapshot, used before the script has started. */
    public static SessionSnapshot empty() {
        return new SessionSnapshot(
                /* status           */ MLMStatus.IDLE,
                /* miningSpot       */ null,
                /* sackCount        */ SackState.UNKNOWN,
                /* maxSackSize      */ SackState.SACK_SIZE,
                /* capacityAssumed  */ true,
                /* startTimeMs      */ System.currentTimeMillis(),
                /* totalValueGained */ 0L,
                /* gainedNuggets    */ 0,
                /* startXp          */ 0,
                /* currentXp        */ 0,
                /* runiteCount      */ 0,
                /* adamantiteCount  */ 0,
                /* mithrilCount     */ 0,
                /* goldCount        */ 0,
                /* coalCount        */ 0,
                /* subStateLabel    */ "Idle",
                /* statusEnteredMs  */ System.currentTimeMillis(),
                /* lastOreMs        */ 0L
        );
    }
}
