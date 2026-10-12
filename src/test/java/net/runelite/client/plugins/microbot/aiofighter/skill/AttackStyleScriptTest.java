package net.runelite.client.plugins.microbot.aiofighter.skill;

import net.runelite.api.widgets.WidgetInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AttackStyleScriptTest {

    @Test
    void pickComponentReturnsNullWhenNoStyleMatches() {
        assertNull(AttackStyleScript.pickComponent(Collections.emptyList(), new Random(1)));
        assertNull(AttackStyleScript.pickComponent(null, new Random(1)));
    }

    @Test
    void pickComponentReturnsOnlyCandidate() {
        assertSame(WidgetInfo.COMBAT_STYLE_TWO,
                AttackStyleScript.pickComponent(Collections.singletonList(WidgetInfo.COMBAT_STYLE_TWO), new Random(1)));
    }

    @Test
    void pickComponentReturnsOneOfCandidates() {
        List<WidgetInfo> candidates = Arrays.asList(WidgetInfo.COMBAT_STYLE_ONE, WidgetInfo.COMBAT_STYLE_THREE);
        for (int seed = 0; seed < 20; seed++) {
            assertTrue(candidates.contains(AttackStyleScript.pickComponent(candidates, new Random(seed))));
        }
    }
}
