package net.runelite.client.plugins.microbot.droslayer;

import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Bank-owned, observed-state charging. A charged item id does not prove that usable ether remains. */
@Slf4j
public final class DroSlayerWildernessWeaponCharge
{
    public enum Result { READY, WORKING, BLOCKED }
    enum Family
    {
        VIGGORA("Viggora's chainmace", ItemID.WILD_CAVE_CHAINMACE_UNCHARGED, ItemID.WILD_CAVE_CHAINMACE_CHARGED),
        CRAW("Craw's bow", ItemID.WILD_CAVE_BOW_UNCHARGED, ItemID.WILD_CAVE_BOW_CHARGED),
        URSINE("Ursine chainmace", ItemID.WILD_CAVE_URSINE_UNCHARGED, ItemID.WILD_CAVE_URSINE_CHARGED),
        WEBWEAVER("Webweaver bow", ItemID.WILD_CAVE_WEBWEAVER_UNCHARGED, ItemID.WILD_CAVE_WEBWEAVER_CHARGED);
        final String name;
        final int uncharged, charged;
        Family(String name, int uncharged, int charged) { this.name = name; this.uncharged = uncharged; this.charged = charged; }
        boolean matches(int id) { return id == charged || id == uncharged; }
    }
    interface Environment
    {
        boolean available();
        boolean paused();
        boolean bankOpen();
        boolean sameBank(WorldPoint origin);
        default boolean inCombat() { return false; }
        WorldPoint location();
        int equippedWeapon();
        int inventoryWeapon(Family family);
        default int inventoryFamilyCount(Family family) { return inventoryWeapon(family) == 0 ? 0 : 1; }
        int bankWeapon(Family family);
        int inventoryEther();
        int bankEther();
        int emptySlots();
        boolean depositFood();
        boolean openBank();
        boolean closeBank();
        boolean unequip(int id);
        boolean withdrawWeapon(int id);
        boolean check(int id);
        boolean withdrawEther(int amount);
        boolean depositEther(int amount);
        boolean combine(int id);
        boolean amountPrompt();
        boolean submitAmount(int amount);
        boolean chargeConfirmation();
        boolean confirmCharge();
        boolean wear(int id);
    }
    enum Stage { IDLE, ACQUIRE, CHECK, ETHER, COMBINE, WAIT_CHARGE, VERIFY, RESTORE, DONE, BLOCKED }
    public static final int ACTIVATION_ETHER = 1000;
    public static final int DEFAULT_USABLE_CHARGES = 2000;
    public static final int MINIMUM_TRIP_CHARGES = 200;
    private static final int LOW_CHARGES = 100;
    private static final int MAX_TARGET_CHARGES = 5000;
    private static final long CLICK_INTERVAL_MS = 1500;
    private static final long CHECK_WAIT_MS = 5000;
    private static final long STEP_TIMEOUT_MS = 20000;
    private static final Pattern CHARGES = Pattern.compile("(?:has|have|contains|containing|with)\\s+(?:a total of\\s+)?([0-9][0-9,]*)\\s+(?:(?:revenant\\s+)?ether\\s+)?charges?\\b");
    private static final Pattern STORED_ETHER = Pattern.compile("(?:has|contains|containing|with)\\s+([0-9][0-9,]*)\\s+(?:units? of\\s+)?(?:revenant\\s+)?ether\\b");
    private final Environment environment;
    private final LongSupplier clock;
    private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
    private Stage stage = Stage.IDLE;
    private Family family;
    private WorldPoint bankOrigin;
    private boolean originallyWorn;
    private int target, etherToAdd, etherBefore, checkAttempts, actionAttempts, observedVersion, checkVersion;
    private volatile int usableCharges = -1;
    private long nextAction, stepDeadline, checkDeadline;
    private String failureReason = "", lastAction = "";

    public DroSlayerWildernessWeaponCharge() { this(new NativeEnvironment(), () -> System.nanoTime() / 1_000_000L); }
    DroSlayerWildernessWeaponCharge(Environment environment, LongSupplier clock) { this.environment = environment; this.clock = clock; }

    public static boolean isRevenantWeapon(int id) { return family(id) != null; }
    public static int chargedWeaponId(int id) { Family f = family(id); return f == null ? id : f.charged; }
    public static boolean isRevenantWeapon(String name)
    {
        if (name == null) return false;
        String value = normalize(name).replace(" (u)", "");
        for (Family f : Family.values()) if (normalize(f.name).equals(value)) return true;
        return false;
    }
    private static Family family(int id) { for (Family f : Family.values()) if (f.matches(id)) return f; return null; }

