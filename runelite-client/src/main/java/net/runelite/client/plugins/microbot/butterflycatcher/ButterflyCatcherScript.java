package net.runelite.client.plugins.microbot.butterflycatcher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * ButterflyCatcherScript
 *
 * Two modes — both run indefinitely with no banking:
 *
 *   BAREHANDED:
 *     Click catch, wait for animation/movement to resolve, repeat.
 *     Requires Hunter level >= target.getBarehandedLevelRequired().
 *
 *   BUTTERFLY_NET:
 *     Same loop, but requires a butterfly net or magic butterfly net to be
 *     equipped. Verified once on startup. Requires Hunter level >=
 *     target.getNetLevelRequired() (10 levels lower than barehanded).
 *
 * Item IDs for nets:
 *   Butterfly net       : 10010
 *   Magic butterfly net : 11259
 */
@Slf4j
public class ButterflyCatcherScript extends Script {

    // Item IDs for butterfly nets
    private static final int NET_ITEM_ID       = 10010;
    private static final int MAGIC_NET_ITEM_ID = 11259;

    @Inject
    private Rs2NpcCache npcCache;

    private static final String TAG  = "[ButterflyCatcher]";
    private static final int    TICK = 600;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private ButterflyType targetButterfly;
    private CatchMode     catchMode;
    private boolean       catchCommitted = false;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public boolean run(ButterflyCatcherConfig config) {
        this.targetButterfly = config.butterflyType();
        this.catchMode       = config.catchMode();

        int requiredLevel = effectiveLevel();
        Microbot.log(TAG + " Starting — target: " + targetButterfly.getDisplayName()
                + " | mode: " + catchMode
                + " | Required Hunter level: " + requiredLevel);

        // --- Net equipment check (BUTTERFLY_NET mode only) ---
        if (catchMode == CatchMode.BUTTERFLY_NET) {
            if (!isNetEquipped()) {
                Microbot.log(TAG + " ⚠ No butterfly net equipped! "
                        + "Please equip a Butterfly Net (10010) or Magic Butterfly Net (11259) "
                        + "before starting. Stopping.");
                Microbot.showMessage("Butterfly Catcher stopped: no butterfly net equipped. "
                        + "Equip a net and restart.");
                return false;
            }
            Microbot.log(TAG + " Butterfly net verified — equipment check passed.");
        }

        // --- Hunter level check ---
        int hunterLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (hunterLevel < requiredLevel) {
            Microbot.log(TAG + " ⚠ Hunter level too low ("
                    + hunterLevel + " / " + requiredLevel
                    + ") for " + targetButterfly.getDisplayName()
                    + " in " + catchMode + " mode. Stopping.");
            Microbot.showMessage("Butterfly Catcher stopped: Hunter level too low ("
                    + hunterLevel + " / " + requiredLevel + ").");
            return false;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::tick, 0, TICK, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    private void tick() {
        try {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;

            // Re-check level every tick in case boosted level drops below threshold
            int hunterLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
            if (hunterLevel < effectiveLevel()) {
                Microbot.log(TAG + " Hunter level too low ("
                        + hunterLevel + " / " + effectiveLevel()
                        + ") — stopping.");
                shutdown();
                return;
            }

            tickCatching();

        } catch (Exception e) {
            log.error(TAG + " Unexpected error in tick: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // CATCHING
    // -------------------------------------------------------------------------

    private void tickCatching() {
        // Idle guard — don't spam-click while already committed to a catch attempt
        if (catchCommitted) {
            if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                return;
            }
            catchCommitted = false;
        }

        if (Rs2Player.isAnimating()) return;

        // Find nearest target NPC
        Rs2NpcModel target = findNearestTarget();

        if (target == null) {
            Microbot.log(TAG + " No " + targetButterfly.getDisplayName()
                    + " found nearby (IDs: " + Arrays.toString(targetButterfly.getNpcIds()) + ") — waiting.");
            return;
        }

        boolean clicked = target.click("Catch");
        if (!clicked) {
            log.warn(TAG + " click(Catch) failed on {} at {}",
                    targetButterfly.getDisplayName(), target.getWorldLocation());
            return;
        }

        catchCommitted = true;
        log.debug(TAG + " Clicked Catch on {} at {}",
                targetButterfly.getDisplayName(), target.getWorldLocation());
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isMoving(), 1500);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the Hunter level required for the current catch mode.
     * BUTTERFLY_NET uses the lower net threshold; BAREHANDED adds 10.
     */
    private int effectiveLevel() {
        return catchMode == CatchMode.BUTTERFLY_NET
                ? targetButterfly.getNetLevelRequired()
                : targetButterfly.getBarehandedLevelRequired();
    }

    /**
     * Returns true if a butterfly net or magic butterfly net is currently equipped.
     */
    private boolean isNetEquipped() {
        return Rs2Equipment.isWearing(NET_ITEM_ID) || Rs2Equipment.isWearing(MAGIC_NET_ITEM_ID);
    }

    private Rs2NpcModel findNearestTarget() {
        Rs2NpcModel best = null;
        int bestDist     = Integer.MAX_VALUE;
        WorldPoint myPos = Rs2Player.getWorldLocation();

        for (int npcId : targetButterfly.getNpcIds()) {
            Rs2NpcModel candidate = npcCache.query().withId(npcId).nearest();
            if (candidate == null) continue;
            int dist = candidate.getWorldLocation().distanceTo(myPos);
            if (dist < bestDist) {
                bestDist = dist;
                best     = candidate;
            }
        }
        return best;
    }
}
