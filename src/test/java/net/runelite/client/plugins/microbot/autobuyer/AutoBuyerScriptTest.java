package net.runelite.client.plugins.microbot.autobuyer;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoBuyerScriptTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> rs2Player;
    private MockedStatic<Rs2GrandExchange> grandExchange;
    private List<Plugin> plugins;
    private AutoBuyerConfig config;
    private ProbeScript script;

    @BeforeEach
    void setUp() throws Exception {
        resetStatics();
        plugins = new ArrayList<>();
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugins()).thenReturn(plugins);
        ClientThread clientThread = mock(ClientThread.class);

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        microbot.when(Microbot::getPluginManager).thenReturn(pluginManager);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        rs2Player = mockStatic(Rs2Player.class);
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(false);
        grandExchange = mockStatic(Rs2GrandExchange.class);
        grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(true);

        config = mock(AutoBuyerConfig.class);
        when(config.buyQuest()).thenReturn(false);
        when(config.listOfItemsToBuy()).thenReturn("bronze arrow[5]");
        when(config.pricePerItem()).thenReturn(Percentage.PERCENT_10);

        script = new ProbeScript();
    }

    @AfterEach
    void tearDown() throws Exception {
        Thread.interrupted();
        grandExchange.close();
        rs2Player.close();
        microbot.close();
        resetStatics();
    }

    @Test
    void stoppingBeforeAnyListWasParsedDoesNotThrow() {
        assertDoesNotThrow(() -> script.shutdown());
    }

    @Test
    void buyQuestWithoutQuestHelperStopsWithoutBuyingTheManualList() {
        when(config.buyQuest()).thenReturn(true);

        script.run(config);
        script.tick();

        assertTrue(script.future.cancelled, "the script must stop when no quest can be read");
        verify(config, never()).listOfItemsToBuy();
        grandExchange.verify(() -> Rs2GrandExchange.processOffer(any(GrandExchangeRequest.class)), never());
    }

    @Test
    void buyQuestWithNoQuestSelectedStopsWithoutBuyingTheManualList() {
        when(config.buyQuest()).thenReturn(true);
        QuestHelperPlugin questHelper = mock(QuestHelperPlugin.class);
        when(questHelper.getSelectedQuest()).thenReturn(null);
        plugins.add(questHelper);

        script.run(config);
        script.tick();

        assertTrue(script.future.cancelled, "the script must stop when no quest is selected");
        verify(config, never()).listOfItemsToBuy();
        grandExchange.verify(() -> Rs2GrandExchange.processOffer(any(GrandExchangeRequest.class)), never());
    }

    @Test
    void questDeselectedAfterTheListWasBuiltKeepsBuyingTheRemainingItems() throws Exception {
        when(config.buyQuest()).thenReturn(true);
        QuestHelperPlugin questHelper = mock(QuestHelperPlugin.class);
        when(questHelper.getSelectedQuest()).thenReturn(null);
        plugins.add(questHelper);
        Map<String, Integer> remaining = new HashMap<>();
        remaining.put("bucket", 1);

        script.run(config);
        setStatic("itemsList", remaining);
        setStatic("initialCount", 2);
        setStatic("totalBought", 1);
        setStatic("initialized", true);
        script.tick();

        assertFalse(script.future.cancelled, "a run that is already buying must survive the quest selection being cleared");
        assertTrue(getBoolean("initialized"));
        assertEquals(1, remaining.size(), "the deferred item must stay queued");
        verify(config, never()).listOfItemsToBuy();
    }

    @Test
    void stopDuringAFailedOpenDoesNotCountOrWalk() throws Exception {
        grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(false);
        grandExchange.when(Rs2GrandExchange::openExchange).thenAnswer(invocation -> {
            script.future.cancel(true);
            setStatic("failedOpens", 0);
            return false;
        });

        script.run(config);
        script.tick();

        assertEquals(0, getInt("failedOpens"), "a cancelled pass must not count a failed open");
        grandExchange.verify(Rs2GrandExchange::walkToGrandExchange, never());
    }

    @Test
    void emptyBuyListStopsOnTheFirstPassAndStaysUninitialized() throws Exception {
        when(config.listOfItemsToBuy()).thenReturn("");

        script.run(config);
        script.tick();

        assertTrue(script.future.cancelled, "an empty list must stop the script on a fresh client");
        assertFalse(getBoolean("initialized"), "a stopped start must not leave the script initialized");
    }

    @Test
    void entryEndingInAnOpeningBracketStopsTheScript() throws Exception {
        when(config.listOfItemsToBuy()).thenReturn("rune arrow[50],chaos rune[");

        script.run(config);
        script.tick();

        assertTrue(script.future.cancelled, "a malformed entry must stop the script");
        assertFalse(getBoolean("initialized"), "a failed parse must not leave a partial list initialized");
        grandExchange.verify(() -> Rs2GrandExchange.processOffer(any(GrandExchangeRequest.class)), never());
    }

    @Test
    void nonNumericQuantityStopsOnTheFirstParse() throws Exception {
        when(config.listOfItemsToBuy()).thenReturn("rune arrow[abc]");

        script.run(config);
        script.tick();

        assertTrue(script.future.cancelled, "an invalid quantity must stop the script on a fresh client");
        assertFalse(getBoolean("initialized"), "a failed parse must not leave a partial list initialized");
    }

    @Test
    void failedOpenKeepsTheListAndStopsAfterFiveConsecutiveFailures() {
        grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(false);
        grandExchange.when(Rs2GrandExchange::openExchange).thenReturn(false);

        script.run(config);
        for (int i = 0; i < 4; i++) {
            script.tick();
        }

        assertFalse(script.future.cancelled, "a few failed opens must be retried");
        grandExchange.verify(Rs2GrandExchange::walkToGrandExchange, times(4));
        grandExchange.verify(() -> Rs2GrandExchange.processOffer(any(GrandExchangeRequest.class)), never());

        script.tick();

        assertTrue(script.future.cancelled, "five failed opens in a row must stop the script");
        grandExchange.verify(() -> Rs2GrandExchange.processOffer(any(GrandExchangeRequest.class)), never());
    }

    @Test
    void successfulOpenResetsTheFailedOpenCount() throws Exception {
        grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(false);
        grandExchange.when(Rs2GrandExchange::openExchange).thenReturn(false);

        script.run(config);
        script.tick();
        script.tick();
        assertEquals(2, getInt("failedOpens"));

        grandExchange.when(Rs2GrandExchange::openExchange).thenReturn(true);
        script.tick();

        assertEquals(0, getInt("failedOpens"));
        assertFalse(script.future.cancelled);
    }

    @Test
    void staleBoughtCountDoesNotFinishARunWithADeferredItem() throws Exception {
        plugins.add(mock(QuestHelperPlugin.class));
        setStatic("totalBought", 1);

        script.run(config);
        script.tick();

        assertFalse(script.future.cancelled, "an item waiting for a free slot must keep the run alive");
        grandExchange.verify(() -> Rs2GrandExchange.processOffer(any(GrandExchangeRequest.class)), never());
    }

    private static void resetStatics() throws Exception {
        setStatic("initialized", false);
        setStatic("initialCount", 0);
        setStatic("totalBought", 0);
        setStatic("itemsList", null);
        setStatic("failedOpens", 0);
    }

    private static Field field(String name) throws Exception {
        Field field = AutoBuyerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setStatic(String name, Object value) throws Exception {
        field(name).set(null, value);
    }

    private static boolean getBoolean(String name) throws Exception {
        return field(name).getBoolean(null);
    }

    private static int getInt(String name) throws Exception {
        return field(name).getInt(null);
    }

    private static final class ProbeScript extends AutoBuyerScript {
        private Runnable body;
        private FakeFuture future;

        ProbeScript() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            when(executor.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        body = invocation.getArgument(0);
                        future = new FakeFuture();
                        return future;
                    });
            this.scheduledExecutorService = executor;
        }

        void tick() {
            body.run();
        }
    }

    private static final class FakeFuture implements ScheduledFuture<Object> {
        private boolean cancelled;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
