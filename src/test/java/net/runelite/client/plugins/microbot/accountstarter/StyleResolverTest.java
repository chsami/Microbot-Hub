package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.StyleResolver;
import org.junit.jupiter.api.Test;

class StyleResolverTest {
    private static final String[] LONGSWORD_LABELS = {"Chop", "Slash", "Lunge", "Block"};
    private static final String[] LONGSWORD_TYPES = {"Accurate", "Aggressive", "Controlled", "Defensive"};
    private static final String[] UNARMED_LABELS = {"Punch", "Kick", "", "Block"};
    private static final String[] UNARMED_TYPES = {"Accurate", "Aggressive", "Other", "Defensive"};
    private static final String[] WHIP_LABELS = {"Flick", "Lash", "", "Deflect"};
    private static final String[] WHIP_TYPES = {"Accurate", "Controlled", "Other", "Defensive"};
    private static final String[] BOW_LABELS = {"Accurate", "Rapid", "", "Longrange"};
    private static final String[] BOW_TYPES = {"Ranging", "Ranging", "Other", "Longrange"};
    private static final String[] STAFF_TYPES = {"Accurate", "Aggressive", "Defensive", "Other", "Casting", "Defensive"};
    private static final Set<Skill> NONE = EnumSet.noneOf(Skill.class);
    private static final Set<Skill> DEF = EnumSet.of(Skill.DEFENCE);

    @Test
    void rapidAndAccurateAreToldApartByLabel() {
        assertEquals(1, StyleResolver.indexFor(Action.RANGED_RAPID, BOW_LABELS, BOW_TYPES, DEF));
        assertEquals(0, StyleResolver.indexFor(Action.RANGED_ACCURATE, BOW_LABELS, BOW_TYPES, DEF));
        assertTrue(StyleResolver.safe(1, 0, BOW_TYPES, Action.RANGED_RAPID, DEF));
    }

    @Test
    void longswordTable() {
        assertEquals(0, StyleResolver.indexFor(Action.MELEE_ACCURATE, LONGSWORD_LABELS, LONGSWORD_TYPES, DEF));
        assertEquals(1, StyleResolver.indexFor(Action.MELEE_AGGRESSIVE, LONGSWORD_LABELS, LONGSWORD_TYPES, DEF));
        assertEquals(3, StyleResolver.indexFor(Action.MELEE_DEFENSIVE, LONGSWORD_LABELS, LONGSWORD_TYPES, NONE));
    }

    @Test
    void lungeIsControlledNotAggressive() {
        assertFalse(StyleResolver.safe(2, 0, LONGSWORD_TYPES, Action.MELEE_AGGRESSIVE, DEF));
        assertTrue(StyleResolver.safe(1, 0, LONGSWORD_TYPES, Action.MELEE_AGGRESSIVE, DEF));
    }

    @Test
    void unarmedTable() {
        assertEquals(0, StyleResolver.indexFor(Action.MELEE_ACCURATE, UNARMED_LABELS, UNARMED_TYPES, DEF));
        assertEquals(1, StyleResolver.indexFor(Action.MELEE_AGGRESSIVE, UNARMED_LABELS, UNARMED_TYPES, DEF));
        assertEquals(3, StyleResolver.indexFor(Action.MELEE_DEFENSIVE, UNARMED_LABELS, UNARMED_TYPES, NONE));
    }

    @Test
    void controlledOnlyMatchRejectedWhenDefenceProtected() {
        assertEquals(-1, StyleResolver.indexFor(Action.MELEE_AGGRESSIVE, WHIP_LABELS, WHIP_TYPES, DEF));
        assertEquals(-1, StyleResolver.indexFor(Action.MELEE_CONTROLLED, WHIP_LABELS, WHIP_TYPES, DEF));
        assertFalse(StyleResolver.safe(1, 0, WHIP_TYPES, Action.MELEE_AGGRESSIVE, DEF));
    }

    @Test
    void protectedWantedActionNeverResolves() {
        assertEquals(-1, StyleResolver.indexFor(Action.MELEE_DEFENSIVE, LONGSWORD_LABELS, LONGSWORD_TYPES, DEF));
    }

    @Test
    void hiddenButtonIsNotClicked() {
        String[] labels = {"Chop", "", "Lunge", "Block"};
        assertEquals(-1, StyleResolver.indexFor(Action.MELEE_AGGRESSIVE, labels, LONGSWORD_TYPES, DEF));
    }

    @Test
    void bowAccurateAndLongrange() {
        assertEquals(0, StyleResolver.indexFor(Action.RANGED_ACCURATE, BOW_LABELS, BOW_TYPES, DEF));
        assertFalse(StyleResolver.safe(3, 0, BOW_TYPES, Action.RANGED_ACCURATE, DEF));
        assertTrue(StyleResolver.safe(1, 0, BOW_TYPES, Action.RANGED_ACCURATE, DEF));
    }

    @Test
    void autocastModes() {
        assertTrue(StyleResolver.safe(4, 0, STAFF_TYPES, Action.MAGIC_CAST, DEF));
        assertFalse(StyleResolver.safe(4, 1, STAFF_TYPES, Action.MAGIC_CAST, DEF));
        assertFalse(StyleResolver.safe(4, 0, UNARMED_TYPES, Action.MAGIC_CAST, NONE));
    }

    @Test
    void controlledIsNotAccurateEvenWhenNothingProtected() {
        assertFalse(StyleResolver.safe(2, 0, LONGSWORD_TYPES, Action.MELEE_ACCURATE, NONE));
    }

    @Test
    void wrongSkillOrUnknownIsUnsafe() {
        assertFalse(StyleResolver.safe(0, 0, LONGSWORD_TYPES, Action.MELEE_AGGRESSIVE, NONE));
        assertFalse(StyleResolver.safe(0, 0, new String[]{"Mystery"}, Action.MELEE_ACCURATE, NONE));
        assertFalse(StyleResolver.safe(0, 0, LONGSWORD_TYPES, Action.MELEE_ACCURATE, EnumSet.of(Skill.HITPOINTS)));
    }
}
