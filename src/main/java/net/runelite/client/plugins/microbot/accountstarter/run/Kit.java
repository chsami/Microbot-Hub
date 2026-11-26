package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.FreeSource;
import net.runelite.client.plugins.microbot.accountstarter.logic.HarlanClaim;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.LootRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.LoopRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.NeedResolver;
import net.runelite.client.plugins.microbot.accountstarter.logic.Resolution;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public final class Kit {
    private static final int MAX_FAILURES = 3;
    private static final int MAX_BANK_TRIPS = 4;
    private static final int MAX_DIALOGUE_STEPS = 15;
    /** walkToBankAndUseBank can click the booth while still walking: the bank may take a few seconds more to open. */
    static final int BANK_OPEN_MS = 8000;
    private static final Set<Integer> WHOLE_STACK = Set.of(ItemID.FISHING_BAIT, ItemID.FEATHER);
    private static final int MAX_STEPS = 40;
    private static final int MAX_SHOP_CLICKS = 30;
    private static final Set<Integer> NEVER_DEPOSIT = Set.of(ApTask.LAMP_ID, ApTask.QUEST_LAMP_ID, ApTask.VOUCHER_ID, ApTask.STARTER_KIT_ID);

    private final ApTask task;
    private final Choices choices;
    private final Supplier<AccountSnapshot> snapshots;
    private final ApPace pace;
    private final Random random;
    private final Function<AccountSnapshot, List<Integer>> carryAlso;

    private int failures;
    private int bankTrips;
    private int steps;

    public Kit(ApTask task, Choices choices, Supplier<AccountSnapshot> snapshots, ApPace pace, Random random) {
        this(task, choices, snapshots, pace, random, s -> List.of());
    }

    public Kit(ApTask task, Choices choices, Supplier<AccountSnapshot> snapshots, ApPace pace, Random random,
               Function<AccountSnapshot, List<Integer>> carryAlso) {
        this.carryAlso = carryAlso;
        this.task = task;
        this.choices = choices;
        this.snapshots = snapshots;
        this.pace = pace;
        this.random = random;
    }

    public Outcome prepare(List<Need> needs, Set<Integer> keep) {
        if (++steps > MAX_STEPS) return Outcome.parked("the kit did not finish in " + MAX_STEPS + " steps");
        AccountSnapshot s = snapshots.get();
        List<Need> fromBank = new ArrayList<>();
        for (Need n : needs) {
            if (carried(n, s) >= n.getQuantity()) continue;
            Resolution r = NeedResolver.resolve(n, s, false, 0, id -> 0, 1);
            switch (r.getKind()) {
                case PARK:
                    return Outcome.parked(r.getReason());
                case CLAIM_REWARD:
                    return Outcome.natural(r.getReason());
                case FREE:
                    if (n.getFree() == FreeSource.CATCH_AND_COOK) return Outcome.replan(r.getReason());
                    if (n.getFree() == FreeSource.HARLAN) return harlan(n);
                    if (n.getFree() == FreeSource.LOWE || n.getFree() == FreeSource.AUBURY) {
                        return shop(n, s, (int) needs.stream().filter(x -> x.getFree() == n.getFree()).count());
                    }
                    return tutor(n, s);
                default:
                    int owned = n.getAnyOf().stream().mapToInt(s::owned).sum();
                    if (!s.isBankKnown() || carried(n, s) < Math.min(n.getQuantity(), owned)) fromBank.add(n);
            }
        }
        List<Integer> picks = LoopRules.gearPicks(task, s, choices);
        List<Integer> extras = extras(s);
        Set<Integer> wanted = new HashSet<>(keep);
        wanted.addAll(extras);
        wanted.addAll(NEVER_DEPOSIT);
        wanted.addAll(picks);
        if (burying()) wanted.addAll(LootRules.BONES);
        needs.forEach(n -> wanted.addAll(n.getAnyOf()));
        boolean deposit = s.getInventory().keySet().stream().anyMatch(id -> !wanted.contains(id)) || !s.getNotedInventory().isEmpty();
        boolean pickInBank = picks.stream().anyMatch(id -> !s.getInventory().containsKey(id) && !s.getEquipment().containsKey(id));
        boolean extraInBank = extrasMissing(extras, s.getInventory()).keySet().stream().anyMatch(s.getBank()::containsKey);
        if (deposit || pickInBank || extraInBank || !fromBank.isEmpty()) {
            return bank(s, wanted, fromBank, picks, extras);
        }
        // metal lowers ranged and magic accuracy
        boolean tookOff = false;
        for (int id : GearPlan.takeOff(task.skill())) {
            if (!s.getEquipment().containsKey(id)) continue;
            Global.sleep((int) pace.reactionMs(random));
            if (!Rs2Equipment.unEquip(id) || !Global.sleepUntil(() -> !Rs2Equipment.isWearing(id), 2000)) {
                return fail("could not take off item " + id);
            }
            tookOff = true;
        }
        if (tookOff) return Outcome.cont();
        boolean wore = false;
        for (int id : picks) {
            // a spare of what is already worn is not a reason to wear it again, or the kit never ends
            if (!s.getInventory().containsKey(id) || s.getEquipment().containsKey(id)) continue;
            Outcome o = equip(id);
            if (o.getKind() != Outcome.Kind.CONTINUE) return o;
            wore = true;
        }
        return wore ? Outcome.cont() : Outcome.done("kit ready for " + task.name());
    }

    private List<Integer> extras(AccountSnapshot s) {
        return new ArrayList<>(carryAlso.apply(s));
    }

    public static Map<Integer, Integer> extrasMissing(List<Integer> extras, Map<Integer, Integer> inventory) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        extras.forEach(id -> out.merge(id, 1, Integer::sum));
        out.replaceAll((id, n) -> WHOLE_STACK.contains(id) ? (inventory.containsKey(id) ? 0 : 1) : n - inventory.getOrDefault(id, 0));
        out.values().removeIf(n -> n <= 0);
        return out;
    }

    private Outcome bank(AccountSnapshot s, Set<Integer> wanted, List<Need> fromBank, List<Integer> picks, List<Integer> extras) {
        if (++bankTrips > MAX_BANK_TRIPS) {
            return Outcome.parked("could not sort the kit at the bank");
        }
        if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, BANK_OPEN_MS)) {
            return fail("could not open a bank");
        }
        Predicate<Rs2ItemModel> unwanted = depositable(wanted, burying());
        if (Rs2Inventory.items().anyMatch(unwanted)) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Bank.depositAll(unwanted);
            Global.sleepUntil(() -> Rs2Inventory.items().noneMatch(unwanted), 3000);
        }
        boolean withdraws = !fromBank.isEmpty() || picks.stream().anyMatch(Rs2Bank::hasItem) || extras.stream().anyMatch(Rs2Bank::hasItem);
        if (withdraws && !itemMode()) {
            Rs2Bank.closeBank();
            return fail("could not switch the bank to withdraw items, not notes");
        }
        for (Need n : fromBank) {
            int missing = n.getQuantity() - carried(n, s);
            n.getAnyOf().stream().filter(Rs2Bank::hasItem).findFirst().ifPresent(id -> withdraw(id, missing));
        }
        for (int id : picks) {
            if (!s.getInventory().containsKey(id) && !s.getEquipment().containsKey(id) && Rs2Bank.hasItem(id)) {
                withdraw(id, 1);
            }
        }
        extrasMissing(extras, s.getInventory()).forEach((id, missing) -> {
            if (!Rs2Bank.hasItem(id)) return;
            if (WHOLE_STACK.contains(id)) Rs2Bank.withdrawAll(id);
            else withdraw(id, missing);
        });
        Rs2Bank.closeBank();
        Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
        return Outcome.cont();
    }

    /** Ids are canonical, so a noted item matches by its unnoted id (depositAll(int) would never match it). */
    static Predicate<Rs2ItemModel> depositable(Set<Integer> keep) {
        return depositable(keep, false);
    }

    static Predicate<Rs2ItemModel> depositable(Set<Integer> keep, boolean burying) {
        return item -> deposits(item.isNoted() ? item.getUnNotedId() : item.getId(), item.isNoted(), keep, burying);
    }

    public static boolean deposits(int id, boolean noted, Set<Integer> keep, boolean burying) {
        if (NEVER_DEPOSIT.contains(id)) return false;
        if (burying && LootRules.BONES.contains(id)) return false;
        return noted || !keep.contains(id);
    }

    public static boolean withdrawReady(boolean bankOpen, boolean noteMode) {
        return bankOpen && !noteMode;
    }

    private static boolean itemMode() {
        if (withdrawReady(Rs2Bank.isOpen(), Rs2Bank.hasWithdrawAsNote())) return true;
        Rs2Bank.setWithdrawAsItem();
        return withdrawReady(Rs2Bank.isOpen(), Rs2Bank.hasWithdrawAsNote());
    }

    private boolean burying() {
        return choices.isBury() && XpGuard.allowed(Action.BURY, choices.getProtectedSkills());
    }

    private void withdraw(int id, int quantity) {
        Global.sleep((int) pace.reactionMs(random));
        int before = Rs2Inventory.itemQuantity(id);
        if (quantity <= 1) Rs2Bank.withdrawOne(id);
        else Rs2Bank.withdrawX(id, quantity);
        Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before, 2000);
    }

    private Outcome equip(int id) {
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.wield(id) || !Global.sleepUntil(() -> Rs2Equipment.isWearing(id), 2000)) {
            return fail("could not wear item " + id);
        }
        return Outcome.cont();
    }

    static int stockUp(int missing, int canPay, int stack, boolean extra) {
        if (missing <= 0) return 0;
        int rounded = (missing + stack - 1) / stack * stack + (extra ? stack : 0);
        return Math.max(0, Math.min(rounded, canPay));
    }

    static int shopClick(int left) {
        return left >= 50 ? 50 : left >= 10 ? 10 : left >= 5 ? 5 : 1;
    }

    private Outcome shop(Need n, AccountSnapshot s, int sharers) {
        String npc = n.getFree().tutorName();
        int item = n.getBuyItemId();
        int have = n.getAnyOf().stream().mapToInt(s::owned).sum();
        int canPay = s.getCoins() / (Needs.shopPrice(item) * Math.max(1, sharers));
        int want = stockUp(n.getQuantity() - have, canPay, item == ItemID.BRONZE_ARROW ? 50 : 10, random.nextInt(3) == 0);
        if (want == 0) return Outcome.parked("no coins for " + n.getLabel() + " at " + npc);
        if (Rs2Inventory.itemQuantity(ItemID.COINS) < want * Needs.shopPrice(item)) {
            if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, BANK_OPEN_MS)) return fail("could not open a bank");
            // the price climbs as stock falls: bring all the coins the trip could use
            Rs2Bank.withdrawAll(ItemID.COINS);
            Global.sleepUntil(() -> Rs2Inventory.hasItem(ItemID.COINS), 2000);
            Rs2Bank.closeBank();
        }
        if (!Detour.walkTo(Spots.VARROCK.get(npc), 4)) return fail("could not reach " + npc);
        if (!Rs2Shop.openShop(npc, true) || !Global.sleepUntil(Rs2Shop::isOpen, 5000)) return fail(npc + "'s shop did not open");
        int before = Rs2Inventory.itemQuantity(item);
        for (int left = want, i = 0; left > 0 && i < MAX_SHOP_CLICKS; i++) {
            int last = Rs2Inventory.itemQuantity(item);
            int size = shopClick(left);
            Global.sleep((int) pace.reactionMs(random));
            if (!Rs2Shop.buyItem(item, String.valueOf(size)) || !Global.sleepUntil(() -> Rs2Inventory.itemQuantity(item) > last, 2400)) break;
            left -= size;
        }
        Rs2Shop.closeShop();
        return Rs2Inventory.itemQuantity(item) > before ? Outcome.cont() : fail(npc + " sold no " + n.getLabel());
    }

    /** Talk-to, not his Claim-weapons option: that gives nothing. */
    private Outcome harlan(Need n) {
        if (!Rs2Walker.walkTo(MudfootActivity.HARLAN, 3)) return fail("could not reach Captain Harlan");
        Rs2NpcModel harlan = Microbot.getRs2NpcCache().query().withNames("Captain Harlan").nearestOnClientThread();
        if (harlan == null) return fail("Captain Harlan is not here");
        Global.sleep((int) pace.reactionMs(random));
        if (!harlan.click("Talk-to") || !Global.sleepUntil(Rs2Dialogue::isInDialogue, 5000)) return fail("Captain Harlan did not answer");
        String set = n.getAnyOf().contains(GearPlan.AIR) || n.getAnyOf().contains(GearPlan.MIND)
                ? "Could I have a staff and some runes?" : "Could I have a shortbow and some arrows?";
        BreakHandlerScript.setLockState(true);
        try {
            converse(List.of("Do you have any weapons I could have?", set));
            // he offers the other sets next: the menu is closed, not answered
            Dialogs.close();
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        // counted even when he refuses, so a claim too soon is not repeated
        HarlanClaim.claimed(System.currentTimeMillis());
        return Outcome.cont();
    }

    private Outcome tutor(Need n, AccountSnapshot s) {
        Skill skill = task.skill();
        WorldPoint tile = Spots.TUTORS.get(skill);
        int npcId = tutorId(skill);
        if (tile == null || npcId == 0) {
            return Outcome.parked(n.getLabel() + ": no tutor for " + skill);
        }
        if (!Rs2Walker.walkTo(tile, 4)) {
            return fail("could not reach the " + skill + " tutor");
        }
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withId(npcId).nearestOnClientThread();
        if (npc == null) {
            return fail("the " + skill + " tutor is not here");
        }
        Global.sleep((int) pace.reactionMs(random));
        if (!npc.click("Talk-to") || !Global.sleepUntil(Rs2Dialogue::isInDialogue, 5000)) {
            return fail("the " + skill + " tutor did not answer");
        }
        BreakHandlerScript.setLockState(true);
        try {
            converse(toolOptions(skill));
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        if (!Global.sleepUntil(() -> n.getAnyOf().stream().anyMatch(Rs2Inventory::hasItem), 3000)) {
            return fail("the " + skill + " tutor gave no " + n.getLabel());
        }
        return Outcome.cont();
    }

    private void converse(List<String> options) {
        for (int step = 0; step < MAX_DIALOGUE_STEPS; step++) {
            if (!Global.sleepUntil(Rs2Dialogue::isInDialogue, 2000)) break;
            if (Rs2Dialogue.hasSelectAnOption()) {
                String pick = options.stream().filter(Rs2Dialogue::hasDialogueOption).findFirst().orElse(null);
                if (pick == null) break;
                Global.sleep((int) pace.reactionMs(random));
                if (!Rs2Dialogue.clickOption(pick)) break;
                Global.sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption(), 3000);
                continue;
            }
            String text = Rs2Dialogue.getDialogueText();
            if (text == null) break;
            Global.sleep((int) pace.readMs(text, random));
            Rs2Dialogue.clickContinue();
            Global.sleepUntil(() -> !Rs2Dialogue.isInDialogue() || !text.equals(Rs2Dialogue.getDialogueText()), 3000);
        }
    }

    private Outcome fail(String reason) {
        log.info("Account Starter: kit: {}", reason);
        return ++failures >= MAX_FAILURES ? Outcome.parked(reason) : Outcome.cont();
    }

    private static int carried(Need n, AccountSnapshot s) {
        return n.getAnyOf().stream().mapToInt(s::carried).sum();
    }

    private static int tutorId(Skill skill) {
        switch (skill) {
            case FISHING: return NpcID.FINLAY;
            case WOODCUTTING: return NpcID.WILFRED;
            case MINING: return NpcID.MONLUM;
            default: return 0;
        }
    }

    static List<String> toolOptions(Skill skill) {
        switch (skill) {
            case FISHING: return List.of("fishing tools");
            case WOODCUTTING: return List.of("any tools", "Both please", "An axe please", "A tinderbox please");
            default: return List.of("have a pickaxe");
        }
    }
}