    /** Client-thread safe: no actions, locks, or blocking client-thread queries. */
    public void onChatMessage(String message)
    {
        if (message == null) return;
        String value = normalize(message);
        if ((value.contains("ether") || value.contains("charge")) && messages.size() < 128) messages.offer(value);
    }

    /** Passive field check. All actual Check/recharge input is confined to ensureAtBank. */
    public boolean needsRecharge()
    {
        Family worn = family(environment.equippedWeapon());
        if (stage == Stage.DONE && !environment.bankOpen()) stage = Stage.IDLE;
        if (worn == null) return false;
        if (family != worn && !isBusy()) { family = worn; usableCharges = -1; }
        drainMessages();
        return environment.equippedWeapon() == worn.uncharged || usableCharges < 0 || usableCharges <= LOW_CHARGES;
    }
    public Result ensureAtBank(DroSlayerConfig config, int taskRemaining) { return ensureAtBank(config, taskRemaining, 0); }
    /** desiredWeaponId=0 means the actual worn weapon; otherwise only the selected setup family is considered. */
    public Result ensureAtBank(DroSlayerConfig config, int taskRemaining, int desiredWeaponId)
    {
        int configured = config == null ? DEFAULT_USABLE_CHARGES : config.wildernessWeaponCharges();
        return ensureAtBank(taskRemaining, desiredWeaponId, configured);
    }
    Result ensureAtBank(int taskRemaining, int desiredWeaponId, int configured)
    {
        long now = clock.getAsLong();
        if (!environment.available() || environment.paused()) { stepDeadline = now + STEP_TIMEOUT_MS; return Result.WORKING; }
        Family wanted = desiredWeaponId == 0 ? family(environment.equippedWeapon()) : family(desiredWeaponId);
        if (!isBusy() && stage != Stage.BLOCKED && wanted == null) return Result.READY;
        if (!isBusy() && stage != Stage.BLOCKED && (stage != Stage.DONE || family != wanted))
        {
            if (!environment.bankOpen()) return Result.WORKING; // Never initiate a charge/check in the field.
            family = wanted;
            bankOrigin = environment.location();
            originallyWorn = family.matches(environment.equippedWeapon());
            target = targetCharges(taskRemaining, configured);
            usableCharges = -1;
            checkAttempts = 0;
            messages.clear();
            enter(Stage.ACQUIRE, now);
            log.info("Wilderness weapon: preparing {} targetUsable={} remainingTask={}", family.name, target, taskRemaining);
        }
        if (stage == Stage.BLOCKED) return Result.BLOCKED;
        if (stage == Stage.DONE) return Result.READY;
        if (!environment.sameBank(bankOrigin)) return hold("Weapon charging interrupted away from its bank");
        if (environment.inCombat()) { stepDeadline = now + STEP_TIMEOUT_MS; return Result.WORKING; }
        drainMessages();
        if (now > stepDeadline) return hold("Timed out at weapon charging step " + stage);
        int carried = environment.inventoryWeapon(family);
        if (environment.inventoryFamilyCount(family) > 1 || (stage == Stage.ACQUIRE && originallyWorn
                && family.matches(environment.equippedWeapon()) && carried != 0))
            return hold("Multiple copies of " + family.name + " prevent identification of the selected weapon; bank the spare copy");
        switch (stage)
        {
            case ACQUIRE:
                if (carried != 0)
                {
                    if (carried == family.uncharged) { usableCharges = 0; enter(Stage.ETHER, now); }
                    else beginCheck(Stage.CHECK, now);
                    return Result.WORKING;
                }
                if (family.matches(environment.equippedWeapon()))
                {
                    if (environment.emptySlots() == 0) return bankAction(now, () -> environment.depositFood(), "making room to remove the weapon");
                    if (environment.bankOpen()) return action(now, environment::closeBank, "closing bank to remove weapon");
                    return action(now, () -> environment.unequip(environment.equippedWeapon()), "removing weapon");
                }
                if (!environment.bankOpen()) return action(now, environment::openBank, "opening bank for selected weapon");
                int stored = environment.bankWeapon(family);
                if (stored == 0) return hold("Selected " + family.name + " was not found in equipment, inventory, or bank");
                if (environment.emptySlots() == 0) return action(now, environment::depositFood, "making room for selected weapon");
                return action(now, () -> environment.withdrawWeapon(stored), "withdrawing selected weapon");
            case CHECK:
            case VERIFY:
                if (environment.bankOpen()) return action(now, environment::closeBank, "closing bank to check charges");
                if (checkAttempts > 0 && observedVersion > checkVersion && usableCharges >= 0)
                {
                    if (stage == Stage.VERIFY && (carried != family.charged || usableCharges < target))
                        return hold("Recharge did not produce the verified usable charge target: " + usableCharges + "/" + target);
                    if (usableCharges >= target) enter(Stage.RESTORE, now); else enter(Stage.ETHER, now);
                    return Result.WORKING;
                }
                if (now < checkDeadline) return Result.WORKING;
                if (checkAttempts >= 3) return hold("No readable Check charge response for " + family.name + "; inspect charge chat text");
                if (now < nextAction) return Result.WORKING;
                checkVersion = observedVersion;
                checkAttempts++;
                checkDeadline = now + CHECK_WAIT_MS;
                return action(now, () -> environment.check(carried), "checking usable weapon charges");
            case ETHER:
                if (!environment.bankOpen()) return action(now, environment::openBank, "opening bank for ether");
                int activation = carried == family.uncharged ? ACTIVATION_ETHER : 0;
                int available = environment.inventoryEther() + environment.bankEther();
                int desired = activation + Math.max(0, target - usableCharges);
                if (available < desired)
                {
                    int possible = Math.max(0, usableCharges) + Math.max(0, available - activation);
                    if (available < activation || possible < MINIMUM_TRIP_CHARGES) return hold("Insufficient revenant ether for activation and a usable trip reserve");
                    target = Math.min(target, possible);
                    desired = activation + Math.max(0, target - usableCharges);
                    log.info("Wilderness weapon: limited ether stock; using targetUsable={} for {}", target, family.name);
                }
                etherToAdd = desired;
                if (etherToAdd == 0) { enter(Stage.RESTORE, now); return Result.WORKING; }
                if (environment.inventoryEther() > etherToAdd)
                {
                    int excess = environment.inventoryEther() - etherToAdd;
                    return action(now, () -> environment.depositEther(excess), "banking excess ether");
                }
                if (environment.inventoryEther() < etherToAdd)
                {
                    if (environment.inventoryEther() == 0 && environment.emptySlots() == 0)
                        return action(now, environment::depositFood, "making room for ether");
                    int amount = etherToAdd - environment.inventoryEther();
                    return action(now, () -> environment.withdrawEther(amount), "withdrawing bounded ether amount=" + amount);
                }
                etherBefore = environment.inventoryEther();
                enter(Stage.COMBINE, now);
                return Result.WORKING;
            case COMBINE:
                if (environment.bankOpen()) return action(now, environment::closeBank, "closing bank to combine ether");
                if (now < nextAction) return Result.WORKING;
                Result result = action(now, () -> environment.combine(carried), "adding ether to " + family.name);
                if (result != Result.BLOCKED) enter(Stage.WAIT_CHARGE, now);
                return result;
            case WAIT_CHARGE:
                if (carried == family.charged && environment.inventoryEther() < etherBefore)
                {
                    beginCheck(Stage.VERIFY, now);
                    return Result.WORKING;
                }
                if (environment.amountPrompt()) return action(now, () -> environment.submitAmount(etherToAdd), "submitting observed ether amount prompt");
                if (environment.chargeConfirmation()) return action(now, environment::confirmCharge, "confirming observed weapon activation prompt");
                return Result.WORKING;
            case RESTORE:
                if (originallyWorn && environment.equippedWeapon() != family.charged)
                {
                    if (environment.bankOpen()) return action(now, environment::closeBank, "closing bank to restore weapon");
                    return action(now, () -> environment.wear(family.charged), "restoring charged weapon");
                }
                if (!environment.bankOpen()) return action(now, environment::openBank, "reopening bank after charge verification");
                if (environment.inventoryEther() > 0)
                    return action(now, () -> environment.depositEther(environment.inventoryEther()), "banking unused ether");
                enter(Stage.DONE, now);
                log.info("Wilderness weapon: verified {} usableCharges={} originallyWorn={}", family.name, usableCharges, originallyWorn);
                return Result.READY;
            default: return Result.WORKING;
        }
    }

