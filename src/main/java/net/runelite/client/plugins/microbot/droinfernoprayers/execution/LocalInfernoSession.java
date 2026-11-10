package net.runelite.client.plugins.microbot.droinfernoprayers.execution;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaFightInput;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDispatcher;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoDecisionEngine;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.Commands;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common.State;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno.InfernoCommands;
import net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno.InfernoState;
import net.runelite.client.plugins.microbot.droinfernoprayers.DroRecorderDiagnostic;

/**
 * Local Inferno lifecycle, capture and command admission. Only the bounded decision worker owns a
 * background executor; validated actions use the existing native arena input and receipt owners.
 */
public final class LocalInfernoSession implements AutoCloseable
{
    private static final Gson RECORDING_JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final Set<String> SECTIONS = Set.of("player", "npcs", "prayers", "inventory",
        "events", "projectiles", "equipment", "ground", "camera", "collision");
    private final Events events = new Events();
    private final StateMessage stateMessage = new StateMessage();
    private final CommandRunner runner;
    private final LocalDecisionWorker worker;
    private final BooleanSupplier allowed;
    private final Consumer<String> log;
    private Map<String, Object> options = Map.of();
    private boolean ancientSpellbook;
    private int bloodAutocastLevel;
    private int iceRunes = -1;
    private volatile boolean running;
    private volatile boolean closed;
    private volatile String lastEnd;

    public LocalInfernoSession(Consumer<Runnable> gameThread, Consumer<String> log,
        BooleanSupplier allowed, InfernoDecisionEngine engine)
    {
        this(gameThread, log, allowed, adapt(Objects.requireNonNull(engine)), null, null);
    }

    /** Deterministic worker scheduling for the lifecycle and action-admission regressions. */
    LocalInfernoSession(Consumer<Runnable> gameThread, Consumer<String> log, BooleanSupplier allowed,
        LocalDecisionWorker.Engine engine, LongSupplier clock, ExecutorService executor)
    {
        this.allowed = Objects.requireNonNull(allowed);
        this.log = Objects.requireNonNull(log);
        runner = new CommandRunner(gameThread, log, this::commandResult,
            () -> running && allowed.getAsBoolean(), () -> end("Local Inferno: blocked unapproved action"));
        worker = executor == null
            ? new LocalDecisionWorker(engine, this::command, runner::tick, this::failure)
            : new LocalDecisionWorker(engine, this::command, runner::tick, this::failure, clock, executor);
        worker.inputAllowed(allowed);
    }

    private static LocalDecisionWorker.Engine adapt(InfernoDecisionEngine engine)
    {
        return new LocalDecisionWorker.Engine()
        {
            @Override public void reset() { engine.reset(); }
            @Override public void configure(Map<String, Object> settings) { engine.configure(settings); }
            @Override public List<JsonObject> decide(JsonObject snapshot) { return engine.decide(snapshot); }
            @Override public void onResult(JsonObject result) { engine.onResult(result); }
            @Override public String status() { return engine.status(); }
        };
    }

    public LocalInfernoSession input(ArenaInputDispatcher input)
    {
        runner.executor(new CommandRunner.Executor()
        {
            @Override public boolean prepare(String op) { return input.prepare(op); }
            @Override public net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputScope.ClickHandler mouse() { return input.mouse(); }
            @Override public void execute(String op, int priority, BooleanSupplier fresh, Runnable task) { input.execute(op, priority, fresh, task); }
            @Override public void gesture(String op, int priority, BooleanSupplier fresh, Runnable task) { input.gesture(op, priority, fresh, task); }
        });
        return this;
    }

    public LocalInfernoSession fightInput(Supplier<ArenaFightInput> mode)
    {
        runner.fightInput(mode);
        return this;
    }

    /** "Prayers only": every non-prayer command from the engine is dropped; the player controls the rest. */
    private volatile java.util.function.BooleanSupplier prayersOnly=()->false;
    public LocalInfernoSession prayersOnly(java.util.function.BooleanSupplier value)
    {
        prayersOnly=value==null?()->false:value;
        return this;
    }
    /** "Manual movement": the engine's walks are dropped; the player moves, everything else stays automatic. */
    private volatile BooleanSupplier manualMovement=()->false;
    public LocalInfernoSession manualMovement(BooleanSupplier value)
    {
        manualMovement=value==null?()->false:value;
        return this;
    }
    private boolean dropped(JsonObject packet)
    {
        var op=packet.get("op");
        if(!prayersOnly.getAsBoolean())
            return manualMovement.getAsBoolean()&&op!=null&&op.isJsonPrimitive()&&"movement.tile".equals(op.getAsString());
        return op==null||!op.isJsonPrimitive()||!op.getAsString().startsWith("prayer.");
    }

    public LocalInfernoSession common(State state, Commands commands)
    {
        stateMessage.state = state;
        runner.commands = commands;
        if (commands != null) stateMessage.colosseumUi = commands.interfaceState();
        return this;
    }

