package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.StandLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Melee (crimson) phase: on each tail swing the dodge target becomes whichever of the stand tile and
 * its melee-dodge partner is farther from the tile the swing was aimed at. Here we just walk to the
 * current target and only attack once we've arrived, so the attack click can't strand us on the tile being swung at. The gear swap is mouseless and does
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

    public static WorldPoint dodgeTarget(WorldPoint swingTile, StandLocation home) {
        WorldPoint stand = home.toWorldPoint();
        StandLocation dodge = home.meleeDodge();
        if (dodge == null) {
            return stand;
        }
        WorldPoint away = dodge.toWorldPoint();
        return swingTile.distanceTo(away) > swingTile.distanceTo(stand) ? away : stand;
    }

    @Override
    public Object execute(ZulrahState state) {
        WorldPoint target = ZulrahHelpers.targetTile(state.context());
        if (target == null) {
            return null;
        }
        if (target.equals(Rs2Player.getWorldLocation())) {
            if (canAttackFromTile(state.context())) {
                ZulrahHelpers.attackZulrah();
            }
            return "attack";
        }
        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkFastCanvas(target, true);
            return "walk";
        }
        return "moving";
    }

    /** Attack only while surfaced with the gear ready and not already locked on, so we hold the tile
     *  (rather than spam clicks at a submerged target or hit with the wrong style) while it dives. */
    private static boolean canAttackFromTile(FightContext ctx) {
        return ctx.isSurfaced() && ZulrahHelpers.gearReady(ctx) && !ZulrahHelpers.isInteractingWithZulrah();
    }
}
