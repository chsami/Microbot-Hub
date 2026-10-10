package net.runelite.client.plugins.microbot.bankseller;

import org.junit.jupiter.api.Test;
/** Pure login readiness regressions; never starts or controls a game client. */
public final class BankSellerLoginReadinessTest {
    @Test
    void waitsForTwoRealGameTicks() {
        BankSellerLoginReadiness guard = new BankSellerLoginReadiness();
        check(!guard.matchesProfile("account-a"), "Unobserved accounts must not match");
        check(!guard.observe(true, true, "account-a", 100), "First valid observation binds but is not ready");
        check(guard.matchesProfile("account-a"), "First valid observation immediately binds the run");
        for (int retry = 0; retry < 10; retry++) {
            check(!guard.observe(true, true, "account-a", 100), "Polling the same tick is not game progress");
        }
        check(!guard.observe(true, true, "account-a", 101), "One elapsed game tick is insufficient");
        check(guard.observe(true, true, "account-a", 102), "Two elapsed game ticks establish readiness");
        check(guard.observe(true, true, "account-a", 102), "A ready same-account observation stays ready");
        check(guard.observe(true, true, "account-a", 110), "Later monotonic ticks remain ready");
        check(!guard.accountChanged(), "Normal observations never claim an account switch");
    }

    @Test
    void invalidObservationsNeverBindOrBecomeReady() {
        BankSellerLoginReadiness guard = new BankSellerLoginReadiness();
        check(!guard.observe(false, true, "account-a", 100), "Logged-out observations are not ready");
        check(!guard.matchesProfile("account-a"), "Logged-out data cannot bind an account");
        check(!guard.observe(true, false, "account-a", 100), "Missing local player is not ready");
        check(!guard.matchesProfile("account-a"), "Missing player cannot bind an account");
        check(!guard.observe(true, true, null, 100), "Null profile is not ready");
        check(!guard.observe(true, true, "", 100), "Empty profile is not ready");
        check(!guard.observe(true, true, " \t ", 100), "Whitespace profile is not ready");
        check(!guard.observe(true, true, "account-a", -1), "Unknown tick cannot establish readiness");
        check(!guard.matchesProfile("account-a"), "Invalid tick cannot bind an account");
        check(!guard.accountChanged(), "Invalid data does not manufacture an account switch");
        check(!guard.observe(true, true, "account-b", 0), "First genuinely valid account becomes the binding");
        check(guard.matchesProfile("account-b"), "A different first valid account is permitted");
        check(guard.observe(true, true, "account-b", 2), "Tick zero is valid and can establish readiness");
        check(!guard.matchesProfile(null), "Null never matches an established account");
        check(!guard.matchesProfile(""), "Empty never matches an established account");
    }

    @Test
    void logoutAndLoadingResetReadinessButKeepBinding() {
        BankSellerLoginReadiness guard = readyGuard();
        check(!guard.observe(false, false, "account-b", 500), "Logout invalidates readiness");
        check(!guard.accountChanged(), "An invalid logged-out profile is not a valid account switch");
        check(guard.matchesProfile("account-a"), "Logout does not release the original account");
        check(!guard.observe(true, true, "account-a", 500), "Re-login starts a fresh tick boundary");
        check(!guard.observe(true, true, "account-a", 501), "Re-login still needs two ticks");
        check(guard.observe(true, true, "account-a", 502), "Same-account re-login can become ready");
        check(!guard.observe(false, true, "account-a", 503), "Loading or hopping invalidates readiness");
        check(!guard.observe(true, true, "account-a", 600), "Post-loading first observation is not ready");
        check(guard.observe(true, true, "account-a", 602), "Post-loading same-account ticks become ready");
        check(!guard.observe(true, false, "account-a", 603), "A temporarily missing player invalidates readiness");
        check(!guard.observe(true, true, "account-a", 604), "A returned player needs a fresh boundary");
        check(!guard.observe(true, true, null, 605), "A temporarily missing profile invalidates readiness");
        check(!guard.observe(true, true, "account-a", 606), "A returned profile needs a fresh boundary");
        check(guard.observe(true, true, "account-a", 608), "Readiness recovers only after fresh valid ticks");
    }

