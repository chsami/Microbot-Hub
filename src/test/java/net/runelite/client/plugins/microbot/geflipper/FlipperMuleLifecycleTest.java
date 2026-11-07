package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

class FlipperMuleLifecycleTest {
    @TempDir Path temporary;

    @Test
    void removedHostAndLocationPreferencesCannotOverrideAutomaticCoordination() throws Exception {
        for (Method method : FlipperConfig.class.getMethods()) {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null) {
                assertNotEquals("muleHost", item.keyName());
                assertNotEquals("muleLocation", item.keyName());
                assertNotEquals("muleReserveGp", item.keyName());
            }
        }
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            f.preferences.put("Flipper Config.muleHost", "synthetic-old-host");
            f.preferences.put("Flipper Config.muleLocation", "synthetic-old-location");
            f.preferences.put("Flipper Config.muleReserveGp", "synthetic-old-reserve");
            Map<String, String> expected = new HashMap<>(f.preferences);
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = receiverView(false);
            port.view = MuleSessionTest.snapshot(101L, "FlipperAcc", 330, true, false, false, false,
                50_000_000, 0, false, false, "", 0, false, 0);
            port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "", "OK");
            AtomicReference<String> host = new AtomicReference<>();
            MuleSession.Port recording = (MuleSession.Port) Proxy.newProxyInstance(
                MuleSession.Port.class.getClassLoader(), new Class<?>[]{MuleSession.Port.class}, (proxy, method, args) -> {
                    if (method.getName().equals("requestMuleLease")) host.set((String) args[0]);
                    return method.invoke(port, args);
                });
            MuleSession session = new MuleSession(recording);
            configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER), session);
            field(MuleSession.class, "eligibleSince").setLong(session,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - 10_000);
            assertFalse((Boolean) invoke(f.script, "initializeMuleOrCopilot"));
            assertEquals("auto", host.get());
            MuleSession.ReceiverSettings receiver = (MuleSession.ReceiverSettings) invoke(f.script, "receiverSettings");
            assertTrue(receiver.location.auto);
            assertEquals(expected, f.preferences, "Legacy preferences are ignored, not overwritten or erased");
        }
    }

    @Test
    void receiverFinishCompletesWithoutCopilotAndDisablesOnlyItsOwnPlugin() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = MuleSession.Snapshot.loggedOut();
            port.serverRunning = true;
            configure(f, new MutableConfig(FlipperConfig.MuleMode.MULE_RECEIVER), new MuleSession(port));
            AtomicInteger completions = new AtomicInteger();
            assertTrue(f.script.requestFinish(completions::incrementAndGet));
            assertTrue((Boolean) invoke(f.script, "tickMuleReceiver"));
            assertFalse(port.serverRunning);
            assertTrue(f.script.isFinishComplete());
            assertEquals(1, completions.get());
            invoke(f.script, "tickMuleReceiver");
            assertEquals(1, completions.get(), "Completion is queued once");
            f.plugin.stopAfterFinish(3);
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertFalse(f.plugins.isPluginActive(f.plugin));
            assertEquals("true", f.preferences.get("runelite.unrelatedplugin"));
        }
    }

    @Test
    void profileChangeCannotCompleteReceiverFinishOnTheReplacementProfile() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = receiverView(false);
            configure(f, new MutableConfig(FlipperConfig.MuleMode.MULE_RECEIVER), new MuleSession(port));
            AtomicInteger completions = new AtomicInteger();
            assertTrue(f.script.requestFinish(completions::incrementAndGet));
            f.script.pauseFinishForProfileChange();
            f.script.pauseMuleForProfileChange();
            assertTrue((Boolean) invoke(f.script, "tickMuleReceiver"));
            assertFalse(f.script.isFinishComplete());
            assertEquals(0, completions.get());
            assertFalse(port.serverRunning);
        }
    }

    @Test
    void receiverFinishWaitsUntilAnExistingTradeHasClosed() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = receiverView(true);
            configure(f, new MutableConfig(FlipperConfig.MuleMode.MULE_RECEIVER), new MuleSession(port));
            assertTrue(f.script.requestFinish(() -> { }));
            assertTrue((Boolean) invoke(f.script, "tickMuleReceiver"));
            assertEquals(1, port.declined);
            assertFalse(f.script.isFinishComplete());
            assertTrue((Boolean) invoke(f.script, "muleActionsAllowed"), "Finish must allow its decline action");
            port.view = receiverView(false);
            invoke(f.script, "tickMuleReceiver");
            assertTrue(f.script.isFinishComplete());
        }
    }

    @Test
    void loggedOutModeChangeStopsListenerBeforeTheLoginGuard() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = MuleSession.Snapshot.loggedOut();
            port.serverRunning = true;
            configure(f, new MutableConfig(FlipperConfig.MuleMode.OFF), new MuleSession(port));
            field(FlipperScript.class, "observedMuleMode").set(f.script, FlipperConfig.MuleMode.MULE_RECEIVER);
            invoke(f.script, "reconcileMuleMode");
            assertFalse(port.serverRunning);
        }
    }

    @Test
    void switchingToReceiverRestoresTemporaryCopilotSellOnlyMode() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            configure(f, new MutableConfig(FlipperConfig.MuleMode.MULE_RECEIVER), new MuleSession(port));
            FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(finish.request(true));
            assertTrue(finish.begin(null, f.copilot.controller, f.copilot.manager));
            field(FlipperScript.class, "muleFinish").setBoolean(f.script, true);
            field(FlipperScript.class, "observedMuleMode").set(f.script, FlipperConfig.MuleMode.FLIPPER_SENDER);
            invoke(f.script, "reconcileMuleMode");
            assertFalse(finish.isRequested());
            assertFalse(field(FlipperScript.class, "muleFinish").getBoolean(f.script));
            assertEquals("", finish.status());
        }
    }

    @Test
    void disabledMulingAndFinishStillPermitTheCapturedReturnJourney() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            MuleSession session = new MuleSession(port);
            configure(f, new MutableConfig(FlipperConfig.MuleMode.OFF), session);
            field(MuleSession.class, "phase").set(session, MuleSession.Phase.RETURNING);
            assertTrue(f.script.requestFinish(() -> { }));
            assertTrue((Boolean) invoke(f.script, "muleActionsAllowed"));
        }
    }

    @Test
    void onlyRealAuthorizedTradeRequestsReachReceiverAcceptance() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = receiverView(false);
            port.activeLease = new MuleNetwork.Lease("FlipperAcc", 20_000_000, Long.MAX_VALUE);
            configure(f, new MutableConfig(FlipperConfig.MuleMode.MULE_RECEIVER), new MuleSession(port));
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> method.getName().equals("getGameState") ? GameState.LOGGED_IN
                    : method.getName().equals("getAccountHash") ? 202L : null);
            field(FlipperPlugin.class, "client").set(f.plugin, client);
            ChatMessage event = new ChatMessage();
            event.setType(ChatMessageType.GAMEMESSAGE);
            event.setName("FlipperAcc");
            event.setMessage("FlipperAcc wishes to trade with you.");
            f.plugin.onChatMessage(event);
            invoke(f.script, "tickMuleReceiver");
            assertEquals(0, port.tradePlayerCalls);
            event.setType(ChatMessageType.TRADEREQ);
            event.setName("");
            f.plugin.onChatMessage(event);
            invoke(f.script, "tickMuleReceiver");
            assertEquals(1, port.tradePlayerCalls);
            invoke(f.script, "tickMuleReceiver");
            assertEquals(1, port.tradePlayerCalls);
        }
    }

    @Test
    void requestNameParsingRejectsUnrelatedOrInvalidNames() {
        assertEquals("flipperacc", MulePolicy.tradeRequestName("FlipperAcc", ""));
        assertEquals("flipperacc", MulePolicy.tradeRequestName("", "<col=ffffff>FlipperAcc</col> wishes to trade with you."));
        assertEquals("", MulePolicy.tradeRequestName("", "ordinary chat from FlipperAcc"));
        assertEquals("", MulePolicy.tradeRequestName("NameThatIsFarTooLong", ""));
        assertEquals("", MulePolicy.tradeRequestName("Bad|Name", ""));
    }

    @Test
    void hyphenatedNamesAreAccepted() {
        assertEquals("mule-one", MulePolicy.tradeRequestName("Mule-One", ""));
        assertEquals("bank-mule", MulePolicy.tradeRequestName("", "Bank-Mule wishes to trade with you."));
        assertEquals("a-b c-d", MulePolicy.tradeRequestName("A-b C-d", ""));
        assertTrue(MulePolicy.sameRsn("Bank-Mule", "bank-mule"));
        assertEquals("", MulePolicy.tradeRequestName("Way-Too-Long-1", ""));
    }

    private static MuleSession.Snapshot receiverView(boolean trade) {
        return MuleSessionTest.snapshot(202L, "BankMule", 301, true, false, false, false,
            5_000_000, 0, trade, false, trade ? "FlipperAcc" : "", 0, false, trade ? 20_000_000 : 0);
    }

    @Test
    void capitalPreparationWaitsForAMatchingHealthyMule() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary);
             MuleConnectionMonitor monitor = cachedConnection(false)) {
            MuleSession session = new MuleSession(new MuleSessionTest.FakePort());
            configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER), session);
            field(FlipperScript.class, "muleConnection").set(f.script, monitor);
            field(MuleSession.class, "liquidating").setBoolean(session, true);
            invokeManageLiquidation(f.script);
            FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertFalse(finish.isRequested(), "An offline Mule must not make the sender cancel or sell offers");
            assertFalse(field(FlipperScript.class, "muleFinish").getBoolean(f.script));
            assertFalse((Boolean) invoke(f.script, "secondScreenAfkSuppressed"),
                "An unavailable Mule must not suppress the user's second-screen AFK");
        }
    }

    @Test
    void fundedTransferRestoresTemporaryModeWithoutFinishingThePlugin() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary);
             MuleConnectionMonitor monitor = cachedConnection(true)) {
            MuleSession session = new MuleSession(new MuleSessionTest.FakePort());
            configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER), session);
            field(FlipperScript.class, "muleConnection").set(f.script, monitor);
            field(MuleSession.class, "liquidating").setBoolean(session, true);
            invokeManageLiquidation(f.script);
            FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(finish.isRequested());
            assertTrue(finish.begin(null, f.copilot.controller, f.copilot.manager));
            field(MuleSession.class, "phase").set(session, MuleSession.Phase.PREPARING_CASH);
            field(MuleSession.class, "liquidating").setBoolean(session, false);
            invokeManageLiquidation(f.script);
            assertFalse(finish.isRequested());
            assertFalse(field(FlipperScript.class, "muleFinish").getBoolean(f.script));
            assertFalse(f.script.isFinishComplete());
        }
    }

    @Test
    void lostConnectionEndsOnlyTheTemporaryCapitalPreparation() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary);
             MuleConnectionMonitor monitor = cachedConnection(true)) {
            MuleSession session = new MuleSession(new MuleSessionTest.FakePort());
            configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER), session);
            field(FlipperScript.class, "muleConnection").set(f.script, monitor);
            field(MuleSession.class, "liquidating").setBoolean(session, true);
            invokeManageLiquidation(f.script);
            FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(finish.begin(null, f.copilot.controller, f.copilot.manager));
            monitor.disable();
            invokeManageLiquidation(f.script);
            assertFalse(finish.isRequested());
            assertFalse(field(FlipperScript.class, "muleFinish").getBoolean(f.script));
            assertTrue(session.liquidationRequested(), "Health loss must not fabricate a completed transfer");
        }
    }

    private static MuleConnectionMonitor cachedConnection(boolean ready) throws Exception {
        MuleConnectionMonitor monitor = new MuleConnectionMonitor((mule, own, port) -> {
            throw new AssertionError("Lifecycle tests must not contact a real Mule");
        }, () -> 100L, Executors.newSingleThreadScheduledExecutor());
        field(MuleConnectionMonitor.class, "current").set(monitor,
            new MuleConnectionMonitor.Snapshot(ready ? MuleConnectionMonitor.State.READY
                : MuleConnectionMonitor.State.OFFLINE, ready, ready, 100));
        return monitor;
    }

    @Test
    void delayedIdentityReadCannotRecreateHealthWorkerAfterProfileChange() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary);
             MuleConnectionMonitor monitor = cachedConnection(true)) {
            configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER),
                new MuleSession(new MuleSessionTest.FakePort()));
            assertTrue(f.script.isRunning());
            field(FlipperScript.class, "muleConnection").set(f.script, monitor);
            long captured = field(FlipperScript.class, "muleConnectionGeneration").getLong(f.script);
            f.script.pauseMuleForProfileChange();
            Class<?> identityType = Class.forName(FlipperScript.class.getName() + "$MuleIdentity");
            java.lang.reflect.Constructor<?> constructor = identityType.getDeclaredConstructor(long.class, String.class);
            constructor.setAccessible(true);
            Method publish = FlipperScript.class.getDeclaredMethod("publishSenderConnection", long.class, identityType);
            publish.setAccessible(true);
            publish.invoke(f.script, captured, constructor.newInstance(101L, "FlipperAcc"));
            assertNull(field(FlipperScript.class, "muleConnection").get(f.script));
            assertEquals(MuleConnectionMonitor.State.OFF, f.script.getMuleConnectionStatus().state);
        }
    }

    @Test
    void updateTransferRunsWhileFinishWaitsAndThenLetsFinishStop() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
            port.view = MuleSessionTest.snapshot(101L, "FlipperAcc", 330, true, false, false, false,
                50_000_000, 0, false, false, "", 0, false, 0);
            port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
            MuleSession session = new MuleSession(port);
            configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER), session);
            FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(finish.request(false), "The update's Finish is running");

            assertTrue((Boolean) invoke(f.script, "initializeMuleOrCopilot"), "Finish alone never starts a transfer");
            assertEquals(0, port.requestLeaseCalls);

            field(FlipperScript.class, "updateDueAt").setLong(f.script,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + 300_000);
            invoke(f.script, "startUpdateMule");
            assertEquals(FlipperScript.UpdateMule.RUNNING, field(FlipperScript.class, "updateMule").get(f.script));
            assertTrue((Boolean) invoke(f.script, "muleActionsAllowed"), "The update transfer may act during Finish");
            assertFalse((Boolean) invoke(f.script, "initializeMuleOrCopilot"), "The transfer owns the tick");
            assertEquals(20_000_000, port.lastRequestedAmount, "Everything above the 30M keep amount");
            assertTrue(session.active());

            session.reset(); // The transfer ended (sent or cancelled) and the Flipper is home.
            invoke(f.script, "initializeMuleOrCopilot");
            assertEquals(FlipperScript.UpdateMule.DONE, field(FlipperScript.class, "updateMule").get(f.script));
            invoke(f.script, "initializeMuleOrCopilot");
            assertEquals(1, port.requestLeaseCalls, "One transfer per update; Finish then stops normally");
        }
    }

    @Test
    void aTransferUnderWayWhenTheUpdateIsAnnouncedIsFinishedNotCancelled() throws Exception {
        for (FlipperScript.UpdateMule update : new FlipperScript.UpdateMule[]{
            FlipperScript.UpdateMule.AFTER_FINISH, FlipperScript.UpdateMule.NONE}) {
            try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
                MuleSessionTest.FakePort port = new MuleSessionTest.FakePort();
                port.view = MuleSessionTest.snapshot(101L, "FlipperAcc", 330, true, false, false, false,
                    50_000_000, 0, false, false, "", 0, false, 0);
                port.muleReply = new MuleNetwork.MuleReply(true, true, "BankMule", 301, "FlipperAcc", "127.0.0.1", "OK");
                MuleSession session = new MuleSession(port);
                configure(f, new MutableConfig(FlipperConfig.MuleMode.FLIPPER_SENDER), session);
                MuleSession.SenderSettings settings = new MuleSession.SenderSettings(true, 30_000_000, 30_000_000,
                    "BankMule", "auto", 43599, 0, MulePolicy.TileSpec.auto());
                long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                session.tickSender(settings, false, now - 6_000);
                assertTrue(session.tickSender(settings, false, now - 1_000));
                assertEquals(MuleSession.Phase.PREPARING_CASH, session.phase());

                field(FlipperScript.class, "updateMule").set(f.script, update);
                FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
                assertTrue(finish.request(false));
                invoke(f.script, "initializeMuleOrCopilot");
                if (update == FlipperScript.UpdateMule.NONE) {
                    assertEquals(MuleSession.Phase.IDLE, session.phase(), "A plain Finish still cancels the transfer");
                    assertEquals(java.util.Arrays.asList(false), port.releasedLeases);
                } else {
                    assertEquals(MuleSession.Phase.HOPPING_TO_MULE, session.phase());
                    assertTrue(port.releasedLeases.isEmpty());
                }
            }
        }
    }

    private static void invokeManageLiquidation(FlipperScript script) throws Exception {
        Method method = FlipperScript.class.getDeclaredMethod("manageMuleLiquidation", long.class);
        method.setAccessible(true);
        method.invoke(script, 100L);
    }

    private static void configure(FlipperFinishLifecycleTest.Fixture f, FlipperConfig config, MuleSession session)
            throws Exception {
        field(FlipperScript.class, "config").set(f.script, config);
        field(FlipperPlugin.class, "config").set(f.plugin, config);
        field(FlipperScript.class, "muleSession").set(f.script, session);
        field(FlipperScript.class, "observedMuleMode").set(f.script, config.muleMode());
    }

    private static Object invoke(FlipperScript script, String name) throws Exception {
        Method method = FlipperScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(script);
    }

    private static final class MutableConfig implements FlipperConfig {
        final MuleMode mode;
        MutableConfig(MuleMode mode) { this.mode = mode; }
        @Override public MuleMode muleMode() { return mode; }
        @Override public String muleRsn() { return "BankMule"; }
        @Override public String muleThresholdGp() { return "30M"; }
    }
}
