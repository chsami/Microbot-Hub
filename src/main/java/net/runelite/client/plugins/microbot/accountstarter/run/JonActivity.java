package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.accountstarter.AccountStarterConfig;
import net.runelite.client.plugins.microbot.accountstarter.ResumeStore;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision;
import net.runelite.client.plugins.microbot.accountstarter.logic.JonReply;
import net.runelite.client.plugins.microbot.accountstarter.logic.ResumeState;
import net.runelite.client.plugins.microbot.accountstarter.logic.RewardSpace;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public final class JonActivity implements Activity {
    public static final String OPTED_OUT = "account opted out of Adventure Paths";
    static final String TOO_FULL = "inventory too full for Jon's rewards";
    private static final int MAX_FAILURES = 3;
    private static final int MAX_UNKNOWN = 2;
    private static final int MAX_SPACE_TRIPS = 2;
    private static final int MAX_DIALOGUE_STEPS = 20;
    // the kit opens into 12 items
    private static final int KIT_SLOTS = 12;

    private final boolean kit;
    private final List<ApTask> unclaimed;
    private final ResumeStore store;
    private final String accountKey;
    private final ResumeState state;
    private final ApPace pace;
    private final Random random;
    private final AccountStarterConfig config;
    private final boolean burying;

    private boolean started;
    private boolean needBank;
    private JonReply claimed;
    private final Set<Integer> usedLater;
    private int failures;
    private int unknowns;
    private int spaceTrips;

    public JonActivity(Decision.Kind kind, List<ApTask> unclaimed, ResumeStore store, String accountKey,
                       ResumeState state, ApPace pace, Random random, AccountStarterConfig config, boolean burying,
                       Set<Integer> usedLater) {
        if (kind != Decision.Kind.STARTER_KIT && kind != Decision.Kind.CLAIM_AT_JON) {
            throw new IllegalArgumentException("JonActivity cannot run " + kind);
        }
        this.kit = kind == Decision.Kind.STARTER_KIT;
        this.unclaimed = List.copyOf(unclaimed);
        this.usedLater = Set.copyOf(usedLater);
        this.store = store;
        this.accountKey = accountKey;
        this.state = state;
        this.pace = pace;
        this.random = random;
        this.config = config;
        this.burying = burying;
    }

    @Override
    public String describe() {
        return kit ? "Getting the starter kit from Adventurer Jon" : "Claiming rewards from Adventurer Jon";
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        if (!started) {
            started = true;
            needBank = s.getFreeSlots() < slotsWanted(s);
        }
        if (needBank) {
            return bank(s);
        }
        if (claimed != null) {
            return bankRewards();
        }
        if (kit && Rs2Inventory.hasItem(ApTask.STARTER_KIT_ID)) {
            return openKit();
        }
        if (!Rs2Walker.walkTo(ApTask.JON_TILE, 4)) {
            return fail("could not reach Adventurer Jon");
        }
        Rs2NpcModel jon = Microbot.getRs2NpcCache().query().withId(ApTask.JON_NPC_ID).nearestOnClientThread();
        if (jon == null) {
            return fail("Adventurer Jon not found");
        }
        Global.sleep((int) pace.reactionMs(random));
        if (!jon.click(kit ? "Talk-to" : "Claim")) {
            return fail("could not click Adventurer Jon");
        }
        if (!Global.sleepUntil(Rs2Dialogue::isInDialogue, 5000)) {
            return fail("Adventurer Jon did not answer");
        }
        JonReply reply;
        BreakHandlerScript.setLockState(true);
        try {
            reply = converse();
            // Jon reopens his menu after answering; close it so the next step does not read it
            Dialogs.close();
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        store.save(accountKey, state);
        return settle(reply);
    }

    @Override
    public void abort() {
        BreakHandlerScript.setLockState(false);
    }

    private Outcome settle(JonReply reply) {
        switch (reply) {
            case KIT_GIVEN:
            case KIT_ALREADY_CLAIMED:
                state.setStarterKitClaimed(true);
                store.save(accountKey, state);
                return reply == JonReply.KIT_GIVEN ? openKit() : Outcome.done("starter kit already claimed");
            case ALL_GIVEN:
            case ALL_GIVEN_MEMBERS_LEFT:
                if (!rewardsCarried().isEmpty()) {
                    claimed = reply;
                    return Outcome.cont();
                }
                return Outcome.done("Jon: " + reply);
            case NO_REWARDS:
            case MEMBERS_LEFT_ONLY:
                return Outcome.done("Jon: " + reply);
            case NEED_SPACE_NOTHING_GIVEN:
            case NEED_SPACE_PART_GIVEN:
                if (++spaceTrips > MAX_SPACE_TRIPS) {
                    return Outcome.parked(TOO_FULL);
                }
                needBank = true;
                return Outcome.cont();
            case NOT_PARTICIPATING:
                // allowOptIn already answered "Yes please" in the dialogue; talk again to get the real reply
                return config.allowOptIn() ? fail("rejoined Adventure Paths") : Outcome.failed(OPTED_OUT);
            default:
                return ++unknowns >= MAX_UNKNOWN ? Outcome.parked("Adventurer Jon's reply was not recognised") : Outcome.cont();
        }
    }

    private JonReply converse() {
        JonReply last = JonReply.UNKNOWN;
        for (int step = 0; step < MAX_DIALOGUE_STEPS; step++) {
            if (!Global.sleepUntil(Rs2Dialogue::isInDialogue, 2000)) {
                break;
            }
            if (Rs2Dialogue.hasSelectAnOption()) {
                // Jon reopens his menu after answering: stop instead of asking again
                if (last != JonReply.UNKNOWN && last != JonReply.NOT_PARTICIPATING) {
                    break;
                }
                if (!answer()) {
                    break;
                }
                Global.sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption(), 3000);
                continue;
            }
            String text = readText();
            JonReply r = JonReply.parse(text);
            if (r != JonReply.UNKNOWN && r != JonReply.OPT_OUT_PROMPT) {
                last = r;
            }
            if (!text.isEmpty()) {
                state.setLastJonReply(text);
            }
            Global.sleep((int) pace.readMs(text, random));
            Rs2Dialogue.clickContinue();
            Global.sleepUntil(() -> !Rs2Dialogue.isInDialogue() || !text.equals(readText()), 3000);
        }
        return last;
    }

    private boolean answer() {
        Global.sleep((int) pace.reactionMs(random));
        if (Rs2Dialogue.hasDialogueOption("Yes please")) {
            return Rs2Dialogue.clickOption(config.allowOptIn() ? "Yes please" : "No thanks");
        }
        if (JonReply.parse(Rs2Dialogue.getQuestion()) == JonReply.OPT_OUT_PROMPT) {
            return Rs2Dialogue.clickOption("No", true);
        }
        return Rs2Dialogue.clickOption(kit ? "starter kits" : "claim my Adventure Path rewards");
    }

    private Outcome bank(AccountSnapshot s) {
        if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) {
            return fail("could not open a bank");
        }
        int want = Math.min(slotsWanted(s), 28);
        // live slot ids, so noted items deposit too
        List<Integer> ids = Rs2Inventory.items().filter(Kit.depositable(Set.of(), burying)).map(Rs2ItemModel::getId)
                .distinct().collect(Collectors.toList());
        for (int id : ids) {
            if (Rs2Inventory.emptySlotCount() >= want) {
                break;
            }
            Global.sleep((int) pace.reactionMs(random));
            Rs2Bank.depositAll(id);
            Global.sleepUntil(() -> !Rs2Inventory.hasItem(id), 2000);
        }
        needBank = Rs2Inventory.emptySlotCount() < want;
        Rs2Bank.closeBank();
        return needBank ? fail("could not free inventory space") : Outcome.cont();
    }

    private Outcome bankRewards() {
        if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) {
            return fail("could not open a bank");
        }
        for (int id : rewardsCarried()) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Bank.depositAll(id);
            Global.sleepUntil(() -> !Rs2Inventory.hasItem(id), 2000);
        }
        boolean left = !rewardsCarried().isEmpty();
        Rs2Bank.closeBank();
        return left ? fail("could not bank the rewards") : Outcome.done("Jon: " + claimed + ", rewards banked");
    }

    private List<Integer> rewardsCarried() {
        Set<Integer> rewards = RewardSpace.toBank(unclaimed, usedLater);
        return Rs2Inventory.items().filter(Kit.depositable(Set.of(), burying))
                .filter(i -> rewards.contains(i.isNoted() ? i.getUnNotedId() : i.getId()))
                .map(Rs2ItemModel::getId).distinct().collect(Collectors.toList());
    }

    private Outcome openKit() {
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.interact(ApTask.STARTER_KIT_ID, "Open")
                || !Global.sleepUntil(() -> !Rs2Inventory.hasItem(ApTask.STARTER_KIT_ID), 3000)) {
            return fail("could not open the starter kit");
        }
        return Outcome.done("starter kit opened");
    }

    private int slotsWanted(AccountSnapshot s) {
        return kit ? KIT_SLOTS : RewardSpace.slotsNeeded(unclaimed, s.getInventory());
    }

    private Outcome fail(String reason) {
        log.info("Account Starter: Jon: {}", reason);
        return ++failures >= MAX_FAILURES ? Outcome.parked(reason) : Outcome.cont();
    }

    private static String readText() {
        String text = Rs2Dialogue.getDialogueText();
        if (!text.isEmpty()) {
            return text;
        }
        // item boxes ("gives you a ... starter kit") are not read by getDialogueText
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(InterfaceID.Objectbox.TEXT);
            return w == null || w.isHidden() ? null : w.getText();
        }).orElse("");
    }
}