    public LocalInfernoSession inferno(InfernoState state, InfernoCommands commands)
    {
        stateMessage.infernoState = state;
        runner.infernoCommands = commands;
        return this;
    }

    public LocalInfernoSession settings(Map<String, Object> settings)
    {
        options = settings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        return this;
    }

    public Events events() { return events; }
    public boolean running() { return running; }
    public String lastEnd() { return lastEnd; }
    public String status() { return lastEnd != null ? lastEnd : "Local: " + worker.status(); }

    /** Capture uses the same native spell checks as arena preparation. Client thread only. */
    public void spellState(boolean ancient, int bloodLevel)
    {
        ancientSpellbook = ancient;
        bloodAutocastLevel = ancient && (bloodLevel == 56 || bloodLevel == 68 || bloodLevel == 80 || bloodLevel == 92) ? bloodLevel : 0;
    }

    /** Carried Ice rune tiers (InfernoSnapshot ICE_* bits). Client thread only. */
    public void iceRunes(int tiers) { iceRunes = ancientSpellbook ? tiers : 0; }

    /** Client-thread game/varbit receipts remain on the shared prayer diagnostics owner. */
    public void observePrayers(Client client)
    {
        if (running) runner.observePrayers(id -> client.getVarbitValue(id) == 1);
    }

    public void observePrayerMenu(int widget, boolean consumed)
    {
        if (running) runner.observePrayerMenu(widget, consumed);
    }

    public synchronized void start()
    {
        if (closed) throw new IllegalStateException("Local Inferno session is closed");
        stop();
        lastEnd = null;
        worker.start(options);
        stateMessage.welcome(SECTIONS, new int[]{2240, 5312, 2303, 5375}, null, new int[0]);
        running = true;
        events.collect(true);
        // Main recorder keeps its native sampling; combat input never changes recorder settings.
        lifecycle("WELCOME", "LOCAL_ENGINE_READY");
        log.accept("Local Inferno controller ready");
    }

    public synchronized void stop()
    {
        running = false;
        worker.stop();
        runner.clear();
        events.collect(false);
        stateMessage.clear();
        // Main recorder settings remain owned by Recorder.
        lifecycle("STOP", lastEnd == null ? "CLOSED_LOCALLY" : "SESSION_ENDED");
    }

    /** Fresh complete capture on the client thread; inference and corpus loading stay off-thread. */
    public synchronized void onGameTick()
    {
        runner.nextTick();
        if (!running) return;
        runner.runDue();
        if (!running) return;
        JsonObject snapshot;
        try { snapshot = stateMessage.buildFull(runner.tick(), events.drain()); }
        catch (RuntimeException unavailable)
        {
            end("Local Inferno: snapshot capture failed: " + unavailable.getClass().getSimpleName());
            return;
        }
        snapshot.addProperty("inputAllowed", allowed.getAsBoolean());
        if (snapshot.has("player"))
        {
            snapshot.getAsJsonObject("player").addProperty("ancientSpellbook", ancientSpellbook);
            snapshot.getAsJsonObject("player").addProperty("bloodAutocast", bloodAutocastLevel > 0);
            snapshot.getAsJsonObject("player").addProperty("bloodAutocastLevel", bloodAutocastLevel);
            if (iceRunes >= 0) snapshot.getAsJsonObject("player").addProperty("iceRunes", iceRunes);
        }
        worker.offer(snapshot);
        record(Map.of("record", "arena_local_state", "tick", runner.tick(),
            "status", String.valueOf(worker.status()), "snapshot", RECORDING_JSON.toJson(snapshot)));
    }

    /** Admission and start/stop share a short lock; engine computation never holds it. */
    private synchronized void command(long generation, JsonObject packet, BooleanSupplier fresh)
    {
        if (running && worker.currentGeneration(generation) && fresh.getAsBoolean() && !dropped(packet)) runner.onCommand(packet);
    }

    private void commandResult(JsonObject result)
    {
        worker.result(result);
    }

    private synchronized void failure(long generation, String reason)
    {
        if (worker.failedGeneration(generation)) end("Local Inferno: " + reason);
    }

    private synchronized void end(String reason)
    {
        lastEnd = reason;
        stop();
        log.accept(reason);
    }

    private void lifecycle(String stage, String reason)
    {
        record(Map.of("record", "arena_connection", "product", ArenaProduct.INFERNO.protocolName,
            "status", running ? "LOCAL_READY" : "LOCAL_STOPPED", "stage", stage, "reason", reason,
            "cause", lastEnd == null ? "NONE" : lastEnd, "attempt", 1));
    }

    private static void record(Map<String, Object> values)
    {
        try
        {
            var bus = Microbot.getEventBus();
            if (bus != null) bus.post(new DroRecorderDiagnostic(values));
        }
        catch (RuntimeException unavailable) { /* Recorder failure cannot interrupt local execution. */ }
    }

    @Override public synchronized void close()
    {
        if (closed) return;
        closed = true;
        stop();
        worker.close();
    }
}
