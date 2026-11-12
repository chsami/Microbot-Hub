package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DroSlayerWalkerRequirementTest {

    @Test
    void missingEfficientWalkerBlocksStart() {
        PluginManager manager = mock(PluginManager.class);
        Plugin other = mock(Plugin.class);
        when(manager.getPlugins()).thenReturn(List.of(other));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getPluginManager).thenReturn(manager);
            assertFalse(DroSlayerWalkerRequirement.efficientWalkerEnabled());
        }
    }

    @Test
    void noPluginManagerBlocksStart() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getPluginManager).thenReturn(null);
            assertFalse(DroSlayerWalkerRequirement.efficientWalkerEnabled());
        }
    }

    @Test
    void messageTellsUsersToGetTheWalkerFromTheHub() {
        assertTrue(DroSlayerWalkerRequirement.MISSING_MESSAGE.contains("[Dro] Efficient Walker"));
        assertTrue(DroSlayerWalkerRequirement.MISSING_MESSAGE.contains("Microbot Hub"));
    }
}
