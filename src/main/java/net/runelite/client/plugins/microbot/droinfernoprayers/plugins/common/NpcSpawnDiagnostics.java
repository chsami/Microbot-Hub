package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.Npc;

public final class NpcSpawnDiagnostics
{
    private static final int MAX_TRACKED = 128;
    private static final int SUMMARY_INTERVAL = 100;
    private static final long SLOW_NPC_BUILD_MICROS = 50_000;
    private final Client client;
    private final ArenaProduct product;
    private final Consumer<Map<String, Object>> sink;
    private final Map<Long, Seen> tracked = new LinkedHashMap<>();
    private long snapshotTick = -1;
    private long nextOrdinal, snapshotNumber, snapshotStarted;
    private int snapshotClientTick, snapshotCycle, nullEntries, nullNames;

    private static final class Seen
    {
        final int id, index, worldView, spawnTick, spawnCycle;
        final long ordinal, spawnedAt;
        final WeakReference<NPC> npc;
        long firstSnapshotTick = -1;
        int firstClientTick = -1, firstCycle = -1;
        boolean wasPresent;
        Map<String, Object> details;

        Seen(NPC npc, int tick, int cycle, long ordinal)
        {
            id = npc.getId(); index = npc.getIndex(); worldView = worldView(npc);
            spawnTick = tick; spawnCycle = cycle;
            this.ordinal = ordinal; this.npc = new WeakReference<>(npc);
            spawnedAt = tick >= 0 ? System.nanoTime() : -1;
        }
    }

    public NpcSpawnDiagnostics(Client client, ArenaProduct product, Consumer<Map<String, Object>> sink)
    {
        this.client = client;
        this.product = product;
        this.sink = sink;
    }

    public void beginSnapshot(long tick)
    {
        snapshotTick = tick; snapshotNumber++; nullEntries = 0; nullNames = 0;
        snapshotStarted = System.nanoTime();
        observe("BEGIN_SNAPSHOT", () -> {
            snapshotClientTick = client.getTickCount(); snapshotCycle = client.getGameCycle();
        });
    }

    public void spawned(NPC npc)
    {
        observe("SPAWN", () -> {
            Map<String, Object> row = row("SPAWN", npc, false);
            if (npc != null)
            {
                Seen seen = new Seen(npc, client.getTickCount(), client.getGameCycle(), ++nextOrdinal);
                remember(key(npc), seen);
                origin(row, seen);

                publish(row);
                seen.details = details(npc);
                Map<String, Object> definition = row("SPAWN_DEFINITION", npc, false);
                origin(definition, seen); definition.putAll(seen.details);
                publish(definition);
            }
            else publish(row);
        });
    }

    public void despawned(NPC npc)
    {
        observe("DESPAWN", () -> {
            Seen seen = npc == null ? null : tracked.get(key(npc));

            if (seen != null && seen.npc.get() == npc)
            {
                tracked.remove(key(npc));
                Map<String, Object> row = row("DESPAWN", npc, false);
                origin(row, seen); row.put("despawnedBeforeSnapshot", seen.firstClientTick < 0);
                publish(row);
            }
        });
    }

    public void changed(NPC npc)
    {
        observe("COMPOSITION_CHANGED", () -> {
            if (npc == null) return;
            Map<String, Object> row = row("COMPOSITION_CHANGED", npc, false);
            Seen seen = tracked.get(key(npc));
            if (seen != null && seen.npc.get() == npc) origin(row, seen);
            row.putAll(details(npc)); publish(row);
        });
    }

    public void skipped(NPC npc)
    {
        observe("SKIP", () -> {
            if (npc == null) nullEntries++; else nullNames++;
            Map<String, Object> row = row(npc == null ? "SKIPPED_NULL_NPC" : "SKIPPED_NULL_NAME", npc, true);

            publish(row);
            if (npc != null)
            {
                Map<String, Object> definition = row("SKIPPED_DEFINITION", npc, true);
                definition.putAll(details(npc)); publish(definition);
            }
        });
    }

