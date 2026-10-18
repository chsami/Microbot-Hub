package net.runelite.client.plugins.microbot.farmtreerun;

import net.runelite.api.ObjectComposition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FarmTreeRunPatchEmptyTest {

    private static ObjectComposition named(String name) {
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getName()).thenReturn(name);
        return composition;
    }

    @Test
    void nullCompositionIsTreatedAsEmptyInsteadOfThrowing() {
        assertTrue(FarmTreeRunScript.isEmptyPatchComposition(null));
    }

    @Test
    void nullNameIsTreatedAsEmpty() {
        assertTrue(FarmTreeRunScript.isEmptyPatchComposition(named(null)));
    }

    @Test
    void patchNameIsEmpty() {
        assertTrue(FarmTreeRunScript.isEmptyPatchComposition(named("Tree patch")));
        assertTrue(FarmTreeRunScript.isEmptyPatchComposition(named("Fruit Tree Patch")));
    }

    @Test
    void grownTreeIsNotEmpty() {
        assertFalse(FarmTreeRunScript.isEmptyPatchComposition(named("Magic tree")));
        assertFalse(FarmTreeRunScript.isEmptyPatchComposition(named("Papaya tree")));
    }
}
