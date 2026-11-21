package net.runelite.client.plugins.microbot.drorecorder;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

@Slf4j
final class DroRecorderWriter
{
    private static final int MAX_QUEUE = 4096;
    private static final int ESSENTIAL_RESERVE = 1024;
    private static final int MAX_LOSS_RANGES = 512;
    private static final WriteJob POISON = new WriteJob(null, false);

    private final BlockingQueue<WriteJob> queue = new LinkedBlockingQueue<>(MAX_QUEUE + 2);
    private final Path sessionDirectory;
    private final Path rawFile;
    private final Path eventsFile;
    private final Gson gson;
    private final boolean compressed;
    private final Map<String, AtomicLong> droppedByType = new ConcurrentHashMap<>();
    private final AtomicLong droppedLines = new AtomicLong();
    private final List<Map<String, Object>> pendingLoss = new ArrayList<>();
    private long essentialRecordsLost;

    @Getter
    private volatile boolean running;

    private Thread writerThread;
    @Getter
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    DroRecorderWriter(Path sessionDirectory, Gson gson)
    {
        this(sessionDirectory, gson, false);
    }

    DroRecorderWriter(Path sessionDirectory, Gson gson, boolean compressed)
    {
        this.gson = gson;
        this.compressed = compressed;
        this.sessionDirectory = sessionDirectory;
        this.rawFile = sessionDirectory.resolve("session.jsonl" + (compressed ? ".gz" : ""));
        this.eventsFile = sessionDirectory.resolve("events.jsonl" + (compressed ? ".gz" : ""));
    }

    Path getSessionDirectory()
    {
        return sessionDirectory;
    }

    long getDroppedLines()
    {
        return droppedLines.get();
    }

