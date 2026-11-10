package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

/** A menu header alone is not sufficient evidence that its destination key is ready. */
public final class FossilRowboatMenu {
    private final LongSupplier delay;
    private long settleMs;
    private long visibleSince = -1;

    FossilRowboatMenu() {
        this(() -> ThreadLocalRandom.current().nextLong(300L, 501L));
    }

    FossilRowboatMenu(long settleMs) { this(() -> settleMs); }
    FossilRowboatMenu(LongSupplier delay) { this.delay = delay; }
    static String destination(String label){return label.replaceFirst("^\\d+\\.\\s*", "").trim();}
    boolean ready(boolean matching, long now) {
        if (!matching) { visibleSince = -1; return false; }
        if (visibleSince < 0) {
            visibleSince = now;
            settleMs = delay.getAsLong();
        }
        return now - visibleSince >= settleMs;
    }

    /** Background travel only. Sample a fresh delay for every menu, after its option appears. */
    public static boolean awaitOption(String option, long timeoutMs) {
        if (Microbot.getClient().isClientThread()) return false;
        FossilRowboatMenu menu = new FossilRowboatMenu();
        long deadline = System.nanoTime() / 1_000_000 + Math.max(100L, timeoutMs);
        do {
            if (Thread.currentThread().isInterrupted()) return false;
            boolean matching = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Dialogue.hasDialogueOption(option, false)).orElse(false);
            if (menu.ready(matching, System.nanoTime() / 1_000_000)) return true;
            try { Thread.sleep(25L); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() / 1_000_000 < deadline);
        return false;
    }
}
