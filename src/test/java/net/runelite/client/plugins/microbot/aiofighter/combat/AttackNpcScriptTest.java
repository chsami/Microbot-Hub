package net.runelite.client.plugins.microbot.aiofighter.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AttackNpcScriptTest {

    @Test
    void holdsAttackWhileInCombatWhenAntibanCooldownNeverStarts() {
        assertTrue(AttackNpcScript.shouldHoldAttack(true, true, 0.1, false));
        assertTrue(AttackNpcScript.shouldHoldAttack(true, true, 1.0, false));
    }

    @Test
    void holdsAttackWhileInCombatWithAntibanDisabled() {
        assertTrue(AttackNpcScript.shouldHoldAttack(true, false, 0.0, false));
    }

    @Test
    void holdsAttackDuringActiveCooldown() {
        assertTrue(AttackNpcScript.shouldHoldAttack(false, true, 1.0, true));
    }

    @Test
    void ignoresStaleCooldownFlagWhenAntibanCannotClearIt() {
        assertFalse(AttackNpcScript.shouldHoldAttack(false, false, 1.0, true));
        assertFalse(AttackNpcScript.shouldHoldAttack(false, true, 0.0, true));
    }

    @Test
    void attacksWhenIdleAndNoCooldown() {
        assertFalse(AttackNpcScript.shouldHoldAttack(false, true, 1.0, false));
        assertFalse(AttackNpcScript.shouldHoldAttack(false, false, 0.0, false));
    }
}
