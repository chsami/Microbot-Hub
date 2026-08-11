package net.runelite.client.plugins.custom.zulrah.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.custom.zulrah.constants.StandLocation;
import net.runelite.client.plugins.custom.zulrah.constants.ZulrahType;
import net.runelite.client.plugins.custom.zulrah.rotationutils.ZulrahPhase;

/**
 * Applies a pending phase change (set by the plugin's animation events): sets the stand tile, the
 * melee-dodge flag, and resets the gear-swap counter. Prayer/gear/positioning are then handled by
 * the later actions this same tick.
 */
@Slf4j
public class SyncPhaseAction implements ZulrahAction {

    @Override
    public int order() {
        return 400;
    }

    @Override
    public String key() {
        return "sync-phase";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        return state.context().isPhaseChanged();
    }

    @Override
    public Object execute(ZulrahState state) {
        FightContext ctx = state.context();
        ZulrahPhase phase = ctx.getPhase();
        ctx.setPhaseChanged(false);
        if (phase == null) {
            return null;
        }
        log.info("Changing zulrah phase. Next phase is {}", phase.getZulrahNpc().getType().getName());
        ctx.setStandLocation(phase.getAttributes().getStandLocation().toWorldPoint());
        boolean melee = phase.getZulrahNpc().getType() == ZulrahType.MELEE
                && phase.getAttributes().getStandLocation() == StandLocation.NORTHEAST_NORTH;
        ctx.setMeleeDodgePhase(melee);
        if (melee) {
            ctx.setMeleeDodgeAtNorth(true);
        }
        ctx.setGearSwapAttempts(0);
        // Re-anchor the jad starting overhead next time we hit a jad phase.
        ctx.setJadStartPrayerSet(false);
        return phase;
    }
}
