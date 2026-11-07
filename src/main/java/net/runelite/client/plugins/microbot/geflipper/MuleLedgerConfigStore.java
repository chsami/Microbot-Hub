package net.runelite.client.plugins.microbot.geflipper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

/** Captures a profile once and imports legacy totals without writing syncable settings. */
final class MuleLedgerConfigStore implements MuleLedger.Store {
    static final String GROUP = FlipperConfig.GROUP;
    static final String ACCOUNT_KEY = "ledgerAccount";
    static final String INVENTORY_KEY = "ledgerInventoryGp";
    static final String BANK_KEY = "ledgerBankGp";
    static final String LIFETIME_KEY = "ledgerLifetimeGp";
    private final MuleLedgerLocalStore local;

    MuleLedgerConfigStore(ConfigManager manager) {
        this(manager, Paths.get(System.getProperty("user.home"), ".runelite", "geflipper", "mule-ledger"));
    }

    MuleLedgerConfigStore(ConfigManager manager, Path directory) {
        Objects.requireNonNull(manager);
        // The SDK swaps both profile identity and its data while holding this monitor.
        synchronized (manager) {
            ConfigProfile profile = manager.getProfile();
            if (profile == null) { local = null; return; }
            long id = profile.getId();
            MuleLedger.State seed = new MuleLedger.State(number(manager, ACCOUNT_KEY, 0),
                number(manager, INVENTORY_KEY, -1), number(manager, BANK_KEY, 0), number(manager, LIFETIME_KEY, 0));
            if (seed.account == 0 && seed.inventoryGp == -1 && seed.bankGp == 0 && seed.lifetimeGp == 0) {
                seed = MuleLedger.State.EMPTY;
            }
            local = new MuleLedgerLocalStore(directory, id, seed);
        }
    }

    @Override public long profileId() { return local == null ? Long.MIN_VALUE : local.profileId(); }
    @Override public MuleLedger.State load() { return local == null ? MuleLedger.State.EMPTY : local.load(); }
    @Override public void save(MuleLedger.State state) { if (local != null) local.save(state); }

    private static long number(ConfigManager manager, String key, long fallback) {
        try {
            String text = manager.getConfiguration(GROUP, key);
            return text == null || text.trim().isEmpty() ? fallback : Long.parseLong(text.trim());
        } catch (RuntimeException invalid) { return fallback; }
    }
}
