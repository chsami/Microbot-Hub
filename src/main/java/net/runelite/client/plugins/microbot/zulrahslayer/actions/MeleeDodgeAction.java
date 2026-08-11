package net.runelite.client.plugins.custom.zulrah.actions;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.custom.zulrah.constants.StandLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Melee (crimson) phase: the dodge target tile is flipped on each tail swing (by the plugin's
 * animation event). Here we just walk to the current target and only attack once we've arrived, so
 * the attack click can't strand us on the tile being swung at. The gear swap is mouseless and does
 * not interrupt movement, so it runs in parallel this same tick — dodging never stands down for it.
 */
public class MeleeDodgeAction implements ZulrahAction {

    @Override
    public int order() {
        return 700;
    }

    @Override
    public String key() {
        return "melee-dodge";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        return state.context().isMeleeDodgePhase();
    }

    @Override
    public Object execute(ZulrahState state) {
        WorldPoint target = (state.context().isMeleeDodgeAtNorth()
                ? StandLocation.NORTHEAST_NORTH
                : StandLocation.NORTHEAST_TOP).toWorldPoint();
        if (Rs2Player.getWorldLocation().equals(target)) {
            // Only attack while Zulrah is surfaced and the gear is ready; when it dives, hold the tile
            // without spamming clicks at a target we can't hit, and don't attack with the wrong style.
            if (state.context().isSurfaced() && ZulrahHelpers.gearReady(state.context())
                    && !ZulrahHelpers.isInteractingWithZulrah()) {
                ZulrahHelpers.clickNearestZulrah();
            }
            return "attack";
        }
        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkFastCanvas(target, true);
            return "walk";
        }
        return "moving";
    }
}
