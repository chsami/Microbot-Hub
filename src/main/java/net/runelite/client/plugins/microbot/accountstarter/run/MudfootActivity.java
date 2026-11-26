package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.HarlanClaim;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** No published progress varbit or outlaw ids: the step is saved by us and every NPC is found by name and option. */
@Slf4j
public final class MudfootActivity implements Activity {
    /** Saved by ordinal: keep the order. */
    public enum Step { START, MEET_MUDFOOT, CONFRONT, WEAPON, RANGER, MAGE, WARRIOR, FINISH }

    public static final int MIN_FOOD = 5;
    private static final int FOOD_TRIP = 10;

    static final WorldPoint HARLAN = new WorldPoint(3219, 3239, 0);
    static final WorldPoint MUDFOOT = new WorldPoint(3249, 3249, 0);
    static final WorldPoint STORE = new WorldPoint(3214, 3245, 0);
    static final WorldPoint RANGER_AT = new WorldPoint(3166, 3288, 0);
    static final WorldPoint MAGE_AT = new WorldPoint(3172, 3240, 0);
    static final WorldPoint WARRIOR_AT = new WorldPoint(3243, 3197, 0);
    private static final int AREA = 12;
    private static final int MAX_FAILURES = 3;
    private static final int MAX_DIALOGUE_STEPS = 40;
    private static final int MAX_KIT_TRIES = 8;
    private static final int KIT_SLOTS = 12;
    private static final List<String> WHERE_TO = List.of("Where to, Mudfoot?", "What now, Mudfoot?");
    /** Mudfoot waits by the H.A.M hideout when left behind. */
    private static final long NO_OUTLAW_MS = 25_000;
    private static final int SHORTBOW = ItemID.SHORTBOW, STAFF = GearPlan.STAFF, SCIMITAR = ItemID.IRON_SCIMITAR;
    private static final Set<Integer> STAB = Set.of(ItemID.BRONZE_SWORD, ItemID.IRON_SWORD, ItemID.BRONZE_DAGGER, ItemID.IRON_DAGGER);
    private static final Set<Integer> RANGER_DROP = Set.of(SHORTBOW, ItemID.BRONZE_ARROW);
    private static final Set<Integer> MAGE_DROP = Set.of(STAFF, ItemID.AIRRUNE, ItemID.MINDRUNE);
    private static final Set<Integer> WARRIOR_DROP = Set.of(SCIMITAR, ItemID.IRON_KITESHIELD);

    private final Choices choices;
    private final ApPace pace;
    private final Random random;
    private final IntConsumer saveStep;

    private volatile Step step;
    private int failures;
    private int lootFails;
    private boolean fought;
    private boolean styleSet;
    private long noOutlawSince;
    private int fetches;
    private String lastRead = "";
    private boolean claimed;
    private boolean triedHarlan;
    private volatile boolean walking;
    private volatile String walkPlace;

    private final double eatAt;

    private final boolean magicKit;
    private GearPlan.QuestKit kit;
    private boolean kitChecked;
    private boolean deposited;
    private int kitTries;

    public MudfootActivity(Choices choices, ApPace pace, Random random, boolean magicKit, int savedStep, IntConsumer saveStep) {
        this.choices = choices;
        this.magicKit = magicKit;
        this.pace = pace;
        this.random = random;
        this.saveStep = saveStep;
        this.eatAt = 0.55 + random.nextDouble() * 0.15;
        Step[] all = Step.values();
        this.step = all[Math.max(0, Math.min(savedStep, all.length - 1))];
    }

    @Override
    public String describe() {
        return "Mudfoot's Big Adventure: " + step.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
    }

    @Override
    public void abort() {
        if (walking) Rs2Walker.setTarget(null);
    }

    @Override
    public boolean walking() {
        return walking;
    }

