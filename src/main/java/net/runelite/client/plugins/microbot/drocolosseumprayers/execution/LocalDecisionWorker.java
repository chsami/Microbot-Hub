package net.runelite.client.plugins.microbot.drocolosseumprayers.execution;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * One local decision owner, away from the client thread. There is at most one waiting snapshot; a busy
 * decision never creates a backlog of old game ticks. The existing command runner still owns delivery.
 */
final class LocalDecisionWorker implements AutoCloseable
{
    interface Engine
    {
        void reset();
        void configure(Map<String, Object> options);
        List<JsonObject> decide(JsonObject snapshot);
        void onResult(JsonObject result);
        String status();
        default boolean readyForFirstWave(){return false;}
    }

    private static final long MAX_AGE_NANOS = TimeUnit.MILLISECONDS.toNanos(600);
    private static final int MAX_ACTIONS = 32;
    private static final int MAX_RECEIPTS = 128;
    private final Engine engine;
    private final BiConsumer<Long, JsonObject> commands;
    private final BiConsumer<Long, String> failure;
    private final LongSupplier currentTick;
    private final LongSupplier clock;
    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong commandIds = new AtomicLong();
    private final AtomicReference<Frame> waiting = new AtomicReference<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Receipt> receipts = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> outstanding =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> outstandingOrder = new ConcurrentLinkedQueue<>();
    private volatile Map<String, Object> options = Collections.emptyMap();
    private volatile boolean accepting;
    private volatile boolean readyForFirstWave;
    private volatile boolean closed;
    private volatile String status = "Stopped";
    private volatile java.util.function.BooleanSupplier inputAllowed = () -> true;
    /** Read/written only by the decision executor. */
    private long initializedGeneration = Long.MIN_VALUE;

    LocalDecisionWorker(Engine engine, Consumer<JsonObject> commands, LongSupplier currentTick,
        Consumer<String> failure)
    {
        this(engine, commands, currentTick, (owner, reason) -> failure.accept(reason));
    }

    LocalDecisionWorker(Engine engine, Consumer<JsonObject> commands, LongSupplier currentTick,
        BiConsumer<Long, String> failure)
    {
        this(engine, (owner, packet) -> commands.accept(packet), currentTick, failure);
    }