    public void snapshot(WorldView top, List<NPC> source, List<Npc> sent)
    {
        long buildMicros = snapshotStarted == 0 ? -1 : (System.nanoTime() - snapshotStarted) / 1_000;
        observe("SNAPSHOT", () -> {
            int firstCount = 0, missingCount = 0, changedCount = 0;
            Map<Long, NPC> candidates = new LinkedHashMap<>();
            for (NPC npc : source) if (npc != null) candidates.put(key(npc), npc);
            for (Map.Entry<Long, NPC> entry : candidates.entrySet())
            {
                NPC npc = entry.getValue();
                Seen seen = tracked.get(entry.getKey());
                if (seen == null || seen.npc.get() != npc)
                {
                    seen = new Seen(npc, -1, -1, ++nextOrdinal);
                    remember(entry.getKey(), seen);
                }
                boolean inSnapshot = sent.stream().anyMatch(value -> value.index == npc.getIndex() && value.id == npc.getId());
                boolean first = inSnapshot && seen.firstClientTick < 0;
                if (first)
                {
                    seen.firstSnapshotTick = snapshotTick; seen.firstClientTick = client.getTickCount();
                    seen.firstCycle = client.getGameCycle(); firstCount++;
                }
                if (!inSnapshot) missingCount++;
                Map<String, Object> details = details(npc);
                boolean changed = seen.details != null && !seen.details.equals(details);
                if (changed) changedCount++;
                if (first || !inSnapshot || changed
                    || (seen.spawnTick >= 0 && client.getTickCount() - seen.spawnTick <= 2))
                {
                    Map<String, Object> row = row(first ? "FIRST_SNAPSHOT" : "SNAPSHOT", npc, true);
                    row.put("topLevelAvailable", top != null);
                    row.put("inSource", true);
                    row.put("inSnapshot", inSnapshot);
                    origin(row, seen);
                    row.put("detailsChanged", changed);
                    if (first && seen.spawnTick >= 0)
                    {
                        row.put("spawnToSnapshotTicks", seen.firstClientTick - seen.spawnTick);
                        row.put("spawnToSnapshotCycles", seen.firstCycle - seen.spawnCycle);
                        row.put("spawnToSnapshotMicros", (System.nanoTime() - seen.spawnedAt) / 1_000);
                    }
                    row.putAll(details);
                    publish(row);
                }
                seen.wasPresent = true;
                seen.details = details;
            }
            for (Map.Entry<Long, Seen> entry : tracked.entrySet())
            {
                Seen seen = entry.getValue();
                if (!candidates.containsKey(entry.getKey()))
                {
                    if (seen.firstClientTick < 0 || seen.wasPresent)
                    {
                        Map<String, Object> row = row("ABSENT_FROM_SOURCE", null, true);
                        row.put("id", seen.id); row.put("index", seen.index); row.put("worldView", seen.worldView);
                        row.put("topLevelAvailable", top != null);
                        row.put("inSource", false); row.put("inSnapshot", false);
                        origin(row, seen); publish(row);
                    }
                    if (seen.firstClientTick < 0) missingCount++;
                    seen.wasPresent = false;
                }
            }
            boolean clockChanged = snapshotClientTick != client.getTickCount() || snapshotCycle != client.getGameCycle();
            if (snapshotNumber <= 1 || snapshotNumber % SUMMARY_INTERVAL == 0 || top == null || firstCount > 0
                || missingCount > 0 || changedCount > 0 || nullEntries > 0 || nullNames > 0
                || clockChanged || buildMicros >= SLOW_NPC_BUILD_MICROS)
            {
                Map<String, Object> row = row("SNAPSHOT_SUMMARY", null, true);
                row.put("topLevelAvailable", top != null);
                if (top != null) row.put("topLevelWorldView", top.getId());
                row.put("sourceNpcCount", source.size()); row.put("snapshotNpcCount", sent.size());
                row.put("nullNpcSkips", nullEntries); row.put("nullNameSkips", nullNames);
                row.put("firstSnapshots", firstCount); row.put("missingNpcCount", missingCount);
                row.put("npcBuildMicros", buildMicros); row.put("clockChangedDuringNpcBuild", clockChanged);
                row.put("buildStartClientTick", snapshotClientTick); row.put("buildStartGameCycle", snapshotCycle);
                publish(row);
            }
        });
    }

