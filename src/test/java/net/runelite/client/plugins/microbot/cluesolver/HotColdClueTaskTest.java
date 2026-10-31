package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.HotColdClueTask;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HotColdClueTaskTest {

    private static final WorldPoint DIG_SPOT = new WorldPoint(3110, 3260, 0);
    private static final WorldPoint ELSEWHERE = new WorldPoint(3100, 3250, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Inventory> inventory;
    private ClueScrollPlugin clueScrollPlugin;
    private HotColdClue clue;
    private EventBus eventBus;
    private Client client;
    private Player player;
    private boolean solverWasRunning;
    private final List<String> statuses = new ArrayList<>();
    private final List<Rs2NpcModel> npcs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
        Rs2NpcCache npcCache = mock(Rs2NpcCache.class);
        when(npcCache.query()).thenAnswer(invocation -> new Rs2NpcQueryable());
        when(npcCache.getStream()).thenAnswer(invocation -> new ArrayList<>(npcs).stream());
        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(npcCache);
        walker = mockStatic(Rs2Walker.class);
        inventory = mockStatic(Rs2Inventory.class);

        clueScrollPlugin = mock(ClueScrollPlugin.class);
        clue = mock(HotColdClue.class);
        when(clueScrollPlugin.getClue()).thenReturn(clue);
        eventBus = mock(EventBus.class);
        client = mock(Client.class);
        player = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(ELSEWHERE);

        solverWasRunning = ClueSolverPlugin.isSolverRunning;
        ClueSolverPlugin.isSolverRunning = true;
    }

    @AfterEach
    void tearDown() {
        ClueSolverPlugin.isSolverRunning = solverWasRunning;
        inventory.close();
        walker.close();
        microbot.close();
    }

    @Test
    void runRegistersOnceWithoutResubmittingItself() {
        ExecutorService executor = mock(ExecutorService.class);
        HotColdClueTask task = newTask(executor);
        CompletableFuture<Boolean> result = start(task);

        verify(eventBus, times(1)).register(task);
        verifyNoInteractions(executor);
        assertFalse(result.isDone());
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE), statuses);
    }

    @Test
    void unknownDigSpotKeepsWaitingWithoutWalking() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(null);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        verify(eventBus, never()).unregister(task);
        assertFalse(result.isDone());
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE), statuses);
    }

    @Test
    void knownDigSpotWalksThereDigsAndSucceedsOnceClueIsGone() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        walker.when(() -> Rs2Walker.walkTo(DIG_SPOT, 0)).thenReturn(true);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(DIG_SPOT, 0));
        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"));
        assertFalse(result.isDone());
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_WALKING,
                HotColdClueTask.STATUS_DUG), statuses);

        when(clueScrollPlugin.getClue()).thenReturn(null);
        task.onGameTick(new GameTick());

        verify(eventBus).unregister(task);
        assertTrue(result.join());
    }

    @Test
    void missingSpadeWaitsWithoutWalkingAndDigsOnceSpadeIsAdded() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        walker.when(() -> Rs2Walker.walkTo(DIG_SPOT, 0)).thenReturn(true);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(false);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        verify(eventBus, never()).unregister(task);
        assertFalse(result.isDone());
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_NO_SPADE), statuses);

        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(DIG_SPOT, 0));
        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"));
        assertFalse(result.isDone());
    }

    @Test
    void unchangedClueAfterDigWaitsThenDigsAgain() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        when(player.getWorldLocation()).thenReturn(DIG_SPOT);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        for (int i = 1; i < HotColdClueTask.DIG_RESULT_TICKS; i++) {
            task.onGameTick(new GameTick());
        }
        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"), times(1));

        task.onGameTick(new GameTick());

        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"), times(2));
        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        assertFalse(result.isDone());
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_DUG,
                HotColdClueTask.STATUS_DUG_AGAIN), statuses);
    }

    @Test
    void attackingWizardAfterDigBlocksDigUntilItIsGone() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        when(player.getWorldLocation()).thenReturn(DIG_SPOT);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        Rs2NpcModel wizard = mock(Rs2NpcModel.class);
        when(wizard.isInteractingWithPlayer()).thenReturn(true);
        npcs.add(wizard);
        for (int i = 0; i < HotColdClueTask.DIG_RESULT_TICKS + 3; i++) {
            task.onGameTick(new GameTick());
        }

        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"), times(1));
        assertEquals(HotColdClueTask.STATUS_UNDER_ATTACK, statuses.get(statuses.size() - 1));

        npcs.clear();
        task.onGameTick(new GameTick());

        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"), times(2));
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_DUG,
                HotColdClueTask.STATUS_UNDER_ATTACK, HotColdClueTask.STATUS_DUG_AGAIN), statuses);
        assertFalse(result.isDone());
    }

    @Test
    void clueSolvedWhileUnderAttackAfterDigSucceeds() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        when(player.getWorldLocation()).thenReturn(DIG_SPOT);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        Rs2NpcModel wizard = mock(Rs2NpcModel.class);
        when(wizard.isInteractingWithPlayer()).thenReturn(true);
        npcs.add(wizard);
        for (int i = 0; i < HotColdClueTask.DIG_RESULT_TICKS; i++) {
            task.onGameTick(new GameTick());
        }
        when(clueScrollPlugin.getClue()).thenReturn(null);
        task.onGameTick(new GameTick());

        verify(eventBus).unregister(task);
        assertTrue(result.join());
    }

    @Test
    void movedDigSpotIsFollowed() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenReturn(true);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(false);
        HotColdClueTask task = newTask(new DirectExecutor());
        start(task);

        task.onGameTick(new GameTick());
        when(clue.getLocation(clueScrollPlugin)).thenReturn(null);
        task.onGameTick(new GameTick());
        WorldPoint newSpot = new WorldPoint(3120, 3270, 0);
        when(clue.getLocation(clueScrollPlugin)).thenReturn(newSpot);
        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(DIG_SPOT, 0), times(1));
        walker.verify(() -> Rs2Walker.walkTo(newSpot, 0), times(1));
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_WALKING,
                HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_WALKING), statuses);
    }

    @Test
    void unreachableDigSpotKeepsReasonAndRetriesOnlyAfterPlayerMoves() {
        String cannotReach = "Hot Cold clue: can't find a path to the dig spot at 3110, 3260. Walk closer and the solver will try again.";
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        walker.when(() -> Rs2Walker.walkTo(DIG_SPOT, 0)).thenReturn(false);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(DIG_SPOT, 0), times(1));
        microbot.verify(() -> Microbot.log(cannotReach), times(1));
        microbot.verify(() -> Microbot.log(HotColdClueTask.START_MESSAGE), times(1));
        inventory.verify(() -> Rs2Inventory.interact(anyInt(), anyString()), never());
        assertEquals(List.of(HotColdClueTask.STATUS_WAITING_FOR_DEVICE, HotColdClueTask.STATUS_WALKING,
                HotColdClueTask.STATUS_CANNOT_REACH), statuses);
        verify(eventBus, never()).unregister(task);
        assertFalse(result.isDone());

        when(player.getWorldLocation()).thenReturn(new WorldPoint(3105, 3255, 0));
        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(DIG_SPOT, 0), times(2));
        microbot.verify(() -> Microbot.log(cannotReach), times(1));
        assertEquals(HotColdClueTask.STATUS_CANNOT_REACH, statuses.get(statuses.size() - 1));
        assertFalse(result.isDone());

        walker.when(() -> Rs2Walker.walkTo(DIG_SPOT, 0)).thenReturn(true);
        inventory.when(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig")).thenReturn(true);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3108, 3258, 0));
        task.onGameTick(new GameTick());

        inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"));
        assertEquals(HotColdClueTask.STATUS_DUG, statuses.get(statuses.size() - 1));
    }

    @Test
    void solverStoppedDuringWalkDoesNotDig() {
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        walker.when(() -> Rs2Walker.walkTo(DIG_SPOT, 0)).thenAnswer(invocation -> {
            ClueSolverPlugin.isSolverRunning = false;
            return true;
        });
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());

        inventory.verify(() -> Rs2Inventory.interact(anyInt(), anyString()), never());
        assertFalse(statuses.contains(HotColdClueTask.STATUS_CANNOT_REACH));
        verify(eventBus).unregister(task);
        assertFalse(result.join());
    }

    @Test
    void replacedClueEndsTask() {
        when(clueScrollPlugin.getClue()).thenReturn(mock(ClueScroll.class));
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());

        verify(eventBus).unregister(task);
        assertFalse(result.join());
    }

    @Test
    void stoppedSolverEndsTask() {
        ClueSolverPlugin.isSolverRunning = false;
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());

        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        verify(eventBus).unregister(task);
        assertFalse(result.join());
    }

    @Test
    void completedTaskUnregistersOnceAndIgnoresLaterTicks() {
        ClueSolverPlugin.isSolverRunning = false;
        HotColdClueTask task = newTask(new DirectExecutor());
        CompletableFuture<Boolean> result = start(task);

        task.onGameTick(new GameTick());
        ClueSolverPlugin.isSolverRunning = true;
        when(clue.getLocation(clueScrollPlugin)).thenReturn(DIG_SPOT);
        inventory.when(() -> Rs2Inventory.contains("Spade")).thenReturn(true);
        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());

        verify(eventBus, times(1)).unregister(task);
        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        assertFalse(result.join());
    }

    private HotColdClueTask newTask(ExecutorService executor) {
        return new HotColdClueTask(client, clue, clueScrollPlugin, null, eventBus, executor, statuses::add);
    }

    private CompletableFuture<Boolean> start(HotColdClueTask task) {
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
