package net.runelite.client.plugins.microbot.droslayer;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** One bank action per step; only an observed finished weapon completes the repair. */
public final class DroSlayerTentacleRepair {
    public enum Result { WORKING, READY, BLOCKED }
    interface Environment {
        default void begin() { }
        boolean available();
        boolean bankOpen();
        boolean equipped(int id);
        boolean carried(int id);
        int bankCount(int id);
        int freeSlots();
        boolean openBank();
        boolean closeBank();
        boolean makeSpace();
        boolean unequip();
        boolean withdraw(int id);
        boolean combine();
        boolean warning();
        boolean confirmation();
        boolean continueWarning();
        boolean confirm();
    }

    private final Environment env;
    private final LongSupplier clock;
    private boolean active;
    private boolean combining;
    private long deadline;
    private long nextAction;
    private String failure;

    DroSlayerTentacleRepair() { this(new NativeEnvironment(action -> DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, action)), System::currentTimeMillis); }
    /** Other owned combat activities retain their own input profile and banking route. */
    public DroSlayerTentacleRepair(Function<BooleanSupplier, Boolean> input) {
        this(new NativeEnvironment(input), System::currentTimeMillis);
    }
    DroSlayerTentacleRepair(Environment env, LongSupplier clock) { this.env = env; this.clock = clock; }
    public void begin() { reset(); env.begin(); active = true; deadline = clock.getAsLong() + 90_000L; }
    public void reset() { active = false; combining = false; nextAction = 0L; failure = null; }
    public boolean isActive() { return active; }
    public String failureReason() { return failure; }

    public Result step() {
        if (!env.available()) return Result.WORKING;
        if (env.carried(ItemID.ABYSSAL_TENTACLE) || env.equipped(ItemID.ABYSSAL_TENTACLE)) {
            active = false;
            return Result.READY;
        }
        long now = clock.getAsLong();
        if (now >= deadline) return blocked("Tentacle rebuild timed out before the finished weapon appeared");
        if (now < nextAction) return Result.WORKING;
        nextAction = now + 1_200L;
        if (combining) {
            if (env.warning()) env.continueWarning();
            else if (env.confirmation()) env.confirm();
            return Result.WORKING;
        }
        if (env.equipped(ItemID.KRAKEN_TENTACLE)) {
            if (env.freeSlots() == 0) return makeSpace();
            if (env.bankOpen()) env.closeBank();
            else env.unequip();
            return Result.WORKING;
        }
        for (int id : new int[] {ItemID.KRAKEN_TENTACLE, ItemID.ABYSSAL_WHIP}) {
            if (env.carried(id)) continue;
            if (!env.bankOpen()) env.openBank();
            else if (env.bankCount(id) == 0) return blocked(id == ItemID.ABYSSAL_WHIP
                    ? "No abyssal whip in bank to rebuild the tentacle" : "No Kraken tentacle available to rebuild");
            else if (env.freeSlots() == 0) return makeSpace();
            else env.withdraw(id);
            return Result.WORKING;
        }
        if (env.bankOpen()) env.closeBank();
        else if (env.combine()) combining = true;
        return Result.WORKING;
    }

    private Result makeSpace() {
        if (!env.bankOpen()) env.openBank();
        else if (!env.makeSpace()) return blocked("No inventory room to rebuild the tentacle");
        return Result.WORKING;
    }
    private Result blocked(String reason) { failure = reason; active = false; return Result.BLOCKED; }

    private static final class NativeEnvironment implements Environment {
        private final Function<BooleanSupplier, Boolean> input;
        private WorldPoint bankLocation;
        NativeEnvironment(Function<BooleanSupplier, Boolean> input) { this.input = input; }
        public void begin() { bankLocation = null; }
        public boolean available() {
            if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts.get() || Rs2Player.isInCombat()) return false;
            WorldPoint here = Rs2Player.getWorldLocation();
            if (bankLocation == null && Rs2Bank.isOpen()) bankLocation = here;
            return bankLocation != null && here != null && bankLocation.distanceTo(here) <= 6;
        }
        public boolean bankOpen() { return Rs2Bank.isOpen(); }
        public boolean equipped(int id) { return Rs2Equipment.isWearing(id); }
        public boolean carried(int id) { return Rs2Inventory.get(i -> i.getId() == id && !i.isNoted()) != null; }
        public int bankCount(int id) { return Rs2Bank.count(id); }
        public int freeSlots() { return Rs2Inventory.emptySlotCount(); }
        private boolean input(BooleanSupplier action) { return input.apply(action); }
        public boolean openBank() { return input(Rs2Bank::openBank); }
        public boolean closeBank() { return input(Rs2Bank::closeBank); }
        public boolean makeSpace() {
            Rs2ItemModel item = Rs2Inventory.items().filter(i -> i.getId() != ItemID.KRAKEN_TENTACLE
                    && i.getId() != ItemID.ABYSSAL_WHIP && i.getId() != ItemID.ABYSSAL_TENTACLE).findFirst().orElse(null);
            return item != null && input(() -> Rs2Bank.depositOne(item.getId()));
        }
        public boolean unequip() { return input(() -> Rs2Equipment.unEquip(ItemID.KRAKEN_TENTACLE)); }
        public boolean withdraw(int id) { return input(() -> { Rs2Bank.setWithdrawAsItem(); return Rs2Bank.withdrawOne(id); }); }
        public boolean combine() { return input(() -> Rs2Inventory.combine(ItemID.KRAKEN_TENTACLE, ItemID.ABYSSAL_WHIP)); }
        public boolean warning() {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                if (!Rs2Dialogue.hasContinue()) return false;
                for (int child = 1; child <= 4; child++) {
                    String text = Rs2Widget.getChildWidgetText(229, child);
                    if (text != null && text.toLowerCase(java.util.Locale.ROOT)
                            .contains("tentacle will gradually consume your whip")) return true;
                }
                return false;
            }).orElse(false);
        }
        public boolean confirmation() {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Rs2Dialogue.hasDialogueOption("Yes, let the tentacle consume the whip.")).orElse(false);
        }
        public boolean continueWarning() { return input(() -> { Rs2Dialogue.clickContinue(); return true; }); }
        public boolean confirm() { return input(() -> Rs2Dialogue.clickOption("Yes, let the tentacle consume the whip.", true)); }
    }
}
