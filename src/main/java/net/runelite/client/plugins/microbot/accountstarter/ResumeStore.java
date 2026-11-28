package net.runelite.client.plugins.microbot.accountstarter;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.accountstarter.logic.ResumeState;

@Slf4j
public final class ResumeStore {
    private static final Gson GSON = new Gson();

    private final Path dir;
    private boolean warned;

    public ResumeStore(Path dir) {
        this.dir = dir;
    }

    public ResumeState load(String accountKey) {
        Path file = file(accountKey);
        if (!Files.exists(file)) {
            return new ResumeState();
        }
        try {
            ResumeState s = GSON.fromJson(Files.readString(file), ResumeState.class);
            if (s != null) {
                return s;
            }
        } catch (IOException | RuntimeException e) {
        }
        if (!warned) {
            warned = true;
            log.warn("Account Starter: resume file unreadable, starting fresh");
        }
        return new ResumeState();
    }

    /** All saves share one temp file, and an interrupt would close it mid-write. */
    public synchronized void save(String accountKey, ResumeState state) {
        boolean interrupted = Thread.interrupted();
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("resume-" + accountKey + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(state));
            try {
                Files.move(tmp, file(accountKey), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file(accountKey), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Account Starter: could not save resume file: {}", e.getClass().getSimpleName());
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private Path file(String accountKey) {
        return dir.resolve("resume-" + accountKey + ".json");
    }
}
