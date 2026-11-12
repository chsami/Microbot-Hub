package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ArenaInputLeaseTest
{
    private boolean before;

    @BeforeEach public void remember() { before = InputArbiter.isDisabled(); }
    @AfterEach public void restore() { InputArbiter.setDisabled(before); }

    @Test public void theSavedSettingIsNeverWritten() {
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyDisableInputYielding)).thenReturn("false");
        InputArbiter.setDisabled(false);

        ArenaInputLease lease = new ArenaInputLease(configs);
        assertTrue(InputArbiter.isDisabled());
        assertTrue(ArenaInputLease.active());
        InputArbiter.setDisabled(false);
        lease.enforce();
        assertTrue(InputArbiter.isDisabled(), "re-applied in memory when the client resets it");
        lease.close();

        assertFalse(InputArbiter.isDisabled(), "back to the player's saved choice");
        assertFalse(ArenaInputLease.active());
        verify(configs, never()).setConfiguration(anyString(), anyString(), any(Object.class));
        verify(configs, never()).setConfiguration(anyString(), anyString(), anyString());
        verify(configs, never()).unsetConfiguration(anyString(), anyString());
    }

    @Test public void aSettingChangedDuringTheRunIsWhatCloseRestores() {
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyDisableInputYielding)).thenReturn("false");
        InputArbiter.setDisabled(false);
        ArenaInputLease lease = new ArenaInputLease(configs);
        when(configs.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyDisableInputYielding)).thenReturn("true");
        lease.close();
        assertTrue(InputArbiter.isDisabled());
    }

    @Test public void withNothingSavedTheValueFromBeforeTheRunComesBack() {
        ConfigManager configs = mock(ConfigManager.class);
        InputArbiter.setDisabled(true);
        ArenaInputLease first = new ArenaInputLease(configs);
        ArenaInputLease second = new ArenaInputLease(configs);
        first.close();
        assertTrue(ArenaInputLease.active(), "held until the last owner closes");
        second.close();
        second.close();
        assertTrue(InputArbiter.isDisabled());
        assertFalse(ArenaInputLease.active());
    }
}
