package net.runelite.client.plugins.microbot.droslayer;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/** Each combat script owns its repair, input profile and normal bank route. */
public final class DroCombatTentacleRepair {
    private final Function<BooleanSupplier, Boolean> input;
    private final Consumer<String> blocked;
    private DroSlayerTentacleRepair repair;
    private boolean expected;
    private boolean recovering;
    private boolean failed;
    private long nextAction;

    public DroCombatTentacleRepair(Function<BooleanSupplier, Boolean> input, Consumer<String> blocked) {
        this.input = input;
        this.blocked = blocked;
    }

    public void reset() {
        repair = null;
        expected = recovering = failed = false;
        nextAction = 0;
    }

    public boolean needsRepair() {
        expected |= Rs2Equipment.isWearing(ItemID.ABYSSAL_TENTACLE);
        if (expected && !Rs2Equipment.isWearing(ItemID.ABYSSAL_TENTACLE)
                && !Rs2Inventory.hasItem(ItemID.ABYSSAL_TENTACLE)
                && (Rs2Equipment.isWearing(ItemID.KRAKEN_TENTACLE)
                    || Rs2Inventory.hasItem(ItemID.KRAKEN_TENTACLE))) {
            recovering = true;
        }
        return recovering;
    }

    public boolean stepAtBank() {
        if (!needsRepair()) return true;
        if (failed) return false;
        if (Rs2Equipment.isWearing(ItemID.ABYSSAL_TENTACLE)) {
            reset();
            expected = true;
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < nextAction) return false;
        if (Rs2Inventory.hasItem(ItemID.ABYSSAL_TENTACLE)) {
            nextAction = now + 1800;
            input.apply(Rs2Bank.isOpen() ? Rs2Bank::closeBank
                    : () -> Rs2Inventory.equip(ItemID.ABYSSAL_TENTACLE));
            return false;
        }
        if (repair == null) {
            // Let the host reach its existing bank.
            if (!Rs2Bank.isOpen()) return true;
            if (Rs2Bank.count(ItemID.ABYSSAL_TENTACLE) > 0) {
                nextAction = now + 1800;
                input.apply(() -> Rs2Bank.setWithdrawAsItem()
                        && Rs2Bank.withdrawOne(ItemID.ABYSSAL_TENTACLE));
                return false;
            }
            repair = new DroSlayerTentacleRepair(input);
            repair.begin();
        }
        DroSlayerTentacleRepair.Result result = repair.step();
        if (result == DroSlayerTentacleRepair.Result.BLOCKED) {
            failed = true;
            blocked.accept(repair.failureReason());
        }
        // Observe and equip the finished weapon on the following pass.
        return false;
    }
}
