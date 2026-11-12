package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BurningAmuletLandingTest
{
    private final BurningAmuletLanding landing = new BurningAmuletLanding();
    private final Object request = new Object(), provider = new Object(), attempt = new Object();
    private final WorldPoint expected = BurningAmuletLanding.CHAOS_TEMPLE;
    private final WorldPoint actual = new WorldPoint(3242, 3634, 0);
    private void arm() { landing.arm(1, request, provider, 301, 100, new WorldPoint(3200, 3500, 0), expected, true); }
    private boolean observe(int tick, boolean idle) { return landing.observe(1, request, provider, attempt, 301, tick, expected, actual, true, true, idle); }
    @Test void settledEightTileLanding() { arm(); assertTrue(observe(103, true)); }
    @Test void recordsJumpDuringAnimation() { arm(); assertFalse(observe(102, false)); assertTrue(observe(104, true)); }
    @Test void rejectsUnarmed() { assertFalse(observe(103, true)); }
    @Test void requiresDispatch() { arm(); assertFalse(landing.observe(1, request, provider, null, 301, 103, expected, actual, true, true, true)); }
    @Test void rejectsWalkingIntoRadius() { landing.arm(1, request, provider, 301, 100, new WorldPoint(3250, 3634, 0), expected, true); assertFalse(observe(105, true)); }
    @Test void expires() { arm(); assertFalse(observe(117, true)); }
    @Test void requestEpochChangeResets() { arm(); assertFalse(landing.observe(2, request, provider, attempt, 301, 103, expected, actual, true, true, true)); assertFalse(observe(104, true)); }
    @Test void providerChangeResets() { arm(); assertFalse(landing.observe(1, request, new Object(), attempt, 301, 103, expected, actual, true, true, true)); }
    @Test void worldChangeResets() { arm(); assertFalse(landing.observe(1, request, provider, attempt, 302, 103, expected, actual, true, true, true)); }
    @Test void cancellationResets() { arm(); landing.reset(); assertFalse(observe(103, true)); }
    @Test void requiresLoggedInAndWalking() { arm(); assertFalse(landing.observe(1, request, provider, attempt, 301, 103, expected, actual, false, true, true)); arm(); assertFalse(landing.observe(1, request, provider, attempt, 301, 103, expected, actual, true, false, true)); }
    @Test void nativeRadiusRemainsNative() { arm(); assertFalse(landing.observe(1, request, provider, attempt, 301, 103, expected, new WorldPoint(3240,3634,0), true,true,true)); }
    @Test void onlyChargedAmulets() { assertTrue(BurningAmuletLanding.charged(21175)); assertFalse(BurningAmuletLanding.charged(21177)); landing.arm(1,request,provider,301,100,actual,expected,false); assertFalse(observe(103,true)); }
    @Test void rejectsOtherLanding() { landing.arm(1,request,provider,301,100,actual,new WorldPoint(3028,3842,0),true); assertFalse(observe(103,true)); }
    @Test void preDispatchPreparationDoesNotConsumeLandingWindow() {
        arm();
        landing.arm(1,request,provider,301,110,new WorldPoint(3200,3500,0),expected,true);
        assertTrue(observe(120,true));
    }
    @Test void finalChargeDisappearanceDoesNotOverwriteFlightEvidence() {
        arm();
        assertFalse(observe(102,false));
        landing.arm(1,request,provider,301,103,actual,expected,false);
        assertTrue(observe(104,true));
    }
    @Test void changedAttemptRejectsOldEvidence() {
        arm(); assertFalse(observe(102,false));
        assertFalse(landing.observe(1,request,provider,new Object(),301,103,expected,actual,true,true,true));
    }
    @Test void walkingAtRunSpeedIsNotDiscontinuity() {
        landing.arm(1,request,provider,301,100,new WorldPoint(3262,3634,0),expected,true);
        assertFalse(observe(110,true));
    }
}
