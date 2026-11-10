package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Local, bounded diagnostics. No chat-history capture, account identifiers or network uploads. */
final class VoyageLog implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VoyageLog.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    final Path directory = RuneLite.RUNELITE_DIR.toPath().resolve("efficient-sailing");
    private Path logFile;
    private BufferedWriter writer;
    private long written;
    synchronized void open() {
        close();
        try {
            Files.createDirectories(directory);
            logFile = directory.resolve("voyage-" + STAMP.format(Instant.now()) + ".jsonl");
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            written = 0;
        } catch (IOException ex) { LOG.warn("[EfficientSailing] Cannot create local journal", ex); }
    }
    synchronized void note(String message) {
        LOG.info("[EfficientSailing] {}", message);
        if (writer == null || written > 4_000_000) return;
        String line = "{\"time\":\"" + Instant.now() + "\",\"message\":\"" + escape(message) + "\"}\n";
        try { writer.write(line); writer.flush(); written += line.length(); }
        catch (IOException ex) { LOG.warn("[EfficientSailing] Journal write failed", ex); close(); }
    }
    synchronized Path dump(SailingSnapshot snapshot, SeaPoint goal, String state, List<SeaPoint> route,
                           List<HazardZone> hazards, String ui) throws IOException {
        return dump(snapshot,goal,state,route,hazards,ui,route,"Legacy local route");
    }
    synchronized Path dump(SailingSnapshot snapshot,SeaPoint goal,String state,List<SeaPoint> route,
                           List<HazardZone> hazards,String ui,List<SeaPoint> localRoute,String navigation) throws IOException {
        Files.createDirectories(directory);
        if (writer != null) writer.flush();
        Path zip = directory.resolve("diagnostics-" + STAMP.format(Instant.now()) + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "state.txt", "EfficientSailing " + EfficientSailingController.VERSION + "\nState: " + state + "\nDestination: " + goal + "\n"
                    + (snapshot == null ? "No live snapshot" : snapshot.diagnostic())
                    + "\n\nRelevant boat UI (not chat history):\n" + ui);
            StringBuilder points = new StringBuilder("x,y\n");
            for (SeaPoint point : route) points.append(point.x).append(',').append(point.y).append('\n');
            entry(out, "route.csv", points.toString());
            entry(out,"provisional-voyage.csv",points.toString());
            StringBuilder local=new StringBuilder("x,y\n");
            for(SeaPoint point:localRoute)local.append(point.x).append(',').append(point.y).append('\n');
            entry(out,"local-route.csv",local.toString());entry(out,"navigation.txt",navigation);
            StringBuilder zones = new StringBuilder(); for (HazardZone h : hazards) zones.append(h).append('\n');
            entry(out, "hazards.txt", zones.toString());
            if (snapshot != null && snapshot.chart != null) {
                SceneChart c = snapshot.chart;
                StringBuilder chart = new StringBuilder("x,y,cell\n");
                for (int x = c.baseX; x < c.baseX + c.width; x++) for (int y = c.baseY; y < c.baseY + c.height; y++) {
                    chart.append(x).append(',').append(y).append(',').append(c.cell(x, y)).append('\n');
                }
                entry(out, "live-sea-chart.csv", chart.toString());
            }
            if (logFile != null && Files.isRegularFile(logFile)) {
                out.putNextEntry(new ZipEntry("voyage.jsonl")); Files.copy(logFile, out); out.closeEntry();
            }
        }
        return zip;
    }
    private static void entry(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(text.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
    }
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
    @Override public synchronized void close() {
        if (writer != null) try { writer.close(); } catch (IOException ignored) { }
        writer = null;
    }
}
