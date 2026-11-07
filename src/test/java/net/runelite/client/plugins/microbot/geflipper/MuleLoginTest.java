package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class MuleLoginTest {
    @Test
    void aReceiverLoginOwnsEachStepAndDoesNotReadCredentialsBeforeTheirDispatch() {
        FakePort port = new FakePort();
        MuleLogin.login(port, 301);
        assertEquals(List.of(MuleLogin.Step.WORLD, MuleLogin.Step.OPEN, MuleLogin.Step.CREDENTIALS,
            MuleLogin.Step.SUBMIT, MuleLogin.Step.SUBMIT), port.actions);
        assertEquals(1, port.credentialWrites);
    }

    @Test
    void blockedOrUnreservedLoginCannotEvenReadTheProfileOrDispatchWorldChanges() {
        FakePort blocked = new FakePort();
        blocked.allowed = false;
        MuleLogin.login(blocked, 301);
        assertEquals(0, blocked.profileReads);
        assertTrue(blocked.actions.isEmpty());
        FakePort unreserved = new FakePort();
        unreserved.lease = null;
        MuleLogin.login(unreserved, 301);
        assertTrue(unreserved.actions.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"1,human", "2,human", "3,human", "1,profile", "2,profile", "3,profile",
        "1,lease", "2,lease", "3,lease", "1,interrupted", "2,interrupted", "3,interrupted"})
    void takeoverProfileReplacementLeaseLossOrInterruptionCancelsAllRemainingSteps(int wait, String change) {
        FakePort port = new FakePort();
        port.afterPause = () -> {
            if (port.pauses != wait) return;
            if ("profile".equals(change)) port.profile = "other-profile";
            else if ("lease".equals(change)) port.lease = new MuleNetwork.Lease("NextFlipper", 1000, 1000L);
            else port.allowed = false;
        };
        MuleLogin.login(port, 301);
        assertEquals(wait, port.actions.size(), "No step after the cancelled wait may run");
        assertFalse(port.actions.contains(MuleLogin.Step.SUBMIT));
    }

    @Test
    void queuedDispatchMustRecheckOwnershipAndCannotWriteCredentialsAfterCancellation() {
        FakePort port = new FakePort();
        port.beforeDispatch = () -> {
            if (port.actions.size() == 2) port.allowed = false;
        };
        MuleLogin.login(port, 301);
        assertEquals(0, port.credentialWrites);
        assertEquals(List.of(MuleLogin.Step.WORLD, MuleLogin.Step.OPEN), port.actions);
    }

    @Test
    void nativeLauncherContinueDoesNotReadOrWriteCredentials() {
        FakePort port = new FakePort();
        port.index = 10;
        MuleLogin.login(port, 301);
        assertEquals(List.of(MuleLogin.Step.WORLD, MuleLogin.Step.CONTINUE), port.actions);
        assertEquals(0, port.credentialWrites);
    }

    private static final class FakePort implements MuleLogin.Port {
        boolean allowed = true;
        String profile = "synthetic-profile";
        MuleNetwork.Lease lease = new MuleNetwork.Lease("Flipper", 1000, 1000L);
        int index, pauses, profileReads, credentialWrites;
        Runnable afterPause = () -> { }, beforeDispatch = () -> { };
        final List<MuleLogin.Step> actions = new ArrayList<>();
        public boolean allowed() { return allowed; }
        public Object profileIdentity() { profileReads++; return profile; }
        public MuleNetwork.Lease lease() { return lease; }
        public int loginIndex() { return index; }
        public boolean perform(MuleLogin.Step step, int expectedIndex, int world, BooleanSupplier owned) {
            beforeDispatch.run();
            if (!owned.getAsBoolean() || index != expectedIndex) return false;
            actions.add(step);
            if (step == MuleLogin.Step.OPEN || step == MuleLogin.Step.DISMISS) index = 2;
            if (step == MuleLogin.Step.CREDENTIALS) credentialWrites++;
            return true;
        }
        public boolean pause(int millis, BooleanSupplier owned) {
            pauses++;
            afterPause.run();
            return owned.getAsBoolean();
        }
    }
}
