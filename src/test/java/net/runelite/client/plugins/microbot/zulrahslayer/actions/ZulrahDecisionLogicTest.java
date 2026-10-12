package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import net.runelite.client.plugins.microbot.zulrahslayer.constants.ZulrahType;
import net.runelite.client.plugins.microbot.zulrahslayer.rotationutils.ZulrahNpc;
import net.runelite.client.plugins.microbot.zulrahslayer.rotationutils.ZulrahPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure combat-decision logic in {@link ZulrahHelpers} (no client): which style to attack a form with,
 * and the venom-damage projection that drives the anti-venom threshold.
 */
class ZulrahDecisionLogicTest {

    private static ZulrahPhase phase(ZulrahType type, boolean jad) {
        // attackWithMagic only reads the NPC, so the attributes are irrelevant here.
        return new ZulrahPhase(new ZulrahNpc(type, jad), null);
    }

    @Test
    void onlyTheTanzaniteMagicFormIsAttackedWithRanged() {
        // The MAGIC (tanzanite) form resists magic, so we range it -> attackWithMagic == false.
        assertFalse(ZulrahHelpers.attackWithMagic(phase(ZulrahType.MAGIC, false)),
                "the tanzanite magic form should be attacked with ranged");

        // Every other form is attacked with magic.
        assertTrue(ZulrahHelpers.attackWithMagic(phase(ZulrahType.RANGE, false)));
        assertTrue(ZulrahHelpers.attackWithMagic(phase(ZulrahType.MELEE, false)));

        // The Jad phase is always magiced, even when its rotation entry encodes it as the MAGIC form.
        assertTrue(ZulrahHelpers.attackWithMagic(phase(ZulrahType.MAGIC, true)),
                "the Jad phase is always attacked with magic regardless of encoded form");
        assertTrue(ZulrahHelpers.attackWithMagic(phase(ZulrahType.RANGE, true)));
    }

    @Test
    void poisonDamageBelowVenomThresholdRoundsUpByFifths() {
        assertEquals(0, ZulrahHelpers.nextPoisonDamage(0));
        assertEquals(1, ZulrahHelpers.nextPoisonDamage(1));
        assertEquals(1, ZulrahHelpers.nextPoisonDamage(5));
        assertEquals(2, ZulrahHelpers.nextPoisonDamage(6));
        assertEquals(2, ZulrahHelpers.nextPoisonDamage(10));
    }

    @Test
    void venomDamageStartsAtSixIncrementsByTwoAndCapsAtTwenty() {
        int threshold = ZulrahHelpers.VENOM_THRESHOLD;
        assertEquals(6, ZulrahHelpers.nextPoisonDamage(threshold), "venom starts at 6 at the threshold");
        assertEquals(8, ZulrahHelpers.nextPoisonDamage(threshold + 1), "each varp step adds 2 damage");
        assertEquals(ZulrahHelpers.VENOM_MAXIMUM_DAMAGE, ZulrahHelpers.nextPoisonDamage(threshold + 50),
                "venom damage is capped at the maximum");
    }

    @Test
    void antiVenomThresholdBoundaryIsDamageGreaterThanTen() {
        int threshold = ZulrahHelpers.VENOM_THRESHOLD;
        // AntiVenomAction drinks when poison >= threshold AND nextPoisonDamage > 10.
        assertEquals(10, ZulrahHelpers.nextPoisonDamage(threshold + 2), "damage 10 does not yet exceed the drink threshold");
        assertEquals(12, ZulrahHelpers.nextPoisonDamage(threshold + 3), "damage 12 crosses the drink threshold");
        assertTrue(ZulrahHelpers.nextPoisonDamage(threshold + 3) > 10);
        assertFalse(ZulrahHelpers.nextPoisonDamage(threshold + 2) > 10);
    }
}
