package net.runelite.client.plugins.microbot.geflipper;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/** Display-only totals in a fixed local file, outside configuration synchronization. */
final class MuleLedgerLocalStore implements MuleLedger.Store {
    private final long profileId;
    private final Path file;
    private final MuleLedger.State seed;
    private boolean unreadable;

    MuleLedgerLocalStore(Path directory, long profileId, MuleLedger.State seed) {
        if (profileId == Long.MIN_VALUE) throw new IllegalArgumentException("Unknown ledger profile");
        this.profileId = profileId;
        this.file = directory.toAbsolutePath().normalize().resolve(fileKey(profileId) + ".properties");
        this.seed = seed == null ? MuleLedger.State.EMPTY : seed;
    }

    @Override public long profileId() { return profileId; }

    @Override public synchronized MuleLedger.State load() {
        try {
            if (!Files.exists(file)) {
                if (seed != MuleLedger.State.EMPTY) save(seed);
                return seed;
            }
            if (Files.size(file) > 4096) throw new IOException("Invalid ledger size");
            Properties values = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
            long account = number(values, "account"), inventory = number(values, "inventory");
            long bank = number(values, "bank"), lifetime = number(values, "lifetime");
            if (inventory < -1 || bank < 0 || lifetime < 0) throw new IOException("Invalid ledger values");
            return new MuleLedger.State(account, inventory, bank, lifetime);
        } catch (IOException | IllegalArgumentException failure) {
            unreadable = true;
            throw new IllegalStateException("Local mule totals could not be read", failure);
        }
    }

    @Override public synchronized void save(MuleLedger.State state) {
        if (unreadable) throw new IllegalStateException("Unreadable mule totals are preserved");
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "ledger-", ".pending");
            Properties values = new Properties();
            values.setProperty("account", Long.toString(state.account));
            values.setProperty("inventory", Long.toString(state.inventoryGp));
            values.setProperty("bank", Long.toString(state.bankGp));
            values.setProperty("lifetime", Long.toString(state.lifetimeGp));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "GEFlipper local display totals");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Local mule totals could not be saved", failure);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private static long number(Properties values, String key) throws IOException {
        String text = values.getProperty(key);
        if (text == null) throw new IOException("Incomplete ledger");
        return Long.parseLong(text);
    }

    private static String fileKey(long profileId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                ("geflipper-ledger-v1:" + profileId).getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return key.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
