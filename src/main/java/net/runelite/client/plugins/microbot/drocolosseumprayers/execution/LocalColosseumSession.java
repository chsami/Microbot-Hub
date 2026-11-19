package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

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
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaFightInput;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputDispatcher;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumDecisionEngine;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.Commands;
import net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common.State;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaDiagnostic;

public final class LocalColosseumSession implements AutoCloseable
{
    private static final Gson SNAPSHOT_JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final Set<String> SECTIONS = Set.of("player", "npcs", "prayers", "events", "projectiles", "equipment", "collision", "widgets");
    private final Events events = new Events();
    private final StateMessage stateMessage = new StateMessage();
    private final CommandRunner runner;
    private final LocalDecisionWorker worker;
    private final BooleanSupplier allowed;
    private final Consumer<String> log;
    private Map<String, Object> options = Map.of();
    private volatile boolean running;
    private volatile boolean closed;
    private volatile String lastEnd;

    public LocalColosseumSession(Consumer<Runnable> gameThread, Consumer<String> log,
        BooleanSupplier allowed, ColosseumDecisionEngine engine)
    {
        this(gameThread, log, allowed, adapt(Objects.requireNonNull(engine)), null, null);
    }

    LocalColosseumSession(Consumer<Runnable> gameThread, Consumer<String> log, BooleanSupplier allowed,
        LocalDecisionWorker.Engine engine, LongSupplier clock, ExecutorService executor)
    {
        this.allowed = Objects.requireNonNull(allowed);
        this.log = Objects.requireNonNull(log);
        runner = new CommandRunner(gameThread, log, this::commandResult,
            () -> running && allowed.getAsBoolean(), () -> end("Local Colosseum: blocked unapproved action"));

        runner.product(ArenaProduct.COLOSSEUM);
        worker = executor == null
            ? new LocalDecisionWorker(engine, this::command, runner::tick, this::failure)
            : new LocalDecisionWorker(engine, this::command, runner::tick, this::failure, clock, executor);
        worker.inputAllowed(allowed);
    }

    private static LocalDecisionWorker.Engine adapt(ColosseumDecisionEngine engine)
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

    public LocalColosseumSession input(ArenaInputDispatcher input)
    {
        runner.executor(new CommandRunner.Executor()
        {
            @Override public boolean prepare(String op) { return input.prepare(op); }
            @Override public net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope.ClickHandler mouse() { return input.mouse(); }
            @Override public void execute(String op, int priority, BooleanSupplier fresh, Runnable task) { input.execute(op, priority, fresh, task); }
            @Override public void gesture(String op, int priority, BooleanSupplier fresh, Runnable task) { input.gesture(op, priority, fresh, task); }
        });
        return this;
    }

    public LocalColosseumSession fightInput(Supplier<ArenaFightInput> mode)
    {
        runner.fightInput(mode);
        return this;
    }

    public LocalColosseumSession common(State state, Commands commands)
    {
        stateMessage.state = state;
        runner.commands = commands;
        return this;
    }

    public LocalColosseumSession settings(Map<String, Object> settings)
    {
        options = settings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        return this;
    }

    public Events events() { return events; }
    public boolean running() { return running; }
    public String lastEnd() { return lastEnd; }
    public String status() { return lastEnd != null ? lastEnd : "Local: " + worker.status(); }

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
        if (closed) throw new IllegalStateException("Local Colosseum session is closed");
        stop();
        lastEnd = null;
        worker.start(options);
        stateMessage.welcome(SECTIONS, new int[]{1792, 3072, 1855, 3135}, null, new int[]{56688655, 56688656, 56688657});
        running = true;
        events.collect(true);

        lifecycle("WELCOME", "LOCAL_ENGINE_READY");
        log.accept("Local Colosseum controller ready");
    }

    public synchronized void stop()
    {
        running = false;
        worker.stop();
        runner.clear();
        events.collect(false);
        stateMessage.clear();

        lifecycle("STOP", lastEnd == null ? "CLOSED_LOCALLY" : "SESSION_ENDED");
    }

    public void onGameTick()
    {
        runner.nextTick();
        if (!running) return;

        stateMessage.capturePrayers();
        runner.runDue();
        if (!running) return;
        JsonObject snapshot;
        try { snapshot = stateMessage.buildFull(runner.tick(), events.drain()); }
        catch (RuntimeException unavailable)
        {
            end("Local Colosseum: snapshot capture failed: " + unavailable.getClass().getSimpleName());
            return;
        }
        snapshot.addProperty("inputAllowed", allowed.getAsBoolean());
        worker.offer(snapshot);
        publish(Map.of("event", "arena_local_state", "tick", runner.tick(), "status", String.valueOf(worker.status()),
            "snapshot", SNAPSHOT_JSON.toJson(snapshot)));
    }

    private synchronized void command(long generation, JsonObject packet)
    {
        if (running && worker.currentGeneration(generation)) runner.onCommand(packet);
    }

    private void commandResult(JsonObject result)
    {
        worker.result(result);
    }

    private synchronized void failure(long generation, String reason)
    {
        if (worker.failedGeneration(generation)) end("Local Colosseum: " + reason);
    }

    private synchronized void end(String reason)
    {
        lastEnd = reason;
        stop();
        log.accept(reason);
    }

    private void lifecycle(String stage, String reason)
    {
        publish(Map.of("event", "arena_session", "product", ArenaProduct.COLOSSEUM.productName,
            "status", running ? "LOCAL_READY" : "LOCAL_STOPPED", "stage", stage, "reason", reason,
            "cause", lastEnd == null ? "NONE" : lastEnd, "attempt", 1));
    }

    private static void publish(Map<String, Object> values)
    {
        try
        {
            var bus = Microbot.getEventBus();
            if (bus != null) bus.post(new ArenaDiagnostic(values));
        }
        catch (RuntimeException unavailable) {  }
    }

    @Override public synchronized void close()
    {
        if (closed) return;
        closed = true;
        stop();
        worker.close();
    }
}
