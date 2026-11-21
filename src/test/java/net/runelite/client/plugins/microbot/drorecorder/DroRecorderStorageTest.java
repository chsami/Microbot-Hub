package net.runelite.client.plugins.microbot.drorecorder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class DroRecorderStorageTest
{
    @TempDir Path directory;

    @Test void crowdEventsShareOneNpcContextPerTickAndKeepIndividualEvents() throws Exception
    {
        net.runelite.api.Client client = org.mockito.Mockito.mock(net.runelite.api.Client.class);
        DroRecorderConfig config = org.mockito.Mockito.mock(DroRecorderConfig.class);
        org.mockito.Mockito.when(client.isClientThread()).thenReturn(true);
        org.mockito.Mockito.when(client.getTickCount()).thenReturn(42);
        org.mockito.Mockito.when(config.recordCombatEvents()).thenReturn(true);
        org.mockito.Mockito.when(config.recordNpcEvents()).thenReturn(true);
        net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache cache = org.mockito.Mockito.mock(
            net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache.class);
        org.mockito.Mockito.when(cache.getStream()).thenAnswer(call -> java.util.stream.Stream.empty());
        net.runelite.api.NPC actor = org.mockito.Mockito.mock(net.runelite.api.NPC.class);
        try (org.mockito.MockedStatic<net.runelite.client.plugins.microbot.Microbot> microbot =
            org.mockito.Mockito.mockStatic(net.runelite.client.plugins.microbot.Microbot.class))
        {
            microbot.when(net.runelite.client.plugins.microbot.Microbot::getRs2NpcCache).thenReturn(cache);
            DroRecorderSession session = new DroRecorderSession(client, new Gson(), config, directory);
            session.start();
            for (int i = 0; i < 100; i++) { session.recordInteraction(actor, null); }
            session.recordNpcLifecycle("npc_spawned", actor);
            org.mockito.Mockito.when(client.getTickCount()).thenReturn(43);
            session.recordInteraction(actor, null);
            session.stop();
            session.getCompletion().get(5, TimeUnit.SECONDS);
        }
        List<JsonObject> records = Files.readAllLines(directory.resolve("events.jsonl")).stream()
            .map(line -> new JsonParser().parse(line).getAsJsonObject()).collect(Collectors.toList());
        assertEquals(2, records.stream().filter(r -> "npc_context".equals(r.get("type").getAsString())).count());
        assertEquals(101, records.stream().filter(r -> "interaction_changed".equals(r.get("type").getAsString())).count());
        long context = 0;
        long previous = 0;
        for (JsonObject record : records)
        {
            assertTrue(record.get("sequence").getAsLong() > previous);
            previous = record.get("sequence").getAsLong();
            if ("npc_context".equals(record.get("type").getAsString())) { context = previous; }
            if (record.has("npcContextSequence"))
            {
                assertEquals(context, record.get("npcContextSequence").getAsLong());
                assertFalse(record.has("allNpcs"));
            }
        }
    }

    @Test void gzipKeepsUnicodeAndOrderedRecordsAndClosesBothStreams() throws Exception
    {
        DroRecorderWriter writer = new DroRecorderWriter(directory, new Gson(), true);
        writer.start();
        writer.writeRaw(Map.of("type", "tick", "sequence", 1));
        writer.writeEvent(Map.of("type", "menu_click", "sequence", 2, "target", "Cordelia — 修理"));
        writer.writeEvent(Map.of("type", "session_stop", "sequence", 3));
        writer.stop();
        writer.getCompletion().get(5, TimeUnit.SECONDS);
        List<String> raw = gzipLines("session.jsonl.gz");
        List<String> events = gzipLines("events.jsonl.gz");
        assertEquals(3, raw.size());
        assertEquals(raw.subList(1, 3), events);
        assertTrue(raw.get(1).contains("修理"));
        assertFalse(Files.exists(directory.resolve("session.jsonl")));
    }

    @Test void recordingContinuesBeyondTheFormerCombinedUncompressedCap() throws Exception
    {
        DroRecorderWriter writer = new DroRecorderWriter(directory, new Gson(), true);
        writer.start();
        String payload = "x".repeat(128 * 1024);
        for (int i = 0; i < 1100; i++)
        {
            writer.writeEvent(Map.of("type", "interaction_changed", "sequence", i, "payload", payload));
        }
        writer.writeEvent(Map.of("type", "session_stop", "reason", "manual"));
        writer.stop();
        writer.getCompletion().get(30, TimeUnit.SECONDS);
        assertFalse(writer.isRunning());
        assertEquals(0, writer.getDroppedLines());
        assertTrue(expandedBytes("session.jsonl.gz") + expandedBytes("events.jsonl.gz") > 256L * 1024 * 1024);
        String last = null;int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new GZIPInputStream(Files.newInputStream(directory.resolve("events.jsonl.gz"))), StandardCharsets.UTF_8)))
        {
            for (String line; (line = reader.readLine()) != null;) { last = line;count++; }
        }
        assertEquals(1101, count);
        JsonObject terminal = new JsonParser().parse(last).getAsJsonObject();
        assertEquals("session_stop", terminal.get("type").getAsString());
        assertEquals("manual", terminal.get("reason").getAsString());
        assertEquals(0, terminal.get("droppedLines").getAsLong());
    }

    @Test void largeSceneIsKeptAndCompressedStreamsStillEndCleanly() throws Exception
    {
        DroRecorderWriter writer = new DroRecorderWriter(directory, new Gson(), true);
        writer.start();
        writer.writeEvent(Map.of("type", "scene_snapshot", "objects", "x".repeat(20000)));
        writer.stop();
        writer.getCompletion().get(5, TimeUnit.SECONDS);
        List<String> raw = gzipLines("session.jsonl.gz");
        assertEquals(1, raw.size());
        assertTrue(raw.get(0).contains("scene_snapshot"));
        assertEquals(20000, new JsonParser().parse(raw.get(0)).getAsJsonObject().get("objects").getAsString().length());
        assertEquals(raw, gzipLines("events.jsonl.gz"));
        assertTrue(Files.size(directory.resolve("session.jsonl.gz")) + Files.size(directory.resolve("events.jsonl.gz")) < 8192);
    }

    private long expandedBytes(String name) throws Exception
    {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(directory.resolve(name))))
        {
            byte[] buffer = new byte[65536];long bytes = 0;
            for (int count; (count = input.read(buffer)) != -1;) { bytes += count; }
            return bytes;
        }
    }

    private List<String> gzipLines(String name) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new GZIPInputStream(Files.newInputStream(directory.resolve(name))), StandardCharsets.UTF_8)))
        {
            return reader.lines().collect(Collectors.toList());
        }
    }
}
