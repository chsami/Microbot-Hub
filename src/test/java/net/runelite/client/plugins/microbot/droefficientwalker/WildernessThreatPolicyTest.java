package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

public class WildernessThreatPolicyTest
{
    private static final WorldPoint HERE = new WorldPoint(3200, 3600, 0); // level 11
    private WildernessThreatPolicy.Snapshot evaluate(int level, boolean pvp, boolean deadman,
        boolean localTarget, WildernessThreatPolicy.NearbyPlayer... others)
    {
        return WildernessThreatPolicy.evaluate(HERE, level, pvp, deadman, localTarget,
            Arrays.asList(others), 32, 5);
    }
    private WildernessThreatPolicy.NearbyPlayer other(int level, WorldPoint location)
    {
        return new WildernessThreatPolicy.NearbyPlayer(location, level, true, false);
    }

    @Test public void safeEnclaveNeverReportsCombatThreat()
    {
        var result = WildernessThreatPolicy.evaluate(new WorldPoint(3130, 3636, 0), 90, false, false,
            true, Collections.singletonList(other(90, HERE)), 32, 5);
        assertEquals(0, result.getWildernessLevel());
        assertFalse(result.hasThreat());
    }

    @Test public void feroxOccupantsDoNotTriggerLogoutAfterLocalPlayerLeavesGate()
    {
        WorldPoint outside = new WorldPoint(3118, 3639, 0);
        WorldPoint inside = new WorldPoint(3130, 3636, 0);
        for (boolean pvpWorld : new boolean[]{false, true})
        {
            var result = WildernessThreatPolicy.evaluate(outside, 90, pvpWorld, false, false,
                Collections.singletonList(other(90, inside)), 32, 5);
            assertTrue(result.getWildernessLevel() > 0);
            assertFalse(result.hasThreat());
            assertTrue(result.threatPlayers().isEmpty());
        }
    }

    @Test public void nearbyPlayerOutsideFeroxStillTriggersLogoutAmongSafeOccupants()
    {
        WorldPoint outside = new WorldPoint(3118, 3639, 0);
        var result = WildernessThreatPolicy.evaluate(outside, 90, false, false, false,
            Arrays.asList(other(90, new WorldPoint(3130, 3636, 0)),
                new WildernessThreatPolicy.NearbyPlayer(new WorldPoint(3117, 3639, 0),
                    90, true, false, -1, 7)), 32, 5);
        assertTrue(result.isAttackCapablePlayerNearby());
        assertEquals(Collections.singleton(7), result.threatPlayers());
    }

    @Test public void wildernessCombatRangeAndUpperBufferRemainInclusive()
    {
        assertFalse(evaluate(90, false, false, false, other(78, HERE)).hasThreat());
        assertTrue(evaluate(90, false, false, false, other(79, HERE)).hasThreat());
        assertTrue(evaluate(90, false, false, false, other(106, HERE)).hasThreat());
        assertFalse(evaluate(90, false, false, false, other(107, HERE)).hasThreat());
    }

    @Test public void pvpAndDeadmanRangesMatchOriginalWalker()
    {
        assertTrue(evaluate(90, true, false, false, other(64, HERE)).hasThreat());
        assertFalse(evaluate(90, true, false, false, other(63, HERE)).hasThreat());
        assertTrue(evaluate(90, false, true, false, other(3, HERE)).hasThreat());
        assertTrue(evaluate(90, false, true, false, other(126, HERE)).hasThreat());
    }

    @Test public void radiusPlaneAndNameExcludeOrdinaryNearbyPlayers()
    {
        assertTrue(evaluate(90, false, false, false, other(90, HERE.dx(32))).hasThreat());
        assertFalse(evaluate(90, false, false, false, other(90, HERE.dx(33))).hasThreat());
        assertFalse(evaluate(90, false, false, false,
            other(90, new WorldPoint(HERE.getX(), HERE.getY(), 1))).hasThreat());
        assertFalse(evaluate(90, false, false, false,
            new WildernessThreatPolicy.NearbyPlayer(HERE, 90, false, false)).hasThreat());
    }

    @Test public void pvpEngagementWinsEvenBeforeNameAndProximityAreAvailable()
    {
        assertTrue(evaluate(90, false, false, true).isPvpCombat());
        var result = evaluate(90, false, false, false,
            new WildernessThreatPolicy.NearbyPlayer(HERE.dx(40), 20, false, true));
        assertTrue(result.isPvpCombat());
        assertTrue(result.hasThreat());
        assertFalse(result.isAttackCapablePlayerNearby());
    }

    @Test public void scopedRoutingDoesNotChangeSavedPreferencesAndTravelHasNoCombatOverride() throws Exception
    {
        var ownership = new WildernessThreatOwnership();
        EfficientWalkerConfig stored = new EfficientWalkerConfig() {};
        var effective = ownership.effectiveConfig(stored);
        assertFalse(stored.allowWildernessTravel());
        try (WildernessCombatContext context = ownership.acquire("Dro Slayer"))
        {
            assertTrue(effective.allowWildernessTravel());
            assertFalse(ownership.isCombatActive());
            context.setCombatActive(true);
            assertTrue(ownership.isCombatActive());
            context.setCombatActive(false);
            assertFalse(ownership.isCombatActive());
            assertFalse(stored.allowWildernessTravel());
            assertEquals(stored.wildernessThreatRadius(), effective.wildernessThreatRadius());
        }
        assertFalse(effective.allowWildernessTravel());
        assertFalse(ownership.isCombatActive());
    }