    @Override
    public String walkingTo() {
        return walking ? walkPlace : null;
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        // read direct: Rs2Player's cache only refreshes quests QuestHelperQuest lists
        QuestState qs = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Quest.MUDFOOTS_BIG_ADVENTURE.getState(Microbot.getClient())).orElse(null);
        if (qs == null) return Outcome.cont();
        if (qs == QuestState.FINISHED) {
            save(Step.FINISH);
            return Outcome.done("Mudfoot's Big Adventure complete");
        }
        if (qs == QuestState.NOT_STARTED && step != Step.START) save(Step.START);
        if (qs == QuestState.IN_PROGRESS && step == Step.START) save(Step.MEET_MUDFOOT);
        Outcome eat = eatIfLow(s);
        if (eat != null) return eat;
        if (!magicKit && step == Step.START && Needs.edible(s) < MIN_FOOD && s.getFreeSlots() > 0 && Needs.bankedFood(s) != 0) return fetchFood(s);
        if (magicKit && kitPending(s)) return loadKit(s);
        kitTries = 0;
        switch (step) {
            case START:
                return talk("Captain Harlan", HARLAN, List.of("Do you need help with anything?", "Yes."), null);
            case MEET_MUDFOOT:
                // Mudfoot will not follow while a pet is out
                return pickUpFollower() ? Outcome.cont() : talk("Mudfoot", MUDFOOT, List.of(), "behind you");
            case CONFRONT:
                return confront();
            case WEAPON:
                return weapon(s);
            case RANGER:
                return fight(s, RANGER_AT, "the chicken field by the windmill", RANGER_DROP, Step.MAGE,
                        List.of(Style.MELEE, Style.RANGED, Style.MAGIC));
            case MAGE:
                return fight(s, MAGE_AT, "the H.A.M hideout", MAGE_DROP, Step.WARRIOR,
                        List.of(Style.RANGED, Style.MELEE, Style.MAGIC));
            case WARRIOR:
                // he prays Protect from Melee after the first hit: magic or ranged only
                return fight(s, WARRIOR_AT, "the Lumbridge graveyard", WARRIOR_DROP, Step.FINISH,
                        List.of(Style.MAGIC, Style.RANGED));
            default:
                return finish();
        }
    }

    /** Harlan only offers the closing line once all three are dead: without it the last fight is not over. */
    private Outcome finish() {
        boolean there = npc("Captain Harlan", HARLAN) != null;
        Outcome o = talk("Captain Harlan", HARLAN, List.of("All of those thieves have been dealt with."), null);
        if (there && !lastRead.contains("all of those thieves")) save(Step.WARRIOR);
        return o;
    }

    private Outcome talk(String name, WorldPoint at, List<String> options, String advanceOn) {
        Rs2NpcModel npc = npc(name, at);
        if (npc == null) return walkTo(at, "to " + name);
        Global.sleep((int) pace.reactionMs(random));
        if (!npc.click("Talk-to") || !Global.sleepUntil(Rs2Dialogue::isInDialogue, 6000)) return fail(name + " did not answer");
        String read = converse(options);
        if (advanceOn != null && read.contains(advanceOn.toLowerCase(Locale.ENGLISH))) next();
        else if (step == Step.MEET_MUDFOOT && read.contains("where to, mudfoot")) next();
        if (outlawDone(step, read)) fought = true;
        return heard(read, name);
    }

    private Outcome confront() {
        Rs2NpcModel outlaw = outlaw(STORE, "Confront");
        if (outlaw == null) {
            WorldPoint me = Rs2Player.getWorldLocation();
            if (me == null || me.distanceTo2D(STORE) > AREA) return walkTo(STORE, "to the General Store");
            // he only roams here before the cutscene: gone means the confront already happened
            long now = System.currentTimeMillis();
            if (noOutlawSince == 0) noOutlawSince = now;
            if (now - noOutlawSince >= NO_OUTLAW_MS) next();
            return Outcome.cont();
        }
        noOutlawSince = 0;
        Global.sleep((int) pace.reactionMs(random));
        if (!outlaw.click("Confront") || !Global.sleepUntil(Rs2Dialogue::isInDialogue, 8000)) return fail("the outlaw could not be confronted");
        String read = converse(List.of());
        if (read.contains("time to go now") || read.contains("grab poker")) next();
        return heard(read, "the outlaw");
    }

    /** Any style beats the ranger and the mage: only an unarmed account takes Harlan's sword. */
    private Outcome weapon(AccountSnapshot s) {
        if (Rs2Equipment.isWearing(EquipmentInventorySlot.WEAPON)) {
            next();
            return Outcome.cont();
        }
        int stab = STAB.stream().filter(id -> s.carried(id) > 0 || s.getEquipment().containsKey(id)).findFirst().orElse(0);
        if (stab == 0) {
            Outcome o = talk("Captain Harlan", HARLAN, List.of("So, about those thieves...", "Could I have a weapon for stabbing?",
                    "Thanks. I'd best get going."), null);
            if (STAB.stream().noneMatch(Rs2Inventory::hasItem)) return o;
            stab = STAB.stream().filter(Rs2Inventory::hasItem).findFirst().orElse(0);
        }
        if (!s.getEquipment().containsKey(stab)) {
            Global.sleep((int) pace.reactionMs(random));
            int id = stab;
            if (!Rs2Inventory.wield(id) || !Global.sleepUntil(() -> !Rs2Inventory.hasItem(id), 2400)) return fail("could not wield the stab weapon");
            if (Rs2Inventory.hasItem(ItemID.WOODEN_SHIELD)) Rs2Inventory.wield(ItemID.WOODEN_SHIELD);
        }
        next();
        return Outcome.cont();
    }

    enum Style { MELEE, RANGED, MAGIC }

    private Outcome fight(AccountSnapshot s, WorldPoint at, String place, Set<Integer> drop, Step after, List<Style> prefer) {
        if (Rs2Dialogue.isInDialogue()) {
            String read = converse(WHERE_TO);
            if (outlawDone(step, read)) fought = true;
            // outlaws refuse a confront without him; he waits where he was left
            if (read.contains("mudfoot first")) return talk("Mudfoot", step == Step.MAGE ? RANGER_AT : MAGE_AT, WHERE_TO, null);
            return Outcome.cont();
        }
        if (fought && loot(drop)) return Outcome.cont();
        if (kit == null && step == Step.WARRIOR && !fought && !hasShots(s)) return restock(s);
        // mid-fight the outlaw reports a far-off location, so "none near the spot" must not end the fight
        if (Rs2Player.getInteracting() != null || Rs2Player.isAnimating(1800)) return Outcome.cont();
        // once the fight starts the outlaw is a new NPC at instanced coordinates: check it first, or the old one gets Confront
        Rs2NpcModel outlaw = fought ? null : fightingOutlaw();
        if (outlaw == null) outlaw = outlaw(at, null);
        if (outlaw == null) {
            if (fought) {
                // an outlaw out of view is not a kill: only Mudfoot's words end the fight
                fought = false;
                styleSet = false;
                noOutlawSince = 0;
                save(after);
                return Outcome.cont();
            }
            if (Rs2Player.getWorldLocation() == null || Rs2Player.getWorldLocation().distanceTo2D(at) > AREA) {
                return walkTo(at, place);
            }
            return noOutlaw(at);
        }
        noOutlawSince = 0;
        if (!styleSet) {
            String why = style(s, prefer);
            if (why != null) {
                if (claimed) return Outcome.parked("quest: " + why);
                return claimWeapons();
            }
            styleSet = true;
            return Outcome.cont();
        }
        String op = firstAction(outlaw, "Attack", "Confront");
        if (op == null) {
            // no fight option yet: the fight starts by talking to Mudfoot nearby
            return talk("Mudfoot", at, WHERE_TO, null);
        }
        Global.sleep((int) pace.reactionMs(random));
        BreakHandlerScript.setLockState(true);
        try {
            if (!outlaw.click(op)) return fail("the outlaw could not be clicked");
            Global.sleepUntil(() -> Rs2Player.getInteracting() != null || Rs2Dialogue.isInDialogue(), 5000);
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        return Outcome.cont();
    }

    private Outcome claimWeapons() {
        if (npc("Captain Harlan", HARLAN) == null) return walkTo(HARLAN, "to Captain Harlan");
        claimed = true;
        HarlanClaim.claimed(System.currentTimeMillis());
        return talk("Captain Harlan", HARLAN, List.of("Do you have any weapons I could have?",
                "Could I have a staff and some runes?", "Could I have a shortbow and some arrows?"), null);
    }

    private Outcome noOutlaw(WorldPoint at) {
        long now = System.currentTimeMillis();
        if (noOutlawSince == 0) noOutlawSince = now;
        if (npc("Mudfoot", at) != null) return talk("Mudfoot", at, WHERE_TO, null);
        if (now - noOutlawSince < NO_OUTLAW_MS) return Outcome.cont();
        // no warrior and no Mudfoot: he may be dead already, and Harlan's menu says so
        if (step == Step.WARRIOR && !triedHarlan) {
            triedHarlan = true;
            save(Step.FINISH);
            return Outcome.cont();
        }
        if (++fetches > MAX_FAILURES) return Outcome.parked("quest: no outlaw and no Mudfoot at " + at);
        noOutlawSince = 0;
        return talk("Mudfoot", MAGE_AT, WHERE_TO, null);
    }

    private String style(AccountSnapshot s, List<Style> prefer) {
        Set<Skill> prot = choices.getProtectedSkills();
        if (kit != null) {
            String why = CombatStyle.ensure(Action.MAGIC_CAST, prot, kit.getSpell());
            if (why == null) return null;
            log.info("Account Starter: quest: magic kit dropped: {}", why);
            kit = null;
        }
        for (Style st : prefer) {
            switch (st) {
                case MAGIC:
                    if (!XpGuard.allowed(Action.MAGIC_CAST, prot)) continue;
                    if (s.carried(ItemID.AIRRUNE) < 1 || s.carried(ItemID.MINDRUNE) < 1) continue;
                    if (s.carried(STAFF) > 0) Rs2Inventory.wield(STAFF);
                    return CombatStyle.ensure(Action.MAGIC_CAST, prot, Rs2CombatSpells.WIND_STRIKE);
                case RANGED:
                    if (!XpGuard.allowed(Action.RANGED_ACCURATE, prot)) continue;
                    if (s.carried(SHORTBOW) + worn(s, SHORTBOW) < 1 || s.carried(ItemID.BRONZE_ARROW) + worn(s, ItemID.BRONZE_ARROW) < 1) continue;
                    if (s.carried(SHORTBOW) > 0) Rs2Inventory.wield(SHORTBOW);
                    if (s.carried(ItemID.BRONZE_ARROW) > 0) Rs2Inventory.wield(ItemID.BRONZE_ARROW);
                    return CombatStyle.ensure(Action.RANGED_ACCURATE, prot, null);
                default:
                    for (Action a : new Action[]{Action.MELEE_ACCURATE, Action.MELEE_AGGRESSIVE, Action.MELEE_DEFENSIVE}) {
                        if (XpGuard.allowed(a, prot)) return CombatStyle.ensure(a, prot, null);
                    }
            }
        }
        return "no combat style this build allows has its gear (bow and arrows, or staff and air/mind runes)";
    }

    private static int worn(AccountSnapshot s, int id) {
        return s.getEquipment().getOrDefault(id, 0);
    }

    private boolean loot(Set<Integer> drop) {
        if (lootFails >= MAX_FAILURES) return false;
        Rs2TileItemModel item = Microbot.getRs2TileItemCache().query()
                .where(i -> drop.contains(i.getId()) && i.isLootAble()).within(10).nearestOnClientThread();
        if (item == null) return false;
        int id = item.getId();
        int before = Rs2Inventory.itemQuantity(id);
        Global.sleep((int) pace.reactionMs(random));
        boolean got = item.pickup() && Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before, 4000);
        lootFails = got ? 0 : lootFails + 1;
        return true;
    }

    private static boolean hasShots(AccountSnapshot s) {
        return s.carried(ItemID.AIRRUNE) > 0 && s.carried(ItemID.MINDRUNE) > 0 || s.carried(ItemID.BRONZE_ARROW) > 0;
    }

    /** Harlan gives his free set only every 30 minutes. */
    private Outcome restock(AccountSnapshot s) {
        styleSet = false;
        Map<Integer, Integer> bank = s.getBank();
        boolean runes = bank.getOrDefault(ItemID.AIRRUNE, 0) > 0 && bank.getOrDefault(ItemID.MINDRUNE, 0) > 0;
        boolean arrows = bank.getOrDefault(ItemID.BRONZE_ARROW, 0) > 0;
        if (!runes && !arrows) {
            if (claimed) return Outcome.parked("quest: out of runes and arrows");
            return claimWeapons();
        }
        if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) return fail("could not open a bank");
        Global.sleep((int) pace.reactionMs(random));
        for (int id : runes ? List.of(ItemID.AIRRUNE, ItemID.MINDRUNE) : List.of(ItemID.BRONZE_ARROW)) {
            Rs2Bank.withdrawAll(id);
            Global.sleepUntil(() -> Rs2Inventory.hasItem(id), 2400);
        }
        if (runes && s.carried(STAFF) == 0 && bank.getOrDefault(STAFF, 0) > 0) Rs2Bank.withdrawOne(STAFF);
        int food = Needs.bankedFood(s);
        if (food != 0 && Needs.edible(s) < FOOD_TRIP) Rs2Bank.withdrawX(food, Math.min(FOOD_TRIP - Needs.edible(s), s.getFreeSlots()));
        Rs2Bank.closeBank();
        return Outcome.cont();
    }

    private boolean kitPending(AccountSnapshot s) {
        return !kitChecked || kit != null && !(s.getEquipment().containsKey(kit.getStaff()) && kit.hasCast(s));
    }

    private Outcome loadKit(AccountSnapshot s) {
        styleSet = false;
        if (++kitTries > MAX_KIT_TRIES) {
            log.info("Account Starter: quest: magic kit could not be loaded");
            return dropKit();
        }
        if (kit != null && s.carried(kit.getStaff()) > 0 && kit.hasCast(s)) {
            if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
            int staff = kit.getStaff();
            Global.sleep((int) pace.reactionMs(random));
            if (Rs2Inventory.wield(staff)) Global.sleepUntil(() -> Rs2Equipment.isWearing(staff), 2400);
            return Outcome.cont();
        }
        if (!Rs2Bank.isOpen()) {
            walkPlace = "to a bank";
            walking = true;
            try {
                if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) return fail("could not open a bank");
            } finally {
                walking = false;
            }
            return Outcome.cont();
        }
        // an empty read means the bank has not landed yet, not that it holds nothing
        if (s.getBank().isEmpty()) return Outcome.cont();
        if (kit == null) kit = GearPlan.questKit(s.getLevels().getOrDefault(Skill.MAGIC, 1), s.getLevels().getOrDefault(Skill.ATTACK, 1), GearPlan.ownedMap(s));
        if (kit == null) {
            log.info("Account Starter: quest: no staff and runes for the magic kit");
            return dropKit();
        }
        kitChecked = true;
        if (!deposited && s.getFreeSlots() < KIT_SLOTS) {
            deposited = true;
            Rs2Bank.depositAll();
            Global.sleepUntil(Rs2Inventory::isEmpty, 2400);
            return Outcome.cont();
        }
        Global.sleep((int) pace.reactionMs(random));
        if (Rs2Bank.hasWithdrawAsNote()) Rs2Bank.setWithdrawAsItem();
        if (s.carried(kit.getStaff()) == 0) Rs2Bank.withdrawOne(kit.getStaff());
        kit.runes().forEach((rune, want) -> {
            if (want > s.carried(rune)) Rs2Bank.withdrawX(rune, want - s.carried(rune));
        });
        int staff = kit.getStaff();
        Global.sleepUntil(() -> Rs2Inventory.hasItem(staff) || Rs2Equipment.isWearing(staff), 2400);
        return Outcome.cont();
    }

    private Outcome dropKit() {
        kit = null;
        kitChecked = true;
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        return Outcome.cont();
    }

    private boolean pickUpFollower() {
        Integer index = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPC follower = Microbot.getClient().getFollower();
            return follower == null ? null : follower.getIndex();
        }).orElse(null);
        if (index == null) return false;
        Rs2NpcModel pet = Microbot.getRs2NpcCache().query().where(n -> n.getIndex() == index).nearestOnClientThread();
        if (pet == null || firstAction(pet, "Pick-up") == null) return false;
        Global.sleep((int) pace.reactionMs(random));
        return pet.click("Pick-up") && Global.sleepUntil(() -> Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getFollower() == null).orElse(false), 4000);
    }

    private Outcome fetchFood(AccountSnapshot s) {
        if (!Rs2Bank.walkToBankAndUseBank() || !Global.sleepUntil(Rs2Bank::isOpen, Kit.BANK_OPEN_MS)) return fail("could not open a bank");
        int id = Needs.bankedFood(s);
        Global.sleep((int) pace.reactionMs(random));
        Rs2Bank.withdrawX(id, Math.min(FOOD_TRIP, s.getFreeSlots()));
        Global.sleepUntil(() -> Rs2Inventory.hasItem(id), 2400);
        Rs2Bank.closeBank();
        return Outcome.cont();
    }

    private Outcome eatIfLow(AccountSnapshot s) {
        int max = s.getLevels().getOrDefault(Skill.HITPOINTS, 10);
        int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        if (hp >= max * eatAt) return null;
        int food = Rs2Inventory.items().filter(i -> !i.isNoted() && Needs.FOOD.contains(i.getId())).map(Rs2ItemModel::getId)
                .findFirst().orElse(0);
        if (food == 0) {
            if (Needs.bankedFood(s) != 0 && s.getFreeSlots() > 0) return fetchFood(s);
            if (hp * 3 > max) return null;
            Rs2Walker.walkTo(HARLAN, 3);
            return Outcome.parked("quest: out of food at " + hp + " Hitpoints");
        }
        Global.sleep((int) pace.reactionMs(random));
        Rs2Inventory.interact(food, "Eat");
        Global.sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > hp, 2400);
        return Outcome.cont();
    }

    private Rs2NpcModel npc(String name, WorldPoint at) {
        return Microbot.getRs2NpcCache().query().withNames(name).within(at, AREA).nearestOnClientThread();
    }

    private Rs2NpcModel outlaw(WorldPoint at, String action) {
        return Microbot.getRs2NpcCache().query()
                .where(n -> n.getName() != null && n.getName().toLowerCase(Locale.ENGLISH).contains("outlaw")
                        && (action == null || firstAction(n, action) != null))
                .within(at, AREA).nearestOnClientThread();
    }

    /** Only the fight's outlaw offers Attack, wherever its instance puts it. */
    private Rs2NpcModel fightingOutlaw() {
        return Microbot.getRs2NpcCache().query()
                .where(n -> n.getName() != null && n.getName().toLowerCase(Locale.ENGLISH).contains("outlaw") && firstAction(n, "Attack") != null)
                .within(AREA).nearestOnClientThread();
    }

    private static String firstAction(Rs2NpcModel npc, String... wanted) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition c = npc.getNpc() == null ? null : npc.getNpc().getTransformedComposition();
            if (c == null || c.getActions() == null) return null;
            List<String> has = Arrays.asList(c.getActions());
            for (String w : wanted) if (has.contains(w)) return w;
            return null;
        }).orElse(null);
    }

    private String converse(List<String> options) {
        StringBuilder read = new StringBuilder();
        BreakHandlerScript.setLockState(true);
        try {
            for (int i = 0; i < MAX_DIALOGUE_STEPS; i++) {
                // cutscenes pause between lines
                if (!Global.sleepUntil(Rs2Dialogue::isInDialogue, 6000)) break;
                if (Rs2Dialogue.hasSelectAnOption()) {
                    String pick = options.stream().filter(Rs2Dialogue::hasDialogueOption).findFirst().orElse(null);
                    if (pick == null) {
                        Dialogs.close();
                        break;
                    }
                    Global.sleep((int) pace.reactionMs(random));
                    if (!Rs2Dialogue.clickOption(pick)) break;
                    read.append(pick).append(' ');
                    Global.sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption(), 3000);
                    continue;
                }
                String text = Rs2Dialogue.getDialogueText();
                if (text == null) break;
                read.append(text).append(' ');
                Global.sleep((int) pace.readMs(text, random));
                Rs2Dialogue.clickContinue();
                Global.sleepUntil(() -> !Rs2Dialogue.isInDialogue() || !text.equals(Rs2Dialogue.getDialogueText()), 4000);
            }
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        String all = plain(read.toString());
        log.info("Account Starter: quest {}: {}", step, all.length() > 200 ? all.substring(0, 200) + "..." : all);
        return all;
    }

    static boolean outlawDone(Step step, String read) {
        switch (step) {
            case RANGER:
                return read.contains("making them dead") || read.contains("next thief by ruined building");
            case MAGE:
                return read.contains("another nasty thief go dead");
            case WARRIOR:
                return read.contains("last of those thieves") || read.contains("crossed mudfoot") || read.contains("go tell guard man");
            default:
                return false;
        }
    }

    /** Dialogue text holds line breaks that read as spaces ("grab<br>poker"). */
    static String plain(String text) {
        return text.replace("<br>", " ").toLowerCase(Locale.ENGLISH);
    }

    private Outcome heard(String read, String who) {
        boolean same = read.equals(lastRead);
        lastRead = read;
        if (same) return fail(who + " said the same again");
        failures = 0;
        return Outcome.cont();
    }

    private Outcome walkTo(WorldPoint to, String place) {
        walkPlace = place;
        walking = true;
        try {
            if (Rs2Walker.walkTo(to, 3)) return Outcome.cont();
        } finally {
            walking = false;
        }
        return fail("could not reach " + place);
    }

    private void next() {
        save(Step.values()[Math.min(step.ordinal() + 1, Step.values().length - 1)]);
    }

    private void save(Step to) {
        if (to == step) return;
        log.info("Account Starter: quest step {} -> {}", step, to);
        step = to;
        failures = 0;
        lootFails = 0;
        noOutlawSince = 0;
        fetches = 0;
        saveStep.accept(to.ordinal());
    }

    private Outcome fail(String why) {
        log.info("Account Starter: quest: {}", why);
        return ++failures >= MAX_FAILURES ? Outcome.parked("quest: " + why) : Outcome.cont();
    }
}
