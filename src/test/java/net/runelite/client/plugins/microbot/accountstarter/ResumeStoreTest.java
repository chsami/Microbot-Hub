package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.*;

import net.runelite.client.plugins.microbot.accountstarter.logic.ResumeState;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeStoreTest {
    @TempDir Path dir;

    @Test
    void resumeRoundTrips() {
        ResumeStore store = new ResumeStore(dir);
        ResumeState s = new ResumeState();
        s.setCurrentTask("task-1");
        s.setStarterKitClaimed(true);
        s.setLastJonReply("hello");
        store.save("abc", s);
        assertEquals(s, new ResumeStore(dir).load("abc"));
    }

    @Test
    void corruptFileGivesEmptyState() throws Exception {
        ResumeStore store = new ResumeStore(dir);
        store.save("abc", new ResumeState());
        Files.writeString(dir.resolve("resume-abc.json"), "{not json");
        assertEquals(new ResumeState(), store.load("abc"));
        assertEquals(new ResumeState(), store.load("missing"));
    }
}
