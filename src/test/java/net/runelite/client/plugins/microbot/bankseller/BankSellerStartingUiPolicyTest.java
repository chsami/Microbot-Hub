package net.runelite.client.plugins.microbot.bankseller;

import org.junit.jupiter.api.Test;
import net.runelite.client.config.ConfigItem;
import java.util.Arrays;

import static net.runelite.client.plugins.microbot.bankseller.BankSellerStartingUiPolicy.Action;

/** Pure startup UI regressions; never starts or controls a game client. */
public final class BankSellerStartingUiPolicyTest {
    @Test
    void coversEveryInputCombination() {
        // Bits: inventory missing, equipment missing, GE open, bank open.
        Action[] expected = {
                Action.NONE, Action.OPEN_INVENTORY, Action.OPEN_EQUIPMENT, Action.OPEN_INVENTORY,
                Action.NONE, Action.CLOSE_EXCHANGE, Action.CLOSE_EXCHANGE, Action.CLOSE_EXCHANGE,
                Action.NONE, Action.CLOSE_BANK, Action.CLOSE_BANK, Action.CLOSE_BANK,
                Action.NONE, Action.CLOSE_EXCHANGE, Action.CLOSE_EXCHANGE, Action.CLOSE_EXCHANGE
        };
        for (int mask = 0; mask < expected.length; mask++) {
            expect(expected[mask], (mask & 1) != 0, (mask & 2) != 0,
                    (mask & 4) != 0, (mask & 8) != 0,
                    "Startup input combination " + mask);
        }
    }

    @Test
    void waitsForModalClosureBeforeRevealingSlots() {
        // Dispatching a close does not prove it succeeded. Every fresh live
        // observation must keep choosing close until that modal disappears.
        for (int retry = 0; retry < 4; retry++) {
            expect(Action.CLOSE_EXCHANGE, true, true, true, true,
                    "An open GE blocks both bank closure and tab switches");
        }
        for (int retry = 0; retry < 4; retry++) {
            expect(Action.CLOSE_BANK, true, true, false, true,
                    "A remaining bank blocks tab switches after GE closure");
        }
        expect(Action.OPEN_INVENTORY, true, true, false, false,
                "After both modals close, inventory is revealed first");
        expect(Action.OPEN_EQUIPMENT, false, true, false, false,
                "A verified inventory stays captured while equipment is revealed");
        expect(Action.NONE, false, false, false, false,
                "Both verified snapshots need no further startup UI action");
    }

    @Test
    void revealsOnlyTheMissingContainer() {
        expect(Action.CLOSE_EXCHANGE, false, true, true, false,
                "Missing equipment still needs the GE closed");
        expect(Action.OPEN_EQUIPMENT, false, true, false, false,
                "Known inventory does not get needlessly reopened");
        expect(Action.CLOSE_BANK, true, false, false, true,
                "Missing inventory still needs the bank closed");
        expect(Action.OPEN_INVENTORY, true, false, false, false,
                "Known equipment does not get needlessly reopened");
    }

    @Test
    void safetyControlsAreNotConfigurable() {
        String key = "protectStartingItems";
        check(Arrays.stream(BankSellerConfig.class.getMethods()).noneMatch(method ->
                        method.getName().equals(key) || (method.isAnnotationPresent(ConfigItem.class)
                                && method.getAnnotation(ConfigItem.class).keyName().equals(key))),
                "Removed safety toggle must not remain in settings: " + key);
        check(new BankSellerConfig() { }.instructions().contains("always protected"),
                "Instructions must describe mandatory protection");
    }

    private static void expect(Action expected, boolean inventoryMissing, boolean equipmentMissing,
                               boolean exchangeOpen, boolean bankOpen, String message) {
        Action actual = BankSellerStartingUiPolicy.nextAction(inventoryMissing, equipmentMissing,
                exchangeOpen, bankOpen);
        check(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
