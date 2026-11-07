package net.runelite.client.plugins.microbot.geflipper;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A receiver owns each login step separately; no delayed core login sequence outlives that ownership. */
final class MuleLogin {
    enum Step { DISMISS, WORLD, OPEN, CREDENTIALS, SUBMIT, CONTINUE }

    interface Port {
        boolean allowed();
        Object profileIdentity();
        MuleNetwork.Lease lease();
        int loginIndex();
        boolean perform(Step step, int expectedIndex, int world, BooleanSupplier owned);
        boolean pause(int millis, BooleanSupplier owned);
    }

    private MuleLogin() { }

    static void login(Port port, int world) {
        if (world <= 0 || !port.allowed()) return;
        Object profile = port.profileIdentity();
        MuleNetwork.Lease lease = port.lease();
        if (profile == null || lease == null) return;
        BooleanSupplier owned = () -> port.allowed() && Objects.equals(profile, port.profileIdentity())
            && lease.sameReservation(port.lease());
        if (!owned.getAsBoolean()) return;
        int index = port.loginIndex();
        if (index == 3 || index == 24 || index == 34 || index == 4 || index == 14) {
            if (!perform(port, Step.DISMISS, index, world, owned) || !port.pause(600, owned)) return;
        }
        if (!perform(port, Step.WORLD, port.loginIndex(), world, owned) || !port.pause(600, owned)) return;
        index = port.loginIndex();
        if (index == 0) {
            if (!perform(port, Step.OPEN, index, world, owned) || !port.pause(600, owned)) return;
            index = port.loginIndex();
        }
        if (index == 2) {
            if (!perform(port, Step.CREDENTIALS, index, world, owned) || !port.pause(300, owned)
                || !perform(port, Step.SUBMIT, index, world, owned) || !port.pause(300, owned)) return;
            // Preserve the SDK's second Enter only while the same credentials screen is still visible.
            if (port.loginIndex() == 2 && !perform(port, Step.SUBMIT, 2, world, owned)) return;
        }
        index = port.loginIndex();
        if (index == 9 || index == 10) perform(port, Step.CONTINUE, index, world, owned);
    }

    private static boolean perform(Port port, Step step, int index, int world, BooleanSupplier owned) {
        return owned.getAsBoolean() && port.perform(step, index, world, owned);
    }
}
