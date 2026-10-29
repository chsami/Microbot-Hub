package net.runelite.client.plugins.microbot.fourtify.core.activity;

import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * An item an activity needs before it starts, such as "an axe for your Woodcutting level": any of some item
 * IDs, best first, of which only those {@code usable} in the current state count. Carried or worn counts as
 * having it. When it is missing, the activity's bank trip fetches the best usable one the bank holds.
 * <p>
 * A need made {@link #upgradable} also asks for a better item when the bank holds one.
 * <p>
 * A need given an {@link #orInstead} way out is also satisfied for the activity's requirement when that way holds (a
 * skill that needs no tool, say); the bank trip still fetches the item when the bank holds it.
 * <p>
 * A need made with {@link #wear} must also be worn, such as a weapon or armour: once carried, the activity's
 * {@code Equip} task puts it on. The activity's worn needs are its gear.
 */
public final class NeededItem {
    private final String label;
    private final List<Integer> idsBestFirst;
    private final BiPredicate<GameState, Integer> usable;
    private final int amount;
    private final boolean worn;
    private final boolean stackable;
    private final boolean twoHanded;
    private final boolean usedUp;
    /** How good each item ID is, higher is better, or null when the need is not upgradable. */
    private final ToIntFunction<Integer> rank;
    /** Withdraws tried per upgrade item ID; shared by the copies of this need. */
    private final Map<Integer, Integer> upgradeTries;
    /** Another way to satisfy the activity's requirement for this need, or null. */
    private final Predicate<GameState> instead;
    /** What {@link #instead} asks for, in words, for the requirement's text. */
    private final String insteadText;

    /** An upgrade is tried this many times per item, the number of failures after which a task is skipped. */
    static final int UPGRADE_TRIES = 3;

    public NeededItem(String label, int amount, List<Integer> idsBestFirst, BiPredicate<GameState, Integer> usable) {
        this(label, amount, idsBestFirst, usable, false, false, false, false, null, new HashMap<>(), null, null);
    }

    private NeededItem(String label, int amount, List<Integer> idsBestFirst, BiPredicate<GameState, Integer> usable,
                       boolean worn, boolean stackable, boolean twoHanded, boolean usedUp,
                       ToIntFunction<Integer> rank, Map<Integer, Integer> upgradeTries,
                       Predicate<GameState> instead, String insteadText) {
        this.label = label;
        this.amount = amount;
        this.idsBestFirst = new ArrayList<>(idsBestFirst);
        this.usable = usable;
        this.worn = worn;
        this.stackable = stackable;
        this.twoHanded = twoHanded;
        this.usedUp = usedUp;
        this.rank = rank;
        this.upgradeTries = upgradeTries;
        this.instead = instead;
        this.insteadText = insteadText;
    }

    /** The items stack in one inventory slot, such as feathers or runes: taking more of one carried needs no free slot. */
    public NeededItem stacks() {
        return new NeededItem(label, amount, idsBestFirst, usable, worn, true, twoHanded, usedUp, rank, upgradeTries, instead, insteadText);
    }

    /**
     * A tool that has better versions, such as an axe: while the bank holds a usable item that {@code rank}s higher
     * than the best one carried or worn, the bank trip fetches it ({@link #upgrade}) and banks the worse ones
     * ({@link #superseded}). Items of equal rank are not upgrades of each other. The need is still met by any
     * usable item, so the activity is never blocked for want of an upgrade. The bank contents are as last seen
     * (Microbot remembers them), so they can be out of date: an upgrade that does not arrive is tried
     * {@value #UPGRADE_TRIES} times and then given up for the rest of the run.
     */
    public NeededItem upgradable(ToIntFunction<Integer> rank) {
        return new NeededItem(label, amount, idsBestFirst, usable, worn, stackable, twoHanded, usedUp, rank, upgradeTries, instead, insteadText);
    }

    /**
     * Another way to satisfy the activity's requirement for this need, such as a skill that needs no tool: the
     * requirement holds when the item is met or obtainable or when {@code instead} holds, and its text ends with
     * {@code text}. Nothing else changes: {@link #isMet}, the bank trip and the upgrades judge the item alone, so a
     * usable item in the bank is still fetched.
     */
    public NeededItem orInstead(String text, Predicate<GameState> instead) {
        return new NeededItem(label, amount, idsBestFirst, usable, worn, stackable, twoHanded, usedUp, rank, upgradeTries, instead, text);
    }

    /** The other way to satisfy the requirement holds ({@link #orInstead}). */
    public boolean insteadHolds(GameState state) {
        return instead != null && instead.test(state);
    }

    /** The other way in words, to follow the need's name in a requirement's text: ", or ..." or nothing. */
    public String insteadText() {
        return insteadText == null ? "" : ", or " + insteadText;
    }

    public boolean isStackable() {
        return stackable;
    }

    /** A two-handed weapon: it takes off the shield too. */
    public NeededItem twoHanded() {
        return new NeededItem(label, amount, idsBestFirst, usable, worn, stackable, true, usedUp, rank, upgradeTries, instead, insteadText);
    }

    public boolean isTwoHanded() {
        return twoHanded;
    }

    /**
     * Materials the work uses up, such as logs to fletch: met while any is left, so the work goes on until none
     * is; a bank trip then fetches the full amount.
     */
    public NeededItem usedUp() {
        return new NeededItem(label, amount, idsBestFirst, usable, worn, stackable, twoHanded, true, rank, upgradeTries, instead, insteadText);
    }

    /** A piece of gear: the best of these the state allows (such as by Attack or Defence level), to be worn. */
    public static NeededItem wear(String label, List<Integer> idsBestFirst, BiPredicate<GameState, Integer> usable) {
        return new NeededItem(label, 1, idsBestFirst, usable, true, false, false, false, null, new HashMap<>(), null, null);
    }

    /** Must be worn, not only carried. */
    public boolean mustBeWorn() {
        return worn;
    }

    /** One of the usable items is worn. */
    public boolean isWorn(GameState state) {
        for (int id : idsBestFirst) {
            if (usable.test(state, id) && state.getEquipment().contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** The best usable item carried in the inventory, to put on; null when none is carried. */
    public Integer carried(GameState state) {
        for (int id : idsBestFirst) {
            if (usable.test(state, id) && state.count(id) > 0) {
                return id;
            }
        }
        return null;
    }

    /** One of these items, any of them, whatever the state. */
    public static NeededItem any(String label, int... idsBestFirst) {
        List<Integer> ids = new ArrayList<>();
        for (int id : idsBestFirst) {
            ids.add(id);
        }
        return new NeededItem(label, 1, ids, (state, id) -> true);
    }

    /** Every item ID this need may ask for, so the snapshot reads them from the bank. */
    public Set<Integer> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(idsBestFirst));
    }

    /** Carried or worn, counting only the IDs usable now. */
    public int have(GameState state) {
        int have = 0;
        for (int id : idsBestFirst) {
            if (usable.test(state, id)) {
                have += state.count(id) + (state.getEquipment().contains(id) ? 1 : 0);
            }
        }
        return have;
    }

    public boolean isMet(GameState state) {
        return usedUp ? have(state) > 0 : have(state) >= amount;
    }

    public int missing(GameState state) {
        return Math.max(0, amount - have(state));
    }

    /** The best usable item ID the bank holds, or null. */
    public Integer inBank(GameState state) {
        for (int id : idsBestFirst) {
            if (usable.test(state, id) && state.bankCount(id) > 0) {
                return id;
            }
        }
        return null;
    }

    /** How many to have; an upgrade fetches this many of the better item. */
    public int amount() {
        return amount;
    }

    /** The highest rank among the usable items carried or worn, or null when none is ({@link #upgradable} needs only). */
    private Integer bestOwnedRank(GameState state) {
        Integer best = null;
        for (int id : idsBestFirst) {
            if (usable.test(state, id) && (state.count(id) > 0 || state.getEquipment().contains(id))) {
                best = best == null ? rank.applyAsInt(id) : Math.max(best, rank.applyAsInt(id));
            }
        }
        return best;
    }

    /**
     * The better item to fetch: the best usable item the bank holds that ranks strictly above the best one carried
     * or worn. Null when the need is not upgradable, nothing usable is owned yet (that is a missing need), the bank
     * has not been seen, nothing better is in it, or its withdraw was already tried {@value #UPGRADE_TRIES} times.
     */
    public Integer upgrade(GameState state) {
        Integer owned = rank == null || !state.isBankKnown() ? null : bestOwnedRank(state);
        if (owned == null) {
            return null;
        }
        Integer best = null;
        for (int id : idsBestFirst) {
            if (usable.test(state, id) && state.bankCount(id) > 0 && rank.applyAsInt(id) > owned
                    && upgradeTries.getOrDefault(id, 0) < UPGRADE_TRIES
                    && (best == null || rank.applyAsInt(id) > rank.applyAsInt(best))) {
                best = id;
            }
        }
        return best;
    }

    /** Records one withdraw of an upgrade item, so a bank that no longer holds it is not asked for ever. */
    public void tried(int id) {
        upgradeTries.merge(id, 1, Integer::sum);
    }

    /** The usable items carried that rank below the best one carried or worn: what an upgrade makes useless. */
    public Set<Integer> superseded(GameState state) {
        Set<Integer> out = new LinkedHashSet<>();
        Integer owned = rank == null ? null : bestOwnedRank(state);
        if (owned != null) {
            for (int id : idsBestFirst) {
                if (usable.test(state, id) && state.count(id) > 0 && rank.applyAsInt(id) < owned) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /** Met, or there is a way to meet it: in the bank, or the bank has not been seen yet. */
    public boolean isObtainable(GameState state) {
        return isMet(state) || !state.isBankKnown() || inBank(state) != null;
    }

    @Override
    public String toString() {
        return label;
    }
}
