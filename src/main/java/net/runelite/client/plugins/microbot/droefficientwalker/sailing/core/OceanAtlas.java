package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/**
 * Observed sea tiles only. Single-writer worker ownership; snapshots share immutable chunks.
 * A new chunk is published on modification, so an in-flight A* never sees half an update.
 */
public final class OceanAtlas {
    private static final int MAGIC = 0x45534131; // ESA1
    private static final int MAX_CHUNKS = 24000;
    private final LinkedHashMap<Long, byte[]> chunks = new LinkedHashMap<>();
    private long revision;
    private long knownTiles;

    public long revision() { return revision; }
    public long knownTiles() { return knownTiles; }
    public int chunkCount() { return chunks.size(); }
    public void clear() { chunks.clear(); knownTiles = 0; revision++; }

    public void merge(SceneChart scene) {
        if (scene == null) return;
        Map<Long, byte[]> changed = new HashMap<>();
        for (int x = scene.baseX; x < scene.baseX + scene.width; x++) {
            for (int y = scene.baseY; y < scene.baseY + scene.height; y++) {
                byte value = scene.cell(x, y);
                if (value == SeaGrid.UNKNOWN) continue; // Never persist unloaded scene borders.
                long key = SeaPoint.key(x >> 3, y >> 3);
                int index = (x & 7) + ((y & 7) << 3);
                byte[] current = changed.get(key);
                if (current == null) current = chunks.get(key);
                byte old = current == null ? SeaGrid.UNKNOWN : current[index];
                if (old == value) continue;
                if (!changed.containsKey(key)) {
                    current = current == null ? new byte[64] : current.clone();
                    changed.put(key, current);
                }
                if (old == SeaGrid.UNKNOWN) knownTiles++;
                current[index] = value;
            }
        }
        if (!changed.isEmpty()) {
            for (Map.Entry<Long, byte[]> entry : changed.entrySet()) {
                chunks.remove(entry.getKey());
                chunks.put(entry.getKey(), entry.getValue());
            }
            while (chunks.size() > MAX_CHUNKS) {
                Iterator<Map.Entry<Long, byte[]>> it = chunks.entrySet().iterator();
                byte[] removed = it.next().getValue(); it.remove();
                for (byte b : removed) if (b != SeaGrid.UNKNOWN) knownTiles--;
            }
            revision++;
        }
    }

    public SeaGrid snapshot(SceneChart live, List<HazardZone> hazards) {
        return snapshot(live,hazards,null);
    }
    /** Optional tile-data seed is advisory outside the live scene and never grants live clearance. */
    public SeaGrid snapshot(SceneChart live, List<HazardZone> hazards, SeaGrid seed) {
        final Map<Long, byte[]> frozen = new HashMap<>(chunks);
        final List<HazardZone> zones = Collections.unmodifiableList(new ArrayList<>(hazards));
        return new SeaGrid() {
            @Override public byte cell(int x, int y) {
                if (x < 0 || y < 0 || x > 16383 || y > 16383) return BLOCKED;
                for (HazardZone zone : zones) if (zone.contains(x, y)) return BLOCKED;
                // An unknown LIVE tile overrides old cache data. It may be unloading.
                if (live != null && live.contains(x, y)) return live.cell(x, y);
                byte[] chunk = frozen.get(SeaPoint.key(x >> 3, y >> 3));
                byte value = chunk == null ? UNKNOWN : chunk[(x & 7) + ((y & 7) << 3)];
                return value != UNKNOWN || seed == null ? value : seed.cell(x,y);
            }
            @Override public boolean live(int x, int y) {
                return live != null && live.contains(x, y) && live.cell(x, y) != UNKNOWN;
            }
        };
    }

    /** Atomic replace; malformed/corrupt files are rejected, never interpreted as open water. */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC); out.writeInt(chunks.size());
            for (Map.Entry<Long, byte[]> e : chunks.entrySet()) {
                out.writeLong(e.getKey()); out.write(e.getValue());
            }
        }
        byte[] data = buffer.toByteArray();
        CRC32 crc = new CRC32(); crc.update(data);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.write(data); out.writeLong(crc.getValue());
        }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    public void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        long length = Files.size(file);
        if (length < 16 || length > MAX_CHUNKS * 72L + 16) throw new IOException("Invalid atlas size");
        byte[] bytes = Files.readAllBytes(file);
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 8);
        Map<Long, byte[]> loaded = new LinkedHashMap<>();
        long known = 0;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IOException("Unknown atlas format");
            int count = in.readInt();
            if (count < 0 || count > MAX_CHUNKS || bytes.length != 16 + count * 72) throw new IOException("Invalid atlas count");
            for (int i = 0; i < count; i++) {
                long key = in.readLong();
                int cx = (int) (key >> 32), cy = (int) key;
                if (cx < 0 || cy < 0 || cx > 2047 || cy > 2047 || loaded.containsKey(key)) throw new IOException("Invalid atlas key");
                byte[] cells = new byte[64]; in.readFully(cells);
                for (byte b : cells) {
                    if (b < SeaGrid.UNKNOWN || b > SeaGrid.BLOCKED) throw new IOException("Invalid atlas cell");
                    if (b != SeaGrid.UNKNOWN) known++;
                }
                loaded.put(key, cells);
            }
            if (in.readLong() != crc.getValue()) throw new IOException("Atlas checksum mismatch");
        }
        chunks.clear(); chunks.putAll(loaded); knownTiles = known; revision++;
    }
}
