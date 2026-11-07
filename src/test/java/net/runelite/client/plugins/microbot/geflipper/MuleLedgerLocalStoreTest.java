package net.runelite.client.plugins.microbot.geflipper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MuleLedgerLocalStoreTest {
    @TempDir Path temporary;

    @Test
    void independentProfilesRoundTripOnlyTheFourTotalsWithoutPendingFiles() throws Exception {
        MuleLedgerLocalStore first = store(101L);
        MuleLedgerLocalStore second = store(303L);
        first.save(new MuleLedger.State(202L, 20L, 30L, 100_000_000L));
        second.save(new MuleLedger.State(202L, 40L, 50L, 300_000_000L));
        first.save(new MuleLedger.State(202L, 25L, 35L, 120_000_000L));
        assertEquals(120_000_000L, store(101L).load().lifetimeGp);
        assertEquals(300_000_000L, store(303L).load().lifetimeGp);
        List<Path> files;
        try (Stream<Path> entries = Files.list(temporary)) { files = entries.collect(Collectors.toList()); }
        assertEquals(2, files.size());
        for (Path file : files) {
            assertTrue(file.getFileName().toString().matches("[0-9a-f]{64}\\.properties"));
            Properties values = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
            assertEquals(Set.of("account", "inventory", "bank", "lifetime"), values.stringPropertyNames());
        }
    }

    @Test
    void legacySeedIsWrittenOnceAndAnExistingLocalFileWinsOnReopen() {
        MuleLedger.State seed = new MuleLedger.State(202L, 10L, 30L, 100_000_000L);
        MuleLedgerLocalStore first = new MuleLedgerLocalStore(temporary, 101L, seed);
        assertEquals(100_000_000L, first.load().lifetimeGp);
        first.save(new MuleLedger.State(202L, 15L, 30L, 120_000_000L));
        MuleLedgerLocalStore reopened = new MuleLedgerLocalStore(temporary, 101L, seed);
        assertEquals(120_000_000L, reopened.load().lifetimeGp);
        assertEquals(15L, reopened.load().inventoryGp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"account=202\ninventory=-1\nbank=0\nlifetime=broken\n",
        "account=202\ninventory=-2\nbank=0\nlifetime=42\n",
        "account=202\ninventory=0\nbank=-1\nlifetime=42\n",
        "account=202\ninventory=0\nbank=0\n"})
    void corruptFilesArePreservedEvenWhenAccountingTriesToSaveAgain(String damaged) throws Exception {
        MuleLedgerLocalStore store = store(101L);
        store.save(new MuleLedger.State(202L, 10L, 0L, 42L));
        Path file;
        try (Stream<Path> files = Files.list(temporary)) { file = files.findFirst().orElseThrow(); }
        byte[] original = damaged.getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        assertThrows(IllegalStateException.class, store::load);
        assertThrows(IllegalStateException.class, () -> store.save(new MuleLedger.State(202L, 20L, 0L, 100L)));
        MuleLedger ledger = new MuleLedger();
        ledger.startSession(store);
        ledger.recordReceivedForProfile(101L, 202L, 1000L);
        ledger.flush();
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void oversizedFileIsNotReplacedAndUnknownProfileCannotCreateAStore() throws Exception {
        MuleLedgerLocalStore store = store(101L);
        store.save(new MuleLedger.State(202L, 10L, 0L, 42L));
        Path file;
        try (Stream<Path> files = Files.list(temporary)) { file = files.findFirst().orElseThrow(); }
        byte[] original = new byte[4097];
        Files.write(file, original);
        assertThrows(IllegalStateException.class, store::load);
        assertThrows(IllegalStateException.class, () -> store.save(MuleLedger.State.EMPTY));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertThrows(IllegalArgumentException.class,
            () -> new MuleLedgerLocalStore(temporary, Long.MIN_VALUE, MuleLedger.State.EMPTY));
    }

    private MuleLedgerLocalStore store(long profile) {
        return new MuleLedgerLocalStore(temporary, profile, MuleLedger.State.EMPTY);
    }
}
