package net.runelite.client.plugins.custom.zulrah.actions;

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
        // lastAttackAtMs == 0 means we haven't attacked yet: treat as exactly off cooldown (ready)
        // rather than "epoch 0", which produced the nonsensical multi-billion-ms log/timer values.
        final boolean everAttacked = ctx.getLastAttackAtMs() != 0L;
        final long sinceLastMs = everAttacked ? now - ctx.getLastAttackAtMs() : cooldownMs;
        final boolean offCooldown = sinceLastMs >= cooldownMs;

        // Only attack while Zulrah is surfaced. During the opening run (before the first spawn) and
        // between phases (while it is submerged) we still walk to the tile, but don't waste clicks on
        // a target we can't hit.
        final boolean surfaced = ctx.isSurfaced();

        // While it's up, keep the camera on it so our clicks land (only turns when it drifts off-screen).
        if (surfaced) {
            ZulrahHelpers.faceZulrah();
        }

        // OPENING (first phase only): don't head to the first stand tile yet — wait for Zulrah to
        // surface, get the opening attack off from the spawn spot, and release only once that attack
        // has actually fired (player animating and stationary). Then normal reposition takes over and
        // walks to the first tile. Set/cleared via FightContext.openingHold (armed in onFightStart).
        if (ctx.isOpeningHold()) {
            if (!surfaced || !ZulrahHelpers.gearReady(ctx)) {
                return "opening-wait"; // wait for the snake to surface / the right gear to be on
            }
            if (!ZulrahHelpers.isInteractingWithZulrah()) {
                ZulrahHelpers.clickNearestZulrah(); // start the opening attack (auto-walks into range)
                return "opening-attack";
            }
            // Interacting: release only once the attack animation has fired (stationary + animating),
            // so we actually get the hit off before moving. Then switch to a walk-only run to the first
            // tile (openingWalk) — from the centre Zulrah is in range, so attacking again here would keep
            // us pinned instead of moving.
            if (Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                ctx.setLastAttackAtMs(now);
                ctx.setOpeningHold(false);
                ctx.setOpeningWalk(true);
                return "opening-fired";
            }
            return "opening-pending";
        }

        // OPENING WALK (first phase only): after the opening hit, walk straight to the first stand tile
        // without stopping to attack. We can't rely on the attack-while-repositioning branch below —
        // from the spawn spot Zulrah is in range, so it would attack in place every cooldown and never
        // let us leave the centre. Cleared on arrival, then normal safespot attacking resumes.
        if (ctx.isOpeningWalk()) {
            if (ZulrahHelpers.atTargetTile(ctx)) {
                ctx.setOpeningWalk(false); // arrived — fall through to normal safespot attacking
            } else {
                if (!Rs2Player.isMoving()) {
                    log.info("[dps] opening: walking to the first tile {} (no attacks en route)", target);
                    Rs2Walker.walkFastCanvas(target, true);
                }
                return "opening-walk";
            }
        }

        // Arrived on (or as close as we can get to) the stand tile: attack in place and keep the
        // cooldown timer in sync with the game's cadence. atTargetTile() tolerates stopping one tile
        // short so we don't loop forever walking if we can't stand exactly on the tile.
        if (ZulrahHelpers.atTargetTile(ctx)) {
            if (!surfaced || !ZulrahHelpers.gearReady(ctx)) {
                // In position, but wait to surface / to finish the gear swap before attacking, so we
                // don't waste hits with the wrong combat style.
                return "hold-safespot";
            }
            if (!ZulrahHelpers.isInteractingWithZulrah()) {
                ZulrahHelpers.clickNearestZulrah();
            }
            if (ZulrahHelpers.isInteractingWithZulrah() && offCooldown) {
                ctx.setLastAttackAtMs(now);
            }
            return "attack-safespot";
        }

        // Repositioning: attack the moment the weapon is off cooldown, gear is ready and Zulrah is in
        // range (the cooldown ticks are free for movement), otherwise keep walking.
        if (surfaced && offCooldown && ZulrahHelpers.gearReady(ctx) && ZulrahHelpers.zulrahInRange()) {
            log.info("[dps] attacking Zulrah mid-reposition to {} ({}ms since last attack)", target, sinceLastMs);
            ZulrahHelpers.clickNearestZulrah();
            ctx.setLastAttackAtMs(now);
            return "attack-moving";
        }
        if (!Rs2Player.isMoving()) {
            log.info("[dps] walking to {} | {}ms since last attack, {}ms until next attack",
                    target, sinceLastMs, Math.max(0L, cooldownMs - sinceLastMs));
            Rs2Walker.walkFastCanvas(target, true);
            return "walk";
        }
        return "moving";
    }
}
