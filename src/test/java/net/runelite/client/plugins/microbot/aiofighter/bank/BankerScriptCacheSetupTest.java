package net.runelite.client.plugins.microbot.aiofighter.bank;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public final class BankerScriptCacheSetupTest {

    private static InventorySetup setup(String name) {
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn(name);
        return setup;
    }

    @Test
    void findsSetupByNameIgnoringCase() {
        InventorySetup cache = setup("Cache");
        assertSame(cache, BankerScript.findSetupByName(Arrays.asList(setup("Melee"), null, setup(null), cache), "cache"));
    }

    @Test
    void returnsNullWhenSetupNotRegisteredYet() {
        assertNull(BankerScript.findSetupByName(Collections.singletonList(setup("Melee")), "cache"));
        assertNull(BankerScript.findSetupByName(Collections.emptyList(), "cache"));
        assertNull(BankerScript.findSetupByName(null, "cache"));
        assertNull(BankerScript.findSetupByName(Collections.singletonList(setup("cache")), null));
    }

    @Test
    void settingNullInventorySetupIsIgnored() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            assertDoesNotThrow(() -> AIOFighterPlugin.setCurrentSlayerInventorySetup(null));
            microbot.verify(Microbot::getConfigManager, never());
        }
    }
}
