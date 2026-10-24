package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.AnagramClueTask;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnagramClueTaskTest {

    private static final WorldPoint LOCATION = new WorldPoint(3222, 3218, 0);

    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Dialogue> dialogue;
    private MockedStatic<Rs2Keyboard> keyboard;
    private Client client;
    private ClueScrollPlugin clueScrollPlugin;
    private AnagramClue clue;

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
        dialogue = mockStatic(Rs2Dialogue.class);
        dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(true);
        keyboard = mockStatic(Rs2Keyboard.class);

        Player player = mock(Player.class);
        when(player.getWorldLocation()).thenReturn(LOCATION);
        client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(player);

        clueScrollPlugin = mock(ClueScrollPlugin.class);
        clue = mock(AnagramClue.class);
        when(clue.getLocation(clueScrollPlugin)).thenReturn(LOCATION);
        when(clue.getObjectId()).thenReturn(-1);
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
        CompletableFuture<Boolean> result = runTick("HANDLING_DIALOGUE");

        keyboard.verify(() -> Rs2Keyboard.typeString(any()), never());
        keyboard.verify(Rs2Keyboard::enter, never());
        assertTrue(result.isDone());
        assertTrue(result.join());
    }

    @Test
    void anagramWithBlankAnswerDoesNotType() throws Exception {
        when(clue.getAnswer(clueScrollPlugin)).thenReturn("  ");
        CompletableFuture<Boolean> result = runTick("HANDLING_DIALOGUE");

        keyboard.verify(() -> Rs2Keyboard.typeString(any()), never());
        assertTrue(result.join());
    }

    @Test
    void anagramWithAnswerTypesItAndSubmits() throws Exception {
        when(clue.getAnswer(clueScrollPlugin)).thenReturn("6");
        CompletableFuture<Boolean> result = runTick("HANDLING_DIALOGUE");

        keyboard.verify(() -> Rs2Keyboard.typeString("6"));
        keyboard.verify(Rs2Keyboard::enter);
        assertTrue(result.join());
    }

    @Test
    void answerIsResolvedOnClientThread() throws Exception {
        when(clue.getAnswer(clueScrollPlugin)).thenAnswer(invocation -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return "51";
        });
        CompletableFuture<Boolean> result = runTick("HANDLING_DIALOGUE");

        keyboard.verify(() -> Rs2Keyboard.typeString("51"));
        assertTrue(result.join());
    }

    @Test
    void npcNameIsResolvedOnClientThreadWhenArriving() throws Exception {
        stubNpcProvider();
        when(clue.getNpcName(clueScrollPlugin)).thenAnswer(invocation -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return "Nieve";
        });
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.withName("Nieve")).thenReturn(query);
        Rs2NpcCache npcCache = mock(Rs2NpcCache.class);
        when(npcCache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(npcCache);

        CompletableFuture<Boolean> result = runTick("WALKING_TO_LOCATION");

        verify(query).withName("Nieve");
        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void missingNpcNameFailsWithoutQueryingNpcs() throws Exception {
        stubNpcProvider();
        when(clue.getNpcName(clueScrollPlugin)).thenReturn(null);

        CompletableFuture<Boolean> result = runTick("INTERACTING_WITH_NPC");

        microbot.verify(Microbot::getRs2NpcCache, never());
        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void locationIsResolvedOnClientThread() throws Exception {
        when(clue.getLocation(clueScrollPlugin)).thenAnswer(invocation -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return LOCATION;
        });
        try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            walker.when(() -> Rs2Walker.walkTo(LOCATION, 2)).thenReturn(true);
            AnagramClueTask task = new AnagramClueTask(client, clue, clueScrollPlugin, null,
                    mock(EventBus.class), new DirectExecutor());
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            task.setFuture(result);

            task.run();

            walker.verify(() -> Rs2Walker.walkTo(LOCATION, 2));
            assertFalse(result.isDone());
        }
    }

    @Test
    void missingLocationFailsWithoutWalking() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(null);
        try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            AnagramClueTask task = new AnagramClueTask(client, clue, clueScrollPlugin, null,
                    mock(EventBus.class), new DirectExecutor());
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            task.setFuture(result);

            task.run();

            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
            assertTrue(result.isDone());
            assertFalse(result.join());
        }
    }

    @SuppressWarnings("unchecked")
    private void stubNpcProvider() {
        when(clue.getNpcProvider()).thenReturn(mock(Function.class));
    }

    private CompletableFuture<Boolean> runTick(String state) throws Exception {
        AnagramClueTask task = new AnagramClueTask(client, clue, clueScrollPlugin, null,
                mock(EventBus.class), new DirectExecutor());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        task.setFuture(result);
        setState(task, state);
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
