package net.runelite.client.plugins.microbot.droslayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import java.util.concurrent.atomic.AtomicLong;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Optional task bracelets; missing/exhausted stock never creates a supply requirement. */
@Slf4j
public final class DroSlayerBracelets {
    public static final String SLAUGHTER = "Bracelet of slaughter";
    public static final String EXPEDITIOUS = "Expeditious bracelet";
    private static final AtomicLong nextEquipAt = new AtomicLong();

    private DroSlayerBracelets() {}

    public static String requestedBracelet(DroSlayerTaskProfileJson profile, boolean wilderness) {
        if (profile == null) return null;
        boolean slaughter = wilderness ? profile.isWildernessSlaughterBracelet() : profile.isSlaughterBracelet();
        boolean expeditious = wilderness ? profile.isWildernessExpeditiousBracelet() : profile.isExpeditiousBracelet();
        // A hand-edited conflicting JSON pair cannot safely choose either bracelet.
        return slaughter == expeditious ? null : slaughter ? SLAUGHTER : EXPEDITIOUS;
    }

    /** Call on the script worker after required food, protection and travel supplies are ready. */
    public static boolean ensureAtBank(DroSlayerTaskProfileJson profile, boolean wilderness) {
        return ensureAtBank(profile, wilderness, LIVE);
    }

    /** Equip the primary during gear loading; defer any spare until required supplies are loaded. */
    public static boolean equipAtBank(DroSlayerTaskProfileJson profile, boolean wilderness) {
        return prepareAtBank(profile, wilderness, LIVE, false);
    }

    /** Equip a carried replacement after degradation, without leaving combat to fetch more. */
    public static boolean equipCarried(DroSlayerTaskProfileJson profile, boolean wilderness) {
        return equipCarried(profile, wilderness, LIVE);
    }

    static boolean equipCarried(DroSlayerTaskProfileJson profile, boolean wilderness, Supplies supplies) {
        String name = requestedBracelet(profile, wilderness);
        boolean ready = name != null && (supplies.worn(name)
                || supplies.carried(name) > 0 && equipRememberingHands(name, supplies));
        if (ready) supplies.attempts().bankDisplacedHands(supplies);
        return ready;
    }

    private static boolean equipRememberingHands(String name, Supplies supplies) {
        int previousHands = supplies.handsItemId();
        if (!supplies.equip(name) || !supplies.worn(name)) return false;
        if (previousHands > 0) supplies.attempts().rememberDisplacedHands(previousHands);
        return true;
    }

    /** Ignore the saved glove slot only when the selected optional override is actually available. */
    public static boolean isHandsOverrideAvailable(DroSlayerTaskProfileJson profile, boolean wilderness) {
        String name = requestedBracelet(profile, wilderness);
        return name != null && (LIVE.worn(name) || LIVE.carried(name) > 0
                || LIVE.bankOpen() && LIVE.stock(name) > 0);
    }

    interface Supplies {
        boolean bankOpen();
        boolean worn(String name);
        int carried(String name);
        int stock(String name);
        int emptySlots();
        boolean withdrawOne(String name);
        boolean equip(String name);
        boolean depositOneFood();
        long bankSession();
        BankAttemptBudget attempts();
        default int handsItemId() { return -1; }
        default int carriedQuantity(int id) { return 0; }
        default boolean depositOne(int id) { return false; }
    }

    static boolean ensureAtBank(DroSlayerTaskProfileJson profile, boolean wilderness, Supplies supplies) {
        return prepareAtBank(profile, wilderness, supplies, true);
    }

    static boolean prepareAtBank(DroSlayerTaskProfileJson profile, boolean wilderness,
            Supplies supplies, boolean allowSpare) {
        String name = requestedBracelet(profile, wilderness);
        if (name == null || !supplies.bankOpen()) return false;
        BankAttemptBudget budget = supplies.attempts();
        if (!budget.start(supplies.bankSession(), name)) return supplies.worn(name);
        RoomAttempt room = new RoomAttempt();
        if (!supplies.worn(name)) {
            if (supplies.carried(name) == 0) {
                if (supplies.stock(name) <= 0) return false;
                if (!room.available(supplies) || !supplies.withdrawOne(name)) {
                    budget.failed();
                    return false;
                }
            }
            if (!equipRememberingHands(name, supplies)) return false;
        }
        budget.bankDisplacedHands(supplies);
        // One spare is enough. A full inventory may make room by banking exactly one food.
        if (allowSpare && supplies.carried(name) == 0 && supplies.stock(name) > 0) {
            if (!room.available(supplies) || !supplies.withdrawOne(name)) budget.failed();
        }
        return supplies.worn(name);
    }