    public boolean isBusy() { return stage != Stage.IDLE && stage != Stage.DONE && stage != Stage.BLOCKED; }
    public boolean isBlocked() { return stage == Stage.BLOCKED; }
    public String getFailureReason() { return failureReason; }
    public int getObservedUsableCharges() { drainMessages(); return usableCharges; }
    /** Call once on entry to BANKING, never on every banking tick. Busy input ownership is preserved. */
    public void beginBankVisit()
    {
        if (isBusy()) return;
        stage = Stage.IDLE;
        failureReason = "";
        nextAction = 0;
    }
    public void reset()
    {
        stage = Stage.IDLE; family = null; bankOrigin = null; usableCharges = -1;
        nextAction = stepDeadline = checkDeadline = 0;
        checkAttempts = actionAttempts = observedVersion = checkVersion = 0;
        failureReason = ""; messages.clear();
    }
    static int targetCharges(int remaining, int configured)
    {
        return (int) Math.min(MAX_TARGET_CHARGES, Math.max(MINIMUM_TRIP_CHARGES,
                Math.max(configured, Math.min(MAX_TARGET_CHARGES, Math.max(0L, (long) remaining) + 100L))));
    }
    private void beginCheck(Stage checking, long now)
    {
        enter(checking, now); checkVersion = observedVersion; checkAttempts = 0; checkDeadline = 0;
    }
    private void enter(Stage next, long now) { stage = next; actionAttempts = 0; lastAction = ""; stepDeadline = now + STEP_TIMEOUT_MS; }
    private Result bankAction(long now, BooleanSupplier operation, String description)
    {
        return action(now, environment.bankOpen() ? operation : environment::openBank, description);
    }
    private Result action(long now, BooleanSupplier operation, String description)
    {
        if (now < nextAction) return Result.WORKING;
        if (!description.equals(lastAction)) { lastAction = description; actionAttempts = 0; }
        if (actionAttempts >= 3) return hold("Weapon charging made no observed progress while " + description);
        actionAttempts++;
        nextAction = now + CLICK_INTERVAL_MS;
        boolean dispatched = operation.getAsBoolean();
        log.info("Wilderness weapon action={} attempt={} dispatched={}; awaiting observed state", description, actionAttempts, dispatched);
        return Result.WORKING;
    }
    private Result hold(String reason)
    {
        failureReason = reason; stage = Stage.BLOCKED;
        log.warn("Wilderness weapon: {}; task held at bank", reason);
        return Result.BLOCKED;
    }
    private void drainMessages()
    {
        String message;
        while ((message = messages.poll()) != null)
        {
            if (family == null) continue;
            Family named = null;
            for (Family candidate : Family.values()) if (message.contains(normalize(candidate.name))) named = candidate;
            if (named != null && named != family) continue;
            boolean own = named == family || message.contains(family == Family.VIGGORA || family == Family.URSINE ? "chainmace" : "bow");
            boolean checking = (stage == Stage.CHECK || stage == Stage.VERIFY) && checkAttempts > 0;
            boolean wornWeaponMessage = message.contains("weapon") && family.matches(environment.equippedWeapon());
            if (!own && !(message.contains("weapon") && checking) && !wornWeaponMessage) continue;
            if (message.contains("run out of charge") || message.contains("ran out of charge")
                    || message.contains("no charges") || message.contains("out of charges") || message.contains("out of ether")
                    || ((own || wornWeaponMessage) && (message.contains("need to recharge") || message.contains("needs recharging")
                    || message.contains("must recharge"))))
            {
                usableCharges = 0; observedVersion++; continue;
            }
            if (message.contains("need") || message.contains("require") || message.contains("to activate") || message.contains("you add")) continue;
            Matcher charges = CHARGES.matcher(message);
            Matcher ether = STORED_ETHER.matcher(message);
            try
            {
                if (charges.find()) usableCharges = Integer.parseInt(charges.group(1).replace(",", ""));
                else if (ether.find()) usableCharges = Math.max(0, Integer.parseInt(ether.group(1).replace(",", "")) - ACTIVATION_ETHER);
                else { if (checking) log.debug("Wilderness weapon: unmatched Check chat={}", message); continue; }
                if (usableCharges > 16000) { usableCharges = -1; continue; }
                observedVersion++;
                log.info("Wilderness weapon: observed {} usableCharges={}", family.name, usableCharges);
            }
            catch (NumberFormatException ignored) { /* Unrecognized amounts must not unblock departure. */ }
        }
    }
    private static String normalize(String value) { return value == null ? "" : value.replaceAll("<[^>]*>", "").replace('\u2019', '\'').trim().toLowerCase(Locale.ROOT); }

