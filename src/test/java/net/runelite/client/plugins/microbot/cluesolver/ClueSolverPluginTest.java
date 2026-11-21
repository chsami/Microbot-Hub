package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClueSolverPluginTest {

    private ClueSolverPlugin plugin;
    private ClueSolverScript script;
    private ClueSolverConfig config;
    private ConfigManager configManager;
    private boolean solverWasRunning;

    @BeforeEach
    void setUp() {
        script = mock(ClueSolverScript.class);
        config = mock(ClueSolverConfig.class);
        configManager = mock(ConfigManager.class);

        plugin = new ClueSolverPlugin();
        plugin.clueSolverScript = script;
        plugin.clueSolverConfig = config;
        plugin.configManager = configManager;

        solverWasRunning = ClueSolverPlugin.isSolverRunning;
        ClueSolverPlugin.isSolverRunning = false;
    }

    @AfterEach
    void tearDown() {
        ClueSolverPlugin.isSolverRunning = solverWasRunning;
    }

    @Test
    void enablingSettingWhileRunningKeepsSolverRunning() {
        when(config.enableClueSolver()).thenReturn(true);

        plugin.onConfigChanged(enableChanged());
        plugin.onConfigChanged(enableChanged());

        assertTrue(ClueSolverPlugin.isSolverRunning);
        verify(script, times(1)).start();
        verify(script, never()).shutdown();
    }

    @Test
    void disablingSettingStopsSolver() {
        when(config.enableClueSolver()).thenReturn(true);
        plugin.onConfigChanged(enableChanged());
        when(config.enableClueSolver()).thenReturn(false);

        plugin.onConfigChanged(enableChanged());

        assertFalse(ClueSolverPlugin.isSolverRunning);
        verify(script, times(1)).shutdown();
    }

    @Test
    void unrelatedConfigChangeLeavesSolverAlone() {
        when(config.enableClueSolver()).thenReturn(true);
        ConfigChanged other = new ConfigChanged();
        other.setGroup("cluesolver");
        other.setKey("somethingElse");
        ConfigChanged otherGroup = new ConfigChanged();
        otherGroup.setGroup("other");
        otherGroup.setKey("enableClueSolver");

        plugin.onConfigChanged(other);
        plugin.onConfigChanged(otherGroup);

        assertFalse(ClueSolverPlugin.isSolverRunning);
        verify(script, never()).start();
    }

    @Test
    void buttonToggleWritesSettingAndMatchesLabel() {
        plugin.setSolverEnabled(true);

        verify(configManager).setConfiguration("cluesolver", "enableClueSolver", true);
        assertTrue(ClueSolverPlugin.isSolverRunning);
        assertEquals("Stop", ClueSolverOverlay.buttonLabel(ClueSolverPlugin.isSolverRunning));

        plugin.setSolverEnabled(false);

        verify(configManager).setConfiguration("cluesolver", "enableClueSolver", false);
        assertFalse(ClueSolverPlugin.isSolverRunning);
        assertEquals("Start", ClueSolverOverlay.buttonLabel(ClueSolverPlugin.isSolverRunning));
        verify(script, times(1)).start();
        verify(script, times(1)).shutdown();
    }

    private static ConfigChanged enableChanged() {
        ConfigChanged event = new ConfigChanged();
        event.setGroup("cluesolver");
        event.setKey("enableClueSolver");
        return event;
    }
}
