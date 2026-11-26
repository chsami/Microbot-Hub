package net.runelite.client.plugins.microbot.accountstarter.run;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Habits;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.accountstarter.run.SideTraining.Next;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public final class GatherActivity extends WalkingActivity {
    public static final String UNMARKED = "task complete but the game has not marked it; check Adventure Paths";
    static final long UNMARKED_MS = 120_000;
    private static final long SEARCH_MS = 10_000;
    private static final int MAX_REPICKS = 3;
    private static final int MAX_FAILURES = 5;
    private static final int MAX_BANK_VISITS = 8;
    private static final int MAX_COOK_FAILS = 3;
    private static final long SHARE_AFTER_MS = 20_000;
    private static final int BUSY_GAP_MS = 1800;
    private static final int COOK_REACH = 6;
    private static final double FAVOURITE_CHANCE = 0.75;
    private static final Set<String> NET_ACTIONS = Set.of("net", "small net");
    private static final String[] COOK_TARGETS = {"Fire", "Range", "Cooking range"};

    private final ApTask task;
    private final Skill skill;
    private final Action gatherAction;
    private final Choices choices;
    private final Supplier<AccountSnapshot> snapshots;
    private final ApPace pace;
    private final Random random;
    private final Habits habits;
    private final long spotSeed;

    private Kit kit;
    private int bankVisits;
    private Spot spot;
    private final Set<String> triedSpots = new HashSet<>();
    private final Set<WorldPoint> refused = new HashSet<>();
    private int repicks;
    private long searchStart;
    private int lastLevel = -1;
    private long reachedAt;
    private long idleSince;
    private int bankAt = -1;
    private long idleWait;
    private Next side;
    private long allTakenSince;
    private WorldPoint anchor;
    private boolean cookBlocked;
    private int cookFails;
    private int failures;

    public GatherActivity(ApTask task, Choices choices, Supplier<AccountSnapshot> snapshots, ApPace pace, Random random,
                          Habits habits, long spotSeed) {
        if (task.path() != ApPath.GATHERER) {
            throw new IllegalArgumentException("GatherActivity cannot run " + task);
        }
        this.task = task;
        this.skill = task.skill();
        this.gatherAction = actionFor(skill);
        this.choices = choices;
        this.snapshots = snapshots;
        this.pace = pace;
        this.random = random;
        this.habits = habits;
        this.spotSeed = spotSeed;
        this.kit = newKit();
    }

    @Override
    public String describe() {
        return "Training " + skill.getName() + " to " + task.target();
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        if (s.getProgress().of(task) != TaskState.NOT_DONE) {
            return Outcome.done(task.name() + " marked complete");
        }
        int level = s.getLevels().getOrDefault(skill, 1);
        if (lastLevel < 0) lastLevel = level;
        if (level > lastLevel) {
            lastLevel = level;
            return Outcome.natural(skill.getName() + " level " + level);
        }
        long now = System.currentTimeMillis();
        if (unmarkedTooLong(level, task.target(), now)) {
            return Outcome.parked(UNMARKED);
        }
        if (!XpGuard.allowed(gatherAction, choices.getProtectedSkills())) {
            return Outcome.parked(skill.getName() + " is protected");
        }
        if (kit != null) {
            Outcome o = whileWalking(null, () -> kit.prepare(Needs.forTask(task, s), keep(s)));
            if (o.getKind() != Outcome.Kind.DONE) return o;
            kit = null;
            spot = null;
        }
        if (Rs2Dialogue.hasContinue()) {
            Global.sleep((int) pace.levelUpMs(random, habits.getGlanceAfterLevel()));
            Rs2Dialogue.clickContinue();
            return Outcome.cont();
        }
        Rs2NpcModel attacker = Attackers.onUs(-1);
        if (attacker != null) {
            if (Attackers.fightBack(attacker, choices.getProtectedSkills(), pace, random)) return Outcome.cont();
            String name = attacker.getName();
            whileWalking("away from the " + name, () -> Attackers.escape(attacker, pace, random));
            return fail("a " + name + " kept attacking");
        }
        if (busy()) {
            idleSince = 0;
            return Outcome.cont();
        }
        if (idleSince == 0) {
            idleSince = now;
            boolean gatherNext = spot != null && side == null && s.getFreeSlots() > Math.max(0, bankAt);
            idleWait = gatherNext ? pace.reclickMs(random) : pace.reactionMs(random);
            return Outcome.cont();
        }
        if (now - idleSince < idleWait) return Outcome.cont();
        idleSince = 0;
        if (side != null && sideStillApplies(side, s)) {
            return doSide(side, s);
        }
        side = null;
        if (bankAt < 0 || s.getFreeSlots() > 12) bankAt = Math.max(0, habits.getBankAtFreeSlots() + random.nextInt(3) - 1);
        int free = Math.max(0, s.getFreeSlots() - bankAt);
        if (free == 0) {
            Next n = nextWhenFull(s);
            if (n == Next.BANK) return bank();
            side = n;
            return doSide(n, s);
        }
        return gather(s, now);
    }

    /** A rock gives one ore, so never take one another player is mining. */
    private Rs2TileObjectModel pickRock(List<Rs2TileObjectModel> rocks, WorldPoint me, long now) {
        if (rocks.isEmpty() || me == null) return null;
        List<WorldPoint> miners = otherMiners();
        List<WorldPoint> rockTiles = rocks.stream().map(Rs2TileObjectModel::getWorldLocation).collect(Collectors.toList());
        if (anchor == null || miners.contains(anchor)) {
            anchor = bestAnchor(rockTiles, t -> Rs2Tile.isWalkable(t), miners, me, random);
        }
        if (anchor != null && !me.equals(anchor)) {
            walkPlace = "a mining spot";
            Rs2Walker.walkFastCanvas(anchor);
            Global.sleepUntil(() -> anchor.equals(Rs2Player.getWorldLocation()), 3000);
            return null;
        }
        List<Rs2TileObjectModel> free = rocks.stream().filter(r -> !occupied(r.getWorldLocation(), miners))
                .filter(r -> anchor == null || adjacent(anchor, r.getWorldLocation()))
                .sorted(Comparator.comparingInt(r -> r.getWorldLocation().distanceTo(me))).limit(3).collect(Collectors.toList());
        if (!free.isEmpty()) {
            allTakenSince = 0;
            return free.get(random.nextInt(free.size()));
        }
        if (allTakenSince == 0) allTakenSince = now;
        if (now - allTakenSince > SHARE_AFTER_MS && random.nextDouble() < habits.getLootSkip()) {
            allTakenSince = 0;
            return nearest(rocks, me);
        }
        return null;
    }

    public static boolean adjacent(WorldPoint tile, WorldPoint rock) {
        return tile.getPlane() == rock.getPlane() && Math.abs(tile.getX() - rock.getX()) + Math.abs(tile.getY() - rock.getY()) == 1;
    }

    public static WorldPoint bestAnchor(List<WorldPoint> rocks, Predicate<WorldPoint> walkable,
                                        List<WorldPoint> players, WorldPoint me, Random r) {
        Map<WorldPoint, Integer> touching = new HashMap<>();
        for (WorldPoint rock : rocks) {
            for (int[] d : new int[][]{{0, 1}, {1, 0}, {0, -1}, {-1, 0}}) {
                WorldPoint t = new WorldPoint(rock.getX() + d[0], rock.getY() + d[1], rock.getPlane());
                touching.merge(t, 1, Integer::sum);
            }
        }
        int best = touching.entrySet().stream().filter(e -> e.getValue() >= 2 && !rocks.contains(e.getKey())
                && !players.contains(e.getKey()) && walkable.test(e.getKey())).mapToInt(Map.Entry::getValue).max().orElse(0);
        if (best < 2) return null;
        List<WorldPoint> top = touching.entrySet().stream().filter(e -> e.getValue() == best && !rocks.contains(e.getKey())
                && !players.contains(e.getKey()) && walkable.test(e.getKey())).map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(t -> t.distanceTo(me))).limit(2).collect(Collectors.toList());
        return top.get(r.nextInt(top.size()));
    }

    public static boolean occupied(WorldPoint rock, List<WorldPoint> busyPlayers) {
        if (rock == null) return false;
        for (WorldPoint p : busyPlayers) {
            if (p.getPlane() == rock.getPlane() && Math.abs(p.getX() - rock.getX()) <= 1 && Math.abs(p.getY() - rock.getY()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static List<WorldPoint> otherMiners() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player me = Microbot.getClient().getLocalPlayer();
            List<WorldPoint> at = new ArrayList<>();
            for (Player p : Microbot.getClient().getTopLevelWorldView().players()) {
                if (p != me && p.getAnimation() != -1) at.add(p.getWorldLocation());
            }
            return at;
        }).orElse(Collections.emptyList());
    }

    private static Rs2TileObjectModel nearest(List<Rs2TileObjectModel> objs, WorldPoint me) {
        if (me == null) return objs.isEmpty() ? null : objs.get(0);
        return objs.stream().min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(me))).orElse(null);
    }

    boolean unmarkedTooLong(int level, int target, long now) {
        if (level < target) {
            reachedAt = 0;
            return false;
        }
        if (reachedAt == 0) reachedAt = now;
        return now - reachedAt > UNMARKED_MS;
    }

    private Outcome gather(AccountSnapshot s, long now) {
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        if (spot == null) {
            List<Spot> usable = Spots.usable(task, s.getLevels().getOrDefault(skill, 1), skiller());
            spot = pickSpot(usable, spotSeed, random, triedSpots);
            anchor = null; // a tile beside the last spot's rocks would walk back there
            refused.clear();
            searchStart = 0;
            if (spot == null) {
                return Outcome.parked("no " + skill.getName() + " spot has anything to gather");
            }
            log.info("Account Starter: gathering at {}", spot.getName());
        }
        boolean near = s.getLocation().distanceTo2D(spot.getCenter()) <= spot.getRadius() + 2;
        int reach = spot.getRadius() + 4;
        Set<WorldPoint> walkable = Reach.tiles();
        if (skill == Skill.FISHING) {
            Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withNames(spot.getTargets().toArray(new String[0]))
                    .where(n -> netAction(n) != null && !refused.contains(n.getWorldLocation())
                            && Reach.touches(new WorldArea(n.getWorldLocation(), 1, 1), walkable))
                    .within(spot.getCenter(), reach).nearestOnClientThread();
            if (npc != null) {
                String op = Microbot.getClientThread().runOnClientThreadOptional(() -> netAction(npc)).orElse(null);
                Global.sleep((int) pace.reactionMs(random));
                return started(op != null && npc.click(op), npc.getWorldLocation(), s);
            }
        } else {
            String op = gatherAction == Action.CHOP ? "Chop down" : "Mine";
            List<Rs2TileObjectModel> found = Microbot.getRs2TileObjectCache().query().ofType(TileObjectType.GAME)
                    .withNames(spot.getTargets().toArray(new String[0])).withAction(op)
                    .where(o -> !refused.contains(o.getWorldLocation()) && Reach.touches(o.getWorldArea(), walkable))
                    .within(spot.getCenter(), reach).toListOnClientThread();
            Rs2TileObjectModel obj = gatherAction == Action.MINE ? pickRock(found, s.getLocation(), now) : nearest(found, s.getLocation());
            if (obj != null) {
                Global.sleep((int) pace.reactionMs(random));
                return started(obj.click(op), obj.getWorldLocation(), s);
            }
        }
        if (!near) {
            return walk(spot.getCenter(), 2, spot.getRadius(), spot.getName());
        }
        if (searchStart == 0) {
            searchStart = now;
            return Outcome.cont();
        }
        if (now - searchStart < SEARCH_MS) return Outcome.cont();
        triedSpots.add(spot.getName());
        spot = null;
        if (allTakenSince > 0) {
            if (triedSpots.size() >= Spots.forTask(task).size()) triedSpots.clear();
            return Outcome.cont();
        }
        if (++repicks > MAX_REPICKS) {
            return Outcome.parked("found nothing to gather at " + String.join(", ", triedSpots));
        }
        return Outcome.cont();
    }

    private Outcome started(boolean clicked, WorldPoint at, AccountSnapshot s) {
        searchStart = 0;
        int dist = s.getLocation().distanceTo2D(at);
        boolean ok = clicked && Global.sleepUntil(this::busy, Math.min(8000, 1800 + 600 * dist));
        if (ok) {
            failures = 0;
            return Outcome.cont();
        }
        refused.add(at);
        return fail("the " + skill.getName() + " target at " + at + " did not respond");
    }

    private boolean busy() {
        return Rs2Player.isAnimating(BUSY_GAP_MS) || Rs2Player.isMoving()
                || (skill == Skill.FISHING && Rs2Player.isInteracting());
    }

    private Next nextWhenFull(AccountSnapshot s) {
        Map<Integer, Integer> inv = s.getInventory();
        int ck = s.getLevels().getOrDefault(Skill.COOKING, 1);
        boolean rawFish = !cookBlocked && SideTraining.firstUsable(inv, SideTraining.RAW_FISH, ck) != 0;
        boolean cookNear = rawFish && choices.isCook() && cookTarget() != null;
        return SideTraining.plan(inv, 0, choices, ck, cookNear);
    }

    private boolean sideStillApplies(Next n, AccountSnapshot s) {
        Map<Integer, Integer> inv = s.getInventory();
        switch (n) {
            case COOK:
                return !cookBlocked && XpGuard.allowed(Action.COOK, choices.getProtectedSkills())
                        && SideTraining.firstUsable(inv, SideTraining.RAW_FISH, s.getLevels().getOrDefault(Skill.COOKING, 1)) != 0;
            case BURY:
                return XpGuard.allowed(Action.BURY, choices.getProtectedSkills())
                        && inv.keySet().stream().anyMatch(SideTraining.BONES::contains);
            default:
                return false;
        }
    }

    private Outcome doSide(Next n, AccountSnapshot s) {
        if (!sideStillApplies(n, s)) {
            side = null;
            return Outcome.cont();
        }
        switch (n) {
            case COOK: return cook(s);
            default: return bury(s);
        }
    }

    private Outcome cook(AccountSnapshot s) {
        Rs2TileObjectModel fire = cookTarget();
        int raw = SideTraining.firstUsable(s.getInventory(), SideTraining.RAW_FISH, s.getLevels().getOrDefault(Skill.COOKING, 1));
        if (fire == null) return cookFailed("no fire or range in reach");
        String why = cookOn(fire, raw, pace, random);
        return why == null ? Outcome.cont() : cookFailed(why);
    }

    static String cookOn(Rs2TileObjectModel fire, int raw, ApPace pace, Random random) {
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.use(raw) || !Global.sleepUntil(Rs2Inventory::isItemSelected, 1500)) {
            return "could not select the raw fish";
        }
        // one fish cooks at once with no menu
        if (Rs2Inventory.itemQuantity(raw) == 1) {
            return fire.click() && Global.sleepUntil(() -> Rs2Inventory.itemQuantity(raw) == 0, 5000) ? null : "the last fish did not cook";
        }
        if (!fire.click() || !Global.sleepUntil(Rs2Widget::isProductionWidgetOpen, 5000)) {
            return "the cook menu did not open";
        }
        Global.sleep((int) pace.reactionMs(random));
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        Global.sleepUntil(Rs2Player::isAnimating, 3000);
        return null;
    }

    private Outcome cookFailed(String reason) {
        log.info("Account Starter: cook: {}", reason);
        Rs2Inventory.deselect();
        if (++cookFails >= MAX_COOK_FAILS) {
            cookBlocked = true;
            side = null;
        }
        return Outcome.cont();
    }

    private Outcome bury(AccountSnapshot s) {
        int id = s.getInventory().keySet().stream().filter(SideTraining.BONES::contains).findFirst().orElse(0);
        int before = Rs2Inventory.itemQuantity(id);
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.interact(id, "Bury") || !Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id) < before, 2400)) {
            return fail("could not bury the bones");
        }
        return Outcome.cont();
    }

    private Outcome bank() {
        if (++bankVisits > MAX_BANK_VISITS) {
            return Outcome.parked("banked " + MAX_BANK_VISITS + " times without finishing " + task.name());
        }
        kit = newKit();
        return Outcome.cont();
    }

    public static Spot pickSpot(List<Spot> usable, long spotSeed, Random r, Set<String> tried) {
        List<Spot> left = usable.stream().filter(sp -> !tried.contains(sp.getName())).collect(Collectors.toList());
        if (left.isEmpty()) return null;
        List<Spot> byName = new ArrayList<>(usable);
        byName.sort(Comparator.comparing(Spot::getName));
        // SplittableRandom mixes the seed; a fresh Random gives near seeds the same first draw
        Spot favourite = byName.get(new SplittableRandom(spotSeed).nextInt(byName.size()));
        if (left.contains(favourite) && r.nextDouble() < FAVOURITE_CHANCE) return favourite;
        return left.get(r.nextInt(left.size()));
    }

    private Outcome walk(WorldPoint to, int distance, int arriveWithin, String place) {
        return whileWalking(place, () -> {
            if (Rs2Walker.walkTo(to, distance)) return Outcome.cont();
            // spot centres can be water or a busy tile: stopping inside the spot's area is arriving
            WorldPoint at = Rs2Player.getWorldLocation();
            return at != null && at.getPlane() == to.getPlane() && at.distanceTo(to) <= arriveWithin ? Outcome.cont() : fail("could not reach " + place);
        });
    }

    private Kit newKit() {
        return new Kit(task, choices, snapshots, pace, random);
    }

    private Set<Integer> keep(AccountSnapshot s) {
        Set<Integer> keep = new HashSet<>();
        for (Need n : Needs.forTask(task, s)) keep.addAll(n.getAnyOf());
        return keep;
    }

    private boolean skiller() {
        return choices.getChosen().stream().noneMatch(t -> t.path() == ApPath.COMBAT);
    }

    static Rs2TileObjectModel cookTarget() {
        return Microbot.getRs2TileObjectCache().query().ofType(TileObjectType.GAME).withNames(COOK_TARGETS)
                .nearestOnClientThread(COOK_REACH);
    }

    /** Must run on the client thread (reads the NPC composition). */
    static String netAction(Rs2NpcModel npc) {
        NPCComposition c = npc.getNpc() == null ? null : npc.getNpc().getTransformedComposition();
        if (c == null || c.getActions() == null) return null;
        return Arrays.stream(c.getActions()).filter(a -> a != null && NET_ACTIONS.contains(a.toLowerCase()))
                .findFirst().orElse(null);
    }

    static int xp(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(skill)).orElse(0);
    }

    private static Action actionFor(Skill skill) {
        switch (skill) {
            case FISHING: return Action.FISH;
            case WOODCUTTING: return Action.CHOP;
            default: return Action.MINE;
        }
    }

    private Outcome fail(String reason) {
        log.info("Account Starter: gather: {}", reason);
        return ++failures >= MAX_FAILURES ? Outcome.parked(reason) : Outcome.cont();
    }
}
