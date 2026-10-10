package net.runelite.client.plugins.microbot.aiofighter.bank;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BankerScriptSetupResolutionTest {

    @Test
    void resolvesSetupByNameIgnoringCase() {
        assertTrue(BankerScript.isSetupResolvable(Arrays.asList("Melee", "Ranged"), "melee"));
    }

    @Test
    void fallsBackToDefaultSetupLikeCore() {
        assertTrue(BankerScript.isSetupResolvable(Arrays.asList("Ranged", "default"), "Melee"));
    }

    @Test
    void renamedOrDeletedSetupIsNotResolvable() {
        assertFalse(BankerScript.isSetupResolvable(Arrays.asList("Ranged", "Default"), "Melee"));
    }

    @Test
    void unloadedSetupsAreNotResolvable() {
        assertFalse(BankerScript.isSetupResolvable(null, "Melee"));
        assertFalse(BankerScript.isSetupResolvable(Collections.emptyList(), "Melee"));
        assertFalse(BankerScript.isSetupResolvable(Arrays.asList(null, "Ranged"), "Melee"));
        assertFalse(BankerScript.isSetupResolvable(Collections.singletonList("Melee"), null));
    }
}