    private static void origin(Map<String, Object> row, Seen seen)
    {
        row.put("npcOrdinal", seen.ordinal); row.put("spawnId", seen.id);
        row.put("spawnClientTick", seen.spawnTick); row.put("spawnGameCycle", seen.spawnCycle);
        row.put("spawnObserved", seen.spawnTick >= 0);
        if (seen.firstClientTick >= 0)
        {
            row.put("firstSnapshotTick", seen.firstSnapshotTick);
            row.put("firstSnapshotClientTick", seen.firstClientTick);
            row.put("firstSnapshotGameCycle", seen.firstCycle);
        }
    }

    private void remember(long key, Seen seen)
    {
        if (!tracked.containsKey(key) && tracked.size() >= MAX_TRACKED)
        {
            tracked.remove(tracked.keySet().iterator().next());
            publish(row("TRACKING_CAPACITY_EVICTION", null, false));
        }
        tracked.put(key, seen);
    }

    private Map<String, Object> row(String phase, NPC npc, boolean snapshot)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event", "arena_npc_visibility"); row.put("phase", phase);
        row.put("product", product.productName);
        row.put("clientTick", client.getTickCount()); row.put("gameCycle", client.getGameCycle());
        row.put("clientThread", client.isClientThread());
        if (snapshot)
        {
            row.put("snapshotTick", snapshotTick); row.put("source", "TOP_LEVEL_WORLD_VIEW");
            row.put("snapshotNumber", snapshotNumber);
        }
        if (npc != null)
        {
            row.put("id", npc.getId()); row.put("index", npc.getIndex()); row.put("worldView", worldView(npc));
        }
        return row;
    }

    private static Map<String, Object> details(NPC npc)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        try
        {
            String name = npc.getName();
            row.put("nameNull", name == null); row.put("name", name == null ? "<null>" : name);
        }
        catch (RuntimeException failure) { row.put("nameReadError", failure.getClass().getSimpleName()); }
        try { composition(row, "base", npc.getComposition()); }
        catch (RuntimeException failure) { row.put("baseReadError", failure.getClass().getSimpleName()); }
        try { composition(row, "transformed", npc.getTransformedComposition()); }
        catch (RuntimeException failure) { row.put("transformedReadError", failure.getClass().getSimpleName()); }
        return row;
    }

    private static void composition(Map<String, Object> row, String prefix, NPCComposition composition)
    {
        row.put(prefix + "Null", composition == null);
        if (composition == null) return;
        row.put(prefix + "Id", composition.getId());
        String name = composition.getName();
        row.put(prefix + "NameNull", name == null);
        row.put(prefix + "Name", name == null ? "<null>" : name);
        row.put(prefix + "Size", composition.getSize());
    }

    private static int worldView(NPC npc) { return npc.getWorldView() == null ? -1 : npc.getWorldView().getId(); }
    private static long key(NPC npc) { return ((long) worldView(npc) << 32) | (npc.getIndex() & 0xffffffffL); }

    private void observe(String phase, Runnable observation)
    {
        try { observation.run(); }
        catch (RuntimeException failure)
        {
            publish(Map.of("event", "arena_npc_visibility", "phase", "OBSERVATION_FAILED",
                "product", product.productName, "snapshotTick", snapshotTick,
                "during", phase, "errorType", failure.getClass().getSimpleName()));
        }
    }

    private void publish(Map<String, Object> row)
    {
        try { sink.accept(Collections.unmodifiableMap(row)); }
        catch (RuntimeException ignored) {  }
    }
}
