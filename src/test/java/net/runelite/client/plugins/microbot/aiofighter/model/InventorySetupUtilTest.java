package net.runelite.client.plugins.microbot.aiofighter.model;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

public final class InventorySetupUtilTest {

    private final AIOFighterConfig previousConfig = InventorySetupUtil.config;

    @AfterEach
    void restoreConfig() {
        InventorySetupUtil.config = previousConfig;
    }

    @Test
    void determiningSetupWithoutAnySlayerSetupsDoesNotThrow() {
        InventorySetupUtil.config = mock(AIOFighterConfig.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            assertDoesNotThrow(() -> InventorySetupUtil.determineInventorySetup("Goblins"));
            assertDoesNotThrow(() -> InventorySetupUtil.determineInventorySetup("Not a slayer monster"));
            microbot.verify(Microbot::getConfigManager, never());
        }
    }
}