    void start()
    {
        if (running)
        {
            return;
        }

        running = true;
        writerThread = new Thread(this::writerLoop, "DroRecorder-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    void writeRaw(Map<String, Object> record)
    {
        enqueue(new WriteJob(record, false));
    }

    void writeEvent(Map<String, Object> record)
    {
        enqueue(new WriteJob(record, true));
    }

    private synchronized void enqueue(WriteJob job)
    {
        if (!running || job == null || job.record == null)
        {
            return;
        }

        boolean terminal = "session_stop".equals(job.record.get("type"));
        if (!terminal && !essential(job) && queue.size() >= MAX_QUEUE - ESSENTIAL_RESERVE)
        {
            recordDrop(job);
            return;
        }
        if (!terminal && queue.size() >= MAX_QUEUE)
        {
            // Retain admission order; discard background scene/mouse noise before combat evidence.
            WriteJob disposable = queue.stream().filter(queued -> queued != POISON && !essential(queued)).findFirst().orElse(null);
            if (disposable == null || !queue.remove(disposable))
            {
                recordDrop(job);
                return;
            }
            recordDrop(disposable);
        }
        if (!queue.offer(job)) { recordDrop(job); }
    }

    private static boolean essential(WriteJob job)
    {
        String type = String.valueOf(job.record.get("type"));
        return !type.equals("object_spawned") && !type.equals("object_despawned")
            && !type.equals("mouse_moved") && !type.equals("mouse_dragged");
    }

    synchronized void stop()
    {
        if (!running)
        {
            return;
        }

        running = false;
        // Two reserved slots guarantee room for the terminal record and shutdown marker.
        queue.offer(POISON);
    }

    private void writerLoop()
    {
        try
        {
            Files.createDirectories(sessionDirectory);

            try (BufferedWriter raw = openWriter(rawFile);
                 BufferedWriter events = openWriter(eventsFile))
            {
                long lastFlush = System.currentTimeMillis();
                int sinceFlush = 0;

                while (true)
                {
                    WriteJob job = queue.poll(1, TimeUnit.SECONDS);
                    if (job == null)
                    {
                        raw.flush();
                        events.flush();
                        continue;
                    }
                    // stop() is ordered with enqueue(); all accepted work precedes POISON.
                    if (job == POISON) { break; }
                    writeJob(raw, events, job);
                    sinceFlush++;

                    long now = System.currentTimeMillis();
                    if (sinceFlush >= 100 || now - lastFlush >= 1000)
                    {
                        raw.flush();
                        events.flush();
                        lastFlush = now;
                        sinceFlush = 0;
                    }
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            completion.completeExceptionally(e);
        }
        catch (IOException | RuntimeException e)
        {
            completion.completeExceptionally(e);
            log.error("DroRecorder writer failed", e);
        }
        finally
        {
            running = false;
            completion.complete(null);
        }
    }

    private BufferedWriter openWriter(Path path) throws IOException
    {
        OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try
        {
            if (compressed) { stream = new GZIPOutputStream(stream, 65536, true); }
            return new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), 65536);
        }
        catch (IOException | RuntimeException e)
        {
            stream.close();
            throw e;
        }
    }

    private void writeJob(BufferedWriter raw, BufferedWriter events, WriteJob job) throws IOException
    {
        List<Map<String, Object>> losses;
        synchronized (this)
        {
            losses = new ArrayList<>();
            // Gap records are produced by the writer, so they cannot themselves be crowded out.
            long sequence = number(job.record, "sequence", Long.MAX_VALUE);
            boolean terminal = "session_stop".equals(job.record.get("type"));
            var iterator = pendingLoss.iterator();
            while (iterator.hasNext())
            {
                Map<String, Object> loss = iterator.next();
                if (terminal || number(loss, "lastSequence", 0) < sequence)
                {
                    losses.add(new LinkedHashMap<>(loss));
                    iterator.remove();
                }
            }
        }
        for (Map<String, Object> loss : losses)
        {
            String gap = gson.toJson(loss);
            raw.write(gap); raw.write('\n'); events.write(gap); events.write('\n');
        }
        if ("session_stop".equals(job.record.get("type")))
        {
            job.record.put("droppedLines", droppedLines.get());
            job.record.put("droppedByType", getDroppedByType());
            job.record.put("essentialRecordsLost", essentialRecordsLost);
        }
        String json = gson.toJson(job.record);
        raw.write(json);
        raw.write('\n');

        if (job.discreteEvent)
        {
            events.write(json);
            events.write('\n');
        }
    }

    private synchronized void recordDrop(WriteJob job)
    {
        droppedLines.incrementAndGet();
        if (job.record != null)
        {
            droppedByType.computeIfAbsent(String.valueOf(job.record.get("type")), key -> new AtomicLong()).incrementAndGet();
            if (essential(job)) { essentialRecordsLost++; }
            long tick = number(job.record, "tick", -1);
            long sequence = number(job.record, "sequence", -1);
            String type = String.valueOf(job.record.get("type"));
            Map<String, Object> range = pendingLoss.isEmpty() ? null : pendingLoss.get(pendingLoss.size() - 1);
            boolean compact = pendingLoss.size() >= MAX_LOSS_RANGES;
            if (range == null || !compact && (number(range, "lastTick", -2) != tick || !type.equals(range.get("lostType"))))
            {
                range = new LinkedHashMap<>();
                range.put("type", "recording_gap");
                range.put("tick", tick);
                range.put("firstTick", tick); range.put("lastTick", tick);
                range.put("firstSequence", sequence); range.put("lastSequence", sequence);
                range.put("lostType", type); range.put("count", 0L);
                range.put("essential", essential(job));
                range.put("timestampMs", job.record.get("timestampMs"));
                range.put("sessionMs", job.record.get("sessionMs"));
                pendingLoss.add(range);
            }
            range.put("firstTick", Math.min(number(range, "firstTick", tick), tick));
            range.put("lastTick", Math.max(number(range, "lastTick", tick), tick));
            range.put("firstSequence", Math.min(number(range, "firstSequence", sequence), sequence));
            range.put("lastSequence", Math.max(number(range, "lastSequence", sequence), sequence));
            range.put("count", number(range, "count", 0) + 1);
            if (compact) { range.put("rangesCompacted", true); range.put("lostType", "MIXED"); }
            if (essential(job)) { range.put("essential", true); }
        }
    }

    private static long number(Map<String, Object> record, String key, long fallback)
    {
        Object value = record.get(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    Map<String, Long> getDroppedByType()
    {
        Map<String, Long> result = new LinkedHashMap<>();
        droppedByType.forEach((type, count) -> result.put(type, count.get()));
        return result;
    }

    private static final class WriteJob
    {
        private final Map<String, Object> record;
        private final boolean discreteEvent;

        private WriteJob(Map<String, Object> record, boolean discreteEvent)
        {
            this.record = record == null ? null : new LinkedHashMap<>(record);
            this.discreteEvent = discreteEvent;
        }
    }
}