    @Test
    void changedAccountLatchesEvenWhenSwitchingBack() {
        BankSellerLoginReadiness guard = readyGuard();
        check(!guard.observe(true, true, "account-b", 102), "A valid different account is never ready");
        check(guard.accountChanged(), "Account switch is permanently latched");
        check(!guard.matchesProfile("account-a"), "Original account no longer matches after a switch");
        check(!guard.matchesProfile("account-b"), "New account does not inherit the run");
        check(!guard.observe(true, true, "account-a", 104), "Switching back must not revive the run");
        check(!guard.observe(true, true, "account-a", 106), "More elapsed ticks cannot clear the switch");
        guard.resetTiming();
        check(guard.accountChanged(), "Timing reset does not clear the account-switch latch");
        check(!guard.observe(true, true, "account-a", 200), "Reset timing cannot revive an invalidated run");
        check(!guard.observe(false, false, null, -1), "Invalid observations stay unready after a switch");
        check(guard.accountChanged(), "Invalid observations cannot clear a prior account switch");
    }

    @Test
    void backwardsTickRequiresFreshReadiness() {
        BankSellerLoginReadiness guard = readyGuard();
        check(!guard.observe(true, true, "account-a", 50), "A backwards tick invalidates old readiness");
        check(guard.matchesProfile("account-a"), "Clock reset keeps the original account binding");
        check(!guard.observe(true, true, "account-a", 50), "Repeated reset tick remains unready");
        check(!guard.observe(true, true, "account-a", 51), "One tick after reset is insufficient");
        check(guard.observe(true, true, "account-a", 52), "Two fresh ticks recover readiness");
        check(!guard.observe(true, true, "account-a", -1), "Negative clock values reset readiness");
        check(!guard.observe(true, true, "account-a", 100), "First valid clock after unknown timing waits");
        check(guard.observe(true, true, "account-a", 102), "Fresh valid clock can recover readiness");
    }

    @Test
    void timingResetDoesNotReleaseTheAccount() {
        BankSellerLoginReadiness guard = readyGuard();
        guard.resetTiming();
        check(guard.matchesProfile("account-a"), "Explicit reset preserves the bound account");
        check(!guard.observe(true, true, "account-a", 200), "Explicit reset clears old readiness");
        check(guard.observe(true, true, "account-a", 202), "Same account can regain readiness");
        guard.resetTiming();
        check(!guard.observe(true, true, "account-b", 204), "Reset cannot permit another account");
        check(guard.accountChanged(), "A different account after reset still invalidates the run");
    }

    @Test
    void newRunHasAnIndependentBinding() {
        BankSellerLoginReadiness oldRun = readyGuard();
        oldRun.observe(true, true, "account-b", 104);
        BankSellerLoginReadiness newRun = new BankSellerLoginReadiness();
        check(!newRun.accountChanged(), "A new run has no inherited switch latch");
        check(!newRun.matchesProfile("account-a"), "A new run has no inherited account binding");
        check(!newRun.observe(true, true, "account-b", 104), "New run independently binds its valid account");
        check(newRun.matchesProfile("account-b"), "New run belongs to its own first account");
        check(newRun.observe(true, true, "account-b", 106), "New run can become ready normally");
        check(oldRun.accountChanged(), "A new instance does not alter the previous run");
    }

    @Test
    void largeTickValuesDoNotOverflow() {
        BankSellerLoginReadiness guard = new BankSellerLoginReadiness();
        check(!guard.observe(true, true, "account-a", 0), "Large-clock test begins unready");
        check(guard.observe(true, true, "account-a", Integer.MAX_VALUE), "Elapsed tick calculation cannot overflow");
        check(!guard.observe(true, true, "account-a", Integer.MIN_VALUE), "Wrapped negative tick is invalid");
        check(!guard.observe(true, true, "account-a", 0), "Clock restart begins a new wait");
        check(guard.observe(true, true, "account-a", 2), "Clock restart requires two new ticks");
    }

    private static BankSellerLoginReadiness readyGuard() {
        BankSellerLoginReadiness guard = new BankSellerLoginReadiness();
        guard.observe(true, true, "account-a", 100);
        if (!guard.observe(true, true, "account-a", 102)) {
            throw new AssertionError("Test setup failed to establish readiness");
        }
        return guard;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
