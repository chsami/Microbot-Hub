package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.CipherClueTask;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CipherClueTaskTest {

    private static final WorldPoint LOCATION = new WorldPoint(3229, 6045, 0);

    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Walker> walker;
    private ClueScrollPlugin clueScrollPlugin;
    private CipherClue clue;

    @BeforeEach
    void setUp() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
            onClientThread.set(true);
            try {
                return Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call());
            } finally {
                onClientThread.set(false);
            }
        });
        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        walker = mockStatic(Rs2Walker.class);
        walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class))).thenReturn(true);

        clueScrollPlugin = mock(ClueScrollPlugin.class);
        clue = mock(CipherClue.class);
    }

    @AfterEach
    void tearDown() {
        walker.close();
        microbot.close();
    }

    @Test
    void locationIsResolvedOnClientThreadBeforeWalking() {
        when(clue.getLocation(clueScrollPlugin)).thenAnswer(invocation -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return LOCATION;
        });

        CompletableFuture<Boolean> result = runTask();

        walker.verify(() -> Rs2Walker.walkTo(LOCATION));
        assertFalse(result.isDone());
    }

    @Test
    void missingLocationFailsTaskWithoutWalking() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(null);

        CompletableFuture<Boolean> result = runTask();

        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    private CompletableFuture<Boolean> runTask() {
        CipherClueTask task = new CipherClueTask(mock(Client.class), clue, clueScrollPlugin, null,
                mock(EventBus.class), new DirectExecutor());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        task.setFuture(result);
        task.run();
        return result;
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