    /** Failed optional banking is retried only after a newly observed bank opening. */
    static final class BankAttemptBudget {
        private long session = Long.MIN_VALUE;
        private String bracelet;
        private boolean failed;
        private int displacedHands = -1;
        private long handsDepositSession = Long.MIN_VALUE;
        void rememberDisplacedHands(int id) {
            displacedHands = id;
            handsDepositSession = Long.MIN_VALUE;
        }
        void bankDisplacedHands(Supplies supplies) {
            if (displacedHands <= 0 || !supplies.bankOpen()) return;
            int before = supplies.carriedQuantity(displacedHands);
            if (before <= 0) { displacedHands = -1; return; }
            if (handsDepositSession == supplies.bankSession()) return;
            handsDepositSession = supplies.bankSession();
            supplies.depositOne(displacedHands);
            if (supplies.carriedQuantity(displacedHands) < before) displacedHands = -1;
        }
        boolean start(long session, String bracelet) {
            if (this.session != session || !bracelet.equals(this.bracelet)) {
                this.session = session;
                this.bracelet = bracelet;
                failed = false;
            }
            return !failed;
        }
        void failed() { failed = true; }
    }

    private static final class RoomAttempt {
        private boolean attempted;
        boolean available(Supplies supplies) {
            if (supplies.emptySlots() > 0) return true;
            if (attempted) return false;
            attempted = true;
            return supplies.depositOneFood() && supplies.emptySlots() > 0;
        }
    }

    private static final Supplies LIVE = new Supplies() {
        private final BankAttemptBudget budget = new BankAttemptBudget();
        public long bankSession() { return Rs2Bank.getBankLastOpenedAt(); }
        public BankAttemptBudget attempts() { return budget; }
        public boolean bankOpen() { return Rs2Bank.isOpen(); }
        public boolean worn(String name) { return Rs2Equipment.isWearing(name, true); }
        public int carried(String name) {
            return (int) Rs2Inventory.items().filter(item -> usable(item, name)).count();
        }
        public int stock(String name) { return Rs2Bank.count(name, true); }
        public int emptySlots() { return Rs2Inventory.getEmptySlots(); }
        public int handsItemId() {
            Rs2ItemModel hands = Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.GLOVES);
            return hands == null ? -1 : hands.getId();
        }
        public int carriedQuantity(int id) { return Rs2Inventory.itemQuantity(id); }
        public boolean depositOne(int id) {
            int before = carriedQuantity(id);
            if (!bankOpen() || before <= 0) return false;
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Bank.depositOne(id));
            boolean observed = sleepUntil(() -> carriedQuantity(id) < before, 1500);
            if (observed) log.info("[DroSlayer] Banked displaced glove-slot item id={}", id);
            else log.warn("[DroSlayer] Displaced glove deposit not observed id={}; retrying only after next bank opening", id);
            return observed;
        }
        public boolean depositOneFood() {
            Rs2ItemModel food = Rs2Inventory.items().filter(item -> item != null
                    && item.getName() != null && !item.isNoted() && !item.isStackable()
                    && DroSlayerWildernessSupplies.isFood(item, true)).findFirst().orElse(null);
            if (food == null) return false;
            int before = Rs2Inventory.itemQuantity(food.getId());
            if (!DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Bank.depositOne(food.getId()))) return false;
            boolean observed = sleepUntil(() -> Rs2Inventory.itemQuantity(food.getId()) < before
                    && Rs2Inventory.getEmptySlots() > 0, 1500);
            if (observed) log.info("[DroSlayer] Banked one {} to make room for optional task bracelet", food.getName());
            else log.warn("[DroSlayer] Optional bracelet food deposit was not observed; holding further bank attempts until next bank opening");
            return observed;
        }
        public boolean withdrawOne(String name) {
            int before = carried(name);
            if (!DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Bank.setWithdrawAsItem() && Rs2Bank.withdrawOne(name, true))) return false;
            boolean observed = sleepUntil(() -> carried(name) > before, 1500);
            if (observed) log.info("[DroSlayer] Optional task bracelet withdrawn: {}", name);
            else log.warn("[DroSlayer] Optional bracelet withdrawal was not observed: {}; waiting for next bank opening", name);
            return observed;
        }
        public boolean equip(String name) {
            if (worn(name)) return true;
            Rs2ItemModel item = Rs2Inventory.items().filter(candidate -> usable(candidate, name)).findFirst().orElse(null);
            if (item == null) return false;
            long now = System.currentTimeMillis();
            long next = nextEquipAt.get();
            if (now < next || !nextEquipAt.compareAndSet(next, now + 2000L)) return false;
            boolean clicked;
            if (Rs2Bank.isOpen()) clicked = DroSlayerRuntime.performAction(
                    BaseProfileDro.ActionPhase.SETUP, false, () -> Rs2Bank.wearItem(name, true));
            else {
                String action = item.getAction("wear");
                if (action == null) action = item.getAction("wield");
                String selectedAction = action;
                clicked = selectedAction != null && DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.MAINTENANCE, false,
                        () -> Rs2Inventory.interact(item, selectedAction));
            }
            if (!clicked) return false;
            boolean observed = sleepUntil(() -> worn(name), 1200);
            if (observed) log.info("[DroSlayer] Optional task bracelet equipped: {}", name);
            return observed;
        }
    };

    private static boolean usable(Rs2ItemModel item, String name) {
        return item != null && item.getQuantity() > 0 && !item.isNoted() && name.equalsIgnoreCase(item.getName());
    }
}
