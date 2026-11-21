package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class WalkerRequestOwnershipTest {
    private static final WorldPoint TARGET = new WorldPoint(3200, 3200, 0);

    @Test void legacySameTargetReusesRequestAndNestsPreparations() {
        var owner = new WalkerRequestOwnership();
        var first = owner.begin(TARGET, WalkerRequestOwnership.LEGACY_OWNER, false);
        assertSame(first, owner.begin(TARGET, WalkerRequestOwnership.LEGACY_OWNER, false));
        assertEquals(2, first.preparations);
        owner.finished(first); assertTrue(owner.preparing());
        owner.finished(first); assertFalse(owner.preparing());
        owner.finished(first); assertEquals(0, first.preparations);
        assertTrue(owner.prepare(first)); assertEquals(1, first.preparations);
    }

    @Test void explicitSameTargetStartAlwaysReplacesAndInvalidatesBoundWork() {
        var owner = new WalkerRequestOwnership();
        var first = owner.begin(TARGET, "activity", true);
        try (var scope = owner.bind(first)) {
            assertDoesNotThrow(owner::check);
            var next = owner.begin(TARGET, "activity", true);
            assertNotEquals(first.id, next.id);
            assertFalse(owner.isCurrent(first)); assertTrue(owner.isCurrent(next.id));
            assertEquals("REPLACED", owner.detachedStatus(first.id));
            assertSame(first, owner.capture()); assertEquals(first.id, owner.captureEpoch());
            assertThrows(CancellationException.class, owner::check);
        }
        assertSame(owner.current(), owner.capture()); assertFalse(owner.hasContext());
    }

    @Test void legacyCallerCannotReuseAnOwnedRequestEvenAtSameTarget() {
        var owner = new WalkerRequestOwnership();
        var owned = owner.begin(TARGET, "activity", true);
        var legacy = owner.begin(TARGET, WalkerRequestOwnership.LEGACY_OWNER, false);
        assertNotSame(owned, legacy); assertEquals(WalkerRequestOwnership.LEGACY_OWNER, legacy.owner);
        assertNotSame(legacy, owner.begin(TARGET.dx(1), WalkerRequestOwnership.LEGACY_OWNER, false));
    }

    @Test void cancelledRequestCannotPrepareOrAffectReplacementPreparationCount() {
        var owner = new WalkerRequestOwnership();
        var first = owner.begin(TARGET, "first", true);
        long cancelledEpoch = owner.cancel();
        assertTrue(first.cancelled); assertFalse(owner.isCurrent(first.id));
        assertFalse(owner.prepare(first)); assertNull(owner.current());
        assertEquals("CANCELLED", owner.detachedStatus(first.id));
        var replacement = owner.begin(TARGET, "second", true);
        assertTrue(replacement.id > cancelledEpoch);
        owner.finished(first);
        assertEquals(1, replacement.preparations); assertTrue(owner.preparing());
        assertEquals("CANCELLED", owner.detachedStatus(first.id));
    }

    @Test void nestedScopeRestoresOuterContextIncludingItsStaleEpoch() {
        var owner = new WalkerRequestOwnership();
        var first = owner.begin(TARGET, "first", true);
        try (var outer = owner.bind(first)) {
            var second = owner.begin(TARGET, "second", true);
            try (var inner = owner.bind(second)) {
                assertSame(second, owner.context()); assertDoesNotThrow(owner::check);
            }
            assertSame(first, owner.context()); assertFalse(owner.isContextCurrent());
        }
        assertNull(owner.context()); assertTrue(owner.isContextCurrent());
    }

    @Test void nullRequestScopeStillRejectsWorkAfterEpochChanges() {
        var owner = new WalkerRequestOwnership();
        long before = owner.epoch();
        try (var scope = owner.bind(null, before)) {
            assertTrue(owner.hasContext()); assertDoesNotThrow(owner::check);
            assertTrue(owner.cancel() > before);
            assertNull(owner.capture()); assertEquals(before, owner.captureEpoch());
            assertThrows(CancellationException.class, owner::check);
        }
        assertFalse(owner.hasContext()); assertDoesNotThrow(owner::check);
    }
}
