package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AttackNpcScriptCenterInitTest {

    private static final WorldPoint UNSET = new WorldPoint(0, 0, 0);
    private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

    @Test
    void unsetCenterIsInitialisedInAutomaticMode() {
        assertTrue(AttackNpcScript.needsCenterInit(false, false, UNSET));
    }

    @Test
    void existingCenterIsKept() {
        assertFalse(AttackNpcScript.needsCenterInit(false, false, LUMBRIDGE));
    }

    @Test
    void manualCenterAndSlayerModeKeepOwnership() {
        assertFalse(AttackNpcScript.needsCenterInit(true, false, UNSET));
        assertFalse(AttackNpcScript.needsCenterInit(false, true, UNSET));
        assertFalse(AttackNpcScript.needsCenterInit(false, false, null));
    }
}
