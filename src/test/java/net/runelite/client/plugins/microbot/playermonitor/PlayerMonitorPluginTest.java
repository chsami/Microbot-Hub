package net.runelite.client.plugins.microbot.playermonitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMonitorPluginTest {

    @TempDir
    Path tempDir;

    @Test
    void writeClickCountCreatesMissingDirectory() throws IOException {
        File file = tempDir.resolve("PlayerMonitor").resolve("click_count.log").toFile();

        PlayerMonitorPlugin.writeClickCount(file, 42);

        assertTrue(file.isFile());
        assertEquals("42 ", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void writeClickCountOverwritesExistingFile() throws IOException {
        File file = tempDir.resolve("click_count.log").toFile();
        Files.write(file.toPath(), "999 ".getBytes(StandardCharsets.UTF_8));

        PlayerMonitorPlugin.writeClickCount(file, 7);

        assertEquals("7 ", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }
}
