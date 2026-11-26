package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.AttackerRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.Habits;
import net.runelite.client.plugins.microbot.accountstarter.logic.LootRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.NeedResolver;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public final class CombatActivity extends WalkingActivity {
    public enum OutOfFood { BANK, FOOD_RUN, REST }

    public static final String OUT_OF_FOOD = "out of food";

    private static final long REST_MS = 6 * 60_000L;
    private static final long SHUFFLE_MIN_MS = 60_000;
    private static final long SHUFFLE_SPAN_MS = 60_000;
    private static final double NEARLY_DEAD_PERCENT = 34;
    private static final long SEARCH_MS = 10_000;
    /** A drop lands 5 ticks after death (goblins 6), on the death tile. */
    private static final long LOOT_MS = 4_800;
    private static final long LOST_MS = 3_000;
    private static final long MAX_FIGHT_MS = 90_000;
    private static final int MAX_REPICKS = 3;
    private static final int MAX_FAILURES = 5;
    private static final int MAX_BANK_VISITS = 8;
    private static final int MAX_LOOT_PICKS = 6;
    private static final int LOOT_REACH = 2;
    private static final String OUT_OF_ARROWS = "out of arrows";
    /** Own arrows land under the target where each one hit: looted this far from the kill tile. */
    private static final int ARROW_REACH = 8;
    private static final int BUSY_GAP_MS = 1800;
    private static final int TICK_MS = 600;
    private static final int PICK_AMONG = 3;
    private static final int FIGHT_CASTS = 10;
    private static final Set<Integer> REAL_ARROWS = Set.of(ItemID.BRONZE_ARROW, ItemID.IRON_ARROW);
    private static final Set<Integer> TRAINING_ARROWS = Set.of(GearPlan.TRAINING_ARROWS);
    private static final Set<Integer> ARROWS = Set.of(ItemID.BRONZE_ARROW, ItemID.IRON_ARROW, GearPlan.TRAINING_ARROWS);

    private final ApTask task;
    private final Skill skill;
    private Action style;
    private boolean styleChosen;
    private final Choices choices;
    private final Supplier<AccountSnapshot> snapshots;
    private final ApPace pace;
    private final Random random;
    private final Habits habits;
    private double eatPoint;
    private int buryAt = 2;
    private final long spotSeed;
    private final AtomicReference<String> sessionSpot;

    private Kit kit;
    private boolean styleSet;
    private int bankVisits;
    private boolean fled;
    private Spot spot;
    private List<Spot> spotChoices = List.of();
    private final Set<String> triedSpots = new HashSet<>();
    private final Set<Integer> refused = new HashSet<>();
    private int repicks;
    private long searchStart;
    private int lastLevel = -1;
    private long reachedAt;
    private Rs2NpcModel target;
    private long lastBusyAt;
    private long fightSince;
    private long lastAttackAt;
    private WorldPoint killTile;
    private WorldPoint lastTargetTile;
    private final Set<WorldPoint> fightTiles = new HashSet<>();
    private final Set<WorldPoint> lootTiles = new HashSet<>();
    private final boolean pickUpArrows;
    private long lootUntil;
    private int lootPicks;
    private final Set<Long> lootSeen = new HashSet<>();
    private int failures;
    private int ammoFails;
    private int eatFails;
    private boolean breaksLocked;
    private volatile boolean aborted;
    private long restUntil;
    private long nextShuffleAt;

    public CombatActivity(ApTask task, Choices choices, Supplier<AccountSnapshot> snapshots, ApPace pace, Random random,
                          Habits habits, long spotSeed, AtomicReference<String> sessionSpot, boolean pickUpArrows) {
        this.pickUpArrows = pickUpArrows;
        if (task.path() != ApPath.COMBAT) {
            throw new IllegalArgumentException("CombatActivity cannot run " + task);
        }
        this.task = task;
        this.skill = task.skill();
        this.choices = choices;
        this.snapshots = snapshots;
        this.pace = pace;
        this.random = random;
        this.habits = habits;
        this.spotSeed = spotSeed;
        this.sessionSpot = sessionSpot;
        this.kit = newKit();
    }

    @Override
    public String describe() {
        return "Training " + skill.getName() + " to " + task.target();
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        Outcome o = run(s);
        // anything but CONTINUE can end this activity without abort() being called
        lockBreaks(target != null && o.getKind() == Outcome.Kind.CONTINUE);
        return o;
    }

    private Outcome run(AccountSnapshot s) {
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
            return Outcome.parked(GatherActivity.UNMARKED);
        }
        if (!styleChosen) {
            style = XpGuard.styleFor(skill, level, choices.getProtectedSkills()).orElse(null);
            styleChosen = true;
        }
        if (style == null || choices.getProtectedSkills().contains(skill)) {
            return Outcome.parked("no combat style trains " + skill.getName() + " without a protected skill");
        }
        if (kit != null) {
            Outcome o = whileWalking(null, () -> kit.prepare(Needs.forTask(task, s), keep(s)));
            if (o.getKind() != Outcome.Kind.DONE) return o;
            kit = null;
            spot = null;
            styleSet = false;
            if (fled && lowHp() && food() == 0) startRest(now);
            fled = false;
        }
        // food before the level-up message: reading it can take seconds
        if (restUntil == 0 && lowHp()) {
            Outcome o = eatOrFlee(s, now);
            if (o != null) return o;
        }
        if (Rs2Dialogue.hasContinue()) {
            Global.sleep((int) pace.levelUpMs(random, habits.getGlanceAfterLevel()));
            Rs2Dialogue.clickContinue();
            return Outcome.cont();
        }
        if (restUntil > 0) return rest(now);
        if (!styleSet) {
            // a bow's styles read the same without ammo, but an attack without it does nothing
            if (skill == Skill.RANGED && wieldArrows(s)) return Outcome.cont();
            String why = CombatStyle.ensure(style, choices.getProtectedSkills(), spell(s));
            if (why != null) return Outcome.parked(why);
            styleSet = true;
        }
        if (target != null) {
            Outcome o = fighting(s, now);
            if (o != null) return o;
        }
        if (lootUntil > now) {
            if (loot(s)) return Outcome.cont();
        } else {
            lootSeen.clear();
        }
        if (busy()) return Outcome.cont();
        if (buryNow(s)) return bury(s);
        if (skill == Skill.RANGED && wieldArrows(s)) return Outcome.cont();
        if (s.getFreeSlots() == 0) return bank(s);
        Outcome supplies = supplies(s);
        if (supplies != null) return supplies;
        return attack(s, now);
    }

    boolean unmarkedTooLong(int level, int target, long now) {
        if (level < target) {
            reachedAt = 0;
            return false;
        }
        if (reachedAt == 0) reachedAt = now;
        return now - reachedAt > GatherActivity.UNMARKED_MS;
    }

    @Override
    public void abort() {
        // shutdown() aborts from the Swing thread while a tick may still be running
        aborted = true;
        lockBreaks(false);
        super.abort();
    }

    /** A break mid-fight would leave the player standing there being hit, with nobody eating. */
    private synchronized void lockBreaks(boolean lock) {
        if (aborted) lock = false;
        if (lock == breaksLocked) return;
        breaksLocked = lock;
        BreakHandlerScript.setLockState(lock);
    }

    private Outcome fighting(AccountSnapshot s, long now) {
        // runes running out mid-fight clears autocast and the staff falls back to a melee style
        if (!CombatStyle.activeSafe(style, choices.getProtectedSkills())) {
            target = null;
            stopAttacking();
            if (skill == Skill.MAGIC && casts(s, s.getInventory()) == 0) {
                Outcome o = supplies(s);
                if (o != null) return o;
            }
            return Outcome.parked("the combat style left " + style + " mid-fight; stopped attacking");
        }
        if (skill == Skill.RANGED && ammoFor(s).stream().noneMatch(id -> s.carried(id) > 0)) {
            target = null;
            stopAttacking();
            return Outcome.replan(OUT_OF_ARROWS);
        }
        WorldPoint at = target.getWorldLocation();
        if (at != null) {
            lastTargetTile = at;
            fightTiles.add(at);
        }
        // a killed NPC often despawns before it ever reports dead
        int idx = target.getIndex();
        boolean gone = Microbot.getRs2NpcCache().query().where(n -> n.getIndex() == idx).firstOnClientThread() == null;
        if (gone || target.isDead() || target.getHealthRatio() == 0) {
            killTile = lastTargetTile;
            lootTiles.clear();
            lootTiles.addAll(fightTiles);
            lootUntil = now + LOOT_MS;
            lootPicks = 0;
            target = null;
            failures = 0;
            repicks = 0;
            triedSpots.clear();
            return Outcome.cont();
        }
        if (now - fightSince > MAX_FIGHT_MS) {
            // a stuck "can't reach" can keep the player interacting forever
            refused.add(target.getIndex());
            target = null;
            return fail("a fight ran past " + MAX_FIGHT_MS / 1000 + " s");
        }
        Rs2NpcModel other = Attackers.onUs(target.getIndex());
        if (other != null && (spot == null || !spot.getTargets().contains(other.getName()))
                && !AttackerRules.fightBack(other.getName(), other.getCombatLevel())) {
            target = null;
            return escape(other);
        }
        if (busy() || target.isInteractingWithPlayer()) {
            lastBusyAt = now;
            return Outcome.cont();
        }
        if (now - lastBusyAt > LOST_MS) {
            refused.add(target.getIndex());
            target = null;
        }
        return Outcome.cont();
    }

    private Outcome attack(AccountSnapshot s, long now) {
        if (Rs2Inventory.isItemSelected()) Rs2Inventory.deselect();
        if (spot == null) {
            spotChoices = Spots.usable(task, s.getLevels(), false, Needs.edible(s));
            String stay = sessionSpot.get();
            spot = spotChoices.stream().filter(sp -> sp.getName().equals(stay) && !triedSpots.contains(stay)).findFirst()
                    .orElseGet(() -> GatherActivity.pickSpot(spotChoices, spotSeed, random, triedSpots));
            if (spot != null && stay == null) sessionSpot.set(spot.getName());
            refused.clear();
            searchStart = 0;
            if (spot == null) return Outcome.parked("no " + skill.getName() + " spot has anything to fight");
            log.info("Account Starter: fighting at {}", spot.getName());
        }
        Rs2NpcModel attacker = Attackers.onUs(-1);
        if (attacker != null && !spot.getTargets().contains(attacker.getName())
                && !AttackerRules.fightBack(attacker.getName(), attacker.getCombatLevel())) {
            return escape(attacker);
        }
        Set<WorldPoint> reach = Reach.tiles();
        boolean far = s.getLocation().distanceTo2D(spot.getCenter()) > spot.getRadius() + 2;
        Rs2NpcModel npc = attacker;
        if (npc == null) {
            List<Rs2NpcModel> free = freeTargets(spot.getRadius() + 4, reach);
            if (!far && leaveCrowd(free.size())) return Outcome.cont();
            npc = free.isEmpty() ? null : free.get(random.nextInt(Math.min(PICK_AMONG, free.size())));
        }
        if (npc != null) {
            Rs2NpcModel picked = npc;
            String npcName = npc.getName();
            if (!CombatStyle.activeSafe(style, choices.getProtectedSkills())) {
                return Outcome.parked("the combat style no longer matches " + style + "; not attacking");
            }
            Global.sleep((int) pace.reactionMs(random));
            int dist = s.getLocation().distanceTo2D(npc.getWorldLocation());
            lastAttackAt = System.currentTimeMillis();
            boolean ok = npc.click("Attack") && Global.sleepUntil(
                    () -> picked.isInteractingWithPlayer() || Rs2Player.getInteracting() != null,
                    Math.min(8000, 1800 + 600 * dist));
            searchStart = 0;
            if (ok) {
                target = npc;
                fightTiles.clear();
                lastBusyAt = System.currentTimeMillis();
                fightSince = lastBusyAt;
                return Outcome.cont();
            }
            refused.add(npc.getIndex());
            return fail("the " + npcName + " did not fight back");
        }
        // the pen and the field are fenced: standing by them outside is not being there
        boolean outside = !reach.contains(spot.getCenter());
        if (far || outside) {
            return walk(spot.getCenter(), 2, outside ? 0 : spot.getRadius(), spot.getName());
        }
        if (searchStart == 0) {
            searchStart = now;
            return Outcome.cont();
        }
        if (now - searchStart < SEARCH_MS) return Outcome.cont();
        triedSpots.add(spot.getName());
        spot = null;
        if (++repicks > MAX_REPICKS) {
            return Outcome.parked("found nothing to fight at " + String.join(", ", triedSpots));
        }
        return Outcome.cont();
    }

    private List<Rs2NpcModel> freeTargets(int within, Set<WorldPoint> reach) {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return List.of();
        Set<Actor> others = othersTargets();
        WorldPoint centre = me.distanceTo(spot.getCenter()) <= spot.getRadius() + 8 ? me : spot.getCenter();
        return Microbot.getRs2NpcCache().query()
                .withNames(spot.getTargets().toArray(new String[0]))
                .within(centre, Math.max(within, 10))
                .where(n -> !refused.contains(n.getIndex()) && notTaken(n, others) && reach.contains(n.getWorldLocation()))
                .toListOnClientThread().stream()
                .sorted(Comparator.comparingInt(n -> n.getWorldLocation().distanceTo(me)))
                .collect(Collectors.toList());
    }

    private boolean leaveCrowd(int freeTargets) {
        if (spot.getName().equals(sessionSpot.get())) return false;
        boolean another = spotChoices.stream().anyMatch(o -> !o.getName().equals(spot.getName()) && !triedSpots.contains(o.getName()));
        if (!another) return false;
        int players = playersNear(spot.getCenter(), spot.getRadius() + 4);
        if (!Spots.crowded(players, freeTargets)) return false;
        log.info("Account Starter: {} is crowded ({} players, {} free targets); moving on", spot.getName(), players, freeTargets);
        triedSpots.add(spot.getName());
        spot = null;
        return true;
    }

    private static int playersNear(WorldPoint at, int r) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player me = Microbot.getClient().getLocalPlayer();
            int n = 0;
            for (Player p : Microbot.getClient().getTopLevelWorldView().players()) {
                if (p != me && p.getWorldLocation().distanceTo(at) <= r) n++;
            }
            return n;
        }).orElse(0);
    }

    /** A health bar means it was hit recently: someone is fighting it between hits. */
    private static boolean notTaken(Rs2NpcModel n, Set<Actor> othersTargets) {
        if (n.isDead() || n.getHealthRatio() == 0) return false;
        if (othersTargets.contains(n.getNpc())) return false;
        Actor with = n.getInteracting();
        Player me = Microbot.getClient().getLocalPlayer();
        if (with == me) return true;
        return with == null && n.getHealthRatio() == -1;
    }

    private static Set<Actor> othersTargets() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player me = Microbot.getClient().getLocalPlayer();
            Set<Actor> targets = new HashSet<>();
            for (Player p : Microbot.getClient().getTopLevelWorldView().players()) {
                if (p != me && p.getInteracting() != null) targets.add(p.getInteracting());
            }
            return targets;
        }).orElse(Collections.emptySet());
    }

    private Outcome escape(Rs2NpcModel from) {
        String name = from.getName();
        log.info("Account Starter: combat: walking away from a level {} {}", from.getCombatLevel(), name);
        whileWalking("away from the " + name, () -> Attackers.escape(from, pace, random));
        return fail("a " + name + " kept attacking");
    }

    private boolean busy() {
        return Rs2Player.isAnimating(BUSY_GAP_MS) || Rs2Player.isMoving() || Rs2Player.getInteracting() != null;
    }

    private boolean lowHp() {
        int max = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        if (max <= 0) return false;
        double hp = (double) Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) / max;
        if (eatPoint == 0 || hp > eatPoint + 0.2) eatPoint = habits.getEatAtFraction() + random.nextGaussian() * 0.04;
        return hp <= eatPoint;
    }

    public static OutOfFood outOfFood(boolean bankKnown, boolean foodBanked, boolean catchAllowed) {
        if (!bankKnown || foodBanked) return OutOfFood.BANK;
        return catchAllowed ? OutOfFood.FOOD_RUN : OutOfFood.REST;
    }

    private Outcome eatOrFlee(AccountSnapshot s, long now) {
        int food = food();
        if (food == 0) {
            if (task.target() <= 5 && target != null && Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > 2) {
                double hp = target.getHealthPercentage();
                if (hp > 0 && hp <= NEARLY_DEAD_PERCENT) return null;
            }
            boolean banked = Needs.FOOD.stream().anyMatch(id -> s.getBank().getOrDefault(id, 0) > 0
                    || s.getNotedInventory().getOrDefault(id, 0) > 0);
            switch (outOfFood(s.isBankKnown(), banked, NeedResolver.canCatchAndCook(s))) {
                case BANK:
                    flee(OUT_OF_FOOD);
                    return Outcome.natural(OUT_OF_FOOD);
                case FOOD_RUN:
                    log.info("Account Starter: out of food; catching and cooking more");
                    target = null;
                    stopAttacking();
                    return Outcome.replan(OUT_OF_FOOD);
                default:
                    log.info("Account Starter: no food and none can be made; resting");
                    startRest(now);
                    return Outcome.cont();
            }
        }
        // the server drops an inventory click made in the same tick as an attack
        if (now - lastAttackAt < TICK_MS) return Outcome.cont();
        int before = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.interact(food, "Eat")
                || !Global.sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > before, 2400)) {
            log.info("Account Starter: combat: could not eat");
            if (++eatFails < MAX_FAILURES) return Outcome.cont();
            flee("could not eat");
            return Outcome.parked("could not eat " + MAX_FAILURES + " times");
        }
        eatFails = 0;
        return Outcome.cont();
    }

    private void flee(String why) {
        log.info("Account Starter: {}; leaving the fight", why);
        target = null;
        fled = true;
        whileWalking("the bank", Rs2Bank::walkToBank);
        kit = newKit();
    }

    private void startRest(long now) {
        log.info("Account Starter: no food to be had; resting until Hitpoints recover");
        target = null;
        stopAttacking();
        restUntil = now + REST_MS;
        nextShuffleAt = 0;
    }

    private Outcome rest(long now) {
        int max = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) >= Math.ceil(max * (habits.getEatAtFraction() + habits.getRestMargin()))) {
            log.info("Account Starter: rested; back to the fight");
            restUntil = 0;
            spot = null;
            return Outcome.cont();
        }
        if (now > restUntil) return Outcome.parked("no food and HP not recovering");
        Rs2NpcModel on = Attackers.onUs(-1);
        if (on != null) {
            whileWalking("away from the " + on.getName(), () -> Attackers.escape(on, pace, random));
            return Outcome.cont();
        }
        if (now < nextShuffleAt) return Outcome.cont();
        // a small move also keeps the stuck watchdog from reading the rest as a hang
        nextShuffleAt = now + SHUFFLE_MIN_MS + (long) (random.nextDouble() * SHUFFLE_SPAN_MS);
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return Outcome.cont();
        int dx = random.nextInt(3) - 1;
        int dy = random.nextInt(3) - 1;
        if (dx == 0 && dy == 0) dx = random.nextBoolean() ? 1 : -1;
        WorldPoint p = me.dx(dx).dy(dy);
        if (Rs2Tile.isWalkable(p)) {
            Global.sleep((int) pace.reactionMs(random));
            Rs2Walker.walkFastCanvas(p);
        }
        return Outcome.cont();
    }

    /** A walk click cancels the attack. */
    private void stopAttacking() {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return;
        List<int[]> offsets = new ArrayList<>(List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1},
                new int[]{1, 1}, new int[]{-1, -1}, new int[]{1, -1}, new int[]{-1, 1}));
        Collections.shuffle(offsets, random);
        for (int[] o : offsets) {
            WorldPoint p = me.dx(o[0]).dy(o[1]);
            if (Rs2Tile.isWalkable(p)) {
                Rs2Walker.walkFastCanvas(p);
                Global.sleepUntil(() -> Rs2Player.getInteracting() == null, 1800);
                return;
            }
        }
    }

    private static int food() {
        return Rs2Inventory.items().filter(i -> !i.isNoted() && Needs.FOOD.contains(i.getId())).map(Rs2ItemModel::getId)
                .findFirst().orElse(0);
    }

    private boolean loot(AccountSnapshot s) {
        if (killTile == null || lootPicks >= MAX_LOOT_PICKS) {
            lootUntil = 0;
            return false;
        }
        Set<Integer> own = ownArrows(s);
        Set<WorldPoint> fought = new HashSet<>(lootTiles);
        WorldPoint kill = killTile;
        Set<WorldPoint> reach = Reach.tiles();
        // own kills and arrows read OWNERSHIP_SELF; public piles do not
        List<Rs2TileItemModel> here = Microbot.getRs2TileItemCache().query()
                .where(i -> i.isOwned() && i.getWorldLocation() != null && reach.contains(i.getWorldLocation())
                        && lootTile(i.getId(), i.getWorldLocation(), kill, own, fought))
                .toListOnClientThread();
        if (here.isEmpty()) return true;
        Rs2TileItemModel item = null;
        for (Rs2TileItemModel i : here) {
            if (!lootSeen.add(lootKey(i.getId(), i.getWorldLocation()))) continue;
            if (fits(i, s) && LootRules.take(i.getId(), i.getQuantity(), choices,
                    own.contains(i.getId())) && !i.isMembers()) {
                item = i;
                break;
            }
        }
        if (item == null) {
            if (lootPicks == 0) return true;
            lootUntil = 0;
            return false;
        }
        lootPicks++;
        int id = item.getId();
        int before = Rs2Inventory.itemQuantity(id);
        Global.sleep((int) pace.reactionMs(random));
        if (item.pickup()) Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before, 3000);
        return true;
    }

    /** getHash() is the scene node's, shared by a whole pile, so it cannot key one item. */
    public static long lootKey(int itemId, WorldPoint at) {
        return ((long) itemId << 32) | (at.hashCode() & 0xffffffffL);
    }

    public static boolean lootTile(int itemId, WorldPoint at, WorldPoint kill, Set<Integer> ownArrows, Set<WorldPoint> fought) {
        int d = at.distanceTo(kill);
        return d <= LOOT_REACH || (ownArrows.contains(itemId) && d <= ARROW_REACH && fought.contains(at));
    }

    private static boolean fits(Rs2TileItemModel i, AccountSnapshot s) {
        return s.getFreeSlots() > 0 || (i.isStackable() && s.getInventory().containsKey(i.getId()));
    }

    private boolean buryNow(AccountSnapshot s) {
        if (!choices.isBury() || !XpGuard.allowed(Action.BURY, choices.getProtectedSkills())) return false;
        int bones = s.getInventory().entrySet().stream().filter(e -> LootRules.BONES.contains(e.getKey())).mapToInt(Map.Entry::getValue).sum();
        if (bones == 0) return false;
        return !habits.isBuryInBatches() || bones >= buryAt || s.getFreeSlots() < 3;
    }

    private Outcome bury(AccountSnapshot s) {
        int id = s.getInventory().keySet().stream().filter(LootRules.BONES::contains).findFirst().orElse(0);
        int before = Rs2Inventory.itemQuantity(id);
        Global.sleep((int) pace.reactionMs(random));
        if (!Rs2Inventory.interact(id, "Bury") || !Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id) < before, 2400)) {
            return fail("could not bury the bones");
        }
        if (before <= 1) buryAt = 2 + random.nextInt(3);
        else buryAt = 1;
        return Outcome.cont();
    }

    private boolean wieldArrows(AccountSnapshot s) {
        int worn = wornArrow(s);
        Set<Integer> fires = ammoFor(s);
        int carried = s.getInventory().keySet().stream().filter(id -> worn == 0 ? fires.contains(id) : id == worn)
                .findFirst().orElse(0);
        if (carried == 0 || ammoFails >= MAX_FAILURES) return false;
        Global.sleep((int) pace.reactionMs(random));
        // Rs2Inventory.wield skips an id already worn, so picked-up arrows would never rejoin the quiver
        if (Rs2Inventory.interact(carried, "Wield") && Global.sleepUntil(() -> !Rs2Inventory.hasItem(carried), 2000)) {
            ammoFails = 0;
        } else {
            ammoFails++;
        }
        return true;
    }

    private static int wornArrow(AccountSnapshot s) {
        Set<Integer> fires = ammoFor(s);
        return s.getEquipment().keySet().stream().filter(fires::contains).findFirst().orElse(0);
    }

    private Set<Integer> ownArrows(AccountSnapshot s) {
        return skill == Skill.RANGED && pickUpArrows ? ownArrows(s.getEquipment()) : Collections.emptySet();
    }

    /** The training bow fires only training arrows. */
    public static Set<Integer> ownArrows(Map<Integer, Integer> equipment) {
        return equipment.containsKey(GearPlan.TRAINING_BOW) ? TRAINING_ARROWS : REAL_ARROWS;
    }

    private static Set<Integer> ammoFor(AccountSnapshot s) {
        return ownArrows(s.getEquipment());
    }

    private Outcome supplies(AccountSnapshot s) {
        if (skill == Skill.RANGED) {
            if (wornArrow(s) != 0) return null;
            Set<Integer> fires = ammoFor(s);
            boolean banked = s.getBank().keySet().stream().anyMatch(fires::contains);
            return banked || !s.isBankKnown() ? bank(s) : replan("out of arrows");
        }
        if (skill != Skill.MAGIC) return null;
        int carried = casts(s, s.getInventory());
        // not Rs2Magic.canCast: it opens the Magic tab on every call
        if (carried >= FIGHT_CASTS) return null;
        if (!s.isBankKnown() || casts(s, GearPlan.ownedMap(s)) > carried) return bank(s);
        return carried > 0 ? null : replan("out of runes");
    }

    private Outcome replan(String why) {
        target = null;
        return Outcome.replan(why);
    }

    private int casts(AccountSnapshot s, Map<Integer, Integer> runes) {
        int casts = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> e : GearPlan.cost(spell(s)).entrySet()) {
            int staff = GearPlan.staffFor(e.getKey());
            if (staff != 0 && s.getEquipment().containsKey(staff)) continue;
            casts = Math.min(casts, runes.getOrDefault(e.getKey(), 0) / e.getValue());
        }
        return casts;
    }

    private Rs2CombatSpells spell(AccountSnapshot s) {
        Map<Integer, Integer> owned = GearPlan.ownedMap(s);
        for (int rune : new int[]{GearPlan.AIR, GearPlan.WATER, GearPlan.EARTH, GearPlan.FIRE}) {
            int staff = GearPlan.staffFor(rune);
            if (!s.getEquipment().containsKey(staff)) owned.remove(staff);
        }
        return GearPlan.bestStrike(s.getLevels().getOrDefault(Skill.MAGIC, 1), owned);
    }

    private Outcome bank(AccountSnapshot s) {
        if (choices.isBury() && XpGuard.allowed(Action.BURY, choices.getProtectedSkills())
                && s.getInventory().keySet().stream().anyMatch(LootRules.BONES::contains)) {
            return bury(s);
        }
        if (++bankVisits > MAX_BANK_VISITS) {
            return Outcome.parked("banked " + MAX_BANK_VISITS + " times without finishing " + task.name());
        }
        target = null;
        kit = newKit();
        return Outcome.cont();
    }

    private Outcome walk(WorldPoint to, int distance, int arriveWithin, String place) {
        return whileWalking(place, () -> {
            if (Detour.walkTo(to, distance)) return Outcome.cont();
            // spot centres can be water or a busy tile: stopping inside the spot's area is arriving
            WorldPoint at = Rs2Player.getWorldLocation();
            return at != null && at.getPlane() == to.getPlane() && at.distanceTo(to) <= arriveWithin ? Outcome.cont() : fail("could not reach " + place);
        });
    }

    private Kit newKit() {
        return new Kit(task, choices, snapshots, pace, random);
    }

    private Set<Integer> keep(AccountSnapshot s) {
        Set<Integer> keep = new HashSet<>(ARROWS);
        keep.addAll(Needs.FOOD);
        for (Need n : Needs.forTask(task, s)) keep.addAll(n.getAnyOf());
        return keep;
    }

    private Outcome fail(String reason) {
        log.info("Account Starter: combat: {}", reason);
        return ++failures >= MAX_FAILURES ? Outcome.parked(reason) : Outcome.cont();
    }
}
