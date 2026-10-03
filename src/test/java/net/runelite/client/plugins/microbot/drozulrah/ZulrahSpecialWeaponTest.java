package net.runelite.client.plugins.microbot.drozulrah;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahSpecialWeaponTest
{
    @Test public void shortbowVariantsHaveDifferentCosts() {
        assertEquals(550, ZulrahSpecialWeapon.find("Magic shortbow").cost);
        assertEquals(500, ZulrahSpecialWeapon.find("Magic shortbow (i)").cost);
        assertFalse(ZulrahSpecialWeapon.find("Magic shortbow (i)").magic);
    }
    @Test public void onlyOffensiveNightmareVariantsAreSupported() {
        assertTrue(ZulrahSpecialWeapon.find("Volatile nightmare staff").magic);
        assertEquals(550, ZulrahSpecialWeapon.find("Eldritch nightmare staff").cost);
        assertNull(ZulrahSpecialWeapon.find("Nightmare staff"));
        assertNull(ZulrahSpecialWeapon.find("Harmonised nightmare staff"));
        assertNull(ZulrahSpecialWeapon.find("Trident of the swamp"));
        assertNull(ZulrahSpecialWeapon.find(null));
    }
    @Test public void confirmationUsesWeaponCost() {
        ZulrahSpecGate gate = new ZulrahSpecGate();
        gate.dispatched(0, 1000, 400);
        assertFalse(gate.canDispatch());
        assertEquals(ZulrahSpecGate.Result.SPENT, gate.observe(600, 600, false));
        assertTrue(gate.canDispatch());
        gate.dispatched(1000, 1000, 550);
        assertEquals(ZulrahSpecGate.Result.NONE, gate.observe(1600, 500, false));
        assertEquals(ZulrahSpecGate.Result.SPENT, gate.observe(1600, 450, false));
    }
    @Test public void failedClicksStopWithoutBlockingOtherWeapons() {
        ZulrahSpecGate failed = new ZulrahSpecGate();
        failed.dispatched(0, 1000, 500);
        assertEquals(ZulrahSpecGate.Result.RETRY, failed.observe(5000, 1000, false));
        failed.dispatched(6000, 1000, 500);
        assertEquals(ZulrahSpecGate.Result.DISABLED, failed.observe(11000, 1000, false));
        assertFalse(failed.canDispatch());
        assertTrue(new ZulrahSpecGate().canDispatch());
        failed.reset();
        assertTrue(failed.canDispatch());
    }
    @Test public void armedClickIsConfirmedImmediately() {
        ZulrahSpecGate gate = new ZulrahSpecGate();
        gate.dispatched(0, 1000, 550);
        assertEquals(ZulrahSpecGate.Result.ARMED, gate.observe(100, 1000, true));
    }
}