    private static final class NativeEnvironment implements Environment
    {
        public boolean available() { return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN).orElse(false); }
        public boolean paused() { return Microbot.pauseAllScripts.get(); }
        public boolean bankOpen() { return Rs2Bank.isOpen(); }
        public WorldPoint location() { return Rs2Player.getWorldLocation(); }
        public boolean sameBank(WorldPoint origin)
        {
            WorldPoint player = location();
            return origin != null && player != null && player.distanceTo(origin) <= 6;
        }
        public boolean inCombat() { return Rs2Player.isInCombat(); }
        public int equippedWeapon() { Rs2ItemModel item = Rs2Equipment.get(EquipmentInventorySlot.WEAPON); return item == null ? 0 : item.getId(); }
        public int inventoryWeapon(Family family) { Rs2ItemModel item = Rs2Inventory.get(i -> family.matches(i.getId()) && !i.isNoted()); return item == null ? 0 : item.getId(); }
        public int inventoryFamilyCount(Family family) { return Rs2Inventory.count(i -> family.matches(i.getId()) && !i.isNoted()); }
        public int bankWeapon(Family family)
        {
            Rs2ItemModel charged = Rs2Bank.get(i -> i.getId() == family.charged && i.getQuantity() > 0);
            if (charged != null) return charged.getId();
            Rs2ItemModel uncharged = Rs2Bank.get(i -> i.getId() == family.uncharged && i.getQuantity() > 0);
            return uncharged == null ? 0 : uncharged.getId();
        }
        public int inventoryEther() { return Rs2Inventory.itemQuantity(ItemID.WILD_CAVE_SHARD); }
        public int bankEther() { return Rs2Bank.count(ItemID.WILD_CAVE_SHARD); }
        public int emptySlots() { return Rs2Inventory.getEmptySlots(); }
        private boolean input(BooleanSupplier action) { return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, action); }
        public boolean depositFood()
        {
            Rs2ItemModel food = Rs2Inventory.get(i -> DroSlayerWildernessSupplies.isFood(i, true));
            return food != null && input(() -> Rs2Bank.depositOne(food.getId()));
        }
        public boolean openBank() { return input(Rs2Bank::openBank); }
        public boolean closeBank() { return input(Rs2Bank::closeBank); }
        public boolean unequip(int id) { return input(() -> Rs2Equipment.unEquip(id)); }
        public boolean withdrawWeapon(int id) { return input(() -> { Rs2Bank.setWithdrawAsItem(); return Rs2Bank.withdrawOne(id); }); }
        public boolean check(int id) { return id != 0 && input(() -> Rs2Inventory.interact(id, "Check")); }
        public boolean withdrawEther(int amount) { return input(() -> Rs2Bank.withdrawX(ItemID.WILD_CAVE_SHARD, amount)); }
        public boolean depositEther(int amount) { return input(() -> Rs2Bank.depositX(ItemID.WILD_CAVE_SHARD, amount)); }
        public boolean combine(int id) { return id != 0 && input(() -> Rs2Inventory.combine(ItemID.WILD_CAVE_SHARD, id)); }
        public boolean amountPrompt()
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                String title = Rs2Widget.getChildWidgetText(162, 42);
                if (title == null || !Rs2Widget.isWidgetVisible(162, 44)) return false;
                String prompt = normalize(title);
                return prompt.contains("ether") || prompt.equals("enter amount:") || prompt.equals("enter amount");
            }).orElse(false);
        }
        public boolean submitAmount(int amount) { return input(() -> { Rs2Keyboard.typeString(Integer.toString(amount)); Rs2Keyboard.keyPress(KeyEvent.VK_ENTER); return true; }); }
        public boolean chargeConfirmation()
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                String question = normalize(Rs2Dialogue.getQuestion());
                return question.contains("ether") && (question.contains("charge") || question.contains("activate")) && Rs2Dialogue.hasDialogueOption("Yes");
            }).orElse(false);
        }
        public boolean confirmCharge() { return input(() -> Rs2Dialogue.clickOption("Yes", false)); }
        public boolean wear(int id) { return input(() -> Rs2Inventory.interact(id, "Wield")); }
    }
}
