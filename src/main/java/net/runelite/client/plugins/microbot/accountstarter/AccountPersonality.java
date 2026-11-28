package net.runelite.client.plugins.microbot.accountstarter;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The key and seed are never logged; several clients share this directory without a lock. */
public final class AccountPersonality {
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SALT = Pattern.compile("[0-9a-f]{32}");
    private static final Logger LOG = LoggerFactory.getLogger(AccountPersonality.class);

    private final String key;
    private final long coreSeed;
    private final boolean persistent;

    private static final class Record {
        long seed;
        long created;
    }

    private AccountPersonality(String key, long coreSeed, boolean persistent) {
        this.key = key;
        this.coreSeed = coreSeed;
        this.persistent = persistent;
    }

    public static String accountKey(String salt, long accountHash) {
        return hex(sha256((salt + accountHash).getBytes(StandardCharsets.UTF_8)));
    }

    public static AccountPersonality load(Path dir, long accountHash) {
        try {
            String salt = readOrCreateSalt(dir);
            if (salt != null) {
                String key = accountKey(salt, accountHash);
                Long seed = readOrCreateSeed(dir.resolve("personality"), key);
                if (seed != null) {
                    return new AccountPersonality(key, seed, true);
                }
            }
            LOG.warn("Account Starter: a personality file was unreadable, using a session-only personality");
        } catch (IOException | RuntimeException e) {
            LOG.warn("Account Starter: personality files unavailable ({}), using a session-only personality",
                e.getClass().getSimpleName());
        }
        return new AccountPersonality(accountKey(Long.toString(RANDOM.nextLong()), accountHash), RANDOM.nextLong(),
            false);
    }

    private static String readOrCreateSalt(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("salt");
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        if (publish(dir, "salt", hex(b), file)) {
            return hex(b);
        }
        String text = Files.readString(file).trim();
        if (SALT.matcher(text).matches()) {
            return text;
        }
        if (!text.isEmpty()) {
            setAside(file);
        }
        return null;
    }

    private static Long readOrCreateSeed(Path dir, String key) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(key + ".json");
        Record rec = new Record();
        rec.seed = RANDOM.nextLong();
        rec.created = System.currentTimeMillis();
        return publish(dir, key, GSON.toJson(rec), file) ? rec.seed : readSeed(file);
    }

    /**
     * Writes {@code text} to {@code file} only if no other client got there first, and says whether this call won.
     * A hard link fails atomically when the target exists, unlike Files.move, which on Linux checks then renames.
     */
    private static boolean publish(Path dir, String prefix, String text, Path file) throws IOException {
        Path tmp = Files.createTempFile(dir, prefix, ".tmp");
        try {
            Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
            Files.createLink(file, tmp);
            return true;
        } catch (FileAlreadyExistsException lost) {
            return false;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Long readSeed(Path file) throws IOException {
        try {
            Record rec = GSON.fromJson(Files.readString(file), Record.class);
            if (rec != null && rec.created > 0) {
                return rec.seed;
            }
        } catch (RuntimeException corrupt) {
        }
        setAside(file);
        return null;
    }

    private static void setAside(Path file) throws IOException {
        Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()),
            StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean persistent() {
        return persistent;
    }

    public long seedFor(String activity) {
        byte[] h = sha256((coreSeed + ":" + activity).getBytes(StandardCharsets.UTF_8));
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (h[i] & 0xff);
        }
        return v;
    }

    public String label() {
        return key.substring(0, 4);
    }

    String key() {
        return key;
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
