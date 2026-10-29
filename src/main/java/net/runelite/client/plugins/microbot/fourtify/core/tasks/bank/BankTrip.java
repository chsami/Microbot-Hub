package net.runelite.client.plugins.microbot.fourtify.core.tasks.bank;

import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.engine.TaskList;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.gear.Equip;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A visit to the bank as one task list, in checked steps: open the nearest bank, deposit items by ID, withdraw
 * what the needs lack, close the bank. A visit is due when {@code depositWhen} holds (such as a full inventory
 * of logs), a need is unmet and the trip can meet it, or the bank holds an upgrade for a need ({@link
 * NeededItem#upgradable}) and there is room to take it. Each step is judged by the next snapshots (bank open,
 * items gone, item arrived, bank closed), not by what the banking helper returned.
 */
public final class BankTrip {
    private BankTrip() {
    }

    /**
     * A trip can meet this need: the bank may hold it (unseen, or seen with it), and there is room to take it
     * (a free slot, or a stack of it to add to), now or once the trip has deposited its items. A visit that cannot
     * would open the bank, fail to withdraw, close it and start again.
     */
    public static boolean canMeet(NeededItem need, Set<Integer> deposit, GameState state) {
        return need.isObtainable(state) && (hasRoomFor(need, state) || state.hasAny(deposit));
    }

    /**
     * A free slot, or a stack of the item the bank would give, which taking more only adds to. A two-handed weapon
     * taken over a worn weapon and shield needs a second free slot, to wield it afterwards ({@link Equip#canWield}).
     */
    static boolean hasRoomFor(NeededItem need, GameState state) {
        Integer id = need.inBank(state);
        if (need.isStackable() && id != null && state.count(id) > 0) {
            return true;
        }
        boolean weaponAndShield = state.wornIn(Equip.WEAPON_SLOT) >= 0 && state.wornIn(Equip.SHIELD_SLOT) >= 0;
        return state.getFreeSlots() >= (need.isTwoHanded() && weaponAndShield ? 2 : 1);
    }

    public static TaskList of(String name, Set<Integer> deposit, String depositLabel, Predicate<GameState> depositWhen, List<NeededItem> needs) {
        return of(name, deposit, depositLabel, depositWhen, needs, UnneededItems.none());
    }

    /** Also banks the carried items the activity does not need, once, before its work. */
    public static TaskList of(String name, Set<Integer> deposit, String depositLabel, Predicate<GameState> depositWhen,
                              List<NeededItem> needs, UnneededItems unneeded) {
        Predicate<GameState> needsVisit = state -> needs.stream().anyMatch(need -> need.isMet(state)
                ? need.upgrade(state) != null && (hasRoomFor(need, state) || state.hasAny(depositable(deposit, unneeded, state)))
                : canMeet(need, depositable(deposit, unneeded, state), state));
        return new TaskList(name,
                new DepositItems(deposit, depositLabel),
                new DepositItems(unneeded::in, "items the activity does not need"),
                new TakeFromBank(needs),
                new DepositItems(state -> superseded(needs, state), "items an upgrade replaces"),
                new GoToBank(state -> (depositWhen.test(state) && state.hasAny(deposit)) || !unneeded.in(state).isEmpty()
                        || needsVisit.test(state)),
                new CloseBank());
    }

    /**
     * The carried items of upgradable needs that rank below a better one carried or worn, such as a bronze axe once
     * a rune axe is carried. They go after the better item is in the inventory, so the inventory does not collect
     * them, and never before it (the better item is what makes them superseded).
     */
    static Set<Integer> superseded(List<NeededItem> needs, GameState state) {
        Set<Integer> all = new HashSet<>();
        needs.forEach(need -> all.addAll(need.superseded(state)));
        return all;
    }

    /** What a trip can deposit to make room: the activity's deposit items and the unneeded items still to bank. */
    public static Set<Integer> depositable(Set<Integer> deposit, UnneededItems unneeded, GameState state) {
        Set<Integer> all = new HashSet<>(deposit);
        all.addAll(unneeded.in(state));
        return all;
    }
}
