package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.HotColdClueTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClueSolverScriptTest {

    private static final long WAIT_SECONDS = 5;

    private ClueSolverScript script;
    private ClueScrollPlugin clueScrollPlugin;
    private ClueSolverOverlay overlay;
    private HotColdClue clue;
    private boolean solverWasRunning;
    private final BlockingQueue<HotColdClueTask> startedTasks = new LinkedBlockingQueue<>();
    private final CountDownLatch stoppedAfterError = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        clue = mock(HotColdClue.class);
        when(clue.getLocation(any(ClueScrollPlugin.class))).thenThrow(new IllegalStateException("boom"));
        clueScrollPlugin = mock(ClueScrollPlugin.class);
        when(clueScrollPlugin.getClue()).thenReturn(clue);
        overlay = mock(ClueSolverOverlay.class);
        doAnswer(invocation -> {
            stoppedAfterError.countDown();
            return null;
        }).when(overlay).updateTaskStatus(HotColdClueTask.STATUS_STOPPED_AFTER_ERROR);
        EventBus eventBus = mock(EventBus.class);
        doAnswer(invocation -> {
            Object subscriber = invocation.getArgument(0);
            if (subscriber instanceof HotColdClueTask) {
                startedTasks.add((HotColdClueTask) subscriber);
            }
            return null;
        }).when(eventBus).register(any());

        script = new ClueSolverScript();
        script.client = mock(Client.class);
        script.eventBus = eventBus;
        script.clueScrollPlugin = clueScrollPlugin;
        script.overlay = overlay;

        solverWasRunning = ClueSolverPlugin.isSolverRunning;
        ClueSolverPlugin.isSolverRunning = true;
    }

    @AfterEach
    void tearDown() {
        script.shutdown();
        ClueSolverPlugin.isSolverRunning = solverWasRunning;
    }

    @Test
    void hotColdErrorDoesNotRestartSameClue() throws InterruptedException {
        failHotColdTask();

        script.processClues();
        script.processClues();

        verify(overlay, times(1)).updateTaskStatus(startsWith("New Clue Detected"));
        assertTrue(startedTasks.isEmpty());
    }

    @Test
    void hotColdErrorAllowsSameClueAgainAfterSolverRestart() throws InterruptedException {
        failHotColdTask();

        script.shutdown();
        script.processClues();

        verify(overlay, times(2)).updateTaskStatus(startsWith("New Clue Detected"));
        assertNotNull(startedTasks.poll(WAIT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void hotColdErrorAllowsNewClueOnceClueChanged() throws InterruptedException {
        failHotColdTask();

        when(clueScrollPlugin.getClue()).thenReturn(null);
        script.processClues();
        when(clueScrollPlugin.getClue()).thenReturn(clue);
        script.processClues();

        verify(overlay, times(2)).updateTaskStatus(startsWith("New Clue Detected"));
        assertNotNull(startedTasks.poll(WAIT_SECONDS, TimeUnit.SECONDS));
    }

    private void failHotColdTask() throws InterruptedException {
        script.processClues();

        HotColdClueTask task = startedTasks.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(task);
        task.onGameTick(new GameTick());

        assertTrue(stoppedAfterError.await(WAIT_SECONDS, TimeUnit.SECONDS));
    }
}