    @Test public void staleLeaseCannotChangeOrReleaseReplacementOwner() throws Exception
    {
        var ownership = new WildernessThreatOwnership();
        var old = ownership.acquire("Old");
        ownership.clear();
        assertFalse(old.isActive());
        var current = ownership.acquire("New");
        current.setCombatActive(true);
        old.setCombatActive(false);
        old.close();
        assertTrue(ownership.isOwned());
        assertTrue(current.isActive());
        assertTrue(ownership.isCombatActive());
        current.close();
        current.close();
        assertFalse(ownership.isOwned());
        assertFalse(ownership.isCombatActive());
    }

    @Test public void onlyEligibleChargedLevelThirtyItemsArePrepared()
    {
        assertTrue(WildernessCombatEscape.isLevel30Escape("Royal seed pod"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Amulet of glory(6)"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Amulet of glory (1)"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Amulet of glory(t)(4)"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Amulet of eternal glory"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Ring of wealth (5)"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Combat bracelet(1)"));
        assertTrue(WildernessCombatEscape.isLevel30Escape("Skills necklace(6)"));
        assertFalse(WildernessCombatEscape.isLevel30Escape("Ring of dueling(8)"));
        assertFalse(WildernessCombatEscape.isLevel30Escape("Amulet of glory"));
        assertFalse(WildernessCombatEscape.isLevel30Escape("Amulet of glory(0)"));
        assertFalse(WildernessCombatEscape.isLevel30Escape("Skills necklace"));
        assertTrue(WildernessCombatEscape.escapePreference("Royal seed pod")
            < WildernessCombatEscape.escapePreference("Amulet of glory(6)"));
    }

    @Test public void reflectionBridgeCanUseAndReleasePublicContext() throws Exception
    {
        var ownership = new WildernessThreatOwnership();
        var context = ownership.acquire("Dro Slayer");
        context.getClass().getMethod("setCombatActive", boolean.class).invoke(context, true);
        assertTrue(ownership.isCombatActive());
        assertEquals(Boolean.TRUE, context.getClass().getMethod("isActive").invoke(context));
        context.getClass().getMethod("close").invoke(context);
        assertFalse(ownership.isOwned());
    }

    @Test public void travelLogoutLeavesNpcCombatRecoveryUninterrupted()
    {
        assertFalse(WildernessThreatPolicy.mayAttemptTravelLogout(true));
        assertTrue(WildernessThreatPolicy.mayAttemptTravelLogout(false));
    }

    @Test public void deepWildernessLogoutWaitsForEveryCombatSignalToClear()
    {
        assertFalse(WildernessCombatEscape.mayLogout(true, false, false));
        assertFalse(WildernessCombatEscape.mayLogout(false, true, false));
        assertFalse(WildernessCombatEscape.mayLogout(false, false, true));
        assertTrue(WildernessCombatEscape.mayLogout(false, false, false));
    }

    @Test public void confirmedPlayerAttackUsesAnimationAndTenObservedGameTicks()
    {
        assertFalse(WildernessPvpActivity.isAttackAction(true, -1));
        assertFalse(WildernessPvpActivity.isAttackAction(false, 422));
        assertTrue(WildernessPvpActivity.isAttackAction(true, 422));
        var activity = new WildernessPvpActivity();
        assertFalse(activity.observe(true, false, 10));
        assertTrue(activity.observe(true, true, 11));
        assertTrue(activity.observe(true, false, 20));
        assertFalse(activity.observe(true, false, 21));
        assertTrue(activity.observe(true, true, 22));
        assertFalse(activity.observe(false, true, 23));
        assertFalse(activity.observe(true, false, 24));
    }

    @Test public void newAttackSignalDoesNotChangeLegacyTravelEngagement()
    {
        var follow = evaluate(90, false, false, false,
            new WildernessThreatPolicy.NearbyPlayer(HERE, 90, true, true, -1, 7));
        assertTrue(follow.isPvpCombat());
        assertFalse(follow.hasAttackAction());
        var attack = evaluate(90, false, false, false,
            new WildernessThreatPolicy.NearbyPlayer(HERE, 90, true, true, 422, 7));
        assertTrue(attack.hasAttackAction());
    }

    @Test public void quietSuppressionIsClearedByExplicitContextReset()
    {
        var episode = new WildernessQuietEncounter();
        var threat = evaluate(90, false, false, false,
            new WildernessThreatPolicy.NearbyPlayer(HERE, 90, true, false, -1, 7));
        assertTrue(episode.observe(threat, 10));
        episode.release(threat);
        assertFalse(episode.observe(threat, 11));
        episode.reset();
        assertTrue(episode.observe(threat, 12));
    }
}
