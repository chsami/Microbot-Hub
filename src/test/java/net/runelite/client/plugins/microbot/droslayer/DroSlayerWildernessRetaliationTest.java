package net.runelite.client.plugins.microbot.droslayer;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerWildernessRetaliationTest
{
    @Test
    public void mixedGodWarsRoomCannotOverrideEligibleSpiritualTargets()
    {
        assertTrue(DroSlayerScript.allowsRetaliationTarget(true, "Spiritual ranger", Collections.singletonList("Spiritual ranger")));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Spiritual warrior", Collections.singletonList("Spiritual ranger")));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Spiritual mage", Collections.singletonList("Spiritual ranger")));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Bloodveld", Collections.singletonList("Spiritual ranger")));
    }

    @Test
    public void emptyOrMissingWildernessAssignmentCannotEnableAnyNpcFallback()
    {
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Aviansie", Collections.emptyList()));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Aviansie", null));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, null, Collections.singletonList("Aviansie")));
    }

    @Test
    public void wildernessRetaliationUsesExactAllowedVariants()
    {
        assertTrue(DroSlayerScript.allowsRetaliationTarget(true, "  BLACK DEMON ", Arrays.asList("Black demon", "Black demons")));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Demonic gorilla", Collections.singletonList("Black demon")));
        assertFalse(DroSlayerScript.allowsRetaliationTarget(true, "Baby black dragon", Collections.singletonList("Black dragon")));
    }

    @Test
    public void ordinarySlayerRetaliationBehaviorIsPreserved()
    {
        assertTrue(DroSlayerScript.allowsRetaliationTarget(false, "Unrelated attacker", Collections.singletonList("Bloodveld")));
        assertTrue(DroSlayerScript.allowsRetaliationTarget(false, "Unrelated attacker", null));
    }
}
