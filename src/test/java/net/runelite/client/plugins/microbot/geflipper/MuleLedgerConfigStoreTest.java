package net.runelite.client.plugins.microbot.geflipper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class MuleLedgerConfigStoreTest {
    @TempDir Path temporary;

    @Test
    void nothingSavedLoadsAsAnEmptyLedger() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            MuleLedger.State state = new MuleLedgerConfigStore(fixture.manager, temporary.resolve("local")).load();
            assertEquals(0L, state.account);
            assertEquals(-1L, state.inventoryGp);
            assertEquals(0L, state.bankGp);
            assertEquals(0L, state.lifetimeGp);
        }
    }

    @Test
    void savingTotalsWritesNoSyncableConfigurationOrConfigurationEvents() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            MuleLedgerConfigStore store = new MuleLedgerConfigStore(fixture.manager, temporary.resolve("local"));
            store.load();
            store.save(new MuleLedger.State(202L, 5_000_000L, 700L, 9_000_000L));
            assertTrue(fixture.preferences.isEmpty());
            assertEquals(0, fixture.configEvents.changes);
            assertEquals(9_000_000L, store.load().lifetimeGp);
            fixture.assertNoSyncableChanges();
        }
    }

    @Test
    void legacyTotalsAreImportedReadOnlyAndLocalUpdatesTakePrecedenceOnReopen() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.preferences.putAll(Map.of("Flipper Config.ledgerAccount", "202",
                "Flipper Config.ledgerInventoryGp", "5000000", "Flipper Config.ledgerBankGp", "700",
                "Flipper Config.ledgerLifetimeGp", "9000000", "Flipper Config.muleRsn", "SyntheticMule",
                "flippingcopilot.slotActionSwap", "false"));
            Map<String, String> before = new HashMap<>(fixture.preferences);
            Path local = temporary.resolve("local");
            MuleLedgerConfigStore store = new MuleLedgerConfigStore(fixture.manager, local);
            assertEquals(9_000_000L, store.load().lifetimeGp);
            store.save(new MuleLedger.State(202L, 5_000_000L, 700L, 11_000_000L));
            assertEquals(before, fixture.preferences, "Import and later saves must leave every preference unchanged");
            assertEquals(11_000_000L, new MuleLedgerConfigStore(fixture.manager, local).load().lifetimeGp);
            fixture.assertNoSyncableChanges();
        }
    }

    @Test
    void loadsWhatWasSavedAndToleratesDamagedValues() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.preferences.put("Flipper Config.ledgerAccount", "202");
            fixture.preferences.put("Flipper Config.ledgerInventoryGp", " 42 ");
            fixture.preferences.put("Flipper Config.ledgerBankGp", "not a number");
            fixture.preferences.put("Flipper Config.ledgerLifetimeGp", "-5");
            MuleLedger.State state = new MuleLedgerConfigStore(fixture.manager, temporary.resolve("local")).load();
            assertEquals(202L, state.account);
            assertEquals(42L, state.inventoryGp);
            assertEquals(0L, state.bankGp);
            assertEquals(0L, state.lifetimeGp, "A negative total is treated as none");
        }
    }

    @Test
    void aLedgerKeepsItsAllTimeTotalAcrossRestartsThroughTheRealStore() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            MuleLedger first = new MuleLedger();
            first.attachStore(new MuleLedgerConfigStore(fixture.manager, temporary.resolve("local")));
            first.startSession();
            first.recordReceived(202L, 6_000_000L);
            first.observeHoldings(202L, 8_000_000L, 0L, false);
            first.flush();

            MuleLedger restarted = new MuleLedger();
            restarted.attachStore(new MuleLedgerConfigStore(fixture.manager, temporary.resolve("local")));
            restarted.startSession();
            MuleLedger.Snapshot snapshot = restarted.snapshot();
            assertEquals(0L, snapshot.sessionGp, "The session total starts again");
            assertEquals(6_000_000L, snapshot.lifetimeGp);
            assertEquals(8_000_000L, snapshot.heldGp, "The login screen can show the last seen coins at once");
            fixture.assertNoSyncableChanges();
        }
    }

    @Test
    void unrelatedPluginSettingsAreNeverTouched() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.preferences.put("Flipper Config.muleRsn", "SyntheticMule");
            fixture.preferences.put("flippingcopilot.slotActionSwap", "false");
            fixture.preferences.put("microbot.enableAutoRunOn", "false");
            Map<String, String> before = new HashMap<>(fixture.preferences);
            MuleLedgerConfigStore store = new MuleLedgerConfigStore(fixture.manager, temporary.resolve("local"));
            store.load();
            store.save(new MuleLedger.State(1L, 2L, 3L, 4L));
            assertEquals(before, fixture.preferences);
            fixture.assertNoSyncableChanges();
        }
    }

    @Test
    void anOldStoreKeepsWritingItsOriginalFileAfterTheCoreProfileChanges() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            Path local = temporary.resolve("local");
            fixture.preferences.putAll(Map.of("Flipper Config.ledgerAccount", "202", "Flipper Config.ledgerLifetimeGp", "100000000"));
            MuleLedgerConfigStore first = new MuleLedgerConfigStore(fixture.manager, local);
            first.load();
            Map<String, String> secondPreferences = fixture.installProfile(303L);
            secondPreferences.putAll(Map.of("Flipper Config.ledgerAccount", "202", "Flipper Config.ledgerLifetimeGp", "300000000"));
            MuleLedgerConfigStore second = new MuleLedgerConfigStore(fixture.manager, local);
            second.load();
            first.save(new MuleLedger.State(202L, 25_000_000L, 0L, 120_000_000L));
            assertEquals(101L, first.profileId());
            assertEquals(303L, second.profileId());
            assertEquals(120_000_000L, new MuleLedgerLocalStore(local, 101L, MuleLedger.State.EMPTY).load().lifetimeGp);
            assertEquals(300_000_000L, second.load().lifetimeGp, "Two profiles of one account retain different totals");
            assertEquals("300000000", secondPreferences.get("Flipper Config.ledgerLifetimeGp"));
            fixture.assertNoSyncableChanges();
        }
    }

    @Test
    void missingProfileCannotSelectOrWriteAnyLocalLedger() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            field(ConfigManager.class, "profile").set(fixture.manager, null);
            Path local = temporary.resolve("local");
            MuleLedgerConfigStore store = new MuleLedgerConfigStore(fixture.manager, local);
            assertEquals(Long.MIN_VALUE, store.profileId());
            assertEquals(0L, store.load().lifetimeGp);
            store.save(new MuleLedger.State(202L, 10L, 0L, 10L));
            assertFalse(java.nio.file.Files.exists(local));
            fixture.assertNoSyncableChanges();
        }
    }

    static final class Fixture implements AutoCloseable {
        final ConfigManager manager;
        final Map<String, String> preferences;
        final ConfigEvents configEvents = new ConfigEvents();
        private final Path temporary;
        private final Object previousProfileName;

        @SuppressWarnings("unchecked")
        Fixture(Path temporary) throws Exception {
            this.temporary = temporary;
            previousProfileName = field(ConfigManager.class, "configProfileName").get(null);
            Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            EventBus events = new EventBus();
            events.register(configEvents);
            manager = (ConfigManager) constructor.newInstance(null, inertScheduler(), events,
                null, null, null, null, null);
            preferences = installProfile(101L);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> installProfile(long id) throws Exception {
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic-profile-" + id + ".properties").toFile());
            field(ConfigManager.class, "configProfile").set(manager, data);
            Constructor<ConfigProfile> profileConstructor = ConfigProfile.class.getDeclaredConstructor(long.class);
            profileConstructor.setAccessible(true);
            field(ConfigManager.class, "profile").set(manager, profileConstructor.newInstance(id));
            return (Map<String, String>) field(dataType, "properties").get(data);
        }

        void assertNoSyncableChanges() throws Exception {
            Object data = field(ConfigManager.class, "configProfile").get(manager);
            assertTrue(((Map<?, ?>) field(data.getClass(), "patchChanges").get(data)).isEmpty());
            assertEquals(0, configEvents.changes);
        }

        @Override public void close() throws Exception {
            field(ConfigManager.class, "configProfileName").set(null, previousProfileName);
        }
    }

    private static final class ConfigEvents {
        int changes;
        @Subscribe public void onConfigChanged(ConfigChanged ignored) { changes++; }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static ScheduledExecutorService inertScheduler() {
        return (ScheduledExecutorService) Proxy.newProxyInstance(ScheduledExecutorService.class.getClassLoader(),
            new Class<?>[]{ScheduledExecutorService.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("schedule")) {
                    AtomicBoolean cancelled = new AtomicBoolean();
                    return Proxy.newProxyInstance(ScheduledFuture.class.getClassLoader(),
                        new Class<?>[]{ScheduledFuture.class}, (future, action, values) -> {
                            switch (action.getName()) {
                                case "cancel": cancelled.set(true); return true;
                                case "isDone": case "isCancelled": return cancelled.get();
                                case "getDelay": return 0L;
                                case "compareTo": return 0;
                                case "hashCode": return System.identityHashCode(future);
                                case "equals": return future == values[0];
                                default: return null;
                            }
                        });
                }
                return method.getReturnType() == boolean.class ? false : null;
            });
    }
}
