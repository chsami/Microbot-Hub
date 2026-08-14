package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Non-melee phases: keep on the safespot and attack; when repositioning, attack the moment the
 * weapon is off cooldown and Zulrah is in range (the cooldown ticks are free for movement),
 * otherwise walk. Runs alongside the (mouseless, non-interrupting) gear swap in the same tick, so it
 * no longer stands down while gear is swapping — walking/attacking and equipping happen in parallel.
 */
@Slf4j
public class RepositionAttackAction implements ZulrahAction {

    @Override
    public int order() {
        return 800;
    }

    @Override
    public String key() {
        return "reposition-attack";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        FightContext ctx = state.context();
        return !ctx.isMeleeDodgePhase() && ctx.getStandLocation() != null;
    }

    @Override
    public Object execute(ZulrahState state) {
        FightContext ctx = state.context();
        WorldPoint target = ctx.getStandLocation();

        final long now = System.currentTimeMillis();
        final long cooldownMs = ZulrahHelpers.attackCooldownMs(ctx);

        final boolean everAttacked = ctx.getLastAttackAtMs() != 0L;
        final long timeSinceLastAttack = everAttacked ? now - ctx.getLastAttackAtMs() : cooldownMs;
        final boolean canAttackAgain = timeSinceLastAttack >= cooldownMs;

        final boolean surfaced = ctx.isSurfaced();

        if (surfaced) {
            ZulrahHelpers.faceZulrah();
        }

        if (ctx.isWaitingForFirstAttackOnZulrah()) {
            if (notReadyToAttack(surfaced, ctx)) {
                return "opening-wait";
            }
            if (!ZulrahHelpers.isInteractingWithZulrah()) {
                ZulrahHelpers.attackZulrah();
                return "opening-attack";
            }

            if (playerIsPerformingAttack()) {
                ctx.setLastAttackAtMs(now);
                ctx.setWaitingForFirstAttackOnZulrah(false);
                ctx.setNeedsToGoToFirstLocation(true);
                return "opening-fired";
            }
            return "opening-pending";
        }

        if (ctx.isNeedsToGoToFirstLocation()) {
            if (ZulrahHelpers.atTargetTile(ctx)) {
                ctx.setNeedsToGoToFirstLocation(false);
            } else {
                if (!Rs2Player.isMoving()) {
                    Rs2Walker.walkFastCanvas(target, true);
                }
                return "opening-walk";
            }
        }

        if (ZulrahHelpers.atTargetTile(ctx)) {
            if (notReadyToAttack(surfaced, ctx)) {
                return "hold-safespot";
            }
            if (!ZulrahHelpers.isInteractingWithZulrah()) {
                ZulrahHelpers.attackZulrah();
            }
            if (ZulrahHelpers.isInteractingWithZulrah() && canAttackAgain) {
                ctx.setLastAttackAtMs(now);
            }
            return "attack-safespot";
        }

        if (zulrahCanBeAttacked(surfaced, canAttackAgain, ctx)) {
            log.debug("[dps] attacking Zulrah mid-reposition to {} ({}ms since last attack)", target, timeSinceLastAttack);
            ZulrahHelpers.attackZulrah();
            ctx.setLastAttackAtMs(now);
            return "attack-moving";
        }
        if (!Rs2Player.isMoving()) {
            log.debug("[dps] walking to {} | {}ms since last attack, {}ms until next attack",
                    target, timeSinceLastAttack, Math.max(0L, cooldownMs - timeSinceLastAttack));
            Rs2Walker.walkFastCanvas(target, true);
            return "walk";
        }
        return "moving";
    }

    private static boolean zulrahCanBeAttacked(boolean surfaced, boolean canAttackAgain, FightContext ctx) {
        return surfaced && canAttackAgain && ZulrahHelpers.gearReady(ctx) && ZulrahHelpers.zulrahInRange();
    }

    private static boolean notReadyToAttack(boolean surfaced, FightContext ctx) {
        return !surfaced || !ZulrahHelpers.gearReady(ctx);
    }

    private static boolean playerIsPerformingAttack() {
        return Rs2Player.isAnimating() && !Rs2Player.isMoving();
    }
}
