package net.runelite.client.plugins.custom.zulrah.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.custom.zulrah.rotationutils.ZulrahPhase;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Validates prayer against the current phase every tick and corrects mismatches. Only ENABLES the
 * wanted overhead (overheads are mutually exclusive in-game, so the other is auto-disabled);
 * toggling both explicitly caused the flip-flop bug. Leaves the jad phase's overhead to its flick.
 */
@Slf4j
public class EnforcePrayersAction implements ZulrahAction {

    @Override
    public int order() {
        return 500;
    }

    @Override
    public String key() {
        return "enforce-prayers";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        return state.context().getPhase() != null;
    }

    @Override
    public Object execute(ZulrahState state) {
        ZulrahPhase phase = state.context().getPhase();

        // Offensive prayer: keep the correct one up (magic vs the range form).
        Rs2PrayerEnum offensive = ZulrahHelpers.attackWithMagic(phase)
                ? Rs2Prayer.getBestMagePrayer()
                : Rs2Prayer.getBestRangePrayer();
        if (offensive != null && !Rs2Prayer.isPrayerActive(offensive)) {
            Rs2Prayer.toggle(offensive, true);
        }

        // The jad phase flicks its own overhead per attack (handleZulrahAttack). But we must ANCHOR
        // the correct starting overhead once on entry — otherwise it inherits the previous phase's
        // prayer and the whole alternation is one step out of phase (every attack lands). After that
        // one anchor, leave the overhead to the flick.
        if (phase.getZulrahNpc().isJad()) {
            log.info("[jad phase]");
            if (!state.context().isJadStartPrayerSet()) {
                enforceOverhead(phase.getAttributes().getPrayer());
                state.context().setJadStartPrayerSet(true);
            }
            return "jad-anchored";
        }
        enforceOverhead(phase.getAttributes().getPrayer());
        return "enforced";
    }

    private void enforceOverhead(Rs2PrayerEnum wanted) {
        if (wanted != null) {
            if (!Rs2Prayer.isPrayerActive(wanted)) {
                log.info("[prayer] enabling {}", wanted);
                Rs2Prayer.toggle(wanted, true);
            }
            return;
        }
        // No overhead this phase (e.g. melee): turn off whichever protection prayer is active.
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
        }
    }
}
