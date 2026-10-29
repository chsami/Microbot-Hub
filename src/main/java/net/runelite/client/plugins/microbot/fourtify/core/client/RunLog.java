package net.runelite.client.plugins.microbot.fourtify.core.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;
import net.runelite.client.plugins.microbot.fourtify.core.engine.TickEntry;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes a live run readable without a screen. Each tick's {@link TickEntry} goes to
 * <ul>
 *     <li>a JSON-lines file in {@code ~/.runelite/fourtify/runs/}, one per run and at most {@value #MAX_FILE_MB} MB,
 *     which tests can replay;</li>
 *     <li>the agent server's result store under {@code <script class>.ticks}
 *     ({@code GET /scripts/results?className=...}), capped at {@value #STORE_LIMIT} records.</li>
 * </ul>
 * When the run ends, one verdict goes to the result store under the script's class name: whether it finished,
 * why it stopped, XP gained, every failure, and the run log file.
 */
@Slf4j
public class RunLog {
    /** About five minutes of ticks, the length of a live check, without holding much of the client's memory. */
    static final int STORE_LIMIT = 500;
    static final int RUNS_KEPT = 20;
    /** With 20 runs kept, the run logs never take more than 1 GB of disk. */
    static final int MAX_FILE_MB = 50;
    private static final Gson GSON = new Gson();
    private static final Type MAP = new TypeToken<Map<String, Object>>() { }.getType();

    private final String key;
    private final Path file;
    private BufferedWriter out;
    private int stored;
    private long written;
    private final long maxFileBytes;
    private String fileProblem;
    private final List<String> failures = new ArrayList<>();

    public RunLog(String key, Path dir, String runName) {
        this(key, dir, runName, MAX_FILE_MB * 1024L * 1024L);
    }

    RunLog(String key, Path dir, String runName, long maxFileBytes) {
        this.key = key;
        this.maxFileBytes = maxFileBytes;
        String name = runName.replaceAll("[^A-Za-z0-9]+", "-") + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl";
        this.file = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            prune(dir);
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            fileProblem = "no run log file: " + e.getMessage();
            log.warn("Cannot write the run log {}: {}", file, e.getMessage());
        }
        ScriptResultStore.clear(key + ".ticks");
        ScriptResultStore.clear(key);
    }

    /** The default place: {@code ~/.runelite/fourtify/runs}. */
    public static Path defaultDir() {
        return new File(System.getProperty("user.home"), ".runelite/fourtify/runs").toPath();
    }

    public Path file() {
        return file;
    }

    public synchronized void record(TickEntry record) {
        for (String event : record.getEvents()) {
            if (event.contains(" failed (") || event.contains(" skipped after ")) {
                failures.add("tick " + record.getState().getTick() + " " + event);
            }
        }
        String json = GSON.toJson(record);
        if (out != null) {
            if (written + json.length() > maxFileBytes) {
                fileProblem = "run log file stopped at " + maxFileBytes / 1024 + " KB (tick " + record.getState().getTick() + ")";
                closeFile();
            } else {
                try {
                    out.write(json);
                    out.newLine();
                    out.flush();
                    written += json.length() + 1;
                } catch (IOException e) {
                    fileProblem = "run log file stopped: " + e.getMessage();
                    log.warn("Cannot write the run log {}: {}", file, e.getMessage());
                    closeFile();
                }
            }
        }
        if (++stored > STORE_LIMIT) {
            ScriptResultStore.clear(key + ".ticks");
            stored = 1;
        }
        ScriptResultStore.submit(key + ".ticks", GSON.fromJson(json, MAP));
    }

    /** Once, when the run ends. */
    public synchronized void verdict(String reason, boolean finished, GameState first, GameState last) {
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("finished", finished);
        verdict.put("reason", reason);
        verdict.put("ticks", first == null || last == null ? 0 : last.getTick() - first.getTick());
        Map<String, Integer> xp = new LinkedHashMap<>();
        if (first != null && last != null) {
            for (Map.Entry<Skill, Integer> entry : last.getXp().entrySet()) {
                int gained = entry.getValue() - first.xp(entry.getKey());
                if (gained > 0) {
                    xp.put(entry.getKey().getName(), gained);
                }
            }
        }
        verdict.put("xpGained", xp);
        verdict.put("failures", failures);
        verdict.put("runLog", file.toString());
        if (fileProblem != null) {
            verdict.put("runLogProblem", fileProblem);
        }
        ScriptResultStore.submit(key, verdict);
        closeFile();
    }

    private void closeFile() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // nothing more is written to it
            }
            out = null;
        }
    }

    /** Keeps the newest {@value #RUNS_KEPT} run logs, so the disk does not fill up. */
    private static void prune(Path dir) {
        File[] runs = dir.toFile().listFiles((d, name) -> name.endsWith(".jsonl"));
        if (runs == null || runs.length < RUNS_KEPT) {
            return;
        }
        Arrays.sort(runs, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = RUNS_KEPT - 1; i < runs.length; i++) {
            if (!runs[i].delete()) {
                log.warn("Cannot delete old run log {}", runs[i]);
            }
        }
    }
}
