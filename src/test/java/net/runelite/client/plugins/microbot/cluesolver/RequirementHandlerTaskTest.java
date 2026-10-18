package net.runelite.client.plugins.microbot.cluesolver;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cluesolver.cluetask.RequirementHandlerTask;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RequirementHandlerTaskTest {

    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private final AtomicInteger bankCallsOnClientThread = new AtomicInteger();
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Bank> bank;
    private DeferredExecutor executor;
    private CompletableFuture<Boolean> result;
    private RequirementHandlerTask task;

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
        bank = mockStatic(Rs2Bank.class, invocation -> {
            if (onClientThread.get()) {
                bankCallsOnClientThread.incrementAndGet();
            }
            return invocation.getMethod().getReturnType() == boolean.class ? false : null;
        });

        Player player = mock(Player.class);
        when(player.getWorldLocation()).thenReturn(BankLocation.AL_KHARID.getWorldPoint());
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(player);

        ItemRequirement spade = mock(ItemRequirement.class);
        when(spade.fulfilledBy(any(Item[].class))).thenReturn(false);
        when(spade.getCollectiveName(client)).thenReturn("Spade");

        executor = new DeferredExecutor();
        result = new CompletableFuture<>();
        task = new RequirementHandlerTask(client, Collections.singletonList(spade), mock(EventBus.class), null, null, executor);
        task.setFuture(result);
    }

    @AfterEach
    void tearDown() {
        bank.close();
        microbot.close();
    }

    @Test
    void executeTaskHandsTheBankWorkToTheExecutor() {
        task.run();

        assertEquals(1, executor.queued.size());
        bank.verifyNoInteractions();
        assertFalse(result.isDone());
    }

    @Test
    void nearestBankIsResolvedOutsideTheClientThread() {
        bank.when(Rs2Bank::getNearestBank).thenReturn(BankLocation.AL_KHARID);

        task.run();
        executor.runAll();

        bank.verify(Rs2Bank::getNearestBank);
        bank.verify(Rs2Bank::openBank);
        assertEquals(0, bankCallsOnClientThread.get());
        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void unreachableBankFailsTheTaskWithoutWalking() {
        task.run();
        executor.runAll();

        bank.verify(Rs2Bank::getNearestBank);
        bank.verify(Rs2Bank::walkToBankAndUseBank, never());
        bank.verify(Rs2Bank::openBank, never());
        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void gameTickRunsBankFetchOnTheExecutorAndDropsOverlappingTicks() {
        bank.when(Rs2Bank::isOpen).thenReturn(true);

        task.onGameTick(new GameTick());
        task.onGameTick(new GameTick());

        assertEquals(1, executor.queued.size());
        bank.verify(Rs2Bank::closeBank, never());

        executor.runAll();

        bank.verify(Rs2Bank::closeBank);
        assertTrue(result.join());

        task.onGameTick(new GameTick());
        assertEquals(1, executor.queued.size());
    }

    @Test
    void gameTickDoesNotPlanARouteBeforeABankIsChosen() {
        bank.when(Rs2Bank::isOpen).thenReturn(false);

        task.onGameTick(new GameTick());

        bank.verify(Rs2Bank::getNearestBank, never());
        assertEquals(0, executor.queued.size());
    }

    private static final class DeferredExecutor extends AbstractExecutorService {
        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            while (!queued.isEmpty()) {
                queued.remove(0).run();
            }
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
