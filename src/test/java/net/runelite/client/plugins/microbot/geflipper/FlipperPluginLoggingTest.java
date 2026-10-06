package net.runelite.client.plugins.microbot.geflipper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.GameChatAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real plugin lifecycle without starting trading or requiring a game client. */
public class FlipperPluginLoggingTest {
    private static final String PACKAGE = "net.runelite.client.plugins.microbot.geflipper";

    @Test
    public void quietStartupAndVerboseTogglePreserveOtherScriptsLogging() throws Exception {
        verifyLifecycle(false);
    }

    @Test
    public void verboseStartupAndQuietTogglePreserveOtherScriptsLogging() throws Exception {
        verifyLifecycle(true);
    }

    private void verifyLifecycle(boolean initiallyVerbose) throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger own = context.getLogger(PACKAGE);
        Logger sibling = context.getLogger("net.runelite.client.plugins.microbot.loggingtest.OtherScript");
        Level originalRootLevel = root.getLevel();
        Level originalOwnLevel = own.getLevel();
        Level originalSiblingLevel = sibling.getLevel();
        boolean originalOwnAdditive = own.isAdditive();
        boolean originalSiblingAdditive = sibling.isAdditive();
        List<Appender<ILoggingEvent>> originalAppenders = appenders(root);
        Map<Appender<ILoggingEvent>, Boolean> originalStarted = new IdentityHashMap<>();
        for (Appender<ILoggingEvent> appender : originalAppenders) {
            originalStarted.put(appender, appender.isStarted());
        }
        ChatConfiguration originalChat = ChatConfiguration.capture();
        CapturingGameChatAppender sentinel = new CapturingGameChatAppender();
        EventBus eventBus = new EventBus();
        FlipperPlugin plugin = new FlipperPlugin();
        StubScript script = new StubScript();
        AtomicBoolean verbose = new AtomicBoolean(initiallyVerbose);
        FlipperConfig config = new FlipperConfig() {
            @Override
            public boolean verboseLogging() {
                return verbose.get();
            }
        };
        setPluginField(plugin, "config", config);
        setPluginField(plugin, "flipperScript", script);
        try {
            root.setLevel(Level.INFO);
            sibling.setLevel(Level.INFO);
            sibling.setAdditive(true);
            own.setAdditive(true);
            GameChatAppender.updateConfiguration(true, Level.INFO, true);
            sentinel.setContext(context);
            sentinel.setName("GEFLIPPER_LOGGING_TEST");
            sentinel.start();
            root.addAppender(sentinel);
            List<Appender<ILoggingEvent>> expectedAppenders = appenders(root);
            ChatConfiguration expectedChat = ChatConfiguration.capture();

            assertSiblingMessages(sibling, sentinel, "before startup");
            plugin.startUp();
            assertOwnLogVolume(own, sentinel, initiallyVerbose, "startup");
            assertSharedLoggingUnchanged(root, sentinel, expectedAppenders, expectedChat, originalStarted);
            assertSiblingMessages(sibling, sentinel, "while running");

            eventBus.register(plugin);
            verbose.set(!initiallyVerbose);
            ConfigChanged event = new ConfigChanged();
            event.setGroup("Flipper Config");
            event.setKey("verboseLogging");
            eventBus.post(event);
            assertOwnLogVolume(own, sentinel, !initiallyVerbose, "config change");
            assertSharedLoggingUnchanged(root, sentinel, expectedAppenders, expectedChat, originalStarted);
            assertSiblingMessages(sibling, sentinel, "after config change");

            plugin.shutDown();
            assertEquals(1, script.starts);
            assertEquals(1, script.stops);
            assertSharedLoggingUnchanged(root, sentinel, expectedAppenders, expectedChat, originalStarted);
            assertSiblingMessages(sibling, sentinel, "after shutdown");
        } finally {
            eventBus.unregister(plugin);
            // Restore even when a regression detaches/stops ROOT appenders or changes shared flags.
            for (Appender<ILoggingEvent> appender : appenders(root)) {
                root.detachAppender(appender);
            }
            for (Appender<ILoggingEvent> appender : originalAppenders) {
                root.addAppender(appender);
                if (originalStarted.get(appender) && !appender.isStarted()) {
                    appender.start();
                } else if (!originalStarted.get(appender) && appender.isStarted()) {
                    appender.stop();
                }
            }
            sentinel.stop();
            root.setLevel(originalRootLevel);
            own.setLevel(originalOwnLevel);
            own.setAdditive(originalOwnAdditive);
            sibling.setLevel(originalSiblingLevel);
            sibling.setAdditive(originalSiblingAdditive);
            originalChat.restore();
        }
    }

    private static void assertSharedLoggingUnchanged(Logger root, CapturingGameChatAppender sentinel,
            List<Appender<ILoggingEvent>> expectedAppenders, ChatConfiguration expectedChat,
            Map<Appender<ILoggingEvent>, Boolean> originalStarted) throws Exception {
        assertEquals(Level.INFO, root.getLevel(), "GE Flipper must not change ROOT's level");
        assertEquals(expectedAppenders, appenders(root), "GE Flipper must not replace or detach ROOT appenders");
        assertTrue(sentinel.isStarted(), "GE Flipper must not stop the shared game chat appender");
        for (Map.Entry<Appender<ILoggingEvent>, Boolean> entry : originalStarted.entrySet()) {
            assertEquals(entry.getValue().booleanValue(), entry.getKey().isStarted(),
                "GE Flipper must not start or stop existing ROOT appenders");
        }
        ChatConfiguration actual = ChatConfiguration.capture();
        assertEquals(expectedChat.enabled, actual.enabled, "Shared game chat enablement changed");
        assertEquals(expectedChat.minimum, actual.minimum, "Shared game chat minimum level changed");
        assertEquals(expectedChat.microbotOnly, actual.microbotOnly, "Shared game chat scope changed");
    }

    private static void assertSiblingMessages(Logger sibling, CapturingGameChatAppender sentinel, String phase) {
        sibling.info(phase + " sibling INFO");
        sibling.warn(phase + " sibling WARN");
        assertTrue(sentinel.messages.contains(phase + " sibling INFO"), "Other script INFO lost " + phase);
        assertTrue(sentinel.messages.contains(phase + " sibling WARN"), "Other script WARN lost " + phase);
    }

    private static void assertOwnLogVolume(Logger own, CapturingGameChatAppender sentinel, boolean verbose,
            String phase) {
        assertEquals(verbose ? Level.INFO : Level.WARN, own.getLevel());
        own.info(phase + " own INFO");
        own.warn(phase + " own WARN");
        assertEquals(verbose, sentinel.messages.contains(phase + " own INFO"));
        assertTrue(sentinel.messages.contains(phase + " own WARN"));
    }

    private static List<Appender<ILoggingEvent>> appenders(Logger logger) {
        List<Appender<ILoggingEvent>> values = new ArrayList<>();
        logger.iteratorForAppenders().forEachRemaining(values::add);
        return values;
    }

    private static void setPluginField(FlipperPlugin plugin, String name, Object value) throws Exception {
        Field field = FlipperPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private static final class StubScript extends FlipperScript {
        private int starts;
        private int stops;

        @Override
        public boolean run(FlipperConfig config) {
            starts++;
            return true;
        }

        @Override
        public void shutdown() {
            stops++;
        }
    }

    /** Keeps the real game chat filters, replacing only the client-dependent rendering operation. */
    private static final class CapturingGameChatAppender extends GameChatAppender {
        private final List<String> messages = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            messages.add(event.getFormattedMessage());
        }
    }

    private static final class ChatConfiguration {
        private final boolean enabled;
        private final Level minimum;
        private final boolean microbotOnly;

        private ChatConfiguration(boolean enabled, Level minimum, boolean microbotOnly) {
            this.enabled = enabled;
            this.minimum = minimum;
            this.microbotOnly = microbotOnly;
        }

        private static ChatConfiguration capture() throws Exception {
            return new ChatConfiguration((boolean) read("loggingEnabled"), (Level) read("minimumLevel"),
                (boolean) read("onlyMicrobotLogging"));
        }

        private static Object read(String name) throws Exception {
            Field field = GameChatAppender.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        }

        private void restore() {
            GameChatAppender.updateConfiguration(enabled, minimum, microbotOnly);
        }
    }
}
