package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountPersonalityTest {
    @TempDir Path dir;

    @Test
    void sameAccountSameSeed() {
        AccountPersonality a = AccountPersonality.load(dir, 123456789L);
        AccountPersonality b = AccountPersonality.load(dir, 123456789L);
        assertEquals(a.seedFor("walk"), b.seedFor("walk"));
        assertEquals(a.label(), b.label());
        assertNotEquals(a.seedFor("walk"), a.seedFor("bank"));
    }

    @Test
    void differentAccountsDiffer() {
        AccountPersonality a = AccountPersonality.load(dir, 111L);
        AccountPersonality b = AccountPersonality.load(dir, 222L);
        assertNotEquals(a.seedFor("walk"), b.seedFor("walk"));
    }

    @Test
    void twoAccountsLoadingAtOnceBothSurvive() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(2);
        Future<AccountPersonality> fa = ex.submit(() -> { gate.await(); return AccountPersonality.load(dir, 1L); });
        Future<AccountPersonality> fb = ex.submit(() -> { gate.await(); return AccountPersonality.load(dir, 2L); });
        gate.countDown();
        AccountPersonality pa = fa.get();
        AccountPersonality pb = fb.get();
        ex.shutdown();
        assertTrue(pa.persistent() && pb.persistent());
        assertEquals(pa.seedFor("x"), AccountPersonality.load(dir, 1L).seedFor("x"));
        assertEquals(pb.seedFor("x"), AccountPersonality.load(dir, 2L).seedFor("x"));
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(1, s.filter(p -> p.getFileName().toString().startsWith("salt")).count());
        }
    }

    @Test
    void corruptAccountFileIsSetAsideNotReRolledForOthers() throws Exception {
        long bBefore = AccountPersonality.load(dir, 2L).seedFor("x");
        AccountPersonality first = AccountPersonality.load(dir, 1L);
        Files.writeString(dir.resolve("personality").resolve(first.key() + ".json"), "{garbage");
        assertFalse(AccountPersonality.load(dir, 1L).persistent());
        try (Stream<Path> s = Files.list(dir.resolve("personality"))) {
            assertEquals(1, s.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
        assertEquals(bBefore, AccountPersonality.load(dir, 2L).seedFor("x"));
    }

    @Test
    void blankSaltIsNotAccepted() throws Exception {
        Files.writeString(dir.resolve("salt"), "  \n");
        assertFalse(AccountPersonality.load(dir, 1L).persistent());
        assertEquals("", Files.readString(dir.resolve("salt")).trim());
    }

    @Test
    void keyHidesTheHash() {
        long hash = 987654321012L;
        String key = AccountPersonality.accountKey("salt", hash);
        assertFalse(key.contains(Long.toString(hash)));
        assertEquals(64, key.length());
        assertEquals(4, AccountPersonality.load(dir, hash).label().length());
    }
}
