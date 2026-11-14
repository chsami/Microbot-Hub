package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerWildernessSuppliesTest {
    @Test public void blightedSuppliesNeedWilderness() {
        assertFalse(DroSlayerWildernessSupplies.canConsume("Blighted manta ray", false));
        assertFalse(DroSlayerWildernessSupplies.canConsume("Blighted anglerfish", false));
        assertFalse(DroSlayerWildernessSupplies.canConsume("Blighted karambwan", false));
        assertTrue(DroSlayerWildernessSupplies.canConsume("Blighted manta ray", true));
        assertTrue(DroSlayerWildernessSupplies.canConsume("Manta ray", false));
    }
    @Test public void doseChecksRejectUnusableAndUnrelatedPotions() {
        assertEquals(0, DroSlayerWildernessSupplies.prayerDoses("Blighted super restore(4)", false));
        assertEquals(4, DroSlayerWildernessSupplies.prayerDoses("Blighted super restore(4)", true));
        assertEquals(1, DroSlayerWildernessSupplies.prayerDoses("Super restore(1)", false));
        assertEquals(3, DroSlayerWildernessSupplies.prayerDoses("Prayer potion(3)", false));
        assertEquals(2, DroSlayerWildernessSupplies.prayerDoses("Sanfew serum(2)", false));
        assertEquals(0, DroSlayerWildernessSupplies.prayerDoses("Restore potion(4)", true));
        assertEquals(0, DroSlayerWildernessSupplies.prayerDoses("Super restore", true));
    }
    @Test public void wildernessFoodPreferenceKeepsNormalFallback() {
        assertTrue(DroSlayerWildernessSupplies.foodPreference("Blighted manta ray", true)
                < DroSlayerWildernessSupplies.foodPreference("Manta ray", true));
        assertEquals(0, DroSlayerWildernessSupplies.foodPreference("Manta ray", false));
        assertTrue(DroSlayerWildernessSupplies.foodPreference("Shark", false)
                < DroSlayerWildernessSupplies.foodPreference("Unusual food", false));
    }
    @Test public void sacksApplyOnlyToIceSpells() {
        assertTrue(DroSlayerWildernessSupplies.isIceSpell("Ice Barrage"));
        assertTrue(DroSlayerWildernessSupplies.isIceSpell("ICE_BURST"));
        assertFalse(DroSlayerWildernessSupplies.isIceSpell("Blood Barrage"));
        assertFalse(DroSlayerWildernessSupplies.isIceSpell(null));
    }
}
