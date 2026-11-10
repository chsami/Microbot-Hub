package net.runelite.client.plugins.microbot.droslayer.walker;

import java.io.*;
import java.util.zip.*;

/** Walker-only recorded passages; each planner retains its unrelated collision data. */
public final class MotherlodeCollisionMap
{
    private MotherlodeCollisionMap() { }
    private static byte[] cached, preservedCached;

    /** Both Walker planners share the same passages; the stock Microbot map is untouched. */
    public static InputStream open(InputStream source) { return open(source, false); }
    static InputStream openPreserved(InputStream source) { return open(source, true); }

    private static synchronized InputStream open(InputStream source, boolean preserved) {
        if (source == null) return null;
        try (InputStream input = source) {
            // Preserve each planner's existing archive outside the recorded passages.
            byte[] data = preserved ? preservedCached : cached;
            if (data == null) {
                data = patch(input);
                if (preserved) preservedCached = data; else cached = data;
            }
            return new ByteArrayInputStream(data);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Motherlode Mine entrance passages", ex);
        }
    }

    static byte[] patch(InputStream source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(source); ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] data = input.readAllBytes();
                if ("58_88".equals(entry.getName())) {
                    // (3731,5683): N/S/W only. Its eastern neighbour is another blocked rock/floor.
                    open(data, 3731, 5683, 0); open(data, 3731, 5682, 0); open(data, 3730, 5683, 1);
                    // (3733,5680): all four cardinal neighbours are walkable in the capture.
                    open(data, 3733, 5680, 0); open(data, 3733, 5679, 0);
                    open(data, 3733, 5680, 1); open(data, 3732, 5680, 1);
                }
                PrifddinasCollisionMap.patch(entry.getName(), data);
                ZipEntry copy = new ZipEntry(entry.getName()); copy.setTime(0);
                output.putNextEntry(copy); output.write(data); output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void open(byte[] flags, int x, int y, int direction) {
        int index = ((y % 64) * 64 + x % 64) * 2 + direction;
        if (index / 8 >= flags.length) throw new IllegalArgumentException("Missing MLM entrance collision data");
        flags[index / 8] |= 1 << (index % 8);
    }
}