    LocalDecisionWorker(Engine engine, BiConsumer<Long, JsonObject> commands, LongSupplier currentTick,
        BiConsumer<Long, String> failure)
    {
        this(engine, commands, currentTick, failure, System::nanoTime,
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "dro-inferno-local-decisions");
                thread.setDaemon(true);
                return thread;
            }));
    }

    LocalDecisionWorker(Engine engine, Consumer<JsonObject> commands, LongSupplier currentTick,
        Consumer<String> failure, LongSupplier clock, ExecutorService executor)
    {
        this(engine, commands, currentTick, (owner, reason) -> failure.accept(reason), clock, executor);
    }

    LocalDecisionWorker(Engine engine, Consumer<JsonObject> commands, LongSupplier currentTick,
        BiConsumer<Long, String> failure, LongSupplier clock, ExecutorService executor)
    {
        this(engine, (owner, packet) -> commands.accept(packet), currentTick, failure, clock, executor);
    }

    LocalDecisionWorker(Engine engine, BiConsumer<Long, JsonObject> commands, LongSupplier currentTick,
        BiConsumer<Long, String> failure, LongSupplier clock, ExecutorService executor)
    {
        this.engine = java.util.Objects.requireNonNull(engine);
        this.commands = java.util.Objects.requireNonNull(commands);
        this.currentTick = java.util.Objects.requireNonNull(currentTick);
        this.failure = java.util.Objects.requireNonNull(failure);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.executor = java.util.Objects.requireNonNull(executor);
    }

    synchronized void start(Map<String, Object> settings)
    {
        if (closed)
        {
            throw new IllegalStateException("Local decision worker is closed");
        }
        stop();
        options = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        status = "Ready; waiting for an arena snapshot";
        accepting = true;
        // Begin corpus/configuration work beside the entrance without waiting for wave one's first tick.
        waiting.set(new Frame(generation.get(), Long.MIN_VALUE, clock.getAsLong(), null, options));
        schedule();
    }

    synchronized void stop()
    {
        accepting = false;
        readyForFirstWave = false;
        generation.incrementAndGet();
        waiting.set(null);
        receipts.clear();
        outstanding.clear();
        outstandingOrder.clear();
        status = "Stopped";
    }

    boolean readyForFirstWave(){return accepting&&!closed&&readyForFirstWave;}

    String status()
    {
        return status;
    }

    void inputAllowed(java.util.function.BooleanSupplier allowed)
    {
        inputAllowed = java.util.Objects.requireNonNull(allowed);
    }

    /** A delayed failure notification cannot terminate a newer run. */
    boolean failedGeneration(long token)
    {
        return !closed && !accepting && generation.get() == token;
    }

    /** The local session rechecks this under its lifecycle lock before the runner adopts a packet. */
    boolean currentGeneration(long token)
    {
        return !closed && accepting && generation.get() == token;
    }

    /** The caller owns the client-thread capture; no live game object enters this worker. */
    void offer(JsonObject snapshot)
    {
        if (!accepting || closed || !snapshot.has("tick"))
        {
            return;
        }
        long gen = generation.get();
        Frame frame = new Frame(gen, snapshot.get("tick").getAsLong(), clock.getAsLong(),
            snapshot.deepCopy(), options);
        if (!accepting || gen != generation.get())
        {
            return;
        }
        waiting.set(frame);
        schedule();
    }

    void result(JsonObject result)
    {
        if (!accepting || closed || !result.has("id"))
        {
            return;
        }
        Long owner = outstanding.remove(result.get("id").getAsLong());
        if (owner == null || owner.longValue() != generation.get())
        {
            return;
        }
        while (receipts.size() >= MAX_RECEIPTS)
        {
            receipts.poll();
        }
        receipts.add(new Receipt(owner, result.deepCopy()));
    }

    private void schedule()
    {
        if (!draining.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            executor.execute(this::drain);
        }
        catch (RejectedExecutionException rejected)
        {
            draining.set(false);
            if (!closed && accepting)
            {
                fail(generation.get(), "Decision executor unavailable");
            }
        }
    }

    private void drain()
    {
        try
        {
            Frame frame;
            while ((frame = waiting.getAndSet(null)) != null)
            {
                if (!current(frame))
                {
                    continue;
                }
                try
                {
                    if (initializedGeneration != frame.generation)
                    {
                        engine.reset();
                        engine.configure(frame.options);
                        initializedGeneration = frame.generation;
                        synchronized(this){if(current(frame))readyForFirstWave=true;}
                    }
                    if (frame.snapshot == null)
                    {
                        continue;
                    }
                    Receipt receipt;
                    while ((receipt = receipts.poll()) != null)
                    {
                        if (receipt.generation == frame.generation)
                        {
                            engine.onResult(receipt.result);
                        }
                    }
                    List<JsonObject> actions = engine.decide(frame.snapshot);
                    String decidedStatus=engine.status();
                    synchronized(this)
                    {
                        if(current(frame)){status=decidedStatus;}
                    }
                    if (actions == null || actions.size() > MAX_ACTIONS)
                    {
                        throw new IllegalArgumentException("Unbounded local decision");
                    }
                    // Freeze the complete plan before handing anything to the asynchronous action runner.
                    List<JsonObject> packets = new ArrayList<>(actions.size());
                    for (JsonObject action : actions)
                    {
                        if (action == null || !action.has("op") || !action.has("args")
                            || !action.get("args").isJsonObject())
                        {
                            throw new IllegalArgumentException("Invalid local decision");
                        }
                        JsonObject packet = action.deepCopy();
                        packet.addProperty("type", "command");
                        packet.addProperty("id", commandIds.incrementAndGet());
                        packet.addProperty("tick", frame.tick);
                        if (!packet.has("deadlineMs"))
                        {
                            packet.addProperty("deadlineMs", 600);
                        }
                        packets.add(packet);
                    }
                    for (JsonObject packet : packets)
                    {
                        if (!current(frame) || currentTick.getAsLong() != frame.tick
                            || clock.getAsLong() - frame.at > MAX_AGE_NANOS || !inputAllowed.getAsBoolean())
                        {
                            break;
                        }
                        long id = packet.get("id").getAsLong();
                        outstanding.put(id, frame.generation);
                        outstandingOrder.add(id);
                        while (outstandingOrder.size() > 256)
                        {
                            Long oldest = outstandingOrder.poll();
                            if (oldest != null)
                            {
                                outstanding.remove(oldest);
                            }
                        }
                        commands.accept(frame.generation, packet);
                    }
                }
                catch (RuntimeException invalid)
                {
                    fail(frame.generation, "Decision failed: " + invalid.getClass().getSimpleName());
                }
            }
        }
        finally
        {
            draining.set(false);
            if (!closed && accepting && waiting.get() != null)
            {
                schedule();
            }
        }
    }

    private boolean current(Frame frame)
    {
        return currentGeneration(frame.generation);
    }

    private void fail(long gen, String reason)
    {
        long token;
        synchronized (this)
        {
            if (closed || !accepting || gen != generation.get())
            {
                return;
            }
            stop();
            status = reason;
            token = generation.get();
        }
        // The local session may hold its own lifecycle lock while starting this worker; never call it under ours.
        failure.accept(token, reason);
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        stop();
        executor.shutdownNow();
    }

    private static final class Frame
    {
        final long generation;
        final long tick;
        final long at;
        final JsonObject snapshot;
        final Map<String, Object> options;

        Frame(long generation, long tick, long at, JsonObject snapshot, Map<String, Object> options)
        {
            this.generation = generation;
            this.tick = tick;
            this.at = at;
            this.snapshot = snapshot;
            this.options = options;
        }
    }

    private static final class Receipt
    {
        final long generation;
        final JsonObject result;

        Receipt(long generation, JsonObject result)
        {
            this.generation = generation;
            this.result = result;
        }
    }
}
