package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.AnagramClueTask;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AnagramClueTaskTest {

    private static final WorldPoint LOCATION = new WorldPoint(3222, 3218, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Dialogue> dialogue;
    private MockedStatic<Rs2Keyboard> keyboard;
    private ClueScrollPlugin clueScrollPlugin;
    private AnagramClue clue;

    @BeforeEach
    void setUp() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.of(LOCATION));
        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        dialogue = mockStatic(Rs2Dialogue.class);
        dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(true);
        keyboard = mockStatic(Rs2Keyboard.class);

        clueScrollPlugin = mock(ClueScrollPlugin.class);
        clue = mock(AnagramClue.class);
        when(clue.getLocation(clueScrollPlugin)).thenReturn(LOCATION);
    }

    @AfterEach
    void tearDown() {
        keyboard.close();
        dialogue.close();
        microbot.close();
    }

    @Test
    void anagramWithoutAnswerClicksThroughDialogueWithoutTyping() throws Exception {
        when(clue.getAnswer(clueScrollPlugin)).thenReturn(null);
        CompletableFuture<Boolean> result = runDialogueTick();

        keyboard.verify(() -> Rs2Keyboard.typeString(any()), never());
        keyboard.verify(Rs2Keyboard::enter, never());
        assertTrue(result.isDone());
        assertTrue(result.join());
    }

    @Test
    void anagramWithBlankAnswerDoesNotType() throws Exception {
        when(clue.getAnswer(clueScrollPlugin)).thenReturn("  ");
        CompletableFuture<Boolean> result = runDialogueTick();

        keyboard.verify(() -> Rs2Keyboard.typeString(any()), never());
        assertTrue(result.join());
    }

    @Test
    void anagramWithAnswerTypesItAndSubmits() throws Exception {
        when(clue.getAnswer(clueScrollPlugin)).thenReturn("6");
        CompletableFuture<Boolean> result = runDialogueTick();

        keyboard.verify(() -> Rs2Keyboard.typeString("6"));
        keyboard.verify(Rs2Keyboard::enter);
        assertTrue(result.join());
    }

    private CompletableFuture<Boolean> runDialogueTick() throws Exception {
        AnagramClueTask task = new AnagramClueTask(mock(Client.class), clue, clueScrollPlugin, null,
                mock(EventBus.class), new DirectExecutor());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        task.setFuture(result);
        setState(task, "HANDLING_DIALOGUE");
        task.onGameTick(new GameTick());
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setState(AnagramClueTask task, String name) throws Exception {
        Field field = AnagramClueTask.class.getDeclaredField("state");
        field.setAccessible(true);
        field.set(task, Enum.valueOf((Class<Enum>) field.getType(), name));
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
