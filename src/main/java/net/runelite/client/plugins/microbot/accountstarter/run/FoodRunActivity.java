package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPCComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.FreeSource;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public final class FoodRunActivity extends WalkingActivity {
    public enum Step { FISH, COOK, DONE, FULL, OUT_OF_TRIES }

    public enum Method {
        NET("Net", 1, 1, 15, 1),
        BAIT("Bait", 5, 1, 10, 5),
        FLY("Lure", 20, 15, 30, 25);

        final String action;
        final int fish, cook, fish2, cook2;

        Method(String action, int fish, int cook, int fish2, int cook2) {
            this.action = action;
            this.fish = fish;
            this.cook = cook;
            this.fish2 = fish2;
            this.cook2 = cook2;
        }

        boolean fits(int fishing, int cooking) {
            return fishing >= fish && cooking >= cook && (fishing < fish2 || cooking >= cook2);
        }
    }

    static final int MIN_BAIT = 20;
    static final List<Integer> GEAR = List.of(ItemID.NET, ItemID.FISHING_ROD, ItemID.FISHING_BAIT, ItemID.FLY_FISHING_ROD, ItemID.FEATHER);
    static final List<WorldPoint> SAFE_TILES = List.of(new WorldPoint(3241, 3197, 0), new WorldPoint(3222, 3217, 0));
    /** Raw fish whose cooked form is food: cooked anchovies heal 1 and are not in Needs.FOOD. */
    public static final Map<Integer, Integer> RAW_FOOD = SideTraining.RAW_FISH.entrySet().stream()
            .filter(e -> e.getKey() != ItemID.RAW_ANCHOVIES).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    static final int FAR_TILES = 150;
    private static final int MAX_REFUSED = 4;
    private static final int ROOF_REACH = 10;
    /** A fire lasts 60-119 s: the spare log covers one burning out mid-batch. */
    private static final int LOGS_TAKEN = 2;
    private static final int FREE_LOG_TRIES = 3;
    private static final int FIRE_REACH = 6;
    /** Level 1 lights about one try in four, and the game keeps trying by itself. */
    private static final int LIGHT_MS = 40_000;

    /** The kitchen floor beside the Cook-o-matic 100, which requires Cook's Assistant. */
    static final WorldPoint RANGE_TILE = new WorldPoint(3211, 3215, 0);
    private static final int RANGE_REACH = 8;
    private static final int MAX_FAILURES = 3;
    private static final int MAX_TRIPS = 3;
    private static final long SEARCH_MS = 10_000;
    private static final int TREE_REACH = 12;
    private static final int BUSY_GAP_MS = 1800;
    /** A fish takes about 2.4 s: longer with no inventory change means the batch stopped. */
    private static final int NEXT_FISH_MS = 3600;

    private final ApTask task;
    private final Choices choices;
    private final Supplier<AccountSnapshot> snapshots;
    private final ApPace pace;
    private final Random random;
    private final long spotSeed;
    private final int minStock;

    private Kit kit;
    private final Kit tinderKit;
    private volatile boolean questDone;
    private int refusedTiles;
    private int safeTile;
    private final List<WorldPoint> safeTiles = new ArrayList<>(SAFE_TILES);
    private int walkFails;
    private boolean fishStarted;
    private int freeLogTries;
    private Spot spot;
    private final Set<String> triedSpots = new HashSet<>();
    private long searchStart;
    private Step last;
    private int trips;
    private boolean cooking;
    private boolean rangeBlocked;
    private int rangeFails;
    private int failures;
    private volatile Step phase = Step.FISH;
    private volatile Method fishing = Method.NET;
    private volatile int fishingLevel = 1;
    private volatile int cookingLevel = 1;

    public FoodRunActivity(ApTask task, Choices choices, Supplier<AccountSnapshot> snapshots, ApPace pace, Random random, long spotSeed) {
        this(task, choices, snapshots, pace, random, spotSeed, 0);
    }

    public FoodRunActivity(ApTask task, Choices choices, Supplier<AccountSnapshot> snapshots, ApPace pace, Random random, long spotSeed, int minStock) {
        this.task = task;
        this.choices = choices;
        this.snapshots = snapshots;
        this.pace = pace;
        this.random = random;
        this.spotSeed = spotSeed;
        this.minStock = minStock;
        Collections.shuffle(safeTiles, random);
        this.kit = new Kit(ApTask.FISHING_5, choices, snapshots, pace, random, this::carry);
        // Wilfred is the Woodcutting tutor, so his kit is the one that walks to him
        this.tinderKit = new Kit(ApTask.WOODCUTTING_5, choices, snapshots, pace, random);
    }

    public static Step step(int cooked, int raw, int freeSlots, int stock, int extra, int trips, int maxTrips) {
        if (raw > 0 && (cooked >= stock || cooked + raw >= stock + extra || freeSlots == 0)) return Step.COOK;
        if (cooked >= stock) return Step.DONE;
        if (trips >= maxTrips) return Step.OUT_OF_TRIES;
        if (freeSlots == 0) return Step.FULL;
        return Step.FISH;
    }

    public static Method method(int fishing, int cooking, boolean baitGear) {
        return baitGear && Method.BAIT.fits(fishing, cooking) ? Method.BAIT : Method.NET;
    }

    public static Method method(int fishing, int cooking, boolean baitGear, boolean flyGear) {
        return flyGear && Method.FLY.fits(fishing, cooking) ? Method.FLY : method(fishing, cooking, baitGear);
    }

    static Method method(AccountSnapshot s, int fishing, int cooking) {
        return method(fishing, cooking, s.owned(ItemID.FISHING_ROD) > 0 && s.owned(ItemID.FISHING_BAIT) >= MIN_BAIT,
                s.owned(ItemID.FLY_FISHING_ROD) > 0 && s.owned(ItemID.FEATHER) >= MIN_BAIT);
    }

    public static Method kept(Method current, Method fresh, boolean started, int fishing, int cooking, boolean tool, int supply) {
        return started && current != Method.NET && tool && supply > 0 && current.fits(fishing, cooking) ? current : fresh;
    }

    public static boolean homeTeleportOk(int tilesFromLumbridge, int walkFails) {
        return tilesFromLumbridge > FAR_TILES && walkFails > 0;
    }

    public static List<String> trees(int woodcutting, int firemaking) {
        List<String> out = new ArrayList<>(List.of("Tree", "Dead tree"));
        if (woodcutting >= 15 && firemaking >= 15) out.add("Oak tree");
        if (woodcutting >= 30 && firemaking >= 30) out.add("Willow tree");
        return out;
    }

    public static int fireLog(Map<Integer, Integer> held, int firemaking) {
        return SideTraining.firstUsable(held, SideTraining.LOGS, firemaking);
    }

    /** About half burn at Cooking 3-6. */
    public static int extra(int cookingLevel, int stock) {
        return cookingLevel < 20 ? stock : 2;
    }

    @Override
    public String describe() {
        return phase == Step.COOK ? "Cooking food (Cooking " + cookingLevel + ")"
                : "Catching food: " + fishing.action + " (Fishing " + fishingLevel + ")";
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        // the walker counts Home Teleport as a shortcut even for a short trip
        boolean before = Rs2Walker.disableTeleports;
        Rs2Walker.disableTeleports = before || !homeTeleportOk(s.getLocation().distanceTo2D(Spots.LUMBRIDGE_BANK), walkFails);
        try {
            return run(s);
        } finally {
            Rs2Walker.disableTeleports = before;
        }
    }

    private Outcome run(AccountSnapshot s) {
        fishingLevel = s.getLevels().getOrDefault(Skill.FISHING, 1);
        cookingLevel = s.getLevels().getOrDefault(Skill.COOKING, 1);
        boolean fly = fishing == Method.FLY;
        fishing = kept(fishing, method(s, fishingLevel, cookingLevel), fishStarted, fishingLevel, cookingLevel,
                s.getInventory().containsKey(fly ? ItemID.FLY_FISHING_ROD : ItemID.FISHING_ROD),
                s.getInventory().getOrDefault(fly ? ItemID.FEATHER : ItemID.FISHING_BAIT, 0));
        boolean canFish = XpGuard.allowed(Action.FISH, choices.getProtectedSkills());
        if (kit == null && canFish && fishing == Method.NET && !s.getInventory().containsKey(ItemID.NET)) {
            kit = new Kit(ApTask.FISHING_5, choices, snapshots, pace, random, this::carry);
        }
        // read direct: Rs2Player's quest cache can go stale
        questDone = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Quest.COOKS_ASSISTANT.getState(Microbot.getClient()) == QuestState.FINISHED).orElse(false);
        if (!XpGuard.allowed(Action.COOK, choices.getProtectedSkills())) return Outcome.parked("no food: Cooking protected");
        if (kit != null) {
            List<Need> needs = canFish && fishing == Method.NET ? Needs.forTask(ApTask.FISHING_5, s) : List.of();
            Outcome o = whileWalking(null, () -> kit.prepare(needs, keep(s)));
            if (o.getKind() != Outcome.Kind.DONE) return o;
            kit = null;
        }
        if (fireNeeded() && s.owned(ItemID.TINDERBOX) == 0) {
            Outcome o = whileWalking("Wilfred", () -> tinderKit.prepare(wilfredNeeds(s), keep(s)));
            if (o.getKind() != Outcome.Kind.DONE) return o;
        }
        if (Rs2Dialogue.hasContinue()) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Dialogue.clickContinue();
            return Outcome.cont();
        }
        Rs2NpcModel attacker = Attackers.onUs(-1);
        if (attacker != null) return attacked(attacker);
        int cooked = Needs.edible(s);
        int raw = raw(s);
        int stock = stock(s);
        int extra = extra(cookingLevel, stock);
        Step st = cooking && raw > 0 ? Step.COOK : step(cooked, raw, s.getFreeSlots(), stock, extra, trips, MAX_TRIPS);
        if (st == Step.FISH && last == Step.COOK) {
            trips++;
            st = step(cooked, raw, s.getFreeSlots(), stock, extra, trips, MAX_TRIPS);
        }
        if (st == Step.FISH && !canFish) {
            if (raw == 0) return Outcome.parked("no food: Fishing protected and no raw fish banked");
            st = Step.COOK;
        }
        last = st;
        if (st != Step.COOK && Rs2Inventory.contains(FoodRunActivity::burnt)) {
            Rs2Inventory.dropAll(FoodRunActivity::burnt);
            return Outcome.cont();
        }
        switch (st) {
            case DONE:
                return Outcome.done("caught and cooked " + cooked + " food");
            case FULL:
                return Outcome.parked("inventory full before " + stock + " food was cooked");
            case OUT_OF_TRIES:
                return Outcome.parked("could not catch and cook " + stock + " food in " + MAX_TRIPS + " trips");
            case COOK:
                phase = Step.COOK;
                if (cooking && Rs2Inventory.waitForInventoryChanges(NEXT_FISH_MS)) return Outcome.cont();
                return cook(s);
            default:
                phase = Step.FISH;
                cooking = false;
                if ((!questDone || rangeBlocked) && !XpGuard.allowed(Action.LIGHT_FIRE, choices.getProtectedSkills())) {
                    return Outcome.parked("no food: no range and Firemaking protected");
                }
                if (fishingBusy()) return Outcome.cont();
                if (needsRoofLog(s)) return freeLog();
                return fish(s, System.currentTimeMillis());
        }
    }

    private Outcome fish(AccountSnapshot s, long now) {
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        if (spot != null && (fishing == Method.FLY) != (spot == Spots.LUMBRIDGE_RIVER)) spot = null;
        if (spot == null) {
            List<Spot> usable = fishing == Method.FLY ? List.of(Spots.LUMBRIDGE_RIVER) : Spots.usable(ApTask.FISHING_5, fishingLevel, false);
            spot = GatherActivity.pickSpot(usable, spotSeed, random, triedSpots);
            searchStart = 0;
            if (spot == null) {
                triedSpots.clear();
                return fail("no fishing spot at the swamp shore");
            }
        }
        Set<WorldPoint> walkable = Reach.tiles();
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withNames(spot.getTargets().toArray(new String[0]))
                .where(n -> action(n) != null && Reach.touches(new WorldArea(n.getWorldLocation(), 1, 1), walkable))
                .within(spot.getCenter(), spot.getRadius() + 4).nearestOnClientThread();
        if (npc != null) {
            String op = Microbot.getClientThread().runOnClientThreadOptional(() -> action(npc)).orElse(null);
            int dist = s.getLocation().distanceTo2D(npc.getWorldLocation());
            Global.sleep((int) pace.reactionMs(random));
            searchStart = 0;
            if (op != null && npc.click(op) && Global.sleepUntil(this::fishingBusy, Math.min(8000, 1800 + 600 * dist))) {
                failures = 0;
                fishStarted = true;
                return Outcome.cont();
            }
            return fail("the fishing spot did not respond");
        }
        if (s.getLocation().distanceTo2D(spot.getCenter()) > spot.getRadius() + 2) {
            return walkOrFail(spot.getCenter(), spot.getRadius(), spot.getName());
        }
        if (searchStart == 0) {
            searchStart = now;
            return Outcome.cont();
        }
        if (now - searchStart >= SEARCH_MS) {
            triedSpots.add(spot.getName());
            spot = null;
        }
        return Outcome.cont();
    }

    private String action(Rs2NpcModel npc) {
        if (fishing == Method.NET) return GatherActivity.netAction(npc);
        NPCComposition c = npc.getNpc() == null ? null : npc.getNpc().getTransformedComposition();
        if (c == null || c.getActions() == null) return null;
        return Arrays.stream(c.getActions()).filter(fishing.action::equalsIgnoreCase).findFirst().orElse(null);
    }

    public enum CookAt { RANGE, FIRE_NEAR, ROOF_LOG, LIGHT_FIRE, FREE_LOGS, CHOP_LOGS, NOWHERE }

    public static CookAt cookAt(boolean cooksAssistantDone, boolean rangeBlocked, boolean fireNear, boolean roofLog, boolean canLight,
                                boolean freeLogs, boolean canChop) {
        if (fireNear) return CookAt.FIRE_NEAR;
        if (roofLog) return CookAt.ROOF_LOG;
        if (canLight) return CookAt.LIGHT_FIRE;
        if (cooksAssistantDone && !rangeBlocked) return CookAt.RANGE;
        if (canChop) return CookAt.CHOP_LOGS;
        return freeLogs ? CookAt.FREE_LOGS : CookAt.NOWHERE;
    }

    private Outcome cook(AccountSnapshot s) {
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        // fires only: the range sits close to the courtyard and refuses anyone before the quest
        Set<WorldPoint> walkable = Reach.tiles();
        Rs2TileObjectModel near = Microbot.getRs2TileObjectCache().query().ofType(TileObjectType.GAME).withName("Fire")
                .where(o -> Reach.touches(new WorldArea(o.getWorldLocation(), 1, 1), walkable)).nearestOnClientThread(FIRE_REACH);
        boolean lighter = lighter(s);
        Rs2TileItemModel roofLog = lighter ? roofLog(s) : null;
        switch (cookAt(questDone, rangeBlocked, near != null, roofLog != null, canLightFire(s), lighter && freeLogTries < FREE_LOG_TRIES, canChop(s))) {
            case FIRE_NEAR:
                return cookOn(near, s);
            case ROOF_LOG:
                return lightRoofLog(roofLog);
            case LIGHT_FIRE:
                return lightFire(s);
            case FREE_LOGS:
                return freeLog();
            case CHOP_LOGS:
                return chop();
            case NOWHERE:
                return Outcome.parked("no range and no way to light a fire (needs a tinderbox and logs, or an axe)");
            default:
                break;
        }
        Rs2TileObjectModel at = null;
        if (!rangeBlocked) {
            at = range();
            if (at == null) {
                boolean reached = walk(RANGE_TILE, 3, 5, "the Lumbridge Castle kitchen");
                if ((!reached || range() == null) && ++rangeFails >= MAX_FAILURES) {
                    log.info("Account Starter: food: could not reach the castle range; cooking on a fire");
                    rangeBlocked = true;
                }
                return Outcome.cont();
            }
        }
        if (at == null) return Outcome.cont();
        return cookOn(at, s);
    }

    private Outcome cookOn(Rs2TileObjectModel at, AccountSnapshot s) {
        int rawId = SideTraining.firstUsable(s.getInventory(), RAW_FOOD, cookingLevel);
        String why;
        BreakHandlerScript.setLockState(true);
        try {
            why = GatherActivity.cookOn(at, rawId, pace, random);
        } finally {
            BreakHandlerScript.setLockState(false);
        }
        if (why != null) {
            Rs2Inventory.deselect();
            return fail(why);
        }
        cooking = true;
        failures = 0;
        return Outcome.cont();
    }

    private boolean lighter(AccountSnapshot s) {
        return s.getInventory().containsKey(ItemID.TINDERBOX) && XpGuard.allowed(Action.LIGHT_FIRE, choices.getProtectedSkills());
    }

    private boolean canLightFire(AccountSnapshot s) {
        return lighter(s) && fireLog(s.getInventory(), s.getLevels().getOrDefault(Skill.FIREMAKING, 1)) != 0;
    }

    private boolean needsRoofLog(AccountSnapshot s) {
        return fireNeeded() && lighter(s) && !canLightFire(s) && !canChop(s) && freeLogTries < FREE_LOG_TRIES;
    }

    private Rs2TileItemModel roofLog(AccountSnapshot s) {
        if (s.getLocation().getPlane() != Spots.LUMBRIDGE_BANK.getPlane()) return null;
        Set<WorldPoint> walkable = Reach.tiles();
        return Microbot.getRs2TileItemCache().query()
                .where(i -> i.getId() == ItemID.LOGS && i.getWorldLocation() != null && walkable.contains(i.getWorldLocation())
                        && i.getWorldLocation().distanceTo2D(s.getLocation()) <= ROOF_REACH)
                .nearestOnClientThread();
    }

    /** Lit where it lies, tinderbox only: about 5 s. */
    private Outcome lightRoofLog(Rs2TileItemModel log) {
        int before = GatherActivity.xp(Skill.FIREMAKING);
        Global.sleep((int) pace.reactionMs(random));
        if (log.click("Light") && Global.sleepUntil(() -> GatherActivity.xp(Skill.FIREMAKING) > before, LIGHT_MS)) return Outcome.cont();
        return fail("could not light a log on the roof");
    }

    /** Wilfred gives a tinderbox and a bronze axe free at any level, both in the one visit. */
    private List<Need> wilfredNeeds(AccountSnapshot s) {
        List<Need> out = new ArrayList<>();
        out.add(new Need("a tinderbox", Set.of(ItemID.TINDERBOX), 1, FreeSource.WOODCUTTING_TUTOR, 0, ""));
        if (GearPlan.bestAxe(s).isEmpty() && XpGuard.allowed(Action.CHOP, choices.getProtectedSkills())) {
            out.add(new Need("an axe", Set.of(ItemID.BRONZE_AXE), 1, FreeSource.WOODCUTTING_TUTOR, 0, ""));
        }
        return out;
    }

    private static boolean burnt(Rs2ItemModel item) {
        return item.getId() == ItemID.RAW_ANCHOVIES || (item.getName() != null && item.getName().startsWith("Burnt"));
    }

    private boolean canChop(AccountSnapshot s) {
        // an axe chops from the pack or wielded
        return s.getInventory().containsKey(ItemID.TINDERBOX) && GearPlan.bestAxe(s).isPresent()
                && (s.getInventory().containsKey(GearPlan.bestAxe(s).getAsInt()) || s.getEquipment().containsKey(GearPlan.bestAxe(s).getAsInt()))
                && XpGuard.allowed(Action.CHOP, choices.getProtectedSkills())
                && XpGuard.allowed(Action.LIGHT_FIRE, choices.getProtectedSkills());
    }

    /** The bank floor has four log spawns: a fresh account needs no axe. */
    private Outcome freeLog() {
        freeLogTries++;
        if (!walk(Spots.LUMBRIDGE_BANK, 4, 8, "the logs on the castle's top floor")) return Outcome.cont();
        Set<WorldPoint> walkable = Reach.tiles();
        List<Rs2TileItemModel> logs = Microbot.getRs2TileItemCache().query()
                .where(i -> i.getId() == ItemID.LOGS && i.getWorldLocation() != null && walkable.contains(i.getWorldLocation()))
                .toListOnClientThread();
        if (logs.isEmpty()) return Outcome.cont();
        int before = Rs2Inventory.itemQuantity(ItemID.LOGS);
        Global.sleep((int) pace.reactionMs(random));
        if (logs.get(0).pickup()) Global.sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.LOGS) > before, 5000);
        return Outcome.cont();
    }

    private Outcome chop() {
        Set<WorldPoint> walkable = Reach.tiles();
        AccountSnapshot now = snapshots.get();
        List<String> names = trees(now.getLevels().getOrDefault(Skill.WOODCUTTING, 1), now.getLevels().getOrDefault(Skill.FIREMAKING, 1));
        Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query().ofType(TileObjectType.GAME).withNames(names.toArray(new String[0]))
                .withAction("Chop down").where(o -> Reach.touches(o.getWorldArea(), walkable)).nearestOnClientThread(TREE_REACH);
        if (tree == null) {
            Spot trees = Spots.usable(ApTask.WOODCUTTING_5, 1, false).get(0);
            if (Rs2Player.getWorldLocation().distanceTo2D(trees.getCenter()) <= trees.getRadius()) return fail("no tree near to chop logs for a fire");
            return walkOrFail(trees.getCenter(), trees.getRadius(), trees.getName());
        }
        int before = logsHeld();
        Global.sleep((int) pace.reactionMs(random));
        if (tree.click("Chop down") && Global.sleepUntil(() -> logsHeld() > before, 20_000)) {
            return Outcome.cont();
        }
        return fail("could not chop logs for a fire");
    }

    private static int logsHeld() {
        return SideTraining.LOGS.keySet().stream().mapToInt(Rs2Inventory::itemQuantity).sum();
    }

    private boolean fireNeeded() {
        return (!questDone || rangeBlocked) && XpGuard.allowed(Action.LIGHT_FIRE, choices.getProtectedSkills());
    }

    private int rawShort(AccountSnapshot s) {
        return Math.max(0, stock(s) + extra(cookingLevel, stock(s)) - Needs.edible(s) - raw(s));
    }

    private List<Integer> carry(AccountSnapshot s) {
        List<Integer> out = new ArrayList<>(GEAR);
        int rawShort = rawShort(s);
        for (Map.Entry<Integer, Integer> e : RAW_FOOD.entrySet()) {
            if (cookingLevel < e.getValue()) continue;
            int take = Math.min(rawShort, s.getBank().getOrDefault(e.getKey(), 0));
            out.addAll(Collections.nCopies(take, e.getKey()));
            rawShort -= take;
        }
        if (!fireNeeded()) return out;
        out.add(ItemID.TINDERBOX);
        int logId = fireLog(GearPlan.ownedMap(s), s.getLevels().getOrDefault(Skill.FIREMAKING, 1));
        if (logId != 0) out.addAll(Collections.nCopies(LOGS_TAKEN, logId));
        else if (XpGuard.allowed(Action.CHOP, choices.getProtectedSkills())) GearPlan.bestAxe(s).ifPresent(out::add);
        return out;
    }

    /** No lighting animation means the tile refused, so it steps aside. */
    private Outcome lightFire(AccountSnapshot s) {
        int logId = fireLog(s.getInventory(), s.getLevels().getOrDefault(Skill.FIREMAKING, 1));
        int before = GatherActivity.xp(Skill.FIREMAKING);
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.combine(ItemID.TINDERBOX, logId) || !Global.sleepUntil(Rs2Player::isAnimating, 2400)) {
            Rs2Inventory.deselect();
            return refused(s);
        }
        if (Global.sleepUntil(() -> GatherActivity.xp(Skill.FIREMAKING) > before, LIGHT_MS)) return Outcome.cont();
        return fail("could not light a fire to cook on");
    }

    private Outcome refused(AccountSnapshot s) {
        log.info("Account Starter: food: no fire allowed at {}", s.getLocation());
        if (++refusedTiles >= MAX_REFUSED) {
            refusedTiles = 0;
            return moveToSafer("no tile here took a fire");
        }
        List<WorldPoint> aside = new ArrayList<>(Reach.tiles());
        aside.removeIf(t -> t.distanceTo2D(s.getLocation()) < 2 || t.distanceTo2D(s.getLocation()) > 4);
        if (aside.isEmpty()) return moveToSafer("no clear tile to step to");
        walk(aside.get(random.nextInt(aside.size())), 0, 1, "a clear tile");
        return Outcome.cont();
    }

    private Outcome attacked(Rs2NpcModel attacker) {
        String name = attacker.getName();
        if (Attackers.fightBack(attacker, choices.getProtectedSkills(), pace, random)) {
            // the attack click ended any batch: cook() starts it again once the attacker is dead
            cooking = false;
            return Outcome.cont();
        }
        if (phase == Step.COOK) {
            cooking = false;
            return moveToSafer("a " + name + " attacked while cooking");
        }
        whileWalking("away from the " + name, () -> Attackers.escape(attacker, pace, random));
        return fail("a " + name + " kept attacking");
    }

    private Outcome moveToSafer(String why) {
        log.info("Account Starter: food: {}", why);
        if (safeTile >= safeTiles.size()) return Outcome.parked("food run: " + why + ", no safer place left");
        return walkOrFail(safeTiles.get(safeTile++), 2, "a safer place to cook");
    }

    private static Rs2TileObjectModel range() {
        return Microbot.getRs2TileObjectCache().query().ofType(TileObjectType.GAME).withId(ObjectID.COOKSQUESTRANGE)
                .nearestOnClientThread(RANGE_REACH);
    }

    private int raw(AccountSnapshot s) {
        Map<Integer, Integer> inv = s.getInventory();
        return RAW_FOOD.entrySet().stream().filter(e -> cookingLevel >= e.getValue())
                .mapToInt(e -> inv.getOrDefault(e.getKey(), 0) - s.getNotedInventory().getOrDefault(e.getKey(), 0)).sum();
    }

    private int stock(AccountSnapshot s) {
        return stock(task, s, minStock);
    }

    public static int stock(ApTask task, AccountSnapshot s, int minStock) {
        return Math.max(minStock, Needs.forTask(task, s).stream().filter(n -> n.getAnyOf().equals(Needs.FOOD)).mapToInt(Need::getQuantity)
                .findFirst().orElse(Needs.smallStock(s.getFoodStock())));
    }

    private Set<Integer> keep(AccountSnapshot s) {
        Set<Integer> keep = new HashSet<>(Needs.FOOD);
        keep.addAll(RAW_FOOD.keySet());
        keep.addAll(SideTraining.LOGS.keySet());
        keep.add(ItemID.TINDERBOX);
        keep.addAll(GEAR);
        keep.add(ItemID.NET);
        GearPlan.bestAxe(s).ifPresent(keep::add);
        keep.addAll(List.of(ItemID.BRONZE_ARROW, ItemID.IRON_ARROW, GearPlan.TRAINING_ARROWS));
        for (Need n : Needs.forTask(task, s)) keep.addAll(n.getAnyOf());
        return keep;
    }

    private boolean fishingBusy() {
        return Rs2Player.isAnimating(BUSY_GAP_MS) || Rs2Player.isMoving() || Rs2Player.isInteracting();
    }

    private boolean walk(WorldPoint to, int distance, int arriveWithin, String place) {
        return whileWalking(place, () -> {
            if (Rs2Walker.walkTo(to, distance)) return true;
            WorldPoint at = Rs2Player.getWorldLocation();
            if (at != null && at.getPlane() == to.getPlane() && at.distanceTo(to) <= arriveWithin) return true;
            walkFails++;
            return false;
        });
    }

    private Outcome walkOrFail(WorldPoint to, int arriveWithin, String place) {
        return walk(to, 2, arriveWithin, place) ? Outcome.cont() : fail("could not reach " + place);
    }

    private Outcome fail(String reason) {
        log.info("Account Starter: food: {}", reason);
        return ++failures >= MAX_FAILURES ? Outcome.parked("food run: " + reason) : Outcome.cont();
    }
}
