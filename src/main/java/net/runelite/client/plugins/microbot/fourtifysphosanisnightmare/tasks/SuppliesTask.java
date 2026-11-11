package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Doses;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/**
 * Eats and drinks restores at the configured points, drinks the parasite cure while a parasite grows
 * unweakened, and keeps the combat potion's boost up in the dream and before entering it.
 */
public final class SuppliesTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 3;
    /** Hitpoints kept above her blast: one final-phase sleepwalker's hit. */
    static final int BLAST_SPARE = 15;
    /**
     * Prayer points that outlast her blast's sleepwalkers: the last is dealt with about 20 ticks after they rise,
     * and Piety with a protection prayer drains a point about every two ticks without prayer bonus.
     */
    static final int PRAYER_HOLD_FLOOR = 12;
    /** Drink the combat potion again once the Strength boost falls below this. */
    static final int BOOST_FLOOR = 5;
    /** The most a risen parasite or a husk hits for, and a parasite bursting out, weakened or not. */
    static final int PARASITE_HIT = 22;
    static final int HUSK_HIT = 26;
    static final int WEAKENED_BURST = 10;
    static final int PARASITE_BURST = 90;
    /** Hitpoints a divine potion's dose costs. */
    static final int DIVINE_COST = 10;
    private final NightmareActions actions;
    private final PhosanisNightmareConfig config;
    private final NightmareFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Eating";

    public SuppliesTask(NightmareActions actions, PhosanisNightmareConfig config, NightmareFight fight, TripState trip, IntSupplier tick)
    {
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
    }

    public int food()
    {
        return actions.count(config.foodId());
    }

    public int restoreDoses()
    {
        return Doses.count(List.of(Doses.SUPER_RESTORE, Doses.PRAYER_POTION), actions::count);
    }

    public int cureDoses()
    {
        return Doses.count(List.of(config.parasiteCure().getDoses()), actions::count);
    }

    /** Carried doses of the configured combat potion, any dose, by name. */
    public List<Integer> combatPotions()
    {
        List<Integer> result = new ArrayList<>();
        String base = baseName(actions.itemName(config.combatPotionId()));
        if (config.combatPotionId() <= 0 || base == null)
        {
            return result;
        }
        for (int id : actions.carriedIds())
        {
            if (base.equals(baseName(actions.itemName(id))))
            {
                result.add(id);
            }
        }
        return result;
    }

    /** A potion's name without its dose, such as "Divine super combat potion" from "Divine super combat potion(4)". */
    static String baseName(String name)
    {
        return name == null || !name.matches(".*\\(\\d\\)") ? name : name.substring(0, name.lastIndexOf('('));
    }

    /**
     * At or below the eating point, or too low to keep a final-phase sleepwalker's hit to spare after her coming
     * blast. Eating ends the attack in progress, so the blast waits until no sleepwalker is still on its way to her.
     * While one is, the eating point waits too, unless every other hit that can land first would leave too little.
     */
    boolean needsFood()
    {
        int hitpoints = actions.hitpoints();
        int blast = fight.blastDamage();
        boolean coming = sleepwalkerComing();
        boolean forBlast = blast > 0 && hitpoints <= blast + BLAST_SPARE && !coming;
        // Her final phase sends sleepwalkers without end, so there the eating point never waits.
        boolean hold = coming && fight.getPhase() < 4 && hitpoints > blast + BLAST_SPARE + otherHits();
        return hitpoints < actions.maxHitpoints() && (hitpoints <= config.eatAt() && !hold || forBlast) && food() > 0;
    }

    /** One hit from each risen parasite and husk, and the burst of a parasite still growing. */
    private int otherHits()
    {
        int hits = actions.npcs(Set.of(DreamData.STRONG_PARASITE, DreamData.WEAK_PARASITE)).size() * PARASITE_HIT
            + actions.npcs(DreamData.HUSK_IDS).size() * HUSK_HIT;
        if (actions.varbit(DreamData.PARASITE_VARBIT) > 0)
        {
            hits += fight.isWeakened() ? WEAKENED_BURST : PARASITE_BURST;
        }
        return hits;
    }

    private boolean sleepwalkerComing()
    {
        return actions.npcs(Set.of(DreamData.SLEEPWALKER)).stream()
            .anyMatch(walker -> walker.getAnimation() != DreamData.SLEEPWALKER_ABSORBED);
    }

    /** A drink ends the attack in progress, so while her blast's sleepwalkers come it waits if the points last. */
    boolean needsRestore()
    {
        int points = actions.prayerPoints();
        boolean hold = sleepwalkerComing() && fight.getPhase() < 4 && points > PRAYER_HOLD_FLOOR;
        return points < actions.maxPrayer() && points <= config.drinkAt() && !hold && restoreDoses() > 0;
    }

    boolean needsCure()
    {
        return actions.varbit(DreamData.PARASITE_VARBIT) > 0 && !fight.isWeakened() && cureDoses() > 0;
    }

    /** A divine dose that would leave too few Hitpoints goes down with food, so the boost never lapses for long. */
    boolean needsBoost()
    {
        boolean boostingPlace = actions.inInstance()
            || trip.getPhase() == TripPhase.TRAVELLING && DreamData.inSanctuary(actions.position());
        // A drink ends the attack in progress, and the boost can wait until her sleepwalkers are gone.
        return boostingPlace && actions.boosted(Skill.STRENGTH) - actions.real(Skill.STRENGTH) < BOOST_FLOOR
            && !fight.sleepwalkersUp() && !combatPotions().isEmpty() && (boostAffordable() || food() > 0);
    }

    /** The boost is due, but its dose alone would leave the player at or below the eating point. */
    boolean eatsForBoost()
    {
        return needsBoost() && !boostAffordable();
    }

    private boolean boostAffordable()
    {
        return affordable(baseName(actions.itemName(config.combatPotionId())), actions.hitpoints(), config.eatAt());
    }

    /** A divine dose costs Hitpoints, so alone it waits until it leaves the player above the eating point. */
    static boolean affordable(String potion, int hitpoints, int eatAt)
    {
        return potion == null || !potion.startsWith("Divine") || hitpoints - DIVINE_COST > eatAt;
    }

    @Override
    public boolean isValid()
    {
        int now = tick.getAsInt();
        return trip.getPhase() != TripPhase.BANKING && now - requestedAt >= OBSERVE_TICKS
            && (needsFood() || needsRestore() || needsCure() || needsBoost());
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
        // Food and potions have separate delays in the game, so one of each goes on the same tick.
        Map<Integer, String> clicks = new LinkedHashMap<>();
        String drink = null;
        if (needsFood() || eatsForBoost())
        {
            clicks.put(config.foodId(), "Eat");
        }
        if (needsCure())
        {
            drink = "a parasite cure";
            clicks.put(Doses.lowest(List.of(config.parasiteCure().getDoses()), actions::count), "Drink");
        }
        else if (needsRestore())
        {
            drink = "a restore";
            clicks.put(Doses.lowest(List.of(Doses.SUPER_RESTORE, Doses.PRAYER_POTION), actions::count), "Drink");
        }
        else if (needsBoost())
        {
            drink = "a combat potion";
            // The fewest doses first: names end in (1) to (4).
            clicks.put(combatPotions().stream().min(Comparator.comparing(actions::itemName)).orElse(-1), "Drink");
        }
        if (drink == null)
        {
            status = "Eating";
        }
        else
        {
            status = (clicks.containsKey(config.foodId()) ? "Eating and drinking " : "Drinking ") + drink;
        }
        actions.inventoryAll(clicks, sent);
    }
}
