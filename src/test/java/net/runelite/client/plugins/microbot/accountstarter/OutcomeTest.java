package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.runelite.client.plugins.microbot.accountstarter.run.Outcome;
import org.junit.jupiter.api.Test;

class OutcomeTest {
    @Test
    void factoriesSetKindAndReason() {
        assertEquals(Outcome.Kind.CONTINUE, Outcome.cont().getKind());
        assertEquals(Outcome.Kind.NATURAL_POINT, Outcome.natural("a").getKind());
        assertEquals("a", Outcome.natural("a").getReason());
        assertEquals(Outcome.Kind.DONE, Outcome.done("b").getKind());
        assertEquals("b", Outcome.done("b").getReason());
        assertEquals(Outcome.Kind.PARKED, Outcome.parked("c").getKind());
        assertEquals("c", Outcome.parked("c").getReason());
        assertEquals(Outcome.Kind.FAILED, Outcome.failed("d").getKind());
        assertEquals("d", Outcome.failed("d").getReason());
    }
}
