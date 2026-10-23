package net.runelite.client.plugins.microbot.aiofighter;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public final class AIOFighterPluginSetConfigTest {

    @Test
    void settingNullCenterIsIgnoredWhenPlayerPositionUnavailable() {
        ConfigManager configManager = mock(ConfigManager.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getConfigManager).thenReturn(configManager);
            assertDoesNotThrow(() -> AIOFighterPlugin.setCenter(null));
            verifyNoInteractions(configManager);
        }
    }

    @Test
    void settingCenterWritesWorldPointToConfig() {
        ConfigManager configManager = mock(ConfigManager.class);
        WorldPoint center = new WorldPoint(3200, 3200, 0);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getConfigManager).thenReturn(configManager);
            AIOFighterPlugin.setCenter(center);
            verify(configManager).setConfiguration(eq(AIOFighterConfig.GROUP), eq("centerLocation"), eq(center));
        }
    }
}
