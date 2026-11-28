package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.RewardItem;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/** Talking to the NPC with the voucher is enough: the reward's varbit is set during the opening lines. */
@Slf4j
public final class VoucherActivity implements Activity {
    private boolean cleared;
    private static final int MAX_FAILURES = 3;
    private static final int MAX_DIALOGUE_STEPS = 20;
    private static final int SLOTS = 2;
    private static final List<String> NEVER = List.of("buy", "trade", "sell", "shop");

    private final VoucherReward reward;
    private final ApPace pace;
    private final Random random;
    private final boolean burying;
    private int failures;

    public VoucherActivity(VoucherReward reward, ApPace pace, Random random, boolean burying) {
        this.reward = reward;
        this.pace = pace;
        this.random = random;
        this.burying = burying;
    }

    @Override
    public String describe() {
        return "Claiming the voucher reward from " + reward.npcName();
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        if (!cleared) {
            Dialogs.close();
            cleared = true;
        }
        if (claimed(reward)) {
            return finish();
        }
        if (s.getFreeSlots() < SLOTS || !s.getInventory().containsKey(ApTask.VOUCHER_ID)) {
            return bank();
        }
        WorldPoint tile = Spots.VARROCK.get(reward.npcName());
        if (tile == null || !Detour.walkTo(tile, 4)) {
            return fail("could not reach " + reward.npcName());
        }
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withName(reward.npcName()).nearestOnClientThread();
        if (npc == null) {
            return fail(reward.npcName() + " not found");
        }
        Global.sleep((int) pace.reactionMs(random));
        if (!npc.click("Talk-to") || !Global.sleepUntil(Rs2Dialogue::isInDialogue, 5000)) {
            return fail(reward.npcName() + " did not answer");
        }
        BreakHandlerScript.setLockState(true);
        try {
            converse();
            Dialogs.close();
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        if (Global.sleepUntil(() -> claimed(reward), 3000)) {
            return finish();
        }
        return fail(reward.npcName() + " gave no reward");
    }

    @Override
    public void abort() {
        BreakHandlerScript.setLockState(false);
    }

    public static Optional<String> voucherOption(List<String> options) {
        return options.stream().filter(o -> {
            String l = o.toLowerCase(Locale.ENGLISH);
            return l.contains("voucher") && NEVER.stream().noneMatch(l::contains);
        }).findFirst();
    }

    public static boolean isReward(int itemId) {
        return Arrays.stream(VoucherReward.values()).flatMap(v -> v.rewards().stream())
                .mapToInt(RewardItem::getItemId).anyMatch(id -> id == itemId);
    }

    private void converse() {
        for (int step = 0; step < MAX_DIALOGUE_STEPS; step++) {
            if (!Global.sleepUntil(Rs2Dialogue::isInDialogue, 2000)) return;
            if (Rs2Dialogue.hasSelectAnOption()) {
                List<String> options = Rs2Dialogue.getDialogueOptions().stream().map(Widget::getText).collect(Collectors.toList());
                Optional<String> pick = voucherOption(options);
                if (!pick.isPresent()) {
                    if (!claimed(reward)) log.info("Account Starter: voucher: {} offered no reward: {}", reward.npcName(), options);
                    return;
                }
                Global.sleep((int) pace.reactionMs(random));
                if (!Rs2Dialogue.clickOption(pick.get(), true)) return;
                Global.sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption(), 3000);
                continue;
            }
            String text = Rs2Dialogue.getDialogueText();
            Global.sleep((int) pace.readMs(text, random));
            Rs2Dialogue.clickContinue();
            Global.sleepUntil(() -> !Rs2Dialogue.isInDialogue() || !text.equals(Rs2Dialogue.getDialogueText()), 3000);
        }
    }

    private Outcome bank() {
        if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) {
            return fail("could not open a bank");
        }
        Predicate<Rs2ItemModel> unwanted = Kit.depositable(Set.of(), burying);
        if (Rs2Inventory.emptySlotCount() < SLOTS && Rs2Inventory.items().anyMatch(unwanted)) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Bank.depositAll(unwanted);
            Global.sleepUntil(() -> Rs2Inventory.emptySlotCount() >= SLOTS, 3000);
        }
        if (!Rs2Inventory.hasItem(ApTask.VOUCHER_ID) && Rs2Bank.hasItem(ApTask.VOUCHER_ID)) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Bank.withdrawOne(ApTask.VOUCHER_ID);
            Global.sleepUntil(() -> Rs2Inventory.hasItem(ApTask.VOUCHER_ID), 2000);
        }
        boolean ready = Rs2Inventory.emptySlotCount() >= SLOTS && Rs2Inventory.hasItem(ApTask.VOUCHER_ID);
        Rs2Bank.closeBank();
        Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
        return ready ? Outcome.cont() : fail("could not get the voucher and two free slots");
    }

    /** The voucher stays in the inventory on F2P. */
    private Outcome finish() {
        String claimedText = "reward claimed from " + reward.npcName();
        if (!Arrays.stream(VoucherReward.values()).allMatch(VoucherActivity::claimed)) return Outcome.done(claimedText);
        // the Apothecary pays his potions noted
        Predicate<Rs2ItemModel> rewards = i -> isReward(i.isNoted() ? i.getUnNotedId() : i.getId());
        if (Rs2Inventory.items().noneMatch(rewards)) return Outcome.done(claimedText + ", rewards banked");
        if (Rs2Bank.walkToBankAndUseBank() && Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Bank.depositAll(rewards);
            Global.sleepUntil(() -> Rs2Inventory.items().noneMatch(rewards), 3000);
            Rs2Bank.closeBank();
            Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            if (Rs2Inventory.items().noneMatch(rewards)) return Outcome.done(claimedText + ", rewards banked");
        }
        return ++failures >= MAX_FAILURES ? Outcome.done(claimedText + ", rewards left in the inventory") : Outcome.cont();
    }

    private static boolean claimed(VoucherReward r) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getVarbitValue(r.varbit()) != 0).orElse(false);
    }

    private Outcome fail(String reason) {
        log.info("Account Starter: voucher: {}", reason);
        return ++failures >= MAX_FAILURES
                ? Outcome.failed("could not claim the voucher reward from " + reward.npcName())
                : Outcome.cont();
    }
}
