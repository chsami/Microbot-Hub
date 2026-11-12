package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.SpecTiming;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Hazards;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Loadout;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.SleepwalkerPlan;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.magic.Spell;

/**
 * Chooses the target each tick and attacks it: the sleepwalkers once her blast is coming, with only their weapon
 * wielded until they rise, otherwise a parasite first, then the husks (missile husk first), the ready totems with
 * magic, and her with melee; in her final phase each sleepwalker, thrown at, before a parasite and her. A special attack goes on her when the energy allows. Melee waits on a safe tile beside her while her hazards are out, or beside where a surge will
 * land. While they are out, an attack waits until it can start without walking.
 */
public final class CombatTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 2;
    // shortcut: the shortest powered staff range, not the worn weapon's own; read the weapon's range if longer staves wait too often.
    private static final int CAST_RANGE = 7;
    /** A refused special attack bar shows within this many ticks. */
    private static final int SPEC_CHECK_TICKS = 2;
    /** The player's eating and drinking animation, which is not an attack. */
    private static final int EAT_ANIMATION = 829;
    /** A struck sleepwalker still standing after this many ticks was not hit, and is a target again. */
    private static final int STRIKE_TICKS = 5;
    /** The longest the last totem waits for her husks or parasite, so a set without them does not stall. */
    public static final int TOTEM_HOLD_TICKS = 30;
    private static final int SLEEPWALKER_ATTACK_TICKS = 2;
    private static final int THROWN_CATEGORY = 19;
    private static final int LONGRANGE_REACH = 2;
    /** The ghostly thralls, best first, with their spellbook buttons. */
    private static final List<Thrall> THRALLS = List.of(
        new Thrall(Rs2Thrall.GREATER_GHOST, InterfaceID.MagicSpellbook.RESURRECT_GREATER_GHOST, 6),
        new Thrall(Rs2Thrall.SUPERIOR_GHOST, InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_GHOST, 4),
        new Thrall(Rs2Thrall.LESSER_GHOST, InterfaceID.MagicSpellbook.RESURRECT_LESSER_GHOST, 2));

    /** A thrall spell, its spellbook button and the Prayer points the summon costs. */
    static final class Thrall
    {
        final Rs2Thrall spell;
        final int button;
        final int prayer;

        Thrall(Rs2Thrall spell, int button, int prayer)
        {
            this.spell = spell;
            this.button = button;
            this.prayer = prayer;
        }
    }
    private static final Set<Integer> THRALL_NPCS = Set.of(NpcID.ARCEUUS_THRALL_GHOST_LESSER,
        NpcID.ARCEUUS_THRALL_GHOST_SUPERIOR, NpcID.ARCEUUS_THRALL_GHOST_GREATER, NpcID.ARCEUUS_THRALL_SKELETON_LESSER,
        NpcID.ARCEUUS_THRALL_SKELETON_SUPERIOR, NpcID.ARCEUUS_THRALL_SKELETON_GREATER, NpcID.ARCEUUS_THRALL_ZOMBIE_LESSER,
        NpcID.ARCEUUS_THRALL_ZOMBIE_SUPERIOR, NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER);
    /** A resurrection spell can be cast once every ten seconds. */
    static final int THRALL_COOLDOWN_TICKS = 17;
    /** Death Charge can be cast once every 60 seconds. */
    static final int DEATH_CHARGE_COOLDOWN_TICKS = 100;
    private final NightmareActions actions;
    private final PhosanisNightmareConfig config;
    private final NightmareFight fight;
    private final Hazards hazards;
    private final TripState trip;
    private final GearTask gear;
    private final IntSupplier tick;
    private final Function<WorldPoint, Collection<WorldPoint>> reachable;
    private final BiPredicate<WorldArea, WorldArea> sight;
    private final Map<Loadout, Integer> weapons;
    private final Consumer<String> tell;
    private final Predicate<Spell> castable;
    /** The player has been told that the thrall or Death Charge cannot be cast. */
    private boolean thrallTold;
    private boolean chargeTold;
    /** Sleepwalkers the player has already attacked, and the tick of the attack; any hit kills one. */
    private final Map<NPC, Integer> struck = new HashMap<>();
    /** The tick each sleepwalker rose on, from which its first step is reckoned. */
    private final Map<NPC, Integer> risen = new HashMap<>();
    /** The tile each sleepwalker was first seen on: one still there has not taken its first step. */
    private final Map<NPC, WorldPoint> riseTiles = new HashMap<>();
    /** The sleepwalker chosen this tick is the last, and is hit in melee. */
    private boolean meleeWalker;
    private Actor lastTarget;
    /** The totem being charged: after a dodge or a drink the player goes back to it, not to one as near. */
    private NPC totem;
    private int lastAnimation = -1;
    private int animatedAt = Integer.MIN_VALUE / 2;
    private String option = "Attack";
    private int requestedAt = Integer.MIN_VALUE / 2;
    private int summonedAt = Integer.MIN_VALUE / 2;
    private int chargedAt = Integer.MIN_VALUE / 2;
    private int specCost;
    private int specNeeded;
    private int specRequestedAt = -1;
    private int specRequestedEnergy;
    private int specSwingAt = -1;
    private int specSwingEnergy;
    private int lastEnergy = -1;
    private int holdingSince = -1;
    private boolean holding;
    private boolean blastComing;
    /** All four totems have been seen charged since her totems woke: her blast is next. */
    private boolean totemsFired;
    private int throwPhase;
    private int lastStrike = Integer.MIN_VALUE / 2;
    private String status = "Waiting for a target";

    public CombatTask(NightmareActions actions, PhosanisNightmareConfig config, NightmareFight fight, Hazards hazards,
        TripState trip, GearTask gear, IntSupplier tick, Function<WorldPoint, Collection<WorldPoint>> reachable,
        BiPredicate<WorldArea, WorldArea> sight, Predicate<Spell> castable, Map<Loadout, Integer> weapons, Consumer<String> tell)
    {
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.hazards = hazards;
        this.trip = trip;
        this.gear = gear;
        this.tick = tick;
        this.reachable = reachable;
        this.sight = sight;
        this.castable = castable;
        this.weapons = weapons;
        this.tell = tell;
    }

    /** Chooses this tick's target and loadout before any task runs, and learns the special attack's cost. */
    public void observe()
    {
        int now = tick.getAsInt();
        int energy = actions.specEnergy();
        if (lastEnergy >= 0 && energy < lastEnergy && trip.getLoadout() == Loadout.SPEC)
        {
            specCost = lastEnergy - energy;
        }
        lastEnergy = energy;
        if (specRequestedAt >= 0 && now - specRequestedAt >= SPEC_CHECK_TICKS)
        {
            if (!actions.specEnabled() && energy >= specRequestedEnergy)
            {
                // The bar stayed off with this much energy, so the weapon needs more.
                specNeeded = energy + 1;
            }
            specRequestedAt = -1;
        }
        observeSpecSwing(now, energy);
        option = "Attack";
        trip.setTarget(null);
        holding = false;
        blastComing = false;
        if (fight.getStage() != Stage.TOTEMS)
        {
            holdingSince = -1;
        }
        observeStrike();
        if (!actions.inInstance() || !fight.isFighting())
        {
            trip.setLoadout(Loadout.MELEE);
            return;
        }
        blastComing = blastComing();
        if (blastComing)
        {
            // Her blast kills a player whose sleepwalkers all reach her, so they come before anything left from the phase.
            // A husk beside the player dies to one crush hit and frees the player to reach them, so it comes first.
            // One under her is out of melee reach, so the staff reaches the sleepwalkers from where the player is held.
            chooseThrows();
            NPC walker = sleepwalker();
            NPC husk = husk();
            if (husk != null && (walker == null || !underHer(husk)))
            {
                choose(husk, huskLoadout(husk));
                return;
            }
            if (walker == null && fight.getPhase() == 3 && fight.sleepwalkersUp() && !fight.sleepwalkerOutOfView(
                actions.npcs(Set.of(DreamData.SLEEPWALKER), true).stream().map(NPC::getIndex).collect(Collectors.toSet())))
            {
                // Her last blast's sleepwalkers are dealt with: her final phase comes next. A parasite would heal
                // her through it, so it dies first; then that phase's set goes on and it opens with an attack.
                if (!attackParasite())
                {
                    choose(null, specDue(true) ? Loadout.SPEC : Loadout.MELEE);
                }
                return;
            }
            // Until they rise, only their weapon is wielded: a slower weapon's attack would hold back the first throw.
            choose(walker, held() ? Loadout.LONGRANGE : meleeWalker ? Loadout.HUSK : Loadout.SLEEPWALKER);
            return;
        }
        // Her final phase sleepwalkers are left: they rise from every side, and killing her before the first
        // reaches her, about 20 ticks after it rises, is what stops them.
        if (attackParasite())
        {
            return;
        }
        NPC npc = husk();
        if (npc != null)
        {
            choose(npc, huskLoadout(npc));
            return;
        }
        if (fight.getStage() == Stage.TOTEMS)
        {
            option = "Charge";
            holding = holdLastTotem(now);
            choose(holding ? null : totem(), Loadout.MAGIC);
            return;
        }
        choose(actions.boss(), specDue(fight.getStage() == Stage.DESPERATION) ? Loadout.SPEC : Loadout.MELEE);
    }

    /**
     * The bar can show the special attack on while the game refuses it. A swing at her with the bar on that
     * spends no energy within {@link #SPEC_CHECK_TICKS} means the weapon needs more than there is, so the
     * fight goes on with the melee set.
     */
    private void observeSpecSwing(int now, int energy)
    {
        Player player = actions.player();
        NPC boss = actions.boss();
        if (specSwingAt < 0 && trip.getLoadout() == Loadout.SPEC && actions.specEnabled() && player != null
            && boss != null && player.getInteracting() == boss && player.getAnimation() != -1
            && player.getAnimation() != EAT_ANIMATION)
        {
            specSwingAt = now;
            specSwingEnergy = energy;
        }
        if (specSwingAt >= 0 && now - specSwingAt >= SPEC_CHECK_TICKS)
        {
            if (energy >= specSwingEnergy)
            {
                specNeeded = energy + 1;
            }
            specSwingAt = -1;
        }
    }

    /** Husks are up, so the player is held in place. */
    private boolean held()
    {
        return first(Set.of(DreamData.MAGIC_HUSK, DreamData.RANGED_HUSK)) != null;
    }

    /**
     * Her blast is coming: she is in her blast form, all four totems are charged, or her sleepwalkers have risen,
     * which happens before she takes the form. Her final phase sends them one at a time with no blast.
     */
    private boolean blastComing()
    {
        Stage stage = fight.getStage();
        // The totems leave their charged form as they fire, before her sleepwalkers rise, so the blast stays due
        // until her totems wake again.
        if (stage == Stage.TOTEMS && actions.npcs(DreamData.CHARGED_TOTEMS).size() >= DreamData.CHARGED_TOTEMS.size())
        {
            totemsFired = true;
        }
        else if (stage != Stage.TOTEMS && stage != Stage.BLAST)
        {
            totemsFired = false;
        }
        return stage == Stage.BLAST || totemsFired
            || stage != Stage.DESPERATION && first(Set.of(DreamData.SLEEPWALKER)) != null;
    }

    /**
     * Before her sleepwalkers rise, a safe tile one off her centre both ways: from there the throws and the last melee
     * hit reach the most of the ways they can rise. None when the player is already there.
     */
    private WorldPoint blastWaitTile(WorldPoint at)
    {
        WorldArea body = home();
        if (body == null || at == null)
        {
            return null;
        }
        WorldPoint centre = new WorldPoint(body.getX() + body.getWidth() / 2, body.getY() + body.getHeight() / 2, body.getPlane());
        Predicate<WorldPoint> waits = tile -> Math.abs(tile.getX() - centre.getX()) * Math.abs(tile.getY() - centre.getY()) == 1;
        if (waits.test(at))
        {
            return null;
        }
        return hazards.inReach(at, reachable.apply(at), null, waits);
    }

    /** Where she charges her blast and her sleepwalkers walk to: the arena's centre, wherever she walked before. */
    private WorldArea home()
    {
        NPC boss = actions.boss();
        WorldArea home = hazards.home();
        return home != null ? home : boss == null ? null : boss.getWorldArea();
    }

    /** The missile husk first, then the magic husk; one under her, out of melee reach, after one beside the player. */
    private NPC husk()
    {
        NPC fallback = null;
        for (int id : new int[] {DreamData.RANGED_HUSK, DreamData.MAGIC_HUSK})
        {
            NPC npc = first(Set.of(id));
            if (npc != null && !underHer(npc))
            {
                return npc;
            }
            fallback = fallback != null ? fallback : npc;
        }
        return fallback;
    }

    /** Melee cannot reach a husk risen under her, so the staff alone casts at it from where the player is held. */
    private Loadout huskLoadout(NPC husk)
    {
        return underHer(husk) ? Loadout.LONGRANGE : Loadout.HUSK;
    }

    /** Under her and out of melee reach; she rises husks around the player, so a player under her reaches one beside. */
    private boolean underHer(NPC npc)
    {
        NPC boss = actions.boss();
        WorldArea body = boss == null ? null : boss.getWorldArea();
        WorldPoint at = npc.getWorldLocation();
        WorldPoint player = actions.position();
        return body != null && at != null && body.contains(at)
            && (player == null || !new WorldArea(player, 1, 1).isInMeleeDistance(npc.getWorldArea()));
    }

    /**
     * The last ready totem waits while her next special could be her husks or her parasite: husks summoned as
     * the totems finish can hold the player while her sleepwalkers walk to her. She uses each of a phase's three
     * specials once before any again, so a round that has had its spawn and is not over cannot bring one. Without
     * that count, the wait ends once she has summoned them since the totems woke. Either way it ends after
     * {@link #TOTEM_HOLD_TICKS}, for a set of specials without them.
     */
    private boolean holdLastTotem(int now)
    {
        boolean clear = fight.roundKnown() ? !fight.spawnCanComeNext() : fight.spawnedSinceTotems();
        if (clear || actions.npcs(DreamData.READY_TOTEMS).size() != 1)
        {
            holdingSince = -1;
            return false;
        }
        if (holdingSince < 0)
        {
            holdingSince = now;
        }
        return now - holdingSince < TOTEM_HOLD_TICKS;
    }

    /** The totem being charged while it is still ready, otherwise the nearest ready one. */
    private NPC totem()
    {
        if (totem == null || !actions.npcs(DreamData.READY_TOTEMS).contains(totem))
        {
            totem = first(DreamData.READY_TOTEMS);
        }
        return totem;
    }

    /** The ready totems left are across the arena, out of reach: step to a safe tile beside the nearest one. */
    private boolean stepToTotem(Runnable sent)
    {
        WorldPoint at = actions.player().getWorldLocation();
        NPC totem = actions.npcs(DreamData.READY_TOTEMS).stream()
            .min(Comparator.comparingInt(npc -> npc.getWorldArea().distanceTo(at))).orElse(null);
        WorldPoint tile = totem == null ? null : hazards.beside(at, reachable.apply(at), totem.getWorldArea());
        if (tile == null)
        {
            return false;
        }
        status = "Moving to a totem";
        actions.step(tile, sent);
        return true;
    }

    /**
     * No ready totem is in view while her totems are up: the game shows NPCs only within about 15 tiles, so from the
     * arena's edge a far corner's totem is missing. From her centre all four are in view.
     */
    private boolean totemsUnseen()
    {
        return fight.getStage() == Stage.TOTEMS && actions.npcs(DreamData.READY_TOTEMS).isEmpty();
    }

    /** The attack can start from where the player stands: beside the target for melee, in range and sight for magic. */
    boolean inReach(WorldPoint at, WorldArea target)
    {
        WorldArea from = new WorldArea(at, 1, 1);
        if (!trip.getLoadout().isMagic())
        {
            return from.isInMeleeDistance(target);
        }
        return from.distanceTo(target) <= (trip.getLoadout() == Loadout.SLEEPWALKER ? throwRange() : CAST_RANGE)
            && sight.test(from, target);
    }

    private int throwRange()
    {
        int weapon = weapons.getOrDefault(Loadout.SLEEPWALKER, -1);
        int reach = actions.reach(weapon);
        return reach <= 0 ? CAST_RANGE : reach + (thrown(weapon) && styled("(Longrange)") ? LONGRANGE_REACH : 0);
    }

    private int throwTicks()
    {
        int weapon = weapons.getOrDefault(Loadout.SLEEPWALKER, -1);
        return !thrown(weapon) ? SLEEPWALKER_ATTACK_TICKS : actions.attackSpeed(weapon) - (styled("(Rapid)") ? 1 : 0);
    }

    private boolean thrown(int weapon)
    {
        return actions.wearing(weapon) && actions.varbit(VarbitID.COMBAT_WEAPON_CATEGORY) == THROWN_CATEGORY
            && actions.reach(weapon) > 0 && actions.attackSpeed(weapon) > 1;
    }

    private boolean styled(String type)
    {
        return actions.styleIndex() == actions.styleButton(type);
    }

    private void chooseThrows()
    {
        int phase = fight.getPhase();
        int weapon = weapons.getOrDefault(Loadout.SLEEPWALKER, -1);
        WorldArea body = home();
        if (phase == throwPhase || body == null || !thrown(weapon) || first(Set.of(DreamData.SLEEPWALKER)) != null)
        {
            return;
        }
        throwPhase = phase;
        WorldPoint wait = new WorldPoint(body.getX() + body.getWidth() / 2 - 1, body.getY() + body.getHeight() / 2 + 1, body.getPlane());
        boolean third = phase == 3;
        int walkIn = (third ? DreamData.THIRD_PHASE_WALK_TICKS : DreamData.SLEEPWALKER_WALK_TICKS) - 1;
        int walkLate = (third ? DreamData.THIRD_PHASE_LATE_WALK_TICKS : DreamData.SLEEPWALKER_WALK_TICKS) - 1;
        int reach = actions.reach(weapon);
        int speed = actions.attackSpeed(weapon);
        trip.setLongThrows(SleepwalkerPlan.reaching(wait, body, phase, walkIn, walkLate, reach + LONGRANGE_REACH, speed)
            < SleepwalkerPlan.reaching(wait, body, phase, walkIn, walkLate, reach, speed - 1));
    }

    /**
     * Remembers the sleepwalker the player's attack has started on, so the next one is attacked while the hit is
     * still on its way.
     */
    private void observeStrike()
    {
        int now = tick.getAsInt();
        List<NPC> walkers = actions.npcs(Set.of(DreamData.SLEEPWALKER));
        struck.entrySet().removeIf(entry -> !walkers.contains(entry.getKey()) || now - entry.getValue() > STRIKE_TICKS);
        risen.keySet().retainAll(walkers);
        riseTiles.keySet().retainAll(walkers);
        for (NPC walker : walkers)
        {
            // One rising is first seen on the tick after it rose; one already up when first seen may be walking.
            risen.putIfAbsent(walker, now - 1 - (walker.getAnimation() == DreamData.SLEEPWALKER_RISE ? 0 : DreamData.SLEEPWALKER_WALK_TICKS));
            riseTiles.putIfAbsent(walker, walker.getWorldLocation());
        }
        Player player = actions.player();
        Actor target = player == null ? null : player.getInteracting();
        int animation = player == null ? -1 : player.getAnimation();
        // On the first tick after a switch, the attack on the previous target can still be playing, unless an
        // attack started on this tick.
        boolean started = target == lastTarget || lastAnimation == -1 || animatedAt == now;
        if (walkers.contains(target) && animation != -1 && animation != EAT_ANIMATION && started
            && !struck.containsKey(target) && now - lastStrike >= throwTicks()
            && inReach(player.getWorldLocation(), ((NPC) target).getWorldArea()))
        {
            struck.put((NPC) target, now);
            lastStrike = now;
        }
        lastTarget = target;
        lastAnimation = animation;
    }

    /** The player's animation started on this tick. */
    public void playerAnimated(int at)
    {
        animatedAt = at;
    }

    /**
     * The sleepwalker not attacked yet that comes first in the order letting the fewest reach her, assuming they
     * walk as soon as they can, which leaves time to spare if they wait longer. The last one is hit in melee when
     * that lands sooner.
     */
    private NPC sleepwalker()
    {
        meleeWalker = false;
        List<NPC> walkers = new ArrayList<>(actions.npcs(Set.of(DreamData.SLEEPWALKER)));
        walkers.removeIf(struck::containsKey);
        Player player = actions.player();
        Actor approached = player == null ? null : player.getInteracting();
        if (approached instanceof NPC && walkers.contains(approached))
        {
            // A new order on the way there would turn the player around, so the one gone for stays until it is hit.
            meleeWalker = trip.getLoadout() == Loadout.HUSK;
            return (NPC) approached;
        }
        WorldArea home = home();
        WorldPoint at = actions.position();
        if (walkers.isEmpty() || home == null || at == null)
        {
            return walkers.isEmpty() ? null : walkers.get(0);
        }
        int now = tick.getAsInt();
        int[] walkIn = new int[walkers.size()];
        int[] walkLate = new int[walkers.size()];
        boolean third = fight.getPhase() == 3;
        int walkTicks = third ? DreamData.THIRD_PHASE_WALK_TICKS : DreamData.SLEEPWALKER_WALK_TICKS;
        int lateTicks = third ? DreamData.THIRD_PHASE_LATE_WALK_TICKS : walkTicks;
        List<WorldPoint> tiles = new ArrayList<>();
        for (int index = 0; index < walkers.size(); index++)
        {
            NPC walker = walkers.get(index);
            tiles.add(walker.getWorldLocation());
            // A sleepwalker off the tile it rose on is already walking.
            int rose = risen.getOrDefault(walker, now);
            boolean walking = !walker.getWorldLocation().equals(riseTiles.get(walker));
            walkIn[index] = walking ? 1 : Math.max(1, rose + walkTicks - now);
            walkLate[index] = walking ? 1 : Math.max(1, rose + lateTicks - now);
        }
        int attackTicks = throwTicks();
        SleepwalkerPlan.Step step = SleepwalkerPlan.next(at, tiles, walkIn, walkLate, home,
            Math.max(1, lastStrike + attackTicks - now), throwRange(), attackTicks);
        meleeWalker = step.isMelee();
        return walkers.get(step.getIndex());
    }

    /** Her hazards are out and no safe tile the player can reach is beside the target. */
    private boolean meleeBlocked(NPC target)
    {
        WorldPoint at = actions.position();
        WorldArea area = target.getWorldArea();
        return at != null && area != null && hazards.isActive() && !new WorldArea(at, 1, 1).isInMeleeDistance(area)
            && hazards.beside(at, reachable.apply(at), area) == null;
    }

    /** Chooses a parasite when one is out; false when none is. */
    private boolean attackParasite()
    {
        NPC parasite = first(Set.of(DreamData.STRONG_PARASITE, DreamData.WEAK_PARASITE));
        if (parasite == null)
        {
            return false;
        }
        // A parasite under her has no safe tile beside it while her rifts are out, and it heals her until it
        // dies, so magic reaches it from where the player stands.
        choose(parasite, meleeBlocked(parasite) ? Loadout.MAGIC : Loadout.PARASITE);
        return true;
    }

    private void turnOnSpec(int now)
    {
        status = "Turning on the special attack";
        int energy = actions.specEnergy();
        actions.spec(true, () ->
        {
            requestedAt = now;
            specRequestedAt = now;
            specRequestedEnergy = energy;
        });
    }

    private NPC first(Set<Integer> ids)
    {
        return actions.nearest(ids);
    }

    private void choose(NPC npc, Loadout loadout)
    {
        trip.setTarget(npc);
        trip.setLoadout(loadout);
    }

    /** A special attack fits: a weapon is set, the phase is allowed and the energy covers what it cost before. */
    private boolean specDue(boolean finalPhase)
    {
        boolean phase = config.specTiming() == SpecTiming.ANY_PHASE || finalPhase;
        int energy = actions.specEnergy();
        return specWeapon() > 0 && phase && energy > 0 && energy >= Math.max(specCost, specNeeded);
    }

    @Override
    public boolean isValid()
    {
        // A stake throws every other tick, and a struck sleepwalker is already known, so the next one is attacked
        // on the tick after a click instead of a throw later.
        Loadout loadout = trip.getLoadout();
        int wait = loadout == Loadout.SLEEPWALKER || loadout == Loadout.LONGRANGE ? 1 : OBSERVE_TICKS;
        return trip.getPhase() == TripPhase.FIGHTING && actions.inInstance() && fight.isFighting()
            && tick.getAsInt() - requestedAt >= wait;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        int now = tick.getAsInt();
        Runnable sent = () -> requestedAt = now;
        if (castSupport(now, sent))
        {
            return;
        }
        NPC target = trip.getTarget();
        if (target == null)
        {
            if (holding)
            {
                status = "Holding the last totem for her husks";
                return;
            }
            if (fight.getStage() == Stage.TOTEMS && !hazards.isActive() && stepToTotem(sent))
            {
                return;
            }
            boolean unseen = totemsUnseen();
            WorldPoint wait = blastComing || unseen ? blastWaitTile(actions.position()) : null;
            if (wait != null)
            {
                status = blastComing ? "Moving to the middle for her sleepwalkers" : "Moving to the middle for her totems";
                actions.step(wait, sent);
                return;
            }
            if (blastComing && trip.getLoadout() == Loadout.SPEC && gear.ready() && !actions.specEnabled())
            {
                // Ready before her final phase, so its first click is the special itself.
                turnOnSpec(now);
                return;
            }
            status = blastComing ? "Waiting for sleepwalkers" : "Waiting for a target";
            return;
        }
        if (!gear.ready())
        {
            status = "Switching gear";
            return;
        }
        if (trip.getLoadout() == Loadout.SPEC && !actions.specEnabled())
        {
            turnOnSpec(now);
            return;
        }
        Player player = actions.player();
        // A coming surge ends at the far end of her lane: melee waits there instead of beside where she stands.
        // Off a corner of her body is one tile away but out of melee reach, so that steps beside her too.
        WorldArea landing = target == actions.boss() ? hazards.surgeLanding() : null;
        WorldArea goal = landing != null ? landing : target.getWorldArea();
        if (target == actions.boss() && !trip.getLoadout().isMagic() && hazards.isActive()
            && !inReach(player.getWorldLocation(), goal))
        {
            WorldPoint tile = hazards.beside(player.getWorldLocation(), reachable.apply(player.getWorldLocation()), goal);
            if (tile == null)
            {
                status = "Waiting for a safe tile beside her";
                return;
            }
            status = landing != null ? "Moving to where she lands" : "Moving beside her";
            actions.step(tile, sent);
            return;
        }
        if (landing != null)
        {
            status = "Waiting where she lands";
            return;
        }
        // An attack that has to walk first would leave the safe tile while her hazards are out. Her blast's
        // sleepwalkers and a totem across the arena cannot wait for them to end, so a safe tile in reach is
        // stepped to instead.
        if (hazards.isActive() && !inReach(player.getWorldLocation(), target.getWorldArea()))
        {
            WorldPoint at = player.getWorldLocation();
            NPC boss = actions.boss();
            WorldArea area = target.getWorldArea();
            boolean steps = trip.getLoadout() == Loadout.SLEEPWALKER || target.getId() == DreamData.SLEEPWALKER
                || DreamData.READY_TOTEMS.contains(target.getId());
            WorldPoint tile = !steps ? null
                : hazards.inReach(at, reachable.apply(at), boss == null ? null : boss.getWorldArea(), from -> inReach(from, area));
            if (tile == null)
            {
                status = "Waiting for a clear shot";
                return;
            }
            status = "Moving into range";
            actions.step(tile, sent);
            return;
        }
        if (player.getInteracting() == target && trip.getLoadout() != Loadout.SPEC)
        {
            status = "Fighting";
            return;
        }
        status = describe(target);
        actions.npc(target, option, sent);
    }

    /** Summons a thrall for the totems and charges Death Charge before special attacks, when enabled. */
    private boolean castSupport(int now, Runnable sent)
    {
        if (config.ghostlyThrall() && fight.getStage() == Stage.TOTEMS
            && !thrallUp(actions.varbit(VarbitID.ARCEUUS_RESURRECTION_ACTIVE), actions.varbit(VarbitID.ARCEUUS_RESURRECTION_COOLDOWN),
                !actions.npcs(THRALL_NPCS).isEmpty(), now - summonedAt))
        {
            List<Thrall> affordable = affordableThralls(actions.prayerPoints());
            List<Thrall> ready = affordable.stream().filter(thrall -> castable.test(thrall.spell)).collect(Collectors.toList());
            if (!affordable.isEmpty() && ready.isEmpty() && !thrallTold)
            {
                // Short Prayer points are normal, so only a spell the spellbook, level or runes rule out is told.
                thrallTold = true;
                tell.accept("A ghostly thrall cannot be cast: use the Arceuus spellbook and carry its runes, or turn off Ghostly thrall");
            }
            for (Thrall thrall : ready)
            {
                if (actions.cast(thrall.spell, thrall.button, () ->
                {
                    sent.run();
                    summonedAt = now;
                }))
                {
                    status = "Summoning a thrall";
                    return true;
                }
            }
        }
        if (config.deathCharge() && specWeapon() > 0
            && !deathChargeUp(actions.varbit(VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE), actions.varbit(VarbitID.ARCEUUS_DEATH_CHARGE_COOLDOWN),
                now - chargedAt))
        {
            boolean ready = castable.test(Rs2Spells.DEATH_CHARGE);
            if (!ready && !chargeTold)
            {
                chargeTold = true;
                tell.accept("Death Charge cannot be cast: use the Arceuus spellbook and carry its runes, or turn off Death Charge");
            }
            if (ready && actions.cast(Rs2Spells.DEATH_CHARGE, InterfaceID.MagicSpellbook.DEATH_CHARGE, () ->
            {
                sent.run();
                chargedAt = now;
            }))
            {
                status = "Casting Death Charge";
                return true;
            }
        }
        return false;
    }

    private int specWeapon()
    {
        return weapons.getOrDefault(Loadout.SPEC, -1);
    }

    /** The thrall spells, best first, whose Prayer cost the player's points cover; the game refuses the rest. */
    static List<Thrall> affordableThralls(int prayerPoints)
    {
        return THRALLS.stream().filter(thrall -> thrall.prayer <= prayerPoints).collect(Collectors.toList());
    }

    /**
     * A thrall is out or its spell is cooling down. The game's timers say so where it shows them; otherwise a thrall
     * in sight or a summon sent within the cooldown does.
     */
    static boolean thrallUp(int active, int cooldown, boolean thrallSeen, int sinceSummon)
    {
        return active != 0 || cooldown != 0 || thrallSeen || sinceSummon < THRALL_COOLDOWN_TICKS;
    }

    /**
     * Death Charge waits for a kill or is cooling down. The game's timers say so where it shows them; otherwise a
     * cast sent within the cooldown does.
     */
    static boolean deathChargeUp(int active, int cooldown, int sinceCast)
    {
        return active != 0 || cooldown != 0 || sinceCast < DEATH_CHARGE_COOLDOWN_TICKS;
    }

    private String describe(NPC target)
    {
        // The target names the attack: a loadout such as longrange serves both husks and sleepwalkers.
        if (DreamData.HUSK_IDS.contains(target.getId()))
        {
            return "Attacking a husk";
        }
        if (target.getId() == DreamData.SLEEPWALKER)
        {
            return "Attacking a sleepwalker";
        }
        switch (trip.getLoadout())
        {
            case PARASITE:
                return "Attacking the parasite";
            case HUSK:
                return "Attacking a husk";
            case SLEEPWALKER:
            case LONGRANGE:
                return "Attacking a sleepwalker";
            case MAGIC:
                return "Charging a totem";
            case SPEC:
                return "Special attack";
            default:
                return "Attacking " + (target.getName() == null ? "her" : target.getName());
        }
    }

    public void reset()
    {
        totemsFired = false;
        struck.clear();
        lastStrike = Integer.MIN_VALUE / 2;
        risen.clear();
        riseTiles.clear();
        lastTarget = null;
        totem = null;
        lastAnimation = -1;
        animatedAt = Integer.MIN_VALUE / 2;
        requestedAt = Integer.MIN_VALUE / 2;
        summonedAt = Integer.MIN_VALUE / 2;
        chargedAt = Integer.MIN_VALUE / 2;
        specCost = 0;
        specNeeded = 0;
        specRequestedAt = -1;
        specSwingAt = -1;
        lastEnergy = -1;
        holdingSince = -1;
        holding = false;
    }
}
